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
import app.freerouting.core.RoutingJob;
import app.freerouting.core.library.BoardLibrary;
import app.freerouting.core.library.Padstack;
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
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class Structure extends ScopeKeyword {

  /** Creates a new instance of Structure. */
  public Structure() {
    super("structure");
  }

  public static void writeScope(WriteScopeParameter par) throws IOException {
    par.file.startScope();
    par.file.write("structure");

    // write the layer structure
    writeLayers(par);

    // write the boundaries
    writeBoundaries(par);

    // write the routing vias
    writeViaPadstacks(par.board.library, par.file, par.identifierType);

    // write the rules
    writeDefaultRules(par);

    // write the snap angles
    writeSnapAngle(par.file, par.board.rules.getTraceAngleRestriction());

    // write the control scope
    writeControlScope(par.board.rules, par.file);

    if (par.autorouteSettings != null) {
      // write the auto-route settings
      AutorouteSettings.writeScope(
          par.file, par.autorouteSettings, par.board.layerStructure, par.identifierType);
    }

    // write the conduction areas
    writeConductionAreas(par);

    // write the keepouts
    writeKeepouts(par);

    par.file.endScope();
  }

  private static void writeConductionAreas(WriteScopeParameter par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = par.board.itemList.readObject(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (par.board.layerStructure.arr[currArea.getLayer()].isSignal) {
        // These conduction areas are written in the wiring scope.
        continue;
      }
      Plane.writeScope(par, currArea);
    }
  }

  private static void writeKeepouts(WriteScopeParameter par) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = par.board.itemList.readObject(it);
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
      writeKeepoutScope(par, currKeepout);
    }
  }

  private static void writeBoundaries(WriteScopeParameter par) throws IOException {
    // write the bounding box
    par.file.startScope();
    par.file.write("boundary");
    IntBox bounds = par.board.getBoundingBox();
    double[] rectCoor = par.coordinateTransform.boardToDsn(bounds);
    Rectangle boundingRectangle = new Rectangle(Layer.PCB, rectCoor);
    boundingRectangle.writeScope(par.file, par.identifierType);
    par.file.endScope();
    // lookup the outline in the board
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = par.board.itemList.startReadObject();
    for (; ; ) {
      currOb = par.board.itemList.readObject(it);
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
      Shape outlineShape = par.coordinateTransform.boardToDsn(outline.getShape(i), Layer.SIGNAL);
      par.file.startScope();
      par.file.write("boundary");
      outlineShape.writeScope(par.file, par.identifierType);
      par.file.endScope();
    }
  }

  static void writeLayers(WriteScopeParameter par) throws IOException {
    for (int i = 0; i < par.board.layerStructure.arr.length; i++) {
      boolean writeLayerRule =
          par.board.rules.getDefaultNetClass().getTraceHalfWidth(i)
                  != par.board.rules.getDefaultNetClass().getTraceHalfWidth(0)
              || !clearanceEquals(par.board.rules.clearanceMatrix, i, 0);
      Layer.writeScope(par, i, writeLayerRule);
    }
  }

  static void writeDefaultRules(WriteScopeParameter par) throws IOException {
    // write the default rule using 0 as default layer.
    Rule.writeDefaultRule(par, 0);
  }

  private static void writeViaPadstacks(
      BoardLibrary library, IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.newLine();
    file.write("(via");
    for (int i = 0; i < library.viaPadstackCount(); i++) {
      Padstack currPadstack = library.getViaPadstack(i);
      if (currPadstack != null) {
        file.write(" ");
        identifierType.write(currPadstack.name, file);
      } else {
        FRLogger.warn("Structure.write_via_padstacks: padstack is null");
      }
    }
    file.write(")");
  }

  private static void writeControlScope(BoardRules rules, IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("control");
    file.newLine();
    file.write("(via_at_smd ");
    boolean viaAtSmdAllowed = false;
    for (int i = 0; i < rules.viaInfos.count(); i++) {
      if (rules.viaInfos.get(i).attachSmdAllowed()) {
        viaAtSmdAllowed = true;
        break;
      }
    }
    if (viaAtSmdAllowed) {
      file.write("on)");
    } else {
      file.write("off)");
    }
    file.endScope();
  }

  private static void writeKeepoutScope(WriteScopeParameter par, ObstacleArea keepout)
      throws IOException {
    Area keepoutArea = keepout.getArea();
    int layerNo = keepout.getLayer();
    app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[layerNo];
    final Layer keepoutLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (keepoutArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = keepoutArea.getBorder();
      holes = keepoutArea.getHoles();
    }
    par.file.startScope();
    if (keepout instanceof ViaObstacleArea) {
      par.file.write("via_keepout");
    } else {
      par.file.write("keepout");
    }
    Shape dsnShape = par.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(par.file, par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = par.coordinateTransform.boardToDsn(holes[i], keepoutLayer);
      dsnHole.writeHoleScope(par.file, par.identifierType);
    }
    // write clearance class if it's defined for this keepout area.
    if (keepout.clearanceClassNo() > 0) {
      // skip it if it's the default clearance class.
      String clearanceName = par.board.rules.clearanceMatrix.getName(keepout.clearanceClassNo());

      if (!"default".equals(clearanceName)) {
        Rule.writeItemClearanceClass(clearanceName, par.file, par.identifierType);
      }
    }
    par.file.endScope();
  }

  private static boolean readBoundaryScope(
      IJFlexScanner scanner, BoardConstructionInfo boardConstructionInfo) {
    Shape currShape = Shape.readScope(scanner, null);
    try {
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLEARANCE_CLASS) {
            boardConstructionInfo.outlineClearanceClassName = DsnFile.readStringScope(scanner);
          } else {
            Shape additionalShape = Shape.readScopeFromKeyword(scanner, nextToken, null);
            addBoundaryShape(boardConstructionInfo, additionalShape);
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
          "Structure.read_boundary_scope: shape is null at '" + scanner.getScopeIdentifier() + "'");
      return true;
    }
    addBoundaryShape(boardConstructionInfo, currShape);
    return true;
  }

  private static void addBoundaryShape(BoardConstructionInfo boardConstructionInfo, Shape shape) {
    if (shape == null) {
      return;
    }
    if (shape instanceof PolylinePath || shape instanceof PolygonPath) {
      boardConstructionInfo.outlineShapes.add(shape);
      return;
    }
    if (shape.layer == Layer.PCB) {
      if (boardConstructionInfo.boundingShape == null) {
        boardConstructionInfo.boundingShape = shape;
      } else {
        boardConstructionInfo.outlineShapes.add(shape);
      }
    } else if (shape.layer == Layer.SIGNAL) {
      boardConstructionInfo.outlineShapes.add(shape);
    } else {
      FRLogger.warn("Structure.add_boundary_shape: unexpected layer at boundary");
    }
  }

  static boolean readLayerScope(
      IJFlexScanner scanner, BoardConstructionInfo boardConstructionInfo, String stringQuote) {
    try {
      boolean layerOk = true;
      boolean isSignal = true;

      String layerString = scanner.nextString();

      Collection<String> netNames = new LinkedList<>();
      Object nextToken = scanner.nextToken();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Structure.read_layer_scope: ( expected at '" + scanner.getScopeIdentifier() + "'");
          return false;
        }
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.TYPE) {
          nextToken = scanner.nextToken();
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
                      + scanner.getScopeIdentifier()
                      + "'");
            }
            layerOk = false;
          }
          nextToken = scanner.nextToken();
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Structure.read_layer_scope: ) expected at '" + scanner.getScopeIdentifier() + "'");
            return false;
          }
        } else if (nextToken == Keyword.RULE) {
          Collection<Rule> currRules = Rule.readScope(scanner);
          boardConstructionInfo.layerDependentRules.add(new LayerRule(layerString, currRules));
        } else if (nextToken == Keyword.USE_NET) {
          for (; ; ) {
            scanner.yybegin(SpecctraDsnStreamReader.NAME);
            nextToken = scanner.nextToken();
            if (nextToken == Keyword.CLOSED_BRACKET) {
              break;
            }
            if (nextToken instanceof String string) {
              netNames.add(string);
            } else {
              FRLogger.warn(
                  "Structure.read_layer_scope: string expected at '"
                      + scanner.getScopeIdentifier()
                      + "'");
            }
          }
        } else {
          skipScope(scanner);
        }
        nextToken = scanner.nextToken();
      }
      if (layerOk) {
        final Layer currLayer =
            new Layer(layerString, boardConstructionInfo.foundLayerCount, isSignal, netNames);
        boardConstructionInfo.layerInfo.add(currLayer);
        ++boardConstructionInfo.foundLayerCount;
      }
    } catch (IOException e) {
      FRLogger.error("Layer.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }

  static Collection<String> readViaPadstacks(IJFlexScanner scanner) {
    try {
      Collection<String> normalVias = new LinkedList<>();
      Collection<String> spareVias = new LinkedList<>();
      for (; ; ) {
        Object nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          nextToken = scanner.nextToken();
          if (nextToken == Keyword.SPARE) {
            spareVias = readViaPadstacks(scanner);
          } else {
            skipScope(scanner);
          }
        } else if (nextToken instanceof String string) {
          normalVias.add(string);
        } else {
          FRLogger.warn(
              "Structure.read_via_padstack: String expected at '"
                  + scanner.getScopeIdentifier()
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

  private static boolean readControlScope(ReadScopeParameter par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_control_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_control_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.VIA_AT_SMD) {
          par.viaAtSmdAllowed = DsnFile.readOnOffScope(par.scanner);
        } else {
          skipScope(par.scanner);
        }
      }
    }
    return true;
  }

  public static AngleRestriction readSnapAngle(IJFlexScanner scanner) {
    try {
      Object nextToken = scanner.nextToken();
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
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Structure.read_selection_layer_scop: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return snapAngle;
    } catch (IOException e) {
      FRLogger.error("Structure.read_snap_angle: IO error scanning file", e);
      return null;
    }
  }

  public static void writeSnapAngle(IndentFileWriter file, AngleRestriction angleRestriction)
      throws IOException {
    file.startScope();
    file.write("snapAngle ");
    file.newLine();

    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      file.write("ninety_degree");
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      file.write("fortyfive_degree");
    } else {
      file.write("none");
    }
    file.endScope();
  }

  private static void insertMissingPowerPlanes(
      Collection<Layer> layerInfo, NetList netlist, BasicBoard board) {
    Collection<ConductionArea> conductionAreas = board.getConductionAreas();
    for (Layer currLayer : layerInfo) {
      if (currLayer.isSignal) {
        continue;
      }
      boolean conductionAreaFound = false;
      for (ConductionArea currConductionArea : conductionAreas) {
        if (currConductionArea.getLayer() == currLayer.no) {
          conductionAreaFound = true;
          break;
        }
      }
      if (!conductionAreaFound && !currLayer.netNames.isEmpty()) {
        String currNetName = currLayer.netNames.iterator().next();
        Net.Id currNetId = new Net.Id(currNetName, 1);
        if (!netlist.contains(currNetId)) {
          Net newNet = netlist.addNet(currNetId);
          if (newNet != null) {
            board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
          }
        }
        final app.freerouting.rules.Net currentNet =
            board.rules.nets.get(currNetId.name, currNetId.subnetNumber);
        {
          if (currentNet == null) {
            FRLogger.warn(
                "Structure.insert_missing_power_planes: net not found at '" + currNetId.name + "'");
            continue;
          }
        }
        int[] netNumbers = new int[1];
        netNumbers[0] = currentNet.netNumber;
        board.insertConductionArea(
            board.boundingBox,
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
  private static Collection<PolylineShape> separateHoles(Collection<PolylineShape> outlineShapes) {
    OutlineShape[] shapeArr = new OutlineShape[outlineShapes.size()];
    Iterator<PolylineShape> it = outlineShapes.iterator();
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
        outlineShapes.remove(shapeArr[i].shape);
        holeList.add(shapeArr[i].shape);
      }
    }
    return holeList;
  }

  // Check, if a conduction area is inserted on each plane,
  // and insert evtl. a conduction area

  /** Updates the board rules from the rules read from the dsn file. */
  private static void updateBoardRules(
      ReadScopeParameter par, BoardConstructionInfo boardConstructionInfo, BoardRules boardRules) {
    boolean smdToTurnGapFound = false;
    // update the clearance matrix
    for (Rule currOb : boardConstructionInfo.defaultRules) {
      if (currOb instanceof Rule.ClearanceRule currRule) {
        if (setClearanceRule(currRule, -1, par.coordinateTransform, boardRules, par.stringQuote)) {
          smdToTurnGapFound = true;
        }
      }
    }
    // update width rules
    for (Object currOb : boardConstructionInfo.defaultRules) {
      if (currOb instanceof Rule.WidthRule rule) {
        final double wireWidth = rule.value;
        int traceHalfwidth = (int) Math.round(par.coordinateTransform.dsnToBoard(wireWidth) / 2);
        FRLogger.debug(
            "Set default trace width (all layers): DSN="
                + wireWidth
                + " → board="
                + (traceHalfwidth * 2)
                + " ("
                + (traceHalfwidth * 2 / 40000.0)
                + " mm)");
        boardRules.setDefaultTraceHalfWidths(traceHalfwidth);
      }
    }
    for (LayerRule layerRule : boardConstructionInfo.layerDependentRules) {
      int layerNo = par.layerStructure.getNo(layerRule.layerName);
      if (layerNo < 0) {
        continue;
      }
      for (Rule currOb : layerRule.rule) {
        if (currOb instanceof Rule.WidthRule rule) {
          final double wireWidth = rule.value;
          int traceHalfwidth = (int) Math.round(par.coordinateTransform.dsnToBoard(wireWidth) / 2);
          boardRules.setDefaultTraceHalfWidth(layerNo, traceHalfwidth);
        } else if (currOb instanceof Rule.ClearanceRule currRule) {
          setClearanceRule(currRule, layerNo, par.coordinateTransform, boardRules, par.stringQuote);
        }
      }
    }
    if (!smdToTurnGapFound) {
      boardRules.setPinEdgeToTurnDist(boardRules.getMinTraceHalfWidth());
    }
  }

  /**
   * Converts a dsn clearance rule into a board clearance rule. If p_layer_no is negative, the rule
   * is set on all layers. Returns true, if the string smd_to_turn_gap was found.
   */
  public static boolean setClearanceRule(
      Rule.ClearanceRule rule,
      int layerNo,
      CoordinateTransform coordinateTransform,
      BoardRules boardRules,
      String stringQuote) {
    boolean result = false;
    int currClearance = (int) Math.round(coordinateTransform.dsnToBoard(rule.value));
    if (rule.clearanceClassPairs.isEmpty()) {
      if (layerNo < 0) {
        boardRules.clearanceMatrix.setDefaultValue(currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (all layers): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + rule.value);
      } else {
        boardRules.clearanceMatrix.setDefaultValue(layerNo, currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (layer "
                + layerNo
                + "): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + rule.value);
      }
      return result;
    }
    if (containsWireClearancePair(rule.clearanceClassPairs)) {
      createDefaultClearanceClasses(boardRules);
    }

    for (String currString : rule.clearanceClassPairs) {
      if ("smd_to_turn_gap".equalsIgnoreCase(currString)) {
        boardRules.setPinEdgeToTurnDist(currClearance);
        result = true;
        continue;
      }
      String[] currPair = new String[2];
      if (rule.clearanceClassPairs.size() == 2) {
        Iterator<String> iterator = rule.clearanceClassPairs.iterator();
        currPair[0] = iterator.next();
        currPair[1] = iterator.next();
        for (int i = 0; i < currPair.length; i++) {
          currPair[i] = currPair[i].replaceAll("[\"]", "");
          if (currPair[1].startsWith("_")) {
            currPair[1] = currPair[1].substring(1);
          }
        }
      } else if (currString.startsWith(stringQuote)) {
        // split at the second occurrence of p_string_quote
        currString = currString.substring(stringQuote.length());
        currPair = currString.split(stringQuote, 2);
        if (currPair.length != 2 || !currPair[1].startsWith("_")) {
          FRLogger.warn("Structure.set_clearance_rule: '_' expected at '" + currString + "'");
          FRLogger.warn(
              "You probably get this error because your clearance rule name has spaces or "
                  + "special characters in its name. Please change them first, and try again.");
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
        firstClassNo = boardRules.clearanceMatrix.getNo(currPair[0]);
      }
      if (firstClassNo < 0) {
        firstClassNo = appendClearanceClass(boardRules, currPair[0]);
      }
      int secondClassNo;
      if ("wire".equals(currPair[1])) {
        secondClassNo = 1; // default class
      } else {
        secondClassNo = boardRules.clearanceMatrix.getNo(currPair[1]);
      }
      if (secondClassNo < 0) {
        secondClassNo = appendClearanceClass(boardRules, currPair[1]);
      }
      if (layerNo < 0) {
        boardRules.clearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        boardRules.clearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
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
        boardRules.clearanceMatrix.setValue(firstClassNo, secondClassNo, layerNo, currClearance);
        boardRules.clearanceMatrix.setValue(secondClassNo, firstClassNo, layerNo, currClearance);
        FRLogger.debug(
            "Set clearance (layer "
                + layerNo
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

  static boolean containsWireClearancePair(Collection<String> clearancePairs) {
    for (String currPair : clearancePairs) {
      if (currPair.startsWith("wire_") || currPair.endsWith("_wire")) {
        return true;
      }
    }
    return false;
  }

  private static void createDefaultClearanceClasses(BoardRules boardRules) {
    appendClearanceClass(boardRules, "via");
    appendClearanceClass(boardRules, "smd");
    appendClearanceClass(boardRules, "pin");
    appendClearanceClass(boardRules, "area");
  }

  private static int appendClearanceClass(BoardRules boardRules, String name) {
    boardRules.clearanceMatrix.appendClass(name);
    int result = boardRules.clearanceMatrix.getNo(name);
    NetClass defaultNetClass = boardRules.getDefaultNetClass();
    switch (name) {
      case "via" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
      default -> {
        // Ignore unsupported item classes.
      }
    }
    return result;
  }

  /** Returns true, if all clearance values on the 2 input layers are equal. */
  private static boolean clearanceEquals(ClearanceMatrix clMatrix, int layer1, int layer2) {
    if (layer1 == layer2) {
      return true;
    }
    for (int i = 1; i < clMatrix.getClassCount(); i++) {
      for (int j = i; j < clMatrix.getClassCount(); j++) {
        if (clMatrix.getValue(i, j, layer1, false) != clMatrix.getValue(i, j, layer2, false)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean insertKeepout(
      Shape.ReadAreaScopeResult area,
      ReadScopeParameter par,
      KeepoutType keepoutType,
      FixedState fixedState) {
    Area keepoutArea = Shape.transformAreaToBoard(area.shapeList, par.coordinateTransform);
    if (keepoutArea.dimension() < 2) {
      // A degenerate keepout (e.g. all polygon vertices identical, exported incorrectly by the EDA
      // tool) cannot be enforced as a routing constraint. The board remains valid — the keepout
      // restriction is simply not applied, making routing more permissive in that area.
      // This is a known export defect in some EDA tools (e.g. KiCad 4.0.7).
      FRLogger.warn(
          "Keepout zone '"
              + area.areaName
              + "' was skipped because its geometry is degenerate "
              + "(e.g. zero-area polygon). This is likely a DSN export issue in your EDA tool. "
              + "The board will be routed without this keepout constraint.");
      return true;
    }
    BasicBoard board = par.boardHandling.getRoutingBoard();
    if (board == null) {
      FRLogger.warn("Structure.insert_keepout: board not initialized");
      return false;
    }
    final Layer currLayer = (area.shapeList.iterator().next()).layer;
    if (currLayer == Layer.SIGNAL) {
      for (int i = 0; i < board.getLayerCount(); i++) {
        if (par.layerStructure.arr[i].isSignal) {
          insertKeepout(board, keepoutArea, i, area.clearanceClassName, keepoutType, fixedState);
        }
      }
    } else if (currLayer.no >= 0) {
      insertKeepout(
          board, keepoutArea, currLayer.no, area.clearanceClassName, keepoutType, fixedState);
    } else {
      FRLogger.warn(
          "Structure.insert_keepout: unknown layer name at '"
              + par.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    return true;
  }

  private static void insertKeepout(
      BasicBoard board,
      Area area,
      int layer,
      String clearanceClassName,
      KeepoutType keepoutType,
      FixedState fixedState) {
    int clearanceClassNo;
    if (clearanceClassName == null) {
      clearanceClassNo =
          board
              .rules
              .getDefaultNetClass()
              .defaultItemClearanceClasses
              .get(DefaultItemClearanceClasses.ItemClass.AREA);
    } else {
      clearanceClassNo = board.rules.clearanceMatrix.getNo(clearanceClassName);
      if (clearanceClassNo < 0) {
        FRLogger.warn(
            "Keepout.insert_keepout: clearance class not found at '" + clearanceClassName + "'");
        clearanceClassNo = BoardRules.clearanceClassNone();
      }
    }
    if (keepoutType == KeepoutType.via_keepout) {
      board.insertViaObstacle(area, layer, clearanceClassNo, fixedState);
    } else if (keepoutType == KeepoutType.place_keepout) {
      board.insertComponentObstacle(area, layer, clearanceClassNo, fixedState);
    } else {
      board.insertObstacle(area, layer, clearanceClassNo, fixedState);
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter par) {
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
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
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
          readBoundaryScope(par.scanner, boardConstructionInfo);
        } else if (nextToken == Keyword.LAYER) {
          readOk = readLayerScope(par.scanner, boardConstructionInfo, par.stringQuote);
          if (par.layerStructure != null) {
            // correct the layerStructure because another layer isr read
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
        } else if (nextToken == Keyword.VIA) {
          par.viaPadstackNames = readViaPadstacks(par.scanner);
        } else if (nextToken == Keyword.RULE) {
          boardConstructionInfo.defaultRules.addAll(Rule.readScope(par.scanner));
        } else if (nextToken == Keyword.KEEPOUT) {
          if (par.layerStructure == null) {
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          keepoutList.add(Shape.readAreaScope(par.scanner, par.layerStructure, false));
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (par.layerStructure == null) {
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          viaKeepoutList.add(Shape.readAreaScope(par.scanner, par.layerStructure, false));
        } else if (nextToken == Keyword.PLACE_KEEPOUT) {
          if (par.layerStructure == null) {
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          placeKeepoutList.add(Shape.readAreaScope(par.scanner, par.layerStructure, false));
        } else if (nextToken == Keyword.PLANE_SCOPE) {
          if (par.layerStructure == null) {
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          Keyword.PLANE_SCOPE.readScope(par);
        } else if (nextToken == Keyword.AUTOROUTE_SETTINGS) {
          if (par.layerStructure == null) {
            par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
            par.autorouteSettings = AutorouteSettings.readScope(par.scanner, par.layerStructure);
          }
        } else if (nextToken == Keyword.CONTROL) {
          readOk = readControlScope(par);
        } else if (nextToken == Keyword.FLIP_STYLE) {
          flipStyleRotateFirst = PlaceControl.readFlipStyleRotateFirst(par.scanner);
        } else if (nextToken == Keyword.SNAP_ANGLE) {

          AngleRestriction snapAngle = readSnapAngle(par.scanner);
          if (snapAngle != null) {
            par.snapAngle = snapAngle;
          }
        } else {
          skipScope(par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }

    // let's create a board based on the data we read (TODO: move this method
    // somewhere outside of the designforms.specctra package)
    boolean result = true;
    if (par.boardHandling.getRoutingBoard() == null) {
      result = createBoard(par, boardConstructionInfo);
    }
    RoutingBoard board = par.boardHandling.getRoutingBoard();
    if (board == null) {
      return false;
    }
    if (flipStyleRotateFirst) {
      board.components.setFlipStyleRotateFirst(true);
    }

    // insert the keepouts
    for (Shape.ReadAreaScopeResult currArea : keepoutList) {
      if (!insertKeepout(currArea, par, KeepoutType.keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : viaKeepoutList) {
      if (!insertKeepout(currArea, par, KeepoutType.via_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : placeKeepoutList) {
      if (!insertKeepout(currArea, par, KeepoutType.place_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    // insert the planes.
    for (ReadScopeParameter.PlaneInfo planeInfo : par.planeList) {
      Net.Id netId = new Net.Id(planeInfo.netName, 1);
      if (!par.netlist.contains(netId)) {
        Net newNet = par.netlist.addNet(netId);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
        }
      }
      final app.freerouting.rules.Net currentNet = board.rules.nets.get(planeInfo.netName, 1);
      if (currentNet == null) {
        FRLogger.warn(
            "Plane.read_scope: net not found at '" + par.scanner.getScopeIdentifier() + "'");
        continue;
      }
      Area planeArea =
          Shape.transformAreaToBoard(planeInfo.area.shapeList, par.coordinateTransform);
      final Layer currLayer = (planeInfo.area.shapeList.iterator().next()).layer;
      if (currLayer.no >= 0) {
        int clearanceClassNo;
        if (planeInfo.area.clearanceClassName != null) {
          clearanceClassNo = board.rules.clearanceMatrix.getNo(planeInfo.area.clearanceClassName);
          if (clearanceClassNo < 0) {
            FRLogger.warn(
                "Structure.read_scope: clearance class not found at '"
                    + par.scanner.getScopeIdentifier()
                    + "'");
            clearanceClassNo = BoardRules.clearanceClassNone();
          }
        } else {
          clearanceClassNo =
              currentNet
                  .getNetClass()
                  .defaultItemClearanceClasses
                  .get(DefaultItemClearanceClasses.ItemClass.AREA);
        }
        int[] netNumbers = new int[1];
        netNumbers[0] = currentNet.netNumber;
        board.insertConductionArea(
            planeArea, currLayer.no, netNumbers, clearanceClassNo, false, FixedState.SYSTEM_FIXED);
      } else {
        FRLogger.warn(
            "Plane.read_scope: unexpected layer name at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
    }
    insertMissingPowerPlanes(boardConstructionInfo.layerInfo, par.netlist, board);

    // Apply DSN autoroute settings to the current routing job if they were parsed
    if (par.autorouteSettings != null) {
      // Get the current routing job from the board manager
      RoutingJob currentJob = par.boardHandling.getCurrentRoutingJob();
      if (currentJob != null && currentJob.routerSettings != null) {
        // Apply the DSN file's autoroute settings to the routing job
        currentJob.routerSettings.applyNewValuesFrom(par.autorouteSettings);
        FRLogger.info("Applied DSN autoroute settings to routing job");
      }
    }

    return result;
  }

  private boolean createBoard(ReadScopeParameter par, BoardConstructionInfo boardConstructionInfo) {
    int layerCount = boardConstructionInfo.layerInfo.size();
    if (layerCount == 0) {
      FRLogger.warn(
          "Structure.create_board: layers missing in structure scope at '"
              + par.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (boardConstructionInfo.boundingShape == null) {
      // happens if the boundary shape with layer pcb is missing
      if (boardConstructionInfo.outlineShapes.isEmpty()) {
        FRLogger.warn(
            "Structure.create_board: outline missing at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        par.boardOutlineOk = false;
        return false;
      }
      Iterator<Shape> it = boardConstructionInfo.outlineShapes.iterator();

      Rectangle boundingBox = it.next().boundingBox();
      while (it.hasNext()) {
        boundingBox = boundingBox.union(it.next().boundingBox());
      }
      boardConstructionInfo.boundingShape = boundingBox;
    }
    Rectangle boundingBox = boardConstructionInfo.boundingShape.boundingBox();
    app.freerouting.board.Layer[] boardLayerArr = new app.freerouting.board.Layer[layerCount];
    Iterator<Layer> it = boardConstructionInfo.layerInfo.iterator();
    for (int i = 0; i < layerCount; i++) {
      final Layer currLayer = it.next();
      if (currLayer.no < 0 || currLayer.no >= layerCount) {
        FRLogger.warn(
            "Structure.create_board: illegal layer number at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      boardLayerArr[i] = new app.freerouting.board.Layer(currLayer.name, currLayer.isSignal);
    }
    final app.freerouting.board.LayerStructure boardLayerStructure =
        new app.freerouting.board.LayerStructure(boardLayerArr);
    par.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);

    // Calculate an approximate scaling between dsn coordinates and board
    // coordinates.
    int scaleFactor = Math.max(par.resolution, 1);

    double maxCoor = 0;
    for (int i = 0; i < 4; i++) {
      maxCoor = Math.max(maxCoor, Math.abs(boundingBox.coor[i] * par.resolution));
    }
    if (maxCoor == 0) {
      par.boardOutlineOk = false;
      return false;
    }
    // make scalefactor smaller, if there is a danger of integer overflow.
    while (5 * maxCoor >= Limits.CRIT_INT) {
      scaleFactor /= 10;
      maxCoor /= 10;
    }

    par.coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);

    IntBox bounds = (IntBox) boundingBox.transformToBoard(par.coordinateTransform);
    bounds = bounds.offset(1000);

    Collection<PolylineShape> boardOutlineShapes = new LinkedList<>();
    for (Shape currShape : boardConstructionInfo.outlineShapes) {
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
          (PolylineShape) currShape.transformToBoard(par.coordinateTransform);
      if (currBoardShape.dimension() > 0) {
        boardOutlineShapes.add(currBoardShape);
      }
    }
    if (boardOutlineShapes.isEmpty()) {
      // construct an outline from the boundingShape, if the outline is missing.
      PolylineShape currBoardShape =
          (PolylineShape)
              boardConstructionInfo.boundingShape.transformToBoard(par.coordinateTransform);
      boardOutlineShapes.add(currBoardShape);
    }
    final Collection<PolylineShape> holeShapes = separateHoles(boardOutlineShapes);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(boardLayerStructure, 0);
    BoardRules boardRules = new BoardRules(boardLayerStructure, clearanceMatrix);
    Communication.SpecctraParserInfo specctraParserInfo =
        new Communication.SpecctraParserInfo(
            par.stringQuote,
            par.hostCad,
            par.hostVersion,
            par.constants,
            par.writeResolution,
            par.dsnFileGeneratedByHost);
    Communication boardCommunication =
        new Communication(
            par.unit,
            par.resolution,
            specctraParserInfo,
            par.coordinateTransform,
            par.itemIdNoGenerator,
            par.observers);

    if (boardCommunication.hostIsOldKicad()) {
      FRLogger.warn(
          "Structure.create_board: The DSN file was exported from an old KiCad version "
              + "that has known compatibility issues. Please update KiCad to version 6 or newer.");
    }

    PolylineShape[] outlineShapeArr = new PolylineShape[boardOutlineShapes.size()];
    Iterator<PolylineShape> it2 = boardOutlineShapes.iterator();
    for (int i = 0; i < outlineShapeArr.length; i++) {
      outlineShapeArr[i] = it2.next();
    }
    updateBoardRules(par, boardConstructionInfo, boardRules);
    boardRules.setTraceAngleRestriction(par.snapAngle);
    par.boardHandling.createBoard(
        bounds,
        boardLayerStructure,
        outlineShapeArr,
        boardConstructionInfo.outlineClearanceClassName,
        boardRules,
        boardCommunication);

    BasicBoard board = par.boardHandling.getRoutingBoard();

    // Insert the holes in the board outline as keepouts.
    for (PolylineShape currOutlineHole : holeShapes) {
      for (int i = 0; i < boardLayerStructure.arr.length; i++) {
        board.insertObstacle(currOutlineHole, i, 0, FixedState.SYSTEM_FIXED);
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

    LayerRule(String layerName, Collection<Rule> rule) {
      this.layerName = layerName;
      this.rule = rule;
    }
  }

  /** Used to separate the holes in the outline. */
  private static class OutlineShape {

    final PolylineShape shape;
    final IntBox boundingBox;
    final TileShape[] convexShapes;
    boolean isHole;

    public OutlineShape(PolylineShape shape) {
      this.shape = shape;
      boundingBox = shape.boundingBox();
      convexShapes = shape.splitToConvex();
      isHole = false;
    }

    /** Returns true, if this shape contains all corners of p_other_shape. */
    private boolean containsAllCorners(OutlineShape otherShape) {
      if (this.convexShapes == null) {
        // calculation of the convex shapes failed
        return false;
      }
      int cornerCount = otherShape.shape.borderLineCount();
      for (int i = 0; i < cornerCount; i++) {
        Point currCorner = otherShape.shape.corner(i);
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
