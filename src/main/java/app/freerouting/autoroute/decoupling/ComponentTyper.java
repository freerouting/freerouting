package app.freerouting.autoroute.decoupling;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Classifies a component into structural topological categories based on its pin count, pad
 * geometry, and layer placement.
 */
public final class ComponentTyper {

  private ComponentTyper() {}

  /** Result of typing a component, including any soft topological flags. */
  public record TypingResult(ComponentType type, boolean isXtalLike) {}

  /**
   * Types a component given its ordered list of pins.
   *
   * @param pins all pins belonging to the component
   * @return the classified {@link ComponentType} and secondary topological flags
   */
  public static TypingResult classify(List<Pin> pins) {
    if (pins == null || pins.isEmpty()) {
      return new TypingResult(ComponentType.OTHER, false);
    }

    int pinCount = pins.size();

    // 1. Check for TWO_PIN_SMD
    if (pinCount == 2) {
      Pin p0 = pins.get(0);
      Pin p1 = pins.get(1);
      boolean isP0Smd = p0.firstLayer() == p0.lastLayer();
      boolean isP1Smd = p1.firstLayer() == p1.lastLayer();
      boolean sameLayer = p0.firstLayer() == p1.firstLayer();
      boolean validNets = p0.netCount() == 1 && p1.netCount() == 1;

      if (isP0Smd && isP1Smd && sameLayer && validNets) {
        return new TypingResult(ComponentType.TWO_PIN_SMD, false);
      }
      return new TypingResult(ComponentType.OTHER, false);
    }

    if (pinCount < 2) {
      return new TypingResult(ComponentType.OTHER, false);
    }

    // 2. Count SMD vs THT and collect distinct nets
    int smdCount = 0;
    Set<Integer> distinctNets = new HashSet<>();
    int[] netOccurrences = new int[pinCount];
    int maxNetRepeat = 0;

    for (Pin pin : pins) {
      if (pin.firstLayer() == pin.lastLayer()) {
        smdCount++;
      }
      for (int i = 0; i < pin.netCount(); i++) {
        int net = pin.getNetNumber(i);
        if (net > 0) {
          distinctNets.add(net);
        }
      }
    }

    // Check for XTAL_LIKE soft flag: pinCount == 4, all pad bboxes identical, <= 3 distinct nets,
    // >= 2 pins on one net
    boolean isXtalLike = false;
    if (pinCount == 4 && distinctNets.size() <= 3 && arePadBoxesIdentical(pins)) {
      // Check if at least 2 pins share a net
      for (int net : distinctNets) {
        int count = 0;
        for (Pin pin : pins) {
          for (int i = 0; i < pin.netCount(); i++) {
            if (pin.getNetNumber(i) == net) {
              count++;
            }
          }
        }
        if (count >= 2) {
          isXtalLike = true;
          break;
        }
      }
    }

    // 3. Check for CONNECTOR_LIKE
    // Condition A: Pin count >= 3 and mostly through-hole (< 50% SMD)
    boolean mostlyThroughHole = (smdCount * 2) < pinCount;

    // Condition B: Pin count >= 3, all pad bboxes identical, and all pin centers collinear
    boolean isCollinearHeader = arePadBoxesIdentical(pins) && arePinCentersCollinear(pins);

    if (mostlyThroughHole || isCollinearHeader) {
      return new TypingResult(ComponentType.CONNECTOR_LIKE, isXtalLike);
    }

    // 4. Check for IC: pinCount >= 3, at least 2 distinct nets, SMD fraction >= 50%
    if (distinctNets.size() >= 2 && (smdCount * 2) >= pinCount) {
      return new TypingResult(ComponentType.IC, isXtalLike);
    }

    return new TypingResult(ComponentType.OTHER, isXtalLike);
  }

  private static boolean arePadBoxesIdentical(List<Pin> pins) {
    if (pins.isEmpty()) {
      return false;
    }
    IntBox firstBox = pins.get(0).boundingBox();
    if (firstBox == null) {
      return false;
    }
    int w0 = firstBox.width();
    int h0 = firstBox.height();

    for (int i = 1; i < pins.size(); i++) {
      IntBox box = pins.get(i).boundingBox();
      if (box == null || box.width() != w0 || box.height() != h0) {
        return false;
      }
    }
    return true;
  }

  private static boolean arePinCentersCollinear(List<Pin> pins) {
    if (pins.size() <= 2) {
      return true;
    }
    Point p0 = pins.get(0).getCenter();
    Point p1 = pins.get(1).getCenter();
    if (p0 == null || p1 == null) {
      return false;
    }

    // Find the first distinct point from p0 to establish line direction
    int secondIdx = 1;
    while (secondIdx < pins.size() && pins.get(secondIdx).getCenter().equals(p0)) {
      secondIdx++;
    }
    if (secondIdx >= pins.size()) {
      return true; // All centers coincident
    }
    Point pDir = pins.get(secondIdx).getCenter();
    long dx0 = getX(pDir) - getX(p0);
    long dy0 = getY(pDir) - getY(p0);

    for (int i = secondIdx + 1; i < pins.size(); i++) {
      Point pi = pins.get(i).getCenter();
      if (pi == null) {
        return false;
      }
      long dxi = getX(pi) - getX(p0);
      long dyi = getY(pi) - getY(p0);
      // 2D cross product: dx0 * dyi - dy0 * dxi == 0
      if ((dx0 * dyi - dy0 * dxi) != 0) {
        return false;
      }
    }
    return true;
  }

  private static long getX(Point p) {
    if (p instanceof IntPoint ip) {
      return ip.x;
    }
    return Math.round(p.toFloat().x);
  }

  private static long getY(Point p) {
    if (p instanceof IntPoint ip) {
      return ip.y;
    }
    return Math.round(p.toFloat().y);
  }
}
