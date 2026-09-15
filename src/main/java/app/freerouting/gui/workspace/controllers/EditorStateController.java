package app.freerouting.gui.workspace.controllers;

import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.session.EditorEvent;
import app.freerouting.gui.workspace.session.EditorStateHandle;
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
  public EditorStateHandle currentState();

  /** Installs an opaque editor state. */
  public void setState(EditorStateHandle state);

  /** Dispatches an input event and returns the resulting state. */
  public EditorStateHandle dispatch(EditorEvent event);

  /** Draws the current editor state. */
  public void draw(Graphics graphics);

  /** Returns the context menu for the current state. */
  public JPopupMenu popupMenu();

  /** Returns whether an interactive drag is active. */
  public boolean isInteractiveDrag();

  /** Returns whether the current state is a menu state. */
  public boolean isMenuState();

  /** Returns whether the current state is an inspected state. */
  public boolean isInspectedState();

  /** Returns whether the current state is a move state. */
  public boolean isMoveState();

  /** Switches to the inspect-menu state. */
  public void setInspectMenuState();

  /** Switches to the route-menu state. */
  public void setRouteMenuState();

  /** Switches to the drag-menu state. */
  public void setDragMenuState();

  /** Starts routing at a board location. */
  public void startRoute(FloatPoint location);

  /** Selects items at a board location. */
  public void selectItems(FloatPoint location);

  /** Selects the supplied items. */
  public void selectItems(Set<Item> items);

  /** Selects items in the active region. */
  public void selectItemsInRegion();

  /** Swaps pins at a board location. */
  public void swapPins(FloatPoint location);

  /** Zooms to the current selection. */
  public void zoomSelection();

  /** Toggles selection at a board location. */
  public void toggleSelect(FloatPoint location);

  /** Displays information for selected items. */
  public void displaySelectedItemInfo();

  /** Filters the current selection. */
  public void filterSelection();

  /** Extends selection to whole nets. */
  public void extendSelectionToWholeNets();

  /** Extends selection to whole components. */
  public void extendSelectionToWholeComponents();

  /** Extends selection to whole connected sets. */
  public void extendSelectionToWholeConnectedSets();

  /** Extends selection to whole connections. */
  public void extendSelectionToWholeConnections();

  /** Toggles selected-item violation display. */
  public void toggleSelectedItemViolations();

  /** Rotates the active placement by 45-degree increments. */
  public void turn45Degree(int factor);

  /** Changes the active placement side. */
  public void changePlacementSide();

  /** Changes the active layer and reports success. */
  public boolean changeLayerAction(int newLayer);

  /** Zooms to the active region. */
  public void zoomRegion();

  /** Starts circle construction. */
  public void startCircle(FloatPoint location);

  /** Starts tile construction. */
  public void startTile(FloatPoint location);

  /** Starts polygon construction. */
  public void startPolygon(FloatPoint location);

  /** Starts hole construction. */
  public void startHole(FloatPoint location);

  /** Allows implementations to perform a view-specific reset without exposing state classes. */
  default void resetRotation() {}
}
