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
    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();

    /**
     * Start timing a section
     */
    public static void start(String section) {
        startTime.set(System.nanoTime());
    }

    /**
     * End timing a section and record the duration
     */
    public static void end(String section) {
        Long start = startTime.get();
        if (start != null) {
            long duration = System.nanoTime() - start;
            timings.computeIfAbsent(section, k -> new AtomicLong()).addAndGet(duration);
            counts.computeIfAbsent(section, k -> new AtomicLong()).incrementAndGet();
            startTime.remove();
        }
    }

    /**
     * Print profiling results sorted by total time
     */
    public static void printResults() {
        FRLogger.info("=== Performance Profile ===");

        timings.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .forEach(entry -> {
                    String section = entry.getKey();
                    long totalMs = entry.getValue().get() / 1_000_000;
                    long count = counts.get(section).get();
                    long avgMs = count > 0 ? totalMs / count : 0;

                    FRLogger.info(String.format("  %-40s: %8d ms total, %8d calls, %6d ms avg",
                            section, totalMs, count, avgMs));
                });

        FRLogger.info("===========================");
    }

    /**
     * Clear all profiling data
     */
    public static void reset() {
        timings.clear();
        counts.clear();
    }
}
