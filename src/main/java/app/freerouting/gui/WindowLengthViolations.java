package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.RatsNest;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.Nets;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class WindowLengthViolations extends WindowObjectListWithFilter {

  /** Creates a new instance of WindowLengthViolations */
  public WindowLengthViolations(BoardFrame p_board_frame) {
    super(p_board_frame);
    setLanguage(p_board_frame.get_locale());

    this.setTitle(tm.getText("title"));
    this.listEmptyMessage.setText(tm.getText("listEmpty"));
  }

  @Override
  protected void fill_list() {
    RatsNest ratsnest = this.boardFrame.boardPanel.boardHandling.get_ratsnest();
    Nets netList = this.boardFrame.boardPanel.boardHandling.get_routing_board().rules.nets;
    SortedSet<LengthViolation> lengthViolations = new TreeSet<>();
    for (int netIndex = 1; netIndex <= netList.max_net_no(); netIndex++) {
      double currViolationLength = ratsnest.get_length_violation(netIndex);
      if (currViolationLength != 0) {
        LengthViolation currLengthViolation =
            new LengthViolation(netList.get(netIndex), currViolationLength);
        lengthViolations.add(currLengthViolation);
      }
    }

    for (LengthViolation currViolation : lengthViolations) {
      this.add_to_list(currViolation);
    }
    this.list.setVisibleRowCount(Math.min(lengthViolations.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selectedViolations = list.getSelectedValuesList();
    if (selectedViolations.isEmpty()) {
      return;
    }
    Set<Item> selectedItems = new TreeSet<>();
    for (int i = 0; i < selectedViolations.size(); i++) {
      LengthViolation currViolation = (LengthViolation) selectedViolations.get(i);
      selectedItems.addAll(currViolation.net.get_items());
    }
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    boardHandling.select_items(selectedItems);
    boardHandling.zoom_selection();
  }

  private class LengthViolation implements Comparable<LengthViolation> {

    final Net net;
    final double violationLength;

    LengthViolation(Net p_net, double p_violation_length) {
      net = p_net;
      violationLength = p_violation_length;
    }

    @Override
    public int compareTo(LengthViolation p_other) {
      return this.net.name.compareToIgnoreCase(p_other.net.name);
    }

    @Override
    public String toString() {
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      NetClass netClass = this.net.getNetClass();
      float length = (float) coordinateTransform.board_to_user(this.net.get_trace_length());
      if (violationLength > 0) {
        return tm.getText(
            "length_violation_max_message",
            this.net.name,
            String.valueOf(length),
            String.valueOf(
                (float) coordinateTransform.board_to_user(netClass.get_maximum_trace_length())));
      }
      return tm.getText(
          "length_violation_min_message",
          this.net.name,
          String.valueOf(length),
          String.valueOf(
              (float) coordinateTransform.board_to_user(netClass.get_minimum_trace_length())));
    }
  }
}
