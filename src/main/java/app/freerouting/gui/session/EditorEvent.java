package app.freerouting.gui.session;

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

  record LeftClick(FloatPoint location) implements EditorEvent {}

  record MouseMoved() implements EditorEvent {}

  record MousePressed(FloatPoint location) implements EditorEvent {}

  record MouseDragged(FloatPoint location) implements EditorEvent {}

  record ButtonReleased() implements EditorEvent {}

  record MouseWheelMoved(int rotation) implements EditorEvent {}

  record KeyTyped(char keyChar) implements EditorEvent {}

  record Complete() implements EditorEvent {}

  record Cancel() implements EditorEvent {}
}
