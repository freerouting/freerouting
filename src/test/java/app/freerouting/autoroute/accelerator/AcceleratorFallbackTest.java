package app.freerouting.autoroute.accelerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.FanoutSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcceleratorFallbackTest {

  @BeforeEach
  void setUp() {
    AcceleratorRegistry.reset();
  }

  @Test
  void disabledBySettingsFallback() {
    FanoutSettings settings = new FanoutSettings();
    settings.gpuAccelerationEnabled = false;

    ComputeBackend backend = AcceleratorRegistry.discover(settings);
    assertNotNull(backend);
    assertFalse(backend.isAvailable());
    assertEquals(GpuFallbackReason.DISABLED_BY_SETTINGS, backend.fallbackReason());
    assertNull(backend.getDeviceInfo());
    assertThrows(UnsupportedOperationException.class, () -> backend.openSession(settings));
  }

  @Test
  void cpuPreferenceFallback() {
    FanoutSettings settings = new FanoutSettings();
    settings.gpuAccelerationEnabled = true;
    settings.gpuDevicePreference = "cpu";

    ComputeBackend backend = AcceleratorRegistry.discover(settings);
    assertNotNull(backend);
    assertFalse(backend.isAvailable());
    assertEquals(GpuFallbackReason.DISABLED_BY_SETTINGS, backend.fallbackReason());
  }

  @Test
  void circuitBreakerTripForcesFallback() {
    FanoutSettings settings = new FanoutSettings();
    settings.gpuAccelerationEnabled = true;
    settings.gpuDevicePreference = "auto";

    AcceleratorRegistry.tripCircuitBreaker(GpuFallbackReason.KERNEL_TIMEOUT);
    ComputeBackend backend = AcceleratorRegistry.discover(settings);
    assertNotNull(backend);
    assertFalse(backend.isAvailable());
    assertEquals(GpuFallbackReason.KERNEL_TIMEOUT, backend.fallbackReason());
  }

  @Test
  void settingsMergerCliOverridePrecedence() {
    // Priority 0: DefaultSettings has gpuMinBgaPins = 9
    // Priority 60: CliSettings overrides with --router.fanout.gpu_min_bga_pins=128
    String[] cliArgs =
        new String[] {
          "--router.fanout.gpu_min_bga_pins=128",
          "--router.fanout.gpu_kernel_timeout_ms=5000",
          "--router.fanout.gpu_device_preference=dgpu"
        };
    CliSettings cliSettings = new CliSettings(cliArgs);

    SettingsMerger merger = new SettingsMerger();
    merger.addOrReplaceSources(new DefaultSettings());
    merger.addOrReplaceSources(cliSettings);

    RouterSettings merged = merger.merge();
    assertNotNull(merged.fanout);
    assertEquals(128, merged.fanout.gpuMinBgaPins);
    assertEquals(5000, merged.fanout.gpuKernelTimeoutMs);
    assertEquals("dgpu", merged.fanout.gpuDevicePreference);
    // Baseline from DefaultSettings preserved
    assertEquals(true, merged.fanout.gpuAccelerationEnabled);
    assertEquals(256, merged.fanout.gpuMaxVramMb);
  }

  @Test
  void reaperExecutesQuarantinedCleanup() throws InterruptedException {
    AtomicBoolean cleanedUp = new AtomicBoolean(false);
    CountDownLatch latch = new CountDownLatch(1);

    GpuResourceReaper.getInstance()
        .enqueue(
            new GpuResourceReaper.PoisonedResource(
                null,
                () -> {
                  cleanedUp.set(true);
                  latch.countDown();
                }));

    boolean completed = latch.await(2, TimeUnit.SECONDS);
    assertTrue(completed, "Reaper should execute cleanup within 2 seconds");
    assertTrue(cleanedUp.get(), "Quarantined cleanup action must be executed");
  }
}
