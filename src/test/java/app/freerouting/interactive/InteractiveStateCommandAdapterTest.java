package app.freerouting.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.commands.InteractiveCommand;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InteractiveStateCommandAdapterTest {

  @Test
  void leftClickCommandDelegatesToStateMethod() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    when(manager.get_locale()).thenReturn(Locale.ENGLISH);

    RecordingState state = new RecordingState(manager);
    RecordingState nextState = new RecordingState(manager);
    FloatPoint location = new FloatPoint(12.5, 7.25);
    state.leftClickResult = nextState;

    InteractiveCommand command = state.left_button_clicked_command(location);

    InteractiveState result = command.execute();

    assertTrue(state.leftClickCalled);
    assertEquals(location, state.lastLeftClickLocation);
    assertSame(nextState, result);
  }

  @Test
  void cancelCommandDelegatesToStateMethod() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    when(manager.get_locale()).thenReturn(Locale.ENGLISH);

    RecordingState state = new RecordingState(manager);
    RecordingState nextState = new RecordingState(manager);
    state.cancelResult = nextState;

    InteractiveState result = state.cancel_command().execute();

    assertTrue(state.cancelCalled);
    assertSame(nextState, result);
  }

  private static final class RecordingState extends InteractiveState {

    private boolean leftClickCalled;
    private FloatPoint lastLeftClickLocation;
    private InteractiveState leftClickResult;

    private boolean cancelCalled;
    private InteractiveState cancelResult;

    private RecordingState(GuiBoardManager boardManager) {
      super(null, boardManager);
    }

    @Override
    public InteractiveState left_button_clicked(FloatPoint p_location) {
      this.leftClickCalled = true;
      this.lastLeftClickLocation = p_location;
      return leftClickResult;
    }

    @Override
    public InteractiveState cancel() {
      this.cancelCalled = true;
      return cancelResult;
    }
  }
}