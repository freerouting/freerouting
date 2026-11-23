package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.LatticeClass;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.PinDisposition;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.NetClass;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects area-array BGA footprints on a PCB and classifies their lattice topology, local
 * coordinate frame, concentric Chebyshev ring depths, and pin routing dispositions.
 */
public final class BgaFootprintDetector {

  /**
   * Minimum share of the pins of a BGA that must use the same padstack. The balls of a BGA all have
   * the same pad; an SOIC or a QFN with an exposed pad has its leads plus one or more larger pads.
   */
  static final double MIN_COMMON_PADSTACK_SHARE = 0.9;

  /**
   * Maximum ratio between the long and the short side of the bounding box of the common padstack. A
   * ball pad is round or square; the lead pads of an SOIC or a QFN are about twice as long as they
   * are wide.
   */
  static final double MAX_BALL_PAD_ASPECT_RATIO = 1.2;

  private BgaFootprintDetector() {}

  /**
   * Scans the board's components and returns descriptors for all components that qualify as BGAs.
   *
   * @param board routing board
   * @param settings router settings
   * @return list of detected BGA escape descriptors
   */
  public static List<BgaEscapeDescriptor> detect(RoutingBoard board, RouterSettings settings) {
    return detect(board, settings, null);
  }

  /**
   * Scans the board's components and returns descriptors for all components that qualify as BGAs,
   * reporting detection messages to an optional info logger.
   *
   * @param board routing board
   * @param settings router settings
   * @param infoLogger optional logger callback for UI/job messaging
   * @return list of detected BGA escape descriptors
   */
  public static List<BgaEscapeDescriptor> detect(
      RoutingBoard board, RouterSettings settings, java.util.function.Consumer<String> infoLogger) {
    if (board == null || board.components == null) {
      return Collections.emptyList();
    }

    int minPins = 9;
    if (settings != null && settings.fanout != null && settings.fanout.bgaEscapeMinPins != null) {
      minPins = Math.max(9, settings.fanout.bgaEscapeMinPins);
    }

    // Group board SMD pins by component
    Map<Integer, List<Pin>> smdPinsByComp = new HashMap<>();

    for (Pin pin : board.getSmdPins()) {
      int compId = pin.getComponentId();
      if (compId <= 0) {
        continue;
      }
      smdPinsByComp.computeIfAbsent(compId, _ -> new ArrayList<>()).add(pin);
    }

    List<BgaEscapeDescriptor> result = new ArrayList<>();
    for (int compId = 1; compId <= board.components.count(); compId++) {
      Component comp = board.components.get(compId);
      if (comp == null) {
        continue;
      }
      List<Pin> pins = onePadPerPin(smdPinsByComp.get(compId));
      if (pins.size() < minPins) {
        continue;
      }

      int unescapedCount = 0;
      for (Pin p : pins) {
        if (p.netCount() > 0 && !p.hasIgnoredNets() && p.getNormalContacts().isEmpty()) {
          unescapedCount++;
        }
      }
      if (unescapedCount == 0) {
        continue; // Component is already fully fanned out / escaped
      }

      BgaEscapeDescriptor descriptor = analyzeComponent(board, comp, pins, settings);
      if (descriptor != null) {
        result.add(descriptor);
        String msg =
            "BGA detector: identified component '"
                + comp.name
                + "' ("
                + pins.size()
                + " pins, pitch="
                + String.format(java.util.Locale.US, "%.2f", descriptor.pitchUm())
                + " um, lattice="
                + descriptor.latticeClass()
                + ", rings="
                + (descriptor.maxRing() + 1)
                + ")";
        FRLogger.info(msg);
        if (infoLogger != null) {
          infoLogger.accept(msg);
        }
      }
    }

    if (result.isEmpty() && !smdPinsByComp.isEmpty()) {
      String msg =
          "BGA detector: evaluated "
              + smdPinsByComp.size()
              + " SMD component(s); none met the BGA area-array threshold (minPins="
              + minPins
              + ").";
      FRLogger.info(msg);
      if (infoLogger != null) {
        infoLogger.accept(msg);
      }
    }

    return result;
  }

