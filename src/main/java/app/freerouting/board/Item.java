package app.freerouting.board;

import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
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
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Basic class of the items on a board. */
public abstract class Item
    implements SearchTreeObject, ItemInfoPrinter.Printable, UndoableObjects.Storable, Serializable {

  private static final double PROTECT_FANOUT_LENGTH = 400;
  private final int id;

  /** The board this Item is on. */
  public transient BasicBoard board;

  public double smallestClearance;

  /** Not 0, if this item belongs to a component. */
  protected int componentId;

  /** The nets, to which this item belongs. */
  public int[] netNumbers;

  /** The index in the clearance matrix describing the required spacing to other items. */
  private int clearanceClassIndex;

  /** Points to the entries of this item in the ShapeSearchTrees. */
  private transient ItemSearchTreesInfo searchTreesInfo;

  private FixedState fixedState;

  /** False, if the item is deleted or not inserted into the board. */
  private boolean onTheBoard;

  /** Temporary data used in the autoroute algorithm. */
  private transient ItemAutorouteInfo autorouteInfo;

  Item(
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      int componentId,
      FixedState fixedState,
      BasicBoard board) {
    if (netNumbers == null) {
      this.netNumbers = new int[0];
    } else {
      this.netNumbers = new int[netNumbers.length];
      System.arraycopy(netNumbers, 0, this.netNumbers, 0, netNumbers.length);
    }
    this.clearanceClassIndex = clearanceClassIndex;
    this.componentId = componentId;
    this.fixedState = fixedState;
    this.board = board;
    if (id <= 0) {
      this.id = board.communication.idGenerator.newId();
    } else {
      this.id = id;
    }
  }

  /** Implements the comparable interface. */
  @Override
  public int compareTo(Object other) {
    int result;
    if (other instanceof Item item) {
      result = item.id - id;
    } else {
      result = 1;
    }
    return result;
  }

  /** Returns the unique identification number of this item. */
  @Override
  public int getId() {
    return id;
  }

  /**
   * Returns the neutral semantic category of this board item.
   *
   * <p>This accessor is intentionally independent of rendering APIs. It gives a future GUI renderer
   * a stable dispatch key while keeping the item model usable by headless routing and DRC code.
   */
  public BoardItemType getBoardItemType() {
    if (this instanceof Pin) {
      return BoardItemType.PIN;
    }
    if (this instanceof Via) {
      return BoardItemType.VIA;
    }
    if (this instanceof Trace) {
      return BoardItemType.TRACE;
    }
    if (this instanceof ConductionArea) {
      return BoardItemType.CONDUCTION_AREA;
    }
    if (this instanceof ViaObstacleArea) {
      return BoardItemType.VIA_OBSTACLE_AREA;
    }
    if (this instanceof ComponentObstacleArea) {
      return BoardItemType.COMPONENT_OBSTACLE_AREA;
    }
    if (this instanceof ObstacleArea) {
      return BoardItemType.OBSTACLE_AREA;
    }
    if (this instanceof ComponentOutline) {
      return BoardItemType.COMPONENT_OUTLINE;
    }
    if (this instanceof BoardOutline) {
      return BoardItemType.BOARD_OUTLINE;
    }
    return BoardItemType.OTHER;
  }

  /** Returns true if the net number array of this item contains netNumber. */
  public boolean containsNet(int netNumber) {
    if (netNumber <= 0) {
      return false;
    }
    for (int i = 0; i < netNumbers.length; i++) {
      if (netNumbers[i] == netNumber) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isObstacle(int netNumber) {
    return !containsNet(netNumber);
  }

  /** Returns, if this item in not allowed to overlap with other. */
  public abstract boolean isObstacle(Item other);

  @Override
  public boolean isTraceObstacle(int netNumber) {
    return !containsNet(netNumber);
  }

  /** Returns true if the net number arrays of this and other have a common number. */
  public boolean sharesNet(Item other) {
    return this.sharesNetNo(other.netNumbers);
  }

  /** Returns true if the net number array of this and netNumbers have a common number. */
  public boolean sharesNetNo(int[] netNumbers) {
    for (int i = 0; i < this.netNumbers.length; i++) {
      for (int j = 0; j < netNumbers.length; j++) {
        if (this.netNumbers[i] == netNumbers[j]) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the number of shapes of this item after decomposition into convex polygonal shapes. */
  public abstract int tileShapeCount();

  /** Returns the index-th shape of this item after decomposition into convex polygonal shapes. */
  public TileShape getTileShape(int index) {
    if (this.board == null) {
      FRLogger.warn("Item.get_tile_shape: app.freerouting.board is null");
      return null;
    }
    return getTreeShape(this.board.searchTreeManager.getDefaultTree(), index);
  }

  @Override
  public int treeShapeCount(ShapeTree tree) {
    if (this.board == null) {
      return 0;
    }
    TileShape[] precalculatedTreeShapes = this.getPrecalculatedTreeShapes(tree);
    return precalculatedTreeShapes.length;
  }

  @Override
  public TileShape getTreeShape(ShapeTree tree, int index) {
    if (this.board == null) {
      return null;
    }
    TileShape[] precalculatedTreeShapes = this.getPrecalculatedTreeShapes(tree);
    if (precalculatedTreeShapes == null || index < 0 || index >= precalculatedTreeShapes.length) {
      this.clearDerivedData();
      precalculatedTreeShapes = this.getPrecalculatedTreeShapes(tree);
    }
    if (precalculatedTreeShapes == null || index < 0 || index >= precalculatedTreeShapes.length) {
      return null;
    }
    return precalculatedTreeShapes[index];
  }

  private TileShape[] getPrecalculatedTreeShapes(ShapeTree tree) {
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    TileShape[] precalculatedTreeShapes = this.searchTreesInfo.getPrecalculatedTreeShapes(tree);
    if (precalculatedTreeShapes == null) {
      precalculatedTreeShapes = this.calculateTreeShapes((ShapeSearchTree) tree);
      this.searchTreesInfo.setPrecalculatedTreeShapes(precalculatedTreeShapes, tree);
    }
    return precalculatedTreeShapes;
  }

  /** Calculates the tree shapes for this item for searchTree. */
  protected abstract TileShape[] calculateTreeShapes(ShapeSearchTree searchTree);

  /** Returns false, if this item is deleted oor not inserted into the board. */
  public boolean isOnTheBoard() {
    return this.onTheBoard;
  }

  public void setOnTheBoard(boolean value) {
    this.onTheBoard = value;
  }

  /**
   * Creates a copy of this item with ID id. If id {@literal <}= 0, the id of the new item is
   * generated internally.
   */
  public abstract Item copy(int id);

  @Override
  public Object clone() {
    Item dup = copy(this.getId());

    dup.onTheBoard = this.onTheBoard;
    // dup.searchTreesInfo = this.searchTreesInfo;

    return dup;
  }

  /** Returns true, if the layer range of this item contains layer. */
  public abstract boolean isOnLayer(int layer);

  /** Returns the number of the first layer containing geometry of this item. */
  public abstract int firstLayer();

  /** Returns the number of the last layer containing geometry of this item. */
  public abstract int lastLayer();

  /** Write this item to an output stream. */
  public abstract boolean write(ObjectOutputStream stream);

  /** Translates the shapes of this item by vector. Does not move the item in the board. */
  public abstract void translateBy(Vector vector);

  /**
   * Turns this Item by factor times 90 degree around pole. Does not update the item in the board.
   */
  public abstract void turn90Degree(int factor, IntPoint pole);

  /** Rotates this Item by angleInDegree around pole. Does not update the item in the board. */
  public abstract void rotateApprox(double angleInDegree, FloatPoint pole);

  /**
   * Changes the placement side of this Item and mirrors it at the vertical line through pole. Does
   * not update the item in the board.
   */
  public abstract void changePlacementSide(IntPoint pole);

  /** Returns a box containing the geometry of this item. */
  public abstract IntBox boundingBox();

  /** Translates this item by vector in the board. */
  public void moveBy(Vector vector) {
    board.itemList.saveForUndo(this);
    board.searchTreeManager.remove(this);
    this.translateBy(vector);
    board.searchTreeManager.insert(this);

    // let the observers synchronize the changes
    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notifyChanged(this);
    }
  }

  /** Returns true, if some shapes of this item and other are on the same layer. */
  public boolean sharesLayer(Item other) {
    int maxFirstLayer = Math.max(this.firstLayer(), other.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), other.lastLayer());
    return maxFirstLayer <= minLastLayer;
  }

  /**
   * Returns the first layer, where both this item and other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int firstCommonLayer(Item other) {
    int maxFirstLayer = Math.max(this.firstLayer(), other.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), other.lastLayer());
    if (maxFirstLayer > minLastLayer) {
      return -1;
    }
    return maxFirstLayer;
  }

  /**
   * Returns the last layer, where both this item and other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int lastCommonLayer(Item other) {
    int maxFirstLayer = Math.max(this.firstLayer(), other.firstLayer());
    int minLastLayer = Math.min(this.lastLayer(), other.lastLayer());
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
    if (componentId <= 0) {
      return null;
    }
    return board.components.get(componentId).name;
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
      TileShape currentTileShape = getTileShape(i);
      Collection<TreeEntry> currentOverlappingItems =
          defaultTree.overlappingTreeEntriesWithClearance(
              currentTileShape, shapeLayer(i), new int[0], this.clearanceClassIndex);
      for (TreeEntry currentEntry : currentOverlappingItems) {
        if (!(currentEntry.object instanceof Item currentItem) || currentEntry.object == this) {
          continue;
        }
        boolean isObstacle = currentItem.isObstacle(this);
        if (isObstacle && this instanceof Trace thisTrace && currentItem instanceof Trace) {
          // Look, if both traces are connected to the same tie pin.
          // In this case they are allowed to overlap without sharing a net.
          Point contactPoint = thisTrace.firstCorner();
          boolean contactFound = false;
          Collection<Item> currentContacts = thisTrace.getNormalContacts(contactPoint, true);
          {
            if (currentContacts.contains(currentItem)) {
              contactFound = true;
            }
          }
          if (!contactFound) {
            contactPoint = thisTrace.lastCorner();
            currentContacts = thisTrace.getNormalContacts(contactPoint, true);
            {
              if (currentContacts.contains(currentItem)) {
                contactFound = true;
              }
            }
          }
          if (contactFound) {
            for (Item currentContact : currentContacts) {
              if (currentContact instanceof Pin) {
                if (currentContact.sharesNet(this) && currentContact.sharesNet(currentItem)) {
                  isObstacle = false;
                  break;
                }
              }
            }
          }
        }

        if (isObstacle) {
          // Get the two shapes the clearance is calculated between
          TileShape shape1 = currentTileShape;
          TileShape shape2 = currentItem.getTreeShape(defaultTree, currentEntry.shapeIndexInObject);
          if (shape1 == null || shape2 == null) {
            FRLogger.warn("Item.clearanceViolations: unexpected null shape");
            continue;
          }

          // Calculate the expected minimum clearance between these two shapes
          double minimumClearance =
              board.rules.clearanceMatrix.getValue(
                  currentItem.clearanceClassIndex, this.clearanceClassIndex, shapeLayer(i), false);

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
            ClearanceViolation currentViolation =
                new ClearanceViolation(
                    this,
                    currentItem,
                    intersection,
                    shapeLayer(i),
                    minimumClearance,
                    actualClearance);
            result.add(currentViolation);
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
      for (SearchTreeObject currentObject : overlappingItems) {
        if (!(currentObject instanceof Item currentItem)) {
          continue;
        }
        if (currentItem != this
            && currentItem instanceof Connectable
            && currentItem.sharesNet(this)) {
          result.add(currentItem);
        }
      }
    }
    return result;
  }

  /**
   * Returns all connectable Items with a direct contacts to this item on the input layer. The
   * result will be empty, if this item is not connectable.
   */
  public Set<Item> getAllContacts(int layer) {
    Set<Item> result = new TreeSet<>();
    if (!(this instanceof Connectable)) {
      return result;
    }
    for (int i = 0; i < this.tileShapeCount(); i++) {
      if (this.shapeLayer(i) != layer) {
        continue;
      }
      Collection<SearchTreeObject> overlappingItems =
          board.overlappingObjects(getTileShape(i), layer);
      for (SearchTreeObject currentObject : overlappingItems) {
        if (!(currentObject instanceof Item currentItem)) {
          continue;
        }
        if (currentItem != this
            && currentItem instanceof Connectable
            && currentItem.sharesNet(this)) {
          result.add(currentItem);
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
  public boolean isConnectedOnLayer(int layer) {
    Collection<Item> contactsOnLayer = this.getAllContacts(layer);
    return !contactsOnLayer.isEmpty();
  }

  /** Default implementation to be overwritten in the Connectable subclasses. */
  public Set<Item> getNormalContacts() {
    return new TreeSet<>();
  }

  /**
   * Returns the contact point, if this item and other are Connectable and have a unique normal
   * contact. Returns null otherwise
   */
  public Point normalContactPoint(Item other) {
    return null;
  }

  /** Auxiliary function. */
  Point normalContactPoint(Trace other) {
    return null;
  }

  /** Auxiliary function. */
  Point normalContactPoint(DrillItem other) {
    return null;
  }

  /**
   * Returns the set of all Connectable items of the net with number netNumber which can be reached
   * recursively via normal contacts from this item. If netNumber {@literal <}= 0, the net number is
   * ignored.
   */
  public Set<Item> getConnectedSet(int netNumber) {
    return getConnectedSet(netNumber, false);
  }

  /**
   * Returns the set of all Connectable items of the net with number netNumber which can be reached
   * recursively via normal contacts from this item. If netNumber {@literal <}= 0, the net number is
   * ignored. If stopAtPlane, the recursive algorithm stops, when a conduction area is reached,
   * which does not belong to a component.
   */
  public Set<Item> getConnectedSet(int netNumber, boolean stopAtPlane) {
    Set<Item> result = new TreeSet<>();
    if (netNumber > 0 && !this.containsNet(netNumber)) {
      return result;
    }
    result.add(this);
    getConnectedSetRecu(result, netNumber, stopAtPlane);
    return result;
  }

  /** Recursive part of get_connected_set. */
  private void getConnectedSetRecu(Set<Item> result, int netNumber, boolean stopAtPlane) {
    Collection<Item> contactList = getNormalContacts();
    if (contactList == null) {
      return;
    }
    for (Item currentContact : contactList) {
      if (stopAtPlane
          && currentContact instanceof ConductionArea
          && currentContact.getComponentId() <= 0) {
        continue;
      }
      if (netNumber > 0 && !currentContact.containsNet(netNumber)) {
        continue;
      }
      if (result.add(currentContact)) {
        currentContact.getConnectedSetRecu(result, netNumber, stopAtPlane);
      }
    }
  }

  /** Returns true, if this item contains some overlap to be cleaned. */
  public boolean isOverlap() {
    return false;
  }

  /**
   * Recursive part of Trace.is_cycle. If ignoreAreas is true, cycles where conduction areas are
   * involved are ignored.
   */
  boolean isCycleRecu(
      Set<Item> visitedItems, Item searchItem, Item comeFromItem, boolean ignoreAreas) {
    if (ignoreAreas && this instanceof ConductionArea) {
      return false;
    }
    Collection<Item> contactList = getNormalContacts();
    if (contactList == null) {
      return false;
    }
    for (Item currentContact : contactList) {
      if (currentContact == comeFromItem) {
        continue;
      }
      if (currentContact == searchItem) {
        return true;
      }
      if (visitedItems.add(currentContact)) {
        if (currentContact.isCycleRecu(visitedItems, searchItem, this, ignoreAreas)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns the set of all Connectable items belonging to the net with number netNumber, which are
   * not in the connected set of this item. If netNumber {@literal <}= 0, the net numbers contained
   * in this items are used instead of netNumber.
   */
  public Set<Item> getUnconnectedSet(int netNumber) {
    Set<Item> result = new TreeSet<>();
    if (netNumber > 0 && !this.containsNet(netNumber)) {
      return result;
    }
    if (netNumber > 0) {
      result.addAll(board.getConnectableItems(netNumber));
    } else {
      for (int currentNetNumber : this.netNumbers) {
        result.addAll(board.getConnectableItems(currentNetNumber));
      }
    }
    result.removeAll(this.getConnectedSet(netNumber));
    return result;
  }

  /** Returns all traces and vias from this item until the next fork or terminal item. */
  public Set<Item> getConnectionItems() {
    return getConnectionItems(StopConnectionOption.NONE);
  }

  /**
   * Returns all traces and vias from this item until the next fork or terminal item. If stopOption
   * == StopConnectionOption.FANOUT_VIA, the algorithm will stop at the next fanout via, If
   * stopOption == StopConnectionOption.VIA, the algorithm will stop at any via.
   */
  public Set<Item> getConnectionItems(StopConnectionOption stopOption) {
    Set<Item> contacts = this.getNormalContacts();
    Set<Item> result = new TreeSet<>();
    if (this.isRoutable()) {
      result.add(this);
    }
    for (Item currentItem : contacts) {
      Point prevContactPoint = this.normalContactPoint(currentItem);
      if (prevContactPoint == null) {
        // no unique contact point
        continue;
      }
      int prevContactLayer = this.firstCommonLayer(currentItem);
      if (this instanceof Trace startTrace) {
        // Check, that there is only 1 contact at this location.
        // Only for pins and vias items of more than 1 connection
        // are collected
        Collection<Item> checkContacts = startTrace.getNormalContacts(prevContactPoint, false);
        if (checkContacts.size() != 1) {
          continue;
        }
      }
      // Search from currentItem along the contacts
      // until the next fork or nonroute item.
      for (; ; ) {
        if (!currentItem.isRoutable()) {
          // connection ends
          break;
        }
        if (currentItem instanceof Via) {
          if (stopOption == StopConnectionOption.VIA) {
            break;
          }
          if (stopOption == StopConnectionOption.FANOUT_VIA) {
            if (currentItem.isFanoutVia(result)) {
              break;
            }
          }
        }
        result.add(currentItem);
        Collection<Item> currentObContacts = currentItem.getNormalContacts();
        // filter the contacts at the previous contact point,
        // because we were already there.
        // If then there is not exactly 1 new contact left, there is
        // a stub or a fork.
        Point nextContactPoint = null;
        int nextContactLayer = -1;
        Item nextContact = null;
        boolean forkFound = false;
        for (Item tmpContact : currentObContacts) {
          int tmpContactLayer = currentItem.firstCommonLayer(tmpContact);
          if (tmpContactLayer >= 0) {
            Point tmpContactPoint = currentItem.normalContactPoint(tmpContact);
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
        currentItem = nextContact;
        prevContactPoint = nextContactPoint;
        prevContactLayer = nextContactLayer;
      }
    }
    return result;
  }

  /** Function to be overwritten by classes Trace and Via. */
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

  /** Test function checking the item for inconsistencies. */
  public boolean validate() {
    boolean result = board.searchTreeManager.validateEntries(this);
    for (int i = 0; i < this.tileShapeCount(); i++) {
      TileShape currentShape = this.getTileShape(i);
      if (currentShape.isEmpty()) {
        FRLogger.warn("Item.validate: shape is empty");
        result = false;
      }
    }
    return result;
  }

  /**
   * Returns for this item the layer of the shape with index index. If idNo {@literal <}= 0, it will
   * be generated internally.
   */
  @Override
  public abstract int shapeLayer(int index);

  /** Returns true, if it is not allowed to change this item except shoving the item. */
  public boolean isUserFixed() {
    return fixedState.ordinal() >= FixedState.USER_FIXED.ordinal();
  }

  /** Returns true, if it is not allowed to delete this item. */
  boolean isDeletionForbidden() {
    // Items belonging to a component are delete_fixed.
    if (this.componentId > 0 || isUserFixed()) {
      return true;
    }
    // Also power planes are delete_fixed.
    if (this instanceof ConductionArea area) {
      return !this.board.layerStructure.layers[area.getLayer()].isSignal;
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
  public void setFixedState(FixedState fixedState) {
    this.fixedState = fixedState;
  }

  /** Returns false, if this item is an obstacle for vias with the input net number. */
  public boolean isDrillable(int netNumber) {
    return false;
  }

  /** Unfixes the item, if it is not fixed by the system. */
  public void unfix() {
    if (fixedState != FixedState.SYSTEM_FIXED) {
      fixedState = FixedState.UNFIXED;
    }
  }

  /** Returns true, if this item is an unfixed trace or via, so it can be routed by auto-router. */
  public boolean isRoutable() {
    return false;
  }

  /** Returns, if this item can be routed to. */
  public boolean isConnectable() {
    return (this instanceof Connectable) && this.netCount() > 0;
  }

  /** Returns the count of nets this item belongs to. */
  public int netCount() {
    return netNumbers.length;
  }

  /** Returns the no-th net number of this item for 0 {@literal <=} no {@literal <} netCount(). */
  public int getNetNumber(int no) {
    return netNumbers[no];
  }

  /** Return the component ID of this item or 0, if it does not belong to a component. */
  public int getComponentId() {
    return componentId;
  }

  /**
   * Removes netNumber from the net number array. Returns false, if netNumber was not contained in
   * this array.
   */
  public boolean removeFromNet(int netNumber) {
    int foundIndex = -1;
    for (int i = 0; i < this.netNumbers.length; i++) {
      if (this.netNumbers[i] == netNumber) {
        foundIndex = i;
      }
    }
    if (foundIndex < 0) {
      return false;
    }
    int[] newNetNoArr = new int[this.netNumbers.length - 1];
    System.arraycopy(this.netNumbers, 0, newNetNoArr, 0, foundIndex);
    if (foundIndex < newNetNoArr.length) {
      // copy remaining elements if present
      System.arraycopy(
          this.netNumbers,
          foundIndex + 1,
          newNetNoArr,
          foundIndex,
          newNetNoArr.length - foundIndex);
    }
    this.netNumbers = newNetNoArr;
    return true;
  }

  /**
   * Returns the index in the clearance matrix describing the required spacing of this item to other
   * items.
   */
  public int clearanceClassIndex() {
    return clearanceClassIndex;
  }

  /**
   * Sets the index in the clearance matrix describing the required spacing of this item to other
   * items.
   */
  public void setClearanceClassIndex(int index) {
    if (index < 0 || index >= this.board.rules.clearanceMatrix.getClassCount()) {
      FRLogger.warn("Item.set_clearance_class_no: index out of range");
      return;
    }
    clearanceClassIndex = index;
  }

  /** Changes the clearance class of this item and updates the search tree. */
  public void changeClearanceClassIndex(int index) {
    if (index < 0 || index >= this.board.rules.clearanceMatrix.getClassCount()) {
      FRLogger.warn("Item.set_clearance_class_no: index out of range");
      return;
    }
    clearanceClassIndex = index;
    this.clearDerivedData();
    if (this.board != null && this.board.searchTreeManager.isClearanceCompensationUsed()) {
      // reinsert the item into the search tree, because the compensated shape has changed.
      this.board.searchTreeManager.remove(this);
      this.board.searchTreeManager.insert(this);
    }
  }

  /** Assigns this item to the component with the input component ID. */
  public void assignComponentId(int id) {
    componentId = id;
  }

  /**
   * Makes this item connectable and assigns it to the input net. If netNumber {@literal <} 0, the
   * net items net number will be removed and the item will no longer be connectable.
   */
  public void assignNetNo(int netNumber) {
    if (!Nets.isNormalNetNumber(netNumber)) {
      return;
    }
    if (netNumber > board.rules.nets.maxNetNumber()) {
      FRLogger.warn("Item.assign_net_no: netNumber to big");
      return;
    }
    board.itemList.saveForUndo(this);
    if (netNumber <= 0) {
      netNumbers = new int[0];
    } else {
      if (netNumbers.length == 0) {
        netNumbers = new int[1];
      } else if (netNumbers.length > 1) {
        FRLogger.warn("Item.assign_net_no: unexpected netCount > 1");
      }
      netNumbers[0] = netNumber;
    }
  }

  /** Returns true, if item is contained in the input filter. */
  public abstract boolean isSelectedByFilter(ItemSelectionFilter filter);

  /** Internally used for implementing the function is_selected_by_filter. */
  protected boolean isSelectedByFixedFilter(ItemSelectionFilter filter) {
    boolean result;
    if (this.isUserFixed()) {
      result = filter.isSelected(ItemSelectionFilter.SelectableChoices.FIXED);
    } else {
      result = filter.isSelected(ItemSelectionFilter.SelectableChoices.UNFIXED);
    }
    return result;
  }

  /** Sets the item tree entries for the tree with identification number treeNo. */
  @Override
  public void setSearchTreeEntries(ShapeTree.Leaf[] treeEntries, ShapeTree tree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    this.searchTreesInfo.setTreeEntries(treeEntries, tree);
  }

  /**
   * Returns the tree entries for the tree with identification number treeNo, or null, if for this
   * tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] getSearchTreeEntries(ShapeSearchTree tree) {
    if (this.searchTreesInfo == null) {
      return null;
    }
    return this.searchTreesInfo.getTreeEntries(tree);
  }

  /**
   * Sets the precalculated tree shapes tree entries for the tree with identification number treeNo.
   */
  public void setPrecalculatedTreeShapes(TileShape[] shapes, ShapeSearchTree tree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      FRLogger.warn("Item.set_precalculated_tree_shapes searchTreesInfo not allocated");
      return;
    }
    this.searchTreesInfo.setPrecalculatedTreeShapes(shapes, tree);
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

  /** Gets the information for hover event to display. */
  public String getHoverInfo(Locale locale) {
    return "";
  }

  /** Internal function used in the implementation of get_hover_info. */
  public String getConnectableItemHoverInfo(Locale locale) {
    return this.getNetHoverInfo(locale);
  }

  /** Internal function used in the implementation of get_hover_info. */
  public String getNetHoverInfo(Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.netCount(); i++) {
      if (i > 0) {
        sb.append("<br>");
      }
      Net currentNet = board.rules.nets.get(this.getNetNumber(i));
      sb.append(tm.getText("net_hover_info", currentNet.name));
    }
    return sb.toString();
  }

  /** Internal function used in the implementation of print_info. */
  protected void printNetInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    for (int i = 0; i < this.netCount(); i++) {
      printer.append(", " + tm.getText("net") + " ");
      Net currentNet = board.rules.nets.get(this.getNetNumber(i));
      printer.append(currentNet.name, tm.getText("net_info"), currentNet);
    }
  }

  /** Internal function used in the implementation of print_info. */
  protected void printClearanceInfo(ItemInfoPrinter printer, Locale locale) {
    if (this.clearanceClassIndex > 0) {
      TextManager tm = new TextManager(this.getClass(), locale);

      printer.append(", " + tm.getText("clearanceClass") + " ");
      String name = board.rules.clearanceMatrix.getName(this.clearanceClassIndex);
      printer.append(
          name,
          tm.getText("clearance_info"),
          board.rules.clearanceMatrix.getRow(this.clearanceClassIndex));
    }
  }

  /** Internal function used in the implementation of print_info. */
  protected void printFixedInfo(ItemInfoPrinter printer, Locale locale) {
    if (this.fixedState != FixedState.UNFIXED) {
      TextManager tm = new TextManager(this.getClass(), locale);

      printer.append(", ");
      printer.append(tm.getText(this.fixedState.toString()));
    }
  }

  /** Internal function used in the implementation of print_info. */
  protected void printContactInfo(ItemInfoPrinter printer, Locale locale) {
    Collection<Item> contacts = this.getNormalContacts();
    if (!contacts.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), locale);

      printer.append(", " + tm.getText("contacts") + " ");
      int contactCount = contacts.size();
      printer.appendItems(String.valueOf(contactCount), tm.getText("contact_info"), contacts);
    }
  }

  /** Internal function used in the implementation of print_info. */
  protected void printClearanceViolationInfo(ItemInfoPrinter printer, Locale locale) {
    Collection<ClearanceViolation> clearanceViolations = this.clearanceViolations();
    if (!clearanceViolations.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), locale);

      printer.append(", ");
      int violationCount = clearanceViolations.size();
      Collection<ItemInfoPrinter.Printable> violations = new LinkedList<>(clearanceViolations);
      printer.appendObjects(
          String.valueOf(violationCount), tm.getText("violation_info"), violations);
      if (violationCount == 1) {
        printer.append(" " + tm.getText("clearance_violation"));
      } else {
        printer.append(" " + tm.getText("clearanceViolations"));
      }
    }
  }

  /** Internal function used in the implementation of print_info. */
  protected void printConnectableItemInfo(ItemInfoPrinter printer, Locale locale) {
    this.printClearanceInfo(printer, locale);
    this.printFixedInfo(printer, locale);
    this.printNetInfo(printer, locale);
    this.printContactInfo(printer, locale);
    this.printClearanceViolationInfo(printer, locale);
  }

  /** Internal function used in the implementation of print_info. */
  protected void printItemInfo(ItemInfoPrinter printer, Locale locale) {
    this.printClearanceInfo(printer, locale);
    this.printFixedInfo(printer, locale);
    this.printClearanceViolationInfo(printer, locale);
  }

  /** Checks, if all nets of this items are normal. */
  public boolean netsNormal() {
    for (int i = 0; i < this.netNumbers.length; i++) {
      if (!Nets.isNormalNetNumber(this.netNumbers[i])) {
        return false;
      }
    }
    return true;
  }

  /** Checks, if this item and other contain exactly the same net numbers. */
  public boolean netsEqual(Item other) {
    return netsEqual(other.netNumbers);
  }

  /** Checks, if this item contains exactly the nets in netNumbers. */
  public boolean netsEqual(int[] netNumbers) {
    if (this.netNumbers.length != netNumbers.length) {
      return false;
    }
    for (int currentNetNumber : netNumbers) {
      if (!this.containsNet(currentNetNumber)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true, if the via is directly ob by a trace connected to a nearby SMD-pin. If
   * ignoreItems != null, contact traces in P-ignoreItems are ignored.
   */
  boolean isFanoutVia(Set<Item> ignoreItems) {
    Collection<Item> contactList = this.getNormalContacts();
    for (Item currentContact : contactList) {
      if (currentContact instanceof Pin
          && currentContact.firstLayer() == currentContact.lastLayer()
          && currentContact.getNormalContacts().size() <= 1) {
        return true;
      }
      if (currentContact instanceof Trace currentTrace) {
        if (ignoreItems != null && ignoreItems.contains(currentContact)) {
          continue;
        }
        if (currentTrace.getLength() >= PROTECT_FANOUT_LENGTH * currentTrace.getHalfWidth()) {
          continue;
        }
        Collection<Item> traceContactList = currentTrace.getNormalContacts();
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
   * Checks if the item has a net that must be ignored by the auto-router.
   *
   * @return true, if this item has at least one net that must be ignored
   */
  public boolean hasIgnoredNets() {
    for (int netNumber : this.netNumbers) {
      Net net = this.board.rules.nets.get(netNumber);
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

    if (componentId > 0) {
      simpleName.append(" of component #");
      simpleName.append(componentId);
    }

    return simpleName.toString();
  }

  /** GetAllNets. */
  public List<Net> getAllNets() {
    List<Net> nets = new ArrayList<>();
    for (int netNumber : this.netNumbers) {
      Net net = board.rules.nets.get(netNumber);
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
