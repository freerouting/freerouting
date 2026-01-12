package app.freerouting.autoroute;

import app.freerouting.board.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import java.io.Serializable;

/**
 * Thread-safe logbook for tracking routing failures per item.
 * Helps detect when the router is stuck attempting the same impossible
 * connection repeatedly.
 */
public class RoutingFailureLog implements Serializable {

    /** Failure threshold - give up after this many failures for the same item */
    public static final int FAILURE_THRESHOLD = 50;

    /** Map of item ID to failure information */
    private final ConcurrentHashMap<Integer, ItemFailureInfo> failures;

    public RoutingFailureLog() {
        this.failures = new ConcurrentHashMap<>();
    }

    /**
     * Records a routing failure for an item.
     * 
     * @param item   The item that failed to route
     * @param passNo The pass number when the failure occurred
     * @param state  The failure state
     * @param reason The failure reason/details
     */
    public void recordFailure(Item item, int passNo, AutorouteAttemptState state, String reason) {
        if (item == null) {
            return;
        }

        failures.compute(item.get_id_no(), (key, existing) -> {
            if (existing == null) {
                existing = new ItemFailureInfo(item);
            }
            existing.recordFailure(passNo, state, reason);
            return existing;
        });
    }

    /**
     * Checks if an item should be skipped due to exceeding the failure threshold.
     * 
     * @param item The item to check
     * @return true if the item should be skipped
     */
    public boolean shouldSkip(Item item) {
        if (item == null) {
            return false;
        }

        ItemFailureInfo info = failures.get(item.get_id_no());
        return info != null && info.shouldGiveUp();
    }

    /**
     * Gets all items that have exceeded the failure threshold.
     * 
     * @return List of unroutable items
     */
    public List<ItemFailureInfo> getUnroutableItems() {
        List<ItemFailureInfo> unroutable = new ArrayList<>();
        for (ItemFailureInfo info : failures.values()) {
            if (info.shouldGiveUp()) {
                unroutable.add(info);
            }
        }
        return unroutable;
    }

    /**
     * Checks if there are any unroutable items.
     * 
     * @return true if there are items that exceeded the failure threshold
     */
    public boolean hasUnroutableItems() {
        return failures.values().stream().anyMatch(ItemFailureInfo::shouldGiveUp);
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
        ItemFailureInfo info = failures.get(item.get_id_no());
        return info != null ? info.failureCount : 0;
    }

    /**
     * Clears all failure records.
     */
    public void clear() {
        failures.clear();
    }

    /**
     * Information about routing failures for a specific item.
     */
    public static class ItemFailureInfo implements Serializable {
        public final Item item;
        public final int netNo;
        public int failureCount;
        public AutorouteAttemptState lastFailureState;
        public String lastFailureReason;
        public long lastAttemptPass;

        public ItemFailureInfo(Item item) {
            this.item = item;
            this.netNo = item.net_count() > 0 ? item.get_net_no(0) : -1;
            this.failureCount = 0;
            this.lastFailureState = null;
            this.lastFailureReason = "";
            this.lastAttemptPass = 0;
        }

        /**
         * Records a failure for this item (thread-safe).
         * 
         * @param passNo The pass number
         * @param state  The failure state
         * @param reason The failure reason
         */
        public synchronized void recordFailure(long passNo, AutorouteAttemptState state, String reason) {
            this.failureCount++;
            this.lastAttemptPass = passNo;
            this.lastFailureState = state;
            this.lastFailureReason = reason != null ? reason : "";
        }

        /**
         * Checks if this item should be given up on (thread-safe).
         * 
         * @return true if failure count exceeds threshold
         */
        public synchronized boolean shouldGiveUp() {
            return failureCount >= FAILURE_THRESHOLD;
        }

        @Override
        public String toString() {
            return item.getClass().getSimpleName() + " (net #" + netNo + ", " + failureCount + " failures)";
        }
    }
}
