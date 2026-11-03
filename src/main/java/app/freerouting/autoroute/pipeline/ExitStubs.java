package app.freerouting.autoroute.pipeline;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.PinClearanceSource;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Erik Gavriluk (freerouting.net) (router.autorouter.exit_stubs): the straight way out of a
 * fine-pitch pin whose only way out meets the clearances to its neighbours exactly, placed before
 * the fanout as a designer draws it.
 */
public final class ExitStubs {

  public static final FixedState PLACED_STATE = FixedState.USER_FIXED;
  public static final int MIN_PINS = 8;
  public static final int TIGHT_SLACK = 2 * ClearanceMatrix.clearance_safety_margin;
  public static final double INWARD_REACH_PAD_LENGTHS = 3.0;

  private static final Pattern KICAD_PAD_SIZE =
      Pattern.compile("Pad_([0-9]+(?:\\.[0-9]+)?)x([0-9]+(?:\\.[0-9]+)?)_");
  private static final Pattern TOLERANCE_COPY = Pattern.compile("^(.*)#tolerance[0-9]+$");

  private ExitStubs() {}

  public record Mirror(IntPoint bend, IntPoint end) {}

  public record Stub(
      Pin pin,
      IntPoint end,
      int halfWidth,
      boolean inward,
      double slack,
      IntPoint bend,
      Mirror mirror) {

    public Stub(Pin pin, IntPoint end, int halfWidth, boolean inward, double slack) {
      this(pin, end, halfWidth, inward, slack, null, null);
    }

    public Stub(Pin pin, IntPoint end, int halfWidth, boolean inward, double slack, IntPoint bend) {
      this(pin, end, halfWidth, inward, slack, bend, null);
    }

    public Point[] corners(IntPoint centre) {
      return bend != null ? new Point[] {centre, bend, end} : new Point[] {centre, end};
    }

    public Stub mirrored() {
      return mirror != null
          ? new Stub(pin, mirror.end(), halfWidth, inward, slack, mirror.bend(), null)
          : null;
    }
  }

  private record StubPlacement(Stub stub, List<Item> traces) {}

