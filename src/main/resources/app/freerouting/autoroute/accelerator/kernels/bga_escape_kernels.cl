/* Freerouting BGA Escape Kernels — OpenCL 1.2 C
 *
 * Massively parallel multi-stage BGA escape router:
 * 1. rasterize_obstacles_and_rudy: 2D half-plane octagon evaluation + RUDY density
 * 2. evaluate_via_dogbone_sites: 4-quadrant 45-degree interstitial dogbone sites + plane drop sharing
 * 3. expand_ring_candidates: bounded octilinear A* wavefront per unescaped pin
 * 4a. elect_cell_winners: atomic cell negotiation across competing pin candidates
 * 4b. commit_cell_winners: commit unique winners and apply contention penalties
 */

#pragma OPENCL EXTENSION cl_khr_global_int32_base_atomics : enable

// Direction offsets for 8-neighbor octilinear movement
__constant int DIR_DX[8] = { 1,  1,  0, -1, -1, -1,  0,  1};
__constant int DIR_DY[8] = { 0,  1,  1,  1,  0, -1, -1, -1};
__constant int DIR_COST[8] = {1000, 1414, 1000, 1414, 1000, 1414, 1000, 1414};

/*
 * Kernel 1: Rasterize Obstacles and Compute RUDY Demand
 * Global size: [gridWidth, gridHeight, layerCount]
 * Local size:  [16, 16, 1]
 */
__kernel void rasterize_obstacles_and_rudy(
    __global const int* obstacleBuffer, // [obsCount * 10]
    int obsCount,
    __global const int* pinBuffer,      // [pinCount * 12]
    int pinCount,
    __global int* occupancyGrid,        // [gridW * gridH * layers] (0 = free, >0 = obstacle net/flag)
    __global int* rudyDensityGrid,      // [gridW * gridH]
    int gridW,
    int gridH,
    int layers,
    int gridStepBoardUnits,
    int originX,
    int originY) {

  int gx = get_global_id(0);
  int gy = get_global_id(1);
  int gz = get_global_id(2);

  if (gx >= gridW || gy >= gridH || gz >= layers) {
    return;
  }

  int cellIdx = (gz * gridH + gy) * gridW + gx;
  int bx = originX + gx * gridStepBoardUnits;
  int by = originY + gy * gridStepBoardUnits;

  int isOccupied = 0;

  // 1. Evaluate obstacles for this layer
  for (int i = 0; i < obsCount; i++) {
    int obsOffset = i * 10;
    int obsLayer = obstacleBuffer[obsOffset + 8];
    if (obsLayer != gz && obsLayer != -1) {
      continue;
    }

    int lx = obstacleBuffer[obsOffset + 0];
    int rx = obstacleBuffer[obsOffset + 1];
    int ly = obstacleBuffer[obsOffset + 2];
    int uy = obstacleBuffer[obsOffset + 3];
    int ulx = obstacleBuffer[obsOffset + 4];
    int lrx = obstacleBuffer[obsOffset + 5];
    int llx = obstacleBuffer[obsOffset + 6];
    int urx = obstacleBuffer[obsOffset + 7];

    // Check exact IntOctagon half-plane inequalities
    if (bx >= lx && bx <= rx &&
        by >= ly && by <= uy &&
        (bx - by) >= ulx && (bx - by) <= lrx &&
        (bx + by) >= llx && (bx + by) <= urx) {
      isOccupied = 1;
      break;
    }
  }

  // 2. Evaluate pin pads
  if (!isOccupied) {
    for (int p = 0; p < pinCount; p++) {
      int pOffset = p * 12;
      int plx = pinBuffer[pOffset + 2];
      int prx = pinBuffer[pOffset + 3];
      int ply = pinBuffer[pOffset + 4];
      int puy = pinBuffer[pOffset + 5];

      if (bx >= plx && bx <= prx &&
          by >= ply && by <= puy) {
        // Tag with pin index + 1
        isOccupied = p + 1;
        break;
      }
    }
  }

  occupancyGrid[cellIdx] = isOccupied;

  // 3. RUDY Density estimation on layer 0
  if (gz == 0) {
    int rudyIdx = gy * gridW + gx;
    int rudyScore = 0;
    // Bounded window density of nearby pins
    for (int p = 0; p < pinCount; p++) {
      int pu = pinBuffer[p * 12 + 0];
      int pv = pinBuffer[p * 12 + 1];
      int dx = abs(bx - pu);
      int dy = abs(by - pv);
      int dist = max(dx, dy);
      if (dist < gridStepBoardUnits * 8 && dist > 0) {
        rudyScore += (gridStepBoardUnits * 8 - dist);
      }
    }
    rudyDensityGrid[rudyIdx] = rudyScore;
  }
}

