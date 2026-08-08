package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.FixedState;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.LogicalPart;
import app.freerouting.core.Package;
import app.freerouting.core.Padstack;
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
public class Network extends ScopeKeyword {

  /** Creates a new instance of Network */
  public Network() {
    super("network");
  }

  public static void writeScope(WriteScopeParameter pPar) throws IOException {
    pPar.file.startScope();
    pPar.file.write("network");
    Collection<Pin> boardPins = pPar.board.getPins();
    for (int i = 1; i <= pPar.board.rules.nets.maxNetNo(); i++) {
      Net.writeScope(pPar, pPar.board.rules.nets.get(i), boardPins);
    }
    writeViaInfos(pPar.board.rules, pPar.file, pPar.identifierType);
    writeViaRules(pPar.board.rules, pPar.file, pPar.identifierType);
    writeNetClasses(pPar);
    pPar.file.endScope();
  }

  public static void writeViaInfos(
      BoardRules pRules, IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    for (int i = 0; i < pRules.viaInfos.count(); i++) {
      ViaInfo currVia = pRules.viaInfos.get(i);
      pFile.startScope();
      pFile.write("via ");
      pFile.newLine();
      pIdentifierType.write(currVia.getName(), pFile);
      pFile.write(" ");
      pIdentifierType.write(currVia.getPadstack().name, pFile);
      pFile.write(" ");
      pIdentifierType.write(pRules.clearanceMatrix.getName(currVia.getClearanceClass()), pFile);
      if (currVia.attachSmdAllowed()) {
        pFile.write(" attach");
      }
      pFile.endScope();
    }
  }

  public static void writeViaRules(
      BoardRules pRules, IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    for (ViaRule currRule : pRules.viaRules) {
      pFile.startScope();
      pFile.write("viaRule");
      pFile.newLine();
      pIdentifierType.write(currRule.name, pFile);
      for (int i = 0; i < currRule.viaCount(); i++) {
        pFile.write(" ");
        pIdentifierType.write(currRule.getVia(i).getName(), pFile);
      }
      pFile.endScope();
    }
  }

  public static void writeNetClasses(WriteScopeParameter pPar) throws IOException {
    for (int i = 0; i < pPar.board.rules.netClasses.count(); i++) {
      writeNetClass(pPar.board.rules.netClasses.get(i), pPar);
    }
  }

  public static void writeNetClass(
      app.freerouting.rules.NetClass pNetClass, WriteScopeParameter pPar) throws IOException {
    pPar.file.startScope();
    pPar.file.write("class ");
    pPar.identifierType.write(pNetClass.getName(), pPar.file);
    final int netsPerRow = 8;
    int netCounter = 0;
    for (int i = 1; i <= pPar.board.rules.nets.maxNetNo(); i++) {
      if (pPar.board.rules.nets.get(i).getNetClass() == pNetClass) {
        if (netCounter % netsPerRow == 0) {
          pPar.file.newLine();
        } else {
          pPar.file.write(" ");
        }
        pPar.identifierType.write(pPar.board.rules.nets.get(i).name, pPar.file);
        ++netCounter;
      }
    }

    // write the trace clearance class
    Rule.writeItemClearanceClass(
        pPar.board.rules.clearanceMatrix.getName(pNetClass.getTraceClearanceClass()),
        pPar.file,
        pPar.identifierType);

    if (pNetClass.getViaRule() != null) {
      // write the via rule
      pPar.file.newLine();
      pPar.file.write("(viaRule ");
      pPar.identifierType.write(pNetClass.getViaRule().name, pPar.file);
      pPar.file.write(")");
    }

    // write the rules, if they are different from the default rule.
    Rule.writeScope(pNetClass, pPar);

    writeCircuit(pNetClass, pPar);

    if (!pNetClass.getPullTight()) {
      pPar.file.newLine();
      pPar.file.write("(pullTight off)");
    }

    if (pNetClass.isShoveFixed()) {
      pPar.file.newLine();
      pPar.file.write("(shoveFixed on)");
    }

    pPar.file.endScope();
  }

