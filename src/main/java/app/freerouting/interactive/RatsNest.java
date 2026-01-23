package app.freerouting.interactive;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.boardgraphics.NetIncompletesGraphics;
import app.freerouting.boardgraphics.NetIncompletesGraphics;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.drc.NetIncompletes;

import java.awt.Graphics;
import java.util.Collection;

/**
 * Creates all incompletes (ratsnest) to display them on the screen
 */
public class RatsNest {

  public final DesignRulesChecker drc;
  /**
   * Visibility filter array. If is_filtered[i] is true, the ratsnest for net
   * (i+1) is hidden.
   */
  private final boolean[] is_filtered;
  /**
   * Global flag to hide all ratsnests.
   */
  public boolean hidden;

  /**
   * Creates a new instance of RatsNest.
   * Initializes the incomplete connection calculations via DesignRulesChecker.
   *
   * @param p_board The board containing the nets and items.
   */
  public RatsNest(BasicBoard p_board) {
    this.drc = new DesignRulesChecker(p_board, null);
    this.drc.calculateAllIncompletes();

    int max_net_no = p_board.rules.nets.max_net_no();
    this.is_filtered = new boolean[max_net_no];
    for (int i = 0; i < max_net_no; i++) {
      is_filtered[i] = false;
    }
  }

  /**
   * Recalculates the incomplete connections (airlines) for the specified net.
   * This is typically called when items are added or removed from the board.
   *
   * @param p_net_no The number of the net to recalculate.
   * @param p_board  The board containing the items.
   */
  public void recalculate(int p_net_no, BasicBoard p_board) {
    drc.recalculateNetIncompletes(p_net_no);
  }

  /**
   * Recalculates the incomplete connections for the specified net using a
   * provided list of items.
   * Useful when the item list is already known or filtered.
   *
   * @param p_net_no    The number of the net to recalculate.
   * @param p_item_list The collection of items belonging to the net.
   * @param p_board     The board context.
   */
  public void recalculate(int p_net_no, Collection<Item> p_item_list, BasicBoard p_board) {
    drc.recalculateNetIncompletes(p_net_no, p_item_list);
  }

  /**
   * Returns the total number of incomplete connections (airlines) across all
   * nets.
   * Note: This value can be higher than the number of unconnected nets if a
   * single net has multiple disjoint fragments.
   *
   * @return The total count of airlines.
   */
  public int incomplete_count() {
    return drc.getIncompleteCount();
  }

  /**
   * Returns the number of incomplete connections for a specific net.
   *
   * @param p_net_no The net number to check.
   * @return The count of airlines for the net.
   */
  public int incomplete_count(int p_net_no) {
    return drc.getIncompleteCount(p_net_no);
  }

  /**
   * Returns the total number of nets that violate length restrictions (too short
   * or too long).
   *
   * @return The count of nets with length violations.
   */
  public int length_violation_count() {
    return drc.getLengthViolationCount();
  }

  /**
   * Returns the magnitude of the length violation for the specified net.
   *
   * @param p_net_no The net number.
   * @return Positive value if trace length is too big, negative if too small, 0
   *         if valid or unrestricted.
   */
  public double get_length_violation(int p_net_no) {
    return drc.getLengthViolation(p_net_no);
  }

  /**
   * Retrieves all airlines (incomplete connections) for the entire board.
   *
   * @return An array containing all AirLine objects.
   */
  public AirLine[] get_airlines() {
    return drc.getAllAirlines();
  }

  /**
   * Hides the ratsnest globally.
   */
  public void hide() {
    hidden = true;
  }

  /**
   * Shows the ratsnest (unless individually filtered).
   */
  public void show() {
    hidden = false;
  }

  /**
   * Recalculates length matching violations for all nets.
   *
   * @return true if the status of any length violation has changed.
   */
  public boolean recalculate_length_violations() {
    return drc.recalculateLengthViolations();
  }

  /**
   * Checks if the ratsnest is globally hidden.
   * Used for example to hide the incompletes during interactive routing to reduce
   * clutter.
   *
   * @return true if hidden.
   */
  public boolean is_hidden() {
    return hidden;
  }

  /**
   * Sets the visibility filter for a specific net's ratsnest.
   *
   * @param p_net_no The net number.
   * @param p_value  true to hide the net's airlines, false to show them.
   */
  public void set_filter(int p_net_no, boolean p_value) {
    if (p_net_no < 1 || p_net_no > is_filtered.length) {
      return;
    }
    is_filtered[p_net_no - 1] = p_value;
  }

  /**
   * Draws the ratsnest to the graphics context.
   *
   * @param p_graphics         The AWT graphics object.
   * @param p_graphics_context The context managing board graphics (colors,
   *                           transforms).
   */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    boolean draw_length_violations_only = this.hidden;

    for (int i = 0; i < is_filtered.length; i++) {
      // net index to net number: i -> i+1
      if (!is_filtered[i]) {
        NetIncompletes ni = drc.getNetIncompletes(i + 1);
        if (ni != null) {
          NetIncompletesGraphics.draw(ni, p_graphics, p_graphics_context, draw_length_violations_only);
        }
      }
    }
  }
}