package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for multi-strategy fallback routing and its outcome evaluation. */
class RoutingFallbackTest {

  @Test
  void defaultStrategiesAreKnownAndNoneDisablesThem() {
    RouterSettings defaults = new SettingsMerger(new DefaultSettings()).merge();
    List<String> parsedNames =
        RoutingFallback.parse(defaults).stream().map(RoutingFallback.Strategy::getName).toList();
    assertEquals(List.of("fanout-retry", "short-escape", "open-escape", "no-fanout"), parsedNames);

    List<String> unknown = new ArrayList<>();
    defaults.autorouter.fallbackStrategies = " Open-Escape , bogus, open-escape,fanout-retry";
    List<String> customNames =
        RoutingFallback.parse(defaults, unknown::add).stream()
            .map(RoutingFallback.Strategy::getName)
            .toList();
    assertEquals(List.of("open-escape", "fanout-retry"), customNames);
    assertEquals(List.of("bogus"), unknown);

    defaults.autorouter.fallbackStrategies = "short-escape,none";
    assertTrue(RoutingFallback.parse(defaults).isEmpty());

    defaults.autorouter.fallbackStrategies = "";
    assertTrue(RoutingFallback.parse(defaults).isEmpty());

    defaults.autorouter.fallbackStrategies = null;
    assertTrue(RoutingFallback.parse(defaults).isEmpty());
  }

  @Test
  void strategiesChangeOnlyTheirOwnSettings() {
    RouterSettings defaults = new SettingsMerger(new DefaultSettings()).merge();
    for (RoutingFallback.Strategy strategy : RoutingFallback.KNOWN) {
      RouterSettings changed = defaults.clone();
      strategy.apply(changed);
      assertEquals(2.5, defaults.fanout.minEscapeLengthMm, 1e-6);
      assertEquals(0.1, defaults.autorouter.smdNetViaCostFactor, 1e-6);
      assertFalse(defaults.fanout.retryWithoutEscapeWindow);
      assertEquals(0.0, defaults.getNeckWidthUm(), 1e-6);
      boolean expectedNeedsFanout =
          !List.of("no-neck", "continue-via-in-pad", "continue-fine-neck").contains(strategy.name);
      assertEquals(expectedNeedsFanout, strategy.needsFanout);
    }

    RouterSettings noNeck = defaults.clone();
    noNeck.neckWidthUm = 150.0;
    RoutingFallback.KNOWN.stream()
        .filter(s -> "no-neck".equals(s.name))
        .findFirst()
        .orElseThrow()
        .apply(noNeck);
    assertEquals(0.0, noNeck.getNeckWidthUm(), 1e-6);

    RouterSettings open = defaults.clone();
    RoutingFallback.KNOWN.stream()
        .filter(s -> "open-escape".equals(s.name))
        .findFirst()
        .orElseThrow()
        .apply(open);
    assertEquals(0.0, open.fanout.minEscapeLengthMm, 1e-6);
    assertEquals(1000.0, open.fanout.maxEscapeLengthMm, 1e-6);
    assertEquals(1.0, open.autorouter.smdNetViaCostFactor, 1e-6);

    RouterSettings noFanout = defaults.clone();
    RoutingFallback.KNOWN.stream()
        .filter(s -> "no-fanout".equals(s.name))
        .findFirst()
        .orElseThrow()
        .apply(noFanout);
    assertFalse(noFanout.isFanoutEnabled());
    assertTrue(defaults.isFanoutEnabled());
  }

  @Test
  void outcomesRankLikeThePortfolio() {
    RoutingFallback.Outcome complete = new RoutingFallback.Outcome("a", 0, 0, 3, 10.0f);
    RoutingFallback.Outcome dirty = new RoutingFallback.Outcome("b", 0, 1, 1, 900.0f);
    RoutingFallback.Outcome open = new RoutingFallback.Outcome("c", 1, 0, 0, 999.0f);
    RoutingFallback.Outcome lowScore = new RoutingFallback.Outcome("d", 0, 0, 3, 5.0f);

    assertTrue(RoutingFallback.Outcome.compare(complete, dirty) < 0);
    assertTrue(RoutingFallback.Outcome.compare(dirty, open) < 0);
    assertTrue(RoutingFallback.Outcome.compare(complete, lowScore) < 0);
    assertEquals(0, RoutingFallback.Outcome.compare(complete, complete));
    assertTrue(complete.isCompleteAndClean());
    assertFalse(dirty.isCompleteAndClean());
  }

