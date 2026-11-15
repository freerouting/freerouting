package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
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
import java.util.List;

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
 *   <li>Atomically inserts valid traces and vias into the board model.
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

    for (PinEscapeProposal proposal : proposals) {
      if (Thread.currentThread().isInterrupted()) {
        FRLogger.info("BGA escape committer cancelled by interrupt");
        break;
      }

      if (!proposal.escaped() || proposal.viaCenter() == null) {
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

      // Delta from pin local (u, v) to target via in local frame
      double dLocalU = proposal.viaCenter().x - pd.localU();
      double dLocalV = proposal.viaCenter().y - pd.localV();

      // Rotate delta vector R(+θ) into global space and add to pinCenter
      double dGlobalX = dLocalU * cos - dLocalV * sin;
      double dGlobalY = dLocalU * sin + dLocalV * cos;
      IntPoint globalViaCenter =
          new IntPoint(
              (int) Math.round(pinCenter.toFloat().x + dGlobalX),
              (int) Math.round(pinCenter.toFloat().y + dGlobalY));

      int netNo = pd.netNo();
      int[] netNumbers = (netNo > 0) ? new int[] {netNo} : new int[0];
      int pinLayer = pin.firstLayer();
      int targetLayer = proposal.targetLayer();

      Net net =
          (board.rules != null && board.rules.nets != null && netNo > 0)
              ? board.rules.nets.get(netNo)
              : null;
      NetClass nc = (net != null) ? net.getNetClass() : null;
      int traceHalfWidth =
          (nc != null) ? nc.getTraceHalfWidth(pinLayer) : board.rules.getMinAllowedTraceHalfWidth();
      int clClass = pd.clearanceClassIdx();

      // 1. Check if via placement is legal at globalViaCenter
      boolean viaClear = true;
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
              viaClear = false;
              break;
            }
          }
        }
        if (!viaClear) {
          break;
        }
      }

      if (!viaClear) {
        drcRejections++;
        continue;
      }

      // 2. Authoritative DRC check on dog-bone trace segment
      double maxAllowedLength =
          board.checkTraceSegment(
              pinCenter, globalViaCenter, pinLayer, netNumbers, traceHalfWidth, clClass, true);

      double segDist = pinCenter.toFloat().distance(globalViaCenter.toFloat());
      if (maxAllowedLength < segDist) {
        drcRejections++;
        continue;
      }

      // 3. Atomically commit valid escape to board model
      // Mark as USER_FIXED so subsequent maze fanout ripup passes cannot rip them up
      Polyline polyline = new Polyline(new Point[] {pinCenter, globalViaCenter});
      board.insertTrace(
          polyline, pinLayer, traceHalfWidth, netNumbers, clClass, FixedState.USER_FIXED);
      tracesInserted++;

      board.insertVia(
          viaPadstack, globalViaCenter, netNumbers, clClass, FixedState.USER_FIXED, true);
      viasInserted++;

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
