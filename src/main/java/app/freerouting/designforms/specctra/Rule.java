package app.freerouting.designforms.specctra;

import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/** Class for reading and writing rule scopes from dsn-files. */
public abstract class Rule {
  /** Returns a collection of objects of class Rule. */
  public static Collection<Rule> read_scope(IJFlexScanner p_scanner) {
    Collection<Rule> result = new LinkedList<Rule>();
    Object current_token = null;
    for (; ; ) {
      Object prev_token = current_token;
      try {
        current_token = p_scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("Rule.read_scope: IO error scanning file", e);
        return null;
      }
      if (current_token == null) {
        FRLogger.warn("Rule.read_scope: unexpected end of file");
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
      Collection<String> layer_names = new LinkedList<String>();
      Collection<Rule> rule_list = new LinkedList<Rule>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnFileReader.LAYER_NAME);
        Object next_token = p_scanner.next_token();
        if (next_token == Keyword.OPEN_BRACKET) {
          break;
        }
        if (!(next_token instanceof String)) {

          FRLogger.warn("Rule.read_layer_rule_scope: string expected");
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

          FRLogger.warn("Rule.read_layer_rule_scope: rule expected");
          return null;
        }
        rule_list.addAll(read_scope(p_scanner));
      }
      return new LayerRule(layer_names, rule_list);
    } catch (java.io.IOException e) {
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
      throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("rule");

    // write the trace width
    int default_trace_half_width = p_net_class.get_trace_half_width(0);
    double trace_width = 2 * p_par.coordinate_transform.board_to_dsn(default_trace_half_width);
    p_par.file.new_line();
    p_par.file.write("(width ");
    p_par.file.write((Double.valueOf(trace_width)).toString());
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
      throws java.io.IOException {
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
    p_par.file.write((Double.valueOf(trace_width)).toString());
    p_par.file.write(") ");
    p_par.file.end_scope();
    p_par.file.end_scope();
  }

  /** Writes the default rule as a scope to an output dsn-file. */
  public static void write_default_rule(WriteScopeParameter p_par, int p_layer)
      throws java.io.IOException {
    p_par.file.start_scope();
    p_par.file.write("rule");
    // write the trace width
    double trace_width =
        2
            * p_par.coordinate_transform.board_to_dsn(
                p_par.board.rules.get_default_net_class().get_trace_half_width(0));
    p_par.file.new_line();
    p_par.file.write("(width ");
    p_par.file.write((Double.valueOf(trace_width)).toString());
    p_par.file.write(")");
    // write the default clearance rule
    int default_cl_no = app.freerouting.rules.BoardRules.default_clearance_class();
    int default_board_clearance =
        p_par.board.rules.clearance_matrix.value(default_cl_no, default_cl_no, p_layer);
    double default_clearance = p_par.coordinate_transform.board_to_dsn(default_board_clearance);
    p_par.file.new_line();
    p_par.file.write("(clear ");
    p_par.file.write((Double.valueOf(default_clearance)).toString());
    p_par.file.write(")");
    // write the Smd_to_turn_gap
    Double smd_to_turn_dist =
        p_par.coordinate_transform.board_to_dsn(p_par.board.rules.get_pin_edge_to_turn_dist());
    p_par.file.new_line();
    p_par.file.write("(clear ");
    p_par.file.write(smd_to_turn_dist.toString());
    p_par.file.write(" (type smd_to_turn_gap))");
    int cl_count = p_par.board.rules.clearance_matrix.get_class_count();
    for (int i = 1; i <= cl_count; ++i) {
      write_clearance_rules(p_par, p_layer, i, cl_count, default_board_clearance);
    }
    p_par.file.end_scope();
  }

  /** Write the clearance rules, which are different from the default clearance. */
  private static void write_clearance_rules(
      WriteScopeParameter p_par,
      int p_layer,
      int p_cl_class,
      int p_max_cl_class,
      int p_default_clearance)
      throws java.io.IOException {
    app.freerouting.rules.ClearanceMatrix cl_matrix = p_par.board.rules.clearance_matrix;
    for (int i = p_cl_class; i < p_max_cl_class; ++i) {
      int curr_board_clearance = cl_matrix.value(p_cl_class, i, p_layer);
      if (curr_board_clearance == p_default_clearance) {
        continue;
      }
      double curr_clearance = p_par.coordinate_transform.board_to_dsn(curr_board_clearance);
      p_par.file.new_line();
      p_par.file.write("(clear ");
      p_par.file.write((Double.valueOf(curr_clearance)).toString());
      p_par.file.write(" (type ");
      p_par.identifier_type.write(cl_matrix.get_name(p_cl_class), p_par.file);
      p_par.file.write(DsnFile.CLASS_CLEARANCE_SEPARATOR);
      p_par.identifier_type.write(cl_matrix.get_name(i), p_par.file);
      p_par.file.write("))");
    }
  }

  public static ClearanceRule read_clearance_rule(IJFlexScanner p_scanner) {
    try {
      double value = p_scanner.next_double();

      Collection<String> class_pairs = new LinkedList<String>();
      Object next_token = p_scanner.next_token();
      if (next_token != Keyword.CLOSED_BRACKET) {
        // look for "(type"
        if (next_token != Keyword.OPEN_BRACKET) {
          FRLogger.warn("Rule.read_clearance_rule: ( expected");
          return null;
        }
        next_token = p_scanner.next_token();
        if (next_token != Keyword.TYPE) {
          FRLogger.warn("Rule.read_clearance_rule: type expected");
          return null;
        }

        class_pairs.addAll(List.of(p_scanner.next_string_list(DsnFile.CLASS_CLEARANCE_SEPARATOR)));

        // check the closing ")" of "(type"
        if (!p_scanner.next_closing_bracket()) {
          FRLogger.warn("Rule.read_clearance_rule: closing bracket expected");
          return null;
        }

        // check the closing ")" of "(clear"
        if (!p_scanner.next_closing_bracket()) {
          FRLogger.warn("Rule.read_clearance_rule: closing bracket expected");
          return null;
        }
      }

      return new ClearanceRule(value, class_pairs);
    } catch (java.io.IOException e) {
      FRLogger.error("Rule.read_clearance_rule: IO error scanning file", e);
      return null;
    }
  }

  public static void write_item_clearance_class(
      String p_name,
      app.freerouting.datastructures.IndentFileWriter p_file,
      app.freerouting.datastructures.IdentifierType p_identifier_type)
      throws java.io.IOException {
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
