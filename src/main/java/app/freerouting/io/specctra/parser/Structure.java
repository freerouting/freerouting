package app.freerouting.io.specctra.parser;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.Communication;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.ViaObstacleArea;
import app.freerouting.core.BoardLibrary;
import app.freerouting.core.Padstack;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.datastructures.UndoableObjects.Storable;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass;
import app.freerouting.rules.NetClass;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** Class for reading and writing structure scopes from dsn-files. */
public class Structure extends ScopeKeyword {

  /** Creates a new instance of Structure */
  public Structure() {
    super("structure");
  }

  public static void write_scope(WriteScopeParameter p_par) throws IOException {
    p_par.file.start_scope();
    p_par.file.write("structure");

    // write the layer structure
    write_layers(p_par);

    // write the boundaries
    write_boundaries(p_par);

    // write the routing vias
    write_via_padstacks(p_par.board.library, p_par.file, p_par.identifierType);

    // write the rules
    write_default_rules(p_par);

    // write the snap angles
    write_snap_angle(p_par.file, p_par.board.rules.get_trace_angle_restriction());

    // write the control scope
    write_control_scope(p_par.board.rules, p_par.file);

    if (p_par.autorouteSettings != null) {
      // write the auto-route settings
      AutorouteSettings.write_scope(
          p_par.file, p_par.autorouteSettings, p_par.board.layerStructure, p_par.identifierType);
    }

    // write the conduction areas
    write_conduction_areas(p_par);

    // write the keepouts
    write_keepouts(p_par);

    p_par.file.end_scope();
  }

  private static void write_conduction_areas(WriteScopeParameter p_par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.start_read_object();
    for (; ; ) {
      currOb = p_par.board.itemList.read_object(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (p_par.board.layerStructure.arr[currArea.get_layer()].isSignal) {
        // These conduction areas are written in the wiring scope.
        continue;
      }
      Plane.write_scope(p_par, currArea);
    }
  }

  private static void write_keepouts(WriteScopeParameter p_par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.start_read_object();
    for (; ; ) {
      currOb = p_par.board.itemList.read_object(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ObstacleArea currKeepout)) {
        continue;
      }
      if (currKeepout.get_component_no() != 0) {
        // keepouts belonging to a component are not written individually.
        continue;
      }
      if (currKeepout instanceof ConductionArea) {
        // conduction area will be written later.
        continue;
      }
      write_keepout_scope(p_par, currKeepout);
    }
  }

  private static void write_boundaries(WriteScopeParameter p_par) throws IOException {
    // write the bounding box
    p_par.file.start_scope();
    p_par.file.write("boundary");
    IntBox bounds = p_par.board.get_bounding_box();
    double[] rectCoor = p_par.coordinateTransform.board_to_dsn(bounds);
    Rectangle boundingRectangle = new Rectangle(Layer.PCB, rectCoor);
    boundingRectangle.write_scope(p_par.file, p_par.identifierType);
    p_par.file.end_scope();
    // lookup the outline in the board
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.start_read_object();
    for (; ; ) {
      currOb = p_par.board.itemList.read_object(it);
      if (currOb == null) {
        break;
      }
      if (currOb instanceof BoardOutline) {
        break;
      }
    }
    if (currOb == null) {
      FRLogger.warn("Structure.write_scope: board outline not found");
      return;
    }
    BoardOutline outline = (BoardOutline) currOb;

    // write the outline
    for (int i = 0; i < outline.shape_count(); i++) {
      Shape outlineShape =
          p_par.coordinateTransform.board_to_dsn(outline.get_shape(i), Layer.SIGNAL);
      p_par.file.start_scope();
      p_par.file.write("boundary");
      outlineShape.write_scope(p_par.file, p_par.identifierType);
      p_par.file.end_scope();
    }
  }

  static void write_layers(WriteScopeParameter p_par) throws IOException {
    for (int i = 0; i < p_par.board.layerStructure.arr.length; i++) {
      boolean writeLayerRule =
          p_par.board.rules.get_default_net_class().get_trace_half_width(i)
                  != p_par.board.rules.get_default_net_class().get_trace_half_width(0)
              || !clearance_equals(p_par.board.rules.clearanceMatrix, i, 0);
      Layer.write_scope(p_par, i, writeLayerRule);
    }
  }

  static void write_default_rules(WriteScopeParameter p_par) throws IOException {
    // write the default rule using 0 as default layer.
    Rule.write_default_rule(p_par, 0);
  }

  private static void write_via_padstacks(
      BoardLibrary p_library, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.new_line();
    p_file.write("(via");
    for (int i = 0; i < p_library.via_padstack_count(); i++) {
      Padstack currPadstack = p_library.get_via_padstack(i);
      if (currPadstack != null) {
        p_file.write(" ");
        p_identifier_type.write(currPadstack.name, p_file);
      } else {
        FRLogger.warn("Structure.write_via_padstacks: padstack is null");
      }
    }
    p_file.write(")");
  }