  private static void writeCircuit(
      app.freerouting.rules.NetClass pNetClass, WriteScopeParameter pPar) throws IOException {
    double minTraceLength = pNetClass.getMinimumTraceLength();
    double maxTraceLength = pNetClass.getMaximumTraceLength();
    pPar.file.startScope();
    pPar.file.write("circuit ");
    pPar.file.newLine();
    pPar.file.write("(useLayer");
    int layerCount = pNetClass.layerCount();
    for (int i = 0; i < layerCount; i++) {
      if (pNetClass.isActiveRoutingLayer(i)) {
        pPar.file.write(" ");
        pPar.file.write(pPar.board.layerStructure.arr[i].name);
      }
    }
    pPar.file.write(")");
    if (minTraceLength > 0 || maxTraceLength > 0) {
      pPar.file.newLine();
      pPar.file.write("(length ");
      double transformedMaxLength;
      if (maxTraceLength <= 0) {
        transformedMaxLength = -1;
      } else {
        transformedMaxLength = pPar.coordinateTransform.boardToDsn(maxTraceLength);
      }
      pPar.file.write(String.valueOf(transformedMaxLength));
      pPar.file.write(" ");
      double transformedMinLength;
      if (minTraceLength <= 0) {
        transformedMinLength = 0;
      } else {
        transformedMinLength = pPar.coordinateTransform.boardToDsn(minTraceLength);
      }
      pPar.file.write(String.valueOf(transformedMinLength));
      pPar.file.write(")");
    }
    pPar.file.endScope();
  }

