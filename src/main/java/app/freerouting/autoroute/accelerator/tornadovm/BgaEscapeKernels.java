package app.freerouting.autoroute.accelerator.tornadovm;

import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * TornadoVM version of the BGA escape kernels in {@code bga_escape_kernels.cl}.
 *
 * <p>Each kernel is a thin {@link KernelContext} entry point that reads its thread index and calls
 * a static per-item method. TornadoVM inlines that method into the generated GPU code, and the same
 * method can be called from a plain Java loop, which gives a CPU reference that runs the exact same
 * arithmetic. Buffer layouts and constants match the OpenCL kernels one to one.
 */
public final class BgaEscapeKernels {

  /** Number of ints per pin record in the pin buffer. */
  public static final int PIN_STRIDE = 12;

  /** Number of ints per obstacle record in the obstacle buffer. */
  public static final int OBSTACLE_STRIDE = 10;

  /** Number of ints per pin in the via site buffer (8 sites, 4 ints each). */
  public static final int VIA_SITE_STRIDE = 32;

  /** Number of ints per pin in the solution buffer. */
  public static final int SOLUTION_STRIDE = BgaEscapeGrid.SOLUTION_STRIDE;

  /** Cost written for an unusable via site. */
  public static final int NO_SITE_COST = 999999;

  /** Value of a cell nobody has claimed yet in the winner buffer. */
  public static final int NO_WINNER = 0xFFFFFFFF;

  /** History cost added to a contested cell for each pin that loses it. */
  public static final int LOSER_HISTORY_COST = 500;

  // Indices into the parameter buffer shared by all kernels.
  public static final int P_PIN_COUNT = 0;
  public static final int P_OBSTACLE_COUNT = 1;
  public static final int P_GRID_W = 2;
  public static final int P_GRID_H = 3;
  public static final int P_LAYERS = 4;
  public static final int P_GRID_STEP = 5;
  public static final int P_ORIGIN_X = 6;
  public static final int P_ORIGIN_Y = 7;
  public static final int P_PITCH = 8;
  public static final int P_CURRENT_RING = 9;
  public static final int PARAM_COUNT = 10;

  private BgaEscapeKernels() {}

  /**
   * Kernel 1, {@code rasterize_obstacles_and_rudy}: marks blocked grid cells and computes the RUDY
   * pin density on layer 0. One thread per grid cell over all layers.
   */
  public static void rasterizeObstaclesAndRudy(
      KernelContext context,
      IntArray params,
      IntArray obstacles,
      IntArray pins,
      IntArray occupancy,
      IntArray rudy) {
    int cell = context.globalIdx;
    int totalCells = params.get(P_GRID_W) * params.get(P_GRID_H) * params.get(P_LAYERS);
    if (cell < totalCells) {
      rasterizeCell(cell, params, obstacles, pins, occupancy, rudy);
    }
  }

  /** Body of {@link #rasterizeObstaclesAndRudy} for one cell, callable from plain Java. */
  public static void rasterizeCell(
      int cell,
      IntArray params,
      IntArray obstacles,
      IntArray pins,
      IntArray occupancy,
      IntArray rudy) {
    int gridW = params.get(P_GRID_W);
    int gridH = params.get(P_GRID_H);
    int step = params.get(P_GRID_STEP);
    int obsCount = params.get(P_OBSTACLE_COUNT);
    int pinCount = params.get(P_PIN_COUNT);

    int gx = cell % gridW;
    int gy = (cell / gridW) % gridH;
    int gz = cell / (gridW * gridH);
    int bx = params.get(P_ORIGIN_X) + gx * step;
    int by = params.get(P_ORIGIN_Y) + gy * step;

    int occupied = 0;
    for (int i = 0; i < obsCount && occupied == 0; i++) {
      int o = i * OBSTACLE_STRIDE;
      int obsLayer = obstacles.get(o + 8);
      if (obsLayer == gz || obsLayer == -1) {
        if (bx >= obstacles.get(o)
            && bx <= obstacles.get(o + 1)
            && by >= obstacles.get(o + 2)
            && by <= obstacles.get(o + 3)
            && (bx - by) >= obstacles.get(o + 4)
            && (bx - by) <= obstacles.get(o + 5)
            && (bx + by) >= obstacles.get(o + 6)
            && (bx + by) <= obstacles.get(o + 7)) {
          occupied = -1;
        }
      }
    }

    for (int p = 0; p < pinCount && occupied == 0; p++) {
      int po = p * PIN_STRIDE;
      if (bx >= pins.get(po + 2)
          && bx <= pins.get(po + 3)
          && by >= pins.get(po + 4)
          && by <= pins.get(po + 5)) {
        occupied = p + 1;
      }
    }

    occupancy.set(cell, occupied);

    if (gz == 0) {
      int window = step * 8;
      int score = 0;
      for (int p = 0; p < pinCount; p++) {
        int po = p * PIN_STRIDE;
        int dist = Math.max(Math.abs(bx - pins.get(po)), Math.abs(by - pins.get(po + 1)));
        if (dist < window && dist > 0) {
          score += window - dist;
        }
      }
      rudy.set(gy * gridW + gx, score);
    }
  }

