package app.freerouting.gui.windows.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindowAutorouteParameterStateTest {

  @Test
  @DisplayName("normalizeDoubleInput respects min, max and fallbacks")
  void testNormalizeDoubleInput() {
    // Normal numeric inputs
    assertEquals(4.5, WindowAutorouteParameterState.normalizeDoubleInput(4.5, 4.0, 0.5, 50.0));
    // clamped to min
    assertEquals(0.5, WindowAutorouteParameterState.normalizeDoubleInput(0.1, 4.0, 0.5, 50.0));
    // clamped to max
    assertEquals(50.0, WindowAutorouteParameterState.normalizeDoubleInput(99.0, 4.0, 0.5, 50.0));

    // String inputs with dot and comma
    assertEquals(2.5, WindowAutorouteParameterState.normalizeDoubleInput("2.5", 4.0, 0.5, 50.0));
    // comma support
    assertEquals(2.5, WindowAutorouteParameterState.normalizeDoubleInput("2,5", 4.0, 0.5, 50.0));
    // clamped
    assertEquals(50.0, WindowAutorouteParameterState.normalizeDoubleInput("100,0", 4.0, 0.5, 50.0));

    // Invalid string or null fallbacks
    assertEquals(
        4.0, WindowAutorouteParameterState.normalizeDoubleInput("invalid", 4.0, 0.5, 50.0));
    assertEquals(4.0, WindowAutorouteParameterState.normalizeDoubleInput(null, 4.0, 0.5, 50.0));
    assertEquals(4.0, WindowAutorouteParameterState.normalizeDoubleInput("", 4.0, 0.5, 50.0));
    assertEquals(4.0, WindowAutorouteParameterState.normalizeDoubleInput("   ", 4.0, 0.5, 50.0));
  }

  @Test
  @DisplayName("normalizeIntInput respects clamping bounds and invalid types")
  void testNormalizeIntInput() {
    assertEquals(10, WindowAutorouteParameterState.normalizeIntInput(10, 5, 1, 100));
    assertEquals(1, WindowAutorouteParameterState.normalizeIntInput(-5, 5, 1, 100));
    assertEquals(100, WindowAutorouteParameterState.normalizeIntInput(500, 5, 1, 100));
    assertEquals(5, WindowAutorouteParameterState.normalizeIntInput("not a number", 5, 1, 100));
    assertEquals(5, WindowAutorouteParameterState.normalizeIntInput(null, 5, 1, 100));
  }
}
