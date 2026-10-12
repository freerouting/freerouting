package app.freerouting.autoroute;

import app.freerouting.board.model.items.Item;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe logbook for tracking routing failures per item and per net. Helps detect when the
 * router is stuck attempting the same impossible connection repeatedly and provides structured
 * failure diagnostics for unrouted connection reporting.
 */
public class RoutingFailureLog implements Serializable {

  /** Map of item ID to failure information. */
  private final ConcurrentHashMap<Integer, ItemFailureInfo> failures;

  /** Map of net name to latest failure reason. */
  private final ConcurrentHashMap<String, FailureReason> netFailureReasons;

  /** RoutingFailureLog. */
  public RoutingFailureLog() {
    this.failures = new ConcurrentHashMap<>();
    this.netFailureReasons = new ConcurrentHashMap<>();
  }

  /**
   * Records a routing failure for an item, optionally recording the net-level failure reason.
   *
   * @param item The item that failed to route
   * @param passNo The pass number when the failure occurred
   * @param state The failure state
   * @param reason The failure reason details string
   * @param failureReason The structured failure reason
   * @param netName The name of the net being routed (optional)
   */
  public void recordFailure(
      Item item,
      int passNo,
      AutorouteAttemptState state,
      String reason,
      FailureReason failureReason,
      String netName) {
    if (item != null) {
      failures.compute(
          item.getId(),
          (key, existing) -> {
            if (existing == null) {
              existing = new ItemFailureInfo(item);
            }
            existing.recordFailure(passNo, state, reason, failureReason);
            return existing;
          });
    }
    if (netName != null && failureReason != null) {
      this.netFailureReasons.put(netName, failureReason);
    }
  }

  /**
   * Records a routing failure for an item without a structured failure reason.
   *
   * @param item The item that failed to route
   * @param passNo The pass number when the failure occurred
   * @param state The failure state
   * @param reason The failure reason/details
   */
  public void recordFailure(Item item, int passNo, AutorouteAttemptState state, String reason) {
    recordFailure(item, passNo, state, reason, null, null);
  }

  /**
   * Explicitly records or updates the latest failure reason for a net.
   *
   * @param netName The net name
   * @param reason The structured failure reason
   */
  public void recordNetFailure(String netName, FailureReason reason) {
    if (netName != null && reason != null) {
      this.netFailureReasons.put(netName, reason);
    }
  }

  /**
   * Returns an unmodifiable view of the latest failure reasons per net name.
   *
   * @return Unmodifiable map of net name to failure reason
   */
  public Map<String, FailureReason> getNetFailureReasons() {
    return Collections.unmodifiableMap(this.netFailureReasons);
  }

  /**
   * Gets the failure count for a specific item.
   *
   * @param item The item to check
   * @return The number of failures, or 0 if no failures recorded
   */
  public int getFailureCount(Item item) {
    if (item == null) {
      return 0;
    }
    ItemFailureInfo info = failures.get(item.getId());
    return info != null ? info.failureCount : 0;
  }

  /** Clears all failure records and net failure reasons. */
  public void clear() {
    this.failures.clear();
    this.netFailureReasons.clear();
  }

  /** Information about routing failures for a specific item. */
  public static class ItemFailureInfo implements Serializable {
    public final Item item;
    public final int netNumber;
    public int failureCount;
    public AutorouteAttemptState lastFailureState;
    public String lastFailureReason;
    public FailureReason failureReason;
    public long lastAttemptPass;

    /** Creates failure tracking info for an item. */
    public ItemFailureInfo(Item item) {
      this.item = item;
      this.netNumber = item.netCount() > 0 ? item.getNetNumber(0) : -1;
      this.failureCount = 0;
      this.lastFailureState = null;
      this.lastFailureReason = "";
      this.failureReason = null;
      this.lastAttemptPass = 0;
    }

    /**
     * Records a failure for this item with structured failure reason (thread-safe).
     *
     * @param passNo The pass number
     * @param state The failure state
     * @param reason The failure reason details string
     * @param failureReason The structured failure reason
     */
    public synchronized void recordFailure(
        long passNo, AutorouteAttemptState state, String reason, FailureReason failureReason) {
      this.failureCount++;
      this.lastAttemptPass = passNo;
      this.lastFailureState = state;
      this.lastFailureReason = reason != null ? reason : "";
      this.failureReason = failureReason;
    }

    /**
     * Records a failure for this item (thread-safe).
     *
     * @param passNo The pass number
     * @param state The failure state
     * @param reason The failure reason
     */
    public synchronized void recordFailure(
        long passNo, AutorouteAttemptState state, String reason) {
      recordFailure(passNo, state, reason, null);
    }

    @Override
    public String toString() {
      return item.getClass().getSimpleName()
          + " (net #"
          + netNumber
          + ", "
          + failureCount
          + " failures)";
    }
  }
}
