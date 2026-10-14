package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.Test;

class RoutingSupervisorTest {

  @Test
  void viaFactorDoublesThenCaps() {
    assertEquals(2, RoutingSupervisor.viaFactor(1));
    assertEquals(4, RoutingSupervisor.viaFactor(2));
    assertEquals(8, RoutingSupervisor.viaFactor(3));
    assertEquals(8, RoutingSupervisor.viaFactor(9));
  }

  @Test
  void laterNeighbourhoodTriesWiden() {
    RetryNeighbourhood.Reach first = RetryNeighbourhood.Reach.of(1);
    assertEquals(1.5, first.radiusMm());
    assertFalse(first.anyNet());
    RetryNeighbourhood.Reach third = RetryNeighbourhood.Reach.of(3);
    assertEquals(4.5, third.radiusMm());
    assertEquals(9, third.contenders());
    assertTrue(third.anyNet());
    RetryNeighbourhood.Reach wide = RetryNeighbourhood.Reach.of(20);
    assertEquals(RetryNeighbourhood.MAX_RADIUS_MM, wide.radiusMm());
    assertEquals(RetryNeighbourhood.MAX_WIDE_CONTENDERS, wide.contenders());
  }

  @Test
  void explorationKeepsOnlyTheNamedFallbackStrategies() {
    RouterSettings settings = new DefaultSettings().getSettings();
    assertEquals("short-escape,open-escape", RoutingSupervisor.explorationFallback(settings));
    settings.autorouter.exploreFallbackStrategies = "none-such";
    assertEquals("none", RoutingSupervisor.explorationFallback(settings));
    settings.autorouter.exploreFallbackStrategies = " ";
    assertEquals(
        "direct-first,fanout-retry,short-escape,open-escape,no-fanout",
        RoutingSupervisor.explorationFallback(settings));
  }

  @Test
  void laterViaTryCostsMoreAndExplorationIsOffByDefault() {
    RouterSettings settings = new DefaultSettings().getSettings();
    assertFalse(RoutingSupervisor.explores(settings));
    settings.autorouter.explorePatience = 2;
    assertTrue(RoutingSupervisor.explores(settings));
    int original = settings.getViaCosts();
    RouterSettings attempt = settings.clone();
    RoutingSupervisor.apply(
        new RoutingSupervisor.Attempt("vias-dearer", "vias-dearer (seed 2, try 2)", 2, 2),
        settings,
        attempt);
    assertEquals(original * 4, attempt.getViaCosts());
    assertEquals("short-escape,open-escape", attempt.autorouter.fallbackStrategies);
    assertEquals(0, attempt.autorouter.explorePatience);
  }

  @Test
  void eachKindIsTriedOnceBeforeARepeat() {
    RouterSettings settings = new DefaultSettings().getSettings();
    RoutingSupervisor supervisor = new RoutingSupervisor();
    RoutingSupervisor.Attempt first = supervisor.nextAttempt(1, settings, null);
    assertEquals("seed", first.kind());
    supervisor.attemptDone(first, 4, 4, false);
    assertEquals("retry-plan", supervisor.nextAttempt(2, settings, null).kind());
  }

  @Test
  void residualThresholdMatchesTheDefaultOfFour() {
    RouterSettings settings = new DefaultSettings().getSettings();
    assertFalse(RoutingSupervisor.residual(settings, 0));
    assertTrue(RoutingSupervisor.residual(settings, 4));
    assertFalse(RoutingSupervisor.residual(settings, 5));
  }
}
