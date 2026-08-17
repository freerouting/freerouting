package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the pure and environment-independent pipeline collaborator seams. */
class AutoroutePipelineCollaboratorTest {

  @Test
  void emptyAirlineCalculationPreservesDegenerateLineBehavior() {
    var airline = AutorouteAirlineCalculator.calculateAirline(List.of(), List.of());

    assertNull(airline.a);
    assertNull(airline.b);
  }

  @Test
  void runtimeSnapshotsUseSafeFallbacksWithoutJob() {
    assertEquals(0f, AutorouteRuntimeMetrics.cpuSecondsSnapshot(null));
    assertEquals(0f, AutorouteRuntimeMetrics.allocatedMemoryMbSnapshot(null));
    assertEquals(0f, AutorouteRuntimeMetrics.peakHeapMbSnapshot(null));
  }

  @Test
  void nanosConvertToMillisecondsWithoutChangingScale() {
    assertEquals(12.5, AutorouteRuntimeMetrics.nanosToMillis(12_500_000L));
  }
}