  private static void write_control_scope(BoardRules p_rules, IndentFileWriter p_file)
      throws IOException {
    p_file.start_scope();
    p_file.write("control");
    p_file.new_line();
    p_file.write("(via_at_smd ");
    boolean viaAtSmdAllowed = false;
    for (int i = 0; i < p_rules.viaInfos.count(); i++) {
      if (p_rules.viaInfos.get(i).attach_smd_allowed()) {
        viaAtSmdAllowed = true;
        break;
      }
    }
    if (viaAtSmdAllowed) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.end_scope();
  }

  private static void write_keepout_scope(WriteScopeParameter p_par, ObstacleArea p_keepout)
      throws IOException {
    Area keepoutArea = p_keepout.get_area();
    int layerNo = p_keepout.get_layer();
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[layerNo];
    Layer keepoutLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (keepoutArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = keepoutArea.get_border();
      holes = keepoutArea.get_holes();
    }
    p_par.file.start_scope();
    if (p_keepout instanceof ViaObstacleArea) {
      p_par.file.write("via_keepout");
    } else {
      p_par.file.write("keepout");
    }
    Shape dsnShape = p_par.coordinateTransform.board_to_dsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.write_scope(p_par.file, p_par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = p_par.coordinateTransform.board_to_dsn(holes[i], keepoutLayer);
      dsnHole.write_hole_scope(p_par.file, p_par.identifierType);
    }
    // write clearance class if it's defined for this keepout area.
    if (p_keepout.clearance_class_no() > 0) {
      // skip it if it's the default clearance class.
      String clearanceName =
          p_par.board.rules.clearanceMatrix.get_name(p_keepout.clearance_class_no());

      if (!"default".equals(clearanceName)) {
        Rule.write_item_clearance_class(clearanceName, p_par.file, p_par.identifierType);
      }
    }
    p_par.file.end_scope();
  }

