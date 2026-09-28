package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class PinObstacleTest {

  @Test
  void sameNetViaWithAttachDisallowedIsNotObstacleForSmdPin() {
    Pin pin = mock(Pin.class, Answers.CALLS_REAL_METHODS);
    doReturn(true).when(pin).drillAllowed();

    Via via = mock(Via.class);
    when(via.sharesNet(pin)).thenReturn(true);

    assertFalse(pin.isObstacle(via));
  }
}
