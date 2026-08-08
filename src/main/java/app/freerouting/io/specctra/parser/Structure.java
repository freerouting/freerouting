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

  public static void writeScope(WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("structure");

    // write the layer structure
    writeLayers(p_par);

    // write the boundaries
    writeBoundaries(p_par);

    // write the routing vias
    writeViaPadstacks(p_par.board.library, p_par.file, p_par.identifierType);

    // write the rules
    writeDefaultRules(p_par);

    // write the snap angles
    writeSnapAngle(p_par.file, p_par.board.rules.getTraceAngleRestriction());

    // write the control scope
    writeControlScope(p_par.board.rules, p_par.file);

    if (p_par.autorouteSettings != null) {
      // write the auto-route settings
      AutorouteSettings.writeScope(
          p_par.file, p_par.autorouteSettings, p_par.board.layerStructure, p_par.identifierType);
    }

    // write the conduction areas
    writeConductionAreas(p_par);

    // write the keepouts
    writeKeepouts(p_par);

    p_par.file.endScope();
  }

  private static void writeConductionAreas(WriteScopeParameter p_par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = p_par.board.itemList.readObject(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (p_par.board.layerStructure.arr[currArea.getLayer()].isSignal) {
        // These conduction areas are written in the wiring scope.
        continue;
      }
      Plane.writeScope(p_par, currArea);
    }
  }

  private static void writeKeepouts(WriteScopeParameter p_par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = p_par.board.itemList.readObject(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ObstacleArea currKeepout)) {
        continue;
      }
      if (currKeepout.getComponentNo() != 0) {
        // keepouts belonging to a component are not written individually.
        continue;
      }
      if (currKeepout instanceof ConductionArea) {
        // conduction area will be written later.
        continue;
      }
      writeKeepoutScope(p_par, currKeepout);
    }
  }

  private static void writeBoundaries(WriteScopeParameter p_par) throws IOException {
    // write the bounding box
    p_par.file.startScope();
    p_par.file.write("boundary");
    IntBox bounds = p_par.board.getBoundingBox();
    double[] rectCoor = p_par.coordinateTransform.boardToDsn(bounds);
    Rectangle boundingRectangle = new Rectangle(Layer.PCB, rectCoor);
    boundingRectangle.writeScope(p_par.file, p_par.identifierType);
    p_par.file.endScope();
    // lookup the outline in the board
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = p_par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = p_par.board.itemList.readObject(it);
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
    for (int i = 0; i < outline.shapeCount(); i++) {
      Shape outlineShape =
          p_par.coordinateTransform.boardToDsn(outline.getShape(i), Layer.SIGNAL);
      p_par.file.startScope();
      p_par.file.write("boundary");
      outlineShape.writeScope(p_par.file, p_par.identifierType);
      p_par.file.endScope();
    }
  }

  static void writeLayers(WriteScopeParameter p_par) throws IOException {
    for (int i = 0; i < p_par.board.layerStructure.arr.length; i++) {
      boolean writeLayerRule =
          p_par.board.rules.getDefaultNetClass().getTraceHalfWidth(i)
                  != p_par.board.rules.getDefaultNetClass().getTraceHalfWidth(0)
              || !clearanceEquals(p_par.board.rules.clearanceMatrix, i, 0);
      Layer.writeScope(p_par, i, writeLayerRule);
    }
  }

  static void writeDefaultRules(WriteScopeParameter p_par) throws IOException {
    // write the default rule using 0 as default layer.
    Rule.writeDefaultRule(p_par, 0);
  }

  private static void writeViaPadstacks(
      BoardLibrary p_library, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.newLine();
    p_file.write("(via");
    for (int i = 0; i < p_library.viaPadstackCount(); i++) {
      Padstack currPadstack = p_library.getViaPadstack(i);
      if (currPadstack != null) {
        p_file.write(" ");
        p_identifier_type.write(currPadstack.name, p_file);
      } else {
        FRLogger.warn("Structure.write_via_padstacks: padstack is null");
      }
    }
    p_file.write(")");
  }

  private static void writeControlScope(BoardRules p_rules, IndentFileWriter p_file)
      throws IOException {
    p_file.startScope();
    p_file.write("control");
    p_file.newLine();
    p_file.write("(via_at_smd ");
    boolean viaAtSmdAllowed = false;
    for (int i = 0; i < p_rules.viaInfos.count(); i++) {
      if (p_rules.viaInfos.get(i).attachSmdAllowed()) {
        viaAtSmdAllowed = true;
        break;
      }
    }
    if (viaAtSmdAllowed) {
      p_file.write("on)");
    } else {
      p_file.write("off)");
    }
    p_file.endScope();
  }

  private static void writeKeepoutScope(WriteScopeParameter p_par, ObstacleArea p_keepout)
      throws IOException {
    Area keepoutArea = p_keepout.getArea();
    int layerNo = p_keepout.getLayer();
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[layerNo];
    Layer keepoutLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (keepoutArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = keepoutArea.getBorder();
      holes = keepoutArea.getHoles();
    }
    p_par.file.startScope();
    if (p_keepout instanceof ViaObstacleArea) {
      p_par.file.write("via_keepout");
    } else {
      p_par.file.write("keepout");
    }
    Shape dsnShape = p_par.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(p_par.file, p_par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = p_par.coordinateTransform.boardToDsn(holes[i], keepoutLayer);
      dsnHole.writeHoleScope(p_par.file, p_par.identifierType);
    }
    // write clearance class if it's defined for this keepout area.
    if (p_keepout.clearanceClassNo() > 0) {
      // skip it if it's the default clearance class.
      String clearanceName =
          p_par.board.rules.clearanceMatrix.getName(p_keepout.clearanceClassNo());

      if (!"default".equals(clearanceName)) {
        Rule.writeItemClearanceClass(clearanceName, p_par.file, p_par.identifierType);
      }
    }
    p_par.file.endScope();
  }

  private static boolean readBoundaryScope(
      IJFlexScanner p_scanner, BoardConstructionInfo p_board_construction_info) {
    Shape currShape = Shape.readScope(p_scanner, null);
    try {
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLEARANCE_CLASS) {
            p_board_construction_info.outlineClearanceClassName =
                DsnFile.readStringScope(p_scanner);
          } else {
            Shape additionalShape = Shape.readScopeFromKeyword(p_scanner, nextToken, null);
            addBoundaryShape(p_board_construction_info, additionalShape);
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
              + p_scanner.getScopeIdentifier()
              + "'");
      return true;
    }
    addBoundaryShape(p_board_construction_info, currShape);
    return true;
  }

  private static void addBoundaryShape(
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

  static boolean readLayerScope(
      IJFlexScanner p_scanner,
      BoardConstructionInfo p_board_construction_info,
      String p_string_quote) {
    try {
      boolean layerOk = true;
      boolean isSignal = true;

      String layerString = p_scanner.nextString();

      Collection<String> netNames = new LinkedList<>();
      Object nextToken = p_scanner.nextToken();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Structure.read_layer_scope: ( expected at '"
                  + p_scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.TYPE) {
          nextToken = p_scanner.nextToken();
          if (nextToken == Keyword.POWER) {
            isSignal = false;
          } else if ((nextToken != Keyword.SIGNAL)
              && (!Objects.equals(nextToken.toString(), Keyword.JUMPER.getName()))) {
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
                      + p_scanner.getScopeIdentifier()
                      + "'");
            }
            layerOk = false;
          }
          nextToken = p_scanner.nextToken();
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Structure.read_layer_scope: ) expected at '"
                    + p_scanner.getScopeIdentifier()
                    + "'");
            return false;
          }
        } else if (nextToken == Keyword.RULE) {
          Collection<Rule> currRules = Rule.readScope(p_scanner);
          p_board_construction_info.layerDependentRules.add(new LayerRule(layerString, currRules));
        } else if (nextToken == Keyword.USE_NET) {
          for (; ; ) {
            p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
            nextToken = p_scanner.nextToken();
            if (nextToken == Keyword.CLOSED_BRACKET) {
              break;
            }
            if (nextToken instanceof String string) {
              netNames.add(string);
            } else {
              FRLogger.warn(
                  "Structure.read_layer_scope: string expected at '"
                      + p_scanner.getScopeIdentifier()
                      + "'");
            }
          }
        } else {
          skipScope(p_scanner);
        }
        nextToken = p_scanner.nextToken();
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

  static Collection<String> readViaPadstacks(IJFlexScanner p_scanner) {
    try {
      Collection<String> normalVias = new LinkedList<>();
      Collection<String> spareVias = new LinkedList<>();
      for (; ; ) {
        Object nextToken = p_scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          nextToken = p_scanner.nextToken();
          if (nextToken == Keyword.SPARE) {
            spareVias = readViaPadstacks(p_scanner);
          } else {
            skipScope(p_scanner);
          }
        } else if (nextToken instanceof String string) {
          normalVias.add(string);
        } else {
          FRLogger.warn(
              "Structure.read_via_padstack: String expected at '"
                  + p_scanner.getScopeIdentifier()
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

  private static boolean readControlScope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_control_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_control_scope: unexpected end of file at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.VIA_AT_SMD) {
          p_par.viaAtSmdAllowed = DsnFile.readOnOffScope(p_par.scanner);
        } else {
          skipScope(p_par.scanner);
        }
      }
    }
    return true;
  }

  public static AngleRestriction readSnapAngle(IJFlexScanner p_scanner) {
    try {
      Object nextToken = p_scanner.nextToken();
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
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = p_scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Structure.read_selection_layer_scop: closing bracket expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return snapAngle;
    } catch (IOException e) {
      FRLogger.error("Structure.read_snap_angle: IO error scanning file", e);
      return null;
    }
  }

  public static void writeSnapAngle(IndentFileWriter p_file, AngleRestriction p_angle_restriction)
      throws IOException {
    p_file.startScope();
    p_file.write("snapAngle ");
    p_file.newLine();

    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE) {
      p_file.write("ninety_degree");
    } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      p_file.write("fortyfive_degree");
    } else {
      p_file.write("none");
    }
    p_file.endScope();
  }

  private static void insertMissingPowerPlanes(
      Collection<Layer> p_layer_info, NetList p_netlist, BasicBoard p_board) {
    Collection<ConductionArea> conductionAreas = p_board.getConductionAreas();
    for (Layer currLayer : p_layer_info) {
      if (currLayer.isSignal) {
        continue;
      }
      boolean conductionAreaFound = false;
      for (ConductionArea curr_conduction_area : conductionAreas) {
        if (curr_conduction_area.getLayer() == currLayer.no) {
          conductionAreaFound = true;
          break;
        }
      }
      if (!conductionAreaFound && !currLayer.netNames.isEmpty()) {
        String currNetName = currLayer.netNames.iterator().next();
        Net.Id currNetId = new Net.Id(currNetName, 1);
        if (!p_netlist.contains(currNetId)) {
          Net newNet = p_netlist.addNet(currNetId);
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
        p_board.insertConductionArea(
            p_board.boundingBox,
            currLayer.no,
            netNumbers,
            BoardRules.clearanceClassNone(),
            false,
            FixedState.SYSTEM_FIXED);
      }
    }
  }

  /**
   * Calculates shapes in p_outline_shapes, which are holes in the outline and returns them in the
   * result list.
   */
  private static Collection<PolylineShape> separateHoles(
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
        currShape.isHole = otherShape.containsAllCorners(currShape);
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
  private static void updateBoardRules(
      ReadScopeParameter p_par,
      BoardConstructionInfo p_board_construction_info,
      BoardRules p_board_rules) {
    boolean smdToTurnGapFound = false;
    // update the clearance matrix
    for (Rule currOb : p_board_construction_info.defaultRules) {
      if (currOb instanceof Rule.ClearanceRule currRule) {
        if (setClearanceRule(
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
            (int) Math.round(p_par.coordinateTransform.dsnToBoard(wireWidth) / 2);
        FRLogger.debug(
            "Set default trace width (all layers): DSN="
                + wireWidth
                + " → board="
                + (traceHalfwidth * 2)
                + " ("
                + (traceHalfwidth * 2 / 40000.0)
                + " mm)");
        p_board_rules.setDefaultTraceHalfWidths(traceHalfwidth);
      }
    }
    for (LayerRule layer_rule : p_board_construction_info.layerDependentRules) {
      int layerNo = p_par.layerStructure.getNo(layer_rule.layerName);
      if (layerNo < 0) {
        continue;
      }
      for (Rule currOb : layer_rule.rule) {
        if (currOb instanceof Rule.WidthRule rule) {
          double wireWidth = rule.value;
          int traceHalfwidth =
              (int) Math.round(p_par.coordinateTransform.dsnToBoard(wireWidth) / 2);
          p_board_rules.setDefaultTraceHalfWidth(layerNo, traceHalfwidth);
        } else if (currOb instanceof Rule.ClearanceRule currRule) {
          setClearanceRule(
              currRule, layerNo, p_par.coordinateTransform, p_board_rules, p_par.stringQuote);
        }
      }
    }
    if (!smdToTurnGapFound) {
      p_board_rules.setPinEdgeToTurnDist(p_board_rules.getMinTraceHalfWidth());
    }
  }

  /**
   * Converts a dsn clearance rule into a board clearance rule. If p_layer_no is negative, the rule
   * is set on all layers. Returns true, if the string smd_to_turn_gap was found.
   */
  public static boolean setClearanceRule(
      Rule.ClearanceRule p_rule,
      int p_layer_no,
      CoordinateTransform p_coordinate_transform,
      BoardRules p_board_rules,
      String p_string_quote) {
    boolean result = false;
    int currClearance = (int) Math.round(p_coordinate_transform.dsnToBoard(p_rule.value));
    if (p_rule.clearanceClassPairs.isEmpty()) {
      if (p_layer_no < 0) {
        p_board_rules.clearanceMatrix.setDefaultValue(currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (all layers): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + p_rule.value);
      } else {
        p_board_rules.clearanceMatrix.setDefaultValue(p_layer_no, currClearance);
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
    if (containsWireClearancePair(p_rule.clearanceClassPairs)) {
      createDefaultClearanceClasses(p_board_rules);
    }

    for (String currString : p_rule.clearanceClassPairs) {
      if ("smd_to_turn_gap".equalsIgnoreCase(currString)) {
        p_board_rules.setPinEdgeToTurnDist(currClearance);
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
        firstClassNo = p_board_rules.clearanceMatrix.getNo(currPair[0]);
      }
      if (firstClassNo < 0) {
        firstClassNo = appendClearanceClass(p_board_rules, currPair[0]);
      }
      int secondClassNo;
      if ("wire".equals(currPair[1])) {
        secondClassNo = 1; // default class
      } else {
        secondClassNo = p_board_rules.clearanceMatrix.getNo(currPair[1]);
      }
      if (secondClassNo < 0) {
        secondClassNo = appendClearanceClass(p_board_rules, currPair[1]);
      }
      if (p_layer_no < 0) {
        p_board_rules.clearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        p_board_rules.clearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
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
        p_board_rules.clearanceMatrix.setValue(
            firstClassNo, secondClassNo, p_layer_no, currClearance);
        p_board_rules.clearanceMatrix.setValue(
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

  static boolean containsWireClearancePair(Collection<String> p_clearance_pairs) {
    for (String currPair : p_clearance_pairs) {
      if (currPair.startsWith("wire_") || currPair.endsWith("_wire")) {
        return true;
      }
    }
    return false;
  }

  private static void createDefaultClearanceClasses(BoardRules p_board_rules) {
    appendClearanceClass(p_board_rules, "via");
    appendClearanceClass(p_board_rules, "smd");
    appendClearanceClass(p_board_rules, "pin");
    appendClearanceClass(p_board_rules, "area");
  }

  private static int appendClearanceClass(BoardRules p_board_rules, String p_name) {
    p_board_rules.clearanceMatrix.appendClass(p_name);
    int result = p_board_rules.clearanceMatrix.getNo(p_name);
    NetClass defaultNetClass = p_board_rules.getDefaultNetClass();
    switch (p_name) {
      case "via" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
    }
    return result;
  }

  /** Returns true, if all clearance values on the 2 input layers are equal. */
  private static boolean clearanceEquals(
      ClearanceMatrix p_cl_matrix, int p_layer_1, int p_layer_2) {
    if (p_layer_1 == p_layer_2) {
      return true;
    }
    for (int i = 1; i < p_cl_matrix.getClassCount(); i++) {
      for (int j = i; j < p_cl_matrix.getClassCount(); j++) {
        if (p_cl_matrix.getValue(i, j, p_layer_1, false)
            != p_cl_matrix.getValue(i, j, p_layer_2, false)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean insertKeepout(
      Shape.ReadAreaScopeResult p_area,
      ReadScopeParameter p_par,
      KeepoutType p_keepout_type,
      FixedState p_fixed_state) {
    Area keepoutArea = Shape.transformAreaToBoard(p_area.shapeList, p_par.coordinateTransform);
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
    BasicBoard board = p_par.boardHandling.getRoutingBoard();
    if (board == null) {
      FRLogger.warn("Structure.insert_keepout: board not initialized");
      return false;
    }
    Layer currLayer = (p_area.shapeList.iterator().next()).layer;
    if (currLayer == Layer.SIGNAL) {
      for (int i = 0; i < board.getLayerCount(); i++) {
        if (p_par.layerStructure.arr[i].isSignal) {
          insertKeepout(
              board, keepoutArea, i, p_area.clearanceClassName, p_keepout_type, p_fixed_state);
        }
      }
    } else if (currLayer.no >= 0) {
      insertKeepout(
          board,
          keepoutArea,
          currLayer.no,
          p_area.clearanceClassName,
          p_keepout_type,
          p_fixed_state);
    } else {
      FRLogger.warn(
          "Structure.insert_keepout: unknown layer name at '"
              + p_par.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    return true;
  }

  private static void insertKeepout(
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
              .getDefaultNetClass()
              .defaultItemClearanceClasses
              .get(DefaultItemClearanceClasses.ItemClass.AREA);
    } else {
      clearanceClassNo = p_board.rules.clearanceMatrix.getNo(p_clearance_class_name);
      if (clearanceClassNo < 0) {
        FRLogger.warn(
            "Keepout.insert_keepout: clearance class not found at '"
                + p_clearance_class_name
                + "'");
        clearanceClassNo = BoardRules.clearanceClassNone();
      }
    }
    if (p_keepout_type == KeepoutType.via_keepout) {
      p_board.insertViaObstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    } else if (p_keepout_type == KeepoutType.place_keepout) {
      p_board.insertComponentObstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    } else {
      p_board.insertObstacle(p_area, p_layer, clearanceClassNo, p_fixed_state);
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter p_par) {
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
        nextToken = p_par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_scope: unexpected end of file at '"
                + p_par.scanner.getScopeIdentifier()
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
          readBoundaryScope(p_par.scanner, boardConstructionInfo);
        } else if (nextToken == Keyword.LAYER) {
          readOk = readLayerScope(p_par.scanner, boardConstructionInfo, p_par.stringQuote);
          if (p_par.layerStructure != null) {
            // correct the layerStructure because another layer isr read
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
        } else if (nextToken == Keyword.VIA) {
          p_par.viaPadstackNames = readViaPadstacks(p_par.scanner);
        } else if (nextToken == Keyword.RULE) {
          boardConstructionInfo.defaultRules.addAll(Rule.readScope(p_par.scanner));
        } else if (nextToken == Keyword.KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          keepoutList.add(Shape.readAreaScope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          viaKeepoutList.add(Shape.readAreaScope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.PLACE_KEEPOUT) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          placeKeepoutList.add(Shape.readAreaScope(p_par.scanner, p_par.layerStructure, false));
        } else if (nextToken == Keyword.PLANE_SCOPE) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          Keyword.PLANE_SCOPE.readScope(p_par);
        } else if (nextToken == Keyword.AUTOROUTE_SETTINGS) {
          if (p_par.layerStructure == null) {
            p_par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
            p_par.autorouteSettings =
                AutorouteSettings.readScope(p_par.scanner, p_par.layerStructure);
          }
        } else if (nextToken == Keyword.CONTROL) {
          readOk = readControlScope(p_par);
        } else if (nextToken == Keyword.FLIP_STYLE) {
          flipStyleRotateFirst = PlaceControl.readFlipStyleRotateFirst(p_par.scanner);
        } else if (nextToken == Keyword.SNAP_ANGLE) {

          AngleRestriction snapAngle = readSnapAngle(p_par.scanner);
          if (snapAngle != null) {
            p_par.snapAngle = snapAngle;
          }
        } else {
          skipScope(p_par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }

    // let's create a board based on the data we read (TODO: move this method
    // somewhere outside of the designforms.specctra package)
    boolean result = true;
    if (p_par.boardHandling.getRoutingBoard() == null) {
      result = createBoard(p_par, boardConstructionInfo);
    }
    RoutingBoard board = p_par.boardHandling.getRoutingBoard();
    if (board == null) {
      return false;
    }
    if (flipStyleRotateFirst) {
      board.components.setFlipStyleRotateFirst(true);
    }

    // insert the keepouts
    for (Shape.ReadAreaScopeResult currArea : keepoutList) {
      if (!insertKeepout(currArea, p_par, KeepoutType.keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : viaKeepoutList) {
      if (!insertKeepout(currArea, p_par, KeepoutType.via_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : placeKeepoutList) {
      if (!insertKeepout(currArea, p_par, KeepoutType.place_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    // insert the planes.
    for (ReadScopeParameter.PlaneInfo planeInfo : p_par.planeList) {
      Net.Id netId = new Net.Id(planeInfo.netName, 1);
      if (!p_par.netlist.contains(netId)) {
        Net newNet = p_par.netlist.addNet(netId);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
        }
      }
      app.freerouting.rules.Net currNet = board.rules.nets.get(planeInfo.netName, 1);
      if (currNet == null) {
        FRLogger.warn(
            "Plane.read_scope: net not found at '" + p_par.scanner.getScopeIdentifier() + "'");
        continue;
      }
      Area planeArea =
          Shape.transformAreaToBoard(planeInfo.area.shapeList, p_par.coordinateTransform);
      Layer currLayer = (planeInfo.area.shapeList.iterator().next()).layer;
      if (currLayer.no >= 0) {
        int clearanceClassNo;
        if (planeInfo.area.clearanceClassName != null) {
          clearanceClassNo = board.rules.clearanceMatrix.getNo(planeInfo.area.clearanceClassName);
          if (clearanceClassNo < 0) {
            FRLogger.warn(
                "Structure.read_scope: clearance class not found at '"
                    + p_par.scanner.getScopeIdentifier()
                    + "'");
            clearanceClassNo = BoardRules.clearanceClassNone();
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
        board.insertConductionArea(
            planeArea, currLayer.no, netNumbers, clearanceClassNo, false, FixedState.SYSTEM_FIXED);
      } else {
        FRLogger.warn(
            "Plane.read_scope: unexpected layer name at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
    }
    insertMissingPowerPlanes(boardConstructionInfo.layerInfo, p_par.netlist, board);

    p_par.boardHandling.initializeManualTraceHalfWidths();

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

  private boolean createBoard(
      ReadScopeParameter p_par, BoardConstructionInfo p_board_construction_info) {
    int layerCount = p_board_construction_info.layerInfo.size();
    if (layerCount == 0) {
      FRLogger.warn(
          "Structure.create_board: layers missing in structure scope at '"
              + p_par.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (p_board_construction_info.boundingShape == null) {
      // happens if the boundary shape with layer pcb is missing
      if (p_board_construction_info.outlineShapes.isEmpty()) {
        FRLogger.warn(
            "Structure.create_board: outline missing at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        p_par.boardOutlineOk = false;
        return false;
      }
      Iterator<Shape> it = p_board_construction_info.outlineShapes.iterator();

      Rectangle boundingBox = it.next().boundingBox();
      while (it.hasNext()) {
        boundingBox = boundingBox.union(it.next().boundingBox());
      }
      p_board_construction_info.boundingShape = boundingBox;
    }
    Rectangle boundingBox = p_board_construction_info.boundingShape.boundingBox();
    app.freerouting.board.Layer[] boardLayerArr = new app.freerouting.board.Layer[layerCount];
    Iterator<Layer> it = p_board_construction_info.layerInfo.iterator();
    for (int i = 0; i < layerCount; i++) {
      Layer currLayer = it.next();
      if (currLayer.no < 0 || currLayer.no >= layerCount) {
        FRLogger.warn(
            "Structure.create_board: illegal layer number at '"
                + p_par.scanner.getScopeIdentifier()
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

    IntBox bounds = (IntBox) boundingBox.transformToBoard(p_par.coordinateTransform);
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
          (PolylineShape) currShape.transformToBoard(p_par.coordinateTransform);
      if (currBoardShape.dimension() > 0) {
        boardOutlineShapes.add(currBoardShape);
      }
    }
    if (boardOutlineShapes.isEmpty()) {
      // construct an outline from the boundingShape, if the outline is missing.
      PolylineShape currBoardShape =
          (PolylineShape)
              p_board_construction_info.boundingShape.transformToBoard(p_par.coordinateTransform);
      boardOutlineShapes.add(currBoardShape);
    }
    Collection<PolylineShape> holeShapes = separateHoles(boardOutlineShapes);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(boardLayerStructure, 0);
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

    if (boardCommunication.hostIsOldKicad()) {
      FRLogger.warn(
          "Structure.create_board: The DSN file was exported from an old KiCad version that has known compatibility issues. Please update KiCad to version 6 or newer.");
    }

    PolylineShape[] outlineShapeArr = new PolylineShape[boardOutlineShapes.size()];
    Iterator<PolylineShape> it2 = boardOutlineShapes.iterator();
    for (int i = 0; i < outlineShapeArr.length; i++) {
      outlineShapeArr[i] = it2.next();
    }
    updateBoardRules(p_par, p_board_construction_info, boardRules);
    boardRules.setTraceAngleRestriction(p_par.snapAngle);
    p_par.boardHandling.createBoard(
        bounds,
        boardLayerStructure,
        outlineShapeArr,
        p_board_construction_info.outlineClearanceClassName,
        boardRules,
        boardCommunication);

    BasicBoard board = p_par.boardHandling.getRoutingBoard();

    // Insert the holes in the board outline as keepouts.
    for (PolylineShape curr_outline_hole : holeShapes) {
      for (int i = 0; i < boardLayerStructure.arr.length; i++) {
        board.insertObstacle(curr_outline_hole, i, 0, FixedState.SYSTEM_FIXED);
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
      boundingBox = p_shape.boundingBox();
      convexShapes = p_shape.splitToConvex();
      isHole = false;
    }

    /** Returns true, if this shape contains all corners of p_other_shape. */
    private boolean containsAllCorners(OutlineShape p_other_shape) {
      if (this.convexShapes == null) {
        // calculation of the convex shapes failed
        return false;
      }
      int cornerCount = p_other_shape.shape.borderLineCount();
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
