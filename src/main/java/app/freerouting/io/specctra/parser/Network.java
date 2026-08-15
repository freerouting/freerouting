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

  public static void writeScope(WriteScopeParameter par) throws IOException {
    par.file.startScope();
    par.file.write("network");
    Collection<Pin> boardPins = par.board.getPins();
    for (int i = 1; i <= par.board.rules.nets.maxNetNo(); i++) {
      Net.writeScope(par, par.board.rules.nets.get(i), boardPins);
    }
    writeViaInfos(par.board.rules, par.file, par.identifierType);
    writeViaRules(par.board.rules, par.file, par.identifierType);
    writeNetClasses(par);
    par.file.endScope();
  }

  public static void writeViaInfos(
      BoardRules rules, IndentFileWriter file, IdentifierType identifierType) throws IOException {
    for (int i = 0; i < rules.viaInfos.count(); i++) {
      final ViaInfo currVia = rules.viaInfos.get(i);
      file.startScope();
      file.write("via ");
      file.newLine();
      identifierType.write(currVia.getName(), file);
      file.write(" ");
      identifierType.write(currVia.getPadstack().name, file);
      file.write(" ");
      identifierType.write(rules.clearanceMatrix.getName(currVia.getClearanceClass()), file);
      if (currVia.attachSmdAllowed()) {
        file.write(" attach");
      }
      file.endScope();
    }
  }

  public static void writeViaRules(
      BoardRules rules, IndentFileWriter file, IdentifierType identifierType) throws IOException {
    for (ViaRule currRule : rules.viaRules) {
      file.startScope();
      file.write("viaRule");
      file.newLine();
      identifierType.write(currRule.name, file);
      for (int i = 0; i < currRule.viaCount(); i++) {
        file.write(" ");
        identifierType.write(currRule.getVia(i).getName(), file);
      }
      file.endScope();
    }
  }

  public static void writeNetClasses(WriteScopeParameter par) throws IOException {
    for (int i = 0; i < par.board.rules.netClasses.count(); i++) {
      writeNetClass(par.board.rules.netClasses.get(i), par);
    }
  }

  public static void writeNetClass(app.freerouting.rules.NetClass netClass, WriteScopeParameter par)
      throws IOException {
    par.file.startScope();
    par.file.write("class ");
    par.identifierType.write(netClass.getName(), par.file);
    final int netsPerRow = 8;
    int netCounter = 0;
    for (int i = 1; i <= par.board.rules.nets.maxNetNo(); i++) {
      if (par.board.rules.nets.get(i).getNetClass() == netClass) {
        if (netCounter % netsPerRow == 0) {
          par.file.newLine();
        } else {
          par.file.write(" ");
        }
        par.identifierType.write(par.board.rules.nets.get(i).name, par.file);
        ++netCounter;
      }
    }

    // write the trace clearance class
    Rule.writeItemClearanceClass(
        par.board.rules.clearanceMatrix.getName(netClass.getTraceClearanceClass()),
        par.file,
        par.identifierType);

    if (netClass.getViaRule() != null) {
      // write the via rule
      par.file.newLine();
      par.file.write("(viaRule ");
      par.identifierType.write(netClass.getViaRule().name, par.file);
      par.file.write(")");
    }

    // write the rules, if they are different from the default rule.
    Rule.writeScope(netClass, par);

    writeCircuit(netClass, par);

    if (!netClass.getPullTight()) {
      par.file.newLine();
      par.file.write("(pullTight off)");
    }

    if (netClass.isShoveFixed()) {
      par.file.newLine();
      par.file.write("(shoveFixed on)");
    }

    par.file.endScope();
  }

  private static void writeCircuit(app.freerouting.rules.NetClass netClass, WriteScopeParameter par)
      throws IOException {
    final double minTraceLength = netClass.getMinimumTraceLength();
    final double maxTraceLength = netClass.getMaximumTraceLength();
    par.file.startScope();
    par.file.write("circuit ");
    par.file.newLine();
    par.file.write("(useLayer");
    int layerCount = netClass.layerCount();
    for (int i = 0; i < layerCount; i++) {
      if (netClass.isActiveRoutingLayer(i)) {
        par.file.write(" ");
        par.file.write(par.board.layerStructure.arr[i].name);
      }
    }
    par.file.write(")");
    if (minTraceLength > 0 || maxTraceLength > 0) {
      par.file.newLine();
      par.file.write("(length ");
      double transformedMaxLength;
      if (maxTraceLength <= 0) {
        transformedMaxLength = -1;
      } else {
        transformedMaxLength = par.coordinateTransform.boardToDsn(maxTraceLength);
      }
      par.file.write(String.valueOf(transformedMaxLength));
      par.file.write(" ");
      double transformedMinLength;
      if (minTraceLength <= 0) {
        transformedMinLength = 0;
      } else {
        transformedMinLength = par.coordinateTransform.boardToDsn(minTraceLength);
      }
      par.file.write(String.valueOf(transformedMinLength));
      par.file.write(")");
    }
    par.file.endScope();
  }

  /** Creates a sequence of subnets with 2 pins from p_pin_list. */
  private static Collection<Collection<Net.Pin>> createOrderedSubnets(Collection<Net.Pin> pinList) {
    Collection<Collection<Net.Pin>> result = new LinkedList<>();
    if (pinList.isEmpty()) {
      return result;
    }

    Iterator<Net.Pin> it = pinList.iterator();
    Net.Pin prevPin = it.next();
    while (it.hasNext()) {
      Net.Pin nextPin = it.next();
      Set<Net.Pin> currSubnetPinList = new TreeSet<>();
      currSubnetPinList.add(prevPin);
      currSubnetPinList.add(nextPin);
      result.add(currSubnetPinList);
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
      for (ViaInfo currInfo : viaInfos) {
        board.rules.viaInfos.add(currInfo);
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
      Padstack currPadstack = board.library.getViaPadstack(i);
      boolean viaAttachAllowed = attachAllowed && currPadstack.attachAllowed;
      String viaName;
      if (isDefaultClass) {
        viaName = currPadstack.name;
      } else {
        viaName = currPadstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + netClass.getName();
      }
      ViaInfo foundViaInfo =
          new ViaInfo(viaName, currPadstack, clClass, viaAttachAllowed, board.rules);
      board.rules.viaInfos.add(foundViaInfo);
    }
  }

  private static void insertViaRules(Collection<Collection<String>> viaRules, BasicBoard board) {
    boolean ruleFound = false;
    for (Collection<String> currList : viaRules) {
      if (currList.size() < 2) {
        continue;
      }
      if (addViaRule(currList, board)) {
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
    ViaRule currRule = new ViaRule(ruleName);
    boolean ruleOk = true;
    while (it.hasNext()) {
      final ViaInfo currVia = board.rules.viaInfos.get(it.next());
      if (currVia != null) {
        currRule.appendVia(currVia);
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
      board.rules.viaRules.add(currRule);
    }
    return ruleOk;
  }

  private static void insertNetClasses(Collection<NetClass> netClasses, ReadScopeParameter par) {
    BasicBoard routingBoard = par.boardHandling.getRoutingBoard();
    for (NetClass currClass : netClasses) {
      insertNetClass(
          currClass,
          par.layerStructure,
          routingBoard,
          par.coordinateTransform,
          par.viaAtSmdAllowed);
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
    for (String currNetName : netClass.netList) {
      Collection<app.freerouting.rules.Net> currNetList = board.rules.nets.get(currNetName);
      for (app.freerouting.rules.Net currentNet : currNetList) {
        currentNet.setClass(boardNetClass);
      }
    }

    // read the trace width and clearance rules.

    boolean clearanceRuleFound = false;

    for (Rule currRule : netClass.rules) {
      if (currRule instanceof Rule.WidthRule rule1) {
        int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(rule1.value / 2));
        boardNetClass.setTraceHalfWidth(traceHalfwidth);
      } else if (currRule instanceof Rule.ClearanceRule rule) {
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

    for (Rule.LayerRule currLayerRule : netClass.layerRules) {
      for (String currLayerName : currLayerRule.layerNames) {
        int layerNo = board.layerStructure.getNo(currLayerName);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_net_class: layer not found at '" + boardNetClass.getName() + "'");
          continue;
        }
        for (Rule currRule : currLayerRule.rules) {
          if (currRule instanceof Rule.WidthRule rule1) {
            int traceHalfwidth = (int) Math.round(coordinateTransform.dsnToBoard(rule1.value / 2));
            boardNetClass.setTraceHalfWidth(layerNo, traceHalfwidth);
          } else if (currRule instanceof Rule.ClearanceRule rule) {
            addClearanceRule(
                board.rules.clearanceMatrix, boardNetClass, rule, layerNo, coordinateTransform);
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
      Collection<NetClass.ClassClass> classClasses, ReadScopeParameter par) {
    for (NetClass.ClassClass currClassClass : classClasses) {
      Iterator<String> it1 = currClassClass.classNames.iterator();
      BasicBoard routingBoard = par.boardHandling.getRoutingBoard();
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
                  currClassClass, firstClass, secondClass, routingBoard, par.coordinateTransform);
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
    for (Rule currRule : classClass.rules) {
      if (currRule instanceof Rule.ClearanceRule currClearanceRule) {
        addMixedClearanceRule(
            board.rules.clearanceMatrix,
            firstClass,
            secondClass,
            currClearanceRule,
            -1,
            coordinateTransform);
      } else {
        FRLogger.warn("Network.insert_class_pair_info: unexpected rule");
      }
    }
    for (Rule.LayerRule currLayerRule : classClass.layerRules) {
      for (String currLayerName : currLayerRule.layerNames) {
        int layerNo = board.layerStructure.getNo(currLayerName);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_class_pair_info: layer not found at '" + currLayerName + "'");
          continue;
        }
        for (Rule currRule : currLayerRule.rules) {
          if (currRule instanceof Rule.ClearanceRule rule) {
            addMixedClearanceRule(
                board.rules.clearanceMatrix,
                firstClass,
                secondClass,
                rule,
                layerNo,
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
      int layerNo,
      CoordinateTransform coordinateTransform) {
    int currClearance = (int) Math.round(coordinateTransform.dsnToBoard(clearanceRule.value));
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
      if (layerNo < 0) {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, layerNo, currClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, layerNo, currClearance);
      }
    } else {
      for (String currString : clearanceRule.clearanceClassPairs) {
        String[] currPair = currString.split("_");
        if (currPair.length != 2) {
          continue;
        }

        int currFirstClassNo;
        int currSecondClassNo;
        for (int i = 0; i < 2; i++) {
          if (i == 0) {
            currFirstClassNo = getClearanceClass(clearanceMatrix, firstClass, currPair[0]);
            currSecondClassNo = getClearanceClass(clearanceMatrix, secondClass, currPair[1]);
          } else {
            currFirstClassNo = getClearanceClass(clearanceMatrix, secondClass, currPair[0]);
            currSecondClassNo = getClearanceClass(clearanceMatrix, firstClass, currPair[1]);
          }
          if (layerNo < 0) {
            clearanceMatrix.setValue(currFirstClassNo, currSecondClassNo, currClearance);
            clearanceMatrix.setValue(currSecondClassNo, currFirstClassNo, currClearance);
          } else {
            clearanceMatrix.setValue(currFirstClassNo, currSecondClassNo, layerNo, currClearance);
            clearanceMatrix.setValue(currSecondClassNo, currFirstClassNo, layerNo, currClearance);
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
    for (String currViaName : useVia) {
      for (int i = 0; i < board.rules.viaInfos.count(); i++) {
        ViaInfo currViaInfo = board.rules.viaInfos.get(i);
        if (currViaInfo.getClearanceClass() == defaultViaClClass) {
          if (currViaInfo.getPadstack().name.equals(currViaName)) {
            newViaRule.appendVia(currViaInfo);
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
    for (int i = 0; i < layerStructure.arr.length; i++) {
      netClass.setActiveRoutingLayer(i, false);
    }
    for (String curLayerName : useLayer) {
      int currNo = layerStructure.getNo(curLayerName);
      netClass.setActiveRoutingLayer(currNo, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < layerStructure.arr.length; i++) {
      if (!netClass.isActiveRoutingLayer(i)) {
        netClass.setTraceHalfWidth(i, 0);
      }
    }
  }

  private static void addClearanceRule(
      ClearanceMatrix clearanceMatrix,
      app.freerouting.rules.NetClass netClass,
      Rule.ClearanceRule rule,
      int layerNo,
      CoordinateTransform coordinateTransform) {
    int currClearance = (int) Math.round(coordinateTransform.dsnToBoard(rule.value));
    final String className = netClass.getName();
    int classNo = clearanceMatrix.getNo(className);
    if (classNo < 0) {
      // class not yet existing, create a new class
      clearanceMatrix.appendClass(className);
      classNo = clearanceMatrix.getNo(className);
      // set the clearance values of the new class to the maximum of currClearance and
      // the existing values.
      for (int i = 1; i < clearanceMatrix.getClassCount(); i++) {
        for (int j = 0; j < clearanceMatrix.getLayerCount(); j++) {
          int currValue = Math.max(clearanceMatrix.getValue(classNo, i, j, false), currClearance);
          clearanceMatrix.setValue(classNo, i, j, currValue);
          clearanceMatrix.setValue(i, classNo, j, currValue);
        }
      }
      netClass.defaultItemClearanceClasses.setAll(classNo);
    }
    netClass.setTraceClearanceClass(classNo);
    if (rule.clearanceClassPairs.isEmpty()) {
      if (layerNo < 0) {
        clearanceMatrix.setValue(classNo, classNo, currClearance);
      } else {
        clearanceMatrix.setValue(classNo, classNo, layerNo, currClearance);
      }
      return;
    }
    if (Structure.containsWireClearancePair(rule.clearanceClassPairs)) {
      createDefaultClearanceClasses(netClass, clearanceMatrix);
    }
    for (String currString : rule.clearanceClassPairs) {
      String[] currPair = currString.split("_");
      if (currPair.length != 2) {
        continue;
      }

      int firstClassNo = getClearanceClass(clearanceMatrix, netClass, currPair[0]);
      int secondClassNo = getClearanceClass(clearanceMatrix, netClass, currPair[1]);

      if (layerNo < 0) {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        clearanceMatrix.setValue(firstClassNo, secondClassNo, layerNo, currClearance);
        clearanceMatrix.setValue(secondClassNo, firstClassNo, layerNo, currClearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of p_net_class_name and
   * p_item_class_name. Creates a new class, if that class is not yet existing.
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
    // initialize the clearance values of p_new_class_name from p_net_class_name
    for (int i = 1; i < clearanceMatrix.getClassCount(); i++) {

      for (int j = 0; j < clearanceMatrix.getLayerCount(); j++) {
        int currValue = clearanceMatrix.getValue(netClassNo, i, j, false);
        clearanceMatrix.setValue(result, i, j, currValue);
        clearanceMatrix.setValue(i, result, j, currValue);
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

  private static void insertComponents(ReadScopeParameter par) {
    for (ComponentPlacement nextLibComponent : par.placementList) {
      for (ComponentPlacement.ComponentLocation nextComponent : nextLibComponent.locations) {
        insertComponent(nextComponent, nextLibComponent.libName, par);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insertLogicalParts(ReadScopeParameter par) {
    BasicBoard routingBoard = par.boardHandling.getRoutingBoard();
    for (PartLibrary.LogicalPart nextPart : par.logicalParts) {
      Package libPackage = searchLibPackage(nextPart.name, par.logicalPartMappings, routingBoard);
      if (libPackage == null) {
        return false;
      }
      LogicalPart.PartPin[] boardPartPins = new LogicalPart.PartPin[nextPart.partPins.size()];
      int currIndex = 0;
      for (PartLibrary.PartPin currPartPin : nextPart.partPins) {
        int pinNo = libPackage.getPinNo(currPartPin.pinName);
        if (pinNo < 0) {
          FRLogger.warn(
              "Network.insert_logical_parts: package pin not found at '"
                  + currPartPin.pinName
                  + "'");
          return false;
        }
        boardPartPins[currIndex] =
            new LogicalPart.PartPin(
                pinNo,
                currPartPin.pinName,
                currPartPin.gateName,
                currPartPin.gateSwapCode,
                currPartPin.gatePinName,
                currPartPin.gatePinSwapCode);
        ++currIndex;
      }
      routingBoard.library.logicalParts.add(nextPart.name, boardPartPins);
    }

    for (PartLibrary.LogicalPartMapping nextMapping : par.logicalPartMappings) {
      LogicalPart currLogicalPart = routingBoard.library.logicalParts.get(nextMapping.name);
      {
        if (currLogicalPart == null) {
          FRLogger.warn(
              "Network.insert_logical_parts: logical part not found at '" + nextMapping.name + "'");
        }
      }
      for (String currCmpName : nextMapping.components) {
        app.freerouting.board.Component currComponent = routingBoard.components.get(currCmpName);
        if (currComponent != null) {
          currComponent.setLogicalPart(currLogicalPart);
        } else {
          FRLogger.warn(
              "Network.insert_logical_parts: board component not found at '" + currCmpName + "'");
        }
      }
    }
    return true;
  }

  /**
   * Calculates the library package belonging to the logical part with name p_part_name. Returns
   * null, if the package was not found.
   */
  private static Package searchLibPackage(
      String partName,
      Collection<PartLibrary.LogicalPartMapping> logicalPartMappings,
      BasicBoard board) {
    for (PartLibrary.LogicalPartMapping currMapping : logicalPartMappings) {
      if (currMapping.name.equals(partName)) {
        if (currMapping.components.isEmpty()) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + partName + "'");
          return null;
        }
        String componentName = currMapping.components.getFirst();
        if (componentName == null) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + partName + "'");
          return null;
        }
        app.freerouting.board.Component currComponent = board.components.get(componentName);
        if (currComponent == null) {
          FRLogger.warn(
              "Network.search_lib_package: component not found at '" + componentName + "'");
          return null;
        }
        return currComponent.getPackage();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + partName + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insertComponent(
      ComponentPlacement.ComponentLocation location, String libKey, ReadScopeParameter par) {
    RoutingBoard routingBoard = par.boardHandling.getRoutingBoard();
    Package currFrontPackage = routingBoard.library.packages.get(libKey, true);
    Package currBackPackage = routingBoard.library.packages.get(libKey, false);
    if (currFrontPackage == null || currBackPackage == null) {
      FRLogger.warn(
          "Network.insert_component: component package not found at '"
              + par.scanner.getScopeIdentifier()
              + "'");
      return;
    }

    IntPoint componentLocation;
    if (location.coor != null) {
      componentLocation = par.coordinateTransform.dsnToBoard(location.coor).round();
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
            currFrontPackage,
            currBackPackage,
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
    Package currPackage = newComponent.getPackage();
    for (int i = 0; i < currPackage.pinCount(); i++) {
      Package.Pin currPin = currPackage.getPin(i);
      Padstack currPadstack = routingBoard.library.padstacks.get(currPin.padstackNo);
      if (currPadstack == null) {
        FRLogger.warn(
            "Network.insert_component: pin padstack not found at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      Collection<Net> pinNets = par.netlist.getNets(location.name, currPin.name);
      Collection<Integer> netNumbers = new LinkedList<>();
      for (Net currPinNet : pinNets) {
        app.freerouting.rules.Net currBoardNet =
            routingBoard.rules.nets.get(currPinNet.id.name, currPinNet.id.subnetNumber);
        if (currBoardNet == null) {
          FRLogger.warn(
              "Network.insert_component: board net not found at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
        } else {
          netNumbers.add(currBoardNet.netNumber);
        }
      }
      int[] netNoArr = new int[netNumbers.size()];
      int netIndex = 0;
      for (Integer currNetNo : netNumbers) {
        netNoArr[netIndex] = currNetNo;
        ++netIndex;
      }
      app.freerouting.rules.NetClass netClass;
      app.freerouting.rules.Net boardNet;
      if (netNoArr.length > 0) {
        boardNet = routingBoard.rules.nets.get(netNoArr[0]);
      } else {
        boardNet = null;
      }
      if (boardNet != null) {
        netClass = boardNet.getNetClass();
      } else {
        netClass = routingBoard.rules.getDefaultNetClass();
      }
      int clearanceClass = -1;
      ComponentPlacement.ItemClearanceInfo pinInfo = location.pin_infos.get(currPin.name);
      if (pinInfo != null) {
        clearanceClass = routingBoard.rules.clearanceMatrix.getNo(pinInfo.clearanceClass);
      }
      if (clearanceClass < 0) {
        if (currPadstack.fromLayer() == currPadstack.toLayer()) {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.SMD);
        } else {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.PIN);
        }
      }
      routingBoard.insertPin(newComponent.no, i, netNoArr, clearanceClass, fixedState);
    }

    // insert the keepouts belonging to the package (k = 1 for via keepouts)
    for (int k = 0; k <= 2; k++) {
      Package.Keepout[] keepoutArr;
      Map<String, ComponentPlacement.ItemClearanceInfo> currKeepoutInfos;
      if (k == 0) {
        keepoutArr = currPackage.keepoutArr;
        currKeepoutInfos = location.keepout_infos;
      } else if (k == 1) {
        keepoutArr = currPackage.viaKeepoutArr;
        currKeepoutInfos = location.via_keepout_infos;
      } else {
        keepoutArr = currPackage.placeKeepoutArr;
        currKeepoutInfos = location.place_keepout_infos;
      }
      for (int i = 0; i < keepoutArr.length; i++) {
        Package.Keepout currKeepout = keepoutArr[i];
        int layer = currKeepout.layer;
        if (layer >= routingBoard.getLayerCount()) {
          FRLogger.warn(
              "Network.insert_component: keepout layer is to big at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
          continue;
        }
        if (layer >= 0 && !location.isFront) {
          layer = routingBoard.getLayerCount() - currKeepout.layer - 1;
        }
        int clearanceClass =
            routingBoard
                .rules
                .getDefaultNetClass()
                .defaultItemClearanceClasses
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
        ComponentPlacement.ItemClearanceInfo keepoutInfo = currKeepoutInfos.get(currKeepout.name);
        if (keepoutInfo != null) {
          int currClearanceClass =
              routingBoard.rules.clearanceMatrix.getNo(keepoutInfo.clearanceClass);
          if (currClearanceClass > 0) {
            clearanceClass = currClearanceClass;
          }
        }
        if (layer >= 0) {
          if (k == 0) {
            routingBoard.insertObstacle(
                currKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currKeepout.name,
                fixedState);
          } else if (k == 1) {
            routingBoard.insertViaObstacle(
                currKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currKeepout.name,
                fixedState);
          } else {
            routingBoard.insertComponentObstacle(
                currKeepout.area,
                layer,
                componentTranslation,
                rotationInDegree,
                !location.isFront,
                clearanceClass,
                newComponent.no,
                currKeepout.name,
                fixedState);
          }
        } else {
          // insert the obstacle on all signal layers
          for (int j = 0; j < routingBoard.layerStructure.arr.length; j++) {
            if (routingBoard.layerStructure.arr[j].isSignal) {
              if (k == 0) {
                routingBoard.insertObstacle(
                    currKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currKeepout.name,
                    fixedState);
              } else if (k == 1) {
                routingBoard.insertViaObstacle(
                    currKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currKeepout.name,
                    fixedState);
              } else {
                routingBoard.insertComponentObstacle(
                    currKeepout.area,
                    j,
                    componentTranslation,
                    rotationInDegree,
                    !location.isFront,
                    clearanceClass,
                    newComponent.no,
                    currKeepout.name,
                    fixedState);
              }
            }
          }
        }
      }
    }
    // insert the outline as component keepout
    int courtyardIdx = -1;
    if (currPackage.outline != null && currPackage.outline.length > 1) {
      double maxArea = -1;
      for (int i = 0; i < currPackage.outline.length; i++) {
        if (currPackage.outline[i] != null && currPackage.outline[i].boundingBox() != null) {
          double area = currPackage.outline[i].boundingBox().area();
          if (area > maxArea) {
            maxArea = area;
            courtyardIdx = i;
          }
        }
      }
    }
    if (currPackage.outline != null) {
      for (int i = 0; i < currPackage.outline.length; i++) {
        boolean isCourtyard = i == courtyardIdx;
        if (currPackage.outlineWidths != null && i < currPackage.outlineWidths.length) {
          if (currPackage.outlineWidths[i] == 0.0) {
            isCourtyard = true;
          }
        }
        boolean isFabrication = false;
        if (!isCourtyard
            && currPackage.outlineWidths != null
            && i < currPackage.outlineWidths.length) {
          if (currPackage.outlineWidths[i] <= 110.0) {
            isFabrication = true;
          }
        }
        boolean isClosed = false;
        if (currPackage.outlineIsClosed != null && i < currPackage.outlineIsClosed.length) {
          isClosed = currPackage.outlineIsClosed[i];
        }
        routingBoard.insertComponentOutline(
            currPackage.outline[i],
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
  public boolean readScope(ReadScopeParameter par) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> classClassList = new LinkedList<>();
    Collection<ViaInfo> viaInfos = new LinkedList<>();
    Collection<Collection<String>> viaRules = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Network.read_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
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
              par.scanner,
              par.netlist,
              par.boardHandling.getRoutingBoard(),
              par.coordinateTransform,
              par.layerStructure);
        } else if (nextToken == Keyword.VIA) {
          ViaInfo currViaInfo = readViaInfo(par.scanner, par.boardHandling.getRoutingBoard());
          if (currViaInfo == null) {
            return false;
          }
          viaInfos.add(currViaInfo);
        } else if (nextToken == Keyword.VIA_RULE) {
          Collection<String> currViaRule =
              readViaRule(par.scanner, par.boardHandling.getRoutingBoard());
          if (currViaRule == null) {
            return false;
          }
          viaRules.add(currViaRule);
        } else if (nextToken == Keyword.CLASS) {
          NetClass currClass = NetClass.readScope(par.scanner);
          if (currClass == null) {
            return false;
          }
          classes.add(currClass);
        } else if (nextToken == Keyword.CLASS_CLASS) {
          NetClass.ClassClass currClassClass = NetClass.readClassClassScope(par.scanner);
          if (currClassClass == null) {
            return false;
          }
          classClassList.add(currClassClass);
        } else {
          skipScope(par.scanner);
        }
      }
    }

    // Add any vias defined in the Netclasses to the list of vias to be instantiated
    for (NetClass n : classes) {
      if (par.viaPadstackNames != null) {
        par.viaPadstackNames.addAll(n.useVia);
      } else {
        par.viaPadstackNames = n.useVia;
      }
    }

    RoutingBoard board = par.boardHandling.getRoutingBoard();

    // Set the via padstacks after network parsing, so that named vias from both structure and
    // network DSN sections are properly instantiated .
    if (par.viaPadstackNames != null) {
      Padstack[] viaPadstacks = new Padstack[par.viaPadstackNames.size()];
      Iterator<String> it = par.viaPadstackNames.iterator();
      int foundPadstackCount = 0;
      for (int i = 0; i < viaPadstacks.length; i++) {
        String currPadstackName = it.next();
        String cleanedName =
            currPadstackName != null ? currPadstackName.replaceAll("\\.\\d+", "") : null;
        Padstack currPadstack = board.library.padstacks.get(cleanedName);
        if (currPadstack != null) {
          viaPadstacks[foundPadstackCount] = currPadstack;
          ++foundPadstackCount;
        } else {
          FRLogger.warn(
              "Library.read_scope: via padstack with name '"
                  + currPadstackName
                  + " not found at '"
                  + par.scanner.getScopeIdentifier()
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

    insertViaInfos(viaInfos, par.boardHandling.getRoutingBoard(), par.viaAtSmdAllowed);
    insertViaRules(viaRules, par.boardHandling.getRoutingBoard());
    insertNetClasses(classes, par);
    insertClassPairs(classClassList, par);
    insertComponents(par);
    insertLogicalParts(par);
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
            Set<Net.Pin> currSubnetPinList = new TreeSet<>();
            if (!readNetPins(scanner, currSubnetPinList)) {
              return false;
            }
            subnetPinLists.add(currSubnetPinList);
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
    for (Collection<Net.Pin> currPinList : subnetPinLists) {
      Net.Id netId = new Net.Id(netName, subnetNumber);
      if (!netList.contains(netId)) {
        Net newNet = netList.addNet(netId);
        boolean containsPlane = layerStructure.containsPlane(netName);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, containsPlane);
        }
      }
      Net currSubnet = netList.getNet(netId);
      if (currSubnet == null) {
        FRLogger.warn(
            "Network.read_net_scope: net not found in netlist at '"
                + scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      currSubnet.setPins(currPinList);
      if (!netRules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net boardNet =
            board.rules.nets.get(currSubnet.id.name, currSubnet.id.subnetNumber);
        if (boardNet == null) {
          FRLogger.warn(
              "Network.read_net_scope: board net not found at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        for (Rule currOb : netRules) {
          if (currOb instanceof Rule.WidthRule rule) {
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
