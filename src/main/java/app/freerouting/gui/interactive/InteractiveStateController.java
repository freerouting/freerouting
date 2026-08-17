package app.freerouting.gui.interactive;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.gui.workspace.EditorEvent;
import app.freerouting.gui.workspace.EditorStateController;
import app.freerouting.gui.workspace.EditorStateHandle;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.InteractiveCommand;
import app.freerouting.util.TextManager;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.Set;
import javax.swing.JPopupMenu;

/**
 * Concrete editor-state orchestration.
 *
 * <p>This class is intentionally the only owner of the concrete state machine. The session package
 * sees it only through {@link EditorStateController}.
 */
public final class InteractiveStateController implements EditorStateController {

  private final GuiBoardManager boardManager;
  private final TextManager textManager;
  private InteractiveState currentState;

  /** Creates the concrete editor-state controller for a board manager. */
  public InteractiveStateController(GuiBoardManager boardManager) {
    this.boardManager = boardManager;
    this.textManager = new TextManager(GuiBoardManager.class, boardManager.getLocale());
  }

  /** Installs the default route-menu state after the view has registered this controller. */
  public void bootstrapInitialState() {
    setState(RouteMenuState.getInstance(boardManager));
  }

  @Override
  public EditorStateHandle currentState() {
    return currentState;
  }

  @Override
  public void setState(EditorStateHandle state) {
    if (state == null || state == currentState) {
      return;
    }
    if (!(state instanceof InteractiveState concreteState)) {
      throw new IllegalArgumentException("Unsupported editor state handle: " + state.getClass());
    }
    currentState = concreteState;
    if (!boardManager.isBoardReadOnly()) {
      currentState.setToolbar();
    }
  }

  @Override
  public EditorStateHandle dispatch(EditorEvent event) {
    if (currentState == null || event == null) {
      return currentState;
    }
    InteractiveCommand command =
        switch (event) {
          case EditorEvent.LeftClick click ->
              currentState.leftButtonClickedCommand(click.location());
          case EditorEvent.MouseMoved ignored -> currentState.mouseMovedCommand();
          case EditorEvent.MousePressed pressed ->
              currentState.mousePressedCommand(pressed.location());
          case EditorEvent.MouseDragged dragged ->
              currentState.mouseDraggedCommand(dragged.location());
          case EditorEvent.ButtonReleased ignored -> currentState.buttonReleasedCommand();
          case EditorEvent.MouseWheelMoved wheel ->
              currentState.mouseWheelMovedCommand(wheel.rotation());
          case EditorEvent.KeyTyped key -> currentState.keyTypedCommand(key.keyChar());
          case EditorEvent.Complete ignored -> currentState.completeCommand();
          case EditorEvent.Cancel ignored -> currentState.cancelCommand();
        };
    if (!command.canExecute()) {
      return currentState;
    }
    EditorStateHandle nextState = command.execute();
    return nextState != null ? nextState : currentState;
  }

  @Override
  public void draw(Graphics graphics) {
    if (currentState != null) {
      currentState.draw(graphics);
    }
  }

  @Override
  public JPopupMenu popupMenu() {
    return currentState == null ? null : currentState.getPopupMenu();
  }

  @Override
  public boolean isInteractiveDrag() {
    return currentState instanceof DragState;
  }

  @Override
  public boolean isMenuState() {
    return currentState instanceof MenuState;
  }

  @Override
  public boolean isInspectedState() {
    return currentState instanceof InspectedItemState;
  }

  @Override
  public boolean isMoveState() {
    return currentState instanceof MoveItemState;
  }

  @Override
  public void setInspectMenuState() {
    setState(InspectMenuState.getInstance(boardManager));
    boardManager.screenMessages.setStatusMessage(textManager.getText("select_menu"));
  }

  @Override
  public void setRouteMenuState() {
    setState(RouteMenuState.getInstance(boardManager));
    boardManager.screenMessages.setStatusMessage(textManager.getText("route_menu"));
  }

  @Override
  public void setDragMenuState() {
    setState(DragMenuState.getInstance(boardManager));
    boardManager.screenMessages.setStatusMessage(textManager.getText("drag_menu"));
  }

  @Override
  public void startRoute(FloatPoint location) {
    setState(RouteState.getInstance(location, currentState, boardManager));
  }

  @Override
  public void selectItems(FloatPoint location) {
    if (currentState instanceof MenuState menuState) {
      setState(menuState.selectItems(location));
    }
  }

