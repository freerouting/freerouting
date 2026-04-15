package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Class for reading and writing dsn-files.
 */
public class DsnFile {

  static final char CLASS_CLEARANCE_SEPARATOR = '-';

  private DsnFile() {
  }

  /**
   * Sets contains_plane to true for nets with a conduction_area covering a large
   * part of a signal layer, if that layer does not contain any traces. This is
   * useful in case the layer type was not set correctly to plane in the dsn-file.
   * Returns true, if something was changed.
   *
   * <p>Called from {@link app.freerouting.io.specctra.DsnReader#readBoard} when the
   * DSN file contains no {@code (autoroute ...)} scope.
   */
  public static boolean adjustPlaneAutorouteSettings(BasicBoard routing_board) {
    if (routing_board == null) {
      return false;
    }
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
    for (int i = 0; i < layer_contains_wires_arr.length; i++) {
      layer_contains_wires_arr[i] = false;
      changed_layer_arr[i] = false;
    }
    Collection<ConductionArea> conduction_area_list = new LinkedList<>();
    Collection<Item> item_list = routing_board.get_items();
    for (Item curr_item : item_list) {
      if (curr_item instanceof Trace trace) {
        int curr_layer = trace.get_layer();
        layer_contains_wires_arr[curr_layer] = true;
      } else if (curr_item instanceof ConductionArea area) {
        conduction_area_list.add(area);
      }
    }
    boolean nothing_changed = true;

    BoardOutline board_outline = routing_board.get_outline();
    double board_area = 0;
    for (int i = 0; i < board_outline.shape_count(); i++) {
      TileShape[] curr_piece_arr = board_outline.get_shape(i).split_to_convex();
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
      if (!curr_layer.is_signal || layer_no == 0
          || layer_no == board_layer_structure.arr.length - 1) {
        continue;
      }
      TileShape[] convex_pieces = curr_conduction_area.get_area().split_to_convex();
      double curr_area = 0;
      for (TileShape curr_piece : convex_pieces) {
        curr_area += curr_piece.area();
      }
      if (curr_area < 0.5 * board_area) {
        continue;
      }
      for (int i = 0; i < curr_conduction_area.net_count(); i++) {
        Net curr_net = routing_board.rules.nets.get(curr_conduction_area.get_net_no(i));
        curr_net.set_contains_plane(true);
        nothing_changed = false;
      }
      changed_layer_arr[layer_no] = true;
      if (curr_conduction_area.get_fixed_state().ordinal() < FixedState.USER_FIXED.ordinal()) {
        curr_conduction_area.set_fixed_state(FixedState.USER_FIXED);
      }
    }
    if (nothing_changed) {
      return false;
    }
    return true;
  }

  static boolean read_on_off_scope(IJFlexScanner p_scanner) {
    try {
      Object next_token = p_scanner.next_token();
      boolean result = false;
      if (next_token == Keyword.ON) {
        result = true;
      } else if (next_token != Keyword.OFF) {
        FRLogger.warn("DsnFile.read_boolean: Keyword.OFF expected at '"
            + p_scanner.get_scope_identifier() + "'");
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
      if (next_token instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn("DsnFile.read_integer_scope: number expected at '"
            + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_integer_scope: closing bracket expected at '"
            + p_scanner.get_scope_identifier() + "'");
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
      if (next_token instanceof Double double1) {
        value = double1;
      } else if (next_token instanceof Integer integer) {
        value = integer;
      } else {
        FRLogger.warn("DsnFile.read_float_scope: number expected at '"
            + p_scanner.get_scope_identifier() + "'");
        return 0;
      }
      next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_float_scope: closing bracket expected at '"
            + p_scanner.get_scope_identifier() + "'");
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
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      String result = p_scanner.next_string();
      Object next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("DsnFile.read_string_scope: closing bracket expected at '"
            + p_scanner.get_scope_identifier() + "'");
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
    OK, OUTLINE_MISSING, ERROR
  }
}
