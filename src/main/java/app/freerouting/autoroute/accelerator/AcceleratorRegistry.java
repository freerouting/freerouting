package app.freerouting.autoroute.accelerator;

import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBackend;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.FanoutSettings;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe singleton registry for GPU compute backend discovery and session circuit breaking.
 */
public final class AcceleratorRegistry {

  private static final AtomicReference<ComputeBackend> CACHED_BACKEND = new AtomicReference<>();

  private AcceleratorRegistry() {}

  /**
   * Discovers and returns the appropriate compute backend based on settings and system
   * capabilities.
   *
   * @param settings fanout configuration settings
   * @return active or fallback ComputeBackend (never null)
   */
  public static ComputeBackend discover(FanoutSettings settings) {
    if (settings == null
        || !Boolean.TRUE.equals(settings.gpuAccelerationEnabled)
        || "cpu".equalsIgnoreCase(settings.gpuDevicePreference)) {
      return new NoOpCpuComputeBackend(GpuFallbackReason.DISABLED_BY_SETTINGS);
    }

    String os = System.getProperty("os.name", "").toLowerCase();
    String arch = System.getProperty("os.arch", "").toLowerCase();
    boolean isMacArm64 = os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"));
    boolean experimentalAllowed =
        "opencl_experimental".equalsIgnoreCase(settings.gpuDevicePreference);

    if (isMacArm64 && !experimentalAllowed) {
      FRLogger.info(
          "GPU acceleration: Apple Silicon detected; OpenCL 1.2 is deprecated by Apple. "
              + "Falling back to CPU. Set --router.fanout.gpu_device_preference=opencl_experimental to override.");
      return new NoOpCpuComputeBackend(GpuFallbackReason.UNSUPPORTED_PLATFORM_DRIVER);
    }

    ComputeBackend existing = CACHED_BACKEND.get();
    if (existing != null) {
      return existing;
    }

    ComputeBackend discovered;
    try {
      String pref = (settings != null) ? settings.gpuDevicePreference : "auto";
      discovered = new PanamaOpenClBackend(pref);
      if (!discovered.isAvailable()) {
        discovered = new NoOpCpuComputeBackend(GpuFallbackReason.NO_DEVICE_FOUND);
        FRLogger.info("GPU acceleration: no compatible OpenCL 1.2 device found; using CPU fanout.");
      } else {
        FRLogger.info(
            "GPU acceleration: discovered "
                + discovered.getDeviceInfo().deviceName()
                + " ("
                + discovered.getDeviceInfo().vendor()
                + ")");
      }
    } catch (Throwable t) {
      discovered = new NoOpCpuComputeBackend(GpuFallbackReason.NO_DEVICE_FOUND);
      FRLogger.info(
          "GPU acceleration: OpenCL library could not be loaded ("
              + t.getMessage()
              + "); using CPU fanout.");
    }

    CACHED_BACKEND.compareAndSet(null, discovered);
    return CACHED_BACKEND.get();
  }

  /**
   * Trips the session circuit breaker on fatal errors or kernel timeouts, forcing future calls to
   * CPU.
   *
   * @param reason the fallback trigger
   */
  public static void tripCircuitBreaker(GpuFallbackReason reason) {
    CACHED_BACKEND.set(new NoOpCpuComputeBackend(reason));
    FRLogger.warn("GPU acceleration circuit breaker tripped: " + reason);
  }

  /** Resets the registry cache (primarily for testing purposes). */
  public static void reset() {
    CACHED_BACKEND.set(null);
  }
}
