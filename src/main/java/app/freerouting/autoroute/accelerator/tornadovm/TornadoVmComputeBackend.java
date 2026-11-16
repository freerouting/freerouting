package app.freerouting.autoroute.accelerator.tornadovm;

import app.freerouting.autoroute.accelerator.AcceleratorDeviceInfo;
import app.freerouting.autoroute.accelerator.BgaEscapeGrid;
import app.freerouting.autoroute.accelerator.BgaEscapeKernelBackend;
import app.freerouting.autoroute.accelerator.ComputeBackend;
import app.freerouting.autoroute.accelerator.GpuComputeSession;
import app.freerouting.autoroute.accelerator.GpuFallbackReason;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.FanoutSettings;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import uk.ac.manchester.tornado.api.TornadoBackend;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;

/**
 * Runs the BGA escape kernels through <a
 * href="https://github.com/beehive-lab/TornadoVM">TornadoVM</a>, which compiles the Java kernels in
 * {@link BgaEscapeKernels} for the selected device (OpenCL, CUDA PTX or Metal).
 *
 * <p>TornadoVM is an optional dependency. The registry only creates this class when the device
 * preference starts with {@code tornadovm} and the TornadoVM runtime is on the class path; if the
 * runtime cannot be initialised, the backend reports itself unavailable and the fanout stays on the
 * CPU.
 *
 * <p>Accepted preferences: {@code tornadovm} (TornadoVM default device), {@code tornadovm:opencl},
 * {@code tornadovm:cuda} or {@code tornadovm:metal} (first device of that backend), and {@code
 * tornadovm:<backend index>:<device index>} as listed by {@code tornado --devices}.
 */
public final class TornadoVmComputeBackend implements ComputeBackend, BgaEscapeKernelBackend {

  /** Prefix of the {@code gpu_device_preference} values handled by this backend. */
  public static final String PREFERENCE_PREFIX = "tornadovm";

  /** TornadoVM system property that limits which installed backends are started. */
  public static final String ENABLED_BACKENDS_PROPERTY = "tornado.enabled.backends";

  private static final Set<String> BACKEND_NAMES = Set.of("opencl", "cuda", "metal");

  private final TornadoDevice device;
  private final AcceleratorDeviceInfo deviceInfo;

  /**
   * Selects the TornadoVM device for the given preference. Never throws: if TornadoVM cannot start
   * or no device matches, {@link #isAvailable()} returns {@code false}.
   *
   * @param devicePreference the {@code gpu_device_preference} value, starting with {@code
   *     tornadovm}
   */
  public TornadoVmComputeBackend(String devicePreference) {
    TornadoDevice selected = null;
    try {
      selected = selectDevice(devicePreference);
    } catch (Throwable t) {
      FRLogger.info("GPU acceleration: TornadoVM runtime could not be initialised: " + t);
    }
    this.device = selected;
    this.deviceInfo = (selected != null) ? describe(selected) : null;
  }

  private static TornadoDevice selectDevice(String preference) {
    String spec = (preference == null) ? "" : preference.trim().toLowerCase(Locale.ROOT);
    spec = spec.startsWith(PREFERENCE_PREFIX) ? spec.substring(PREFERENCE_PREFIX.length()) : spec;
    spec = spec.startsWith(":") ? spec.substring(1) : spec;

    // TornadoVM starts a backend the first time it is looked up, and the lookup below has to go
    // through the backends in order. When the preference names a backend, only that one is
    // enabled, so the others (about 0.15 s each to start) are never started. An explicit
    // -Dtornado.enabled.backends is left alone.
    if (BACKEND_NAMES.contains(spec) && System.getProperty(ENABLED_BACKENDS_PROPERTY) == null) {
      System.setProperty(ENABLED_BACKENDS_PROPERTY, spec);
    }

    var runtime = TornadoRuntimeProvider.getTornadoRuntime();
    if (runtime.getNumBackends() == 0) {
      return null;
    }
    if (spec.isEmpty()) {
      return runtime.getDefaultDevice();
    }

    String[] parts = spec.split(":");
    if (parts.length == 2 && isNumber(parts[0]) && isNumber(parts[1])) {
      int backendIndex = Integer.parseInt(parts[0]);
      int deviceIndex = Integer.parseInt(parts[1]);
      if (backendIndex < runtime.getNumBackends()) {
        TornadoBackend backend = runtime.getBackend(backendIndex);
        if (deviceIndex < backend.getNumDevices()) {
          return backend.getDevice(deviceIndex);
        }
      }
      return null;
    }

    for (int b = 0; b < runtime.getNumBackends(); b++) {
      TornadoBackend backend = runtime.getBackend(b);
      if (backend.getBackendType().name().toLowerCase(Locale.ROOT).equals(spec)
          && backend.getNumDevices() > 0) {
        return backend.getDevice(0);
      }
    }
    return null;
  }

