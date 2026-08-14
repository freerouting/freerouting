package app.freerouting.gui.interactive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import app.freerouting.gui.session.EditorStateHandle;
import app.freerouting.gui.session.InteractiveCommand;
import org.junit.jupiter.api.Test;

class InteractiveCommandTest {

  @Test
  void noOpReturnsProvidedState() {
    EditorStateHandle state = mock(InteractiveState.class);

    InteractiveCommand command = InteractiveCommand.noOp(state);

    assertSame(state, command.execute());
    assertTrue(command.canExecute());
  }

  @Test
  void canExecuteCanBeOverriddenByTests() {
    InteractiveCommand command =
        new InteractiveCommand() {
          @Override
          public EditorStateHandle execute() {
            return null;
          }

          @Override
          public boolean canExecute() {
            return false;
          }
        };

    assertFalse(command.canExecute());
  }
}
