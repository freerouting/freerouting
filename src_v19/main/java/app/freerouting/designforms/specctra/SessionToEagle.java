package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.library.Padstack;
import app.freerouting.logger.FRLogger;

import javax.swing.JFrame;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;

/** Transforms a Specctra session file into an Eagle script file. */
public class SessionToEagle extends JFrame {

  /** The function for scanning the session file */
  private final IJFlexScanner scanner;
  /** The generated Eagle script file. */
  private final OutputStreamWriter out_file;
  /**
   * Some information is read from the board, because it is not contained in the specctra session
   * file.
   */
  private final BasicBoard board;
  /** The layer structure in specctra format */
  private final LayerStructure specctra_layer_structure;
  private final app.freerouting.board.Unit unit;
  /** The scale factor for transforming coordinates from the session file to Eagle */
  private final double session_file_scale_denominator;
  /** The scale factor for transforming coordinates from the board to Eagle */
  private final double board_scale_factor;

  SessionToEagle(
      IJFlexScanner p_scanner,
      OutputStreamWriter p_out_file,
      BasicBoard p_board,
      app.freerouting.board.Unit p_unit,
      double p_session_file_scale_dominator,
      double p_board_scale_factor) {
    scanner = p_scanner;
    out_file = p_out_file;
    board = p_board;
    this.specctra_layer_structure = new LayerStructure(p_board.layer_structure);
    unit = p_unit;
    session_file_scale_denominator = p_session_file_scale_dominator;
    board_scale_factor = p_board_scale_factor;
  }

  public static boolean get_instance(
      InputStream p_session,
      OutputStream p_output_stream,
      BasicBoard p_board) {
    if (p_output_stream == null) {
      return false;
    }

    // create a scanner for reading the session_file.

    IJFlexScanner scanner = new SpecctraDsnFileReader(p_session);

    // create a file_writer for the eagle script file.
    OutputStreamWriter file_writer = new OutputStreamWriter(p_output_stream);

    double board_scale_factor = p_board.communication.coordinate_transform.board_to_dsn(1);
    SessionToEagle new_instance =
        new SessionToEagle(
            scanner,
            file_writer,
            p_board,
            p_board.communication.unit,
            p_board.communication.resolution,
            board_scale_factor);

    boolean result;
    try {
      result = new_instance.process_session_scope();
    } catch (IOException e) {
      FRLogger.error("unable to process session scope", e);
      result = false;
    }

    // close files
    try {
      p_session.close();
      file_writer.close();
    } catch (IOException e) {
      FRLogger.error("unable to close files", e);
    }
    return result;
  }

  /** Processes the outmost scope of the session file. Returns false, if an error occurred. */
  private boolean process_session_scope() throws IOException {

    // read the first line of the session file
    Object next_token = null;
    for (int i = 0; i < 3; ++i) {
      next_token = this.scanner.next_token();
      boolean keyword_ok = true;
      if (i == 0) {
        keyword_ok = (next_token == Keyword.OPEN_BRACKET);
      } else if (i == 1) {
        keyword_ok = (next_token == Keyword.SESSION);
        this.scanner.yybegin(
            SpecctraDsnFileReader.NAME); // to overread the name of the pcb for i = 2
      }
      if (!keyword_ok) {
        FRLogger.warn("SessionToEagle.process_session_scope specctra session file format expected");
        return false;
      }
    }

    // Write the header of the eagle script file.

    this.out_file.write("GRID ");
    this.out_file.write(this.unit.toString());
    this.out_file.write("\n");
    this.out_file.write("SET WIRE_BEND 2\n");
    this.out_file.write("SET OPTIMIZING OFF\n");

    // Activate all layers in Eagle.

    for (int i = 0; i < this.board.layer_structure.arr.length; ++i) {
      this.out_file.write("LAYER " + this.get_eagle_layer_string(i) + ";\n");
    }

    this.out_file.write("LAYER 17;\n");
    this.out_file.write("LAYER 18;\n");
    this.out_file.write("LAYER 19;\n");
    this.out_file.write("LAYER 20;\n");
    this.out_file.write("LAYER 23;\n");
    this.out_file.write("LAYER 24;\n");

    // Generate Code to remove the complete route.
    // Write a bounding rectangle with GROUP (Min_X-1 Min_Y-1) (Max_X+1 Max_Y+1);

    IntBox board_bounding_box = this.board.get_bounding_box();

    float min_x = (float) this.board_scale_factor * (board_bounding_box.ll.x - 1);
    float min_y = (float) this.board_scale_factor * (board_bounding_box.ll.y - 1);
    float max_x = (float) this.board_scale_factor * (board_bounding_box.ur.x + 1);
    float max_y = (float) this.board_scale_factor * (board_bounding_box.ur.y + 1);

    this.out_file.write("GROUP (");
    this.out_file.write(String.valueOf(min_x));
    this.out_file.write(" ");
    this.out_file.write(String.valueOf(min_y));
    this.out_file.write(") (");
    this.out_file.write(String.valueOf(max_x));
    this.out_file.write(" ");
    this.out_file.write(String.valueOf(max_y));
    this.out_file.write(");\n");
    this.out_file.write("RIPUP;\n");

    // read the direct subscopes of the session scope
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        // end of file
        return true;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.ROUTES) {
          if (!process_routes_scope()) {
            return false;
          }
        } else if (next_token == Keyword.PLACEMENT_SCOPE) {
          if (!process_placement_scope()) {
            return false;
          }
        } else {
          // overread all scopes except the routes scope for the time being
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    // Wird nur einmal am Ende benoetigt!
    this.out_file.write("RATSNEST\n");
    return true;
  }

