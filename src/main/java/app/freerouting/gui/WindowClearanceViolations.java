package app.freerouting.gui;

import app.freerouting.board.BoardOutline;
import app.freerouting.board.ComponentObstacleArea;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Pin;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.board.ViaObstacleArea;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.ClearanceViolations;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Displays clearance violations detected on the current board. */
public class WindowClearanceViolations extends WindowObjectListWithFilter {

  /** Creates a new instance of clearance violations window. */
  public WindowClearanceViolations(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.getLocale());

    this.setTitle(tm.getText("title"));
    this.listEmptyMessage.setText(tm.getText("listEmptyMessage"));
  }

  @Override
  protected void fillList() {
    GuiBoardManager boardHandling = this.boardFrame.boardPanel.boardHandling;

    ClearanceViolations clearanceViolations =
        new ClearanceViolations(boardHandling.getRoutingBoard().getItems());
    SortedSet<ViolationInfo> sortedSet = new TreeSet<>();
    for (ClearanceViolation currentViolation : clearanceViolations.list) {
      sortedSet.add(new ViolationInfo(currentViolation));
    }
    for (ViolationInfo currentViolation : sortedSet) {
      this.addToList(currentViolation);
    }
    this.list.setVisibleRowCount(Math.min(sortedSet.size(), DEFAULT_TABLE_SIZE));

    if (clearanceViolations.globalSmallestClearance != Double.MAX_VALUE) {
      FRLogger.info(
          "The smallest clearance on the board is %.4f mm."
              .formatted(clearanceViolations.globalSmallestClearance / 10000.0));
    }
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedViolations = list.getSelectedValuesList();
    if (selectedViolations.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedViolations.size(); i++) {
      ClearanceViolation currentViolation = ((ViolationInfo) selectedViolations.get(i)).violation;
      selectedItems.add(currentViolation.firstItem);
      selectedItems.add(currentViolation.secondItem);
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.selectItems(selectedItems);
    boardHandling.toggleSelectedItemViolations();
    boardHandling.zoomSelection();
  }

  private String itemInfo(Item item) {
    String result;
    if (item instanceof Pin) {
      result = tm.getText("pin");
    } else if (item instanceof Via via) {
      Net currentNet = item.board.rules.nets.get(via.getNetNumber(0));
      result = tm.getText("via_with_net_label", currentNet.name);
    } else if (item instanceof Trace trace) {
      Net currentNet = item.board.rules.nets.get(trace.getNetNumber(0));
      result = tm.getText("trace_with_net_label", currentNet.name);
    } else if (item instanceof ConductionArea) {
      result = tm.getText("conductionArea");
    } else if (item instanceof ViaObstacleArea) {
      result = tm.getText("via_keepout");
    } else if (item instanceof ComponentObstacleArea) {
      result = tm.getText("component_keepout");
    } else if (item instanceof ObstacleArea) {
      result = tm.getText("keepout");
    } else if (item instanceof BoardOutline) {
      result = tm.getText("boardOutline");
    } else {
      result = tm.getText("unknown");
    }
    return result;
  }

  private class ViolationInfo implements Comparable<ViolationInfo>, WindowObjectInfo.Printable {

    public final ClearanceViolation violation;
    public final FloatPoint location;
    public final double delta;

    public ViolationInfo(ClearanceViolation violation) {
      this.violation = violation;
      FloatPoint boardLocation = violation.shape.centreOfGravity();
      this.location =
          boardFrame.boardPanel.boardHandling.coordinateTransform.boardToUser(boardLocation);
      this.delta = (violation.expectedClearance - violation.actualClearance) / 10000.0;
    }

    @Override
    public String toString() {
      LayerStructure layerStructure =
          boardFrame.boardPanel.boardHandling.getRoutingBoard().layerStructure;

      return tm.getText(
          "clearance_violation_message_template",
          "%.4f".formatted(delta),
          itemInfo(violation.firstItem),
          itemInfo(violation.secondItem),
          location.toString(boardFrame.getLocale()),
          layerStructure.layers[violation.layer].name);
    }

    @Override
    public void printInfo(ObjectInfoPanel window, Locale locale) {
      this.violation.printInfo(window, locale);
    }

    @Override
    public int compareTo(ViolationInfo other) {
      if (this.delta > other.delta) {
        return -1;
      } else if (this.delta < other.delta) {
        return +1;
      }

      if (this.violation.layer < other.violation.layer) {
        return -1;
      } else if (this.violation.layer > other.violation.layer) {
        return +1;
      }

      return 0;
    }
  }
}
