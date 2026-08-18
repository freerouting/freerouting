package app.freerouting.gui.workspace.controllers;

import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;
import app.freerouting.gui.workspace.session.EditorEvent;
import app.freerouting.gui.workspace.session.EditorStateHandle;
import java.awt.geom.Point2D;
import java.util.Set;

/**
 * Coordinates user input between the GUI session and its opaque editor-state controller.
 *
 * <p>{@link GuiBoardManager} remains the public interaction façade. This collaborator owns the
 * screen-to-board conversion, event dispatch, and state-transition side effects so the session
 * manager does not also have to contain every Swing input handler.
 */
public final class GuiBoardInteractionController {

  private final GuiBoardManager manager;
  private FloatPoint currentMousePosition;

  public GuiBoardInteractionController(GuiBoardManager manager) {
    this.manager = manager;
  }

  public FloatPoint getCurrentMousePosition() {
    return currentMousePosition;
  }

  public void setCurrentMousePosition(FloatPoint position) {
    currentMousePosition = position;
  }

  public void leftButtonClicked(Point2D point) {
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

  public void mouseMoved(Point2D point) {
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

  public void mousePressed(Point2D point) {
    if (stateController() != null && graphicsContext() != null) {
      FloatPoint location = screenToBoard(point);
      manager.setInteractionMousePosition(location);
      EditorStateHandle returnState = dispatch(new EditorEvent.MousePressed(location));
      applyStateChange(returnState, false, false);
    }
  }

  public void mouseDragged(Point2D point) {
    if (stateController() != null && graphicsContext() != null) {
      FloatPoint location = screenToBoard(point);
      manager.setInteractionMousePosition(location);
      EditorStateHandle returnState = dispatch(new EditorEvent.MouseDragged(location));
      applyStateChange(returnState, true, false);
    }
  }

  public void buttonReleased() {
    if (stateController() != null) {
      EditorStateHandle returnState = dispatch(new EditorEvent.ButtonReleased());
      applyStateChange(returnState, true, false);
    }
  }

  public void mouseWheelMoved(Point2D point, int rotation) {
    if (stateController() != null && graphicsContext() != null) {
      manager.setInteractionMousePosition(screenToBoard(point));
      EditorStateHandle returnState = dispatch(new EditorEvent.MouseWheelMoved(rotation));
      applyStateChange(returnState, true, false);
    }
  }

  public void keyTypedAction(char keyChar) {
    if (manager.isBoardReadOnly() || stateController() == null || graphicsContext() == null) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.KeyTyped(keyChar));
    applyStateChange(returnState, true, true);
  }

  public void returnFromState() {
    if (manager.isBoardReadOnly()) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.Complete());
    applyStateChange(returnState, true, false);
  }

  public void cancelState() {
    if (manager.isBoardReadOnly()) {
      return;
    }
    EditorStateHandle returnState = dispatch(new EditorEvent.Cancel());
    applyStateChange(returnState, true, false);
  }

  public boolean changeLayerAction(int newLayer) {
    return !manager.isBoardReadOnly()
        && (stateController() == null || stateController().changeLayerAction(newLayer));
  }

  public void setInspectMenuState() {
    if (stateController() != null) {
      stateController().setInspectMenuState();
    }
  }

  public void setRouteMenuState() {
    if (stateController() != null) {
      stateController().setRouteMenuState();
    }
  }

  public void setDragMenuState() {
    if (stateController() != null) {
      stateController().setDragMenuState();
    }
  }

