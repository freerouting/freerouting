package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Legacy short optimizer and autorouter flags from docs/command_line_arguments.md. They must either
 * reach the merged router settings with a deprecation warning, or warn that they no longer do
 * anything.
 */
class LegacyCliFlagsTest {

  private static RouterSettings merge(String... args) {
    return new SettingsMerger(new DefaultSettings(), new CliSettings(args)).merge();
  }

  /** Number of log entries so far; entries after this index were logged by the test. */
  private static int logMark() {
    return FRLogger.getLogEntries().getEntries(null, null).length;
  }

  private static boolean warnedAbout(int mark, String flag) {
    LogEntry[] entries = FRLogger.getLogEntries().getEntries(null, null);
    return Arrays.stream(entries, Math.min(mark, entries.length), entries.length)
        .filter(e -> e.getType() == LogEntryType.Warning)
        .map(LogEntry::getMessage)
        .anyMatch(m -> m.contains("'" + flag + "'"));
  }

  @Test
  void itemSelectionStrategyFlagSetsPrioritizedAndWarns() {
    int mark = logMark();

    RouterSettings merged = merge("-is", "prioritized");

    assertEquals(ItemSelectionStrategy.PRIORITIZED, merged.optimizer.itemSelectionStrategy);
    assertTrue(warnedAbout(mark, "-is"));
  }

  @Test
  void itemSelectionStrategyFlagSetsSequentialAndWarns() {
    int mark = logMark();

    RouterSettings merged = merge("-is", "prioritized", "-is", "sequential");

    assertEquals(ItemSelectionStrategy.SEQUENTIAL, merged.optimizer.itemSelectionStrategy);
    assertTrue(warnedAbout(mark, "-is"));
  }

  @Test
  void removedRandomItemSelectionWarnsAndKeepsTheDefault() {
    int mark = logMark();

    RouterSettings merged = merge("-is", "random");

    assertEquals(ItemSelectionStrategy.SEQUENTIAL, merged.optimizer.itemSelectionStrategy);
    assertTrue(warnedAbout(mark, "-is"));
  }

  @Test
  void deprecatedMaxPassesFlagWarns() {
    int mark = logMark();

    RouterSettings merged = merge("-mp", "5");

    assertEquals(5, merged.autorouter.maxPasses);
    assertTrue(warnedAbout(mark, "-mp"));
  }

  @Test
  void deprecatedMaxThreadsFlagWarns() {
    int mark = logMark();

    RouterSettings merged = merge("-mt", "2");

    assertEquals(2, merged.getOptimizerMaxThreads());
    assertTrue(warnedAbout(mark, "-mt"));
  }

  @Test
  void deprecatedMaxThreadsZeroOrNegativeWarns() {
    int mark = logMark();

    merge("-mt", "0");
    assertTrue(warnedAbout(mark, "-mt"));

    int markNeg = logMark();
    merge("-mt", "-1");
    assertTrue(warnedAbout(markNeg, "-mt"));
  }

  @Test
  void removedUpdateStrategyFlagsWarn() {
    int mark = logMark();

    merge("-us", "hybrid", "-hr", "1:1");

    assertTrue(warnedAbout(mark, "-us"));
    assertTrue(warnedAbout(mark, "-hr"));
  }

  @Test
  void removedIntermediateStagesFlagWarns() {
    int mark = logMark();

    RouterSettings merged = merge("-im", "-mp", "5");

    assertTrue(warnedAbout(mark, "-im"));
    assertEquals(5, merged.autorouter.maxPasses);
  }

  @Test
  void deprecatedImprovementThresholdFlagIsStillApplied() {
    RouterSettings merged = merge("-oit", "5");

    assertEquals(5.0f, merged.optimizer.optimizationImprovementThreshold);
  }
}
