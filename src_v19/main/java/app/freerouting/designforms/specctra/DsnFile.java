package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.TestLevel;
import app.freerouting.board.Trace;
import app.freerouting.datastructures.IdNoGenerator;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.IBoardHandling;
import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;

/** Class for reading and writing dsn-files. */
public class DsnFile {

  static final char CLASS_CLEARANCE_SEPARATOR = '-';

  private DsnFile() {}

  /**
   * Creates a routing board from a Specctra DSN file. The parameters p_item_observers and
   * p_item_id_no_generator are used, in case the board is embedded into a host system. Returns
   * false, if an error occurred.
   */
  public static ReadResult read(
      InputStream p_input_stream,
      IBoardHandling p_board_handling,
      BoardObservers p_observers,
      IdNoGenerator p_item_id_no_generator,
      TestLevel p_test_level) {
    IJFlexScanner scanner = new SpecctraDsnFileReader(p_input_stream);
    Object curr_token;
    for (int i = 0; i < 3; ++i) {
      try {
        curr_token = scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("DsnFile.read: IO error scanning file", e);
        return ReadResult.ERROR;
      }
      boolean keyword_ok = true;
      if (i == 0) {
        keyword_ok = (curr_token == Keyword.OPEN_BRACKET);
      } else if (i == 1) {
        keyword_ok = (curr_token == Keyword.PCB_SCOPE);
        scanner.yybegin(SpecctraDsnFileReader.NAME); // to overread the name of the pcb for i = 2
      }
      if (!keyword_ok) {
        FRLogger.warn("DsnFile.read: the input file is not in a Specctra DSN file format. It must be a text file starting with the '(pcb' character array.");
        return ReadResult.ERROR;
      }
    }
    ReadScopeParameter read_scope_par =
        new ReadScopeParameter(
            scanner, p_board_handling, p_observers, p_item_id_no_generator, p_test_level);
    boolean read_ok = Keyword.PCB_SCOPE.read_scope(read_scope_par);
    ReadResult result;
    if (read_ok) {
      result = ReadResult.OK;
      if (read_scope_par.autoroute_settings == null) {
        // look for power planes with incorrect layer type and adjust autoroute parameters
        adjust_plane_autoroute_settings(p_board_handling);
      }
    } else if (!read_scope_par.board_outline_ok) {
      result = ReadResult.OUTLINE_MISSING;
    } else {
      result = ReadResult.ERROR;
    }
    // app.freerouting.tests.Validate.check("after reading dsn",
    // read_scope_par.board_handling.get_routing_board());
    return result;
  }

  /**
   * Sets contains_plane to true for nets with a conduction_area covering a large part of a signal
   * layer, if that layer does not contain any traces This is useful in case the layer type was not
   * set correctly to plane in the dsn-file. Returns true, if something was changed.
   */
  private static boolean adjust_plane_autoroute_settings(
      IBoardHandling p_board_handling) {
    BasicBoard routing_board = p_board_handling.get_routing_board();
    app.freerouting.board.LayerStructure board_layer_structure = routing_board.layer_structure;
    if (board_layer_structure.arr.length <= 2) {
      return false;
    }
    for (app.freerouting.board.Layer curr_layer : board_layer_structure.arr) {
      if (!curr_layer.is_signal) {
        return false;
      }
    }
    boolean[] layer_contains_wires_arr = new boolean[board_layer_structure.arr.length];
    boolean[] changed_layer_arr = new boolean[board_layer_structure.arr.length];
    for (int i = 0; i < layer_contains_wires_arr.length; ++i) {
      layer_contains_wires_arr[i] = false;
      changed_layer_arr[i] = false;
    }
    Collection<ConductionArea> conduction_area_list =
        new LinkedList<>();
    Collection<Item> item_list = routing_board.get_items();
    for (Item curr_item : item_list) {
      if (curr_item instanceof Trace) {
        int curr_layer = ((Trace) curr_item).get_layer();
        layer_contains_wires_arr[curr_layer] = true;
      } else if (curr_item instanceof ConductionArea) {
        conduction_area_list.add((ConductionArea) curr_item);
      }
    }
    boolean nothing_changed = true;

    BoardOutline board_outline = routing_board.get_outline();
    double board_area = 0;
    for (int i = 0; i < board_outline.shape_count(); ++i) {
      TileShape[] curr_piece_arr =
          board_outline.get_shape(i).split_to_convex();
      if (curr_piece_arr != null) {
        for (TileShape curr_piece : curr_piece_arr) {
          board_area += curr_piece.area();
        }
      }
    }
    for (ConductionArea curr_conduction_area : conduction_area_list) {
      int layer_no = curr_conduction_area.get_layer();
      if (layer_contains_wires_arr[layer_no]) {
        continue;
      }
      app.freerouting.board.Layer curr_layer = routing_board.layer_structure.arr[layer_no];
      if (!curr_layer.is_signal
          || layer_no == 0
          || layer_no == board_layer_structure.arr.length - 1) {
        continue;
      }
      TileShape[] convex_pieces =
          curr_conduction_area.get_area().split_to_convex();
      double curr_area = 0;
      for (TileShape curr_piece : convex_pieces) {
        curr_area += curr_piece.area();
      }
      if (curr_area < 0.5 * board_area) {
        // skip conduction areas not covering most of the board
        continue;
      }

      for (int i = 0; i < curr_conduction_area.net_count(); ++i) {
        app.freerouting.rules.Net curr_net =
            routing_board.rules.nets.get(curr_conduction_area.get_net_no(i));
        curr_net.set_contains_plane(true);
        nothing_changed = false;
      }

      changed_layer_arr[layer_no] = true;
      if (curr_conduction_area.get_fixed_state().ordinal()
          < FixedState.USER_FIXED.ordinal()) {
        curr_conduction_area.set_fixed_state(FixedState.USER_FIXED);
      }
    }
    if (nothing_changed) {
      return false;
    }
    // Adjust the layer preferred directions in the autoroute settings.
    // and deactivate the changed layers.
    app.freerouting.interactive.AutorouteSettings autoroute_settings =
        p_board_handling.get_settings().autoroute_settings;
    int layer_count = routing_board.get_layer_count();
    boolean curr_preferred_direction_is_horizontal =
        autoroute_settings.get_preferred_direction_is_horizontal(0);
    for (int i = 0; i < layer_count; ++i) {
      if (changed_layer_arr[i]) {
        autoroute_settings.set_layer_active(i, false);
      } else if (autoroute_settings.get_layer_active(i)) {
        autoroute_settings.set_preferred_direction_is_horizontal(
            i, curr_preferred_direction_is_horizontal);
        curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal;
      }
    }
    return true;
  }