  public void startRoute(Point2D point) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    if (stateController() != null) {
      stateController().startRoute(screenToBoard(point));
    }
  }

  public void selectItems(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().selectItems(screenToBoard(point));
  }

  public void selectItems(Set<Item> items) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    manager.displayLayerMessage();
    if (stateController() != null) {
      stateController().selectItems(items);
    }
  }

  public void selectItemsInRegion() {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().selectItemsInRegion();
  }

  public void swapPins(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isMenuState()) {
      return;
    }
    stateController().swapPins(screenToBoard(point));
  }

  public void zoomSelection() {
    if (stateController() != null && stateController().isInspectedState()) {
      stateController().zoomSelection();
    }
  }

  public void toggleSelectAction(Point2D point) {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isInspectedState()) {
      return;
    }
    stateController().toggleSelect(screenToBoard(point));
  }

  public void displaySelectedItemInfo() {
    if (manager.isBoardReadOnly()
        || stateController() == null
        || !stateController().isInspectedState()) {
      return;
    }
    stateController().displaySelectedItemInfo();
  }

  public void extendSelectionToWholeNets() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeNets();
  }

  public void extendSelectionToWholeComponents() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeComponents();
  }

  public void extendSelectionToWholeConnectedSets() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeConnectedSets();
  }

  public void extendSelectionToWholeConnections() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().extendSelectionToWholeConnections();
  }

  public void toggleSelectedItemViolations() {
    if (isReadOnlyInspectedState()) {
      return;
    }
    stateController().toggleSelectedItemViolations();
  }

  public void turn45Degree(int factor) {
    if (!isReadOnlyMoveState()) {
      stateController().turn45Degree(factor);
    }
  }

  public void changePlacementSide() {
    if (!isReadOnlyMoveState()) {
      stateController().changePlacementSide();
    }
  }

  public void resetRotation() {
    if (stateController() != null && stateController().isMoveState()) {
      stateController().resetRotation();
    }
  }

  public void zoomRegion() {
    if (stateController() != null) {
      stateController().zoomRegion();
    }
  }

  public void startCircle(Point2D point) {
    startConstruction(point, Construction.CIRCLE);
  }

  public void startTile(Point2D point) {
    startConstruction(point, Construction.TILE);
  }

  public void startPolygonshapeItem(Point2D point) {
    startConstruction(point, Construction.POLYGON);
  }

  public void startAddingHole(Point2D point) {
    startConstruction(point, Construction.HOLE);
  }

  public Set<Item> pickItems(FloatPoint location) {
    return pickItems(location, manager.getWorkspaceSettings().getItemSelectionFilter());
  }

  public Set<Item> pickItems(FloatPoint point, ItemSelectionFilter itemFilter) {
    IntPoint location = point.round();
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    Set<Item> result =
        manager.getRoutingBoard().pickItems(location, settings.getLayer(), itemFilter);
    GraphicsContext graphicsContext = manager.getGraphicsContext();
    if (result.isEmpty() && settings.getSelectOnAllVisibleLayers()) {
      for (int i = 0; i < graphicsContext.layerCount(); i++) {
        if (i == settings.getLayer() || graphicsContext.getLayerVisibility(i) <= 0) {
          continue;
        }
        result.addAll(manager.getRoutingBoard().pickItems(location, i, itemFilter));
      }
    }
    return result;
  }

  public void moveMouse(FloatPoint toLocation) {
    if (!manager.isBoardReadOnly()) {
      manager
          .getPanel()
          .moveMouse(manager.getGraphicsContext().coordinateTransform.boardToScreen(toLocation));
    }
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
    applyInteractionStateChange(nextState, repaintAfterChange, updateToolbarSelection);
  }

  private void applyInteractionStateChange(
      EditorStateHandle nextState, boolean repaintAfterChange, boolean updateToolbarSelection) {
    if (nextState == null || nextState == manager.getEditorState()) {
      return;
    }
    manager.setEditorState(nextState);
    if (updateToolbarSelection) {
      BoardPanel panel = manager.getPanel();
      if (panel != null && panel.boardFrame != null) {
        panel.boardFrame.setToolbarModeSelectionPanelValue(manager.getEditorState());
      }
    }
    if (repaintAfterChange) {
      manager.repaint();
    }
  }

  private enum Construction {
    CIRCLE,
    TILE,
    POLYGON,
    HOLE
  }
}