  /**
   * Kernel 2, {@code evaluate_via_dogbone_sites}: checks the four diagonal dog-bone via sites of
   * each pin against the occupancy grid on all layers. One thread per pin.
   */
  public static void evaluateViaDogboneSites(
      KernelContext context,
      IntArray params,
      IntArray pins,
      IntArray occupancy,
      IntArray viaSites) {
    int pin = context.globalIdx;
    if (pin < params.get(P_PIN_COUNT)) {
      evaluateViaSites(pin, params, pins, occupancy, viaSites);
    }
  }

  /** Body of {@link #evaluateViaDogboneSites} for one pin, callable from plain Java. */
  public static void evaluateViaSites(
      int pin, IntArray params, IntArray pins, IntArray occupancy, IntArray viaSites) {
    int gridW = params.get(P_GRID_W);
    int gridH = params.get(P_GRID_H);
    int layers = params.get(P_LAYERS);
    int step = params.get(P_GRID_STEP);
    int originX = params.get(P_ORIGIN_X);
    int originY = params.get(P_ORIGIN_Y);
    int halfPitch = params.get(P_PITCH) / 2;

    int po = pin * PIN_STRIDE;
    int u = pins.get(po);
    int v = pins.get(po + 1);
    int targetLayer = pins.get(po + 6);
    int quadrantU = pins.get(po + 8);
    int quadrantV = pins.get(po + 9);
    // Sectors 0 and 2 (east and west) escape along u, sectors 1 and 3 along v.
    boolean outwardAlongU = pins.get(po + 7) == 0 || pins.get(po + 7) == 2;
    int disposition = pins.get(po + 11);
    int base = pin * VIA_SITE_STRIDE;

    for (int site = 0; site < 8; site++) {
      int record = base + site * 4;
      int gx = -1;
      int gy = -1;
      int layer = -1;
      int cost = NO_SITE_COST;

      // Sites 0..3 are the four 45 degree interstitial positions (+-p/2, +-p/2): the quadrant that
      // points away from the package centre, then the one that keeps the outward axis of the
      // breakout sector, then the one that keeps the other axis, then the opposite one. Sites 4..7
      // are unused.
      if (disposition != 0 && site < 4) {
        boolean keepOutward = site == 0 || site == 1;
        boolean keepTransverse = site == 0 || site == 2;
        boolean keepU = outwardAlongU ? keepOutward : keepTransverse;
        boolean keepV = outwardAlongU ? keepTransverse : keepOutward;
        int signU = keepU ? quadrantU : -quadrantU;
        int signV = keepV ? quadrantV : -quadrantV;
        int cx = (u + signU * halfPitch - originX) / step;
        int cy = (v + signV * halfPitch - originY) / step;
        if (cx >= 0 && cx < gridW && cy >= 0 && cy < gridH) {
          boolean valid = true;
          for (int l = 0; l < layers && valid; l++) {
            int occ = occupancy.get((l * gridH + cy) * gridW + cx);
            if (occ != 0 && occ != pin + 1) {
              valid = false;
            }
          }
          if (valid) {
            int siteCost = (site == 0) ? 1000 : ((site == 3) ? 2500 : 1500);
            if (disposition == 1) {
              siteCost /= 2;
            }
            gx = cx;
            gy = cy;
            layer = targetLayer;
            cost = siteCost;
          }
        }
      }

      viaSites.set(record, gx);
      viaSites.set(record + 1, gy);
      viaSites.set(record + 2, layer);
      viaSites.set(record + 3, cost);
    }
  }

  /**
   * Kernel 3, {@code expand_ring_candidates}: picks the cheapest free via site for each pin of the
   * current ring that is not committed yet. One thread per pin.
   */
  public static void expandRingCandidates(
      KernelContext context,
      IntArray params,
      IntArray pins,
      IntArray viaSites,
      IntArray rudy,
      IntArray history,
      IntArray solutions) {
    int pin = context.globalIdx;
    if (pin < params.get(P_PIN_COUNT)) {
      expandCandidate(pin, params, pins, viaSites, rudy, history, solutions);
    }
  }