  private static boolean read_boundary_scope(
      IJFlexScanner p_scanner, BoardConstructionInfo p_board_construction_info) {
    Shape currShape = Shape.read_scope(p_scanner, null);
    try {
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = p_scanner.next_token();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLEARANCE_CLASS) {
            p_board_construction_info.outlineClearanceClassName =
                DsnFile.read_string_scope(p_scanner);
          } else {
            Shape additionalShape = Shape.read_scope_from_keyword(p_scanner, nextToken, null);
            add_boundary_shape(p_board_construction_info, additionalShape);
          }
        }
        prevToken = nextToken;
      }
    } catch (IOException e) {
      FRLogger.error("Structure.read_boundary_scope: IO error scanning file", e);
      return false;
    }
    if (currShape == null) {
      FRLogger.warn(
          "Structure.read_boundary_scope: shape is null at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return true;
    }
    add_boundary_shape(p_board_construction_info, currShape);
    return true;
  }

  private static void add_boundary_shape(
      BoardConstructionInfo p_board_construction_info, Shape shape) {
    if (shape == null) {
      return;
    }
    if (shape instanceof PolylinePath || shape instanceof PolygonPath) {
      p_board_construction_info.outlineShapes.add(shape);
      return;
    }
    if (shape.layer == Layer.PCB) {
      if (p_board_construction_info.boundingShape == null) {
        p_board_construction_info.boundingShape = shape;
      } else {
        p_board_construction_info.outlineShapes.add(shape);
      }
    } else if (shape.layer == Layer.SIGNAL) {
      p_board_construction_info.outlineShapes.add(shape);
    } else {
      FRLogger.warn("Structure.add_boundary_shape: unexpected layer at boundary");
    }
  }

  static boolean read_layer_scope(
      IJFlexScanner p_scanner,
      BoardConstructionInfo p_board_construction_info,
      String p_string_quote) {
    try {
      boolean layerOk = true;
      boolean isSignal = true;

      String layerString = p_scanner.next_string();

      Collection<String> netNames = new LinkedList<>();
      Object nextToken = p_scanner.next_token();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Structure.read_layer_scope: ( expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        nextToken = p_scanner.next_token();
        if (nextToken == Keyword.TYPE) {
          nextToken = p_scanner.next_token();
          if (nextToken == Keyword.POWER) {
            isSignal = false;
          } else if ((nextToken != Keyword.SIGNAL)
              && (!Objects.equals(nextToken.toString(), Keyword.JUMPER.get_name()))) {
            if (nextToken instanceof String) {
              FRLogger.error(
                  "Structure.read_layer_scope: the layer '"
                      + layerString
                      + "' has an unknown layer type '"
                      + nextToken
                      + "'",
                  null);
            } else {
              FRLogger.warn(
                  "Structure.read_layer_scope: the layer '"
                      + layerString
                      + "' has an unknown layer type at '"
                      + p_scanner.get_scope_identifier()
                      + "'");
            }
            layerOk = false;
          }
          nextToken = p_scanner.next_token();
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Structure.read_layer_scope: ) expected at '"
                    + p_scanner.get_scope_identifier()
                    + "'");
            return false;
          }
        } else if (nextToken == Keyword.RULE) {
          Collection<Rule> currRules = Rule.read_scope(p_scanner);
          p_board_construction_info.layerDependentRules.add(new LayerRule(layerString, currRules));
        } else if (nextToken == Keyword.USE_NET) {
          for (; ; ) {
            p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
            nextToken = p_scanner.next_token();
            if (nextToken == Keyword.CLOSED_BRACKET) {
              break;
            }
            if (nextToken instanceof String string) {
              netNames.add(string);
            } else {
              FRLogger.warn(
                  "Structure.read_layer_scope: string expected at '"
                      + p_scanner.get_scope_identifier()
                      + "'");
            }
          }
        } else {
          skip_scope(p_scanner);
        }
        nextToken = p_scanner.next_token();
      }
      if (layerOk) {
        Layer currLayer =
            new Layer(layerString, p_board_construction_info.foundLayerCount, isSignal, netNames);
        p_board_construction_info.layerInfo.add(currLayer);
        ++p_board_construction_info.foundLayerCount;
      }
    } catch (IOException e) {
      FRLogger.error("Layer.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }

  static Collection<String> read_via_padstacks(IJFlexScanner p_scanner) {
    try {
      Collection<String> normalVias = new LinkedList<>();
      Collection<String> spareVias = new LinkedList<>();
      for (; ; ) {
        Object nextToken = p_scanner.next_token();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          nextToken = p_scanner.next_token();
          if (nextToken == Keyword.SPARE) {
            spareVias = read_via_padstacks(p_scanner);
          } else {
            skip_scope(p_scanner);
          }
        } else if (nextToken instanceof String string) {
          normalVias.add(string);
        } else {
          FRLogger.warn(
              "Structure.read_via_padstack: String expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }
      // add the spare vias to the end of the list
      normalVias.addAll(spareVias);
      return normalVias;
    } catch (IOException e) {
      FRLogger.error("Structure.read_via_padstack: IO error scanning file", e);
      return null;
    }
  }

  private static boolean read_control_scope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Structure.read_control_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_control_scope: unexpected end of file at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.VIA_AT_SMD) {
          p_par.viaAtSmdAllowed = DsnFile.read_on_off_scope(p_par.scanner);
        } else {
          skip_scope(p_par.scanner);
        }
      }
    }
    return true;
  }

  public static AngleRestriction read_snap_angle(IJFlexScanner p_scanner) {
    try {
      Object nextToken = p_scanner.next_token();
      AngleRestriction snapAngle;
      if (nextToken == Keyword.NINETY_DEGREE) {
        snapAngle = AngleRestriction.NINETY_DEGREE;
      } else if (nextToken == Keyword.FORTYFIVE_DEGREE) {
        snapAngle = AngleRestriction.FORTYFIVE_DEGREE;
      } else if (nextToken == Keyword.NONE) {
        snapAngle = AngleRestriction.NONE;
      } else {
        FRLogger.warn(
            "Structure.read_snap_angle_scope: unexpected token at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Structure.read_selection_layer_scop: closing bracket expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      return snapAngle;
    } catch (IOException e) {
      FRLogger.error("Structure.read_snap_angle: IO error scanning file", e);
      return null;
    }
  }

  public static void write_snap_angle(IndentFileWriter p_file, AngleRestriction p_angle_restriction)
      throws IOException {
    p_file.start_scope();
    p_file.write("snapAngle ");
    p_file.new_line();

    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE) {
      p_file.write("ninety_degree");
    } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      p_file.write("fortyfive_degree");
    } else {
      p_file.write("none");
    }
    p_file.end_scope();
  }

  private static void insert_missing_power_planes(
      Collection<Layer> p_layer_info, NetList p_netlist, BasicBoard p_board) {
    Collection<ConductionArea> conductionAreas = p_board.get_conduction_areas();
    for (Layer currLayer : p_layer_info) {
      if (currLayer.isSignal) {
        continue;
      }
      boolean conductionAreaFound = false;
      for (ConductionArea curr_conduction_area : conductionAreas) {
        if (curr_conduction_area.get_layer() == currLayer.no) {
          conductionAreaFound = true;
          break;
        }
      }
      if (!conductionAreaFound && !currLayer.netNames.isEmpty()) {
        String currNetName = currLayer.netNames.iterator().next();
        Net.Id currNetId = new Net.Id(currNetName, 1);
        if (!p_netlist.contains(currNetId)) {
          Net newNet = p_netlist.add_net(currNetId);
          if (newNet != null) {
            p_board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
          }
        }
        app.freerouting.rules.Net currNet =
            p_board.rules.nets.get(currNetId.name, currNetId.subnetNumber);
        {
          if (currNet == null) {
            FRLogger.warn(
                "Structure.insert_missing_power_planes: net not found at '" + currNetId.name + "'");
            continue;
          }
        }
        int[] netNumbers = new int[1];
        netNumbers[0] = currNet.netNumber;
        p_board.insert_conduction_area(
            p_board.boundingBox,
            currLayer.no,
            netNumbers,
            BoardRules.clearance_class_none(),
            false,
            FixedState.SYSTEM_FIXED);
      }
    }
  }

  /**
   * Calculates shapes in p_outline_shapes, which are holes in the outline and returns them in the
   * result list.
   */
  private static Collection<PolylineShape> separate_holes(
      Collection<PolylineShape> p_outline_shapes) {
    OutlineShape[] shapeArr = new OutlineShape[p_outline_shapes.size()];
    Iterator<PolylineShape> it = p_outline_shapes.iterator();
    for (int i = 0; i < shapeArr.length; i++) {
      shapeArr[i] = new OutlineShape(it.next());
    }
    for (int i = 0; i < shapeArr.length; i++) {
      OutlineShape currShape = shapeArr[i];
      for (int j = 0; j < shapeArr.length; j++) {
        // check if shapeArr[j] may be contained in shapeArr[i]
        OutlineShape otherShape = shapeArr[j];
        if (i == j || otherShape.isHole) {
          continue;
        }
        if (!otherShape.boundingBox.contains(currShape.boundingBox)) {
          continue;
        }
        currShape.isHole = otherShape.contains_all_corners(currShape);
      }
    }
    Collection<PolylineShape> holeList = new LinkedList<>();
    for (int i = 0; i < shapeArr.length; i++) {
      if (shapeArr[i].isHole) {
        p_outline_shapes.remove(shapeArr[i].shape);
        holeList.add(shapeArr[i].shape);
      }
    }
    return holeList;
  }

  // Check, if a conduction area is inserted on each plane,
  // and insert evtl. a conduction area

  /** Updates the board rules from the rules read from the dsn file. */
  private static void update_board_rules(
      ReadScopeParameter p_par,
      BoardConstructionInfo p_board_construction_info,
      BoardRules p_board_rules) {
    boolean smdToTurnGapFound = false;
    // update the clearance matrix
    for (Rule currOb : p_board_construction_info.defaultRules) {
      if (currOb instanceof Rule.ClearanceRule currRule) {
        if (set_clearance_rule(
            currRule, -1, p_par.coordinateTransform, p_board_rules, p_par.stringQuote)) {
          smdToTurnGapFound = true;
        }
      }
    }
    // update width rules
    for (Object currOb : p_board_construction_info.defaultRules) {
      if (currOb instanceof Rule.WidthRule rule) {
        double wireWidth = rule.value;
        int traceHalfwidth =
            (int) Math.round(p_par.coordinateTransform.dsn_to_board(wireWidth) / 2);
        FRLogger.debug(
            "Set default trace width (all layers): DSN="
                + wireWidth
                + " → board="
                + (traceHalfwidth * 2)
                + " ("
                + (traceHalfwidth * 2 / 40000.0)
                + " mm)");
        p_board_rules.set_default_trace_half_widths(traceHalfwidth);
      }
    }
    for (LayerRule layer_rule : p_board_construction_info.layerDependentRules) {
      int layerNo = p_par.layerStructure.get_no(layer_rule.layerName);
      if (layerNo < 0) {
        continue;
      }
      for (Rule currOb : layer_rule.rule) {
        if (currOb instanceof Rule.WidthRule rule) {
          double wireWidth = rule.value;
          int traceHalfwidth =
              (int) Math.round(p_par.coordinateTransform.dsn_to_board(wireWidth) / 2);
          p_board_rules.set_default_trace_half_width(layerNo, traceHalfwidth);
        } else if (currOb instanceof Rule.ClearanceRule currRule) {
          set_clearance_rule(
              currRule, layerNo, p_par.coordinateTransform, p_board_rules, p_par.stringQuote);
        }
      }
    }
    if (!smdToTurnGapFound) {
      p_board_rules.set_pin_edge_to_turn_dist(p_board_rules.get_min_trace_half_width());
    }
  }

  /**
   * Converts a dsn clearance rule into a board clearance rule. If p_layer_no is negative, the rule
   * is set on all layers. Returns true, if the string smd_to_turn_gap was found.
   */
  public static boolean set_clearance_rule(
      Rule.ClearanceRule p_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform,
      BoardRules p_board_rules,
      String p_string_quote) {
    boolean result = false;
    int currClearance = (int) Math.round(p_coordinate_transform.dsn_to_board(p_rule.value));
    if (p_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_board_rules.clearanceMatrix.set_default_value(currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (all layers): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + p_rule.value);
      } else {
        p_board_rules.clearanceMatrix.set_default_value(p_layer_no, currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (layer "
                + p_layer_no
                + "): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + p_rule.value);
      }
      return result;
    }
    if (contains_wire_clearance_pair(p_rule.clearanceClassPairs)) {
      create_default_clearance_classes(p_board_rules);
    }

    for (String currString : p_rule.clearanceClassPairs) {
      if ("smd_to_turn_gap".equalsIgnoreCase(currString)) {
        p_board_rules.set_pin_edge_to_turn_dist(currClearance);
        result = true;
        continue;
      }
      String[] currPair = new String[2];
      if (p_rule.clearanceClassPairs.size() == 2) {
        Iterator<String> iterator = p_rule.clearanceClassPairs.iterator();
        currPair[0] = iterator.next();
        currPair[1] = iterator.next();
        for (int i = 0; i < currPair.length; i++) {
          currPair[i] = currPair[i].replaceAll("[\"]", "");
          if (currPair[1].startsWith("_")) {
            currPair[1] = currPair[1].substring(1);
          }
        }
      } else if (currString.startsWith(p_string_quote)) {
        // split at the second occurrence of p_string_quote
        currString = currString.substring(p_string_quote.length());
        currPair = currString.split(p_string_quote, 2);
        if (currPair.length != 2 || !currPair[1].startsWith("_")) {
          FRLogger.warn("Structure.set_clearance_rule: '_' expected at '" + currString + "'");
          FRLogger.warn(
              "You probably get this error because your clearance rule name has spaces or special characters in its name. Please change them first, and try again.");
          continue;
        }
        currPair[1] = currPair[1].substring(1);
      } else {
        currPair = currString.split("_", 2);
        if (currPair.length != 2) {
          // pairs with more than 1 underline like smd_via_same_net are not implemented
          continue;
        }
      }

      int firstClassNo;
      if ("wire".equals(currPair[0])) {
        firstClassNo = 1; // default class
      } else {
        firstClassNo = p_board_rules.clearanceMatrix.get_no(currPair[0]);
      }
      if (firstClassNo < 0) {
        firstClassNo = append_clearance_class(p_board_rules, currPair[0]);
      }
      int secondClassNo;
      if ("wire".equals(currPair[1])) {
        secondClassNo = 1; // default class
      } else {
        secondClassNo = p_board_rules.clearanceMatrix.get_no(currPair[1]);
      }
      if (secondClassNo < 0) {
        secondClassNo = append_clearance_class(p_board_rules, currPair[1]);
      }
      if (p_layer_no < 0) {
        p_board_rules.clearanceMatrix.set_value(firstClassNo, secondClassNo, currClearance);
        p_board_rules.clearanceMatrix.set_value(secondClassNo, firstClassNo, currClearance);
        FRLogger.debug(
            "Set clearance (all layers): "
                + currPair[0]
                + "_"
                + currPair[1]
                + " = "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm), classes ["
                + firstClassNo
                + ","
                + secondClassNo
                + "]");
      } else {
        p_board_rules.clearanceMatrix.set_value(
            firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_board_rules.clearanceMatrix.set_value(
            secondClassNo, firstClassNo, p_layer_no, currClearance);
        FRLogger.debug(
            "Set clearance (layer "
                + p_layer_no
                + "): "
                + currPair[0]
                + "_"
                + currPair[1]
                + " = "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm), classes ["
                + firstClassNo
                + ","
                + secondClassNo
                + "]");
      }
    }
    return result;
  }

  static boolean contains_wire_clearance_pair(Collection<String> p_clearance_pairs) {
    for (String currPair : p_clearance_pairs) {
      if (currPair.startsWith("wire_") || currPair.endsWith("_wire")) {
        return true;
      }
    }
    return false;
  }

  private static void create_default_clearance_classes(BoardRules p_board_rules) {
    append_clearance_class(p_board_rules, "via");
    append_clearance_class(p_board_rules, "smd");
    append_clearance_class(p_board_rules, "pin");
    append_clearance_class(p_board_rules, "area");
  }

  private static int append_clearance_class(BoardRules p_board_rules, String p_name) {
    p_board_rules.clearanceMatrix.append_class(p_name);
    int result = p_board_rules.clearanceMatrix.get_no(p_name);
    NetClass defaultNetClass = p_board_rules.get_default_net_class();
    switch (p_name) {
      case "via" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
    }
    return result;
  }

  /** Returns true, if all clearance values on the 2 input layers are equal. */
  private static boolean clearance_equals(
      ClearanceMatrix p_cl_matrix, int p_layer_1, int p_layer_2) {
    if (p_layer_1 == p_layer_2) {
      return true;
    }
    for (int i = 1; i < p_cl_matrix.get_class_count(); i++) {
      for (int j = i; j < p_cl_matrix.get_class_count(); j++) {
        if (p_cl_matrix.get_value(i, j, p_layer_1, false)
            != p_cl_matrix.get_value(i, j, p_layer_2, false)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean insert_keepout(
      Shape.ReadAreaScopeResult p_area,
      ReadScopeParameter p_par,
      KeepoutType p_keepout_type,
      FixedState p_fixed_state) {
    Area keepoutArea = Shape.transform_area_to_board(p_area.shapeList, p_par.coordinateTransform);
    if (keepoutArea.dimension() < 2) {
      // A degenerate keepout (e.g. all polygon vertices identical, exported incorrectly by the EDA
      // tool) cannot be enforced as a routing constraint. The board remains valid — the keepout
      // restriction is simply not applied, making routing more permissive in that area.
      // This is a known export defect in some EDA tools (e.g. KiCad 4.0.7).
      FRLogger.warn(
          "Keepout zone '"
              + p_area.areaName
              + "' was skipped because its geometry is degenerate "
              + "(e.g. zero-area polygon). This is likely a DSN export issue in your EDA tool. "
              + "The board will be routed without this keepout constraint.");
      return true;
    }
    BasicBoard board = p_par.boardHandling.get_routing_board();
    if (board == null) {
      FRLogger.warn("Structure.insert_keepout: board not initialized");
      return false;
    }
    Layer currLayer = (p_area.shapeList.iterator().next()).layer;
    if (currLayer == Layer.SIGNAL) {
      for (int i = 0; i < board.get_layer_count(); i++) {
        if (p_par.layerStructure.arr[i].isSignal) {
          insert_keepout(
              board, keepoutArea, i, p_area.clearanceClassName, p_keepout_type, p_fixed_state);
        }
      }
    } else if (currLayer.no >= 0) {
      insert_keepout(
          board,
          keepoutArea,
          currLayer.no,
          p_area.clearanceClassName,
          p_keepout_type,
          p_fixed_state);
    } else {
      FRLogger.warn(
          "Structure.insert_keepout: unknown layer name at '"
              + p_par.scanner.get_scope_identifier()
              + "'");
      return false;
    }

    return true;
  }

  private static void insert_keepout(
      BasicBoard p_board,
      Area p_area,
      int p_layer,
      String p_clearance_class_name,
      KeepoutType p_keepout_type,
      FixedState p_fixed_state) {
    int clearanceClassNo;
    if (p_clearance_class_name == null) {
      clearanceClassNo =
          p_board
              .rules
              .get_default_net_class()
              .defaultItemClearanceClasses
              .get(DefaultItemClearanceClasses.ItemClass.AREA);
    } else {
      clearanceClassNo = p_board.rules.clearanceMatrix.get_no(p_clearance_class_name);
      if (clearanceClassNo < 0) {
        FRLogger.warn(
            "Keepout.insert_keepout: clearance class not found at '"
                + p_clearance_class_name
                + "'");
        clearanceClassNo = BoardRules.clearance_class_none();
      }
    }
    if (p_keepout_type == KeepoutType.via_keepout) {
      p_board.insert_via_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    } else if (p_keepout_type == KeepoutType.place_keepout) {
      p_board.insert_component_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    } else {
      p_board.insert_obstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    }
  }

  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    BoardConstructionInfo boardConstructionInfo = new BoardConstructionInfo();

    // If true, components on the back side are rotated before mirroring
    // The correct location is the scope PlaceControl, but Electra writes it here.
    boolean flipStyleRotateFirst = false;

    Collection<Shape.ReadAreaScopeResult> keepoutList = new LinkedList<>();
    Collection<Shape.ReadAreaScopeResult> viaKeepoutList = new LinkedList<>();
    Collection<Shape.ReadAreaScopeResult> placeKeepoutList = new LinkedList<>();

    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Structure.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_scope: unexpected end of file at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      boolean readOk = true;
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.BOUNDARY) {
          read_boundary_scope(p_par.scanner, boardConstructionInfo);
        } else if (nextToken == Keyword.LAYER) {
          readOk = read_layer_scope(p_par.scanner, boardConstructionInfo, p_par.stringQuote);
          if (p_par.layerStructure != null) {
            // correct the layerStructure because another layer isr read
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
        } else if (nextToken == Keyword.VIA) {
          p_par.viaPadstackNames = read_via_padstacks(p_par.scanner);
        } else if (nextToken == Keyword.RULE) {
          boardConstructionInfo.defaultRules.addAll(Rule.read_scope(p_par.scanner));
        } else if (nextToken == Keyword.KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          keepoutList.add(Shape.read_area_scope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          viaKeepoutList.add(Shape.read_area_scope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.PLACE_KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          placeKeepoutList.add(Shape.read_area_scope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.PLANE_SCOPE) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          Keyword.PLANE_SCOPE.read_scope(p_par);
        } else if (nextToken == Keyword.AUTOROUTE_SETTINGS) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
            p_par.autorouteSettings =
                AutorouteSettings.read_scope(p_par.scanner, p_par.layerStructure);
          }
        } else if (nextToken == Keyword.CONTROL) {
          readOk = read_control_scope(p_par);
        } else if (nextToken == Keyword.FLIP_STYLE) {
          flipStyleRotateFirst = PlaceControl.read_flip_style_rotate_first(p_par.scanner);
        } else if (nextToken == Keyword.SNAP_ANGLE) {

          AngleRestriction snapAngle = read_snap_angle(p_par.scanner);
          if (snapAngle != null) {
            p_par.snapAngle = snapAngle;
          }
        } else {
          skip_scope(p_par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }

    // let's create a board based on the data we read (TODO: move this method
    // somewhere outside of the designforms.specctra package)
    boolean result = true;
    if (p_par.boardHandling.get_routing_board() == null) {
      result = create_board(p_par, boardConstructionInfo);
    }
    RoutingBoard board = p_par.boardHandling.get_routing_board();
    if (board == null) {
      return false;
    }
    if (flipStyleRotateFirst) {
      board.components.set_flip_style_rotate_first(true);
    }

    // insert the keepouts
    for (Shape.ReadAreaScopeResult currArea : keepoutList) {
      if (!insert_keepout(currArea, p_par, KeepoutType.keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : viaKeepoutList) {
      if (!insert_keepout(currArea, p_par, KeepoutType.via_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : placeKeepoutList) {
      if (!insert_keepout(currArea, p_par, KeepoutType.place_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    // insert the planes.
    for (ReadScopeParameter.PlaneInfo planeInfo : p_par.planeList) {
      Net.Id netId = new Net.Id(planeInfo.netName, 1);
      if (!p_par.netlist.contains(netId)) {
        Net newNet = p_par.netlist.add_net(netId);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
        }
      }
      app.freerouting.rules.Net currNet = board.rules.nets.get(planeInfo.netName, 1);
      if (currNet == null) {
        FRLogger.warn(
            "Plane.read_scope: net not found at '" + p_par.scanner.get_scope_identifier() + "'");
        continue;
      }
      Area planeArea =
          Shape.transform_area_to_board(planeInfo.area.shapeList, p_par.coordinateTransform);
      Layer currLayer = (planeInfo.area.shapeList.iterator().next()).layer;
      if (currLayer.no >= 0) {
        int clearanceClassNo;
        if (planeInfo.area.clearanceClassName != null) {
          clearanceClassNo = board.rules.clearanceMatrix.get_no(planeInfo.area.clearanceClassName);
          if (clearanceClassNo < 0) {
            FRLogger.warn(
                "Structure.read_scope: clearance class not found at '"
                    + p_par.scanner.get_scope_identifier()
                    + "'");
            clearanceClassNo = BoardRules.clearance_class_none();
          }
        } else {
          clearanceClassNo =
              currNet
                  .getNetClass()
                  .defaultItemClearanceClasses
                  .get(DefaultItemClearanceClasses.ItemClass.AREA);
        }
        int[] netNumbers = new int[1];
        netNumbers[0] = currNet.netNumber;
        board.insert_conduction_area(
            planeArea, currLayer.no, netNumbers, clearanceClassNo, false, FixedState.SYSTEM_FIXED);
      } else {
        FRLogger.warn(
            "Plane.read_scope: unexpected layer name at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
    }
    insert_missing_power_planes(boardConstructionInfo.layerInfo, p_par.netlist, board);

    p_par.boardHandling.initialize_manual_trace_half_widths();

    // Apply DSN autoroute settings to the current routing job if they were parsed
    if (p_par.autorouteSettings != null) {
      // Get the current routing job from the board manager
      RoutingJob currentJob = p_par.boardHandling.getCurrentRoutingJob();
      if (currentJob != null && currentJob.routerSettings != null) {
        // Apply the DSN file's autoroute settings to the routing job
        currentJob.routerSettings.applyNewValuesFrom(p_par.autorouteSettings);
        FRLogger.info("Applied DSN autoroute settings to routing job");
      }
    }

    return result;
  }

  private boolean create_board(
      ReadScopeParameter p_par, BoardConstructionInfo p_board_construction_info) {
    int layerCount = p_board_construction_info.layerInfo.size();
    if (layerCount == 0) {
      FRLogger.warn(
          "Structure.create_board: layers missing in structure scope at '"
              + p_par.scanner.get_scope_identifier()
              + "'");
      return false;
    }
    if (p_board_construction_info.boundingShape == null) {
      // happens if the boundary shape with layer pcb is missing
      if (p_board_construction_info.outlineShapes.isEmpty()) {
        FRLogger.warn(
            "Structure.create_board: outline missing at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        p_par.boardOutlineOk = false;
        return false;
      }
      Iterator<Shape> it = p_board_construction_info.outlineShapes.iterator();

      Rectangle boundingBox = it.next().bounding_box();
      while (it.hasNext()) {
        boundingBox = boundingBox.union(it.next().bounding_box());
      }
      p_board_construction_info.boundingShape = boundingBox;
    }
    Rectangle boundingBox = p_board_construction_info.boundingShape.bounding_box();
    app.freerouting.board.Layer[] boardLayerArr = new app.freerouting.board.Layer[layerCount];
    Iterator<Layer> it = p_board_construction_info.layerInfo.iterator();
    for (int i = 0; i < layerCount; i++) {
      Layer currLayer = it.next();
      if (currLayer.no < 0 || currLayer.no >= layerCount) {
        FRLogger.warn(
            "Structure.create_board: illegal layer number at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      boardLayerArr[i] = new app.freerouting.board.Layer(currLayer.name, currLayer.isSignal);
    }
    app.freerouting.board.LayerStructure boardLayerStructure =
        new app.freerouting.board.LayerStructure(boardLayerArr);
    p_par.layerStructure = new LayerStructure(p_board_construction_info.layerInfo);

    // Calculate an approximate scaling between dsn coordinates and board
    // coordinates.
    int scaleFactor = Math.max(p_par.resolution, 1);

    double maxCoor = 0;
    for (int i = 0; i < 4; i++) {
      maxCoor = Math.max(maxCoor, Math.abs(boundingBox.coor[i] * p_par.resolution));
    }
    if (maxCoor == 0) {
      p_par.boardOutlineOk = false;
      return false;
    }
    // make scalefactor smaller, if there is a danger of integer overflow.
    while (5 * maxCoor >= Limits.CRIT_INT) {
      scaleFactor /= 10;
      maxCoor /= 10;
    }

    p_par.coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);

    IntBox bounds = (IntBox) boundingBox.transform_to_board(p_par.coordinateTransform);
    bounds = bounds.offset(1000);

    Collection<PolylineShape> boardOutlineShapes = new LinkedList<>();
    for (Shape currShape : p_board_construction_info.outlineShapes) {
      if (currShape instanceof PolygonPath currPath) {
        if (currPath.width != 0) {
          // set the width to 0, because the offset function used in transform_to_board is
          // not
          // implemented
          // for shapes, which are not convex.
          currShape = new PolygonPath(currPath.layer, 0, currPath.coordinateArr);
        }
      }
      PolylineShape currBoardShape =
          (PolylineShape) currShape.transform_to_board(p_par.coordinateTransform);
      if (currBoardShape.dimension() > 0) {
        boardOutlineShapes.add(currBoardShape);
      }
    }
    if (boardOutlineShapes.isEmpty()) {
      // construct an outline from the boundingShape, if the outline is missing.
      PolylineShape currBoardShape =
          (PolylineShape)
              p_board_construction_info.boundingShape.transform_to_board(p_par.coordinateTransform);
      boardOutlineShapes.add(currBoardShape);
    }
    Collection<PolylineShape> holeShapes = separate_holes(boardOutlineShapes);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.get_default_instance(boardLayerStructure, 0);
    BoardRules boardRules = new BoardRules(boardLayerStructure, clearanceMatrix);
    Communication.SpecctraParserInfo specctraParserInfo =
        new Communication.SpecctraParserInfo(
            p_par.stringQuote,
            p_par.hostCad,
            p_par.hostVersion,
            p_par.constants,
            p_par.writeResolution,
            p_par.dsnFileGeneratedByHost);
    Communication boardCommunication =
        new Communication(
            p_par.unit,
            p_par.resolution,
            specctraParserInfo,
            p_par.coordinateTransform,
            p_par.itemIdNoGenerator,
            p_par.observers);

    if (boardCommunication.host_is_old_kicad()) {
      FRLogger.warn(
          "Structure.create_board: The DSN file was exported from an old KiCad version that has known compatibility issues. Please update KiCad to version 6 or newer.");
    }

    PolylineShape[] outlineShapeArr = new PolylineShape[boardOutlineShapes.size()];
    Iterator<PolylineShape> it2 = boardOutlineShapes.iterator();
    for (int i = 0; i < outlineShapeArr.length; i++) {
      outlineShapeArr[i] = it2.next();
    }
    update_board_rules(p_par, p_board_construction_info, boardRules);
    boardRules.set_trace_angle_restriction(p_par.snapAngle);
    p_par.boardHandling.create_board(
        bounds,
        boardLayerStructure,
        outlineShapeArr,
        p_board_construction_info.outlineClearanceClassName,
        boardRules,
        boardCommunication);

    BasicBoard board = p_par.boardHandling.get_routing_board();

    // Insert the holes in the board outline as keepouts.
    for (PolylineShape curr_outline_hole : holeShapes) {
      for (int i = 0; i < boardLayerStructure.arr.length; i++) {
        board.insert_obstacle(curr_outline_hole, i, 0, FixedState.SYSTEM_FIXED);
      }
    }

    return true;
  }

  enum KeepoutType {
    keepout,
    via_keepout,
    place_keepout
  }

  private static class BoardConstructionInfo {

    Collection<Layer> layerInfo = new LinkedList<>();
    Shape boundingShape;
    List<Shape> outlineShapes = new LinkedList<>();
    String outlineClearanceClassName;
    int foundLayerCount;
    Collection<Rule> defaultRules = new LinkedList<>();
    Collection<LayerRule> layerDependentRules = new LinkedList<>();
  }

  private static class LayerRule {

    final String layerName;
    final Collection<Rule> rule;

    LayerRule(String p_layer_name, Collection<Rule> p_rule) {
      layerName = p_layer_name;
      rule = p_rule;
    }
  }

  /** Used to separate the holes in the outline. */
  private static class OutlineShape {

    final PolylineShape shape;
    final IntBox boundingBox;
    final TileShape[] convexShapes;
    boolean isHole;

    public OutlineShape(PolylineShape p_shape) {
      shape = p_shape;
      boundingBox = p_shape.bounding_box();
      convexShapes = p_shape.split_to_convex();
      isHole = false;
    }

    /** Returns true, if this shape contains all corners of p_other_shape. */
    private boolean contains_all_corners(OutlineShape p_other_shape) {
      if (this.convexShapes == null) {
        // calculation of the convex shapes failed
        return false;
      }
      int cornerCount = p_other_shape.shape.border_line_count();
      for (int i = 0; i < cornerCount; i++) {
        Point currCorner = p_other_shape.shape.corner(i);
        boolean isContained = false;
        for (int j = 0; j < this.convexShapes.length; j++) {
          if (this.convexShapes[j].contains(currCorner)) {
            isContained = true;
            break;
          }
        }
        if (!isContained) {
          return false;
        }
      }
      return true;
    }
  }
}
