package app.freerouting.gui.workspace;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Graphics;
import java.util.Set;
import javax.swing.JPopupMenu;

/**
 * Inversion seam for concrete editor-state orchestration.
 *
 * <p>The implementation belongs to {@code gui.interactive}; this interface is owned by the GUI
 * session so {@link GuiBoardManager} never needs to name a concrete state.
 */
public interface EditorStateController {

  /** Returns the current opaque editor state. */
  EditorStateHandle currentState();

  /** Installs an opaque editor state. */
  void setState(EditorStateHandle state);

  /** Dispatches an input event and returns the resulting state. */
  EditorStateHandle dispatch(EditorEvent event);

  /** Draws the current editor state. */
  void draw(Graphics graphics);

  /** Returns the context menu for the current state. */
  JPopupMenu popupMenu();

  /** Returns whether an interactive drag is active. */
  boolean isInteractiveDrag();

  /** Returns whether the current state is a menu state. */
  boolean isMenuState();

  /** Returns whether the current state is an inspected state. */
  boolean isInspectedState();

  /** Returns whether the current state is a move state. */
  boolean isMoveState();

  /** Switches to the inspect-menu state. */
  void setInspectMenuState();

  /** Switches to the route-menu state. */
  void setRouteMenuState();

  /** Switches to the drag-menu state. */
  void setDragMenuState();

  /** Starts routing at a board location. */
  void startRoute(FloatPoint location);

  /** Selects items at a board location. */
  void selectItems(FloatPoint location);

  /** Selects the supplied items. */
  void selectItems(Set<Item> items);

  /** Selects items in the active region. */
  void selectItemsInRegion();

  /** Swaps pins at a board location. */
  void swapPins(FloatPoint location);

  /** Zooms to the current selection. */
  void zoomSelection();

  /** Toggles selection at a board location. */
  void toggleSelect(FloatPoint location);

  /** Displays information for selected items. */
  void displaySelectedItemInfo();

  /** Filters the current selection. */
  void filterSelection();

  /** Extends selection to whole nets. */
  void extendSelectionToWholeNets();

  /** Extends selection to whole components. */
  void extendSelectionToWholeComponents();

  /** Extends selection to whole connected sets. */
  void extendSelectionToWholeConnectedSets();

  /** Extends selection to whole connections. */
  void extendSelectionToWholeConnections();

  /** Toggles selected-item violation display. */
  void toggleSelectedItemViolations();

  /** Rotates the active placement by 45-degree increments. */
  void turn45Degree(int factor);

  /** Changes the active placement side. */
  void changePlacementSide();

  /** Changes the active layer and reports success. */
  boolean changeLayerAction(int newLayer);

  /** Zooms to the active region. */
  void zoomRegion();

  /** Starts circle construction. */
  void startCircle(FloatPoint location);

  /** Starts tile construction. */
  void startTile(FloatPoint location);

  /** Starts polygon construction. */
  void startPolygon(FloatPoint location);

  /** Starts hole construction. */
  void startHole(FloatPoint location);

  /** Allows implementations to perform a view-specific reset without exposing state classes. */
  default void resetRotation() {}
}
