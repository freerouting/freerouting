package app.freerouting.board;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.BoardLibrary;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import java.awt.Graphics;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Provides the fundamental data structure and operations for a printed circuit board.
 *
 * <p>This class serves as the foundation for board management, providing essential functionality for:
 * <ul>
 *   <li>Managing board geometry and layer structure</li>
 *   <li>Inserting, removing, and modifying board items (traces, vias, pins, obstacles, etc.)</li>
 *   <li>Maintaining search trees for efficient spatial queries</li>
 *   <li>Checking clearance and geometric constraints</li>
 *   <li>Picking items at specific locations</li>
 *   <li>Managing undo/redo operations</li>
 *   <li>Drawing board contents to graphics contexts</li>
 *   <li>Tracking components and their relationships</li>
 *   <li>Computing board statistics and metrics</li>
 * </ul>
 *
 * <p>The board maintains several key data structures:
 * <ul>
 *   <li>An item list containing all board objects (traces, vias, pins, obstacles, etc.)</li>
 *   <li>Search trees for efficient spatial queries and overlap detection</li>
 *   <li>A component registry for managing placed components</li>
 *   <li>Design rules including clearance matrices and net definitions</li>
 *   <li>A library of padstacks, packages, and other design templates</li>
 * </ul>
 *
 * <p>This class supports multi-layer boards and provides comprehensive validation
 * to ensure design rule compliance during item insertion and modification.
 *
 * @see RoutingBoard
 * @see Item
 * @see SearchTreeManager
 * @see BoardRules
 */
public class BasicBoard implements Serializable {

  /**
   * List of all items inserted into this board.
   *
   * <p>This undoable object list contains all board elements including traces, vias, pins,
   * obstacles, conduction areas, and component outlines. The list supports undo/redo operations
   * through snapshot management, allowing the board state to be restored to previous configurations.
   *
   * <p>Items include:
   * <ul>
   *   <li>Traces - copper tracks connecting components</li>
   *   <li>Vias - vertical connections between layers</li>
   *   <li>Pins - connection points on components</li>
   *   <li>Obstacles - keepout areas and physical barriers</li>
   *   <li>Conduction areas - copper pours and planes</li>
   *   <li>Component outlines - physical component boundaries</li>
   * </ul>
   *
   * @see UndoableObjects
   * @see Item
   */
  public final UndoableObjects item_list;
  /**
   * Registry of all components placed on the board.
   *
   * <p>This collection manages component instances, tracking their positions, rotations,
   * orientations (front/back), and associated library packages. Components are assigned
   * unique numbers for identification and referencing by their constituent items (pins,
   * obstacles, outlines).
   *
   * @see Components
   * @see Component
   */
  public final Components components;
  /**
   * Design rules and constraints governing item placement and routing on the board.
   *
   * <p>The BoardRules object defines the restrictions and requirements for board design, including:
   * <ul>
   *   <li>Clearance matrix - minimum spacing between different clearance classes</li>
   *   <li>Net definitions - logical networks connecting components</li>
   *   <li>Trace width restrictions - minimum and maximum trace widths</li>
   *   <li>Via restrictions - rules for via placement and sizing</li>
   *   <li>Angle restrictions - constraints on trace angles (e.g., 45-degree, 90-degree)</li>
   * </ul>
   *
   * <p>These rules are enforced during item insertion and validation to ensure
   * manufacturability and design integrity.
   *
   * @see BoardRules
   * @see app.freerouting.rules.ClearanceMatrix
   */
  public final BoardRules rules;
  /**
   * Library of reusable design elements and templates used on the board.
   *
   * <p>The board library contains:
   * <ul>
   *   <li>Padstack definitions - via and pin geometries across layers</li>
   *   <li>Package definitions - component footprints with pins and outlines</li>
   *   <li>Via templates - predefined via configurations</li>
   * </ul>
   *
   * <p>These library elements are referenced by board items rather than duplicated,
   * enabling consistent design and efficient storage.
   *
   * @see BoardLibrary
   * @see Padstack
   */
  public final BoardLibrary library;
  /**
   * The layer stack-up defining the physical structure of the board.
   *
   * <p>The layer structure describes the board's vertical organization, including:
   * <ul>
   *   <li>Signal layers - copper layers for routing traces</li>
   *   <li>Power and ground planes - dedicated layers for power distribution</li>
   *   <li>Layer names - human-readable identifiers for each layer</li>
   *   <li>Layer ordering - top-to-bottom arrangement</li>
   * </ul>
   *
   * <p>Layers are indexed from 0 (typically the top layer) to layer_count - 1.
   *
   * @see LayerStructure
   * @see Layer
   */
  public final LayerStructure layer_structure;
  /**
   * Communication interface for external system interaction and event notification.
   *
   * <p>This interface provides:
   * <ul>
   *   <li>Observer pattern support - notifies external systems of board changes</li>
   *   <li>Host system integration - synchronization with design databases</li>
   *   <li>Event broadcasting - item insertion, deletion, and modification notifications</li>
   * </ul>
   *
   * <p>The communication object may be null if the board is standalone without
   * external integration requirements.
   *
   * @see Communication
   */
  public final Communication communication;
  /**
   * The orthogonal bounding rectangle encompassing the entire board area.
   *
   * <p>This box defines the valid coordinate space for all board items. Items placed
   * outside this boundary are considered invalid. The bounding box is typically defined
   * by the board outline but may be larger to provide working space margins.
   *
   * <p>All item coordinates and shapes are validated against this boundary during insertion.
   *
   * @see IntBox
   */
  public final IntBox bounding_box;
  /**
   * Manager for spatial search trees enabling efficient item queries and overlap detection.
   *
   * <p>The search tree manager maintains multiple search tree structures optimized for
   * different query types:
   * <ul>
   *   <li>Default tree - general purpose item lookup by location</li>
   *   <li>Clearance-compensated trees - optimized clearance violation checking</li>
   *   <li>Per-layer organization - separate trees for each board layer</li>
   * </ul>
   *
   * <p>These trees dramatically improve performance for operations like picking items
   * at a point, finding overlapping objects, and checking clearances. This field is
   * transient and reconstructed after deserialization.
   *
   * @see SearchTreeManager
   * @see ShapeSearchTree
   */
  public transient SearchTreeManager search_tree_manager;
  /**
   * The rectangular area where graphics may be out-of-date and require repainting.
   *
   * <p>This transient field tracks the region affected by recent board modifications.
   * Graphical interfaces can query this box to perform efficient partial screen updates
   * rather than redrawing the entire board. The box is reset after each update cycle.
   *
   * @see #get_graphics_update_box()
   * @see #join_graphics_update_box(IntBox)
   * @see #reset_graphics_update_box()
   */
  private transient IntBox update_box = IntBox.EMPTY;

  /**
   * The maximum half-width (radius) of all traces on the board.
   *
   * <p>This value is maintained dynamically as traces are added and modified. It's used
   * for optimization calculations and routing algorithms that need to know the largest
   * trace dimension. Half-width is used because traces expand equally on both sides
   * of their centerline.
   *
   * @see #get_max_trace_half_width()
   */
  private int max_trace_half_width = 1000;

  /**
   * The minimum half-width (radius) of all traces on the board.
   *
   * <p>This value is maintained dynamically as traces are added and modified. It's used
   * for optimization calculations and algorithms that need to know the smallest trace
   * dimension. Half-width is used because traces expand equally on both sides of their
   * centerline.
   *
   * @see #get_min_trace_half_width()
   */
  private int min_trace_half_width = 10000;

  /**
   * Creates a new board instance with the specified geometric and design parameters.
   *
   * <p>This constructor initializes all fundamental board structures including the item list,
   * component registry, search trees, and design rules. The board outline is automatically
   * inserted as the first board item.
   *
   * <p>Initialization includes:
   * <ul>
   *   <li>Setting up the layer structure and bounding box</li>
   *   <li>Creating empty item and component lists with undo/redo support</li>
   *   <li>Initializing spatial search trees for efficient queries</li>
   *   <li>Establishing the communication interface for external integration</li>
   *   <li>Inserting the board outline with proper clearance classification</li>
   * </ul>
   *
   * @param p_bounding_box the rectangular boundary defining valid board coordinates
   * @param p_layer_structure the layer stack-up defining signal, power, and ground layers
   * @param p_outline_shapes array of polyline shapes defining the board physical outline(s)
   * @param p_outline_cl_class_no the clearance class number assigned to the board outline
   * @param p_rules the design rules including clearance matrix and net definitions
   * @param p_communication the communication interface for observer notifications; may be null
   *
   * @see #insert_outline(PolylineShape[], int)
   */
  public BasicBoard(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
      int p_outline_cl_class_no, BoardRules p_rules, Communication p_communication) {
    layer_structure = p_layer_structure;
    rules = p_rules;
    library = new BoardLibrary();
    item_list = new UndoableObjects();
    components = new Components();
    communication = p_communication;
    bounding_box = p_bounding_box;
    search_tree_manager = new SearchTreeManager(this);
    p_rules.nets.set_board(this);
    insert_outline(p_outline_shapes, p_outline_cl_class_no);
  }

  /**
   * Deserializes a BasicBoard from a byte array.
   *
   * <p>This static factory method reconstructs a complete board instance from its serialized
   * byte representation. The board's search trees and transient structures are automatically
   * rebuilt during the deserialization process through the readObject hook.
   *
   * @param object_byte_array the serialized board data
   * @return the reconstructed BasicBoard instance, or null if deserialization fails
   *
   * @see #serialize(boolean)
   * @see #readObject(ObjectInputStream)
   */
  public static BasicBoard deserialize(byte[] object_byte_array) {
    try {
      ByteArrayInputStream input_stream = new ByteArrayInputStream(object_byte_array);
      ObjectInputStream object_stream = new ObjectInputStream(input_stream);

      return (BasicBoard) object_stream.readObject();
    } catch (Exception e) {
      FRLogger.error("Couldn't deserialize board", e);
    }

    return null;
  }

