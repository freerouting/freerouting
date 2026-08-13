package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.gui.session.GuiBoardManager;
import app.freerouting.gui.session.RatsNest;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.Nets;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Displays nets whose routed lengths violate their rules. */
public class WindowLengthViolations extends WindowObjectListWithFilter {

  /** Creates a new instance of WindowLengthViolations. */
  public WindowLengthViolations(BoardFrame boardFrame) {
    super(boardFrame);
    setLanguage(boardFrame.get_locale());

    this.setTitle(tm.getText("title"));
    this.listEmptyMessage.setText(tm.getText("listEmpty"));
  }

  @Override
  protected void fillList() {
    RatsNest ratsnest = this.boardFrame.boardPanel.boardHandling.getRatsnest();
    Nets netList = this.boardFrame.boardPanel.boardHandling.getRoutingBoard().rules.nets;
    SortedSet<LengthViolation> lengthViolations = new TreeSet<>();
    for (int netIndex = 1; netIndex <= netList.maxNetNo(); netIndex++) {
      double currViolationLength = ratsnest.getLengthViolation(netIndex);
      if (currViolationLength != 0) {
        LengthViolation currLengthViolation =
            new LengthViolation(netList.get(netIndex), currViolationLength);
        lengthViolations.add(currLengthViolation);
      }
    }

    for (LengthViolation currViolation : lengthViolations) {
      this.addToList(currViolation);
    }
    this.list.setVisibleRowCount(Math.min(lengthViolations.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void selectInstances() {
    List<Object> selectedViolations = list.getSelectedValuesList();
    if (selectedViolations.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedViolations.size(); i++) {
      LengthViolation currViolation = (LengthViolation) selectedViolations.get(i);
      selectedItems.addAll(currViolation.net.getItems());
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.selectItems(selectedItems);
    boardHandling.zoomSelection();
  }

  private class LengthViolation implements Comparable<LengthViolation> {

    final Net net;
    final double violationLength;

    LengthViolation(Net net, double violationLength) {
      this.net = net;
      this.violationLength = violationLength;
    }

    @Override
    public int compareTo(LengthViolation other) {
      return this.net.name.compareToIgnoreCase(other.net.name);
    }

    @Override
    public String toString() {
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      NetClass netClass = this.net.getNetClass();
      float length = (float) coordinateTransform.boardToUser(this.net.getTraceLength());
      if (violationLength > 0) {
        return tm.getText(
            "length_violation_max_message",
            this.net.name,
            String.valueOf(length),
            String.valueOf(
                (float) coordinateTransform.boardToUser(netClass.getMaximumTraceLength())));
      }
      return tm.getText(
          "length_violation_min_message",
          this.net.name,
          String.valueOf(length),
          String.valueOf(
              (float) coordinateTransform.boardToUser(netClass.getMinimumTraceLength())));
    }
  }
}
