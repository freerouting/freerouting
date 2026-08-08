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

  public static void writeScope(WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("network");
    Collection<Pin> boardPins = p_par.board.getPins();
    for (int i = 1; i <= p_par.board.rules.nets.maxNetNo(); i++) {
      Net.writeScope(p_par, p_par.board.rules.nets.get(i), boardPins);
    }
    writeViaInfos(p_par.board.rules, p_par.file, p_par.identifierType);
    writeViaRules(p_par.board.rules, p_par.file, p_par.identifierType);
    writeNetClasses(p_par);
    p_par.file.endScope();
  }

  public static void writeViaInfos(
      BoardRules p_rules, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    for (int i = 0; i < p_rules.viaInfos.count(); i++) {
      ViaInfo currVia = p_rules.viaInfos.get(i);
      p_file.startScope();
      p_file.write("via ");
      p_file.newLine();
      p_identifier_type.write(currVia.getName(), p_file);
      p_file.write(" ");
      p_identifier_type.write(currVia.getPadstack().name, p_file);
      p_file.write(" ");
      p_identifier_type.write(
          p_rules.clearanceMatrix.getName(currVia.getClearanceClass()), p_file);
      if (currVia.attachSmdAllowed()) {
        p_file.write(" attach");
      }
      p_file.endScope();
    }
  }

  public static void writeViaRules(
      BoardRules p_rules, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    for (ViaRule currRule : p_rules.viaRules) {
      p_file.startScope();
      p_file.write("viaRule");
      p_file.newLine();
      p_identifier_type.write(currRule.name, p_file);
      for (int i = 0; i < currRule.viaCount(); i++) {
        p_file.write(" ");
        p_identifier_type.write(currRule.getVia(i).getName(), p_file);
      }
      p_file.endScope();
    }
  }

  public static void writeNetClasses(WriteScopeParameter p_par) throws IOException {
    for (int i = 0; i < p_par.board.rules.netClasses.count(); i++) {
      writeNetClass(p_par.board.rules.netClasses.get(i), p_par);
    }
  }

  public static void writeNetClass(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("class ");
    p_par.identifierType.write(p_net_class.getName(), p_par.file);
    final int netsPerRow = 8;
    int netCounter = 0;
    for (int i = 1; i <= p_par.board.rules.nets.maxNetNo(); i++) {
      if (p_par.board.rules.nets.get(i).getNetClass() == p_net_class) {
        if (netCounter % netsPerRow == 0) {
          p_par.file.newLine();
        } else {
          p_par.file.write(" ");
        }
        p_par.identifierType.write(p_par.board.rules.nets.get(i).name, p_par.file);
        ++netCounter;
      }
    }

    // write the trace clearance class
    Rule.writeItemClearanceClass(
        p_par.board.rules.clearanceMatrix.getName(p_net_class.getTraceClearanceClass()),
        p_par.file,
        p_par.identifierType);

    if (p_net_class.getViaRule() != null) {
      // write the via rule
      p_par.file.newLine();
      p_par.file.write("(viaRule ");
      p_par.identifierType.write(p_net_class.getViaRule().name, p_par.file);
      p_par.file.write(")");
    }

    // write the rules, if they are different from the default rule.
    Rule.writeScope(p_net_class, p_par);

    writeCircuit(p_net_class, p_par);

    if (!p_net_class.getPullTight()) {
      p_par.file.newLine();
      p_par.file.write("(pullTight off)");
    }

    if (p_net_class.isShoveFixed()) {
      p_par.file.newLine();
      p_par.file.write("(shoveFixed on)");
    }

    p_par.file.endScope();
  }

  private static void writeCircuit(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par) throws IOException {
    double minTraceLength = p_net_class.getMinimumTraceLength();
    double maxTraceLength = p_net_class.getMaximumTraceLength();
    p_par.file.startScope();
    p_par.file.write("circuit ");
    p_par.file.newLine();
    p_par.file.write("(useLayer");
    int layerCount = p_net_class.layerCount();
    for (int i = 0; i < layerCount; i++) {
      if (p_net_class.isActiveRoutingLayer(i)) {
        p_par.file.write(" ");
        p_par.file.write(p_par.board.layerStructure.arr[i].name);
      }
    }
    p_par.file.write(")");
    if (minTraceLength > 0 || maxTraceLength > 0) {
      p_par.file.newLine();
      p_par.file.write("(length ");
      double transformedMaxLength;
      if (maxTraceLength <= 0) {
        transformedMaxLength = -1;
      } else {
        transformedMaxLength = p_par.coordinateTransform.boardToDsn(maxTraceLength);
      }
      p_par.file.write(String.valueOf(transformedMaxLength));
      p_par.file.write(" ");
      double transformedMinLength;
      if (minTraceLength <= 0) {
        transformedMinLength = 0;
      } else {
        transformedMinLength = p_par.coordinateTransform.boardToDsn(minTraceLength);
      }
      p_par.file.write(String.valueOf(transformedMinLength));
      p_par.file.write(")");
    }
    p_par.file.endScope();
  }

  /** Creates a sequence of subnets with 2 pins from p_pin_list */
  private static Collection<Collection<Net.Pin>> createOrderedSubnets(
      Collection<Net.Pin> p_pin_list) {
    Collection<Collection<Net.Pin>> result = new LinkedList<>();
    if (p_pin_list.isEmpty()) {
      return result;
    }

    Iterator<Net.Pin> it = p_pin_list.iterator();
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

  private static boolean readNetPins(IJFlexScanner p_scanner, Collection<Net.Pin> p_pin_list) {
    Object nextToken;
    String componentName;
    String pinName;
    while (!(componentName = ((SpecctraDsnStreamReader) p_scanner).nextString(true, '-'))
        .isEmpty()) {

      try {
        p_scanner.yybegin(SpecctraDsnStreamReader.SPEC_CHAR);
        nextToken = p_scanner.nextToken(); // overread the hyphen
      } catch (IOException e) {
        FRLogger.error("Network.read_net_pins: IO error while scanning file", e);
        return false;
      }

      pinName = p_scanner.nextString(true);
      Net.Pin currEntry = new Net.Pin(componentName, pinName);
      p_pin_list.add(currEntry);
    }

    try {
      nextToken = p_scanner.nextToken();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_pins: IO error scanning file", e);
      return false;
    }
    if (nextToken == null) {
      FRLogger.warn(
          "Network.read_net_pins: unexpected end of file at '"
              + p_scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (nextToken != CLOSED_BRACKET) {
      // not end of scope
      FRLogger.warn(
          "Network.read_net_pins: expected closed bracket is missing at '"
              + p_scanner.getScopeIdentifier()
              + "'");
    }

    return true;
  }

  public static ViaInfo readViaInfo(IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.getScopeIdentifier() + "'");
        return null;
      }
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.getScopeIdentifier() + "'");
        return null;
      }
      p_scanner.setScopeIdentifier(padstackName);
      Padstack viaPadstack = p_board.library.getViaPadstack(padstackName);
      if (viaPadstack == null) {
        // The padstack may not yet be inserted into the list of via padstacks
        viaPadstack = p_board.library.padstacks.get(padstackName);
        if (viaPadstack == null) {
          FRLogger.warn(
              "Network.read_via_info: padstack not found at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        p_board.library.addViaPadstack(viaPadstack);
      }
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.getScopeIdentifier() + "'");
        return null;
      }
      int clearanceClass = p_board.rules.clearanceMatrix.getNo((String) nextToken);
      if (clearanceClass < 0) {
        // Clearance class not stored, because it is identical to the default clearance class.
        clearanceClass = BoardRules.defaultClearanceClass();
      }
      boolean attachAllowed = false;
      nextToken = p_scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.ATTACH) {
          FRLogger.warn(
              "Network.read_via_info: Keyword.ATTACH expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        attachAllowed = true;
        nextToken = p_scanner.nextToken();
        if (nextToken != Keyword.CLOSED_BRACKET) {
          FRLogger.warn(
              "Network.read_via_info: closing bracket expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }
      return new ViaInfo(name, viaPadstack, clearanceClass, attachAllowed, p_board.rules);
    } catch (IOException e) {
      FRLogger.error("Network.read_via_info: IO error while scanning file", e);
      return null;
    }
  }

  public static Collection<String> readViaRule(IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      Collection<String> result = new LinkedList<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
        Object nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Network.read_via_rule: string expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
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
      Collection<ViaInfo> p_via_infos, RoutingBoard p_board, boolean p_attach_allowed) {
    if (!p_via_infos.isEmpty()) {
      for (ViaInfo currInfo : p_via_infos) {
        p_board.rules.viaInfos.add(currInfo);
      }
    } else // no via infos found, create default via infos from the via padstacks.
    {
      createDefaultViaInfos(p_board, p_board.rules.getDefaultNetClass(), p_attach_allowed);
    }
  }

  private static void createDefaultViaInfos(
      BasicBoard p_board, app.freerouting.rules.NetClass p_net_class, boolean p_attach_allowed) {
    int clClass =
        p_net_class.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    boolean isDefaultClass = p_net_class == p_board.rules.getDefaultNetClass();
    for (int i = 0; i < p_board.library.viaPadstackCount(); i++) {
      Padstack currPadstack = p_board.library.getViaPadstack(i);
      boolean attachAllowed = p_attach_allowed && currPadstack.attachAllowed;
      String viaName;
      if (isDefaultClass) {
        viaName = currPadstack.name;
      } else {
        viaName = currPadstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_net_class.getName();
      }
      ViaInfo foundViaInfo =
          new ViaInfo(viaName, currPadstack, clClass, attachAllowed, p_board.rules);
      p_board.rules.viaInfos.add(foundViaInfo);
    }
  }

  private static void insertViaRules(
      Collection<Collection<String>> p_via_rules, BasicBoard p_board) {
    boolean ruleFound = false;
    for (Collection<String> currList : p_via_rules) {
      if (currList.size() < 2) {
        continue;
      }
      if (addViaRule(currList, p_board)) {
        ruleFound = true;
      }
    }
    if (!ruleFound) {
      p_board.rules.createDefaultViaRule(p_board.rules.getDefaultNetClass(), "default");
    }
    for (int i = 0; i < p_board.rules.netClasses.count(); i++) {
      p_board.rules.netClasses.get(i).setViaRule(p_board.rules.getDefaultViaRule());
    }
  }

  /** Inserts a via rule into the board. Replaces an already existing via rule with the same */
  public static boolean addViaRule(Collection<String> p_name_list, BasicBoard p_board) {
    Iterator<String> it = p_name_list.iterator();
    String ruleName = it.next();
    ViaRule existingRule = p_board.rules.getViaRule(ruleName);
    ViaRule currRule = new ViaRule(ruleName);
    boolean ruleOk = true;
    while (it.hasNext()) {
      ViaInfo currVia = p_board.rules.viaInfos.get(it.next());
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
        p_board.rules.viaRules.remove(existingRule);
      }
      p_board.rules.viaRules.add(currRule);
    }
    return ruleOk;
  }

  private static void insertNetClasses(
      Collection<NetClass> p_net_classes, ReadScopeParameter p_par) {
    BasicBoard routingBoard = p_par.boardHandling.getRoutingBoard();
    for (NetClass currClass : p_net_classes) {
      insertNetClass(
          currClass,
          p_par.layerStructure,
          routingBoard,
          p_par.coordinateTransform,
          p_par.viaAtSmdAllowed);
    }
  }

  public static void insertNetClass(
      NetClass p_class,
      LayerStructure p_layer_structure,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform,
      boolean p_via_at_smd_allowed) {
    app.freerouting.rules.NetClass boardNetClass =
        KiCadNetClassNames.isKiCadDefaultNetClassName(p_class.name)
            ? p_board.rules.getDefaultNetClass()
            : p_board.rules.appendNetClass(p_class.name);
    if (p_class.traceClearanceClass != null) {
      int traceClearanceClass = p_board.rules.clearanceMatrix.getNo(p_class.traceClearanceClass);
      if (traceClearanceClass >= 0) {
        boardNetClass.setTraceClearanceClass(traceClearanceClass);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: clearance class not found at '"
                + boardNetClass.getName()
                + "'");
      }
    }
    if (p_class.viaRule != null) {
      ViaRule viaRule = p_board.rules.getViaRule(p_class.viaRule);
      if (viaRule != null) {
        boardNetClass.setViaRule(viaRule);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: via rule not found at '" + boardNetClass.getName() + "'");
      }
    }
    if (p_class.maxTraceLength > 0) {
      boardNetClass.setMaximumTraceLength(
          p_coordinate_transform.dsnToBoard(p_class.maxTraceLength));
    }
    if (p_class.minTraceLength > 0) {
      boardNetClass.setMinimumTraceLength(
          p_coordinate_transform.dsnToBoard(p_class.minTraceLength));
    }
    for (String currNetName : p_class.netList) {
      Collection<app.freerouting.rules.Net> currNetList = p_board.rules.nets.get(currNetName);
      for (app.freerouting.rules.Net currNet : currNetList) {
        currNet.setClass(boardNetClass);
      }
    }

    // read the trace width and clearance rules.

    boolean clearanceRuleFound = false;

    for (Rule currRule : p_class.rules) {
      if (currRule instanceof Rule.WidthRule rule1) {
        int traceHalfwidth = (int) Math.round(p_coordinate_transform.dsnToBoard(rule1.value / 2));
        boardNetClass.setTraceHalfWidth(traceHalfwidth);
      } else if (currRule instanceof Rule.ClearanceRule rule) {
        addClearanceRule(
            p_board.rules.clearanceMatrix, boardNetClass, rule, -1, p_coordinate_transform);
        clearanceRuleFound = true;
      } else {
        FRLogger.warn(
            "Network.insert_net_class: rule type not yet implemented at '"
                + boardNetClass.getName()
                + "'");
      }
    }

    // read the layer dependent rules.

    for (Rule.LayerRule curr_layer_rule : p_class.layerRules) {
      for (String curr_layer_name : curr_layer_rule.layerNames) {
        int layerNo = p_board.layerStructure.getNo(curr_layer_name);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_net_class: layer not found at '" + boardNetClass.getName() + "'");
          continue;
        }
        for (Rule currRule : curr_layer_rule.rules) {
          if (currRule instanceof Rule.WidthRule rule1) {
            int traceHalfwidth =
                (int) Math.round(p_coordinate_transform.dsnToBoard(rule1.value / 2));
            boardNetClass.setTraceHalfWidth(layerNo, traceHalfwidth);
          } else if (currRule instanceof Rule.ClearanceRule rule) {
            addClearanceRule(
                p_board.rules.clearanceMatrix,
                boardNetClass,
                rule,
                layerNo,
                p_coordinate_transform);
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

    boardNetClass.setPullTight(p_class.pullTight);
    boardNetClass.setShoveFixed(p_class.shoveFixed);
    boolean viaInfosCreated = false;

    if (clearanceRuleFound && boardNetClass != p_board.rules.getDefaultNetClass()) {
      createDefaultViaInfos(p_board, boardNetClass, p_via_at_smd_allowed);
      viaInfosCreated = true;
    }

    if (!p_class.useVia.isEmpty()) {
      createViaRule(p_class.useVia, boardNetClass, p_board, p_via_at_smd_allowed);
    } else if (viaInfosCreated) {
      p_board.rules.createDefaultViaRule(boardNetClass, boardNetClass.getName());
    }
    if (!p_class.useLayer.isEmpty()) {
      createActiveTraceLayers(p_class.useLayer, p_layer_structure, boardNetClass);
    }
  }

  private static void insertClassPairs(
      Collection<NetClass.ClassClass> p_class_classes, ReadScopeParameter p_par) {
    for (NetClass.ClassClass currClassClass : p_class_classes) {
      Iterator<String> it1 = currClassClass.classNames.iterator();
      BasicBoard routingBoard = p_par.boardHandling.getRoutingBoard();
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
                  currClassClass, firstClass, secondClass, routingBoard, p_par.coordinateTransform);
            }
          }
        }
      }
    }
  }

  private static void insertClassPairInfo(
      NetClass.ClassClass p_class_class,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform) {
    for (Rule currRule : p_class_class.rules) {
      if (currRule instanceof Rule.ClearanceRule curr_clearance_rule) {
        addMixedClearanceRule(
            p_board.rules.clearanceMatrix,
            p_first_class,
            p_second_class,
            curr_clearance_rule,
            -1,
            p_coordinate_transform);
      } else {
        FRLogger.warn("Network.insert_class_pair_info: unexpected rule");
      }
    }
    for (Rule.LayerRule curr_layer_rule : p_class_class.layerRules) {
      for (String curr_layer_name : curr_layer_rule.layerNames) {
        int layerNo = p_board.layerStructure.getNo(curr_layer_name);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_class_pair_info: layer not found at '" + curr_layer_name + "'");
          continue;
        }
        for (Rule currRule : curr_layer_rule.rules) {
          if (currRule instanceof Rule.ClearanceRule rule) {
            addMixedClearanceRule(
                p_board.rules.clearanceMatrix,
                p_first_class,
                p_second_class,
                rule,
                layerNo,
                p_coordinate_transform);
          } else {
            FRLogger.warn("Network.insert_class_pair_info: unexpected layer rule type");
          }
        }
      }
    }
  }

  private static void addMixedClearanceRule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      Rule.ClearanceRule p_clearance_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int currClearance =
        (int) Math.round(p_coordinate_transform.dsnToBoard(p_clearance_rule.value));
    final String firstClassName = p_first_class.getName();
    int firstClassNo = p_clearance_matrix.getNo(firstClassName);
    if (firstClassNo < 0) {
      p_clearance_matrix.appendClass(firstClassName);
      firstClassNo = p_clearance_matrix.getNo(firstClassName);
    }
    final String secondClassName = p_second_class.getName();
    int secondClassNo = p_clearance_matrix.getNo(secondClassName);
    if (secondClassNo < 0) {
      p_clearance_matrix.appendClass(secondClassName);
      secondClassNo = p_clearance_matrix.getNo(secondClassName);
    }
    if (p_clearance_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.setValue(firstClassNo, secondClassNo, currClearance);
        p_clearance_matrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        p_clearance_matrix.setValue(firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_clearance_matrix.setValue(secondClassNo, firstClassNo, p_layer_no, currClearance);
      }
    } else {
      for (String currString : p_clearance_rule.clearanceClassPairs) {
        String[] currPair = currString.split("_");
        if (currPair.length != 2) {
          continue;
        }

        int currFirstClassNo;
        int currSecondClassNo;
        for (int i = 0; i < 2; i++) {
          if (i == 0) {
            currFirstClassNo = getClearanceClass(p_clearance_matrix, p_first_class, currPair[0]);
            currSecondClassNo =
                getClearanceClass(p_clearance_matrix, p_second_class, currPair[1]);
          } else {
            currFirstClassNo = getClearanceClass(p_clearance_matrix, p_second_class, currPair[0]);
            currSecondClassNo = getClearanceClass(p_clearance_matrix, p_first_class, currPair[1]);
          }
          if (p_layer_no < 0) {
            p_clearance_matrix.setValue(currFirstClassNo, currSecondClassNo, currClearance);
            p_clearance_matrix.setValue(currSecondClassNo, currFirstClassNo, currClearance);
          } else {
            p_clearance_matrix.setValue(
                currFirstClassNo, currSecondClassNo, p_layer_no, currClearance);
            p_clearance_matrix.setValue(
                currSecondClassNo, currFirstClassNo, p_layer_no, currClearance);
          }
        }
      }
    }
  }

  private static void createDefaultClearanceClasses(
      app.freerouting.rules.NetClass p_net_class, ClearanceMatrix p_clearance_matrix) {
    getClearanceClass(p_clearance_matrix, p_net_class, "via");
    getClearanceClass(p_clearance_matrix, p_net_class, "smd");
    getClearanceClass(p_clearance_matrix, p_net_class, "pin");
    getClearanceClass(p_clearance_matrix, p_net_class, "area");
  }

  private static void createViaRule(
      Collection<String> p_use_via,
      app.freerouting.rules.NetClass p_net_class,
      BasicBoard p_board,
      boolean p_attach_allowed) {
    ViaRule newViaRule = new ViaRule(p_net_class.getName());
    int defaultViaClClass =
        p_net_class.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (String curr_via_name : p_use_via) {
      for (int i = 0; i < p_board.rules.viaInfos.count(); i++) {
        ViaInfo currViaInfo = p_board.rules.viaInfos.get(i);
        if (currViaInfo.getClearanceClass() == defaultViaClClass) {
          if (currViaInfo.getPadstack().name.equals(curr_via_name)) {
            newViaRule.appendVia(currViaInfo);
          }
        }
      }
    }
    p_board.rules.viaRules.add(newViaRule);
    p_net_class.setViaRule(newViaRule);
  }

  private static void createActiveTraceLayers(
      Collection<String> p_use_layer,
      LayerStructure p_layer_structure,
      app.freerouting.rules.NetClass p_net_class) {
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      p_net_class.setActiveRoutingLayer(i, false);
    }
    for (String cur_layer_name : p_use_layer) {
      int currNo = p_layer_structure.getNo(cur_layer_name);
      p_net_class.setActiveRoutingLayer(currNo, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      if (!p_net_class.isActiveRoutingLayer(i)) {
        p_net_class.setTraceHalfWidth(i, 0);
      }
    }
  }

  private static void addClearanceRule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      Rule.ClearanceRule p_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int currClearance = (int) Math.round(p_coordinate_transform.dsnToBoard(p_rule.value));
    final String className = p_net_class.getName();
    int classNo = p_clearance_matrix.getNo(className);
    if (classNo < 0) {
      // class not yet existing, create a new class
      p_clearance_matrix.appendClass(className);
      classNo = p_clearance_matrix.getNo(className);
      // set the clearance values of the new class to the maximum of currClearance and
      // the existing values.
      for (int i = 1; i < p_clearance_matrix.getClassCount(); i++) {
        for (int j = 0; j < p_clearance_matrix.getLayerCount(); j++) {
          int currValue =
              Math.max(p_clearance_matrix.getValue(classNo, i, j, false), currClearance);
          p_clearance_matrix.setValue(classNo, i, j, currValue);
          p_clearance_matrix.setValue(i, classNo, j, currValue);
        }
      }
      p_net_class.defaultItemClearanceClasses.setAll(classNo);
    }
    p_net_class.setTraceClearanceClass(classNo);
    if (p_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.setValue(classNo, classNo, currClearance);
      } else {
        p_clearance_matrix.setValue(classNo, classNo, p_layer_no, currClearance);
      }
      return;
    }
    if (Structure.containsWireClearancePair(p_rule.clearanceClassPairs)) {
      createDefaultClearanceClasses(p_net_class, p_clearance_matrix);
    }
    for (String currString : p_rule.clearanceClassPairs) {
      String[] currPair = currString.split("_");
      if (currPair.length != 2) {
        continue;
      }

      int firstClassNo = getClearanceClass(p_clearance_matrix, p_net_class, currPair[0]);
      int secondClassNo = getClearanceClass(p_clearance_matrix, p_net_class, currPair[1]);

      if (p_layer_no < 0) {
        p_clearance_matrix.setValue(firstClassNo, secondClassNo, currClearance);
        p_clearance_matrix.setValue(secondClassNo, firstClassNo, currClearance);
      } else {
        p_clearance_matrix.setValue(firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_clearance_matrix.setValue(secondClassNo, firstClassNo, p_layer_no, currClearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of p_net_class_name and
   * p_item_class_name. Creates a new class, if that class is not yet existing.
   */
  private static int getClearanceClass(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      String p_item_class_name) {
    String netClassName = p_net_class.getName();
    String newClassName = netClassName;
    if (!"wire".equals(p_item_class_name)) {
      newClassName = newClassName + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_item_class_name;
    }
    int foundClassNo = p_clearance_matrix.getNo(newClassName);
    if (foundClassNo >= 0) {
      return foundClassNo;
    }
    p_clearance_matrix.appendClass(newClassName);
    int result = p_clearance_matrix.getNo(newClassName);
    int netClassNo = p_clearance_matrix.getNo(netClassName);
    if (netClassNo < 0 || result < 0) {
      FRLogger.warn(
          "Network.get_clearance_class: clearance class not found at '" + netClassName + "'");
      return result;
    }
    // initialize the clearance values of p_new_class_name from p_net_class_name
    for (int i = 1; i < p_clearance_matrix.getClassCount(); i++) {

      for (int j = 0; j < p_clearance_matrix.getLayerCount(); j++) {
        int currValue = p_clearance_matrix.getValue(netClassNo, i, j, false);
        p_clearance_matrix.setValue(result, i, j, currValue);
        p_clearance_matrix.setValue(i, result, j, currValue);
      }
    }
    switch (p_item_class_name) {
      case "via" -> p_net_class.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> p_net_class.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> p_net_class.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> p_net_class.defaultItemClearanceClasses.set(ItemClass.AREA, result);
    }
    return result;
  }

  private static void insertComponents(ReadScopeParameter p_par) {
    for (ComponentPlacement next_lib_component : p_par.placementList) {
      for (ComponentPlacement.ComponentLocation next_component : next_lib_component.locations) {
        insertComponent(next_component, next_lib_component.libName, p_par);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insertLogicalParts(ReadScopeParameter p_par) {
    BasicBoard routingBoard = p_par.boardHandling.getRoutingBoard();
    for (PartLibrary.LogicalPart nextPart : p_par.logicalParts) {
      Package libPackage =
          searchLibPackage(nextPart.name, p_par.logicalPartMappings, routingBoard);
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

    for (PartLibrary.LogicalPartMapping nextMapping : p_par.logicalPartMappings) {
      LogicalPart currLogicalPart = routingBoard.library.logicalParts.get(nextMapping.name);
      {
        if (currLogicalPart == null) {
          FRLogger.warn(
              "Network.insert_logical_parts: logical part not found at '" + nextMapping.name + "'");
        }
      }
      for (String curr_cmp_name : nextMapping.components) {
        app.freerouting.board.Component currComponent = routingBoard.components.get(curr_cmp_name);
        if (currComponent != null) {
          currComponent.setLogicalPart(currLogicalPart);
        } else {
          FRLogger.warn(
              "Network.insert_logical_parts: board component not found at '" + curr_cmp_name + "'");
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
      String p_part_name,
      Collection<PartLibrary.LogicalPartMapping> p_logical_part_mappings,
      BasicBoard p_board) {
    for (PartLibrary.LogicalPartMapping curr_mapping : p_logical_part_mappings) {
      if (curr_mapping.name.equals(p_part_name)) {
        if (curr_mapping.components.isEmpty()) {
          FRLogger.warn(
              "Network.search_lib_package: component list empty at '" + p_part_name + "'");
          return null;
        }
        String componentName = curr_mapping.components.getFirst();
        if (componentName == null) {
          FRLogger.warn(
              "Network.search_lib_package: component list empty at '" + p_part_name + "'");
          return null;
        }
        app.freerouting.board.Component currComponent = p_board.components.get(componentName);
        if (currComponent == null) {
          FRLogger.warn(
              "Network.search_lib_package: component not found at '" + componentName + "'");
          return null;
        }
        return currComponent.getPackage();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + p_part_name + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insertComponent(
      ComponentPlacement.ComponentLocation p_location, String p_lib_key, ReadScopeParameter p_par) {
    RoutingBoard routingBoard = p_par.boardHandling.getRoutingBoard();
    Package currFrontPackage = routingBoard.library.packages.get(p_lib_key, true);
    Package currBackPackage = routingBoard.library.packages.get(p_lib_key, false);
    if (currFrontPackage == null || currBackPackage == null) {
      FRLogger.warn(
          "Network.insert_component: component package not found at '"
              + p_par.scanner.getScopeIdentifier()
              + "'");
      return;
    }

    IntPoint componentLocation;
    if (p_location.coor != null) {
      componentLocation = p_par.coordinateTransform.dsnToBoard(p_location.coor).round();
    } else {
      componentLocation = null;
    }
    double rotationInDegree = p_location.rotation;

    app.freerouting.board.Component newComponent =
        routingBoard.components.add(
            p_location.name,
            componentLocation,
            rotationInDegree,
            p_location.isFront,
            currFrontPackage,
            currBackPackage,
            p_location.positionFixed,
            p_location.partNumber);

    if (componentLocation == null) {
      return; // component is not yet placed.
    }
    Vector componentTranslation = componentLocation.differenceBy(Point.ZERO);
    FixedState fixedState;
    if (p_location.positionFixed) {
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
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return;
      }
      Collection<Net> pinNets = p_par.netlist.getNets(p_location.name, currPin.name);
      Collection<Integer> netNumbers = new LinkedList<>();
      for (Net curr_pin_net : pinNets) {
        app.freerouting.rules.Net currBoardNet =
            routingBoard.rules.nets.get(curr_pin_net.id.name, curr_pin_net.id.subnetNumber);
        if (currBoardNet == null) {
          FRLogger.warn(
              "Network.insert_component: board net not found at '"
                  + p_par.scanner.getScopeIdentifier()
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
      ComponentPlacement.ItemClearanceInfo pinInfo = p_location.pin_infos.get(currPin.name);
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
      Map<String, ComponentPlacement.ItemClearanceInfo> curr_keepout_infos;
      if (k == 0) {
        keepoutArr = currPackage.keepoutArr;
        curr_keepout_infos = p_location.keepout_infos;
      } else if (k == 1) {
        keepoutArr = currPackage.viaKeepoutArr;
        curr_keepout_infos = p_location.via_keepout_infos;
      } else {
        keepoutArr = currPackage.placeKeepoutArr;
        curr_keepout_infos = p_location.place_keepout_infos;
      }
      for (int i = 0; i < keepoutArr.length; i++) {
        Package.Keepout currKeepout = keepoutArr[i];
        int layer = currKeepout.layer;
        if (layer >= routingBoard.getLayerCount()) {
          FRLogger.warn(
              "Network.insert_component: keepout layer is to big at '"
                  + p_par.scanner.getScopeIdentifier()
                  + "'");
          continue;
        }
        if (layer >= 0 && !p_location.isFront) {
          layer = routingBoard.getLayerCount() - currKeepout.layer - 1;
        }
        int clearanceClass =
            routingBoard
                .rules
                .getDefaultNetClass()
                .defaultItemClearanceClasses
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
        ComponentPlacement.ItemClearanceInfo keepoutInfo = curr_keepout_infos.get(currKeepout.name);
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
                !p_location.isFront,
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
                !p_location.isFront,
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
                !p_location.isFront,
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
                    !p_location.isFront,
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
                    !p_location.isFront,
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
                    !p_location.isFront,
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
            p_location.isFront,
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
  public boolean readScope(ReadScopeParameter p_par) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> classClassList = new LinkedList<>();
    Collection<ViaInfo> viaInfos = new LinkedList<>();
    Collection<Collection<String>> viaRules = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Network.read_scope: unexpected end of file at '"
                + p_par.scanner.getScopeIdentifier()
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
              p_par.scanner,
              p_par.netlist,
              p_par.boardHandling.getRoutingBoard(),
              p_par.coordinateTransform,
              p_par.layerStructure);
        } else if (nextToken == Keyword.VIA) {
          ViaInfo currViaInfo =
              readViaInfo(p_par.scanner, p_par.boardHandling.getRoutingBoard());
          if (currViaInfo == null) {
            return false;
          }
          viaInfos.add(currViaInfo);
        } else if (nextToken == Keyword.VIA_RULE) {
          Collection<String> currViaRule =
              readViaRule(p_par.scanner, p_par.boardHandling.getRoutingBoard());
          if (currViaRule == null) {
            return false;
          }
          viaRules.add(currViaRule);
        } else if (nextToken == Keyword.CLASS) {
          NetClass currClass = NetClass.readScope(p_par.scanner);
          if (currClass == null) {
            return false;
          }
          classes.add(currClass);
        } else if (nextToken == Keyword.CLASS_CLASS) {
          NetClass.ClassClass currClassClass = NetClass.readClassClassScope(p_par.scanner);
          if (currClassClass == null) {
            return false;
          }
          classClassList.add(currClassClass);
        } else {
          skipScope(p_par.scanner);
        }
      }
    }

    // Add any vias defined in the Netclasses to the list of vias to be instantiated
    for (NetClass n : classes) {
      if (p_par.viaPadstackNames != null) {
        p_par.viaPadstackNames.addAll(n.useVia);
      } else {
        p_par.viaPadstackNames = n.useVia;
      }
    }

    RoutingBoard board = p_par.boardHandling.getRoutingBoard();

    // Set the via padstacks after network parsing, so that named vias from both structure and
    // network DSN sections are properly instantiated .
    if (p_par.viaPadstackNames != null) {
      Padstack[] viaPadstacks = new Padstack[p_par.viaPadstackNames.size()];
      Iterator<String> it = p_par.viaPadstackNames.iterator();
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
                  + p_par.scanner.getScopeIdentifier()
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

    insertViaInfos(viaInfos, p_par.boardHandling.getRoutingBoard(), p_par.viaAtSmdAllowed);
    insertViaRules(viaRules, p_par.boardHandling.getRoutingBoard());
    insertNetClasses(classes, p_par);
    insertClassPairs(classClassList, p_par);
    insertComponents(p_par);
    insertLogicalParts(p_par);
    return true;
  }

  private boolean readNetScope(
      IJFlexScanner p_scanner,
      NetList p_net_list,
      RoutingBoard p_board,
      CoordinateTransform p_coordinate_transform,
      LayerStructure p_layer_structure) {
    // read the net name
    String netName = p_scanner.nextString();

    Object nextToken;
    int subnetNumber = 1;
    try {
      nextToken = p_scanner.nextToken();
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
          nextToken = p_scanner.nextToken();
        } catch (IOException e) {
          FRLogger.error("Network.read_net_scope: IO error scanning file", e);
          return false;
        }
        if (nextToken == null) {
          FRLogger.warn(
              "Network.read_net_scope: unexpected end of file at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == Keyword.PINS) {
            if (!readNetPins(p_scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.ORDER) {
            pinOrderFound = true;
            if (!readNetPins(p_scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.FROMTO) {
            Set<Net.Pin> currSubnetPinList = new TreeSet<>();
            if (!readNetPins(p_scanner, currSubnetPinList)) {
              return false;
            }
            subnetPinLists.add(currSubnetPinList);
          } else if (nextToken == Keyword.RULE) {
            netRules.addAll(Rule.readScope(p_scanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            FRLogger.warn(
                "Network.read_net_scope: layer_rule not yet implemented at '"
                    + p_scanner.getScopeIdentifier()
                    + "'");
            skipScope(p_scanner);
          } else {
            skipScope(p_scanner);
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
      if (!p_net_list.contains(netId)) {
        Net newNet = p_net_list.addNet(netId);
        boolean containsPlane = p_layer_structure.containsPlane(netName);
        if (newNet != null) {
          p_board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, containsPlane);
        }
      }
      Net currSubnet = p_net_list.getNet(netId);
      if (currSubnet == null) {
        FRLogger.warn(
            "Network.read_net_scope: net not found in netlist at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      currSubnet.setPins(currPinList);
      if (!netRules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net boardNet =
            p_board.rules.nets.get(currSubnet.id.name, currSubnet.id.subnetNumber);
        if (boardNet == null) {
          FRLogger.warn(
              "Network.read_net_scope: board net not found at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        for (Rule currOb : netRules) {
          if (currOb instanceof Rule.WidthRule rule) {
            app.freerouting.rules.NetClass defaultNetRule = p_board.rules.getDefaultNetClass();
            double wireWidth = rule.value;
            int traceHalfwidth =
                (int) Math.round(p_coordinate_transform.dsnToBoard(wireWidth) / 2);
            app.freerouting.rules.NetClass netRule =
                p_board.rules.netClasses.find(
                    traceHalfwidth,
                    defaultNetRule.getTraceClearanceClass(),
                    defaultNetRule.getViaRule());
            if (netRule == null) {
              // create a new net rule
              netRule = p_board.rules.getNewNetClass();
            }
            netRule.setTraceHalfWidth(traceHalfwidth);
            boardNet.setClass(netRule);
          } else {
            FRLogger.warn(
                "Network.read_net_scope: Rule not yet implemented at '"
                    + p_scanner.getScopeIdentifier()
                    + "'");
          }
        }
      }
      ++subnetNumber;
    }
    return true;
  }
}
