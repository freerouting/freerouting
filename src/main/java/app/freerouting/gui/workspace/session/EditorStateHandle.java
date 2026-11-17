package app.freerouting.gui.workspace.session;

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

  /**
   * Returns the collection of selected items if supported by this state, or empty collection
   * otherwise.
   */
  default java.util.Collection<app.freerouting.board.model.items.Item> getSelectedItems() {
    return java.util.Collections.emptyList();
  }
}
