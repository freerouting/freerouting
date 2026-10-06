package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.NetMeanderConstraint;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Class for reading and writing rule scopes from dsn-files. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public abstract class Rule {

  /** Returns a collection of objects of class Rule. */
  public static Collection<Rule> readScope(IJFlexScanner scanner) {
    Collection<Rule> result = new LinkedList<>();
    Object currentToken = null;
    for (; ; ) {
      Object prevToken = currentToken;
      try {
        currentToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Rule.read_scope: IO error scanning file", e);
        return null;
      }
      if (currentToken == null) {
        FRLogger.warn(
            "Rule.read_scope: unexpected end of file at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      if (currentToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        // every rule starts with a "("
        Rule currentRule = null;
        if (currentToken == Keyword.WIDTH) {
          // this is a "(width" rule
          currentRule = readWidthRule(scanner);
        } else if (currentToken == Keyword.CLEARANCE) {
          // this is a "(clear" rule
          currentRule = readClearanceRule(scanner);
        } else if (currentToken == Keyword.LENGTH) {
          // this is a "(length" rule
          Circuit.LengthMatchingRule lengthResult = Circuit.readLengthScope(scanner);
          if (lengthResult != null) {
            currentRule = new LengthRule(lengthResult.maxLength, lengthResult.minLength);
          }
        } else if (isKeyword(currentToken, Keyword.LENGTH_AMPLITUDE, "length_amplitude")) {
          // this is a "(length_amplitude" rule
          currentRule = readLengthAmplitudeRule(scanner);
        } else if (isKeyword(currentToken, Keyword.LENGTH_GAP, "length_gap")) {
          // this is a "(length_gap" rule
          currentRule = readLengthGapRule(scanner);
        } else {
          ScopeKeyword.skipScope(scanner);
        }

        if (currentRule != null) {
          result.add(currentRule);
        }
      }
    }
    return result;
  }

  /** Reads a LayerRule from dsn-file. */
  public static LayerRule readLayerRuleScope(IJFlexScanner scanner) {
    try {
      Collection<String> layerNames = new LinkedList<>();
      Collection<Rule> ruleList = new LinkedList<>();
      for (; ; ) {
        scanner.yybegin(SpecctraDsnStreamReader.LAYER_NAME);
        Object nextToken = scanner.nextToken();
        if (nextToken == Keyword.OPEN_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: string expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        layerNames.add((String) nextToken);
      }
      for (; ; ) {
        Object nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken != Keyword.RULE) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: rule expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        ruleList.addAll(readScope(scanner));
      }
      return new LayerRule(layerNames, ruleList);
    } catch (IOException e) {
      FRLogger.error("Rule.read_layer_rule_scope: IO error scanning file", e);
      return null;
    }
  }

  public static WidthRule readWidthRule(IJFlexScanner scanner) {
    double value = scanner.nextDouble();

    if (!scanner.nextClosingBracket()) {
      return null;
    }

    return new WidthRule(value);
  }

  public static LengthAmplitudeRule readLengthAmplitudeRule(IJFlexScanner scanner) {
    double maxAmplitude = -1;
    double minAmplitude = 0;
    boolean singleSided = false;
    NetMeanderConstraint.CornerStyle cornerStyle = NetMeanderConstraint.CornerStyle.AUTO;
    int cornerRadiusPercentage = NetMeanderConstraint.DEFAULT_CORNER_RADIUS_PERCENT;
    NetMeanderConstraint.MeanderTarget target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;

    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Rule.readLengthAmplitudeRule: IO error scanning file", e);
      return null;
    }

    if (nextToken instanceof Double doubleVal) {
      maxAmplitude = doubleVal;
    } else if (nextToken instanceof Integer intVal) {
      maxAmplitude = intVal;
    } else {
      FRLogger.warn(
          "Rule.readLengthAmplitudeRule: number expected at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }

    boolean minAmplitudeRead = false;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Rule.readLengthAmplitudeRule: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Rule.readLengthAmplitudeRule: unexpected end of file at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (!minAmplitudeRead && prevToken != Keyword.OPEN_BRACKET) {
        if (nextToken instanceof Double doubleVal) {
          minAmplitude = doubleVal;
          minAmplitudeRead = true;
          continue;
        } else if (nextToken instanceof Integer intVal) {
          minAmplitude = intVal;
          minAmplitudeRead = true;
          continue;
        }
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (isKeyword(nextToken, Keyword.TYPE, "type")) {
          String typeStr = scanner.nextString();
          if (typeStr != null
              && (typeStr.equalsIgnoreCase("trombone")
                  || typeStr.equalsIgnoreCase("single_sided"))) {
            singleSided = true;
          }
          scanner.nextClosingBracket();
        } else if (nextToken instanceof String s && s.equalsIgnoreCase("single_sided")) {
          singleSided = DsnFile.readOnOffScope(scanner);
        } else if (nextToken instanceof String s && s.equalsIgnoreCase("corner")) {
          cornerStyle = NetMeanderConstraint.CornerStyle.parse(scanner.nextString());
          scanner.nextClosingBracket();
        } else if (nextToken instanceof String s && s.equalsIgnoreCase("radius")) {
          double radiusVal = scanner.nextDouble();
          if (radiusVal > 0) {
            cornerRadiusPercentage = (int) Math.round(radiusVal);
          }
          scanner.nextClosingBracket();
        } else if (nextToken instanceof String s && s.equalsIgnoreCase("target")) {
          target = NetMeanderConstraint.MeanderTarget.parse(scanner.nextString());
          scanner.nextClosingBracket();
        } else {
          ScopeKeyword.skipScope(scanner);
        }
      }
    }

    return new LengthAmplitudeRule(
        maxAmplitude, minAmplitude, singleSided, cornerStyle, cornerRadiusPercentage, target);
  }

  public static LengthGapRule readLengthGapRule(IJFlexScanner scanner) {
    try {
      double value = scanner.nextDouble();
      NetMeanderConstraint.MeanderTarget target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
      Object nextToken = null;
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = scanner.nextToken();
        if (nextToken == null || nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken instanceof String s && s.equalsIgnoreCase("target")) {
            target = NetMeanderConstraint.MeanderTarget.parse(scanner.nextString());
            scanner.nextClosingBracket();
          } else {
            ScopeKeyword.skipScope(scanner);
          }
        }
      }
      return new LengthGapRule(value, target);
    } catch (IOException e) {
      FRLogger.error("Rule.readLengthGapRule: IO error scanning file", e);
      return null;
    }
  }

  static boolean isKeyword(Object token, Keyword keyword, String name) {
    if (token == keyword) {
      return true;
    }
    return token instanceof String s && s.equalsIgnoreCase(name);
  }

  public static void writeScope(NetClass netClass, WriteScopeParameter scopeParameter)
      throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("rule");

    // write the trace width
    int defaultTraceHalfWidth = netClass.getTraceHalfWidth(0);
    double traceWidth = 2 * scopeParameter.coordinateTransform.boardToDsn(defaultTraceHalfWidth);
    scopeParameter.file.newLine();
    scopeParameter.file.write("(width ");
    scopeParameter.file.write(String.valueOf(traceWidth));
    scopeParameter.file.write(")");
    scopeParameter.file.endScope();
    for (int i = 1; i < scopeParameter.board.layerStructure.layers.length; i++) {
      if (netClass.getTraceHalfWidth(i) != defaultTraceHalfWidth) {
        writeLayerRule(netClass, i, scopeParameter);
      }
    }
  }

  private static void writeLayerRule(
      NetClass netClass, int layerIndex, WriteScopeParameter scopeParameter) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("layer_rule ");

    app.freerouting.board.model.structure.Layer currentBoardLayer =
        scopeParameter.board.layerStructure.layers[layerIndex];

    scopeParameter.file.write(currentBoardLayer.name);
    scopeParameter.file.startScope();
    scopeParameter.file.write("rule ");

    int currentTraceHalfWidth = netClass.getTraceHalfWidth(layerIndex);

    // write the trace width
    double traceWidth = 2 * scopeParameter.coordinateTransform.boardToDsn(currentTraceHalfWidth);
    scopeParameter.file.newLine();
    scopeParameter.file.write("(width ");
    scopeParameter.file.write(String.valueOf(traceWidth));
    scopeParameter.file.write(") ");
    scopeParameter.file.endScope();
    scopeParameter.file.endScope();
  }

  /** Writes the default rule as a scope to an output dsn-file. */
  public static void writeDefaultRule(WriteScopeParameter scopeParameter, int layer)
      throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("rule");
    // write the trace width
    double traceWidth =
        2
            * scopeParameter.coordinateTransform.boardToDsn(
                scopeParameter.board.rules.getDefaultNetClass().getTraceHalfWidth(0));
    scopeParameter.file.newLine();
    scopeParameter.file.write("(width ");
    scopeParameter.file.write(String.valueOf(traceWidth));
    scopeParameter.file.write(")");
    // write the default clearance rule
    int defaultClNo = BoardRules.defaultClearanceClass();
    int defaultBoardClearance =
        scopeParameter.board.rules.clearanceMatrix.getValue(defaultClNo, defaultClNo, layer, false);
    double defaultClearance = scopeParameter.coordinateTransform.boardToDsn(defaultBoardClearance);
    scopeParameter.file.newLine();
    // write the default clearance
    scopeParameter.file.write("(clearance ");
    scopeParameter.file.write(String.valueOf(defaultClearance));
    scopeParameter.file.write(")");
    // write the smd_to_turn_gap
    double smdToTurnDist =
        scopeParameter.coordinateTransform.boardToDsn(
            scopeParameter.board.rules.getPinEdgeToTurnDist());
    scopeParameter.file.newLine();
    scopeParameter.file.write("(clearance ");
    scopeParameter.file.write(String.valueOf(smdToTurnDist));
    scopeParameter.file.write(" (type smd_to_turn_gap))");

    // write the named clearance rules from the clearance matrix
    writeNamedClearanceRules(scopeParameter, layer);
    // write_non_default_clearance_rules(scopeParameter, layer, defaultBoardClearance);

    for (NetMeanderConstraint.MeanderTarget target : NetMeanderConstraint.MeanderTarget.values()) {
      NetMeanderConstraint constraint =
          scopeParameter.board.rules.getDefaultMeanderConstraint(target);
      if (constraint != null) {
        writeMeanderRules(constraint, scopeParameter);
      }
    }

    scopeParameter.file.endScope();
  }

  private static double toBoardDimension(double dsnValue, CoordinateTransform transform) {
    if (dsnValue > 0) {
      return transform.dsnToBoard(dsnValue);
    }
    return dsnValue == 0.0 ? 0.0 : NetMeanderConstraint.UNSPECIFIED;
  }

  private static double toDsnDimension(double boardValue, CoordinateTransform transform) {
    if (boardValue > 0) {
      return transform.boardToDsn(boardValue);
    }
    return boardValue == 0.0 ? 0.0 : -1.0;
  }

  /** Writes meander rules (length_amplitude and length_gap) if defined in the constraint. */
  public static void writeMeanderRules(
      NetMeanderConstraint constraint, WriteScopeParameter scopeParameter) throws IOException {
    if (constraint == null) {
      return;
    }
    boolean shouldWriteAmp =
        constraint.hasMaxAmplitude()
            || constraint.maxAmplitude() == 0.0
            || constraint.singleSided()
            || constraint.hasCustomCornerStyle()
            || (constraint.target() != null
                && constraint.target() != NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    if (shouldWriteAmp) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(length_amplitude ");
      scopeParameter.file.write(
          String.valueOf(
              toDsnDimension(constraint.maxAmplitude(), scopeParameter.coordinateTransform)));

      if (constraint.hasMinAmplitude()) {
        scopeParameter.file.write(" ");
        scopeParameter.file.write(
            String.valueOf(
                scopeParameter.coordinateTransform.boardToDsn(constraint.minAmplitude())));
      }
      if (constraint.singleSided()) {
        scopeParameter.file.write(" (type trombone)");
      }
      if (constraint.hasCustomCornerStyle()) {
        scopeParameter.file.write(" (corner ");
        scopeParameter.file.write(constraint.cornerStyle().toDsn());
        scopeParameter.file.write(")");
      }
      if (constraint.cornerRadiusPercentage() > 0
          && constraint.cornerRadiusPercentage()
              != NetMeanderConstraint.DEFAULT_CORNER_RADIUS_PERCENT) {
        scopeParameter.file.write(" (radius ");
        scopeParameter.file.write(String.valueOf(constraint.cornerRadiusPercentage()));
        scopeParameter.file.write(")");
      }
      if (constraint.target() != null
          && constraint.target() != NetMeanderConstraint.MeanderTarget.SINGLE_TRACK) {
        scopeParameter.file.write(" (target ");
        scopeParameter.file.write(constraint.target().toDsn());
        scopeParameter.file.write(")");
      }
      scopeParameter.file.write(")");
    }

    if (constraint.hasGap() || constraint.gap() == 0.0) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(length_gap ");
      scopeParameter.file.write(
          String.valueOf(toDsnDimension(constraint.gap(), scopeParameter.coordinateTransform)));
      if (constraint.target() != null
          && constraint.target() != NetMeanderConstraint.MeanderTarget.SINGLE_TRACK) {
        scopeParameter.file.write(" (target ");
        scopeParameter.file.write(constraint.target().toDsn());
        scopeParameter.file.write(")");
      }
      scopeParameter.file.write(")");
    }
  }

  /** Write the clearance rules, which are different from the default clearance. */
  private static void writeNonDefaultClearanceRules(
      WriteScopeParameter scopeParameter, int layer, int defaultClearance) throws IOException {

    ClearanceMatrix clMatrix = scopeParameter.board.rules.clearanceMatrix;
    int clCount = scopeParameter.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i <= clCount; i++) {
      for (int j = i; j < clCount; j++) {
        int currentBoardClearance = clMatrix.getValue(i, j, layer, false);

        if (currentBoardClearance == defaultClearance) {
          continue;
        }

        double currentClearance =
            scopeParameter.coordinateTransform.boardToDsn(currentBoardClearance);
        scopeParameter.file.newLine();
        scopeParameter.file.write("(clearance ");
        scopeParameter.file.write(String.valueOf(currentClearance));
        scopeParameter.file.write(" (type ");
        scopeParameter.identifierType.write(clMatrix.getName(i), scopeParameter.file);
        scopeParameter.file.write(DsnFile.CLASS_CLEARANCE_SEPARATOR);
        scopeParameter.identifierType.write(clMatrix.getName(j), scopeParameter.file);
        scopeParameter.file.write("))");
      }
    }
  }

  /** Write the clearance rules for the named classes in the clearance matrix. */
  private static void writeNamedClearanceRules(WriteScopeParameter scopeParameter, int layer)
      throws IOException {

    ClearanceMatrix clMatrix = scopeParameter.board.rules.clearanceMatrix;
    int clCount = scopeParameter.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i < clCount; i++) {
      if (Objects.equals(clMatrix.getName(i), "default")) {
        continue;
      }

      int currentBoardClearance = clMatrix.getValue(i, i, layer, false);
      double currentClearance =
          scopeParameter.coordinateTransform.boardToDsn(currentBoardClearance);

      scopeParameter.file.newLine();
      scopeParameter.file.write("(clearance ");
      scopeParameter.file.write(String.valueOf(currentClearance));
      scopeParameter.file.write(" (type ");
      scopeParameter.identifierType.write(clMatrix.getName(i), scopeParameter.file);
      scopeParameter.file.write("))");
    }
  }

  public static ClearanceRule readClearanceRule(IJFlexScanner scanner) {
    try {
      double value = scanner.nextDouble();

      Collection<String> classPairs = new LinkedList<>();
      Object nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        // look for "(type"
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Rule.read_clearance_rule: ( expected at '" + scanner.getScopeIdentifier() + "'");
          return null;
        }
        nextToken = scanner.nextToken();
        if (nextToken != Keyword.TYPE) {
          FRLogger.warn(
              "Rule.read_clearance_rule: type expected at '" + scanner.getScopeIdentifier() + "'");
          return null;
        }

        classPairs.addAll(List.of(scanner.nextStringList(DsnFile.CLASS_CLEARANCE_SEPARATOR)));

        // check the closing ")" of "(type"
        if (!scanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }

        // check the closing ")" of "(clear"
        if (!scanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }

      return new ClearanceRule(value, classPairs);
    } catch (IOException e) {
      FRLogger.error("Rule.read_clearance_rule: IO error scanning file", e);
      return null;
    }
  }

  public static void writeItemClearanceClass(
      String name, IndentFileWriter file, IdentifierType identifierType) throws IOException {
    file.newLine();
    file.write("(clearance_class ");
    identifierType.write(name, file);
    file.write(")");
  }

  public static class WidthRule extends Rule {

    public final double value;

    public WidthRule(double value) {
      this.value = value;
    }
  }

  public static class ClearanceRule extends Rule {

    final double value;
    final Collection<String> clearanceClassPairs;

    public ClearanceRule(double value, Collection<String> classPairs) {
      this.value = value;
      clearanceClassPairs = classPairs;
    }
  }

  public static class LengthRule extends Rule {

    public final double maxLength;
    public final double minLength;

    public LengthRule(double maxLength, double minLength) {
      this.maxLength = maxLength;
      this.minLength = minLength;
    }
  }

  public static class LengthAmplitudeRule extends Rule {

    public final double maxAmplitude;
    public final double minAmplitude;
    public final boolean singleSided;
    public final NetMeanderConstraint.CornerStyle cornerStyle;
    public final int cornerRadiusPercentage;
    public final NetMeanderConstraint.MeanderTarget target;

    public LengthAmplitudeRule(double maxAmplitude, double minAmplitude) {
      this(
          maxAmplitude,
          minAmplitude,
          false,
          NetMeanderConstraint.CornerStyle.AUTO,
          NetMeanderConstraint.DEFAULT_CORNER_RADIUS_PERCENT,
          NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    }

    public LengthAmplitudeRule(
        double maxAmplitude,
        double minAmplitude,
        boolean singleSided,
        NetMeanderConstraint.CornerStyle cornerStyle,
        int cornerRadiusPercentage) {
      this(
          maxAmplitude,
          minAmplitude,
          singleSided,
          cornerStyle,
          cornerRadiusPercentage,
          NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    }

    public LengthAmplitudeRule(
        double maxAmplitude,
        double minAmplitude,
        boolean singleSided,
        NetMeanderConstraint.CornerStyle cornerStyle,
        int cornerRadiusPercentage,
        NetMeanderConstraint.MeanderTarget target) {
      this.maxAmplitude = maxAmplitude;
      this.minAmplitude = minAmplitude;
      this.singleSided = singleSided;
      this.cornerStyle = cornerStyle;
      this.cornerRadiusPercentage = cornerRadiusPercentage;
      this.target = target != null ? target : NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }

    public NetMeanderConstraint toConstraint(CoordinateTransform coordinateTransform) {
      return new NetMeanderConstraint(
          toBoardDimension(maxAmplitude, coordinateTransform),
          toBoardDimension(minAmplitude, coordinateTransform),
          NetMeanderConstraint.UNSPECIFIED,
          singleSided,
          cornerStyle != null ? cornerStyle : NetMeanderConstraint.CornerStyle.AUTO,
          cornerRadiusPercentage,
          target);
    }
  }

  public static class LengthGapRule extends Rule {

    public final double gap;
    public final NetMeanderConstraint.MeanderTarget target;

    public LengthGapRule(double gap) {
      this(gap, NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
    }

    public LengthGapRule(double gap, NetMeanderConstraint.MeanderTarget target) {
      this.gap = gap;
      this.target = target != null ? target : NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }

    public NetMeanderConstraint toConstraint(CoordinateTransform coordinateTransform) {
      return new NetMeanderConstraint(
          NetMeanderConstraint.UNSPECIFIED,
          NetMeanderConstraint.UNSPECIFIED,
          toBoardDimension(gap, coordinateTransform),
          false,
          NetMeanderConstraint.CornerStyle.AUTO,
          NetMeanderConstraint.DEFAULT_CORNER_RADIUS_PERCENT,
          target);
    }
  }

  /**
   * Combines length_amplitude, length_gap, and collection of rules into an aggregated {@link
   * NetMeanderConstraint} for the single-track target. Returns {@code null} if no matching meander
   * rules are present.
   */
  public static NetMeanderConstraint buildMeanderConstraint(
      LengthAmplitudeRule ampRule,
      LengthGapRule gapRule,
      Collection<Rule> rules,
      CoordinateTransform coordinateTransform) {
    return buildMeanderConstraint(
        ampRule,
        gapRule,
        rules,
        coordinateTransform,
        NetMeanderConstraint.MeanderTarget.SINGLE_TRACK);
  }

  /**
   * Combines length_amplitude, length_gap, and collection of rules into an aggregated {@link
   * NetMeanderConstraint} for the specified target. Returns {@code null} if no matching meander
   * rules are present.
   */
  public static NetMeanderConstraint buildMeanderConstraint(
      LengthAmplitudeRule ampRule,
      LengthGapRule gapRule,
      Collection<Rule> rules,
      CoordinateTransform coordinateTransform,
      NetMeanderConstraint.MeanderTarget target) {
    if (target == null) {
      target = NetMeanderConstraint.MeanderTarget.SINGLE_TRACK;
    }
    NetMeanderConstraint constraint = null;
    if (ampRule != null && ampRule.target == target) {
      constraint = ampRule.toConstraint(coordinateTransform);
    }
    if (gapRule != null && gapRule.target == target) {
      constraint =
          NetMeanderConstraint.merge(constraint, gapRule.toConstraint(coordinateTransform));
    }
    if (rules != null) {
      for (Rule r : rules) {
        if (r instanceof Rule.LengthAmplitudeRule amp && amp.target == target) {
          constraint =
              NetMeanderConstraint.merge(constraint, amp.toConstraint(coordinateTransform));
        } else if (r instanceof Rule.LengthGapRule gap && gap.target == target) {
          constraint =
              NetMeanderConstraint.merge(constraint, gap.toConstraint(coordinateTransform));
        }
      }
    }
    return constraint;
  }

  /**
   * Builds meander constraints across all {@link NetMeanderConstraint.MeanderTarget} types present
   * in the specified rules collection.
   */
  public static Map<NetMeanderConstraint.MeanderTarget, NetMeanderConstraint>
      buildAllMeanderConstraints(Collection<Rule> rules, CoordinateTransform coordinateTransform) {
    Map<NetMeanderConstraint.MeanderTarget, NetMeanderConstraint> map =
        new EnumMap<>(NetMeanderConstraint.MeanderTarget.class);
    for (NetMeanderConstraint.MeanderTarget target : NetMeanderConstraint.MeanderTarget.values()) {
      NetMeanderConstraint constraint =
          buildMeanderConstraint(null, null, rules, coordinateTransform, target);
      if (constraint != null) {
        map.put(target, constraint);
      }
    }
    return map;
  }

  public static class LayerRule {

    final Collection<String> layerNames;
    final Collection<Rule> rules;

    LayerRule(Collection<String> layerNames, Collection<Rule> rules) {
      this.layerNames = layerNames;
      this.rules = rules;
    }
  }
}
