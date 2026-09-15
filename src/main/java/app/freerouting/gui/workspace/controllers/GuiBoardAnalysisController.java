package app.freerouting.gui.workspace.controllers;

import app.freerouting.board.model.items.Item;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.progress.ClearanceViolations;
import app.freerouting.gui.workspace.progress.RatsNest;
import app.freerouting.util.TextManager;
import java.util.Collection;

/**
 * Owns rats-nest and clearance-violation presentation state for a GUI board session.
 *
 * <p>{@link GuiBoardManager} keeps the public façade while this collaborator coordinates board
 * analysis, status messages, and visibility updates.
 */
public final class GuiBoardAnalysisController {

  private final GuiBoardManager manager;
  private final TextManager tm;
  private RatsNest ratsnest;

  public GuiBoardAnalysisController(GuiBoardManager manager, TextManager tm) {
    this.manager = manager;
    this.tm = tm;
  }

  public void toggleRatsnest() {
    if (ratsnest == null || ratsnest.isHidden()) {
      createRatsnest();
    } else {
      ratsnest = null;
    }
    manager.repaint();
  }

  public void toggleClearanceViolations() {
    if (manager.getAnalysisClearanceViolations() == null) {
      ClearanceViolations violations =
          new ClearanceViolations(manager.getRoutingBoard().getItems());
      manager.setAnalysisClearanceViolations(violations);
      Integer violationCount = (violations.list.size() + 1) / 2;
      manager.screenMessages.setStatusMessage(
          violationCount + " " + tm.getText("clearance_violations_found"));
    } else {
      manager.setAnalysisClearanceViolations(null);
      manager.screenMessages.setStatusMessage("");
    }
    manager.repaint();
  }

  public void createRatsnest() {
    ratsnest = new RatsNest(manager.getRoutingBoard());
    updateRatsnestStatusMessage();
  }

  public void attachPreparedRatsNest(RatsNest preparedRatsNest) {
    ratsnest = preparedRatsNest;
    updateRatsnestStatusMessage();
  }

  public void createRatsnestIfAbsent() {
    if (ratsnest == null) {
      createRatsnest();
    } else {
      updateRatsnestStatusMessage();
    }
  }

  public void updateRatsnest(int netNumber) {
    if (ratsnest != null && netNumber > 0) {
      ratsnest.recalculate(netNumber, manager.getRoutingBoard());
      ratsnest.show();
    }
  }

  public void updateRatsnest(int netNumber, Collection<Item> itemList) {
    if (ratsnest != null && netNumber > 0) {
      ratsnest.recalculate(netNumber, itemList, manager.getRoutingBoard());
      ratsnest.show();
    }
  }

  public void updateRatsnest() {
    if (ratsnest != null) {
      ratsnest = new RatsNest(manager.getRoutingBoard());
    }
  }

  public void hideRatsnest() {
    if (ratsnest != null) {
      ratsnest.hide();
    }
  }

  public void showRatsnest() {
    if (ratsnest != null) {
      ratsnest.show();
    }
  }

  public void removeRatsnest() {
    ratsnest = null;
  }

  public RatsNest getRatsnest() {
    if (ratsnest == null) {
      ratsnest = new RatsNest(manager.getRoutingBoard());
    }
    return ratsnest;
  }

  public RatsNest getExistingRatsnest() {
    return ratsnest;
  }

  public void recalculateLengthViolations() {
    if (ratsnest != null && ratsnest.recalculateLengthViolations() && !ratsnest.isHidden()) {
      manager.repaint();
    }
  }

  public void setIncompletesFilter(int netNumber, boolean value) {
    if (ratsnest != null) {
      ratsnest.setFilter(netNumber, value);
    }
  }

  private void updateRatsnestStatusMessage() {
    if (ratsnest == null) {
      return;
    }
    Integer incompleteCount = ratsnest.incompleteCount();
    int lengthViolationCount = ratsnest.lengthViolationCount();
    String message =
        lengthViolationCount == 0
            ? tm.getText("ratsnest_status_incomplete_only", Integer.toString(incompleteCount))
            : tm.getText(
                "ratsnest_status_with_length_violations",
                Integer.toString(incompleteCount),
                Integer.toString(lengthViolationCount));
    manager.screenMessages.setStatusMessage(message);
  }
}
