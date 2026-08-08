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
  protected RouteState(InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
  }

  /**
   * Returns a new instance of this class or null, if starting a new route was not possible at
   * p_location. If p_logfile != null, the creation of the route is stored in the logfile.
   */
  public static RouteState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    if (!(pParentState instanceof MenuState)) {
      FRLogger.warn("RouteState.get_instance: unexpected parent state");
    }
    pBoardHandling.displayLayerMessage();
    IntPoint location = pLocation.round();
    Item pickedItem = startOk(location, pBoardHandling);
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
          getRouteNetNumbersAtTiePin(pin, pBoardHandling.getInteractiveSettings().getLayer());
    } else {
      routeNetNoArr = new int[netCount];
      for (int i = 0; i < netCount; i++) {
        routeNetNoArr[i] = pickedItem.getNetNo(i);
      }
    }
    if (routeNetNoArr.length == 0) {
      return null;
    }
    RoutingBoard routingBoard = pBoardHandling.getRoutingBoard();
    int[] traceHalfWidths = new int[routingBoard.getLayerCount()];
    boolean[] layerActiveArr = new boolean[traceHalfWidths.length];
    for (int i = 0; i < traceHalfWidths.length; i++) {
      traceHalfWidths[i] = pBoardHandling.getTraceHalfwidth(routeNetNoArr[0], i);
      layerActiveArr[i] = false;
      for (int j = 0; j < routeNetNoArr.length; j++) {
        if (pBoardHandling.isActiveRoutingLayer(routeNetNoArr[j], i)) {
          layerActiveArr[i] = true;
        }
      }
    }

    int traceClearanceClass = pBoardHandling.getTraceClearanceClass(routeNetNoArr[0]);
    boolean startOk = true;
    if (pickedItem instanceof Trace pickedTrace) {
      Point pickedCorner = pickedTrace.nearestEndPoint(location);
      if (pickedCorner instanceof IntPoint point
          && pLocation.distance(pickedCorner.toFloat()) < 5 * pickedTrace.getHalfWidth()) {
        location = point;
      } else {
        if (pickedTrace instanceof PolylineTrace trace) {
          FloatPoint nearestPoint = trace.polyline().nearestPointApprox(pLocation);
          location = nearestPoint.round();
        }
        if (!routingBoard.connectToTrace(
            location, pickedTrace, pickedTrace.getHalfWidth(), pickedTrace.clearanceClassNo())) {
          startOk = false;
        }
      }
      if (startOk && !pBoardHandling.getInteractiveSettings().getManualRuleSelection()) {
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
        pBoardHandling.getInteractiveSettings().getIsStitchRoute()
            || currNet.getNetClass().isShoveFixed()
            || !currNet.getNetClass().getPullTight();
    routingBoard.generateSnapshot();
    RouteState newInstance;
    if (isStitchRoute) {
      newInstance = new StitchRouteState(pParentState, pBoardHandling);
    } else {
      newInstance = new DynamicRouteState(pParentState, pBoardHandling);
    }
    newInstance.routingTargetSet = pickedItem.getUnconnectedSet(-1);

    newInstance.route =
        new Route(
            location,
            pBoardHandling.getInteractiveSettings().getLayer(),
            traceHalfWidths,
            layerActiveArr,
            routeNetNoArr,
            traceClearanceClass,
            pBoardHandling.getViaRule(routeNetNoArr[0]),
            pBoardHandling.getInteractiveSettings().getPushEnabled(),
            pBoardHandling.getInteractiveSettings().getTracePullTightRegionWidth(),
            pBoardHandling.getInteractiveSettings().getTracePullTightAccuracy(),
            pickedItem,
            newInstance.routingTargetSet,
            routingBoard,
            isStitchRoute,
            pBoardHandling.getInteractiveSettings().getAutomaticNeckdown(),
            pBoardHandling.getInteractiveSettings().getViaSnapToSmdCenter(),
            pBoardHandling.getInteractiveSettings().getHilightRoutingObstacle());
    newInstance.observersActivated = !routingBoard.observersActive();
    if (newInstance.observersActivated) {
      routingBoard.startNotifyObservers();
    }
    pBoardHandling.repaint();
    newInstance.displayDefaultMessage();
    return newInstance;
  }

  /**
   * Checks starting an interactive route at p_location. Returns the picked start item of the
   * routing at p_location, or null, if no such item was found.
   */
  protected static Item startOk(IntPoint pLocation, GuiBoardManager pHdlg) {
    RoutingBoard routingBoard = pHdlg.getRoutingBoard();

    /*
     * look if an already existing trace ends at p_start_corner
     * and pick it up in this case.
     */
    Item pickedItem =
        routingBoard.pickNearestRoutingItem(
            pLocation, pHdlg.getInteractiveSettings().getLayer(), null);
    int layerCount = routingBoard.getLayerCount();
    if (pickedItem == null && pHdlg.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      // Nothing found on preferred layer, try the other visible layers.
      // Prefer the outer layers.
      pickedItem = pickRoutingItem(pLocation, 0, pHdlg);
      if (pickedItem == null) {
        pickedItem = pickRoutingItem(pLocation, layerCount - 1, pHdlg);
      }
      // prefer signal layers
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pickRoutingItem(pLocation, i, pHdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (!routingBoard.layerStructure.arr[i].isSignal) {
            pickedItem = pickRoutingItem(pLocation, i, pHdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
    }
    return pickedItem;
  }

  private static Item pickRoutingItem(IntPoint pLocation, int pLayerNo, GuiBoardManager pHdlg) {

    if (pLayerNo == pHdlg.getInteractiveSettings().getLayer()
        || (pHdlg.graphicsContext.getLayerVisibility(pLayerNo) <= 0)) {
      return null;
    }
    Item pickedItem = pHdlg.getRoutingBoard().pickNearestRoutingItem(pLocation, pLayerNo, null);
    if (pickedItem != null) {
      pHdlg.setLayer(pickedItem.firstLayer());
    }
    return pickedItem;
  }

  /**
   * get nets of p_tie_pin except nets of traces, which are already connected to this pin on
   * p_layer.
   */
  static int[] getRouteNetNumbersAtTiePin(Pin pPin, int pLayer) {
    Set<Integer> netNumberList = new TreeSet<>();
    for (int i = 0; i < pPin.netCount(); i++) {
      netNumberList.add(pPin.getNetNo(i));
    }
    Set<Item> contacts = pPin.getNormalContacts();
    for (Item currContact : contacts) {
      if (currContact.firstLayer() <= pLayer && currContact.lastLayer() >= pLayer) {
        for (int i = 0; i < currContact.netCount(); i++) {
          netNumberList.remove(currContact.getNetNo(i));
        }
      }
    }
    int[] result = new int[netNumberList.size()];
    int currInd = 0;
    for (Integer currNetNumber : netNumberList) {
      result[currInd] = currNetNumber;
      ++currInd;
    }
    return result;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char pKeyChar) {
    InteractiveState currReturnState = this;
    if (Character.isDigit(pKeyChar)) {
      // change to the p_key_char-ths signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int d = Character.digit(pKeyChar, 10);
      d = Math.min(d, layerStructure.signalLayerCount());
      // Board layers start at 0, keyboard input for layers starts at 1.
      d = Math.max(d - 1, 0);
      Layer newLayer = layerStructure.getSignalLayer(d);
      d = layerStructure.getNo(newLayer);

      if (d >= 0) {
        changeLayerAction(d);
      }
    } else if (pKeyChar == '+') {
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
    } else if (pKeyChar == '-') {
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
      currReturnState = super.keyTyped(pKeyChar);
    }
    return currReturnState;
  }

  /**
   * Append a line to p_location to the trace routed so far. Returns from state, if the route is
   * completed by connecting to a target.
   */
  public InteractiveState addCorner(FloatPoint pLocation) {
    boolean routeCompleted = route.nextCorner(pLocation);
    String layerString = hdlg.getRoutingBoard().layerStructure.arr[route.nearestTargetLayer()].name;
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
  public boolean changeLayerAction(int pNewLayer) {
    boolean result = true;
    if (pNewLayer >= 0 && pNewLayer < hdlg.getRoutingBoard().getLayerCount()) {
      if (this.route != null && !this.route.isLayerActive(pNewLayer)) {
        String layerName = hdlg.getRoutingBoard().layerStructure.arr[pNewLayer].name;
        hdlg.screenMessages.setStatusMessage(
            tm.getText("layer_not_changed_inactive_net_message", layerName));
      }
      boolean changeLayerSucceeded = route.changeLayer(pNewLayer);
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
          if (oldLayer < pNewLayer) {
            fromLayer = oldLayer + 1;
            toLayer = pNewLayer;
          } else {
            fromLayer = pNewLayer;
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
          hdlg.setLayer(pNewLayer);
          String layerName = hdlg.getRoutingBoard().layerStructure.arr[pNewLayer].name;
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
  public void draw(Graphics pGraphics) {
    if (route != null) {
      route.draw(pGraphics, hdlg.graphicsContext);
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
