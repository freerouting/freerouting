package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.PinDisposition;
import app.freerouting.autoroute.fanout.BgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.HoleSpacing;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commits BGA escape proposals to the host {@link RoutingBoard}.
 *
 * <p>Enforces continuous design rules checking:
 *
 * <ol>
 *   <li>Transforms local coordinates back to global board space using inverse affine rotation
 *       $R(+\theta)$.
 *   <li>Snaps trace endpoints exactly to {@link Pin#getCenter()} and dogbone via location.
 *   <li>Pre-validates trace clearances via {@link RoutingBoard#checkTraceSegment}.
 *   <li>Pre-validates via placement via {@link DrillItemMover#check} and {@link HoleSpacing#clear}.
 *   <li>Explores 4-candidate interstitial fallback sites when primary via sites are obstructed by
 *       back-side passives.
 *   <li>Computes dynamic exact $45^\circ$ breakout jogs from the chosen via site onto the sector
 *       corridor centerline.
 *   <li>Applies continuous {@code okLength} truncation to outer-perimeter escape rays.
 *   <li>Atomically inserts valid traces and vias into the board model with {@link
 *       FixedState#USER_FIXED} to protect them from premature removal by tail cleanup.
 *   <li>Rolls back all escapes of the package when fewer than half of its pins that need one got
 *       it, so that a partial escape is not fixed on the board and the whole package is left to the
 *       normal fanout.
 *   <li>Checks {@link Thread#isInterrupted()} between commits for clean cancellation.
 * </ol>
 */
public final class BgaEscapeCommitter {

  /**
   * Minimum share of the pins needing an escape that must get one for the escapes of a package to
   * be kept.
   */
  static final double MIN_ESCAPED_PIN_SHARE = 0.5;

  /** Summary result of a BGA escape commit pass. */
  public record CommitResult(
      int pinsAttempted,
      int pinsCommitted,
      int viasInserted,
      int tracesInserted,
      int drcRejections,
      Set<Pin> committedPins) {

    public CommitResult(
        int pinsAttempted,
        int pinsCommitted,
        int viasInserted,
        int tracesInserted,
        int drcRejections) {
      this(
          pinsAttempted,
          pinsCommitted,
          viasInserted,
          tracesInserted,
          drcRejections,
          java.util.Collections.emptySet());
    }
  }

  private BgaEscapeCommitter() {}

  /**
   * Validates and commits the given escape proposals into the board.
   *
   * @param board host routing board
   * @param bga BGA package descriptor
   * @param proposals list of proposed pin escapes from the BGA escape solver
   * @return commit summary
   */
  public static CommitResult commit(
      RoutingBoard board, BgaEscapeDescriptor bga, List<PinEscapeProposal> proposals) {

    if (board == null
        || board.rules == null
        || bga == null
        || proposals == null
        || proposals.isEmpty()) {
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

    // Maintain placed plane vias for deduplication of shared ground/power vias
    record PlaneViaKey(IntPoint location, int netNo) {}
    Map<PlaneViaKey, Via> placedPlaneVias = new HashMap<>();
    // Maintain committed via locations to prevent double-via collisions
    Set<IntPoint> committedViaLocations = new HashSet<>();
    // Maintain successfully escaped pins to bypass redundant CPU fanout retries
    Set<Pin> committedPins = new HashSet<>();

    // Pins connected before any escape is inserted (to another pad of their net, for example) get
    // no escape: they are neither escaped here nor removed from the normal fanout.
    Set<Pin> connectedBefore = new HashSet<>();
    Set<Pin> needingEscape = new LinkedHashSet<>();
    for (PinEscapeProposal proposal : proposals) {
      Pin pin = proposal.pinDescriptor().pin();
      if (pin == null) {
        continue;
      }
      if (pin.getNormalContacts().isEmpty()) {
        needingEscape.add(pin);
      } else {
        connectedBefore.add(pin);
      }
    }
    board.generateSnapshot();

    for (PinEscapeProposal proposal : proposals) {
      if (Thread.currentThread().isInterrupted()) {
        FRLogger.info("BGA escape committer cancelled by interrupt");
        break;
      }

      if (!proposal.escaped()) {
        continue;
      }

      BgaPinDescriptor pd = proposal.pinDescriptor();
      Pin pin = pd.pin();
      if (pin == null || committedPins.contains(pin)) {
        continue;
      }
      if (connectedBefore.contains(pin)) {
        continue; // Pin was connected before the escapes: it gets no escape
      }
      if (!pin.getNormalContacts().isEmpty()) {
        committedPins.add(pin);
        continue; // Pin already connected by the escape of another pin
      }

      attempted++;
      Point pinCenter = pin.getCenter();
      if (pinCenter == null) {
        drcRejections++;
        continue;
      }

      int netNo = pd.netNo();
      int[] netNumbers = (netNo > 0) ? new int[] {netNo} : new int[0];
      int pinLayer = pin.firstLayer();
      int targetLayer = pd.targetLayer();
      int sector = pd.breakoutSector();
      int ring = pd.ring();

      Net net = (board.rules.nets != null && netNo > 0) ? board.rules.nets.get(netNo) : null;
      NetClass nc = (net != null) ? net.getNetClass() : null;
      int traceHalfWidthPin =
          (nc != null) ? nc.getTraceHalfWidth(pinLayer) : board.rules.getMinAllowedTraceHalfWidth();
      int traceHalfWidthTarget =
          (nc != null)
              ? nc.getTraceHalfWidth(targetLayer)
              : board.rules.getMinAllowedTraceHalfWidth();

      int traceClClass =
          (nc != null && nc.defaultItemClearanceClasses != null)
              ? nc.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.TRACE)
              : pd.clearanceClassIdx();
      int viaClClass =
          (nc != null && nc.defaultItemClearanceClasses != null)
              ? nc.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA)
              : pd.clearanceClassIdx();

      double exitMargin = bga.pitchBoardUnits() * 1.5;
      double p = bga.pitchBoardUnits();
      double minPerimeterClearance = p * 0.25;
      double safetyMargin = Math.max(1.0, Math.min(10.0, traceHalfWidthPin * 0.1));

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
                pinCenter,
                globalExit,
                pinLayer,
                netNumbers,
                traceHalfWidthPin,
                traceClClass,
                false);
        double fullDist = pinCenter.toFloat().distance(globalExit.toFloat());

        if (maxAllowed < fullDist) {
          double distToPerimeter = getDistToPerimeter(pd, sector, bga);
          if (maxAllowed >= distToPerimeter + minPerimeterClearance) {
            double safeLen =
                Math.max(distToPerimeter + minPerimeterClearance, maxAllowed - safetyMargin);
            globalExit = interpolatePoint(pinCenter, globalExit, safeLen / fullDist);
          } else {
            drcRejections++;
            continue;
          }
        }

        Polyline polyline = new Polyline(new Point[] {pinCenter, globalExit});
        board.insertTrace(
            polyline, pinLayer, traceHalfWidthPin, netNumbers, traceClClass, FixedState.USER_FIXED);
        if (pin.getNormalContacts().isEmpty()) {
          drcRejections++;
          continue;
        }
        tracesInserted++;
        committed++;
        committedPins.add(pin);
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
                pinCenter, globalJog, pinLayer, netNumbers, traceHalfWidthPin, traceClClass, false);
        if (maxAllowed1 < pinCenter.toFloat().distance(globalJog.toFloat())) {
          drcRejections++;
          continue;
        }

        double maxAllowed2 =
            board.checkTraceSegment(
                globalJog,
                globalExit,
                pinLayer,
                netNumbers,
                traceHalfWidthPin,
                traceClClass,
                false);
        double dist2 = globalJog.toFloat().distance(globalExit.toFloat());

        if (maxAllowed2 < dist2) {
          double distToPerimeter = Math.max(0.0, getDistToPerimeter(pd, sector, bga) - p / 2.0);
          if (maxAllowed2 >= distToPerimeter + minPerimeterClearance) {
            double safeLen =
                Math.max(distToPerimeter + minPerimeterClearance, maxAllowed2 - safetyMargin);
            globalExit = interpolatePoint(globalJog, globalExit, safeLen / dist2);
          } else {
            drcRejections++;
            continue;
          }
        }

        Polyline polyline = new Polyline(new Point[] {pinCenter, globalJog, globalExit});
        board.insertTrace(
            polyline, pinLayer, traceHalfWidthPin, netNumbers, traceClClass, FixedState.USER_FIXED);
        if (pin.getNormalContacts().isEmpty()) {
          drcRejections++;
          continue;
        }
        tracesInserted++;
        committed++;
        committedPins.add(pin);
        continue;
      }

      // Case C: Ring 2+ or via-escaped pins (4-candidate interstitial exploration)
      ResolvedVia resolvedVia = resolveVia(board, pinLayer, targetLayer, nc, viaClClass);
      if (resolvedVia == null) {
        FRLogger.warn(
            "BGA escape committer: no suitable via padstack configured on board for net "
                + netNo
                + " (pinLayer "
                + pinLayer
                + " to targetLayer "
                + targetLayer
                + ")");
        drcRejections++;
        continue;
      }
      Padstack viaPadstack = resolvedVia.padstack();
      int effectiveViaClClass = resolvedVia.clearanceClassIdx();

      int pinU = (int) Math.round(pd.localU());
      int pinV = (int) Math.round(pd.localV());
      int halfPitch = (int) Math.round(p / 2.0);
      double centerU = (bga.minX() + bga.maxX()) / 2.0;
      double centerV = (bga.minY() + bga.maxY()) / 2.0;
      int qU = (pd.localU() >= centerU) ? 1 : -1;
      int qV = (pd.localV() >= centerV) ? 1 : -1;

      int[] dX;
      int[] dY;
      if (sector == 0 || sector == 2) { // East/West: outward axis is U
        dX = new int[] {qU * halfPitch, qU * halfPitch, -qU * halfPitch, -qU * halfPitch};
        dY = new int[] {qV * halfPitch, -qV * halfPitch, qV * halfPitch, -qV * halfPitch};
      } else { // North/South: outward axis is V
        dX = new int[] {qU * halfPitch, -qU * halfPitch, qU * halfPitch, -qU * halfPitch};
        dY = new int[] {qV * halfPitch, qV * halfPitch, -qV * halfPitch, -qV * halfPitch};
      }

      boolean candidateCommitted = false;

      for (int s = 0; s < 4; s++) {
        int viaU = pinU + dX[s];
        int viaV = pinV + dY[s];

        IntPoint globalViaCenter =
            toGlobalPoint(pinCenter, pd.localU(), pd.localV(), viaU, viaV, cos, sin);

        PlaneViaKey viaKey = new PlaneViaKey(globalViaCenter, netNo);
        boolean isPlaneDrop = (pd.disposition() == PinDisposition.PLANE_DROP);
        boolean viaAlreadyPlaced = isPlaneDrop && placedPlaneVias.containsKey(viaKey);

        if (!viaAlreadyPlaced) {
          if (committedViaLocations.contains(globalViaCenter)) {
            continue; // Collides with already committed via site
          }
          boolean viaClear =
              checkViaClearance(
                  board, viaPadstack, globalViaCenter, netNo, netNumbers, effectiveViaClClass);
          if (!viaClear) {
            continue; // Clearance violation or hole spacing violation
          }
        }

        // Validate dogbone trace on pinLayer
        double maxAllowedDogbone =
            board.checkTraceSegment(
                pinCenter,
                globalViaCenter,
                pinLayer,
                netNumbers,
                traceHalfWidthPin,
                traceClClass,
                false);
        if (maxAllowedDogbone < pinCenter.toFloat().distance(globalViaCenter.toFloat())) {
          continue; // Dogbone obstructed on pinLayer
        }

        IntPoint globalCorridor = null;
        IntPoint globalBreakoutExit = null;

        if (!isPlaneDrop) {
          // Recompute exact 45° breakout jog P1 -> P2 onto sector centerline
          int corridorU;
          int corridorV;
          int exitU;
          int exitV;

          if (sector == 0) { // East: centerline is V = pinV
            int step = Math.abs(pinV - viaV);
            corridorU = viaU + step;
            corridorV = pinV;
            exitU = (int) Math.round(bga.maxX() + exitMargin);
            exitV = pinV;
          } else if (sector == 2) { // West: centerline is V = pinV
            int step = Math.abs(pinV - viaV);
            corridorU = viaU - step;
            corridorV = pinV;
            exitU = (int) Math.round(bga.minX() - exitMargin);
            exitV = pinV;
          } else if (sector == 1) { // North: centerline is U = pinU
            int step = Math.abs(pinU - viaU);
            corridorU = pinU;
            corridorV = viaV + step;
            exitU = pinU;
            exitV = (int) Math.round(bga.maxY() + exitMargin);
          } else { // South: centerline is U = pinU
            int step = Math.abs(pinU - viaU);
            corridorU = pinU;
            corridorV = viaV - step;
            exitU = pinU;
            exitV = (int) Math.round(bga.minY() - exitMargin);
          }

          globalCorridor =
              toGlobalPoint(pinCenter, pd.localU(), pd.localV(), corridorU, corridorV, cos, sin);
          globalBreakoutExit =
              toGlobalPoint(pinCenter, pd.localU(), pd.localV(), exitU, exitV, cos, sin);

          // Validate 45° jog on targetLayer
          double maxAllowedJog =
              board.checkTraceSegment(
                  globalViaCenter,
                  globalCorridor,
                  targetLayer,
                  netNumbers,
                  traceHalfWidthTarget,
                  traceClClass,
                  false);
          if (maxAllowedJog < globalViaCenter.toFloat().distance(globalCorridor.toFloat())) {
            continue; // Jog obstructed on targetLayer
          }

          // Validate corridor on targetLayer with continuous okLength truncation
          double maxAllowedCorridor =
              board.checkTraceSegment(
                  globalCorridor,
                  globalBreakoutExit,
                  targetLayer,
                  netNumbers,
                  traceHalfWidthTarget,
                  traceClClass,
                  false);
          double corridorDist = globalCorridor.toFloat().distance(globalBreakoutExit.toFloat());

          if (maxAllowedCorridor < corridorDist) {
            double distToPerimeter = getDistToPerimeter(pd, sector, bga);
            if (maxAllowedCorridor >= distToPerimeter + minPerimeterClearance) {
              double safeLen =
                  Math.max(
                      distToPerimeter + minPerimeterClearance, maxAllowedCorridor - safetyMargin);
              globalBreakoutExit =
                  interpolatePoint(globalCorridor, globalBreakoutExit, safeLen / corridorDist);
            } else {
              continue; // Corridor blocked before exiting perimeter
            }
          }
        }

        // Commit via first so no orphaned traces exist if via placement fails
        Via insertedVia = null;
        if (!viaAlreadyPlaced) {
          insertedVia =
              board.insertVia(
                  viaPadstack,
                  globalViaCenter,
                  netNumbers,
                  effectiveViaClClass,
                  FixedState.USER_FIXED,
                  true);
          if (insertedVia == null) {
            continue;
          }
        }

        // Commit dogbone trace and verify insertion before proceeding
        Polyline dogbone = new Polyline(new Point[] {pinCenter, globalViaCenter});
        board.insertTrace(
            dogbone, pinLayer, traceHalfWidthPin, netNumbers, traceClClass, FixedState.USER_FIXED);
        if (pin.getNormalContacts().isEmpty()) {
          if (insertedVia != null) {
            board.removeItem(insertedVia);
          }
          continue;
        }

        // Commit breakout trace on targetLayer and verify insertion
        boolean breakoutInserted = false;
        if (!isPlaneDrop && globalCorridor != null && globalBreakoutExit != null) {
          int tracesBeforeBreakout = board.getTraces().size();
          Polyline breakout =
              new Polyline(new Point[] {globalViaCenter, globalCorridor, globalBreakoutExit});
          board.insertTrace(
              breakout,
              targetLayer,
              traceHalfWidthTarget,
              netNumbers,
              traceClClass,
              FixedState.USER_FIXED);
          if (board.getTraces().size() <= tracesBeforeBreakout) {
            // Roll back dogbone traces contacting this pin and the newly inserted via
            for (Item contact : List.copyOf(pin.getNormalContacts())) {
              board.removeItem(contact);
            }
            if (insertedVia != null) {
              board.removeItem(insertedVia);
            }
            continue;
          }
          breakoutInserted = true;
        }

        tracesInserted++;
        if (breakoutInserted) {
          tracesInserted++;
        }

        if (insertedVia != null) {
          viasInserted++;
          committedViaLocations.add(globalViaCenter);
          if (isPlaneDrop) {
            placedPlaneVias.put(viaKey, insertedVia);
          }
        }

        committed++;
        committedPins.add(pin);
        candidateCommitted = true;
        break; // Successfully committed this pin!
      }

      if (!candidateCommitted) {
        drcRejections++;
      }
    }

    FRLogger.info(
        "BGA escape committer: committed "
            + committed
            + "/"
            + attempted
            + " escapes ("
            + drcRejections
            + " rejected by DRC)");

    if (!committedPins.isEmpty()
        && committedPins.size() < MIN_ESCAPED_PIN_SHARE * needingEscape.size()) {
      // A partial escape would be fixed on the board and keep the other pins from the normal
      // fanout: remove it and leave the whole package to the normal fanout.
      board.undo(null);
      board.clearTransientAutorouteState();
      FRLogger.info(
          "BGA escape committer: only "
              + committedPins.size()
              + "/"
              + needingEscape.size()
              + " pins of "
              + bga.component().name
              + " escaped; escapes removed, the package is left to the normal fanout");
      return new CommitResult(attempted, 0, 0, 0, drcRejections, java.util.Collections.emptySet());
    }
    board.popSnapshot();

    return new CommitResult(
        attempted, committed, viasInserted, tracesInserted, drcRejections, committedPins);
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

  private static IntPoint interpolatePoint(Point from, Point to, double ratio) {
    FloatPoint f1 = from.toFloat();
    FloatPoint f2 = to.toFloat();
    double clampedRatio = Math.clamp(ratio, 0.0, 1.0);
    double rx = f1.x + (f2.x - f1.x) * clampedRatio;
    double ry = f1.y + (f2.y - f1.y) * clampedRatio;
    return new IntPoint((int) Math.round(rx), (int) Math.round(ry));
  }

  private static double getDistToPerimeter(
      BgaPinDescriptor pd, int sector, BgaEscapeDescriptor bga) {
    if (sector == 0) { // East
      return Math.max(0.0, bga.maxX() - pd.localU());
    } else if (sector == 1) { // North
      return Math.max(0.0, bga.maxY() - pd.localV());
    } else if (sector == 2) { // West
      return Math.max(0.0, pd.localU() - bga.minX());
    } else { // South
      return Math.max(0.0, pd.localV() - bga.minY());
    }
  }

  static boolean checkViaClearance(
      RoutingBoard board,
      Padstack viaPadstack,
      IntPoint globalViaCenter,
      int netNo,
      int[] netNumbers,
      int viaClClass) {
    if (!HoleSpacing.clear(board, viaPadstack, globalViaCenter)) {
      return false;
    }
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
        // Strict no via-in-pad: physical overlap with any Pin (starting ball pad,
        // same-net decoupling cap, or foreign pin) on layer l is strictly rejected
        for (Item it : board.overlappingItems(viaTile, l)) {
          if (it instanceof Pin) {
            return false;
          }
        }

        Collection<?> obstacles =
            board.overlappingItemsWithClearance(viaTile, l, netNumbers, viaClClass);
        for (Object obs : obstacles) {
          if (obs instanceof Pin) {
            return false;
          } else if (obs instanceof Item it && !it.isDrillable(netNo)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private record ResolvedVia(Padstack padstack, int clearanceClassIdx) {}

  private static boolean coversSpan(Padstack ps, int lowL, int highL) {
    return ps != null && ps.fromLayer() <= lowL && ps.toLayer() >= highL;
  }

  private static ResolvedVia resolveVia(
      RoutingBoard board, int fromLayer, int toLayer, NetClass nc, int defaultViaClClass) {
    int lowL = Math.min(fromLayer, toLayer);
    int highL = Math.max(fromLayer, toLayer);

    // 1. Check NetClass via rule for an exact layer-span match (e.g. blind/microvia)
    if (nc != null && nc.getViaRule() != null) {
      ViaRule vr = nc.getViaRule();
      for (int i = 0; i < vr.viaCount(); i++) {
        ViaInfo vi = vr.getVia(i);
        if (vi != null && vi.getPadstack() != null) {
          Padstack ps = vi.getPadstack();
          if (ps.fromLayer() == lowL && ps.toLayer() == highL) {
            return new ResolvedVia(ps, vi.getClearanceClassIndex());
          }
        }
      }
    }

    // 2. Check board.rules.viaInfos for an exact layer-span match
    for (int i = 0; i < board.rules.viaInfos.count(); i++) {
      ViaInfo vi = board.rules.viaInfos.get(i);
      if (vi != null && vi.getPadstack() != null) {
        Padstack ps = vi.getPadstack();
        if (ps.fromLayer() == lowL && ps.toLayer() == highL) {
          return new ResolvedVia(ps, vi.getClearanceClassIndex());
        }
      }
    }

    // 3. Fallback to any via whose layer span covers [lowL, highL]
    NetClass defaultNc = board.rules.getDefaultNetClass();
    if (defaultNc != null) {
      ViaRule vr = defaultNc.getViaRule();
      if (vr != null) {
        for (int i = 0; i < vr.viaCount(); i++) {
          ViaInfo vi = vr.getVia(i);
          if (vi != null && coversSpan(vi.getPadstack(), lowL, highL)) {
            return new ResolvedVia(vi.getPadstack(), vi.getClearanceClassIndex());
          }
        }
      }
    }
    for (int i = 0; i < board.rules.viaInfos.count(); i++) {
      ViaInfo vi = board.rules.viaInfos.get(i);
      if (vi != null && coversSpan(vi.getPadstack(), lowL, highL)) {
        return new ResolvedVia(vi.getPadstack(), vi.getClearanceClassIndex());
      }
    }
    if (board.library != null
        && board.library.padstacks != null
        && board.library.padstacks.count() > 0) {
      Padstack ps = board.library.padstacks.get(1);
      if (coversSpan(ps, lowL, highL)) {
        return new ResolvedVia(ps, defaultViaClClass);
      }
    }
    return null;
  }
}
