package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.IntBox;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Searches the next autoroute item on a deep copy while the main thread routes the current item.
 *
 * <p>The copy is committed only when {@link SnapshotCommitGate} says the live board has not changed
 * since the snapshot. Otherwise the search is discarded and the main thread routes the item itself,
 * which keeps the same routes as the single-thread pass. Enabled with {@code
 * -Dfreerouting.autoroute.snapshot_commit=true} when {@code router.autorouter.max_threads} is
 * greater than one. The pool is one worker: a wider window cannot be merged into the live board.
 */
final class SnapshotLookahead implements AutoCloseable {

  static final String PROPERTY = "freerouting.autoroute.snapshot_commit";

  /** Non-empty box used when a route changed the board but left no new or ripped geometry. */
  private static final IntBox UNKNOWN_MUTATION = new IntBox(0, 0, 0, 0);

  private final BatchAutorouter router;
  private final ExecutorService workers;
  private Future<PreparedConnection> pending;
  private IntBox committedChanges = IntBox.EMPTY;
  private int retries;
  private int adopted;
  private long copyNanos;
  private long preparedNanos;
  private boolean closed;

  private SnapshotLookahead(BatchAutorouter router, ExecutorService workers) {
    this.router = router;
    this.workers = workers;
  }

  static boolean enabled(BatchAutorouter router) {
    return Boolean.getBoolean(PROPERTY) && router.settings.getAutorouterMaxThreads() > 1;
  }

