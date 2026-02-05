package app.freerouting.autoroute;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Inserts the physical traces and vias on the board for connections found by
 * the autoroute algorithm.
 *
 * <p>
 * This class is responsible for converting the abstract routing path found by
 * {@link LocateFoundConnectionAlgo} into actual board items (traces and vias).
 * It handles:
 * <ul>
 * <li>Push-and-shove insertion of trace segments</li>
 * <li>Via insertion for layer transitions</li>
 * <li>Neckdown routing near pins for optimal connections</li>
 * <li>Connection to existing traces at endpoints</li>
 * <li>Trace normalization after insertion</li>
 * </ul>
 *
 * <p>
 * The insertion process uses forced routing algorithms that push aside existing
 * traces and vias to make room for the new connection. If insertion fails at
 * any
 * point, the entire operation is aborted and null is returned.
 *
 * <p>
 * <strong>Algorithm Flow:</strong>
 * <ol>
 * <li>Insert via at start of each segment (if layer change needed)</li>
 * <li>Insert trace segment using push-and-shove</li>
 * <li>Apply neckdown routing near pins when beneficial</li>
 * <li>Connect to existing traces at endpoints</li>
 * <li>Normalize trace topology to clean up redundant corners</li>
 * </ol>
 *
 * @see LocateFoundConnectionAlgo
 * @see ForcedViaAlgo
 * @see RoutingBoard#insert_forced_trace_polyline
 */
public class InsertFoundConnectionAlgo {

  /**
   * The routing board where traces and vias will be inserted.
   *
   * <p>
   * Provides access to board operations like forced trace insertion,
   * via insertion, and trace normalization.
   */
  private final RoutingBoard board;

  /**
   * The autoroute control settings defining routing parameters and constraints.
   *
   * <p>
   * Contains trace widths, clearance classes, net numbers, recursion limits,
   * and other routing configuration used during insertion.
   */
  private final AutorouteControl ctrl;

  /**
   * The last corner point of the inserted connection path.
   *
   * <p>
   * Used to track the endpoint of the connection for connecting to
   * existing traces after insertion completes.
   */
  private IntPoint last_corner;

  /**
   * The first corner point of the inserted connection path.
   *
   * <p>
   * Used to track the startpoint of the connection for connecting to
   * existing traces after insertion completes.
   */
  private IntPoint first_corner;

  /**
   * Creates a new instance of InsertFoundConnectionAlgo with the specified board
   * and control settings.
   *
   * <p>
   * This private constructor is called internally by {@link #get_instance}.
   * Direct instantiation
   * is not allowed - use the factory method instead.
   *
   * @param p_board the routing board where items will be inserted
   * @param p_ctrl  the autoroute control settings defining routing parameters
   */
  private InsertFoundConnectionAlgo(RoutingBoard p_board, AutorouteControl p_ctrl) {
    this.board = p_board;
    this.ctrl = p_ctrl;
  }



