package app.freerouting.designforms.specctra;

import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polygon;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Class for reading and writing wiring scopes from dsn-files. */
class Wiring extends ScopeKeyword {

  /** Creates a new instance of Wiring */
  public Wiring() {
    super("wiring");
  }

  public static void write_scope(WriteScopeParameter p_par) throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("wiring");
    // write the wires
    Collection<Trace> board_wires = p_par.board.get_traces();
    Iterator<Trace> it = board_wires.iterator();
    while (it.hasNext()) {
      write_wire_scope(p_par, it.next());
    }
    Collection<Via> board_vias = p_par.board.get_vias();
    for (Via curr_via : board_vias) {
      write_via_scope(p_par, curr_via);
    }
    // write the conduction areas
    Iterator<UndoableObjects.UndoableObjectNode> it2 = p_par.board.item_list.start_read_object();
    for (; ; ) {
      Object curr_ob = p_par.board.item_list.read_object(it2);
      if (curr_ob == null) {
        break;
      }
      if (!(curr_ob instanceof app.freerouting.board.ConductionArea)) {
        continue;
      }
      app.freerouting.board.ConductionArea curr_area =
          (app.freerouting.board.ConductionArea) curr_ob;
      if (!(p_par.board.layer_structure.arr[curr_area.get_layer()].is_signal)) {
        // This conduction areas arw written in the structure scope.
        continue;
      }
      write_conduction_area_scope(p_par, (app.freerouting.board.ConductionArea) curr_ob);
    }
    p_par.file.end_scope();
  }

  private static void write_via_scope(WriteScopeParameter p_par, Via p_via)
      throws java.io.IOException {
    app.freerouting.library.Padstack via_padstack = p_via.get_padstack();
    FloatPoint via_location = p_via.get_center().to_float();
    double[] via_coor = p_par.coordinate_transform.board_to_dsn(via_location);
    int net_no;
    app.freerouting.rules.Net via_net;
    if (p_via.net_count() > 0) {
      net_no = p_via.get_net_no(0);
      via_net = p_par.board.rules.nets.get(net_no);
    } else {
      net_no = 0;
      via_net = null;
    }
    p_par.file.start_scope();
    p_par.file.write("via ");
    p_par.identifier_type.write(via_padstack.name, p_par.file);
    for (int i = 0; i < via_coor.length; ++i) {
      p_par.file.write(" ");
      p_par.file.write((Double.valueOf(via_coor[i])).toString());
    }
    if (via_net != null) {
      write_net(via_net, p_par.file, p_par.identifier_type);
    }
    Rule.write_item_clearance_class(
        p_par.board.rules.clearance_matrix.get_name(p_via.clearance_class_no()),
        p_par.file,
        p_par.identifier_type);
    write_fixed_state(p_par.file, p_via.get_fixed_state());
    p_par.file.end_scope();
  }

  private static void write_wire_scope(WriteScopeParameter p_par, Trace p_wire)
      throws java.io.IOException {
    if (!(p_wire instanceof PolylineTrace)) {
      FRLogger.warn("Wiring.write_wire_scope: trace type not yet implemented");
      return;
    }
    PolylineTrace curr_wire = (PolylineTrace) p_wire;
    int layer_no = curr_wire.get_layer();
    app.freerouting.board.Layer board_layer = p_par.board.layer_structure.arr[layer_no];
    Layer curr_layer = new Layer(board_layer.name, layer_no, board_layer.is_signal);
    double wire_width = p_par.coordinate_transform.board_to_dsn(2 * curr_wire.get_half_width());
    app.freerouting.rules.Net wire_net = null;
    if (curr_wire.net_count() > 0) {
      wire_net = p_par.board.rules.nets.get(curr_wire.get_net_no(0));
    }
    if (wire_net == null) {
      FRLogger.warn("Wiring.write_wire_scope: net not found");
      return;
    }
    p_par.file.start_scope();
    p_par.file.write("wire");

    if (p_par.compat_mode) {
      Point[] corner_arr = curr_wire.polyline().corner_arr();
      FloatPoint[] float_corner_arr = new FloatPoint[corner_arr.length];
      for (int i = 0; i < corner_arr.length; ++i) {
        float_corner_arr[i] = corner_arr[i].to_float();
      }
      double[] coors = p_par.coordinate_transform.board_to_dsn(float_corner_arr);
      PolygonPath curr_path = new PolygonPath(curr_layer, wire_width, coors);
      curr_path.write_scope(p_par.file, p_par.identifier_type);
    } else {
      double[] coors = p_par.coordinate_transform.board_to_dsn(curr_wire.polyline().arr);
      PolylinePath curr_path = new PolylinePath(curr_layer, wire_width, coors);
      curr_path.write_scope(p_par.file, p_par.identifier_type);
    }
    write_net(wire_net, p_par.file, p_par.identifier_type);
    Rule.write_item_clearance_class(
        p_par.board.rules.clearance_matrix.get_name(p_wire.clearance_class_no()),
        p_par.file,
        p_par.identifier_type);
    write_fixed_state(p_par.file, curr_wire.get_fixed_state());
    p_par.file.end_scope();
  }

  private static void write_conduction_area_scope(
      WriteScopeParameter p_par, app.freerouting.board.ConductionArea p_conduction_area)
      throws java.io.IOException {
    int net_count = p_conduction_area.net_count();
    if (net_count != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count");
      return;
    }
    app.freerouting.rules.Net curr_net =
        p_par.board.rules.nets.get(p_conduction_area.get_net_no(0));
    app.freerouting.geometry.planar.Area curr_area = p_conduction_area.get_area();
    int layer_no = p_conduction_area.get_layer();
    app.freerouting.board.Layer board_layer = p_par.board.layer_structure.arr[layer_no];
    Layer conduction_layer = new Layer(board_layer.name, layer_no, board_layer.is_signal);
    app.freerouting.geometry.planar.Shape boundary_shape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (curr_area instanceof app.freerouting.geometry.planar.Shape) {
      boundary_shape = (app.freerouting.geometry.planar.Shape) curr_area;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundary_shape = curr_area.get_border();
      holes = curr_area.get_holes();
    }
    p_par.file.start_scope();
    p_par.file.write("wire ");
    Shape dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, conduction_layer);
    if (dsn_shape != null) {
      dsn_shape.write_scope(p_par.file, p_par.identifier_type);
    }
    for (int i = 0; i < holes.length; ++i) {
      Shape dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[i], conduction_layer);
      dsn_hole.write_hole_scope(p_par.file, p_par.identifier_type);
    }
    write_net(curr_net, p_par.file, p_par.identifier_type);
    Rule.write_item_clearance_class(
        p_par.board.rules.clearance_matrix.get_name(p_conduction_area.clearance_class_no()),
        p_par.file,
        p_par.identifier_type);
    p_par.file.end_scope();
  }

  private static void write_net(
      app.freerouting.rules.Net p_net, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(");
    Net.write_net_id(p_net, p_file, p_identifier_type);
    p_file.write(")");
  }

  private static void write_fixed_state(IndentFileWriter p_file, FixedState p_fixed_state)
      throws java.io.IOException {
    if (p_fixed_state == FixedState.UNFIXED) {
      return;
    }
    p_file.new_line();
    p_file.write("(type ");
    if (p_fixed_state == FixedState.SHOVE_FIXED) {
      p_file.write("shove_fixed)");
    } else if (p_fixed_state == FixedState.SYSTEM_FIXED) {
      p_file.write("fix)");
    } else {
      p_file.write("protect)");
    }
  }

  private static Collection<app.freerouting.rules.Net> get_subnets(
      Net.Id p_net_id, app.freerouting.rules.BoardRules p_rules) {
    Collection<app.freerouting.rules.Net> found_nets = new LinkedList<app.freerouting.rules.Net>();
    if (p_net_id != null) {
      if (p_net_id.subnet_number > 0) {
        app.freerouting.rules.Net found_net =
            p_rules.nets.get(p_net_id.name, p_net_id.subnet_number);
        if (found_net != null) {
          found_nets.add(found_net);
        }
      } else {
        found_nets = p_rules.nets.get(p_net_id.name);
      }
    }
    return found_nets;
  }

  private static boolean via_exists(
      IntPoint p_location,
      app.freerouting.library.Padstack p_padstack,
      int[] p_net_no_arr,
      app.freerouting.board.BasicBoard p_board) {
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    int from_layer = p_padstack.from_layer();
    int to_layer = p_padstack.to_layer();
    Collection<Item> picked_items = p_board.pick_items(p_location, p_padstack.from_layer(), filter);
    for (Item curr_item : picked_items) {
      Via curr_via = (Via) curr_item;
      if (curr_via.nets_equal(p_net_no_arr)
          && curr_via.get_center().equals(p_location)
          && curr_via.first_layer() == from_layer
          && curr_via.last_layer() == to_layer) {
        return true;
      }
    }
    return false;
  }

  static app.freerouting.board.FixedState calc_fixed(IJFlexScanner p_scanner) {
    try {
      app.freerouting.board.FixedState result = app.freerouting.board.FixedState.UNFIXED;
      Object next_token = p_scanner.next_token();
      if (next_token == SHOVE_FIXED) {
        result = app.freerouting.board.FixedState.SHOVE_FIXED;
      } else if (next_token == FIX) {
        result = app.freerouting.board.FixedState.SYSTEM_FIXED;
      } else if (next_token != NORMAL) {
        result = app.freerouting.board.FixedState.USER_FIXED;
      }
      next_token = p_scanner.next_token();
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Wiring.is_fixed: ) expected");
        return app.freerouting.board.FixedState.UNFIXED;
      }
      return result;
    } catch (java.io.IOException e) {
      FRLogger.error("Wiring.is_fixed: IO error scanning file", e);
      return app.freerouting.board.FixedState.UNFIXED;
    }
  }

  /** Reads a net_id. The subnet_number of the net_id will be 0, if no subneet_number was found. */
  private static Net.Id read_net_id(IJFlexScanner p_scanner) {
    try {
      int subnet_number = 0;
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      Object next_token = p_scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Wiring:read_net_id: String expected");
        return null;
      }
      String net_name = (String) next_token;
      next_token = p_scanner.next_token();
      if (next_token instanceof Integer) {
        subnet_number = (Integer) next_token;
        next_token = p_scanner.next_token();
      }
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Wiring.read_net_id: closing bracket expected");
      }
      return new Net.Id(net_name, subnet_number);
    } catch (java.io.IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  public boolean read_scope(ReadScopeParameter p_par) {
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.warn("Wiring.read_scope: IO error scanning file");
        return false;
      }
      if (next_token == null) {
        FRLogger.warn("Wiring.read_scope: unexpected end of file");
        return false;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      boolean read_ok = true;
      if (prev_token == OPEN_BRACKET) {
        if (next_token == WIRE) {
          read_wire_scope(p_par);
        } else if (next_token == VIA) {
          read_ok = read_via_scope(p_par);
        } else {
          skip_scope(p_par.scanner);
        }
      }
      if (!read_ok) {
        return false;
      }
    }
    RoutingBoard board = p_par.board_handling.get_routing_board();
    for (int i = 1; i <= board.rules.nets.max_net_no(); ++i) {
      try {
        board.normalize_traces(i);
      } catch (Exception e) {
        FRLogger.warn("The normalization of net '" + board.rules.nets.get(i).name + "' failed.");
      }
    }
    return true;
  }

  private Item read_wire_scope(ReadScopeParameter p_par) {
    Net.Id net_id = null;
    String clearance_class_name = null;
    app.freerouting.board.FixedState fixed = app.freerouting.board.FixedState.UNFIXED;
    Path path = null; // Used, if a trace is read.
    Shape border_shape = null; // Used, if a conduction area is read.
    Collection<Shape> hole_list = new LinkedList<Shape>();
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
        return null;
      }
      if (next_token == null) {
        FRLogger.warn("Wiring.read_wire_scope: unexpected end of file");
        return null;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == OPEN_BRACKET) {
        if (next_token == POLYGON_PATH) {
          path = Shape.read_polygon_path_scope(p_par.scanner, p_par.layer_structure);
        } else if (next_token == POLYLINE_PATH) {
          path = Shape.read_polyline_path_scope(p_par.scanner, p_par.layer_structure);
        } else if (next_token == RECTANGLE) {

          border_shape = Shape.read_rectangle_scope(p_par.scanner, p_par.layer_structure);
        } else if (next_token == POLYGON) {

          border_shape = Shape.read_polygon_scope(p_par.scanner, p_par.layer_structure);
        } else if (next_token == CIRCLE) {

          border_shape = Shape.read_circle_scope(p_par.scanner, p_par.layer_structure);
        } else if (next_token == WINDOW) {
          Shape hole_shape = Shape.read_scope(p_par.scanner, p_par.layer_structure);
          hole_list.add(hole_shape);
          // overread the closing bracket
          try {
            next_token = p_par.scanner.next_token();
          } catch (java.io.IOException e) {
            FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
            return null;
          }
          if (next_token != CLOSED_BRACKET) {
            FRLogger.warn("Wiring.read_wire_scope: closing bracket expected");
            return null;
          }
        } else if (next_token == NET) {
          net_id = read_net_id(p_par.scanner);
        } else if (next_token == CLEARANCE_CLASS) {
          clearance_class_name = DsnFile.read_string_scope(p_par.scanner);
        } else if (next_token == TYPE) {
          fixed = calc_fixed(p_par.scanner);
        } else {
          skip_scope(p_par.scanner);
        }
      }
    }
    if (path == null && border_shape == null) {
      FRLogger.warn("Wiring.read_wire_scope: shape missing");
      return null;
    }
    RoutingBoard board = p_par.board_handling.get_routing_board();

    app.freerouting.rules.NetClass net_class = board.rules.get_default_net_class();
    Collection<app.freerouting.rules.Net> found_nets = get_subnets(net_id, board.rules);
    int[] net_no_arr = new int[found_nets.size()];
    int curr_index = 0;
    for (app.freerouting.rules.Net curr_net : found_nets) {
      net_no_arr[curr_index] = curr_net.net_number;
      net_class = curr_net.get_class();
      ++curr_index;
    }
    int clearance_class_no = -1;
    if (clearance_class_name != null) {
      clearance_class_no = board.rules.clearance_matrix.get_no(clearance_class_name);
    }
    int layer_no;
    int half_width;
    if (path != null) {
      layer_no = path.layer.no;
      half_width = (int) Math.round(p_par.coordinate_transform.dsn_to_board(path.width / 2));
    } else {
      layer_no = border_shape.layer.no;
      half_width = 0;
    }
    if (layer_no < 0 || layer_no >= board.get_layer_count()) {
      FRLogger.warn("Wiring.read_wire_scope: unexpected layer ");
      if (path != null) {
        FRLogger.warn(path.layer.name);
      } else {
        FRLogger.warn(border_shape.layer.name);
      }
      return null;
    }

    IntBox bounding_box = board.get_bounding_box();

    Item result = null;
    if (border_shape != null) {
      if (clearance_class_no < 0) {
        clearance_class_no =
            net_class.default_item_clearance_classes.get(
                app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.AREA);
      }
      Collection<Shape> area = new LinkedList<Shape>();
      area.add(border_shape);
      area.addAll(hole_list);
      app.freerouting.geometry.planar.Area conduction_area =
          Shape.transform_area_to_board(area, p_par.coordinate_transform);
      result =
          board.insert_conduction_area(
              conduction_area, layer_no, net_no_arr, clearance_class_no, false, fixed);
    } else if (path instanceof PolygonPath) {
      if (clearance_class_no < 0) {
        clearance_class_no =
            net_class.default_item_clearance_classes.get(
                app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      IntPoint[] corner_arr = new IntPoint[path.coordinate_arr.length / 2];
      double[] curr_point = new double[2];
      for (int i = 0; i < corner_arr.length; ++i) {
        curr_point[0] = path.coordinate_arr[2 * i];
        curr_point[1] = path.coordinate_arr[2 * i + 1];
        FloatPoint curr_corner = p_par.coordinate_transform.dsn_to_board(curr_point);
        if (!bounding_box.contains(curr_corner)) {
          FRLogger.warn("Wiring.read_wire_scope: wire corner outside board");
          return null;
        }
        corner_arr[i] = curr_corner.round();
      }

      Polygon polygon = new Polygon(corner_arr);

      // if it doesn't have two different points, it's not a valid polygon, so we must skip it
      if (polygon.corner_array().length >= 2) {
        Polyline trace_polyline = new Polyline(polygon);
        // Traces are not yet normalized here because cycles may be removed premature.
        result =
            board.insert_trace_without_cleaning(
                trace_polyline, layer_no, half_width, net_no_arr, clearance_class_no, fixed);
      }
    } else if (path instanceof PolylinePath) {
      if (clearance_class_no < 0) {
        clearance_class_no =
            net_class.default_item_clearance_classes.get(
                app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      Line[] line_arr = new Line[path.coordinate_arr.length / 4];
      double[] curr_point = new double[2];
      for (int i = 0; i < line_arr.length; ++i) {
        curr_point[0] = path.coordinate_arr[4 * i];
        curr_point[1] = path.coordinate_arr[4 * i + 1];
        FloatPoint curr_a = p_par.coordinate_transform.dsn_to_board(curr_point);
        curr_point[0] = path.coordinate_arr[4 * i + 2];
        curr_point[1] = path.coordinate_arr[4 * i + 3];
        FloatPoint curr_b = p_par.coordinate_transform.dsn_to_board(curr_point);
        line_arr[i] = new Line(curr_a.round(), curr_b.round());
      }
      Polyline trace_polyline = new Polyline(line_arr);
      result =
          board.insert_trace_without_cleaning(
              trace_polyline, layer_no, half_width, net_no_arr, clearance_class_no, fixed);
    } else {
      FRLogger.warn("Wiring.read_wire_scope: unexpected Path subclass");
      return null;
    }
    if (result != null && result.net_count() == 0) {
      try_correct_net(result);
    }
    return result;
  }

  /**
   * Maybe trace of type turret without net in Mentor design. Try to assig the net by calculating
   * the overlaps.
   */
  private void try_correct_net(Item p_item) {
    if (!(p_item instanceof Trace)) {
      return;
    }
    Trace curr_trace = (Trace) p_item;
    java.util.Set<Item> contacts = curr_trace.get_normal_contacts(curr_trace.first_corner(), true);
    contacts.addAll(curr_trace.get_normal_contacts(curr_trace.last_corner(), true));
    int corrected_net_no = 0;
    for (Item curr_contact : contacts) {
      if (curr_contact.net_count() == 1) {
        corrected_net_no = curr_contact.get_net_no(0);
        break;
      }
    }
    if (corrected_net_no != 0) {
      p_item.assign_net_no(corrected_net_no);
    }
  }

  private boolean read_via_scope(ReadScopeParameter p_par) {
    try {
      app.freerouting.board.FixedState fixed = app.freerouting.board.FixedState.UNFIXED;
      // read the padstack name
      Object next_token = p_par.scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Wiring.read_via_scope: padstack name expected");
        return false;
      }
      String padstack_name = (String) next_token;
      // read the location
      double[] location = new double[2];
      for (int i = 0; i < 2; ++i) {
        next_token = p_par.scanner.next_token();
        if (next_token instanceof Double) {
          location[i] = ((Double) next_token).doubleValue();
        } else if (next_token instanceof Integer) {
          location[i] = ((Integer) next_token).intValue();
        } else {
          FRLogger.warn("Wiring.read_via_scope: number expected");
          return false;
        }
      }
      Net.Id net_id = null;
      String clearance_class_name = null;
      for (; ; ) {
        Object prev_token = next_token;
        next_token = p_par.scanner.next_token();
        if (next_token == null) {
          FRLogger.warn("Wiring.read_via_scope: unexpected end of file");
          return false;
        }
        if (next_token == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prev_token == OPEN_BRACKET) {
          if (next_token == NET) {
            net_id = read_net_id(p_par.scanner);
          } else if (next_token == CLEARANCE_CLASS) {
            clearance_class_name = DsnFile.read_string_scope(p_par.scanner);
          } else if (next_token == TYPE) {
            fixed = calc_fixed(p_par.scanner);
          } else {
            skip_scope(p_par.scanner);
          }
        }
      }
      RoutingBoard board = p_par.board_handling.get_routing_board();
      app.freerouting.library.Padstack curr_padstack = board.library.padstacks.get(padstack_name);
      if (curr_padstack == null) {
        FRLogger.warn("Wiring.read_via_scope: via padstack not found");
        return false;
      }
      app.freerouting.rules.NetClass net_class = board.rules.get_default_net_class();
      Collection<app.freerouting.rules.Net> found_nets = get_subnets(net_id, board.rules);
      if (net_id != null && found_nets.isEmpty()) {
        FRLogger.warn("Wiring.read_via_scope: net with name '" + net_id.name + "' not found");
      }
      int[] net_no_arr = new int[found_nets.size()];
      int curr_index = 0;
      for (app.freerouting.rules.Net curr_net : found_nets) {
        net_no_arr[curr_index] = curr_net.net_number;
        net_class = curr_net.get_class();
      }
      int clearance_class_no = -1;
      if (clearance_class_name != null) {
        clearance_class_no = board.rules.clearance_matrix.get_no(clearance_class_name);
      }
      if (clearance_class_no < 0) {
        clearance_class_no =
            net_class.default_item_clearance_classes.get(
                app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.VIA);
      }
      IntPoint board_location = p_par.coordinate_transform.dsn_to_board(location).round();
      if (via_exists(board_location, curr_padstack, net_no_arr, board)) {
        FRLogger.warn(
            "Multiple via skipped at (" + board_location.x + ", " + board_location.y + ")");
      } else {
        boolean attach_allowed = p_par.via_at_smd_allowed && curr_padstack.attach_allowed;
        board.insert_via(
            curr_padstack, board_location, net_no_arr, clearance_class_no, fixed, attach_allowed);
      }
      return true;
    } catch (java.io.IOException e) {
      FRLogger.error("Wiring.read_via_scope: IO error scanning file", e);
      return false;
    }
  }
}
