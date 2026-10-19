package app.freerouting.board.optimize;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.ShapeEntrySide;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntDirection;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact memo of the trace shove check in {@link TraceShover}.
 *
 * <p>The maze search and optimizer ask the same shove question many times on an unchanged board.
 * Entries hold while the board's content version is unchanged. A hit replays the answer and all
 * shared-state mutations: temporary trace IDs from the ID generator, and writes to the board's
 * shove-failure record.
 */
public final class ShoveCheckCache {

  /** The entry count at which the cache starts over, bounding its memory. */
  public static final int MAX_ENTRIES = 1 << 16;

  /** The most border lines a simplex key holds; a trace segment's tile has at most eight. */
  public static final int MAX_LINES = 8;

  private static final ThreadLocal<ShoveCheckCache> CURRENT =
      ThreadLocal.withInitial(ShoveCheckCache::new);

  private final Map<Key, Entry> entries = new HashMap<>();
  private WeakReference<RoutingBoard> owner;
  private long stamp = Long.MIN_VALUE;
  private long hits;
  private long misses;

  /** Returns this thread's shove check cache instance. */
  public static ShoveCheckCache forThread() {
    return CURRENT.get();
  }

  public long getHits() {
    return hits;
  }

  public long getMisses() {
    return misses;
  }

  void countHit() {
    hits++;
  }

  void countMiss() {
    misses++;
  }

  /** Looks up an entry for {@code key} on {@code board} at content version {@code contentStamp}. */
  public Entry tryGet(RoutingBoard board, long contentStamp, Key key) {
    reset(board, contentStamp);
    return entries.get(key);
  }

  /** Puts an entry for {@code key} on {@code board} at content version {@code contentStamp}. */
  public void put(RoutingBoard board, long contentStamp, Key key, Entry entry) {
    reset(board, contentStamp);
    if (entries.size() >= MAX_ENTRIES) {
      entries.clear();
      board.getShoveCacheObstacles().clear();
      return;
    }
    entries.put(key, entry);
  }

  /** The index of {@code obstacle} in the board's obstacle table, adding it when new. */
  public static int obstacleIndex(RoutingBoard board, Item obstacle) {
    List<Item> table = board.getShoveCacheObstacles();
    int index = table.size() - 1;
    if (index >= 0 && table.get(index) == obstacle) {
      return index;
    }
    table.add(obstacle);
    return table.size() - 1;
  }

  private void reset(RoutingBoard board, long contentStamp) {
    if (contentStamp == stamp && owner != null && owner.get() == board) {
      return;
    }
    if (!entries.isEmpty()) {
      entries.clear();
    }
    board.getShoveCacheObstacles().clear();
    stamp = contentStamp;
    owner = new WeakReference<>(board);
  }

  /** True for a check whose key can be held strictly by value. */
  public static boolean supported(TileShape shape, Direction dir, int[] nets) {
    return (shape instanceof IntOctagon || shape instanceof IntBox || integerSimplex(shape))
        && (dir == null || dir instanceof IntDirection)
        && (nets == null || nets.length <= 2);
  }

  private static boolean integerSimplex(TileShape shape) {
    if (!(shape instanceof Simplex s) || shape.getClass() != Simplex.class) {
      return false;
    }
    int n = s.borderLineCount();
    if (n == 0 || n > MAX_LINES) {
      return false;
    }
    for (int i = 0; i < n; i++) {
      Line line = s.borderLine(i);
      if (!(line.a instanceof IntPoint) || !(line.b instanceof IntPoint)) {
        return false;
      }
    }
    return true;
  }

  private static int[] primitive(int x, int y) {
    int g = (int) gcd(Math.abs((long) x), Math.abs((long) y));
    return g == 0 ? new int[] {0, 0} : new int[] {x / g, y / g};
  }

  private static long gcd(long a, long b) {
    while (b != 0) {
      long t = b;
      b = a % b;
      a = t;
    }
    return a;
  }

  /** Inputs of one shove check, held strictly by value with no object references kept. */
  public static final class Key {
    private final int kind; // 1 octagon, 2 box, 3 simplex
    private final int lineCount;
    private final int[] coords;
    private final int sideNo;
    private final long sideX;
    private final long sideY;
    private final boolean sideHasPoint;
    private final int dirX;
    private final int dirY;
    private final int netCount;
    private final int net0;
    private final int net1;
    private final int layer;
    private final int clearanceClass;
    private final int depth;
    private final int viaDepth;
    private final int springOverDepth;
    private final boolean timed;
    private final int hash;