  @Test
  void closedConnectionCannotBuyNewClearanceViolation() {
    RoutingFallback.Outcome incumbent = new RoutingFallback.Outcome("best", 2, 0, 0, 999.0f);
    RoutingFallback.Outcome candidate = new RoutingFallback.Outcome("fallback", 1, 1, 1, 0.0f);

    assertTrue(RoutingFallback.Outcome.compare(candidate, incumbent) < 0);
    assertFalse(RoutingFallback.Outcome.canReplace(candidate, incumbent, 1));
    assertFalse(RoutingFallback.Outcome.canReplace(candidate, incumbent, 0));
  }

  @Test
  void equalViolationCountsMustPreserveTheIncumbentsIdentities() {
    RoutingFallback.Outcome incumbent = new RoutingFallback.Outcome("best", 2, 1, 3, 500.0f);
    RoutingFallback.Outcome candidate = new RoutingFallback.Outcome("continue", 1, 1, 3, 400.0f);

    assertFalse(RoutingFallback.Outcome.canReplace(candidate, incumbent, 1));
    assertTrue(RoutingFallback.Outcome.canReplace(candidate, incumbent, 0));

    RoutingFallback.Outcome repaired = new RoutingFallback.Outcome("continue", 1, 0, 2, 400.0f);
    assertTrue(RoutingFallback.Outcome.canReplace(repaired, incumbent, 0));
  }

  @ParameterizedTest
  @CsvSource({
    "STAGNATION, true",
    "NO_IMPROVEMENT, true",
    "COMPLETED, true",
    "MAX_PASSES, false",
    "TIMED_OUT, false",
    "CANCELLED, false",
    "WORK_LIMIT, false"
  })
  void onlyARouterThatGaveUpIsRetried(BatchAutorouter.StopReason reason, boolean retryable) {
    assertEquals(retryable, RoutingFallback.isRetryable(reason));
  }

  @Test
  void stopReasonDescriptionsAndManifestNames() {
    assertEquals("completed", RoutingFallback.describe(BatchAutorouter.StopReason.COMPLETED));
    assertEquals("pass limit", RoutingFallback.describe(BatchAutorouter.StopReason.MAX_PASSES));
    assertEquals("stagnation", RoutingFallback.describe(BatchAutorouter.StopReason.STAGNATION));
    assertEquals(
        "no improvement", RoutingFallback.describe(BatchAutorouter.StopReason.NO_IMPROVEMENT));
    assertEquals("timeout", RoutingFallback.describe(BatchAutorouter.StopReason.TIMED_OUT));
    assertEquals("work limit", RoutingFallback.describe(BatchAutorouter.StopReason.WORK_LIMIT));
    assertEquals("cancelled", RoutingFallback.describe(BatchAutorouter.StopReason.CANCELLED));

    assertEquals("max_passes", RoutingFallback.manifestName(BatchAutorouter.StopReason.MAX_PASSES));
    assertEquals(
        "no_improvement", RoutingFallback.manifestName(BatchAutorouter.StopReason.NO_IMPROVEMENT));
    assertEquals("timed_out", RoutingFallback.manifestName(BatchAutorouter.StopReason.TIMED_OUT));
    assertEquals("work_limit", RoutingFallback.manifestName(BatchAutorouter.StopReason.WORK_LIMIT));
    assertEquals("stagnation", RoutingFallback.manifestName(BatchAutorouter.StopReason.STAGNATION));
  }

  @Test
  void newSettingsReachTheRouterFromTheCommandLine() {
    RouterSettings settings =
        new SettingsMerger(
                new DefaultSettings(),
                new CliSettings(
                    new String[] {
                      "--router.autorouter.fallback_strategies=short-escape",
                      "--router.autorouter.smd_net_via_cost_factor=1.0",
                      "--router.fanout.retry_without_escape_window=true",
                      "--router.autorouter.fallback_max_seconds=42.0"
                    }))
            .merge();

    assertEquals("short-escape", settings.autorouter.fallbackStrategies);
    assertEquals(42.0, settings.autorouter.fallbackMaxSeconds, 1e-6);
    assertEquals(1.0, settings.autorouter.smdNetViaCostFactor, 1e-6);
    assertTrue(settings.fanout.retryWithoutEscapeWindow);
  }
}
