package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polygon;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.NetClass;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/** Class for reading and writing wiring scopes from dsn-files. */
public class Wiring extends ScopeKeyword {

  /** Creates a new instance of Wiring */
  public Wiring() {
    super("wiring");
  }

  public static void writeScope(WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("wiring");
    // write the wires
    Collection<Trace> boardWires = p_par.board.getTraces();
    for (Trace curr_board_wire : boardWires) {
      writeWireScope(p_par, curr_board_wire);
    }
    Collection<Via> boardVias = p_par.board.getVias();
    for (Via currVia : boardVias) {
      writeViaScope(p_par, currVia);
    }
    // write the conduction areas
    Iterator<UndoableObjects.UndoableObjectNode> it2 = p_par.board.itemList.startReadObject();
    for (; ; ) {
      Object currOb = p_par.board.itemList.readObject(it2);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (!p_par.board.layerStructure.arr[currArea.getLayer()].isSignal) {
        // This conduction areas arw written in the structure scope.
        continue;
      }
      writeConductionAreaScope(p_par, (ConductionArea) currOb);
    }
    p_par.file.endScope();
  }

  private static void writeViaScope(WriteScopeParameter p_par, Via p_via) throws IOException {
    Padstack viaPadstack = p_via.getPadstack();
    FloatPoint viaLocation = p_via.getCenter().toFloat();
    double[] viaCoor = p_par.coordinateTransform.boardToDsn(viaLocation);
    int netNo;
    app.freerouting.rules.Net viaNet;
    if (p_via.netCount() > 0) {
      netNo = p_via.getNetNo(0);
      viaNet = p_par.board.rules.nets.get(netNo);
    } else {
      netNo = 0;
      viaNet = null;
    }
    p_par.file.startScope();
    p_par.file.write("via ");
    p_par.identifierType.write(viaPadstack.name, p_par.file);
    for (int i = 0; i < viaCoor.length; i++) {
      p_par.file.write(" ");
      p_par.file.write(String.valueOf(viaCoor[i]));
    }
    if (viaNet != null) {
      writeNet(viaNet, p_par.file, p_par.identifierType);
    }
    Rule.writeItemClearanceClass(
        p_par.board.rules.clearanceMatrix.getName(p_via.clearanceClassNo()),
        p_par.file,
        p_par.identifierType);
    writeFixedState(p_par.file, p_via.getFixedState());
    p_par.file.endScope();
  }

  private static void writeWireScope(WriteScopeParameter p_par, Trace p_wire) throws IOException {
    if (!(p_wire instanceof PolylineTrace curr_wire)) {
      FRLogger.warn("Wiring.write_wire_scope: trace type not yet implemented");
      return;
    }
    int layerNo = curr_wire.getLayer();
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[layerNo];
    Layer currLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    double wireWidth = p_par.coordinateTransform.boardToDsn(2 * curr_wire.getHalfWidth());
    app.freerouting.rules.Net wireNet = null;
    if (curr_wire.netCount() > 0) {
      wireNet = p_par.board.rules.nets.get(curr_wire.getNetNo(0));
    }
    if (wireNet == null) {
      FRLogger.warn("Wiring.write_wire_scope: net not found");
      return;
    }
    p_par.file.startScope();
    p_par.file.write("wire");

    if (p_par.compatMode) {
      Point[] cornerArr = curr_wire.polyline().cornerArr();
      FloatPoint[] floatCornerArr = new FloatPoint[cornerArr.length];
      for (int i = 0; i < cornerArr.length; i++) {
        floatCornerArr[i] = cornerArr[i].toFloat();
      }
      double[] coors = p_par.coordinateTransform.boardToDsn(floatCornerArr);
      PolygonPath currPath = new PolygonPath(currLayer, wireWidth, coors);
      currPath.writeScope(p_par.file, p_par.identifierType);
    } else {
      double[] coors = p_par.coordinateTransform.boardToDsn(curr_wire.polyline().arr);
      PolylinePath currPath = new PolylinePath(currLayer, wireWidth, coors);
      currPath.writeScope(p_par.file, p_par.identifierType);
    }
    writeNet(wireNet, p_par.file, p_par.identifierType);
    Rule.writeItemClearanceClass(
        p_par.board.rules.clearanceMatrix.getName(p_wire.clearanceClassNo()),
        p_par.file,
        p_par.identifierType);
    writeFixedState(p_par.file, curr_wire.getFixedState());
    p_par.file.endScope();
  }

