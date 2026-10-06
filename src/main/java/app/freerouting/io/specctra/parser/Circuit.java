package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import app.freerouting.rules.NetMeanderConstraint;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public final class Circuit {

  private Circuit() {}

  /**
   * Currently only the length matching rule is read from a circuit scope. If the scope does not
   * contain a length matching rule, null is returned.
   */
  public static ReadScopeResult readScope(IJFlexScanner scanner) {
    Object nextToken = null;
    double minTraceLength = 0;
    double maxTraceLength = 0;
    Collection<String> useVia = new LinkedList<>();
    Collection<String> useLayer = new LinkedList<>();
    Collection<Rule> meanderRules = new LinkedList<>();
    Rule.LengthAmplitudeRule amplitudeRule = null;
    Rule.LengthGapRule gapRule = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_scope: unexpected end of file at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.LENGTH) {
          LengthMatchingRule lengthRule = readLengthScope(scanner);
          if (lengthRule != null) {
            minTraceLength = lengthRule.minLength;
            maxTraceLength = lengthRule.maxLength;
          }
        } else if (Rule.isKeyword(nextToken, Keyword.LENGTH_AMPLITUDE, "length_amplitude")) {
          Rule.LengthAmplitudeRule parsedAmp = Rule.readLengthAmplitudeRule(scanner);
          if (parsedAmp != null) {
            meanderRules.add(parsedAmp);
            if (parsedAmp.target == NetMeanderConstraint.MeanderTarget.SINGLE_TRACK
                && amplitudeRule == null) {
              amplitudeRule = parsedAmp;
            }
          }
        } else if (Rule.isKeyword(nextToken, Keyword.LENGTH_GAP, "length_gap")) {
          Rule.LengthGapRule parsedGap = Rule.readLengthGapRule(scanner);
          if (parsedGap != null) {
            meanderRules.add(parsedGap);
            if (parsedGap.target == NetMeanderConstraint.MeanderTarget.SINGLE_TRACK
                && gapRule == null) {
              gapRule = parsedGap;
            }
          }
        } else if (nextToken == Keyword.USE_VIA) {
          useVia.addAll(Structure.readViaPadstacks(scanner));
        } else if (nextToken == Keyword.USE_LAYER) {
          useLayer.addAll(Arrays.stream(DsnFile.readStringListScope(scanner)).toList());
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    if (amplitudeRule == null) {
      for (Rule r : meanderRules) {
        if (r instanceof Rule.LengthAmplitudeRule a) {
          amplitudeRule = a;
          break;
        }
      }
    }
    if (gapRule == null) {
      for (Rule r : meanderRules) {
        if (r instanceof Rule.LengthGapRule g) {
          gapRule = g;
          break;
        }
      }
    }
    return new ReadScopeResult(
        maxTraceLength, minTraceLength, useVia, useLayer, amplitudeRule, gapRule, meanderRules);
  }

  static LengthMatchingRule readLengthScope(IJFlexScanner scanner) {
    double maxLength = -1;
    double minLength = 0;
    Object nextToken;

    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
      return null;
    }

    if (nextToken instanceof Double doubleVal) {
      maxLength = doubleVal;
    } else if (nextToken instanceof Integer intVal) {
      maxLength = intVal;
    } else {
      FRLogger.warn(
          "Circuit.read_length_scope: number expected at '" + scanner.getScopeIdentifier() + "'");
      return null;
    }

    boolean minLengthRead = false;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Circuit.read_length_scope: unexpected end of file at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (!minLengthRead && prevToken != Keyword.OPEN_BRACKET) {
        if (nextToken instanceof Double doubleVal) {
          minLength = doubleVal;
          minLengthRead = true;
          continue;
        } else if (nextToken instanceof Integer intVal) {
          minLength = intVal;
          minLengthRead = true;
          continue;
        }
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skipScope(scanner);
      }
    }
    return new LengthMatchingRule(maxLength, minLength);
  }

  /** A maxLength of -1 indicates that no maximum length is defined. */
  public static class ReadScopeResult {

    public final double maxLength;
    public final double minLength;
    public final Collection<String> useVia;
    public final Collection<String> useLayer;
    public final Rule.LengthAmplitudeRule amplitudeRule;
    public final Rule.LengthGapRule gapRule;
    public final Collection<Rule> meanderRules;

    public ReadScopeResult(
        double maxLength,
        double minLength,
        Collection<String> useVia,
        Collection<String> useLayer) {
      this(maxLength, minLength, useVia, useLayer, null, null, Collections.emptyList());
    }

    public ReadScopeResult(
        double maxLength,
        double minLength,
        Collection<String> useVia,
        Collection<String> useLayer,
        Rule.LengthAmplitudeRule amplitudeRule,
        Rule.LengthGapRule gapRule) {
      this(
          maxLength,
          minLength,
          useVia,
          useLayer,
          amplitudeRule,
          gapRule,
          collectRules(amplitudeRule, gapRule));
    }

    public ReadScopeResult(
        double maxLength,
        double minLength,
        Collection<String> useVia,
        Collection<String> useLayer,
        Rule.LengthAmplitudeRule amplitudeRule,
        Rule.LengthGapRule gapRule,
        Collection<Rule> meanderRules) {
      this.maxLength = maxLength;
      this.minLength = minLength;
      this.useVia = useVia;
      this.useLayer = useLayer;
      this.amplitudeRule = amplitudeRule;
      this.gapRule = gapRule;
      this.meanderRules = meanderRules != null ? meanderRules : Collections.emptyList();
    }

    private static Collection<Rule> collectRules(
        Rule.LengthAmplitudeRule amp, Rule.LengthGapRule gap) {
      Collection<Rule> rules = new LinkedList<>();
      if (amp != null) {
        rules.add(amp);
      }
      if (gap != null) {
        rules.add(gap);
      }
      return rules;
    }
  }

  /** A maxLength of -1 indicates that no maximum length is defined. */
  static class LengthMatchingRule {

    public final double maxLength;
    public final double minLength;

    public LengthMatchingRule(double maxLength, double minLength) {
      this.maxLength = maxLength;
      this.minLength = minLength;
    }
  }
}
