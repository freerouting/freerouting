package app.freerouting.gui.workspace;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.rendering.GraphicsContext;
import java.awt.geom.Point2D;
import java.util.Set;

/**
 * Coordinates user input between the GUI session and its opaque editor-state controller.
 *
 * <p>{@link GuiBoardManager} remains the public interaction façade. This collaborator owns the
 * screen-to-board conversion, event dispatch, and state-transition side effects so the session
 * manager does not also have to contain every Swing input handler.
 */
final class GuiBoardInteractionController {

  private final GuiBoardManager manager;

  GuiBoardInteractionController(GuiBoardManager manager) {
    this.manager = manager;
  }

  void leftButtonClicked(Point2D point) {
    if (manager.isBoardReadOnly()) {
      manager.stopAutorouterAndRouteOptimizer();
      return;
    }
    if (stateController() != null && graphicsContext() != null) {
      FloatPoint location = screenToBoard(point);
      EditorStateHandle returnState = dispatch(new EditorEvent.LeftClick(location));
      applyStateChange(returnState, true, false);
    }
  }

  void mouseMoved(Point2D point) {
    if (stateController() == null || graphicsContext() == null) {
      return;
    }

    FloatPoint currentMousePosition = screenToBoard(point);
    manager.setInteractionMousePosition(currentMousePosition);
    manager.screenMessages.setMousePosition(
        manager.getCoordinateTransform().boardToUser(currentMousePosition));

    if (manager.isBoardReadOnly()) {
      return;
    }

    EditorStateHandle returnState = dispatch(new EditorEvent.MouseMoved());
    Set<Item> hoverItem = manager.pickItems(currentMousePosition);
    BoardPanel panel = manager.getPanel();
    if (hoverItem.size() == 1) {
      panel.setToolTipText(hoverItem.iterator().next().getHoverInfo(manager.getLocale()));
    } else {
      panel.setToolTipText(null);
    }
    applyStateChange(returnState, true, false);
  }

  void mousePressed(Point2D point) {
    if (stateController() != null && graphicsContext() != null) {
      FloatPoint location = screenToBoard(point);
      manager.setInteractionMousePosition(location);
      EditorStateHandle returnState = dispatch(new EditorEvent.MousePressed(location));
      applyStateChange(returnState, false, false);
    }
  }

  void mouseDragged(Point2D point) {
    if (stateController() != null && graphicsContext() != null) {
      FloatPoint location = screenToBoard(point);
      manager.setInteractionMousePosition(location);
      EditorStateHandle returnState = dispatch(new EditorEvent.MouseDragged(location));
      applyStateChange(returnState, true, false);
    }
  }

  void buttonReleased() {
    if (stateController() != null) {
      EditorStateHandle returnState = dispatch(new EditorEvent.ButtonReleased());
      applyStateChange(returnState, true, false);
    }
  }

  void mouseWheelMoved(Point2D point, int rotation) {
    if (stateController() != null && graphicsContext() != null) {
      manager.setInteractionMousePosition(screenToBoard(point));
      EditorStateHandle returnState = dispatch(new EditorEvent.MouseWheelMoved(rotation));
      applyStateChange(returnState, true, false);
    }
  }

