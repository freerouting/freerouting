package app.freerouting.gui.workspace.controllers;

import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.gui.controls.ComboBoxLayer;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaRule;
import java.util.Collection;

/**
 * Owns interactive routing settings that are independent of layer-view presentation.
 *
 * <p>{@link GuiBoardManager} remains the public façade. This collaborator keeps live {@link
 * WorkspaceSettings} precedence and board-rule mutations together without expanding the GUI-session
 * contract.
 */
public final class GuiBoardRoutingSettings {

  private final GuiBoardManager manager;

  public GuiBoardRoutingSettings(GuiBoardManager manager) {
    this.manager = manager;
  }

  public void setIgnoreConduction(boolean value) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    manager.getRoutingBoard().changeConductionIsObstacle(!value);
  }

  public void setPinEdgeToTurnDist(double value) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    RoutingBoard board = manager.getRoutingBoard();
    double edgeToTurnDist = manager.getCoordinateTransform().userToBoard(value);
    if (edgeToTurnDist != board.rules.getPinEdgeToTurnDist()) {
      for (Pin currentPin : board.getPins()) {
        if (!currentPin.hasTraceExitRestrictions()) {
          continue;
        }
        Collection<Item> contactList = currentPin.getNormalContacts();
        for (Item currentContact : contactList) {
          if (currentContact instanceof PolylineTrace trace
              && currentContact.getFixedState() == FixedState.SHOVE_FIXED
              && trace.cornerCount() == 2) {
            currentContact.setFixedState(FixedState.UNFIXED);
          }
        }
      }
    }
    board.rules.setPinEdgeToTurnDist(edgeToTurnDist);
  }

  public int getTraceHalfwidth(int netNumber, int layer) {
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    return settings.getManualRuleSelection()
        ? settings.getManualTraceHalfWidth(layer)
        : manager.getRoutingBoard().rules.getTraceHalfWidth(netNumber, layer);
  }

  public boolean isActiveRoutingLayer(int netNumber, int layer) {
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    if (settings.getManualRuleSelection()) {
      return true;
    }
    Net currentNet = manager.getRoutingBoard().rules.nets.get(netNumber);
    if (currentNet == null) {
      return true;
    }
    NetClass currentNetClass = currentNet.getNetClass();
    return currentNetClass == null || currentNetClass.isActiveRoutingLayer(layer);
  }

  public int getTraceClearanceClass(int netNumber) {
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    if (settings.getManualRuleSelection()) {
      return settings.getManualTraceClearanceClass();
    }
    return manager
        .getRoutingBoard()
        .rules
        .nets
        .get(netNumber)
        .getNetClass()
        .getTraceClearanceClass();
  }

  public ViaRule getViaRule(int netNumber) {
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    ViaRule result =
        settings.getManualRuleSelection()
            ? manager.getRoutingBoard().rules.viaRules.get(settings.getManualViaRuleIndex())
            : null;
    if (result == null) {
      result = manager.getRoutingBoard().rules.nets.get(netNumber).getNetClass().getViaRule();
    }
    return result;
  }

  public void setDefaultTraceHalfwidth(int layer, int value) {
    if (manager.isBoardReadOnly()) {
      return;
    }
    RoutingBoard board = manager.getRoutingBoard();
    if (layer >= 0 && layer <= board.getLayerCount()) {
      board.rules.setDefaultTraceHalfWidth(layer, value);
    }
  }

  public void setClearanceCompensation(boolean value) {
    if (!manager.isBoardReadOnly()) {
      manager.getRoutingBoard().searchTreeManager.setClearanceCompensationUsed(value);
    }
  }

  public void setCurrentSnapAngle(AngleRestriction snapAngle) {
    if (!manager.isBoardReadOnly()) {
      manager.getRoutingBoard().rules.setTraceAngleRestriction(snapAngle);
    }
  }

  public void setManualTraceHalfWidth(int layerIndex, int value) {
    WorkspaceSettings settings = manager.getWorkspaceSettings();
    if (layerIndex == ComboBoxLayer.ALL_LAYER_INDEX) {
      for (int i = 0; i < settings.getLayerCount(); i++) {
        settings.setManualTraceHalfWidth(i, value);
      }
    } else if (layerIndex == ComboBoxLayer.INNER_LAYER_INDEX) {
      for (int i = 1; i < settings.getLayerCount() - 1; i++) {
        settings.setManualTraceHalfWidth(i, value);
      }
    } else {
      settings.setManualTraceHalfWidth(layerIndex, value);
    }
  }

  public void setSelectable(ItemSelectionFilter.SelectableChoices itemType, boolean value) {
    manager.getWorkspaceSettings().setSelectable(itemType, value);
    if (!value
        && manager.getInteractionStateController() != null
        && manager.getInteractionStateController().isInspectedState()) {
      manager.getInteractionStateController().filterSelection();
    }
  }
}
