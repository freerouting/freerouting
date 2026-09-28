package app.freerouting.gui.workspace.controllers;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.CoordinateTransform;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;

/**
 * Coordinates active-layer state with layer visibility and GUI presentation.
 *
 * <p>This keeps layer/view synchronization separate from routing-rule policy while retaining the
 * manager's public layer façade.
 */
public final class GuiBoardLayerController {

  private final GuiBoardManager manager;

  public GuiBoardLayerController(GuiBoardManager manager) {
    this.manager = manager;
  }

  public void setLayerVisibility(int layer, double value) {
    GraphicsContext graphicsContext = manager.getGraphicsContext();
    if (layer < 0 || layer >= graphicsContext.layerCount()) {
      return;
    }
    graphicsContext.setLayerVisibility(layer, value);
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    if (value == 0 && settings.getLayer() == layer) {
      double bestVisibility = 0;
      int bestVisibleLayer = 0;
      for (int i = 0; i < graphicsContext.layerCount(); i++) {
        if (graphicsContext.getLayerVisibility(i) > bestVisibility) {
          bestVisibility = graphicsContext.getLayerVisibility(i);
          bestVisibleLayer = i;
        }
      }
      settings.setLayer(bestVisibleLayer);
    }
  }

  public void setCurrentLayer(int layer) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    int clampedLayer = Math.max(0, Math.min(layer, manager.getRoutingBoard().getLayerCount() - 1));
    setLayer(clampedLayer);
  }

  public void setLayer(int layerIndex) {
    RoutingBoard board = manager.getRoutingBoard();
    Layer currentLayer = board.layerStructure.layers[layerIndex];
    manager.screenMessages.setLayer(currentLayer.name);
    manager.getWorkspaceSettings().setLayer(layerIndex);

    if (!manager.isBoardReadOnly() && currentLayer.isSignal) {
      manager.getPanel().setSelectedSignalLayer(layerIndex);
    }

    GraphicsContext graphicsContext = manager.getGraphicsContext();
    if (graphicsContext != null) {
      if (graphicsContext.getLayerVisibility(layerIndex) == 0) {
        graphicsContext.setLayerVisibility(layerIndex, 1);
        if (manager.getPanel() != null && manager.getPanel().boardFrame != null) {
          manager.getPanel().boardFrame.refreshWindows();
        }
      }
      graphicsContext.setFullyVisibleLayer(layerIndex);
    }
    manager.repaint();
  }

  public void displayLayerMessage() {
    manager.screenMessages.clearAddField();
    Layer currentLayer =
        manager.getRoutingBoard().layerStructure.layers[manager.getWorkspaceSettings().getLayer()];
    manager.screenMessages.setLayer(currentLayer.name);
  }

  public void changeUserUnit(Unit unit) {
    manager.screenMessages.setUnitLabel(unit.toString());
    CoordinateTransform oldTransform = manager.getCoordinateTransform();
    manager.coordinateTransform =
        new CoordinateTransform(
            oldTransform.userUnitFactor,
            unit,
            oldTransform.boardUnitFactor,
            oldTransform.boardUnit);
  }
}
