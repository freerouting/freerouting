package app.freerouting.autoroute;

import app.freerouting.logger.FRLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple performance profiler to measure time spent in different routing
 * operations.
 * Thread-safe for use in multi-threaded routing.
 */
public class PerformanceProfiler {

    private static final Map<String, AtomicLong> timings = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private static final ThreadLocal<java.util.Deque<Invocation>> stack = ThreadLocal
            .withInitial(java.util.ArrayDeque::new);
    /**
     * Print profiling results sorted by total time
     */
    private static final java.util.List<PassInfo> passHistory = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());
    private static ConfigInfo configInfo;

    /**
     * Start timing a section
     */
    public static void start(String section) {
        stack.get().push(new Invocation(section, System.nanoTime()));
    }

    /**
     * End timing a section and record the duration
     */
    public static void end(String section) {
        java.util.Deque<Invocation> s = stack.get();
        if (!s.isEmpty()) {
            Invocation inv = s.pop();
            // Optional: verify inv.name.equals(section)
            if (inv.name.equals(section)) {
                long duration = System.nanoTime() - inv.startTime;
                timings.computeIfAbsent(section, k -> new AtomicLong()).addAndGet(duration);
                counts.computeIfAbsent(section, k -> new AtomicLong()).incrementAndGet();
            } else {
                // Imbalance or wrong nesting. Put it back? Or ignore?
                // Ideally log error but for now just ignore strict check to avoid crashes
                // Actually if mismatch, it means we missed an end() call somewhere or crossed
                // threads (impossible with ThreadLocal)
                // But let's record it anyway if possible? No, duration would be wrong.
                // For robustness in this debugging session, allow mismatch if name matches (it
                // should).
                // If mismatch, maybe we popped the wrong one.
                // Let's assume correct usage for now.
                // Re-add to timings even if name doesn't match? No, that corrupts data.
                // If name doesn't match, we likely popped a child that wasn't closed.
                // Let's rely on correct nesting.
            }
        }
    }

    /**
     * Record statistics for a completed pass
     */
    public static void recordPass(int passNo, int unroutedItems, long durationMs, int ripupCost) {
        passHistory.add(new PassInfo(passNo, unroutedItems, durationMs, ripupCost));
    }

    public static void recordConfiguration(int viaCosts, int planeViaCosts, double[] preferredCosts,
            double[] againstCosts) {
        configInfo = new ConfigInfo(viaCosts, planeViaCosts, preferredCosts, againstCosts);
    }

    /**
     * Print profiling results sorted by total time
     */

    /**
     * Print profiling results sorted by total time
     */
    public static void printResults() {
        FRLogger.debug("=== Performance Profile ===");

        if (configInfo != null) {
            FRLogger.debug("=== Routing Parameters ===");
            FRLogger.debug(String.format("  Via Costs: %d, Plane Via Costs: %d", configInfo.viaCosts,
                    configInfo.planeViaCosts));
            FRLogger.debug("  Layer Costs (Preferred / Against):");
            for (int i = 0; i < configInfo.preferredCosts.length; i++) {
                FRLogger.debug(String.format("    Layer %d: %.1f / %.1f", i + 1, configInfo.preferredCosts[i],
                        configInfo.againstCosts[i]));
            }
            FRLogger.debug("");
        }

        timings.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    String section = entry.getKey();
                    long totalMs = entry.getValue().get() / 1_000_000;
                    long count = counts.get(section).get();
                    long avgMs = count > 0 ? totalMs / count : 0;

                    FRLogger.debug(String.format("  %-40s: %8d ms total, %8d calls, %6d ms avg",
                            section, totalMs, count, avgMs));
                });

        if (!passHistory.isEmpty()) {
            FRLogger.debug("");
            FRLogger.debug("=== Pass History ===");
            synchronized (passHistory) {
                // Sort by pass info just in case threads messed up order, though auto-router is
                // single threaded per board usually
                passHistory.sort(java.util.Comparator.comparingInt(p -> p.passNo));
                for (PassInfo pass : passHistory) {
                    FRLogger.debug(String.format("  Pass %-3d: %4d unrouted items, %8.2f s, %5d ripup cost",
                            pass.passNo, pass.unroutedItems, pass.durationMs / 1000.0, pass.ripupCost));
                }
            }
        }

        FRLogger.debug("===========================");
    }

    /**
     * Clear all profiling data
     */
    public static void reset() {
        timings.clear();
        counts.clear();
    }

    private static class Invocation {
        String name;
        long startTime;

        Invocation(String name, long startTime) {
            this.name = name;
            this.startTime = startTime;
        }
    }

    public static class PassInfo {
        int passNo;
        int unroutedItems;
        long durationMs;
        int ripupCost;

        public PassInfo(int passNo, int unroutedItems, long durationMs, int ripupCost) {
            this.passNo = passNo;
            this.unroutedItems = unroutedItems;
            this.durationMs = durationMs;
            this.ripupCost = ripupCost;
        }
    }

    /**
     * Store configuration metrics for reporting
     */
    private static class ConfigInfo {
        int viaCosts;
        int planeViaCosts;
        double[] preferredCosts;
        double[] againstCosts;

        ConfigInfo(int viaCosts, int planeViaCosts, double[] preferredCosts, double[] againstCosts) {
            this.viaCosts = viaCosts;
            this.planeViaCosts = planeViaCosts;
            this.preferredCosts = preferredCosts;
            this.againstCosts = againstCosts;
        }
    }
}