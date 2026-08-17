package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    assertEquals("1h 2m 3s", WindowAutorouteParameterState.formatTimeout(timeout.totalSeconds()));
  }

  @Test
  void capsTimeoutAtOneDay() {
    var timeout = WindowAutorouteParameterState.parseTimeout("99:99:99");

    assertEquals(24, timeout.hours());
    assertEquals(0, timeout.minutes());
    assertEquals(0, timeout.seconds());
    assertEquals("24:00:00", WindowAutorouteParameterState.buildTimeout(99, 99, 99));
  }
}
