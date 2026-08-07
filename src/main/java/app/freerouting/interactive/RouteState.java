package app.freerouting.interactive;

import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Interactive routing state. */
public class RouteState extends InteractiveState {

  protected Route route;
  protected boolean observersActivated;
  private Set<Item> routingTargetSet;

  /**
   * Creates a new instance of RouteState If p_logfile != null, the creation of the route is stored
   * in the logfile.
   */
  protected RouteState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  /**
   * Returns a new instance of this class or null, if starting a new route was not possible at
   * p_location. If p_logfile != null, the creation of the route is stored in the logfile.
   */
  public static RouteState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    if (!(p_parent_state instanceof MenuState)) {
      FRLogger.warn("RouteState.get_instance: unexpected parent state");
    }
    p_board_handling.display_layer_message();
    IntPoint location = p_location.round();
    Item pickedItem = start_ok(location, p_board_handling);
    if (pickedItem == null) {
      return null;
    }
    int netCount = pickedItem.net_count();
    if (netCount <= 0) {
      return null;
    }
    int[] routeNetNoArr;
    if (pickedItem instanceof Pin pin && netCount > 1) {
      // tie pin, remove nets, which are already connected to this pin on the current
      // layer.
      routeNetNoArr =
          get_route_net_numbers_at_tie_pin(
              pin, p_board_handling.getInteractiveSettings().get_layer());
    } else {
      routeNetNoArr = new int[netCount];
      for (int i = 0; i < netCount; i++) {
        routeNetNoArr[i] = pickedItem.get_net_no(i);
      }
    }
    if (routeNetNoArr.length == 0) {
      return null;
    }
    RoutingBoard routingBoard = p_board_handling.get_routing_board();
    int[] traceHalfWidths = new int[routingBoard.get_layer_count()];
    boolean[] layerActiveArr = new boolean[traceHalfWidths.length];
    for (int i = 0; i < traceHalfWidths.length; i++) {
      traceHalfWidths[i] = p_board_handling.get_trace_halfwidth(routeNetNoArr[0], i);
      layerActiveArr[i] = false;
      for (int j = 0; j < routeNetNoArr.length; j++) {
        if (p_board_handling.is_active_routing_layer(routeNetNoArr[j], i)) {
          layerActiveArr[i] = true;
        }
      }
    }

    int traceClearanceClass = p_board_handling.get_trace_clearance_class(routeNetNoArr[0]);
    boolean startOk = true;
    if (pickedItem instanceof Trace pickedTrace) {
      Point pickedCorner = pickedTrace.nearest_end_point(location);
      if (pickedCorner instanceof IntPoint point
          && p_location.distance(pickedCorner.to_float()) < 5 * pickedTrace.get_half_width()) {
        location = point;
      } else {
        if (pickedTrace instanceof PolylineTrace trace) {
          FloatPoint nearestPoint = trace.polyline().nearest_point_approx(p_location);
          location = nearestPoint.round();
        }
        if (!routingBoard.connect_to_trace(
            location,
            pickedTrace,
            pickedTrace.get_half_width(),
            pickedTrace.clearance_class_no())) {
          startOk = false;
        }
      }
      if (startOk && !p_board_handling.getInteractiveSettings().get_manual_rule_selection()) {
        // Pick up the half with and the clearance class of the found trace.
        int[] newTraceHalfWidths = new int[traceHalfWidths.length];
        System.arraycopy(traceHalfWidths, 0, newTraceHalfWidths, 0, traceHalfWidths.length);
        newTraceHalfWidths[pickedTrace.get_layer()] = pickedTrace.get_half_width();
        traceHalfWidths = newTraceHalfWidths;
        traceClearanceClass = pickedTrace.clearance_class_no();
      }
    } else if (pickedItem instanceof DrillItem drill_item) {
      Point center = drill_item.get_center();
      if (center instanceof IntPoint point) {
        location = point;
      }
    }
    if (!startOk) {
      return null;
    }

    Net currNet = routingBoard.rules.nets.get(routeNetNoArr[0]);
    if (currNet == null) {
      return null;
    }
    // Switch to stitch mode for nets, which are shove fixed.
    boolean isStitchRoute =
        p_board_handling.getInteractiveSettings().get_is_stitch_route()
            || currNet.get_class().is_shove_fixed()
            || !currNet.get_class().get_pull_tight();
    routingBoard.generate_snapshot();
    RouteState newInstance;
    if (isStitchRoute) {
      newInstance = new StitchRouteState(p_parent_state, p_board_handling);
    } else {
      newInstance = new DynamicRouteState(p_parent_state, p_board_handling);
    }
    newInstance.routingTargetSet = pickedItem.get_unconnected_set(-1);