  public static String apply(RouterSettings settings, RoutingBoard board, List<Integer> itemIds) {
    if (settings.autorouter == null || settings.autorouter.exitStubs != Boolean.TRUE) {
      return null;
    }
    Set<Integer> before = board.getItems().stream().map(Item::getId).collect(Collectors.toSet());
    List<Stub> stubs = plan(board, settings);
    int allowance = copperAllowance(board, settings);
    int placed = 0;
    int inward = 0;
    int fanned = 0;
    Set<Integer> pins = new HashSet<>();
    Map<String, Integer> parts = new TreeMap<>();
    List<StubPlacement> inserted = new ArrayList<>();

    for (Stub planned : stubs) {
      Stub stub = planned;
      int net = stub.pin.getNetNumber(0);
      int layer = stub.pin.firstLayer();
      int traceClass = board.rules.nets.get(net).getNetClass().getTraceClearanceClass();
      IntPoint centre = centre(stub.pin);
      if (pathSlack(board, stub.corners(centre), layer, stub.halfWidth, net, traceClass, allowance)
          < 0) {
        if (stub.mirrored() == null
            || pathSlack(
                    board,
                    stub.mirrored().corners(centre),
                    layer,
                    stub.mirrored().halfWidth,
                    net,
                    traceClass,
                    allowance)
                < 0) {
          continue;
        }
        stub = stub.mirrored();
      }
      Set<Integer> beforeStub =
          board.getItems().stream().map(Item::getId).collect(Collectors.toSet());
      board.insertTrace(
          new Polyline(stub.corners(centre)),
          layer,
          stub.halfWidth,
          new int[] {net},
          traceClass,
          PLACED_STATE);
      List<Item> traces =
          board.getItems().stream()
              .filter(i -> i instanceof Trace && !beforeStub.contains(i.getId()))
              .collect(Collectors.toList());
      inserted.add(new StubPlacement(stub, traces));
    }

    int unreachable = 0;
    for (boolean removed = true; removed; ) {
      removed = false;
      for (StubPlacement entry : new ArrayList<>(inserted)) {
        if (entry.stub.inward || endReachable(board, entry.stub)) {
          continue;
        }
        for (Item trace : entry.traces) {
          if (trace.isOnTheBoard()) {
            trace.setFixedState(FixedState.UNFIXED);
            board.removeItem(trace);
          }
        }
        inserted.remove(entry);
        unreachable++;
        removed = true;
      }
    }

    for (StubPlacement entry : inserted) {
      placed++;
      pins.add(entry.stub.pin.getId());
      if (entry.stub.inward) {
        inward++;
      }
      if (entry.stub.bend != null) {
        fanned++;
      }
      String part =
          board.components.get(entry.stub.pin.getComponentId()) != null
              ? board.components.get(entry.stub.pin.getComponentId()).name
              : Integer.toString(entry.stub.pin.getComponentId());
      parts.put(part, parts.getOrDefault(part, 0) + 1);
    }

    List<Integer> newIds =
        board.getItems().stream()
            .filter(
                i ->
                    i instanceof Trace
                        && i.getFixedState() == PLACED_STATE
                        && !before.contains(i.getId()))
            .map(Item::getId)
            .sorted()
            .collect(Collectors.toList());
    itemIds.addAll(newIds);

    if (placed == 0) {
      return unreachable > 0
          ? "Exit stubs: none placed; the ends of all "
              + unreachable
              + " planned were out of the router's reach."
          : null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Exit stubs: ")
        .append(pins.size())
        .append(" fine-pitch pins whose way out meets the clearances exactly get ")
        .append(placed)
        .append(" stubs (")
        .append(inward)
        .append(" inward to a pad of their net, ")
        .append(fanned)
        .append(" bent in a fan; ");
    int partIdx = 0;
    for (Map.Entry<String, Integer> p : parts.entrySet()) {
      if (partIdx > 0) sb.append(", ");
      if (partIdx == 6) {
        sb.append("...");
        break;
      }
      sb.append(p.getKey()).append(" ").append(p.getValue());
      partIdx++;
    }
    if (unreachable > 0) {
      sb.append("; ").append(unreachable).append(" left out, their ends out of the router's reach");
    }
    sb.append("), router.autorouter.exit_stubs=true.");
    return sb.toString();
  }

  public static boolean endReachable(RoutingBoard board, Stub stub) {
    int net = stub.pin.getNetNumber(0);
    int layer = stub.pin.firstLayer();
    int traceClass = board.rules.nets.get(net).getNetClass().getTraceClearanceClass();
    IntPoint from = stub.bend != null ? stub.bend : centre(stub.pin);
    int dx = Integer.signum(stub.end.x - from.x);
    int dy = Integer.signum(stub.end.y - from.y);
    int length = endProbeLength(board, stub.end, layer, stub.halfWidth, net, traceClass);

    int[][] dirs =
        new int[][] {
          {dx, dy},
          {Integer.signum(dx - dy), Integer.signum(dx + dy)},
          {Integer.signum(dx + dy), Integer.signum(dy - dx)}
        };
    for (int[] d : dirs) {
      Polyline path =
          new Polyline(
              new Point[] {
                stub.end, new IntPoint(stub.end.x + d[0] * length, stub.end.y + d[1] * length)
              });
      if (board.checkPolylineTrace(path, layer, stub.halfWidth, new int[] {net}, traceClass)) {
        return true;
      }
    }
    return false;
  }

  public static int endProbeLength(
      BasicBoard board, IntPoint end, int layer, int half, int net, int traceClass) {
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int margin = ClearanceMatrix.clearance_safety_margin;
    int length = half + matrix.getValue(traceClass, traceClass, layer, false) + margin;
    int widest = half + matrix.maxValue(traceClass, layer) + margin;
    if (widest <= length) {
      return length;
    }
    int reach = length;
    int search = widest + reach;
    IntBox area = new IntBox(end.x - search, end.y - search, end.x + search, end.y + search);
    FloatPoint at = end.toFloat();
    for (SearchTreeObject entry : board.overlappingObjects(area, layer)) {
      if (!(entry instanceof Item item) || !item.isTraceObstacle(net)) {
        continue;
      }
      int needed =
          half + matrix.getValue(item.clearanceClassIndex(), traceClass, layer, false) + margin;
      if (needed <= length) {
        continue;
      }
      for (int i = 0; i < item.tileShapeCount(); i++) {
        if (item.shapeLayer(i) == layer
            && item.getTileShape(i) != null
            && !item.getTileShape(i).isEmpty()
            && segmentDistance(at, at, item.getTileShape(i)) <= needed + reach) {
          length = needed;
          break;
        }
      }
    }
    return length;
  }

  public static List<Stub> plan(BasicBoard board, RouterSettings settings) {
    Map<Integer, Integer> pinsOfNet = new HashMap<>();
    for (Pin pin : board.getPins()) {
      for (int i = 0; i < pin.netCount(); i++) {
        int n = pin.getNetNumber(i);
        pinsOfNet.put(n, pinsOfNet.getOrDefault(n, 0) + 1);
      }
    }
    int allowance = copperAllowance(board, settings);
    List<Stub> result = new ArrayList<>();
    for (int id = 1; id <= board.components.count(); id++) {
      List<Pin> pins = new ArrayList<>(board.getComponentPins(id));
      if (pins.size() < MIN_PINS) {
        continue;
      }
      double cx = pins.stream().mapToDouble(p -> centre(p).x).average().orElse(0.0);
      double cy = pins.stream().mapToDouble(p -> centre(p).y).average().orElse(0.0);
      List<Stub> part = new ArrayList<>();
      List<Pin> sortedPins = new ArrayList<>(pins);
      sortedPins.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
      for (Pin pin : sortedPins) {
        part.addAll(candidates(board, settings, pin, pins, pinsOfNet, cx, cy, allowance));
      }
      result.addAll(fans(board, part, allowance));
    }
    return result;
  }

  public static List<Stub> plan(BasicBoard board) {
    return plan(board, null);
  }

  private record RowEntry(Stub stub, IntPoint centre, int lateral) {}

  private static List<Stub> fans(BasicBoard board, List<Stub> stubs, int allowance) {
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int margin = ClearanceMatrix.clearance_safety_margin;
    record RowKey(int dx, int dy, int line) {}
    Map<RowKey, List<RowEntry>> rows = new HashMap<>();
    Set<Integer> inwardPins =
        stubs.stream().filter(s -> s.inward).map(s -> s.pin.getId()).collect(Collectors.toSet());
    List<Stub> result = new ArrayList<>();
    List<Stub> diagonal = new ArrayList<>();

    for (Stub stub : stubs) {
      if (stub.inward || stub.bend != null) {
        result.add(stub);
        continue;
      }
      IntPoint centre = centre(stub.pin);
      int dx = Integer.signum(stub.end.x - centre.x);
      int dy = Integer.signum(stub.end.y - centre.y);
      if (dx != 0 && dy != 0) {
        diagonal.add(stub);
        continue;
      }
      IntBox box = stub.pin.getTileShape(0).boundingBox();
      int line = dx != 0 ? (dx > 0 ? box.ur.x : box.ll.x) : (dy > 0 ? box.ur.y : box.ll.y);
      RowKey key = new RowKey(dx, dy, line);
      rows.computeIfAbsent(key, k -> new ArrayList<>())
          .add(new RowEntry(stub, centre, dx != 0 ? centre.y : centre.x));
    }

    for (Map.Entry<RowKey, List<RowEntry>> entry : rows.entrySet()) {
      List<RowEntry> all = entry.getValue();
      all.sort((a, b) -> Integer.compare(a.lateral, b.lateral));
      int pitch = 0;
      if (all.size() > 1) {
        pitch = Integer.MAX_VALUE;
        for (int i = 1; i < all.size(); i++) {
          pitch = Math.min(pitch, all.get(i).lateral - all.get(i - 1).lateral);
        }
      }
      List<List<RowEntry>> runs = new ArrayList<>();
      for (RowEntry re : all) {
        if (runs.isEmpty()
            || re.lateral
                    - runs.get(runs.size() - 1).get(runs.get(runs.size() - 1).size() - 1).lateral
                > pitch * 3 / 2) {
          runs.add(new ArrayList<>());
        }
        runs.get(runs.size() - 1).add(re);
      }
      RowKey key = entry.getKey();
      for (List<RowEntry> run : runs) {
        Supplier<List<Stub>> fanned =
            () -> fanOut(board, run, key.dx, key.dy, key.line, allowance, matrix, margin, true);
        Supplier<List<Stub>> staircase =
            () -> staircase(board, run, key.dx, key.dy, key.line, allowance, matrix, margin);
        result.addAll(
            reachable(
                board,
                fanOut(board, run, key.dx, key.dy, key.line, allowance, matrix, margin, false),
                fanned,
                matrix,
                staircase,
                run.size(),
                inwardPins));
      }
    }
    result.addAll(diagonalFans(board, diagonal, allowance, matrix, margin));
    return result;
  }

  private record DiagEntry(Stub stub, IntPoint centre, double lateral) {}

  private static class DiagRow {
    final int dx;
    final int dy;
    final double line;
    final List<DiagEntry> row = new ArrayList<>();

    DiagRow(int dx, int dy, double line) {
      this.dx = dx;
      this.dy = dy;
      this.line = line;
    }
  }

  private static List<Stub> diagonalFans(
      BasicBoard board, List<Stub> stubs, int allowance, ClearanceMatrix matrix, int margin) {
    List<Stub> result = new ArrayList<>();
    List<DiagRow> rows = new ArrayList<>();

    for (Stub stub : stubs) {
      IntPoint centre = centre(stub.pin);
      int dx = Integer.signum(stub.end.x - centre.x);
      int dy = Integer.signum(stub.end.y - centre.y);
      FloatPoint[] corners = stub.pin.getTileShape(0).cornerApproxArr();
      double line = -Double.MAX_VALUE;
      for (FloatPoint c : corners) {
        double val = (c.x * dx + c.y * dy) / Math.sqrt(2);
        if (val > line) line = val;
      }
      double lateral = (centre.x * -dy + centre.y * dx) / Math.sqrt(2);
      DiagRow matched = null;
      for (DiagRow r : rows) {
        if (r.dx == dx && r.dy == dy && Math.abs(r.line - line) <= 4) {
          matched = r;
          break;
        }
      }
      if (matched == null) {
        matched = new DiagRow(dx, dy, line);
        rows.add(matched);
      }
      matched.row.add(new DiagEntry(stub, centre, lateral));
    }

    for (DiagRow r : rows) {
      List<DiagEntry> all = r.row;
      all.sort((a, b) -> Double.compare(a.lateral, b.lateral));
      double pitch = 0;
      if (all.size() > 1) {
        pitch = Double.MAX_VALUE;
        for (int i = 1; i < all.size(); i++) {
          pitch = Math.min(pitch, all.get(i).lateral - all.get(i - 1).lateral);
        }
      }
      List<List<DiagEntry>> runs = new ArrayList<>();
      for (DiagEntry entry : all) {
        if (runs.isEmpty()
            || entry.lateral
                    - runs.get(runs.size() - 1).get(runs.get(runs.size() - 1).size() - 1).lateral
                > pitch * 1.5) {
          runs.add(new ArrayList<>());
        }
        runs.get(runs.size() - 1).add(entry);
      }
      for (List<DiagEntry> run : runs) {
        Supplier<List<Stub>> fanned =
            () -> diagonalFanOut(board, run, r.dx, r.dy, allowance, matrix, margin, true);
        result.addAll(
            reachable(
                board,
                diagonalFanOut(board, run, r.dx, r.dy, allowance, matrix, margin, false),
                fanned,
                matrix,
                null,
                0,
                null));
      }
    }
    return result;
  }

  private static List<Stub> diagonalFanOut(
      BasicBoard board,
      List<DiagEntry> run,
      int dx,
      int dy,
      int allowance,
      ClearanceMatrix matrix,
      int margin,
      boolean force) {
    List<Stub> straight = run.stream().map(DiagEntry::stub).collect(Collectors.toList());
    if (run.size() < 2) {
      return straight;
    }
    int layer = run.get(0).stub.pin.firstLayer();
    int half = run.stream().mapToInt(e -> e.stub.halfWidth).max().orElse(0);
    double pitch = Double.MAX_VALUE;
    int between = 0;
    for (int i = 1; i < run.size(); i++) {
      pitch = Math.min(pitch, run.get(i).lateral - run.get(i - 1).lateral);
      between =
          Math.max(
              between,
              matrix.getValue(
                  traceClass(board, run.get(i - 1).stub.pin),
                  traceClass(board, run.get(i).stub.pin),
                  layer,
                  true));
    }
    if (!force && pitch - 2 * half >= between + TIGHT_SLACK) {
      return straight;
    }
    double root2 = Math.sqrt(2);
    DiagonalResult diag = diagonal(run.get(0).stub.pin.getTileShape(0));
    if (diag == null) {
      return straight;
    }
    int spread = 2 * half + between + TIGHT_SLACK;
    double rise = Math.max(margin, Math.ceil(spread * root2 - pitch));
    int tipSteps = (int) Math.ceil(diag.halfLength / root2);
    IntPoint c0 = run.get(0).centre;
    int netOf0 = run.get(0).stub.pin.getNetNumber(0);
    int[] requiredHolder = new int[1];
    slack(
        board,
        c0,
        new IntPoint(c0.x + dx * tipSteps, c0.y + dy * tipSteps),
        layer,
        half,
        netOf0,
        traceClass(board, run.get(0).stub.pin),
        allowance,
        requiredHolder);
    double first =
        Math.max(margin, Math.ceil((half + requiredHolder[0]) * root2) - (pitch - diag.halfWidth))
            + margin;
    double reach = Math.ceil(half + between + margin * root2);

    int n = run.size();
    List<Stub> result = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      DiagEntry de = run.get(i);
      Stub stub = de.stub;
      IntPoint centre = de.centre;
      int net = stub.pin.getNetNumber(0);
      int traceClass = traceClass(board, stub.pin);
      int side = (2 * i + 1 < n) ? -1 : (2 * i + 1 > n) ? 1 : 0;
      int level = side < 0 ? i : (side > 0 ? n - 1 - i : (n - 1) / 2);
      int bendSteps = tipSteps + (int) Math.ceil((first + level * rise) / root2);
      IntPoint bendAt = new IntPoint(centre.x + dx * bendSteps, centre.y + dy * bendSteps);
      Stub fan;
      if (side == 0) {
        int endSteps = bendSteps + (int) Math.ceil(2 * reach / root2);
        fan =
            new Stub(
                stub.pin,
                new IntPoint(centre.x + dx * endSteps, centre.y + dy * endSteps),
                stub.halfWidth,
                false,
                stub.slack,
                null,
                null);
      } else {
        int[] a = new int[] {dx, 0};
        int[] b = new int[] {0, dy};
        int[] turn = Integer.signum(a[0] * -dy + a[1] * dx) == side ? a : b;
        fan =
            new Stub(
                stub.pin,
                new IntPoint(bendAt.x + turn[0] * (int) reach, bendAt.y + turn[1] * (int) reach),
                stub.halfWidth,
                false,
                stub.slack,
                bendAt,
                null);
      }
      if (pathSlack(board, fan.corners(centre), layer, fan.halfWidth, net, traceClass, allowance)
          >= 0) {
        result.add(fan);
      }
    }
    return result;
  }

