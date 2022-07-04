package app.freerouting.designforms.specctra;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;

public class AutorouteSettings {

  static app.freerouting.interactive.AutorouteSettings read_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    app.freerouting.interactive.AutorouteSettings result =
        new app.freerouting.interactive.AutorouteSettings(p_layer_structure.arr.length);
    boolean with_fanout = false;
    boolean with_autoroute = true;
    boolean with_postroute = true;
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("AutorouteSettings.read_scope: IO error scanning file", e);
        return null;
      }
      if (next_token == null) {
        FRLogger.warn("AutorouteSettings.read_scope: unexpected end of file");
        return null;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.FANOUT) {
          with_fanout = DsnFile.read_on_off_scope(p_scanner);
        } else if (next_token == Keyword.AUTOROUTE) {
          with_autoroute = DsnFile.read_on_off_scope(p_scanner);
        } else if (next_token == Keyword.POSTROUTE) {
          with_postroute = DsnFile.read_on_off_scope(p_scanner);
        } else if (next_token == Keyword.VIAS) {
          result.set_vias_allowed(DsnFile.read_on_off_scope(p_scanner));
        } else if (next_token == Keyword.VIA_COSTS) {
          result.set_via_costs(DsnFile.read_integer_scope(p_scanner));
        } else if (next_token == Keyword.PLANE_VIA_COSTS) {
          result.set_plane_via_costs(DsnFile.read_integer_scope(p_scanner));
        } else if (next_token == Keyword.START_RIPUP_COSTS) {
          result.set_start_ripup_costs(DsnFile.read_integer_scope(p_scanner));
        } else if (next_token == Keyword.START_PASS_NO) {
          result.set_start_pass_no(DsnFile.read_integer_scope(p_scanner));
        } else if (next_token == Keyword.LAYER_RULE) {
          result = read_layer_rule(p_scanner, p_layer_structure, result);
          if (result == null) {
            return null;
          }
        } else {
          ScopeKeyword.skip_scope(p_scanner);
        }
      }
    }
    result.set_with_fanout(with_fanout);
    result.set_with_autoroute(with_autoroute);
    result.set_with_postroute(with_postroute);
    return result;
  }

  static app.freerouting.interactive.AutorouteSettings read_layer_rule(
      IJFlexScanner p_scanner,
      LayerStructure p_layer_structure,
      app.freerouting.interactive.AutorouteSettings p_settings) {
    p_scanner.yybegin(SpecctraDsnFileReader.NAME);
    Object next_token;
    try {
      next_token = p_scanner.next_token();
    } catch (java.io.IOException e) {
      FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
      return null;
    }
    if (!(next_token instanceof String)) {
      FRLogger.warn("AutorouteSettings.read_layer_rule: String expected");
      return null;
    }
    int layer_no = p_layer_structure.get_no((String) next_token);
    if (layer_no < 0) {
      FRLogger.warn("AutorouteSettings.read_layer_rule: layer not found");
      return null;
    }
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
        return null;
      }
      if (next_token == null) {
        FRLogger.warn("AutorouteSettings.read_layer_rule: unexpected end of file");
        return null;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.ACTIVE) {
          p_settings.set_layer_active(layer_no, DsnFile.read_on_off_scope(p_scanner));
        } else if (next_token == Keyword.PREFERRED_DIRECTION) {
          try {
            boolean pref_dir_is_horizontal = true;
            next_token = p_scanner.next_token();
            if (next_token == Keyword.VERTICAL) {
              pref_dir_is_horizontal = false;
            } else if (next_token != Keyword.HORIZONTAL) {
              FRLogger.warn("AutorouteSettings.read_layer_rule: unexpected key word");
              return null;
            }
            p_settings.set_preferred_direction_is_horizontal(layer_no, pref_dir_is_horizontal);
            next_token = p_scanner.next_token();
            if (next_token != Keyword.CLOSED_BRACKET) {
              FRLogger.warn("AutorouteSettings.read_layer_rule: uclosing bracket expected");
              return null;
            }
          } catch (java.io.IOException e) {
            FRLogger.error("AutorouteSettings.read_layer_rule: IO error scanning file", e);
            return null;
          }
        } else if (next_token == Keyword.PREFERRED_DIRECTION_TRACE_COSTS) {
          p_settings.set_preferred_direction_trace_costs(
              layer_no, DsnFile.read_float_scope(p_scanner));
        } else if (next_token == Keyword.AGAINST_PREFERRED_DIRECTION_TRACE_COSTS) {
          p_settings.set_against_preferred_direction_trace_costs(
              layer_no, DsnFile.read_float_scope(p_scanner));
        } else {
          ScopeKeyword.skip_scope(p_scanner);
        }
      }
    }
    return p_settings;
  }

  static void write_scope(
      IndentFileWriter p_file,
      app.freerouting.interactive.AutorouteSettings p_settings,
      app.freerouting.board.LayerStructure p_layer_structure,
      IdentifierType p_identifier_type)
      throws java.io.IOException {
    p_file.start_scope();
    p_file.write("autoroute_settings");
    p_file.new_line();
    p_file.write("(fanout ");
    if (p_settings.get_with_fanout()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.new_line();
    p_file.write("(autoroute ");
    if (p_settings.get_with_autoroute()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.new_line();
    p_file.write("(postroute ");
    if (p_settings.get_with_postroute()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.new_line();
    p_file.write("(vias ");
    if (p_settings.get_vias_allowed()) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.new_line();
    p_file.write("(via_costs ");
    {
      Integer via_costs = p_settings.get_via_costs();
      p_file.write(via_costs.toString());
    }
    p_file.write(")");
    p_file.new_line();
    p_file.write("(plane_via_costs ");
    {
      Integer via_costs = p_settings.get_plane_via_costs();
      p_file.write(via_costs.toString());
    }
    p_file.write(")");
    p_file.new_line();
    p_file.write("(start_ripup_costs ");
    {
      Integer ripup_costs = p_settings.get_start_ripup_costs();
      p_file.write(ripup_costs.toString());
    }
    p_file.write(")");
    p_file.new_line();
    p_file.write("(start_pass_no ");
    {
      Integer pass_no = p_settings.get_start_pass_no();
      p_file.write(pass_no.toString());
    }
    p_file.write(")");
    for (int i = 0; i < p_layer_structure.arr.length; ++i) {
      app.freerouting.board.Layer curr_layer = p_layer_structure.arr[i];
      p_file.start_scope();
      p_file.write("layer_rule ");
      p_identifier_type.write(curr_layer.name, p_file);
      p_file.new_line();
      p_file.write("(active ");
      if (p_settings.get_layer_active(i)) {
        p_file.write("on)");
      } else {
        p_file.write("off)");
      }
      p_file.new_line();
      p_file.write("(preferred_direction ");
      if (p_settings.get_preferred_direction_is_horizontal(i)) {
        p_file.write("horizontal)");
      } else {
        p_file.write("vertical)");
      }
      p_file.new_line();
      p_file.write("(preferred_direction_trace_costs ");
      Float trace_costs = (float) p_settings.get_preferred_direction_trace_costs(i);
      p_file.write(trace_costs.toString());
      p_file.write(")");
      p_file.new_line();
      p_file.write("(against_preferred_direction_trace_costs ");
      trace_costs = (float) p_settings.get_against_preferred_direction_trace_costs(i);
      p_file.write(trace_costs.toString());
      p_file.write(")");
      p_file.end_scope();
    }
    p_file.end_scope();
  }
}
