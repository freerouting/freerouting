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
  public static ReadScopeResult readScope(IJFlexScanner pScanner) {
    Object nextToken = null;
    double minTraceLength = 0;
    double maxTraceLength = 0;
    Collection<String> useVia = new LinkedList<>();
    Collection<String> useLayer = new LinkedList<>();
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_scope: unexpected end of file at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.LENGTH) {
          LengthMatchingRule lengthRule = readLengthScope(pScanner);
          if (lengthRule != null) {
            minTraceLength = lengthRule.minLength;
            maxTraceLength = lengthRule.maxLength;
          }
        } else if (nextToken == Keyword.USE_VIA) {
          useVia.addAll(Structure.readViaPadstacks(pScanner));
        } else if (nextToken == Keyword.USE_LAYER) {
          useLayer.addAll(Arrays.stream(DsnFile.readStringListScope(pScanner)).toList());
        } else {
          ScopeKeyword.skipScope(pScanner);
        }
      }
    }
    return new ReadScopeResult(maxTraceLength, minTraceLength, useVia, useLayer);
  }

  static LengthMatchingRule readLengthScope(IJFlexScanner pScanner) {
    LengthMatchingRule result;
    double[] lengthArr = new double[2];
    Object nextToken = null;
    for (int i = 0; i < 2; i++) {
      try {
        nextToken = pScanner.nextToken();
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
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
    }
    result = new LengthMatchingRule(lengthArr[0], lengthArr[1]);
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_length_scope: unexpected end of file at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skipScope(pScanner);
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
        double pMaxLength,
        double pMinLength,
        Collection<String> pUseVia,
        Collection<String> pUseLayer) {
      maxLength = pMaxLength;
      minLength = pMinLength;
      useVia = pUseVia;
      useLayer = pUseLayer;
    }
  }

  /** A maxLength of -1 indicates that no maximum length is defined. */
  private static class LengthMatchingRule {

    public final double maxLength;
    public final double minLength;

    public LengthMatchingRule(double pMaxLength, double pMinLength) {
      maxLength = pMaxLength;
      minLength = pMinLength;
    }
  }
}