  /**
   * Creates and executes a connection insertion algorithm for the found routing
   * path.
   *
   * <p>
   * This factory method creates an instance and immediately attempts to insert
   * all
   * traces and vias from the connection found by the autoroute algorithm. The
   * insertion
   * process includes:
   * <ol>
   * <li>Iterating through all connection items (trace segments)</li>
   * <li>Inserting vias where layer transitions occur</li>
   * <li>Inserting trace segments using push-and-shove</li>
   * <li>Connecting to existing traces at the start and end points</li>
   * <li>Normalizing the traces to optimize topology</li>
   * </ol>
   *
   * <p>
   * If any step fails (via insertion failure, trace insertion failure, etc.), the
   * method returns null and logs debug information about the failure point.
   *
   * <p>
   * <strong>Connection Process:</strong>
   * <ul>
   * <li>Start at target layer and item</li>
   * <li>For each connection segment: insert via (if needed) then insert
   * trace</li>
   * <li>End at start layer and item</li>
   * <li>Make perpendicular connections to PolylineTrace endpoints</li>
   * <li>Normalize traces to clean up the topology</li>
   * </ul>
   *
   * @param p_connection the located connection path to insert, containing the
   *                     route geometry
   * @param p_board      the routing board where traces and vias will be inserted
   * @param p_ctrl       the autoroute control settings with routing parameters
   * @return a new InsertFoundConnectionAlgo instance if insertion succeeded, null
   *         if it failed
   *
   * @see LocateFoundConnectionAlgo
   * @see #insert_trace(LocateFoundConnectionAlgoAnyAngle.ResultItem)
   * @see #insert_via(Point, int, int)
   */
  public static InsertFoundConnectionAlgo get_instance(LocateFoundConnectionAlgo p_connection, RoutingBoard p_board,
      AutorouteControl p_ctrl) {
    if (p_connection == null || p_connection.connection_items == null) {
      return null;
    }
    int curr_layer = p_connection.target_layer;
    InsertFoundConnectionAlgo new_instance = new InsertFoundConnectionAlgo(p_board, p_ctrl);

    // Build detailed trace message with performance check
    if ((globalSettings != null) && (globalSettings.debugSettings != null)
        && (globalSettings.debugSettings.enableDetailedLogging)) {
      String netLabel = FRLogger.formatNetLabel(p_board, p_ctrl.net_no);
      StringBuilder detailsBuilder = new StringBuilder();
      detailsBuilder.append("Inserting connection with ").append(p_connection.connection_items.size()).append(" items: ");

      List<Point> allPoints = new ArrayList<>();
      int itemIndex = 0;
      for (LocateFoundConnectionAlgoAnyAngle.ResultItem item : p_connection.connection_items) {
        if (itemIndex > 0) {
          detailsBuilder.append(", ");
        }
        detailsBuilder.append("item[").append(itemIndex).append("]: layer=").append(item.layer)
            .append(", corners=").append(item.corners.length)
            .append(", from=").append(item.corners[0])
            .append(", to=").append(item.corners[item.corners.length - 1]);
        allPoints.add(item.corners[0]);
        allPoints.add(item.corners[item.corners.length - 1]);
        itemIndex++;
      }
      detailsBuilder.append(", target_layer=").append(p_connection.target_layer)
          .append(", start_layer=").append(p_connection.start_layer)
          .append(", target_item=").append(p_connection.target_item != null ? p_connection.target_item.toString() : "null")
          .append(", start_item=").append(p_connection.start_item != null ? p_connection.start_item.toString() : "null");

      FRLogger.trace("InsertFoundConnectionAlgo", "inserting_connection",
          detailsBuilder.toString(),
          netLabel,
          allPoints.toArray(new Point[0]));
    }

    for (LocateFoundConnectionAlgoAnyAngle.ResultItem curr_new_item : p_connection.connection_items) {
      if (!new_instance.insert_via(curr_new_item.corners[0], curr_layer, curr_new_item.layer)) {
        if ((globalSettings != null) && (globalSettings.debugSettings != null)
            && (globalSettings.debugSettings.enableDetailedLogging)) {
          String netLabel = FRLogger.formatNetLabel(p_board, p_ctrl.net_no);
          FRLogger.trace("InsertFoundConnectionAlgo", "insert_via_failed",
              "Via insertion failed at " + curr_new_item.corners[0] + " from layer " + curr_layer
                  + " to " + curr_new_item.layer,
              netLabel,
              new Point[] { curr_new_item.corners[0] });
        }
        return null;
      }
      curr_layer = curr_new_item.layer;
      if (!new_instance.insert_trace(curr_new_item)) {
        if ((globalSettings != null) && (globalSettings.debugSettings != null)
            && (globalSettings.debugSettings.enableDetailedLogging)) {
          String netLabel = FRLogger.formatNetLabel(p_board, p_ctrl.net_no);
          FRLogger.trace("InsertFoundConnectionAlgo", "insert_trace_failed",
              "Trace insertion failed on layer " + curr_new_item.layer + " with "
                  + curr_new_item.corners.length + " corners, from " + curr_new_item.corners[0]
                  + " to " + curr_new_item.corners[curr_new_item.corners.length - 1],
              netLabel,
              new Point[] { curr_new_item.corners[0],
                  curr_new_item.corners[curr_new_item.corners.length - 1] });
        }
        return null;
      }
    }
    if (!new_instance.insert_via(new_instance.last_corner, curr_layer, p_connection.start_layer)) {
      return null;
    }
    if (p_connection.target_item instanceof PolylineTrace to_trace) {
      p_board.connect_to_trace(new_instance.first_corner, to_trace, p_ctrl.trace_half_width[p_connection.start_layer],
          p_ctrl.trace_clearance_class_no);
    }
    if (p_connection.start_item instanceof PolylineTrace to_trace) {
      p_board.connect_to_trace(new_instance.last_corner, to_trace, p_ctrl.trace_half_width[p_connection.target_layer],
          p_ctrl.trace_clearance_class_no);
    }

    // NOTE: Normalize is commented out here to prevent premature tail removal
    // during incremental routing
    // Normalization (including tail removal) will happen at the end of the routing
    // operation
    // See: https://github.com/freerouting/freerouting/issues/XXX
    // try {
    // p_board.normalize_traces(p_ctrl.net_no);
    // } catch (Exception _) {
    // FRLogger.warn("The normalization of net '" +
    // p_board.rules.nets.get(p_ctrl.net_no).name + "' failed.");
    // }

    return new_instance;
  }