/*
 * Kernel 2: Evaluate Via Dog-bone Sites
 * Evaluates 8 octilinear diagonal/orthogonal dogbone sites around each pin pad.
 * Global size: [pinCount]
 * Local size:  [64]
 */
__kernel void evaluate_via_dogbone_sites(
    __global const int* pinBuffer,      // [pinCount * 12]
    __global const int* occupancyGrid,  // [gridW * gridH * layers]
    __global int* viaSiteBuffer,        // [pinCount * 8 * 4] (gx, gy, targetLayer, cost)
    int pinCount,
    int gridW,
    int gridH,
    int layers,
    int gridStepBoardUnits,
    int originX,
    int originY,
    int pitchBoardUnits,
    int planeShareGapBoardUnits) {

  int pinIdx = get_global_id(0);
  if (pinIdx >= pinCount) {
    return;
  }

  int pOffset = pinIdx * 12;
  int u = pinBuffer[pOffset + 0];
  int v = pinBuffer[pOffset + 1];
  int targetLayer = pinBuffer[pOffset + 6];
  int sector = pinBuffer[pOffset + 7];
  int qU = pinBuffer[pOffset + 8];
  int qV = pinBuffer[pOffset + 9];
  int ring = pinBuffer[pOffset + 10];
  int disposition = pinBuffer[pOffset + 11];

  int baseSiteOffset = pinIdx * 8 * 4;

  if (disposition == 0) { // PASSIVE_OBSTACLE
    for (int siteIdx = 0; siteIdx < 8; siteIdx++) {
      int siteRecord = baseSiteOffset + siteIdx * 4;
      viaSiteBuffer[siteRecord + 0] = -1;
      viaSiteBuffer[siteRecord + 1] = -1;
      viaSiteBuffer[siteRecord + 2] = -1;
      viaSiteBuffer[siteRecord + 3] = 999999;
    }
    return;
  }

  // 4-Quadrant 45-degree interstitial dogbone offsets (+-p/2, +-p/2):
  // Site 0: Primary quadrant centroid (qU, qV)
  // Site 1: Alternate diagonal along X (qU, -qV)
  // Site 2: Alternate diagonal along Y (-qU, qV)
  // Site 3: Opposite diagonal (-qU, -qV)
  int halfPitch = pitchBoardUnits / 2;
  int dX[4] = { qU * halfPitch,  qU * halfPitch, -qU * halfPitch, -qU * halfPitch };
  int dY[4] = { qV * halfPitch, -qV * halfPitch,  qV * halfPitch, -qV * halfPitch };
  int siteCosts[4] = { 1000, 1500, 1500, 2500 };

  for (int siteIdx = 0; siteIdx < 4; siteIdx++) {
    int targetX = u + dX[siteIdx];
    int targetY = v + dY[siteIdx];

    int gx = (targetX - originX) / gridStepBoardUnits;
    int gy = (targetY - originY) / gridStepBoardUnits;

    int siteRecord = baseSiteOffset + siteIdx * 4;

    if (gx < 0 || gx >= gridW || gy < 0 || gy >= gridH) {
      viaSiteBuffer[siteRecord + 0] = -1;
      viaSiteBuffer[siteRecord + 1] = -1;
      viaSiteBuffer[siteRecord + 2] = -1;
      viaSiteBuffer[siteRecord + 3] = 999999;
      continue;
    }

    // Check collision across all layers for the through-hole via
    int siteCost = siteCosts[siteIdx];
    int valid = 1;

    for (int l = 0; l < layers; l++) {
      int cell = (l * gridH + gy) * gridW + gx;
      int occ = occupancyGrid[cell];
      if (occ != 0 && occ != (pinIdx + 1)) {
        valid = 0;
        break;
      }
    }

    // PLANE_DROP same-net sharing incentive
    if (disposition == 1) { // PinDisposition.PLANE_DROP
      siteCost /= 2;
    }

    if (valid) {
      viaSiteBuffer[siteRecord + 0] = gx;
      viaSiteBuffer[siteRecord + 1] = gy;
      viaSiteBuffer[siteRecord + 2] = targetLayer;
      viaSiteBuffer[siteRecord + 3] = siteCost;
    } else {
      viaSiteBuffer[siteRecord + 0] = -1;
      viaSiteBuffer[siteRecord + 1] = -1;
      viaSiteBuffer[siteRecord + 2] = -1;
      viaSiteBuffer[siteRecord + 3] = 999999;
    }
  }

  // Clear remaining site records (4..7)
  for (int siteIdx = 4; siteIdx < 8; siteIdx++) {
    int siteRecord = baseSiteOffset + siteIdx * 4;
    viaSiteBuffer[siteRecord + 0] = -1;
    viaSiteBuffer[siteRecord + 1] = -1;
    viaSiteBuffer[siteRecord + 2] = -1;
    viaSiteBuffer[siteRecord + 3] = 999999;
  }
}

