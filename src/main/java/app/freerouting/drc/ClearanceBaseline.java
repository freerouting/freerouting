package app.freerouting.drc;

import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input-board violations for conservative result validation. An exemption requires the
 * same item geometry, nets, layer and clearance measurements; item IDs may change on SES import.
 * Changed pre-existing copper is deliberately rechecked rather than exempted by a count allowance.
 */
public final class ClearanceBaseline {

  private final Entry[] entries;

  public ClearanceBaseline(Collection<ClearanceViolation> violations) {
    this.entries = capture(violations);
  }

  public int countNewOrChanged(Collection<ClearanceViolation> violations) {
    if (entries.length == 0) {
      return violations.size();
    }
    return getNewOrChanged(violations).size();
  }

  /** Violations requiring repair, excluding only exact unchanged input exemptions. */
  public List<ClearanceViolation> getNewOrChanged(Collection<ClearanceViolation> violations) {
    if (entries.length == 0) {
      return new ArrayList<>(violations);
    }
    List<ClearanceViolation> currentViolations = new ArrayList<>(violations);
    Entry[] currentEntries = capture(currentViolations);
    boolean[] used = new boolean[entries.length];
    List<ClearanceViolation> result = new ArrayList<>();

    for (int n = 0; n < currentEntries.length; n++) {
      Entry current = currentEntries[n];
      int match = -1;
      for (int i = 0; i < entries.length; i++) {
        if (!used[i] && entries[i].matches(current)) {
          match = i;
          break;
        }
      }
      if (match < 0) {
        result.add(currentViolations.get(n));
      } else {
        used[match] = true;
      }
    }
    return result;
  }

  private static Entry[] capture(Collection<ClearanceViolation> violations) {
    Map<Item, ItemGeometry> items = new HashMap<>();
    List<Entry> result = new ArrayList<>(violations.size());
    for (ClearanceViolation v : violations) {
      ItemGeometry first = items.computeIfAbsent(v.firstItem, ItemGeometry::new);
      ItemGeometry second = items.computeIfAbsent(v.secondItem, ItemGeometry::new);
      result.add(new Entry(first, second, v.layer, v.expectedClearance, v.actualClearance));
    }
    return result.toArray(new Entry[0]);
  }

  private static final class Entry {
    final ItemGeometry first;
    final ItemGeometry second;
    final int layer;
    final double expected;
    final double actual;

    Entry(ItemGeometry first, ItemGeometry second, int layer, double expected, double actual) {
      this.first = first;
      this.second = second;
      this.layer = layer;
      this.expected = expected;
      this.actual = actual;
    }

    boolean matches(Entry other) {
      return layer == other.layer
          && Double.compare(expected, other.expected) == 0
          && Double.compare(actual, other.actual) == 0
          && ((first.matches(other.first) && second.matches(other.second))
              || (first.matches(other.second) && second.matches(other.first)));
    }
  }

  private static final class ItemGeometry {
    final String type;
    final int[] nets;
    final ShapeGeometry[] shapes;

    ItemGeometry(Item item) {
      this.type = item.getClass().getName();
      this.nets = item.netNumbers != null ? item.netNumbers.clone() : new int[0];
      Arrays.sort(this.nets);
      int count = item.tileShapeCount();
      this.shapes = new ShapeGeometry[count];
      for (int i = 0; i < count; i++) {
        this.shapes[i] = new ShapeGeometry(item.shapeLayer(i), item.getTileShape(i));
      }
    }

    boolean matches(ItemGeometry other) {
      if (!Objects.equals(type, other.type)
          || !Arrays.equals(nets, other.nets)
          || shapes.length != other.shapes.length) {
        return false;
      }
      for (int i = 0; i < shapes.length; i++) {
        if (!shapes[i].matches(other.shapes[i])) {
          return false;
        }
      }
      return true;
    }
  }

  private static final class ShapeGeometry {
    final int layer;
    final Point[] corners;

    ShapeGeometry(int layer, TileShape shape) {
      this.layer = layer;
      if (shape == null) {
        this.corners = null;
      } else {
        int count = shape.borderLineCount();
        this.corners = new Point[count];
        for (int i = 0; i < count; i++) {
          this.corners[i] = shape.corner(i);
        }
        Arrays.sort(this.corners, Point::compareXY);
      }
    }

    boolean matches(ShapeGeometry other) {
      if (layer != other.layer) {
        return false;
      }
      if (corners == null) {
        return other.corners == null;
      }
      if (other.corners == null || corners.length != other.corners.length) {
        return false;
      }
      for (int i = 0; i < corners.length; i++) {
        if (!corners[i].equals(other.corners[i])) {
          return false;
        }
      }
      return true;
    }
  }
}
