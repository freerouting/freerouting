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

  public static void write_scope(WriteScopeParameter p_par) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("network");
    Collection<Pin> boardPins = p_par.board.get_pins();
    for (int i = 1; i <= p_par.board.rules.nets.max_net_no(); i++) {
      Net.write_scope(p_par, p_par.board.rules.nets.get(i), boardPins);
    }
    write_via_infos(p_par.board.rules, p_par.file, p_par.identifierType);
    write_via_rules(p_par.board.rules, p_par.file, p_par.identifierType);
    write_net_classes(p_par);
    p_par.file.end_scope();
  }

  public static void write_via_infos(
      BoardRules p_rules, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    for (int i = 0; i < p_rules.viaInfos.count(); i++) {
      ViaInfo currVia = p_rules.viaInfos.get(i);
      p_file.start_scope();
      p_file.write("via ");
      p_file.new_line();
      p_identifier_type.write(currVia.get_name(), p_file);
      p_file.write(" ");
      p_identifier_type.write(currVia.get_padstack().name, p_file);
      p_file.write(" ");
      p_identifier_type.write(
          p_rules.clearanceMatrix.get_name(currVia.get_clearance_class()), p_file);
      if (currVia.attach_smd_allowed()) {
        p_file.write(" attach");
      }
      p_file.end_scope();
    }
  }

  public static void write_via_rules(
      BoardRules p_rules, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    for (ViaRule currRule : p_rules.viaRules) {
      p_file.start_scope();
      p_file.write("viaRule");
      p_file.new_line();
      p_identifier_type.write(currRule.name, p_file);
      for (int i = 0; i < currRule.via_count(); i++) {
        p_file.write(" ");
        p_identifier_type.write(currRule.get_via(i).get_name(), p_file);
      }
      p_file.end_scope();
    }
  }

  public static void write_net_classes(WriteScopeParameter p_par) throws IOException {
    for (int i = 0; i < p_par.board.rules.netClasses.count(); i++) {
      write_net_class(p_par.board.rules.netClasses.get(i), p_par);
    }
  }

  public static void write_net_class(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("class ");
    p_par.identifierType.write(p_net_class.get_name(), p_par.file);
    final int netsPerRow = 8;
    int netCounter = 0;
    for (int i = 1; i <= p_par.board.rules.nets.max_net_no(); i++) {
      if (p_par.board.rules.nets.get(i).get_class() == p_net_class) {
        if (netCounter % netsPerRow == 0) {
          p_par.file.new_line();
        } else {
          p_par.file.write(" ");
        }
        p_par.identifierType.write(p_par.board.rules.nets.get(i).name, p_par.file);
        ++netCounter;
      }
    }

    // write the trace clearance class
    Rule.write_item_clearance_class(
        p_par.board.rules.clearanceMatrix.get_name(p_net_class.get_trace_clearance_class()),
        p_par.file,
        p_par.identifierType);

    if (p_net_class.get_via_rule() != null) {
      // write the via rule
      p_par.file.new_line();
      p_par.file.write("(viaRule ");
      p_par.identifierType.write(p_net_class.get_via_rule().name, p_par.file);
      p_par.file.write(")");
    }

    // write the rules, if they are different from the default rule.
    Rule.write_scope(p_net_class, p_par);

    write_circuit(p_net_class, p_par);

    if (!p_net_class.get_pull_tight()) {
      p_par.file.new_line();
      p_par.file.write("(pullTight off)");
    }

    if (p_net_class.is_shove_fixed()) {
      p_par.file.new_line();
      p_par.file.write("(shoveFixed on)");
    }

    p_par.file.end_scope();
  }

  private static void write_circuit(
      app.freerouting.rules.NetClass p_net_class, WriteScopeParameter p_par) throws IOException {
    double minTraceLength = p_net_class.get_minimum_trace_length();
    double maxTraceLength = p_net_class.get_maximum_trace_length();
    p_par.file.start_scope();
    p_par.file.write("circuit ");
    p_par.file.new_line();
    p_par.file.write("(useLayer");
    int layerCount = p_net_class.layer_count();
    for (int i = 0; i < layerCount; i++) {
      if (p_net_class.is_active_routing_layer(i)) {
        p_par.file.write(" ");
        p_par.file.write(p_par.board.layerStructure.arr[i].name);
      }
    }
    p_par.file.write(")");
    if (minTraceLength > 0 || maxTraceLength > 0) {
      p_par.file.new_line();
      p_par.file.write("(length ");
      double transformedMaxLength;
      if (maxTraceLength <= 0) {
        transformedMaxLength = -1;
      } else {
        transformedMaxLength = p_par.coordinateTransform.board_to_dsn(maxTraceLength);
      }
      p_par.file.write(String.valueOf(transformedMaxLength));
      p_par.file.write(" ");
      double transformedMinLength;
      if (minTraceLength <= 0) {
        transformedMinLength = 0;
      } else {
        transformedMinLength = p_par.coordinateTransform.board_to_dsn(minTraceLength);
      }
      p_par.file.write(String.valueOf(transformedMinLength));
      p_par.file.write(")");
    }
    p_par.file.end_scope();
  }

  /** Creates a sequence of subnets with 2 pins from p_pin_list */
  private static Collection<Collection<Net.Pin>> create_ordered_subnets(
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

  private static boolean read_net_pins(IJFlexScanner p_scanner, Collection<Net.Pin> p_pin_list) {
    Object nextToken;
    String componentName;
    String pinName;
    while (!(componentName = ((SpecctraDsnStreamReader) p_scanner).next_string(true, '-'))
        .isEmpty()) {

      try {
        p_scanner.yybegin(SpecctraDsnStreamReader.SPEC_CHAR);
        nextToken = p_scanner.next_token(); // overread the hyphen
      } catch (IOException e) {
        FRLogger.error("Network.read_net_pins: IO error while scanning file", e);
        return false;
      }

      pinName = p_scanner.next_string(true);
      Net.Pin currEntry = new Net.Pin(componentName, pinName);
      p_pin_list.add(currEntry);
    }

    try {
      nextToken = p_scanner.next_token();
    } catch (IOException e) {
      FRLogger.error("Network.read_net_pins: IO error scanning file", e);
      return false;
    }
    if (nextToken == null) {
      FRLogger.warn(
          "Network.read_net_pins: unexpected end of file at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return false;
    }
    if (nextToken != CLOSED_BRACKET) {
      // not end of scope
      FRLogger.warn(
          "Network.read_net_pins: expected closed bracket is missing at '"
              + p_scanner.get_scope_identifier()
              + "'");
    }

    return true;
  }

  public static ViaInfo read_via_info(IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = p_scanner.next_token();
      if (!(nextToken instanceof String name)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.next_token();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      p_scanner.set_scope_identifier(padstackName);
      Padstack viaPadstack = p_board.library.get_via_padstack(padstackName);
      if (viaPadstack == null) {
        // The padstack may not yet be inserted into the list of via padstacks
        viaPadstack = p_board.library.padstacks.get(padstackName);
        if (viaPadstack == null) {
          FRLogger.warn(
              "Network.read_via_info: padstack not found at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        p_board.library.add_via_padstack(viaPadstack);
      }
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_scanner.next_token();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Network.read_via_info: string expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      int clearanceClass = p_board.rules.clearanceMatrix.get_no((String) nextToken);
      if (clearanceClass < 0) {
        // Clearance class not stored, because it is identical to the default clearance class.
        clearanceClass = BoardRules.default_clearance_class();
      }
      boolean attachAllowed = false;
      nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.ATTACH) {
          FRLogger.warn(
              "Network.read_via_info: Keyword.ATTACH expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        attachAllowed = true;
        nextToken = p_scanner.next_token();
        if (nextToken != Keyword.CLOSED_BRACKET) {
          FRLogger.warn(
              "Network.read_via_info: closing bracket expected at '"
                  + p_scanner.get_scope_identifier()
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

  public static Collection<String> read_via_rule(IJFlexScanner p_scanner, BasicBoard p_board) {
    try {
      Collection<String> result = new LinkedList<>();
      for (; ; ) {
        p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
        Object nextToken = p_scanner.next_token();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Network.read_via_rule: string expected at '"
                  + p_scanner.get_scope_identifier()
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

  private static void insert_via_infos(
      Collection<ViaInfo> p_via_infos, RoutingBoard p_board, boolean p_attach_allowed) {
    if (!p_via_infos.isEmpty()) {
      for (ViaInfo currInfo : p_via_infos) {
        p_board.rules.viaInfos.add(currInfo);
      }
    } else // no via infos found, create default via infos from the via padstacks.
    {
      create_default_via_infos(p_board, p_board.rules.get_default_net_class(), p_attach_allowed);
    }
  }

  private static void create_default_via_infos(
      BasicBoard p_board, app.freerouting.rules.NetClass p_net_class, boolean p_attach_allowed) {
    int clClass =
        p_net_class.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    boolean isDefaultClass = p_net_class == p_board.rules.get_default_net_class();
    for (int i = 0; i < p_board.library.via_padstack_count(); i++) {
      Padstack currPadstack = p_board.library.get_via_padstack(i);
      boolean attachAllowed = p_attach_allowed && currPadstack.attachAllowed;
      String viaName;
      if (isDefaultClass) {
        viaName = currPadstack.name;
      } else {
        viaName = currPadstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_net_class.get_name();
      }
      ViaInfo foundViaInfo =
          new ViaInfo(viaName, currPadstack, clClass, attachAllowed, p_board.rules);
      p_board.rules.viaInfos.add(foundViaInfo);
    }
  }

  private static void insert_via_rules(
      Collection<Collection<String>> p_via_rules, BasicBoard p_board) {
    boolean ruleFound = false;
    for (Collection<String> currList : p_via_rules) {
      if (currList.size() < 2) {
        continue;
      }
      if (add_via_rule(currList, p_board)) {
        ruleFound = true;
      }
    }
    if (!ruleFound) {
      p_board.rules.create_default_via_rule(p_board.rules.get_default_net_class(), "default");
    }
    for (int i = 0; i < p_board.rules.netClasses.count(); i++) {
      p_board.rules.netClasses.get(i).set_via_rule(p_board.rules.get_default_via_rule());
    }
  }

  /** Inserts a via rule into the board. Replaces an already existing via rule with the same */
  public static boolean add_via_rule(Collection<String> p_name_list, BasicBoard p_board) {
    Iterator<String> it = p_name_list.iterator();
    String ruleName = it.next();
    ViaRule existingRule = p_board.rules.get_via_rule(ruleName);
    ViaRule currRule = new ViaRule(ruleName);
    boolean ruleOk = true;
    while (it.hasNext()) {
      ViaInfo currVia = p_board.rules.viaInfos.get(it.next());
      if (currVia != null) {
        currRule.append_via(currVia);
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

  private static void insert_net_classes(
      Collection<NetClass> p_net_classes, ReadScopeParameter p_par) {
    BasicBoard routingBoard = p_par.boardHandling.get_routing_board();
    for (NetClass currClass : p_net_classes) {
      insert_net_class(
          currClass,
          p_par.layerStructure,
          routingBoard,
          p_par.coordinateTransform,
          p_par.viaAtSmdAllowed);
    }
  }

  public static void insert_net_class(
      NetClass p_class,
      LayerStructure p_layer_structure,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform,
      boolean p_via_at_smd_allowed) {
    app.freerouting.rules.NetClass boardNetClass =
        KiCadNetClassNames.isKiCadDefaultNetClassName(p_class.name)
            ? p_board.rules.get_default_net_class()
            : p_board.rules.append_net_class(p_class.name);
    if (p_class.traceClearanceClass != null) {
      int traceClearanceClass = p_board.rules.clearanceMatrix.get_no(p_class.traceClearanceClass);
      if (traceClearanceClass >= 0) {
        boardNetClass.set_trace_clearance_class(traceClearanceClass);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: clearance class not found at '"
                + boardNetClass.get_name()
                + "'");
      }
    }
    if (p_class.viaRule != null) {
      ViaRule viaRule = p_board.rules.get_via_rule(p_class.viaRule);
      if (viaRule != null) {
        boardNetClass.set_via_rule(viaRule);
      } else {
        FRLogger.warn(
            "Network.insert_net_class: via rule not found at '" + boardNetClass.get_name() + "'");
      }
    }
    if (p_class.maxTraceLength > 0) {
      boardNetClass.set_maximum_trace_length(
          p_coordinate_transform.dsn_to_board(p_class.maxTraceLength));
    }
    if (p_class.minTraceLength > 0) {
      boardNetClass.set_minimum_trace_length(
          p_coordinate_transform.dsn_to_board(p_class.minTraceLength));
    }
    for (String currNetName : p_class.netList) {
      Collection<app.freerouting.rules.Net> currNetList = p_board.rules.nets.get(currNetName);
      for (app.freerouting.rules.Net currNet : currNetList) {
        currNet.set_class(boardNetClass);
      }
    }

    // read the trace width and clearance rules.

    boolean clearanceRuleFound = false;

    for (Rule currRule : p_class.rules) {
      if (currRule instanceof Rule.WidthRule rule1) {
        int traceHalfwidth = (int) Math.round(p_coordinate_transform.dsn_to_board(rule1.value / 2));
        boardNetClass.set_trace_half_width(traceHalfwidth);
      } else if (currRule instanceof Rule.ClearanceRule rule) {
        add_clearance_rule(
            p_board.rules.clearanceMatrix, boardNetClass, rule, -1, p_coordinate_transform);
        clearanceRuleFound = true;
      } else {
        FRLogger.warn(
            "Network.insert_net_class: rule type not yet implemented at '"
                + boardNetClass.get_name()
                + "'");
      }
    }

    // read the layer dependent rules.

    for (Rule.LayerRule curr_layer_rule : p_class.layerRules) {
      for (String curr_layer_name : curr_layer_rule.layerNames) {
        int layerNo = p_board.layerStructure.get_no(curr_layer_name);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_net_class: layer not found at '" + boardNetClass.get_name() + "'");
          continue;
        }
        for (Rule currRule : curr_layer_rule.rules) {
          if (currRule instanceof Rule.WidthRule rule1) {
            int traceHalfwidth =
                (int) Math.round(p_coordinate_transform.dsn_to_board(rule1.value / 2));
            boardNetClass.set_trace_half_width(layerNo, traceHalfwidth);
          } else if (currRule instanceof Rule.ClearanceRule rule) {
            add_clearance_rule(
                p_board.rules.clearanceMatrix,
                boardNetClass,
                rule,
                layerNo,
                p_coordinate_transform);
            clearanceRuleFound = true;
          } else {
            FRLogger.warn(
                "Network.insert_net_class: layer rule type not yet implemented at '"
                    + boardNetClass.get_name()
                    + "'");
          }
        }
      }
    }

    boardNetClass.set_pull_tight(p_class.pullTight);
    boardNetClass.set_shove_fixed(p_class.shoveFixed);
    boolean viaInfosCreated = false;

    if (clearanceRuleFound && boardNetClass != p_board.rules.get_default_net_class()) {
      create_default_via_infos(p_board, boardNetClass, p_via_at_smd_allowed);
      viaInfosCreated = true;
    }

    if (!p_class.useVia.isEmpty()) {
      create_via_rule(p_class.useVia, boardNetClass, p_board, p_via_at_smd_allowed);
    } else if (viaInfosCreated) {
      p_board.rules.create_default_via_rule(boardNetClass, boardNetClass.get_name());
    }
    if (!p_class.useLayer.isEmpty()) {
      create_active_trace_layers(p_class.useLayer, p_layer_structure, boardNetClass);
    }
  }

  private static void insert_class_pairs(
      Collection<NetClass.ClassClass> p_class_classes, ReadScopeParameter p_par) {
    for (NetClass.ClassClass currClassClass : p_class_classes) {
      Iterator<String> it1 = currClassClass.classNames.iterator();
      BasicBoard routingBoard = p_par.boardHandling.get_routing_board();
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
              insert_class_pair_info(
                  currClassClass, firstClass, secondClass, routingBoard, p_par.coordinateTransform);
            }
          }
        }
      }
    }
  }

  private static void insert_class_pair_info(
      NetClass.ClassClass p_class_class,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      BasicBoard p_board,
      CoordinateTransform p_coordinate_transform) {
    for (Rule currRule : p_class_class.rules) {
      if (currRule instanceof Rule.ClearanceRule curr_clearance_rule) {
        add_mixed_clearance_rule(
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
        int layerNo = p_board.layerStructure.get_no(curr_layer_name);
        if (layerNo < 0) {
          FRLogger.warn(
              "Network.insert_class_pair_info: layer not found at '" + curr_layer_name + "'");
          continue;
        }
        for (Rule currRule : curr_layer_rule.rules) {
          if (currRule instanceof Rule.ClearanceRule rule) {
            add_mixed_clearance_rule(
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

  private static void add_mixed_clearance_rule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_first_class,
      app.freerouting.rules.NetClass p_second_class,
      Rule.ClearanceRule p_clearance_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int currClearance =
        (int) Math.round(p_coordinate_transform.dsn_to_board(p_clearance_rule.value));
    final String firstClassName = p_first_class.get_name();
    int firstClassNo = p_clearance_matrix.get_no(firstClassName);
    if (firstClassNo < 0) {
      p_clearance_matrix.append_class(firstClassName);
      firstClassNo = p_clearance_matrix.get_no(firstClassName);
    }
    final String secondClassName = p_second_class.get_name();
    int secondClassNo = p_clearance_matrix.get_no(secondClassName);
    if (secondClassNo < 0) {
      p_clearance_matrix.append_class(secondClassName);
      secondClassNo = p_clearance_matrix.get_no(secondClassName);
    }
    if (p_clearance_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(firstClassNo, secondClassNo, currClearance);
        p_clearance_matrix.set_value(secondClassNo, firstClassNo, currClearance);
      } else {
        p_clearance_matrix.set_value(firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_clearance_matrix.set_value(secondClassNo, firstClassNo, p_layer_no, currClearance);
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
            currFirstClassNo = get_clearance_class(p_clearance_matrix, p_first_class, currPair[0]);
            currSecondClassNo =
                get_clearance_class(p_clearance_matrix, p_second_class, currPair[1]);
          } else {
            currFirstClassNo = get_clearance_class(p_clearance_matrix, p_second_class, currPair[0]);
            currSecondClassNo = get_clearance_class(p_clearance_matrix, p_first_class, currPair[1]);
          }
          if (p_layer_no < 0) {
            p_clearance_matrix.set_value(currFirstClassNo, currSecondClassNo, currClearance);
            p_clearance_matrix.set_value(currSecondClassNo, currFirstClassNo, currClearance);
          } else {
            p_clearance_matrix.set_value(
                currFirstClassNo, currSecondClassNo, p_layer_no, currClearance);
            p_clearance_matrix.set_value(
                currSecondClassNo, currFirstClassNo, p_layer_no, currClearance);
          }
        }
      }
    }
  }

  private static void create_default_clearance_classes(
      app.freerouting.rules.NetClass p_net_class, ClearanceMatrix p_clearance_matrix) {
    get_clearance_class(p_clearance_matrix, p_net_class, "via");
    get_clearance_class(p_clearance_matrix, p_net_class, "smd");
    get_clearance_class(p_clearance_matrix, p_net_class, "pin");
    get_clearance_class(p_clearance_matrix, p_net_class, "area");
  }

  private static void create_via_rule(
      Collection<String> p_use_via,
      app.freerouting.rules.NetClass p_net_class,
      BasicBoard p_board,
      boolean p_attach_allowed) {
    ViaRule newViaRule = new ViaRule(p_net_class.get_name());
    int defaultViaClClass =
        p_net_class.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
    for (String curr_via_name : p_use_via) {
      for (int i = 0; i < p_board.rules.viaInfos.count(); i++) {
        ViaInfo currViaInfo = p_board.rules.viaInfos.get(i);
        if (currViaInfo.get_clearance_class() == defaultViaClClass) {
          if (currViaInfo.get_padstack().name.equals(curr_via_name)) {
            newViaRule.append_via(currViaInfo);
          }
        }
      }
    }
    p_board.rules.viaRules.add(newViaRule);
    p_net_class.set_via_rule(newViaRule);
  }

  private static void create_active_trace_layers(
      Collection<String> p_use_layer,
      LayerStructure p_layer_structure,
      app.freerouting.rules.NetClass p_net_class) {
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      p_net_class.set_active_routing_layer(i, false);
    }
    for (String cur_layer_name : p_use_layer) {
      int currNo = p_layer_structure.get_no(cur_layer_name);
      p_net_class.set_active_routing_layer(currNo, true);
    }
    // currently all inactive layers have tracewidth 0.
    for (int i = 0; i < p_layer_structure.arr.length; i++) {
      if (!p_net_class.is_active_routing_layer(i)) {
        p_net_class.set_trace_half_width(i, 0);
      }
    }
  }

  private static void add_clearance_rule(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      Rule.ClearanceRule p_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform) {
    int currClearance = (int) Math.round(p_coordinate_transform.dsn_to_board(p_rule.value));
    final String className = p_net_class.get_name();
    int classNo = p_clearance_matrix.get_no(className);
    if (classNo < 0) {
      // class not yet existing, create a new class
      p_clearance_matrix.append_class(className);
      classNo = p_clearance_matrix.get_no(className);
      // set the clearance values of the new class to the maximum of currClearance and
      // the existing values.
      for (int i = 1; i < p_clearance_matrix.get_class_count(); i++) {
        for (int j = 0; j < p_clearance_matrix.get_layer_count(); j++) {
          int currValue =
              Math.max(p_clearance_matrix.get_value(classNo, i, j, false), currClearance);
          p_clearance_matrix.set_value(classNo, i, j, currValue);
          p_clearance_matrix.set_value(i, classNo, j, currValue);
        }
      }
      p_net_class.defaultItemClearanceClasses.set_all(classNo);
    }
    p_net_class.set_trace_clearance_class(classNo);
    if (p_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(classNo, classNo, currClearance);
      } else {
        p_clearance_matrix.set_value(classNo, classNo, p_layer_no, currClearance);
      }
      return;
    }
    if (Structure.contains_wire_clearance_pair(p_rule.clearanceClassPairs)) {
      create_default_clearance_classes(p_net_class, p_clearance_matrix);
    }
    for (String currString : p_rule.clearanceClassPairs) {
      String[] currPair = currString.split("_");
      if (currPair.length != 2) {
        continue;
      }

      int firstClassNo = get_clearance_class(p_clearance_matrix, p_net_class, currPair[0]);
      int secondClassNo = get_clearance_class(p_clearance_matrix, p_net_class, currPair[1]);

      if (p_layer_no < 0) {
        p_clearance_matrix.set_value(firstClassNo, secondClassNo, currClearance);
        p_clearance_matrix.set_value(secondClassNo, firstClassNo, currClearance);
      } else {
        p_clearance_matrix.set_value(firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_clearance_matrix.set_value(secondClassNo, firstClassNo, p_layer_no, currClearance);
      }
    }
  }

  /**
   * Gets the number of the clearance class with name combined of p_net_class_name and
   * p_item_class_name. Creates a new class, if that class is not yet existing.
   */
  private static int get_clearance_class(
      ClearanceMatrix p_clearance_matrix,
      app.freerouting.rules.NetClass p_net_class,
      String p_item_class_name) {
    String netClassName = p_net_class.get_name();
    String newClassName = netClassName;
    if (!"wire".equals(p_item_class_name)) {
      newClassName = newClassName + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_item_class_name;
    }
    int foundClassNo = p_clearance_matrix.get_no(newClassName);
    if (foundClassNo >= 0) {
      return foundClassNo;
    }
    p_clearance_matrix.append_class(newClassName);
    int result = p_clearance_matrix.get_no(newClassName);
    int netClassNo = p_clearance_matrix.get_no(netClassName);
    if (netClassNo < 0 || result < 0) {
      FRLogger.warn(
          "Network.get_clearance_class: clearance class not found at '" + netClassName + "'");
      return result;
    }
    // initialize the clearance values of p_new_class_name from p_net_class_name
    for (int i = 1; i < p_clearance_matrix.get_class_count(); i++) {

      for (int j = 0; j < p_clearance_matrix.get_layer_count(); j++) {
        int currValue = p_clearance_matrix.get_value(netClassNo, i, j, false);
        p_clearance_matrix.set_value(result, i, j, currValue);
        p_clearance_matrix.set_value(i, result, j, currValue);
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

  private static void insert_components(ReadScopeParameter p_par) {
    for (ComponentPlacement next_lib_component : p_par.placementList) {
      for (ComponentPlacement.ComponentLocation next_component : next_lib_component.locations) {
        insert_component(next_component, next_lib_component.libName, p_par);
      }
    }
  }

  /**
   * Create the part library on the board. Can be called after the components are inserted. Returns
   * false, if an error occurred.
   */
  private static boolean insert_logical_parts(ReadScopeParameter p_par) {
    BasicBoard routingBoard = p_par.boardHandling.get_routing_board();
    for (PartLibrary.LogicalPart nextPart : p_par.logicalParts) {
      Package libPackage =
          search_lib_package(nextPart.name, p_par.logicalPartMappings, routingBoard);
      if (libPackage == null) {
        return false;
      }
      LogicalPart.PartPin[] boardPartPins = new LogicalPart.PartPin[nextPart.partPins.size()];
      int currIndex = 0;
      for (PartLibrary.PartPin currPartPin : nextPart.partPins) {
        int pinNo = libPackage.get_pin_no(currPartPin.pinName);
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
          currComponent.set_logical_part(currLogicalPart);
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
  private static Package search_lib_package(
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
        return currComponent.get_package();
      }
    }
    FRLogger.warn("Network.search_lib_package: library package '" + p_part_name + "' not found");
    return null;
  }

  /** Inserts all board components belonging to the input library component. */
  private static void insert_component(
      ComponentPlacement.ComponentLocation p_location, String p_lib_key, ReadScopeParameter p_par) {
    RoutingBoard routingBoard = p_par.boardHandling.get_routing_board();
    Package currFrontPackage = routingBoard.library.packages.get(p_lib_key, true);
    Package currBackPackage = routingBoard.library.packages.get(p_lib_key, false);
    if (currFrontPackage == null || currBackPackage == null) {
      FRLogger.warn(
          "Network.insert_component: component package not found at '"
              + p_par.scanner.get_scope_identifier()
              + "'");
      return;
    }

    IntPoint componentLocation;
    if (p_location.coor != null) {
      componentLocation = p_par.coordinateTransform.dsn_to_board(p_location.coor).round();
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
    Vector componentTranslation = componentLocation.difference_by(Point.ZERO);
    FixedState fixedState;
    if (p_location.positionFixed) {
      fixedState = FixedState.SYSTEM_FIXED;
    } else {
      fixedState = FixedState.UNFIXED;
    }
    Package currPackage = newComponent.get_package();
    for (int i = 0; i < currPackage.pin_count(); i++) {
      Package.Pin currPin = currPackage.get_pin(i);
      Padstack currPadstack = routingBoard.library.padstacks.get(currPin.padstackNo);
      if (currPadstack == null) {
        FRLogger.warn(
            "Network.insert_component: pin padstack not found at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return;
      }
      Collection<Net> pinNets = p_par.netlist.get_nets(p_location.name, currPin.name);
      Collection<Integer> netNumbers = new LinkedList<>();
      for (Net curr_pin_net : pinNets) {
        app.freerouting.rules.Net currBoardNet =
            routingBoard.rules.nets.get(curr_pin_net.id.name, curr_pin_net.id.subnetNumber);
        if (currBoardNet == null) {
          FRLogger.warn(
              "Network.insert_component: board net not found at '"
                  + p_par.scanner.get_scope_identifier()
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
        netClass = boardNet.get_class();
      } else {
        netClass = routingBoard.rules.get_default_net_class();
      }
      int clearanceClass = -1;
      ComponentPlacement.ItemClearanceInfo pinInfo = p_location.pin_infos.get(currPin.name);
      if (pinInfo != null) {
        clearanceClass = routingBoard.rules.clearanceMatrix.get_no(pinInfo.clearanceClass);
      }
      if (clearanceClass < 0) {
        if (currPadstack.from_layer() == currPadstack.to_layer()) {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.SMD);
        } else {
          clearanceClass =
              netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.PIN);
        }
      }
      routingBoard.insert_pin(newComponent.no, i, netNoArr, clearanceClass, fixedState);
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
        if (layer >= routingBoard.get_layer_count()) {
          FRLogger.warn(
              "Network.insert_component: keepout layer is to big at '"
                  + p_par.scanner.get_scope_identifier()
                  + "'");
          continue;
        }
        if (layer >= 0 && !p_location.isFront) {
          layer = routingBoard.get_layer_count() - currKeepout.layer - 1;
        }
        int clearanceClass =
            routingBoard
                .rules
                .get_default_net_class()
                .defaultItemClearanceClasses
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
        ComponentPlacement.ItemClearanceInfo keepoutInfo = curr_keepout_infos.get(currKeepout.name);
        if (keepoutInfo != null) {
          int currClearanceClass =
              routingBoard.rules.clearanceMatrix.get_no(keepoutInfo.clearanceClass);
          if (currClearanceClass > 0) {
            clearanceClass = currClearanceClass;
          }
        }
        if (layer >= 0) {
          if (k == 0) {
            routingBoard.insert_obstacle(
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
            routingBoard.insert_via_obstacle(
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
            routingBoard.insert_component_obstacle(
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
                routingBoard.insert_obstacle(
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
                routingBoard.insert_via_obstacle(
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
                routingBoard.insert_component_obstacle(
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
        if (currPackage.outline[i] != null && currPackage.outline[i].bounding_box() != null) {
          double area = currPackage.outline[i].bounding_box().area();
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
        routingBoard.insert_component_outline(
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
  public boolean read_scope(ReadScopeParameter p_par) {
    Collection<NetClass> classes = new LinkedList<>();
    Collection<NetClass.ClassClass> classClassList = new LinkedList<>();
    Collection<ViaInfo> viaInfos = new LinkedList<>();
    Collection<Collection<String>> viaRules = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Network.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Network.read_scope: unexpected end of file at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.NET) {
          read_net_scope(
              p_par.scanner,
              p_par.netlist,
              p_par.boardHandling.get_routing_board(),
              p_par.coordinateTransform,
              p_par.layerStructure);
        } else if (nextToken == Keyword.VIA) {
          ViaInfo currViaInfo =
              read_via_info(p_par.scanner, p_par.boardHandling.get_routing_board());
          if (currViaInfo == null) {
            return false;
          }
          viaInfos.add(currViaInfo);
        } else if (nextToken == Keyword.VIA_RULE) {
          Collection<String> currViaRule =
              read_via_rule(p_par.scanner, p_par.boardHandling.get_routing_board());
          if (currViaRule == null) {
            return false;
          }
          viaRules.add(currViaRule);
        } else if (nextToken == Keyword.CLASS) {
          NetClass currClass = NetClass.read_scope(p_par.scanner);
          if (currClass == null) {
            return false;
          }
          classes.add(currClass);
        } else if (nextToken == Keyword.CLASS_CLASS) {
          NetClass.ClassClass currClassClass = NetClass.read_class_class_scope(p_par.scanner);
          if (currClassClass == null) {
            return false;
          }
          classClassList.add(currClassClass);
        } else {
          skip_scope(p_par.scanner);
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

    RoutingBoard board = p_par.boardHandling.get_routing_board();

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
                  + p_par.scanner.get_scope_identifier()
                  + "'");
        }
      }
      if (foundPadstackCount != viaPadstacks.length) {
        // Some via padstacks were not found in the padstacks scope of the dsn-file.
        Padstack[] correctedPadstacks = new Padstack[foundPadstackCount];
        System.arraycopy(viaPadstacks, 0, correctedPadstacks, 0, foundPadstackCount);
        viaPadstacks = correctedPadstacks;
      }
      board.library.set_via_padstacks(viaPadstacks);
    }

    insert_via_infos(viaInfos, p_par.boardHandling.get_routing_board(), p_par.viaAtSmdAllowed);
    insert_via_rules(viaRules, p_par.boardHandling.get_routing_board());
    insert_net_classes(classes, p_par);
    insert_class_pairs(classClassList, p_par);
    insert_components(p_par);
    insert_logical_parts(p_par);
    return true;
  }

  private boolean read_net_scope(
      IJFlexScanner p_scanner,
      NetList p_net_list,
      RoutingBoard p_board,
      CoordinateTransform p_coordinate_transform,
      LayerStructure p_layer_structure) {
    // read the net name
    String netName = p_scanner.next_string();

    Object nextToken;
    int subnetNumber = 1;
    try {
      nextToken = p_scanner.next_token();
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
          nextToken = p_scanner.next_token();
        } catch (IOException e) {
          FRLogger.error("Network.read_net_scope: IO error scanning file", e);
          return false;
        }
        if (nextToken == null) {
          FRLogger.warn(
              "Network.read_net_scope: unexpected end of file at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == Keyword.PINS) {
            if (!read_net_pins(p_scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.ORDER) {
            pinOrderFound = true;
            if (!read_net_pins(p_scanner, pinList)) {
              return false;
            }
          } else if (nextToken == Keyword.FROMTO) {
            Set<Net.Pin> currSubnetPinList = new TreeSet<>();
            if (!read_net_pins(p_scanner, currSubnetPinList)) {
              return false;
            }
            subnetPinLists.add(currSubnetPinList);
          } else if (nextToken == Keyword.RULE) {
            netRules.addAll(Rule.read_scope(p_scanner));
          } else if (nextToken == Keyword.LAYER_RULE) {
            FRLogger.warn(
                "Network.read_net_scope: layer_rule not yet implemented at '"
                    + p_scanner.get_scope_identifier()
                    + "'");
            skip_scope(p_scanner);
          } else {
            skip_scope(p_scanner);
          }
        }
      }
    }
    if (subnetPinLists.isEmpty()) {
      if (pinOrderFound) {
        subnetPinLists = create_ordered_subnets(pinList);
      } else {
        subnetPinLists.add(pinList);
      }
    }
    for (Collection<Net.Pin> currPinList : subnetPinLists) {
      Net.Id netId = new Net.Id(netName, subnetNumber);
      if (!p_net_list.contains(netId)) {
        Net newNet = p_net_list.add_net(netId);
        boolean containsPlane = p_layer_structure.contains_plane(netName);
        if (newNet != null) {
          p_board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, containsPlane);
        }
      }
      Net currSubnet = p_net_list.get_net(netId);
      if (currSubnet == null) {
        FRLogger.warn(
            "Network.read_net_scope: net not found in netlist at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return false;
      }
      currSubnet.set_pins(currPinList);
      if (!netRules.isEmpty()) {
        // Evaluate the net rules.
        app.freerouting.rules.Net boardNet =
            p_board.rules.nets.get(currSubnet.id.name, currSubnet.id.subnetNumber);
        if (boardNet == null) {
          FRLogger.warn(
              "Network.read_net_scope: board net not found at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        for (Rule currOb : netRules) {
          if (currOb instanceof Rule.WidthRule rule) {
            app.freerouting.rules.NetClass defaultNetRule = p_board.rules.get_default_net_class();
            double wireWidth = rule.value;
            int traceHalfwidth =
                (int) Math.round(p_coordinate_transform.dsn_to_board(wireWidth) / 2);
            app.freerouting.rules.NetClass netRule =
                p_board.rules.netClasses.find(
                    traceHalfwidth,
                    defaultNetRule.get_trace_clearance_class(),
                    defaultNetRule.get_via_rule());
            if (netRule == null) {
              // create a new net rule
              netRule = p_board.rules.get_new_net_class();
            }
            netRule.set_trace_half_width(traceHalfwidth);
            boardNet.set_class(netRule);
          } else {
            FRLogger.warn(
                "Network.read_net_scope: Rule not yet implemented at '"
                    + p_scanner.get_scope_identifier()
                    + "'");
          }
        }
      }
      ++subnetNumber;
    }
    return true;
  }
}
