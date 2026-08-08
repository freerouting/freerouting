package app.freerouting.io.specctra.parser;

import app.freerouting.board.Layer;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** Class for reading and writing rule scopes from dsn-files. */
public abstract class Rule {

  /** Returns a collection of objects of class Rule. */
  public static Collection<Rule> readScope(IJFlexScanner p_scanner) {
    Collection<Rule> result = new LinkedList<>();
    Object currentToken = null;
    for (; ; ) {
      Object prevToken = currentToken;
      try {
        currentToken = p_scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Rule.read_scope: IO error scanning file", e);
        return null;
      }
      if (currentToken == null) {
        FRLogger.warn(
            "Rule.read_scope: unexpected end of file at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (currentToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        // every rule starts with a "("
        Rule currRule = null;
        if (currentToken == Keyword.WIDTH) {
          // this is a "(width" rule
          currRule = readWidthRule(p_scanner);
        } else if (currentToken == Keyword.CLEARANCE) {
          // this is a "(clear" rule
          currRule = readClearanceRule(p_scanner);
        } else {
          ScopeKeyword.skipScope(p_scanner);
        }

        if (currRule != null) {
          result.add(currRule);
        }
      }
    }
    return result;
  }

  /** Reads a LayerRule from dsn-file. */
  public static LayerRule readLayerRuleScope(IJFlexScanner p_scanner) {
    try {
      Collection<String> layerNames = new LinkedList<>();
      Collection<Rule> ruleList = new LinkedList<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnStreamReader.LAYER_NAME);
        Object nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.OPEN_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: string expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        layerNames.add((String) nextToken);
      }
      for (; ; ) {
        Object nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken != Keyword.RULE) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: rule expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        ruleList.addAll(readScope(p_scanner));
      }
      return new LayerRule(layerNames, ruleList);
    } catch (IOException e) {
      FRLogger.error("Rule.read_layer_rule_scope: IO error scanning file", e);
      return null;
    }
  }

  public static WidthRule readWidthRule(IJFlexScanner p_scanner) {
    double value = p_scanner.nextDouble();

    if (!p_scanner.nextClosingBracket()) {
      return null;
    }

    return new WidthRule(value);
  }

  public static void writeScope(NetClass p_net_class, WriteScopeParameter p_par)
      throws IOException {
    p_par.file.startScope();
    p_par.file.write("rule");

    // write the trace width
    int defaultTraceHalfWidth = p_net_class.getTraceHalfWidth(0);
    double traceWidth = 2 * p_par.coordinateTransform.boardToDsn(defaultTraceHalfWidth);
    p_par.file.newLine();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(traceWidth));
    p_par.file.write(")");
    p_par.file.endScope();
    for (int i = 1; i < p_par.board.layerStructure.arr.length; i++) {
      if (p_net_class.getTraceHalfWidth(i) != defaultTraceHalfWidth) {
        writeLayerRule(p_net_class, i, p_par);
      }
    }
  }

  private static void writeLayerRule(
      NetClass p_net_class, int p_layer_no, WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("layer_rule ");

    Layer currBoardLayer = p_par.board.layerStructure.arr[p_layer_no];

    p_par.file.write(currBoardLayer.name);
    p_par.file.startScope();
    p_par.file.write("rule ");

    int currTraceHalfWidth = p_net_class.getTraceHalfWidth(p_layer_no);

    // write the trace width
    double traceWidth = 2 * p_par.coordinateTransform.boardToDsn(currTraceHalfWidth);
    p_par.file.newLine();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(traceWidth));
    p_par.file.write(") ");
    p_par.file.endScope();
    p_par.file.endScope();
  }

  /** Writes the default rule as a scope to an output dsn-file. */
  public static void writeDefaultRule(WriteScopeParameter p_par, int p_layer) throws IOException {
    p_par.file.startScope();
    p_par.file.write("rule");
    // write the trace width
    double traceWidth =
        2
            * p_par.coordinateTransform.boardToDsn(
                p_par.board.rules.getDefaultNetClass().getTraceHalfWidth(0));
    p_par.file.newLine();
    p_par.file.write("(width ");
    p_par.file.write(String.valueOf(traceWidth));
    p_par.file.write(")");
    // write the default clearance rule
    int defaultClNo = BoardRules.defaultClearanceClass();
    int defaultBoardClearance =
        p_par.board.rules.clearanceMatrix.getValue(defaultClNo, defaultClNo, p_layer, false);
    double defaultClearance = p_par.coordinateTransform.boardToDsn(defaultBoardClearance);
    p_par.file.newLine();
    // write the default clearance
    p_par.file.write("(clearance ");
    p_par.file.write(String.valueOf(defaultClearance));
    p_par.file.write(")");
    // write the smd_to_turn_gap
    double smdToTurnDist =
        p_par.coordinateTransform.boardToDsn(p_par.board.rules.getPinEdgeToTurnDist());
    p_par.file.newLine();
    p_par.file.write("(clearance ");
    p_par.file.write(String.valueOf(smdToTurnDist));
    p_par.file.write(" (type smd_to_turn_gap))");

    // write the named clearance rules from the clearance matrix
    writeNamedClearanceRules(p_par, p_layer);
    // write_non_default_clearance_rules(p_par, p_layer, defaultBoardClearance);

    p_par.file.endScope();
  }

  /** Write the clearance rules, which are different from the default clearance. */
  private static void writeNonDefaultClearanceRules(
      WriteScopeParameter p_par, int p_layer, int p_default_clearance) throws IOException {

    ClearanceMatrix clMatrix = p_par.board.rules.clearanceMatrix;
    int clCount = p_par.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i <= clCount; i++) {
      for (int j = i; j < clCount; j++) {
        int currBoardClearance = clMatrix.getValue(i, j, p_layer, false);

        if (currBoardClearance == p_default_clearance) {
          continue;
        }

        double currClearance = p_par.coordinateTransform.boardToDsn(currBoardClearance);
        p_par.file.newLine();
        p_par.file.write("(clearance ");
        p_par.file.write(String.valueOf(currClearance));
        p_par.file.write(" (type ");
        p_par.identifierType.write(clMatrix.getName(i), p_par.file);
        p_par.file.write(DsnFile.CLASS_CLEARANCE_SEPARATOR);
        p_par.identifierType.write(clMatrix.getName(j), p_par.file);
        p_par.file.write("))");
      }
    }
  }

  /** Write the clearance rules for the named classes in the clearance matrix. */
  private static void writeNamedClearanceRules(WriteScopeParameter p_par, int p_layer)
      throws IOException {

    ClearanceMatrix clMatrix = p_par.board.rules.clearanceMatrix;
    int clCount = p_par.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i < clCount; i++) {
      if (Objects.equals(clMatrix.getName(i), "default")) {
        continue;
      }

      int currBoardClearance = clMatrix.getValue(i, i, p_layer, false);
      double currClearance = p_par.coordinateTransform.boardToDsn(currBoardClearance);

      p_par.file.newLine();
      p_par.file.write("(clearance ");
      p_par.file.write(String.valueOf(currClearance));
      p_par.file.write(" (type ");
      p_par.identifierType.write(clMatrix.getName(i), p_par.file);
      p_par.file.write("))");
    }
  }

  public static ClearanceRule readClearanceRule(IJFlexScanner p_scanner) {
    try {
      double value = p_scanner.nextDouble();

      Collection<String> classPairs = new LinkedList<>();
      Object nextToken = p_scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        // look for "(type"
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Rule.read_clearance_rule: ( expected at '" + p_scanner.getScopeIdentifier() + "'");
          return null;
        }
        nextToken = p_scanner.nextToken();
        if (nextToken != Keyword.TYPE) {
          FRLogger.warn(
              "Rule.read_clearance_rule: type expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }

        classPairs.addAll(List.of(p_scanner.nextStringList(DsnFile.CLASS_CLEARANCE_SEPARATOR)));

        // check the closing ")" of "(type"
        if (!p_scanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }

        // check the closing ")" of "(clear"
        if (!p_scanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + p_scanner.getScopeIdentifier()
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
      String p_name, IndentFileWriter p_file, IdentifierType p_identifier_type) throws IOException {
    p_file.newLine();
    p_file.write("(clearanceClass ");
    p_identifier_type.write(p_name, p_file);
    p_file.write(")");
  }

  public static class WidthRule extends Rule {

    public final double value;

    public WidthRule(double p_value) {
      value = p_value;
    }
  }

  public static class ClearanceRule extends Rule {

    final double value;
    final Collection<String> clearanceClassPairs;

    public ClearanceRule(double p_value, Collection<String> p_class_pairs) {
      value = p_value;
      clearanceClassPairs = p_class_pairs;
    }
  }

  public static class LayerRule {

    final Collection<String> layerNames;
    final Collection<Rule> rules;

    LayerRule(Collection<String> p_layer_names, Collection<Rule> p_rules) {
      layerNames = p_layer_names;
      rules = p_rules;
    }
  }
}
