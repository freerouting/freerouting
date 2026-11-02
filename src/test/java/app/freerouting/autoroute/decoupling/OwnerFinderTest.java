package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Pin;
import app.freerouting.geometry.planar.IntPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwnerFinderTest {

  private Pin createMockPin(int id, int compId, int net, int x, int y) {
    Pin pin = mock(Pin.class);
    when(pin.getId()).thenReturn(id);
    when(pin.getComponentId()).thenReturn(compId);
    when(pin.netCount()).thenReturn(1);
    when(pin.getNetNumber(0)).thenReturn(net);
    when(pin.getCenter()).thenReturn(new IntPoint(x, y));
    return pin;
  }

  @Test
  void testFindOwnersWithinRadius() {
    final BoardIndex index = mock(BoardIndex.class);
    final IdentifierConfig config = IdentifierConfig.defaultConfig();
    final double resolutionMm = 1000.0; // 1000 board units = 1 mm

    // Capacitor at (0, 0), rail is net 2
    Pin capPinA = createMockPin(1, 10, 2, 0, 0);
    Pin capPinB = createMockPin(2, 10, 1, 1000, 0);
    CapacitorScorer.EvaluationState capState =
        new CapacitorScorer.EvaluationState(10, capPinA, capPinB, 2, 1, 0, 500L, 500L);
    capState.gatePassed = true;
    capState.railNet = 2;
    capState.groundNet = 1;
    capState.score = 45;
    capState.verdict = Verdict.REVIEW;

    // IC 1 pin at (1000, 1000) -> distance sqrt(2) mm ~ 1.41 mm <= 7.5 mm
    Pin icPin1 = createMockPin(101, 50, 2, 1000, 1000);
    // IC 2 pin at (4000, 3000) -> distance 5.0 mm <= 7.5 mm
    Pin icPin2 = createMockPin(102, 51, 2, 4000, 3000);
    // IC 3 pin far away at (10000, 10000) -> distance ~ 14 mm > 7.5 mm
    Pin icPin3 = createMockPin(103, 52, 2, 10000, 10000);

    when(index.getPinsOfNet(2)).thenReturn(List.of(capPinA, icPin1, icPin2, icPin3));
    when(index.getComponentType(50)).thenReturn(ComponentType.IC);
    when(index.getComponentType(51)).thenReturn(ComponentType.IC);
    when(index.getComponentType(52)).thenReturn(ComponentType.IC);

    when(index.getPinsOfComponent(50))
        .thenReturn(List.of(icPin1, mock(Pin.class), mock(Pin.class))); // 3 pins
    when(index.getPinsOfComponent(51)).thenReturn(List.of(icPin2, mock(Pin.class))); // 2 pins

    List<CapacitorScorer.EvaluationState> states = new ArrayList<>(List.of(capState));

    OwnerFinder.findOwners(states, index, config, resolutionMm);

    // Verified: IC 1 and IC 2 within radius, IC 3 excluded
    assertEquals(2, capState.owners.size());
    assertEquals(101, capState.owners.get(0).pinId());
    assertEquals(50, capState.owners.get(0).componentId());
    assertEquals(3, capState.owners.get(0).componentPinCount());
    assertEquals(2000000L, capState.owners.get(0).distanceSquared());

    assertEquals(102, capState.owners.get(1).pinId());
    assertEquals(51, capState.owners.get(1).componentId());
    assertEquals(25000000L, capState.owners.get(1).distanceSquared());

    // Verified bonus awarded: 45 + 20 = 65 -> promoted to DECOUPLING (threshold 60)
    assertTrue(ReasonCode.OWNER_IN_RADIUS.isSet(capState.reasonMask));
    assertEquals(65, capState.score);
    assertEquals(Verdict.DECOUPLING, capState.verdict);
  }
}
