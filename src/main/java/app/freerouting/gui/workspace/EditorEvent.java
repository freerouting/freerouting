package app.freerouting.gui.workspace;

import app.freerouting.geometry.planar.FloatPoint;

/** User input delivered to the concrete editor-state controller. */
public sealed interface EditorEvent
    permits EditorEvent.LeftClick,
        EditorEvent.MouseMoved,
        EditorEvent.MousePressed,
        EditorEvent.MouseDragged,
        EditorEvent.ButtonReleased,
        EditorEvent.MouseWheelMoved,
        EditorEvent.KeyTyped,
        EditorEvent.Complete,
        EditorEvent.Cancel {

  /** A left mouse click at a board location. */
  record LeftClick(FloatPoint location) implements EditorEvent {}

  /** A mouse-moved event. */
  record MouseMoved() implements EditorEvent {}

  /** A mouse press at a board location. */
  record MousePressed(FloatPoint location) implements EditorEvent {}

  /** A mouse drag at a board location. */
  record MouseDragged(FloatPoint location) implements EditorEvent {}

  /** A mouse-button release event. */
  record ButtonReleased() implements EditorEvent {}

  /** A mouse-wheel event with its rotation amount. */
  record MouseWheelMoved(int rotation) implements EditorEvent {}

  /** A typed keyboard character. */
  record KeyTyped(char keyChar) implements EditorEvent {}

  /** Completion of the current editor action. */
  record Complete() implements EditorEvent {}

  /** Cancellation of the current editor action. */
  record Cancel() implements EditorEvent {}
}
