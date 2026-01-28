package app.freerouting.interactive;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.boardgraphics.NetIncompletesGraphics;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.drc.NetIncompletes;
import java.awt.Graphics;
import java.util.Collection;

/**
 * Manages the visual representation of incomplete connections (rats nest) on the PCB board.
 *
 * <p>A "rats nest" or "air wires" refers to the display of unrouted connections between pins
 * that need to be connected. These are shown as straight lines (airlines) connecting the
 * nearest points of items that should be electrically connected but aren't yet routed.
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Connection Analysis:</strong> Calculates which pins need to be connected</li>
 *   <li><strong>Airline Generation:</strong> Creates visual lines showing unrouted connections</li>
 *   <li><strong>Length Validation:</strong> Checks if routed traces meet length requirements</li>
 *   <li><strong>Visibility Control:</strong> Manages display filtering per net or globally</li>
 *   <li><strong>Graphics Rendering:</strong> Draws the rats nest on screen</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong>
 * <ul>
 *   <li>Visual feedback showing what connections remain to be routed</li>
 *   <li>Identifying the shortest path between unconnected items</li>
 *   <li>Monitoring routing progress (number of incomplete connections)</li>
 *   <li>Detecting length matching violations for critical nets</li>
 * </ul>
 *
 * <p><strong>Technical Details:</strong>
 * Airlines connect the nearest points between items that should be on the same net but
 * are not physically connected. When multiple disconnected groups exist on a net,
 * multiple airlines will be shown. The rats nest is recalculated automatically when
 * items are added, moved, or removed from the board.
 *
 * @see DesignRulesChecker
 * @see AirLine
 * @see NetIncompletes
 */
public class RatsNest {

  /**
   * Design rules checker that performs incomplete connection calculations.
   *
   * <p>The DRC maintains the current state of all incomplete connections and provides
   * methods to recalculate them when the board changes. It analyzes connectivity between
   * items and generates airlines representing unrouted connections.
   *
   * @see DesignRulesChecker
   */
  public final DesignRulesChecker drc;

  /**
   * Per-net visibility filter array for selective rats nest display.
   *
   * <p>Array indexing: {@code is_filtered[i]} controls visibility for net number {@code (i+1)}.
   * <ul>
   *   <li>{@code true}: Airlines for this net are hidden</li>
   *   <li>{@code false}: Airlines for this net are visible (unless globally hidden)</li>
   * </ul>
   *
   * <p>This allows users to focus on specific nets by hiding the rats nest clutter
   * from other nets.
   */
  private final boolean[] is_filtered;

  /**
   * Global visibility flag for the entire rats nest display.
   *
   * <p>When {@code true}, all rats nest airlines are hidden regardless of individual
   * net filters. When {@code false}, airlines are shown according to per-net filters.
   *
   * <p>Typically set to {@code true} during interactive routing to reduce visual
   * clutter while actively routing traces.
   */
  public boolean hidden;

  /**
   * Creates a new RatsNest instance and performs initial incomplete connection analysis.
   *
   * <p>Initialization process:
   * <ol>
   *   <li>Creates a DesignRulesChecker for the board</li>
   *   <li>Calculates all incomplete connections across all nets</li>
   *   <li>Initializes per-net visibility filters (all visible by default)</li>
   * </ol>
   *
   * <p>The initial calculation analyzes the entire board to determine which items
   * need connections but aren't physically connected yet. This can be time-consuming
   * on large boards with many nets.
   *
   * @param p_board the board containing nets and items to analyze for incomplete connections
   *
   * @see DesignRulesChecker#calculateAllIncompletes()
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
   * Recalculates incomplete connections (airlines) for the specified net.
   *
   * <p>Called after board modifications that affect connectivity, such as:
   * <ul>
   *   <li>Adding or removing traces</li>
   *   <li>Adding or removing vias</li>
   *   <li>Moving components</li>
   *   <li>Deleting items</li>
   * </ul>
   *
   * <p>The recalculation determines which items on the net are electrically connected
   * and which require routing, updating the airline display accordingly.
   *
   * @param p_net_no the net number to recalculate (must be valid)
   * @param p_board the board containing the items (provided for context)
   *
   * @see DesignRulesChecker#recalculateNetIncompletes(int)
   */
  public void recalculate(int p_net_no, BasicBoard p_board) {
    drc.recalculateNetIncompletes(p_net_no);
  }