  @Override
  public void selectItems(Set<Item> items) {
    if (currentState instanceof MenuState) {
      setState(InspectedItemState.getInstance(items, currentState, boardManager));
    } else if (currentState instanceof InspectedItemState inspectedState) {
      inspectedState.getItemList().clear();
      inspectedState.getItemList().addAll(items);
      boardManager.repaint();
    }
  }

  @Override
  public void selectItemsInRegion() {
    if (currentState instanceof MenuState) {
      setState(InspectItemsInRegionState.getInstance(currentState, boardManager));
    }
  }

  @Override
  public void swapPins(FloatPoint location) {
    if (currentState instanceof MenuState menuState) {
      setState(menuState.swapPins(location));
    }
  }

  @Override
  public void zoomSelection() {
    if (!(currentState instanceof InspectedItemState inspectedState)) {
      return;
    }
    IntBox boundingBox =
        boardManager.getRoutingBoard().getBoundingBox(inspectedState.getItemList());
    boundingBox = boundingBox.offset(boardManager.getRoutingBoard().rules.getMaxTraceHalfWidth());
    Point2D lowerLeft =
        boardManager.graphicsContext.coordinateTransform.boardToScreen(boundingBox.ll.toFloat());
    Point2D upperRight =
        boardManager.graphicsContext.coordinateTransform.boardToScreen(boundingBox.ur.toFloat());
    boardManager.getPanel().zoomFrame(lowerLeft, upperRight);
  }

  @Override
  public void toggleSelect(FloatPoint location) {
    if (!(currentState instanceof InspectedItemState inspectedState)) {
      return;
    }
    InteractiveState nextState = inspectedState.toggleSelect(location);
    if (nextState != currentState) {
      setState(nextState);
      boardManager.repaint();
    }
  }

  @Override
  public void displaySelectedItemInfo() {
    if (currentState instanceof InspectedItemState inspectedState) {
      inspectedState.info();
    }
  }

  @Override
  public void filterSelection() {
    if (currentState instanceof InspectedItemState inspectedState) {
      setState(inspectedState.filter());
    }
  }

  @Override
  public void extendSelectionToWholeNets() {
    if (currentState instanceof InspectedItemState inspectedState) {
      setState(inspectedState.extentToWholeNets());
    }
  }

  @Override
  public void extendSelectionToWholeComponents() {
    if (currentState instanceof InspectedItemState inspectedState) {
      setState(inspectedState.extentToWholeComponents());
    }
  }

  @Override
  public void extendSelectionToWholeConnectedSets() {
    if (currentState instanceof InspectedItemState inspectedState) {
      setState(inspectedState.extentToWholeConnectedSets());
    }
  }

  @Override
  public void extendSelectionToWholeConnections() {
    if (currentState instanceof InspectedItemState inspectedState) {
      setState(inspectedState.extentToWholeConnections());
    }
  }

  @Override
  public void toggleSelectedItemViolations() {
    if (currentState instanceof InspectedItemState inspectedState) {
      inspectedState.toggleClearanceViolations();
    }
  }

  @Override
  public void turn45Degree(int factor) {
    if (currentState instanceof MoveItemState moveItemState) {
      moveItemState.turn45Degree(factor);
    }
  }

  @Override
  public void changePlacementSide() {
    if (currentState instanceof MoveItemState moveItemState) {
      moveItemState.changePlacementSide();
    }
  }

  @Override
  public boolean changeLayerAction(int newLayer) {
    return currentState == null || currentState.changeLayerAction(newLayer);
  }

  @Override
  public void zoomRegion() {
    setState(ZoomRegionState.getInstance(currentState, boardManager));
  }

  @Override
  public void startCircle(FloatPoint location) {
    setState(CircleConstructionState.getInstance(location, currentState, boardManager));
  }

  @Override
  public void startTile(FloatPoint location) {
    setState(TileConstructionState.getInstance(location, currentState, boardManager));
  }

  @Override
  public void startPolygon(FloatPoint location) {
    setState(PolygonShapeConstructionState.getInstance(location, currentState, boardManager));
  }

  @Override
  public void startHole(FloatPoint location) {
    setState(HoleConstructionState.getInstance(location, currentState, boardManager));
  }

  @Override
  public void resetRotation() {
    if (currentState instanceof MoveItemState moveItemState) {
      moveItemState.resetRotation();
    }
  }
}
