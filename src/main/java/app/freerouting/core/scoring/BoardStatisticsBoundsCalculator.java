package app.freerouting.core.scoring;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
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

    for (int netNumber = 1; netNumber <= board.rules.nets.maxNetNumber(); netNumber++) {
      Net net = board.rules.nets.get(netNumber);
      if (net == null) {
        continue;
      }

      List<Terminal> terminals = getTerminals(board, net);
      if (terminals.size() < 2) {
        continue;
      }

      MstResult mst = calculateMst(terminals);
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
      addSignalLayers(board, pin.firstLayer(), pin.lastLayer(), signalLayers);
    } else if (item instanceof ConductionArea area) {
      IntBox box = area.boundingBox();
      x = (box.ll.x + box.ur.x) / 2.0;
      y = (box.ll.y + box.ur.y) / 2.0;
      addSignalLayer(board, area.getLayer(), signalLayers);
    } else {
      return null;
    }

    return new Terminal(x, y, signalLayers);
  }

  private static void addSignalLayers(
      BasicBoard board, int firstLayer, int lastLayer, Set<Integer> signalLayers) {
    for (int layer = firstLayer; layer <= lastLayer; layer++) {
      addSignalLayer(board, layer, signalLayers);
    }
  }

  private static void addSignalLayer(BasicBoard board, int layer, Set<Integer> signalLayers) {
    if (layer >= 0
        && layer < board.layerStructure.layers.length
        && board.layerStructure.layers[layer].isSignal) {
      signalLayers.add(layer);
    }
  }

  private static MstResult calculateMst(List<Terminal> terminals) {
    boolean[] used = new boolean[terminals.size()];
    double[] distances = new double[terminals.size()];
    int[] parents = new int[terminals.size()];
    Arrays.fill(distances, Double.POSITIVE_INFINITY);
    Arrays.fill(parents, -1);
    distances[0] = 0.0;

    double length = 0.0;
    int bendCount = 0;
    for (int edge = 0; edge < terminals.size(); edge++) {
      int current = -1;
      for (int index = 0; index < terminals.size(); index++) {
        if (!used[index] && (current < 0 || distances[index] < distances[current])) {
          current = index;
        }
      }
      if (current < 0) {
        break;
      }

      used[current] = true;
      if (parents[current] >= 0) {
        Terminal from = terminals.get(parents[current]);
        Terminal to = terminals.get(current);
        length += distances[current];
        if (from.x != to.x && from.y != to.y) {
          bendCount++;
        }
      }

      for (int index = 0; index < terminals.size(); index++) {
        if (!used[index]) {
          double distance = manhattanDistance(terminals.get(current), terminals.get(index));
          if (distance < distances[index]) {
            distances[index] = distance;
            parents[index] = current;
          }
        }
      }
    }
    return new MstResult(length, bendCount);
  }

  private static double manhattanDistance(Terminal first, Terminal second) {
    return Math.abs(first.x - second.x) + Math.abs(first.y - second.y);
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

  private record Terminal(double x, double y, Set<Integer> signalLayers) {}

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