  /**
   * Converts a byte array to its hexadecimal string representation.
   *
   * <p>This helper method is used by the hash generation process to create a human-readable
   * representation of the MD5 digest.
   *
   * @param arrayBytes the byte array to convert
   * @return a hexadecimal string representation of the input bytes
   */
  private static String convert_byte_array_to_hex_string(byte[] arrayBytes) {
    StringBuilder stringBuffer = new StringBuilder();
    for (int i = 0; i < arrayBytes.length; i++) {
      stringBuffer.append(Integer
          .toString((arrayBytes[i] & 0xff) + 0x100, 16)
          .substring(1));
    }
    return stringBuffer.toString();
  }

  /**
   * Serializes the board to a byte array for storage or transmission.
   *
   * <p>This method converts the board to a byte representation suitable for:
   * <ul>
   *   <li>Saving to disk</li>
   *   <li>Network transmission</li>
   *   <li>Creating snapshots for undo/redo</li>
   *   <li>Generating hash digests for comparison</li>
   * </ul>
   *
   * <p>The basicProfile parameter controls what gets serialized:
   * <ul>
   *   <li>If true - only traces, vias, and item_list (minimal profile for hashing/comparison)</li>
   *   <li>If false - complete board including all components, rules, and library data</li>
   * </ul>
   *
   * @param basicProfile if true, serialize only essential routing data; if false, serialize everything
   * @return the serialized board as a byte array, or null if serialization fails
   *
   * @see #deserialize(byte[])
   * @see #get_hash()
   */
  public byte[] serialize(boolean basicProfile) {
    try {
      ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
      ObjectOutputStream object_stream = new ObjectOutputStream(output_stream);

      if (basicProfile) {
        object_stream.writeObject(this.get_traces());
        object_stream.writeObject(this.get_vias());
        object_stream.writeObject(this.item_list);
      } else {
        object_stream.writeObject(this);
      }

      object_stream.close();

      return output_stream.toByteArray();
    } catch (Exception e) {
      FRLogger.error("Couldn't serialize board", e);
    }

    return null;
  }

  /**
   * Creates a deep copy of this board through serialization/deserialization.
   *
   * <p>This method produces a complete independent copy of the board including all items,
   * components, rules, and design data. The copy is isolated from the original - modifications
   * to either board will not affect the other.
   *
   * @return a deep copy of this board
   *
   * @see #serialize(boolean)
   * @see #deserialize(byte[])
   */
  @Override
  public BasicBoard clone() {
    return deserialize(this.serialize(false));
  }

  /**
   * Generates an MD5 hash of the board's routing data for comparison purposes.
   *
   * <p>The hash is computed from the basic profile serialization (traces, vias, and item list only),
   * making it suitable for detecting differences in routing topology while ignoring non-routing
   * changes like component placements or rule modifications.
   *
   * <p>This hash can be used to:
   * <ul>
   *   <li>Detect if routing has changed between board versions</li>
   *   <li>Verify routing integrity after operations</li>
   *   <li>Compare routing solutions</li>
   * </ul>
   *
   * @return an MD5 hash string (hex-encoded), or null if hash generation fails
   *
   * @see #serialize(boolean)
   * @see #diff_traces(BasicBoard)
   */
  public String get_hash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(this.serialize(true));
      byte[] hashedBytes = digest.digest();