  /** Creates a sequence of subnets with 2 pins from p_pin_list */
  private static Collection<Collection<Net.Pin>> createOrderedSubnets(
      Collection<Net.Pin> pPinList) {
    Collection<Collection<Net.Pin>> result = new LinkedList<>();
    if (pPinList.isEmpty()) {
      return result;
    }

    Iterator<Net.Pin> it = pPinList.iterator();
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

  private static boolean readNetPins(IJFlexScanner pScanner, Collection<Net.Pin> pPinList) {
    Object nextToken;
    String componentName;
    String pinName;
    while (!(componentName = ((SpecctraDsnStreamReader) pScanner).nextString(true, '-'))
        .isEmpty()) {

      try {
        pScanner.yybegin(SpecctraDsnStreamReader.SPEC_CHAR);
        nextToken = pScanner.nextToken(); // overread the hyphen
      } catch (IOException e) {
        FRLogger.error("Network.read_net_pins: IO error while scanning file", e);
        return false;
      }

      pinName = pScanner.nextString(true);
      Net.Pin currEntry = new Net.Pin(componentName, pinName);
      pPinList.add(currEntry);
    }

    try {
      nextToken = pScanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_pins: IO error scanning file", e);
      return false;
    }
    if (nextToken == null) {
      FRLogger.warn(
          "Network.read_net_pins: unexpected end of file at '"
              + pScanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (nextToken != CLOSED_BRACKET) {
      // not end of scope
      FRLogger.warn(
          "Network.read_net_pins: expected closed bracket is missing at '"
              + pScanner.getScopeIdentifier()
              + "'");
    }

    return true;
  }

  public static ViaInfo readViaInfo(IJFlexScanner pScanner, BasicBoard pBoard) {
    try {
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + pScanner.getScopeIdentifier() + "'");
        return null;
      }
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + pScanner.getScopeIdentifier() + "'");
        return null;
      }
      pScanner.setScopeIdentifier(padstackName);
      Padstack viaPadstack = pBoard.library.getViaPadstack(padstackName);
      if (viaPadstack == null) {
        // The padstack may not yet be inserted into the list of via padstacks
        viaPadstack = pBoard.library.padstacks.get(padstackName);
        if (viaPadstack == null) {
          FRLogger.warn(
              "Network.read_via_info: padstack not found at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        pBoard.library.addViaPadstack(viaPadstack);
      }
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + pScanner.getScopeIdentifier() + "'");
        return null;
      }
      int clearanceClass = pBoard.rules.clearanceMatrix.getNo((String) nextToken);
      if (clearanceClass < 0) {
        // Clearance class not stored, because it is identical to the default clearance class.
        clearanceClass = BoardRules.defaultClearanceClass();
      }
      boolean attachAllowed = false;
      nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.ATTACH) {
          FRLogger.warn(
              "Network.read_via_info: Keyword.ATTACH expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        attachAllowed = true;
        nextToken = pScanner.nextToken();
        if (nextToken != Keyword.CLOSED_BRACKET) {
          FRLogger.warn(
              "Network.read_via_info: closing bracket expected at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }
      return new ViaInfo(name, viaPadstack, clearanceClass, attachAllowed, pBoard.rules);
    } catch (IOException e) {
      FRLogger.error("Network.read_via_info: IO error while scanning file", e);
      return null;
    }
  }

  public static Collection<String> readViaRule(IJFlexScanner pScanner, BasicBoard pBoard) {
    try {
      Collection<String> result = new LinkedList<>();
      for (; ; ) {
        pScanner.yybegin(SpecctraDsnStreamReader.NAME);
        Object nextToken = pScanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Network.read_via_rule: string expected at '" + pScanner.getScopeIdentifier() + "'");
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
      Collection<ViaInfo> pViaInfos, RoutingBoard pBoard, boolean pAttachAllowed) {
    if (!pViaInfos.isEmpty()) {
      for (ViaInfo currInfo : pViaInfos) {
        pBoard.rules.viaInfos.add(currInfo);
      }
    } else // no via infos found, create default via infos from the via padstacks.
    {
      createDefaultViaInfos(pBoard, pBoard.rules.getDefaultNetClass(), pAttachAllowed);
    }
  }

  private static void createDefaultViaInfos(
      BasicBoard pBoard, app.freerouting.rules.NetClass pNetClass, boolean pAttachAllowed) {
    int clClass =
        pNetClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    boolean isDefaultClass = pNetClass == pBoard.rules.getDefaultNetClass();
    for (int i = 0; i < pBoard.library.viaPadstackCount(); i++) {
      Padstack currPadstack = pBoard.library.getViaPadstack(i);
      boolean attachAllowed = pAttachAllowed && currPadstack.attachAllowed;
      String viaName;
      if (isDefaultClass) {
        viaName = currPadstack.name;
      } else {
        viaName = currPadstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + pNetClass.getName();
      }
      ViaInfo foundViaInfo =
          new ViaInfo(viaName, currPadstack, clClass, attachAllowed, pBoard.rules);
      pBoard.rules.viaInfos.add(foundViaInfo);
    }
  }

  private static void insertViaRules(Collection<Collection<String>> pViaRules, BasicBoard pBoard) {
    boolean ruleFound = false;
    for (Collection<String> currList : pViaRules) {
      if (currList.size() < 2) {
        continue;
      }
      if (addViaRule(currList, pBoard)) {
        ruleFound = true;
      }
    }
    if (!ruleFound) {
      pBoard.rules.createDefaultViaRule(pBoard.rules.getDefaultNetClass(), "default");
    }
    for (int i = 0; i < pBoard.rules.netClasses.count(); i++) {
      pBoard.rules.netClasses.get(i).setViaRule(pBoard.rules.getDefaultViaRule());
    }
  }

  /** Inserts a via rule into the board. Replaces an already existing via rule with the same */
  public static boolean addViaRule(Collection<String> pNameList, BasicBoard pBoard) {
    Iterator<String> it = pNameList.iterator();
    String ruleName = it.next();
    ViaRule existingRule = pBoard.rules.getViaRule(ruleName);
    ViaRule currRule = new ViaRule(ruleName);
    boolean ruleOk = true;
    while (it.hasNext()) {
      ViaInfo currVia = pBoard.rules.viaInfos.get(it.next());
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
        pBoard.rules.viaRules.remove(existingRule);
      }
      pBoard.rules.viaRules.add(currRule);
    }
    return ruleOk;
  }

  private static void insertNetClasses(Collection<NetClass> pNetClasses, ReadScopeParameter pPar) {
    BasicBoard routingBoard = pPar.boardHandling.getRoutingBoard();
    for (NetClass currClass : pNetClasses) {
      insertNetClass(
          currClass,
          pPar.layerStructure,
          routingBoard,
          pPar.coordinateTransform,
          pPar.viaAtSmdAllowed);
    }
  }

  public static void insertNetClass(
      NetClass pClass,
      LayerStructure pLayerStructure,
      BasicBoard pBoard,
      CoordinateTransform pCoordinateTransform,
      boolean pViaAtSmdAllowed) {
    app.freerouting.rules.NetClass boardNetClass =
        KiCadNetClassNames.isKiCadDefaultNetClassName(pClass.name)
            ? pBoard.rules.getDefaultNetClass()
            : pBoard.rules.appendNetClass(pClass.name);
    if (pClass.traceClearanceClass != null) {
      int traceClearanceClass = pBoard.rules.clearanceMatrix.getNo(pClass.traceClearanceClass);
      if (traceClearanceClass >= 0) {
        boardNetClass.setTraceClearanceClass(traceClearanceClass);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: clearance class not found at '"
                + boardNetClass.getName()
                + "'");
      }
    }
    if (pClass.viaRule != null) {
      ViaRule viaRule = pBoard.rules.getViaRule(pClass.viaRule);
      if (viaRule != null) {
        boardNetClass.setViaRule(viaRule);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: via rule not found at '" + boardNetClass.getName() + "'");
      }
    }
    if (pClass.maxTraceLength > 0) {
      boardNetClass.setMaximumTraceLength(pCoordinateTransform.dsnToBoard(pClass.maxTraceLength));
    }
    if (pClass.minTraceLength > 0) {
      boardNetClass.setMinimumTraceLength(pCoordinateTransform.dsnToBoard(pClass.minTraceLength));
    }
    for (String currNetName : pClass.netList) {
      Collection<app.freerouting.rules.Net> currNetList = pBoard.rules.nets.get(currNetName);
      for (app.freerouting.rules.Net currNet : currNetList) {
        currNet.setClass(boardNetClass);
      }
    }

    // read the trace width and clearance rules.

    boolean clearanceRuleFound = false;

    for (Rule currRule : pClass.rules) {
      if (currRule instanceof Rule.WidthRule rule1) {
        int traceHalfwidth = (int) Math.round(pCoordinateTransform.dsnToBoard(rule1.value / 2));
        boardNetClass.setTraceHalfWidth(traceHalfwidth);
      } else if (currRule instanceof Rule.ClearanceRule rule) {
        addClearanceRule(
            pBoard.rules.clearanceMatrix, boardNetClass, rule, -1, pCoordinateTransform);
        clearanceRuleFound = true;
      } else {
        FRLogger.warn(
            "Network.insert_net_class: rule type not yet implemented at '"
                + boardNetClass.getName()
                + "'");
      }
    }

    // read the layer dependent rules.

    for (Rule.LayerRule currLayerRule : pClass.layerRules) {
      for (String currLayerName : currLayerRule.layerNames) {
        int layerNo = pBoard.layerStructure.getNo(currLayerName);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_net_class: layer not found at '" + boardNetClass.getName() + "'");
          continue;
        }
        for (Rule currRule : currLayerRule.rules) {
          if (currRule instanceof Rule.WidthRule rule1) {
            int traceHalfwidth = (int) Math.round(pCoordinateTransform.dsnToBoard(rule1.value / 2));
            boardNetClass.setTraceHalfWidth(layerNo, traceHalfwidth);
          } else if (currRule instanceof Rule.ClearanceRule rule) {
            addClearanceRule(
                pBoard.rules.clearanceMatrix, boardNetClass, rule, layerNo, pCoordinateTransform);
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

    boardNetClass.setPullTight(pClass.pullTight);
    boardNetClass.setShoveFixed(pClass.shoveFixed);
    boolean viaInfosCreated = false;

    if (clearanceRuleFound && boardNetClass != pBoard.rules.getDefaultNetClass()) {
      createDefaultViaInfos(pBoard, boardNetClass, pViaAtSmdAllowed);
      viaInfosCreated = true;
    }

    if (!pClass.useVia.isEmpty()) {
      createViaRule(pClass.useVia, boardNetClass, pBoard, pViaAtSmdAllowed);
    } else if (viaInfosCreated) {
      pBoard.rules.createDefaultViaRule(boardNetClass, boardNetClass.getName());
    }
    if (!pClass.useLayer.isEmpty()) {
      createActiveTraceLayers(pClass.useLayer, pLayerStructure, boardNetClass);
    }
  }

  private static void insertClassPairs(
      Collection<NetClass.ClassClass> pClassClasses, ReadScopeParameter pPar) {
    for (NetClass.ClassClass currClassClass : pClassClasses) {
      Iterator<String> it1 = currClassClass.classNames.iterator();
      BasicBoard routingBoard = pPar.boardHandling.getRoutingBoard();
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
                  currClassClass, firstClass, secondClass, routingBoard, pPar.coordinateTransform);
            }
          }
        }
      }
    }
  }

