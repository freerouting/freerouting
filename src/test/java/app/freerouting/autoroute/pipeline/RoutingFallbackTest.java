package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingFallbackTest {

  @Test
  void defaultStrategiesStartWithDirectFirst() {
    RouterSettings defaults = new DefaultSettings().getSettings();
    List<String> names = new ArrayList<>();
    for (RoutingFallback.Strategy strategy : RoutingFallback.parse(defaults, null)) {
      names.add(strategy.name);
    }
    assertEquals(
        List.of(
            "direct-first",
            "fanout-retry",
            "short-escape",
            "open-escape",
            "no-fanout",
            "continue-auto-neck"),
        names);
  }

  @Test
  void noneDisablesFallbackAndUnknownNamesAreSkipped() {
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.autorouter.fallbackStrategies = "none";
    assertTrue(RoutingFallback.parse(settings, null).isEmpty());

    List<String> unknown = new ArrayList<>();
    settings.autorouter.fallbackStrategies = " Open-Escape , bogus, open-escape, fanout-retry";
    List<String> names = new ArrayList<>();
    for (RoutingFallback.Strategy strategy : RoutingFallback.parse(settings, unknown::add)) {
      names.add(strategy.name);
    }
    assertEquals(List.of("open-escape", "fanout-retry"), names);
    assertEquals(List.of("bogus"), unknown);
  }

  @Test
  void directFirstAndOpenEscapeChangeACloneOnly() {
    RouterSettings defaults = new DefaultSettings().getSettings();
    RouterSettings directFirst = defaults.clone();
    RoutingFallback.find("direct-first").apply(directFirst);
    assertTrue(directFirst.usesDirectPinRouting());
    assertFalse(defaults.usesDirectPinRouting());

    RouterSettings openEscape = defaults.clone();
    RoutingFallback.find("open-escape").apply(openEscape);
    assertEquals(0.0, openEscape.fanout.minEscapeLengthMm);
    assertEquals(1000.0, openEscape.fanout.maxEscapeLengthMm);
    assertEquals(1.0, openEscape.autorouter.smdNetViaCostFactor);
    assertEquals(2.5, defaults.fanout.minEscapeLengthMm);

    assertTrue(RoutingFallback.find("fanout-retry").fanoutOnly);
    assertTrue(RoutingFallback.find("short-escape").fanoutOnly);
    assertFalse(RoutingFallback.find("direct-first").fanoutOnly);
  }

  @Test
  void fanoutStrategiesDoNotApplyWhenFanoutDoesNotRun() {
    RouterSettings settings = new DefaultSettings().getSettings();
    List<RoutingFallback.Strategy> parsed = RoutingFallback.parse(settings, null);
    List<RoutingFallback.Strategy> withoutFanout =
        RoutingFallback.applicable(parsed, settings, false);
    assertEquals(1, withoutFanout.size());
    assertEquals("continue-auto-neck", withoutFanout.get(0).name);
    assertEquals(6, RoutingFallback.applicable(parsed, settings, true).size());
  }

  @Test
  void autoNeckAppliesOnlyWhenNeckWidthIsUnset() {
    RouterSettings settings = new DefaultSettings().getSettings();
    List<RoutingFallback.Strategy> parsed = RoutingFallback.parse(settings, null);
    assertTrue(
        RoutingFallback.applicable(parsed, settings, false).stream()
            .anyMatch(s -> "continue-auto-neck".equals(s.name)));

    settings.setNeckWidthUm(150);
    assertTrue(
        RoutingFallback.applicable(parsed, settings, false).stream()
            .noneMatch(s -> "continue-auto-neck".equals(s.name)));
  }

  @Test
  void outcomeRanksFewerOpensThenFewerNewViolationsThenHigherScore() {
    RoutingFallback.Outcome worse = new RoutingFallback.Outcome("a", 2, 0, 0, 10, 0);
    RoutingFallback.Outcome better = new RoutingFallback.Outcome("b", 1, 0, 0, 1, 0);
    assertTrue(RoutingFallback.Outcome.compare(better, worse) < 0);
    RoutingFallback.Outcome dirty = new RoutingFallback.Outcome("c", 1, 3, 3, 50, 0);
    assertTrue(RoutingFallback.Outcome.compare(better, dirty) < 0);
    assertFalse(RoutingFallback.Outcome.canReplace(dirty, worse, 1));
    assertTrue(RoutingFallback.Outcome.canReplace(better, worse, 0));
  }

  @Test
  void designBlockedOpensCountAsComplete() {
    RoutingFallback.Outcome blocked = new RoutingFallback.Outcome("default", 2, 0, 0, 1, 2);
    assertTrue(blocked.isCompleteAndClean());
  }

  @Test
  void retryableStopsAreTheRouterGivingUp() {
    assertTrue(RoutingFallback.isRetryable(BatchAutorouter.StopReason.STAGNATION));
    assertTrue(RoutingFallback.isRetryable(BatchAutorouter.StopReason.NO_IMPROVEMENT));
    assertTrue(RoutingFallback.isRetryable(BatchAutorouter.StopReason.COMPLETED));
    assertFalse(RoutingFallback.isRetryable(BatchAutorouter.StopReason.MAX_PASSES));
    assertFalse(RoutingFallback.isRetryable(BatchAutorouter.StopReason.TIMED_OUT));
    assertFalse(RoutingFallback.isRetryable(BatchAutorouter.StopReason.CANCELLED));
    assertFalse(RoutingFallback.isRetryable(BatchAutorouter.StopReason.REPEATED));
  }

  @Test
  void preExistingViolationsAreNotCountedAsNew() {
    ClearanceViolation violation = new ClearanceViolation(null, null, null, 0, 100, 50);
    List<ClearanceViolation> violations = List.of(violation);
    ClearanceBaseline input = new ClearanceBaseline(violations);
    assertEquals(0, RoutingFallback.Outcome.newOrChangedCount(1, input, violations));
    assertEquals(1, RoutingFallback.Outcome.newOrChangedCount(1, null, violations));
    assertSame(input, RoutingFallback.Outcome.exemptionBaseline(null, input));
  }

  @Test
  void directFirstTreatsNonPositiveFanoutItemLimitAsUnlimited() {
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.fanout.maxItems = 0;
    assertEquals(Integer.MAX_VALUE, DirectPinRouting.fanoutItemLimit(settings));
    settings.fanout.maxItems = 3;
    assertEquals(3, DirectPinRouting.fanoutItemLimit(settings));
  }

  @Test
  void automaticContinuationsPreserveProjectMinTrackWidthFloor() {
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.setNeckWidthUm(200.0);

    List<RoutingFallback.Strategy> withoutProject =
        RoutingFallbackRunner.automaticContinuations(job);
    assertTrue(withoutProject.stream().anyMatch(s -> "continue-no-neck".equals(s.name)));

    job.kicadProject =
        app.freerouting.io.kicad.KiCadProject.parse(
            "{\"board\": {\"design_settings\": {\"rules\": {\"min_track_width\": 0.2}}}}",
            "board.kicad_pro");
    List<RoutingFallback.Strategy> withProject = RoutingFallbackRunner.automaticContinuations(job);
    assertTrue(withProject.stream().noneMatch(s -> "continue-no-neck".equals(s.name)));
  }

  @Test
  void autoNeckWarningLogsOnlyWhenAutoNeckFallbackIsChosenWithUnsetNeckWidth() {
    List<app.freerouting.logger.LogEntry> entries = new ArrayList<>();
    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.routerSettings = new DefaultSettings().getSettings();
    job.addLogEntryAddedEventListener(event -> entries.add(event.getLogEntry()));

    // Non-auto-neck strategy does not emit the warning.
    RoutingFallbackRunner.warnIfAutoNeckFallbackUsed(job, "direct-first");
    assertTrue(entries.isEmpty());

    // Explicit neck_width_um > 0 does not emit the warning even for continue-auto-neck.
    job.routerSettings.setNeckWidthUm(150.0);
    RoutingFallbackRunner.warnIfAutoNeckFallbackUsed(job, "continue-auto-neck");
    assertTrue(entries.isEmpty());

    // Unset neck_width_um (<= 0) with continue-auto-neck emits a WARNING log.
    job.routerSettings.setNeckWidthUm(0.0);
    RoutingFallbackRunner.warnIfAutoNeckFallbackUsed(job, "continue-auto-neck");
    assertEquals(1, entries.size());
    assertEquals(app.freerouting.logger.LogEntryType.Warning, entries.get(0).getType());
    assertTrue(entries.get(0).getMessage().contains("neck_width_um=150.0"));
  }
}
