package app.freerouting.management;

import app.freerouting.util.RuntimeTelemetryProvider;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

/**
 * {@link RuntimeTelemetryProvider} backed by {@code java.lang.management} and {@code
 * com.sun.management.ThreadMXBean}.
 */
public final class JvmRuntimeTelemetryProvider implements RuntimeTelemetryProvider {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public JvmRuntimeTelemetryProvider() {}

  @Override
  public long currentThreadCpuNanos() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      return threadMxBean.getThreadCpuTime(Thread.currentThread().threadId());
    } catch (Throwable t) {
      return -1L;
    }
  }

  @Override
  public long currentThreadAllocatedBytes() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      if (!threadMxBean.isThreadAllocatedMemoryEnabled()) {
        threadMxBean.setThreadAllocatedMemoryEnabled(true);
      }
      return threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    } catch (Throwable t) {
      return -1L;
    }
  }

  @Override
  public float currentHeapUsageMb() {
    try {
      long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
      return heapUsed / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      return RuntimeTelemetryProvider.fallback().currentHeapUsageMb();
    }
  }
}