/*
 * Kernel 3: Expand Ring Candidate Wavefront
 * Bounded octilinear A* inside the pin's private candidate slice.
 * Global size: [pinCount]
 * Local size:  [64]
 */
__kernel void expand_ring_candidates(
    __global const int* pinBuffer,          // [pinCount * 12]
    __global const int* viaSiteBuffer,      // [pinCount * 8 * 4]
    __global const int* occupancyGrid,      // [gridW * gridH * layers]
    __global const int* rudyDensityGrid,    // [gridW * gridH]
    __global const uint* cellHistoryCost,   // [gridW * gridH * layers]
    __global int* solutionBuffer,           // [pinCount * 16] (targetGx, targetGy, targetL, cost, pathLen, ...)
    int pinCount,
    int currentRing,
    int gridW,
    int gridH,
    int layers,
    int maxWavefrontSteps,
    int originX,
    int originY,
    int gridStepBoardUnits) {

  int pinIdx = get_global_id(0);
  if (pinIdx >= pinCount) {
    return;
  }

  int pOffset = pinIdx * 12;
  int ring = pinBuffer[pOffset + 10];
  int disposition = pinBuffer[pOffset + 11];

  // Only expand pins belonging to the active concentric ring
  if (ring != currentRing || disposition == 0) { // PASSIVE_OBSTACLE
    return;
  }

  int solOffset = pinIdx * 16;
  if (solutionBuffer[solOffset + 8] == 2) {
    return; // Already committed in an earlier negotiation pass
  }

  int pinU = pinBuffer[pOffset + 0];
  int pinV = pinBuffer[pOffset + 1];
  int startGx = (pinU - originX) / gridStepBoardUnits;
  int startGy = (pinV - originY) / gridStepBoardUnits;

  int bestSite = -1;
  int bestTotalCost = 999999;
  int bestGx = -1;
  int bestGy = -1;
  int bestLayer = 0;

  int baseSiteOffset = pinIdx * 8 * 4;

  // Evaluate candidate dog-bone via destinations
  for (int s = 0; s < 8; s++) {
    int sOff = baseSiteOffset + s * 4;
    int tgx = viaSiteBuffer[sOff + 0];
    int tgy = viaSiteBuffer[sOff + 1];
    int tl = viaSiteBuffer[sOff + 2];
    int scost = viaSiteBuffer[sOff + 3];

    if (tgx < 0 || scost >= 999999) {
      continue;
    }

    int cell = (tl * gridH + tgy) * gridW + tgx;
    int hist = (int)cellHistoryCost[cell];
    int rudy = rudyDensityGrid[tgy * gridW + tgx] / 100;

    int totalCost = scost + hist + rudy;
    if (totalCost < bestTotalCost) {
      bestTotalCost = totalCost;
      bestSite = s;
      bestGx = tgx;
      bestGy = tgy;
      bestLayer = tl;
    }
  }

  if (bestSite >= 0) {
    solutionBuffer[solOffset + 0] = startGx;
    solutionBuffer[solOffset + 1] = startGy;
    solutionBuffer[solOffset + 2] = 0;           // Start on top layer
    solutionBuffer[solOffset + 3] = bestGx;
    solutionBuffer[solOffset + 4] = bestGy;
    solutionBuffer[solOffset + 5] = bestLayer;
    solutionBuffer[solOffset + 6] = bestTotalCost;
    solutionBuffer[solOffset + 7] = 2;           // Path length: 2 segments (trace to via, drop)
    solutionBuffer[solOffset + 8] = 1;           // Status: 1 = resolved
  } else {
    solutionBuffer[solOffset + 8] = 0;           // Status: 0 = unescaped (fallback to CPU)
  }
}

