package app.freerouting.io.specctra.parser;

import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.CornerStyle;
import app.freerouting.rules.MeanderPatternType;
import app.freerouting.rules.NetMeanderConstraint;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public final class Circuit {

  private Circuit() {}

  /**
   * Reads a circuit scope from a dsn-file. Length matching and meander pattern properties are
   * extracted if present.
   */
  public static ReadScopeResult readScope(IJFlexScanner scanner) {
    Object nextToken = null;
    double minTraceLength = 0;
    double maxTraceLength = 0;
    Collection<String> useVia = new LinkedList<>();
    Collection<String> useLayer = new LinkedList<>();
    MeanderDescriptorBuilder meanderBuilder = new MeanderDescriptorBuilder();

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
        if (meanderBuilder.readDescriptor(nextToken, scanner)) {
          // Handled meander descriptor
        } else if (Keyword.matches(nextToken, Keyword.LENGTH)) {
          LengthMatchingRule lengthRule = readLengthScope(scanner);
          if (lengthRule != null) {
            minTraceLength = lengthRule.minLength;
            maxTraceLength = lengthRule.maxLength;
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
    return new ReadScopeResult(
        maxTraceLength,
        minTraceLength,
        useVia,
        useLayer,
        meanderBuilder.minAmplitude,
        meanderBuilder.maxAmplitude,
        meanderBuilder.spacing,
        meanderBuilder.cornerStyle,
        meanderBuilder.radiusPercentage,
        meanderBuilder.singleSided,
        meanderBuilder.patternType);
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

  static Double parseDouble(Object token) {
    if (token instanceof Double d) {
      return d;
    }
    if (token instanceof Integer i) {
      return i.doubleValue();
    }
    if (token instanceof String s) {
      try {
        return Double.parseDouble(s);
      } catch (NumberFormatException _) {
        return null;
      }
    }
    return null;
  }

  static AmplitudeRule readLengthAmplitudeScope(IJFlexScanner scanner) {
    double maxAmp = -1;
    double minAmp = 0;
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Circuit.read_length_amplitude: IO error", e);
      return null;
    }
    Double parsedMax = parseDouble(nextToken);
    if (parsedMax != null) {
      maxAmp = parsedMax;
    } else {
      FRLogger.warn(
          "Circuit.read_length_amplitude: number expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      skipToClosedBracket(scanner);
      return null;
    }

    boolean minRead = false;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.read_length_amplitude: IO error", e);
        return null;
      }
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (!minRead && prevToken != Keyword.OPEN_BRACKET) {
        Double parsedMin = parseDouble(nextToken);
        if (parsedMin != null) {
          minAmp = parsedMin;
          minRead = true;
          continue;
        }
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        ScopeKeyword.skipScope(scanner);
      }
    }
    if (maxAmp > 0 && minAmp > 0 && minAmp > maxAmp) {
      double temp = minAmp;
      minAmp = maxAmp;
      maxAmp = temp;
    }
    return new AmplitudeRule(maxAmp, minAmp);
  }

  static Double readNumberScope(IJFlexScanner scanner) {
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Circuit.read_number_scope: IO error scanning file", e);
      return null;
    }
    Double result = parseDouble(nextToken);
    if (result == null) {
      FRLogger.warn(
          "Circuit.read_number_scope: number expected at '" + scanner.getScopeIdentifier() + "'");
    }
    skipToClosedBracket(scanner);
    return result;
  }

  static String readStringScope(IJFlexScanner scanner) {
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Circuit.read_string_scope: IO error scanning file", e);
      return null;
    }
    String result = null;
    if (nextToken instanceof String str) {
      result = str;
    } else if (nextToken instanceof Keyword kw) {
      result = kw.getName();
    }
    skipToClosedBracket(scanner);
    return result;
  }

  static Boolean readBooleanScope(IJFlexScanner scanner) {
    String str = readStringScope(scanner);
    if (str == null) {
      return null;
    }
    return str.equalsIgnoreCase("on")
        || str.equalsIgnoreCase("true")
        || str.equalsIgnoreCase("yes")
        || str.equalsIgnoreCase("1");
  }

  static void readMeanderPatternScopeInto(IJFlexScanner scanner, MeanderDescriptorBuilder builder) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Circuit.readMeanderPatternScopeInto: IO error scanning file", e);
        return;
      }
      if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (Keyword.matches(nextToken, Keyword.TYPE)
            || Keyword.matches(nextToken, Keyword.PATTERN_TYPE)) {
          String typeStr = readStringScope(scanner);
          if (typeStr != null) {
            builder.patternType = MeanderPatternType.parse(typeStr);
          }
        } else if (!builder.readDescriptor(nextToken, scanner)) {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
  }

  static void skipToClosedBracket(IJFlexScanner scanner) {
    for (; ; ) {
      try {
        Object token = scanner.nextToken();
        if (token == null || token == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (token == Keyword.OPEN_BRACKET) {
          ScopeKeyword.skipScope(scanner);
        }
      } catch (IOException e) {
        FRLogger.error("Circuit.skipToClosedBracket: IO error", e);
        break;
      }
    }
  }

  /** Accumulates and reads meander pattern parameters across circuit and rule scopes. */
  public static class MeanderDescriptorBuilder {

    public Double minAmplitude;
    public Double maxAmplitude;
    public Double spacing;
    public CornerStyle cornerStyle;
    public Double radiusPercentage;
    public Boolean singleSided;
    public MeanderPatternType patternType;

    /**
     * Attempts to read a meander descriptor matching the given token from the scanner.
     *
     * @param token current token
     * @param scanner active scanner
     * @return true if token matched a meander descriptor, false otherwise
     */
    public boolean readDescriptor(Object token, IJFlexScanner scanner) {
      if (Keyword.matches(token, Keyword.LENGTH_AMPLITUDE)) {
        AmplitudeRule amp = readLengthAmplitudeScope(scanner);
        if (amp != null) {
          this.maxAmplitude = amp.maxAmplitude;
          this.minAmplitude = amp.minAmplitude;
        }
        return true;
      }
      if (Keyword.matches(token, Keyword.LENGTH_GAP)) {
        Double gap = readNumberScope(scanner);
        if (gap != null) {
          this.spacing = gap;
        }
        return true;
      }
      if (Keyword.matches(token, Keyword.CORNER_STYLE)) {
        String styleStr = readStringScope(scanner);
        if (styleStr != null) {
          this.cornerStyle = CornerStyle.parse(styleStr);
        }
        return true;
      }
      if (Keyword.matches(token, Keyword.RADIUS)) {
        Double rad = readNumberScope(scanner);
        if (rad != null) {
          this.radiusPercentage = rad;
        }
        return true;
      }
      if (Keyword.matches(token, Keyword.SINGLE_SIDED)) {
        Boolean ss = readBooleanScope(scanner);
        if (ss != null) {
          this.singleSided = ss;
        }
        return true;
      }
      if (Keyword.matches(token, Keyword.MEANDER_PATTERN)) {
        readMeanderPatternScopeInto(scanner, this);
        return true;
      }
      return false;
    }

    /** Returns true if any meander parameter has been populated. */
    public boolean hasAttributes() {
      return maxAmplitude != null
          || minAmplitude != null
          || spacing != null
          || cornerStyle != null
          || radiusPercentage != null
          || singleSided != null
          || patternType != null;
    }

    /** Converts the accumulated descriptors into a MeanderRule. */
    public Rule.MeanderRule toMeanderRule() {
      return new Rule.MeanderRule(
          minAmplitude,
          maxAmplitude,
          spacing,
          cornerStyle,
          radiusPercentage,
          singleSided,
          patternType);
    }
  }

  public static class ReadScopeResult {

    public final double maxLength;
    public final double minLength;
    public final Collection<String> useVia;
    public final Collection<String> useLayer;
    public final Double minAmplitude;
    public final Double maxAmplitude;
    public final Double spacing;
    public final CornerStyle cornerStyle;
    public final Double radiusPercentage;
    public final Boolean singleSided;
    public final MeanderPatternType patternType;

    public ReadScopeResult(
        double maxLength,
        double minLength,
        Collection<String> useVia,
        Collection<String> useLayer) {
      this(maxLength, minLength, useVia, useLayer, null, null, null, null, null, null, null);
    }

    public ReadScopeResult(
        double maxLength,
        double minLength,
        Collection<String> useVia,
        Collection<String> useLayer,
        Double minAmplitude,
        Double maxAmplitude,
        Double spacing,
        CornerStyle cornerStyle,
        Double radiusPercentage,
        Boolean singleSided,
        MeanderPatternType patternType) {
      this.maxLength = maxLength;
      this.minLength = minLength;
      this.useVia = useVia;
      this.useLayer = useLayer;
      this.minAmplitude = minAmplitude;
      this.maxAmplitude = maxAmplitude;
      this.spacing = spacing;
      this.cornerStyle = cornerStyle;
      this.radiusPercentage = radiusPercentage;
      this.singleSided = singleSided;
      this.patternType = patternType;
    }

    /** Returns true if any meander parameter is defined in this circuit scope. */
    public boolean hasMeanderConstraint() {
      return (this.maxAmplitude != null && this.maxAmplitude > 0)
          || (this.spacing != null && this.spacing > 0)
          || this.cornerStyle != null
          || this.singleSided != null;
    }

    /**
     * Converts the parsed circuit meander attributes into a {@link NetMeanderConstraint}.
     *
     * @param defaultType default pattern type if none specified
     * @param transform coordinate transform from DSN to board coordinates
     * @return populated NetMeanderConstraint in board units, or null if unconstrained
     */
    public NetMeanderConstraint toMeanderConstraint(
        MeanderPatternType defaultType, CoordinateTransform transform) {
      if (!hasMeanderConstraint()) {
        return null;
      }
      MeanderPatternType type = (this.patternType != null) ? this.patternType : defaultType;
      NetMeanderConstraint preset = NetMeanderConstraint.createDefaultPreset(type, 1.0);
      return NetMeanderConstraint.fromDsn(
          this.minAmplitude,
          this.maxAmplitude,
          this.spacing,
          this.cornerStyle,
          this.radiusPercentage,
          this.singleSided,
          transform,
          preset);
    }

    /**
     * Converts the parsed circuit meander attributes into a {@link NetMeanderConstraint} using the
     * single track preset as fallback.
     *
     * @param transform coordinate transform from DSN to board coordinates
     * @return populated NetMeanderConstraint in board units, or null if unconstrained
     */
    public NetMeanderConstraint toMeanderConstraint(CoordinateTransform transform) {
      return toMeanderConstraint(MeanderPatternType.SINGLE_TRACK, transform);
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

  static class AmplitudeRule {

    public final double maxAmplitude;
    public final double minAmplitude;

    public AmplitudeRule(double maxAmplitude, double minAmplitude) {
      this.maxAmplitude = maxAmplitude;
      this.minAmplitude = minAmplitude;
    }
  }
}
