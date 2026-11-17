package app.freerouting.core.scoring;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Computes board-only lower bounds for optimizer scoring. */
final class BoardStatisticsBoundsCalculator {

  private static final Map<BasicBoard, BoardStatisticsBounds> CACHE = new WeakHashMap<>();

  private BoardStatisticsBoundsCalculator() {}

  static synchronized BoardStatisticsBounds calculate(BasicBoard board) {
    BoardStatisticsBounds cached = CACHE.get(board);
    if (cached != null) {
      return cached;
    }
    BoardStatisticsBounds result = calculateUncached(board);
    CACHE.put(board, result);
    return result;
  }

  private static BoardStatisticsBounds calculateUncached(BasicBoard board) {
    BoardStatisticsBounds result = new BoardStatisticsBounds();
    double minTraceLength = 0.0;
    int minViaCount = 0;
    int minBendCount = 0;
    double boardUnitToMmFactor =
        Unit.scale(1.0, board.communication.unit, Unit.MM)
            / (board.communication.resolution > 0 ? board.communication.resolution : 1);
    AngleRestriction angleRestriction = board.rules.getTraceAngleRestriction();

    for (int netNumber = 1; netNumber <= board.rules.nets.maxNetNumber(); netNumber++) {
      Net net = board.rules.nets.get(netNumber);
      if (net == null) {
        continue;
      }

      List<Terminal> terminals = getTerminals(board, net);
      if (terminals.size() < 2) {
        continue;
      }

      MstResult mst = calculateMst(terminals, angleRestriction);
      minTraceLength += mst.length * boardUnitToMmFactor;
      minBendCount += mst.bendCount;
      minViaCount += calculateMinimumViaCount(net, terminals);
    }

    result.minTraceLengthMm = (float) minTraceLength;
    result.minViaCount = minViaCount;
    result.minBendCount = minBendCount;
    return result;
  }

  private static List<Terminal> getTerminals(BasicBoard board, Net net) {
    List<Terminal> result = new ArrayList<>();
    for (Item item : net.getTerminalItems()) {
      Terminal terminal = toTerminal(board, item);
      if (terminal != null && !terminal.signalLayers.isEmpty()) {
        result.add(terminal);
      }
    }
    return result;
  }

