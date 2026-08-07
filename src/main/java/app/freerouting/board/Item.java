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
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_component_no,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    if (p_net_no_arr == null) {
      netNoArr = new int[0];
    } else {
      netNoArr = new int[p_net_no_arr.length];
      System.arraycopy(p_net_no_arr, 0, netNoArr, 0, p_net_no_arr.length);
    }
    clearanceClass = p_clearance_type;
    componentNo = p_component_no;
    fixedState = p_fixed_state;
    board = p_board;
    if (p_id_no <= 0) {
      idNo = board.communication.idNoGenerator.new_no();
    } else {
      idNo = p_id_no;
    }
  }

  /** Implements the comparable interface. */
  @Override
  public int compareTo(Object p_other) {
    int result;
    if (p_other instanceof Item item) {
      result = item.idNo - idNo;
    } else {
      result = 1;
    }
    return result;
  }

  /** returns the unique identification number of this item */
  public int get_id_no() {
    return idNo;
  }

  /** Returns true if the net number array of this item contains p_net_no. */
  public boolean contains_net(int p_net_no) {
    if (p_net_no <= 0) {
      return false;
    }
    for (int i = 0; i < netNoArr.length; i++) {
      if (netNoArr[i] == p_net_no) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean is_obstacle(int p_net_no) {
    return !contains_net(p_net_no);
  }

  @Override
  public boolean is_trace_obstacle(int p_net_no) {
    return !contains_net(p_net_no);
  }

  /** Returns, if this item in not allowed to overlap with p_other. */
  public abstract boolean is_obstacle(Item p_other);

  /** Returns true if the net number arrays of this and p_other have a common number. */
  public boolean shares_net(Item p_other) {
    return this.shares_net_no(p_other.netNoArr);
  }

  /** Returns true if the net number array of this and p_net_no_arr have a common number. */
  public boolean shares_net_no(int[] p_net_no_arr) {
    for (int i = 0; i < netNoArr.length; i++) {
      for (int j = 0; j < p_net_no_arr.length; j++) {
        if (netNoArr[i] == p_net_no_arr[j]) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the number of shapes of this item after decomposition into convex polygonal shapes */
  public abstract int tile_shape_count();

  /**
   * Returns the p_index-throws shape of this item after decomposition into convex polygonal shapes
   */
  public TileShape get_tile_shape(int p_index) {
    if (this.board == null) {
      FRLogger.warn("Item.get_tile_shape: app.freerouting.board is null");
      return null;
    }
    return get_tree_shape(this.board.searchTreeManager.get_default_tree(), p_index);
  }

  @Override
  public int tree_shape_count(ShapeTree p_tree) {
    if (this.board == null) {
      return 0;
    }
    TileShape[] precalculatedTreeShapes = this.get_precalculated_tree_shapes(p_tree);
    return precalculatedTreeShapes.length;
  }

  @Override
  public TileShape get_tree_shape(ShapeTree p_tree, int p_index) {
    if (this.board == null) {
      return null;
    }
    TileShape[] precalculatedTreeShapes = this.get_precalculated_tree_shapes(p_tree);
    if (precalculatedTreeShapes == null
        || p_index < 0
        || p_index >= precalculatedTreeShapes.length) {
      this.clear_derived_data();
      precalculatedTreeShapes = this.get_precalculated_tree_shapes(p_tree);
    }
    if (precalculatedTreeShapes == null
        || p_index < 0
        || p_index >= precalculatedTreeShapes.length) {
      return null;
    }
    return precalculatedTreeShapes[p_index];
  }

  private TileShape[] get_precalculated_tree_shapes(ShapeTree p_tree) {
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    TileShape[] precalculatedTreeShapes =
        this.searchTreesInfo.get_precalculated_tree_shapes(p_tree);
    if (precalculatedTreeShapes == null) {
      precalculatedTreeShapes = this.calculate_tree_shapes((ShapeSearchTree) p_tree);
      this.searchTreesInfo.set_precalculated_tree_shapes(precalculatedTreeShapes, p_tree);
    }
    return precalculatedTreeShapes;
  }

  /** Calculates the tree shapes for this item for p_search_tree. */
  protected abstract TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree);

  /** Returns false, if this item is deleted oor not inserted into the board. */
  public boolean is_on_the_board() {
    return this.onTheBoard;
  }

  void set_on_the_board(boolean p_value) {
    this.onTheBoard = p_value;
  }

  /**
   * Creates a copy of this item with id number p_id_no. If p_id_no {@literal <}= 0, the idNo of the
   * new item is generated internally
   */
  public abstract Item copy(int p_id_no);

  @Override
  public Object clone() {
    Item dup = copy(this.get_id_no());

    dup.onTheBoard = this.onTheBoard;
    // dup.searchTreesInfo = this.searchTreesInfo;

    return dup;
  }

  /** returns true, if the layer range of this item contains p_layer */
  public abstract boolean is_on_layer(int p_layer);

  /** Returns the number of the first layer containing geometry of this item. */
  public abstract int first_layer();

  /** Returns the number of the last layer containing geometry of this item. */
  public abstract int last_layer();

  /** write this item to an output stream */
  public abstract boolean write(ObjectOutputStream p_stream);

  /** Translates the shapes of this item by p_vector. Does not move the item in the board. */
  public abstract void translate_by(Vector p_vector);

  /**
   * Turns this Item by p_factor times 90 degree around p_pole. Does not update the item in the
   * board.
   */
  public abstract void turn_90_degree(int p_factor, IntPoint p_pole);

  /**
   * Rotates this Item by p_angle_in_degree around p_pole. Does not update the item in the board.
   */
  public abstract void rotate_approx(double p_angle_in_degree, FloatPoint p_pole);

  /**
   * Changes the placement side of this Item and mirrors it at the vertical line through p_pole.
   * Does not update the item in the board.
   */
  public abstract void change_placement_side(IntPoint p_pole);

  /** Returns a box containing the geometry of this item. */
  public abstract IntBox bounding_box();

  /** Translates this item by p_vector in the board. */
  public void move_by(Vector p_vector) {
    board.itemList.save_for_undo(this);
    board.searchTreeManager.remove(this);
    this.translate_by(p_vector);
    board.searchTreeManager.insert(this);

    // let the observers synchronize the changes
    if ((board.communication != null) && (board.communication.observers != null)) {
      board.communication.observers.notify_changed(this);
    }
  }

  /** Returns true, if some shapes of this item and p_other are on the same layer. */
  public boolean shares_layer(Item p_other) {
    int maxFirstLayer = Math.max(this.first_layer(), p_other.first_layer());
    int minLastLayer = Math.min(this.last_layer(), p_other.last_layer());
    return maxFirstLayer <= minLastLayer;
  }

  /**
   * Returns the first layer, where both this item and p_other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int first_common_layer(Item p_other) {
    int maxFirstLayer = Math.max(this.first_layer(), p_other.first_layer());
    int minLastLayer = Math.min(this.last_layer(), p_other.last_layer());
    if (maxFirstLayer > minLastLayer) {
      return -1;
    }
    return maxFirstLayer;
  }

  /**
   * Returns the last layer, where both this item and p_other have a shape. Returns -1, if such a
   * layer does not exist.
   */
  public int last_common_layer(Item p_other) {
    int maxFirstLayer = Math.max(this.first_layer(), p_other.first_layer());
    int minLastLayer = Math.min(this.last_layer(), p_other.last_layer());
    if (maxFirstLayer > minLastLayer) {
      return -1;
    }
    return minLastLayer;
  }

  /**
   * Return the name of the component of this item or null, if this item does not belong to a
   * component.
   */
  public String component_name() {
    if (componentNo <= 0) {
      return null;
    }
    return board.components.get(componentNo).name;
  }

  /** Returns the count of clearance violations of this item with other items. */
  public int clearance_violation_count() {
    Collection<ClearanceViolation> violations = this.clearance_violations();
    return violations.size();
  }

  /**
   * Returns a list of all clearance violations of this item with other items. The objects in the
   * list are of type ClearanceViolations. The firstItem in such an object is always this item.
   */
  public Collection<ClearanceViolation> clearance_violations() {
    Collection<ClearanceViolation> result = new LinkedList<>();
    if (this.board == null) {
      return result;
    }
    ShapeSearchTree defaultTree = board.searchTreeManager.get_default_tree();
    for (int i = 0; i < tile_shape_count(); i++) {
      TileShape currTileShape = get_tile_shape(i);
      Collection<TreeEntry> currOverlappingItems =
          defaultTree.overlapping_tree_entries_with_clearance(
              currTileShape, shape_layer(i), new int[0], clearanceClass);
      for (TreeEntry currEntry : currOverlappingItems) {
        if (!(currEntry.object instanceof Item currItem) || currEntry.object == this) {
          continue;
        }
        boolean isObstacle = currItem.is_obstacle(this);
        if (isObstacle && this instanceof Trace this_trace && currItem instanceof Trace) {
          // Look, if both traces are connected to the same tie pin.
          // In this case they are allowed to overlap without sharing a net.
          Point contactPoint = this_trace.first_corner();
          boolean contactFound = false;
          Collection<Item> currContacts = this_trace.get_normal_contacts(contactPoint, true);
          {
            if (currContacts.contains(currItem)) {
              contactFound = true;
            }
          }
          if (!contactFound) {
            contactPoint = this_trace.last_corner();
            currContacts = this_trace.get_normal_contacts(contactPoint, true);
            {
              if (currContacts.contains(currItem)) {
                contactFound = true;
              }
            }
          }
          if (contactFound) {
            for (Item currContact : currContacts) {
              if (currContact instanceof Pin) {
                if (currContact.shares_net(this) && currContact.shares_net(currItem)) {
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
          TileShape shape2 = currItem.get_tree_shape(defaultTree, currEntry.shapeIndexInObject);
          if (shape1 == null || shape2 == null) {
            FRLogger.warn("Item.clearanceViolations: unexpected null shape");
            continue;
          }

          // Calculate the expected minimum clearance between these two shapes
          double minimumClearance =
              board.rules.clearanceMatrix.get_value(
                  currItem.clearanceClass, this.clearanceClass, shape_layer(i), false);

          double actualClearance = 0;

          TileShape enlargedShape1 = (TileShape) shape1.enlarge(0);
          TileShape enlargedShape2 = (TileShape) shape2.enlarge(0);

          if (!this.board.searchTreeManager.is_clearance_compensation_used()) {
            double clOffset = 0.5 * minimumClearance;
            enlargedShape1 = (TileShape) shape1.enlarge(clOffset);
            enlargedShape2 = (TileShape) shape2.enlarge(clOffset);

            actualClearance =
                calculate_clearance_between_two_shapes(
                    shape1, shape2, minimumClearance + ClearanceMatrix.clearance_safety_margin);
            if ((smallestClearance == 0) || (actualClearance < smallestClearance)) {
              smallestClearance = actualClearance;
            }
          }

          TileShape intersection = enlargedShape1.intersection(enlargedShape2);
          if (intersection.dimension() == 2) {
            ClearanceViolation currViolation =
                new ClearanceViolation(
                    this,
                    currItem,
                    intersection,
                    shape_layer(i),
                    minimumClearance,
                    actualClearance);
            result.add(currViolation);
          }
        }
      }
    }
    return result;
  }

  private double calculate_clearance_between_two_shapes(
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
  public Set<Item> get_all_contacts() {
    Set<Item> result = new TreeSet<>();
    if (!(this instanceof Connectable)) {
      return result;
    }
    for (int i = 0; i < this.tile_shape_count(); i++) {
      Collection<SearchTreeObject> overlappingItems =
          board.overlapping_objects(get_tile_shape(i), shape_layer(i));
      for (SearchTreeObject currOb : overlappingItems) {
        if (!(currOb instanceof Item currItem)) {
          continue;
        }
        if (currItem != this && currItem instanceof Connectable && currItem.shares_net(this)) {
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
  public Set<Item> get_all_contacts(int p_layer) {
    Set<Item> result = new TreeSet<>();
    if (!(this instanceof Connectable)) {
      return result;
    }
    for (int i = 0; i < this.tile_shape_count(); i++) {
      if (this.shape_layer(i) != p_layer) {
        continue;
      }
      Collection<SearchTreeObject> overlappingItems =
          board.overlapping_objects(get_tile_shape(i), p_layer);
      for (SearchTreeObject currOb : overlappingItems) {
        if (!(currOb instanceof Item currItem)) {
          continue;
        }
        if (currItem != this && currItem instanceof Connectable && currItem.shares_net(this)) {
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
  public boolean is_connected() {
    Collection<Item> contacts = this.get_all_contacts();
    return !contacts.isEmpty();
  }

  /**
   * Checks, if this item is electrically connected to another connectable item on the input layer.
   * Returns false for items, which are not connectable.
   */
  public boolean is_connected_on_layer(int p_layer) {
    Collection<Item> contactsOnLayer = this.get_all_contacts(p_layer);
    return !contactsOnLayer.isEmpty();
  }

  /** default implementation to be overwritten in the Connectable subclasses */
  public Set<Item> get_normal_contacts() {
    return new TreeSet<>();
  }

  /**
   * Returns the contact point, if this item and p_other are Connectable and have a unique normal
   * contact. Returns null otherwise
   */
  public Point normal_contact_point(Item p_other) {
    return null;
  }

  /** auxiliary function */
  Point normal_contact_point(Trace p_other) {
    return null;
  }

  /** auxiliary function */
  Point normal_contact_point(DrillItem p_other) {
    return null;
  }

  /**
   * Returns the set of all Connectable items of the net with number p_net_no which can be reached
   * recursively via normal contacts from this item. If p_net_no {@literal <}= 0, the net number is
   * ignored.
   */
  public Set<Item> get_connected_set(int p_net_no) {
    return get_connected_set(p_net_no, false);
  }

  /**
   * Returns the set of all Connectable items of the net with number p_net_no which can be reached
   * recursively via normal contacts from this item. If p_net_no {@literal <}= 0, the net number is
   * ignored. If p_stop_at_plane, the recursive algorithm stops, when a conduction area is reached,
   * which does not belong to a component.
   */
  public Set<Item> get_connected_set(int p_net_no, boolean p_stop_at_plane) {
    Set<Item> result = new TreeSet<>();
    if (p_net_no > 0 && !this.contains_net(p_net_no)) {
      return result;
    }
    result.add(this);
    get_connected_set_recu(result, p_net_no, p_stop_at_plane);
    return result;
  }

  /** recursive part of get_connected_set */
  private void get_connected_set_recu(Set<Item> p_result, int p_net_no, boolean p_stop_at_plane) {
    Collection<Item> contactList = get_normal_contacts();
    if (contactList == null) {
      return;
    }
    for (Item currContact : contactList) {
      if (p_stop_at_plane
          && currContact instanceof ConductionArea
          && currContact.get_component_no() <= 0) {
        continue;
      }
      if (p_net_no > 0 && !currContact.contains_net(p_net_no)) {
        continue;
      }
      if (p_result.add(currContact)) {
        currContact.get_connected_set_recu(p_result, p_net_no, p_stop_at_plane);
      }
    }
  }

  /** Returns true, if this item contains some overlap to be cleaned. */
  public boolean is_overlap() {
    return false;
  }

  /**
   * Recursive part of Trace.is_cycle. If p_ignore_areas is true, cycles where conduction areas are
   * involved are ignored.
   */
  boolean is_cycle_recu(
      Set<Item> p_visited_items,
      Item p_search_item,
      Item p_come_from_item,
      boolean p_ignore_areas) {
    if (p_ignore_areas && this instanceof ConductionArea) {
      return false;
    }
    Collection<Item> contactList = get_normal_contacts();
    if (contactList == null) {
      return false;
    }
    for (Item currContact : contactList) {
      if (currContact == p_come_from_item) {
        continue;
      }
      if (currContact == p_search_item) {
        return true;
      }
      if (p_visited_items.add(currContact)) {
        if (currContact.is_cycle_recu(p_visited_items, p_search_item, this, p_ignore_areas)) {
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
  public Set<Item> get_unconnected_set(int p_net_no) {
    Set<Item> result = new TreeSet<>();
    if (p_net_no > 0 && !this.contains_net(p_net_no)) {
      return result;
    }
    if (p_net_no > 0) {
      result.addAll(board.get_connectable_items(p_net_no));
    } else {
      for (int currNetNo : this.netNoArr) {
        result.addAll(board.get_connectable_items(currNetNo));
      }
    }
    result.removeAll(this.get_connected_set(p_net_no));
    return result;
  }

  /** Returns all traces and vias from this item until the next fork or terminal item. */
  public Set<Item> get_connection_items() {
    return get_connection_items(StopConnectionOption.NONE);
  }

  /**
   * Returns all traces and vias from this item until the next fork or terminal item. If
   * p_stop_option == StopConnectionOption.FANOUT_VIA, the algorithm will stop at the next fanout
   * via, If p_stop_option == StopConnectionOption.VIA, the algorithm will stop at any via.
   */
  public Set<Item> get_connection_items(StopConnectionOption p_stop_option) {
    Set<Item> contacts = this.get_normal_contacts();
    Set<Item> result = new TreeSet<>();
    if (this.is_routable()) {
      result.add(this);
    }
    for (Item currItem : contacts) {
      Point prevContactPoint = this.normal_contact_point(currItem);
      if (prevContactPoint == null) {
        // no unique contact point
        continue;
      }
      int prevContactLayer = this.first_common_layer(currItem);
      if (this instanceof Trace start_trace) {
        // Check, that there is only 1 contact at this location.
        // Only for pins and vias items of more than 1 connection
        // are collected
        Collection<Item> checkContacts = start_trace.get_normal_contacts(prevContactPoint, false);
        if (checkContacts.size() != 1) {
          continue;
        }
      }
      // Search from currItem along the contacts
      // until the next fork or nonroute item.
      for (; ; ) {
        if (!currItem.is_routable()) {
          // connection ends
          break;
        }
        if (currItem instanceof Via) {
          if (p_stop_option == StopConnectionOption.VIA) {
            break;
          }
          if (p_stop_option == StopConnectionOption.FANOUT_VIA) {
            if (currItem.is_fanout_via(result)) {
              break;
            }
          }
        }
        result.add(currItem);
        Collection<Item> currObContacts = currItem.get_normal_contacts();
        // filter the contacts at the previous contact point,
        // because we were already there.
        // If then there is not exactly 1 new contact left, there is
        // a stub or a fork.
        Point nextContactPoint = null;
        int nextContactLayer = -1;
        Item nextContact = null;
        boolean forkFound = false;
        for (Item tmp_contact : currObContacts) {
          int tmpContactLayer = currItem.first_common_layer(tmp_contact);
          if (tmpContactLayer >= 0) {
            Point tmpContactPoint = currItem.normal_contact_point(tmp_contact);
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
              nextContact = tmp_contact;
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
  public boolean is_tail() {
    return false;
  }

  /**
   * Returns all corners of this item, which are used for displaying the ratsnest. To be overwritten
   * in derived classes implementing the Connectable interface.
   */
  public Point[] get_ratsnest_corners() {
    return new Point[0];
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color p_color, double p_intensity) {
    Color[] colorArr = new Color[board.get_layer_count()];
    Arrays.fill(colorArr, p_color);
    draw(p_g, p_graphics_context, colorArr, p_intensity);
  }

  /**
   * Draws this item with its draw colors from p_graphics_context. p_layer_visibility[i] is expected
   * between 0 and 1 for each layer i.
   */
  public void draw(Graphics p_g, GraphicsContext p_graphics_context) {
    Color[] layerColors = get_draw_colors(p_graphics_context);
    draw(p_g, p_graphics_context, layerColors, get_draw_intensity(p_graphics_context));
  }

  /** Draws this item on a specific layer only, with its draw colors from p_graphics_context. */
  public void draw_layer(Graphics p_g, GraphicsContext p_graphics_context, int p_layer_no) {
    if (this.is_on_layer(p_layer_no)) {
      Color[] layerColors = get_draw_colors(p_graphics_context);
      draw_layer(
          p_g, p_graphics_context, layerColors, get_draw_intensity(p_graphics_context), p_layer_no);
    }
  }

  /** Draws this item on a specific layer only. */
  public void draw_layer(
      Graphics p_g,
      GraphicsContext p_graphics_context,
      Color[] p_color_arr,
      double p_intensity,
      int p_layer_no) {
    if (this.is_on_layer(p_layer_no)) {
      draw(p_g, p_graphics_context, p_color_arr, p_intensity);
    }
  }

  /** Test function checking the item for inconsistencies. */
  public boolean validate() {
    boolean result = board.searchTreeManager.validate_entries(this);
    for (int i = 0; i < this.tile_shape_count(); i++) {
      TileShape currShape = this.get_tile_shape(i);
      if (currShape.is_empty()) {
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
  public abstract int shape_layer(int p_index);

  /** Returns true, if it is not allowed to change this item except shoving the item */
  public boolean is_user_fixed() {
    return fixedState.ordinal() >= FixedState.USER_FIXED.ordinal();
  }

  /** Returns true, if it is not allowed to delete this item. */
  boolean isDeletionForbidden() {
    // Items belonging to a component are delete_fixed.
    if (this.componentNo > 0 || is_user_fixed()) {
      return true;
    }
    // Also power planes are delete_fixed.
    if (this instanceof ConductionArea area) {
      return !this.board.layerStructure.arr[area.get_layer()].isSignal;
    }
    return false;
  }

  /**
   * Returns true, if it is not allowed to change the location of this item by the push algorithm.
   */
  public boolean is_shove_fixed() {
    return this.fixedState.ordinal() >= FixedState.SHOVE_FIXED.ordinal();
  }

  /** Returns the fixed state of this Item. */
  public FixedState get_fixed_state() {
    return this.fixedState;
  }

  /** Fixes the item. */
  public void set_fixed_state(FixedState p_fixed_state) {
    fixedState = p_fixed_state;
  }

  /** Returns false, if this item is an obstacle for vias with the input net number. */
  public boolean is_drillable(int p_net_no) {
    return false;
  }

  /** Unfixes the item, if it is not fixed by the system. */
  public void unfix() {
    if (fixedState != FixedState.SYSTEM_FIXED) {
      fixedState = FixedState.UNFIXED;
    }
  }

  /** returns true, if this item is an unfixed trace or via, so it can be routed by auto-router */
  public boolean is_routable() {
    return false;
  }

  /** Returns, if this item can be routed to. */
  public boolean is_connectable() {
    return (this instanceof Connectable) && this.net_count() > 0;
  }

  /** Returns the count of nets this item belongs to. */
  public int net_count() {
    return netNoArr.length;
  }

  /**
   * gets the p_no-th net number of this item for 0 {@literal <}= p_no {@literal <}
   * this.net_count().
   */
  public int get_net_no(int p_no) {
    return netNoArr[p_no];
  }

  /** Return the component number of this item or 0, if it does not belong to a component. */
  public int get_component_no() {
    return componentNo;
  }

  /**
   * Removes p_net_no from the net number array. Returns false, if p_net_no was not contained in
   * this array.
   */
  public boolean remove_from_net(int p_net_no) {
    int foundIndex = -1;
    for (int i = 0; i < this.netNoArr.length; i++) {
      if (this.netNoArr[i] == p_net_no) {
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
  public int clearance_class_no() {
    return clearanceClass;
  }

  /**
   * Sets the index in the clearance matrix describing the required spacing of this item to other
   * items.
   */
  public void set_clearance_class_no(int p_index) {
    if (p_index < 0 || p_index >= this.board.rules.clearanceMatrix.get_class_count()) {
      FRLogger.warn("Item.set_clearance_class_no: p_index out of range");
      return;
    }
    clearanceClass = p_index;
  }

  /** Changes the clearance class of this item and updates the search tree. */
  public void change_clearance_class(int p_index) {
    if (p_index < 0 || p_index >= this.board.rules.clearanceMatrix.get_class_count()) {
      FRLogger.warn("Item.set_clearance_class_no: p_index out of range");
      return;
    }
    clearanceClass = p_index;
    this.clear_derived_data();
    if (this.board != null && this.board.searchTreeManager.is_clearance_compensation_used()) {
      // reinsert the item into the search tree, because the compensated shape has changed.
      this.board.searchTreeManager.remove(this);
      this.board.searchTreeManager.insert(this);
    }
  }

  /** Assigns this item to the component with the input component number. */
  public void assign_component_no(int p_no) {
    componentNo = p_no;
  }

  /**
   * Makes this item connectable and assigns it to the input net. If p_net_no {@literal <} 0, the
   * net items net number will be removed and the item will no longer be connectable.
   */
  public void assign_net_no(int p_net_no) {
    if (!Nets.is_normal_net_no(p_net_no)) {
      return;
    }
    if (p_net_no > board.rules.nets.max_net_no()) {
      FRLogger.warn("Item.assign_net_no: p_net_no to big");
      return;
    }
    board.itemList.save_for_undo(this);
    if (p_net_no <= 0) {
      netNoArr = new int[0];
    } else {
      if (netNoArr.length == 0) {
        netNoArr = new int[1];
      } else if (netNoArr.length > 1) {
        FRLogger.warn("Item.assign_net_no: unexpected netCount > 1");
      }
      netNoArr[0] = p_net_no;
    }
  }

  /** Returns true, if p_item is contained in the input filter. */
  public abstract boolean is_selected_by_filter(ItemSelectionFilter p_filter);

  /** Internally used for implementing the function is_selected_by_filter */
  protected boolean is_selected_by_fixed_filter(ItemSelectionFilter p_filter) {
    boolean result;
    if (this.is_user_fixed()) {
      result = p_filter.is_selected(ItemSelectionFilter.SelectableChoices.FIXED);
    } else {
      result = p_filter.is_selected(ItemSelectionFilter.SelectableChoices.UNFIXED);
    }
    return result;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  @Override
  public void set_search_tree_entries(ShapeTree.Leaf[] p_tree_entries, ShapeTree p_tree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      this.searchTreesInfo = new ItemSearchTreesInfo();
    }
    this.searchTreesInfo.set_tree_entries(p_tree_entries, p_tree);
  }

  /**
   * Returns the tree entries for the tree with identification number p_tree_no, or null, if for
   * this tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] get_search_tree_entries(ShapeSearchTree p_tree) {
    if (this.searchTreesInfo == null) {
      return null;
    }
    return this.searchTreesInfo.get_tree_entries(p_tree);
  }

  /**
   * Sets the precalculated tree shapes tree entries for the tree with identification number
   * p_tree_no.
   */
  protected void set_precalculated_tree_shapes(TileShape[] p_shapes, ShapeSearchTree p_tree) {
    if (this.board == null) {
      return;
    }
    if (this.searchTreesInfo == null) {
      FRLogger.warn("Item.set_precalculated_tree_shapes searchTreesInfo not allocated");
      return;
    }
    this.searchTreesInfo.set_precalculated_tree_shapes(p_shapes, p_tree);
  }

  /** Sets the search tree entries of this item to null. */
  public void clear_search_tree_entries() {
    this.searchTreesInfo = null;
  }

  /** Gets the information for the autoroute algorithm. Creates it, if it does not yet exist. */
  public ItemAutorouteInfo get_autoroute_info() {
    if (autorouteInfo == null) {
      autorouteInfo = new ItemAutorouteInfo(this);
    }
    return autorouteInfo;
  }

  /** Gets the information for the autoroute algorithm. */
  public ItemAutorouteInfo get_autoroute_info_pur() {
    return autorouteInfo;
  }

  /** Clears the data allocated for the autoroute algorithm. */
  public void clear_autoroute_info() {
    autorouteInfo = null;
  }

  /**
   * Clear all cached or derived data. so that they have to be recalculated, when they are used next
   * time.
   */
  public void clear_derived_data() {
    if (this.searchTreesInfo != null) {
      this.searchTreesInfo.clear_precalculated_tree_shapes();
    }
    autorouteInfo = null;
  }

  /** Gets the information for hover event to display */
  public String get_hover_info(Locale p_locale) {
    return "";
  }

  /** Internal function used in the implementation of get_hover_info */
  public String get_connectable_item_hover_info(Locale p_locale) {
    return this.get_net_hover_info(p_locale);
  }

  /** Internal function used in the implementation of get_hover_info */
  public String get_net_hover_info(Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.net_count(); i++) {
      if (i > 0) {
        sb.append("<br>");
      }
      Net currNet = board.rules.nets.get(this.get_net_no(i));
      sb.append(tm.getText("net_hover_info", currNet.name));
    }
    return sb.toString();
  }

  /** Internal function used in the implementation of print_info */
  protected void print_net_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    for (int i = 0; i < this.net_count(); i++) {
      p_window.append(", " + tm.getText("net") + " ");
      Net currNet = board.rules.nets.get(this.get_net_no(i));
      p_window.append(currNet.name, tm.getText("net_info"), currNet);
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void print_clearance_info(ObjectInfoPanel p_window, Locale p_locale) {
    if (this.clearanceClass > 0) {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append(", " + tm.getText("clearanceClass") + " ");
      String name = board.rules.clearanceMatrix.get_name(this.clearanceClass);
      p_window.append(
          name,
          tm.getText("clearance_info"),
          board.rules.clearanceMatrix.get_row(this.clearanceClass));
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void print_fixed_info(ObjectInfoPanel p_window, Locale p_locale) {
    if (this.fixedState != FixedState.UNFIXED) {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append(", ");
      p_window.append(tm.getText(this.fixedState.toString()));
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void print_contact_info(ObjectInfoPanel p_window, Locale p_locale) {
    Collection<Item> contacts = this.get_normal_contacts();
    if (!contacts.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append(", " + tm.getText("contacts") + " ");
      int contactCount = contacts.size();
      p_window.append_items(String.valueOf(contactCount), tm.getText("contact_info"), contacts);
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void print_clearance_violation_info(ObjectInfoPanel p_window, Locale p_locale) {
    Collection<ClearanceViolation> clearanceViolations = this.clearance_violations();
    if (!clearanceViolations.isEmpty()) {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append(", ");
      int violationCount = clearanceViolations.size();
      Collection<ObjectInfoPanel.Printable> violations = new LinkedList<>(clearanceViolations);
      p_window.append_objects(
          String.valueOf(violationCount), tm.getText("violation_info"), violations);
      if (violationCount == 1) {
        p_window.append(" " + tm.getText("clearance_violation"));
      } else {
        p_window.append(" " + tm.getText("clearanceViolations"));
      }
    }
  }

  /** Internal function used in the implementation of print_info */
  protected void print_connectable_item_info(ObjectInfoPanel p_window, Locale p_locale) {
    this.print_clearance_info(p_window, p_locale);
    this.print_fixed_info(p_window, p_locale);
    this.print_net_info(p_window, p_locale);
    this.print_contact_info(p_window, p_locale);
    this.print_clearance_violation_info(p_window, p_locale);
  }

  /** Internal function used in the implementation of print_info */
  protected void print_item_info(ObjectInfoPanel p_window, Locale p_locale) {
    this.print_clearance_info(p_window, p_locale);
    this.print_fixed_info(p_window, p_locale);
    this.print_clearance_violation_info(p_window, p_locale);
  }

  /** Checks, if all nets of this items are normal. */
  public boolean nets_normal() {
    for (int i = 0; i < this.netNoArr.length; i++) {
      if (!Nets.is_normal_net_no(this.netNoArr[i])) {
        return false;
      }
    }
    return true;
  }

  /** Checks, if this item and p_other contain exactly the same net numbers. */
  public boolean nets_equal(Item p_other) {
    return nets_equal(p_other.netNoArr);
  }

  /** Checks, if this item contains exactly the nets in p_net_no_arr */
  public boolean nets_equal(int[] p_net_no_arr) {
    if (this.netNoArr.length != p_net_no_arr.length) {
      return false;
    }
    for (int currNetNo : p_net_no_arr) {
      if (!this.contains_net(currNetNo)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true, if the via is directly ob by a trace connected to a nearby SMD-pin. If
   * p_ignore_items != null, contact traces in P-ignoreItems are ignored.
   */
  boolean is_fanout_via(Set<Item> p_ignore_items) {
    Collection<Item> contactList = this.get_normal_contacts();
    for (Item currContact : contactList) {
      if (currContact instanceof Pin
          && currContact.first_layer() == currContact.last_layer()
          && currContact.get_normal_contacts().size() <= 1) {
        return true;
      }
      if (currContact instanceof Trace currTrace) {
        if (p_ignore_items != null && p_ignore_items.contains(currContact)) {
          continue;
        }
        if (currTrace.get_length() >= PROTECT_FANOUT_LENGTH * currTrace.get_half_width()) {
          continue;
        }
        Collection<Item> traceContactList = currTrace.get_normal_contacts();
        for (Item tmp_contact : traceContactList) {
          if (tmp_contact instanceof Pin
              && tmp_contact.first_layer() == tmp_contact.last_layer()
              && tmp_contact.get_normal_contacts().size() <= 1) {
            return true;
          }
          if (tmp_contact instanceof PolylineTrace contactTrace
              && tmp_contact.get_fixed_state() == FixedState.SHOVE_FIXED) {
            // look for shove fixed exit traces of SMD-pins
            if (contactTrace.corner_count() == 2) {
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
  public boolean has_ignored_nets() {
    for (int netNo : this.netNoArr) {
      Net net = this.board.rules.nets.get(netNo);
      if (net.get_class().isIgnoredByAutorouter) {
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