  /**
   * Recalculates incomplete connections for the specified net using a provided item collection.
   *
   * <p>This optimized variant only analyzes the provided items instead of searching the
   * entire board. More efficient when the affected items are already known, such as:
   * <ul>
   *   <li>After moving or modifying specific components</li>
   *   <li>During partial board updates</li>
   *   <li>When working with a pre-filtered item set</li>
   * </ul>
   *
   * <p>The item collection should include all items on the net that may have changed
   * connectivity status.
   *
   * @param p_net_no the net number to recalculate
   * @param p_item_list the collection of items to analyze for this net
   * @param p_board the board context (provided for completeness)
   *
   * @see DesignRulesChecker#recalculateNetIncompletes(int, Collection)
   */
  public void recalculate(int p_net_no, Collection<Item> p_item_list, BasicBoard p_board) {
    drc.recalculateNetIncompletes(p_net_no, p_item_list);
  }

  /**
   * Returns the total number of incomplete connections (airlines) across all nets on the board.
   *
   * <p>Important notes:
   * <ul>
   *   <li>Each airline represents one disconnected segment requiring routing</li>
   *   <li>A single net with multiple isolated groups generates multiple airlines</li>
   *   <li>The count can exceed the number of nets with incomplete connections</li>
   *   <li>A count of 0 indicates a fully routed board</li>
   * </ul>
   *
   * <p><strong>Example:</strong> A net with 4 pins split into 2 groups (A-B and C-D)
   * will have 1 airline connecting the groups, even though 2 connections need to be routed.
   *
   * @return the total count of airlines across all nets
   *
   * @see #incomplete_count(int)
   * @see DesignRulesChecker#getIncompleteCount()
   */
  public int incomplete_count() {
    return drc.getIncompleteCount();
  }

  /**
   * Returns the number of incomplete connections for a specific net.
   *
   * <p>This count represents how many disconnected groups exist on the net.
   * A fully connected net returns 0. A net with n disconnected groups typically
   * has (n-1) airlines connecting them.
   *
   * <p>Useful for:
   * <ul>
   *   <li>Monitoring routing progress on a specific net</li>
   *   <li>Identifying nets requiring attention</li>
   *   <li>Validating that critical nets are fully routed</li>
   * </ul>
   *
   * @param p_net_no the net number to check
   * @return the count of airlines for this net, or 0 if fully connected
   *
   * @see #incomplete_count()
   * @see DesignRulesChecker#getIncompleteCount(int)
   */
  public int incomplete_count(int p_net_no) {
    return drc.getIncompleteCount(p_net_no);
  }

  /**
   * Returns the total number of nets with length matching violations.
   *
   * <p>Length violations occur when routed traces don't meet specified requirements:
   * <ul>
   *   <li><strong>Too short:</strong> Trace length is below minimum requirement</li>
   *   <li><strong>Too long:</strong> Trace length exceeds maximum limit</li>
   * </ul>
   *
   * <p>Common use cases for length constraints:
   * <ul>
   *   <li>High-speed differential pairs (matched lengths required)</li>
   *   <li>DDR memory signals (strict length matching)</li>
   *   <li>Clock distribution (controlled delays)</li>
   *   <li>Impedance-controlled traces</li>
   * </ul>
   *
   * <p>A count of 0 indicates all length-constrained nets are within tolerance.
   *
   * @return the number of nets violating length restrictions
   *
   * @see #get_length_violation(int)
   * @see DesignRulesChecker#getLengthViolationCount()
   */
  public int length_violation_count() {
    return drc.getLengthViolationCount();
  }

  /**
   * Returns the magnitude of the length violation for the specified net.
   *
   * <p>Return value interpretation:
   * <ul>
   *   <li><strong>Positive value:</strong> Trace is too long by this amount</li>
   *   <li><strong>Negative value:</strong> Trace is too short by this amount (absolute value)</li>
   *   <li><strong>Zero:</strong> Net is within acceptable length range or has no restrictions</li>
   * </ul>
   *
   * <p>The violation magnitude is in board units and represents how far the actual
   * trace length deviates from the acceptable range.
   *
   * <p><strong>Example:</strong> A return value of +50 means the trace is 50 units
   * longer than the maximum allowed length.
   *
   * @param p_net_no the net number to check
   * @return positive if too long, negative if too short, 0 if valid or unrestricted
   *
   * @see #length_violation_count()
   * @see DesignRulesChecker#getLengthViolation(int)
   */
  public double get_length_violation(int p_net_no) {
    return drc.getLengthViolation(p_net_no);
  }

