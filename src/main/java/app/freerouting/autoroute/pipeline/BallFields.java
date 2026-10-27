package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Erik Gavriluk (freerouting.net) (router.autorouter.ball_field_cost, experiment, off by default):
 * the ball fields of a board's area-array parts (BGAs), each grown by one pitch, on the balls'
 * layer. A trace pays cost times its length inside a field on top of its ordinary cost, so a search
 * leaves a field by the shortest way and does not run along or across it.
 */
public final class BallFields {
  /** The fewest balls a part needs: no QFN, no small array. */
  public static final int MIN_BALLS = 16;

  /** A field: the layer and the box (board units) of the balls' centres grown by one pitch. */
  public record Field(int layer, double x0, double y0, double x1, double y1) {}

  private final List<Field> fields;
  private final double cost;

  public BallFields(List<Field> fields, double cost) {
    this.fields = new ArrayList<>(fields);
    this.cost = cost;
  }

  public double getCost() {
    return cost;
  }

  public List<Field> getFields() {
    return fields;
  }

  /** The ball fields of board's parts, or null when it has none or the cost is off. */
  public static BallFields of(BasicBoard board, double cost) {
    if (!(cost > 0)) {
      return null;
    }
    List<Field> found = new ArrayList<>();
    for (int id = 1; id <= board.components.count(); id++) {
      Collection<Pin> pins = board.getComponentPins(id);
      Field field = fieldOf(pins);
      if (field != null) {
        found.add(field);
      }
    }
    return found.isEmpty() ? null : new BallFields(found, cost);
  }

  /**
   * The field of a part's pins: at least MIN_BALLS single-layer pins on one layer, on an
   * axis-parallel grid of at least three rows and three columns that they fill at least half of (a
   * QFP's or QFN's perimeter rows fill a fifth or less of theirs). Null for any other part.
   */
  public static Field fieldOf(Collection<Pin> pinCollection) {
    if (pinCollection == null || pinCollection.size() < MIN_BALLS) {
      return null;
    }
    List<Pin> pins = new ArrayList<>(pinCollection);
    int layer = pins.get(0).firstLayer();
    List<FloatPoint> centres = new ArrayList<>(pins.size());
    for (Pin pin : pins) {
      if (pin.firstLayer() != layer || pin.lastLayer() != layer) {
        return null;
      }
      centres.add(pin.getCenter().toFloat());
    }
    return fieldOf(centres, layer);
  }

  /** fieldOf on the balls' centres (board units). */
  public static Field fieldOf(List<FloatPoint> centres, int layer) {
    if (centres == null || centres.size() < MIN_BALLS) {
      return null;
    }
    // The pitch: the smallest distance between two balls.
    double pitch = Double.MAX_VALUE;
    for (int i = 0; i < centres.size(); i++) {
      for (int j = i + 1; j < centres.size(); j++) {
        double d = centres.get(i).distance(centres.get(j));
        if (d > 0 && d < pitch) {
          pitch = d;
        }
      }
    }
    if (pitch == Double.MAX_VALUE) {
      return null;
    }
    List<Double> ys = new ArrayList<>(centres.size());
    List<Double> xs = new ArrayList<>(centres.size());
    double minX = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (FloatPoint c : centres) {
      ys.add(c.y);
      xs.add(c.x);
      if (c.x < minX) {
        minX = c.x;
      }
      if (c.x > maxX) {
        maxX = c.x;
      }
      if (c.y < minY) {
        minY = c.y;
      }
      if (c.y > maxY) {
        maxY = c.y;
      }
    }
    int rows = distinct(ys, pitch / 4.0);
    int columns = distinct(xs, pitch / 4.0);
    if (rows < 3 || columns < 3 || centres.size() * 2 < rows * columns) {
      return null;
    }
    return new Field(layer, minX - pitch, minY - pitch, maxX + pitch, maxY + pitch);
  }

  /** The number of distinct values, two values within tolerance counting as one. */
  public static int distinct(List<Double> values, double tolerance) {
    int count = 0;
    double last = Double.NEGATIVE_INFINITY;
    List<Double> sorted = new ArrayList<>(values);
    sorted.sort(Double::compareTo);
    for (double v : sorted) {
      if (v - last > tolerance) {
        count++;
      }
      last = v;
    }
    return count;
  }

  /** What a trace from (fromX, fromY) to (toX, toY) on layer pays inside the fields. */
  public double pathCost(int layer, double fromX, double fromY, double toX, double toY) {
    double inside = 0;
    for (Field field : fields) {
      if (field.layer() == layer) {
        inside += insideLength(field, fromX, fromY, toX, toY);
      }
    }
    return inside * cost;
  }

  /** The length of the segment inside the field's box (Liang-Barsky clipping). */
  public static double insideLength(
      Field field, double fromX, double fromY, double toX, double toY) {
    double dx = toX - fromX;
    double dy = toY - fromY;
    double[] t = new double[] {0.0, 1.0};
    if (!clip(-dx, fromX - field.x0(), t)
        || !clip(dx, field.x1() - fromX, t)
        || !clip(-dy, fromY - field.y0(), t)
        || !clip(dy, field.y1() - fromY, t)) {
      return 0.0;
    }
    return t[1] > t[0] ? (t[1] - t[0]) * Math.sqrt(dx * dx + dy * dy) : 0.0;
  }

  private static boolean clip(double p, double q, double[] t) {
    if (p == 0) {
      return q >= 0;
    }
    double r = q / p;
    if (p < 0) {
      if (r > t[1]) {
        return false;
      }
      if (r > t[0]) {
        t[0] = r;
      }
    } else {
      if (r < t[0]) {
        return false;
      }
      if (r < t[1]) {
        t[1] = r;
      }
    }
    return true;
  }
}