  void keyTypedAction(char keyChar) {
    if (manager.isBoardReadOnly() || stateController() == null || graphicsContext() == null) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.KeyTyped(keyChar));
    applyStateChange(returnState, true, true);
  }

  void returnFromState() {
    if (manager.isBoardReadOnly()) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.Complete());
    applyStateChange(returnState, true, false);
  }

  void cancelState() {
    if (manager.isBoardReadOnly()) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.Cancel());
    applyStateChange(returnState, true, false);
  }

  boolean changeLayerAction(int newLayer) {
    return !manager.isBoardReadOnly()
        && (stateController() == null || stateController().changeLayerAction(newLayer));
  }

  void setInspectMenuState() {
    if (stateController() != null) {
      stateController().setInspectMenuState();
    }
  }

  void setRouteMenuState() {
    if (stateController() != null) {
      stateController().setRouteMenuState();
    }
  }

  void setDragMenuState() {
    if (stateController() != null) {
      stateController().setDragMenuState();
    }
  }

  void startRoute(Point2D point) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    if (stateController() != null) {
      stateController().startRoute(screenToBoard(point));
    }
  }

  void selectItems(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().selectItems(screenToBoard(point));
  }

  void selectItems(Set<Item> items) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    manager.displayLayerMessage();
    if (stateController() != null) {
      stateController().selectItems(items);
    }
  }

  void selectItemsInRegion() {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().selectItemsInRegion();
  }

  void swapPins(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().swapPins(screenToBoard(point));
  }

  void zoomSelection() {
    if (stateController() != null && stateController().isInspectedState()) {
      stateController().zoomSelection();
    }
  }

  void toggleSelectAction(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isInspectedState()) {
      return;
    }
    stateController().toggleSelect(screenToBoard(point));
  }

  void displaySelectedItemInfo() {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isInspectedState()) {
      return;
    }
    stateController().displaySelectedItemInfo();
  }

  void extendSelectionToWholeNets() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeNets();
  }

  void extendSelectionToWholeComponents() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeComponents();
  }

  void extendSelectionToWholeConnectedSets() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeConnectedSets();
  }

  void extendSelectionToWholeConnections() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeConnections();
  }

  void toggleSelectedItemViolations() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().toggleSelectedItemViolations();
  }

  void turn45Degree(int factor) {
    if (!isReadOnlyMoveState()) {
      stateController().turn45Degree(factor);
    }
  }

  void changePlacementSide() {
    if (!isReadOnlyMoveState()) {
      stateController().changePlacementSide();
    }
  }

  void resetRotation() {
    if (stateController() != null && stateController().isMoveState()) {
      stateController().resetRotation();
    }
  }

  void zoomRegion() {
    if (stateController() != null) {
      stateController().zoomRegion();
    }
  }

  void startCircle(Point2D point) {
    startConstruction(point, Construction.CIRCLE);
  }

  void startTile(Point2D point) {
    startConstruction(point, Construction.TILE);
  }

  void startPolygonshapeItem(Point2D point) {
    startConstruction(point, Construction.POLYGON);
  }

  void startAddingHole(Point2D point) {
    startConstruction(point, Construction.HOLE);
  }

  private boolean isReadOnlyInspectedState() {
    return manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isInspectedState();
  }

  private boolean isReadOnlyMoveState() {
    return manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMoveState();
  }

  private void startConstruction(Point2D point, Construction construction) {
    if (manager.isBoardReadOnly() || stateController() == null) {
      return;
    }
    FloatPoint location = screenToBoard(point);
    switch (construction) {
      case CIRCLE -> stateController().startCircle(location);
      case TILE -> stateController().startTile(location);
      case POLYGON -> stateController().startPolygon(location);
      case HOLE -> stateController().startHole(location);
      default -> throw new AssertionError("Unhandled construction: " + construction);
    }
  }

  private EditorStateController stateController() {
    return manager.getInteractionStateController();
  }

  private GraphicsContext graphicsContext() {
    return manager.getGraphicsContext();
  }

  private FloatPoint screenToBoard(Point2D point) {
    return graphicsContext().coordinateTransform.screenToBoard(point);
  }

  private EditorStateHandle dispatch(EditorEvent event) {
    EditorStateController controller = stateController();
    return controller == null ? null : controller.dispatch(event);
  }

  private void applyStateChange(
      EditorStateHandle nextState, boolean repaintAfterChange, boolean updateToolbarSelection) {
    manager.applyInteractionStateChange(nextState, repaintAfterChange, updateToolbarSelection);
  }

  private enum Construction {
    CIRCLE,
    TILE,
    POLYGON,
    HOLE
  }
}