  private boolean process_placement_scope() throws IOException {
    // read the component scopes
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        // unexpected end of file
        return false;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {

        if (next_token == Keyword.COMPONENT_SCOPE) {
          if (!process_component_placement()) {
            return false;
          }
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    process_swapped_pins();
    return true;
  }

  private boolean process_component_placement() throws IOException {
    ComponentPlacement component_placement = Component.read_scope(this.scanner);
    if (component_placement == null) {
      return false;
    }
    for (ComponentPlacement.ComponentLocation curr_location : component_placement.locations) {
      this.out_file.write("ROTATE =");
      int rotation = (int) Math.round(curr_location.rotation);
      String rotation_string;
      if (curr_location.is_front) {
        rotation_string = "R" + rotation;
      } else {
        rotation_string = "MR" + rotation;
      }
      this.out_file.write(rotation_string);
      this.out_file.write(" '");
      this.out_file.write(curr_location.name);
      this.out_file.write("';\n");
      this.out_file.write("move '");
      this.out_file.write(curr_location.name);
      this.out_file.write("' (");
      double x_coor = curr_location.coor[0] / this.session_file_scale_denominator;
      this.out_file.write(String.valueOf(x_coor));
      this.out_file.write(" ");
      double y_coor = curr_location.coor[1] / this.session_file_scale_denominator;
      this.out_file.write(String.valueOf(y_coor));
      this.out_file.write(");\n");
    }
    return true;
  }

  private boolean process_routes_scope() throws IOException {
    // read the direct subscopes of the routes scope
    boolean result = true;
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        // unexpected end of file
        return false;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {

        if (next_token == Keyword.NETWORK_OUT) {
          result = process_network_scope();
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean process_network_scope() throws IOException {
    boolean result = true;
    Object next_token = null;
    // read the net scopes
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        // unexpected end of file
        return false;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {

        if (next_token == Keyword.NET) {
          result = process_net_scope();
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean process_net_scope() throws IOException {
    // read the net name
    Object next_token = this.scanner.next_token();
    if (!(next_token instanceof String)) {
      FRLogger.warn("SessionToEagle.process_net_scope: String expected at '" + this.scanner.get_scope_identifier() + "'");
      return false;
    }
    String net_name = (String) next_token;
    this.scanner.set_scope_identifier(net_name);

    // Hier alle nicht gefixten Traces und Vias des Netz mit Namen net_name
    // in der Eagle Datenhaltung loeschen.

    // read the wires and vias of this net
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        // end of file
        return true;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.WIRE) {
          if (!process_wire_scope(net_name)) {
            return false;
          }
        } else if (next_token == Keyword.VIA) {
          if (!process_via_scope(net_name)) {
            return false;
          }
        } else {
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return true;
  }

  private boolean process_wire_scope(String p_net_name) throws IOException {
    PolygonPath wire_path = null;
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      next_token = this.scanner.next_token();
      if (next_token == null) {
        FRLogger.warn("SessionToEagle.process_wire_scope: unexpected end of file at '" + this.scanner.get_scope_identifier() + "'");
        return false;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.POLYGON_PATH) {
          wire_path = Shape.read_polygon_path_scope(this.scanner, this.specctra_layer_structure);
        } else {
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    if (wire_path == null) {
      // conduction areas are skipped
      return true;
    }

    this.out_file.write("CHANGE LAYER ");

    this.out_file.write(wire_path.layer.name);
    this.out_file.write(";\n");

    // WIRE ['signal_name'] [width] [ROUND | FLAT]  [curve | @radius]

    this.out_file.write("WIRE '");

    this.out_file.write(p_net_name);
    this.out_file.write("' ");
    double wire_width = wire_path.width / this.session_file_scale_denominator;
    this.out_file.write(String.valueOf(wire_width));
    this.out_file.write(" (");
    for (int i = 0; i < wire_path.coordinate_arr.length; ++i) {
      double wire_coor = wire_path.coordinate_arr[i] / this.session_file_scale_denominator;
      this.out_file.write(String.valueOf(wire_coor));
      if (i % 2 == 0) {
        this.out_file.write(" ");
      } else {
        if (i == wire_path.coordinate_arr.length - 1) {
          this.out_file.write(")");
        } else {
          this.out_file.write(") (");
        }
      }
    }
    this.out_file.write(";\n");

    return true;
  }

  private boolean process_via_scope(String p_net_name) throws IOException {
    // read the padstack name
    Object next_token = this.scanner.next_token();
    if (!(next_token instanceof String)) {
      FRLogger.warn("SessionToEagle.process_via_scope: padstack name expected at '" + this.scanner.get_scope_identifier() + "'");
      return false;
    }
    String padstack_name = (String) next_token;
    this.scanner.set_scope_identifier(padstack_name);
    // read the location
    double[] location = new double[2];
    for (int i = 0; i < 2; ++i) {
      next_token = this.scanner.next_token();
      if (next_token instanceof Double) {
        location[i] = (Double) next_token;
      } else if (next_token instanceof Integer) {
        location[i] = (Integer) next_token;
      } else {
        FRLogger.warn("SessionToEagle.process_via_scope: number expected at '" + this.scanner.get_scope_identifier() + "'");
        return false;
      }
    }
    next_token = this.scanner.next_token();
    while (next_token == Keyword.OPEN_BRACKET) {
      // skip unknown scopes
      ScopeKeyword.skip_scope(this.scanner);
      next_token = this.scanner.next_token();
    }
    if (next_token != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("SessionToEagle.process_via_scope: closing bracket expected at '" + this.scanner.get_scope_identifier() + "'");
      return false;
    }

    Padstack via_padstack = this.board.library.padstacks.get(padstack_name);

    if (via_padstack == null) {
      FRLogger.warn("SessionToEagle.process_via_scope: via padstack not found at '" + this.scanner.get_scope_identifier() + "'");
      return false;
    }

    ConvexShape via_shape =
        via_padstack.get_shape(via_padstack.from_layer());

    double via_diameter = via_shape.max_width() * this.board_scale_factor;

    // The Padstack name is of the form Name$drill_diameter$from_layer-to_layer

    String[] name_parts = via_padstack.name.split("\\$", 3);

    // example CHANGE DRILL 0.2

    this.out_file.write("CHANGE DRILL ");
    if (name_parts.length > 1) {
      this.out_file.write(name_parts[1]);
    } else {
      // create a default drill, because it is needed in Eagle
      this.out_file.write("0.1");
    }
    this.out_file.write(";\n");

    // VIA ['signal_name'] [diameter] [shape] [layers] [flags]
    // Via Net2 0.6 round 1-4 (20.0, 222.0);
    this.out_file.write("VIA '");

    this.out_file.write(p_net_name);
    this.out_file.write("' ");

    // Durchmesser aus Padstack
    this.out_file.write(String.valueOf(via_diameter));

    // Shape lesen und einsetzen Square / Round / Octagon
    if (via_shape instanceof app.freerouting.geometry.planar.Circle) {
      this.out_file.write(" round ");
    } else if (via_shape instanceof IntOctagon) {
      this.out_file.write(" octagon ");
    } else {
      this.out_file.write(" square ");
    }
    this.out_file.write(get_eagle_layer_string(via_padstack.from_layer()));
    this.out_file.write("-");
    this.out_file.write(get_eagle_layer_string(via_padstack.to_layer()));
    this.out_file.write(" (");
    double x_coor = location[0] / this.session_file_scale_denominator;
    this.out_file.write(String.valueOf(x_coor));
    this.out_file.write(" ");
    double y_coor = location[1] / this.session_file_scale_denominator;
    this.out_file.write(String.valueOf(y_coor));
    this.out_file.write(");\n");

    return true;
  }

  private String get_eagle_layer_string(int p_layer_no) {
    if (p_layer_no < 0 || p_layer_no >= specctra_layer_structure.arr.length) {
      return "0";
    }
    String[] name_pieces = this.specctra_layer_structure.arr[p_layer_no].name.split("#", 2);
    return name_pieces[0];
  }

  private boolean process_swapped_pins() throws IOException {
    for (int i = 1; i <= this.board.components.count(); ++i) {
      if (!process_swapped_pins(i)) {
        return false;
      }
    }
    return true;
  }

  private boolean process_swapped_pins(int p_component_no) throws IOException {
    Collection<Pin> component_pins =
        this.board.get_component_pins(p_component_no);
    boolean component_has_swapped_pins = false;
    for (Pin curr_pin : component_pins) {
      if (curr_pin.get_changed_to() != curr_pin) {
        component_has_swapped_pins = true;
        break;
      }
    }
    if (!component_has_swapped_pins) {
      return true;
    }
    PinInfo[] pin_info_arr = new PinInfo[component_pins.size()];
    int i = 0;
    for (Pin curr_pin : component_pins) {
      pin_info_arr[i] = new PinInfo(curr_pin);
      ++i;
    }
    for (i = 0; i < pin_info_arr.length; ++i) {
      PinInfo curr_pin_info = pin_info_arr[i];
      if (curr_pin_info.curr_changed_to != curr_pin_info.pin.get_changed_to()) {
        PinInfo other_pin_info = null;
        for (int j = i + 1; j < pin_info_arr.length; ++j) {
          if (pin_info_arr[j].pin.get_changed_to() == curr_pin_info.pin) {
            other_pin_info = pin_info_arr[j];
          }
        }
        if (other_pin_info == null) {
          FRLogger.warn("SessuinToEagle.process_swapped_pins: other_pin_info not found at '" + this.scanner.get_scope_identifier() + "'");
          return false;
        }
        write_pin_swap(curr_pin_info.pin, other_pin_info.pin);
        curr_pin_info.curr_changed_to = other_pin_info.pin;
        other_pin_info.curr_changed_to = curr_pin_info.pin;
      }
    }
    return true;
  }

  private void write_pin_swap(Pin p_pin_1, Pin p_pin_2)
      throws IOException {
    int layer_no = Math.max(p_pin_1.first_layer(), p_pin_2.first_layer());
    String layer_name = board.layer_structure.arr[layer_no].name;

    this.out_file.write("CHANGE LAYER ");
    this.out_file.write(layer_name);
    this.out_file.write(";\n");

    double[] location_1 =
        this.board.communication.coordinate_transform.board_to_dsn(p_pin_1.get_center().to_float());
    double[] location_2 =
        this.board.communication.coordinate_transform.board_to_dsn(p_pin_2.get_center().to_float());

    this.out_file.write("PINSWAP ");
    this.out_file.write(" (");
    double curr_coor = location_1[0];
    this.out_file.write(String.valueOf(curr_coor));
    this.out_file.write(" ");
    curr_coor = location_1[1];
    this.out_file.write(String.valueOf(curr_coor));
    this.out_file.write(") (");
    curr_coor = location_2[0];
    this.out_file.write(String.valueOf(curr_coor));
    this.out_file.write(" ");
    curr_coor = location_2[1];
    this.out_file.write(String.valueOf(curr_coor));
    this.out_file.write(");\n");
  }

  private static class PinInfo {
    final Pin pin;
    Pin curr_changed_to;
    PinInfo(Pin p_pin) {
      pin = p_pin;
      curr_changed_to = p_pin;
    }
  }
}
