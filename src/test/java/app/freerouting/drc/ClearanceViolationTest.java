package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.ComponentOutline;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import org.junit.jupiter.api.Test;

class ClearanceViolationTest {

  @Test
  void testPinToPinIsUnfixableAndCategorizedCorrectly() {
    Pin pin1 = mock(Pin.class);
    when(pin1.isRoutable()).thenReturn(false);
    Pin pin2 = mock(Pin.class);
    when(pin2.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(pin1, pin2, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.PIN_TO_PIN, violation.getCategory());
  }

  @Test
  void testPinToOutlineIsUnfixableAndCategorizedCorrectly() {
    Pin pin = mock(Pin.class);
    when(pin.isRoutable()).thenReturn(false);
    BoardOutline outline = mock(BoardOutline.class);
    when(outline.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(pin, outline, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.PIN_TO_OUTLINE_OR_KEEPOUT, violation.getCategory());
  }

  @Test
  void testPinToKeepoutIsUnfixableAndCategorizedCorrectly() {
    Pin pin = mock(Pin.class);
    when(pin.isRoutable()).thenReturn(false);
    ObstacleArea keepout = mock(ObstacleArea.class);
    when(keepout.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(keepout, pin, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.PIN_TO_OUTLINE_OR_KEEPOUT, violation.getCategory());
  }

  @Test
  void testPinToComponentOutlineIsUnfixableAndCategorizedCorrectly() {
    Pin pin = mock(Pin.class);
    when(pin.isRoutable()).thenReturn(false);
    ComponentOutline compOutline = mock(ComponentOutline.class);
    when(compOutline.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(pin, compOutline, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.PIN_TO_OUTLINE_OR_KEEPOUT, violation.getCategory());
  }

  @Test
  void testFixedRouteIsUnfixableAndCategorizedCorrectly() {
    Pin pin = mock(Pin.class);
    when(pin.isRoutable()).thenReturn(false);
    Trace fixedTrace = mock(Trace.class);
    when(fixedTrace.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(pin, fixedTrace, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.FIXED_ROUTE, violation.getCategory());

    Via fixedVia = mock(Via.class);
    when(fixedVia.isRoutable()).thenReturn(false);
    ClearanceViolation viaViolation =
        new ClearanceViolation(fixedVia, fixedTrace, null, 0, 100.0, 50.0);

    assertTrue(viaViolation.isUnfixable());
    assertEquals(ClearanceViolation.Category.FIXED_ROUTE, viaViolation.getCategory());
  }

  @Test
  void testRoutableTraceIsPotentiallyFixable() {
    Pin pin = mock(Pin.class);
    when(pin.isRoutable()).thenReturn(false);
    Trace routableTrace = mock(Trace.class);
    when(routableTrace.isRoutable()).thenReturn(true);

    ClearanceViolation violation = new ClearanceViolation(pin, routableTrace, null, 0, 100.0, 50.0);

    assertFalse(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.POTENTIALLY_FIXABLE, violation.getCategory());
  }

  @Test
  void testTwoRoutableTracesArePotentiallyFixable() {
    Trace trace1 = mock(Trace.class);
    when(trace1.isRoutable()).thenReturn(true);
    Trace trace2 = mock(Trace.class);
    when(trace2.isRoutable()).thenReturn(true);

    ClearanceViolation violation = new ClearanceViolation(trace1, trace2, null, 0, 100.0, 50.0);

    assertFalse(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.POTENTIALLY_FIXABLE, violation.getCategory());
  }

  @Test
  void testOtherUnfixableItemsCategorizedCorrectly() {
    Item item1 = mock(Item.class);
    when(item1.isRoutable()).thenReturn(false);
    Item item2 = mock(Item.class);
    when(item2.isRoutable()).thenReturn(false);

    ClearanceViolation violation = new ClearanceViolation(item1, item2, null, 0, 100.0, 50.0);

    assertTrue(violation.isUnfixable());
    assertEquals(ClearanceViolation.Category.OTHER_UNFIXABLE, violation.getCategory());
  }
}