      return convert_byte_array_to_hex_string(hashedBytes);
    } catch (Exception e) {
      FRLogger.error("Couldn't calculate hash for board", e);
    }

    return null;
  }

  /**
   * Counts the number of trace differences between this board and another board.
   *
   * <p>This method compares trace IDs to determine how many traces differ between boards.
   * It counts both traces present in one board but not the other, providing a symmetric
   * difference metric. This is useful for:
   * <ul>
   *   <li>Measuring routing progress</li>
   *   <li>Comparing routing solutions</li>
   *   <li>Validating routing operations</li>
   * </ul>
   *
   * @param compare_to the board to compare against
   * @return the total number of trace differences (traces in this board but not in compare_to
   *         plus traces in compare_to but not in this board)
   *
   * @see #get_hash()
   */
  public int diff_traces(BasicBoard compare_to) {
    int result = 0;
    HashSet<Integer> traceIds = new HashSet<>();
    for (Trace trace : this.get_traces()) {
      traceIds.add(trace.get_id_no());
    }

    for (Trace trace : compare_to.get_traces()) {
      if (!traceIds.contains(trace.get_id_no())) {
        result++;
      } else {
        traceIds.remove(trace.get_id_no());
      }
    }
    result += traceIds.size();

    return result;
  }

  /**
   * Inserts a polyline trace into the board without performing normalization or cleanup.
   *
   * <p>This low-level insertion method creates a trace directly from a polyline without
   * applying topology normalization (removing unnecessary corners, combining with adjacent
   * traces, etc.). This is useful when building complex routing in multiple steps where
   * intermediate cleanup would be inefficient.
   *
   * <p>The method:
   * <ul>
   *   <li>Creates a new PolylineTrace object with the specified parameters</li>
   *   <li>Validates that the polyline has at least 2 corners</li>
   *   <li>Rejects traces that start and end at the same point (unless user-fixed)</li>
   *   <li>Updates trace width statistics</li>
   *   <li>Inserts the trace into search trees</li>
   * </ul>
   *
   * <p><strong>Note:</strong> Callers are responsible for normalizing the trace topology
   * after insertion using the trace's normalize() method.
   *
   * @param p_polyline the geometric path of the trace
   * @param p_layer the layer index where the trace is placed
   * @param p_half_width the half-width (radius) of the trace
   * @param p_net_no_arr array of net numbers the trace belongs to
   * @param p_clearance_class the clearance class index for the trace
   * @param p_fixed_state the fixed state (NOT_FIXED, USER_FIXED, or SYSTEM_FIXED)
   * @return the newly created trace, or null if the polyline is invalid
   *
   * @see #insert_trace(Polyline, int, int, int[], int, FixedState)
   * @see PolylineTrace#normalize(IntOctagon)
   */
  public PolylineTrace insert_trace_without_cleaning(Polyline p_polyline, int p_layer, int p_half_width,
      int[] p_net_no_arr, int p_clearance_class, FixedState p_fixed_state) {
    if (p_polyline.corner_count() < 2) {
      return null;
    }
    PolylineTrace new_trace = new PolylineTrace(p_polyline, p_layer, p_half_width, p_net_no_arr, p_clearance_class, 0,
        0, p_fixed_state, this);
    if (new_trace
        .first_corner()
        .equals(new_trace.last_corner())) {
      if (p_fixed_state.ordinal() < FixedState.USER_FIXED.ordinal()) {
        return null;
      }
    }
    insert_item(new_trace);
    if (new_trace.nets_normal()) {
      max_trace_half_width = Math.max(max_trace_half_width, p_half_width);
      min_trace_half_width = Math.min(min_trace_half_width, p_half_width);
    }
    return new_trace;
  }

  /**
   * Inserts a polyline trace into the board and normalizes its topology.
   *
   * <p>This is the standard method for inserting traces. It creates the trace and then
   * normalizes it to remove unnecessary corners, combine with adjacent traces, and optimize
   * the routing topology. The normalization respects any changed area clipping if this is
   * a RoutingBoard.
   *
   * <p>Normalization includes:
   * <ul>
   *   <li>Removing collinear corners</li>
   *   <li>Combining with connected traces that have only one contact</li>
   *   <li>Splitting at connection points</li>
   *   <li>Removing unnecessary bends</li>
   * </ul>
   *
   * @param p_polyline the geometric path of the trace
   * @param p_layer the layer index where the trace is placed
   * @param p_half_width the half-width (radius) of the trace
   * @param p_net_no_arr array of net numbers the trace belongs to
   * @param p_clearance_class the clearance class index for the trace
   * @param p_fixed_state the fixed state (NOT_FIXED, USER_FIXED, or SYSTEM_FIXED)
   *
   * @see #insert_trace_without_cleaning(Polyline, int, int, int[], int, FixedState)
   * @see PolylineTrace#normalize(IntOctagon)
   */
  public void insert_trace(Polyline p_polyline, int p_layer, int p_half_width, int[] p_net_no_arr,
      int p_clearance_class, FixedState p_fixed_state) {
    PolylineTrace new_trace = insert_trace_without_cleaning(p_polyline, p_layer, p_half_width, p_net_no_arr,
        p_clearance_class, p_fixed_state);
    if (new_trace == null) {
      return;
    }
    IntOctagon clip_shape = null;
    if (this instanceof RoutingBoard board) {
      ChangedArea changed_area = board.changed_area;
      if (changed_area != null) {
        clip_shape = changed_area.get_area(p_layer);
      }
    }

    try {
      new_trace.normalize(clip_shape);
    } catch (Exception e) {
      FRLogger.error("Couldn't insert new trace, because its normalization failed.", e);
    }
  }

  /**
   * Inserts a trace defined by an array of corner points and normalizes its topology.
   *
   * <p>This convenience method converts a point array to a polyline and delegates to the
   * main trace insertion method. Each point is validated to ensure it lies within the
   * board's bounding box.
   *
   * @param p_points array of corner points defining the trace path
   * @param p_layer the layer index where the trace is placed
   * @param p_half_width the half-width (radius) of the trace
   * @param p_net_no_arr array of net numbers the trace belongs to
   * @param p_clearance_class the clearance class index for the trace
   * @param p_fixed_state the fixed state (NOT_FIXED, USER_FIXED, or SYSTEM_FIXED)
   *
   * @see #insert_trace(Polyline, int, int, int[], int, FixedState)
   */
  public void insert_trace(Point[] p_points, int p_layer, int p_half_width, int[] p_net_no_arr, int p_clearance_class,
      FixedState p_fixed_state) {
    for (int i = 0; i < p_points.length; i++) {
      if (!this.bounding_box.contains(p_points[i])) {
        FRLogger.warn("LayeredBoard.insert_trace: input point out of range");
      }
    }
    Polyline poly = new Polyline(p_points);
    insert_trace(poly, p_layer, p_half_width, p_net_no_arr, p_clearance_class, p_fixed_state);
  }

  /**
   * Inserts a via into the board at the specified location.
   *
   * <p>A via provides vertical connectivity between board layers. This method:
   * <ul>
   *   <li>Creates a new via using the specified padstack definition</li>
   *   <li>Splits any traces at the via location to create proper connections</li>
   *   <li>Inserts the via into the search trees</li>
   *   <li>Notifies observers of the new item</li>
   * </ul>
   *
   * <p>The attach_allowed parameter controls whether the via can overlap with SMD pins
   * of the same net. This is useful for fanout routing where vias need to be placed
   * directly on surface mount pads.
   *
   * @param p_padstack the padstack definition specifying via geometry on each layer
   * @param p_center the center point where the via is placed
   * @param p_net_no_arr array of net numbers the via belongs to
   * @param p_clearance_class the clearance class index for the via
   * @param p_fixed_state the fixed state (NOT_FIXED, USER_FIXED, or SYSTEM_FIXED)
   * @param p_attach_allowed if true, allows the via to overlap same-net SMD pins
   * @return the newly created via
   *
   * @see Via
   * @see Padstack
   */
  public Via insert_via(Padstack p_padstack, Point p_center, int[] p_net_no_arr, int p_clearance_class,
      FixedState p_fixed_state, boolean p_attach_allowed) {
    Via new_via = new Via(p_padstack, p_center, p_net_no_arr, p_clearance_class, 0, 0, p_fixed_state, p_attach_allowed,
        this);
    insert_item(new_via);
    int from_layer = p_padstack.from_layer();
    int to_layer = p_padstack.to_layer();
    for (int i = from_layer; i < to_layer; i++) {
      for (int curr_net_no : p_net_no_arr) {
        split_traces(p_center, i, curr_net_no);
      }
    }
    return new_via;
  }

  /**
   * Inserts a component pin into the board.
   *
   * <p>Pins are connection points on components that traces and vias connect to. Each pin
   * references a padstack definition from the component's package in the board library.
   *
   * <p>Pin identification:
   * <ul>
   *   <li>Component number - identifies which component this pin belongs to</li>
   *   <li>Pin number - identifies which pin within the component (0-based index)</li>
   * </ul>
   *
   * @param p_component_no the unique identifier of the component this pin belongs to
   * @param p_pin_no the pin index within the component's package (starting from 0)
   * @param p_net_no_arr array of net numbers the pin belongs to
   * @param p_clearance_class the clearance class index for the pin
   * @param p_fixed_state the fixed state (typically SYSTEM_FIXED for component pins)
   * @return the newly created pin
   *
   * @see Pin
   * @see Component
   */
  public Pin insert_pin(int p_component_no, int p_pin_no, int[] p_net_no_arr, int p_clearance_class,
      FixedState p_fixed_state) {
    Pin new_pin = new Pin(p_component_no, p_pin_no, p_net_no_arr, p_clearance_class, 0, p_fixed_state, this);
    insert_item(new_pin);
    return new_pin;
  }

  /**
   * Inserts a standalone obstacle area into the board.
   *
   * <p>Obstacle areas define regions where routing is not allowed. They can represent:
   * <ul>
   *   <li>Keepout zones</li>
   *   <li>Physical board features (mounting holes, cutouts)</li>
   *   <li>Non-copper board elements</li>
   * </ul>
   *
   * <p>The area can be a complex polygon with holes. This version creates a standalone
   * obstacle not associated with any component.
   *
   * @param p_area the geometric area of the obstacle (may contain holes)
   * @param p_layer the layer index where the obstacle exists
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_fixed_state the fixed state (typically SYSTEM_FIXED for design obstacles)
   * @return the newly created obstacle, or null if the area is null
   *
   * @see ObstacleArea
   * @see Area
   */
  public ObstacleArea insert_obstacle(Area p_area, int p_layer, int p_clearance_class, FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_obstacle: p_area is null");
      return null;
    }
    ObstacleArea obs = new ObstacleArea(p_area, p_layer, Vector.ZERO, 0, false, p_clearance_class, 0, 0, null,
        p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts an obstacle area belonging to a specific component into the board.
   *
   * <p>Component obstacles move with their parent component and are transformed (translated,
   * rotated, flipped) according to the component's placement. These typically represent:
   * <ul>
   *   <li>Component body keepouts</li>
   *   <li>Courtyard areas</li>
   *   <li>Component-specific routing restrictions</li>
   * </ul>
   *
   * @param p_area the geometric area in the component's local coordinate system
   * @param p_layer the layer index where the obstacle exists
   * @param p_translation the translation vector from component origin to board coordinates
   * @param p_rotation_in_degree the rotation angle of the component in degrees
   * @param p_side_changed true if the component is on the opposite side (flipped)
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_component_no the unique identifier of the owning component
   * @param p_name the name identifying this obstacle within the component package
   * @param p_fixed_state the fixed state (typically matches component's fixed state)
   * @return the newly created obstacle, or null if the area is null
   *
   * @see ObstacleArea
   * @see Component
   */
  public ObstacleArea insert_obstacle(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree,
      boolean p_side_changed, int p_clearance_class, int p_component_no, String p_name,
      FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_obstacle: p_area is null");
      return null;
    }
    ObstacleArea obs = new ObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed,
        p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts a standalone via obstacle area into the board.
   *
   * <p>Via obstacles are special keepout zones that prevent via placement but allow traces.
   * They're used to define areas where vias cannot be placed due to:
   * <ul>
   *   <li>Manufacturing constraints</li>
   *   <li>Thermal management requirements</li>
   *   <li>Component clearance zones</li>
   * </ul>
   *
   * @param p_area the geometric area of the via obstacle (may contain holes)
   * @param p_layer the layer index where the obstacle exists
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_fixed_state the fixed state (typically SYSTEM_FIXED)
   * @return the newly created via obstacle, or null if the area is null
   *
   * @see ViaObstacleArea
   */
  public ViaObstacleArea insert_via_obstacle(Area p_area, int p_layer, int p_clearance_class,
      FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_via_obstacle: p_area is null");
      return null;
    }
    ViaObstacleArea obs = new ViaObstacleArea(p_area, p_layer, Vector.ZERO, 0, false, p_clearance_class, 0, 0, null,
        p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts a via obstacle area belonging to a specific component into the board.
   *
   * <p>Component via obstacles are transformed with their parent component and prevent
   * via placement in areas such as under surface mount components or near sensitive
   * component features.
   *
   * @param p_area the geometric area in the component's local coordinate system
   * @param p_layer the layer index where the obstacle exists
   * @param p_translation the translation vector from component origin to board coordinates
   * @param p_rotation_in_degree the rotation angle of the component in degrees
   * @param p_side_changed true if the component is on the opposite side (flipped)
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_component_no the unique identifier of the owning component
   * @param p_name the name identifying this obstacle within the component package
   * @param p_fixed_state the fixed state (typically matches component's fixed state)
   * @return the newly created via obstacle, or null if the area is null
   *
   * @see ViaObstacleArea
   */
  public ViaObstacleArea insert_via_obstacle(Area p_area, int p_layer, Vector p_translation,
      double p_rotation_in_degree, boolean p_side_changed, int p_clearance_class, int p_component_no,
      String p_name, FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_via_obstacle: p_area is null");
      return null;
    }
    ViaObstacleArea obs = new ViaObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed,
        p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts a standalone component obstacle area into the board.
   *
   * <p>Component obstacles define areas where components cannot be placed. Unlike regular
   * obstacles, these specifically prevent component placement while potentially allowing
   * routing. They're used for:
   * <ul>
   *   <li>Mounting hole keepouts</li>
   *   <li>Edge connector zones</li>
   *   <li>Height restriction areas</li>
   * </ul>
   *
   * @param p_area the geometric area of the component obstacle (may contain holes)
   * @param p_layer the layer index where the obstacle exists
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_fixed_state the fixed state (typically SYSTEM_FIXED)
   * @return the newly created component obstacle, or null if the area is null
   *
   * @see ComponentObstacleArea
   */
  public ComponentObstacleArea insert_component_obstacle(Area p_area, int p_layer, int p_clearance_class,
      FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_component_obstacle: p_area is null");
      return null;
    }
    ComponentObstacleArea obs = new ComponentObstacleArea(p_area, p_layer, Vector.ZERO, 0, false, p_clearance_class, 0,
        0, null, p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts a component obstacle area belonging to a specific component into the board.
   *
   * <p>This component obstacle moves with its parent component and defines zones where
   * other components cannot be placed, preventing component-to-component interference.
   *
   * @param p_area the geometric area in the component's local coordinate system
   * @param p_layer the layer index where the obstacle exists
   * @param p_translation the translation vector from component origin to board coordinates
   * @param p_rotation_in_degree the rotation angle of the component in degrees
   * @param p_side_changed true if the component is on the opposite side (flipped)
   * @param p_clearance_class the clearance class index for the obstacle
   * @param p_component_no the unique identifier of the owning component
   * @param p_name the name identifying this obstacle within the component package
   * @param p_fixed_state the fixed state (typically matches component's fixed state)
   * @return the newly created component obstacle, or null if the area is null
   *
   * @see ComponentObstacleArea
   */
  public ComponentObstacleArea insert_component_obstacle(Area p_area, int p_layer, Vector p_translation,
      double p_rotation_in_degree, boolean p_side_changed, int p_clearance_class, int p_component_no,
      String p_name, FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_component_obstacle: p_area is null");
      return null;
    }
    ComponentObstacleArea obs = new ComponentObstacleArea(p_area, p_layer, p_translation, p_rotation_in_degree,
        p_side_changed, p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
    insert_item(obs);
    return obs;
  }

  /**
   * Inserts a component outline into the board showing the physical component boundary.
   *
   * <p>Component outlines provide visual representation of component extents on the board.
   * They're primarily used for:
   * <ul>
   *   <li>Assembly documentation</li>
   *   <li>Visual component identification</li>
   *   <li>Silkscreen generation</li>
   *   <li>3D model alignment</li>
   * </ul>
   *
   * @param p_area the geometric boundary of the component
   * @param p_is_front true if the component is on the front (top) side, false for back (bottom)
   * @param p_translation the translation vector from component origin to board coordinates
   * @param p_rotation_in_degree the rotation angle of the component in degrees
   * @param p_component_no the unique identifier of the component
   * @param p_fixed_state the fixed state (typically matches component's fixed state)
   * @return the newly created outline, or null if the area is null or unbounded
   *
   * @see ComponentOutline
   */
  public ComponentOutline insert_component_outline(Area p_area, boolean p_is_front, Vector p_translation,
      double p_rotation_in_degree, int p_component_no, FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_component_outline: p_area is null");
      return null;
    }
    if (!p_area.is_bounded()) {
      FRLogger.warn("BasicBoard.insert_component_outline: p_area is not bounded");
      return null;
    }
    ComponentOutline outline = new ComponentOutline(p_area, p_is_front, p_translation, p_rotation_in_degree, 0,
        p_component_no, p_fixed_state, this);
    insert_item(outline);
    return outline;
  }

  /**
   * Inserts a conduction area (copper pour or plane) into the board.
   *
   * <p>Conduction areas are filled copper regions typically used for:
   * <ul>
   *   <li>Power and ground planes</li>
   *   <li>Copper pours for better current carrying capacity</li>
   *   <li>EMI shielding</li>
   *   <li>Heat dissipation</li>
   * </ul>
   *
   * <p>The is_obstacle parameter controls routing behavior:
   * <ul>
   *   <li>If true - traces of foreign nets cannot route through the area</li>
   *   <li>If false - traces of all nets can route through (with proper clearance)</li>
   * </ul>
   *
   * @param p_area the geometric area of the conduction region (may contain holes)
   * @param p_layer the layer index where the conduction area exists
   * @param p_net_no_arr array of net numbers the conduction area belongs to
   * @param p_clearance_class the clearance class index for the area
   * @param p_is_obstacle if true, prevents foreign net routing; if false, allows it
   * @param p_fixed_state the fixed state (NOT_FIXED, USER_FIXED, or SYSTEM_FIXED)
   * @return the newly created conduction area, or null if the area is null
   *
   * @see ConductionArea
   */
  public ConductionArea insert_conduction_area(Area p_area, int p_layer, int[] p_net_no_arr, int p_clearance_class,
      boolean p_is_obstacle, FixedState p_fixed_state) {
    if (p_area == null) {
      FRLogger.warn("BasicBoard.insert_conduction_area: p_area is null");
      return null;
    }
    ConductionArea c = new ConductionArea(p_area, p_layer, Vector.ZERO, 0, false, p_net_no_arr, p_clearance_class, 0, 0,
        null, p_is_obstacle, p_fixed_state, this);
    insert_item(c);
    return c;
  }

  /**
   * Inserts the board physical outline defining its boundaries and shape.
   *
   * <p>The board outline defines the physical edges and cutouts of the PCB. It can consist
   * of multiple closed polyline shapes to represent:
   * <ul>
   *   <li>The main board perimeter</li>
   *   <li>Internal cutouts and slots</li>
   *   <li>Complex board shapes</li>
   * </ul>
   *
   * <p>The outline is typically inserted during board creation and establishes the
   * boundary constraints for all other board items.
   *
   * @param p_outline_shapes array of closed polyline shapes defining the board outline(s)
   * @param p_clearance_class_no the clearance class number for the outline
   * @return the newly created board outline
   *
   * @see BoardOutline
   * @see PolylineShape
   */
  public BoardOutline insert_outline(PolylineShape[] p_outline_shapes, int p_clearance_class_no) {
    BoardOutline result = new BoardOutline(p_outline_shapes, p_clearance_class_no, 0, this);
    insert_item(result);
    return result;
  }

  /**
   * Returns the board's physical outline shape.
   *
   * <p>This method searches the item list for the BoardOutline object that defines
   * the board's physical boundaries. There should normally be exactly one board outline
   * object, created during board initialization.
   *
   * @return the board outline, or null if no outline has been defined
   *
   * @see BoardOutline
   * @see #insert_outline(PolylineShape[], int)
   */
  public BoardOutline get_outline() {
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof BoardOutline outline) {
        return outline;
      }
    }
    return null;
  }

  /**
   * Removes an item from the board and updates all related structures.
   *
   * <p>This method performs complete cleanup including:
   * <ul>
   *   <li>Removing the item from search trees</li>
   *   <li>Deleting the item from the undoable object list</li>
   *   <li>Notifying observers of the deletion</li>
   *   <li>Calling additional_update_after_change for autoroute database maintenance</li>
   * </ul>
   *
   * @param p_item the item to remove; if null, no action is taken
   *
   * @see #remove_items(Collection)
   */
  public void remove_item(Item p_item) {
    if (p_item == null) {
      return;
    }
    additional_update_after_change(p_item); // must be called before p_item is deleted.
    search_tree_manager.remove(p_item);
    item_list.delete(p_item);

    // let the observers synchronize the deletion
    if ((communication != null) && (communication.observers != null)) {
      communication.observers.notify_deleted(p_item);
    }
  }

  /**
   * Searches for an item with the specified ID number on the board.
   *
   * <p>Each item has a unique ID number assigned when it's created. This method performs
   * a linear search through all board items to find the one with the matching ID.
   *
   * @param p_id_no the unique ID number of the item to search for
   * @return the item with the matching ID, or null if no such item exists
   *
   * @see Item#get_id_no()
   */
  public Item get_item(int p_id_no) {
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.get_id_no() == p_id_no) {
        return curr_item;
      }
    }
    return null;
  }

  /**
   * Returns all items currently on the board.
   *
   * <p>This method creates a snapshot collection of all board items including traces, vias,
   * pins, obstacles, conduction areas, and other objects. The returned collection is independent
   * of the internal item list.
   *
   * @return a collection containing all board items
   *
   * @see #get_connectable_items(int)
   */
  public Collection<Item> get_items() {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      result.add(curr_item);
    }
    return result;
  }

  /**
   * Returns all connectable items (traces, pins, vias) belonging to the specified net.
   *
   * <p>Connectable items are those that implement the Connectable interface and can form
   * electrical connections. This includes traces, vias, pins, and conduction areas.
   *
   * @param p_net_no the net number to search for
   * @return a collection of all connectable items containing the specified net
   *
   * @see Connectable
   * @see #connectable_item_count(int)
   */
  public Collection<Item> get_connectable_items(int p_net_no) {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no)) {
        result.add(curr_item);
      }
    }
    return result;
  }

  /**
   * Counts the connectable items belonging to the specified net.
   *
   * <p>This method efficiently counts items without creating a collection, making it
   * faster than calling get_connectable_items().size() when only the count is needed.
   *
   * @param p_net_no the net number to count items for
   * @return the number of connectable items in the specified net
   *
   * @see #get_connectable_items(int)
   */
  public int connectable_item_count(int p_net_no) {
    int result = 0;
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no)) {
        ++result;
      }
    }
    return result;
  }

  /**
   * Returns all board items (pins, obstacles, outlines) belonging to the specified component.
   *
   * <p>Components consist of multiple items that all share the same component number.
   * This includes pins, component obstacles, via obstacles, and component outlines.
   *
   * @param p_component_no the unique component identifier
   * @return a collection of all items belonging to the component
   *
   * @see #get_component_pins(int)
   * @see Component
   */
  public Collection<Item> get_component_items(int p_component_no) {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.get_component_no() == p_component_no) {
        result.add(curr_item);
      }
    }
    return result;
  }

  /**
   * Returns all pins belonging to the specified component.
   *
   * <p>Pins are the electrical connection points on a component. This method filters
   * the component's items to return only pin objects.
   *
   * @param p_component_no the unique component identifier
   * @return a collection of all pins belonging to the component
   *
   * @see #get_pin(int, int)
   * @see Pin
   */
  public Collection<Pin> get_component_pins(int p_component_no) {
    Collection<Pin> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.get_component_no() == p_component_no && curr_item instanceof Pin pin) {
        result.add(pin);
      }
    }
    return result;
  }

  /**
   * Returns the specific pin identified by component and pin numbers.
   *
   * <p>Pins are uniquely identified by the combination of their component number and
   * pin number within that component. Pin numbers are 0-based indices corresponding to
   * the pin's position in the component's package definition.
   *
   * @param p_component_no the unique component identifier
   * @param p_pin_no the pin index within the component (starting from 0)
   * @return the matching pin, or null if no such pin exists
   *
   * @see #get_component_pins(int)
   * @see Pin
   */
  public Pin get_pin(int p_component_no, int p_pin_no) {
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item.get_component_no() == p_component_no && curr_item instanceof Pin curr_pin) {
        if (curr_pin.pin_no == p_pin_no) {
          return curr_pin;
        }
      }
    }
    return null;
  }

  /**
   * Removes multiple items from the board in a single operation.
   *
   * <p>This method attempts to remove all items in the provided collection. Items that
   * are user-fixed or have deletion forbidden will be skipped. This is safer than
   * individual removal as it validates each item before deletion.
   *
   * @param p_item_list the collection of items to remove
   * @return true if all items were successfully removed, false if any items could not
   *         be removed due to being fixed or deletion-forbidden
   *
   * @see #remove_item(Item)
   * @see Item#isDeletionForbidden()
   */
  public boolean remove_items(Collection<Item> p_item_list) {
    boolean result = true;
    for (Item curr_item : p_item_list) {
      if (curr_item.isDeletionForbidden() || curr_item.is_user_fixed()) {
        // We are not allowed to delete this item
        result = false;
      } else {
        remove_item(curr_item);
      }
    }
    return result;
  }

  /**
   * Returns all conduction areas (copper pours and planes) on the board.
   *
   * <p>Conduction areas include power planes, ground planes, and copper pour regions.
   *
   * @return a collection of all conduction areas
   *
   * @see ConductionArea
   */
  public Collection<ConductionArea> get_conduction_areas() {
    Collection<ConductionArea> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof ConductionArea area) {
        result.add(area);
      }
    }
    return result;
  }

  /**
   * Returns all pins on the board across all components.
   *
   * <p>Pins are the electrical connection points on components where traces and vias connect.
   * This includes both through-hole (multi-layer) and SMD (single-layer) pins.
   *
   * @return a collection of all pins on the board
   *
   * @see #get_smd_pins()
   * @see Pin
   */
  public Collection<Pin> get_pins() {
    Collection<Pin> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Pin pin) {
        result.add(pin);
      }
    }
    return result;
  }

  /**
   * Returns all surface-mount device (SMD) pins that exist on only one layer.
   *
   * <p>SMD pins are single-layer connection points typically used for surface mount components.
   * Unlike through-hole pins that span multiple layers, SMD pins exist only on the top or
   * bottom layer. These pins often require fanout routing to connect to internal layers.
   *
   * @return a collection of all SMD pins (pins where first_layer == last_layer)
   *
   * @see #get_pins()
   * @see Pin
   */
  public Collection<Pin> get_smd_pins() {
    Collection<Pin> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Pin curr_pin) {
        if (curr_pin.first_layer() == curr_pin.last_layer()) {
          result.add(curr_pin);
        }
      }
    }
    return result;
  }

  /**
   * Returns all vias on the board.
   *
   * <p>Vias provide vertical electrical connections between board layers. This includes
   * through vias (connecting all layers), blind vias, and buried vias.
   *
   * @return a collection of all vias
   *
   * @see Via
   */
  public Collection<Via> get_vias() {
    Collection<Via> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Via via) {
        result.add(via);
      }
    }
    return result;
  }

  /**
   * Returns all traces on the board.
   *
   * <p>Traces are the copper tracks that form electrical connections between components.
   * This includes all trace types (polyline traces, arc traces, etc.) on all layers.
   *
   * @return a collection of all traces
   *
   * @see Trace
   * @see PolylineTrace
   */
  public Collection<Trace> get_traces() {
    Collection<Trace> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Trace trace) {
        result.add(trace);
      }
    }
    return result;
  }

  /**
   * Calculates the total length of all traces on the board.
   *
   * <p>This metric sums the geometric lengths of all trace segments and is useful for:
   * <ul>
   *   <li>Routing progress assessment</li>
   *   <li>Board complexity measurement</li>
   *   <li>Comparing routing solutions</li>
   *   <li>Estimating signal propagation delays</li>
   * </ul>
   *
   * @return the sum of all trace lengths on the board
   *
   * @see Trace#get_length()
   */
  public double cumulative_trace_length() {
    double result = 0;
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Trace trace) {
        result += trace.get_length();
      }
    }
    return result;
  }

  /**
   * Combines connected traces of the specified net that have only one contact at their connection point.
   *
   * <p>This optimization merges traces that can be represented as a single continuous trace,
   * reducing the number of trace objects and simplifying the board topology. The operation is
   * repeated until no more combinations are possible.
   *
   * <p>Traces are combined when:
   * <ul>
   *   <li>They connect at a common endpoint</li>
   *   <li>Only one trace connects at that endpoint (no branching)</li>
   *   <li>They have compatible widths and properties</li>
   * </ul>
   *
   * @param p_net_no the net number to combine traces for; use negative value to combine all nets
   * @return true if any traces were combined, false if no changes were made
   *
   * @see Trace#combine()
   */
  public boolean combine_traces(int p_net_no) {
    boolean result = false;
    boolean something_changed = true;
    while (something_changed) {
      something_changed = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
      for (;;) {
        Item curr_item = (Item) item_list.read_object(it);
        if (curr_item == null) {
          break;
        }
        if ((p_net_no < 0 || curr_item.contains_net(p_net_no)) && curr_item instanceof Trace trace
            && curr_item.is_on_the_board()) {
          if (trace.combine()) {
            something_changed = true;
            result = true;
            break;
          }
        }
      }
    }
    return result;
  }

  /**
   * Normalizes the traces of the specified net to optimize their topology.
   *
   * <p>Normalization performs several cleanup operations on traces:
   * <ul>
   *   <li>Removes unnecessary corners and collinear segments</li>
   *   <li>Combines traces at connection points</li>
   *   <li>Detects and removes routing cycles</li>
   *   <li>Optimizes trace paths</li>
   * </ul>
   *
   * <p>The operation is repeated until the topology stabilizes (no further changes possible).
   * This is an important cleanup step after routing modifications.
   *
   * @param p_net_no the net number whose traces should be normalized
   * @return true if any traces were modified, false if no changes were made
   * @throws Exception if normalization fails due to geometric issues
   *
   * @see PolylineTrace#normalize(IntOctagon)
   * @see #remove_if_cycle(Trace)
   */
  public boolean normalize_traces(int p_net_no) throws Exception {
    boolean result = false;
    boolean something_changed = true;
    Item curr_item;
    while (something_changed) {
      something_changed = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
      for (;;) {
        try {
          curr_item = (Item) item_list.read_object(it);
        } catch (ConcurrentModificationException _) {
          something_changed = true;
          break;
        }
        if (curr_item == null) {
          break;
        }
        if (curr_item.contains_net(p_net_no) && curr_item instanceof PolylineTrace curr_trace
            && curr_item.is_on_the_board()) {
          if (curr_trace.normalize(null)) {
            something_changed = true;
            result = true;
          } else if (!curr_trace.is_user_fixed() && this.remove_if_cycle(curr_trace)) {
            something_changed = true;
            result = true;
          }
        }
      }
    }
    return result;
  }

  /**
   * Splits traces at the specified location to enable proper connection topology.
   *
   * <p>When a via or pin is placed on an existing trace, the trace must be split at that
   * point to create distinct connections. This method finds all traces of the specified net
   * that contain the given point and splits them.
   *
   * @param p_location the point where traces should be split
   * @param p_layer the layer to check for traces
   * @param p_net_no the net number of traces to split
   * @return true if any traces were split, false if no splits occurred
   *
   * @see Trace#split(IntOctagon)
   */
  public boolean split_traces(Point p_location, int p_layer, int p_net_no) {
    ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> picked_items = this.pick_items(p_location, p_layer, filter);
    IntOctagon location_shape = TileShape
        .get_instance(p_location)
        .bounding_octagon();
    boolean trace_split = false;
    for (Item curr_item : picked_items) {
      Trace curr_trace = (Trace) curr_item;
      if (curr_trace.contains_net(p_net_no)) {
        Collection<PolylineTrace> split_pieces = curr_trace.split(location_shape);
        if (split_pieces.size() != 1) {
          trace_split = true;
        }
      }
    }
    return trace_split;
  }

  /**
   * Returns groups of items that are electrically connected within the specified net.
   *
   * <p>This method partitions all connectable items of a net into distinct connected sets.
   * Each set represents a group of items that have electrical continuity through traces
   * and vias. Multiple sets indicate incomplete routing (unconnected islands).
   *
   * <p>This is useful for:
   * <ul>
   *   <li>Detecting unrouted connections (ratsnests)</li>
   *   <li>Validating routing completeness</li>
   *   <li>Analyzing net topology</li>
   * </ul>
   *
   * @param p_net_no the net number to analyze; returns empty collection if <= 0
   * @return a collection of collections, where each inner collection contains
   *         items forming a connected group
   *
   * @see Item#get_connected_set(int)
   */
  public Collection<Collection<Item>> get_connected_sets(int p_net_no) {
    Collection<Collection<Item>> result = new LinkedList<>();
    if (p_net_no <= 0) {
      return result;
    }
    SortedSet<Item> items_to_handle = new TreeSet<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = this.item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Connectable && curr_item.contains_net(p_net_no)) {
        items_to_handle.add(curr_item);
      }
    }
    Iterator<Item> it2 = items_to_handle.iterator();
    while (it2.hasNext()) {
      Item curr_item = it2.next();
      Collection<Item> next_connected_set = curr_item.get_connected_set(p_net_no);
      result.add(next_connected_set);
      items_to_handle.removeAll(next_connected_set);
      it2 = items_to_handle.iterator();
    }
    return result;
  }

  /**
   * Returns all search tree objects on the specified layer that overlap with the given shape.
   *
   * <p>This method performs efficient spatial queries using the board's search tree structures.
   * It returns all objects whose shapes intersect with the query shape.
   *
   * @param p_shape the convex shape to check for overlaps
   * @param p_layer the layer index to search; use negative value to search all layers
   * @return a set of all overlapping search tree objects
   *
   * @see #overlapping_items_with_clearance(ConvexShape, int, int[], int)
   */
  public Set<SearchTreeObject> overlapping_objects(ConvexShape p_shape, int p_layer) {
    return this.search_tree_manager
        .get_default_tree()
        .overlapping_objects(p_shape, p_layer);
  }

  /**
   * Returns items that overlap with the shape including required clearance spacing.
   *
   * <p>This method finds all items whose expanded shapes (including clearance distance)
   * intersect with the query shape. The clearance matrix defines minimum spacing requirements
   * between different clearance classes.
   *
   * <p>Notes:
   * <ul>
   *   <li>May return items that are nearly overlapping but don't overlap with exact calculation</li>
   *   <li>Nets in p_ignore_net_nos are excluded from clearance checking</li>
   *   <li>Layer can be negative to search all layers</li>
   * </ul>
   *
   * @param p_shape the convex shape to check
   * @param p_layer the layer index to search; use negative value to search all layers
   * @param p_ignore_net_nos array of net numbers to exclude from results
   * @param p_clearance_class the clearance class index for determining spacing requirements
   * @return a set of items with clearance violations
   *
   * @see #check_shape(Area, int, int[], int)
   */
  public Set<Item> overlapping_items_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos,
      int p_clearance_class) {
    ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
    return default_tree.overlapping_items_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_clearance_class);
  }

  /**
   * Returns all items on the specified layer that overlap with the given area.
   *
   * <p>Since areas can be non-convex and may contain holes, this method splits the area
   * into convex tiles and queries each separately, then combines the results.
   *
   * @param p_area the area (potentially with holes) to check for overlaps
   * @param p_layer the layer index to search; use negative value to search all layers
   * @return a set of all items that overlap with the area
   *
   * @see #overlapping_objects(ConvexShape, int)
   */
  public Set<Item> overlapping_items(Area p_area, int p_layer) {
    Set<Item> result = new TreeSet<>();
    TileShape[] tile_shapes = p_area.split_to_convex();
    for (int i = 0; i < tile_shapes.length; i++) {
      Set<SearchTreeObject> curr_overlaps = overlapping_objects(tile_shapes[i], p_layer);
      for (SearchTreeObject curr_overlap : curr_overlaps) {
        if (curr_overlap instanceof Item item) {
          result.add(item);
        }
      }
    }
    return result;
  }

  /**
   * Checks if an object with the specified shape can be inserted without clearance violations.
   *
   * <p>This method validates that a proposed shape would not violate clearance requirements
   * with existing board items. It splits non-convex areas into convex tiles and checks each.
   *
   * <p>Validation includes:
   * <ul>
   *   <li>Verifying the shape is within the board's bounding box</li>
   *   <li>Checking clearance requirements against all overlapping items</li>
   *   <li>Respecting net assignments (same-net items may touch)</li>
   * </ul>
   *
   * @param p_shape the area to check (may contain holes)
   * @param p_layer the layer index where the object would be placed
   * @param p_net_no_arr array of net numbers the object belongs to
   * @param p_cl_class the clearance class index
   * @return true if the shape can be inserted without violations, false otherwise
   *
   * @see #check_trace_shape(TileShape, int, int[], int, Set)
   */
  public boolean check_shape(Area p_shape, int p_layer, int[] p_net_no_arr, int p_cl_class) {
    TileShape[] tiles = p_shape.split_to_convex();
    ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
    for (int i = 0; i < tiles.length; i++) {
      TileShape curr_shape = tiles[i];
      if (!curr_shape.is_contained_in(bounding_box)) {
        return false;
      }
      Set<SearchTreeObject> obstacles = new TreeSet<>();
      default_tree.overlapping_objects_with_clearance(curr_shape, p_layer, p_net_no_arr, p_cl_class, obstacles);
      for (SearchTreeObject curr_ob : obstacles) {
        boolean is_obstacle = true;
        for (int j = 0; j < p_net_no_arr.length; j++) {
          if (!curr_ob.is_obstacle(p_net_no_arr[j])) {
            is_obstacle = false;
          }
        }
        if (is_obstacle) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks if a trace shape can be inserted without clearance violations.
   *
   * <p>This specialized method validates trace shapes with additional rules beyond basic
   * clearance checking. It handles special cases like:
   * <ul>
   *   <li>Contact pins - traces may touch pins they connect to</li>
   *   <li>Foreign pins - treated as obstacles to avoid acid traps</li>
   *   <li>Tie pins - traces of foreign nets at multi-net pins may be allowed</li>
   * </ul>
   *
   * <p>If contact_pins is provided, only pins in that set are treated as valid contacts.
   * Other pins are considered obstacles even if they're on the same net.
   *
   * @param p_shape the trace shape to check
   * @param p_layer the layer index where the trace would be placed
   * @param p_net_no_arr array of net numbers the trace belongs to
   * @param p_cl_class the clearance class index
   * @param p_contact_pins set of pins the trace is allowed to contact; may be null
   * @return true if the trace can be inserted without violations, false otherwise
   *
   * @see #check_polyline_trace(Polyline, int, int, int[], int)
   */
  public boolean check_trace_shape(TileShape p_shape, int p_layer, int[] p_net_no_arr, int p_cl_class,
      Set<Pin> p_contact_pins) {
    if (!p_shape.is_contained_in(bounding_box)) {
      return false;
    }
    ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();
    Collection<TreeEntry> tree_entries = new LinkedList<>();
    int[] ignore_net_nos = new int[0];
    if (default_tree.is_clearance_compensation_used()) {
      default_tree.overlapping_tree_entries(p_shape, p_layer, ignore_net_nos, tree_entries);
    } else {
      default_tree.overlapping_tree_entries_with_clearance(p_shape, p_layer, ignore_net_nos, p_cl_class, tree_entries);
    }
    for (TreeEntry curr_tree_entry : tree_entries) {
      if (!(curr_tree_entry.object instanceof Item curr_item)) {
        continue;
      }
      if (p_contact_pins != null) {
        if (p_contact_pins.contains(curr_item)) {
          continue;
        }
        if (curr_item instanceof Pin) {
          // The contact pins of the trace should be contained in p_ignore_items.
          // Other pins are handled as obstacles to avoid acid traps.
          return false;
        }
      }
      boolean is_obstacle = true;
      for (int i = 0; i < p_net_no_arr.length; i++) {
        if (!curr_item.is_trace_obstacle(p_net_no_arr[i])) {
          is_obstacle = false;
        }
      }
      if (is_obstacle && (curr_item instanceof PolylineTrace) && p_contact_pins != null) {
        // check for traces of foreign nets at tie pins, which will be ignored inside
        // the pin shape
        TileShape intersection = null;
        for (Pin curr_contact_pin : p_contact_pins) {
          if (curr_contact_pin.net_count() <= 1 || !curr_contact_pin.shares_net(curr_item)) {
            continue;
          }
          if (intersection == null) {
            TileShape obstacle_trace_shape = curr_item.get_tile_shape(curr_tree_entry.shape_index_in_object);
            intersection = p_shape.intersection(obstacle_trace_shape);
          }
          TileShape pin_shape = curr_contact_pin.get_tile_shape_on_layer(p_layer);
          if (pin_shape.contains_approx(intersection)) {
            is_obstacle = false;
            break;
          }
        }
      }
      if (is_obstacle) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if a polyline trace can be inserted without clearance violations.
   *
   * <p>This convenience method creates a temporary trace from the polyline parameters and
   * validates each segment shape. It automatically identifies contact pins at the trace
   * endpoints and includes them in the validation.
   *
   * @param p_polyline the geometric path of the proposed trace
   * @param p_layer the layer index where the trace would be placed
   * @param p_pen_half_width the half-width (radius) of the trace
   * @param p_net_no_arr array of net numbers the trace belongs to
   * @param p_clearance_class the clearance class index
   * @return true if the entire trace can be inserted without violations, false otherwise
   *
   * @see #check_trace_shape(TileShape, int, int[], int, Set)
   */
  public boolean check_polyline_trace(Polyline p_polyline, int p_layer, int p_pen_half_width, int[] p_net_no_arr,
      int p_clearance_class) {
    Trace tmp_trace = new PolylineTrace(p_polyline, p_layer, p_pen_half_width, p_net_no_arr, p_clearance_class, 0, 0,
        FixedState.NOT_FIXED, this);
    Set<Pin> contact_pins = tmp_trace.touching_pins_at_end_corners();
    for (int i = 0; i < tmp_trace.tile_shape_count(); i++) {
      if (!this.check_trace_shape(tmp_trace.get_tile_shape(i), p_layer, p_net_no_arr, p_clearance_class,
          contact_pins)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the number of layers on this board.
   *
   * <p>Layers are indexed from 0 to layer_count - 1. The count includes all signal,
   * power, and ground layers defined in the layer structure.
   *
   * @return the total number of layers
   *
   * @see LayerStructure
   */
  public int get_layer_count() {
    return layer_structure.arr.length;
  }

  /**
   * Renders all board items to a graphics context for display.
   *
   * <p>This method draws all visible board items in order of their draw priority. Items
   * are drawn from lowest to middle priority, allowing proper layering of visual elements.
   * The graphics context controls layer visibility and rendering settings.
   *
   * <p>Drawing is resilient to concurrent modifications - if the item list changes during
   * rendering (e.g., when running logfiles interactively), the method returns gracefully
   * rather than throwing exceptions.
   *
   * @param p_graphics the Java Graphics object to render to
   * @param p_graphics_context the board graphics context containing layer visibility and settings
   *
   * @see Drawable
   * @see GraphicsContext
   */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    if (p_graphics_context == null) {
      return;
    }

    // draw all items on the board
    for (int curr_priority = Drawable.MIN_DRAW_PRIORITY; curr_priority <= Drawable.MIDDLE_DRAW_PRIORITY; curr_priority++) {
      Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
      for (;;) {
        try {
          Item curr_item = (Item) item_list.read_object(it);
          if (curr_item == null) {
            break;
          }
          if (curr_item.get_draw_priority() == curr_priority) {
            curr_item.draw(p_graphics, p_graphics_context);
          }
        } catch (ConcurrentModificationException _) {
          // may happen when window are changed interactively while running a logfile
          return;
        }
      }
    }
  }

  /**
   * Returns items whose shapes contain the specified point on the given layer.
   *
   * <p>This method performs spatial picking - finding all items at a specific location.
   * It's used for interactive selection, connection detection, and geometry queries.
   * An optional filter can restrict results to specific item types.
   *
   * @param p_location the point to pick items at
   * @param p_layer the layer index to search; use negative value to search all layers
   * @param p_filter optional filter to restrict item types; may be null to include all items
   * @return a set of items containing the specified point
   *
   * @see ItemSelectionFilter
   */
  public Set<Item> pick_items(Point p_location, int p_layer, ItemSelectionFilter p_filter) {
    TileShape point_shape = TileShape.get_instance(p_location);
    Collection<SearchTreeObject> overlaps = overlapping_objects(point_shape, p_layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject curr_object : overlaps) {
      if (curr_object instanceof Item item) {
        result.add(item);
      }
    }
    if (p_filter != null) {
      result = p_filter.filter(result);
    }
    return result;
  }

  /**
   * Checks if a point lies within the board's bounding box.
   *
   * <p>Points outside the bounding box are considered invalid for item placement.
   * This check should be performed before inserting items to ensure they're within
   * the valid board area.
   *
   * @param p_point the point to check
   * @return true if the point is within the board's bounding box, false otherwise
   *
   * @see #get_bounding_box()
   */
  public boolean contains(Point p_point) {
    return p_point.is_contained_in(bounding_box);
  }

  /**
   * Returns the minimum clearance required between items of two clearance classes.
   *
   * <p>The clearance matrix defines minimum spacing requirements between different types
   * of objects. Classes are indexed starting from 0. The clearance value may vary by layer.
   *
   * @param p_class_1 the first clearance class index
   * @param p_class_2 the second clearance class index
   * @param p_layer the layer index where clearance is checked
   * @return the minimum required clearance distance, or 0 if rules are not defined
   *
   * @see BoardRules
   * @see app.freerouting.rules.ClearanceMatrix
   */
  public int clearance_value(int p_class_1, int p_class_2, int p_layer) {
    if (rules == null || rules.clearance_matrix == null) {
      return 0;
    }
    return rules.clearance_matrix.get_value(p_class_1, p_class_2, p_layer, true);
  }

  /**
   * Returns the largest trace half-width on the board.
   *
   * <p>This value is updated as traces are added and is useful for optimization
   * algorithms and rendering calculations.
   *
   * @return the maximum trace half-width (radius) in board units
   *
   * @see #get_min_trace_half_width()
   */
  public int get_max_trace_half_width() {
    return max_trace_half_width;
  }

  /**
   * Returns the smallest trace half-width on the board.
   *
   * <p>This value is updated as traces are added and is useful for optimization
   * algorithms and sampling calculations.
   *
   * @return the minimum trace half-width (radius) in board units
   *
   * @see #get_max_trace_half_width()
   */
  public int get_min_trace_half_width() {
    return min_trace_half_width;
  }

  /**
   * Returns the rectangular boundary encompassing the entire board.
   *
   * @return the board's bounding box
   *
   * @see #get_bounding_box(Collection)
   */
  public IntBox get_bounding_box() {
    return bounding_box;
  }

  /**
   * Calculates a bounding box that contains all items in the collection.
   *
   * <p>This is useful for determining the extent of a selection or group of items.
   *
   * @param p_item_list the collection of items to bound
   * @return the smallest box containing all items
   */
  public IntBox get_bounding_box(Collection<Item> p_item_list) {
    IntBox result = IntBox.EMPTY;
    for (Item curr_item : p_item_list) {
      result = result.union(curr_item.bounding_box());
    }
    return result;
  }

  /**
   * Resets the graphics update region to empty.
   *
   * <p>Call this after the display has been fully updated to clear the dirty region.
   *
   * @see #get_graphics_update_box()
   * @see #join_graphics_update_box(IntBox)
   */
  public void reset_graphics_update_box() {
    update_box = IntBox.EMPTY;
  }

  /**
   * Returns the rectangular region that needs graphics updates.
   *
   * <p>This region accumulates areas affected by board modifications since the last
   * reset. Graphical interfaces can use this to perform efficient partial repaints.
   *
   * @return the box containing all areas needing display updates
   *
   * @see #reset_graphics_update_box()
   */
  public IntBox get_graphics_update_box() {
    return update_box;
  }

  /**
   * Enlarges the graphics update region to include the specified box.
   *
   * <p>Call this whenever a board modification affects a screen region to mark
   * it for repainting.
   *
   * @param p_box the region to add to the update area
   */
  public void join_graphics_update_box(IntBox p_box) {
    if (update_box == null) {
      reset_graphics_update_box();
    }
    update_box = update_box.union(p_box);
  }

  /**
   * Enables observer notifications for board changes.
   *
   * <p>When active, observers are notified of all item insertions, deletions, and modifications.
   * This is used to synchronize external systems (like host design databases) with board changes.
   *
   * @see #end_notify_observers()
   * @see #observers_active()
   */
  public void start_notify_observers() {
    if ((communication != null) && (this.communication.observers != null)) {
      communication.observers.activate();
    }
  }

  /**
   * Disables observer notifications for board changes.
   *
   * <p>Call this when performing batch operations where individual change notifications
   * would be inefficient or unnecessary.
   *
   * @see #start_notify_observers()
   */
  public void end_notify_observers() {
    if ((communication != null) && (this.communication.observers != null)) {
      communication.observers.deactivate();
    }
  }

  /**
   * Checks if observer notifications are currently enabled.
   *
   * @return true if observers are being notified of changes, false otherwise
   *
   * @see #start_notify_observers()
   */
  public boolean observers_active() {
    boolean result;
    if ((communication != null) && (this.communication.observers != null)) {
      result = communication.observers.is_active();
    } else {
      result = false;
    }
    return result;
  }

  /**
   * Converts an obstacle area into a conductive area with the specified net.
   *
   * <p>This method transforms a non-conductive obstacle into a conduction area that
   * belongs to a net, making it an active part of the circuit. The original obstacle
   * is removed and replaced with a new conduction area preserving the geometry and
   * placement.
   *
   * @param p_area the obstacle area to convert
   * @param p_net_no the net number to assign to the conduction area
   * @return the newly created conduction area
   *
   * @see ConductionArea
   * @see ObstacleArea
   */
  public Connectable make_conductive(ObstacleArea p_area, int p_net_no) {
    Item new_item;
    Area curr_area = p_area.get_relative_area();
    int layer = p_area.get_layer();
    FixedState fixed_state = p_area.get_fixed_state();
    Vector translation = p_area.get_translation();
    double rotation = p_area.get_rotation_in_degree();
    boolean side_changed = p_area.get_side_changed();
    int[] net_no_arr = new int[1];
    net_no_arr[0] = p_net_no;
    new_item = new ConductionArea(curr_area, layer, translation, rotation, side_changed, net_no_arr,
        p_area.clearance_class_no(), 0, p_area.get_component_no(), p_area.name, true, fixed_state, this);
    remove_item(p_area);
    insert_item(new_item);
    return (Connectable) new_item;
  }

  /**
   * Inserts an item into the board database with full integration.
   *
   * <p>This low-level method handles complete item insertion including:
   * <ul>
   *   <li>Validating clearance class is within valid range</li>
   *   <li>Adding the item to the undoable object list</li>
   *   <li>Inserting into spatial search trees for efficient queries</li>
   *   <li>Notifying observers of the new item</li>
   *   <li>Calling additional_update_after_change for derived class processing</li>
   * </ul>
   *
   * <p>Most code should use the specific insert methods (insert_trace, insert_via, etc.)
   * rather than calling this directly.
   *
   * @param p_item the item to insert; if null, no action is taken
   *
   * @see #additional_update_after_change(Item)
   */
  public void insert_item(Item p_item) {
    if (p_item == null) {
      return;
    }

    if (rules == null || rules.clearance_matrix == null || p_item.clearance_class_no() < 0
        || p_item.clearance_class_no() >= rules.clearance_matrix.get_class_count()) {
      FRLogger.warn("LayeredBoard.insert_item: clearance_class no out of range");
      p_item.set_clearance_class_no(0);
    }
    p_item.board = this;
    item_list.insert(p_item);
    search_tree_manager.insert(p_item);
    if ((communication != null) && (communication.observers != null)) {
      communication.observers.notify_new(p_item);
    }
    additional_update_after_change(p_item);
  }

  /**
   * Hook method for derived classes to perform additional updates after item changes.
   *
   * <p>This stub method is overridden in RoutingBoard to maintain the autorouter database
   * when items are inserted, modified, or deleted. The base implementation does nothing.
   *
   * @param p_item the item that was changed (inserted, modified, or deleted)
   *
   * @see RoutingBoard#additional_update_after_change(Item)
   */
  public void additional_update_after_change(Item p_item) {
  }

  /**
   * Restores the board to the state at the previous snapshot.
   *
   * <p>This method undoes the most recent changes by restoring items from the undo stack.
   * It handles both component changes and item changes, updating search trees and notifying
   * observers of all modifications.
   *
   * <p>The operation includes:
   * <ul>
   *   <li>Restoring components to their previous state</li>
   *   <li>Removing items that were added since the snapshot</li>
   *   <li>Restoring items that were removed since the snapshot</li>
   *   <li>Updating search trees and observer notifications</li>
   *   <li>Recording changed nets in the provided set (if not null)</li>
   * </ul>
   *
   * @param p_changed_nets if not null, net numbers of affected items are added to this set
   * @return true if undo was performed, false if no more undo is possible
   *
   * @see #redo(Set)
   * @see #generate_snapshot()
   */
  public boolean undo(Set<Integer> p_changed_nets) {
    this.components.undo(this.communication.observers);
    Collection<UndoableObjects.Storable> cancelled_objects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restored_objects = new LinkedList<>();
    boolean result = item_list.undo(cancelled_objects, restored_objects);
    // update the search trees
    Iterator<UndoableObjects.Storable> it = cancelled_objects.iterator();
    while (it.hasNext()) {
      Item curr_item = (Item) it.next();
      search_tree_manager.remove(curr_item);

      // let the observers synchronize the deletion
      if ((communication != null) && (communication.observers != null)) {
        communication.observers.notify_deleted(curr_item);
      }

      if (p_changed_nets != null) {
        for (int i = 0; i < curr_item.net_count(); i++) {
          p_changed_nets.add(curr_item.get_net_no(i));
        }
      }
    }
    it = restored_objects.iterator();
    while (it.hasNext()) {
      Item curr_item = (Item) it.next();
      curr_item.board = this;
      search_tree_manager.insert(curr_item);
      curr_item.clear_autoroute_info();
      // let the observers know the insertion
      if ((communication != null) && (communication.observers != null)) {
        communication.observers.notify_new(curr_item);
      }
      if (p_changed_nets != null) {
        for (int i = 0; i < curr_item.net_count(); i++) {
          p_changed_nets.add(curr_item.get_net_no(i));
        }
      }
    }
    return result;
  }

  /**
   * Restores the board to the state before the last undo operation.
   *
   * <p>This method re-applies changes that were undone, moving forward in the undo/redo
   * history. It performs the inverse operation of undo, restoring items and notifying
   * observers appropriately.
   *
   * <p>The operation includes:
   * <ul>
   *   <li>Restoring components to their redo state</li>
   *   <li>Removing items that were restored by undo</li>
   *   <li>Restoring items that were cancelled by undo</li>
   *   <li>Updating search trees and observer notifications</li>
   *   <li>Recording changed nets in the provided set (if not null)</li>
   * </ul>
   *
   * @param p_changed_nets if not null, net numbers of affected items are added to this set
   * @return true if redo was performed, false if no more redo is possible
   *
   * @see #undo(Set)
   * @see #generate_snapshot()
   */
  public boolean redo(Set<Integer> p_changed_nets) {
    this.components.redo(this.communication.observers);
    Collection<UndoableObjects.Storable> cancelled_objects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restored_objects = new LinkedList<>();
    boolean result = item_list.redo(cancelled_objects, restored_objects);
    // update the search trees
    Iterator<UndoableObjects.Storable> it = cancelled_objects.iterator();
    while (it.hasNext()) {
      Item curr_item = (Item) it.next();
      search_tree_manager.remove(curr_item);
      // let the observers synchronize the deletion
      communication.observers.notify_deleted(curr_item);
      if (p_changed_nets != null) {
        for (int i = 0; i < curr_item.net_count(); i++) {
          p_changed_nets.add(curr_item.get_net_no(i));
        }
      }
    }
    it = restored_objects.iterator();
    while (it.hasNext()) {
      Item curr_item = (Item) it.next();
      curr_item.board = this;
      search_tree_manager.insert(curr_item);
      curr_item.clear_autoroute_info();
      // let the observers know the insertion
      if ((communication != null) && (communication.observers != null)) {
        communication.observers.notify_new(curr_item);
      }
      if (p_changed_nets != null) {
        for (int i = 0; i < curr_item.net_count(); i++) {
          p_changed_nets.add(curr_item.get_net_no(i));
        }
      }
    }
    return result;
  }

  /**
   * Creates a snapshot of the current board state for undo/redo support.
   *
   * <p>Snapshots capture the complete state of the board including all items and components.
   * This allows the board to be restored to this point later using undo. Snapshots should
   * be generated before significant operations that users may want to reverse.
   *
   * <p>Best practices:
   * <ul>
   *   <li>Create snapshots before major editing operations</li>
   *   <li>Don't create snapshots too frequently (performance impact)</li>
   *   <li>Balance snapshot frequency with undo granularity needs</li>
   * </ul>
   *
   * @see #undo(Set)
   * @see #pop_snapshot()
   */
  public void generate_snapshot() {
    item_list.generate_snapshot();
    components.generate_snapshot();
  }

  /**
   * Removes the most recent snapshot from the undo stack without restoring it.
   *
   * <p>This is useful for discarding a snapshot after an operation completes successfully
   * and you want to consolidate multiple operations into a single undo point.
   *
   * @return true if a snapshot was removed, false if the undo stack is empty
   *
   * @see #generate_snapshot()
   */
  public boolean pop_snapshot() {
    return item_list.pop_snapshot();
  }

  /**
   * Finds a trace that ends at the specified location without any normal contact.
   *
   * <p>A trace tail is a routing artifact - a trace segment with no electrical connection
   * at one endpoint. This method searches for traces that end at the given point but have
   * no connecting items (pins, vias, or other traces) there.
   *
   * <p>Trace tails typically result from incomplete routing or editing operations and
   * should generally be removed to clean up the board.
   *
   * @param p_location the point to check for trace tails
   * @param p_layer the layer to search on
   * @param p_net_no_arr array of net numbers to match
   * @return the trace tail found at the location, or null if none exists
   *
   * @see #remove_if_cycle(Trace)
   */
  public Trace get_trace_tail(Point p_location, int p_layer, int[] p_net_no_arr) {
    TileShape point_shape = TileShape.get_instance(p_location);
    Collection<SearchTreeObject> found_items = overlapping_objects(point_shape, p_layer);
    for (SearchTreeObject curr_ob : found_items) {
      if (curr_ob instanceof Trace curr_trace) {
        if (!curr_trace.nets_equal(p_net_no_arr)) {
          continue;
        }
        if (curr_trace
            .first_corner()
            .equals(p_location)) {
          Collection<Item> contacts = curr_trace.get_start_contacts();
          if (contacts.isEmpty()) {
            return curr_trace;
          }
        }
        if (curr_trace
            .last_corner()
            .equals(p_location)) {
          Collection<Item> contacts = curr_trace.get_end_contacts();
          if (contacts.isEmpty()) {
            return curr_trace;
          }
        }
      }
    }
    return null;
  }

  /**
   * Detects and removes a trace if it forms a routing cycle (loop).
   *
   * <p>Routing cycles are invalid topology where a trace connects back to itself, creating
   * a loop. This method detects such cycles and removes the problematic trace along with any
   * tails created by the removal.
   *
   * <p>The cleanup process:
   * <ol>
   *   <li>Verifies the trace forms a cycle</li>
   *   <li>Records which endpoints have tails before removal</li>
   *   <li>Removes the cycle trace and its connections</li>
   *   <li>Removes any new tails created at endpoints that didn't have tails before</li>
   * </ol>
   *
   * @param p_trace the trace to check and potentially remove
   * @return true if the trace was a cycle and was removed, false otherwise
   *
   * @see Trace#is_cycle()
   * @see #get_trace_tail(Point, int, int[])
   */
  public boolean remove_if_cycle(Trace p_trace) {
    if (!p_trace.is_on_the_board()) {
      return false;
    }
    if (!p_trace.is_cycle()) {
      return false;
    }
    FRLogger.debug("BasicBoard.remove_if_cycle: removing cycle trace id=" + p_trace.get_id_no() + " (net #"
        + (p_trace.net_count() > 0 ? p_trace.get_net_no(0) : -1) + ")");
    // Remove tails at the endpoints after removing the cycle,
    // if there was no tail before.
    boolean[] tail_at_endpoint_before;
    Point[] end_corners;
    int curr_layer = p_trace.get_layer();
    int[] curr_net_no_arr = p_trace.net_no_arr;
    end_corners = new Point[2];
    end_corners[0] = p_trace.first_corner();
    end_corners[1] = p_trace.last_corner();
    tail_at_endpoint_before = new boolean[2];
    for (int i = 0; i < 2; i++) {
      Trace tail = get_trace_tail(end_corners[i], curr_layer, curr_net_no_arr);
      tail_at_endpoint_before[i] = tail != null;
    }
    Set<Item> connection_items = p_trace.get_connection_items();
    this.remove_items(connection_items);
    for (int i = 0; i < 2; i++) {
      if (!tail_at_endpoint_before[i]) {
        Trace tail = get_trace_tail(end_corners[i], curr_layer, curr_net_no_arr);
        if (tail != null) {
          remove_items(tail.get_connection_items());
        }
      }
    }
    return true;
  }

  /**
   * Custom deserialization handler to rebuild transient structures.
   *
   * <p>After deserialization, this method reconstructs the search tree manager and
   * reinserts all items into the search trees. This ensures spatial queries work
   * correctly after the board is loaded from disk.
   *
   * @param p_stream the input stream containing serialized board data
   * @throws IOException if I/O errors occur during deserialization
   * @throws ClassNotFoundException if class definitions are missing
   */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    // insert the items on the board into the search trees
    search_tree_manager = new SearchTreeManager(this);
    for (Item curr_item : this.get_items()) {
      curr_item.board = this;
      search_tree_manager.insert(curr_item);
    }
  }

  /**
   * Removes all traces and vias from the board, leaving only components and obstacles.
   *
   * <p>This destructive operation is typically used for:
   * <ul>
   *   <li>Clearing routing to start fresh</li>
   *   <li>Preparing for re-routing</li>
   *   <li>Testing and benchmarking</li>
   * </ul>
   *
   * <p><strong>Warning:</strong> This operation cannot be undone through the normal undo
   * mechanism. Consider generating a snapshot before calling if undo capability is needed.
   *
   * @see #get_traces()
   * @see #get_vias()
   */
  public void delete_all_tracks_and_vias() {
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_item = item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof Trace) {
        // delete the trace
        item_list.delete(curr_item);
      }
      if (curr_item instanceof Via) {
        // delete the via
        item_list.delete(curr_item);
      }
    }
  }

  /**
   * Checks if any routable items exist on layers that are inactive in the autoroute control.
   *
   * <p>This diagnostic method warns if traces exist on layers that won't be considered during
   * autorouting. This could indicate configuration issues or unintended layer settings.
   *
   * @param p_ctrl the autoroute control settings defining which layers are active
   *
   * @see AutorouteControl
   */
  public void areThereItemsOnInactiveLayer(AutorouteControl p_ctrl) {
    if (this.get_layer_count() > 2) {
      boolean hasSomethingOnInactiveLayer = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = this.item_list.start_read_object();
      for (;;) {
        UndoableObjects.Storable curr_ob = this.item_list.read_object(it);
        if (curr_ob == null) {
          break;
        }
        if (curr_ob instanceof PolylineTrace curr_item) {
          // This is a connectable item, like PolylineTrace or Pin
          if (!p_ctrl.layer_active[curr_item.get_layer()]) {
            hasSomethingOnInactiveLayer = true;
            FRLogger.warn("There is an item on an inactive layer.");
            break;
          }
        }
      }
    }
  }

  /**
   * Counts traces that have segments not aligned to 45-degree angles.
   *
   * <p>Many design rules prefer traces aligned to 45-degree multiples (0°, 45°, 90°, etc.)
   * for aesthetic and manufacturing reasons. This metric identifies traces that violate
   * this preference.
   *
   * <p>Useful for:
   * <ul>
   *   <li>Design rule checking</li>
   *   <li>Routing quality assessment</li>
   *   <li>Identifying manual routing that needs cleanup</li>
   * </ul>
   *
   * @return the number of traces with non-45-degree segments
   *
   * @see Polyline#is_multiple_of_45_degree()
   */
  public int getNon45DegreeTraceCount() {
    int count = 0;
    for (Item curr_ob : get_items()) {
      if (curr_ob instanceof PolylineTrace curr_trace) {
        if (!curr_trace.polyline().is_multiple_of_45_degree()) {
          ++count;
        }
      }
    }
    return count;
  }
}