package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.FixedState;
import app.freerouting.board.RoutingBoard;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.library.Padstack;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Class for reading and writing net network from dsn-files. */
public class Network extends ScopeKeyword {

  /** Creates a new instance of Network */
  public Network() {
    super("network");
  }

  public static void write_scope(WriteScopeParameter p_par) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("network");
    Collection<app.freerouting.board.Pin> board_pins = p_par.board.get_pins();
    for (int i = 1; i <= p_par.board.rules.nets.max_net_no(); ++i) {
      Net.write_scope(p_par, p_par.board.rules.nets.get(i), board_pins);
    }
    write_via_infos(p_par.board.rules, p_par.file, p_par.identifier_type);
    write_via_rules(p_par.board.rules, p_par.file, p_par.identifier_type);
    write_net_classes(p_par);
    p_par.file.end_scope();
  }

  public static void write_via_infos(
      BoardRules p_rules,
      IndentFileWriter p_file,
      IdentifierType p_identifier_type)
      throws IOException {
    for (int i = 0; i < p_rules.via_infos.count(); ++i) {
      ViaInfo curr_via = p_rules.via_infos.get(i);
      p_file.start_scope();
      p_file.write("via ");
      p_file.new_line();
      p_identifier_type.write(curr_via.get_name(), p_file);
      p_file.write(" ");
      p_identifier_type.write(curr_via.get_padstack().name, p_file);
      p_file.write(" ");
      p_identifier_type.write(
          p_rules.clearance_matrix.get_name(curr_via.get_clearance_class()), p_file);
      if (curr_via.attach_smd_allowed()) {
        p_file.write(" attach");
      }
      p_file.end_scope();
    }
  }

  public static void write_via_rules(
      BoardRules p_rules,
      IndentFileWriter p_file,
      IdentifierType p_identifier_type)
      throws IOException {
    for (ViaRule curr_rule : p_rules.via_rules) {
      p_file.start_scope();
      p_file.write("via_rule");
      p_file.new_line();
      p_identifier_type.write(curr_rule.name, p_file);
      for (int i = 0; i < curr_rule.via_count(); ++i) {
        p_file.write(" ");
        p_identifier_type.write(curr_rule.get_via(i).get_name(), p_file);
      }
      p_file.end_scope();
    }
  }

  public static void write_net_classes(WriteScopeParameter p_par) throws IOException {
    for (int i = 0; i < p_par.board.rules.net_classes.count(); ++i) {
      write_net_class(p_par.board.rules.net_classes.get(i), p_par);
    }
  }

  public static void write_net_class(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par)
      throws IOException {
    p_par.file.start_scope();
    p_par.file.write("class ");
    p_par.identifier_type.write(p_net_class.get_name(), p_par.file);
    final int nets_per_row = 8;
    int net_counter = 0;
    for (int i = 1; i <= p_par.board.rules.nets.max_net_no(); ++i) {
      if (p_par.board.rules.nets.get(i).get_class() == p_net_class) {
        if (net_counter % nets_per_row == 0) {
          p_par.file.new_line();
        } else {
          p_par.file.write(" ");
        }
        p_par.identifier_type.write(p_par.board.rules.nets.get(i).name, p_par.file);
        ++net_counter;
      }
    }

    // write the trace clearance class
    Rule.write_item_clearance_class(
        p_par.board.rules.clearance_matrix.get_name(p_net_class.get_trace_clearance_class()),
        p_par.file,
        p_par.identifier_type);

    // write the via rule
    p_par.file.new_line();
    p_par.file.write("(via_rule ");
    p_par.identifier_type.write(p_net_class.get_via_rule().name, p_par.file);
    p_par.file.write(")");

    // write the rules, if they are different from the default rule.
    Rule.write_scope(p_net_class, p_par);

    write_circuit(p_net_class, p_par);

    if (!p_net_class.get_pull_tight()) {
      p_par.file.new_line();
      p_par.file.write("(pull_tight off)");
    }

    if (p_net_class.is_shove_fixed()) {
      p_par.file.new_line();
      p_par.file.write("(shove_fixed on)");
    }

    p_par.file.end_scope();
  }

  private static void write_circuit(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par)
      throws IOException {
    double min_trace_length = p_net_class.get_minimum_trace_length();
    double max_trace_length = p_net_class.get_maximum_trace_length();
    p_par.file.start_scope();
    p_par.file.write("circuit ");
    p_par.file.new_line();
    p_par.file.write("(use_layer");
    int layer_count = p_net_class.layer_count();
    for (int i = 0; i < layer_count; ++i) {
      if (p_net_class.is_active_routing_layer(i)) {
        p_par.file.write(" ");
        p_par.file.write(p_par.board.layer_structure.arr[i].name);
      }
    }
    p_par.file.write(")");
    if (min_trace_length > 0 || max_trace_length > 0) {
      p_par.file.new_line();
      p_par.file.write("(length ");
      double transformed_max_length;
      if (max_trace_length <= 0) {
        transformed_max_length = -1;
      } else {
        transformed_max_length = p_par.coordinate_transform.board_to_dsn(max_trace_length);
      }
      p_par.file.write(String.valueOf(transformed_max_length));
      p_par.file.write(" ");
      double transformed_min_length;
      if (min_trace_length <= 0) {
        transformed_min_length = 0;
      } else {
        transformed_min_length = p_par.coordinate_transform.board_to_dsn(min_trace_length);
      }
      p_par.file.write(String.valueOf(transformed_min_length));
      p_par.file.write(")");
    }
    p_par.file.end_scope();
  }

  /** Creates a sequence of subnets with 2 pins from p_pin_list */
  private static Collection<Collection<Net.Pin>> create_ordered_subnets(
      Collection<Net.Pin> p_pin_list) {
    Collection<Collection<Net.Pin>> result = new LinkedList<>();
    if (p_pin_list.isEmpty()) {
      return result;
    }

    Iterator<Net.Pin> it = p_pin_list.iterator();
    Net.Pin prev_pin = it.next();
    while (it.hasNext()) {
      Net.Pin next_pin = it.next();
      Set<Net.Pin> curr_subnet_pin_list = new TreeSet<>();
      curr_subnet_pin_list.add(prev_pin);
      curr_subnet_pin_list.add(next_pin);
      result.add(curr_subnet_pin_list);
      prev_pin = next_pin;
    }
    return result;
  }

  private static boolean read_net_pins(IJFlexScanner p_scanner, Collection<Net.Pin> p_pin_list) {
    Object next_token;
    String component_name, pin_name;
    while(!(component_name = ((SpecctraDsnFileReader) p_scanner).next_string(true, '-')).isEmpty()) {
      
      try {
        p_scanner.yybegin(SpecctraDsnFileReader.SPEC_CHAR);
        next_token = p_scanner.next_token(); // overread the hyphen
      } catch (IOException e) {
        FRLogger.error("Network.read_net_pins: IO error while scanning file", e);
        return false;
      }
      
      pin_name = p_scanner.next_string(true);
      Net.Pin curr_entry = new Net.Pin(component_name, pin_name);
      p_pin_list.add(curr_entry);
    }

    try {
      next_token = p_scanner.next_token();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_pins: IO error scanning file", e);
      return false;
    }
    if (next_token == null) {
      FRLogger.warn("Network.read_net_pins: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'");
      return false;
    }
    if (next_token != CLOSED_BRACKET) {
      // not end of scope
      FRLogger.warn("Network.read_net_pins: expected closed bracket is missing at '" + p_scanner.get_scope_identifier() + "'");
    }

    return true;
  }

  static ViaInfo read_via_info(
      IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      Object next_token = p_scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      String name = (String) next_token;
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      next_token = p_scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      String padstack_name = (String) next_token;
      p_scanner.set_scope_identifier(padstack_name);
      Padstack via_padstack =
          p_board.library.get_via_padstack(padstack_name);
      if (via_padstack == null) {
        // The padstack may not yet be inserted into the list of via padstacks
        via_padstack = p_board.library.padstacks.get(padstack_name);
        if (via_padstack == null) {
          FRLogger.warn("Network.read_via_info: padstack not found at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        p_board.library.add_via_padstack(via_padstack);
      }
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      next_token = p_scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      int clearance_class = p_board.rules.clearance_matrix.get_no((String) next_token);
      if (clearance_class < 0) {
        // Clearance class not stored, because it is identical to the default clearance class.
        clearance_class = BoardRules.default_clearance_class();
      }
      boolean attach_allowed = false;
      next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        if (next_token != Keyword.ATTACH) {
          FRLogger.warn("Network.read_via_info: Keyword.ATTACH expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        attach_allowed = true;
        next_token = p_scanner.next_token();
        if (next_token != Keyword.CLOSED_BRACKET) {
          FRLogger.warn("Network.read_via_info: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
      }
      return new ViaInfo(
          name, via_padstack, clearance_class, attach_allowed, p_board.rules);
    } catch (IOException e) {
      FRLogger.error("Network.read_via_info: IO error while scanning file", e);
      return null;
    }
  }

  static Collection<String> read_via_rule(
      IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      Collection<String> result = new LinkedList<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnFileReader.NAME);
        Object next_token = p_scanner.next_token();
        if (next_token == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (!(next_token instanceof String)) {
          FRLogger.warn("Network.read_via_rule: string expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        result.add((String) next_token);
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Network.read_via_rule: IO error while scanning file", e);
      return null;
    }
  }

  private static void insert_via_infos(
      Collection<ViaInfo> p_via_infos,
      RoutingBoard p_board,
      boolean p_attach_allowed) {
    if (!p_via_infos.isEmpty()) {
      for (ViaInfo curr_info : p_via_infos) {
        p_board.rules.via_infos.add(curr_info);
      }
    } else // no via infos found, create default via infos from the via padstacks.
    {
      create_default_via_infos(p_board, p_board.rules.get_default_net_class(), p_attach_allowed);
    }
  }

  private static void create_default_via_infos(
      BasicBoard p_board,
      app.freerouting.rules.NetClass p_net_class,
      boolean p_attach_allowed) {
    int cl_class =
        p_net_class.default_item_clearance_classes.get(
            DefaultItemClearanceClasses.ItemClass.VIA);
    boolean is_default_class = (p_net_class == p_board.rules.get_default_net_class());
    for (int i = 0; i < p_board.library.via_padstack_count(); ++i) {
      Padstack curr_padstack = p_board.library.get_via_padstack(i);
      boolean attach_allowed = p_attach_allowed && curr_padstack.attach_allowed;
      String via_name;
      if (is_default_class) {
        via_name = curr_padstack.name;
      } else {
        via_name = curr_padstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_net_class.get_name();
      }
      ViaInfo found_via_info =
          new ViaInfo(
              via_name, curr_padstack, cl_class, attach_allowed, p_board.rules);
      p_board.rules.via_infos.add(found_via_info);
    }
  }

  private static void insert_via_rules(
      Collection<Collection<String>> p_via_rules, BasicBoard p_board) {
    boolean rule_found = false;
    for (Collection<String> curr_list : p_via_rules) {
      if (curr_list.size() < 2) {
        continue;
      }
      if (add_via_rule(curr_list, p_board)) {
        rule_found = true;
      }
    }
    if (!rule_found) {
      p_board.rules.create_default_via_rule(p_board.rules.get_default_net_class(), "default");
    }
    for (int i = 0; i < p_board.rules.net_classes.count(); ++i) {
      p_board.rules.net_classes.get(i).set_via_rule(p_board.rules.get_default_via_rule());
    }
  }

  /** Inserts a via rule into the board. Replaces an already existing via rule with the same */
  static boolean add_via_rule(
      Collection<String> p_name_list, BasicBoard p_board) {
    Iterator<String> it = p_name_list.iterator();
    String rule_name = it.next();
    ViaRule existing_rule = p_board.rules.get_via_rule(rule_name);
    ViaRule curr_rule = new ViaRule(rule_name);
    boolean rule_ok = true;
    while (it.hasNext()) {
      ViaInfo curr_via = p_board.rules.via_infos.get(it.next());
      if (curr_via != null) {
        curr_rule.append_via(curr_via);
      } else {
        FRLogger.warn("Network.insert_via_rules: via_info not found");
        rule_ok = false;
      }
    }
    if (rule_ok) {
      if (existing_rule != null) {
        // Replace already existing rule.
        p_board.rules.via_rules.remove(existing_rule);
      }
      p_board.rules.via_rules.add(curr_rule);
    }
    return rule_ok;
  }

  private static void insert_net_classes(
      Collection<NetClass> p_net_classes, ReadScopeParameter p_par) {
    BasicBoard routing_board = p_par.board_handling.get_routing_board();
    for (NetClass curr_class : p_net_classes) {
      insert_net_class(
          curr_class,
          p_par.layer_structure,
          routing_board,
          p_par.coordinate_transform,
          p_par.via_at_smd_allowed);
    }
  }

  static void insert_net_class(
      NetClass p_class,
      LayerStructure p_layer_structure,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform,
      boolean p_via_at_smd_allowed) {
    app.freerouting.rules.NetClass board_net_class = p_board.rules.append_net_class(p_class.name);
    if (p_class.trace_clearance_class != null) {
      int trace_clearance_class =
          p_board.rules.clearance_matrix.get_no(p_class.trace_clearance_class);
      if (trace_clearance_class >= 0) {
        board_net_class.set_trace_clearance_class(trace_clearance_class);
      } else {
        FRLogger.warn("Network.insert_net_class: clearance class not found at '" + board_net_class.get_name() + "'");
      }
    }
    if (p_class.via_rule != null) {
      ViaRule via_rule = p_board.rules.get_via_rule(p_class.via_rule);
      if (via_rule != null) {
        board_net_class.set_via_rule(via_rule);
      } else {
        FRLogger.warn("Network.insert_net_class: via rule not found at '" + board_net_class.get_name() + "'");
      }
    }
    if (p_class.max_trace_length > 0) {
      board_net_class.set_maximum_trace_length(
          p_coordinate_transform.dsn_to_board(p_class.max_trace_length));
    }
    if (p_class.min_trace_length > 0) {
      board_net_class.set_minimum_trace_length(
          p_coordinate_transform.dsn_to_board(p_class.min_trace_length));
    }
    for (String curr_net_name : p_class.net_list) {
      Collection<app.freerouting.rules.Net> curr_net_list = p_board.rules.nets.get(curr_net_name);
      for (app.freerouting.rules.Net curr_net : curr_net_list) {
        curr_net.set_class(board_net_class);
      }
    }

    // read the trace width and clearance rules.

    boolean clearance_rule_found = false;

    for (Rule curr_rule : p_class.rules) {
      if (curr_rule instanceof Rule.WidthRule) {
        int trace_halfwidth =
            (int)
                Math.round(
                    p_coordinate_transform.dsn_to_board(((Rule.WidthRule) curr_rule).value / 2));
        board_net_class.set_trace_half_width(trace_halfwidth);
      } else if (curr_rule instanceof Rule.ClearanceRule) {
        add_clearance_rule(
            p_board.rules.clearance_matrix,
            board_net_class,
            (Rule.ClearanceRule) curr_rule,
            -1,
            p_coordinate_transform);
        clearance_rule_found = true;
      } else {
        FRLogger.warn("Network.insert_net_class: rule type not yet implemented at '" + board_net_class.get_name() + "'");
      }
    }

    // read the layer dependent rules.

    for (Rule.LayerRule curr_layer_rule : p_class.layer_rules) {
      for (String curr_layer_name : curr_layer_rule.layer_names) {
        int layer_no = p_board.layer_structure.get_no(curr_layer_name);
        if (layer_no < 0) {
          FRLogger.warn("Network.insert_net_class: layer not found at '" + board_net_class.get_name() + "'");
          continue;
        }
        for (Rule curr_rule : curr_layer_rule.rules) {
          if (curr_rule instanceof Rule.WidthRule) {
            int trace_halfwidth =
                (int)
                    Math.round(
                        p_coordinate_transform.dsn_to_board(
                            ((Rule.WidthRule) curr_rule).value / 2));
            board_net_class.set_trace_half_width(layer_no, trace_halfwidth);
          } else if (curr_rule instanceof Rule.ClearanceRule) {
            add_clearance_rule(
                p_board.rules.clearance_matrix,
                board_net_class,
                (Rule.ClearanceRule) curr_rule,
                layer_no,
                p_coordinate_transform);
            clearance_rule_found = true;
          } else {
            FRLogger.warn("Network.insert_net_class: layer rule type not yet implemented at '" + board_net_class.get_name() + "'");
          }
        }
      }
    }

    board_net_class.set_pull_tight(p_class.pull_tight);
    board_net_class.set_shove_fixed(p_class.shove_fixed);
    boolean via_infos_created = false;

    if (clearance_rule_found && board_net_class != p_board.rules.get_default_net_class()) {
      create_default_via_infos(p_board, board_net_class, p_via_at_smd_allowed);
      via_infos_created = true;
    }

    if (!p_class.use_via.isEmpty()) {
      create_via_rule(p_class.use_via, board_net_class, p_board, p_via_at_smd_allowed);
    } else if (via_infos_created) {
      p_board.rules.create_default_via_rule(board_net_class, board_net_class.get_name());
    }
    if (!p_class.use_layer.isEmpty()) {
      create_active_trace_layers(p_class.use_layer, p_layer_structure, board_net_class);
    }
  }

  private static void insert_class_pairs(
      Collection<NetClass.ClassClass> p_class_classes, ReadScopeParameter p_par) {
    for (NetClass.ClassClass curr_class_class : p_class_classes) {
      Iterator<String> it1 = curr_class_class.class_names.iterator();
      BasicBoard routing_board = p_par.board_handling.get_routing_board();
      while (it1.hasNext()) {
        String first_name = it1.next();
        app.freerouting.rules.NetClass first_class =
            routing_board.rules.net_classes.get(first_name);
        if (first_class == null) {
          FRLogger.warn("Network.insert_class_pairs: first class not found");
        } else {
          Iterator<String> it2 = it1;
          while (it2.hasNext()) {
            String second_name = it2.next();
            app.freerouting.rules.NetClass second_class =
                routing_board.rules.net_classes.get(second_name);
            if (second_class == null) {
              FRLogger.warn("Network.insert_class_pairs: second class not found");
            } else {
              insert_class_pair_info(
                  curr_class_class,
                  first_class,
                  second_class,
                  routing_board,
                  p_par.coordinate_transform);
            }
          }
        }
      }
    }
  }

  private static void insert_class_pair_info(
      NetClass.ClassClass p_class_class,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform) {
    for (Rule curr_rule : p_class_class.rules) {
      if (curr_rule instanceof Rule.ClearanceRule) {
        Rule.ClearanceRule curr_clearance_rule = (Rule.ClearanceRule) curr_rule;
        add_mixed_clearance_rule(
            p_board.rules.clearance_matrix,
            p_first_class,
            p_second_class,
            curr_clearance_rule,
            -1,
            p_coordinate_transform);
      } else {
        FRLogger.warn("Network.insert_class_pair_info: unexpected rule");
      }
    }
    for (Rule.LayerRule curr_layer_rule : p_class_class.layer_rules) {
      for (String curr_layer_name : curr_layer_rule.layer_names) {
        int layer_no = p_board.layer_structure.get_no(curr_layer_name);
        if (layer_no < 0) {
          FRLogger.warn("Network.insert_class_pair_info: layer not found at '" + curr_layer_name + "'");
          continue;
        }
        for (Rule curr_rule : curr_layer_rule.rules) {
          if (curr_rule instanceof Rule.ClearanceRule) {
            add_mixed_clearance_rule(
                p_board.rules.clearance_matrix,
                p_first_class,
                p_second_class,
                (Rule.ClearanceRule) curr_rule,
                layer_no,
                p_coordinate_transform);
          } else {
            FRLogger.warn("Network.insert_class_pair_info: unexpected layer rule type");
          }
        }
      }
    }
  }

  private static void add_mixed_clearance_rule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      Rule.ClearanceRule p_clearance_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int curr_clearance =
        (int) Math.round(p_coordinate_transform.dsn_to_board(p_clearance_rule.value));
    final String first_class_name = p_first_class.get_name();
    int first_class_no = p_clearance_matrix.get_no(first_class_name);
    if (first_class_no < 0) {
      p_clearance_matrix.append_class(first_class_name);
      first_class_no = p_clearance_matrix.get_no(first_class_name);
    }
    final String second_class_name = p_second_class.get_name();
    int second_class_no = p_clearance_matrix.get_no(second_class_name);
    if (second_class_no < 0) {
      p_clearance_matrix.append_class(second_class_name);
      second_class_no = p_clearance_matrix.get_no(second_class_name);
    }
    if (p_clearance_rule.clearance_class_pairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(first_class_no, second_class_no, curr_clearance);
        p_clearance_matrix.set_value(second_class_no, first_class_no, curr_clearance);
      } else {
        p_clearance_matrix.set_value(first_class_no, second_class_no, p_layer_no, curr_clearance);
        p_clearance_matrix.set_value(second_class_no, first_class_no, p_layer_no, curr_clearance);
      }
    } else {
      for (String curr_string : p_clearance_rule.clearance_class_pairs) {
        String[] curr_pair = curr_string.split("_");
        if (curr_pair.length != 2) {
          continue;
        }

        int curr_first_class_no;
        int curr_second_class_no;
        for (int i = 0; i < 2; ++i) {
          if (i == 0) {
            curr_first_class_no =
                get_clearance_class(p_clearance_matrix, p_first_class, curr_pair[0]);
            curr_second_class_no =
                get_clearance_class(p_clearance_matrix, p_second_class, curr_pair[1]);
          } else {
            curr_first_class_no =
                get_clearance_class(p_clearance_matrix, p_second_class, curr_pair[0]);
            curr_second_class_no =
                get_clearance_class(p_clearance_matrix, p_first_class, curr_pair[1]);
          }
          if (p_layer_no < 0) {
            p_clearance_matrix.set_value(curr_first_class_no, curr_second_class_no, curr_clearance);
            p_clearance_matrix.set_value(curr_second_class_no, curr_first_class_no, curr_clearance);
          } else {
            p_clearance_matrix.set_value(
                curr_first_class_no, curr_second_class_no, p_layer_no, curr_clearance);
            p_clearance_matrix.set_value(
                curr_second_class_no, curr_first_class_no, p_layer_no, curr_clearance);
          }
        }
      }
    }
  }

  private static void create_default_clearance_classes(
      app.freerouting.rules.NetClass p_net_class,
      ClearanceMatrix p_clearance_matrix) {
    get_clearance_class(p_clearance_matrix, p_net_class, "via");
    get_clearance_class(p_clearance_matrix, p_net_class, "smd");
    get_clearance_class(p_clearance_matrix, p_net_class, "pin");
    get_clearance_class(p_clearance_matrix, p_net_class, "area");
  }

  private static void create_via_rule(
      Collection<String> p_use_via,
      app.freerouting.rules.NetClass p_net_class,
      BasicBoard p_board,
      boolean p_attach_allowed) {
    ViaRule new_via_rule =
        new ViaRule(p_net_class.get_name());
    int default_via_cl_class =
        p_net_class.default_item_clearance_classes.get(
            DefaultItemClearanceClasses.ItemClass.VIA);
    for (String curr_via_name : p_use_via) {
      for (int i = 0; i < p_board.rules.via_infos.count(); ++i) {
        ViaInfo curr_via_info = p_board.rules.via_infos.get(i);
        if (curr_via_info.get_clearance_class() == default_via_cl_class) {
          if (curr_via_info.get_padstack().name.equals(curr_via_name)) {
            new_via_rule.append_via(curr_via_info);
          }
        }
      }
    }
    p_board.rules.via_rules.add(new_via_rule);
    p_net_class.set_via_rule(new_via_rule);
  }

  private static void create_active_trace_layers(
      Collection<String> p_use_layer,
      LayerStructure p_layer_structure,
      app.freerouting.rules.NetClass p_net_class) {
    for (int i = 0; i < p_layer_structure.arr.length; ++i) {
      p_net_class.set_active_routing_layer(i, false);
    }
    for (String cur_layer_name : p_use_layer) {
      int curr_no = p_layer_structure.get_no(cur_layer_name);
      p_net_class.set_active_routing_layer(curr_no, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < p_layer_structure.arr.length; ++i) {
      if (!p_net_class.is_active_routing_layer(i)) {
        p_net_class.set_trace_half_width(i, 0);
      }
    }
  }

  private static void add_clearance_rule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      Rule.ClearanceRule p_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int curr_clearance = (int) Math.round(p_coordinate_transform.dsn_to_board(p_rule.value));
    final String class_name = p_net_class.get_name();
    int class_no = p_clearance_matrix.get_no(class_name);
    if (class_no < 0) {
      // class not yet existing, create a new class
      p_clearance_matrix.append_class(class_name);
      class_no = p_clearance_matrix.get_no(class_name);
      // set the clearance values of the new class to the maximum of curr_clearance and
      // the existing values.
      for (int i = 1; i < p_clearance_matrix.get_class_count(); ++i) {
        for (int j = 0; j < p_clearance_matrix.get_layer_count(); ++j) {
          int curr_value = Math.max(p_clearance_matrix.get_value(class_no, i, j, false), curr_clearance);
          p_clearance_matrix.set_value(class_no, i, j, curr_value);
          p_clearance_matrix.set_value(i, class_no, j, curr_value);
        }
      }
      p_net_class.default_item_clearance_classes.set_all(class_no);
    }
    p_net_class.set_trace_clearance_class(class_no);
    if (p_rule.clearance_class_pairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(class_no, class_no, curr_clearance);
      } else {
        p_clearance_matrix.set_value(class_no, class_no, p_layer_no, curr_clearance);
      }
      return;
    }
    if (Structure.contains_wire_clearance_pair(p_rule.clearance_class_pairs)) {
      create_default_clearance_classes(p_net_class, p_clearance_matrix);
    }
    for (String curr_string : p_rule.clearance_class_pairs) {
      String[] curr_pair = curr_string.split("_");
      if (curr_pair.length != 2) {
        continue;
      }

      int first_class_no = get_clearance_class(p_clearance_matrix, p_net_class, curr_pair[0]);
      int second_class_no = get_clearance_class(p_clearance_matrix, p_net_class, curr_pair[1]);

      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(first_class_no, second_class_no, curr_clearance);
        p_clearance_matrix.set_value(second_class_no, first_class_no, curr_clearance);
      } else {
        p_clearance_matrix.set_value(first_class_no, second_class_no, p_layer_no, curr_clearance);
        p_clearance_matrix.set_value(second_class_no, first_class_no, p_layer_no, curr_clearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of p_net_class_name and
   * p_item_class_name. Creates a new class, if that class is not yet existing.
   */
  private static int get_clearance_class(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      String p_item_class_name) {
    String net_class_name = p_net_class.get_name();
    String new_class_name = net_class_name;
    if (!p_item_class_name.equals("wire")) {
      new_class_name = new_class_name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_item_class_name;
    }
    int found_class_no = p_clearance_matrix.get_no(new_class_name);
    if (found_class_no >= 0) {
      return found_class_no;
    }
    p_clearance_matrix.append_class(new_class_name);
    int result = p_clearance_matrix.get_no(new_class_name);
    int net_class_no = p_clearance_matrix.get_no(net_class_name);
    if (net_class_no < 0 || result < 0) {
      FRLogger.warn("Network.get_clearance_class: clearance class not found at '" + net_class_name + "'");
      return result;
    }
    // initialize the clearance values of p_new_class_name from p_net_class_name
    for (int i = 1; i < p_clearance_matrix.get_class_count(); ++i) {

      for (int j = 0; j < p_clearance_matrix.get_layer_count(); ++j) {
        int curr_value = p_clearance_matrix.get_value(net_class_no, i, j, false);
        p_clearance_matrix.set_value(result, i, j, curr_value);
        p_clearance_matrix.set_value(i, result, j, curr_value);
      }
    }
    switch (p_item_class_name) {
      case "via"  -> p_net_class.default_item_clearance_classes.set(ItemClass.VIA, result);
      case "pin"  -> p_net_class.default_item_clearance_classes.set(ItemClass.PIN, result);
      case "smd"  -> p_net_class.default_item_clearance_classes.set(ItemClass.SMD, result);
      case "area" -> p_net_class.default_item_clearance_classes.set(ItemClass.AREA, result);
    }
    return result;
  }

  private static void insert_components(ReadScopeParameter p_par) {
    for (ComponentPlacement next_lib_component : p_par.placement_list) {
      for (ComponentPlacement.ComponentLocation next_component : next_lib_component.locations) {
        insert_component(next_component, next_lib_component.lib_name, p_par);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insert_logical_parts(ReadScopeParameter p_par) {
    BasicBoard routing_board = p_par.board_handling.get_routing_board();
    for (PartLibrary.LogicalPart next_part : p_par.logical_parts) {
      app.freerouting.library.Package lib_package =
          search_lib_package(next_part.name, p_par.logical_part_mappings, routing_board);
      if (lib_package == null) {
        return false;
      }
      app.freerouting.library.LogicalPart.PartPin[] board_part_pins =
          new app.freerouting.library.LogicalPart.PartPin[next_part.part_pins.size()];
      int curr_index = 0;
      for (PartLibrary.PartPin curr_part_pin : next_part.part_pins) {
        int pin_no = lib_package.get_pin_no(curr_part_pin.pin_name);
        if (pin_no < 0) {
          FRLogger.warn("Network.insert_logical_parts: package pin not found at '" + curr_part_pin.pin_name + "'");
          return false;
        }
        board_part_pins[curr_index] =
            new app.freerouting.library.LogicalPart.PartPin(
                pin_no,
                curr_part_pin.pin_name,
                curr_part_pin.gate_name,
                curr_part_pin.gate_swap_code,
                curr_part_pin.gate_pin_name,
                curr_part_pin.gate_pin_swap_code);
        ++curr_index;
      }
      routing_board.library.logical_parts.add(next_part.name, board_part_pins);
    }

    for (PartLibrary.LogicalPartMapping next_mapping : p_par.logical_part_mappings) {
      app.freerouting.library.LogicalPart curr_logical_part =
          routing_board.library.logical_parts.get(next_mapping.name);
      {
        if (curr_logical_part == null) {
          FRLogger.warn("Network.insert_logical_parts: logical part not found at '" + next_mapping.name + "'");
        }
      }
      for (String curr_cmp_name : next_mapping.components) {
        app.freerouting.board.Component curr_component =
            routing_board.components.get(curr_cmp_name);
        if (curr_component != null) {
          curr_component.set_logical_part(curr_logical_part);
        } else {
          FRLogger.warn("Network.insert_logical_parts: board component not found at '" + curr_cmp_name + "'");
        }
      }
    }
    return true;
  }

  /**
   * Calculates the library package belonging to the logical part with name p_part_name. Returns
   * null, if the package was not found.
   */
  private static app.freerouting.library.Package search_lib_package(
      String p_part_name,
      Collection<PartLibrary.LogicalPartMapping> p_logical_part_mappings,
      BasicBoard p_board) {
    for (PartLibrary.LogicalPartMapping curr_mapping : p_logical_part_mappings) {
      if (curr_mapping.name.equals(p_part_name)) {
        if (curr_mapping.components.isEmpty()) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + p_part_name + "'");
          return null;
        }
        String component_name = curr_mapping.components.first();
        if (component_name == null) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + p_part_name + "'");
          return null;
        }
        app.freerouting.board.Component curr_component = p_board.components.get(component_name);
        if (curr_component == null) {
          FRLogger.warn("Network.search_lib_package: component not found at '" + component_name + "'");
          return null;
        }
        return curr_component.get_package();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + p_part_name + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insert_component(
      ComponentPlacement.ComponentLocation p_location, String p_lib_key, ReadScopeParameter p_par) {
    RoutingBoard routing_board = p_par.board_handling.get_routing_board();
    app.freerouting.library.Package curr_front_package =
        routing_board.library.packages.get(p_lib_key, true);
    app.freerouting.library.Package curr_back_package =
        routing_board.library.packages.get(p_lib_key, false);
    if (curr_front_package == null || curr_back_package == null) {
      FRLogger.warn("Network.insert_component: component package not found at '" + p_par.scanner.get_scope_identifier() + "'");
      return;
    }

    IntPoint component_location;
    if (p_location.coor != null) {
      component_location = p_par.coordinate_transform.dsn_to_board(p_location.coor).round();
    } else {
      component_location = null;
    }
    double rotation_in_degree = p_location.rotation;

    app.freerouting.board.Component new_component =
        routing_board.components.add(
            p_location.name,
            component_location,
            rotation_in_degree,
            p_location.is_front,
            curr_front_package,
            curr_back_package,
            p_location.position_fixed);

    if (component_location == null) {
      return; // component is not yet placed.
    }
    Vector component_translation = component_location.difference_by(Point.ZERO);
    FixedState fixed_state;
    if (p_location.position_fixed) {
      fixed_state = FixedState.SYSTEM_FIXED;
    } else {
      fixed_state = FixedState.UNFIXED;
    }
    app.freerouting.library.Package curr_package = new_component.get_package();
    for (int i = 0; i < curr_package.pin_count(); ++i) {
      app.freerouting.library.Package.Pin curr_pin = curr_package.get_pin(i);
      Padstack curr_padstack =
          routing_board.library.padstacks.get(curr_pin.padstack_no);
      if (curr_padstack == null) {
        FRLogger.warn("Network.insert_component: pin padstack not found at '" + p_par.scanner.get_scope_identifier() + "'");
        return;
      }
      Collection<Net> pin_nets = p_par.netlist.get_nets(p_location.name, curr_pin.name);
      Collection<Integer> net_numbers = new LinkedList<>();
      for (Net curr_pin_net : pin_nets) {
        app.freerouting.rules.Net curr_board_net =
            routing_board.rules.nets.get(curr_pin_net.id.name, curr_pin_net.id.subnet_number);
        if (curr_board_net == null) {
          FRLogger.warn("Network.insert_component: board net not found at '" + p_par.scanner.get_scope_identifier() + "'");
        } else {
          net_numbers.add(curr_board_net.net_number);
        }
      }
      int[] net_no_arr = new int[net_numbers.size()];
      int net_index = 0;
      for (Integer curr_net_no : net_numbers) {
        net_no_arr[net_index] = curr_net_no;
        ++net_index;
      }
      app.freerouting.rules.NetClass net_class;
      app.freerouting.rules.Net board_net;
      if (net_no_arr.length > 0) {
        board_net = routing_board.rules.nets.get(net_no_arr[0]);
      } else {
        board_net = null;
      }
      if (board_net != null) {
        net_class = board_net.get_class();
      } else {
        net_class = routing_board.rules.get_default_net_class();
      }
      int clearance_class = -1;
      ComponentPlacement.ItemClearanceInfo pin_info = p_location.pin_infos.get(curr_pin.name);
      if (pin_info != null) {
        clearance_class = routing_board.rules.clearance_matrix.get_no(pin_info.clearance_class);
      }
      if (clearance_class < 0) {
        if (curr_padstack.from_layer() == curr_padstack.to_layer()) {
          clearance_class =
              net_class.default_item_clearance_classes.get(
                  DefaultItemClearanceClasses.ItemClass.SMD);
        } else {
          clearance_class =
              net_class.default_item_clearance_classes.get(
                  DefaultItemClearanceClasses.ItemClass.PIN);
        }
      }
      routing_board.insert_pin(new_component.no, i, net_no_arr, clearance_class, fixed_state);
    }

    // insert the keepouts belonging to the package (k = 1 for via keepouts)
    for (int k = 0; k <= 2; ++k) {
      app.freerouting.library.Package.Keepout[] keepout_arr;
      Map<String, ComponentPlacement.ItemClearanceInfo> curr_keepout_infos;
      if (k == 0) {
        keepout_arr = curr_package.keepout_arr;
        curr_keepout_infos = p_location.keepout_infos;
      } else if (k == 1) {
        keepout_arr = curr_package.via_keepout_arr;
        curr_keepout_infos = p_location.via_keepout_infos;
      } else {
        keepout_arr = curr_package.place_keepout_arr;
        curr_keepout_infos = p_location.place_keepout_infos;
      }
      for (int i = 0; i < keepout_arr.length; ++i) {
        app.freerouting.library.Package.Keepout curr_keepout = keepout_arr[i];
        int layer = curr_keepout.layer;
        if (layer >= routing_board.get_layer_count()) {
          FRLogger.warn("Network.insert_component: keepout layer is to big at '" + p_par.scanner.get_scope_identifier() + "'");
          continue;
        }
        if (layer >= 0 && !p_location.is_front) {
          layer = routing_board.get_layer_count() - curr_keepout.layer - 1;
        }
        int clearance_class =
            routing_board
                .rules
                .get_default_net_class()
                .default_item_clearance_classes
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
        ComponentPlacement.ItemClearanceInfo keepout_info =
            curr_keepout_infos.get(curr_keepout.name);
        if (keepout_info != null) {
          int curr_clearance_class =
              routing_board.rules.clearance_matrix.get_no(keepout_info.clearance_class);
          if (curr_clearance_class > 0) {
            clearance_class = curr_clearance_class;
          }
        }
        if (layer >= 0) {
          if (k == 0) {
            routing_board.insert_obstacle(
                curr_keepout.area,
                layer,
                component_translation,
                rotation_in_degree,
                !p_location.is_front,
                clearance_class,
                new_component.no,
                curr_keepout.name,
                fixed_state);
          } else if (k == 1) {
            routing_board.insert_via_obstacle(
                curr_keepout.area,
                layer,
                component_translation,
                rotation_in_degree,
                !p_location.is_front,
                clearance_class,
                new_component.no,
                curr_keepout.name,
                fixed_state);
          } else {
            routing_board.insert_component_obstacle(
                curr_keepout.area,
                layer,
                component_translation,
                rotation_in_degree,
                !p_location.is_front,
                clearance_class,
                new_component.no,
                curr_keepout.name,
                fixed_state);
          }
        } else {
          // insert the obstacle on all signal layers
          for (int j = 0; j < routing_board.layer_structure.arr.length; ++j) {
            if (routing_board.layer_structure.arr[j].is_signal) {
              if (k == 0) {
                routing_board.insert_obstacle(
                    curr_keepout.area,
                    j,
                    component_translation,
                    rotation_in_degree,
                    !p_location.is_front,
                    clearance_class,
                    new_component.no,
                    curr_keepout.name,
                    fixed_state);
              } else if (k == 1) {
                routing_board.insert_via_obstacle(
                    curr_keepout.area,
                    j,
                    component_translation,
                    rotation_in_degree,
                    !p_location.is_front,
                    clearance_class,
                    new_component.no,
                    curr_keepout.name,
                    fixed_state);
              } else {
                routing_board.insert_component_obstacle(
                    curr_keepout.area,
                    j,
                    component_translation,
                    rotation_in_degree,
                    !p_location.is_front,
                    clearance_class,
                    new_component.no,
                    curr_keepout.name,
                    fixed_state);
              }
            }
          }
        }
      }
    }
    // insert the outline as component keepout
    for (int i = 0; i < curr_package.outline.length; ++i) {

      routing_board.insert_component_outline(
          curr_package.outline[i],
          p_location.is_front,
          component_translation,
          rotation_in_degree,
          new_component.no,
          fixed_state);
    }
  }

  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> class_class_list = new LinkedList<>();
    Collection<ViaInfo> via_infos =
        new LinkedList<>();
    Collection<Collection<String>> via_rules = new LinkedList<>();
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (next_token == null) {
        FRLogger.warn("Network.read_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'");
        return false;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == OPEN_BRACKET) {
        if (next_token == Keyword.NET) {
          read_net_scope(
              p_par.scanner,
              p_par.netlist,
              p_par.board_handling.get_routing_board(),
              p_par.coordinate_transform,
              p_par.layer_structure,
              p_par.board_handling.get_locale());
        } else if (next_token == Keyword.VIA) {
          ViaInfo curr_via_info =
              read_via_info(p_par.scanner, p_par.board_handling.get_routing_board());
          if (curr_via_info == null) {
            return false;
          }
          via_infos.add(curr_via_info);
        } else if (next_token == Keyword.VIA_RULE) {
          Collection<String> curr_via_rule =
              read_via_rule(p_par.scanner, p_par.board_handling.get_routing_board());
          if (curr_via_rule == null) {
            return false;
          }
          via_rules.add(curr_via_rule);
        } else if (next_token == Keyword.CLASS) {
          NetClass curr_class = NetClass.read_scope(p_par.scanner);
          if (curr_class == null) {
            return false;
          }
          classes.add(curr_class);
        } else if (next_token == Keyword.CLASS_CLASS) {
          NetClass.ClassClass curr_class_class = NetClass.read_class_class_scope(p_par.scanner);
          if (curr_class_class == null) {
            return false;
          }
          class_class_list.add(curr_class_class);
        } else {
          skip_scope(p_par.scanner);
        }
      }
    }

    // Add any vias defined in the Netclasses to the list of vias to be instantiated
    for (NetClass n : classes) {
      if (p_par.via_padstack_names != null) {
        p_par.via_padstack_names.addAll(n.use_via);
      } else {
        p_par.via_padstack_names = n.use_via;
      }
    }

    RoutingBoard board = p_par.board_handling.get_routing_board();

    // Set the via padstacks after network parsing, so that named vias from both structure and
    // network DSN sections are properly instantiated .
    if (p_par.via_padstack_names != null) {
      Padstack[] via_padstacks =
          new Padstack[p_par.via_padstack_names.size()];
      Iterator<String> it = p_par.via_padstack_names.iterator();
      int found_padstack_count = 0;
      for (int i = 0; i < via_padstacks.length; ++i) {
        String curr_padstack_name = it.next();
        Padstack curr_padstack =
            board.library.padstacks.get(curr_padstack_name);
        if (curr_padstack != null) {
          via_padstacks[found_padstack_count] = curr_padstack;
          ++found_padstack_count;
        } else {
          FRLogger.warn("Library.read_scope: via padstack with name '" + curr_padstack_name + " not found at '" + p_par.scanner.get_scope_identifier() + "'");
        }
      }
      if (found_padstack_count != via_padstacks.length) {
        // Some via padstacks were not found in the padstacks scope of the dsn-file.
        Padstack[] corrected_padstacks =
            new Padstack[found_padstack_count];
        System.arraycopy(via_padstacks, 0, corrected_padstacks, 0, found_padstack_count);
        via_padstacks = corrected_padstacks;
      }
      board.library.set_via_padstacks(via_padstacks);
    }

    insert_via_infos(via_infos, p_par.board_handling.get_routing_board(), p_par.via_at_smd_allowed);
    insert_via_rules(via_rules, p_par.board_handling.get_routing_board());
    insert_net_classes(classes, p_par);
    insert_class_pairs(class_class_list, p_par);
    insert_components(p_par);
    insert_logical_parts(p_par);
    return true;
  }

  private boolean read_net_scope(
      IJFlexScanner p_scanner,
      NetList p_net_list,
      RoutingBoard p_board,
      CoordinateTransform p_coordinate_transform,
      LayerStructure p_layer_structure,
      Locale p_locale) {
    // read the net name
    String net_name = p_scanner.next_string();

    Object next_token;
    int subnet_number = 1;
    try {
      next_token = p_scanner.next_token();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_scope: IO error while scanning file", e);
      return false;
    }
    boolean scope_is_empty = (next_token == CLOSED_BRACKET);
    if (next_token instanceof Integer) {
      subnet_number = (Integer) next_token;
    }
    boolean pin_order_found = false;
    Collection<Net.Pin> pin_list = new LinkedList<>();
    Collection<Rule> net_rules = new LinkedList<>();
    Collection<Collection<Net.Pin>> subnet_pin_lists = new LinkedList<>();
    if (!scope_is_empty) {
      for (; ; ) {
        Object prev_token = next_token;
        try {
          next_token = p_scanner.next_token();
        } catch (IOException e) {
          FRLogger.error("Network.read_net_scope: IO error scanning file", e);
          return false;
        }
        if (next_token == null) {
          FRLogger.warn("Network.read_net_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'");
          return false;
        }
        if (next_token == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prev_token == OPEN_BRACKET) {
          if (next_token == Keyword.PINS) {
            if (!read_net_pins(p_scanner, pin_list)) {
              return false;
            }
          } else if (next_token == Keyword.ORDER) {
            pin_order_found = true;
            if (!read_net_pins(p_scanner, pin_list)) {
              return false;
            }
          } else if (next_token == Keyword.FROMTO) {
            Set<Net.Pin> curr_subnet_pin_list = new TreeSet<>();
            if (!read_net_pins(p_scanner, curr_subnet_pin_list)) {
              return false;
            }
            subnet_pin_lists.add(curr_subnet_pin_list);
          } else if (next_token == Keyword.RULE) {
            net_rules.addAll(Rule.read_scope(p_scanner));
          } else if (next_token == Keyword.LAYER_RULE) {
            FRLogger.warn("Network.read_net_scope: layer_rule not yet implemented at '" + p_scanner.get_scope_identifier() + "'");
            skip_scope(p_scanner);
          } else {
            skip_scope(p_scanner);
          }
        }
      }
    }
    if (subnet_pin_lists.isEmpty()) {
      if (pin_order_found) {
        subnet_pin_lists = create_ordered_subnets(pin_list);
      } else {
        subnet_pin_lists.add(pin_list);
      }
    }
    for (Collection<Net.Pin> curr_pin_list : subnet_pin_lists) {
      Net.Id net_id = new Net.Id(net_name, subnet_number);
      if (!p_net_list.contains(net_id)) {
        Net new_net = p_net_list.add_net(net_id);
        boolean contains_plane = p_layer_structure.contains_plane(net_name);
        if (new_net != null) {
          p_board.rules.nets.add(new_net.id.name, new_net.id.subnet_number, contains_plane);
        }
      }
      Net curr_subnet = p_net_list.get_net(net_id);
      if (curr_subnet == null) {
        FRLogger.warn("Network.read_net_scope: net not found in netlist at '" + p_scanner.get_scope_identifier() + "'");
        return false;
      }
      curr_subnet.set_pins(curr_pin_list);
      if (!net_rules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net board_net =
            p_board.rules.nets.get(curr_subnet.id.name, curr_subnet.id.subnet_number);
        if (board_net == null) {
          FRLogger.warn("Network.read_net_scope: board net not found at '" + p_scanner.get_scope_identifier() + "'");
          return false;
        }
        for (Rule curr_ob : net_rules) {
          if (curr_ob instanceof Rule.WidthRule) {
            app.freerouting.rules.NetClass default_net_rule = p_board.rules.get_default_net_class();
            double wire_width = ((Rule.WidthRule) curr_ob).value;
            int trace_halfwidth =
                (int) Math.round(p_coordinate_transform.dsn_to_board(wire_width) / 2);
            app.freerouting.rules.NetClass net_rule =
                p_board.rules.net_classes.find(
                    trace_halfwidth,
                    default_net_rule.get_trace_clearance_class(),
                    default_net_rule.get_via_rule());
            if (net_rule == null) {
              // create a new net rule
              net_rule = p_board.rules.get_new_net_class(p_locale);
            }
            net_rule.set_trace_half_width(trace_halfwidth);
            board_net.set_class(net_rule);
          } else {
            FRLogger.warn("Network.read_net_scope: Rule not yet implemented at '" + p_scanner.get_scope_identifier() + "'");
          }
        }
      }
      ++subnet_number;
    }
    return true;
  }
}
