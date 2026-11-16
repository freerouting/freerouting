package app.freerouting.util;

import java.util.ServiceLoader;

/**
 * Provides optional JVM runtime telemetry (per-thread CPU time, per-thread allocation, and heap
 * usage) without coupling core routing packages to {@code java.lang.management} or {@code
 * com.sun.management}.
 *
 * <p>A {@code java.management}-backed implementation is discovered via {@link ServiceLoader}; when
 * unavailable, {@link #fallback()} provides a pure {@code java.base} implementation that returns
 * {@code -1} for unsupported thread-level metrics and {@link Runtime} memory figures for heap
 * usage.
 */
public interface RuntimeTelemetryProvider {

  /**
   * Pure {@code java.base} fallback returning {@code -1L} for thread CPU/allocation metrics and
   * {@code (totalMemory - freeMemory)} in MB for heap usage.
   */
  RuntimeTelemetryProvider FALLBACK =
      new RuntimeTelemetryProvider() {
        @Override
        public long currentThreadCpuNanos() {
          return -1L;
        }

        @Override
        public long currentThreadAllocatedBytes() {
          return -1L;
        }

        @Override
        public float currentHeapUsageMb() {
          Runtime runtime = Runtime.getRuntime();
          long used = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
          return used / (1024.0f * 1024.0f);
        }
      };

  /** Returns the current thread's CPU time in nanoseconds, or {@code -1L} if unsupported. */
  long currentThreadCpuNanos();

  /**
   * Returns an approximation of the total amount of memory allocated on the heap by the current
   * thread in bytes, or {@code -1L} if unsupported.
   */
  long currentThreadAllocatedBytes();

  /** Returns the current heap memory usage in megabytes. */
  float currentHeapUsageMb();

  /** Returns the current thread's CPU time in seconds, or {@code -1f} if unsupported. */
  default float currentThreadCpuSeconds() {
    long nanos = currentThreadCpuNanos();
    return nanos < 0L ? -1f : nanos / 1_000_000_000.0f;
  }

  /**
   * Returns the current thread's allocated heap memory in megabytes, or {@code -1f} if unsupported.
   */
  default float currentThreadAllocatedMb() {
    long bytes = currentThreadAllocatedBytes();
    return bytes < 0L ? -1f : bytes / (1024.0f * 1024.0f);
  }

  /** Returns the pure {@code java.base} fallback provider. */
  static RuntimeTelemetryProvider fallback() {
    return FALLBACK;
  }

  /** Returns the active {@link RuntimeTelemetryProvider}, loaded via {@link ServiceLoader}. */
  static RuntimeTelemetryProvider get() {
    return Holder.INSTANCE;
  }

  /** Lazy holder for the {@link ServiceLoader}-discovered provider. */
  final class Holder {
    private static final RuntimeTelemetryProvider INSTANCE = load();

    private Holder() {}

    private static RuntimeTelemetryProvider load() {
      try {
        return ServiceLoader.load(RuntimeTelemetryProvider.class).findFirst().orElse(FALLBACK);
      } catch (Throwable t) {
        return FALLBACK;
      }
    }
  }
}
