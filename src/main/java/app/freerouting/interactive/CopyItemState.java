package app.freerouting.interactive;

import app.freerouting.board.Component;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Via;
import app.freerouting.core.Package;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
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

  /** Creates a new instance of CopyItemState */
  private CopyItemState(
      FloatPoint pLocation,
      Collection<Item> pItemList,
      InteractiveState pParentState,
      GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    itemList = new LinkedList<>();

    startPosition = pLocation.round();
    currentLayer = pBoardHandling.getInteractiveSettings().getLayer();
    layerChanged = false;
    currentPosition = startPosition;
    previousPosition = currentPosition;
    for (Item currItem : pItemList) {
      if (currItem instanceof DrillItem || currItem instanceof ObstacleArea) {
        Item newItem = currItem.copy(0);
        itemList.add(newItem);
      }
    }
  }

  /** Returns a new instance of CopyItemState or null, if p_item_list is empty. */
  public static CopyItemState getInstance(
      FloatPoint pLocation,
      Collection<Item> pItemList,
      InteractiveState pParentState,
      GuiBoardManager pBoardHandling) {
    if (pItemList.isEmpty()) {
      return null;
    }
    pBoardHandling.removeRatsnest(); // copying an item may change the connectivity.
    return new CopyItemState(pLocation, pItemList, pParentState, pBoardHandling);
  }

  /** Creates a new padstack from p_old_padstack with a layer range starting at p_new_layer. */
  private static Padstack changePadstackLayers(
      Padstack pOldPadstack,
      int pNewLayer,
      RoutingBoard pBoard,
      Map<Padstack, Padstack> pPadstackPairs) {
    Padstack newPadstack;
    int oldLayer = pOldPadstack.fromLayer();
    if (oldLayer == pNewLayer) {
      newPadstack = pOldPadstack;
    } else if (pPadstackPairs.containsKey(pOldPadstack)) {
      // New padstack already created, assign it to the via.
      newPadstack = pPadstackPairs.get(pOldPadstack);
    } else {
      // Create a new padstack.
      ConvexShape[] newShapes = new ConvexShape[pBoard.getLayerCount()];
      int layerDiff = oldLayer - pNewLayer;
      for (int i = 0; i < newShapes.length; i++) {
        int newLayerNo = i + layerDiff;
        if (newLayerNo >= 0 && newLayerNo < newShapes.length) {
          newShapes[i] = pOldPadstack.getShape(i + layerDiff);
        }
      }
      newPadstack = pBoard.library.padstacks.add(newShapes);
      pPadstackPairs.put(pOldPadstack, newPadstack);
    }
    return newPadstack;
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    changePosition(hdlg.getCurrentMousePosition());
    return this;
  }

  /** Changes the position for inserting the copied items to p_new_location. */
  private void changePosition(FloatPoint pNewPosition) {
    currentPosition = pNewPosition.round();
    if (!currentPosition.equals(previousPosition)) {
      Vector translateVector = currentPosition.differenceBy(previousPosition);
      for (Item currItem : itemList) {
        currItem.translateBy(translateVector);
      }
      previousPosition = currentPosition;
      hdlg.repaint();
    }
  }

  /** Changes the first layer of the items in the copy list to p_new_layer. */
  @Override
  public boolean changeLayerAction(int pNewLayer) {
    currentLayer = pNewLayer;
    layerChanged = true;
    hdlg.setLayer(pNewLayer);
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
      for (Item currOb : itemList) {
        if (currOb instanceof Via currVia) {
          Padstack newPadstack =
              changePadstackLayers(currVia.getPadstack(), currentLayer, board, padstackPairs);
          currVia.setPadstack(newPadstack);
        }
      }
    }
    // Copy the components of the old items and assign the new items to the copied
    // components.

    // Contains the old and new id no of a copied component.
    Map<Integer, Integer> cmpNoPairs = new TreeMap<>();

    // Contains the new created components after copying.
    Collection<Component> copiedComponents = new LinkedList<>();

    Vector translateVector = currentPosition.differenceBy(startPosition);
    for (Item currItem : itemList) {
      int currCmpNo = currItem.getComponentNo();
      if (currCmpNo > 0) {
        // This item belongs to a component
        int newCmpNo;
        Integer currKey = currCmpNo;
        if (cmpNoPairs.containsKey(currKey)) {
          // the new component for this pin is already created
          Integer currValue = cmpNoPairs.get(currKey);
          newCmpNo = currValue;
        } else {
          Component oldComponent = board.components.get(currCmpNo);
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
              Padstack oldPadstack = board.library.padstacks.get(oldPin.padstackNo);
              if (oldPadstack == null) {
                FRLogger.warn("CopyItemState.insert: package padstack not found");
                return;
              }
              Padstack newPadstack =
                  changePadstackLayers(oldPadstack, currentLayer, board, padstackPairs);
              newPinArr[i] =
                  new Package.Pin(
                      oldPin.name,
                      newPadstack.no,
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
          copiedComponents.add(newComponent);
          newCmpNo = newComponent.no;
          cmpNoPairs.put(currCmpNo, newCmpNo);
        }
        currItem.assignComponentNo(newCmpNo);
      }
    }
    boolean allItemsInserted = true;
    boolean firstTime = true;
    for (Item currItem : itemList) {
      if (currItem.board != null && currItem.clearanceViolationCount() == 0) {
        if (firstTime) {
          // make the current situation restorable by undo
          board.generateSnapshot();
          firstTime = false;
        }
        board.insertItem(currItem.copy(0));
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
  public InteractiveState leftButtonClicked(FloatPoint pLocation) {
    insert();
    return this;
  }

  @Override
  public void draw(Graphics pGraphics) {
    if (itemList == null) {
      return;
    }
    for (Item currItem : itemList) {
      currItem.draw(
          pGraphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHilightColor(),
          hdlg.graphicsContext.getHilightColorIntensity());
    }
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuCopy;
  }
}
