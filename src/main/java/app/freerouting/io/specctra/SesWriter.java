package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Via;
import app.freerouting.core.Package;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.io.specctra.parser.Layer;
import app.freerouting.io.specctra.parser.Parser;
import app.freerouting.io.specctra.parser.Resolution;
import app.freerouting.io.specctra.parser.Shape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Writes a Specctra session (.ses) file from a {@link BasicBoard}.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class. It
 * operates purely on the board's data model.
 *
 * <p>Replaces the write path previously found in {@link
 * app.freerouting.io.specctra.parser.SpecctraSesFileWriter} (now {@link Deprecated}).
 */
public final class SesWriter {

  private SesWriter() {}

  /**
   * Serialises the routing result from {@code board} to Specctra SES format on the given stream.
   *
   * <p>The stream is <em>flushed</em> after writing but is <strong>not closed</strong> — the caller
   * retains ownership of the stream lifecycle.
   *
   * @param board the board whose routing data is serialised (must not be {@code null})
   * @param out target stream (caller owns lifecycle; must not be {@code null})
   * @param designName the PCB name written into the {@code (session ...)} scope header
   * @throws IOException if an I/O error occurs during writing
   */
  public static void write(BasicBoard board, OutputStream out, String designName)
      throws IOException {
    if (out == null) {
      throw new IOException("SesWriter: output stream must not be null");
    }
    IndentFileWriter outputFile = new IndentFileWriter(out);
    String sessionName = designName.replace(".dsn", ".ses");
    String[] reservedChars = {"(", ")", " ", ";", "-", "_", "/", "~", "{", "}"};
    IdentifierType identifierType =
        new IdentifierType(reservedChars, board.communication.specctraParserInfo.stringQuote);
    writeSessionScope(board, identifierType, outputFile, sessionName, designName);
    outputFile.flush();
  }

  // ---------------------------------------------------------------------------
  // Private helpers (migrated from SpecctraSesFileWriter)
  // ---------------------------------------------------------------------------

