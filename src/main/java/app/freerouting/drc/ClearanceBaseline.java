package app.freerouting.drc;

import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable input-board violations for conservative result validation. An exemption requires the
 * same item geometry, nets, layer, and clearance measurements; item IDs may change on SES import.
 * Changed pre-existing copper is deliberately rechecked rather than exempted by a count allowance.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center).
 */
public final class ClearanceBaseline {

  private final Entry[] entries;

  public ClearanceBaseline(Collection<ClearanceViolation> violations) {
    this.entries = capture(violations);
  }

  public int countNewOrChanged(Collection<ClearanceViolation> violations) {
    if (this.entries.length == 0) {
      return violations.size();
    }
    return getNewOrChanged(violations).size();
  }

  /** Violations requiring repair, excluding only exact unchanged input exemptions. */
  public List<ClearanceViolation> getNewOrChanged(Collection<ClearanceViolation> violations) {
    List<ClearanceViolation> result = new ArrayList<>();
    if (this.entries.length == 0) {
      result.addAll(violations);
      return result;
    }
    ClearanceViolation[] currentViolations = violations.toArray(new ClearanceViolation[0]);
    Entry[] currentEntries = capture(violations);
    boolean[] used = new boolean[this.entries.length];
    for (int n = 0; n < currentEntries.length; n++) {
      Entry current = currentEntries[n];
      int match = -1;
      for (int i = 0; i < this.entries.length; i++) {
        if (!used[i] && this.entries[i].matches(current)) {
          match = i;
          break;
        }
      }
      if (match < 0) {
        result.add(currentViolations[n]);
      } else {
        used[match] = true;
      }
    }
    return result;
  }

  private static Entry[] capture(Collection<ClearanceViolation> violations) {
    Map<Item, ItemGeometry> cache = new HashMap<>();
    Entry[] result = new Entry[violations.size()];
    int idx = 0;
    for (ClearanceViolation v : violations) {
      ItemGeometry first =
          v.firstItem != null ? cache.computeIfAbsent(v.firstItem, ItemGeometry::new) : null;
      ItemGeometry second =
          v.secondItem != null ? cache.computeIfAbsent(v.secondItem, ItemGeometry::new) : null;
      result[idx++] = new Entry(first, second, v.layer, v.expectedClearance, v.actualClearance);
    }
    return result;
  }

  private static final class Entry {
    private final ItemGeometry first;
    private final ItemGeometry second;
    private final int layer;
    private final double expected;
    private final double actual;

    Entry(ItemGeometry first, ItemGeometry second, int layer, double expected, double actual) {
      this.first = first;
      this.second = second;
      this.layer = layer;
      this.expected = expected;
      this.actual = actual;
    }

    boolean matches(Entry other) {
      if (other == null) {
        return false;
      }
      if (this.layer != other.layer
          || Double.compare(this.expected, other.expected) != 0
          || Double.compare(this.actual, other.actual) != 0) {
        return false;
      }
      return (matchesItems(this.first, other.first) && matchesItems(this.second, other.second))
          || (matchesItems(this.first, other.second) && matchesItems(this.second, other.first));
    }

    private static boolean matchesItems(ItemGeometry a, ItemGeometry b) {
      if (a == null) {
        return b == null;
      }
      return a.matches(b);
    }
  }

  private static final class ItemGeometry {
    private final String type;
    private final int[] nets;
    private final ShapeGeometry[] shapes;

    ItemGeometry(Item item) {
      this.type = item.getClass().getName();
      this.nets = new int[item.netCount()];
      for (int i = 0; i < this.nets.length; i++) {
        this.nets[i] = item.getNetNumber(i);
      }
      Arrays.sort(this.nets);
      this.shapes = new ShapeGeometry[item.tileShapeCount()];
      for (int i = 0; i < this.shapes.length; i++) {
        this.shapes[i] = new ShapeGeometry(item.shapeLayer(i), item.getTileShape(i));
      }
    }

    boolean matches(ItemGeometry other) {
      if (other == null) {
        return false;
      }
      if (!this.type.equals(other.type) || !Arrays.equals(this.nets, other.nets)) {
        return false;
      }
      if (this.shapes.length != other.shapes.length) {
        return false;
      }
      for (int i = 0; i < this.shapes.length; i++) {
        if (!this.shapes[i].matches(other.shapes[i])) {
          return false;
        }
      }
      return true;
    }
  }

  private static final class ShapeGeometry {
    private final int layer;
    private final Point[] corners;

    ShapeGeometry(int layer, TileShape shape) {
      this.layer = layer;
      if (shape == null) {
        this.corners = null;
      } else {
        int count = shape.borderLineCount();
        this.corners = new Point[count * 2];
        for (int i = 0; i < count; i++) {
          Line border = shape.borderLine(i);
          this.corners[i * 2] = border.a;
          this.corners[i * 2 + 1] = border.b;
        }
        Arrays.sort(this.corners, Point::compareXY);
      }
    }

    boolean matches(ShapeGeometry other) {
      if (other == null || this.layer != other.layer) {
        return false;
      }
      if (this.corners == null) {
        return other.corners == null;
      }
      if (other.corners == null || this.corners.length != other.corners.length) {
        return false;
      }
      for (int i = 0; i < this.corners.length; i++) {
        if (!this.corners[i].equals(other.corners[i])) {
          return false;
        }
      }
      return true;
    }
  }
}