  private static List<Stub> reachable(
      BasicBoard board,
      List<Stub> planned,
      Supplier<List<Stub>> fanned,
      ClearanceMatrix matrix,
      Supplier<List<Stub>> staircase,
      int runCount,
      Set<Integer> inwardPins) {
    if (planned.stream().anyMatch(s -> s.inward)) {
      return planned;
    }
    List<Stub> best = planned;
    int reached =
        (int) planned.stream().filter(s -> reachableAmong(board, s, planned, matrix)).count();
    if (planned.size() >= 2
        && planned.stream().allMatch(s -> s.bend == null)
        && reached < planned.size()) {
      List<Stub> fan = fanned.get();
      int fanReached =
          (int) fan.stream().filter(s -> reachableAmong(board, s, fan, matrix)).count();
      if (fanReached > reached) {
        best = fan;
        reached = fanReached;
      }
    }
    if (staircase != null && runCount >= 2 && reached < runCount) {
      List<Stub> stairs = staircase.get();
      int stairsScore =
          (int)
              stairs.stream()
                  .filter(
                      s ->
                          (inwardPins == null || !inwardPins.contains(s.pin.getId()))
                              && reachableAmong(board, s, stairs, matrix))
                  .count();
      List<Stub> currentBest = best;
      int bestScore =
          (int)
              currentBest.stream()
                  .filter(
                      s ->
                          (inwardPins == null || !inwardPins.contains(s.pin.getId()))
                              && reachableAmong(board, s, currentBest, matrix))
                  .count();
      if (stairsScore > bestScore) {
        best = stairs;
      }
    }
    return best;
  }