  private static void insertClassPairInfo(
      NetClass.ClassClass pClassClass,
      app.freerouting.rules.NetClass pFirstClass,
      app.freerouting.rules.NetClass pSecondClass,
      BasicBoard pBoard,
      CoordinateTransform pCoordinateTransform) {
    for (Rule currRule : pClassClass.rules) {
      if (currRule instanceof Rule.ClearanceRule curr_clearance_rule) {
        addMixedClearanceRule(
            pBoard.rules.clearanceMatrix,
            pFirstClass,
            pSecondClass,
            curr_clearance_rule,
            -1,
            pCoordinateTransform);
      } else {
        FRLogger.warn("Network.insert_class_pair_info: unexpected rule");
      }
    }
    for (Rule.LayerRule currLayerRule : pClassClass.layerRules) {
      for (String currLayerName : currLayerRule.layerNames) {
        int layerNo = pBoard.layerStructure.getNo(currLayerName);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_class_pair_info: layer not found at '" + currLayerName + "'");
          continue;
        }
        for (Rule currRule : currLayerRule.rules) {
          if (currRule instanceof Rule.ClearanceRule rule) {
            addMixedClearanceRule(
                pBoard.rules.clearanceMatrix,
                pFirstClass,
                pSecondClass,
                rule,
                layerNo,
                pCoordinateTransform);
          } else {
            FRLogger.warn("Network.insert_class_pair_info: unexpected layer rule type");
          }
        }
      }
    }
  }

  private static void addMixedClearanceRule(
      ClearanceMatrix pClearanceMatrix,
      app.freerouting.rules.NetClass pFirstClass,
      app.freerouting.rules.NetClass pSecondClass,
      Rule.ClearanceRule pClearanceRule,
      int pLayerNo,
      CoordinateTransform pCoordinateTransform) {
    int currClearance = (int) Math.round(pCoordinateTransform.dsnToBoard(pClearanceRule.value));
    final String firstClassName = pFirstClass.getName();
    int firstClassNo = pClearanceMatrix.getNo(firstClassName);
    if (firstClassNo < 0) {
      pClearanceMatrix.appendClass(firstClassName);
      firstClassNo = pClearanceMatrix.getNo(firstClassName);
    }
    final String secondClassName = pSecondClass.getName();
    int secondClassNo = pClearanceMatrix.getNo(secondClassName);
    if (secondClassNo < 0) {
      pClearanceMatrix.appendClass(secondClassName);
      secondClassNo = pClearanceMatrix.getNo(secondClassName);
    }
    if (pClearanceRule.clearanceClassPairs.isEmpty()) {
      if (pLayerNo < 0) {
        pClearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        pClearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        pClearanceMatrix.setValue(firstClassNo, secondClassNo, pLayerNo, currClearance);
        pClearanceMatrix.setValue(secondClassNo, firstClassNo, pLayerNo, currClearance);
      }
    } else {
      for (String currString : pClearanceRule.clearanceClassPairs) {
        String[] currPair = currString.split("_");
        if (currPair.length != 2) {
          continue;
        }

        int currFirstClassNo;
        int currSecondClassNo;
        for (int i = 0; i < 2; i++) {
          if (i == 0) {
            currFirstClassNo = getClearanceClass(pClearanceMatrix, pFirstClass, currPair[0]);
            currSecondClassNo = getClearanceClass(pClearanceMatrix, pSecondClass, currPair[1]);
          } else {
            currFirstClassNo = getClearanceClass(pClearanceMatrix, pSecondClass, currPair[0]);
            currSecondClassNo = getClearanceClass(pClearanceMatrix, pFirstClass, currPair[1]);
          }
          if (pLayerNo < 0) {
            pClearanceMatrix.setValue(currFirstClassNo, currSecondClassNo, currClearance);
            pClearanceMatrix.setValue(currSecondClassNo, currFirstClassNo, currClearance);
          } else {
            pClearanceMatrix.setValue(currFirstClassNo, currSecondClassNo, pLayerNo, currClearance);
            pClearanceMatrix.setValue(currSecondClassNo, currFirstClassNo, pLayerNo, currClearance);
          }
        }
      }
    }
  }

  private static void createDefaultClearanceClasses(
      app.freerouting.rules.NetClass pNetClass, ClearanceMatrix pClearanceMatrix) {
    getClearanceClass(pClearanceMatrix, pNetClass, "via");
    getClearanceClass(pClearanceMatrix, pNetClass, "smd");
    getClearanceClass(pClearanceMatrix, pNetClass, "pin");
    getClearanceClass(pClearanceMatrix, pNetClass, "area");
  }

  private static void createViaRule(
      Collection<String> pUseVia,
      app.freerouting.rules.NetClass pNetClass,
      BasicBoard pBoard,
      boolean pAttachAllowed) {
    ViaRule newViaRule = new ViaRule(pNetClass.getName());
    int defaultViaClClass =
        pNetClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (String currViaName : pUseVia) {
      for (int i = 0; i < pBoard.rules.viaInfos.count(); i++) {
        ViaInfo currViaInfo = pBoard.rules.viaInfos.get(i);
        if (currViaInfo.getClearanceClass() == defaultViaClClass) {
          if (currViaInfo.getPadstack().name.equals(currViaName)) {
            newViaRule.appendVia(currViaInfo);
          }
        }
      }
    }
    pBoard.rules.viaRules.add(newViaRule);
    pNetClass.setViaRule(newViaRule);
  }

  private static void createActiveTraceLayers(
      Collection<String> pUseLayer,
      LayerStructure pLayerStructure,
      app.freerouting.rules.NetClass pNetClass) {
    for (int i = 0; i < pLayerStructure.arr.length; i++) {
      pNetClass.setActiveRoutingLayer(i, false);
    }
    for (String curLayerName : pUseLayer) {
      int currNo = pLayerStructure.getNo(curLayerName);
      pNetClass.setActiveRoutingLayer(currNo, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < pLayerStructure.arr.length; i++) {
      if (!pNetClass.isActiveRoutingLayer(i)) {
        pNetClass.setTraceHalfWidth(i, 0);
      }
    }
  }

  private static void addClearanceRule(
      ClearanceMatrix pClearanceMatrix,
      app.freerouting.rules.NetClass pNetClass,
      Rule.ClearanceRule pRule,
      int pLayerNo,
      CoordinateTransform pCoordinateTransform) {
    int currClearance = (int) Math.round(pCoordinateTransform.dsnToBoard(pRule.value));
    final String className = pNetClass.getName();
    int classNo = pClearanceMatrix.getNo(className);
    if (classNo < 0) {
      // class not yet existing, create a new class
      pClearanceMatrix.appendClass(className);
      classNo = pClearanceMatrix.getNo(className);
      // set the clearance values of the new class to the maximum of currClearance and
      // the existing values.
      for (int i = 1; i < pClearanceMatrix.getClassCount(); i++) {
        for (int j = 0; j < pClearanceMatrix.getLayerCount(); j++) {
          int currValue = Math.max(pClearanceMatrix.getValue(classNo, i, j, false), currClearance);
          pClearanceMatrix.setValue(classNo, i, j, currValue);
          pClearanceMatrix.setValue(i, classNo, j, currValue);
        }
      }
      pNetClass.defaultItemClearanceClasses.setAll(classNo);
    }
    pNetClass.setTraceClearanceClass(classNo);
    if (pRule.clearanceClassPairs.isEmpty()) {
      if (pLayerNo < 0) {
        pClearanceMatrix.setValue(classNo, classNo, currClearance);
      } else {
        pClearanceMatrix.setValue(classNo, classNo, pLayerNo, currClearance);
      }
      return;
    }
    if (Structure.containsWireClearancePair(pRule.clearanceClassPairs)) {
      createDefaultClearanceClasses(pNetClass, pClearanceMatrix);
    }
    for (String currString : pRule.clearanceClassPairs) {
      String[] currPair = currString.split("_");
      if (currPair.length != 2) {
        continue;
      }

      int firstClassNo = getClearanceClass(pClearanceMatrix, pNetClass, currPair[0]);
      int secondClassNo = getClearanceClass(pClearanceMatrix, pNetClass, currPair[1]);

      if (pLayerNo < 0) {
        pClearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        pClearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        pClearanceMatrix.setValue(firstClassNo, secondClassNo, pLayerNo, currClearance);
        pClearanceMatrix.setValue(secondClassNo, firstClassNo, pLayerNo, currClearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of p_net_class_name and
   * p_item_class_name. Creates a new class, if that class is not yet existing.
   */
  private static int getClearanceClass(
      ClearanceMatrix pClearanceMatrix,
      app.freerouting.rules.NetClass pNetClass,
      String pItemClassName) {
    String netClassName = pNetClass.getName();
    String newClassName = netClassName;
    if (!"wire".equals(pItemClassName)) {
      newClassName = newClassName + DsnFile.CLASS_CLEARANCE_SEPARATOR + pItemClassName;
    }
    int foundClassNo = pClearanceMatrix.getNo(newClassName);
    if (foundClassNo >= 0) {
      return foundClassNo;
    }
    pClearanceMatrix.appendClass(newClassName);
    int result = pClearanceMatrix.getNo(newClassName);
    int netClassNo = pClearanceMatrix.getNo(netClassName);
    if (netClassNo < 0 || result < 0) {
      FRLogger.warn(
          "Network.get_clearance_class: clearance class not found at '" + netClassName + "'");
      return result;
    }
    // initialize the clearance values of p_new_class_name from p_net_class_name
    for (int i = 1; i < pClearanceMatrix.getClassCount(); i++) {

      for (int j = 0; j < pClearanceMatrix.getLayerCount(); j++) {
        int currValue = pClearanceMatrix.getValue(netClassNo, i, j, false);
        pClearanceMatrix.setValue(result, i, j, currValue);
        pClearanceMatrix.setValue(i, result, j, currValue);
      }
    }
    switch (pItemClassName) {
      case "via" -> pNetClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> pNetClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> pNetClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> pNetClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
    }
    return result;
  }

  private static void insertComponents(ReadScopeParameter pPar) {
    for (ComponentPlacement nextLibComponent : pPar.placementList) {
      for (ComponentPlacement.ComponentLocation nextComponent : nextLibComponent.locations) {
        insertComponent(nextComponent, nextLibComponent.libName, pPar);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insertLogicalParts(ReadScopeParameter pPar) {
    BasicBoard routingBoard = pPar.boardHandling.getRoutingBoard();
    for (PartLibrary.LogicalPart nextPart : pPar.logicalParts) {
      Package libPackage = searchLibPackage(nextPart.name, pPar.logicalPartMappings, routingBoard);
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

    for (PartLibrary.LogicalPartMapping nextMapping : pPar.logicalPartMappings) {
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
      String pPartName,
      Collection<PartLibrary.LogicalPartMapping> pLogicalPartMappings,
      BasicBoard pBoard) {
    for (PartLibrary.LogicalPartMapping currMapping : pLogicalPartMappings) {
      if (currMapping.name.equals(pPartName)) {
        if (currMapping.components.isEmpty()) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + pPartName + "'");
          return null;
        }
        String componentName = currMapping.components.getFirst();
        if (componentName == null) {
          FRLogger.warn("Network.search_lib_package: component list empty at '" + pPartName + "'");
          return null;
        }
        app.freerouting.board.Component currComponent = pBoard.components.get(componentName);
        if (currComponent == null) {
          FRLogger.warn(
              "Network.search_lib_package: component not found at '" + componentName + "'");
          return null;
        }
        return currComponent.getPackage();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + pPartName + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insertComponent(
      ComponentPlacement.ComponentLocation pLocation, String pLibKey, ReadScopeParameter pPar) {
    RoutingBoard routingBoard = pPar.boardHandling.getRoutingBoard();
    Package currFrontPackage = routingBoard.library.packages.get(pLibKey, true);
    Package currBackPackage = routingBoard.library.packages.get(pLibKey, false);
    if (currFrontPackage == null || currBackPackage == null) {
      FRLogger.warn(
          "Network.insert_component: component package not found at '"
              + pPar.scanner.getScopeIdentifier()
              + "'");
      return;
    }

    IntPoint componentLocation;
    if (pLocation.coor != null) {
      componentLocation = pPar.coordinateTransform.dsnToBoard(pLocation.coor).round();
    } else {
      componentLocation = null;
    }
    double rotationInDegree = pLocation.rotation;

    app.freerouting.board.Component newComponent =
        routingBoard.components.add(
            pLocation.name,
            componentLocation,
            rotationInDegree,
            pLocation.isFront,
            currFrontPackage,
            currBackPackage,
            pLocation.positionFixed,
            pLocation.partNumber);

    if (componentLocation == null) {
      return; // component is not yet placed.
    }
    Vector componentTranslation = componentLocation.differenceBy(Point.ZERO);
    FixedState fixedState;
    if (pLocation.positionFixed) {
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
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      Collection<Net> pinNets = pPar.netlist.getNets(pLocation.name, currPin.name);
      Collection<Integer> netNumbers = new LinkedList<>();
      for (Net currPinNet : pinNets) {
        app.freerouting.rules.Net currBoardNet =
            routingBoard.rules.nets.get(currPinNet.id.name, currPinNet.id.subnetNumber);
        if (currBoardNet == null) {
          FRLogger.warn(
              "Network.insert_component: board net not found at '"
                  + pPar.scanner.getScopeIdentifier()
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
      ComponentPlacement.ItemClearanceInfo pinInfo = pLocation.pin_infos.get(currPin.name);
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
        currKeepoutInfos = pLocation.keepout_infos;
      } else if (k == 1) {
        keepoutArr = currPackage.viaKeepoutArr;
        currKeepoutInfos = pLocation.via_keepout_infos;
      } else {
        keepoutArr = currPackage.placeKeepoutArr;
        currKeepoutInfos = pLocation.place_keepout_infos;
      }
      for (int i = 0; i < keepoutArr.length; i++) {
        Package.Keepout currKeepout = keepoutArr[i];
        int layer = currKeepout.layer;
        if (layer >= routingBoard.getLayerCount()) {
          FRLogger.warn(
              "Network.insert_component: keepout layer is to big at '"
                  + pPar.scanner.getScopeIdentifier()
                  + "'");
          continue;
        }
        if (layer >= 0 && !pLocation.isFront) {
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
                !pLocation.isFront,
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
                !pLocation.isFront,
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
                !pLocation.isFront,
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
                    !pLocation.isFront,
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
                    !pLocation.isFront,
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
                    !pLocation.isFront,
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
            pLocation.isFront,
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
  public boolean readScope(ReadScopeParameter pPar) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> classClassList = new LinkedList<>();
    Collection<ViaInfo> viaInfos = new LinkedList<>();
    Collection<Collection<String>> viaRules = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Network.read_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
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
              pPar.scanner,
              pPar.netlist,
              pPar.boardHandling.getRoutingBoard(),
              pPar.coordinateTransform,
              pPar.layerStructure);
        } else if (nextToken == Keyword.VIA) {
          ViaInfo currViaInfo = readViaInfo(pPar.scanner, pPar.boardHandling.getRoutingBoard());
          if (currViaInfo == null) {
            return false;
          }
          viaInfos.add(currViaInfo);
        } else if (nextToken == Keyword.VIA_RULE) {
          Collection<String> currViaRule =
              readViaRule(pPar.scanner, pPar.boardHandling.getRoutingBoard());
          if (currViaRule == null) {
            return false;
          }
          viaRules.add(currViaRule);
        } else if (nextToken == Keyword.CLASS) {
          NetClass currClass = NetClass.readScope(pPar.scanner);
          if (currClass == null) {
            return false;
          }
          classes.add(currClass);
        } else if (nextToken == Keyword.CLASS_CLASS) {
          NetClass.ClassClass currClassClass = NetClass.readClassClassScope(pPar.scanner);
          if (currClassClass == null) {
            return false;
          }
          classClassList.add(currClassClass);
        } else {
          skipScope(pPar.scanner);
        }
      }
    }

    // Add any vias defined in the Netclasses to the list of vias to be instantiated
    for (NetClass n : classes) {
      if (pPar.viaPadstackNames != null) {
        pPar.viaPadstackNames.addAll(n.useVia);
      } else {
        pPar.viaPadstackNames = n.useVia;
      }
    }

    RoutingBoard board = pPar.boardHandling.getRoutingBoard();

    // Set the via padstacks after network parsing, so that named vias from both structure and
    // network DSN sections are properly instantiated .
    if (pPar.viaPadstackNames != null) {
      Padstack[] viaPadstacks = new Padstack[pPar.viaPadstackNames.size()];
      Iterator<String> it = pPar.viaPadstackNames.iterator();
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
                  + pPar.scanner.getScopeIdentifier()
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

    insertViaInfos(viaInfos, pPar.boardHandling.getRoutingBoard(), pPar.viaAtSmdAllowed);
    insertViaRules(viaRules, pPar.boardHandling.getRoutingBoard());
    insertNetClasses(classes, pPar);
    insertClassPairs(classClassList, pPar);
    insertComponents(pPar);
    insertLogicalParts(pPar);
    return true;
  }

  private boolean readNetScope(
      IJFlexScanner pScanner,
      NetList pNetList,
      RoutingBoard pBoard,
      CoordinateTransform pCoordinateTransform,
      LayerStructure pLayerStructure) {
    // read the net name
    String netName = pScanner.nextString();

    Object nextToken;
    int subnetNumber = 1;
    try {
      nextToken = pScanner.nextToken();
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
          nextToken = pScanner.nextToken();
        } catch (IOException e) {
          FRLogger.error("Network.read_net_scope: IO error scanning file", e);
          return false;
        }
        if (nextToken == null) {
          FRLogger.warn(
              "Network.read_net_scope: unexpected end of file at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == Keyword.PINS) {
            if (!readNetPins(pScanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.ORDER) {
            pinOrderFound = true;
            if (!readNetPins(pScanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.FROMTO) {
            Set<Net.Pin> currSubnetPinList = new TreeSet<>();
            if (!readNetPins(pScanner, currSubnetPinList)) {
              return false;
            }
            subnetPinLists.add(currSubnetPinList);
          } else if (nextToken == Keyword.RULE) {
            netRules.addAll(Rule.readScope(pScanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            FRLogger.warn(
                "Network.read_net_scope: layer_rule not yet implemented at '"
                    + pScanner.getScopeIdentifier()
                    + "'");
            skipScope(pScanner);
          } else {
            skipScope(pScanner);
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
      if (!pNetList.contains(netId)) {
        Net newNet = pNetList.addNet(netId);
        boolean containsPlane = pLayerStructure.containsPlane(netName);
        if (newNet != null) {
          pBoard.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, containsPlane);
        }
      }
      Net currSubnet = pNetList.getNet(netId);
      if (currSubnet == null) {
        FRLogger.warn(
            "Network.read_net_scope: net not found in netlist at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return false;
      }
      currSubnet.setPins(currPinList);
      if (!netRules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net boardNet =
            pBoard.rules.nets.get(currSubnet.id.name, currSubnet.id.subnetNumber);
        if (boardNet == null) {
          FRLogger.warn(
              "Network.read_net_scope: board net not found at '"
                  + pScanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        for (Rule currOb : netRules) {
          if (currOb instanceof Rule.WidthRule rule) {
            app.freerouting.rules.NetClass defaultNetRule = pBoard.rules.getDefaultNetClass();
            double wireWidth = rule.value;
            int traceHalfwidth = (int) Math.round(pCoordinateTransform.dsnToBoard(wireWidth) / 2);
            app.freerouting.rules.NetClass netRule =
                pBoard.rules.netClasses.find(
                    traceHalfwidth,
                    defaultNetRule.getTraceClearanceClass(),
                    defaultNetRule.getViaRule());
            if (netRule == null) {
              // create a new net rule
              netRule = pBoard.rules.getNewNetClass();
            }
            netRule.setTraceHalfWidth(traceHalfwidth);
            boardNet.setClass(netRule);
          } else {
            FRLogger.warn(
                "Network.read_net_scope: Rule not yet implemented at '"
                    + pScanner.getScopeIdentifier()
                    + "'");
          }
        }
      }
      ++subnetNumber;
    }
    return true;
  }
}