  private static void writeConductionAreaScope(
      WriteScopeParameter p_par, ConductionArea p_conduction_area) throws IOException {
    int netCount = p_conduction_area.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count");
      return;
    }
    app.freerouting.rules.Net currNet = p_par.board.rules.nets.get(p_conduction_area.getNetNo(0));
    Area currArea = p_conduction_area.getArea();
    int layerNo = p_conduction_area.getLayer();
    app.freerouting.board.Layer boardLayer = p_par.board.layerStructure.arr[layerNo];
    Layer conductionLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currArea.getBorder();
      holes = currArea.getHoles();
    }
    p_par.file.startScope();
    p_par.file.write("wire ");
    Shape dsnShape = p_par.coordinateTransform.boardToDsn(boundaryShape, conductionLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(p_par.file, p_par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = p_par.coordinateTransform.boardToDsn(holes[i], conductionLayer);
      dsnHole.writeHoleScope(p_par.file, p_par.identifierType);
    }
    writeNet(currNet, p_par.file, p_par.identifierType);
    Rule.writeItemClearanceClass(
        p_par.board.rules.clearanceMatrix.getName(p_conduction_area.clearanceClassNo()),
        p_par.file,
        p_par.identifierType);
    p_par.file.endScope();
  }

  private static void writeNet(
      app.freerouting.rules.Net p_net, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.newLine();
    p_file.write("(");
    Net.writeNetId(p_net, p_file, p_identifier_type);
    p_file.write(")");
  }

  private static void writeFixedState(IndentFileWriter p_file, FixedState p_fixed_state)
      throws IOException {
    if (p_fixed_state == FixedState.UNFIXED) {
      return;
    }
    p_file.newLine();
    p_file.write("(type ");
    if (p_fixed_state == FixedState.SHOVE_FIXED) {
      p_file.write("shoveFixed)");
    } else if (p_fixed_state == FixedState.SYSTEM_FIXED) {
      p_file.write("fix)");
    } else {
      p_file.write("protect)");
    }
  }

  private static Collection<app.freerouting.rules.Net> getSubnets(
      Net.Id p_net_id, BoardRules p_rules) {
    Collection<app.freerouting.rules.Net> foundNets = new LinkedList<>();
    if (p_net_id != null) {
      if (p_net_id.subnetNumber > 0) {
        app.freerouting.rules.Net foundNet = p_rules.nets.get(p_net_id.name, p_net_id.subnetNumber);
        if (foundNet != null) {
          foundNets.add(foundNet);
        }
      } else {
        foundNets = p_rules.nets.get(p_net_id.name);
      }
    }
    return foundNets;
  }

  private static boolean viaExists(
      IntPoint p_location, Padstack p_padstack, int[] p_net_no_arr, BasicBoard p_board) {
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    int fromLayer = p_padstack.fromLayer();
    int toLayer = p_padstack.toLayer();
    Collection<Item> pickedItems = p_board.pickItems(p_location, p_padstack.fromLayer(), filter);
    for (Item currItem : pickedItems) {
      Via currVia = (Via) currItem;
      if (currVia.netsEqual(p_net_no_arr)
          && currVia.getCenter().equals(p_location)
          && currVia.firstLayer() == fromLayer
          && currVia.lastLayer() == toLayer) {
        return true;
      }
    }
    return false;
  }

  static FixedState calcFixed(IJFlexScanner p_scanner) {
    try {
      FixedState result = FixedState.UNFIXED;
      Object nextToken = p_scanner.nextToken();
      if (nextToken == SHOVE_FIXED) {
        result = FixedState.SHOVE_FIXED;
      } else if (nextToken == FIX) {
        result = FixedState.SYSTEM_FIXED;
      } else if (nextToken != NORMAL) {
        result = FixedState.USER_FIXED;
      }
      nextToken = p_scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn("Wiring.is_fixed: ) expected at '" + p_scanner.getScopeIdentifier() + "'");
        return FixedState.UNFIXED;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Wiring.is_fixed: IO error scanning file", e);
      return FixedState.UNFIXED;
    }
  }

  /** Reads a netId. The subnetNumber of the netId will be 0, if no subnetNumber was found. */
  private static Net.Id readNetId(IJFlexScanner p_scanner) {
    try {
      int subnetNumber = 0;

      String netName = p_scanner.nextString();
      p_scanner.setScopeIdentifier(netName);

      Object nextToken = p_scanner.nextToken();
      if (nextToken instanceof Integer integer) {
        subnetNumber = integer;
        nextToken = p_scanner.nextToken();
      }
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Wiring.read_net_id: closing bracket expected at '"
                + p_scanner.getScopeIdentifier()
                + "'");
      }
      return new Net.Id(netName, subnetNumber);
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Wiring.read_scope: IO error scanning file at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_scope: unexpected end of file at '"
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
        if (nextToken == WIRE) {
          readWireScope(p_par);
        } else if (nextToken == VIA) {
          readOk = readViaScope(p_par);
        } else {
          skipScope(p_par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    RoutingBoard board = p_par.boardHandling.getRoutingBoard();
    try {
      board.normalizeAllTraces();
    } catch (Exception e) {
      String msg = "Wiring: normalization of traces failed";
      FRLogger.debug(msg);
      p_par.warnings.add(msg);
    }
    return true;
  }

  private Item readWireScope(ReadScopeParameter p_par) {
    Net.Id netId = null;
    String clearanceClassName = null;
    FixedState fixed = FixedState.UNFIXED;
    Path path = null; // Used, if a trace is read.
    Shape borderShape = null; // Used, if a conduction area is read.
    Collection<Shape> holeList = new LinkedList<>();
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_wire_scope: unexpected end of file at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == POLYGON_PATH) {
          path = Shape.readPolygonPathScope(p_par.scanner, p_par.layerStructure);
        } else if (nextToken == POLYLINE_PATH) {
          path = Shape.readPolylinePathScope(p_par.scanner, p_par.layerStructure);
        } else if (nextToken == RECTANGLE) {

          borderShape = Shape.readRectangleScope(p_par.scanner, p_par.layerStructure);
        } else if (nextToken == POLYGON) {

          borderShape = Shape.readPolygonScope(p_par.scanner, p_par.layerStructure);
        } else if (nextToken == CIRCLE) {

          borderShape = Shape.readCircleScope(p_par.scanner, p_par.layerStructure);
        } else if (nextToken == WINDOW) {
          Shape holeShape = Shape.readScope(p_par.scanner, p_par.layerStructure);
          holeList.add(holeShape);
          // overread the closing bracket
          try {
            nextToken = p_par.scanner.nextToken();
          } catch (IOException e) {
            FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
            return null;
          }
          if (nextToken != CLOSED_BRACKET) {
            FRLogger.warn(
                "Wiring.read_wire_scope: closing bracket expected at '"
                    + p_par.scanner.getScopeIdentifier()
                    + "'");
            return null;
          }
        } else if (nextToken == NET) {
          netId = readNetId(p_par.scanner);
        } else if (nextToken == CLEARANCE_CLASS) {
          clearanceClassName = DsnFile.readStringScope(p_par.scanner);
        } else if (nextToken == TYPE) {
          fixed = calcFixed(p_par.scanner);
        } else {
          skipScope(p_par.scanner);
        }
      }
    }
    if (path == null && borderShape == null) {
      String msg = "Wiring: wire has no shape at '" + p_par.scanner.getScopeIdentifier() + "'";
      FRLogger.warn(msg);
      p_par.warnings.add(msg);
      return null;
    }
    RoutingBoard board = p_par.boardHandling.getRoutingBoard();

    NetClass netClass = board.rules.getDefaultNetClass();
    Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
    int[] netNoArr = new int[foundNets.size()];
    int currIndex = 0;
    for (app.freerouting.rules.Net currNet : foundNets) {
      netNoArr[currIndex] = currNet.netNumber;
      netClass = currNet.getNetClass();
      ++currIndex;
    }
    int clearanceClassNo = -1;
    if (clearanceClassName != null) {
      clearanceClassNo = board.rules.clearanceMatrix.getNo(clearanceClassName);
    }
    int layerNo;
    int halfWidth;
    if (path != null) {
      layerNo = path.layer.no;
      halfWidth = (int) Math.round(p_par.coordinateTransform.dsnToBoard(path.width / 2));
    } else {
      layerNo = borderShape.layer.no;
      halfWidth = 0;
    }
    if (layerNo < 0 || layerNo >= board.getLayerCount()) {
      String layerName = path != null ? path.layer.name : borderShape.layer.name;
      String msg =
          "Wiring: wire ignored — unknown layer '"
              + layerName
              + "' at '"
              + p_par.scanner.getScopeIdentifier()
              + "'";
      FRLogger.warn(msg);
      p_par.warnings.add(msg);
      return null;
    }

    IntBox boundingBox = board.getBoundingBox();

    Item result = null;
    if (borderShape != null) {
      if (clearanceClassNo < 0) {
        clearanceClassNo =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
      Collection<Shape> area = new LinkedList<>();
      area.add(borderShape);
      area.addAll(holeList);
      Area conductionArea = Shape.transformAreaToBoard(area, p_par.coordinateTransform);
      result =
          board.insertConductionArea(
              conductionArea, layerNo, netNoArr, clearanceClassNo, false, fixed);
    } else if (path instanceof PolygonPath) {
      if (clearanceClassNo < 0) {
        clearanceClassNo =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      IntPoint[] cornerArr = new IntPoint[path.coordinateArr.length / 2];
      double[] currPoint = new double[2];
      for (int i = 0; i < cornerArr.length; i++) {
        currPoint[0] = path.coordinateArr[2 * i];
        currPoint[1] = path.coordinateArr[2 * i + 1];
        FloatPoint currCorner = p_par.coordinateTransform.dsnToBoard(currPoint);
        if (!boundingBox.contains(currCorner)) {
          String msg =
              "Wiring: wire corner ("
                  + (int) currPoint[0]
                  + ","
                  + (int) currPoint[1]
                  + ") is outside board bounds at '"
                  + p_par.scanner.getScopeIdentifier()
                  + "'";
          FRLogger.warn(msg);
          p_par.warnings.add(msg);
          return null;
        }
        cornerArr[i] = currCorner.round();
      }

      Polygon polygon = new Polygon(cornerArr);

      // if it doesn't have two different points, it's not a valid polygon, so we must skip it
      Point[] polygonCorners = polygon.cornerArray();
      // A wire is degenerate if it has fewer than 2 corners, or if all corners map to the same
      // point (zero-length trace). This covers both the 2-point identical case and the N-point
      // all-equal case (e.g. KiCad 4.0.7 exports 3 identical vertices for some degenerate shapes).
      // Such traces cause infinite normalization cycles and must be skipped.
      boolean hasDistinctCorner = false;
      for (int i = 1; i < polygonCorners.length; i++) {
        if (!polygonCorners[i].equals(polygonCorners[0])) {
          hasDistinctCorner = true;
          break;
        }
      }
      boolean isDegenerate = polygonCorners.length < 2 || !hasDistinctCorner;
      if (!isDegenerate) {
        Polyline tracePolyline = new Polyline(polygon);
        // Traces are not yet normalized here because cycles may be removed premature.
        result =
            board.insertTraceWithoutCleaning(
                tracePolyline, layerNo, halfWidth, netNoArr, clearanceClassNo, fixed);
      } else {
        String msg =
            "Wiring: degenerate wire trace skipped (all "
                + polygonCorners.length
                + " corners are identical — zero-length trace) on layer '"
                + path.layer.name
                + "'. This is likely a DSN export issue in your EDA tool.";
        FRLogger.debug(msg);
        p_par.warnings.add(msg);
      }
    } else if (path instanceof PolylinePath) {
      if (clearanceClassNo < 0) {
        clearanceClassNo =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      Line[] lineArr = new Line[path.coordinateArr.length / 4];
      double[] currPoint = new double[2];
      for (int i = 0; i < lineArr.length; i++) {
        currPoint[0] = path.coordinateArr[4 * i];
        currPoint[1] = path.coordinateArr[4 * i + 1];
        FloatPoint currA = p_par.coordinateTransform.dsnToBoard(currPoint);
        currPoint[0] = path.coordinateArr[4 * i + 2];
        currPoint[1] = path.coordinateArr[4 * i + 3];
        FloatPoint currB = p_par.coordinateTransform.dsnToBoard(currPoint);
        lineArr[i] = new Line(currA.round(), currB.round());
      }
      Polyline tracePolyline = new Polyline(lineArr);
      result =
          board.insertTraceWithoutCleaning(
              tracePolyline, layerNo, halfWidth, netNoArr, clearanceClassNo, fixed);
    } else {
      FRLogger.warn(
          "Wiring.read_wire_scope: unexpected Path subclass at '"
              + p_par.scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    if (result != null && result.netCount() == 0) {
      tryCorrectNet(result);
    }
    return result;
  }

  /**
   * Maybe trace of type turret without net in Mentor design. Try to assign the net by calculating
   * the overlaps.
   */
  private void tryCorrectNet(Item p_item) {
    if (!(p_item instanceof Trace currTrace)) {
      return;
    }
    Set<Item> contacts = currTrace.getNormalContacts(currTrace.firstCorner(), true);
    contacts.addAll(currTrace.getNormalContacts(currTrace.lastCorner(), true));
    int correctedNetNo = 0;
    for (Item currContact : contacts) {
      if (currContact.netCount() == 1) {
        correctedNetNo = currContact.getNetNo(0);
        break;
      }
    }
    if (correctedNetNo != 0) {
      p_item.assignNetNo(correctedNetNo);
    }
  }

  private boolean readViaScope(ReadScopeParameter p_par) {
    try {
      FixedState fixed = FixedState.UNFIXED;
      // read the padstack name
      Object nextToken = p_par.scanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Wiring.read_via_scope: padstack name expected at '"
                + p_par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      p_par.scanner.setScopeIdentifier(padstackName);
      // read the location
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = p_par.scanner.nextToken();
        if (nextToken instanceof Double double1) {
          location[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          location[i] = integer;
        } else {
          FRLogger.warn(
              "Wiring.read_via_scope: number expected at '"
                  + p_par.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
      }
      Net.Id netId = null;
      String clearanceClassName = null;
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = p_par.scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "Wiring.read_via_scope: unexpected end of file at '"
                  + p_par.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == NET) {
            netId = readNetId(p_par.scanner);
          } else if (nextToken == CLEARANCE_CLASS) {
            clearanceClassName = DsnFile.readStringScope(p_par.scanner);
          } else if (nextToken == TYPE) {
            fixed = calcFixed(p_par.scanner);
          } else {
            skipScope(p_par.scanner);
          }
        }
      }
      RoutingBoard board = p_par.boardHandling.getRoutingBoard();
      String cleanedName = padstackName != null ? padstackName.replaceAll("\\.\\d+", "") : null;
      Padstack currPadstack = board.library.padstacks.get(cleanedName);
      if (currPadstack == null) {
        String msg =
            "Wiring: via padstack '"
                + padstackName
                + "' not found at '"
                + p_par.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        p_par.warnings.add(msg);
        return false;
      }
      NetClass netClass = board.rules.getDefaultNetClass();
      Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
      if (netId != null && foundNets.isEmpty()) {
        String msg =
            "Wiring: via net '"
                + netId.name
                + "' not found at '"
                + p_par.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        p_par.warnings.add(msg);
      }
      int[] netNoArr = new int[foundNets.size()];
      int currIndex = 0;
      for (app.freerouting.rules.Net currNet : foundNets) {
        netNoArr[currIndex] = currNet.netNumber;
        netClass = currNet.getNetClass();
      }
      int clearanceClassNo = -1;
      if (clearanceClassName != null) {
        clearanceClassNo = board.rules.clearanceMatrix.getNo(clearanceClassName);
      }
      if (clearanceClassNo < 0) {
        clearanceClassNo =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
      }
      IntPoint boardLocation = p_par.coordinateTransform.dsnToBoard(location).round();
      if (viaExists(boardLocation, currPadstack, netNoArr, board)) {
        String msg =
            "Wiring: duplicate via skipped at (" + boardLocation.x + ", " + boardLocation.y + ")";
        FRLogger.warn(msg);
        p_par.warnings.add(msg);
      } else {
        boolean attachAllowed = p_par.viaAtSmdAllowed && currPadstack.attachAllowed;
        board.insertVia(
            currPadstack, boardLocation, netNoArr, clearanceClassNo, fixed, attachAllowed);
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("Wiring.read_via_scope: IO error scanning file", e);
      return false;
    }
  }
}