  /** Body of {@link #expandRingCandidates} for one pin, callable from plain Java. */
  public static void expandCandidate(
      int pin,
      IntArray params,
      IntArray pins,
      IntArray viaSites,
      IntArray rudy,
      IntArray history,
      IntArray solutions) {
    int po = pin * PIN_STRIDE;
    int so = pin * SOLUTION_STRIDE;
    if (pins.get(po + 10) != params.get(P_CURRENT_RING)
        || pins.get(po + 11) == 0
        || solutions.get(so + 8) == 2) {
      return;
    }

    int gridW = params.get(P_GRID_W);
    int gridH = params.get(P_GRID_H);
    int step = params.get(P_GRID_STEP);

    int bestSite = -1;
    int bestCost = NO_SITE_COST;
    int bestGx = -1;
    int bestGy = -1;
    int bestLayer = 0;

    int base = pin * VIA_SITE_STRIDE;
    for (int s = 0; s < 8; s++) {
      int record = base + s * 4;
      int tgx = viaSites.get(record);
      int tgy = viaSites.get(record + 1);
      int tl = viaSites.get(record + 2);
      int siteCost = viaSites.get(record + 3);
      if (tgx >= 0 && siteCost < NO_SITE_COST) {
        int total =
            siteCost
                + history.get((tl * gridH + tgy) * gridW + tgx)
                + rudy.get(tgy * gridW + tgx) / 100;
        if (total < bestCost) {
          bestCost = total;
          bestSite = s;
          bestGx = tgx;
          bestGy = tgy;
          bestLayer = tl;
        }
      }
    }

    if (bestSite >= 0) {
      solutions.set(so, (pins.get(po) - params.get(P_ORIGIN_X)) / step);
      solutions.set(so + 1, (pins.get(po + 1) - params.get(P_ORIGIN_Y)) / step);
      solutions.set(so + 2, 0);
      solutions.set(so + 3, bestGx);
      solutions.set(so + 4, bestGy);
      solutions.set(so + 5, bestLayer);
      solutions.set(so + 6, bestCost);
      solutions.set(so + 7, 2);
      solutions.set(so + 8, 1);
    } else {
      solutions.set(so + 8, 0);
    }
  }

  /**
   * Kernel 4a, {@code elect_cell_winners}: elects one winner per contested via cell. One thread per
   * pin.
   *
   * <p>The OpenCL version packs {@code min(cost, 65534) << 16 | pin} and takes an {@code
   * atomic_min} per cell. TornadoVM has no global atomic min, so each resolved pin scans the other
   * resolved pins, computes the same minimum for its own cell and stores it. Every pin that targets
   * a cell stores the same value, so the winner buffer ends up identical to the atomic version.
   * Only cells with a resolved pin are read afterwards, so the buffer does not need a reset between
   * passes.
   */
  public static void electCellWinners(
      KernelContext context, IntArray params, IntArray solutions, IntArray winners) {
    int pin = context.globalIdx;
    if (pin < params.get(P_PIN_COUNT)) {
      electWinner(pin, params, solutions, winners);
    }
  }

  /** Body of {@link #electCellWinners} for one pin, callable from plain Java. */
  public static void electWinner(int pin, IntArray params, IntArray solutions, IntArray winners) {
    int so = pin * SOLUTION_STRIDE;
    if (solutions.get(so + 8) != 1) {
      return;
    }
    int pinCount = params.get(P_PIN_COUNT);
    int cell = cellOf(so, params, solutions);

    // Packed values are compared as unsigned ints; flipping the sign bit makes a signed
    // comparison give the same order.
    int best = NO_WINNER ^ 0x80000000;
    for (int q = 0; q < pinCount; q++) {
      int qo = q * SOLUTION_STRIDE;
      if (solutions.get(qo + 8) == 1) {
        if (cellOf(qo, params, solutions) == cell) {
          int packed = (Math.min(solutions.get(qo + 6), 65534) << 16) | (q & 0xFFFF);
          int key = packed ^ 0x80000000;
          if (key < best) {
            best = key;
          }
        }
      }
    }
    winners.set(cell, best ^ 0x80000000);
  }

  /**
   * Kernel 4b, {@code commit_cell_winners}: commits the winner of each cell, sends the losers back
   * to the next pass and makes their cell more expensive. One thread per pin.
   */
  public static void commitCellWinners(
      KernelContext context,
      IntArray params,
      IntArray solutions,
      IntArray winners,
      IntArray history) {
    int pin = context.globalIdx;
    if (pin < params.get(P_PIN_COUNT)) {
      int so = pin * SOLUTION_STRIDE;
      if (solutions.get(so + 8) == 1) {
        int cell = cellOf(so, params, solutions);
        if ((winners.get(cell) & 0xFFFF) == (pin & 0xFFFF)) {
          solutions.set(so + 8, 2);
        } else {
          context.atomicAdd(history, cell, LOSER_HISTORY_COST);
          solutions.set(so + 8, 0);
        }
      }
    }
  }

  /**
   * Plain Java version of {@link #commitCellWinners} for one pin. Pins run one after the other, so
   * the history update needs no atomic.
   */
  public static void commitWinner(
      int pin, IntArray params, IntArray solutions, IntArray winners, IntArray history) {
    int so = pin * SOLUTION_STRIDE;
    if (solutions.get(so + 8) == 1) {
      int cell = cellOf(so, params, solutions);
      if ((winners.get(cell) & 0xFFFF) == (pin & 0xFFFF)) {
        solutions.set(so + 8, 2);
      } else {
        history.set(cell, history.get(cell) + LOSER_HISTORY_COST);
        solutions.set(so + 8, 0);
      }
    }
  }

  /** Grid cell (with layer) of the via chosen in the solution record starting at {@code so}. */
  private static int cellOf(int so, IntArray params, IntArray solutions) {
    return (solutions.get(so + 5) * params.get(P_GRID_H) + solutions.get(so + 4))
            * params.get(P_GRID_W)
        + solutions.get(so + 3);
  }
}
