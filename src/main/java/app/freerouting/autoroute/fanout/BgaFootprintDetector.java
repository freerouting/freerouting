package app.freerouting.autoroute.fanout;

import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.BgaPinDescriptor;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.LatticeClass;
import app.freerouting.autoroute.fanout.BgaEscapeDescriptor.PinDisposition;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects area-array BGA footprints on a PCB and classifies their lattice topology, local
 * coordinate frame, concentric Chebyshev ring depths, and pin routing dispositions.
 */
public final class BgaFootprintDetector {

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

    int minPins = 16;
    if (settings != null && settings.fanout != null && settings.fanout.gpuMinBgaPins != null) {
      minPins = Math.max(16, settings.fanout.gpuMinBgaPins);
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
      List<Pin> pins = smdPinsByComp.get(compId);
      if (pins == null || pins.size() < minPins) {
        continue;
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
    if (pins.isEmpty()) {
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

    // 5. Concentric Chebyshev Ring Peeling
    // Chebyshev distance in (u, v) from outer bounding box
    int maxRing = 0;
    List<BgaPinDescriptor> pinDescriptors = new ArrayList<>(pins.size());

    // Sort pins in canonical order (top-to-bottom, left-to-right in local frame)
    localPoints.sort(
        Comparator.comparingDouble((LocalPoint lp) -> lp.v).thenComparingDouble(lp -> lp.u));

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

      // Net disposition
      PinDisposition disposition = PinDisposition.SIGNAL_ESCAPE;
      int netNo = pin.netCount() > 0 ? pin.getNetNumber(0) : 0;
      if (netNo <= 0 || pin.hasIgnoredNets()) {
        disposition = PinDisposition.PASSIVE_OBSTACLE;
      } else {
        Net net =
            (board.rules != null && board.rules.nets != null) ? board.rules.nets.get(netNo) : null;
        if (net != null && net.containsPlane()) {
          disposition = PinDisposition.PLANE_DROP;
        }
      }

      pinDescriptors.add(
          new BgaPinDescriptor(
              pin, lp.u, lp.v, ring, i, disposition, netNo, pin.clearanceClassIndex()));
    }

    // Convert pitch to micrometers
    double resolution = board.communication.getResolution(Unit.UM);
    double pitchUm = minPitch / resolution;

    return new BgaEscapeDescriptor(
        comp, rotDeg, pitchUm, minPitch, lattice, pinDescriptors, minU, maxU, minV, maxV, maxRing);
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
