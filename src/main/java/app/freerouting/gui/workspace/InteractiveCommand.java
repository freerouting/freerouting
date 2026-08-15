package app.freerouting.gui.workspace;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Session-owned command boundary for editor-state transitions.
 *
 * <p>The result is deliberately an opaque {@link EditorStateHandle}; concrete states remain in
 * {@code gui.interactive}.
 */
@FunctionalInterface
public interface InteractiveCommand {

  /** Creates a command from a state-producing supplier. */
  static InteractiveCommand from(Supplier<? extends EditorStateHandle> action) {
    Objects.requireNonNull(action, "action");
    return action::get;
  }

  /** Creates a command that keeps the supplied state unchanged. */
  static InteractiveCommand noOp(EditorStateHandle state) {
    return () -> state;
  }

  /** Executes the command and returns the next opaque state handle. */
  EditorStateHandle execute();

  /** Allows callers to suppress a command before execution. */
  default boolean canExecute() {
    return true;
  }

  /** Optional undo hook for future command-history integration. */
  default void undo() {}
}
