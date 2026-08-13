package app.freerouting.gui.session;

/** Opaque handle for the active editor state. */
public interface EditorStateHandle {

  /** Returns the stable mode identifier used by views. */
  default EditorStateKind kind() {
    return EditorStateKind.UNKNOWN;
  }

  /** Returns whether this state exposes an item selection. */
  default boolean hasSelection() {
    return false;
  }
}
