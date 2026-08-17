package app.freerouting.gui.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns mutable GUI-session mode state shared by routing and interaction actions.
 *
 * <p>The manager remains the public façade while this controller keeps read-only propagation and
 * listener notification in one place.
 */
final class GuiBoardSessionModeController {

  private final GuiBoardManager manager;
  private final List<Consumer<Boolean>> readOnlyEventListeners = new ArrayList<>();
  private boolean boardIsReadOnly;

  GuiBoardSessionModeController(GuiBoardManager manager) {
    this.manager = manager;
  }

  boolean isBoardReadOnly() {
    return boardIsReadOnly;
  }

  void setBoardReadOnly(boolean value) {
    boardIsReadOnly = value;
    manager.getWorkspaceSettings().setReadOnly(value);
    readOnlyEventListeners.forEach(listener -> listener.accept(value));
  }

  void addReadOnlyEventListener(Consumer<Boolean> listener) {
    readOnlyEventListeners.add(listener);
  }
}
