package app.freerouting.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.management.JvmRuntimeTelemetryProvider;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RuntimeTelemetryProvider} discovery, JVM provider, and fallback. */
class RuntimeTelemetryProviderTest {

  @Test
  void serviceLoaderDiscoversJvmRuntimeTelemetryProvider() {
    RuntimeTelemetryProvider provider = RuntimeTelemetryProvider.get();
    assertInstanceOf(JvmRuntimeTelemetryProvider.class, provider);
    assertTrue(provider.currentThreadCpuNanos() >= 0L);
    assertTrue(provider.currentThreadCpuSeconds() >= 0f);
    assertTrue(provider.currentThreadAllocatedBytes() >= 0L);
    assertTrue(provider.currentThreadAllocatedMb() >= 0f);
    assertTrue(provider.currentHeapUsageMb() > 0f);
  }

  @Test
  void fallbackReturnsUnsupportedMarkersAndPositiveHeapUsage() {
    RuntimeTelemetryProvider fallback = RuntimeTelemetryProvider.fallback();
    assertEquals(-1L, fallback.currentThreadCpuNanos());
    assertEquals(-1f, fallback.currentThreadCpuSeconds());
    assertEquals(-1L, fallback.currentThreadAllocatedBytes());
    assertEquals(-1f, fallback.currentThreadAllocatedMb());
    assertTrue(fallback.currentHeapUsageMb() > 0f);
  }

  @Test
  void defaultMethodsConvertNegativeRawValuesToNegativeOneFloat() {
    RuntimeTelemetryProvider unsupported =
        new RuntimeTelemetryProvider() {
          @Override
          public long currentThreadCpuNanos() {
            return -5L;
          }

          @Override
          public long currentThreadAllocatedBytes() {
            return -10L;
          }

          @Override
          public float currentHeapUsageMb() {
            return 0f;
          }
        };

    assertEquals(-1f, unsupported.currentThreadCpuSeconds());
    assertEquals(-1f, unsupported.currentThreadAllocatedMb());
  }
}
