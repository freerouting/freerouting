package app.freerouting.interactive.commands;

import app.freerouting.interactive.InteractiveState;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Command abstraction for interactive state actions.
 *
 * <p>The command shape makes event handling test-friendly: callers can mock command creation,
 * execute commands in isolation, and verify transitions without constructing full GUI flows.
 */
@FunctionalInterface
public interface InteractiveCommand {

  /**
   * Executes the command and returns the next state.
   */
  InteractiveState execute();

  /**
   * Allows pre-checking if the command should run. Defaults to true.
   */
  default boolean canExecute() {
    return true;
  }

  /**
   * Optional undo hook for future command-history integration.
   */
  default void undo() {
  }

  /**
   * Creates a command from a state-producing supplier.
   */
  static InteractiveCommand from(Supplier<InteractiveState> action) {
    Objects.requireNonNull(action, "action");
    return action::get;
  }

  /**
   * Creates a command that keeps the current state unchanged.
   */
  static InteractiveCommand noOp(InteractiveState state) {
    return () -> state;
  }
}