  private static List<Stub> staircase(
      BasicBoard board,
      List<RowEntry> run,
      int dx,
      int dy,
      int line,
      int allowance,
      ClearanceMatrix matrix,
      int margin) {
    List<Stub> result = new ArrayList<>();
    int n = run.size();
    if (n < 2) {
      return run.stream().map(RowEntry::stub).collect(Collectors.toList());
    }
    int layer = run.get(0).stub.pin.firstLayer();
    int half = run.stream().mapToInt(e -> e.stub.halfWidth).max().orElse(0);
    int pitch = Integer.MAX_VALUE;
    int between = 0;
    for (int i = 1; i < n; i++) {
      pitch = Math.min(pitch, run.get(i).lateral - run.get(i - 1).lateral);
      between =
          Math.max(
              between,
              matrix.getValue(
                  traceClass(board, run.get(i - 1).stub.pin),
                  traceClass(board, run.get(i).stub.pin),
                  layer,
                  true));
    }
    int netOf0 = run.get(0).stub.pin.getNetNumber(0);
    int[] requiredHolder = new int[1];
    slack(
        board,
        run.get(0).centre,
        new IntPoint(dx != 0 ? line : run.get(0).centre.x, dy != 0 ? line : run.get(0).centre.y),
        layer,
        half,
        netOf0,
        traceClass(board, run.get(0).stub.pin),
        allowance,
        requiredHolder);
    int clearTips = half + requiredHolder[0] + margin;
    int rise = 2 * half + between + TIGHT_SLACK;
    final int finalPitch = pitch;
    final int finalBetween = between;

    java.util.function.BiFunction<Integer, Integer, Stub> build =
        (i, baseAt) -> {
          RowEntry re = run.get(i);
          Stub stub = re.stub;
          IntPoint centre = re.centre;
          int net = stub.pin.getNetNumber(0);
          int traceClass = traceClass(board, stub.pin);
          int side = (2 * i + 1 < n) ? -1 : (2 * i + 1 > n) ? 1 : 0;
          int level = side < 0 ? i : (side > 0 ? n - 1 - i : n / 2);
          int lx = dx != 0 ? 0 : side;
          int ly = dx != 0 ? side : 0;
          int at = margin + Math.max(baseAt + level * rise, level == 0 ? 0 : clearTips);
          IntPoint bendAt =
              dx != 0
                  ? new IntPoint(line + dx * at, centre.y)
                  : new IntPoint(centre.x, line + dy * at);
          if (side == 0) {
            Stub up =
                new Stub(
                    stub.pin,
                    new IntPoint(bendAt.x + dx * rise, bendAt.y + dy * rise),
                    stub.halfWidth,
                    false,
                    stub.slack,
                    null,
                    null);
            return pathSlack(
                        board, up.corners(centre), layer, up.halfWidth, net, traceClass, allowance)
                    >= 0
                ? up
                : null;
          }
          int length = half + finalBetween + margin;
          int onward = half + matrix.maxValue(traceClass, layer) + margin;
          for (int k = 0; k <= 8; k++, length += finalPitch) {
            IntPoint end = new IntPoint(bendAt.x + lx * length, bendAt.y + ly * length);
            Stub turned = new Stub(stub.pin, end, stub.halfWidth, false, stub.slack, bendAt, null);
            if (pathSlack(
                    board,
                    turned.corners(centre),
                    layer,
                    turned.halfWidth,
                    net,
                    traceClass,
                    allowance)
                < 0) {
              return null;
            }
            Polyline past =
                new Polyline(
                    new Point[] {end, new IntPoint(end.x + lx * onward, end.y + ly * onward)});
            if (board.checkPolylineTrace(
                past, layer, turned.halfWidth, new int[] {net}, traceClass)) {
              return turned;
            }
          }
          return null;
        };

    int lowerCount = n / 2;
    int upperFrom = (n + 1) / 2;

    List<Integer> lowerHalf = new ArrayList<>();
    for (int i = 0; i < lowerCount; i++) lowerHalf.add(i);

    List<Integer> upperHalf = new ArrayList<>();
    for (int i = n - 1; i >= upperFrom; i--) upperHalf.add(i);

    List<List<Integer>> halves = List.of(lowerHalf, upperHalf);
    for (List<Integer> halfIndices : halves) {
      if (halfIndices.isEmpty()) {
        continue;
      }
      int baseAt = build.apply(halfIndices.get(0), 0) == null ? clearTips : 0;
      for (int i : halfIndices) {
        Stub stub = build.apply(i, baseAt);
        if (stub != null) {
          result.add(stub);
        }
      }
    }
    if (n % 2 == 1) {
      Stub middle = build.apply(n / 2, 0);
      if (middle != null) {
        result.add(middle);
      }
    }
    return result;
  }

  public static boolean reachableAmong(
      BasicBoard board, Stub stub, List<Stub> run, ClearanceMatrix matrix) {
    int net = stub.pin.getNetNumber(0);
    int layer = stub.pin.firstLayer();
    int traceClass = traceClass(board, stub.pin);
    IntPoint from = stub.bend != null ? stub.bend : centre(stub.pin);
    int dx = Integer.signum(stub.end.x - from.x);
    int dy = Integer.signum(stub.end.y - from.y);
    int length = endProbeLength(board, stub.end, layer, stub.halfWidth, net, traceClass);

    int[][] dirs =
        new int[][] {
          {dx, dy},
          {Integer.signum(dx - dy), Integer.signum(dx + dy)},
          {Integer.signum(dx + dy), Integer.signum(dy - dx)}
        };
    for (int[] d : dirs) {
      IntPoint b = new IntPoint(stub.end.x + d[0] * length, stub.end.y + d[1] * length);
      if (!board.checkPolylineTrace(
          new Polyline(new Point[] {stub.end, b}),
          layer,
          stub.halfWidth,
          new int[] {net},
          traceClass)) {
        continue;
      }
      boolean clear = true;
      for (Stub other : run) {
        if (other.pin == stub.pin) {
          continue;
        }
        Point[] corners = other.corners(centre(other.pin));
        int required =
            stub.halfWidth
                + other.halfWidth
                + matrix.getValue(traceClass(board, other.pin), traceClass, layer, true);
        for (int i = 0; i + 1 < corners.length && clear; i++) {
          clear =
              segmentsDistance(
                      stub.end.toFloat(),
                      b.toFloat(),
                      corners[i].toFloat(),
                      corners[i + 1].toFloat())
                  >= required;
        }
        if (!clear) {
          break;
        }
      }
      if (clear) {
        return true;
      }
    }
    return false;
  }

  public static double segmentsDistance(FloatPoint a, FloatPoint b, FloatPoint c, FloatPoint d) {
    return cross(a, b, c, d)
        ? 0.0
        : Math.min(
            Math.min(pointSegment(a, c, d), pointSegment(b, c, d)),
            Math.min(pointSegment(c, a, b), pointSegment(d, a, b)));
  }