  /**
   * Analyzes an SMD component to determine if it is an area-array BGA and computes its descriptor.
   */
  public static BgaEscapeDescriptor analyzeComponent(
      RoutingBoard board, Component comp, List<Pin> pins, RouterSettings settings) {
    if (pins.isEmpty() || !hasUniformBallPads(pins)) {
      return null;
    }

    // 1. Compute Centroid
    double sumX = 0;
    double sumY = 0;
    for (Pin p : pins) {
      FloatPoint c = p.getCenter().toFloat();
      sumX += c.x;
      sumY += c.y;
    }
    double centroidX = sumX / pins.size();
    double centroidY = sumY / pins.size();

    // 2. Local frame rotation R(-θ)
    double rotDeg = comp.getRotationInDegree();
    double rad = Math.toRadians(-rotDeg);
    double cos = Math.cos(rad);
    double sin = Math.sin(rad);

    record LocalPoint(Pin pin, double u, double v) {}
    List<LocalPoint> localPoints = new ArrayList<>(pins.size());

    double minU = Double.MAX_VALUE;
    double maxU = -Double.MAX_VALUE;
    double minV = Double.MAX_VALUE;
    double maxV = -Double.MAX_VALUE;

    for (Pin p : pins) {
      FloatPoint c = p.getCenter().toFloat();
      double dx = c.x - centroidX;
      double dy = c.y - centroidY;
      double u = dx * cos - dy * sin;
      double v = dx * sin + dy * cos;

      if (u < minU) minU = u;
      if (u > maxU) maxU = u;
      if (v < minV) minV = v;
      if (v > maxV) maxV = v;

      localPoints.add(new LocalPoint(p, u, v));
    }

    // 3. Compute pitch: nearest-neighbor distances in (u, v)
    double minPitch = Double.MAX_VALUE;
    for (int i = 0; i < localPoints.size(); i++) {
      LocalPoint pi = localPoints.get(i);
      for (int j = i + 1; j < localPoints.size(); j++) {
        LocalPoint pj = localPoints.get(j);
        double du = Math.abs(pi.u - pj.u);
        double dv = Math.abs(pi.v - pj.v);
        double dist = Math.hypot(du, dv);
        if (dist > 1.0 && dist < minPitch) {
          minPitch = dist;
        }
      }
    }

    if (minPitch == Double.MAX_VALUE || minPitch <= 0) {
      return null;
    }

    // Enforce supported BGA pitch limit (up to 1.27 mm = 1270 um)
    double resolution = board.communication.getResolution(Unit.UM);
    double pitchUm = minPitch / resolution;
    if (pitchUm > 1270.0) {
      return null;
    }

    double pitchTol = minPitch * 0.35;
    List<Double> uCoords = new ArrayList<>(pins.size());
    List<Double> vCoords = new ArrayList<>(pins.size());
    for (LocalPoint lp : localPoints) {
      uCoords.add(lp.u);
      vCoords.add(lp.v);
    }
    int cols = countDistinct(uCoords, pitchTol);
    int rows = countDistinct(vCoords, pitchTol);

    if (rows < 3 || cols < 3) {
      return null;
    }

    // Interior pin ratio check (area-array vs perimeter-only QFP)
    // A perimeter ring has 2*(rows + cols - 2) pins. Interior pins must be >= 25%
    int perimeterSlots = Math.max(1, 2 * (rows + cols - 2));
    int interiorCount = Math.max(0, pins.size() - perimeterSlots);
    double interiorRatio = (double) interiorCount / (double) pins.size();

    // Distinguish dense area array from perimeter QFPs
    if (interiorRatio < 0.20 && pins.size() < (rows * cols * 0.5)) {
      return null;
    }

    // 4. Lattice classification
    LatticeClass lattice = LatticeClass.ORTHOGONAL_GRID;
    if (pins.size() < (rows * cols * 0.85)) {
      lattice = LatticeClass.DEPOPULATED_CAVITY_GRID;
    }

    // 5. Concentric Chebyshev Ring Peeling & Layer Allocation
    int pinLayer = pins.isEmpty() ? 0 : pins.get(0).firstLayer();

    // Compute component bounding box in board coordinates
    int minBx = Integer.MAX_VALUE;
    int maxBx = Integer.MIN_VALUE;
    int minBy = Integer.MAX_VALUE;
    int maxBy = Integer.MIN_VALUE;
    for (Pin p : pins) {
      Point pt = p.getCenter();
      if (pt != null) {
        IntPoint ip = pt.toFloat().round();
        if (ip.x < minBx) minBx = ip.x;
        if (ip.x > maxBx) maxBx = ip.x;
        if (ip.y < minBy) minBy = ip.y;
        if (ip.y > maxBy) maxBy = ip.y;
      }
    }
    IntBox bgaBox = new IntBox(new IntPoint(minBx, minBy), new IntPoint(maxBx, maxBy));

    // Identify inner layers containing a BGA-spanning ConductionArea (solid pour)
    Set<Integer> pouredLayers = new HashSet<>();
    if (board.getConductionAreas() != null) {
      for (ConductionArea ca : board.getConductionAreas()) {
        int l = ca.getLayer();
        if (l != pinLayer && ca.getArea() != null) {
          IntBox caBox = ca.boundingBox();
          if (caBox != null && caBox.intersects(bgaBox)) {
            IntBox intersection = caBox.intersection(bgaBox);
            double interArea =
                (double) (intersection.ur.x - intersection.ll.x)
                    * (intersection.ur.y - intersection.ll.y);
            double bgaArea = (double) (bgaBox.ur.x - bgaBox.ll.x) * (bgaBox.ur.y - bgaBox.ll.y);
            if (caBox.contains(bgaBox) || (bgaArea > 0 && interArea / bgaArea >= 0.5)) {
              pouredLayers.add(l);
            }
          }
        }
      }
    }

    List<Integer> signalLayers = new ArrayList<>();
    if (pinLayer == 0) {
      for (int l = 0; l < board.getLayerCount(); l++) {
        boolean layerActive =
            (settings == null || l >= settings.getLayerCount() || settings.getLayerActive(l));
        if (board.layerStructure.layers[l].isSignal && layerActive && !pouredLayers.contains(l)) {
          signalLayers.add(l);
        }
      }
    } else {
      for (int l = board.getLayerCount() - 1; l >= 0; l--) {
        boolean layerActive =
            (settings == null || l >= settings.getLayerCount() || settings.getLayerActive(l));
        if (board.layerStructure.layers[l].isSignal && layerActive && !pouredLayers.contains(l)) {
          signalLayers.add(l);
        }
      }
    }
    if (signalLayers.isEmpty()) {
      signalLayers.add(pinLayer);
    }

    // Determine if surface escape channels between Ring 0 pads can fit a trace + 2 * clearance
    Shape sampleShape = pins.isEmpty() ? null : pins.get(0).getShape(0);
    double padRadius = (sampleShape != null) ? sampleShape.smallestRadius() : (minPitch * 0.25);
    double padDiameter = 2.0 * padRadius;
    double padGap = minPitch - padDiameter;

    NetClass defaultNc = (board.rules != null) ? board.rules.getDefaultNetClass() : null;
    int traceWidth =
        (defaultNc != null)
            ? defaultNc.getTraceHalfWidth(pinLayer) * 2
            : (board.rules != null ? board.rules.getMinAllowedTraceHalfWidth() * 2 : 100);
    int clearance =
        (board.rules != null && board.rules.clearanceMatrix != null)
            ? board.rules.clearanceMatrix.getValue(0, 0, pinLayer, false)
            : 100;
    boolean surfaceChannelsFit = padGap >= (traceWidth + 2 * clearance);

    // Pre-index ConductionAreas overlapping the BGA footprint by net number
    Map<Integer, List<ConductionArea>> conductionAreasByNet = new HashMap<>();
    if (board.getConductionAreas() != null) {
      for (ConductionArea ca : board.getConductionAreas()) {
        if (ca.getArea() != null) {
          IntBox caBox = ca.boundingBox();
          if (caBox != null && caBox.intersects(bgaBox)) {
            for (int n = 0; n < ca.netCount(); n++) {
              int netNum = ca.getNetNumber(n);
              if (netNum > 0) {
                conductionAreasByNet.computeIfAbsent(netNum, _ -> new ArrayList<>()).add(ca);
              }
            }
          }
        }
      }
    }

    // Chebyshev distance in (u, v) from outer bounding box
    int maxRing = 0;
    List<BgaPinDescriptor> pinDescriptors = new ArrayList<>(pins.size());

    // Sort pins in canonical order (top-to-bottom, left-to-right in local frame)
    localPoints.sort(
        Comparator.comparingDouble((LocalPoint lp) -> lp.v).thenComparingDouble(lp -> lp.u));

    double centerU = (minU + maxU) / 2.0;
    double centerV = (minV + maxV) / 2.0;

    for (int i = 0; i < localPoints.size(); i++) {
      LocalPoint lp = localPoints.get(i);
      Pin pin = lp.pin;

      double distLeft = Math.abs(lp.u - minU);
      double distRight = Math.abs(maxU - lp.u);
      double distBottom = Math.abs(lp.v - minV);
      double distTop = Math.abs(maxV - lp.v);

      double minEdgeDist = Math.min(Math.min(distLeft, distRight), Math.min(distBottom, distTop));
      int ring = (int) Math.round(minEdgeDist / minPitch);
      if (ring > maxRing) {
        maxRing = ring;
      }

      // Net disposition: classify as PLANE_DROP only when a matching ConductionArea covers this pin
      PinDisposition disposition = PinDisposition.SIGNAL_ESCAPE;
      int planeTargetLayer = -1;
      int netNo = pin.netCount() > 0 ? pin.getNetNumber(0) : 0;
      if (netNo <= 0 || pin.hasIgnoredNets()) {
        disposition = PinDisposition.PASSIVE_OBSTACLE;
      } else {
        List<ConductionArea> netAreas = conductionAreasByNet.get(netNo);
        Point pinCenter = pin.getCenter();
        if (netAreas != null && pinCenter != null) {
          FloatPoint fp = pinCenter.toFloat();
          int bestDist = Integer.MAX_VALUE;
          for (ConductionArea ca : netAreas) {
            if (ca.getArea().contains(fp)) {
              int caLayer = ca.getLayer();
              int layerDist =
                  (caLayer == pinLayer) ? Integer.MAX_VALUE - 1 : Math.abs(caLayer - pinLayer);
              if (layerDist < bestDist) {
                bestDist = layerDist;
                planeTargetLayer = caLayer;
              }
            }
          }
        }
        if (planeTargetLayer >= 0) {
          disposition = PinDisposition.PLANE_DROP;
        }
      }

      // Partition breakout direction into 4 triangular edge-sectors using diagonals
      double deltaU = lp.u - centerU;
      double deltaV = lp.v - centerV;
      int sector;
      if (Math.abs(deltaU) > Math.abs(deltaV)) {
        sector = (deltaU >= 0) ? 0 : 2; // East (0), West (2)
      } else {
        sector = (deltaV >= 0) ? 1 : 3; // North (1), South (3)
      }

      // Assign target routing layer based on ring depth and surface channel capacity
      int targetLayer;
      if (disposition == PinDisposition.PLANE_DROP) {
        targetLayer = planeTargetLayer;
      } else if (surfaceChannelsFit) {
        if (ring == 0 || ring == 1) {
          targetLayer = pinLayer;
        } else {
          int layerIdx = Math.min(ring - 1, signalLayers.size() - 1);
          targetLayer = signalLayers.get(layerIdx);
        }
      } else {
        if (ring == 0) {
          targetLayer = pinLayer;
        } else {
          int layerIdx = Math.min(ring, signalLayers.size() - 1);
          targetLayer = signalLayers.get(layerIdx);
        }
      }

      pinDescriptors.add(
          new BgaPinDescriptor(
              pin,
              lp.u,
              lp.v,
              ring,
              i,
              disposition,
              netNo,
              pin.clearanceClassIndex(),
              targetLayer,
              sector));
    }

    return new BgaEscapeDescriptor(
        comp, rotDeg, pitchUm, minPitch, lattice, pinDescriptors, minU, maxU, minV, maxV, maxRing);
  }