  static SnapshotLookahead openIfEnabled(BatchAutorouter router) {
    if (!enabled(router)) {
      return null;
    }
    ExecutorService workers =
        Executors.newFixedThreadPool(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "freerouting-snapshot-commit");
              thread.setDaemon(true);
              return thread;
            });
    return new SnapshotLookahead(router, workers);
  }

  static List<ConnectionAttempt> attempts(List<Item> items) {
    List<ConnectionAttempt> attempts = new ArrayList<>();
    for (Item item : items) {
      for (int netIndex = 0; netIndex < item.netCount(); netIndex++) {
        attempts.add(new ConnectionAttempt(item, netIndex));
      }
    }
    return attempts;
  }

  static ConnectionAttempt next(List<ConnectionAttempt> attempts, int ordinal) {
    if (attempts == null || ordinal + 1 >= attempts.size()) {
      return null;
    }
    return attempts.get(ordinal + 1);
  }

  /**
   * Returns the prepared search for this item when it is still valid, or null when the caller must
   * route the item on the live board.
   */
  PreparedConnection poll(Item item, int netIndex) {
    if (pending == null) {
      return null;
    }
    Future<PreparedConnection> future = pending;
    pending = null;
    PreparedConnection prepared;
    try {
      prepared = future.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      retries++;
      return null;
    } catch (ExecutionException exception) {
      retries++;
      return null;
    }
    preparedNanos += prepared.searchNanos;
    if (prepared.board == null
        || prepared.result == null
        || prepared.itemId != item.getId()
        || prepared.netIndex != netIndex
        || prepared.board.getItem(item.getId()) == null) {
      retries++;
      return null;
    }
    if (!SnapshotCommitGate.adoptPreparedBoard(committedChanges)) {
      retries++;
      return null;
    }
    adopted++;
    return prepared;
  }

  /** Copies the current board and searches {@code next} on that copy. */
  void launch(ConnectionAttempt next, int passNo) {
    if (closed || next == null || pending != null) {
      return;
    }
    long copyStart = System.nanoTime();
    RoutingBoard copy = router.board.deepCopy();
    copyNanos += System.nanoTime() - copyStart;
    committedChanges = IntBox.EMPTY;
    if (copy == null) {
      retries++;
      return;
    }
    int itemId = next.item.getId();
    int netIndex = next.netIndex;
    pending = workers.submit(() -> prepare(copy, itemId, netIndex, passNo));
  }

  void noteAfterRoute(
      AutorouteAttemptResult result,
      RoutingBoard board,
      int maxIdBefore,
      Collection<Item> rippedItems) {
    IntBox corridor = corridorOf(board, maxIdBefore, rippedItems);
    if (SnapshotCommitGate.isUnchanged(result, corridor)) {
      return;
    }
    if (corridor.isEmpty()) {
      corridor = UNKNOWN_MUTATION;
    }
    committedChanges = SnapshotCommitGate.union(committedChanges, corridor);
  }

  void noteRemoved(Collection<Item> items) {
    IntBox corridor = IntBox.EMPTY;
    for (Item item : items) {
      corridor = SnapshotCommitGate.union(corridor, item.boundingBox());
    }
    if (corridor.isEmpty()) {
      corridor = UNKNOWN_MUTATION;
    }
    committedChanges = SnapshotCommitGate.union(committedChanges, corridor);
  }

  void copyProfile(BatchAutorouter target) {
    target.profileSnapshotRetries = this.retries;
    target.profileSnapshotAdopted = this.adopted;
    target.profileSnapshotCopyNanos = this.copyNanos;
    target.profileSnapshotPreparedNanos = this.preparedNanos;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (pending != null) {
      pending.cancel(true);
      pending = null;
    }
    workers.shutdownNow();
  }

  private PreparedConnection prepare(RoutingBoard copy, int itemId, int netIndex, int passNo) {
    long searchStart = System.nanoTime();
    Item item = copy.getItem(itemId);
    if (item == null || netIndex >= item.netCount()) {
      return PreparedConnection.missing(itemId, netIndex, System.nanoTime() - searchStart);
    }
    int maxIdBefore = copy.communication.idGenerator.maxGeneratedId();
    SortedSet<Item> rippedItems = new TreeSet<>();
    Map<Item, Integer> ripupCosts = new LinkedHashMap<>();
    BatchAutorouter worker = router.forSnapshot(copy);
    AutorouteAttemptResult result =
        worker.autorouteItem(item, item.getNetNumber(netIndex), rippedItems, ripupCosts, passNo);
    IntBox corridor = corridorOf(copy, maxIdBefore, rippedItems);
    return new PreparedConnection(
        itemId,
        netIndex,
        copy,
        result,
        rippedItems,
        ripupCosts,
        corridor,
        worker.getAirLine(),
        System.nanoTime() - searchStart);
  }

  private static IntBox corridorOf(
      RoutingBoard board, int maxIdBefore, Collection<Item> rippedItems) {
    IntBox corridor = IntBox.EMPTY;
    if (rippedItems != null) {
      for (Item item : rippedItems) {
        corridor = SnapshotCommitGate.union(corridor, item.boundingBox());
      }
    }
    if (board != null) {
      for (Item item : board.getItems()) {
        if (item.getId() > maxIdBefore) {
          corridor = SnapshotCommitGate.union(corridor, item.boundingBox());
        }
      }
    }
    return corridor;
  }

  static final class ConnectionAttempt {
    final Item item;
    final int netIndex;

    ConnectionAttempt(Item item, int netIndex) {
      this.item = item;
      this.netIndex = netIndex;
    }
  }

  static final class PreparedConnection {
    final int itemId;
    final int netIndex;
    final RoutingBoard board;
    final AutorouteAttemptResult result;
    final SortedSet<Item> rippedItems;
    final Map<Item, Integer> ripupCosts;
    final IntBox corridor;
    final FloatLine airLine;
    final long searchNanos;

    private PreparedConnection(
        int itemId,
        int netIndex,
        RoutingBoard board,
        AutorouteAttemptResult result,
        SortedSet<Item> rippedItems,
        Map<Item, Integer> ripupCosts,
        IntBox corridor,
        FloatLine airLine,
        long searchNanos) {
      this.itemId = itemId;
      this.netIndex = netIndex;
      this.board = board;
      this.result = result;
      this.rippedItems = rippedItems;
      this.ripupCosts = ripupCosts;
      this.corridor = corridor;
      this.airLine = airLine;
      this.searchNanos = searchNanos;
    }

    static PreparedConnection missing(int itemId, int netIndex, long searchNanos) {
      return new PreparedConnection(
          itemId,
          netIndex,
          null,
          null,
          new TreeSet<>(),
          new LinkedHashMap<>(),
          IntBox.EMPTY,
          null,
          searchNanos);
    }
  }
}
