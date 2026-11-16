package app.freerouting.autoroute.pipeline;

import app.freerouting.core.RoutingJob;
import app.freerouting.util.RuntimeTelemetryProvider;
import java.util.Locale;

/** Collects optional runtime metrics used by autoroute stage diagnostics. */
final class AutorouteRuntimeMetrics {

  private AutorouteRuntimeMetrics() {}

  static float cpuSecondsSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.cpuTimeUsed;
  }

  static float allocatedMemoryMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.maxMemoryUsed;
  }

  static float peakHeapMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.peakMemoryUsed;
  }

  static long currentThreadCpuNanos() {
    return RuntimeTelemetryProvider.get().currentThreadCpuNanos();
  }

  static float currentThreadCpuSeconds() {
    return RuntimeTelemetryProvider.get().currentThreadCpuSeconds();
  }

  static long currentThreadAllocatedBytes() {
    return RuntimeTelemetryProvider.get().currentThreadAllocatedBytes();
  }

  static float currentThreadAllocatedMb() {
    return RuntimeTelemetryProvider.get().currentThreadAllocatedMb();
  }

  static float currentHeapUsageMb() {
    return RuntimeTelemetryProvider.get().currentHeapUsageMb();
  }

  static String formatOptionalMetric(float value, String format) {
    return value < 0f ? "N/A" : String.format(Locale.US, format, value);
  }

  static double nanosToMillis(long nanos) {
    return nanos / 1_000_000.0;
  }
}
