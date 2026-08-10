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
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class Wiring extends ScopeKeyword {

  /** Creates a new instance of Wiring. */
  public Wiring() {
    super("wiring");
  }

  public static void writeScope(WriteScopeParameter par) throws IOException {
    par.file.startScope();
    par.file.write("wiring");
    // write the wires
    Collection<Trace> boardWires = par.board.getTraces();
    for (Trace currBoardWire : boardWires) {
      writeWireScope(par, currBoardWire);
    }
    Collection<Via> boardVias = par.board.getVias();
    for (Via currVia : boardVias) {
      writeViaScope(par, currVia);
    }
    // write the conduction areas
    Iterator<UndoableObjects.UndoableObjectNode> it2 = par.board.itemList.startReadObject();
    for (; ; ) {
      Object currOb = par.board.itemList.readObject(it2);
      if (currOb == null) {
        break;
      }
      if (!(currOb instanceof ConductionArea currArea)) {
        continue;
      }
      if (!par.board.layerStructure.arr[currArea.getLayer()].isSignal) {
        // This conduction areas arw written in the structure scope.
        continue;
      }
      writeConductionAreaScope(par, (ConductionArea) currOb);
    }
    par.file.endScope();
  }

  private static void writeViaScope(WriteScopeParameter par, Via via) throws IOException {
    final Padstack viaPadstack = via.getPadstack();
    FloatPoint viaLocation = via.getCenter().toFloat();
    final double[] viaCoor = par.coordinateTransform.boardToDsn(viaLocation);
    int netNo;
    app.freerouting.rules.Net viaNet;
    if (via.netCount() > 0) {
      netNo = via.getNetNo(0);
      viaNet = par.board.rules.nets.get(netNo);
    } else {
      netNo = 0;
      viaNet = null;
    }
    par.file.startScope();
    par.file.write("via ");
    par.identifierType.write(viaPadstack.name, par.file);
    for (int i = 0; i < viaCoor.length; i++) {
      par.file.write(" ");
      par.file.write(String.valueOf(viaCoor[i]));
    }
    if (viaNet != null) {
      writeNet(viaNet, par.file, par.identifierType);
    }
    Rule.writeItemClearanceClass(
        par.board.rules.clearanceMatrix.getName(via.clearanceClassNo()),
        par.file,
        par.identifierType);
    writeFixedState(par.file, via.getFixedState());
    par.file.endScope();
  }

  private static void writeWireScope(WriteScopeParameter par, Trace wire) throws IOException {
    if (!(wire instanceof PolylineTrace currWire)) {
      FRLogger.warn("Wiring.write_wire_scope: trace type not yet implemented");
      return;
    }
    int layerNo = currWire.getLayer();
    app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[layerNo];
    final Layer currLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    final double wireWidth = par.coordinateTransform.boardToDsn(2 * currWire.getHalfWidth());
    app.freerouting.rules.Net wireNet = null;
    if (currWire.netCount() > 0) {
      wireNet = par.board.rules.nets.get(currWire.getNetNo(0));
    }
    if (wireNet == null) {
      FRLogger.warn("Wiring.write_wire_scope: net not found");
      return;
    }
    par.file.startScope();
    par.file.write("wire");

    if (par.compatMode) {
      Point[] cornerArr = currWire.polyline().cornerArr();
      FloatPoint[] floatCornerArr = new FloatPoint[cornerArr.length];
      for (int i = 0; i < cornerArr.length; i++) {
        floatCornerArr[i] = cornerArr[i].toFloat();
      }
      double[] coors = par.coordinateTransform.boardToDsn(floatCornerArr);
      PolygonPath currPath = new PolygonPath(currLayer, wireWidth, coors);
      currPath.writeScope(par.file, par.identifierType);
    } else {
      double[] coors = par.coordinateTransform.boardToDsn(currWire.polyline().arr);
      PolylinePath currPath = new PolylinePath(currLayer, wireWidth, coors);
      currPath.writeScope(par.file, par.identifierType);
    }
    writeNet(wireNet, par.file, par.identifierType);
    Rule.writeItemClearanceClass(
        par.board.rules.clearanceMatrix.getName(wire.clearanceClassNo()),
        par.file,
        par.identifierType);
    writeFixedState(par.file, currWire.getFixedState());
    par.file.endScope();
  }

  private static void writeConductionAreaScope(
      WriteScopeParameter par, ConductionArea conductionArea) throws IOException {
    int netCount = conductionArea.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count");
      return;
    }
    final app.freerouting.rules.Net currentNet =
        par.board.rules.nets.get(conductionArea.getNetNo(0));
    Area currArea = conductionArea.getArea();
    int layerNo = conductionArea.getLayer();
    app.freerouting.board.Layer boardLayer = par.board.layerStructure.arr[layerNo];
    final Layer conductionLayer = new Layer(boardLayer.name, layerNo, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currArea.getBorder();
      holes = currArea.getHoles();
    }
    par.file.startScope();
    par.file.write("wire ");
    Shape dsnShape = par.coordinateTransform.boardToDsn(boundaryShape, conductionLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(par.file, par.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = par.coordinateTransform.boardToDsn(holes[i], conductionLayer);
      dsnHole.writeHoleScope(par.file, par.identifierType);
    }
    writeNet(currentNet, par.file, par.identifierType);
    Rule.writeItemClearanceClass(
        par.board.rules.clearanceMatrix.getName(conductionArea.clearanceClassNo()),
        par.file,
        par.identifierType);
    par.file.endScope();
  }

  private static void writeNet(
      app.freerouting.rules.Net net, IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.newLine();
    file.write("(");
    Net.writeNetId(net, file, identifierType);
    file.write(")");
  }

  private static void writeFixedState(IndentFileWriter file, FixedState fixedState)
      throws IOException {
    if (fixedState == FixedState.UNFIXED) {
      return;
    }
    file.newLine();
    file.write("(type ");
    if (fixedState == FixedState.SHOVE_FIXED) {
      file.write("shoveFixed)");
    } else if (fixedState == FixedState.SYSTEM_FIXED) {
      file.write("fix)");
    } else {
      file.write("protect)");
    }
  }

  private static Collection<app.freerouting.rules.Net> getSubnets(
      Net.Id netId, BoardRules rules) {
    Collection<app.freerouting.rules.Net> foundNets = new LinkedList<>();
    if (netId != null) {
      if (netId.subnetNumber > 0) {
        app.freerouting.rules.Net foundNet = rules.nets.get(netId.name, netId.subnetNumber);
        if (foundNet != null) {
          foundNets.add(foundNet);
        }
      } else {
        foundNets = rules.nets.get(netId.name);
      }
    }
    return foundNets;
  }

  private static boolean viaExists(
      IntPoint location, Padstack padstack, int[] netNoArr, BasicBoard board) {
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    int fromLayer = padstack.fromLayer();
    int toLayer = padstack.toLayer();
    Collection<Item> pickedItems = board.pickItems(location, padstack.fromLayer(), filter);
    for (Item currItem : pickedItems) {
      final Via currVia = (Via) currItem;
      if (currVia.netsEqual(netNoArr)
          && currVia.getCenter().equals(location)
          && currVia.firstLayer() == fromLayer
          && currVia.lastLayer() == toLayer) {
        return true;
      }
    }
    return false;
  }

  static FixedState calcFixed(IJFlexScanner scanner) {
    try {
      FixedState result = FixedState.UNFIXED;
      Object nextToken = scanner.nextToken();
      if (nextToken == SHOVE_FIXED) {
        result = FixedState.SHOVE_FIXED;
      } else if (nextToken == FIX) {
        result = FixedState.SYSTEM_FIXED;
      } else if (nextToken != NORMAL) {
        result = FixedState.USER_FIXED;
      }
      nextToken = scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn("Wiring.is_fixed: ) expected at '" + scanner.getScopeIdentifier() + "'");
        return FixedState.UNFIXED;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Wiring.is_fixed: IO error scanning file", e);
      return FixedState.UNFIXED;
    }
  }

  /** Reads a netId. The subnetNumber of the netId will be 0, if no subnetNumber was found. */
  private static Net.Id readNetId(IJFlexScanner scanner) {
    try {
      int subnetNumber = 0;

      final String netName = scanner.nextString();
      scanner.setScopeIdentifier(netName);

      Object nextToken = scanner.nextToken();
      if (nextToken instanceof Integer integer) {
        subnetNumber = integer;
        nextToken = scanner.nextToken();
      }
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Wiring.read_net_id: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
      }
      return new Net.Id(netName, subnetNumber);
    } catch (IOException e) {
      FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e);
      return null;
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Wiring.read_scope: IO error scanning file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_scope: unexpected end of file at '"
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
        if (nextToken == WIRE) {
          readWireScope(par);
        } else if (nextToken == VIA) {
          readOk = readViaScope(par);
        } else {
          skipScope(par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    RoutingBoard board = par.boardHandling.getRoutingBoard();
    try {
      board.normalizeAllTraces();
    } catch (Exception e) {
      String msg = "Wiring: normalization of traces failed";
      FRLogger.debug(msg);
      par.warnings.add(msg);
    }
    return true;
  }

  private Item readWireScope(ReadScopeParameter par) {
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
        nextToken = par.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_wire_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == POLYGON_PATH) {
          path = Shape.readPolygonPathScope(par.scanner, par.layerStructure);
        } else if (nextToken == POLYLINE_PATH) {
          path = Shape.readPolylinePathScope(par.scanner, par.layerStructure);
        } else if (nextToken == RECTANGLE) {

          borderShape = Shape.readRectangleScope(par.scanner, par.layerStructure);
        } else if (nextToken == POLYGON) {

          borderShape = Shape.readPolygonScope(par.scanner, par.layerStructure);
        } else if (nextToken == CIRCLE) {

          borderShape = Shape.readCircleScope(par.scanner, par.layerStructure);
        } else if (nextToken == WINDOW) {
          Shape holeShape = Shape.readScope(par.scanner, par.layerStructure);
          holeList.add(holeShape);
          // overread the closing bracket
          try {
            nextToken = par.scanner.nextToken();
          } catch (IOException e) {
            FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
            return null;
          }
          if (nextToken != CLOSED_BRACKET) {
            FRLogger.warn(
                "Wiring.read_wire_scope: closing bracket expected at '"
                    + par.scanner.getScopeIdentifier()
                    + "'");
            return null;
          }
        } else if (nextToken == NET) {
          netId = readNetId(par.scanner);
        } else if (nextToken == CLEARANCE_CLASS) {
          clearanceClassName = DsnFile.readStringScope(par.scanner);
        } else if (nextToken == TYPE) {
          fixed = calcFixed(par.scanner);
        } else {
          skipScope(par.scanner);
        }
      }
    }
    if (path == null && borderShape == null) {
      String msg = "Wiring: wire has no shape at '" + par.scanner.getScopeIdentifier() + "'";
      FRLogger.warn(msg);
      par.warnings.add(msg);
      return null;
    }
    RoutingBoard board = par.boardHandling.getRoutingBoard();

    NetClass netClass = board.rules.getDefaultNetClass();
    Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
    int[] netNoArr = new int[foundNets.size()];
    int currIndex = 0;
    for (app.freerouting.rules.Net currentNet : foundNets) {
      netNoArr[currIndex] = currentNet.netNumber;
      netClass = currentNet.getNetClass();
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
      halfWidth = (int) Math.round(par.coordinateTransform.dsnToBoard(path.width / 2));
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
              + par.scanner.getScopeIdentifier()
              + "'";
      FRLogger.warn(msg);
      par.warnings.add(msg);
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
      Area conductionArea = Shape.transformAreaToBoard(area, par.coordinateTransform);
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
        FloatPoint currCorner = par.coordinateTransform.dsnToBoard(currPoint);
        if (!boundingBox.contains(currCorner)) {
          String msg =
              "Wiring: wire corner ("
                  + (int) currPoint[0]
                  + ","
                  + (int) currPoint[1]
                  + ") is outside board bounds at '"
                  + par.scanner.getScopeIdentifier()
                  + "'";
          FRLogger.warn(msg);
          par.warnings.add(msg);
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
        par.warnings.add(msg);
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
        FloatPoint currA = par.coordinateTransform.dsnToBoard(currPoint);
        currPoint[0] = path.coordinateArr[4 * i + 2];
        currPoint[1] = path.coordinateArr[4 * i + 3];
        FloatPoint currB = par.coordinateTransform.dsnToBoard(currPoint);
        lineArr[i] = new Line(currA.round(), currB.round());
      }
      Polyline tracePolyline = new Polyline(lineArr);
      result =
          board.insertTraceWithoutCleaning(
              tracePolyline, layerNo, halfWidth, netNoArr, clearanceClassNo, fixed);
    } else {
      FRLogger.warn(
          "Wiring.read_wire_scope: unexpected Path subclass at '"
              + par.scanner.getScopeIdentifier()
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
  private void tryCorrectNet(Item item) {
    if (!(item instanceof Trace currTrace)) {
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
      item.assignNetNo(correctedNetNo);
    }
  }

  private boolean readViaScope(ReadScopeParameter par) {
    try {
      FixedState fixed = FixedState.UNFIXED;
      // read the padstack name
      Object nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Wiring.read_via_scope: padstack name expected at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      par.scanner.setScopeIdentifier(padstackName);
      // read the location
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = par.scanner.nextToken();
        if (nextToken instanceof Double double1) {
          location[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          location[i] = integer;
        } else {
          FRLogger.warn(
              "Wiring.read_via_scope: number expected at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
      }
      Net.Id netId = null;
      String clearanceClassName = null;
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = par.scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "Wiring.read_via_scope: unexpected end of file at '"
                  + par.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == NET) {
            netId = readNetId(par.scanner);
          } else if (nextToken == CLEARANCE_CLASS) {
            clearanceClassName = DsnFile.readStringScope(par.scanner);
          } else if (nextToken == TYPE) {
            fixed = calcFixed(par.scanner);
          } else {
            skipScope(par.scanner);
          }
        }
      }
      RoutingBoard board = par.boardHandling.getRoutingBoard();
      String cleanedName = padstackName != null ? padstackName.replaceAll("\\.\\d+", "") : null;
      Padstack currPadstack = board.library.padstacks.get(cleanedName);
      if (currPadstack == null) {
        String msg =
            "Wiring: via padstack '"
                + padstackName
                + "' not found at '"
                + par.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        par.warnings.add(msg);
        return false;
      }
      NetClass netClass = board.rules.getDefaultNetClass();
      Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
      if (netId != null && foundNets.isEmpty()) {
        String msg =
            "Wiring: via net '"
                + netId.name
                + "' not found at '"
                + par.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        par.warnings.add(msg);
      }
      int[] netNoArr = new int[foundNets.size()];
      int currIndex = 0;
      for (app.freerouting.rules.Net currentNet : foundNets) {
        netNoArr[currIndex] = currentNet.netNumber;
        netClass = currentNet.getNetClass();
      }
      int clearanceClassNo = -1;
      if (clearanceClassName != null) {
        clearanceClassNo = board.rules.clearanceMatrix.getNo(clearanceClassName);
      }
      if (clearanceClassNo < 0) {
        clearanceClassNo =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
      }
      IntPoint boardLocation = par.coordinateTransform.dsnToBoard(location).round();
      if (viaExists(boardLocation, currPadstack, netNoArr, board)) {
        String msg =
            "Wiring: duplicate via skipped at (" + boardLocation.x + ", " + boardLocation.y + ")";
        FRLogger.warn(msg);
        par.warnings.add(msg);
      } else {
        boolean attachAllowed = par.viaAtSmdAllowed && currPadstack.attachAllowed;
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