  /**
   * Inserts a trace segment by using push-and-shove to move aside obstacle traces
   * and vias.
   *
   * <p>
   * This method processes a single routing segment, inserting it corner by corner
   * using
   * forced trace insertion. The algorithm:
   * <ul>
   * <li>Temporarily disables pin edge-to-turn distance correction to prevent
   * interference</li>
   * <li>Identifies pins at start and end for potential neckdown routing</li>
   * <li>Inserts trace segments progressively, handling spring-over when
   * needed</li>
   * <li>Applies neckdown routing near pins when beneficial and enabled</li>
   * <li>Removes trace tails (stubs with no connections) after insertion</li>
   * </ul>
   *
   * <p>
   * <strong>Neckdown Routing:</strong> When enabled (ctrl.with_neckdown), the
   * algorithm
   * attempts to use thinner traces near pins to improve routing density and
   * reduce
   * manufacturing constraints near pad areas.
   *
   * <p>
   * <strong>Spring-Over Handling:</strong> If insertion fails at a point but
   * succeeds
   * at the start, the algorithm may retry with more distant corners to allow the
   * spring-over
   * mechanism to route around obstacles.
   *
   * <p>
   * The method maintains first_corner and last_corner tracking for endpoint
   * connections.
   *
   * @param p_trace the result item containing the trace geometry (array of corner
   *                points)
   * @return true if the entire trace was successfully inserted, false if
   *         insertion failed
   *
   * @see #insert_neckdown(Point, Point, int, Pin, Pin)
   * @see RoutingBoard#insert_forced_trace_polyline
   */
  private boolean insert_trace(LocateFoundConnectionAlgoAnyAngle.ResultItem p_trace) {
    if (p_trace.corners.length == 1) {
      if (this.first_corner == null) {
        this.first_corner = p_trace.corners[0];
      }
      this.last_corner = p_trace.corners[0];
      return true;
    }
    boolean result = true;

    // switch off correcting connection to pin because it may get wrong in inserting
    // the polygon
    // line for line.
    double saved_edge_to_turn_dist = board.rules.get_pin_edge_to_turn_dist();
    board.rules.set_pin_edge_to_turn_dist(-1);

    // Look for pins att the start and the end of p_trace in case that neckdown is
    // necessary.
    Pin start_pin = null;
    Pin end_pin = null;
    if (ctrl.with_neckdown) {
      ItemSelectionFilter item_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
      Point curr_end_corner = p_trace.corners[0];
      for (int i = 0; i < 2; i++) {
        Set<Item> picked_items = this.board.pick_items(curr_end_corner, p_trace.layer, item_filter);
        for (Item curr_item : picked_items) {
          Pin curr_pin = (Pin) curr_item;
          if (curr_pin.contains_net(ctrl.net_no) && curr_pin
              .get_center()
              .equals(curr_end_corner)) {
            if (i == 0) {
              start_pin = curr_pin;
            } else {
              end_pin = curr_pin;
            }
          }
        }
        curr_end_corner = p_trace.corners[p_trace.corners.length - 1];
      }
    }
    int[] net_no_arr = new int[1];
    net_no_arr[0] = ctrl.net_no;

    // Track the point we've successfully inserted up to, not the index
    // This is immune to board geometry changes from trace combines
    Point from_corner_point = p_trace.corners[0];

    for (int i = 1; i < p_trace.corners.length; i++) {
      // Find the current index of from_corner_point in the corners array
      // Search backwards from i-1 to find the closest preceding instance (handles
      // duplicate points)
      int from_corner_no = -1;
      for (int k = i - 1; k >= 0; k--) {
        if (p_trace.corners[k].equals(from_corner_point)) {
          from_corner_no = k;
          break;
        }
      }

      if (from_corner_no < 0) {
        // Point not found - this shouldn't happen but log and abort if it does
        FRLogger.warn("InsertFoundConnectionAlgo: from_corner_point " + from_corner_point
            + " not found in corners array preceding index " + i);
        result = false;
        break;
      }

      Point[] curr_corner_arr = Arrays.copyOfRange(p_trace.corners, from_corner_no, i + 1);
      Polyline insert_polyline = new Polyline(curr_corner_arr);
      FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "insert_trace_segment",
          "inserting trace segment from " + insert_polyline.first_corner() + " to " + insert_polyline.last_corner()
              + " on layer " + p_trace.layer,
          FRLogger.formatNetLabel(this.board, ctrl.net_no),
          new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
      // Log pre-insertion state
      if ((globalSettings != null) && (globalSettings.debugSettings != null)
          && (globalSettings.debugSettings.enableDetailedLogging)) {
        FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "pre_insertion",
            FRLogger.buildTracePayload("insert_segment", "pre_insertion", "state",
                "event=pre_insertion phase=before action=insert"
                    + " corner_index=" + i
                    + " corner_count=" + p_trace.corners.length
                    + " from_corner=" + insert_polyline.first_corner()
                    + " to_corner=" + insert_polyline.last_corner()
                    + " layer=" + p_trace.layer
                    + " half_width=" + ctrl.trace_half_width[p_trace.layer]),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
      }

      Point ok_point = board.insert_forced_trace_polyline(insert_polyline, ctrl.trace_half_width[p_trace.layer],
          p_trace.layer, net_no_arr, ctrl.trace_clearance_class_no,
          ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
          ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE, ctrl.pull_tight_accuracy, true, null);

      // Log post-insertion result
      if ((globalSettings != null) && (globalSettings.debugSettings != null)
          && (globalSettings.debugSettings.enableDetailedLogging)) {
        String resultType;
        if (ok_point == null) {
          resultType = "failed_null";
        } else if (ok_point.equals(insert_polyline.last_corner())) {
          resultType = "success_full";
        } else if (ok_point.equals(insert_polyline.first_corner())) {
          resultType = "failed_at_start";
        } else {
          resultType = "partial_" + ok_point;
        }

        FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "post_insertion",
            FRLogger.buildTracePayload("insert_segment", "post_insertion", resultType,
                "event=post_insertion phase=after action=result"
                    + " corner_index=" + i
                    + " ok_point=" + (ok_point != null ? ok_point.toString() : "null")
                    + " expected_end=" + insert_polyline.last_corner()
                    + " from_corner=" + insert_polyline.first_corner()
                    + " result_type=" + resultType),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
      }

      FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "insert_trace_segment_result",
          "insert result ok_point=" + (ok_point != null ? ok_point.toString() : "null")
              + ", first=" + insert_polyline.first_corner()
              + ", last=" + insert_polyline.last_corner()
              + ", corners=" + insert_polyline.corner_count()
              + " from_corner_point=" + from_corner_point
              + ", i=" + i + "/" + (p_trace.corners.length - 1),
          FRLogger.formatNetLabel(this.board, ctrl.net_no),
          new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
      boolean neckdown_inserted = false;
      if (ok_point != null && ok_point != insert_polyline.last_corner() && ctrl.with_neckdown
          && curr_corner_arr.length == 2) {
        neckdown_inserted = insert_neckdown(ok_point, curr_corner_arr[1], p_trace.layer, start_pin, end_pin);
      }
      if (ok_point == insert_polyline.last_corner() || neckdown_inserted) {
        Point previous_from_corner_point = from_corner_point;
        from_corner_point = p_trace.corners[i]; // Update to the point at index i
        FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "segment_committed",
            "segment committed, from_corner_point=" + previous_from_corner_point + " -> " + from_corner_point
                + ", ok_point=" + ok_point
                + ", neckdown=" + neckdown_inserted,
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
      } else if (ok_point == insert_polyline.first_corner() && i != p_trace.corners.length - 1) {
        // if ok_point == insert_polyline.first_corner() the spring over may have
        // failed.
        // Spring over may correct the situation because an insertion, which is ok with
        // clearance compensation may cause violations without clearance compensation.
        // In this case repeating the insertion with more distant corners may allow the
        // spring_over to correct the situation.

        FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "insertion_failed_at_start",
            "Insertion returned at start point (ok_point == first_corner)"
                + ", from_corner_point=" + from_corner_point
                + ", curr_corner_arr.length=" + curr_corner_arr.length
                + ", i=" + i + "/" + (p_trace.corners.length - 1)
                + ", attempted segment=" + insert_polyline.first_corner() + " -> " + insert_polyline.last_corner(),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });

        Point previous_from_corner_point = from_corner_point;
        if (from_corner_no > 0) {
          // p_trace.corners[i] may be inside the offset for the substitute trace around
          // a spring_over obstacle (if clearance compensation is off).
          if (curr_corner_arr.length < 3) {
            // first correction - move back to the previous corner point
            from_corner_point = p_trace.corners[from_corner_no - 1];
          }
        }
        if (!from_corner_point.equals(previous_from_corner_point)) {
          FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "spring_over_backtrack",
              "spring-over backtrack, from_corner_point=" + previous_from_corner_point + " -> " + from_corner_point
                  + ", i=" + i + "/" + (p_trace.corners.length - 1)
                  + ", segment=" + insert_polyline.first_corner() + " -> " + insert_polyline.last_corner(),
              FRLogger.formatNetLabel(this.board, ctrl.net_no),
              new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });

