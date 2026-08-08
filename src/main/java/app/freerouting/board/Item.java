package app.freerouting.board;

import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.rules.Nets;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Basic class of the items on a board. */
public abstract class Item
    implements Drawable,
        SearchTreeObject,
        ObjectInfoPanel.Printable,
        UndoableObjects.Storable,
        Serializable {

  private static final double PROTECT_FANOUT_LENGTH = 400;
  private final int idNo;

  /** The board this Item is on */
  public transient BasicBoard board;

  public double smallestClearance;

  /** not 0, if this item belongs to a component */
  protected int componentNo;

  /** The nets, to which this item belongs */
  int[] netNoArr;

  /** the index in the clearance matrix describing the required spacing to other items */
  private int clearanceClass;

  /** points to the entries of this item in the ShapeSearchTrees */
  private transient ItemSearchTreesInfo searchTreesInfo;

  private FixedState fixedState;

  /** False, if the item is deleted or not inserted into the board */
  private boolean onTheBoard;

  /** Temporary data used in the autoroute algorithm. */
  private transient ItemAutorouteInfo autorouteInfo;

  Item(
      int[] pNetNoArr,
      int pClearanceType,
      int pIdNo,
      int pComponentNo,
      FixedState pFixedState,
      BasicBoard pBoard) {
    if (pNetNoArr == null) {
      netNoArr = new int[0];
    } else {
      netNoArr = new int[pNetNoArr.length];
      System.arraycopy(pNetNoArr, 0, netNoArr, 0, pNetNoArr.length);
    }
    clearanceClass = pClearanceType;
    componentNo = pComponentNo;
    fixedState = pFixedState;
    board = pBoard;
    if (pIdNo <= 0) {
      idNo = board.communication.idNoGenerator.newNo();
    } else {
      idNo = pIdNo;
    }
  }

  /** Implements the comparable interface. */
  @Override
  public int compareTo(Object pOther) {
    int result;
    if (pOther instanceof Item item) {
      result = item.idNo - idNo;
    } else {
      result = 1;
    }
    return result;
  }

  /** returns the unique identification number of this item */
  public int getIdNo() {
    return idNo;
  }

  /** Returns true if the net number array of this item contains p_net_no. */
  public boolean containsNet(int pNetNo) {
    if (pNetNo <= 0) {
      return false;
    }
    for (int i = 0; i < netNoArr.length; i++) {
      if (netNoArr[i] == pNetNo) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isObstacle(int pNetNo) {
    return !containsNet(pNetNo);
  }

  @Override
  public boolean isTraceObstacle(int pNetNo) {
    return !containsNet(pNetNo);
  }

  /** Returns, if this item in not allowed to overlap with p_other. */
  public abstract boolean isObstacle(Item pOther);

  /** Returns true if the net number arrays of this and p_other have a common number. */
  public boolean sharesNet(Item pOther) {
    return this.sharesNetNo(pOther.netNoArr);
  }

  /** Returns true if the net number array of this and p_net_no_arr have a common number. */
  public boolean sharesNetNo(int[] pNetNoArr) {
    for (int i = 0; i < netNoArr.length; i++) {
      for (int j = 0; j < pNetNoArr.length; j++) {
        if (netNoArr[i] == pNetNoArr[j]) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the number of shapes of this item after decomposition into convex polygonal shapes */
  public abstract int tileShapeCount();

  /**
   * Returns the p_index-throws shape of this item after decomposition into convex polygonal shapes
   */
  public TileShape getTileShape(int pIndex) {
    if (this.board == null) {
      FRLogger.warn("Item.get_tile_shape: app.freerouting.board is null");
      return null;
    }
    return getTreeShape(this.board.searchTreeManager.getDefaultTree(), pIndex);
  }

  @Override
  public int treeShapeCount(ShapeTree pTree) {
    if (this.board == null) {
      return 0;
    }
    TileShape[] precalculatedTreeShapes = this.getPrecalculatedTreeShapes(pTree);
    return precalculatedTreeShapes.length;
  }

  @Override
  public TileShape getTreeShape(ShapeTree pTree, int pIndex) {
    if (this.board == null) {
      return null;
    }
    TileShape[] precalculatedTreeShapes = this.getPrecalculatedTreeShapes(pTree);
    if (precalculatedTreeShapes == null || pIndex < 0 || pIndex >= precalculatedTreeShapes.length) {
      this.clearDerivedData();
      precalculatedTreeShapes = this.getPrecalculatedTreeShapes(pTree);
    }
    if (precalculatedTreeShapes == null || pIndex < 0 || pIndex >= precalculatedTreeShapes.length) {
      return null;
    }
    return precalculatedTreeShapes[pIndex];
  }

  private TileShape[] getPrecalculatedTreeShapes(ShapeTree pTree) {
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    TileShape[] precalculatedTreeShapes = this.searchTreesInfo.getPrecalculatedTreeShapes(pTree);
    if (precalculatedTreeShapes == null) {
      precalculatedTreeShapes = this.calculateTreeShapes((ShapeSearchTree) pTree);
      this.searchTreesInfo.setPrecalculatedTreeShapes(precalculatedTreeShapes, pTree);
    }
    return precalculatedTreeShapes;
  }

  /** Calculates the tree shapes for this item for p_search_tree. */
  protected abstract TileShape[] calculateTreeShapes(ShapeSearchTree pSearchTree);

  /** Returns false, if this item is deleted oor not inserted into the board. */
  public boolean isOnTheBoard() {
    return this.onTheBoard;
  }

  void setOnTheBoard(boolean pValue) {
    this.onTheBoard = pValue;
  }

  /**
   * Creates a copy of this item with id number p_id_no. If p_id_no {@literal <}= 0, the idNo of the
   * new item is generated internally
   */
  public abstract Item copy(int pIdNo);

  @Override
  public Object clone() {
    Item dup = copy(this.getIdNo());

    dup.onTheBoard = this.onTheBoard;
    // dup.searchTreesInfo = this.searchTreesInfo;

    return dup;
  }

  /** returns true, if the layer range of this item contains p_layer */
  public abstract boolean isOnLayer(int pLayer);

  /** Returns the number of the first layer containing geometry of this item. */
  public abstract int firstLayer();

  /** Returns the number of the last layer containing geometry of this item. */
  public abstract int lastLayer();

  /** write this item to an output stream */
  public abstract boolean write(ObjectOutputStream pStream);

  /** Translates the shapes of this item by p_vector. Does not move the item in the board. */
  public abstract void translateBy(Vector pVector);

  /**
   * Turns this Item by p_factor times 90 degree around p_pole. Does not update the item in the
   * board.
   */
  public abstract void turn90Degree(int pFactor, IntPoint pPole);

  /**
   * Rotates this Item by p_angle_in_degree around p_pole. Does not update the item in the board.
   */
  public abstract void rotateApprox(double pAngleInDegree, FloatPoint pPole);

  /**
   * Changes the placement side of this Item and mirrors it at the vertical line through p_pole.
   * Does not update the item in the board.
   */
  public abstract void changePlacementSide(IntPoint pPole);

  /** Returns a box containing the geometry of this item. */
  public abstract IntBox boundingBox();

  /** Translates this item by p_vector in the board. */
  public void moveBy(Vector pVector) {
    board.itemList.saveForUndo(this);
    board.searchTreeManager.remove(this);
    this.translateBy(pVector);
    board.searchTreeManager.insert(this);

    // let the observers synchronize the changes
    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notifyChanged(this);
    }
  }

  /** Returns true, if some shapes of this item and p_other are on the same layer. */
  public boolean sharesLayer(Item pOther) {
    int maxFirstLayer = Math.max(this.firstLayer(), pOther.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), pOther.lastLayer());
    return maxFirstLayer <= minLastLayer;
  }

  /**
   * Returns the first layer, where both this item and p_other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int firstCommonLayer(Item pOther) {
    int maxFirstLayer = Math.max(this.firstLayer(), pOther.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), pOther.lastLayer());
    if (maxFirstLayer > minLastLayer) {
      return -1;
    }
    return maxFirstLayer;
  }

  /**
   * Returns the last layer, where both this item and p_other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int lastCommonLayer(Item pOther) {
    int maxFirstLayer = Math.max(this.firstLayer(), pOther.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), pOther.lastLayer());
    if (maxFirstLayer > minLastLayer) {
      return -1;
    }
    return minLastLayer;
  }

  /**
   * Return the name of the component of this item or null, if this item does not belong to a
   * component.
   */
  public String componentName() {
    if (componentNo <= 0) {
      return null;
    }
    return board.components.get(componentNo).name;
  }

  /** Returns the count of clearance violations of this item with other items. */
  public int clearanceViolationCount() {
    Collection<ClearanceViolation> violations = this.clearanceViolations();
    return violations.size();
  }

  /**
   * Returns a list of all clearance violations of this item with other items. The objects in the
   * list are of type ClearanceViolations. The firstItem in such an object is always this item.
   */
  public Collection<ClearanceViolation> clearanceViolations() {
    Collection<ClearanceViolation> result = new LinkedList<>();
    if (this.board == null) {
      return result;
    }
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    for (int i = 0; i < tileShapeCount(); i++) {
      TileShape currTileShape = getTileShape(i);
      Collection<TreeEntry> currOverlappingItems =
          defaultTree.overlappingTreeEntriesWithClearance(
              currTileShape, shapeLayer(i), new int[0], clearanceClass);
      for (TreeEntry currEntry : currOverlappingItems) {
        if (!(currEntry.object instanceof Item currItem) || currEntry.object == this) {
          continue;
        }
        boolean isObstacle = currItem.isObstacle(this);
        if (isObstacle && this instanceof Trace this_trace && currItem instanceof Trace) {
          // Look, if both traces are connected to the same tie pin.
          // In this case they are allowed to overlap without sharing a net.
          Point contactPoint = this_trace.firstCorner();
          boolean contactFound = false;
          Collection<Item> currContacts = this_trace.getNormalContacts(contactPoint, true);
          {
            if (currContacts.contains(currItem)) {
              contactFound = true;
            }
          }
          if (!contactFound) {
            contactPoint = this_trace.lastCorner();
            currContacts = this_trace.getNormalContacts(contactPoint, true);
            {
              if (currContacts.contains(currItem)) {
                contactFound = true;
              }
            }
          }
          if (contactFound) {
            for (Item currContact : currContacts) {
              if (currContact instanceof Pin) {
                if (currContact.sharesNet(this) && currContact.sharesNet(currItem)) {
                  isObstacle = false;
                  break;
                }
              }
            }
          }
        }

        if (isObstacle) {
          // Get the two shapes the clearance is calculated between
          TileShape shape1 = currTileShape;
          TileShape shape2 = currItem.getTreeShape(defaultTree, currEntry.shapeIndexInObject);
          if (shape1 == null || shape2 == null) {
            FRLogger.warn("Item.clearanceViolations: unexpected null shape");
            continue;
          }

          // Calculate the expected minimum clearance between these two shapes
          double minimumClearance =
              board.rules.clearanceMatrix.getValue(
                  currItem.clearanceClass, this.clearanceClass, shapeLayer(i), false);

          double actualClearance = 0;

          TileShape enlargedShape1 = (TileShape) shape1.enlarge(0);
          TileShape enlargedShape2 = (TileShape) shape2.enlarge(0);

          if (!this.board.searchTreeManager.isClearanceCompensationUsed()) {
            double clOffset = 0.5 * minimumClearance;
            enlargedShape1 = (TileShape) shape1.enlarge(clOffset);
            enlargedShape2 = (TileShape) shape2.enlarge(clOffset);

            actualClearance =
                calculateClearanceBetweenTwoShapes(
                    shape1, shape2, minimumClearance + ClearanceMatrix.clearance_safety_margin);
            if ((smallestClearance == 0) || (actualClearance < smallestClearance)) {
              smallestClearance = actualClearance;
            }
          }

          TileShape intersection = enlargedShape1.intersection(enlargedShape2);
          if (intersection.dimension() == 2) {
            ClearanceViolation currViolation =
                new ClearanceViolation(
                    this, currItem, intersection, shapeLayer(i), minimumClearance, actualClearance);
            result.add(currViolation);
          }
        }
      }
    }
    return result;
  }

  private double calculateClearanceBetweenTwoShapes(
      TileShape shape1, TileShape shape2, double minimumClearance) {
    for (double clearance = minimumClearance; clearance > 0; clearance--) {
      double clOffset = 0.5 * clearance;
      TileShape enlargedShape1 = (TileShape) shape1.enlarge(clOffset);
      TileShape enlargedShape2 = (TileShape) shape2.enlarge(clOffset);

      TileShape intersection = enlargedShape1.intersection(enlargedShape2);
      if (intersection.dimension() != 2) {
        return clearance;
      }
    }

    return 0;
  }

  /**
   * Returns all connectable Items with a direct contacts to this item. The result will be empty, if
   * this item is not connectable.
   */
  public Set<Item> getAllContacts() {
    Set<Item> result = new TreeSet<>();
    if (!(this instanceof Connectable)) {
      return result;
    }
    for (int i = 0; i < this.tileShapeCount(); i++) {
      Collection<SearchTreeObject> overlappingItems =
          board.overlappingObjects(getTileShape(i), shapeLayer(i));
      for (SearchTreeObject currOb : overlappingItems) {
        if (!(currOb instanceof Item currItem)) {
          continue;
        }
        if (currItem != this && currItem instanceof Connectable && currItem.sharesNet(this)) {
          result.add(currItem);
        }
      }
    }
    return result;
  }

  /**
   * Returns all connectable Items with a direct contacts to this item on the input layer. The
   * result will be empty, if this item is not connectable.
   */
  public Set<Item> getAllContacts(int pLayer) {
    Set<Item> result = new TreeSet<>();
    if (!(this instanceof Connectable)) {
      return result;
    }
    for (int i = 0; i < this.tileShapeCount(); i++) {
      if (this.shapeLayer(i) != pLayer) {
        continue;
      }
      Collection<SearchTreeObject> overlappingItems =
          board.overlappingObjects(getTileShape(i), pLayer);
      for (SearchTreeObject currOb : overlappingItems) {
        if (!(currOb instanceof Item currItem)) {
          continue;
        }
        if (currItem != this && currItem instanceof Connectable && currItem.sharesNet(this)) {
          result.add(currItem);
        }
      }
    }
    return result;
  }

  /**
   * Checks, if this item is electrically connected to another connectable item. Returns false for
   * items, which are not connectable.
   */
  public boolean isConnected() {
    Collection<Item> contacts = this.getAllContacts();
    return !contacts.isEmpty();
  }

  /**
   * Checks, if this item is electrically connected to another connectable item on the input layer.
   * Returns false for items, which are not connectable.
   */
  public boolean isConnectedOnLayer(int pLayer) {
    Collection<Item> contactsOnLayer = this.getAllContacts(pLayer);
    return !contactsOnLayer.isEmpty();
  }

  /** default implementation to be overwritten in the Connectable subclasses */
  public Set<Item> getNormalContacts() {
    return new TreeSet<>();
  }

  /**
   * Returns the contact point, if this item and p_other are Connectable and have a unique normal
   * contact. Returns null otherwise
   */
  public Point normalContactPoint(Item pOther) {
    return null;
  }

  /** auxiliary function */
  Point normalContactPoint(Trace pOther) {
    return null;
  }

  /** auxiliary function */
  Point normalContactPoint(DrillItem pOther) {
    return null;
  }

  /**
   * Returns the set of all Connectable items of the net with number p_net_no which can be reached
   * recursively via normal contacts from this item. If p_net_no {@literal <}= 0, the net number is
   * ignored.
   */
  public Set<Item> getConnectedSet(int pNetNo) {
    return getConnectedSet(pNetNo, false);
  }

  /**
   * Returns the set of all Connectable items of the net with number p_net_no which can be reached
   * recursively via normal contacts from this item. If p_net_no {@literal <}= 0, the net number is
   * ignored. If p_stop_at_plane, the recursive algorithm stops, when a conduction area is reached,
   * which does not belong to a component.
   */
  public Set<Item> getConnectedSet(int pNetNo, boolean pStopAtPlane) {
    Set<Item> result = new TreeSet<>();
    if (pNetNo > 0 && !this.containsNet(pNetNo)) {
      return result;
    }
    result.add(this);
    getConnectedSetRecu(result, pNetNo, pStopAtPlane);
    return result;
  }

  /** recursive part of get_connected_set */
  private void getConnectedSetRecu(Set<Item> pResult, int pNetNo, boolean pStopAtPlane) {
    Collection<Item> contactList = getNormalContacts();
    if (contactList == null) {
      return;
    }
    for (Item currContact : contactList) {
      if (pStopAtPlane
          && currContact instanceof ConductionArea
          && currContact.getComponentNo() <= 0) {
        continue;
      }
      if (pNetNo > 0 && !currContact.containsNet(pNetNo)) {
        continue;
      }
      if (pResult.add(currContact)) {
        currContact.getConnectedSetRecu(pResult, pNetNo, pStopAtPlane);
      }
    }
  }

  /** Returns true, if this item contains some overlap to be cleaned. */
  public boolean isOverlap() {
    return false;
  }

  /**
   * Recursive part of Trace.is_cycle. If p_ignore_areas is true, cycles where conduction areas are
   * involved are ignored.
   */
  boolean isCycleRecu(
      Set<Item> pVisitedItems, Item pSearchItem, Item pComeFromItem, boolean pIgnoreAreas) {
    if (pIgnoreAreas && this instanceof ConductionArea) {
      return false;
    }
    Collection<Item> contactList = getNormalContacts();
    if (contactList == null) {
      return false;
    }
    for (Item currContact : contactList) {
      if (currContact == pComeFromItem) {
        continue;
      }
      if (currContact == pSearchItem) {
        return true;
      }
      if (pVisitedItems.add(currContact)) {
        if (currContact.isCycleRecu(pVisitedItems, pSearchItem, this, pIgnoreAreas)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns the set of all Connectable items belonging to the net with number p_net_no, which are
   * not in the connected set of this item. If p_net_no {@literal <}= 0, the net numbers contained
   * in this items are used instead of p_net_no.
   */
  public Set<Item> getUnconnectedSet(int pNetNo) {
    Set<Item> result = new TreeSet<>();
    if (pNetNo > 0 && !this.containsNet(pNetNo)) {
      return result;
    }
    if (pNetNo > 0) {
      result.addAll(board.getConnectableItems(pNetNo));
    } else {
      for (int currNetNo : this.netNoArr) {
        result.addAll(board.getConnectableItems(currNetNo));
      }
    }
    result.removeAll(this.getConnectedSet(pNetNo));
    return result;
  }

  /** Returns all traces and vias from this item until the next fork or terminal item. */
  public Set<Item> getConnectionItems() {
    return getConnectionItems(StopConnectionOption.NONE);
  }

  /**
   * Returns all traces and vias from this item until the next fork or terminal item. If
   * p_stop_option == StopConnectionOption.FANOUT_VIA, the algorithm will stop at the next fanout
   * via, If p_stop_option == StopConnectionOption.VIA, the algorithm will stop at any via.
   */
  public Set<Item> getConnectionItems(StopConnectionOption pStopOption) {
    Set<Item> contacts = this.getNormalContacts();
    Set<Item> result = new TreeSet<>();
    if (this.isRoutable()) {
      result.add(this);
    }
    for (Item currItem : contacts) {
      Point prevContactPoint = this.normalContactPoint(currItem);
      if (prevContactPoint == null) {
        // no unique contact point
        continue;
      }
      int prevContactLayer = this.firstCommonLayer(currItem);
      if (this instanceof Trace start_trace) {
        // Check, that there is only 1 contact at this location.
        // Only for pins and vias items of more than 1 connection
        // are collected
        Collection<Item> checkContacts = start_trace.getNormalContacts(prevContactPoint, false);
        if (checkContacts.size() != 1) {
          continue;
        }
      }
      // Search from currItem along the contacts
      // until the next fork or nonroute item.
      for (; ; ) {
        if (!currItem.isRoutable()) {
          // connection ends
          break;
        }
        if (currItem instanceof Via) {
          if (pStopOption == StopConnectionOption.VIA) {
            break;
          }
          if (pStopOption == StopConnectionOption.FANOUT_VIA) {
            if (currItem.isFanoutVia(result)) {
              break;
            }
          }
        }
        result.add(currItem);
        Collection<Item> currObContacts = currItem.getNormalContacts();
        // filter the contacts at the previous contact point,
        // because we were already there.
        // If then there is not exactly 1 new contact left, there is
        // a stub or a fork.
        Point nextContactPoint = null;
        int nextContactLayer = -1;
        Item nextContact = null;
        boolean forkFound = false;
        for (Item tmpContact : currObContacts) {
          int tmpContactLayer = currItem.firstCommonLayer(tmpContact);
          if (tmpContactLayer >= 0) {
            Point tmpContactPoint = currItem.normalContactPoint(tmpContact);
            if (tmpContactPoint == null) {
              // no unique contact point
              forkFound = true;
              break;
            }
            if (prevContactLayer != tmpContactLayer || !prevContactPoint.equals(tmpContactPoint)) {
              if (nextContact != null) {
                // second new contact found
                forkFound = true;
                break;
              }
              nextContact = tmpContact;
              nextContactPoint = tmpContactPoint;
              nextContactLayer = tmpContactLayer;
            }
          }
        }
        if (nextContact == null || forkFound) {
          break;
        }
        currItem = nextContact;
        prevContactPoint = nextContactPoint;
        prevContactLayer = nextContactLayer;
      }
    }
    return result;
  }

  /** Function to be overwritten by classes Trace and Via */
  public boolean isTail() {
    return false;
  }

  /**
   * Returns all corners of this item, which are used for displaying the ratsnest. To be overwritten
   * in derived classes implementing the Connectable interface.
   */
  public Point[] getRatsnestCorners() {
    return new Point[0];
  }

  @Override
  public void draw(Graphics pG, GraphicsContext pGraphicsContext, Color pColor, double pIntensity) {
    Color[] colorArr = new Color[board.getLayerCount()];
    Arrays.fill(colorArr, pColor);
    draw(pG, pGraphicsContext, colorArr, pIntensity);
  }

  /**
   * Draws this item with its draw colors from p_graphics_context. p_layer_visibility[i] is expected
   * between 0 and 1 for each layer i.
   */
  public void draw(Graphics pG, GraphicsContext pGraphicsContext) {
    Color[] layerColors = getDrawColors(pGraphicsContext);
    draw(pG, pGraphicsContext, layerColors, getDrawIntensity(pGraphicsContext));
  }

  /** Draws this item on a specific layer only, with its draw colors from p_graphics_context. */
  public void drawLayer(Graphics pG, GraphicsContext pGraphicsContext, int pLayerNo) {
    if (this.isOnLayer(pLayerNo)) {
      Color[] layerColors = getDrawColors(pGraphicsContext);
      drawLayer(pG, pGraphicsContext, layerColors, getDrawIntensity(pGraphicsContext), pLayerNo);
    }
  }

  /** Draws this item on a specific layer only. */
  public void drawLayer(
      Graphics pG,
      GraphicsContext pGraphicsContext,
      Color[] pColorArr,
      double pIntensity,
      int pLayerNo) {
    if (this.isOnLayer(pLayerNo)) {
      draw(pG, pGraphicsContext, pColorArr, pIntensity);
    }
  }

  /** Test function checking the item for inconsistencies. */
  public boolean validate() {
    boolean result = board.searchTreeManager.validateEntries(this);
    for (int i = 0; i < this.tileShapeCount(); i++) {
      TileShape currShape = this.getTileShape(i);
      if (currShape.isEmpty()) {
        FRLogger.warn("Item.validate: shape is empty");
        result = false;
      }
    }
    return result;
  }

  /**
   * Returns for this item the layer of the shape with index p_index. If p_id_no {@literal <}= 0, it
   * will be generated internally.
   */
  @Override
  public abstract int shapeLayer(int pIndex);

  /** Returns true, if it is not allowed to change this item except shoving the item */
  public boolean isUserFixed() {
    return fixedState.ordinal() >= FixedState.USER_FIXED.ordinal();
  }

  /** Returns true, if it is not allowed to delete this item. */
  boolean isDeletionForbidden() {
    // Items belonging to a component are delete_fixed.
    if (this.componentNo > 0 || isUserFixed()) {
      return true;
    }
    // Also power planes are delete_fixed.
    if (this instanceof ConductionArea area) {
      return !this.board.layerStructure.arr[area.getLayer()].isSignal;
    }
    return false;
  }

  /**
   * Returns true, if it is not allowed to change the location of this item by the push algorithm.
   */
  public boolean isShoveFixed() {
    return this.fixedState.ordinal() >= FixedState.SHOVE_FIXED.ordinal();
  }

  /** Returns the fixed state of this Item. */
  public FixedState getFixedState() {
    return this.fixedState;
  }

  /** Fixes the item. */
  public void setFixedState(FixedState pFixedState) {
    fixedState = pFixedState;
  }

  /** Returns false, if this item is an obstacle for vias with the input net number. */
  public boolean isDrillable(int pNetNo) {
    return false;
  }

  /** Unfixes the item, if it is not fixed by the system. */
  public void unfix() {
    if (fixedState != FixedState.SYSTEM_FIXED) {
      fixedState = FixedState.UNFIXED;
    }
  }

  /** returns true, if this item is an unfixed trace or via, so it can be routed by auto-router */
  public boolean isRoutable() {
    return false;
  }

  /** Returns, if this item can be routed to. */
  public boolean isConnectable() {
    return (this instanceof Connectable) && this.netCount() > 0;
  }

  /** Returns the count of nets this item belongs to. */
  public int netCount() {
    return netNoArr.length;
  }

  /**
   * gets the p_no-th net number of this item for 0 {@literal <}= p_no {@literal <}
   * this.net_count().
   */
  public int getNetNo(int pNo) {
    return netNoArr[pNo];
  }

  /** Return the component number of this item or 0, if it does not belong to a component. */
  public int getComponentNo() {
    return componentNo;
  }

  /**
   * Removes p_net_no from the net number array. Returns false, if p_net_no was not contained in
   * this array.
   */
  public boolean removeFromNet(int pNetNo) {
    int foundIndex = -1;
    for (int i = 0; i < this.netNoArr.length; i++) {
      if (this.netNoArr[i] == pNetNo) {
        foundIndex = i;
      }
    }
    if (foundIndex < 0) {
      return false;
    }
    int[] newNetNoArr = new int[this.netNoArr.length - 1];
    System.arraycopy(this.netNoArr, 0, newNetNoArr, 0, foundIndex);
    if (foundIndex < newNetNoArr.length) {
      // copy remaining elements if present
      System.arraycopy(
          this.netNoArr, foundIndex + 1, newNetNoArr, foundIndex, newNetNoArr.length - foundIndex);
    }
    this.netNoArr = newNetNoArr;
    return true;
  }

  /**
   * Returns the index in the clearance matrix describing the required spacing of this item to other
   * items
   */
  public int clearanceClassNo() {
    return clearanceClass;
  }

  /**
   * Sets the index in the clearance matrix describing the required spacing of this item to other
   * items.
   */
  public void setClearanceClassNo(int pIndex) {
    if (pIndex < 0 || pIndex >= this.board.rules.clearanceMatrix.getClassCount()) {
      FRLogger.warn("Item.set_clearance_class_no: p_index out of range");
      return;
    }
    clearanceClass = pIndex;
  }

  /** Changes the clearance class of this item and updates the search tree. */
  public void changeClearanceClass(int pIndex) {
    if (pIndex < 0 || pIndex >= this.board.rules.clearanceMatrix.getClassCount()) {
      FRLogger.warn("Item.set_clearance_class_no: p_index out of range");
      return;
    }
    clearanceClass = pIndex;
    this.clearDerivedData();
    if (this.board != null && this.board.searchTreeManager.isClearanceCompensationUsed()) {
      // reinsert the item into the search tree, because the compensated shape has changed.
      this.board.searchTreeManager.remove(this);
      this.board.searchTreeManager.insert(this);
    }
  }

  /** Assigns this item to the component with the input component number. */
  public void assignComponentNo(int pNo) {
    componentNo = pNo;
  }

  /**
   * Makes this item connectable and assigns it to the input net. If p_net_no {@literal <} 0, the
   * net items net number will be removed and the item will no longer be connectable.
   */
  public void assignNetNo(int pNetNo) {
    if (!Nets.isNormalNetNo(pNetNo)) {
      return;
    }
    if (pNetNo > board.rules.nets.maxNetNo()) {
      FRLogger.warn("Item.assign_net_no: p_net_no to big");
      return;
    }
    board.itemList.saveForUndo(this);
    if (pNetNo <= 0) {
      netNoArr = new int[0];
    } else {
      if (netNoArr.length == 0) {
        netNoArr = new int[1];
      } else if (netNoArr.length > 1) {
        FRLogger.warn("Item.assign_net_no: unexpected netCount > 1");
      }
      netNoArr[0] = pNetNo;
    }
  }

  /** Returns true, if p_item is contained in the input filter. */
  public abstract boolean isSelectedByFilter(ItemSelectionFilter pFilter);

  /** Internally used for implementing the function is_selected_by_filter */
  protected boolean isSelectedByFixedFilter(ItemSelectionFilter pFilter) {
    boolean result;
    if (this.isUserFixed()) {
      result = pFilter.isSelected(ItemSelectionFilter.SelectableChoices.FIXED);
    } else {
      result = pFilter.isSelected(ItemSelectionFilter.SelectableChoices.UNFIXED);
    }
    return result;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  @Override
  public void setSearchTreeEntries(ShapeTree.Leaf[] pTreeEntries, ShapeTree pTree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    this.searchTreesInfo.setTreeEntries(pTreeEntries, pTree);
  }

  /**
   * Returns the tree entries for the tree with identification number p_tree_no, or null, if for
   * this tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] getSearchTreeEntries(ShapeSearchTree pTree) {
    if (this.searchTreesInfo == null) {
      return null;
    }
    return this.searchTreesInfo.getTreeEntries(pTree);
  }

  /**
   * Sets the precalculated tree shapes tree entries for the tree with identification number
   * p_tree_no.
   */
  protected void setPrecalculatedTreeShapes(TileShape[] pShapes, ShapeSearchTree pTree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      FRLogger.warn("Item.set_precalculated_tree_shapes searchTreesInfo not allocated");
      return;
    }
    this.searchTreesInfo.setPrecalculatedTreeShapes(pShapes, pTree);
  }

  /** Sets the search tree entries of this item to null. */
  public void clearSearchTreeEntries() {
    this.searchTreesInfo = null;
  }

  /** Gets the information for the autoroute algorithm. Creates it, if it does not yet exist. */
  public ItemAutorouteInfo getAutorouteInfo() {
    if (autorouteInfo == null) {
      autorouteInfo = new ItemAutorouteInfo(this);
    }
    return autorouteInfo;
  }

  /** Gets the information for the autoroute algorithm. */
  public ItemAutorouteInfo getAutorouteInfoPur() {
    return autorouteInfo;
  }

  /** Clears the data allocated for the autoroute algorithm. */
  public void clearAutorouteInfo() {
    autorouteInfo = null;
  }

  /**
   * Clear all cached or derived data. so that they have to be recalculated, when they are used next
   * time.
   */
  public void clearDerivedData() {
    if (this.searchTreesInfo != null) {
      this.searchTreesInfo.clearPrecalculatedTreeShapes();
    }
    autorouteInfo = null;
  }

  /** Gets the information for hover event to display */
  public String getHoverInfo(Locale pLocale) {
    return "";
  }

  /** Internal function used in the implementation of get_hover_info */
  public String getConnectableItemHoverInfo(Locale pLocale) {
    return this.getNetHoverInfo(pLocale);
  }

  /** Internal function used in the implementation of get_hover_info */
  public String getNetHoverInfo(Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.netCount(); i++) {
      if (i > 0) {
        sb.append("<br>");
      }
      Net currNet = board.rules.nets.get(this.getNetNo(i));
      sb.append(tm.getText("net_hover_info", currNet.name));
    }
    return sb.toString();
  }

  /** Internal function used in the implementation of print_info */
  protected void printNetInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    for (int i = 0; i < this.netCount(); i++) {
      pWindow.append(", " + tm.getText("net") + " ");
      Net currNet = board.rules.nets.get(this.getNetNo(i));
      pWindow.append(currNet.name, tm.getText("net_info"), currNet);
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void printClearanceInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    if (this.clearanceClass > 0) {
      TextManager tm = new TextManager(this.getClass(), pLocale);

      pWindow.append(", " + tm.getText("clearanceClass") + " ");
      String name = board.rules.clearanceMatrix.getName(this.clearanceClass);
      pWindow.append(
          name,
          tm.getText("clearance_info"),
          board.rules.clearanceMatrix.getRow(this.clearanceClass));
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void printFixedInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    if (this.fixedState != FixedState.UNFIXED) {
      TextManager tm = new TextManager(this.getClass(), pLocale);

      pWindow.append(", ");
      pWindow.append(tm.getText(this.fixedState.toString()));
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void printContactInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    Collection<Item> contacts = this.getNormalContacts();
    if (!contacts.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), pLocale);

      pWindow.append(", " + tm.getText("contacts") + " ");
      int contactCount = contacts.size();
      pWindow.appendItems(String.valueOf(contactCount), tm.getText("contact_info"), contacts);
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void printClearanceViolationInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    Collection<ClearanceViolation> clearanceViolations = this.clearanceViolations();
    if (!clearanceViolations.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), pLocale);

      pWindow.append(", ");
      int violationCount = clearanceViolations.size();
      Collection<ObjectInfoPanel.Printable> violations = new LinkedList<>(clearanceViolations);
      pWindow.appendObjects(
          String.valueOf(violationCount), tm.getText("violation_info"), violations);
      if (violationCount == 1) {
        pWindow.append(" " + tm.getText("clearance_violation"));
      } else {
        pWindow.append(" " + tm.getText("clearanceViolations"));
      }
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void printConnectableItemInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    this.printClearanceInfo(pWindow, pLocale);
    this.printFixedInfo(pWindow, pLocale);
    this.printNetInfo(pWindow, pLocale);
    this.printContactInfo(pWindow, pLocale);
    this.printClearanceViolationInfo(pWindow, pLocale);
  }

  /** Internal function used in the implementation of print_info */
  protected void printItemInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    this.printClearanceInfo(pWindow, pLocale);
    this.printFixedInfo(pWindow, pLocale);
    this.printClearanceViolationInfo(pWindow, pLocale);
  }

  /** Checks, if all nets of this items are normal. */
  public boolean netsNormal() {
    for (int i = 0; i < this.netNoArr.length; i++) {
      if (!Nets.isNormalNetNo(this.netNoArr[i])) {
        return false;
      }
    }
    return true;
  }

  /** Checks, if this item and p_other contain exactly the same net numbers. */
  public boolean netsEqual(Item pOther) {
    return netsEqual(pOther.netNoArr);
  }

  /** Checks, if this item contains exactly the nets in p_net_no_arr */
  public boolean netsEqual(int[] pNetNoArr) {
    if (this.netNoArr.length != pNetNoArr.length) {
      return false;
    }
    for (int currNetNo : pNetNoArr) {
      if (!this.containsNet(currNetNo)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true, if the via is directly ob by a trace connected to a nearby SMD-pin. If
   * p_ignore_items != null, contact traces in P-ignoreItems are ignored.
   */
  boolean isFanoutVia(Set<Item> pIgnoreItems) {
    Collection<Item> contactList = this.getNormalContacts();
    for (Item currContact : contactList) {
      if (currContact instanceof Pin
          && currContact.firstLayer() == currContact.lastLayer()
          && currContact.getNormalContacts().size() <= 1) {
        return true;
      }
      if (currContact instanceof Trace currTrace) {
        if (pIgnoreItems != null && pIgnoreItems.contains(currContact)) {
          continue;
        }
        if (currTrace.getLength() >= PROTECT_FANOUT_LENGTH * currTrace.getHalfWidth()) {
          continue;
        }
        Collection<Item> traceContactList = currTrace.getNormalContacts();
        for (Item tmpContact : traceContactList) {
          if (tmpContact instanceof Pin
              && tmpContact.firstLayer() == tmpContact.lastLayer()
              && tmpContact.getNormalContacts().size() <= 1) {
            return true;
          }
          if (tmpContact instanceof PolylineTrace contactTrace
              && tmpContact.getFixedState() == FixedState.SHOVE_FIXED) {
            // look for shove fixed exit traces of SMD-pins
            if (contactTrace.cornerCount() == 2) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * Checks if the item has a net that must be ignored by the auto-router
   *
   * @return true, if this item has at least one net that must be ignored
   */
  public boolean hasIgnoredNets() {
    for (int netNo : this.netNoArr) {
      Net net = this.board.rules.nets.get(netNo);
      if (net.getNetClass().isIgnoredByAutorouter) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    StringBuilder simpleName = new StringBuilder();

    simpleName.append(this.getClass().getSimpleName().toLowerCase());

    if (componentNo > 0) {
      simpleName.append(" of component #");
      simpleName.append(componentNo);
    }

    return simpleName.toString();
  }

  public List<Net> getAllNets() {
    List<Net> nets = new ArrayList<>();
    for (int netNo : this.netNoArr) {
      Net net = board.rules.nets.get(netNo);
      if (net != null) {
        nets.add(net);
      }
    }
    return nets;
  }

  public String getAllNetNames() {
    return this.getAllNets().stream()
        .map(Net::toString)
        .reduce((a, b) -> a + "," + b)
        .orElse("no nets");
  }

  /**
   * Used as parameter of get_connection to control, that the connection stops at the next fanout
   * via or at any via.
   */
  public enum StopConnectionOption {
    NONE,
    FANOUT_VIA,
    VIA
  }
}