  /**
   * Retrieves all airlines (incomplete connections) for the entire board.
   *
   * <p>Returns an array containing all AirLine objects representing unrouted connections
   * across all nets. Each AirLine contains:
   * <ul>
   *   <li>Start and end points of the connection</li>
   *   <li>Net number the connection belongs to</li>
   *   <li>Layer information</li>
   * </ul>
   *
   * <p>Useful for:
   * <ul>
   *   <li>Custom visualization or analysis of incomplete connections</li>
   *   <li>Exporting rats nest information</li>
   *   <li>Algorithmic processing of routing requirements</li>
   * </ul>
   *
   * @return an array containing all AirLine objects, or empty array if fully routed
   *
   * @see AirLine
   * @see DesignRulesChecker#getAllAirlines()
   */
  public AirLine[] get_airlines() {
    return drc.getAllAirlines();
  }

  /**
   * Hides the rats nest globally, suppressing all airline displays.
   *
   * <p>When hidden, no airlines are drawn regardless of per-net filter settings.
   * This is typically used during interactive routing to reduce visual clutter,
   * allowing the user to focus on the trace being routed.
   *
   * <p>Length violation indicators may still be displayed depending on
   * implementation settings.
   *
   * @see #show()
   * @see #is_hidden()
   */
  public void hide() {
    hidden = true;
  }

  /**
   * Shows the rats nest, making airlines visible according to per-net filters.
   *
   * <p>Clears the global hidden flag, allowing airlines to be displayed for nets
   * that are not individually filtered. Airlines for filtered nets remain hidden.
   *
   * @see #hide()
   * @see #set_filter(int, boolean)
   */
  public void show() {
    hidden = false;
  }

  /**
   * Recalculates length matching violations for all nets with length constraints.
   *
   * <p>This method:
   * <ul>
   *   <li>Recomputes actual trace lengths for all nets</li>
   *   <li>Compares them against minimum/maximum length requirements</li>
   *   <li>Updates the violation status for each net</li>
   * </ul>
   *
   * <p>Should be called after routing changes that might affect trace lengths,
   * such as optimization, pull-tight, or manual trace adjustments.
   *
   * @return true if any net's violation status changed (new violations or fixes), false otherwise
   *
   * @see #length_violation_count()
   * @see #get_length_violation(int)
   * @see DesignRulesChecker#recalculateLengthViolations()
   */
  public boolean recalculate_length_violations() {
    return drc.recalculateLengthViolations();
  }

  /**
   * Checks if the rats nest is globally hidden.
   *
   * <p>Returns the state of the global visibility flag. When true, all airlines
   * are suppressed regardless of per-net filter settings. Commonly used to:
   * <ul>
   *   <li>Reduce visual clutter during interactive routing</li>
   *   <li>Conditionally disable rats nest updates for performance</li>
   *   <li>Determine whether to draw length violations only</li>
   * </ul>
   *
   * @return true if the rats nest is globally hidden, false if visible (subject to filters)
   *
   * @see #hide()
   * @see #show()
   */
  public boolean is_hidden() {
    return hidden;
  }

  /**
   * Sets the visibility filter for a specific net's rats nest airlines.
   *
   * <p>Filter behavior:
   * <ul>
   *   <li><strong>p_value = true:</strong> Hide airlines for this net</li>
   *   <li><strong>p_value = false:</strong> Show airlines for this net (if not globally hidden)</li>
   * </ul>
   *
   * <p>Per-net filtering allows users to focus on specific routing tasks by hiding
   * irrelevant airlines. The filter is ignored if the rats nest is globally hidden.
   *
   * <p>If the net number is out of valid range (less than 1 or greater than the
   * maximum net number), the operation is silently ignored.
   *
   * @param p_net_no the net number to filter (1-based indexing)
   * @param p_value true to hide the net's airlines, false to show them
   *
   * @see #is_hidden()
   * @see #draw(Graphics, GraphicsContext)
   */
  public void set_filter(int p_net_no, boolean p_value) {
    if (p_net_no < 1 || p_net_no > is_filtered.length) {
      return;
    }
    is_filtered[p_net_no - 1] = p_value;
  }

  /**
   * Draws the rats nest airlines to the graphics context.
   *
   * <p>Drawing behavior:
   * <ul>
   *   <li><strong>If globally hidden:</strong> Only length violations are drawn (if any)</li>
   *   <li><strong>If visible:</strong> Airlines are drawn for non-filtered nets</li>
   * </ul>
   *
   * <p>The method iterates through all nets, checking their filter status and
   * delegating the actual rendering to NetIncompletesGraphics. Airlines are drawn
   * as straight lines from the nearest points between disconnected item groups.
   *
   * <p>Color and style are determined by the graphics context settings, typically
   * using distinctive colors for different net classes or violation types.
   *
   * @param p_graphics the AWT Graphics object for rendering
   * @param p_graphics_context the context managing board graphics (colors, transforms, layers)
   *
   * @see NetIncompletesGraphics#draw
   * @see GraphicsContext
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