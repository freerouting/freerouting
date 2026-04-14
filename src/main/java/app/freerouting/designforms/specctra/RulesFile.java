package app.freerouting.designforms.specctra;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.designforms.specctra.io.RulesReader;
import app.freerouting.designforms.specctra.io.RulesWriter;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ViaInfo;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * File for saving the board rules, so that they can be restored after the Board
 * is creates anew from the host system.
 */
public class RulesFile {

  private RulesFile() {
  }

  /**
   * @deprecated Use {@link RulesWriter#write(BasicBoard, OutputStream, String)} instead.
   */
  @Deprecated
  public static void write(GuiBoardManager p_board_handling, OutputStream p_output_stream, String p_design_name) {
    try {
      RulesWriter.write(p_board_handling.get_routing_board(), p_output_stream, p_design_name);
    } catch (IOException e) {
      FRLogger.error("RulesFile.write: unable to write rules to file", e);
    }
  }

  /**
   * @deprecated Use {@link RulesReader#read(InputStream, String, BasicBoard)} instead.
   */
  @Deprecated
  public static boolean read(InputStream p_input_stream, String p_design_name, GuiBoardManager p_board_handling) {
    return RulesReader.read(p_input_stream, p_design_name, p_board_handling.get_routing_board());
  }

  private static void write_rules(WriteScopeParameter p_par, String p_design_name) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("rules PCB ");
    p_par.file.write(p_design_name);
    Structure.write_snap_angle(p_par.file, p_par.board.rules.get_trace_angle_restriction());
    if (p_par.autoroute_settings != null) {
      AutorouteSettings.write_scope(p_par.file, p_par.autoroute_settings, p_par.board.layer_structure,
          p_par.identifier_type);
    }
    // write the default rule using 0 as default layer.
    Rule.write_default_rule(p_par, 0);
    // write the via padstacks
    for (int i = 1; i <= p_par.board.library.padstacks.count(); i++) {
      Padstack curr_padstack = p_par.board.library.padstacks.get(i);
      if (p_par.board.library.get_via_padstack(curr_padstack.name) != null) {
        Library.write_padstack_scope(p_par, curr_padstack);
      }
    }
    Network.write_via_infos(p_par.board.rules, p_par.file, p_par.identifier_type);
    Network.write_via_rules(p_par.board.rules, p_par.file, p_par.identifier_type);
    Network.write_net_classes(p_par);
    p_par.file.end_scope();
  }

  private static void add_rules(Collection<Rule> p_rules, BasicBoard p_board, String p_layer_name) {
    int layer_no = -1;
    if (p_layer_name != null) {
      layer_no = p_board.layer_structure.get_no(p_layer_name);
      if (layer_no < 0) {
        FRLogger.warn("RulesFile.add_rules: layer not found at '" + p_layer_name + "'");
      }
    }
    CoordinateTransform coordinate_transform = p_board.communication.coordinate_transform;
    String string_quote = p_board.communication.specctra_parser_info.string_quote;
    for (Rule curr_rule : p_rules) {
      if (curr_rule instanceof Rule.WidthRule rule1) {
        double wire_width = rule1.value;
        int trace_halfwidth = (int) Math.round(coordinate_transform.dsn_to_board(wire_width) / 2);
        if (layer_no < 0) {
          p_board.rules.set_default_trace_half_widths(trace_halfwidth);
        } else {
          p_board.rules.set_default_trace_half_width(layer_no, trace_halfwidth);
        }
      } else if (curr_rule instanceof Rule.ClearanceRule rule) {
        Structure.set_clearance_rule(rule, layer_no, coordinate_transform, p_board.rules, string_quote);
      }
    }
  }

  private static boolean add_layer_rules(IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      Object next_token = p_scanner.next_token();
      if (!(next_token instanceof String layer_string)) {
        FRLogger.warn("RulesFile.add_layer_rules: String expected at '" + p_scanner.get_scope_identifier() + "'");
        return false;
      }
      next_token = p_scanner.next_token();
      while (next_token != Keyword.CLOSED_BRACKET) {
        if (next_token != Keyword.OPEN_BRACKET) {
          FRLogger.warn("RulesFile.add_layer_rules: ( expected at '" + p_scanner.get_scope_identifier() + "'");
          return false;
        }
        next_token = p_scanner.next_token();
        if (next_token == Keyword.RULE) {
          Collection<Rule> curr_rules = Rule.read_scope(p_scanner);
          add_rules(curr_rules, p_board, layer_string);
        } else {
          ScopeKeyword.skip_scope(p_scanner);
        }
        next_token = p_scanner.next_token();
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("RulesFile.add_layer_rules: IO error scanning file", e);
      return false;
    }
  }

  private static boolean read_via_info(IJFlexScanner p_scanner, BasicBoard p_board) {
    ViaInfo curr_via_info = Network.read_via_info(p_scanner, p_board);
    if (curr_via_info == null) {
      return false;
    }
    ViaInfo existing_via = p_board.rules.via_infos.get(curr_via_info.get_name());
    if (existing_via != null) {
      // replace existing via info
      p_board.rules.via_infos.remove(existing_via);
    }
    p_board.rules.via_infos.add(curr_via_info);
    return true;
  }

  private static boolean read_via_rule(IJFlexScanner p_scanner, BasicBoard p_board) {
    Collection<String> via_rule = Network.read_via_rule(p_scanner, p_board);
    if (via_rule == null) {
      return false;
    }
    Network.add_via_rule(via_rule, p_board);
    return true;
  }

  private static boolean read_net_class(IJFlexScanner p_scanner, LayerStructure p_layer_structure, BasicBoard p_board) {
    NetClass curr_class = NetClass.read_scope(p_scanner);
    if (curr_class == null) {
      return false;
    }
    Network.insert_net_class(curr_class, p_layer_structure, p_board, p_board.communication.coordinate_transform, false);
    return true;
  }
}