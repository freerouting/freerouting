package app.freerouting.autoroute.accelerator;

import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBackend;
import app.freerouting.autoroute.accelerator.tornadovm.TornadoVmComputeBackend;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.FanoutSettings;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe singleton registry for GPU compute backend discovery and session circuit breaking.
 */
public final class AcceleratorRegistry {

  private static final AtomicReference<ComputeBackend> CACHED_BACKEND = new AtomicReference<>();
  private static final AtomicReference<CachedTornadoVm> CACHED_TORNADOVM_BACKEND =
      new AtomicReference<>();

  /** The TornadoVM backend is cached per preference, which selects the device. */
  private record CachedTornadoVm(String preference, ComputeBackend backend) {}

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

    if (isTornadoVmPreference(settings.gpuDevicePreference)) {
      return discoverTornadoVm(settings.gpuDevicePreference);
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

  private static boolean isTornadoVmPreference(String preference) {
    return preference != null
        && preference
            .trim()
            .toLowerCase(Locale.ROOT)
            .startsWith(TornadoVmComputeBackend.PREFERENCE_PREFIX);
  }

  /**
   * TornadoVM is an optional dependency: if its classes are not on the class path, creating the
   * backend throws {@link NoClassDefFoundError}, which is caught here like any other start-up
   * failure. It does not go through the Apple Silicon OpenCL check, because TornadoVM can use its
   * Metal backend there.
   */
  private static synchronized ComputeBackend discoverTornadoVm(String preference) {
    CachedTornadoVm existing = CACHED_TORNADOVM_BACKEND.get();
    if (existing != null && existing.preference().equals(preference)) {
      return existing.backend();
    }

    ComputeBackend discovered;
    try {
      discovered = new TornadoVmComputeBackend(preference);
      if (discovered.isAvailable()) {
        FRLogger.info(
            "GPU acceleration: discovered "
                + discovered.getDeviceInfo().deviceName()
                + " ("
                + discovered.getDeviceInfo().vendor()
                + ")");
      } else {
        FRLogger.info(
            "GPU acceleration: no TornadoVM device available for '"
                + preference
                + "'; using CPU fanout.");
        discovered = new NoOpCpuComputeBackend(GpuFallbackReason.NO_DEVICE_FOUND);
      }
    } catch (Throwable t) {
      FRLogger.info(
          "GPU acceleration: TornadoVM is not available ("
              + t
              + "); using CPU fanout. Run Freerouting with TornadoVM on the class path to use it.");
      discovered = new NoOpCpuComputeBackend(GpuFallbackReason.NO_DEVICE_FOUND);
    }

    CACHED_TORNADOVM_BACKEND.set(new CachedTornadoVm(preference, discovered));
    return discovered;
  }

  /**
   * Starts TornadoVM and compiles the escape kernels on a background thread when the settings ask
   * for the TornadoVM backend. Called at start-up, so that the one-time TornadoVM start-up and
   * kernel compilation overlap with loading the board instead of delaying the first fanout. Does
   * nothing for any other preference.
   *
   * @param settings fanout settings known at start-up (may be {@code null})
   */
  public static void warmUpAsync(FanoutSettings settings) {
    if (settings == null
        || !Boolean.TRUE.equals(settings.gpuAccelerationEnabled)
        || !isTornadoVmPreference(settings.gpuDevicePreference)) {
      return;
    }
    String preference = settings.gpuDevicePreference;
    Thread.ofPlatform()
        .daemon()
        .name("tornadovm-warm-up")
        .start(
            () -> {
              if (discoverTornadoVm(preference) instanceof TornadoVmComputeBackend tornadoVm) {
                tornadoVm.warmUp();
              }
            });
  }

  /**
   * Trips the session circuit breaker on fatal errors or kernel timeouts, forcing future calls to
   * CPU.
   *
   * @param reason the fallback trigger
   */
  public static void tripCircuitBreaker(GpuFallbackReason reason) {
    CACHED_BACKEND.set(new NoOpCpuComputeBackend(reason));
    CachedTornadoVm tornadoVm = CACHED_TORNADOVM_BACKEND.get();
    if (tornadoVm != null) {
      CACHED_TORNADOVM_BACKEND.set(
          new CachedTornadoVm(tornadoVm.preference(), new NoOpCpuComputeBackend(reason)));
    }
    FRLogger.warn("GPU acceleration circuit breaker tripped: " + reason);
  }

  /** Resets the registry cache (primarily for testing purposes). */
  public static void reset() {
    CACHED_BACKEND.set(null);
    CACHED_TORNADOVM_BACKEND.set(null);
  }
}