  /**
   * Keeps one pad for each pin of the component. In Specctra, the pads of one pin of a footprint
   * (an exposed pad with its thermal vias, for example) are named after the pin with a suffix
   * {@code @1}, {@code @2}... They are one ball position at most, not a grid of their own. The pad
   * kept is the first one of the pin in the package.
   */
  static List<Pin> onePadPerPin(List<Pin> pads) {
    if (pads == null) {
      return Collections.emptyList();
    }
    Map<String, Pin> firstPadOfPin = new LinkedHashMap<>();
    for (Pin pad : pads) {
      firstPadOfPin.merge(
          pinNameWithoutPadSuffix(pad.name()),
          pad,
          (kept, other) -> other.getPinIndex() < kept.getPinIndex() ? other : kept);
    }
    return new ArrayList<>(firstPadOfPin.values());
  }

  private static String pinNameWithoutPadSuffix(String padName) {
    if (padName == null) {
      return "";
    }
    int suffix = padName.indexOf('@');
    return suffix > 0 ? padName.substring(0, suffix) : padName;
  }

  /**
   * Returns true if at least {@link #MIN_COMMON_PADSTACK_SHARE} of the pins use the same padstack
   * and that padstack is round or square ({@link #MAX_BALL_PAD_ASPECT_RATIO}).
   */
  static boolean hasUniformBallPads(List<Pin> pins) {
    Map<Padstack, Integer> pinsByPadstack = new LinkedHashMap<>();
    Map<Padstack, Pin> samplePin = new HashMap<>();
    for (Pin pin : pins) {
      Padstack padstack = pin.getPadstack();
      if (padstack == null) {
        return false;
      }
      pinsByPadstack.merge(padstack, 1, Integer::sum);
      samplePin.putIfAbsent(padstack, pin);
    }
    Padstack common = null;
    int commonCount = 0;
    for (Map.Entry<Padstack, Integer> entry : pinsByPadstack.entrySet()) {
      if (entry.getValue() > commonCount) {
        common = entry.getKey();
        commonCount = entry.getValue();
      }
    }
    if (common == null || commonCount < MIN_COMMON_PADSTACK_SHARE * pins.size()) {
      return false;
    }
    Shape padShape = common.getShape(samplePin.get(common).firstLayer());
    if (padShape == null) {
      return false;
    }
    IntBox box = padShape.boundingBox();
    double shortSide = Math.min(box.width(), box.height());
    double longSide = Math.max(box.width(), box.height());
    return shortSide > 0 && longSide <= MAX_BALL_PAD_ASPECT_RATIO * shortSide;
  }

  private static int countDistinct(List<Double> values, double tolerance) {
    if (values == null || values.isEmpty()) {
      return 0;
    }
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int count = 1;
    double current = sorted.get(0);
    for (int i = 1; i < sorted.size(); i++) {
      if (sorted.get(i) - current > tolerance) {
        count++;
        current = sorted.get(i);
      }
    }
    return count;
  }
}
