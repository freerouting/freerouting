package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erik Gavriluk (freerouting.net) (router.autorouter.ball_escape_order): the order the first pass
 * routes the balls of a ball grid part in, by ring: ring 0 is the grid's outer row and column on
 * each side, ring 1 the balls one pitch further in, and so on. inner_first routes the innermost
 * ring first: the balls with the fewest ways out take theirs while the grid is empty, and the outer
 * balls, which always have the edge, follow.
 */
public final class BallEscapeOrder {

  public static final Set<String> MODES = Set.of("auto", "off", "outer_first", "inner_first");

  private final Map<Integer, Integer> ringByPin;
  private final boolean outerFirst;

  private BallEscapeOrder(Map<Integer, Integer> ringByPin, boolean outerFirst) {
    this.ringByPin = ringByPin;
    this.outerFirst = outerFirst;
  }

  /** The number of balls ordered. */
  public int getBalls() {
    return ringByPin.size();
  }

  /** The ring of the ball with this pin id, or null when it is not a ball of a ball grid part. */
  public Integer getRing(int pinId) {
    return ringByPin.get(pinId);
  }

  /** The setting's value (auto when unset), checked. */
  public static String checkMode(String mode) {
    if (mode == null) {
      mode = "auto";
    }
    if (!MODES.contains(mode)) {
      throw new IllegalArgumentException(
          "router.autorouter.ball_escape_order must be auto, off, outer_first or inner_first");
    }
    return mode;
  }

  /** The order for board's ball grid parts, or null when the mode is off or there are none. */
  public static BallEscapeOrder of(BasicBoard board, String mode) {
    mode = checkMode(mode);
    if ("auto".equals(mode)) {
      if (board == null) {
        return null;
      }
      mode = board.layerStructure.signalLayerCount() == 2 ? "inner_first" : "off";
    }
    if ("off".equals(mode) || board == null) {
      return null;
    }
    // One walk over the board's pins, grouped by part in the board's item order
    Map<Integer, List<Pin>> byPart = new HashMap<>();
    for (Pin pin : board.getPins()) {
      int part = pin.getComponentId();
      if (part <= 0) {
        continue;
      }
      byPart.computeIfAbsent(part, k -> new ArrayList<>()).add(pin);
    }
    Map<Integer, Integer> rings = new HashMap<>();
    for (List<Pin> pins : byPart.values()) {
      if (pins.size() < BallFields.MIN_BALLS || !TwoLayerBga.isBallGrid(pins)) {
        continue;
      }
      List<FloatPoint> centres = new ArrayList<>(pins.size());
      for (Pin p : pins) {
        centres.add(p.getCenter().toFloat());
      }
      int[] ring = rings(centres);
      for (int i = 0; i < pins.size(); i++) {
        rings.put(pins.get(i).getId(), ring[i]);
      }
    }
    return rings.isEmpty() ? null : new BallEscapeOrder(rings, "outer_first".equals(mode));
  }

  /**
   * Each ball's ring on an axis-parallel grid: its distance to the nearest outermost row or column
   * of balls, in pitches (the smallest distance between two balls), rounded.
   */
  public static int[] rings(List<FloatPoint> centres) {
    double pitch = Double.MAX_VALUE;
    for (int i = 0; i < centres.size(); i++) {
      for (int j = i + 1; j < centres.size(); j++) {
        double d = centres.get(i).distance(centres.get(j));
        if (d > 0 && d < pitch) {
          pitch = d;
        }
      }
    }
    double x0 = Double.MAX_VALUE;
    double x1 = -Double.MAX_VALUE;
    double y0 = Double.MAX_VALUE;
    double y1 = -Double.MAX_VALUE;
    for (FloatPoint c : centres) {
      if (c.x < x0) {
        x0 = c.x;
      }
      if (c.x > x1) {
        x1 = c.x;
      }
      if (c.y < y0) {
        y0 = c.y;
      }
      if (c.y > y1) {
        y1 = c.y;
      }
    }
    int[] result = new int[centres.size()];
    for (int i = 0; i < centres.size(); i++) {
      FloatPoint c = centres.get(i);
      double edge = Math.min(Math.min(c.x - x0, x1 - c.x), Math.min(c.y - y0, y1 - c.y));
      result[i] = pitch == Double.MAX_VALUE ? 0 : (int) Math.round(edge / pitch);
    }
    return result;
  }

  private record Entry(int ringKey, int position, Item item) {}

  /**
   * Moves the balls among items to the front, ring by ring in the mode's order, each ring in the
   * items' own order; the other items follow in theirs. Returns the number of balls moved.
   */
  public int moveToFront(List<Item> items) {
    List<Entry> front = new ArrayList<>();
    List<Item> rest = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      Integer ring = (item instanceof Pin pin) ? getRing(pin.getId()) : null;
      if (ring != null) {
        int ringKey = outerFirst ? ring : -ring;
        front.add(new Entry(ringKey, i, item));
      } else {
        rest.add(item);
      }
    }
    if (front.isEmpty()) {
      return 0;
    }
    front.sort(Comparator.comparingInt(Entry::ringKey).thenComparingInt(Entry::position));
    items.clear();
    for (Entry e : front) {
      items.add(e.item());
    }
    items.addAll(rest);
    return front.size();
  }
}