  private static List<Stub> fanOut(
      BasicBoard board,
      List<RowEntry> run,
      int dx,
      int dy,
      int line,
      int allowance,
      ClearanceMatrix matrix,
      int margin,
      boolean force) {
    List<Stub> straight = run.stream().map(RowEntry::stub).collect(Collectors.toList());
    if (run.size() < 2) {
      return straight;
    }
    int layer = run.get(0).stub.pin.firstLayer();
    int half = run.stream().mapToInt(e -> e.stub.halfWidth).max().orElse(0);
    int pitch = Integer.MAX_VALUE;
    int between = 0;
    for (int i = 1; i < run.size(); i++) {
      pitch = Math.min(pitch, run.get(i).lateral - run.get(i - 1).lateral);
      between =
          Math.max(
              between,
              matrix.getValue(
                  traceClass(board, run.get(i - 1).stub.pin),
                  traceClass(board, run.get(i).stub.pin),
                  layer,
                  true));
    }
    if (!force && pitch - 2 * half >= between + TIGHT_SLACK) {
      return straight;
    }
    int spread = 2 * half + between + TIGHT_SLACK;
    int rise = Math.max(margin, (int) Math.ceil(spread * Math.sqrt(2) - pitch));
    IntBox pad = run.get(0).stub.pin.getTileShape(0).boundingBox();
    int padHalf = (dx != 0 ? pad.height() : pad.width()) / 2;
    int netOf0 = run.get(0).stub.pin.getNetNumber(0);
    int[] requiredHolder = new int[1];
    slack(
        board,
        run.get(0).centre,
        new IntPoint(dx != 0 ? line : run.get(0).centre.x, dy != 0 ? line : run.get(0).centre.y),
        layer,
        half,
        netOf0,
        traceClass(board, run.get(0).stub.pin),
        allowance,
        requiredHolder);
    int first =
        Math.max(
                margin,
                (int) Math.ceil((half + requiredHolder[0]) * Math.sqrt(2)) - (pitch - padHalf))
            + margin;
    int reach = (int) Math.ceil((half + between) / Math.sqrt(2)) + margin;
    int n = run.size();
    List<Stub> result = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      RowEntry re = run.get(i);
      Stub stub = re.stub;
      IntPoint centre = re.centre;
      int net = stub.pin.getNetNumber(0);
      int traceClass = traceClass(board, stub.pin);
      int side = (2 * i + 1 < n) ? -1 : (2 * i + 1 > n) ? 1 : 0;
      int level = side < 0 ? i : (side > 0 ? n - 1 - i : (n - 1) / 2);
      int lx = dx != 0 ? 0 : side;
      int ly = dx != 0 ? side : 0;
      int at = first + level * rise;
      IntPoint bendAt =
          dx != 0 ? new IntPoint(line + dx * at, centre.y) : new IntPoint(centre.x, line + dy * at);
      Stub fan =
          side == 0
              ? new Stub(
                  stub.pin,
                  new IntPoint(bendAt.x + dx * reach * 2, bendAt.y + dy * reach * 2),
                  stub.halfWidth,
                  false,
                  stub.slack,
                  null,
                  null)
              : new Stub(
                  stub.pin,
                  new IntPoint(bendAt.x + (dx + lx) * reach, bendAt.y + (dy + ly) * reach),
                  stub.halfWidth,
                  false,
                  stub.slack,
                  bendAt,
                  null);
      if (side != 0) {
        int other = -side;
        int otherLevel = other < 0 ? i : n - 1 - i;
        int mx = dx != 0 ? 0 : other;
        int my = dx != 0 ? other : 0;
        int mat = first + otherLevel * rise;
        IntPoint mirrorBend =
            dx != 0
                ? new IntPoint(line + dx * mat, centre.y)
                : new IntPoint(centre.x, line + dy * mat);
        fan =
            new Stub(
                fan.pin,
                fan.end,
                fan.halfWidth,
                fan.inward,
                fan.slack,
                fan.bend,
                new Mirror(
                    mirrorBend,
                    new IntPoint(
                        mirrorBend.x + (dx + mx) * reach, mirrorBend.y + (dy + my) * reach)));
      }
      if (pathSlack(board, fan.corners(centre), layer, fan.halfWidth, net, traceClass, allowance)
          >= 0) {
        result.add(fan);
      }
    }
    return result;
  }

  private static int traceClass(BasicBoard board, Pin pin) {
    return board.rules.nets.get(pin.getNetNumber(0)).getNetClass().getTraceClearanceClass();
  }

  private static List<Stub> candidates(
      BasicBoard board,
      RouterSettings settings,
      Pin pin,
      List<Pin> partPins,
      Map<Integer, Integer> pinsOfNet,
      double cx,
      double cy,
      int allowance) {
    List<Stub> stubs = new ArrayList<>();
    Stub[] inwardHolder = new Stub[1];
    Stub outward =
        outward(board, settings, pin, partPins, pinsOfNet, cx, cy, allowance, inwardHolder);
    if (outward != null) {
      stubs.add(outward);
    }
    if (inwardHolder[0] != null) {
      stubs.add(inwardHolder[0]);
    }
    return stubs;
  }

  private static Stub outward(
      BasicBoard board,
      RouterSettings settings,
      Pin pin,
      List<Pin> partPins,
      Map<Integer, Integer> pinsOfNet,
      double cx,
      double cy,
      int allowance,
      Stub[] inwardHolder) {
    inwardHolder[0] = null;
    if (pin.netCount() != 1
        || pin.firstLayer() != pin.lastLayer()
        || pin.tileShapeCount() != 1
        || pinsOfNet.getOrDefault(pin.getNetNumber(0), 0) < 2) {
      return null;
    }
    int net = pin.getNetNumber(0);
    int layer = pin.firstLayer();
    if ((settings != null && layer < settings.getLayerCount() && !settings.getLayerActive(layer))
        || board.rules.nets.get(net) == null) {
      return null;
    }
    NetClass netClass = board.rules.nets.get(net).getNetClass();
    if (netClass.isIgnoredByAutorouter
        || !netClass.isActiveRoutingLayer(layer)
        || pin.getTileShape(0) == null) {
      return null;
    }
    TileShape shape = pin.getTileShape(0);
    IntBox box = shape.boundingBox();
    int w = box.width();
    int h = box.height();
    IntPoint centre = centre(pin);
    int classHalf = netClass.getTraceHalfWidth(layer);
    int minHalf = board.rules.getMinAllowedTraceHalfWidth();
    int half = minHalf > 0 ? Math.min(classHalf, minHalf) : classHalf;
    int traceClass = netClass.getTraceClearanceClass();
    if (w == h || Math.max(w, h) < 3 * Math.min(w, h) / 2) {
      return diagonalOutward(
          board, pin, shape, centre, half, classHalf, net, layer, traceClass, cx, cy, allowance);
    }
    boolean alongX = w > h;
    int halfLength = (alongX ? w : h) / 2;
    double offset = alongX ? centre.x - cx : centre.y - cy;
    if (Math.abs(offset) < halfLength) {
      return null;
    }
    int outwardSign = offset > 0 ? 1 : -1;
    int ox = alongX ? outwardSign : 0;
    int oy = alongX ? 0 : outwardSign;
    int ix = -ox;
    int iy = -oy;

    Pin target = null;
    int targetDistance = Integer.MAX_VALUE;
    long reach = (long) (INWARD_REACH_PAD_LENGTHS * 2 * halfLength);
    for (Pin other : partPins) {
      if (other == pin
          || !other.containsNet(net)
          || other.firstLayer() > layer
          || other.lastLayer() < layer
          || Pin.arePiecesOfOnePad(pin, other)
          || padNumber(other).equals(padNumber(pin))) {
        continue;
      }
      for (int i = 0; i < other.tileShapeCount(); i++) {
        if (other.shapeLayer(i) != layer || other.getTileShape(i) == null) {
          continue;
        }
        IntBox ob = other.getTileShape(i).boundingBox();
        boolean onLine =
            alongX
                ? ob.ll.y <= centre.y && centre.y <= ob.ur.y
                : ob.ll.x <= centre.x && centre.x <= ob.ur.x;
        int distance =
            alongX
                ? (ix > 0 ? ob.ll.x - centre.x : centre.x - ob.ur.x)
                : (iy > 0 ? ob.ll.y - centre.y : centre.y - ob.ur.y);
        if (onLine && distance > halfLength && distance <= reach && distance < targetDistance) {
          target = other;
          targetDistance = distance;
        }
      }
    }
    if (target != null) {
      int length = targetDistance + half;
      IntPoint end = new IntPoint(centre.x + ix * length, centre.y + iy * length);
      int[] largestHolder = new int[1];
      double slack =
          slack(board, centre, end, layer, half, net, traceClass, allowance, largestHolder);
      if (slack >= 0
          && slack < TIGHT_SLACK
          && !routerPasses(board, centre, end, layer, half, net, traceClass)) {
        inwardHolder[0] =
            new Stub(
                pin,
                end,
                widest(board, centre, end, layer, half, classHalf, net, traceClass, allowance),
                true,
                slack);
      }
    }

    IntPoint tip = new IntPoint(centre.x + ox * halfLength, centre.y + oy * halfLength);
    int[] largestHolder = new int[1];
    double rowSlack =
        slack(board, centre, tip, layer, half, net, traceClass, allowance, largestHolder);
    if (rowSlack < 0 || rowSlack >= TIGHT_SLACK) {
      return null;
    }
    int run = largestHolder[0] + half + ClearanceMatrix.clearance_safety_margin;
    IntPoint stubEnd = new IntPoint(tip.x + ox * run, tip.y + oy * run);
    double stubSlack =
        slack(board, centre, stubEnd, layer, half, net, traceClass, allowance, largestHolder);
    if (stubSlack < 0
        || (routerPasses(board, centre, stubEnd, layer, half, net, traceClass)
            && !isTolerancePin(board, pin))) {
      return null;
    }
    return new Stub(
        pin,
        stubEnd,
        widest(board, centre, stubEnd, layer, half, classHalf, net, traceClass, allowance),
        false,
        stubSlack);
  }

  private static Stub diagonalOutward(
      BasicBoard board,
      Pin pin,
      TileShape shape,
      IntPoint centre,
      int half,
      int classHalf,
      int net,
      int layer,
      int traceClass,
      double cx,
      double cy,
      int allowance) {
    DiagonalResult diag = diagonal(shape);
    if (diag == null) {
      return null;
    }
    double offset = ((centre.x - cx) * diag.dx + (centre.y - cy) * diag.dy) / Math.sqrt(2);
    if (Math.abs(offset) < diag.halfLength) {
      return null;
    }
    int sign = offset > 0 ? 1 : -1;
    int ox = sign * diag.dx;
    int oy = sign * diag.dy;
    int tipSteps = (int) Math.ceil(diag.halfLength / Math.sqrt(2));
    IntPoint tip = new IntPoint(centre.x + ox * tipSteps, centre.y + oy * tipSteps);
    int[] largestHolder = new int[1];
    double rowSlack =
        slack(board, centre, tip, layer, half, net, traceClass, allowance, largestHolder);
    if (rowSlack < 0 || rowSlack >= TIGHT_SLACK) {
      return null;
    }
    int run = largestHolder[0] + half + ClearanceMatrix.clearance_safety_margin;
    int endSteps = tipSteps + (int) Math.ceil(run / Math.sqrt(2));
    IntPoint stubEnd = new IntPoint(centre.x + ox * endSteps, centre.y + oy * endSteps);
    double stubSlack =
        slack(board, centre, stubEnd, layer, half, net, traceClass, allowance, largestHolder);
    if (stubSlack < 0
        || (routerPasses(board, centre, stubEnd, layer, half, net, traceClass)
            && !isTolerancePin(board, pin))) {
      return null;
    }
    return new Stub(
        pin,
        stubEnd,
        widest(board, centre, stubEnd, layer, half, classHalf, net, traceClass, allowance),
        false,
        stubSlack);
  }

  public record DiagonalResult(int dx, int dy, double halfLength, double halfWidth) {}

  public static DiagonalResult diagonal(TileShape shape) {
    FloatPoint[] corners = shape.cornerApproxArr();
    if (corners.length < 2) {
      return null;
    }
    java.util.function.BiFunction<Integer, Integer, Double> extent =
        (dx, dy) -> {
          double min = Double.MAX_VALUE;
          double max = -Double.MAX_VALUE;
          for (FloatPoint c : corners) {
            double v = c.x * dx + c.y * dy;
            if (v < min) min = v;
            if (v > max) max = v;
          }
          return (max - min) / Math.sqrt(2);
        };
    double rising = extent.apply(1, 1);
    double falling = extent.apply(1, -1);
    double minX = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (FloatPoint c : corners) {
      if (c.x < minX) minX = c.x;
      if (c.x > maxX) maxX = c.x;
      if (c.y < minY) minY = c.y;
      if (c.y > maxY) maxY = c.y;
    }
    double wide = maxX - minX;
    double high = maxY - minY;
    if (Math.abs(wide - high) > 2 || Math.max(rising, falling) < 1.5 * Math.min(rising, falling)) {
      return null;
    }
    return rising > falling
        ? new DiagonalResult(1, 1, rising / 2.0, falling / 2.0)
        : new DiagonalResult(1, -1, falling / 2.0, rising / 2.0);
  }

  private static boolean routerPasses(
      BasicBoard board, IntPoint from, IntPoint to, int layer, int half, int net, int traceClass) {
    return board.checkPolylineTrace(
        new Polyline(new Point[] {from, to}), layer, half, new int[] {net}, traceClass);
  }

  private static boolean isTolerancePin(BasicBoard board, Pin pin) {
    return board.tolerancePins != null && board.tolerancePins.contains(pin.getId());
  }

  private static String padNumber(Pin pin) {
    String name = pin.name();
    if (name == null) {
      return "";
    }
    int at = -1;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '@' || c == '#') {
        at = i;
        break;
      }
    }
    return at > 0 ? name.substring(0, at) : name;
  }

  private static int widest(
      BasicBoard board,
      IntPoint from,
      IntPoint to,
      int layer,
      int half,
      int classHalf,
      int net,
      int traceClass,
      int allowance) {
    int[] holder = new int[1];
    return classHalf > half
            && slack(board, from, to, layer, classHalf, net, traceClass, allowance, holder) >= 0
        ? classHalf
        : half;
  }

  public static IntPoint centre(Pin pin) {
    if (pin.getTileShape(0) != null) {
      IntBox box = pin.getTileShape(0).boundingBox();
      return new IntPoint(
          (int) (((long) box.ll.x + box.ur.x) / 2), (int) (((long) box.ll.y + box.ur.y) / 2));
    }
    return pin.getCenter().toFloat().round();
  }

  public static double slack(
      BasicBoard board,
      IntPoint from,
      IntPoint to,
      int layer,
      int half,
      int net,
      int traceClass,
      int allowance,
      int[] largestHolder) {
    largestHolder[0] = 0;
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int search =
        half + matrix.maxValue(traceClass, layer) + 4 * ClearanceMatrix.clearance_safety_margin;
    IntBox bounds = board.getBoundingBox();
    IntBox area =
        new IntBox(
            Math.max(bounds.ll.x, Math.min(from.x, to.x) - search),
            Math.max(bounds.ll.y, Math.min(from.y, to.y) - search),
            Math.min(bounds.ur.x, Math.max(from.x, to.x) + search),
            Math.min(bounds.ur.y, Math.max(from.y, to.y) + search));
    if (!bounds.contains(from) || !bounds.contains(to)) {
      return -1.0;
    }
    double least = Double.POSITIVE_INFINITY;
    FloatPoint a = from.toFloat();
    FloatPoint b = to.toFloat();
    for (SearchTreeObject entry : board.overlappingObjects(area, layer)) {
      if (!(entry instanceof Item item) || !item.isTraceObstacle(net)) {
        continue;
      }
      int required = ruleClearance(board, traceClass, item, layer, allowance);
      for (int i = 0; i < item.tileShapeCount(); i++) {
        if (item.shapeLayer(i) != layer
            || item.getTileShape(i) == null
            || item.getTileShape(i).isEmpty()) {
          continue;
        }
        TileShape shape = item.getTileShape(i);
        double room =
            segmentDistance(a, b, shape) + modelExcess(board, item, shape) - half - required;
        least = Math.min(least, room);
        if (room < TIGHT_SLACK) {
          largestHolder[0] = Math.max(largestHolder[0], required);
        }
      }
    }
    return least;
  }

  public static double pathSlack(
      BasicBoard board,
      Point[] corners,
      int layer,
      int half,
      int net,
      int traceClass,
      int allowance) {
    double least = Double.POSITIVE_INFINITY;
    int[] holder = new int[1];
    for (int i = 0; i + 1 < corners.length; i++) {
      least =
          Math.min(
              least,
              slack(
                  board,
                  corners[i].toFloat().round(),
                  corners[i + 1].toFloat().round(),
                  layer,
                  half,
                  net,
                  traceClass,
                  allowance,
                  holder));
    }
    return least;
  }

  public static int ruleClearance(
      BasicBoard board, int traceClass, Item item, int layer, int allowance) {
    ClearanceMatrix matrix = board.rules.clearanceMatrix;
    int itemClass = item.clearanceClassIndex();
    String name = matrix.getName(itemClass);
    if (name != null) {
      Matcher m = TOLERANCE_COPY.matcher(name);
      if (m.matches()) {
        int original = matrix.getNo(m.group(1));
        if (original >= 0) {
          itemClass = original;
        }
      }
    }
    if (board.rules.pinClearanceSources != null
        && board.rules.pinClearanceSources.containsKey(itemClass)) {
      PinClearanceSource source = board.rules.pinClearanceSources.get(itemClass);
      int copper =
          source.localClearance() != null
              ? source.localClearance()
              : matrix.getValue(source.originalClass(), traceClass, layer, false);
      boolean outer = layer == 0 || layer == matrix.getLayerCount() - 1;
      return Math.max(copper - allowance, outer ? source.maskClearance() : 0);
    }
    return matrix.getValue(itemClass, traceClass, layer, false) - allowance;
  }

  public static int copperAllowance(BasicBoard board, RouterSettings settings) {
    double epsilonUm =
        settings != null && settings.drcEpsilonUm != null ? settings.drcEpsilonUm : 0.0;
    int epsilon =
        (int)
            Math.floor(
                board.communication.physicalToBoard(Math.max(0.0, epsilonUm), Unit.UM) + 1e-9);
    return Math.max(0, epsilon - 1);
  }

  public static int modelExcess(BasicBoard board, Item item, TileShape shape) {
    if (!(item instanceof Pin pin)
        || pin.getPadstack() == null
        || pin.getPadstack().name == null
        || !pin.getPadstack().name.startsWith("RoundRect")) {
      return 0;
    }
    Matcher m = KICAD_PAD_SIZE.matcher(pin.getPadstack().name);
    if (!m.find()) {
      return 0;
    }
    double wUm = Double.parseDouble(m.group(1));
    double hUm = Double.parseDouble(m.group(2));
    int w = (int) Math.round(board.communication.physicalToBoard(wUm, Unit.UM));
    int h = (int) Math.round(board.communication.physicalToBoard(hUm, Unit.UM));
    IntBox box = shape.boundingBox();
    int excess =
        Math.max(
                Math.min(box.width() - w, box.height() - h),
                Math.min(box.width() - h, box.height() - w))
            / 2;
    int maxExcess = (int) Math.round(board.communication.physicalToBoard(2.0, Unit.UM));
    return excess >= 0 && excess <= maxExcess ? excess : 0;
  }

  public static double segmentDistance(FloatPoint a, FloatPoint b, TileShape shape) {
    FloatPoint[] corners = shape.cornerApproxArr();
    if (corners.length == 0) {
      return Double.POSITIVE_INFINITY;
    }
    if (corners.length == 1) {
      return pointSegment(corners[0], a, b);
    }
    if (shape.contains(a) || shape.contains(b)) {
      return 0.0;
    }
    double least = Double.POSITIVE_INFINITY;
    for (int i = 0; i < corners.length; i++) {
      FloatPoint c = corners[i];
      FloatPoint d = corners[(i + 1) % corners.length];
      if (cross(a, b, c, d)) {
        return 0.0;
      }
      least = Math.min(least, Math.min(pointSegment(c, a, b), pointSegment(d, a, b)));
      least = Math.min(least, Math.min(pointSegment(a, c, d), pointSegment(b, c, d)));
    }
    return least;
  }

  private static double pointSegment(FloatPoint p, FloatPoint a, FloatPoint b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double len = dx * dx + dy * dy;
    double t =
        len > 0 ? Math.max(0.0, Math.min(1.0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len)) : 0.0;
    double x = a.x + t * dx - p.x;
    double y = a.y + t * dy - p.y;
    return Math.sqrt(x * x + y * y);
  }

  private static boolean cross(FloatPoint a, FloatPoint b, FloatPoint c, FloatPoint d) {
    double d1 = side(c, d, a);
    double d2 = side(c, d, b);
    double d3 = side(a, b, c);
    double d4 = side(a, b, d);
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
  }

  private static double side(FloatPoint p, FloatPoint q, FloatPoint r) {
    return (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x);
  }

  private record JointEntry(PolylineTrace trace, List<Point> points) {}

  public static String release(RoutingBoard board, Collection<Integer> itemIds) {
    List<PolylineTrace> traces = new ArrayList<>();
    for (int id : itemIds) {
      Item item = board.getItem(id);
      if (item instanceof PolylineTrace trace && trace.getFixedState() == PLACED_STATE) {
        traces.add(trace);
      }
    }
    if (traces.isEmpty()) {
      return null;
    }
    Set<Integer> netSet = new TreeSet<>();
    for (PolylineTrace t : traces) {
      for (int i = 0; i < t.netCount(); i++) {
        netSet.add(t.getNetNumber(i));
      }
    }
    int openBefore = open(board, netSet);
    board.generateSnapshot();
    int snapped = 0;
    for (PolylineTrace t : traces) {
      snapped += snapNearJoints(board, t);
    }
    List<JointEntry> joints = new ArrayList<>();
    for (PolylineTrace t : traces) {
      joints.add(new JointEntry(t, joints(t)));
    }
    List<PolylineTrace> released = new ArrayList<>();
    int splits = 0;
    for (JointEntry je : joints) {
      PolylineTrace trace = je.trace();
      List<Point> points = je.points();
      trace.setFixedState(FixedState.UNFIXED);
      List<PolylineTrace> pieces = new ArrayList<>();
      pieces.add(trace);
      for (Point point : points) {
        PolylineTrace piece = null;
        for (PolylineTrace p : pieces) {
          if (p.isOnTheBoard()
              && !point.equals(p.firstCorner())
              && !point.equals(p.lastCorner())
              && p.centreLineContains(point)) {
            piece = p;
            break;
          }
        }
        if (piece == null) {
          continue;
        }
        pieces.remove(piece);
        Trace[] halves = piece.split(point);
        if (halves != null) {
          for (Trace h : halves) {
            if (h instanceof PolylineTrace pt) {
              pieces.add(pt);
            }
          }
          splits++;
        } else {
          pieces.add(piece);
        }
      }
      for (PolylineTrace p : pieces) {
        if (p.isOnTheBoard()) {
          released.add(p);
        }
      }
    }

    int removed = 0;
    for (boolean changed = true; changed; ) {
      changed = false;
      for (PolylineTrace piece : new ArrayList<>(released)) {
        if (piece.isOnTheBoard() && piece.isTail() && !piece.hasInteriorContacts()) {
          board.removeItem(piece);
          removed++;
          changed = true;
        }
      }
    }
    int openAfter = open(board, netSet);
    if (openAfter > openBefore) {
      board.undo(null);
      for (int id : itemIds) {
        Item item = board.getItem(id);
        if (item != null) {
          item.setFixedState(PLACED_STATE);
        }
      }
      return "Exit stubs: releasing them would open "
          + (openAfter - openBefore)
          + " connection(s) of their nets ("
          + openBefore
          + " open before, "
          + open(board, netSet)
          + " kept); they stay fixed and are written as protected wiring.";
    }
    board.popSnapshot();
    return "Exit stubs: "
        + traces.size()
        + " released as ordinary copper, "
        + splits
        + " split where routes join them"
        + (snapped > 0 ? " (" + snapped + " route ends moved onto them)" : "")
        + ", "
        + removed
        + " unused pieces removed; "
        + openAfter
        + " connection(s) of their nets open.";
  }

  private static int snapNearJoints(RoutingBoard board, PolylineTrace stub) {
    Point[] line = stub.polyline().corners();
    int moved = 0;
    Set<Item> contacts = new HashSet<>();
    for (int i = 0; i < stub.netCount(); i++) {
      contacts.addAll(board.getConnectableItems(stub.getNetNumber(i)));
    }
    for (Item contact : contacts) {
      if (!(contact instanceof PolylineTrace other)
          || other == stub
          || other.getLayer() != stub.getLayer()
          || !other.isOnTheBoard()
          || other.getFixedState() != FixedState.UNFIXED) {
        continue;
      }
      Point[] corners = other.polyline().corners();
      for (int end = 0; end < 2; end++) {
        int index = (end == 0) ? 0 : corners.length - 1;
        Point point = corners[index];
        if (point instanceof IntPoint grid && stub.centreLineContains(grid)) {
          continue;
        }
        IntPoint at = (point instanceof IntPoint p) ? p : point.toFloat().round();
        IntPoint onto = stub.centreLineContains(at) ? at : nearestOnLine(line, at);
        if (onto == null) {
          continue;
        }
        corners[index] = onto;
        int layer = other.getLayer();
        int half = other.getHalfWidth();
        int clearanceClass = other.clearanceClassIndex();
        int[] nets = other.netNumbers;
        board.removeItem(other);
        board.insertTraceWithoutCleaning(
            new Polyline(corners), layer, half, nets, clearanceClass, FixedState.UNFIXED);
        moved++;
        break;
      }
    }
    return moved;
  }

  public static IntPoint nearestOnLine(Point[] line, IntPoint at) {
    IntPoint best = null;
    double bestDistance = 2.0 + 1e-9;
    for (int i = 0; i + 1 < line.length; i++) {
      if (!(line[i] instanceof IntPoint a) || !(line[i + 1] instanceof IntPoint b)) {
        continue;
      }
      int dx = Integer.signum(b.x - a.x);
      int dy = Integer.signum(b.y - a.y);
      int length = Math.max(Math.abs(b.x - a.x), Math.abs(b.y - a.y));
      if (length == 0 || (dx != 0 && dy != 0 && Math.abs(b.x - a.x) != Math.abs(b.y - a.y))) {
        continue;
      }
      double t = ((double) (at.x - a.x) * dx + (double) (at.y - a.y) * dy) / (dx * dx + dy * dy);
      int k = (int) Math.max(0, Math.min(length, Math.round(t)));
      IntPoint q = new IntPoint(a.x + k * dx, a.y + k * dy);
      double distance = Math.sqrt(Math.pow(q.x - at.x, 2) + Math.pow(q.y - at.y, 2));
      if (distance > 0 && distance < bestDistance) {
        best = q;
        bestDistance = distance;
      }
    }
    return best;
  }

  public static List<Point> joints(Trace trace) {
    List<Point> points = new ArrayList<>();
    for (Item contact : trace.getNormalContacts()) {
      List<Point> ends = new ArrayList<>();
      if (contact instanceof Trace other) {
        ends.add(other.firstCorner());
        ends.add(other.lastCorner());
      } else if (contact instanceof DrillItem drill) {
        ends.add(drill.getCenter());
      }
      for (Point point : ends) {
        if (!point.equals(trace.firstCorner())
            && !point.equals(trace.lastCorner())
            && trace.centreLineContains(point)
            && !points.contains(point)) {
          points.add(point);
        }
      }
    }
    return points;
  }

  public static int open(RoutingBoard board, Iterable<Integer> nets) {
    int total = 0;
    for (int net : nets) {
      total += new NetIncompletes(net, board.getConnectableItems(net), board).count();
    }
    return total;
  }
}
