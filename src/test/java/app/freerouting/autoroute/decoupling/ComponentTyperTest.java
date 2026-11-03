package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentTyperTest {

  private Pin createMockPin(
      int id, int layer, boolean isSmd, int net, int x, int y, int width, int height) {
    Pin pin = mock(Pin.class);
    when(pin.getId()).thenReturn(id);
    when(pin.firstLayer()).thenReturn(layer);
    when(pin.lastLayer()).thenReturn(isSmd ? layer : layer + 1);
    when(pin.netCount()).thenReturn(net > 0 ? 1 : 0);
    if (net > 0) {
      when(pin.getNetNumber(0)).thenReturn(net);
    }
    when(pin.getCenter()).thenReturn(new IntPoint(x, y));
    when(pin.boundingBox())
        .thenReturn(new IntBox(x - width / 2, y - height / 2, x + width / 2, y + height / 2));
    return pin;
  }

  @Test
  void testTwoPinSmdClassification() {
    Pin p0 = createMockPin(1, 0, true, 1, 0, 0, 50, 50);
    Pin p1 = createMockPin(2, 0, true, 2, 100, 0, 50, 50);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1));
    assertEquals(ComponentType.TWO_PIN_SMD, result.type());
    assertFalse(result.isXtalLike());
  }

  @Test
  void testTwoPinThroughHoleIsOther() {
    Pin p0 = createMockPin(1, 0, false, 1, 0, 0, 50, 50);
    Pin p1 = createMockPin(2, 0, false, 2, 100, 0, 50, 50);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1));
    assertEquals(ComponentType.OTHER, result.type());
  }

  @Test
  void testIcClassification() {
    Pin p0 = createMockPin(1, 0, true, 1, 0, 0, 40, 40);
    Pin p1 = createMockPin(2, 0, true, 2, 100, 0, 40, 40);
    Pin p2 = createMockPin(3, 0, true, 3, 0, 100, 40, 40);
    Pin p3 = createMockPin(4, 0, true, 4, 100, 100, 40, 40);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1, p2, p3));
    assertEquals(ComponentType.IC, result.type());
    assertFalse(result.isXtalLike());
  }

  @Test
  void testThroughHoleConnectorClassification() {
    // 3 through-hole pins in a non-collinear layout
    Pin p0 = createMockPin(1, 0, false, 1, 0, 0, 60, 60);
    Pin p1 = createMockPin(2, 0, false, 2, 100, 0, 60, 60);
    Pin p2 = createMockPin(3, 0, false, 3, 50, 100, 60, 60);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1, p2));
    assertEquals(ComponentType.CONNECTOR_LIKE, result.type());
  }

  @Test
  void testCollinearSmdHeaderClassification() {
    // 4 SMD pins along a single straight line with identical pads
    Pin p0 = createMockPin(1, 0, true, 1, 0, 0, 50, 50);
    Pin p1 = createMockPin(2, 0, true, 2, 100, 0, 50, 50);
    Pin p2 = createMockPin(3, 0, true, 3, 200, 0, 50, 50);
    Pin p3 = createMockPin(4, 0, true, 4, 300, 0, 50, 50);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1, p2, p3));
    assertEquals(ComponentType.CONNECTOR_LIKE, result.type());
  }

  @Test
  void testCrystalLikeDetection() {
    // 4 SMD pins in rectangle, pins 1 and 3 share GND net 1, pins 2 and 4 on nets 2 and 3
    Pin p0 = createMockPin(1, 0, true, 1, 0, 0, 40, 40);
    Pin p1 = createMockPin(2, 0, true, 2, 100, 0, 40, 40);
    Pin p2 = createMockPin(3, 0, true, 1, 100, 100, 40, 40);
    Pin p3 = createMockPin(4, 0, true, 3, 0, 100, 40, 40);

    ComponentTyper.TypingResult result = ComponentTyper.classify(List.of(p0, p1, p2, p3));
    assertTrue(result.isXtalLike());
  }
}
