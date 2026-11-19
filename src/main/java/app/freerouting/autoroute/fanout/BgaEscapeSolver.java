package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.PinDisposition;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Deterministic CPU wavefront solver for multi-layer BGA escape routing.
 *
 * <p>Evaluates concentric Chebyshev rings from the outer perimeter inward, assigning surface-layer
 * direct escapes and 4-quadrant interstitial dogbone via sites using a pitch-harmonic quantized
 * cell grid. Fine-grained obstacle clearance verification and 4-corner interstitial fallback are
 * performed authoritatively by {@link BgaEscapeCommitter#commit}.
 */
public final class BgaEscapeSolver {

  /** Encapsulates an escaped pin proposal ready for continuous DRC validation. */
  public record PinEscapeProposal(
      BgaPinDescriptor pinDescriptor,
      IntPoint viaCenter,
      int targetLayer,
      int cost,
      boolean escaped) {}

  private BgaEscapeSolver() {}

  /**
   * Computes the pitch-harmonic grid step in board units:
   *
   * <pre>
   * deltaGrid = clamp(min(floor(P / 8), floor((wMin + cMin) / 2)), deltaMin, deltaMax)
   * where deltaMin = (P &lt;= 500 um) ? max(5 um, floor(P / 32)) : max(10 um, floor(P / 16))
   * </pre>
   */
  public static int computeGridStep(RoutingBoard board, double pitchUm, double pitchBoardUnits) {
    double res = board.communication.getResolution(Unit.UM);
    int p = (int) Math.round(pitchBoardUnits);

    int minWidth = board.rules.getMinAllowedTraceHalfWidth() * 2;
    int minClearance = board.rules.clearanceMatrix.getValue(0, 0, 0, false);
    if (minClearance <= 0) {
      minClearance = (int) Math.round(100.0 * res);
    }

    int candidate = Math.min(p / 8, (minWidth + minClearance) / 2);

    int deltaMin;
    if (pitchUm <= 500.0) {
      int fiveUm = (int) Math.max(1, Math.round(5.0 * res));
      deltaMin = Math.max(fiveUm, p / 32);
    } else {
      int tenUm = (int) Math.max(1, Math.round(10.0 * res));
      deltaMin = Math.max(tenUm, p / 16);
    }
    deltaMin = Math.max(1, deltaMin);

    int deltaMax = Math.max(deltaMin, p / 4);

    return Math.clamp(candidate, deltaMin, deltaMax);
  }

  /**
   * Solves deterministic CPU BGA escape routing for the specified area-array component.
   *
   * @param board routing board
   * @param bga BGA escape descriptor
   * @param settings router settings
   * @return list of pin escape proposals
   */
  public static List<PinEscapeProposal> solve(
      RoutingBoard board, BgaEscapeDescriptor bga, RouterSettings settings) {
    return solve(board, bga, settings, null);
  }

  /**
   * Solves deterministic CPU BGA escape routing for the specified area-array component, reporting
   * progress messages to an optional info logger.
   *
   * @param board routing board
   * @param bga BGA escape descriptor
   * @param settings router settings
   * @param infoLogger optional logger callback for UI/job messaging
   * @return list of pin escape proposals
   */
  public static List<PinEscapeProposal> solve(
      RoutingBoard board,
      BgaEscapeDescriptor bga,
      RouterSettings settings,
      Consumer<String> infoLogger) {
    if (board == null || bga == null || bga.pins() == null || bga.pins().isEmpty()) {
      return Collections.emptyList();
    }

    int gridStep = computeGridStep(board, bga.pitchUm(), bga.pitchBoardUnits());
    List<PinEscapeProposal> proposals = executeDeterministicWavefront(board, bga, gridStep);

    int escapedCount = 0;
    for (PinEscapeProposal proposal : proposals) {
      if (proposal.escaped()) {
        escapedCount++;
      }
    }

    String msg =
        "BGA escape solver: generated "
            + escapedCount
            + "/"
            + proposals.size()
            + " CPU escape proposal(s) for "
            + bga.component().name
            + ".";
    FRLogger.info(msg);
    if (infoLogger != null) {
      infoLogger.accept(msg);
    }

    return proposals;
  }

  /**
   * Deterministic ring-staged wavefront solver.
   *
   * <p>Evaluates 4-quadrant interstitial dogbone sites in concentric Chebyshev ring order from the
   * outer perimeter inward using a quantized local cell grid. Fine-grained obstacle clearance
   * verification is delegated to {@link BgaEscapeCommitter#commit}.
   */
  public static List<PinEscapeProposal> executeDeterministicWavefront(
      RoutingBoard board, BgaEscapeDescriptor bga, int gridStepBoardUnits) {
    if (bga == null || bga.pins() == null || bga.pins().isEmpty()) {
      return Collections.emptyList();
    }

    List<BgaPinDescriptor> pins = bga.pins();
    int pinCount = pins.size();
    int maxRing = bga.maxRing();

    int pitch = (int) Math.round(bga.pitchBoardUnits());
    int pinLayer = pins.get(0).pin().firstLayer();
    int halfPitch = pitch / 2;
    double centerU = (bga.minX() + bga.maxX()) / 2.0;
    double centerV = (bga.minY() + bga.maxY()) / 2.0;

    List<PinEscapeProposal> proposals = new ArrayList<>(pinCount);

    // Track occupied dogbone target sites (quantized cell key -> winning pin index)
    Map<Long, Integer> cellWinners = new HashMap<>();

    // Process concentric rings from outer perimeter (ring 0) inward (ring maxRing)
    for (int r = 0; r <= maxRing; r++) {
      for (int i = 0; i < pinCount; i++) {
        BgaPinDescriptor pd = pins.get(i);
        if (pd.ring() != r || pd.disposition() == PinDisposition.PASSIVE_OBSTACLE) {
          continue;
        }

        // If targetLayer is pinLayer, this pin escapes directly on surface with NO via
        if (pd.targetLayer() == pinLayer && pd.disposition() == PinDisposition.SIGNAL_ESCAPE) {
          proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 0, true));
          continue;
        }

        int pinU = (int) Math.round(pd.localU());
        int pinV = (int) Math.round(pd.localV());

        int bestCost = 999999;
        IntPoint bestVia = null;
        long bestCell = -1;

        int qU = (pd.localU() >= centerU) ? 1 : -1;
        int qV = (pd.localV() >= centerV) ? 1 : -1;

        // 4-Quadrant 45-degree interstitial dogbone sites: (+-p/2, +-p/2)
        int[] dX;
        int[] dY;
        if (pd.breakoutSector() == 0 || pd.breakoutSector() == 2) { // East/West: outward axis is U
          dX = new int[] {qU * halfPitch, qU * halfPitch, -qU * halfPitch, -qU * halfPitch};
          dY = new int[] {qV * halfPitch, -qV * halfPitch, qV * halfPitch, -qV * halfPitch};
        } else { // North/South: outward axis is V
          dX = new int[] {qU * halfPitch, -qU * halfPitch, qU * halfPitch, -qU * halfPitch};
          dY = new int[] {qV * halfPitch, qV * halfPitch, -qV * halfPitch, -qV * halfPitch};
        }
        int[] siteCosts = {1000, 1500, 1500, 2500};

        int cellStep = Math.max(1, gridStepBoardUnits * 2);
        for (int s = 0; s < 4; s++) {
          int vx = pinU + dX[s];
          int vy = pinV + dY[s];
          int scost = siteCosts[s];

          // PLANE_DROP sharing incentive
          if (pd.disposition() == PinDisposition.PLANE_DROP) {
            scost /= 2;
          }

          // Quantized cell hash in local coordinate frame
          long cellKey = (((long) (vx / cellStep)) << 32) | ((long) (vy / cellStep) & 0xFFFFFFFFL);

          if (!cellWinners.containsKey(cellKey)) {
            if (scost < bestCost) {
              bestCost = scost;
              bestVia = new IntPoint(vx, vy);
              bestCell = cellKey;
            }
          }
        }

        if (bestVia != null && bestCell != -1) {
          cellWinners.put(bestCell, i);
          proposals.add(new PinEscapeProposal(pd, bestVia, pd.targetLayer(), bestCost, true));
        } else {
          proposals.add(new PinEscapeProposal(pd, null, pd.targetLayer(), 999999, false));
        }
      }
    }

    return proposals;
  }
}
