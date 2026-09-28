package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.gui.windows.routing.WindowAutorouteParameterState;
import org.junit.jupiter.api.Test;

/** Characterizes the validation and timeout rules used by the autoroute parameter dialog. */
class WindowAutorouteParameterStateTest {

  @Test
  void clampsIntegerAndPositiveDoubleInputs() {
    assertEquals(1, WindowAutorouteParameterState.normalizeIntInput(-5, 4, 1, 9));
    assertEquals(9, WindowAutorouteParameterState.normalizeIntInput(20, 4, 1, 9));
    assertEquals(4, WindowAutorouteParameterState.normalizeIntInput("4", 4, 1, 9));
    assertEquals(4.5, WindowAutorouteParameterState.normalizePositiveDoubleInput(4.5, 2.0));
    assertEquals(2.0, WindowAutorouteParameterState.normalizePositiveDoubleInput(0, 2.0));
  }

  @Test
  void parsesAndFormatsBoundedTimeouts() {
    var timeout = WindowAutorouteParameterState.parseTimeout("01:02:03");

    assertEquals(1, timeout.hours());
    assertEquals(2, timeout.minutes());
    assertEquals(3, timeout.seconds());
    assertEquals("01:02:03", WindowAutorouteParameterState.buildTimeout(1, 2, 3));
    assertEquals(
        "1 hours 2 minutes", WindowAutorouteParameterState.formatTimeout(timeout.totalSeconds()));
  }

  @Test
  void capsTimeoutAtFourWeeks() {
    var timeout = WindowAutorouteParameterState.parseTimeout("999:99:99");

    assertEquals(672, timeout.hours());
    assertEquals(0, timeout.minutes());
    assertEquals(0, timeout.seconds());
    assertEquals(2_419_200L, timeout.totalSeconds());
    assertEquals("672:00:00", WindowAutorouteParameterState.buildTimeout(999, 99, 99));
    assertEquals(
        "28 days 0 hours 0 minutes",
        WindowAutorouteParameterState.formatTimeout(timeout.totalSeconds()));
  }

  @Test
  void formatsTimeoutWithDaysHoursMinutesThresholds() {
    // Greater than 24 hours: "x days y hours z minutes"
    assertEquals(
        "1 days 1 hours 10 minutes",
        WindowAutorouteParameterState.formatTimeout((25 * 3600L) + (10 * 60L)));
    assertEquals(
        "28 days 0 hours 0 minutes", WindowAutorouteParameterState.formatTimeout(2_419_200L));

    // Lower or equal to 24 hours (and >= 1 hour): "y hours z minutes"
    assertEquals("24 hours 0 minutes", WindowAutorouteParameterState.formatTimeout(24 * 3600L));
    assertEquals("1 hours 2 minutes", WindowAutorouteParameterState.formatTimeout(3720L));

    // Less than 1 hour: "z minutes"
    assertEquals("45 minutes", WindowAutorouteParameterState.formatTimeout(45 * 60L));
    assertEquals("0 minutes", WindowAutorouteParameterState.formatTimeout(30L));
    assertEquals("0 minutes", WindowAutorouteParameterState.formatTimeout(0L));
  }

  @Test
  void decomposesAndBuildsTimeoutsWithUnits() {
    // Weeks
    var decomposedWeeks = WindowAutorouteParameterState.decomposeTimeout(14 * 86400L);
    assertEquals(2, decomposedWeeks.value());
    assertEquals(WindowAutorouteParameterState.TimeoutUnit.WEEKS, decomposedWeeks.unit());
    assertEquals(
        14 * 86400L,
        WindowAutorouteParameterState.toSeconds(
            2, WindowAutorouteParameterState.TimeoutUnit.WEEKS));
    assertEquals(
        "336:00:00",
        WindowAutorouteParameterState.buildTimeout(
            2, WindowAutorouteParameterState.TimeoutUnit.WEEKS));

    // Days
    var decomposedDays = WindowAutorouteParameterState.decomposeTimeout(3 * 86400L);
    assertEquals(3, decomposedDays.value());
    assertEquals(WindowAutorouteParameterState.TimeoutUnit.DAYS, decomposedDays.unit());
    assertEquals(
        3 * 86400L,
        WindowAutorouteParameterState.toSeconds(3, WindowAutorouteParameterState.TimeoutUnit.DAYS));
    assertEquals(
        "72:00:00",
        WindowAutorouteParameterState.buildTimeout(
            3, WindowAutorouteParameterState.TimeoutUnit.DAYS));

    // Hours
    var decomposedHours = WindowAutorouteParameterState.decomposeTimeout(12 * 3600L);
    assertEquals(12, decomposedHours.value());
    assertEquals(WindowAutorouteParameterState.TimeoutUnit.HOURS, decomposedHours.unit());
    assertEquals(
        12 * 3600L,
        WindowAutorouteParameterState.toSeconds(
            12, WindowAutorouteParameterState.TimeoutUnit.HOURS));
    assertEquals(
        "12:00:00",
        WindowAutorouteParameterState.buildTimeout(
            12, WindowAutorouteParameterState.TimeoutUnit.HOURS));

    // Minutes
    var decomposedMinutes = WindowAutorouteParameterState.decomposeTimeout(45 * 60L);
    assertEquals(45, decomposedMinutes.value());
    assertEquals(WindowAutorouteParameterState.TimeoutUnit.MINUTES, decomposedMinutes.unit());
    assertEquals(
        45 * 60L,
        WindowAutorouteParameterState.toSeconds(
            45, WindowAutorouteParameterState.TimeoutUnit.MINUTES));
    assertEquals(
        "00:45:00",
        WindowAutorouteParameterState.buildTimeout(
            45, WindowAutorouteParameterState.TimeoutUnit.MINUTES));

    // Boundary: 4 weeks max
    var decomposedMax = WindowAutorouteParameterState.decomposeTimeout(2_419_200L);
    assertEquals(4, decomposedMax.value());
    assertEquals(WindowAutorouteParameterState.TimeoutUnit.WEEKS, decomposedMax.unit());
    assertEquals(
        "672:00:00",
        WindowAutorouteParameterState.buildTimeout(
            4, WindowAutorouteParameterState.TimeoutUnit.WEEKS));

    // Clamp over max
    assertEquals(
        2_419_200L,
        WindowAutorouteParameterState.toSeconds(
            10, WindowAutorouteParameterState.TimeoutUnit.WEEKS));
    assertEquals(
        "672:00:00",
        WindowAutorouteParameterState.buildTimeout(
            10, WindowAutorouteParameterState.TimeoutUnit.WEEKS));
  }

  @Test
  void normalizesTimeoutInput() {
    assertEquals(
        "672:00:00", WindowAutorouteParameterState.normalizeTimeoutInput("672:00:00", "00:30:00"));
    assertEquals(
        "120:30:00", WindowAutorouteParameterState.normalizeTimeoutInput("120:30:00", "00:30:00"));
    assertEquals(
        "00:30:00", WindowAutorouteParameterState.normalizeTimeoutInput("invalid", "00:30:00"));
  }
}
