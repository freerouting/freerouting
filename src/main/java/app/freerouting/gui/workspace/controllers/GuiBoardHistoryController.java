package app.freerouting.gui.workspace.controllers;

import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.util.TextManager;
import java.util.Set;
import java.util.TreeSet;

/**
 * Coordinates GUI history commands around the routing board's snapshot store.
 *
 * <p>The board remains the owner of undo/redo state. This controller preserves the GUI guards,
 * rats-nest updates, localized status messages, and repaint side effects.
 */
public final class GuiBoardHistoryController {

  private final GuiBoardManager manager;
  private final TextManager tm;

  public GuiBoardHistoryController(GuiBoardManager manager, TextManager tm) {
    this.manager = manager;
    this.tm = tm;
  }

  public void generateSnapshot() {
    if (!manager.isBoardReadOnly()) {
      manager.getRoutingBoard().generateSnapshot();
    }
  }

  public void undo() {
    if (!canChangeHistory()) {
      return;
    }
    Set<Integer> changedNets = new TreeSet<>();
    if (manager.getRoutingBoard().undo(changedNets)) {
      changedNets.forEach(manager::updateRatsnest);
      manager.screenMessages.setStatusMessage(tm.getText("undo"));
    } else {
      manager.screenMessages.setStatusMessage(tm.getText("no_more_undo_possible"));
    }
    manager.repaint();
  }

  public void redo() {
    if (!canChangeHistory()) {
      return;
    }
    Set<Integer> changedNets = new TreeSet<>();
    if (manager.getRoutingBoard().redo(changedNets)) {
      changedNets.forEach(manager::updateRatsnest);
      manager.screenMessages.setStatusMessage(tm.getText("redo"));
    } else {
      manager.screenMessages.setStatusMessage(tm.getText("no_more_redo_possible"));
    }
    manager.repaint();
  }

  private boolean canChangeHistory() {
    return !manager.isBoardReadOnly()
        && manager.getInteractionStateController() != null
        && manager.getInteractionStateController().isMenuState();
  }
}