  private static void writeSessionScope(
      BasicBoard board,
      IdentifierType identifierType,
      IndentFileWriter file,
      String sessionName,
      String designName)
      throws IOException {
    double scaleFactor =
        board.communication.coordinateTransform.dsnToBoard(1) / board.communication.resolution;
    CoordinateTransform coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);
    file.startScope(false);
    file.write("session ");
    identifierType.write(sessionName, file);
    file.newLine();
    file.write("(base_design ");
    identifierType.write(designName, file);
    file.write(")");
    writePlacement(board, identifierType, coordinateTransform, file);
    writeWasIs(board, identifierType, file);
    writeRoutes(board, identifierType, coordinateTransform, file);
    file.endScope();
  }

  private static void writePlacement(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("placement");
    Resolution.writeScope(file, board.communication);

    if (board.library.packages != null) {
      for (int i = 1; i <= board.library.packages.count(); i++) {
        writeComponents(
            board, identifierType, coordinateTransform, file, board.library.packages.get(i));
      }
    }

    file.endScope();
  }

  /** Writes all components with the given package to the session file. */
  private static void writeComponents(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file,
      Package pkg)
      throws IOException {
    Collection<Item> boardItems = board.getItems();
    boolean componentFound = false;
    for (int i = 1; i <= board.components.count(); i++) {
      app.freerouting.board.Component currComponent = board.components.get(i);
      if (currComponent.getPackage() == pkg) {
        // check that not all items of the component are deleted
        boolean undeletedItemFound = false;
        for (Item currItem : boardItems) {
          if (currItem.getComponentNo() == currComponent.no) {
            undeletedItemFound = true;
            break;
          }
        }
        if (undeletedItemFound) {
          if (!componentFound) {
            file.startScope();
            file.write("component ");
            identifierType.write(pkg.name, file);
            componentFound = true;
          }
          writeComponent(board, identifierType, coordinateTransform, file, currComponent);
        }
      }
    }
    if (componentFound) {
      file.endScope();
    }
  }

  private static void writeComponent(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file,
      app.freerouting.board.Component component)
      throws IOException {
    file.newLine();
    file.write("(place ");
    identifierType.write(component.name, file);
    double[] location = coordinateTransform.boardToDsn(component.getLocation().toFloat());
    int xCoor = (int) Math.round(location[0]);
    int yCoor = (int) Math.round(location[1]);
    file.write(" ");
    file.write(String.valueOf(xCoor));
    file.write(" ");
    file.write(String.valueOf(yCoor));
    if (component.placedOnFront()) {
      file.write(" front ");
    } else {
      file.write(" back ");
    }
    file.write(formatPlacementRotation(component.getRotationInDegree()));
    if (component.positionFixed) {
      file.newLine();
      file.write(" (lock_type position)");
    }
    file.write(")");
  }

  private static void writeWasIs(
      BasicBoard board, IdentifierType identifierType, IndentFileWriter file) throws IOException {
    file.startScope();
    file.write("was_is");
    Collection<Pin> boardPins = board.getPins();
    for (Pin currPin : boardPins) {
      Pin swappedWith = currPin.getChangedTo();
      if (currPin.getChangedTo() != currPin) {
        file.newLine();
        file.write("(pins ");
        app.freerouting.board.Component currCmp = board.components.get(currPin.getComponentNo());
        if (currCmp != null) {
          identifierType.write(currCmp.name, file);
          file.write("-");
          Package.Pin packagePin = currCmp.getPackage().getPin(currPin.getIndexInPackage());
          identifierType.write(packagePin.name, file);
        } else {
          FRLogger.warn("SesWriter.writeWasIs: component not found");
        }
        file.write(" ");
        app.freerouting.board.Component swapCmp =
            board.components.get(swappedWith.getComponentNo());
        if (swapCmp != null) {
          identifierType.write(swapCmp.name, file);
          file.write("-");
          Package.Pin packagePin = swapCmp.getPackage().getPin(swappedWith.getIndexInPackage());
          identifierType.write(packagePin.name, file);
        } else {
          FRLogger.warn("SesWriter.writeWasIs: component not found");
        }
        file.write(")");
      }
    }
    file.endScope();
  }

  private static void writeRoutes(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("routes ");
    Resolution.writeScope(file, board.communication);
    Parser.writeScope(file, board.communication.specctraParserInfo, identifierType, true);
    writeLibrary(board, identifierType, coordinateTransform, file);
    writeNetwork(board, identifierType, coordinateTransform, file);
    file.endScope();
  }

  private static void writeLibrary(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("library_out ");
    Set<String> writtenPadstackNames = new LinkedHashSet<>();
    for (int i = 0; i < board.library.viaPadstackCount(); i++) {
      Padstack viaPadstack = board.library.getViaPadstack(i);
      if (viaPadstack == null || !writtenPadstackNames.add(viaPadstack.name)) {
        continue;
      }
      writePadstack(viaPadstack, board, identifierType, coordinateTransform, file);
    }
    file.endScope();
  }

  /**
   * Formats component rotation for Specctra placement records in the style KiCad exports: whole
   * degrees as integers ({@code 0}, {@code 339}) and fractional degrees with minimal decimal
   * precision ({@code 338.5}), never unnecessary trailing zeros ({@code 338.500}).
   */
  static String formatPlacementRotation(double degrees) {
    double rounded = Math.rint(degrees * 1000.0) / 1000.0;
    if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
      return String.format(Locale.ENGLISH, "%.0f", rounded);
    }
    String formatted = String.format(Locale.ENGLISH, "%.3f", rounded);
    if (formatted.contains(".")) {
      formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    return formatted;
  }

  private static void writePadstack(
      Padstack padstack,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    // determine the layer range covered by this padstack
    int firstLayerNo = 0;
    while (firstLayerNo < board.getLayerCount() && padstack.getShape(firstLayerNo) == null) {
      ++firstLayerNo;
    }
    int lastLayerNo = board.getLayerCount() - 1;
    while (lastLayerNo >= 0 && padstack.getShape(lastLayerNo) == null) {
      --lastLayerNo;
    }
    if (firstLayerNo >= board.getLayerCount() || lastLayerNo < 0) {
      FRLogger.warn("SesWriter.writePadstack: padstack shape not found");
      return;
    }

    file.startScope();
    file.write("padstack ");
    identifierType.write(padstack.name, file);
    for (int i = firstLayerNo; i <= lastLayerNo; i++) {
      app.freerouting.geometry.planar.Shape currBoardShape = padstack.getShape(i);
      if (currBoardShape == null) {
        continue;
      }
      app.freerouting.board.Layer boardLayer = board.layerStructure.arr[i];
      Layer currLayer = new Layer(boardLayer.name, i, boardLayer.isSignal);
      Shape currShape = coordinateTransform.boardToDsnRel(currBoardShape, currLayer);
      file.startScope();
      file.write("shape");
      currShape.writeScopeInt(file, identifierType);
      file.endScope();
    }
    if (!padstack.attachAllowed) {
      file.newLine();
      file.write("(attach off)");
    }
    file.endScope();
  }

  private static void writeNetwork(
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("network_out ");
    for (int i = 1; i <= board.rules.nets.maxNetNo(); i++) {
      writeNet(i, board, identifierType, coordinateTransform, file);
    }
    file.endScope();
  }

  private static void writeNet(
      int netNo,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    Collection<Item> netItems = board.getConnectableItems(netNo);
    boolean headerWritten = false;
    for (Item currItem : netItems) {
      if (currItem.getFixedState() == FixedState.SYSTEM_FIXED) {
        continue;
      }
      boolean isWire = currItem instanceof PolylineTrace;
      boolean isVia = currItem instanceof Via;
      boolean isConductionArea =
          currItem instanceof ConductionArea
              && board.layerStructure.arr[currItem.firstLayer()].isSignal;
      if (!headerWritten && (isWire || isVia || isConductionArea)) {
        file.startScope();
        file.write("net ");
        Net currNet = board.rules.nets.get(netNo);
        if (currNet == null) {
          FRLogger.warn("SesWriter.writeNet: net not found");
        } else {
          identifierType.write(currNet.name, file);
        }
        headerWritten = true;
      }
      if (isWire) {
        writeWire((PolylineTrace) currItem, board, identifierType, coordinateTransform, file);
      } else if (isVia) {
        writeVia((Via) currItem, board, identifierType, coordinateTransform, file);
      } else if (isConductionArea) {
        writeConductionArea(
            (ConductionArea) currItem, board, identifierType, coordinateTransform, file);
      }
    }
    if (headerWritten) {
      file.endScope();
    }
  }

  private static void writeWire(
      PolylineTrace wire,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    int layerNo = wire.getLayer();
    app.freerouting.board.Layer boardLayer = board.layerStructure.arr[layerNo];
    int wireWidth = (int) Math.round(coordinateTransform.boardToDsn(2 * wire.getHalfWidth()));
    file.startScope();
    file.write("wire");
    Point[] cornerArr = wire.polyline().cornerArr();
    int[] coors = new int[2 * cornerArr.length];
    int cornerIndex = 0;
    int[] prevCoors = null;
    for (int i = 0; i < cornerArr.length; i++) {
      FloatPoint cornerPoint = cornerArr[i].toFloat();
      if (i == 0 || i == cornerArr.length - 1) {
        FloatPoint snapped = snappedEndpoint(wire, i == 0);
        if (snapped != null) {
          cornerPoint = snapped;
        }
      }
      double[] currFloatCoors = coordinateTransform.boardToDsn(cornerPoint);
      int[] currCoors = new int[2];
      currCoors[0] = (int) Math.round(currFloatCoors[0]);
      currCoors[1] = (int) Math.round(currFloatCoors[1]);
      if (i == 0 || (currCoors[0] != prevCoors[0] || currCoors[1] != prevCoors[1])) {
        coors[cornerIndex] = currCoors[0];
        ++cornerIndex;
        coors[cornerIndex] = currCoors[1];
        ++cornerIndex;
        prevCoors = currCoors;
      }
    }
    if (cornerIndex < coors.length) {
      coors = Arrays.copyOf(coors, cornerIndex);
    }
    writePath(boardLayer.name, wireWidth, coors, identifierType, file);
    writeFixedState(file, wire.getFixedState());
    file.endScope();
  }

  /**
   * Returns the exact pad/via center to use for a wire endpoint, or null to keep the corner as-is.
   * Trace endpoints can sit on the border of freerouting's octagon approximation of a pad — a point
   * that is inside the approximation but outside the importing tool's real pad polygon, which then
   * reports the net as unconnected. Snapping the endpoint to the contacted drill item's center (the
   * same coordinate {@code writeVia} emits) removes those sub-pad gaps. Only snaps when the
   * endpoint already lies within the pad inradius of the center, so the last segment cannot leave
   * the pad or fold across a neighbor.
   */
  static FloatPoint snappedEndpoint(PolylineTrace wire, boolean startSide) {
    Point corner = startSide ? wire.firstCorner() : wire.lastCorner();
    FloatPoint cornerFloat = corner.toFloat();
    java.util.Set<Item> contacts = startSide ? wire.getStartContacts() : wire.getEndContacts();
    int layer = wire.getLayer();
    for (Item contact : contacts) {
      if (!(contact instanceof app.freerouting.board.DrillItem drill)) {
        continue;
      }
      if (layer < drill.firstLayer() || layer > drill.lastLayer()) {
        continue;
      }
      app.freerouting.geometry.planar.Shape padShape = drill.getShape(layer - drill.firstLayer());
      if (padShape == null) {
        continue;
      }
      FloatPoint center = drill.getCenter().toFloat();
      double centerDistance = cornerFloat.distance(center);
      if (centerDistance <= 0.5) {
        // Already at the center (within rounding) — nothing to fix.
        return null;
      }
      if (centerDistance <= padShape.borderDistance(center)) {
        return center;
      }
    }
    return null;
  }

  private static void writeVia(
      Via via,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    Padstack viaPadstack = via.getPadstack();
    FloatPoint viaLocation = via.getCenter().toFloat();
    file.startScope();
    file.write("via ");
    identifierType.write(viaPadstack.name, file);
    file.write(" ");
    double[] location = coordinateTransform.boardToDsn(viaLocation);
    int xCoor = (int) Math.round(location[0]);
    file.write(String.valueOf(xCoor));
    file.write(" ");
    int yCoor = (int) Math.round(location[1]);
    file.write(String.valueOf(yCoor));
    writeFixedState(file, via.getFixedState());
    file.endScope();
  }

  private static void writeFixedState(IndentFileWriter file, FixedState fixedState)
      throws IOException {
    if (fixedState.ordinal() <= FixedState.SHOVE_FIXED.ordinal()) {
      return;
    }
    file.newLine();
    file.write("(type ");
    if (fixedState == FixedState.SYSTEM_FIXED) {
      file.write("fix)");
    } else {
      file.write("protect)");
    }
  }

  private static void writePath(
      String layerName,
      int width,
      int[] coors,
      IdentifierType identifierType,
      IndentFileWriter file)
      throws IOException {
    file.startScope();
    file.write("path ");
    identifierType.write(layerName, file);
    file.write(" ");
    file.write(String.valueOf(width));
    int cornerCount = coors.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.newLine();
      file.write(String.valueOf(coors[2 * i]));
      file.write(" ");
      file.write(String.valueOf(coors[2 * i + 1]));
    }
    file.endScope();
  }

  private static void writeConductionArea(
      ConductionArea conductionArea,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    int netCount = conductionArea.netCount();
    if (netCount != 1) {
      FRLogger.warn("SesWriter.writeConductionArea: unexpected net count");
      return;
    }
    Area currArea = conductionArea.getArea();
    int layerNo = conductionArea.getLayer();
    app.freerouting.board.Layer boardLayer = board.layerStructure.arr[layerNo];
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
    file.startScope();
    file.write("wire ");
    Shape dsnShape = coordinateTransform.boardToDsn(boundaryShape, conductionLayer);
    if (dsnShape != null) {
      dsnShape.writeScopeInt(file, identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = coordinateTransform.boardToDsn(holes[i], conductionLayer);
      dsnHole.writeHoleScope(file, identifierType);
    }
    file.endScope();
  }
}
