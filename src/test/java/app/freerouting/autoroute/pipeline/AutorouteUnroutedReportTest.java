package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.FailureReason;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.io.specctra.DsnTestFixtures;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Unit tests for {@link AutorouteUnroutedReport} failure-reason inclusion. */
class AutorouteUnroutedReportTest {

  private static RoutingBoard loadSampleBoard() {
    try {
      return DsnTestFixtures.loadBoard("Issue143-rpi_splitter.dsn");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load sample board fixture", e);
    }
  }

  @Test
  void reasonPrintedForKnownNet() {
    RoutingBoard board = loadSampleBoard();
    // Discover an actual unrouted net name from the board itself
    String baseline = AutorouteUnroutedReport.build(board, Map.of());
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("Net '([^']+)'").matcher(baseline);
    assertTrue(
        matcher.find(),
        () -> "Fixture board must have at least one unrouted net, got:\n" + baseline);
    String netName = matcher.group(1);

    FailureReason reason =
        new FailureReason(
            FailureReason.FailureType.CLEARANCE_WALKED_EXHAUSTED, "All expansion doors blocked.");
    String report = AutorouteUnroutedReport.build(board, Map.of(netName, reason));
    assertTrue(
        report.contains(
            "Last failure reason (CLEARANCE_WALKED_EXHAUSTED): All expansion doors blocked."),
        () -> "Report should contain the failure reason line, got:\n" + report);
  }

  @Test
  void reasonNotPrintedForUnknownNet() {
    RoutingBoard board = loadSampleBoard();
    FailureReason reason =
        new FailureReason(
            FailureReason.FailureType.CLEARANCE_WALKED_EXHAUSTED, "All expansion doors blocked.");
    String report = AutorouteUnroutedReport.build(board, Map.of("net_that_does_not_exist", reason));
    assertFalse(report.contains("Last failure reason"), () -> report);
  }

  @Test
  void reasonNotPrintedWithoutEntry() {
    RoutingBoard board = loadSampleBoard();
    String report = AutorouteUnroutedReport.build(board, Map.of());
    assertFalse(report.contains("Last failure reason"), () -> report);
  }

  @Test
  void legacyOverloadStillWorks() {
    RoutingBoard board = loadSampleBoard();
    String report = AutorouteUnroutedReport.build(board);
    assertFalse(report.contains("Last failure reason"), () -> report);
  }

  /**
   * The report pipeline must stay reason-agnostic: every {@link FailureReason.FailureType} —
   * including the newer {@code START_PIN_ESCAPE_FAILED} and {@code VIA_PLACEMENT_BLOCKED} — must
   * round-trip into the printed report line unchanged.
   */
  @ParameterizedTest
  @EnumSource(FailureReason.FailureType.class)
  void everyFailureTypeIsPrintedForKnownNet(FailureReason.FailureType type) {
    RoutingBoard board = loadSampleBoard();
    String baseline = AutorouteUnroutedReport.build(board, Map.of());
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("Net '([^']+)'").matcher(baseline);
    assertTrue(
        matcher.find(),
        () -> "Fixture board must have at least one unrouted net, got:\n" + baseline);
    String netName = matcher.group(1);

    FailureReason reason = new FailureReason(type, "Test failure description.");
    String report = AutorouteUnroutedReport.build(board, Map.of(netName, reason));
    assertTrue(
        report.contains("Last failure reason (" + type.name() + "): Test failure description."),
        () -> "Report should contain the failure reason line for " + type + ", got:\n" + report);
  }
}
