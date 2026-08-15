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
import app.freerouting.core.library.Padstack;
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

  public static void writeScope(WriteScopeParameter scopeParameter) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("wiring");
    // write the wires
    Collection<Trace> boardWires = scopeParameter.board.getTraces();
    for (Trace currentBoardWire : boardWires) {
      writeWireScope(scopeParameter, currentBoardWire);
    }
    Collection<Via> boardVias = scopeParameter.board.getVias();
    for (Via currentVia : boardVias) {
      writeViaScope(scopeParameter, currentVia);
    }
    // write the conduction areas
    Iterator<UndoableObjects.UndoableObjectNode> it2 =
        scopeParameter.board.itemList.startReadObject();
    for (; ; ) {
      Object currentObject = scopeParameter.board.itemList.readObject(it2);
      if (currentObject == null) {
        break;
      }
      if (!(currentObject instanceof ConductionArea currentArea)) {
        continue;
      }
      if (!scopeParameter.board.layerStructure.arr[currentArea.getLayer()].isSignal) {
        // This conduction areas arw written in the structure scope.
        continue;
      }
      writeConductionAreaScope(scopeParameter, (ConductionArea) currentObject);
    }
    scopeParameter.file.endScope();
  }

  private static void writeViaScope(WriteScopeParameter scopeParameter, Via via)
      throws IOException {
    final Padstack viaPadstack = via.getPadstack();
    FloatPoint viaLocation = via.getCenter().toFloat();
    final double[] viaCoor = scopeParameter.coordinateTransform.boardToDsn(viaLocation);
    int netNumber;
    app.freerouting.rules.Net viaNet;
    if (via.netCount() > 0) {
      netNumber = via.getNetNumber(0);
      viaNet = scopeParameter.board.rules.nets.get(netNumber);
    } else {
      netNumber = 0;
      viaNet = null;
    }
    scopeParameter.file.startScope();
    scopeParameter.file.write("via ");
    scopeParameter.identifierType.write(viaPadstack.name, scopeParameter.file);
    for (int i = 0; i < viaCoor.length; i++) {
      scopeParameter.file.write(" ");
      scopeParameter.file.write(String.valueOf(viaCoor[i]));
    }
    if (viaNet != null) {
      writeNet(viaNet, scopeParameter.file, scopeParameter.identifierType);
    }
    Rule.writeItemClearanceClass(
        scopeParameter.board.rules.clearanceMatrix.getName(via.clearanceClassIndex()),
        scopeParameter.file,
        scopeParameter.identifierType);
    writeFixedState(scopeParameter.file, via.getFixedState());
    scopeParameter.file.endScope();
  }

  private static void writeWireScope(WriteScopeParameter scopeParameter, Trace wire)
      throws IOException {
    if (!(wire instanceof PolylineTrace currentWire)) {
      FRLogger.warn("Wiring.write_wire_scope: trace type not yet implemented");
      return;
    }
    int layerIndex = currentWire.getLayer();
    app.freerouting.board.Layer boardLayer = scopeParameter.board.layerStructure.arr[layerIndex];
    final Layer currentLayer = new Layer(boardLayer.name, layerIndex, boardLayer.isSignal);
    final double wireWidth =
        scopeParameter.coordinateTransform.boardToDsn(2 * currentWire.getHalfWidth());
    app.freerouting.rules.Net wireNet = null;
    if (currentWire.netCount() > 0) {
      wireNet = scopeParameter.board.rules.nets.get(currentWire.getNetNumber(0));
    }
    if (wireNet == null) {
      FRLogger.warn("Wiring.write_wire_scope: net not found");
      return;
    }
    scopeParameter.file.startScope();
    scopeParameter.file.write("wire");

    if (scopeParameter.compatMode) {
      Point[] cornerArr = currentWire.polyline().cornerArr();
      FloatPoint[] floatCornerArr = new FloatPoint[cornerArr.length];
      for (int i = 0; i < cornerArr.length; i++) {
        floatCornerArr[i] = cornerArr[i].toFloat();
      }
      double[] coors = scopeParameter.coordinateTransform.boardToDsn(floatCornerArr);
      PolygonPath currentPath = new PolygonPath(currentLayer, wireWidth, coors);
      currentPath.writeScope(scopeParameter.file, scopeParameter.identifierType);
    } else {
      double[] coors = scopeParameter.coordinateTransform.boardToDsn(currentWire.polyline().arr);
      PolylinePath currentPath = new PolylinePath(currentLayer, wireWidth, coors);
      currentPath.writeScope(scopeParameter.file, scopeParameter.identifierType);
    }
    writeNet(wireNet, scopeParameter.file, scopeParameter.identifierType);
    Rule.writeItemClearanceClass(
        scopeParameter.board.rules.clearanceMatrix.getName(wire.clearanceClassIndex()),
        scopeParameter.file,
        scopeParameter.identifierType);
    writeFixedState(scopeParameter.file, currentWire.getFixedState());
    scopeParameter.file.endScope();
  }

  private static void writeConductionAreaScope(
      WriteScopeParameter scopeParameter, ConductionArea conductionArea) throws IOException {
    int netCount = conductionArea.netCount();
    if (netCount != 1) {
      FRLogger.warn("Plane.write_scope: unexpected net count");
      return;
    }
    final app.freerouting.rules.Net currentNet =
        scopeParameter.board.rules.nets.get(conductionArea.getNetNumber(0));
    Area currentArea = conductionArea.getArea();
    int layerIndex = conductionArea.getLayer();
    app.freerouting.board.Layer boardLayer = scopeParameter.board.layerStructure.arr[layerIndex];
    final Layer conductionLayer = new Layer(boardLayer.name, layerIndex, boardLayer.isSignal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currentArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currentArea.getBorder();
      holes = currentArea.getHoles();
    }
    scopeParameter.file.startScope();
    scopeParameter.file.write("wire ");
    Shape dsnShape = scopeParameter.coordinateTransform.boardToDsn(boundaryShape, conductionLayer);
    if (dsnShape != null) {
      dsnShape.writeScope(scopeParameter.file, scopeParameter.identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = scopeParameter.coordinateTransform.boardToDsn(holes[i], conductionLayer);
      dsnHole.writeHoleScope(scopeParameter.file, scopeParameter.identifierType);
    }
    writeNet(currentNet, scopeParameter.file, scopeParameter.identifierType);
    Rule.writeItemClearanceClass(
        scopeParameter.board.rules.clearanceMatrix.getName(conductionArea.clearanceClassIndex()),
        scopeParameter.file,
        scopeParameter.identifierType);
    scopeParameter.file.endScope();
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

  private static Collection<app.freerouting.rules.Net> getSubnets(Net.Id netId, BoardRules rules) {
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
      IntPoint location, Padstack padstack, int[] netNumbers, BasicBoard board) {
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS);
    int fromLayer = padstack.fromLayer();
    int toLayer = padstack.toLayer();
    Collection<Item> pickedItems = board.pickItems(location, padstack.fromLayer(), filter);
    for (Item currentItem : pickedItems) {
      final Via currentVia = (Via) currentItem;
      if (currentVia.netsEqual(netNumbers)
          && currentVia.getCenter().equals(location)
          && currentVia.firstLayer() == fromLayer
          && currentVia.lastLayer() == toLayer) {
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
  public boolean readScope(ReadScopeParameter scopeParameter) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Wiring.read_scope: IO error scanning file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
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
          readWireScope(scopeParameter);
        } else if (nextToken == VIA) {
          readOk = readViaScope(scopeParameter);
        } else {
          skipScope(scopeParameter.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    RoutingBoard board = scopeParameter.boardHandling.getRoutingBoard();
    try {
      board.normalizeAllTraces();
    } catch (Exception e) {
      String msg = "Wiring: normalization of traces failed";
      FRLogger.debug(msg);
      scopeParameter.warnings.add(msg);
    }
    return true;
  }

  private Item readWireScope(ReadScopeParameter scopeParameter) {
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
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Wiring.read_wire_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == POLYGON_PATH) {
          path = Shape.readPolygonPathScope(scopeParameter.scanner, scopeParameter.layerStructure);
        } else if (nextToken == POLYLINE_PATH) {
          path = Shape.readPolylinePathScope(scopeParameter.scanner, scopeParameter.layerStructure);
        } else if (nextToken == RECTANGLE) {

          borderShape =
              Shape.readRectangleScope(scopeParameter.scanner, scopeParameter.layerStructure);
        } else if (nextToken == POLYGON) {

          borderShape =
              Shape.readPolygonScope(scopeParameter.scanner, scopeParameter.layerStructure);
        } else if (nextToken == CIRCLE) {

          borderShape =
              Shape.readCircleScope(scopeParameter.scanner, scopeParameter.layerStructure);
        } else if (nextToken == WINDOW) {
          Shape holeShape = Shape.readScope(scopeParameter.scanner, scopeParameter.layerStructure);
          holeList.add(holeShape);
          // overread the closing bracket
          try {
            nextToken = scopeParameter.scanner.nextToken();
          } catch (IOException e) {
            FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e);
            return null;
          }
          if (nextToken != CLOSED_BRACKET) {
            FRLogger.warn(
                "Wiring.read_wire_scope: closing bracket expected at '"
                    + scopeParameter.scanner.getScopeIdentifier()
                    + "'");
            return null;
          }
        } else if (nextToken == NET) {
          netId = readNetId(scopeParameter.scanner);
        } else if (nextToken == CLEARANCE_CLASS) {
          clearanceClassName = DsnFile.readStringScope(scopeParameter.scanner);
        } else if (nextToken == TYPE) {
          fixed = calcFixed(scopeParameter.scanner);
        } else {
          skipScope(scopeParameter.scanner);
        }
      }
    }
    if (path == null && borderShape == null) {
      String msg =
          "Wiring: wire has no shape at '" + scopeParameter.scanner.getScopeIdentifier() + "'";
      FRLogger.warn(msg);
      scopeParameter.warnings.add(msg);
      return null;
    }
    RoutingBoard board = scopeParameter.boardHandling.getRoutingBoard();

    NetClass netClass = board.rules.getDefaultNetClass();
    Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
    int[] netNumbers = new int[foundNets.size()];
    int currentIndex = 0;
    for (app.freerouting.rules.Net currentNet : foundNets) {
      netNumbers[currentIndex] = currentNet.netNumber;
      netClass = currentNet.getNetClass();
      ++currentIndex;
    }
    int clearanceClassIndex = -1;
    if (clearanceClassName != null) {
      clearanceClassIndex = board.rules.clearanceMatrix.getNo(clearanceClassName);
    }
    int layerIndex;
    int halfWidth;
    if (path != null) {
      layerIndex = path.layer.no;
      halfWidth = (int) Math.round(scopeParameter.coordinateTransform.dsnToBoard(path.width / 2));
    } else {
      layerIndex = borderShape.layer.no;
      halfWidth = 0;
    }
    if (layerIndex < 0 || layerIndex >= board.getLayerCount()) {
      String layerName = path != null ? path.layer.name : borderShape.layer.name;
      String msg =
          "Wiring: wire ignored — unknown layer '"
              + layerName
              + "' at '"
              + scopeParameter.scanner.getScopeIdentifier()
              + "'";
      FRLogger.warn(msg);
      scopeParameter.warnings.add(msg);
      return null;
    }

    IntBox boundingBox = board.getBoundingBox();

    Item result = null;
    if (borderShape != null) {
      if (clearanceClassIndex < 0) {
        clearanceClassIndex =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
      Collection<Shape> area = new LinkedList<>();
      area.add(borderShape);
      area.addAll(holeList);
      Area conductionArea = Shape.transformAreaToBoard(area, scopeParameter.coordinateTransform);
      result =
          board.insertConductionArea(
              conductionArea, layerIndex, netNumbers, clearanceClassIndex, false, fixed);
    } else if (path instanceof PolygonPath) {
      if (clearanceClassIndex < 0) {
        clearanceClassIndex =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      IntPoint[] cornerArr = new IntPoint[path.coordinateArr.length / 2];
      double[] currentPoint = new double[2];
      for (int i = 0; i < cornerArr.length; i++) {
        currentPoint[0] = path.coordinateArr[2 * i];
        currentPoint[1] = path.coordinateArr[2 * i + 1];
        FloatPoint currentCorner = scopeParameter.coordinateTransform.dsnToBoard(currentPoint);
        if (!boundingBox.contains(currentCorner)) {
          String msg =
              "Wiring: wire corner ("
                  + (int) currentPoint[0]
                  + ","
                  + (int) currentPoint[1]
                  + ") is outside board bounds at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'";
          FRLogger.warn(msg);
          scopeParameter.warnings.add(msg);
          return null;
        }
        cornerArr[i] = currentCorner.round();
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
                tracePolyline, layerIndex, halfWidth, netNumbers, clearanceClassIndex, fixed);
      } else {
        String msg =
            "Wiring: degenerate wire trace skipped (all "
                + polygonCorners.length
                + " corners are identical — zero-length trace) on layer '"
                + path.layer.name
                + "'. This is likely a DSN export issue in your EDA tool.";
        FRLogger.debug(msg);
        scopeParameter.warnings.add(msg);
      }
    } else if (path instanceof PolylinePath) {
      if (clearanceClassIndex < 0) {
        clearanceClassIndex =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.TRACE);
      }
      Line[] lineArr = new Line[path.coordinateArr.length / 4];
      double[] currentPoint = new double[2];
      for (int i = 0; i < lineArr.length; i++) {
        currentPoint[0] = path.coordinateArr[4 * i];
        currentPoint[1] = path.coordinateArr[4 * i + 1];
        FloatPoint currentA = scopeParameter.coordinateTransform.dsnToBoard(currentPoint);
        currentPoint[0] = path.coordinateArr[4 * i + 2];
        currentPoint[1] = path.coordinateArr[4 * i + 3];
        FloatPoint currentB = scopeParameter.coordinateTransform.dsnToBoard(currentPoint);
        lineArr[i] = new Line(currentA.round(), currentB.round());
      }
      Polyline tracePolyline = new Polyline(lineArr);
      result =
          board.insertTraceWithoutCleaning(
              tracePolyline, layerIndex, halfWidth, netNumbers, clearanceClassIndex, fixed);
    } else {
      FRLogger.warn(
          "Wiring.read_wire_scope: unexpected Path subclass at '"
              + scopeParameter.scanner.getScopeIdentifier()
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
    if (!(item instanceof Trace currentTrace)) {
      return;
    }
    Set<Item> contacts = currentTrace.getNormalContacts(currentTrace.firstCorner(), true);
    contacts.addAll(currentTrace.getNormalContacts(currentTrace.lastCorner(), true));
    int correctedNetNo = 0;
    for (Item currentContact : contacts) {
      if (currentContact.netCount() == 1) {
        correctedNetNo = currentContact.getNetNumber(0);
        break;
      }
    }
    if (correctedNetNo != 0) {
      item.assignNetNo(correctedNetNo);
    }
  }

  private boolean readViaScope(ReadScopeParameter scopeParameter) {
    try {
      FixedState fixed = FixedState.UNFIXED;
      // read the padstack name
      Object nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String padstackName)) {
        FRLogger.warn(
            "Wiring.read_via_scope: padstack name expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      scopeParameter.scanner.setScopeIdentifier(padstackName);
      // read the location
      double[] location = new double[2];
      for (int i = 0; i < 2; i++) {
        nextToken = scopeParameter.scanner.nextToken();
        if (nextToken instanceof Double double1) {
          location[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          location[i] = integer;
        } else {
          FRLogger.warn(
              "Wiring.read_via_scope: number expected at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
      }
      Net.Id netId = null;
      String clearanceClassName = null;
      for (; ; ) {
        Object prevToken = nextToken;
        nextToken = scopeParameter.scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "Wiring.read_via_scope: unexpected end of file at '"
                  + scopeParameter.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        if (nextToken == CLOSED_BRACKET) {
          // end of scope
          break;
        }
        if (prevToken == OPEN_BRACKET) {
          if (nextToken == NET) {
            netId = readNetId(scopeParameter.scanner);
          } else if (nextToken == CLEARANCE_CLASS) {
            clearanceClassName = DsnFile.readStringScope(scopeParameter.scanner);
          } else if (nextToken == TYPE) {
            fixed = calcFixed(scopeParameter.scanner);
          } else {
            skipScope(scopeParameter.scanner);
          }
        }
      }
      RoutingBoard board = scopeParameter.boardHandling.getRoutingBoard();
      String cleanedName = padstackName != null ? padstackName.replaceAll("\\.\\d+", "") : null;
      Padstack currentPadstack = board.library.padstacks.get(cleanedName);
      if (currentPadstack == null) {
        String msg =
            "Wiring: via padstack '"
                + padstackName
                + "' not found at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        scopeParameter.warnings.add(msg);
        return false;
      }
      NetClass netClass = board.rules.getDefaultNetClass();
      Collection<app.freerouting.rules.Net> foundNets = getSubnets(netId, board.rules);
      if (netId != null && foundNets.isEmpty()) {
        String msg =
            "Wiring: via net '"
                + netId.name
                + "' not found at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'";
        FRLogger.warn(msg);
        scopeParameter.warnings.add(msg);
      }
      int[] netNumbers = new int[foundNets.size()];
      int currentIndex = 0;
      for (app.freerouting.rules.Net currentNet : foundNets) {
        netNumbers[currentIndex] = currentNet.netNumber;
        netClass = currentNet.getNetClass();
      }
      int clearanceClassIndex = -1;
      if (clearanceClassName != null) {
        clearanceClassIndex = board.rules.clearanceMatrix.getNo(clearanceClassName);
      }
      if (clearanceClassIndex < 0) {
        clearanceClassIndex =
            netClass.defaultItemClearanceClasses.get(DefaultItemClearanceClasses.ItemClass.VIA);
      }
      IntPoint boardLocation = scopeParameter.coordinateTransform.dsnToBoard(location).round();
      if (viaExists(boardLocation, currentPadstack, netNumbers, board)) {
        String msg =
            "Wiring: duplicate via skipped at (" + boardLocation.x + ", " + boardLocation.y + ")";
        FRLogger.warn(msg);
        scopeParameter.warnings.add(msg);
      } else {
        boolean attachAllowed = scopeParameter.viaAtSmdAllowed && currentPadstack.attachAllowed;
        board.insertVia(
            currentPadstack, boardLocation, netNumbers, clearanceClassIndex, fixed, attachAllowed);
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("Wiring.read_via_scope: IO error scanning file", e);
      return false;
    }
  }
}