  private static Terminal toTerminal(BasicBoard board, Item item) {
    double x;
    double y;
    Set<Integer> signalLayers = new HashSet<>();

    if (item instanceof Pin pin) {
      FloatPoint center = pin.getCenter().toFloat();
      x = center.x;
      y = center.y;
      for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); layer++) {
        if (pin.getShape(layer - pin.firstLayer()) != null) {
          addSignalLayer(board, layer, signalLayers);
        }
      }
    } else if (item instanceof ConductionArea area) {
      FloatPoint center = area.getArea().getBorder().centreOfGravity();
      x = center.x;
      y = center.y;
      addSignalLayer(board, area.getLayer(), signalLayers);
      return new Terminal(x, y, signalLayers, area.getArea());
    } else {
      return null;
    }

    return new Terminal(x, y, signalLayers, null);
  }

  private static void addSignalLayer(BasicBoard board, int layer, Set<Integer> signalLayers) {
    if (layer >= 0
        && layer < board.layerStructure.layers.length
        && board.layerStructure.layers[layer].isSignal) {
      signalLayers.add(layer);
    }
  }

  /**
   * Returns the length and bend count of a minimum spanning tree over the terminals of a net.
   *
   * <p>Pins are points. All conduction areas of the net form a single node, at distance 0 from the
   * pins they cover: the plane connects them without traces. Distances follow the trace angle
   * restriction (Manhattan for 90 degrees, octilinear for 45 degrees, Euclidean otherwise), so the
   * tree length never exceeds the length of a routing that the rules allow.
   */
  private static MstResult calculateMst(
      List<Terminal> terminals, AngleRestriction angleRestriction) {
    List<Terminal> pins = new ArrayList<>();
    List<Area> areas = new ArrayList<>();
    for (Terminal terminal : terminals) {
      if (terminal.area == null) {
        pins.add(terminal);
      } else {
        areas.add(terminal.area);
      }
    }
    // Node indexes: 0 .. pins.size() - 1 are the pins, pins.size() is the plane, if any.
    int nodeCount = pins.size() + (areas.isEmpty() ? 0 : 1);
    boolean[] used = new boolean[nodeCount];
    double[] distances = new double[nodeCount];
    int[] parents = new int[nodeCount];
    Arrays.fill(distances, Double.POSITIVE_INFINITY);
    Arrays.fill(parents, -1);
    distances[0] = 0.0;

    double length = 0.0;
    int bendCount = 0;
    for (int edge = 0; edge < nodeCount; edge++) {
      int current = -1;
      for (int index = 0; index < nodeCount; index++) {
        if (!used[index] && (current < 0 || distances[index] < distances[current])) {
          current = index;
        }
      }
      if (current < 0) {
        break;
      }

      used[current] = true;
      if (parents[current] >= 0) {
        length += distances[current];
        if (current < pins.size() && parents[current] < pins.size()) {
          Terminal from = pins.get(parents[current]);
          Terminal to = pins.get(current);
          if (from.x != to.x && from.y != to.y) {
            bendCount++;
          }
        }
      }

      for (int index = 0; index < nodeCount; index++) {
        if (!used[index]) {
          double distance;
          if (current < pins.size() && index < pins.size()) {
            distance = distance(pins.get(current), pins.get(index), angleRestriction);
          } else {
            distance = distanceToAreas(pins.get(Math.min(current, index)), areas);
          }
          if (distance < distances[index]) {
            distances[index] = distance;
            parents[index] = current;
          }
        }
      }
    }
    return new MstResult(length, bendCount);
  }

  private static double distance(
      Terminal first, Terminal second, AngleRestriction angleRestriction) {
    double dx = Math.abs(first.x - second.x);
    double dy = Math.abs(first.y - second.y);
    return switch (angleRestriction) {
      case NINETY_DEGREE -> dx + dy;
      case FORTYFIVE_DEGREE -> Math.max(dx, dy) + (Math.sqrt(2) - 1) * Math.min(dx, dy);
      default -> Math.hypot(dx, dy);
    };
  }

  /** Euclidean distance from a pin to the nearest conduction area of its net, 0 inside one. */
  private static double distanceToAreas(Terminal pin, List<Area> areas) {
    FloatPoint point = new FloatPoint(pin.x, pin.y);
    double result = Double.POSITIVE_INFINITY;
    for (Area area : areas) {
      if (area.contains(point)) {
        return 0.0;
      }
      result = Math.min(result, point.distance(area.nearestPointApprox(point)));
    }
    return result;
  }

  private static int calculateMinimumViaCount(Net net, List<Terminal> terminals) {
    LayerGroups groups = new LayerGroups();
    for (Terminal terminal : terminals) {
      groups.addLayers(terminal.signalLayers);
    }
    if (groups.count() <= 1) {
      return 0;
    }

    ViaRule viaRule = net.getNetClass() != null ? net.getNetClass().getViaRule() : null;
    if (viaRule == null) {
      return 0;
    }

    Set<Integer> remainingGroups = groups.roots();
    List<ViaSpan> spans = new ArrayList<>();
    for (int index = 0; index < viaRule.viaCount(); index++) {
      ViaInfo viaInfo = viaRule.getVia(index);
      int firstLayer = viaInfo.getPadstack().fromLayer();
      int lastLayer = viaInfo.getPadstack().toLayer();
      spans.add(new ViaSpan(Math.min(firstLayer, lastLayer), Math.max(firstLayer, lastLayer)));
    }

    int viaCount = 0;
    while (!remainingGroups.isEmpty()) {
      ViaSpan bestSpan = null;
      Set<Integer> bestCoveredGroups = Set.of();
      for (ViaSpan span : spans) {
        Set<Integer> coveredGroups = groups.groupsCoveredBy(span);
        coveredGroups.retainAll(remainingGroups);
        if (coveredGroups.size() > bestCoveredGroups.size()) {
          bestSpan = span;
          bestCoveredGroups = coveredGroups;
        }
      }
      if (bestSpan == null || bestCoveredGroups.isEmpty()) {
        break;
      }
      remainingGroups.removeAll(bestCoveredGroups);
      viaCount++;
    }
    return viaCount;
  }

  /** A pin, or a conduction area when {@code area} is set ({@code x}, {@code y} its centroid). */
  private record Terminal(double x, double y, Set<Integer> signalLayers, Area area) {}

  private record MstResult(double length, int bendCount) {}

  private record ViaSpan(int firstLayer, int lastLayer) {}

  private static final class LayerGroups {
    private final Map<Integer, Integer> parent = new HashMap<>();

    void addLayers(Collection<Integer> layers) {
      Integer firstLayer = null;
      for (int layer : layers) {
        parent.putIfAbsent(layer, layer);
        if (firstLayer == null) {
          firstLayer = layer;
        } else {
          union(firstLayer, layer);
        }
      }
    }

    int count() {
      return roots().size();
    }

    Set<Integer> roots() {
      Set<Integer> roots = new HashSet<>();
      for (int layer : parent.keySet()) {
        roots.add(find(layer));
      }
      return roots;
    }

    Set<Integer> groupsCoveredBy(ViaSpan span) {
      Set<Integer> result = new HashSet<>();
      for (int layer : parent.keySet()) {
        if (layer >= span.firstLayer && layer <= span.lastLayer) {
          result.add(find(layer));
        }
      }
      return result;
    }

    private int find(int layer) {
      int currentParent = parent.get(layer);
      if (currentParent != layer) {
        currentParent = find(currentParent);
        parent.put(layer, currentParent);
      }
      return currentParent;
    }

    private void union(int firstLayer, int secondLayer) {
      int firstRoot = find(firstLayer);
      int secondRoot = find(secondLayer);
      if (firstRoot != secondRoot) {
        parent.put(secondRoot, firstRoot);
      }
    }
  }
}
