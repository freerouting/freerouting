package app.freerouting.gui.interactive;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
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
      if (currentItem instanceof DrillItem) {
        Item newItem = currentItem.copy(0);
        this.itemList.add(newItem);
      } else if (currentItem instanceof ObstacleArea obs) {
        // Skip companion obstacles belonging to a component to prevent duplicate companions on copy
        if (obs.getComponentId() > 0 && obs.name != null && obs.name.endsWith("_aux")) {
          continue;
        }
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
      ConvexShape[][] newAuxShapes =
          oldPadstack.hasAuxiliaryShapes() ? new ConvexShape[board.getLayerCount()][] : null;
      int layerDiff = oldLayer - newLayer;
      for (int i = 0; i < newShapes.length; i++) {
        int oldLayerNo = i + layerDiff;
        if (oldLayerNo >= 0 && oldLayerNo < newShapes.length) {
          newShapes[i] = oldPadstack.getShape(oldLayerNo);
          if (newAuxShapes != null) {
            newAuxShapes[i] = oldPadstack.getAuxiliaryShapes(oldLayerNo);
          }
        }
      }
      if (newAuxShapes != null) {
        newPadstack = board.library.padstacks.add(newShapes, newAuxShapes);
        if (oldPadstack.hasNonConvexGeometry()) {
          newPadstack.hasNonConvexGeometry = true;
        }
      } else {
        newPadstack = board.library.padstacks.add(newShapes);
      }
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
                      oldPin.getExactRelativeLocation(),
                      oldPin.rotationInDegree);
            }
            newPackage = board.library.packages.add(newPinArr);
          } else {
            newPackage = oldComponent.getPackage();
          }
          Component newComponent = board.components.copy(oldComponent, translateVector, newPackage);
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
        Item copiedItem = currentItem.copy(0);
        board.insertItem(copiedItem);
        if (copiedItem instanceof Pin copiedPin && copiedPin.getPadstack().hasAuxiliaryShapes()) {
          Padstack padstack = copiedPin.getPadstack();
          Component component = board.components.get(copiedPin.getComponentId());
          boolean onFront = component == null || component.placedOnFront();
          boolean absolute = padstack.placedAbsolute;
          int layerCount = padstack.boardLayerCount();

          for (int boardLayer = copiedPin.firstLayer();
              boardLayer <= copiedPin.lastLayer();
              boardLayer++) {
            int padstackLayer = (onFront || absolute) ? boardLayer : layerCount - boardLayer - 1;
            ConvexShape[] auxShapes = padstack.getAuxiliaryShapes(padstackLayer);
            if (auxShapes != null) {
              for (ConvexShape auxShape : auxShapes) {
                if (auxShape != null) {
                  Shape boardShape = copiedPin.transformToBoard(auxShape);
                  Area area = null;
                  if (boardShape instanceof PolylineShape polyShape) {
                    area = new PolylineArea(polyShape, new PolylineShape[0]);
                  } else if (boardShape != null) {
                    area = boardShape;
                  }
                  if (area != null && !area.isEmpty()) {
                    board.insertObstacle(
                        area,
                        boardLayer,
                        copiedPin.netNumbers,
                        copiedPin.clearanceClassIndex(),
                        copiedPin.getComponentId(),
                        padstack.name + "_aux",
                        copiedPin.getFixedState());
                  }
                }
              }
            }
          }
        }
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
    var screenOrigin = hdlg.graphicsContext.coordinateTransform.boardToScreen(FloatPoint.ZERO);
    var screenOffset =
        hdlg.graphicsContext.coordinateTransform.boardToScreen(
            currentPosition.differenceBy(startPosition).toFloat());
    Graphics componentGraphics = graphics.create();
    try {
      componentGraphics.translate(
          (int) Math.round(screenOffset.getX() - screenOrigin.getX()),
          (int) Math.round(screenOffset.getY() - screenOrigin.getY()));
      for (Item currentItem : itemList) {
        // Component geometry follows its authoritative pose until insertion creates the copy.
        // A fresh view discards the translated drill-center cache without changing that pose.
        boolean componentItem = currentItem.getComponentId() > 0;
        BoardRenderer.drawOverlayItem(
            componentItem ? currentItem.copy(currentItem.getId()) : currentItem,
            componentItem ? componentGraphics : graphics,
            hdlg.graphicsContext,
            hdlg.graphicsContext.getHighlightColor(),
            hdlg.graphicsContext.getHighlightColorIntensity());
      }
    } finally {
      componentGraphics.dispose();
    }
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuCopy;
  }
}
