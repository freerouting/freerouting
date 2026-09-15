package app.freerouting.gui.interactive;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JPopupMenu;

/** Interactive copying of items. */
public final class CopyItemState extends InteractiveState {

  private final Collection<Item> itemList;
  private Point startPosition;
  private Point currentPosition;
  private int currentLayer;
  private boolean layerChanged;
  private Point previousPosition;

  /** Creates a new instance of CopyItemState. */
  private CopyItemState(
      FloatPoint location,
      Collection<Item> itemList,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    this.itemList = new LinkedList<>();

    startPosition = location.round();
    currentLayer = boardHandling.getWorkspaceSettings().getLayer();
    layerChanged = false;
    currentPosition = startPosition;
    previousPosition = currentPosition;
    for (Item currentItem : itemList) {
      if (currentItem instanceof DrillItem || currentItem instanceof ObstacleArea) {
        Item newItem = currentItem.copy(0);
        this.itemList.add(newItem);
      }
    }
  }

  /** Returns a new instance of CopyItemState, or null if the item list is empty. */
  public static CopyItemState getInstance(
      FloatPoint location,
      Collection<Item> itemList,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    if (itemList.isEmpty()) {
      return null;
    }
    boardHandling.removeRatsnest(); // copying an item may change the connectivity.
    return new CopyItemState(location, itemList, parentState, boardHandling);
  }

  /** Creates a new padstack from an old padstack with a layer range starting at the new layer. */
  private static Padstack changePadstackLayers(
      Padstack oldPadstack,
      int newLayer,
      RoutingBoard board,
      Map<Padstack, Padstack> padstackPairs) {
    Padstack newPadstack;
    int oldLayer = oldPadstack.fromLayer();
    if (oldLayer == newLayer) {
      newPadstack = oldPadstack;
    } else if (padstackPairs.containsKey(oldPadstack)) {
      // New padstack already created, assign it to the via.
      newPadstack = padstackPairs.get(oldPadstack);
    } else {
      // Create a new padstack.
      ConvexShape[] newShapes = new ConvexShape[board.getLayerCount()];
      int layerDiff = oldLayer - newLayer;
      for (int i = 0; i < newShapes.length; i++) {
        int newLayerNo = i + layerDiff;
        if (newLayerNo >= 0 && newLayerNo < newShapes.length) {
          newShapes[i] = oldPadstack.getShape(i + layerDiff);
        }
      }
      newPadstack = board.library.padstacks.add(newShapes);
      padstackPairs.put(oldPadstack, newPadstack);
    }
    return newPadstack;
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    changePosition(hdlg.getCurrentMousePosition());
    return this;
  }

  /** Changes the position for inserting the copied items to the specified location. */
  private void changePosition(FloatPoint newPosition) {
    currentPosition = newPosition.round();
    if (!currentPosition.equals(previousPosition)) {
      Vector translateVector = currentPosition.differenceBy(previousPosition);
      for (Item currentItem : itemList) {
        currentItem.translateBy(translateVector);
      }
      previousPosition = currentPosition;
      hdlg.repaint();
    }
  }

  /** Changes the first layer of the items in the copy list to the specified layer. */
  @Override
  public boolean changeLayerAction(int newLayer) {
    currentLayer = newLayer;
    layerChanged = true;
    hdlg.setLayer(newLayer);
    return true;
  }

  /**
   * Inserts the items in the copy list into the board. Items, which would produce a clearance
   * violation, are not inserted.
   */
  public void insert() {
    if (itemList == null) {
      return;
    }
    Map<Padstack, Padstack> padstackPairs =
        new TreeMap<>(); // Contains old and new padstacks after layer change.

    RoutingBoard board = hdlg.getRoutingBoard();
    if (layerChanged) {
      // create new via padstacks
      for (Item currentObject : itemList) {
        if (currentObject instanceof Via currentVia) {
          Padstack newPadstack =
              changePadstackLayers(currentVia.getPadstack(), currentLayer, board, padstackPairs);
          currentVia.setPadstack(newPadstack);
        }
      }
    }
    // Copy the components of the old items and assign the new items to the copied
    // components.

    // Contains the old and new id no of a copied component.
    Map<Integer, Integer> cmpNoPairs = new TreeMap<>();

    Vector translateVector = currentPosition.differenceBy(startPosition);
    for (Item currentItem : itemList) {
      int currentCmpNo = currentItem.getComponentId();
      if (currentCmpNo > 0) {
        // This item belongs to a component
        int newCmpNo;
        Integer currentKey = currentCmpNo;
        if (cmpNoPairs.containsKey(currentKey)) {
          // the new component for this pin is already created
          Integer currentValue = cmpNoPairs.get(currentKey);
          newCmpNo = currentValue;
        } else {
          Component oldComponent = board.components.get(currentCmpNo);
          if (oldComponent == null) {
            FRLogger.warn("CopyItemState: component not found");
            continue;
          }
          Point newLocation = oldComponent.getLocation().translateBy(translateVector);
          Package newPackage;
          if (layerChanged) {
            // create a new package with changed layers of the padstacks.
            Package.Pin[] newPinArr = new Package.Pin[oldComponent.getPackage().pinCount()];
            for (int i = 0; i < newPinArr.length; i++) {
              Package.Pin oldPin = oldComponent.getPackage().getPin(i);
              Padstack oldPadstack = board.library.padstacks.get(oldPin.padstackId);
              if (oldPadstack == null) {
                FRLogger.warn("CopyItemState.insert: package padstack not found");
                return;
              }
              Padstack newPadstack =
                  changePadstackLayers(oldPadstack, currentLayer, board, padstackPairs);
              newPinArr[i] =
                  new Package.Pin(
                      oldPin.name,
                      newPadstack.id,
                      oldPin.relativeLocation,
                      oldPin.rotationInDegree);
            }
            newPackage = board.library.packages.add(newPinArr);
          } else {
            newPackage = oldComponent.getPackage();
          }
          Component newComponent =
              board.components.add(
                  newLocation,
                  oldComponent.getRotationInDegree(),
                  oldComponent.placedOnFront(),
                  newPackage);
          newCmpNo = newComponent.id;
          cmpNoPairs.put(currentCmpNo, newCmpNo);
        }
        currentItem.assignComponentId(newCmpNo);
      }
    }
    boolean allItemsInserted = true;
    boolean firstTime = true;
    for (Item currentItem : itemList) {
      if (currentItem.board != null && currentItem.clearanceViolationCount() == 0) {
        if (firstTime) {
          // make the current situation restorable by undo
          board.generateSnapshot();
          firstTime = false;
        }
        board.insertItem(currentItem.copy(0));
      } else {
        allItemsInserted = false;
      }
    }
    if (allItemsInserted) {
      hdlg.screenMessages.setStatusMessage(tm.getText("allItemsInserted"));
    } else {
      hdlg.screenMessages.setStatusMessage(
          tm.getText("some_items_not_inserted_because_of_obstacles"));
    }
    startPosition = currentPosition;
    layerChanged = false;
    hdlg.repaint();
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    insert();
    return this;
  }

  @Override
  public void draw(Graphics graphics) {
    if (itemList == null) {
      return;
    }
    for (Item currentItem : itemList) {
      BoardRenderer.drawOverlayItem(
          currentItem,
          graphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHighlightColor(),
          hdlg.graphicsContext.getHighlightColorIntensity());
    }
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuCopy;
  }
}