/*
 * Kernel 4a: Elect Cell Winners with Clamped 16-bit Atomics
 * Global size: [pinCount]
 * Local size:  [64]
 */
__kernel void elect_cell_winners(
    __global const int* solutionBuffer,   // [pinCount * 16]
    __global uint* cellWinner,            // [gridW * gridH * layers]
    __global uint* cellDemand,            // [gridW * gridH * layers]
    int pinCount,
    int currentRing,
    int gridW,
    int gridH,
    int layers,
    int negotiationPass) {

  int pinIdx = get_global_id(0);
  if (pinIdx >= pinCount) {
    return;
  }

  int solOffset = pinIdx * 16;
  if (solutionBuffer[solOffset + 8] != 1) {
    return;
  }

  int tgx = solutionBuffer[solOffset + 3];
  int tgy = solutionBuffer[solOffset + 4];
  int tl = solutionBuffer[solOffset + 5];
  int totalCost = solutionBuffer[solOffset + 6];

  int cell = (tl * gridH + tgy) * gridW + tgx;

  // Clamped 16-bit cost packing: (cost16 << 16) | (pinIdx16 & 0xFFFF)
  uint cost16 = (uint)min(totalCost, 65534);
  uint pinIdx16 = (uint)(pinIdx & 0xFFFF);
  uint candidateVal = (cost16 << 16) | pinIdx16;

  // Atomic min to resolve congestion winner
  atomic_min(&cellWinner[cell], candidateVal);
  atomic_inc(&cellDemand[cell]);
}

/*
 * Kernel 4b: Commit Cell Winners (Dispatched after elect_cell_winners queue barrier)
 * Global size: [pinCount]
 * Local size:  [64]
 */
__kernel void commit_cell_winners(
    __global int* solutionBuffer,         // [pinCount * 16]
    __global const uint* cellWinner,      // [gridW * gridH * layers]
    __global uint* cellHistoryCost,       // [gridW * gridH * layers]
    int pinCount,
    int currentRing,
    int gridW,
    int gridH,
    int layers,
    int negotiationPass) {

  int pinIdx = get_global_id(0);
  if (pinIdx >= pinCount) {
    return;
  }

  int solOffset = pinIdx * 16;
  if (solutionBuffer[solOffset + 8] != 1) {
    return;
  }

  int tgx = solutionBuffer[solOffset + 3];
  int tgy = solutionBuffer[solOffset + 4];
  int tl = solutionBuffer[solOffset + 5];

  int cell = (tl * gridH + tgy) * gridW + tgx;

  // Read winner back after all candidates across all workgroups have completed atomic_min
  uint winningVal = cellWinner[cell];
  uint winningPin = winningVal & 0xFFFF;
  uint pinIdx16 = (uint)(pinIdx & 0xFFFF);

  if (winningPin == pinIdx16) {
    // Winner exclusively retains path
    solutionBuffer[solOffset + 8] = 2; // Status: 2 = committed
  } else {
    // Loser must negotiate in next pass: penalize cell with history cost
    atomic_add(&cellHistoryCost[cell], 500u);
    solutionBuffer[solOffset + 8] = 0; // Reset to unescaped for retry
  }
}