    public Key(
        TileShape shape,
        ShapeEntrySide fromSide,
        Direction dir,
        int layer,
        int[] nets,
        int clearanceClass,
        int depth,
        int viaDepth,
        int springOverDepth,
        boolean timed) {
      if (shape instanceof IntOctagon o) {
        this.kind = 1;
        this.lineCount = 0;
        this.coords =
            new int[] {
              o.leftX,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX
            };
      } else if (shape instanceof IntBox b) {
        this.kind = 2;
        this.lineCount = 0;
        this.coords = new int[] {b.ll.x, b.ll.y, b.ur.x, b.ur.y};
      } else {
        Simplex x = (Simplex) shape;
        this.kind = 3;
        this.lineCount = x.borderLineCount();
        this.coords = new int[4 * this.lineCount];
        for (int i = 0; i < this.lineCount; i++) {
          Line line = x.borderLine(i);
          IntPoint a = (IntPoint) line.a;
          IntPoint b = (IntPoint) line.b;
          this.coords[4 * i] = a.x;
          this.coords[4 * i + 1] = a.y;
          this.coords[4 * i + 2] = b.x;
          this.coords[4 * i + 3] = b.y;
        }
      }

      if (fromSide == null) {
        this.sideNo = Integer.MIN_VALUE;
        this.sideX = 0;
        this.sideY = 0;
        this.sideHasPoint = false;
      } else {
        this.sideNo = fromSide.no;
        FloatPoint p = fromSide.borderIntersection;
        this.sideHasPoint = (p != null);
        this.sideX = p == null ? 0 : Double.doubleToLongBits(p.x);
        this.sideY = p == null ? 0 : Double.doubleToLongBits(p.y);
      }

      if (dir instanceof IntDirection d) {
        int[] prim = primitive(d.x, d.y);
        this.dirX = prim[0];
        this.dirY = prim[1];
      } else {
        this.dirX = 0;
        this.dirY = 0;
      }

      this.netCount = (nets == null) ? 0 : nets.length;
      this.net0 = (nets != null && nets.length > 0) ? nets[0] : 0;
      this.net1 = (nets != null && nets.length > 1) ? nets[1] : 0;
      this.layer = layer;
      this.clearanceClass = clearanceClass;
      this.depth = depth;
      this.viaDepth = viaDepth;
      this.springOverDepth = springOverDepth;
      this.timed = timed;

      int h = 17;
      h = 31 * h + kind;
      h = 31 * h + lineCount;
      h = 31 * h + Arrays.hashCode(coords);
      h = 31 * h + sideNo;
      h = 31 * h + Long.hashCode(sideX);
      h = 31 * h + Long.hashCode(sideY);
      h = 31 * h + (sideHasPoint ? 1 : 0);
      h = 31 * h + dirX;
      h = 31 * h + dirY;
      h = 31 * h + netCount;
      h = 31 * h + net0;
      h = 31 * h + net1;
      h = 31 * h + layer;
      h = 31 * h + clearanceClass;
      h = 31 * h + depth;
      h = 31 * h + viaDepth;
      h = 31 * h + springOverDepth;
      h = 31 * h + (timed ? 1 : 0);
      this.hash = h;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Key o)) {
        return false;
      }
      return hash == o.hash
          && kind == o.kind
          && lineCount == o.lineCount
          && Arrays.equals(coords, o.coords)
          && sideNo == o.sideNo
          && sideX == o.sideX
          && sideY == o.sideY
          && sideHasPoint == o.sideHasPoint
          && dirX == o.dirX
          && dirY == o.dirY
          && netCount == o.netCount
          && net0 == o.net0
          && net1 == o.net1
          && layer == o.layer
          && clearanceClass == o.clearanceClass
          && depth == o.depth
          && viaDepth == o.viaDepth
          && springOverDepth == o.springOverDepth
          && timed == o.timed;
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  /** A check's outcome and recorded side-effects on shared board state. */
  public static final class Entry {
    public final boolean result;
    public final int ids;
    public final boolean wroteObstacle;
    public final int obstacle;
    public final boolean wroteLayer;
    public final int layer;

    public Entry(
        boolean result,
        int ids,
        boolean wroteObstacle,
        int obstacle,
        boolean wroteLayer,
        int layer) {
      this.result = result;
      this.ids = ids;
      this.wroteObstacle = wroteObstacle;
      this.obstacle = obstacle;
      this.wroteLayer = wroteLayer;
      this.layer = layer;
    }
  }
}
