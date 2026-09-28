package app.freerouting.core;

import com.google.gson.annotations.SerializedName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/** Records resource usage for a routing job. */
public class RouterJobResourceUsage {

  @SerializedName("cpu_time")
  public float cpuTimeUsed = 0.0F;

  @SerializedName("max_memory")
  public float maxMemoryUsed = 0.0F;

  @SerializedName("peak_memory")
  public float peakMemoryUsed = 0.0F;

  @SerializedName("io_read")
  public float ioRead = 0.0F;

  @SerializedName("io_written")
  public float ioWrite = 0.0F;

  /** Captures current JVM resource metrics. */
  public static RouterJobResourceUsage capture(long startTimeMillis) {
    RouterJobResourceUsage usage = new RouterJobResourceUsage();
    try {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      if (threadBean.isCurrentThreadCpuTimeSupported()) {
        long totalCpuTimeNanos = 0;
        for (long id : threadBean.getAllThreadIds()) {
          long t = threadBean.getThreadCpuTime(id);
          if (t > 0) {
            totalCpuTimeNanos += t;
          }
        }
        usage.cpuTimeUsed = (float) (totalCpuTimeNanos / 1_000_000_000.0);
      } else {
        usage.cpuTimeUsed = (float) ((System.currentTimeMillis() - startTimeMillis) / 1000.0);
      }

      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
      usage.peakMemoryUsed = (float) (heapUsage.getUsed() / (1024.0 * 1024.0));
      usage.maxMemoryUsed = (float) (heapUsage.getMax() / (1024.0 * 1024.0));
    } catch (Exception ignored) {
      usage.cpuTimeUsed = (float) ((System.currentTimeMillis() - startTimeMillis) / 1000.0);
    }
    return usage;
  }
}
