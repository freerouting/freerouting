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
  public static Collection<Rule> readScope(IJFlexScanner pScanner) {
    Collection<Rule> result = new LinkedList<>();
    Object currentToken = null;
    for (; ; ) {
      Object prevToken = currentToken;
      try {
        currentToken = pScanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Rule.read_scope: IO error scanning file", e);
        return null;
      }
      if (currentToken == null) {
        FRLogger.warn(
            "Rule.read_scope: unexpected end of file at '" + pScanner.getScopeIdentifier() + "'");
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
          currRule = readWidthRule(pScanner);
        } else if (currentToken == Keyword.CLEARANCE) {
          // this is a "(clear" rule
          currRule = readClearanceRule(pScanner);
        } else {
          ScopeKeyword.skipScope(pScanner);
        }

        if (currRule != null) {
          result.add(currRule);
        }
      }
    }
    return result;
  }

  /** Reads a LayerRule from dsn-file. */
  public static LayerRule readLayerRuleScope(IJFlexScanner pScanner) {
    try {
      Collection<String> layerNames = new LinkedList<>();
      Collection<Rule> ruleList = new LinkedList<>();
      for (; ; ) {
        pScanner.yybegin(SpecctraDsnStreamReader.LAYER_NAME);
        Object nextToken = pScanner.nextToken();
        if (nextToken == Keyword.OPEN_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: string expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        layerNames.add((String) nextToken);
      }
      for (; ; ) {
        Object nextToken = pScanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken != Keyword.RULE) {

          FRLogger.warn(
              "Rule.read_layer_rule_scope: rule expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        ruleList.addAll(readScope(pScanner));
      }
      return new LayerRule(layerNames, ruleList);
    } catch (IOException e) {
      FRLogger.error("Rule.read_layer_rule_scope: IO error scanning file", e);
      return null;
    }
  }

  public static WidthRule readWidthRule(IJFlexScanner pScanner) {
    double value = pScanner.nextDouble();

    if (!pScanner.nextClosingBracket()) {
      return null;
    }

    return new WidthRule(value);
  }

  public static void writeScope(NetClass pNetClass, WriteScopeParameter pPar) throws IOException {
    pPar.file.startScope();
    pPar.file.write("rule");

    // write the trace width
    int defaultTraceHalfWidth = pNetClass.getTraceHalfWidth(0);
    double traceWidth = 2 * pPar.coordinateTransform.boardToDsn(defaultTraceHalfWidth);
    pPar.file.newLine();
    pPar.file.write("(width ");
    pPar.file.write(String.valueOf(traceWidth));
    pPar.file.write(")");
    pPar.file.endScope();
    for (int i = 1; i < pPar.board.layerStructure.arr.length; i++) {
      if (pNetClass.getTraceHalfWidth(i) != defaultTraceHalfWidth) {
        writeLayerRule(pNetClass, i, pPar);
      }
    }
  }

  private static void writeLayerRule(NetClass pNetClass, int pLayerNo, WriteScopeParameter pPar)
      throws IOException {
    pPar.file.startScope();
    pPar.file.write("layer_rule ");

    Layer currBoardLayer = pPar.board.layerStructure.arr[pLayerNo];

    pPar.file.write(currBoardLayer.name);
    pPar.file.startScope();
    pPar.file.write("rule ");

    int currTraceHalfWidth = pNetClass.getTraceHalfWidth(pLayerNo);

    // write the trace width
    double traceWidth = 2 * pPar.coordinateTransform.boardToDsn(currTraceHalfWidth);
    pPar.file.newLine();
    pPar.file.write("(width ");
    pPar.file.write(String.valueOf(traceWidth));
    pPar.file.write(") ");
    pPar.file.endScope();
    pPar.file.endScope();
  }

  /** Writes the default rule as a scope to an output dsn-file. */
  public static void writeDefaultRule(WriteScopeParameter pPar, int pLayer) throws IOException {
    pPar.file.startScope();
    pPar.file.write("rule");
    // write the trace width
    double traceWidth =
        2
            * pPar.coordinateTransform.boardToDsn(
                pPar.board.rules.getDefaultNetClass().getTraceHalfWidth(0));
    pPar.file.newLine();
    pPar.file.write("(width ");
    pPar.file.write(String.valueOf(traceWidth));
    pPar.file.write(")");
    // write the default clearance rule
    int defaultClNo = BoardRules.defaultClearanceClass();
    int defaultBoardClearance =
        pPar.board.rules.clearanceMatrix.getValue(defaultClNo, defaultClNo, pLayer, false);
    double defaultClearance = pPar.coordinateTransform.boardToDsn(defaultBoardClearance);
    pPar.file.newLine();
    // write the default clearance
    pPar.file.write("(clearance ");
    pPar.file.write(String.valueOf(defaultClearance));
    pPar.file.write(")");
    // write the smd_to_turn_gap
    double smdToTurnDist =
        pPar.coordinateTransform.boardToDsn(pPar.board.rules.getPinEdgeToTurnDist());
    pPar.file.newLine();
    pPar.file.write("(clearance ");
    pPar.file.write(String.valueOf(smdToTurnDist));
    pPar.file.write(" (type smd_to_turn_gap))");

    // write the named clearance rules from the clearance matrix
    writeNamedClearanceRules(pPar, pLayer);
    // write_non_default_clearance_rules(p_par, p_layer, defaultBoardClearance);

    pPar.file.endScope();
  }

  /** Write the clearance rules, which are different from the default clearance. */
  private static void writeNonDefaultClearanceRules(
      WriteScopeParameter pPar, int pLayer, int pDefaultClearance) throws IOException {

    ClearanceMatrix clMatrix = pPar.board.rules.clearanceMatrix;
    int clCount = pPar.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i <= clCount; i++) {
      for (int j = i; j < clCount; j++) {
        int currBoardClearance = clMatrix.getValue(i, j, pLayer, false);

        if (currBoardClearance == pDefaultClearance) {
          continue;
        }

        double currClearance = pPar.coordinateTransform.boardToDsn(currBoardClearance);
        pPar.file.newLine();
        pPar.file.write("(clearance ");
        pPar.file.write(String.valueOf(currClearance));
        pPar.file.write(" (type ");
        pPar.identifierType.write(clMatrix.getName(i), pPar.file);
        pPar.file.write(DsnFile.CLASS_CLEARANCE_SEPARATOR);
        pPar.identifierType.write(clMatrix.getName(j), pPar.file);
        pPar.file.write("))");
      }
    }
  }

  /** Write the clearance rules for the named classes in the clearance matrix. */
  private static void writeNamedClearanceRules(WriteScopeParameter pPar, int pLayer)
      throws IOException {

    ClearanceMatrix clMatrix = pPar.board.rules.clearanceMatrix;
    int clCount = pPar.board.rules.clearanceMatrix.getClassCount();

    for (int i = 1; i < clCount; i++) {
      if (Objects.equals(clMatrix.getName(i), "default")) {
        continue;
      }

      int currBoardClearance = clMatrix.getValue(i, i, pLayer, false);
      double currClearance = pPar.coordinateTransform.boardToDsn(currBoardClearance);

      pPar.file.newLine();
      pPar.file.write("(clearance ");
      pPar.file.write(String.valueOf(currClearance));
      pPar.file.write(" (type ");
      pPar.identifierType.write(clMatrix.getName(i), pPar.file);
      pPar.file.write("))");
    }
  }

  public static ClearanceRule readClearanceRule(IJFlexScanner pScanner) {
    try {
      double value = pScanner.nextDouble();

      Collection<String> classPairs = new LinkedList<>();
      Object nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        // look for "(type"
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Rule.read_clearance_rule: ( expected at '" + pScanner.getScopeIdentifier() + "'");
          return null;
        }
        nextToken = pScanner.nextToken();
        if (nextToken != Keyword.TYPE) {
          FRLogger.warn(
              "Rule.read_clearance_rule: type expected at '" + pScanner.getScopeIdentifier() + "'");
          return null;
        }

        classPairs.addAll(List.of(pScanner.nextStringList(DsnFile.CLASS_CLEARANCE_SEPARATOR)));

        // check the closing ")" of "(type"
        if (!pScanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }

        // check the closing ")" of "(clear"
        if (!pScanner.nextClosingBracket()) {
          FRLogger.warn(
              "Rule.read_clearance_rule: closing bracket expected at '"
                  + pScanner.getScopeIdentifier()
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
      String pName, IndentFileWriter pFile, IdentifierType pIdentifierType) throws IOException {
    pFile.newLine();
    pFile.write("(clearanceClass ");
    pIdentifierType.write(pName, pFile);
    pFile.write(")");
  }

  public static class WidthRule extends Rule {

    public final double value;

    public WidthRule(double pValue) {
      value = pValue;
    }
  }

  public static class ClearanceRule extends Rule {

    final double value;
    final Collection<String> clearanceClassPairs;

    public ClearanceRule(double pValue, Collection<String> pClassPairs) {
      value = pValue;
      clearanceClassPairs = pClassPairs;
    }
  }

  public static class LayerRule {

    final Collection<String> layerNames;
    final Collection<Rule> rules;

    LayerRule(Collection<String> pLayerNames, Collection<Rule> pRules) {
      layerNames = pLayerNames;
      rules = pRules;
    }
  }
}
