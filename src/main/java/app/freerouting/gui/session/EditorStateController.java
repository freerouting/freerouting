package app.freerouting.gui.session;

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

  EditorStateHandle currentState();

  void setState(EditorStateHandle state);

  EditorStateHandle dispatch(EditorEvent event);

  void draw(Graphics graphics);

  JPopupMenu popupMenu();

  boolean isInteractiveDrag();

  boolean isMenuState();

  boolean isInspectedState();

  boolean isMoveState();

  void setInspectMenuState();

  void setRouteMenuState();

  void setDragMenuState();

  void startRoute(FloatPoint location);

  void selectItems(FloatPoint location);

  void selectItems(Set<Item> items);

  void selectItemsInRegion();

  void swapPins(FloatPoint location);

  void zoomSelection();

  void toggleSelect(FloatPoint location);

  void displaySelectedItemInfo();

  void filterSelection();

  void extendSelectionToWholeNets();

  void extendSelectionToWholeComponents();

  void extendSelectionToWholeConnectedSets();

  void extendSelectionToWholeConnections();

  void toggleSelectedItemViolations();

  void turn45Degree(int factor);

  void changePlacementSide();

  boolean changeLayerAction(int newLayer);

  void zoomRegion();

  void startCircle(FloatPoint location);

  void startTile(FloatPoint location);

  void startPolygon(FloatPoint location);

  void startHole(FloatPoint location);

  /** Allows implementations to perform a view-specific reset without exposing state classes. */
  default void resetRotation() {}
}
