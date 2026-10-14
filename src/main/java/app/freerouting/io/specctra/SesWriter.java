package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Padstack;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.io.specctra.parser.Layer;
import app.freerouting.io.specctra.parser.Parser;
import app.freerouting.io.specctra.parser.Resolution;
import app.freerouting.io.specctra.parser.Shape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Writes a Specctra session (.ses) file from a {@link BasicBoard}.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class. It
 * operates purely on the board's data model.
 *
 * <p>This class is the public write entry point for Specctra session files.
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
  // Private helpers for session-file writing.
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
    final CoordinateTransform coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);
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
      app.freerouting.board.model.structure.Component currentComponent = board.components.get(i);
      if (currentComponent.getPackage() == pkg) {
        // check that not all items of the component are deleted
        boolean undeletedItemFound = false;
        for (Item currentItem : boardItems) {
          if (currentItem.getComponentId() == currentComponent.id) {
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
          writeComponent(board, identifierType, coordinateTransform, file, currentComponent);
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
      app.freerouting.board.model.structure.Component component)
      throws IOException {
    file.newLine();
    file.write("(place ");
    identifierType.write(component.name, file);
    double[] location = coordinateTransform.boardToDsn(component.getExactLocation());
    final double xcoordinate = location[0];
    final double ycoordinate = location[1];
    file.write(" ");
    file.write(String.valueOf(xcoordinate));
    file.write(" ");
    file.write(String.valueOf(ycoordinate));
    if (component.placedOnFront()) {
      file.write(" front ");
    } else {
      file.write(" back ");
    }
    file.write(formatPlacementRotation(component.getRotationInDegree()));
    if (component.positionFixed && !board.communication.hostCadIsKiCad()) {
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
    for (Pin currentPin : boardPins) {
      Pin swappedWith = currentPin.getChangedTo();
      if (currentPin.getChangedTo() != currentPin) {
        file.newLine();
        file.write("(pins ");
        app.freerouting.board.model.structure.Component currentCmp =
            board.components.get(currentPin.getComponentId());
        if (currentCmp != null) {
          identifierType.write(currentCmp.name, file);
          file.write("-");
          Package.Pin packagePin = currentCmp.getPackage().getPin(currentPin.getPinIndex());
          identifierType.write(packagePin.name, file);
        } else {
          FRLogger.warn("SesWriter.writeWasIs: component not found");
        }
        file.write(" ");
        app.freerouting.board.model.structure.Component swapCmp =
            board.components.get(swappedWith.getComponentId());
        if (swapCmp != null) {
          identifierType.write(swapCmp.name, file);
          file.write("-");
          Package.Pin packagePin = swapCmp.getPackage().getPin(swappedWith.getPinIndex());
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
    return java.math.BigDecimal.valueOf(degrees).stripTrailingZeros().toPlainString();
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
      app.freerouting.geometry.planar.Shape currentBoardShape = padstack.getShape(i);
      if (currentBoardShape == null) {
        continue;
      }
      app.freerouting.board.model.structure.Layer boardLayer = board.layerStructure.layers[i];
      Layer currentLayer = new Layer(boardLayer.name, i, boardLayer.isSignal);
      Shape currentShape = coordinateTransform.boardToDsnRel(currentBoardShape, currentLayer);
      file.startScope();
      file.write("shape");
      currentShape.writeScopeInt(file, identifierType);
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
    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      writeNet(i, board, identifierType, coordinateTransform, file);
    }
    file.endScope();
  }

  private static void writeNet(
      int netNumber,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    Collection<Item> netItems = board.getConnectableItems(netNumber);
    boolean headerWritten = false;
    for (Item currentItem : netItems) {
      if (currentItem.getFixedState() == FixedState.SYSTEM_FIXED) {
        continue;
      }
      boolean isWire = currentItem instanceof PolylineTrace;
      boolean isVia = currentItem instanceof Via;
      boolean isConductionArea =
          currentItem instanceof ConductionArea
              && board.layerStructure.layers[currentItem.firstLayer()].isSignal;
      if (!headerWritten && (isWire || isVia || isConductionArea)) {
        file.startScope();
        file.write("net ");
        Net currentNet = board.rules.nets.get(netNumber);
        if (currentNet == null) {
          FRLogger.warn("SesWriter.writeNet: net not found");
        } else {
          identifierType.write(currentNet.name, file);
        }
        headerWritten = true;
      }
      if (isWire) {
        writeWire((PolylineTrace) currentItem, board, identifierType, coordinateTransform, file);
      } else if (isVia) {
        writeVia((Via) currentItem, board, identifierType, coordinateTransform, file);
      } else if (isConductionArea) {
        writeConductionArea(
            (ConductionArea) currentItem, board, identifierType, coordinateTransform, file);
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
    String layer = board.layerStructure.layers[wire.getLayer()].name;
    double width = coordinateTransform.boardToDsn(2 * wire.getHalfWidth());
    SesTracePath path = SesTracePath.from(wire, coordinateTransform);
    file.startScope();
    file.write("wire");
    path.write(layer, width, identifierType, file);
    writeFixedState(file, wire.getFixedState());
    file.endScope();
  }

  private static void writeVia(
      Via via,
      BasicBoard board,
      IdentifierType identifierType,
      CoordinateTransform coordinateTransform,
      IndentFileWriter file)
      throws IOException {
    Padstack viaPadstack = via.getPadstack();
    final FloatPoint viaLocation = via.getCenter().toFloat();
    file.startScope();
    file.write("via ");
    identifierType.write(viaPadstack.name, file);
    file.write(" ");
    double[] location = coordinateTransform.boardToDsn(viaLocation);
    final double xcoordinate = location[0];
    file.write(String.valueOf(xcoordinate));
    file.write(" ");
    final double ycoordinate = location[1];
    file.write(String.valueOf(ycoordinate));
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
    Area currentArea = conductionArea.getArea();
    int layerIndex = conductionArea.getLayer();
    app.freerouting.board.model.structure.Layer boardLayer =
        board.layerStructure.layers[layerIndex];
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
