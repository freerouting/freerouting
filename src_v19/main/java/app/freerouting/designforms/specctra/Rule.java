package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** Class for reading and writing rule scopes from dsn-files. */
public abstract class Rule {
  /** Returns a collection of objects of class Rule. */
  public static Collection<Rule> read_scope(IJFlexScanner p_scanner) {
    Collection<Rule> result = new LinkedList<>();
    Object current_token = null;
    for (; ; ) {
      Object prev_token = current_token;
      try {
        current_token = p_scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Rule.read_scope: IO error scanning file", e);
        return null;
      }
      if (current_token == null) {
        FRLogger.warn("Rule.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      if (current_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == Keyword.OPEN_BRACKET) {
        // every rule starts with a "("
        Rule curr_rule = null;
        if (current_token == Keyword.WIDTH) {
          // this is a "(width" rule
          curr_rule = read_width_rule(p_scanner);
        } else if (current_token == Keyword.CLEARANCE) {
          // this is a "(clear" rule
          curr_rule = read_clearance_rule(p_scanner);
        } else {
          ScopeKeyword.skip_scope(p_scanner);
        }

        if (curr_rule != null) {
          result.add(curr_rule);
        }
      }
    }
    return result;
  }

  /** Reads a LayerRule from dsn-file. */
  public static LayerRule read_layer_rule_scope(IJFlexScanner p_scanner) {
    try {
      Collection<String> layer_names = new LinkedList<>();
      Collection<Rule> rule_list = new LinkedList<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnFileReader.LAYER_NAME);
        Object next_token = p_scanner.next_token();
        if (next_token == Keyword.OPEN_BRACKET) {
          break;
        }
        if (!(next_token instanceof String)) {

          FRLogger.warn("Rule.read_layer_rule_scope: string expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        layer_names.add((String) next_token);
      }
      for (; ; ) {
        Object next_token = p_scanner.next_token();
        if (next_token == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (next_token != Keyword.RULE) {

          FRLogger.warn("Rule.read_layer_rule_scope: rule expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        rule_list.addAll(read_scope(p_scanner));
      }
      return new LayerRule(layer_names, rule_list);
    } catch (IOException e) {
      FRLogger.error("Rule.read_layer_rule_scope: IO error scanning file", e);
      return null;
    }
  }

  public static WidthRule read_width_rule(IJFlexScanner p_scanner) {
    double value = p_scanner.next_double();

    if (!p_scanner.next_closing_bracket()) {
      return null;
    }

    return new WidthRule(value);
  }

  public static void write_scope(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par)
      throws IOException {
    p_par.file.start_scope();
    p_par.file.write("rule");

    // write the trace width
    int default_trace_half_width = p_net_class.get_trace_half_width(0);
    double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(default_trace_half_width);
    p_par.file.new_line();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(trace_width));
    p_par.file.write(")");
    p_par.file.end_scope();
    for (int i = 1; i < p_par.board.layer_structure.arr.length; ++i) {
      if (p_net_class.get_trace_half_width(i) != default_trace_half_width) {
        write_layer_rule(p_net_class, i, p_par);
      }
    }
  }

  private static void write_layer_rule(
      app.freerouting.rules.NetClass p_net_class, int p_layer_no, WriteScopeParameter p_par)
      throws IOException {
    p_par.file.start_scope();
    p_par.file.write("layer_rule ");

    app.freerouting.board.Layer curr_board_layer = p_par.board.layer_structure.arr[p_layer_no];

    p_par.file.write(curr_board_layer.name);
    p_par.file.start_scope();
    p_par.file.write("rule ");

    int curr_trace_half_width = p_net_class.get_trace_half_width(p_layer_no);

    // write the trace width
    double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(curr_trace_half_width);
    p_par.file.new_line();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(trace_width));
    p_par.file.write(") ");
    p_par.file.end_scope();
    p_par.file.end_scope();
  }

  /** Writes the default rule as a scope to an output dsn-file. */
  public static void write_default_rule(WriteScopeParameter p_par, int p_layer)
      throws IOException {
    p_par.file.start_scope();
    p_par.file.write("rule");
    // write the trace width
    double trace_width =
        2
            * p_par.coordinate_transform.board_to_dsn(
                p_par.board.rules.get_default_net_class().get_trace_half_width(0));
    p_par.file.new_line();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(trace_width));
    p_par.file.write(")");
    // write the default clearance rule
    int default_cl_no = BoardRules.default_clearance_class();
    int default_board_clearance =
        p_par.board.rules.clearance_matrix.get_value(default_cl_no, default_cl_no, p_layer, false);
    double default_clearance = p_par.coordinate_transform.board_to_dsn(default_board_clearance);
    p_par.file.new_line();
    // write the default clearance
    p_par.file.write("(clearance ");
    p_par.file.write(String.valueOf(default_clearance));
    p_par.file.write(")");
    // write the smd_to_turn_gap
    double smd_to_turn_dist =
        p_par.coordinate_transform.board_to_dsn(p_par.board.rules.get_pin_edge_to_turn_dist());
    p_par.file.new_line();
    p_par.file.write("(clearance ");
    p_par.file.write(String.valueOf(smd_to_turn_dist));
    p_par.file.write(" (type smd_to_turn_gap))");

    // write the named clearance rules from the clearance matrix
    write_named_clearance_rules(p_par, p_layer);
    //write_non_default_clearance_rules(p_par, p_layer, default_board_clearance);

    p_par.file.end_scope();
  }

  /** Write the clearance rules, which are different from the default clearance. */
  private static void write_non_default_clearance_rules(
      WriteScopeParameter p_par,
      int p_layer,
      int p_default_clearance)
      throws IOException {

    ClearanceMatrix cl_matrix = p_par.board.rules.clearance_matrix;
    int cl_count = p_par.board.rules.clearance_matrix.get_class_count();

    for (int i = 1; i <= cl_count; ++i) {
      for (int j = i; j < cl_count; ++j) {
        int curr_board_clearance = cl_matrix.get_value(i, j, p_layer, false);

        if (curr_board_clearance == p_default_clearance) {
          continue;
        }

        double curr_clearance = p_par.coordinate_transform.board_to_dsn(curr_board_clearance);
        p_par.file.new_line();
        p_par.file.write("(clearance ");
        p_par.file.write(String.valueOf(curr_clearance));
        p_par.file.write(" (type ");
        p_par.identifier_type.write(cl_matrix.get_name(i), p_par.file);
        p_par.file.write(DsnFile.CLASS_CLEARANCE_SEPARATOR);
        p_par.identifier_type.write(cl_matrix.get_name(j), p_par.file);
        p_par.file.write("))");
      }
    }
  }

  /** Write the clearance rules for the named classes in the clearance matrix. */
  private static void write_named_clearance_rules(
      WriteScopeParameter p_par,
      int p_layer)
      throws IOException {

    ClearanceMatrix cl_matrix = p_par.board.rules.clearance_matrix;
    int cl_count = p_par.board.rules.clearance_matrix.get_class_count();

    for (int i = 1; i < cl_count; ++i) {
      if (Objects.equals(cl_matrix.get_name(i), "default")) {
        continue;
      }

      int curr_board_clearance = cl_matrix.get_value(i, i, p_layer, false);
      double curr_clearance = p_par.coordinate_transform.board_to_dsn(curr_board_clearance);

      p_par.file.new_line();
      p_par.file.write("(clearance ");
      p_par.file.write(String.valueOf(curr_clearance));
      p_par.file.write(" (type ");
      p_par.identifier_type.write(cl_matrix.get_name(i), p_par.file);
      p_par.file.write("))");
    }
  }

  public static ClearanceRule read_clearance_rule(IJFlexScanner p_scanner) {
    try {
      double value = p_scanner.next_double();

      Collection<String> class_pairs = new LinkedList<>();
      Object next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        // look for "(type"
        if (next_token != Keyword.OPEN_BRACKET) {
          FRLogger.warn("Rule.read_clearance_rule: ( expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
        next_token = p_scanner.next_token();
        if (next_token != Keyword.TYPE) {
          FRLogger.warn("Rule.read_clearance_rule: type expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }

        class_pairs.addAll(List.of(p_scanner.next_string_list(DsnFile.CLASS_CLEARANCE_SEPARATOR)));

        // check the closing ")" of "(type"
        if (!p_scanner.next_closing_bracket()) {
          FRLogger.warn("Rule.read_clearance_rule: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }

        // check the closing ")" of "(clear"
        if (!p_scanner.next_closing_bracket()) {
          FRLogger.warn("Rule.read_clearance_rule: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'");
          return null;
        }
      }

      return new ClearanceRule(value, class_pairs);
    } catch (IOException e) {
      FRLogger.error("Rule.read_clearance_rule: IO error scanning file", e);
      return null;
    }
  }

  public static void write_item_clearance_class(
      String p_name,
      IndentFileWriter p_file,
      IdentifierType p_identifier_type)
      throws IOException {
    p_file.new_line();
    p_file.write("(clearance_class ");
    p_identifier_type.write(p_name, p_file);
    p_file.write(")");
  }

  public static class WidthRule extends Rule {
    final double value;

    public WidthRule(double p_value) {
      value = p_value;
    }
  }

  public static class ClearanceRule extends Rule {
    final double value;
    final Collection<String> clearance_class_pairs;
    public ClearanceRule(double p_value, Collection<String> p_class_pairs) {
      value = p_value;
      clearance_class_pairs = p_class_pairs;
    }
  }

  public static class LayerRule {
    final Collection<String> layer_names;
    final Collection<Rule> rules;
    LayerRule(Collection<String> p_layer_names, Collection<Rule> p_rules) {
      layer_names = p_layer_names;
      rules = p_rules;
    }
  }
}
