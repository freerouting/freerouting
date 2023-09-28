package app.freerouting.designforms.specctra;

import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

public class Circuit {
  /**
   * Currently only the length matching rule is read from a circuit scope. If the scope does not
   * contain a length matching rule, null is returned.
   */
  public static ReadScopeResult read_scope(IJFlexScanner p_scanner) {
    Object next_token = null;
    double min_trace_length = 0;
    double max_trace_length = 0;
    Collection<String> use_via = new LinkedList<>();
    Collection<String> use_layer = new LinkedList<>();
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_scope: IO error scanning file", e);
        return null;
      }
      if (next_token == null) {
        FRLogger.warn("Circuit.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == Keyword.OPEN_BRACKET) {
        if (next_token == Keyword.LENGTH) {
          LengthMatchingRule length_rule = read_length_scope(p_scanner);
          if (length_rule != null) {
            min_trace_length = length_rule.min_length;
            max_trace_length = length_rule.max_length;
          }
        } else if (next_token == Keyword.USE_VIA) {
          use_via.addAll(Structure.read_via_padstacks(p_scanner));
        } else if (next_token == Keyword.USE_LAYER) {
          use_layer.addAll(Arrays.stream(DsnFile.read_string_list_scope(p_scanner)).toList());
        } else {
          ScopeKeyword.skip_scope(p_scanner);
        }
      }
    }
    return new ReadScopeResult(max_trace_length, min_trace_length, use_via, use_layer);
  }

  static LengthMatchingRule read_length_scope(IJFlexScanner p_scanner) {
    LengthMatchingRule result;
    double[] length_arr = new double[2];
    Object next_token = null;
    for (int i = 0; i < 2; ++i) {
      try {
        next_token = p_scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (next_token instanceof Double) {
        length_arr[i] = (Double) next_token;
      } else if (next_token instanceof Integer) {
        length_arr[i] = (Integer) next_token;
      } else {
        FRLogger.warn("Circuit.read_length_scope: number expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
    }
    result = new LengthMatchingRule(length_arr[0], length_arr[1]);
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (next_token == null) {
        FRLogger.warn("Circuit.read_length_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      if (next_token == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skip_scope(p_scanner);
      }
    }
    return result;
  }

  /** A max_length of -1 indicates, tha no maximum length is defined. */
  public static class ReadScopeResult {
    public final double max_length;
    public final double min_length;
    public final Collection<String> use_via;
    public final Collection<String> use_layer;
    public ReadScopeResult(
        double p_max_length,
        double p_min_length,
        Collection<String> p_use_via,
        Collection<String> p_use_layer) {
      max_length = p_max_length;
      min_length = p_min_length;
      use_via = p_use_via;
      use_layer = p_use_layer;
    }
  }

  /** A max_length of -1 indicates, tha no maximum length is defined. */
  private static class LengthMatchingRule {
    public final double max_length;
    public final double min_length;
    public LengthMatchingRule(double p_max_length, double p_min_length) {
      max_length = p_max_length;
      min_length = p_min_length;
    }
  }
}