  /**
   * Writes p_board to a text file in the Specctra dsn format. Returns false, if the write failed.
   * If p_compat_mode is true, only standard specctra dsn scopes are written, so that any host
   * system with a specctra interface can read them.
   */
  public static boolean write(
      BoardHandling p_board_handling,
      OutputStream p_file,
      String p_design_name,
      boolean p_compat_mode) {
    // app.freerouting.tests.Validate.check("before writing dsn", p_board);
    IndentFileWriter output_file = new IndentFileWriter(p_file);

    try {
      write_pcb_scope(p_board_handling, output_file, p_design_name, p_compat_mode);
    } catch (IOException e) {
      FRLogger.error("unable to write dsn file", e);
      return false;
    }
    try {
      output_file.close();
    } catch (IOException e) {
      FRLogger.error("unable to close dsn file", e);
      return false;
    }
    return true;
  }

  private static void write_pcb_scope(
      BoardHandling p_board_handling,
      IndentFileWriter p_file,
      String p_design_name,
      boolean p_compat_mode)
      throws IOException {
    BasicBoard routing_board = p_board_handling.get_routing_board();
    WriteScopeParameter write_scope_parameter =
        new WriteScopeParameter(
            routing_board,
            p_board_handling.settings.autoroute_settings,
            p_file,
            routing_board.communication.specctra_parser_info.string_quote,
            routing_board.communication.coordinate_transform,
            p_compat_mode);

    p_file.start_scope(false);
    p_file.write("pcb ");
    write_scope_parameter.identifier_type.write(p_design_name, p_file);
    Parser.write_scope(
        write_scope_parameter.file,
        write_scope_parameter.board.communication.specctra_parser_info,
        write_scope_parameter.identifier_type,
        false);
    Resolution.write_scope(p_file, routing_board.communication);
    Unit.write_scope(p_file, routing_board.communication.unit);
    Structure.write_scope(write_scope_parameter);
    Placement.write_scope(write_scope_parameter);
    Library.write_scope(write_scope_parameter);
    PartLibrary.write_scope(write_scope_parameter);
    Network.write_scope(write_scope_parameter);
    Wiring.write_scope(write_scope_parameter);
    p_file.end_scope();
  }

  static boolean read_on_off_scope(IJFlexScanner p_scanner) {
    try {
      Object next_token = p_scanner.next_token();
      boolean result = false;
      if (next_token == Keyword.ON) {
        result = true;
      } else if (next_token != Keyword.OFF) {
        FRLogger.warn("DsnFile.read_boolean: Keyword.OFF expected at '" + p_scanner.get_scope_identifier() + "'");
      }
      ScopeKeyword.skip_scope(p_scanner);
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_boolean: IO error scanning file", e);
      return false;
    }
  }

  static int read_integer_scope(IJFlexScanner p_scanner) {
    try {
      int value;
      Object next_token = p_scanner.next_token();
      if (next_token instanceof Integer) {
        value = (Integer) next_token;
      } else {
        FRLogger.warn("DsnFile.read_integer_scope: number expected at '" + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_integer_scope: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_integer_scope: IO error scanning file", e);
      return 0;
    }
  }

  static double read_float_scope(IJFlexScanner p_scanner) {
    try {
      double value;
      Object next_token = p_scanner.next_token();
      if (next_token instanceof Double) {
        value = (Double) next_token;
      } else if (next_token instanceof Integer) {
        value = (Integer) next_token;
      } else {
        FRLogger.warn("DsnFile.read_float_scope: number expected at '" + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_float_scope: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      return value;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_float_scope: IO error scanning file", e);
      return 0;
    }
  }

  public static String read_string_scope(IJFlexScanner p_scanner) {
    try {
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      String result = p_scanner.next_string();
      Object next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_string_scope: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  public static String[] read_string_list_scope(IJFlexScanner p_scanner) {
    String[] result = p_scanner.next_string_list();

    if (!p_scanner.next_closing_bracket()) {
      return null;
    }

    return result;
  }

  public enum ReadResult {
    OK,
    OUTLINE_MISSING,
    ERROR
  }
}
