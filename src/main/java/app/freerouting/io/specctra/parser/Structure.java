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

  public static void writeScope(WriteScopeParameter pPar) throws IOException {
    pPar.file.startScope();
    pPar.file.write("structure");

    // write the layer structure
    writeLayers(pPar);

    // write the boundaries
    writeBoundaries(pPar);

    // write the routing vias
    writeViaPadstacks(pPar.board.library, pPar.file, pPar.identifierType);

    // write the rules
    writeDefaultRules(pPar);

    // write the snap angles
    writeSnapAngle(pPar.file, pPar.board.rules.getTraceAngleRestriction());

    // write the control scope
    writeControlScope(pPar.board.rules, pPar.file);

    if (pPar.autorouteSettings != null) {
      // write the auto-route settings
      AutorouteSettings.writeScope(
          pPar.file, pPar.autorouteSettings, pPar.board.layerStructure, pPar.identifierType);
    }

    // write the conduction areas
    writeConductionAreas(pPar);

    // write the keepouts
    writeKeepouts(pPar);

    pPar.file.endScope();
  }

  private static void writeConductionAreas(WriteScopeParameter pPar) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = pPar.board.itemList.startReadObject();
    for (; ; ) {
      currOb = pPar.board.itemList.readObject(it);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (pPar.board.layerStructure.arr[currArea.getLayer()].isSignal) {
        // These conduction areas are written in the wiring scope.
        continue;
      }
      Plane.writeScope(pPar, currArea);
    }
  }

  private static void writeKeepouts(WriteScopeParameter pPar) throws IOException {
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = pPar.board.itemList.startReadObject();
    for (; ; ) {
      currOb = pPar.board.itemList.readObject(it);
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
      writeKeepoutScope(pPar, currKeepout);
    }
  }

  private static void writeBoundaries(WriteScopeParameter pPar) throws IOException {
    // write the bounding box
    pPar.file.startScope();
    pPar.file.write("boundary");
    IntBox bounds = pPar.board.getBoundingBox();
    double[] rectCoor = pPar.coordinateTransform.boardToDsn(bounds);
    Rectangle boundingRectangle = new Rectangle(Layer.PCB, rectCoor);
    boundingRectangle.writeScope(pPar.file, pPar.identifierType);
    pPar.file.endScope();
    // lookup the outline in the board
    Storable currOb;
    Iterator<UndoableObjects.UndoableObjectNode> it = pPar.board.itemList.startReadObject();
    for (; ; ) {
      currOb = pPar.board.itemList.readObject(it);
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
      Shape outlineShape = pPar.coordinateTransform.boardToDsn(outline.getShape(i), Layer.SIGNAL);
      pPar.file.startScope();
      pPar.file.write("boundary");
      outlineShape.writeScope(pPar.file, pPar.identifierType);
      pPar.file.endScope();
    }
  }

  static void writeLayers(WriteScopeParameter pPar) throws IOException {
    for (int i = 0; i < pPar.board.layerStructure.arr.length; i++) {
      boolean writeLayerRule =
          pPar.board.rules.getDefaultNetClass().getTraceHalfWidth(i)
                  != pPar.board.rules.getDefaultNetClass().getTraceHalfWidth(0)
              || !clearanceEquals(pPar.board.rules.clearanceMatrix, i, 0);
      Layer.writeScope(pPar, i, writeLayerRule);
    }
  }

  static void writeDefaultRules(WriteScopeParameter pPar) throws IOException {
    // write the default rule using 0 as default layer.
    Rule.writeDefaultRule(pPar, 0);
  }

  private static void writeViaPadstacks(
      BoardLibrary pLibrary, IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.newLine();
    pFile.write("(via");
    for (int i = 0; i < pLibrary.viaPadstackCount(); i++) {
      Padstack currPadstack = pLibrary.getViaPadstack(i);
      if (currPadstack != null) {
        pFile.write(" ");
        pIdentifierType.write(currPadstack.name, pFile);
      } else {
        FRLogger.warn("Structure.write_via_padstacks: padstack is null");
      }
    }
    pFile.write(")");
  }

  private static void writeControlScope(BoardRules pRules, IndentFileWriter pFile)
      throws IOException {
    pFile.startScope();
    pFile.write("control");
    pFile.newLine();
    pFile.write("(via_at_smd ");
    boolean viaAtSmdAllowed = false;
    for (int i = 0; i < pRules.viaInfos.count(); i++) {
      if (pRules.viaInfos.get(i).attachSmdAllowed()) {
        viaAtSmdAllowed = true;
        break;
      }
    }
    if (viaAtSmdAllowed) {
      pFile.write("on)");
    } else {
      pFile.write("off)");
    }
    pFile.endScope();
  }

  private static void writeKeepoutScope(WriteScopeParameter pPar, ObstacleArea pKeepout)
      throws IOException {
    Area keepoutArea = pKeepout.getArea();
    int layerNo = pKeepout.getLayer();
    app.freerouting.board.Layer boardLayer = pPar.board.layerStructure.arr[layerNo];
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
    pPar.file.startScope();
    if (pKeepout instanceof ViaObstacleArea) {
      pPar.file.write("via_keepout");
    } else {
      pPar.file.write("keepout");
    }
    Shape dsnShape = pPar.coordinateTransform.boardToDsn(boundaryShape, keepoutLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(pPar.file, pPar.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = pPar.coordinateTransform.boardToDsn(holes[i], keepoutLayer);
      dsnHole.writeHoleScope(pPar.file, pPar.identifierType);
    }
    // write clearance class if it's defined for this keepout area.
    if (pKeepout.clearanceClassNo() > 0) {
      // skip it if it's the default clearance class.
      String clearanceName = pPar.board.rules.clearanceMatrix.getName(pKeepout.clearanceClassNo());

      if (!"default".equals(clearanceName)) {
        Rule.writeItemClearanceClass(clearanceName, pPar.file, pPar.identifierType);
      }
    }
    pPar.file.endScope();
  }

  private static boolean readBoundaryScope(
      IJFlexScanner pScanner, BoardConstructionInfo pBoardConstructionInfo) {
    Shape currShape = Shape.readScope(pScanner, null);
    try {
      Object prevToken = null;
      for (; ; ) {
        Object nextToken = pScanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (prevToken == Keyword.OPEN_BRACKET) {
          if (nextToken == Keyword.CLEARANCE_CLASS) {
            pBoardConstructionInfo.outlineClearanceClassName = DsnFile.readStringScope(pScanner);
          } else {
            Shape additionalShape = Shape.readScopeFromKeyword(pScanner, nextToken, null);
            addBoundaryShape(pBoardConstructionInfo, additionalShape);
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
              + pScanner.getScopeIdentifier()
              + "'");
      return true;
    }
    addBoundaryShape(pBoardConstructionInfo, currShape);
    return true;
  }

  private static void addBoundaryShape(BoardConstructionInfo pBoardConstructionInfo, Shape shape) {
    if (shape == null) {
      return;
    }
    if (shape instanceof PolylinePath || shape instanceof PolygonPath) {
      pBoardConstructionInfo.outlineShapes.add(shape);
      return;
    }
    if (shape.layer == Layer.PCB) {
      if (pBoardConstructionInfo.boundingShape == null) {
        pBoardConstructionInfo.boundingShape = shape;
      } else {
        pBoardConstructionInfo.outlineShapes.add(shape);
      }
    } else if (shape.layer == Layer.SIGNAL) {
      pBoardConstructionInfo.outlineShapes.add(shape);
    } else {
      FRLogger.warn("Structure.add_boundary_shape: unexpected layer at boundary");
    }
  }

  static boolean readLayerScope(
      IJFlexScanner pScanner, BoardConstructionInfo pBoardConstructionInfo, String pStringQuote) {
    try {
      boolean layerOk = true;
      boolean isSignal = true;

      String layerString = pScanner.nextString();

      Collection<String> netNames = new LinkedList<>();
      Object nextToken = pScanner.nextToken();
      while (nextToken != Keyword.CLOSED_BRACKET) {
        if (nextToken != Keyword.OPEN_BRACKET) {
          FRLogger.warn(
              "Structure.read_layer_scope: ( expected at '" + pScanner.getScopeIdentifier() + "'");
          return false;
        }
        nextToken = pScanner.nextToken();
        if (nextToken == Keyword.TYPE) {
          nextToken = pScanner.nextToken();
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
                      + pScanner.getScopeIdentifier()
                      + "'");
            }
            layerOk = false;
          }
          nextToken = pScanner.nextToken();
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Structure.read_layer_scope: ) expected at '"
                    + pScanner.getScopeIdentifier()
                    + "'");
            return false;
          }
        } else if (nextToken == Keyword.RULE) {
          Collection<Rule> currRules = Rule.readScope(pScanner);
          pBoardConstructionInfo.layerDependentRules.add(new LayerRule(layerString, currRules));
        } else if (nextToken == Keyword.USE_NET) {
          for (; ; ) {
            pScanner.yybegin(SpecctraDsnStreamReader.NAME);
            nextToken = pScanner.nextToken();
            if (nextToken == Keyword.CLOSED_BRACKET) {
              break;
            }
            if (nextToken instanceof String string) {
              netNames.add(string);
            } else {
              FRLogger.warn(
                  "Structure.read_layer_scope: string expected at '"
                      + pScanner.getScopeIdentifier()
                      + "'");
            }
          }
        } else {
          skipScope(pScanner);
        }
        nextToken = pScanner.nextToken();
      }
      if (layerOk) {
        Layer currLayer =
            new Layer(layerString, pBoardConstructionInfo.foundLayerCount, isSignal, netNames);
        pBoardConstructionInfo.layerInfo.add(currLayer);
        ++pBoardConstructionInfo.foundLayerCount;
      }
    } catch (IOException e) {
      FRLogger.error("Layer.read_scope: IO error scanning file", e);
      return false;
    }
    return true;
  }

  static Collection<String> readViaPadstacks(IJFlexScanner pScanner) {
    try {
      Collection<String> normalVias = new LinkedList<>();
      Collection<String> spareVias = new LinkedList<>();
      for (; ; ) {
        Object nextToken = pScanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          nextToken = pScanner.nextToken();
          if (nextToken == Keyword.SPARE) {
            spareVias = readViaPadstacks(pScanner);
          } else {
            skipScope(pScanner);
          }
        } else if (nextToken instanceof String string) {
          normalVias.add(string);
        } else {
          FRLogger.warn(
              "Structure.read_via_padstack: String expected at '"
                  + pScanner.getScopeIdentifier()
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

  private static boolean readControlScope(ReadScopeParameter pPar) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_control_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_control_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == Keyword.VIA_AT_SMD) {
          pPar.viaAtSmdAllowed = DsnFile.readOnOffScope(pPar.scanner);
        } else {
          skipScope(pPar.scanner);
        }
      }
    }
    return true;
  }

  public static AngleRestriction readSnapAngle(IJFlexScanner pScanner) {
    try {
      Object nextToken = pScanner.nextToken();
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
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Structure.read_selection_layer_scop: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return snapAngle;
    } catch (IOException e) {
      FRLogger.error("Structure.read_snap_angle: IO error scanning file", e);
      return null;
    }
  }

  public static void writeSnapAngle(IndentFileWriter pFile, AngleRestriction pAngleRestriction)
      throws IOException {
    pFile.startScope();
    pFile.write("snapAngle ");
    pFile.newLine();

    if (pAngleRestriction == AngleRestriction.NINETY_DEGREE) {
      pFile.write("ninety_degree");
    } else if (pAngleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      pFile.write("fortyfive_degree");
    } else {
      pFile.write("none");
    }
    pFile.endScope();
  }

  private static void insertMissingPowerPlanes(
      Collection<Layer> pLayerInfo, NetList pNetlist, BasicBoard pBoard) {
    Collection<ConductionArea> conductionAreas = pBoard.getConductionAreas();
    for (Layer currLayer : pLayerInfo) {
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
        if (!pNetlist.contains(currNetId)) {
          Net newNet = pNetlist.addNet(currNetId);
          if (newNet != null) {
            pBoard.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
          }
        }
        app.freerouting.rules.Net currentNet =
            pBoard.rules.nets.get(currNetId.name, currNetId.subnetNumber);
        {
          if (currentNet == null) {
            FRLogger.warn(
                "Structure.insert_missing_power_planes: net not found at '" + currNetId.name + "'");
            continue;
          }
        }
        int[] netNumbers = new int[1];
        netNumbers[0] = currentNet.netNumber;
        pBoard.insertConductionArea(
            pBoard.boundingBox,
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
  private static Collection<PolylineShape> separateHoles(Collection<PolylineShape> pOutlineShapes) {
    OutlineShape[] shapeArr = new OutlineShape[pOutlineShapes.size()];
    Iterator<PolylineShape> it = pOutlineShapes.iterator();
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
        pOutlineShapes.remove(shapeArr[i].shape);
        holeList.add(shapeArr[i].shape);
      }
    }
    return holeList;
  }

  // Check, if a conduction area is inserted on each plane,
  // and insert evtl. a conduction area

  /** Updates the board rules from the rules read from the dsn file. */
  private static void updateBoardRules(
      ReadScopeParameter pPar,
      BoardConstructionInfo pBoardConstructionInfo,
      BoardRules pBoardRules) {
    boolean smdToTurnGapFound = false;
    // update the clearance matrix
    for (Rule currOb : pBoardConstructionInfo.defaultRules) {
      if (currOb instanceof Rule.ClearanceRule currRule) {
        if (setClearanceRule(
            currRule, -1, pPar.coordinateTransform, pBoardRules, pPar.stringQuote)) {
          smdToTurnGapFound = true;
        }
      }
    }
    // update width rules
    for (Object currOb : pBoardConstructionInfo.defaultRules) {
      if (currOb instanceof Rule.WidthRule rule) {
        double wireWidth = rule.value;
        int traceHalfwidth = (int) Math.round(pPar.coordinateTransform.dsnToBoard(wireWidth) / 2);
        FRLogger.debug(
            "Set default trace width (all layers): DSN="
                + wireWidth
                + " → board="
                + (traceHalfwidth * 2)
                + " ("
                + (traceHalfwidth * 2 / 40000.0)
                + " mm)");
        pBoardRules.setDefaultTraceHalfWidths(traceHalfwidth);
      }
    }
    for (LayerRule layerRule : pBoardConstructionInfo.layerDependentRules) {
      int layerNo = pPar.layerStructure.getNo(layerRule.layerName);
      if (layerNo < 0) {
        continue;
      }
      for (Rule currOb : layerRule.rule) {
        if (currOb instanceof Rule.WidthRule rule) {
          double wireWidth = rule.value;
          int traceHalfwidth = (int) Math.round(pPar.coordinateTransform.dsnToBoard(wireWidth) / 2);
          pBoardRules.setDefaultTraceHalfWidth(layerNo, traceHalfwidth);
        } else if (currOb instanceof Rule.ClearanceRule currRule) {
          setClearanceRule(
              currRule, layerNo, pPar.coordinateTransform, pBoardRules, pPar.stringQuote);
        }
      }
    }
    if (!smdToTurnGapFound) {
      pBoardRules.setPinEdgeToTurnDist(pBoardRules.getMinTraceHalfWidth());
    }
  }

  /**
   * Converts a dsn clearance rule into a board clearance rule. If p_layer_no is negative, the rule
   * is set on all layers. Returns true, if the string smd_to_turn_gap was found.
   */
  public static boolean setClearanceRule(
      Rule.ClearanceRule pRule,
      int pLayerNo,
      CoordinateTransform pCoordinateTransform,
      BoardRules pBoardRules,
      String pStringQuote) {
    boolean result = false;
    int currClearance = (int) Math.round(pCoordinateTransform.dsnToBoard(pRule.value));
    if (pRule.clearanceClassPairs.isEmpty()) {
      if (pLayerNo < 0) {
        pBoardRules.clearanceMatrix.setDefaultValue(currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (all layers): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + pRule.value);
      } else {
        pBoardRules.clearanceMatrix.setDefaultValue(pLayerNo, currClearance);
        FRLogger.debug(
            "Set DEFAULT clearance (layer "
                + pLayerNo
                + "): "
                + currClearance
                + " ("
                + (currClearance / 40000.0)
                + " mm) from DSN value "
                + pRule.value);
      }
      return result;
    }
    if (containsWireClearancePair(pRule.clearanceClassPairs)) {
      createDefaultClearanceClasses(pBoardRules);
    }

    for (String currString : pRule.clearanceClassPairs) {
      if ("smd_to_turn_gap".equalsIgnoreCase(currString)) {
        pBoardRules.setPinEdgeToTurnDist(currClearance);
        result = true;
        continue;
      }
      String[] currPair = new String[2];
      if (pRule.clearanceClassPairs.size() == 2) {
        Iterator<String> iterator = pRule.clearanceClassPairs.iterator();
        currPair[0] = iterator.next();
        currPair[1] = iterator.next();
        for (int i = 0; i < currPair.length; i++) {
          currPair[i] = currPair[i].replaceAll("[\"]", "");
          if (currPair[1].startsWith("_")) {
            currPair[1] = currPair[1].substring(1);
          }
        }
      } else if (currString.startsWith(pStringQuote)) {
        // split at the second occurrence of p_string_quote
        currString = currString.substring(pStringQuote.length());
        currPair = currString.split(pStringQuote, 2);
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
        firstClassNo = pBoardRules.clearanceMatrix.getNo(currPair[0]);
      }
      if (firstClassNo < 0) {
        firstClassNo = appendClearanceClass(pBoardRules, currPair[0]);
      }
      int secondClassNo;
      if ("wire".equals(currPair[1])) {
        secondClassNo = 1; // default class
      } else {
        secondClassNo = pBoardRules.clearanceMatrix.getNo(currPair[1]);
      }
      if (secondClassNo < 0) {
        secondClassNo = appendClearanceClass(pBoardRules, currPair[1]);
      }
      if (pLayerNo < 0) {
        pBoardRules.clearanceMatrix.setValue(firstClassNo, secondClassNo, currClearance);
        pBoardRules.clearanceMatrix.setValue(secondClassNo, firstClassNo, currClearance);
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
        pBoardRules.clearanceMatrix.setValue(firstClassNo, secondClassNo, pLayerNo, currClearance);
        pBoardRules.clearanceMatrix.setValue(secondClassNo, firstClassNo, pLayerNo, currClearance);
        FRLogger.debug(
            "Set clearance (layer "
                + pLayerNo
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

  static boolean containsWireClearancePair(Collection<String> pClearancePairs) {
    for (String currPair : pClearancePairs) {
      if (currPair.startsWith("wire_") || currPair.endsWith("_wire")) {
        return true;
      }
    }
    return false;
  }

  private static void createDefaultClearanceClasses(BoardRules pBoardRules) {
    appendClearanceClass(pBoardRules, "via");
    appendClearanceClass(pBoardRules, "smd");
    appendClearanceClass(pBoardRules, "pin");
    appendClearanceClass(pBoardRules, "area");
  }

  private static int appendClearanceClass(BoardRules pBoardRules, String pName) {
    pBoardRules.clearanceMatrix.appendClass(pName);
    int result = pBoardRules.clearanceMatrix.getNo(pName);
    NetClass defaultNetClass = pBoardRules.getDefaultNetClass();
    switch (pName) {
      case "via" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.VIA, result);
      case "pin" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.PIN, result);
      case "smd" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.SMD, result);
      case "area" -> defaultNetClass.defaultItemClearanceClasses.set(ItemClass.AREA, result);
    }
    return result;
  }

  /** Returns true, if all clearance values on the 2 input layers are equal. */
  private static boolean clearanceEquals(ClearanceMatrix pClMatrix, int pLayer1, int pLayer2) {
    if (pLayer1 == pLayer2) {
      return true;
    }
    for (int i = 1; i < pClMatrix.getClassCount(); i++) {
      for (int j = i; j < pClMatrix.getClassCount(); j++) {
        if (pClMatrix.getValue(i, j, pLayer1, false) != pClMatrix.getValue(i, j, pLayer2, false)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean insertKeepout(
      Shape.ReadAreaScopeResult pArea,
      ReadScopeParameter pPar,
      KeepoutType pKeepoutType,
      FixedState pFixedState) {
    Area keepoutArea = Shape.transformAreaToBoard(pArea.shapeList, pPar.coordinateTransform);
    if (keepoutArea.dimension() < 2) {
      // A degenerate keepout (e.g. all polygon vertices identical, exported incorrectly by the EDA
      // tool) cannot be enforced as a routing constraint. The board remains valid — the keepout
      // restriction is simply not applied, making routing more permissive in that area.
      // This is a known export defect in some EDA tools (e.g. KiCad 4.0.7).
      FRLogger.warn(
          "Keepout zone '"
              + pArea.areaName
              + "' was skipped because its geometry is degenerate "
              + "(e.g. zero-area polygon). This is likely a DSN export issue in your EDA tool. "
              + "The board will be routed without this keepout constraint.");
      return true;
    }
    BasicBoard board = pPar.boardHandling.getRoutingBoard();
    if (board == null) {
      FRLogger.warn("Structure.insert_keepout: board not initialized");
      return false;
    }
    Layer currLayer = (pArea.shapeList.iterator().next()).layer;
    if (currLayer == Layer.SIGNAL) {
      for (int i = 0; i < board.getLayerCount(); i++) {
        if (pPar.layerStructure.arr[i].isSignal) {
          insertKeepout(board, keepoutArea, i, pArea.clearanceClassName, pKeepoutType, pFixedState);
        }
      }
    } else if (currLayer.no >= 0) {
      insertKeepout(
          board, keepoutArea, currLayer.no, pArea.clearanceClassName, pKeepoutType, pFixedState);
    } else {
      FRLogger.warn(
          "Structure.insert_keepout: unknown layer name at '"
              + pPar.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    return true;
  }

  private static void insertKeepout(
      BasicBoard pBoard,
      Area pArea,
      int pLayer,
      String pClearanceClassName,
      KeepoutType pKeepoutType,
      FixedState pFixedState) {
    int clearanceClassNo;
    if (pClearanceClassName == null) {
      clearanceClassNo =
          pBoard
              .rules
              .getDefaultNetClass()
              .defaultItemClearanceClasses
              .get(DefaultItemClearanceClasses.ItemClass.AREA);
    } else {
      clearanceClassNo = pBoard.rules.clearanceMatrix.getNo(pClearanceClassName);
      if (clearanceClassNo < 0) {
        FRLogger.warn(
            "Keepout.insert_keepout: clearance class not found at '" + pClearanceClassName + "'");
        clearanceClassNo = BoardRules.clearanceClassNone();
      }
    }
    if (pKeepoutType == KeepoutType.via_keepout) {
      pBoard.insertViaObstacle(pArea, pLayer, clearanceClassNo, pFixedState);
    } else if (pKeepoutType == KeepoutType.place_keepout) {
      pBoard.insertComponentObstacle(pArea, pLayer, clearanceClassNo, pFixedState);
    } else {
      pBoard.insertObstacle(pArea, pLayer, clearanceClassNo, pFixedState);
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter pPar) {
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
        nextToken = pPar.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Structure.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Structure.read_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
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
          readBoundaryScope(pPar.scanner, boardConstructionInfo);
        } else if (nextToken == Keyword.LAYER) {
          readOk = readLayerScope(pPar.scanner, boardConstructionInfo, pPar.stringQuote);
          if (pPar.layerStructure != null) {
            // correct the layerStructure because another layer isr read
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
        } else if (nextToken == Keyword.VIA) {
          pPar.viaPadstackNames = readViaPadstacks(pPar.scanner);
        } else if (nextToken == Keyword.RULE) {
          boardConstructionInfo.defaultRules.addAll(Rule.readScope(pPar.scanner));
        } else if (nextToken == Keyword.KEEPOUT) {
          if (pPar.layerStructure == null) {
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          keepoutList.add(Shape.readAreaScope(pPar.scanner, pPar.layerStructure, false));
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (pPar.layerStructure == null) {
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          viaKeepoutList.add(Shape.readAreaScope(pPar.scanner, pPar.layerStructure, false));
        } else if (nextToken == Keyword.PLACE_KEEPOUT) {
          if (pPar.layerStructure == null) {
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          placeKeepoutList.add(Shape.readAreaScope(pPar.scanner, pPar.layerStructure, false));
        } else if (nextToken == Keyword.PLANE_SCOPE) {
          if (pPar.layerStructure == null) {
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
          }
          Keyword.PLANE_SCOPE.readScope(pPar);
        } else if (nextToken == Keyword.AUTOROUTE_SETTINGS) {
          if (pPar.layerStructure == null) {
            pPar.layerStructure = new LayerStructure(boardConstructionInfo.layerInfo);
            pPar.autorouteSettings = AutorouteSettings.readScope(pPar.scanner, pPar.layerStructure);
          }
        } else if (nextToken == Keyword.CONTROL) {
          readOk = readControlScope(pPar);
        } else if (nextToken == Keyword.FLIP_STYLE) {
          flipStyleRotateFirst = PlaceControl.readFlipStyleRotateFirst(pPar.scanner);
        } else if (nextToken == Keyword.SNAP_ANGLE) {

          AngleRestriction snapAngle = readSnapAngle(pPar.scanner);
          if (snapAngle != null) {
            pPar.snapAngle = snapAngle;
          }
        } else {
          skipScope(pPar.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }

    // let's create a board based on the data we read (TODO: move this method
    // somewhere outside of the designforms.specctra package)
    boolean result = true;
    if (pPar.boardHandling.getRoutingBoard() == null) {
      result = createBoard(pPar, boardConstructionInfo);
    }
    RoutingBoard board = pPar.boardHandling.getRoutingBoard();
    if (board == null) {
      return false;
    }
    if (flipStyleRotateFirst) {
      board.components.setFlipStyleRotateFirst(true);
    }

    // insert the keepouts
    for (Shape.ReadAreaScopeResult currArea : keepoutList) {
      if (!insertKeepout(currArea, pPar, KeepoutType.keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : viaKeepoutList) {
      if (!insertKeepout(currArea, pPar, KeepoutType.via_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    for (Shape.ReadAreaScopeResult currArea : placeKeepoutList) {
      if (!insertKeepout(currArea, pPar, KeepoutType.place_keepout, FixedState.SYSTEM_FIXED)) {
        return false;
      }
    }

    // insert the planes.
    for (ReadScopeParameter.PlaneInfo planeInfo : pPar.planeList) {
      Net.Id netId = new Net.Id(planeInfo.netName, 1);
      if (!pPar.netlist.contains(netId)) {
        Net newNet = pPar.netlist.addNet(netId);
        if (newNet != null) {
          board.rules.nets.add(newNet.id.name, newNet.id.subnetNumber, true);
        }
      }
      app.freerouting.rules.Net currentNet = board.rules.nets.get(planeInfo.netName, 1);
      if (currentNet == null) {
        FRLogger.warn(
            "Plane.read_scope: net not found at '" + pPar.scanner.getScopeIdentifier() + "'");
        continue;
      }
      Area planeArea =
          Shape.transformAreaToBoard(planeInfo.area.shapeList, pPar.coordinateTransform);
      Layer currLayer = (planeInfo.area.shapeList.iterator().next()).layer;
      if (currLayer.no >= 0) {
        int clearanceClassNo;
        if (planeInfo.area.clearanceClassName != null) {
          clearanceClassNo = board.rules.clearanceMatrix.getNo(planeInfo.area.clearanceClassName);
          if (clearanceClassNo < 0) {
            FRLogger.warn(
                "Structure.read_scope: clearance class not found at '"
                    + pPar.scanner.getScopeIdentifier()
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
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
    }
    insertMissingPowerPlanes(boardConstructionInfo.layerInfo, pPar.netlist, board);

    pPar.boardHandling.initializeManualTraceHalfWidths();

    // Apply DSN autoroute settings to the current routing job if they were parsed
    if (pPar.autorouteSettings != null) {
      // Get the current routing job from the board manager
      RoutingJob currentJob = pPar.boardHandling.getCurrentRoutingJob();
      if (currentJob != null && currentJob.routerSettings != null) {
        // Apply the DSN file's autoroute settings to the routing job
        currentJob.routerSettings.applyNewValuesFrom(pPar.autorouteSettings);
        FRLogger.info("Applied DSN autoroute settings to routing job");
      }
    }

    return result;
  }

  private boolean createBoard(
      ReadScopeParameter pPar, BoardConstructionInfo pBoardConstructionInfo) {
    int layerCount = pBoardConstructionInfo.layerInfo.size();
    if (layerCount == 0) {
      FRLogger.warn(
          "Structure.create_board: layers missing in structure scope at '"
              + pPar.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    if (pBoardConstructionInfo.boundingShape == null) {
      // happens if the boundary shape with layer pcb is missing
      if (pBoardConstructionInfo.outlineShapes.isEmpty()) {
        FRLogger.warn(
            "Structure.create_board: outline missing at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        pPar.boardOutlineOk = false;
        return false;
      }
      Iterator<Shape> it = pBoardConstructionInfo.outlineShapes.iterator();

      Rectangle boundingBox = it.next().boundingBox();
      while (it.hasNext()) {
        boundingBox = boundingBox.union(it.next().boundingBox());
      }
      pBoardConstructionInfo.boundingShape = boundingBox;
    }
    Rectangle boundingBox = pBoardConstructionInfo.boundingShape.boundingBox();
    app.freerouting.board.Layer[] boardLayerArr = new app.freerouting.board.Layer[layerCount];
    Iterator<Layer> it = pBoardConstructionInfo.layerInfo.iterator();
    for (int i = 0; i < layerCount; i++) {
      Layer currLayer = it.next();
      if (currLayer.no < 0 || currLayer.no >= layerCount) {
        FRLogger.warn(
            "Structure.create_board: illegal layer number at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      boardLayerArr[i] = new app.freerouting.board.Layer(currLayer.name, currLayer.isSignal);
    }
    app.freerouting.board.LayerStructure boardLayerStructure =
        new app.freerouting.board.LayerStructure(boardLayerArr);
    pPar.layerStructure = new LayerStructure(pBoardConstructionInfo.layerInfo);

    // Calculate an approximate scaling between dsn coordinates and board
    // coordinates.
    int scaleFactor = Math.max(pPar.resolution, 1);

    double maxCoor = 0;
    for (int i = 0; i < 4; i++) {
      maxCoor = Math.max(maxCoor, Math.abs(boundingBox.coor[i] * pPar.resolution));
    }
    if (maxCoor == 0) {
      pPar.boardOutlineOk = false;
      return false;
    }
    // make scalefactor smaller, if there is a danger of integer overflow.
    while (5 * maxCoor >= Limits.CRIT_INT) {
      scaleFactor /= 10;
      maxCoor /= 10;
    }

    pPar.coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);

    IntBox bounds = (IntBox) boundingBox.transformToBoard(pPar.coordinateTransform);
    bounds = bounds.offset(1000);

    Collection<PolylineShape> boardOutlineShapes = new LinkedList<>();
    for (Shape currShape : pBoardConstructionInfo.outlineShapes) {
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
          (PolylineShape) currShape.transformToBoard(pPar.coordinateTransform);
      if (currBoardShape.dimension() > 0) {
        boardOutlineShapes.add(currBoardShape);
      }
    }
    if (boardOutlineShapes.isEmpty()) {
      // construct an outline from the boundingShape, if the outline is missing.
      PolylineShape currBoardShape =
          (PolylineShape)
              pBoardConstructionInfo.boundingShape.transformToBoard(pPar.coordinateTransform);
      boardOutlineShapes.add(currBoardShape);
    }
    Collection<PolylineShape> holeShapes = separateHoles(boardOutlineShapes);
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(boardLayerStructure, 0);
    BoardRules boardRules = new BoardRules(boardLayerStructure, clearanceMatrix);
    Communication.SpecctraParserInfo specctraParserInfo =
        new Communication.SpecctraParserInfo(
            pPar.stringQuote,
            pPar.hostCad,
            pPar.hostVersion,
            pPar.constants,
            pPar.writeResolution,
            pPar.dsnFileGeneratedByHost);
    Communication boardCommunication =
        new Communication(
            pPar.unit,
            pPar.resolution,
            specctraParserInfo,
            pPar.coordinateTransform,
            pPar.itemIdNoGenerator,
            pPar.observers);

    if (boardCommunication.hostIsOldKicad()) {
      FRLogger.warn(
          "Structure.create_board: The DSN file was exported from an old KiCad version that has known compatibility issues. Please update KiCad to version 6 or newer.");
    }

    PolylineShape[] outlineShapeArr = new PolylineShape[boardOutlineShapes.size()];
    Iterator<PolylineShape> it2 = boardOutlineShapes.iterator();
    for (int i = 0; i < outlineShapeArr.length; i++) {
      outlineShapeArr[i] = it2.next();
    }
    updateBoardRules(pPar, pBoardConstructionInfo, boardRules);
    boardRules.setTraceAngleRestriction(pPar.snapAngle);
    pPar.boardHandling.createBoard(
        bounds,
        boardLayerStructure,
        outlineShapeArr,
        pBoardConstructionInfo.outlineClearanceClassName,
        boardRules,
        boardCommunication);

    BasicBoard board = pPar.boardHandling.getRoutingBoard();

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

    LayerRule(String pLayerName, Collection<Rule> pRule) {
      layerName = pLayerName;
      rule = pRule;
    }
  }

  /** Used to separate the holes in the outline. */
  private static class OutlineShape {

    final PolylineShape shape;
    final IntBox boundingBox;
    final TileShape[] convexShapes;
    boolean isHole;

    public OutlineShape(PolylineShape pShape) {
      shape = pShape;
      boundingBox = pShape.boundingBox();
      convexShapes = pShape.splitToConvex();
      isHole = false;
    }

    /** Returns true, if this shape contains all corners of p_other_shape. */
    private boolean containsAllCorners(OutlineShape pOtherShape) {
      if (this.convexShapes == null) {
        // calculation of the convex shapes failed
        return false;
      }
      int cornerCount = pOtherShape.shape.borderLineCount();
      for (int i = 0; i < cornerCount; i++) {
        Point currCorner = pOtherShape.shape.corner(i);
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
