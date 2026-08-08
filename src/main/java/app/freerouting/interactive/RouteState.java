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
  public static RouteState getInstance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    if (!(p_parent_state instanceof MenuState)) {
      FRLogger.warn("RouteState.get_instance: unexpected parent state");
    }
    p_board_handling.displayLayerMessage();
    IntPoint location = p_location.round();
    Item pickedItem = startOk(location, p_board_handling);
    if (pickedItem == null) {
      return null;
    }
    int netCount = pickedItem.netCount();
    if (netCount <= 0) {
      return null;
    }
    int[] routeNetNoArr;
    if (pickedItem instanceof Pin pin && netCount > 1) {
      // tie pin, remove nets, which are already connected to this pin on the current
      // layer.
      routeNetNoArr =
          getRouteNetNumbersAtTiePin(
              pin, p_board_handling.getInteractiveSettings().getLayer());
    } else {
      routeNetNoArr = new int[netCount];
      for (int i = 0; i < netCount; i++) {
        routeNetNoArr[i] = pickedItem.getNetNo(i);
      }
    }
    if (routeNetNoArr.length == 0) {
      return null;
    }
    RoutingBoard routingBoard = p_board_handling.getRoutingBoard();
    int[] traceHalfWidths = new int[routingBoard.getLayerCount()];
    boolean[] layerActiveArr = new boolean[traceHalfWidths.length];
    for (int i = 0; i < traceHalfWidths.length; i++) {
      traceHalfWidths[i] = p_board_handling.getTraceHalfwidth(routeNetNoArr[0], i);
      layerActiveArr[i] = false;
      for (int j = 0; j < routeNetNoArr.length; j++) {
        if (p_board_handling.isActiveRoutingLayer(routeNetNoArr[j], i)) {
          layerActiveArr[i] = true;
        }
      }
    }

    int traceClearanceClass = p_board_handling.getTraceClearanceClass(routeNetNoArr[0]);
    boolean startOk = true;
    if (pickedItem instanceof Trace pickedTrace) {
      Point pickedCorner = pickedTrace.nearestEndPoint(location);
      if (pickedCorner instanceof IntPoint point
          && p_location.distance(pickedCorner.toFloat()) < 5 * pickedTrace.getHalfWidth()) {
        location = point;
      } else {
        if (pickedTrace instanceof PolylineTrace trace) {
          FloatPoint nearestPoint = trace.polyline().nearestPointApprox(p_location);
          location = nearestPoint.round();
        }
        if (!routingBoard.connectToTrace(
            location,
            pickedTrace,
            pickedTrace.getHalfWidth(),
            pickedTrace.clearanceClassNo())) {
          startOk = false;
        }
      }
      if (startOk && !p_board_handling.getInteractiveSettings().getManualRuleSelection()) {
        // Pick up the half with and the clearance class of the found trace.
        int[] newTraceHalfWidths = new int[traceHalfWidths.length];
        System.arraycopy(traceHalfWidths, 0, newTraceHalfWidths, 0, traceHalfWidths.length);
        newTraceHalfWidths[pickedTrace.getLayer()] = pickedTrace.getHalfWidth();
        traceHalfWidths = newTraceHalfWidths;
        traceClearanceClass = pickedTrace.clearanceClassNo();
      }
    } else if (pickedItem instanceof DrillItem drill_item) {
      Point center = drill_item.getCenter();
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
        p_board_handling.getInteractiveSettings().getIsStitchRoute()
            || currNet.getNetClass().isShoveFixed()
            || !currNet.getNetClass().getPullTight();
    routingBoard.generateSnapshot();
    RouteState newInstance;
    if (isStitchRoute) {
      newInstance = new StitchRouteState(p_parent_state, p_board_handling);
    } else {
      newInstance = new DynamicRouteState(p_parent_state, p_board_handling);
    }
    newInstance.routingTargetSet = pickedItem.getUnconnectedSet(-1);

    newInstance.route =
        new Route(
            location,
            p_board_handling.getInteractiveSettings().getLayer(),
            traceHalfWidths,
            layerActiveArr,
            routeNetNoArr,
            traceClearanceClass,
            p_board_handling.getViaRule(routeNetNoArr[0]),
            p_board_handling.getInteractiveSettings().getPushEnabled(),
            p_board_handling.getInteractiveSettings().getTracePullTightRegionWidth(),
            p_board_handling.getInteractiveSettings().getTracePullTightAccuracy(),
            pickedItem,
            newInstance.routingTargetSet,
            routingBoard,
            isStitchRoute,
            p_board_handling.getInteractiveSettings().getAutomaticNeckdown(),
            p_board_handling.getInteractiveSettings().getViaSnapToSmdCenter(),
            p_board_handling.getInteractiveSettings().getHilightRoutingObstacle());
    newInstance.observersActivated = !routingBoard.observersActive();
    if (newInstance.observersActivated) {
      routingBoard.startNotifyObservers();
    }
    p_board_handling.repaint();
    newInstance.displayDefaultMessage();
    return newInstance;
  }

  /**
   * Checks starting an interactive route at p_location. Returns the picked start item of the
   * routing at p_location, or null, if no such item was found.
   */
  protected static Item startOk(IntPoint p_location, GuiBoardManager p_hdlg) {
    RoutingBoard routingBoard = p_hdlg.getRoutingBoard();

    /*
     * look if an already existing trace ends at p_start_corner
     * and pick it up in this case.
     */
    Item pickedItem =
        routingBoard.pickNearestRoutingItem(
            p_location, p_hdlg.getInteractiveSettings().getLayer(), null);
    int layerCount = routingBoard.getLayerCount();
    if (pickedItem == null && p_hdlg.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      // Nothing found on preferred layer, try the other visible layers.
      // Prefer the outer layers.
      pickedItem = pickRoutingItem(p_location, 0, p_hdlg);
      if (pickedItem == null) {
        pickedItem = pickRoutingItem(p_location, layerCount - 1, p_hdlg);
      }
      // prefer signal layers
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pickRoutingItem(p_location, i, p_hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (!routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pickRoutingItem(p_location, i, p_hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
    }
    return pickedItem;
  }

  private static Item pickRoutingItem(
      IntPoint p_location, int p_layer_no, GuiBoardManager p_hdlg) {

    if (p_layer_no == p_hdlg.getInteractiveSettings().getLayer()
        || (p_hdlg.graphicsContext.getLayerVisibility(p_layer_no) <= 0)) {
      return null;
    }
    Item pickedItem =
        p_hdlg.getRoutingBoard().pickNearestRoutingItem(p_location, p_layer_no, null);
    if (pickedItem != null) {
      p_hdlg.setLayer(pickedItem.firstLayer());
    }
    return pickedItem;
  }

  /**
   * get nets of p_tie_pin except nets of traces, which are already connected to this pin on
   * p_layer.
   */
  static int[] getRouteNetNumbersAtTiePin(Pin p_pin, int p_layer) {
    Set<Integer> netNumberList = new TreeSet<>();
    for (int i = 0; i < p_pin.netCount(); i++) {
      netNumberList.add(p_pin.getNetNo(i));
    }
    Set<Item> contacts = p_pin.getNormalContacts();
    for (Item currContact : contacts) {
      if (currContact.firstLayer() <= p_layer && currContact.lastLayer() >= p_layer) {
        for (int i = 0; i < currContact.netCount(); i++) {
          netNumberList.remove(currContact.getNetNo(i));
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
  public InteractiveState keyTyped(char p_key_char) {
    InteractiveState currReturnState = this;
    if (Character.isDigit(p_key_char)) {
      // change to the p_key_char-ths signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int d = Character.digit(p_key_char, 10);
      d = Math.min(d, layerStructure.signalLayerCount());
      // Board layers start at 0, keyboard input for layers starts at 1.
      d = Math.max(d - 1, 0);
      Layer newLayer = layerStructure.getSignalLayer(d);
      d = layerStructure.getNo(newLayer);

      if (d >= 0) {
        changeLayerAction(d);
      }
    } else if (p_key_char == '+') {
      // change to the next signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int currentLayerNo = hdlg.getInteractiveSettings().getLayer();
      do {
        ++currentLayerNo;
      } while (currentLayerNo < layerStructure.arr.length
          && !layerStructure.arr[currentLayerNo].isSignal);
      if (currentLayerNo < layerStructure.arr.length) {
        changeLayerAction(currentLayerNo);
      }
    } else if (p_key_char == '-') {
      // change to the previous signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int currentLayerNo = hdlg.getInteractiveSettings().getLayer();
      do {
        --currentLayerNo;
      } while (currentLayerNo >= 0 && !layerStructure.arr[currentLayerNo].isSignal);
      if (currentLayerNo >= 0) {
        changeLayerAction(currentLayerNo);
      }

    } else {
      currReturnState = super.keyTyped(p_key_char);
    }
    return currReturnState;
  }

  /**
   * Append a line to p_location to the trace routed so far. Returns from state, if the route is
   * completed by connecting to a target.
   */
  public InteractiveState addCorner(FloatPoint p_location) {
    boolean routeCompleted = route.nextCorner(p_location);
    String layerString =
        hdlg.getRoutingBoard().layerStructure.arr[route.nearestTargetLayer()].name;
    hdlg.screenMessages.setTargetLayer(layerString);
    if (routeCompleted) {
      if (this.observersActivated) {
        hdlg.getRoutingBoard().endNotifyObservers();
        this.observersActivated = false;
      }
    }
    InteractiveState result;
    if (routeCompleted) {
      result = this.returnState;
      hdlg.screenMessages.clear();
      for (int currNetNo : this.route.netNoArr) {
        hdlg.updateRatsnest(currNetNo);
      }
    } else {
      result = this;
    }
    hdlg.recalculateLengthViolations();
    hdlg.repaint(hdlg.getGraphicsUpdateRectangle());
    return result;
  }

  @Override
  public InteractiveState cancel() {
    Trace tail =
        hdlg.getRoutingBoard()
            .getTraceTail(
                route.getLastCorner(), hdlg.getInteractiveSettings().getLayer(), route.netNoArr);
    if (tail != null) {
      Collection<Item> removeItems = tail.getConnectionItems(Item.StopConnectionOption.VIA);
      if (hdlg.getInteractiveSettings().getPushEnabled()) {
        hdlg.getRoutingBoard()
            .removeItemsAndPullTight(
                removeItems,
                hdlg.getInteractiveSettings().getTracePullTightRegionWidth(),
                hdlg.getInteractiveSettings().getTracePullTightAccuracy());
      } else {
        hdlg.getRoutingBoard().removeItems(removeItems);
      }
    }
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    hdlg.screenMessages.clear();
    for (int currNetNo : this.route.netNoArr) {
      hdlg.updateRatsnest(currNetNo);
    }
    return this.returnState;
  }

  @Override
  public boolean changeLayerAction(int p_new_layer) {
    boolean result = true;
    if (p_new_layer >= 0 && p_new_layer < hdlg.getRoutingBoard().getLayerCount()) {
      if (this.route != null && !this.route.isLayerActive(p_new_layer)) {
        String layerName = hdlg.getRoutingBoard().layerStructure.arr[p_new_layer].name;
        hdlg.screenMessages.setStatusMessage(
            tm.getText("layer_not_changed_inactive_net_message", layerName));
      }
      boolean changeLayerSucceeded = route.changeLayer(p_new_layer);
      if (changeLayerSucceeded) {
        boolean connectedToPlane = false;
        // check, if the layer change resulted in a connection to a power plane.
        int oldLayer = hdlg.getInteractiveSettings().getLayer();
        ItemSelectionFilter selectionFilter =
            new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
        Collection<Item> pickedItems =
            hdlg.getRoutingBoard().pickItems(route.getLastCorner(), oldLayer, selectionFilter);
        Via newVia = null;
        for (Item currVia : pickedItems) {
          if (currVia.sharesNetNo(route.netNoArr)) {
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
          Collection<Item> contacts = newVia.getNormalContacts();
          for (Item currItem : contacts) {
            if (currItem instanceof ConductionArea currArea) {
              if (currArea.getLayer() >= fromLayer && currArea.getLayer() <= toLayer) {
                connectedToPlane = true;
                break;
              }
            }
          }
        }

        if (connectedToPlane) {
          hdlg.setInteractiveState(this.returnState);
          for (int currNetNo : this.route.netNoArr) {
            hdlg.updateRatsnest(currNetNo);
          }
        } else {
          hdlg.setLayer(p_new_layer);
          String layerName = hdlg.getRoutingBoard().layerStructure.arr[p_new_layer].name;
          hdlg.screenMessages.setStatusMessage(tm.getText("layer_changed_to_message", layerName));
          // make the current situation restorable by undo
          hdlg.getRoutingBoard().generateSnapshot();
        }
      } else {
        int shoveFailingLayer = hdlg.getRoutingBoard().getShoveFailingLayer();
        if (shoveFailingLayer >= 0) {
          String layerName =
              hdlg.getRoutingBoard()
                  .layerStructure
                  .arr[hdlg.getRoutingBoard().getShoveFailingLayer()]
                  .name;
          hdlg.screenMessages.setStatusMessage(
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
  public void displayDefaultMessage() {
    if (route != null) {
      Net currNet = hdlg.getRoutingBoard().rules.nets.get(route.netNoArr[0]);
      hdlg.screenMessages.setStatusMessage(tm.getText("routing_net_message", currNet.name));
    }
  }
}
