package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.FixedState;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.library.LogicalPart;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Padstack;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.io.KiCadNetClassNames;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Class for reading and writing net network from dsn-files. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class Network extends ScopeKeyword {

  /** Creates a new instance of Network. */
  public Network() {
    super("network");
  }

  public static void writeScope(WriteScopeParameter scopeParameter) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("network");
    Collection<Pin> boardPins = scopeParameter.board.getPins();
    for (int i = 1; i <= scopeParameter.board.rules.nets.maxNetNumber(); i++) {
      Net.writeScope(scopeParameter, scopeParameter.board.rules.nets.get(i), boardPins);
    }
    writeViaInfos(scopeParameter.board.rules, scopeParameter.file, scopeParameter.identifierType);
    writeViaRules(scopeParameter.board.rules, scopeParameter.file, scopeParameter.identifierType);
    writeNetClasses(scopeParameter);
    scopeParameter.file.endScope();
  }

  public static void writeViaInfos(
      BoardRules rules, IndentFileWriter file, IdentifierType identifierType) throws IOException {
    for (int i = 0; i < rules.viaInfos.count(); i++) {
      final ViaInfo currentVia = rules.viaInfos.get(i);
      file.startScope();
      file.write("via ");
      file.newLine();
      identifierType.write(currentVia.getName(), file);
      file.write(" ");
      identifierType.write(currentVia.getPadstack().name, file);
      file.write(" ");
      identifierType.write(rules.clearanceMatrix.getName(currentVia.getClearanceClass()), file);
      if (currentVia.attachSmdAllowed()) {
        file.write(" attach");
      }
      file.endScope();
    }
  }

  public static void writeViaRules(
      BoardRules rules, IndentFileWriter file, IdentifierType identifierType) throws IOException {
    for (ViaRule currentRule : rules.viaRules) {
      file.startScope();
      file.write("viaRule");
      file.newLine();
      identifierType.write(currentRule.name, file);
      for (int i = 0; i < currentRule.viaCount(); i++) {
        file.write(" ");
        identifierType.write(currentRule.getVia(i).getName(), file);
      }
      file.endScope();
    }
  }

  public static void writeNetClasses(WriteScopeParameter scopeParameter) throws IOException {
    for (int i = 0; i < scopeParameter.board.rules.netClasses.count(); i++) {
      writeNetClass(scopeParameter.board.rules.netClasses.get(i), scopeParameter);
    }
  }

  public static void writeNetClass(
      app.freerouting.rules.NetClass netClass, WriteScopeParameter scopeParameter)
      throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("class ");
    scopeParameter.identifierType.write(netClass.getName(), scopeParameter.file);
    final int netsPerRow = 8;
    int netCounter = 0;
    for (int i = 1; i <= scopeParameter.board.rules.nets.maxNetNumber(); i++) {
      if (scopeParameter.board.rules.nets.get(i).getNetClass() == netClass) {
        if (netCounter % netsPerRow == 0) {
          scopeParameter.file.newLine();
        } else {
          scopeParameter.file.write(" ");
        }
        scopeParameter.identifierType.write(
            scopeParameter.board.rules.nets.get(i).name, scopeParameter.file);
        ++netCounter;
      }
    }

    // write the trace clearance class
    Rule.writeItemClearanceClass(
        scopeParameter.board.rules.clearanceMatrix.getName(netClass.getTraceClearanceClass()),
        scopeParameter.file,
        scopeParameter.identifierType);

    if (netClass.getViaRule() != null) {
      // write the via rule
      scopeParameter.file.newLine();
      scopeParameter.file.write("(viaRule ");
      scopeParameter.identifierType.write(netClass.getViaRule().name, scopeParameter.file);
      scopeParameter.file.write(")");
    }

    // write the rules, if they are different from the default rule.
    Rule.writeScope(netClass, scopeParameter);

    writeCircuit(netClass, scopeParameter);

    if (!netClass.getPullTight()) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(pullTight off)");
    }

    if (netClass.isShoveFixed()) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(shoveFixed on)");
    }

    scopeParameter.file.endScope();
  }

  private static void writeCircuit(
      app.freerouting.rules.NetClass netClass, WriteScopeParameter scopeParameter)
      throws IOException {
    final double minTraceLength = netClass.getMinimumTraceLength();
    final double maxTraceLength = netClass.getMaximumTraceLength();
    scopeParameter.file.startScope();
    scopeParameter.file.write("circuit ");
    scopeParameter.file.newLine();
    scopeParameter.file.write("(useLayer");
    int layerCount = netClass.layerCount();
    for (int i = 0; i < layerCount; i++) {
      if (netClass.isActiveRoutingLayer(i)) {
        scopeParameter.file.write(" ");
        scopeParameter.file.write(scopeParameter.board.layerStructure.layers[i].name);
      }
    }
    scopeParameter.file.write(")");
    if (minTraceLength > 0 || maxTraceLength > 0) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(length ");
      double transformedMaxLength;
      if (maxTraceLength <= 0) {
        transformedMaxLength = -1;
      } else {
        transformedMaxLength = scopeParameter.coordinateTransform.boardToDsn(maxTraceLength);
      }
      scopeParameter.file.write(String.valueOf(transformedMaxLength));
      scopeParameter.file.write(" ");
      double transformedMinLength;
      if (minTraceLength <= 0) {
        transformedMinLength = 0;
      } else {
        transformedMinLength = scopeParameter.coordinateTransform.boardToDsn(minTraceLength);
      }
      scopeParameter.file.write(String.valueOf(transformedMinLength));
      scopeParameter.file.write(")");
    }
    scopeParameter.file.endScope();
  }

  /** Creates a sequence of subnets with 2 pins from pinList. */
  private static Collection<Collection<Net.Pin>> createOrderedSubnets(Collection<Net.Pin> pinList) {
    Collection<Collection<Net.Pin>> result = new LinkedList<>();
    if (pinList.isEmpty()) {
      return result;
    }

    Iterator<Net.Pin> it = pinList.iterator();
    Net.Pin prevPin = it.next();
    while (it.hasNext()) {
      Net.Pin nextPin = it.next();
      Set<Net.Pin> currentSubnetPinList = new TreeSet<>();
      currentSubnetPinList.add(prevPin);
      currentSubnetPinList.add(nextPin);
      result.add(currentSubnetPinList);
      prevPin = nextPin;
    }
    return result;
  }

  private static boolean readNetPins(IJFlexScanner scanner, Collection<Net.Pin> pinList) {
    Object nextToken;
    String componentName;
    String pinName;
    while (!(componentName = ((SpecctraDsnStreamReader) scanner).nextString(true, '-')).isEmpty()) {

      try {
        scanner.yybegin(SpecctraDsnStreamReader.SPEC_CHAR);
        nextToken = scanner.nextToken(); // overread the hyphen
      } catch (IOException e) {
        FRLogger.error("Network.read_net_pins: IO error while scanning file", e);
        return false;
      }

      pinName = scanner.nextString(true);
      Net.Pin currentEntry = new Net.Pin(componentName, pinName);
      pinList.add(currentEntry);
    }

    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_pins: IO error scanning file", e);
      return false;
    }
    if (nextToken == null) {
      FRLogger.warn(
          "Network.read_net_pins: unexpected end of file at '"
              + scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (nextToken != CLOSED_BRACKET) {
      // not end of scope
      FRLogger.warn(
          "Network.read_net_pins: expected closed bracket is missing at '"
              + scanner.getScopeIdentifier()
              + "'");
    }

    return true;
  }

  public static ViaInfo readViaInfo(IJFlexScanner scanner, BasicBoard board) {
    try {
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      scanner.setScopeIdentifier(padstackName);
      Padstack viaPadstack = board.library.getViaPadstack(padstackName);
      if (viaPadstack == null) {
        // The padstack may not yet be inserted into the list of via padstacks
        viaPadstack = board.library.padstacks.get(padstackName);
        if (viaPadstack == null) {
          FRLogger.warn(
              "Network.read_via_info: padstack not found at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        board.library.addViaPadstack(viaPadstack);
      }
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      int clearanceClass = board.rules.clearanceMatrix.getNo((String) nextToken);
      if (clearanceClass < 0) {
        // Clearance class not stored, because it is identical to the default clearance netClass.
        clearanceClass = BoardRules.defaultClearanceClass();
      }
      boolean attachAllowed = false;
      nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.ATTACH) {
          FRLogger.warn(
              "Network.read_via_info: Keyword.ATTACH expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        attachAllowed = true;
        nextToken = scanner.nextToken();
        if (nextToken != Keyword.CLOSED_BRACKET) {
          FRLogger.warn(
              "Network.read_via_info: closing bracket expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }
      return new ViaInfo(name, viaPadstack, clearanceClass, attachAllowed, board.rules);
    } catch (IOException e) {
      FRLogger.error("Network.read_via_info: IO error while scanning file", e);
      return null;
    }
  }

  public static Collection<String> readViaRule(IJFlexScanner scanner, BasicBoard board) {
    try {
      Collection<String> result = new LinkedList<>();
      for (; ; ) {
        scanner.yybegin(SpecctraDsnStreamReader.NAME);
        Object nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Network.read_via_rule: string expected at '" + scanner.getScopeIdentifier() + "'");
          return null;
        }
        result.add((String) nextToken);
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Network.read_via_rule: IO error while scanning file", e);
      return null;
    }
  }

  private static void insertViaInfos(
      Collection<ViaInfo> viaInfos, RoutingBoard board, boolean attachAllowed) {
    if (!viaInfos.isEmpty()) {
      for (ViaInfo currentInfo : viaInfos) {
        board.rules.viaInfos.add(currentInfo);
      }
    } else {
      // No via infos found; create default via infos from the via padstacks.
      createDefaultViaInfos(board, board.rules.getDefaultNetClass(), attachAllowed);
    }
  }

  private static void createDefaultViaInfos(
      BasicBoard board, app.freerouting.rules.NetClass netClass, boolean attachAllowed) {
    int clClass =
        netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    boolean isDefaultClass = netClass == board.rules.getDefaultNetClass();
    for (int i = 0; i < board.library.viaPadstackCount(); i++) {
      Padstack currentPadstack = board.library.getViaPadstack(i);
      boolean viaAttachAllowed = attachAllowed && currentPadstack.attachAllowed;
      String viaName;
      if (isDefaultClass) {
        viaName = currentPadstack.name;
      } else {
        viaName = currentPadstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + netClass.getName();
      }
      ViaInfo foundViaInfo =
          new ViaInfo(viaName, currentPadstack, clClass, viaAttachAllowed, board.rules);
      board.rules.viaInfos.add(foundViaInfo);
    }
  }

  private static void insertViaRules(Collection<Collection<String>> viaRules, BasicBoard board) {
    boolean ruleFound = false;
    for (Collection<String> currentList : viaRules) {
      if (currentList.size() < 2) {
        continue;
      }
      if (addViaRule(currentList, board)) {
        ruleFound = true;
      }
    }
    if (!ruleFound) {
      board.rules.createDefaultViaRule(board.rules.getDefaultNetClass(), "default");
    }
    for (int i = 0; i < board.rules.netClasses.count(); i++) {
      board.rules.netClasses.get(i).setViaRule(board.rules.getDefaultViaRule());
    }
  }

  /** Inserts a via rule into the board. Replaces an already existing via rule with the same */
  public static boolean addViaRule(Collection<String> nameList, BasicBoard board) {
    Iterator<String> it = nameList.iterator();
    String ruleName = it.next();
    ViaRule existingRule = board.rules.getViaRule(ruleName);
    ViaRule currentRule = new ViaRule(ruleName);
    boolean ruleOk = true;
    while (it.hasNext()) {
      final ViaInfo currentVia = board.rules.viaInfos.get(it.next());
      if (currentVia != null) {
        currentRule.appendVia(currentVia);
      } else {
        FRLogger.warn("Network.insert_via_rules: viaInfo not found");
        ruleOk = false;
      }
    }
    if (ruleOk) {
      if (existingRule != null) {
        // Replace already existing rule.
        board.rules.viaRules.remove(existingRule);
      }
      board.rules.viaRules.add(currentRule);
    }
    return ruleOk;
  }

  private static void insertNetClasses(
      Collection<NetClass> netClasses, ReadScopeParameter scopeParameter) {
    BasicBoard routingBoard = scopeParameter.boardHandling.getRoutingBoard();
    for (NetClass currentClass : netClasses) {
      insertNetClass(
          currentClass,
          scopeParameter.layerStructure,
          routingBoard,
          scopeParameter.coordinateTransform,
          scopeParameter.viaAtSmdAllowed);
    }
  }

  public static void insertNetClass(
      NetClass netClass,
      LayerStructure layerStructure,
      BasicBoard board,
      CoordinateTransform coordinateTransform,
      boolean viaAtSmdAllowed) {
    app.freerouting.rules.NetClass boardNetClass =
        KiCadNetClassNames.isKiCadDefaultNetClassName(netClass.name)
            ? board.rules.getDefaultNetClass()
            : board.rules.appendNetClass(netClass.name);
    if (netClass.traceClearanceClass != null) {
      int traceClearanceClass = board.rules.clearanceMatrix.getNo(netClass.traceClearanceClass);
      if (traceClearanceClass >= 0) {
        boardNetClass.setTraceClearanceClass(traceClearanceClass);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: clearance class not found at '"
                + boardNetClass.getName()
                + "'");
      }
    }
    if (netClass.viaRule != null) {
      ViaRule viaRule = board.rules.getViaRule(netClass.viaRule);
      if (viaRule != null) {
        boardNetClass.setViaRule(viaRule);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: via rule not found at '" + boardNetClass.getName() + "'");
      }
    }
    if (netClass.maxTraceLength > 0) {
      boardNetClass.setMaximumTraceLength(coordinateTransform.dsnToBoard(netClass.maxTraceLength));
    }
    if (netClass.minTraceLength > 0) {
      boardNetClass.setMinimumTraceLength(coordinateTransform.dsnToBoard(netClass.minTraceLength));
    }
    for (String currentNetName : netClass.netList) {
      Collection<app.freerouting.rules.Net> currentNetList = board.rules.nets.get(currentNetName);
      for (app.freerouting.rules.Net currentNet : currentNetList) {
        currentNet.setClass(boardNetClass);
      }
    }

    // read the trace width and clearance rules.

    boolean clearanceRuleFound = false;

    for (Rule currentRule : netClass.rules) {
      if (currentRule instanceof Rule.WidthRule rule1) {
        int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(rule1.value / 2));
        boardNetClass.setTraceHalfWidth(traceHalfwidth);
      } else if (currentRule instanceof Rule.ClearanceRule rule) {
        addClearanceRule(board.rules.clearanceMatrix, boardNetClass, rule, -1, coordinateTransform);
        clearanceRuleFound = true;
      } else {
        FRLogger.warn(
            "Network.insert_net_class: rule type not yet implemented at '"
                + boardNetClass.getName()
                + "'");
      }
    }

    // read the layer dependent rules.

    for (Rule.LayerRule currentLayerRule : netClass.layerRules) {
      for (String currentLayerName : currentLayerRule.layerNames) {
        int layerIndex = board.layerStructure.getNo(currentLayerName);
        if (layerIndex < 0) {
          FRLogger.warn(
              "Network.insert_net_class: layer not found at '" + boardNetClass.getName() + "'");
          continue;
        }
        for (Rule currentRule : currentLayerRule.rules) {
          if (currentRule instanceof Rule.WidthRule rule1) {
            int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(rule1.value / 2));
            boardNetClass.setTraceHalfWidth(layerIndex, traceHalfwidth);
          } else if (currentRule instanceof Rule.ClearanceRule rule) {
            addClearanceRule(
                board.rules.clearanceMatrix, boardNetClass, rule, layerIndex, coordinateTransform);
            clearanceRuleFound = true;
          } else {
            FRLogger.warn(
                "Network.insert_net_class: layer rule type not yet implemented at '"
                    + boardNetClass.getName()
                    + "'");
          }
        }
      }
    }

    boardNetClass.setPullTight(netClass.pullTight);
    boardNetClass.setShoveFixed(netClass.shoveFixed);
    boolean viaInfosCreated = false;

    if (clearanceRuleFound && boardNetClass != board.rules.getDefaultNetClass()) {
      createDefaultViaInfos(board, boardNetClass, viaAtSmdAllowed);
      viaInfosCreated = true;
    }

    if (!netClass.useVia.isEmpty()) {
      createViaRule(netClass.useVia, boardNetClass, board, viaAtSmdAllowed);
    } else if (viaInfosCreated) {
      board.rules.createDefaultViaRule(boardNetClass, boardNetClass.getName());
    }
    if (!netClass.useLayer.isEmpty()) {
      createActiveTraceLayers(netClass.useLayer, layerStructure, boardNetClass);
    }
  }

  private static void insertClassPairs(
      Collection<NetClass.ClassClass> classClasses, ReadScopeParameter scopeParameter) {
    for (NetClass.ClassClass currentClassClass : classClasses) {
      Iterator<String> it1 = currentClassClass.classNames.iterator();
      BasicBoard routingBoard = scopeParameter.boardHandling.getRoutingBoard();
      while (it1.hasNext()) {
        String firstName = it1.next();
        app.freerouting.rules.NetClass firstClass =
            KiCadNetClassNames.resolveNetClass(routingBoard.rules, firstName);
        if (firstClass == null) {
          FRLogger.warn("Network.insert_class_pairs: first class not found");
        } else {
          Iterator<String> it2 = it1;
          while (it2.hasNext()) {
            String secondName = it2.next();
            app.freerouting.rules.NetClass secondClass =
                KiCadNetClassNames.resolveNetClass(routingBoard.rules, secondName);
            if (secondClass == null) {
              FRLogger.warn("Network.insert_class_pairs: second class not found");
            } else {
              insertClassPairInfo(
                  currentClassClass,
                  firstClass,
                  secondClass,
                  routingBoard,
                  scopeParameter.coordinateTransform);
            }
          }
        }
      }
    }
  }

  private static void insertClassPairInfo(
      NetClass.ClassClass classClass,
      app.freerouting.rules.NetClass firstClass,
      app.freerouting.rules.NetClass secondClass,
      BasicBoard board,
      CoordinateTransform coordinateTransform) {
    for (Rule currentRule : classClass.rules) {
      if (currentRule instanceof Rule.ClearanceRule currentClearanceRule) {
        addMixedClearanceRule(
            board.rules.clearanceMatrix,
            firstClass,
            secondClass,
            currentClearanceRule,
            -1,
            coordinateTransform);
      } else {
        FRLogger.warn("Network.insert_class_pair_info: unexpected rule");
      }
    }
    for (Rule.LayerRule currentLayerRule : classClass.layerRules) {
      for (String currentLayerName : currentLayerRule.layerNames) {
        int layerIndex = board.layerStructure.getNo(currentLayerName);
        if (layerIndex < 0) {
          FRLogger.warn(
              "Network.insert_class_pair_info: layer not found at '" + currentLayerName + "'");
          continue;
        }
        for (Rule currentRule : currentLayerRule.rules) {
          if (currentRule instanceof Rule.ClearanceRule rule) {
            addMixedClearanceRule(
                board.rules.clearanceMatrix,
                firstClass,
                secondClass,
                rule,
                layerIndex,
                coordinateTransform);
          } else {
            FRLogger.warn("Network.insert_class_pair_info: unexpected layer rule type");
          }
        }
      }
    }
  }

  private static void addMixedClearanceRule(
      ClearanceMatrix clearanceMatrix,
      app.freerouting.rules.NetClass firstClass,
      app.freerouting.rules.NetClass secondClass,
      Rule.ClearanceRule clearanceRule,
      int layerIndex,
      CoordinateTransform coordinateTransform) {
    int currentClearance = (int) Math.round(coordinateTransform.dsnToBoard(clearanceRule.value));
    final String firstClassName = firstClass.getName();
    int firstClassNo = clearanceMatrix.getNo(firstClassName);
    if (firstClassNo < 0) {
      clearanceMatrix.appendClass(firstClassName);
      firstClassNo = clearanceMatrix.getNo(firstClassName);
    }
    final String secondClassName = secondClass.getName();
    int secondClassNo = clearanceMatrix.getNo(secondClassName);
    if (secondClassNo < 0) {
      clearanceMatrix.appendClass(secondClassName);
      secondClassNo = clearanceMatrix.getNo(secondClassName);
    }
    if (clearanceRule.clearanceClassPairs.isEmpty()) {
      if (layerIndex < 0) {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, currentClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, currentClearance);
      } else {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, layerIndex, currentClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, layerIndex, currentClearance);
      }
    } else {
      for (String currentString : clearanceRule.clearanceClassPairs) {
        String[] currentPair = currentString.split("_");
        if (currentPair.length != 2) {
          continue;
        }

        int currentFirstClassNo;
        int currentSecondClassNo;
        for (int i = 0; i < 2; i++) {
          if (i == 0) {
            currentFirstClassNo = getClearanceClass(clearanceMatrix, firstClass, currentPair[0]);
            currentSecondClassNo = getClearanceClass(clearanceMatrix, secondClass, currentPair[1]);
          } else {
            currentFirstClassNo = getClearanceClass(clearanceMatrix, secondClass, currentPair[0]);
            currentSecondClassNo = getClearanceClass(clearanceMatrix, firstClass, currentPair[1]);
          }
          if (layerIndex < 0) {
            clearanceMatrix.setValue(currentFirstClassNo, currentSecondClassNo, currentClearance);
            clearanceMatrix.setValue(currentSecondClassNo, currentFirstClassNo, currentClearance);
          } else {
            clearanceMatrix.setValue(
                currentFirstClassNo, currentSecondClassNo, layerIndex, currentClearance);
            clearanceMatrix.setValue(
                currentSecondClassNo, currentFirstClassNo, layerIndex, currentClearance);
          }
        }
      }
    }
  }

  private static void createDefaultClearanceClasses(
      app.freerouting.rules.NetClass netClass, ClearanceMatrix clearanceMatrix) {
    getClearanceClass(clearanceMatrix, netClass, "via");
    getClearanceClass(clearanceMatrix, netClass, "smd");
    getClearanceClass(clearanceMatrix, netClass, "pin");
    getClearanceClass(clearanceMatrix, netClass, "area");
  }

  private static void createViaRule(
      Collection<String> useVia,
      app.freerouting.rules.NetClass netClass,
      BasicBoard board,
      boolean attachAllowed) {
    ViaRule newViaRule = new ViaRule(netClass.getName());
    int defaultViaClClass =
        netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (String currentViaName : useVia) {
      for (int i = 0; i < board.rules.viaInfos.count(); i++) {
        ViaInfo currentViaInfo = board.rules.viaInfos.get(i);
        if (currentViaInfo.getClearanceClass() == defaultViaClClass) {
          if (currentViaInfo.getPadstack().name.equals(currentViaName)) {
            newViaRule.appendVia(currentViaInfo);
          }
        }
      }
    }
    board.rules.viaRules.add(newViaRule);
    netClass.setViaRule(newViaRule);
  }

  private static void createActiveTraceLayers(
      Collection<String> useLayer,
      LayerStructure layerStructure,
      app.freerouting.rules.NetClass netClass) {
    for (int i = 0; i < layerStructure.layers.length; i++) {
      netClass.setActiveRoutingLayer(i, false);
    }
    for (String curLayerName : useLayer) {
      int currentNo = layerStructure.getNo(curLayerName);
      netClass.setActiveRoutingLayer(currentNo, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < layerStructure.layers.length; i++) {
      if (!netClass.isActiveRoutingLayer(i)) {
        netClass.setTraceHalfWidth(i, 0);
      }
    }
  }

  private static void addClearanceRule(
      ClearanceMatrix clearanceMatrix,
      app.freerouting.rules.NetClass netClass,
      Rule.ClearanceRule rule,
      int layerIndex,
      CoordinateTransform coordinateTransform) {
    int currentClearance = (int) Math.round(coordinateTransform.dsnToBoard(rule.value));
    final String className = netClass.getName();
    int classNo = clearanceMatrix.getNo(className);
    if (classNo < 0) {
      // class not yet existing, create a new class
      clearanceMatrix.appendClass(className);
      classNo = clearanceMatrix.getNo(className);
      // set the clearance values of the new class to the maximum of currentClearance and
      // the existing values.
      for (int i = 1; i < clearanceMatrix.getClassCount(); i++) {
        for (int j = 0; j < clearanceMatrix.getLayerCount(); j++) {
          int currentValue =
              Math.max(clearanceMatrix.getValue(classNo, i, j, false), currentClearance);
          clearanceMatrix.setValue(classNo, i, j, currentValue);
          clearanceMatrix.setValue(i, classNo, j, currentValue);
        }
      }
      netClass.defaultItemClearanceClasses.setAll(classNo);
    }
    netClass.setTraceClearanceClass(classNo);
    if (rule.clearanceClassPairs.isEmpty()) {
      if (layerIndex < 0) {
        clearanceMatrix.setValue(classNo, classNo, currentClearance);
      } else {
        clearanceMatrix.setValue(classNo, classNo, layerIndex, currentClearance);
      }
      return;
    }
    if (Structure.containsWireClearancePair(rule.clearanceClassPairs)) {
      createDefaultClearanceClasses(netClass, clearanceMatrix);
    }
    for (String currentString : rule.clearanceClassPairs) {
      String[] currentPair = currentString.split("_");
      if (currentPair.length != 2) {
        continue;
      }

      int firstClassNo = getClearanceClass(clearanceMatrix, netClass, currentPair[0]);
      int secondClassNo = getClearanceClass(clearanceMatrix, netClass, currentPair[1]);

      if (layerIndex < 0) {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, currentClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, currentClearance);
      } else {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, layerIndex, currentClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, layerIndex, currentClearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of netClassName and itemClassName.
   * Creates a new class, if that class is not yet existing.
   */
  private static int getClearanceClass(
      ClearanceMatrix clearanceMatrix,
      app.freerouting.rules.NetClass netClass,
      String itemClassName) {
    String netClassName = netClass.getName();
    String newClassName = netClassName;
    if (!"wire".equals(itemClassName)) {
      newClassName = newClassName + DsnFile.CLASS_CLEARANCE_SEPARATOR + itemClassName;
    }
    int foundClassNo = clearanceMatrix.getNo(newClassName);
    if (foundClassNo >= 0) {
      return foundClassNo;
    }
    clearanceMatrix.appendClass(newClassName);
    int result = clearanceMatrix.getNo(newClassName);
    int netClassNo = clearanceMatrix.getNo(netClassName);
    if (netClassNo < 0 || result < 0) {
      FRLogger.warn(
          "Network.get_clearance_class: clearance class not found at '" + netClassName + "'");
      return result;
    }
    // initialize the clearance values of newClassName from netClassName
    for (int i = 1; i < clearanceMatrix.getClassCount(); i++) {

      for (int j = 0; j < clearanceMatrix.getLayerCount(); j++) {
        int currentValue = clearanceMatrix.getValue(netClassNo, i, j, false);
        clearanceMatrix.setValue(result, i, j, currentValue);
        clearanceMatrix.setValue(i, result, j, currentValue);
      }
    }
    switch (itemClassName) {
      case "via" -> netClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> netClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> netClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> netClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
      default -> {
        // Ignore unsupported item classes.
      }
    }
    return result;
  }

  private static void insertComponents(ReadScopeParameter scopeParameter) {
    for (ComponentPlacement nextLibComponent : scopeParameter.placementList) {
      for (ComponentPlacement.ComponentLocation nextComponent : nextLibComponent.locations) {
        insertComponent(nextComponent, nextLibComponent.libName, scopeParameter);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insertLogicalParts(ReadScopeParameter scopeParameter) {
    BasicBoard routingBoard = scopeParameter.boardHandling.getRoutingBoard();
    for (PartLibrary.LogicalPart nextPart : scopeParameter.logicalParts) {
      Package libPackage =
          searchLibPackage(nextPart.name, scopeParameter.logicalPartMappings, routingBoard);
      if (libPackage == null) {
        return false;
      }
      LogicalPart.PartPin[] boardPartPins = new LogicalPart.PartPin[nextPart.partPins.size()];
      int currentIndex = 0;
      for (PartLibrary.PartPin currentPartPin : nextPart.partPins) {
        int pinNo = libPackage.getPinNo(currentPartPin.pinName);
        if (pinNo < 0) {
          FRLogger.warn(
              "Network.insert_logical_parts: package pin not found at '"
                  + currentPartPin.pinName
                  + "'");
          return false;
        }
        boardPartPins[currentIndex] =
            new LogicalPart.PartPin(
                pinNo,
                currentPartPin.pinName,
                currentPartPin.gateName,
                currentPartPin.gateSwapCode,
                currentPartPin.gatePinName,
                currentPartPin.gatePinSwapCode);
        ++currentIndex;
      }
      routingBoard.library.logicalParts.add(nextPart.name, boardPartPins);
    }

    for (PartLibrary.LogicalPartMapping nextMapping : scopeParameter.logicalPartMappings) {
      LogicalPart currentLogicalPart = routingBoard.library.logicalParts.get(nextMapping.name);
      {
        if (currentLogicalPart == null) {
          FRLogger.warn(
              "Network.insert_logical_parts: logical part not found at '" + nextMapping.name + "'");
        }
      }
      for (String currentCmpName : nextMapping.components) {
        app.freerouting.board.Component currentComponent =
            routingBoard.components.get(currentCmpName);
        if (currentComponent != null) {
          currentComponent.setLogicalPart(currentLogicalPart);
        } else {
          FRLogger.warn(
              "Network.insert_logical_parts: board component not found at '"
                  + currentCmpName
                  + "'");
        }
      }
    }
    return true;
  }

  /**
   * Calculates the library package belonging to the logical part with name partName. Returns null,
   * if the package was not found.
   */
  private static Package searchLibPackage(
      String partName,
      Collection<PartLibrary.LogicalPartMapping> logicalPartMappings,
      BasicBoard board) {
    for (PartLibrary.LogicalPartMapping currentMapping : logicalPartMappings) {
      if (currentMapping.name.equals(partName)) {
        if (currentMapping.components.isEmpty()) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + partName + "'");
          return null;
        }
        String componentName = currentMapping.components.getFirst();
        if (componentName == null) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + partName + "'");
          return null;
        }
        app.freerouting.board.Component currentComponent = board.components.get(componentName);
        if (currentComponent == null) {
          FRLogger.warn(
              "Network.search_lib_package: component not found at '" + componentName + "'");
          return null;
        }
        return currentComponent.getPackage();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + partName + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insertComponent(
      ComponentPlacement.ComponentLocation location,
      String libKey,
      ReadScopeParameter scopeParameter) {
    RoutingBoard routingBoard = scopeParameter.boardHandling.getRoutingBoard();
    Package currentFrontPackage = routingBoard.library.packages.get(libKey, true);
    Package currentBackPackage = routingBoard.library.packages.get(libKey, false);
    if (currentFrontPackage == null || currentBackPackage == null) {
      FRLogger.warn(
          "Network.insert_component: component package not found at '"
              + scopeParameter.scanner.getScopeIdentifier()
              + "'");
      return;
    }

    IntPoint componentLocation;
    if (location.coor != null) {
      componentLocation = scopeParameter.coordinateTransform.dsnToBoard(location.coor).round();
    } else {
      componentLocation = null;
    }
    double rotationInDegree = location.rotation;

    app.freerouting.board.Component newComponent =
        routingBoard.components.add(
            location.name,
            componentLocation,
            rotationInDegree,
            location.isFront,
            currentFrontPackage,
            currentBackPackage,
            location.positionFixed,
            location.partNumber);

    if (componentLocation == null) {
      return; // component is not yet placed.
    }
    Vector componentTranslation = componentLocation.differenceBy(Point.ZERO);
    FixedState fixedState;
    if (location.positionFixed) {
      fixedState = FixedState.SYSTEM_FIXED;
    } else {
      fixedState = FixedState.UNFIXED;
    }
    Package currentPackage = newComponent.getPackage();
    for (int i = 0; i < currentPackage.pinCount(); i++) {
      Package.Pin currentPin = currentPackage.getPin(i);
      Padstack currentPadstack = routingBoard.library.padstacks.get(currentPin.padstackNo);
      if (currentPadstack == null) {
        FRLogger.warn(
            "Network.insert_component: pin padstack not found at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      Collection<Net> pinNets = scopeParameter.netlist.getNets(location.name, currentPin.name);
      Collection<Integer> pinNetNumbers = new LinkedList<>();
      for (Net currentPinNet : pinNets) {
        app.freerouting.rules.Net currentBoardNet =
            routingBoard.rules.nets.get(currentPinNet.id.name, currentPinNet.id.subnetNumber);
        if (currentBoardNet == null) {
          FRLogger.warn(
              "Network.insert_component: board net not found at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
        } else {
          pinNetNumbers.add(currentBoardNet.netNumber);
        }
      }
      int[] netNumberArray = new int[pinNetNumbers.size()];
      int netIndex = 0;
      for (Integer currentNetNumber : pinNetNumbers) {
        netNumberArray[netIndex] = currentNetNumber;
        ++netIndex;
      }
      app.freerouting.rules.NetClass netClass;
      app.freerouting.rules.Net boardNet;
      if (netNumberArray.length > 0) {
        boardNet = routingBoard.rules.nets.get(netNumberArray[0]);
      } else {
        boardNet = null;
      }
      if (boardNet != null) {
        netClass = boardNet.getNetClass();
      } else {
        netClass = routingBoard.rules.getDefaultNetClass();
      }
      int clearanceClass = -1;
      ComponentPlacement.ItemClearanceInfo pinInfo = location.pin_infos.get(currentPin.name);
      if (pinInfo != null) {
        clearanceClass = routingBoard.rules.clearanceMatrix.getNo(pinInfo.clearanceClass);
      }
      if (clearanceClass < 0) {
        if (currentPadstack.fromLayer() == currentPadstack.toLayer()) {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.SMD);
        } else {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.PIN);
        }
      }
      routingBoard.insertPin(newComponent.no, i, netNumberArray, clearanceClass, fixedState);
    }

    // insert the keepouts belonging to the package (k = 1 for via keepouts)
    for (int k = 0; k <= 2; k++) {
      Package.Keepout[] keepouts;
      Map<String, ComponentPlacement.ItemClearanceInfo> currentKeepoutInfos;
      if (k == 0) {
        keepouts = currentPackage.keepouts;
        currentKeepoutInfos = location.keepout_infos;
      } else if (k == 1) {
        keepouts = currentPackage.viaKeepouts;
        currentKeepoutInfos = location.via_keepout_infos;
      } else {
        keepouts = currentPackage.placeKeepoutArr;
        currentKeepoutInfos = location.place_keepout_infos;
      }
      for (int i = 0; i < keepouts.length; i++) {
        Package.Keepout currentKeepout = keepouts[i];
        int layer = currentKeepout.layer;
        if (layer >= routingBoard.getLayerCount()) {
          FRLogger.warn(
              "Network.insert_component: keepout layer is to big at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
          continue;
        }
        if (layer >= 0 && !location.isFront) {
          layer = routingBoard.getLayerCount() - currentKeepout.layer - 1;
        }
        int clearanceClass =
            routingBoard
                .rules
                .getDefaultNetClass()
                .defaultItemClearanceClasses
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
        ComponentPlacement.ItemClearanceInfo keepoutInfo =
            currentKeepoutInfos.get(currentKeepout.name);
        if (keepoutInfo != null) {
          int currentClearanceClass =
              routingBoard.rules.clearanceMatrix.getNo(keepoutInfo.clearanceClass);
          if (currentClearanceClass > 0) {
            clearanceClass = currentClearanceClass;
          }
        }
        if (layer >= 0) {
          if (k == 0) {
            routingBoard.insertObstacle(
                currentKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currentKeepout.name,
                fixedState);
          } else if (k == 1) {
            routingBoard.insertViaObstacle(
                currentKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currentKeepout.name,
                fixedState);
          } else {
            routingBoard.insertComponentObstacle(
                currentKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currentKeepout.name,
                fixedState);
          }
        } else {
          // insert the obstacle on all signal layers
          for (int j = 0; j < routingBoard.layerStructure.layers.length; j++) {
            if (routingBoard.layerStructure.layers[j].isSignal) {
              if (k == 0) {
                routingBoard.insertObstacle(
                    currentKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currentKeepout.name,
                    fixedState);
              } else if (k == 1) {
                routingBoard.insertViaObstacle(
                    currentKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currentKeepout.name,
                    fixedState);
              } else {
                routingBoard.insertComponentObstacle(
                    currentKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currentKeepout.name,
                    fixedState);
              }
            }
          }
        }
      }
    }
    // insert the outline as component keepout
    int courtyardIdx = -1;
    if (currentPackage.outline != null && currentPackage.outline.length > 1) {
      double maxArea = -1;
      for (int i = 0; i < currentPackage.outline.length; i++) {
        if (currentPackage.outline[i] != null && currentPackage.outline[i].boundingBox() != null) {
          double area = currentPackage.outline[i].boundingBox().area();
          if (area > maxArea) {
            maxArea = area;
            courtyardIdx = i;
          }
        }
      }
    }
    if (currentPackage.outline != null) {
      for (int i = 0; i < currentPackage.outline.length; i++) {
        boolean isCourtyard = i == courtyardIdx;
        if (currentPackage.outlineWidths != null && i < currentPackage.outlineWidths.length) {
          if (currentPackage.outlineWidths[i] == 0.0) {
            isCourtyard = true;
          }
        }
        boolean isFabrication = false;
        if (!isCourtyard
            && currentPackage.outlineWidths != null
            && i < currentPackage.outlineWidths.length) {
          if (currentPackage.outlineWidths[i] <= 110.0) {
            isFabrication = true;
          }
        }
        boolean isClosed = false;
        if (currentPackage.outlineIsClosed != null && i < currentPackage.outlineIsClosed.length) {
          isClosed = currentPackage.outlineIsClosed[i];
        }
        routingBoard.insertComponentOutline(
            currentPackage.outline[i],
            location.isFront,
            componentTranslation,
            rotationInDegree,
            newComponent.no,
            isCourtyard,
            isFabrication,
            isClosed,
            fixedState);
      }
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> classClassList = new LinkedList<>();
    Collection<ViaInfo> viaInfos = new LinkedList<>();
    Collection<Collection<String>> viaRules = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Network.read_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.NET) {
          readNetScope(
              scopeParameter.scanner,
              scopeParameter.netlist,
              scopeParameter.boardHandling.getRoutingBoard(),
              scopeParameter.coordinateTransform,
              scopeParameter.layerStructure);
        } else if (nextToken == Keyword.VIA) {
          ViaInfo currentViaInfo =
              readViaInfo(scopeParameter.scanner, scopeParameter.boardHandling.getRoutingBoard());
          if (currentViaInfo == null) {
            return false;
          }
          viaInfos.add(currentViaInfo);
        } else if (nextToken == Keyword.VIA_RULE) {
          Collection<String> currentViaRule =
              readViaRule(scopeParameter.scanner, scopeParameter.boardHandling.getRoutingBoard());
          if (currentViaRule == null) {
            return false;
          }
          viaRules.add(currentViaRule);
        } else if (nextToken == Keyword.CLASS) {
          NetClass currentClass = NetClass.readScope(scopeParameter.scanner);
          if (currentClass == null) {
            return false;
          }
          classes.add(currentClass);
        } else if (nextToken == Keyword.CLASS_CLASS) {
          NetClass.ClassClass currentClassClass =
              NetClass.readClassClassScope(scopeParameter.scanner);
          if (currentClassClass == null) {
            return false;
          }
          classClassList.add(currentClassClass);
        } else {
          skipScope(scopeParameter.scanner);
        }
      }
    }

    // Add any vias defined in the Netclasses to the list of vias to be instantiated
    for (NetClass n : classes) {
      if (scopeParameter.viaPadstackNames != null) {
        scopeParameter.viaPadstackNames.addAll(n.useVia);
      } else {
        scopeParameter.viaPadstackNames = n.useVia;
      }
    }

    RoutingBoard board = scopeParameter.boardHandling.getRoutingBoard();

    // Set the via padstacks after network parsing, so that named vias from both structure and
    // network DSN sections are properly instantiated .
    if (scopeParameter.viaPadstackNames != null) {
      Padstack[] viaPadstacks = new Padstack[scopeParameter.viaPadstackNames.size()];
      Iterator<String> it = scopeParameter.viaPadstackNames.iterator();
      int foundPadstackCount = 0;
      for (int i = 0; i < viaPadstacks.length; i++) {
        String currentPadstackName = it.next();
        String cleanedName =
            currentPadstackName != null ? currentPadstackName.replaceAll("\\.\\d+", "") : null;
        Padstack currentPadstack = board.library.padstacks.get(cleanedName);
        if (currentPadstack != null) {
          viaPadstacks[foundPadstackCount] = currentPadstack;
          ++foundPadstackCount;
        } else {
          FRLogger.warn(
              "Library.read_scope: via padstack with name '"
                  + currentPadstackName
                  + " not found at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
        }
      }
      if (foundPadstackCount != viaPadstacks.length) {
        // Some via padstacks were not found in the padstacks scope of the dsn-file.
        Padstack[] correctedPadstacks = new Padstack[foundPadstackCount];
        System.arraycopy(viaPadstacks, 0, correctedPadstacks, 0, foundPadstackCount);
        viaPadstacks = correctedPadstacks;
      }
      board.library.setViaPadstacks(viaPadstacks);
    }

    insertViaInfos(
        viaInfos, scopeParameter.boardHandling.getRoutingBoard(), scopeParameter.viaAtSmdAllowed);
    insertViaRules(viaRules, scopeParameter.boardHandling.getRoutingBoard());
    insertNetClasses(classes, scopeParameter);
    insertClassPairs(classClassList, scopeParameter);
    insertComponents(scopeParameter);
    insertLogicalParts(scopeParameter);
    return true;
  }

  private boolean readNetScope(
      IJFlexScanner scanner,
      NetList netList,
      RoutingBoard board,
      CoordinateTransform coordinateTransform,
      LayerStructure layerStructure) {
    // read the net name
    final String netName = scanner.nextString();

    Object nextToken;
    int subnetNumber = 1;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_scope: IO error while scanning file", e);
      return false;
    }
    boolean scopeIsEmpty = nextToken == CLOSED_BRACKET;
    if (nextToken instanceof Integer integer) {
      subnetNumber = integer;
    }
    boolean pinOrderFound = false;
    Collection<Net.Pin> pinList = new LinkedList<>();
    Collection<Rule> netRules = new LinkedList<>();
    Collection<Collection<Net.Pin>> subnetPinLists = new LinkedList<>();
    if (!scopeIsEmpty) {
      for (; ; ) {
        Object prevToken = nextToken;
        try {
          nextToken = scanner.nextToken();
        } catch (IOException e) {
          FRLogger.error("Network.read_net_scope: IO error scanning file", e);
          return false;
        }
        if (nextToken == null) {
          FRLogger.warn(
              "Network.read_net_scope: unexpected end of file at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == Keyword.PINS) {
            if (!readNetPins(scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.ORDER) {
            pinOrderFound = true;
            if (!readNetPins(scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.FROMTO) {
            Set<Net.Pin> currentSubnetPinList = new TreeSet<>();
            if (!readNetPins(scanner, currentSubnetPinList)) {
              return false;
            }
            subnetPinLists.add(currentSubnetPinList);
          } else if (nextToken == Keyword.RULE) {
            netRules.addAll(Rule.readScope(scanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            FRLogger.warn(
                "Network.read_net_scope: layer_rule not yet implemented at '"
                    + scanner.getScopeIdentifier()
                    + "'");
            skipScope(scanner);
          } else {
            skipScope(scanner);
          }
        }
      }
    }
    if (subnetPinLists.isEmpty()) {
      if (pinOrderFound) {
        subnetPinLists = createOrderedSubnets(pinList);
      } else {
        subnetPinLists.add(pinList);
      }
    }
    for (Collection<Net.Pin> currentPinList : subnetPinLists) {
      Net.Id netId = new Net.Id(netName, subnetNumber);
      if (!netList.contains(netId)) {
        Net newNet = netList.addNet(netId);
        boolean containsPlane = layerStructure.containsPlane(netName);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, containsPlane);
        }
      }
      Net currentSubnet = netList.getNet(netId);
      if (currentSubnet == null) {
        FRLogger.warn(
            "Network.read_net_scope: net not found in netlist at '"
                + scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      currentSubnet.setPins(currentPinList);
      if (!netRules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net boardNet =
            board.rules.nets.get(currentSubnet.id.name, currentSubnet.id.subnetNumber);
        if (boardNet == null) {
          FRLogger.warn(
              "Network.read_net_scope: board net not found at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        for (Rule currentObject : netRules) {
          if (currentObject instanceof Rule.WidthRule rule) {
            app.freerouting.rules.NetClass defaultNetRule = board.rules.getDefaultNetClass();
            final double wireWidth = rule.value;
            int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(wireWidth) / 2);
            app.freerouting.rules.NetClass netRule =
                board.rules.netClasses.find(
                    traceHalfwidth,
                    defaultNetRule.getTraceClearanceClass(),
                    defaultNetRule.getViaRule());
            if (netRule == null) {
              // create a new net rule
              netRule = board.rules.getNewNetClass();
            }
            netRule.setTraceHalfWidth(traceHalfwidth);
            boardNet.setClass(netRule);
          } else {
            FRLogger.warn(
                "Network.read_net_scope: Rule not yet implemented at '"
                    + scanner.getScopeIdentifier()
                    + "'");
          }
        }
      }
      ++subnetNumber;
    }
    return true;
  }
}
