package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

public final class Circuit {

  private Circuit() {}

  /**
   * Currently only the length matching rule is read from a circuit scope. If the scope does not
   * contain a length matching rule, null is returned.
   */
  public static ReadScopeResult readScope(IJFlexScanner p_scanner) {
    Object nextToken = null;
    double minTraceLength = 0;
    double maxTraceLength = 0;
    Collection<String> useVia = new LinkedList<>();
    Collection<String> useLayer = new LinkedList<>();
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_scope: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.LENGTH) {
          LengthMatchingRule lengthRule = readLengthScope(p_scanner);
          if (lengthRule != null) {
            minTraceLength = lengthRule.minLength;
            maxTraceLength = lengthRule.maxLength;
          }
        } else if (nextToken == Keyword.USE_VIA) {
          useVia.addAll(Structure.readViaPadstacks(p_scanner));
        } else if (nextToken == Keyword.USE_LAYER) {
          useLayer.addAll(Arrays.stream(DsnFile.readStringListScope(p_scanner)).toList());
        } else {
          ScopeKeyword.skipScope(p_scanner);
        }
      }
    }
    return new ReadScopeResult(maxTraceLength, minTraceLength, useVia, useLayer);
  }

  static LengthMatchingRule readLengthScope(IJFlexScanner p_scanner) {
    LengthMatchingRule result;
    double[] lengthArr = new double[2];
    Object nextToken = null;
    for (int i = 0; i < 2; i++) {
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken instanceof Double double1) {
        lengthArr[i] = double1;
      } else if (nextToken instanceof Integer integer) {
        lengthArr[i] = integer;
      } else {
        FRLogger.warn(
            "Circuit.read_length_scope: number expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
    }
    result = new LengthMatchingRule(lengthArr[0], lengthArr[1]);
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_length_scope: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skipScope(p_scanner);
      }
    }
    return result;
  }

  /** A maxLength of -1 indicates that no maximum length is defined. */
  public static class ReadScopeResult {

    public final double maxLength;
    public final double minLength;
    public final Collection<String> useVia;
    public final Collection<String> useLayer;

    public ReadScopeResult(
        double p_max_length,
        double p_min_length,
        Collection<String> p_use_via,
        Collection<String> p_use_layer) {
      maxLength = p_max_length;
      minLength = p_min_length;
      useVia = p_use_via;
      useLayer = p_use_layer;
    }
  }

  /** A maxLength of -1 indicates that no maximum length is defined. */
  private static class LengthMatchingRule {

    public final double maxLength;
    public final double minLength;

    public LengthMatchingRule(double p_max_length, double p_min_length) {
      maxLength = p_max_length;
      minLength = p_min_length;
    }
  }
}