    newInstance.route =
        new Route(
            location,
            p_board_handling.getInteractiveSettings().get_layer(),
            traceHalfWidths,
            layerActiveArr,
            routeNetNoArr,
            traceClearanceClass,
            p_board_handling.get_via_rule(routeNetNoArr[0]),
            p_board_handling.getInteractiveSettings().get_push_enabled(),
            p_board_handling.getInteractiveSettings().get_trace_pull_tight_region_width(),
            p_board_handling.getInteractiveSettings().get_trace_pull_tight_accuracy(),
            pickedItem,
            newInstance.routingTargetSet,
            routingBoard,
            isStitchRoute,
            p_board_handling.getInteractiveSettings().get_automatic_neckdown(),
            p_board_handling.getInteractiveSettings().get_via_snap_to_smd_center(),
            p_board_handling.getInteractiveSettings().get_hilight_routing_obstacle());
    newInstance.observersActivated = !routingBoard.observers_active();
    if (newInstance.observersActivated) {
      routingBoard.start_notify_observers();
    }
    p_board_handling.repaint();
    newInstance.display_default_message();
    return newInstance;
  }

  /**
   * Checks starting an interactive route at p_location. Returns the picked start item of the
   * routing at p_location, or null, if no such item was found.
   */
  protected static Item start_ok(IntPoint p_location, GuiBoardManager p_hdlg) {
    RoutingBoard routingBoard = p_hdlg.get_routing_board();

    /*
     * look if an already existing trace ends at p_start_corner
     * and pick it up in this case.
     */
    Item pickedItem =
        routingBoard.pick_nearest_routing_item(
            p_location, p_hdlg.getInteractiveSettings().get_layer(), null);
    int layerCount = routingBoard.get_layer_count();
    if (pickedItem == null && p_hdlg.getInteractiveSettings().get_select_on_all_visible_layers()) {
      // Nothing found on preferred layer, try the other visible layers.
      // Prefer the outer layers.
      pickedItem = pick_routing_item(p_location, 0, p_hdlg);
      if (pickedItem == null) {
        pickedItem = pick_routing_item(p_location, layerCount - 1, p_hdlg);
      }
      // prefer signal layers
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pick_routing_item(p_location, i, p_hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (!routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pick_routing_item(p_location, i, p_hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
    }
    return pickedItem;
  }

  private static Item pick_routing_item(
      IntPoint p_location, int p_layer_no, GuiBoardManager p_hdlg) {

    if (p_layer_no == p_hdlg.getInteractiveSettings().get_layer()
        || (p_hdlg.graphicsContext.get_layer_visibility(p_layer_no) <= 0)) {
      return null;
    }
    Item pickedItem =
        p_hdlg.get_routing_board().pick_nearest_routing_item(p_location, p_layer_no, null);
    if (pickedItem != null) {
      p_hdlg.set_layer(pickedItem.first_layer());
    }
    return pickedItem;
  }

  /**
   * get nets of p_tie_pin except nets of traces, which are already connected to this pin on
   * p_layer.
   */
  static int[] get_route_net_numbers_at_tie_pin(Pin p_pin, int p_layer) {
    Set<Integer> netNumberList = new TreeSet<>();
    for (int i = 0; i < p_pin.net_count(); i++) {
      netNumberList.add(p_pin.get_net_no(i));
    }
    Set<Item> contacts = p_pin.get_normal_contacts();
    for (Item currContact : contacts) {
      if (currContact.first_layer() <= p_layer && currContact.last_layer() >= p_layer) {
        for (int i = 0; i < currContact.net_count(); i++) {
          netNumberList.remove(currContact.get_net_no(i));
        }
      }
    }
    int[] result = new int[netNumberList.size()];
    int currInd = 0;
    for (Integer curr_net_number : netNumberList) {
      result[currInd] = curr_net_number;
      ++currInd;
    }
    return result;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState currReturnState = this;
    if (Character.isDigit(p_key_char)) {
      // change to the p_key_char-ths signal layer
      LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
      int d = Character.digit(p_key_char, 10);
      d = Math.min(d, layerStructure.signal_layer_count());
      // Board layers start at 0, keyboard input for layers starts at 1.
      d = Math.max(d - 1, 0);
      Layer newLayer = layerStructure.get_signal_layer(d);
      d = layerStructure.get_no(newLayer);

      if (d >= 0) {
        change_layer_action(d);
      }
    } else if (p_key_char == '+') {
      // change to the next signal layer
      LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
      int currentLayerNo = hdlg.getInteractiveSettings().get_layer();
      do {
        ++currentLayerNo;
      } while (currentLayerNo < layerStructure.arr.length
          && !layerStructure.arr[currentLayerNo].isSignal);
      if (currentLayerNo < layerStructure.arr.length) {
        change_layer_action(currentLayerNo);
      }
    } else if (p_key_char == '-') {
      // change to the previous signal layer
      LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
      int currentLayerNo = hdlg.getInteractiveSettings().get_layer();
      do {
        --currentLayerNo;
      } while (currentLayerNo >= 0 && !layerStructure.arr[currentLayerNo].isSignal);
      if (currentLayerNo >= 0) {
        change_layer_action(currentLayerNo);
      }

    } else {
      currReturnState = super.key_typed(p_key_char);
    }
    return currReturnState;
  }

  /**
   * Append a line to p_location to the trace routed so far. Returns from state, if the route is
   * completed by connecting to a target.
   */
  public InteractiveState add_corner(FloatPoint p_location) {
    boolean routeCompleted = route.next_corner(p_location);
    String layerString =
        hdlg.get_routing_board().layerStructure.arr[route.nearest_target_layer()].name;
    hdlg.screenMessages.set_target_layer(layerString);
    if (routeCompleted) {
      if (this.observersActivated) {
        hdlg.get_routing_board().end_notify_observers();
        this.observersActivated = false;
      }
    }
    InteractiveState result;
    if (routeCompleted) {
      result = this.returnState;
      hdlg.screenMessages.clear();
      for (int currNetNo : this.route.netNoArr) {
        hdlg.update_ratsnest(currNetNo);
      }
    } else {
      result = this;
    }
    hdlg.recalculate_length_violations();
    hdlg.repaint(hdlg.get_graphics_update_rectangle());
    return result;
  }

  @Override
  public InteractiveState cancel() {
    Trace tail =
        hdlg.get_routing_board()
            .get_trace_tail(
                route.get_last_corner(), hdlg.getInteractiveSettings().get_layer(), route.netNoArr);
    if (tail != null) {
      Collection<Item> removeItems = tail.get_connection_items(Item.StopConnectionOption.VIA);
      if (hdlg.getInteractiveSettings().get_push_enabled()) {
        hdlg.get_routing_board()
            .remove_items_and_pull_tight(
                removeItems,
                hdlg.getInteractiveSettings().get_trace_pull_tight_region_width(),
                hdlg.getInteractiveSettings().get_trace_pull_tight_accuracy());
      } else {
        hdlg.get_routing_board().remove_items(removeItems);
      }
    }
    if (this.observersActivated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observersActivated = false;
    }
    hdlg.screenMessages.clear();
    for (int currNetNo : this.route.netNoArr) {
      hdlg.update_ratsnest(currNetNo);
    }
    return this.returnState;
  }

  @Override
  public boolean change_layer_action(int p_new_layer) {
    boolean result = true;
    if (p_new_layer >= 0 && p_new_layer < hdlg.get_routing_board().get_layer_count()) {
      if (this.route != null && !this.route.is_layer_active(p_new_layer)) {
        String layerName = hdlg.get_routing_board().layerStructure.arr[p_new_layer].name;
        hdlg.screenMessages.set_status_message(
            tm.getText("layer_not_changed_inactive_net_message", layerName));
      }
      boolean changeLayerSucceeded = route.change_layer(p_new_layer);
      if (changeLayerSucceeded) {
        boolean connectedToPlane = false;
        // check, if the layer change resulted in a connection to a power plane.
        int oldLayer = hdlg.getInteractiveSettings().get_layer();
        ItemSelectionFilter selectionFilter =
            new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
        Collection<Item> pickedItems =
            hdlg.get_routing_board().pick_items(route.get_last_corner(), oldLayer, selectionFilter);
        Via newVia = null;
        for (Item currVia : pickedItems) {
          if (currVia.shares_net_no(route.netNoArr)) {
            newVia = (Via) currVia;
            break;
          }
        }
        if (newVia != null) {
          int fromLayer;
          int toLayer;
          if (oldLayer < p_new_layer) {
            fromLayer = oldLayer + 1;
            toLayer = p_new_layer;
          } else {
            fromLayer = p_new_layer;
            toLayer = oldLayer - 1;
          }
          Collection<Item> contacts = newVia.get_normal_contacts();
          for (Item currItem : contacts) {
            if (currItem instanceof ConductionArea currArea) {
              if (currArea.get_layer() >= fromLayer && currArea.get_layer() <= toLayer) {
                connectedToPlane = true;
                break;
              }
            }
          }
        }

        if (connectedToPlane) {
          hdlg.set_interactive_state(this.returnState);
          for (int currNetNo : this.route.netNoArr) {
            hdlg.update_ratsnest(currNetNo);
          }
        } else {
          hdlg.set_layer(p_new_layer);
          String layerName = hdlg.get_routing_board().layerStructure.arr[p_new_layer].name;
          hdlg.screenMessages.set_status_message(tm.getText("layer_changed_to_message", layerName));
          // make the current situation restorable by undo
          hdlg.get_routing_board().generate_snapshot();
        }
      } else {
        int shoveFailingLayer = hdlg.get_routing_board().get_shove_failing_layer();
        if (shoveFailingLayer >= 0) {
          String layerName =
              hdlg.get_routing_board()
                  .layerStructure
                  .arr[hdlg.get_routing_board().get_shove_failing_layer()]
                  .name;
          hdlg.screenMessages.set_status_message(
              tm.getText("layer_not_changed_obstacle_message", layerName));
        } else {
          FRLogger.warn("RouteState.change_layer_action: shoveFailingLayer not set");
        }
        result = false;
      }
      hdlg.repaint();
    }
    return result;
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (route != null) {
      route.draw(p_graphics, hdlg.graphicsContext);
    }
  }

  @Override
  public void display_default_message() {
    if (route != null) {
      Net currNet = hdlg.get_routing_board().rules.nets.get(route.netNoArr[0]);
      hdlg.screenMessages.set_status_message(tm.getText("routing_net_message", currNet.name));
    }
  }
}