  private static boolean isNumber(String s) {
    return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
  }

  private static AcceleratorDeviceInfo describe(TornadoDevice device) {
    String backendName = device.getTornadoVMBackend().name();
    return new AcceleratorDeviceInfo(
        device.getPhysicalDevice().getDeviceName(),
        "TornadoVM " + backendName + " (" + device.getPlatformName() + ")",
        device.getDeviceName(),
        false,
        0,
        device.getMaxGlobalMemory());
  }

  @Override
  public boolean isAvailable() {
    return device != null;
  }

  @Override
  public AcceleratorDeviceInfo getDeviceInfo() {
    return deviceInfo;
  }

  @Override
  public GpuComputeSession openSession(FanoutSettings settings) {
    if (device == null) {
      throw new IllegalStateException("TornadoVM backend is not available");
    }
    // TornadoVM owns the device context and buffers; a run allocates and releases its own.
    return new TornadoVmSession(deviceInfo);
  }

  @Override
  public GpuFallbackReason fallbackReason() {
    return (device != null) ? null : GpuFallbackReason.NO_DEVICE_FOUND;
  }

  /**
   * Runs the escape kernels once on a small synthetic package, so that TornadoVM has compiled them
   * and warmed up its own code before the first real solve. Meant to run on a background thread
   * while the board is still loading; a solve that starts meanwhile waits for it to finish.
   */
  public synchronized void warmUp() {
    if (device == null) {
      return;
    }
    long start = System.nanoTime();
    try {
      BgaEscapeRunner.runOnDevice(warmUpGrid(), device);
      FRLogger.info(
          "GPU acceleration: TornadoVM kernels compiled for "
              + deviceInfo.deviceName()
              + " in "
              + (System.nanoTime() - start) / 1_000_000
              + " ms (background warm-up).");
    } catch (Throwable t) {
      // The real solve reports any lasting problem and falls back on its own.
      FRLogger.debug("GPU acceleration: TornadoVM warm-up failed: " + t);
    }
  }

  @Override
  public synchronized int[] runBgaEscape(BgaEscapeGrid grid) {
    if (device == null) {
      throw new IllegalStateException("TornadoVM backend is not available");
    }
    return BgaEscapeRunner.runOnDevice(grid, device);
  }

  /** Two by two balls at 800 units pitch, two layers, one obstacle outside the grid. */
  private static BgaEscapeGrid warmUpGrid() {
    int pitch = 800;
    int step = 100;
    int[] pins = new int[4 * BgaEscapeKernels.PIN_STRIDE];
    for (int i = 0; i < 4; i++) {
      int u = (i % 2 == 0 ? -1 : 1) * pitch / 2;
      int v = (i < 2 ? -1 : 1) * pitch / 2;
      int o = i * BgaEscapeKernels.PIN_STRIDE;
      int halfWidth = pitch / 4;
      pins[o] = u;
      pins[o + 1] = v;
      pins[o + 2] = u - halfWidth;
      pins[o + 3] = u + halfWidth;
      pins[o + 4] = v - halfWidth;
      pins[o + 5] = v + halfWidth;
      pins[o + 6] = 1;
      pins[o + 8] = u >= 0 ? 1 : -1;
      pins[o + 9] = v >= 0 ? 1 : -1;
      pins[o + 11] = 2;
    }
    int[] obstacle = {100_000, 100_100, 100_000, 100_100, -100, 100, 200_000, 200_200, -1, 0};
    return new BgaEscapeGrid(pins, 4, obstacle, 1, 32, 32, 2, step, -1600, -1600, pitch, 0);
  }

  private static final class TornadoVmSession implements GpuComputeSession {
    private final AcceleratorDeviceInfo info;
    private final AtomicBoolean alive = new AtomicBoolean(true);

    TornadoVmSession(AcceleratorDeviceInfo info) {
      this.info = info;
    }

    @Override
    public AcceleratorDeviceInfo deviceInfo() {
      return info;
    }

    @Override
    public boolean isAlive() {
      return alive.get();
    }

    @Override
    public void poison() {
      alive.set(false);
    }

    @Override
    public void close() {
      alive.set(false);
    }
  }
}
