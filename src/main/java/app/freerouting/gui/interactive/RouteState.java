package app.freerouting.gui.interactive;

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
import app.freerouting.gui.workspace.GuiBoardManager;
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
   * Creates a new instance of RouteState.
   *
   * <p>The creation of the route is stored in a logfile when logging is enabled.
   */
  protected RouteState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  /**
   * Returns a new instance of this class, or null if starting a new route was not possible at the
   * given location.
   */
  public static RouteState getInstance(
      FloatPoint floatLocation, InteractiveState parentState, GuiBoardManager boardHandling) {
    if (!(parentState instanceof MenuState)) {
      FRLogger.warn("RouteState.get_instance: unexpected parent state");
    }
    boardHandling.displayLayerMessage();
    IntPoint location = floatLocation.round();
    Item pickedItem = startOk(location, boardHandling);
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
          getRouteNetNumbersAtTiePin(pin, boardHandling.getWorkspaceSettings().getLayer());
    } else {
      routeNetNoArr = new int[netCount];
      for (int i = 0; i < netCount; i++) {
        routeNetNoArr[i] = pickedItem.getNetNumber(i);
      }
    }
    if (routeNetNoArr.length == 0) {
      return null;
    }
    RoutingBoard routingBoard = boardHandling.getRoutingBoard();
    int[] traceHalfWidths = new int[routingBoard.getLayerCount()];
    boolean[] layerActiveArr = new boolean[traceHalfWidths.length];
    for (int i = 0; i < traceHalfWidths.length; i++) {
      traceHalfWidths[i] = boardHandling.getTraceHalfwidth(routeNetNoArr[0], i);
      layerActiveArr[i] = false;
      for (int j = 0; j < routeNetNoArr.length; j++) {
        if (boardHandling.isActiveRoutingLayer(routeNetNoArr[j], i)) {
          layerActiveArr[i] = true;
        }
      }
    }

    int traceClearanceClass = boardHandling.getTraceClearanceClass(routeNetNoArr[0]);
    boolean startOk = true;
    if (pickedItem instanceof Trace pickedTrace) {
      Point pickedCorner = pickedTrace.nearestEndPoint(location);
      if (pickedCorner instanceof IntPoint point
          && floatLocation.distance(pickedCorner.toFloat()) < 5 * pickedTrace.getHalfWidth()) {
        location = point;
      } else {
        if (pickedTrace instanceof PolylineTrace trace) {
          FloatPoint nearestPoint = trace.polyline().nearestPointApprox(floatLocation);
          location = nearestPoint.round();
        }
        if (!routingBoard.connectToTrace(
            location, pickedTrace, pickedTrace.getHalfWidth(), pickedTrace.clearanceClassIndex())) {
          startOk = false;
        }
      }
      if (startOk && !boardHandling.getWorkspaceSettings().getManualRuleSelection()) {
        // Pick up the half with and the clearance class of the found trace.
        int[] newTraceHalfWidths = new int[traceHalfWidths.length];
        System.arraycopy(traceHalfWidths, 0, newTraceHalfWidths, 0, traceHalfWidths.length);
        newTraceHalfWidths[pickedTrace.getLayer()] = pickedTrace.getHalfWidth();
        traceHalfWidths = newTraceHalfWidths;
        traceClearanceClass = pickedTrace.clearanceClassIndex();
      }
    } else if (pickedItem instanceof DrillItem drillItem) {
      Point center = drillItem.getCenter();
      if (center instanceof IntPoint point) {
        location = point;
      }
    }
    if (!startOk) {
      return null;
    }

    Net currentNet = routingBoard.rules.nets.get(routeNetNoArr[0]);
    if (currentNet == null) {
      return null;
    }
    // Switch to stitch mode for nets, which are shove fixed.
    boolean isStitchRoute =
        boardHandling.getWorkspaceSettings().getIsStitchRoute()
            || currentNet.getNetClass().isShoveFixed()
            || !currentNet.getNetClass().getPullTight();
    routingBoard.generateSnapshot();
    RouteState newInstance;
    if (isStitchRoute) {
      newInstance = new StitchRouteState(parentState, boardHandling);
    } else {
      newInstance = new DynamicRouteState(parentState, boardHandling);
    }
    newInstance.routingTargetSet = pickedItem.getUnconnectedSet(-1);

    newInstance.route =
        new Route(
            location,
            boardHandling.getWorkspaceSettings().getLayer(),
            traceHalfWidths,
            layerActiveArr,
            routeNetNoArr,
            traceClearanceClass,
            boardHandling.getViaRule(routeNetNoArr[0]),
            boardHandling.getWorkspaceSettings().getPushEnabled(),
            boardHandling.getWorkspaceSettings().getTracePullTightRegionWidth(),
            boardHandling.getWorkspaceSettings().getTracePullTightAccuracy(),
            pickedItem,
            newInstance.routingTargetSet,
            routingBoard,
            isStitchRoute,
            boardHandling.getWorkspaceSettings().getAutomaticNeckdown(),
            boardHandling.getWorkspaceSettings().getViaSnapToSmdCenter(),
            boardHandling.getWorkspaceSettings().getHighlightRoutingObstacle());
    newInstance.observersActivated = !routingBoard.observersActive();
    if (newInstance.observersActivated) {
      routingBoard.startNotifyObservers();
    }
    boardHandling.repaint();
    newInstance.displayDefaultMessage();
    return newInstance;
  }

  /**
   * Checks whether an interactive route can start at the specified location.
   *
   * @return the picked start item, or {@code null} if no suitable item was found
   */
  protected static Item startOk(IntPoint location, GuiBoardManager hdlg) {
    RoutingBoard routingBoard = hdlg.getRoutingBoard();

    /*
     * Look for an existing trace ending at the specified location and pick it up in that case.
     */
    Item pickedItem =
        routingBoard.pickNearestRoutingItem(location, hdlg.getWorkspaceSettings().getLayer(), null);
    int layerCount = routingBoard.getLayerCount();
    if (pickedItem == null && hdlg.getWorkspaceSettings().getSelectOnAllVisibleLayers()) {
      // Nothing found on preferred layer, try the other visible layers.
      // Prefer the outer layers.
      pickedItem = pickRoutingItem(location, 0, hdlg);
      if (pickedItem == null) {
        pickedItem = pickRoutingItem(location, layerCount - 1, hdlg);
      }
      // prefer signal layers
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (routingBoard.layerStructure.layers[i].isSignal) {
            pickedItem = pickRoutingItem(location, i, hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
      if (pickedItem == null) {
        for (int i = 1; i < layerCount - 1; i++) {
          if (!routingBoard.layerStructure.layers[i].isSignal) {
            pickedItem = pickRoutingItem(location, i, hdlg);
            if (pickedItem != null) {
              break;
            }
          }
        }
      }
    }
    return pickedItem;
  }

  private static Item pickRoutingItem(IntPoint location, int layerIndex, GuiBoardManager hdlg) {

    if (layerIndex == hdlg.getWorkspaceSettings().getLayer()
        || (hdlg.graphicsContext.getLayerVisibility(layerIndex) <= 0)) {
      return null;
    }
    Item pickedItem = hdlg.getRoutingBoard().pickNearestRoutingItem(location, layerIndex, null);
    if (pickedItem != null) {
      hdlg.setLayer(pickedItem.firstLayer());
    }
    return pickedItem;
  }

  /**
   * Gets the nets of the tie pin except nets of traces already connected to this pin on the given
   * layer.
   */
  static int[] getRouteNetNumbersAtTiePin(Pin pin, int layer) {
    Set<Integer> netNumberList = new TreeSet<>();
    for (int i = 0; i < pin.netCount(); i++) {
      netNumberList.add(pin.getNetNumber(i));
    }
    Set<Item> contacts = pin.getNormalContacts();
    for (Item currentContact : contacts) {
      if (currentContact.firstLayer() <= layer && currentContact.lastLayer() >= layer) {
        for (int i = 0; i < currentContact.netCount(); i++) {
          netNumberList.remove(currentContact.getNetNumber(i));
        }
      }
    }
    int[] result = new int[netNumberList.size()];
    int currentInd = 0;
    for (Integer currentNetNumber : netNumberList) {
      result[currentInd] = currentNetNumber;
      ++currentInd;
    }
    return result;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState currentReturnState = this;
    if (Character.isDigit(keyChar)) {
      // Change to the signal layer selected by the numeric key.
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int d = Character.digit(keyChar, 10);
      d = Math.min(d, layerStructure.signalLayerCount());
      // Board layers start at 0, keyboard input for layers starts at 1.
      d = Math.max(d - 1, 0);
      Layer newLayer = layerStructure.getSignalLayer(d);
      d = layerStructure.getNo(newLayer);

      if (d >= 0) {
        changeLayerAction(d);
      }
    } else if (keyChar == '+') {
      // change to the next signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int currentLayerIndex = hdlg.getWorkspaceSettings().getLayer();
      do {
        ++currentLayerIndex;
      } while (currentLayerIndex < layerStructure.layers.length
          && !layerStructure.layers[currentLayerIndex].isSignal);
      if (currentLayerIndex < layerStructure.layers.length) {
        changeLayerAction(currentLayerIndex);
      }
    } else if (keyChar == '-') {
      // change to the previous signal layer
      LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
      int currentLayerIndex = hdlg.getWorkspaceSettings().getLayer();
      do {
        --currentLayerIndex;
      } while (currentLayerIndex >= 0 && !layerStructure.layers[currentLayerIndex].isSignal);
      if (currentLayerIndex >= 0) {
        changeLayerAction(currentLayerIndex);
      }

    } else {
      currentReturnState = super.keyTyped(keyChar);
    }
    return currentReturnState;
  }

  /**
   * Appends a line to the trace routed so far.
   *
   * @return the parent state if the route was completed; otherwise, this state
   */
  public InteractiveState addCorner(FloatPoint location) {
    boolean routeCompleted = route.nextCorner(location);
    String layerString =
        hdlg.getRoutingBoard().layerStructure.layers[route.nearestTargetLayer()].name;
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
      for (int currentNetNumber : this.route.netNumbers) {
        hdlg.updateRatsnest(currentNetNumber);
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
                route.getLastCorner(), hdlg.getWorkspaceSettings().getLayer(), route.netNumbers);
    if (tail != null) {
      Collection<Item> removeItems = tail.getConnectionItems(Item.StopConnectionOption.VIA);
      if (hdlg.getWorkspaceSettings().getPushEnabled()) {
        hdlg.getRoutingBoard()
            .removeItemsAndPullTight(
                removeItems,
                hdlg.getWorkspaceSettings().getTracePullTightRegionWidth(),
                hdlg.getWorkspaceSettings().getTracePullTightAccuracy());
      } else {
        hdlg.getRoutingBoard().removeItems(removeItems);
      }
    }
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    hdlg.screenMessages.clear();
    for (int currentNetNumber : this.route.netNumbers) {
      hdlg.updateRatsnest(currentNetNumber);
    }
    return this.returnState;
  }

  @Override
  public boolean changeLayerAction(int newLayer) {
    boolean result = true;
    if (newLayer >= 0 && newLayer < hdlg.getRoutingBoard().getLayerCount()) {
      if (this.route != null && !this.route.isLayerActive(newLayer)) {
        String layerName = hdlg.getRoutingBoard().layerStructure.layers[newLayer].name;
        hdlg.screenMessages.setStatusMessage(
            tm.getText("layer_not_changed_inactive_net_message", layerName));
      }
      boolean changeLayerSucceeded = route.changeLayer(newLayer);
      if (changeLayerSucceeded) {
        boolean connectedToPlane = false;
        // check, if the layer change resulted in a connection to a power plane.
        int oldLayer = hdlg.getWorkspaceSettings().getLayer();
        ItemSelectionFilter selectionFilter =
            new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
        Collection<Item> pickedItems =
            hdlg.getRoutingBoard().pickItems(route.getLastCorner(), oldLayer, selectionFilter);
        Via newVia = null;
        for (Item currentVia : pickedItems) {
          if (currentVia.sharesNetNo(route.netNumbers)) {
            newVia = (Via) currentVia;
            break;
          }
        }
        if (newVia != null) {
          int fromLayer;
          int toLayer;
          if (oldLayer < newLayer) {
            fromLayer = oldLayer + 1;
            toLayer = newLayer;
          } else {
            fromLayer = newLayer;
            toLayer = oldLayer - 1;
          }
          Collection<Item> contacts = newVia.getNormalContacts();
          for (Item currentItem : contacts) {
            if (currentItem instanceof ConductionArea currentArea) {
              if (currentArea.getLayer() >= fromLayer && currentArea.getLayer() <= toLayer) {
                connectedToPlane = true;
                break;
              }
            }
          }
        }

        if (connectedToPlane) {
          hdlg.setEditorState(this.returnState);
          for (int currentNetNumber : this.route.netNumbers) {
            hdlg.updateRatsnest(currentNetNumber);
          }
        } else {
          hdlg.setLayer(newLayer);
          String layerName = hdlg.getRoutingBoard().layerStructure.layers[newLayer].name;
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
                  .layers[hdlg.getRoutingBoard().getShoveFailingLayer()]
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
  public void draw(Graphics graphics) {
    if (route != null) {
      route.draw(graphics, hdlg.graphicsContext);
    }
  }

  @Override
  public void displayDefaultMessage() {
    if (route != null) {
      Net currentNet = hdlg.getRoutingBoard().rules.nets.get(route.netNumbers[0]);
      hdlg.screenMessages.setStatusMessage(tm.getText("routing_net_message", currentNet.name));
    }
  }
}