          // Remove the trace stub that led to the dead end
          // We must be careful not to remove the whole trace if it's merged.
          // We pick the trace at the split point (from_corner_point which we just
          // retreated TO).
          // And we try to split it to isolate the stub going to
          // previous_from_corner_point.

          ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
          Set<Item> picked_items = board.pick_items(from_corner_point, p_trace.layer, filter);
          for (Item item : picked_items) {
            if (item instanceof PolylineTrace trace && trace.contains_net(ctrl.net_no)) {
              Trace[] pieces = trace.split(from_corner_point);

              if (pieces != null) {
                // Trace split successfully. Find the piece connected to the dead end.
                for (Trace piece : pieces) {
                  // Check if this piece contains the dead end point
                  if (piece.first_corner().equals(previous_from_corner_point)
                      || piece.last_corner().equals(previous_from_corner_point)) {
                    FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "remove_backtrack_stub",
                        "removing trace stub after split id=" + piece.get_id_no()
                            + ", from=" + piece.first_corner() + " -> " + piece.last_corner(),
                        FRLogger.formatNetLabel(this.board, ctrl.net_no), new Point[0]);
                    board.remove_item(piece);
                  }
                }
              } else {
                // Split returned null, possibly because from_corner_point is an endpoint.
                // If it is the start/end, and the trace connects to the dead end, remove it.
                if (trace.first_corner().equals(from_corner_point)) {
                  if (trace.last_corner().equals(previous_from_corner_point)
                      || trace.polyline().contains(previous_from_corner_point)) {
                    FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "remove_backtrack_endpoint_stub",
                        "removing trace stub (endpoint match) id=" + trace.get_id_no()
                            + ", from=" + trace.first_corner() + " -> " + trace.last_corner(),
                        FRLogger.formatNetLabel(this.board, ctrl.net_no), new Point[0]);
                    board.remove_item(trace);
                  }
                } else if (trace.last_corner().equals(from_corner_point)) {
                  if (trace.first_corner().equals(previous_from_corner_point)
                      || trace.polyline().contains(previous_from_corner_point)) {
                    FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "remove_backtrack_endpoint_stub_rev",
                        "removing trace stub (reverse match) id=" + trace.get_id_no()
                            + ", from=" + trace.first_corner() + " -> " + trace.last_corner(),
                        FRLogger.formatNetLabel(this.board, ctrl.net_no), new Point[0]);
                    board.remove_item(trace);
                  }
                }
              }
            }
          }
        }
        FRLogger.trace("InsertFoundConnectionAlgo.insert_segment", "spring_over_retry",
            "spring-over retry from_corner_point=" + from_corner_point
                + ", i=" + i + "/" + (p_trace.corners.length - 1)
                + ", segment=" + insert_polyline.first_corner() + " -> " + insert_polyline.last_corner(),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { insert_polyline.first_corner(), insert_polyline.last_corner() });
        FRLogger.trace("InsertFoundConnectionAlgo: violation corrected");
      } else {
        // Log detailed information about where insertion failed
        FRLogger.trace("InsertFoundConnectionAlgo.insert_trace", "insert_trace_failure",
            "InsertFoundConnectionAlgo: insert trace failed for net #" + ctrl.net_no +
                " at corner " + i + "/" + (p_trace.corners.length - 1) +
                " on layer " + p_trace.layer +
                ", trace width: " + ctrl.trace_half_width[p_trace.layer] +
                ", from corner point: " + from_corner_point +
                ", ok_point: " + (ok_point != null ? ok_point.toString() : "null") +
                ", target: " + insert_polyline.last_corner(),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { from_corner_point, insert_polyline.last_corner() });
        result = false;
        break;
      }

    }

    for (int i = 0; i < p_trace.corners.length - 1; i++) {
      Trace trace_stub = board.get_trace_tail(p_trace.corners[i], p_trace.layer, net_no_arr);
      if (trace_stub != null) {
        FRLogger.trace("InsertFoundConnectionAlgo.insert_trace", "remove_trace_tail",
            FRLogger.buildTracePayload("insert_trace", "tail_cleanup", "remove",
                "tail_id=" + trace_stub.get_id_no()
                    + " layer=" + trace_stub.get_layer()
                    + " start=" + trace_stub.first_corner()
                    + " end=" + trace_stub.last_corner()
                    + " corner_index=" + i
                    + " check_location=" + p_trace.corners[i]
                    + " insertion_result=" + result
                    + " start_contacts=" + trace_stub.get_start_contacts().size()
                    + " end_contacts=" + trace_stub.get_end_contacts().size()),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { trace_stub.first_corner(), trace_stub.last_corner() });
        board.remove_item(trace_stub);
      } else {
        FRLogger.trace("InsertFoundConnectionAlgo.insert_trace", "no_tail_at_corner",
            FRLogger.buildTracePayload("insert_trace", "tail_cleanup", "check",
                "corner_index=" + i
                    + " check_location=" + p_trace.corners[i]
                    + " layer=" + p_trace.layer
                    + " no_tail_found=true"
                    + " insertion_result=" + result),
            FRLogger.formatNetLabel(this.board, ctrl.net_no),
            new Point[] { p_trace.corners[i] });
      }
    }

    board.rules.set_pin_edge_to_turn_dist(saved_edge_to_turn_dist);
    if (this.first_corner == null) {
      this.first_corner = p_trace.corners[0];
    }
    this.last_corner = p_trace.corners[p_trace.corners.length - 1];

    // Log final state of all traces on this net after insertion
    if ((globalSettings != null) && (globalSettings.debugSettings != null)
        && (globalSettings.debugSettings.enableDetailedLogging) && net_no_arr.length > 0) {
      int netNo = net_no_arr[0];
      java.util.Collection<Item> netItems = board.get_connectable_items(netNo);
      int traceCount = 0;
      StringBuilder traceDetails = new StringBuilder();
      for (Item item : netItems) {
        if (item instanceof Trace trace) {
          traceCount++;
          if (!traceDetails.isEmpty()) {
            traceDetails.append("; ");
          }
          Set<Item> startContacts = trace.get_start_contacts();
          Set<Item> endContacts = trace.get_end_contacts();
          traceDetails.append(String.format("Trace#%d[%s->%s,start_contacts=%d,end_contacts=%d]",
              trace.get_id_no(), trace.first_corner(), trace.last_corner(),
              startContacts.size(), endContacts.size()));
        }
      }
      FRLogger.trace("InsertFoundConnectionAlgo.insert_trace", "post_insertion_state",
          "event=insertion_complete action=state_check result=" + result
              + " net=" + netNo + " total_net_items=" + netItems.size()
              + " trace_count=" + traceCount + " traces=[" + traceDetails + "]",
          FRLogger.formatNetLabel(this.board, netNo),
          new Point[] { p_trace.corners[0], p_trace.corners[p_trace.corners.length - 1] });
    }

    return result;
  }

  /**
   * Attempts to insert a neckdown trace segment connecting between two points.
   *
   * <p>
   * Neckdown routing uses a thinner trace width near pins to:
   * <ul>
   * <li>Reduce manufacturing constraints around pads</li>
   * <li>Improve routing density in congested areas</li>
   * <li>Provide smoother transitions from pin to trace</li>
   * <li>Comply with pin-specific neckdown requirements</li>
   * </ul>
   *
   * <p>
   * This method checks both the start and end pins (if present) and attempts
   * neckdown insertion from whichever pin is appropriate. Only one neckdown is
   * performed per segment.
   *
   * @param p_from_corner the starting point of the segment
   * @param p_to_corner   the ending point of the segment
   * @param p_layer       the layer where the neckdown will be inserted
   * @param p_start_pin   the pin at the start of the segment, may be null
   * @param p_end_pin     the pin at the end of the segment, may be null
   * @return true if neckdown insertion succeeded, false otherwise
   *
   * @see #try_neck_down(Point, Point, int, Pin, boolean)
   */
  boolean insert_neckdown(Point p_from_corner, Point p_to_corner, int p_layer, Pin p_start_pin, Pin p_end_pin) {
    if (p_start_pin != null) {
      Point ok_point = try_neck_down(p_to_corner, p_from_corner, p_layer, p_start_pin, true);
      if (ok_point == p_from_corner) {
        return true;
      }
    }
    if (p_end_pin != null) {
      Point ok_point = try_neck_down(p_from_corner, p_to_corner, p_layer, p_end_pin, false);
      return ok_point == p_to_corner;
    }
    return false;
  }

  /**
   * Attempts to insert a neckdown trace segment from a point toward a pin.
   *
   * <p>
   * This method implements the detailed neckdown insertion algorithm:
   * <ol>
   * <li>Validates that the pin is on the specified layer</li>
   * <li>Calculates the neckdown distance based on pin size and clearance</li>
   * <li>Checks if the endpoint is within neckdown range of the pin</li>
   * <li>Determines the neckdown trace width from pin specifications</li>
   * <li>Finds the transition point where neckdown should begin</li>
   * <li>Inserts normal-width trace up to the transition point</li>
   * <li>Inserts narrower neckdown trace from transition to endpoint</li>
   * </ol>
   *
   * <p>
   * The algorithm uses a tolerance to ensure clean connections and may insert
   * additional corners to maintain angle restrictions while transitioning between
   * different trace widths.
   *
   * <p>
   * <strong>Neckdown Distance Calculation:</strong>
   * The neckdown region extends from the pin by a distance of:
   * {@code 2 * (0.5 * pin_width + clearance)}
   *
   * <p>
   * If the neckdown cannot improve the routing (e.g., neckdown width >= normal
   * width,
   * or endpoint too far from pin), the method returns null to indicate no
   * neckdown
   * should be applied.
   *
   * @param p_from_corner the starting point of the neckdown segment
   * @param p_to_corner   the ending point (typically at or near the pin)
   * @param p_layer       the layer where neckdown will be inserted
   * @param p_pin         the pin that requires or benefits from neckdown
   * @param p_at_start    true if the pin is at the start of the overall trace,
   *                      false if at the end
   * @return the point where neckdown insertion succeeded, or null if neckdown
   *         failed or is not beneficial
   *
   * @see Pin#get_trace_neckdown_halfwidth(int)
   * @see RoutingBoard#insert_forced_trace_segment
   */
  private Point try_neck_down(Point p_from_corner, Point p_to_corner, int p_layer, Pin p_pin, boolean p_at_start) {
    if (!p_pin.is_on_layer(p_layer)) {
      return null;
    }
    FloatPoint pin_center = p_pin
        .get_center()
        .to_float();
    double curr_clearance = this.board.rules.clearance_matrix.get_value(ctrl.trace_clearance_class_no,
        p_pin.clearance_class_no(), p_layer, true);
    double pin_neck_down_distance = 2 * (0.5 * p_pin.get_max_width(p_layer) + curr_clearance);
    if (pin_center.distance(p_to_corner.to_float()) >= pin_neck_down_distance) {
      return null;
    }

    int neck_down_halfwidth = p_pin.get_trace_neckdown_halfwidth(p_layer);
    if (neck_down_halfwidth >= ctrl.trace_half_width[p_layer]) {
      return null;
    }

    FloatPoint float_from_corner = p_from_corner.to_float();
    FloatPoint float_to_corner = p_to_corner.to_float();

    final int TOLERANCE = 2;

    int[] net_no_arr = new int[1];
    net_no_arr[0] = ctrl.net_no;

    double ok_length = board.check_trace_segment(p_from_corner, p_to_corner, p_layer, net_no_arr,
        ctrl.trace_half_width[p_layer], ctrl.trace_clearance_class_no, true);
    if (ok_length >= Integer.MAX_VALUE) {
      return p_from_corner;
    }
    ok_length -= TOLERANCE;
    Point neck_down_end_point;
    if (ok_length <= TOLERANCE) {
      neck_down_end_point = p_from_corner;
    } else {
      FloatPoint float_neck_down_end_point = float_from_corner.change_length(float_to_corner, ok_length);
      neck_down_end_point = float_neck_down_end_point.round();
      // add a corner in case neck_down_end_point is not exactly on the line from
      // p_from_corner to
      // p_to_corner
      boolean horizontal_first = Math.abs(float_from_corner.x - float_neck_down_end_point.x) >= Math
          .abs(float_from_corner.y - float_neck_down_end_point.y);
      IntPoint add_corner = LocateFoundConnectionAlgo
          .calculate_additional_corner(float_from_corner, float_neck_down_end_point, horizontal_first,
              board.rules.get_trace_angle_restriction())
          .round();
      Point curr_ok_point = board.insert_forced_trace_segment(p_from_corner, add_corner, ctrl.trace_half_width[p_layer],
          p_layer, net_no_arr, ctrl.trace_clearance_class_no,
          ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
          ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE, ctrl.pull_tight_accuracy, true, null);
      if (curr_ok_point != add_corner) {
        return p_from_corner;
      }
      curr_ok_point = board.insert_forced_trace_segment(add_corner, neck_down_end_point, ctrl.trace_half_width[p_layer],
          p_layer, net_no_arr, ctrl.trace_clearance_class_no,
          ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
          ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE, ctrl.pull_tight_accuracy, true, null);
      if (curr_ok_point != neck_down_end_point) {
        return p_from_corner;
      }
      add_corner = LocateFoundConnectionAlgo
          .calculate_additional_corner(float_neck_down_end_point, float_to_corner, !horizontal_first,
              board.rules.get_trace_angle_restriction())
          .round();
      if (!add_corner.equals(p_to_corner)) {
        curr_ok_point = board.insert_forced_trace_segment(neck_down_end_point, add_corner,
            ctrl.trace_half_width[p_layer], p_layer, net_no_arr, ctrl.trace_clearance_class_no,
            ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
            ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE, ctrl.pull_tight_accuracy, true, null);
        if (curr_ok_point != add_corner) {
          return p_from_corner;
        }
        neck_down_end_point = add_corner;
      }
    }

    Point ok_point = board.insert_forced_trace_segment(neck_down_end_point, p_to_corner, neck_down_halfwidth, p_layer,
        net_no_arr, ctrl.trace_clearance_class_no, ctrl.max_shove_trace_recursion_depth,
        ctrl.max_shove_via_recursion_depth, ctrl.max_spring_over_recursion_depth, Integer.MAX_VALUE,
        ctrl.pull_tight_accuracy, true, null);
    return ok_point;
  }

  /**
   * Finds and inserts the most cost-effective via to connect two layers at the
   * specified location.
   *
   * <p>
   * This method searches through available via types (from the via rule) to find
   * one that:
   * <ul>
   * <li>Spans between the required layers (from_layer to to_layer)</li>
   * <li>Can be inserted at the location without clearance violations</li>
   * <li>Is the cheapest option (vias are checked in order of increasing
   * cost)</li>
   * </ul>
   *
   * <p>
   * The algorithm:
   * <ol>
   * <li>Returns immediately if both layers are the same (no via needed)</li>
   * <li>Normalizes layer order (always from lower to higher layer number)</li>
   * <li>Iterates through via types in the via rule</li>
   * <li>Checks each via's layer span and clearance feasibility</li>
   * <li>Inserts the first suitable via found</li>
   * </ol>
   *
   * <p>
   * If no suitable via is found (due to layer span limitations or clearance
   * violations), the method returns false and logs debug information about the
   * failure.
   *
   * @param p_location   the board position where the via should be inserted
   * @param p_from_layer the starting layer index
   * @param p_to_layer   the ending layer index
   * @return true if a via was successfully inserted (or none was needed), false
   *         if insertion failed
   *
   * @see ForcedViaAlgo#check
   * @see ForcedViaAlgo#insert
   * @see ViaInfo
   */
  private boolean insert_via(Point p_location, int p_from_layer, int p_to_layer) {
    if (p_from_layer == p_to_layer) {
      return true; // no via necessary
    }
    int from_layer;
    int to_layer;
    // sort the input layers
    if (p_from_layer < p_to_layer) {
      from_layer = p_from_layer;
      to_layer = p_to_layer;
    } else {
      from_layer = p_to_layer;
      to_layer = p_from_layer;
    }
    int[] net_no_arr = new int[1];
    net_no_arr[0] = ctrl.net_no;
    ViaInfo via_info = null;
    String netLabel = FRLogger.formatNetLabel(this.board, ctrl.net_no);
    for (int i = 0; i < this.ctrl.via_rule.via_count(); i++) {
      ViaInfo curr_via_info = this.ctrl.via_rule.get_via(i);
      Padstack curr_via_padstack = curr_via_info.get_padstack();
      if (curr_via_padstack.from_layer() > from_layer || curr_via_padstack.to_layer() < to_layer) {
        continue;
      }
      if (ForcedViaAlgo.check(curr_via_info, p_location, net_no_arr, this.ctrl.max_shove_trace_recursion_depth,
          this.ctrl.max_shove_via_recursion_depth, this.board)) {
        via_info = curr_via_info;
        break;
      }
    }
    if (via_info == null) {
      FRLogger.debug("InsertFoundConnectionAlgo: via mask not found for " + netLabel
          + " at " + p_location + " between layers " + from_layer + " and " + to_layer);
      return false;
    }
    // insert the via
    if (!ForcedViaAlgo.insert(via_info, p_location, net_no_arr, this.ctrl.trace_clearance_class_no,
        this.ctrl.trace_half_width, this.ctrl.max_shove_trace_recursion_depth,
        this.ctrl.max_shove_via_recursion_depth, this.board)) {
      FRLogger.debug("InsertFoundConnectionAlgo: forced via failed for " + netLabel
          + " at " + p_location + " with via " + via_info.get_name());
      return false;
    }
    return true;
  }
}