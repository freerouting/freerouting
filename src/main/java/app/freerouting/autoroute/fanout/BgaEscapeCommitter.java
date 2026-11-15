package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.PinDisposition;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Commits GPU-negotiated BGA escape proposals to the host {@link RoutingBoard}.
 *
 * <p>Enforces continuous design rules checking:
 *
 * <ol>
 *   <li>Transforms local coordinates back to global board space using inverse affine rotation
 *       $R(+\theta)$.
 *   <li>Snaps trace endpoints exactly to {@link Pin#getCenter()} and dogbone via location.
 *   <li>Pre-validates trace clearances via {@link RoutingBoard#checkTraceSegment}.
 *   <li>Pre-validates via placement via {@link DrillItemMover#check}.
 *   <li>Atomically inserts valid traces and vias into the board model with {@link
 *       FixedState#USER_FIXED}.
 *   <li>Checks {@link Thread#isInterrupted()} between commits for clean cancellation.
 * </ol>
 */
public final class BgaEscapeCommitter {

  /** Summary result of a BGA escape commit pass. */
  public record CommitResult(
      int pinsAttempted,
      int pinsCommitted,
      int viasInserted,
      int tracesInserted,
      int drcRejections) {}

  private BgaEscapeCommitter() {}

  /**
   * Validates and commits the given escape proposals into the board.
   *
   * @param board host routing board
   * @param bga BGA package descriptor
   * @param proposals list of proposed pin escapes from the GPU solver
   * @return commit summary
   */
  public static CommitResult commit(
      RoutingBoard board, BgaEscapeDescriptor bga, List<PinEscapeProposal> proposals) {

    if (board == null || bga == null || proposals == null || proposals.isEmpty()) {
      return new CommitResult(0, 0, 0, 0, 0);
    }

    int attempted = 0;
    int committed = 0;
    int viasInserted = 0;
    int tracesInserted = 0;
    int drcRejections = 0;

    double rotDeg = bga.rotationDeg();
    double rad = Math.toRadians(rotDeg);
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);

    Padstack viaPadstack = resolveViaPadstack(board);
    if (viaPadstack == null) {
      FRLogger.warn("BGA escape committer: no default via padstack configured on board");
      return new CommitResult(proposals.size(), 0, 0, 0, proposals.size());
    }

    // Maintain placed plane vias for deduplication of shared ground/power vias
    Map<Long, Via> placedPlaneVias = new HashMap<>();

    for (PinEscapeProposal proposal : proposals) {
      if (Thread.currentThread().isInterrupted()) {
        FRLogger.info("BGA escape committer cancelled by interrupt");
        break;
      }

      if (!proposal.escaped()) {
        continue;
      }

      attempted++;
      BgaPinDescriptor pd = proposal.pinDescriptor();
      Pin pin = pd.pin();
      Point pinCenter = pin.getCenter();
      if (pinCenter == null) {
        drcRejections++;
        continue;
      }

      int netNo = pd.netNo();
      int[] netNumbers = (netNo > 0) ? new int[] {netNo} : new int[0];
      int pinLayer = pin.firstLayer();
      int targetLayer = pd.targetLayer();
      int clClass = pd.clearanceClassIdx();
      int sector = pd.breakoutSector();
      int ring = pd.ring();

      Net net =
          (board.rules != null && board.rules.nets != null && netNo > 0)
              ? board.rules.nets.get(netNo)
              : null;
      NetClass nc = (net != null) ? net.getNetClass() : null;
      int traceHalfWidthPin =
          (nc != null) ? nc.getTraceHalfWidth(pinLayer) : board.rules.getMinAllowedTraceHalfWidth();
      int traceHalfWidthTarget =
          (nc != null)
              ? nc.getTraceHalfWidth(targetLayer)
              : board.rules.getMinAllowedTraceHalfWidth();

      double exitMargin = bga.pitchBoardUnits() * 1.5;
      double p = bga.pitchBoardUnits();

      // Case A: Ring 0 direct perimeter escape on pinLayer (no via)
      if (ring == 0
          && targetLayer == pinLayer
          && pd.disposition() == PinDisposition.SIGNAL_ESCAPE) {
        double localExitU = pd.localU();
        double localExitV = pd.localV();
        if (sector == 0) {
          localExitU = bga.maxX() + exitMargin;
        } else if (sector == 1) {
          localExitV = bga.maxY() + exitMargin;
        } else if (sector == 2) {
          localExitU = bga.minX() - exitMargin;
        } else {
          localExitV = bga.minY() - exitMargin;
        }

        IntPoint globalExit =
            toGlobalPoint(pinCenter, pd.localU(), pd.localV(), localExitU, localExitV, cos, sin);
        double maxAllowed =
            board.checkTraceSegment(
                pinCenter, globalExit, pinLayer, netNumbers, traceHalfWidthPin, clClass, true);
        if (maxAllowed < pinCenter.toFloat().distance(globalExit.toFloat())) {
          drcRejections++;
          continue;
        }

        Polyline polyline = new Polyline(new Point[] {pinCenter, globalExit});
        board.insertTrace(
            polyline, pinLayer, traceHalfWidthPin, netNumbers, clClass, FixedState.USER_FIXED);
        tracesInserted++;
        committed++;
        continue;
      }

      // Case B: Ring 1 2-segment half-pitch dogleg on pinLayer (no via)
      if (ring == 1
          && targetLayer == pinLayer
          && pd.disposition() == PinDisposition.SIGNAL_ESCAPE) {
        double centerU = (bga.minX() + bga.maxX()) / 2.0;
        double centerV = (bga.minY() + bga.maxY()) / 2.0;
        double sgnU = (pd.localU() >= centerU) ? 1.0 : -1.0;
        double sgnV = (pd.localV() >= centerV) ? 1.0 : -1.0;

        double localJogU = pd.localU();
        double localJogV = pd.localV();
        double localExitU = pd.localU();
        double localExitV = pd.localV();

        if (sector == 0) { // East
          localJogU = pd.localU() + p / 2.0;
          localJogV = pd.localV() + sgnV * (p / 2.0);
          localExitU = bga.maxX() + exitMargin;
          localExitV = localJogV;
        } else if (sector == 1) { // North
          localJogU = pd.localU() + sgnU * (p / 2.0);
          localJogV = pd.localV() + p / 2.0;
          localExitU = localJogU;
          localExitV = bga.maxY() + exitMargin;
        } else if (sector == 2) { // West
          localJogU = pd.localU() - p / 2.0;
          localJogV = pd.localV() + sgnV * (p / 2.0);
          localExitU = bga.minX() - exitMargin;
          localExitV = localJogV;
        } else { // South (sector 3)
          localJogU = pd.localU() + sgnU * (p / 2.0);
          localJogV = pd.localV() - p / 2.0;
          localExitU = localJogU;
          localExitV = bga.minY() - exitMargin;
        }

        IntPoint globalJog =
            toGlobalPoint(pinCenter, pd.localU(), pd.localV(), localJogU, localJogV, cos, sin);
        IntPoint globalExit =
            toGlobalPoint(pinCenter, pd.localU(), pd.localV(), localExitU, localExitV, cos, sin);

        double maxAllowed1 =
            board.checkTraceSegment(
                pinCenter, globalJog, pinLayer, netNumbers, traceHalfWidthPin, clClass, true);
        if (maxAllowed1 < pinCenter.toFloat().distance(globalJog.toFloat())) {
          drcRejections++;
          continue;
        }

        double maxAllowed2 =
            board.checkTraceSegment(
                globalJog, globalExit, pinLayer, netNumbers, traceHalfWidthPin, clClass, true);
        if (maxAllowed2 < globalJog.toFloat().distance(globalExit.toFloat())) {
          drcRejections++;
          continue;
        }

        Polyline polyline = new Polyline(new Point[] {pinCenter, globalJog, globalExit});
        board.insertTrace(
            polyline, pinLayer, traceHalfWidthPin, netNumbers, clClass, FixedState.USER_FIXED);
        tracesInserted++;
        committed++;
        continue;
      }

      // Case C: Ring 2+ or via-escaped pins
      if (proposal.viaCenter() == null) {
        drcRejections++;
        continue;
      }

      IntPoint globalViaCenter =
          toGlobalPoint(
              pinCenter,
              pd.localU(),
              pd.localV(),
              proposal.viaCenter().x,
              proposal.viaCenter().y,
              cos,
              sin);

      // Check via clearance across all padstack layers
      long viaKey = (((long) globalViaCenter.x) << 32) | ((long) globalViaCenter.y & 0xFFFFFFFFL);
      boolean isPlaneDrop = (pd.disposition() == PinDisposition.PLANE_DROP);
      boolean viaAlreadyPlaced = isPlaneDrop && placedPlaneVias.containsKey(viaKey);

      if (!viaAlreadyPlaced) {
        boolean viaClear =
            checkViaClearance(board, viaPadstack, globalViaCenter, pin, netNo, netNumbers, clClass);
        if (!viaClear) {
          drcRejections++;
          continue;
        }
      }

      // Check dogbone trace from pinCenter to globalViaCenter on pinLayer
      double maxAllowedDogbone =
          board.checkTraceSegment(
              pinCenter, globalViaCenter, pinLayer, netNumbers, traceHalfWidthPin, clClass, true);
      if (maxAllowedDogbone < pinCenter.toFloat().distance(globalViaCenter.toFloat())) {
        drcRejections++;
        continue;
      }

      // If not PLANE_DROP, check breakout corridor trace on targetLayer
      IntPoint globalCorridor = null;
      IntPoint globalBreakoutExit = null;
      if (!isPlaneDrop) {
        double localCorridorU = pd.localU();
        double localCorridorV = pd.localV();
        double localBreakoutExitU = pd.localU();
        double localBreakoutExitV = pd.localV();

        if (sector == 0) { // East
          localCorridorU = pd.localU() + p;
          localCorridorV = pd.localV();
          localBreakoutExitU = bga.maxX() + exitMargin;
          localBreakoutExitV = pd.localV();
        } else if (sector == 1) { // North
          localCorridorU = pd.localU();
          localCorridorV = pd.localV() + p;
          localBreakoutExitU = pd.localU();
          localBreakoutExitV = bga.maxY() + exitMargin;
        } else if (sector == 2) { // West
          localCorridorU = pd.localU() - p;
          localCorridorV = pd.localV();
          localBreakoutExitU = bga.minX() - exitMargin;
          localBreakoutExitV = pd.localV();
        } else { // South (sector 3)
          localCorridorU = pd.localU();
          localCorridorV = pd.localV() - p;
          localBreakoutExitU = pd.localU();
          localBreakoutExitV = bga.minY() - exitMargin;
        }

        globalCorridor =
            toGlobalPoint(
                pinCenter, pd.localU(), pd.localV(), localCorridorU, localCorridorV, cos, sin);
        globalBreakoutExit =
            toGlobalPoint(
                pinCenter,
                pd.localU(),
                pd.localV(),
                localBreakoutExitU,
                localBreakoutExitV,
                cos,
                sin);

        double maxAllowedJog =
            board.checkTraceSegment(
                globalViaCenter,
                globalCorridor,
                targetLayer,
                netNumbers,
                traceHalfWidthTarget,
                clClass,
                true);
        if (maxAllowedJog < globalViaCenter.toFloat().distance(globalCorridor.toFloat())) {
          drcRejections++;
          continue;
        }

        double maxAllowedCorridor =
            board.checkTraceSegment(
                globalCorridor,
                globalBreakoutExit,
                targetLayer,
                netNumbers,
                traceHalfWidthTarget,
                clClass,
                true);
        if (maxAllowedCorridor < globalCorridor.toFloat().distance(globalBreakoutExit.toFloat())) {
          drcRejections++;
          continue;
        }
      }

      // Commit dogbone trace
      Polyline dogbone = new Polyline(new Point[] {pinCenter, globalViaCenter});
      board.insertTrace(
          dogbone, pinLayer, traceHalfWidthPin, netNumbers, clClass, FixedState.USER_FIXED);
      tracesInserted++;

      // Commit via
      if (!viaAlreadyPlaced) {
        Via insertedVia =
            board.insertVia(
                viaPadstack, globalViaCenter, netNumbers, clClass, FixedState.USER_FIXED, true);
        viasInserted++;
        if (isPlaneDrop && insertedVia != null) {
          placedPlaneVias.put(viaKey, insertedVia);
        }
      }

      // Commit breakout trace on targetLayer
      if (!isPlaneDrop && globalCorridor != null && globalBreakoutExit != null) {
        Polyline breakout =
            new Polyline(new Point[] {globalViaCenter, globalCorridor, globalBreakoutExit});
        board.insertTrace(
            breakout,
            targetLayer,
            traceHalfWidthTarget,
            netNumbers,
            clClass,
            FixedState.USER_FIXED);
        tracesInserted++;
      }

      committed++;
    }

    FRLogger.info(
        "BGA escape committer: committed "
            + committed
            + "/"
            + attempted
            + " escapes ("
            + drcRejections
            + " rejected by DRC)");

    return new CommitResult(attempted, committed, viasInserted, tracesInserted, drcRejections);
  }

  private static IntPoint toGlobalPoint(
      Point pinCenter,
      double localPinU,
      double localPinV,
      double targetU,
      double targetV,
      double cos,
      double sin) {
    double dLocalU = targetU - localPinU;
    double dLocalV = targetV - localPinV;
    double dGlobalX = dLocalU * cos - dLocalV * sin;
    double dGlobalY = dLocalU * sin + dLocalV * cos;
    return new IntPoint(
        (int) Math.round(pinCenter.toFloat().x + dGlobalX),
        (int) Math.round(pinCenter.toFloat().y + dGlobalY));
  }

  private static boolean checkViaClearance(
      RoutingBoard board,
      Padstack viaPadstack,
      IntPoint globalViaCenter,
      Pin pin,
      int netNo,
      int[] netNumbers,
      int clClass) {
    for (int l = viaPadstack.fromLayer(); l <= viaPadstack.toLayer(); l++) {
      ConvexShape shape = viaPadstack.getShape(l);
      if (shape != null) {
        ConvexShape translatedShape =
            (ConvexShape) shape.translateBy(globalViaCenter.differenceBy(Point.ZERO));
        TileShape viaTile;
        if (translatedShape instanceof TileShape ts) {
          viaTile = ts;
        } else {
          viaTile = translatedShape.boundingOctagon();
        }
        Collection<?> obstacles =
            board.overlappingItemsWithClearance(viaTile, l, netNumbers, clClass);
        for (Object obs : obstacles) {
          if (obs instanceof Pin p && p.equals(pin)) {
            continue; // Pin pad is our starting point
          }
          if (obs instanceof app.freerouting.board.model.items.Item it
              && it.isTraceObstacle(netNo)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static Padstack resolveViaPadstack(RoutingBoard board) {
    if (board.rules != null) {
      NetClass defaultNc = board.rules.getDefaultNetClass();
      if (defaultNc != null) {
        ViaRule vr = defaultNc.getViaRule();
        if (vr != null && vr.viaCount() > 0) {
          ViaInfo vi = vr.getVia(0);
          if (vi != null) {
            return vi.getPadstack();
          }
        }
      }
      if (board.rules.viaInfos != null && board.rules.viaInfos.count() > 0) {
        ViaInfo vi = board.rules.viaInfos.get(1);
        if (vi != null) {
          return vi.getPadstack();
        }
      }
    }
    return (board.library != null
            && board.library.padstacks != null
            && board.library.padstacks.count() > 0)
        ? board.library.padstacks.get(1)
        : null;
  }
}
