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
import app.freerouting.io.specctra.parser.CoordinateTransform;
import app.freerouting.io.specctra.parser.Layer;
import app.freerouting.io.specctra.parser.Parser;
import app.freerouting.io.specctra.parser.Resolution;
import app.freerouting.io.specctra.parser.Shape;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

/**
 * Writes a Specctra session (.ses) file from a {@link BasicBoard}.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class.
 * It operates purely on the board's data model.
 *
 * <p>Replaces the write path previously found in
 * {@link app.freerouting.io.specctra.parser.SpecctraSesFileWriter}
 * (now {@link Deprecated}).
 */
public final class SesWriter {

  private SesWriter() {
  }

  /**
   * Serialises the routing result from {@code board} to Specctra SES format on the given stream.
   *
   * <p>The stream is <em>flushed</em> after writing but is <strong>not closed</strong> — the
   * caller retains ownership of the stream lifecycle.
   *
   * @param board      the board whose routing data is serialised (must not be {@code null})
   * @param out        target stream (caller owns lifecycle; must not be {@code null})
   * @param designName the PCB name written into the {@code (session ...)} scope header
   * @throws IOException if an I/O error occurs during writing
   */
  public static void write(BasicBoard board, OutputStream out, String designName) throws IOException {
    if (out == null) {
      throw new IOException("SesWriter: output stream must not be null");
    }
    IndentFileWriter outputFile = new IndentFileWriter(out);
    String sessionName = designName.replace(".dsn", ".ses");
    String[] reservedChars = {"(", ")", " ", ";", "-", "_", "/", "~", "{", "}"};
    IdentifierType identifierType = new IdentifierType(reservedChars,
        board.communication.specctra_parser_info.string_quote);
    writeSessionScope(board, identifierType, outputFile, sessionName, designName);
    outputFile.flush();
  }

  // ---------------------------------------------------------------------------
  // Private helpers (migrated from SpecctraSesFileWriter)
  // ---------------------------------------------------------------------------

  private static void writeSessionScope(BasicBoard board, IdentifierType identifierType,
      IndentFileWriter file, String sessionName, String designName) throws IOException {
    double scaleFactor =
        board.communication.coordinate_transform.dsn_to_board(1) / board.communication.resolution;
    CoordinateTransform coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);
    file.start_scope(false);
    file.write("session ");
    identifierType.write(sessionName, file);
    file.new_line();
    file.write("(base_design ");
    identifierType.write(designName, file);
    file.write(")");
    writePlacement(board, identifierType, coordinateTransform, file);
    writeWasIs(board, identifierType, file);
    writeRoutes(board, identifierType, coordinateTransform, file);
    file.end_scope();
  }

  private static void writePlacement(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("placement");
    Resolution.write_scope(file, board.communication);

    if (board.library.packages != null) {
      for (int i = 1; i <= board.library.packages.count(); i++) {
        writeComponents(board, identifierType, coordinateTransform, file,
            board.library.packages.get(i));
      }
    }

    file.end_scope();
  }

  /** Writes all components with the given package to the session file. */
  private static void writeComponents(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file, Package pkg)
      throws IOException {
    Collection<Item> boardItems = board.get_items();
    boolean componentFound = false;
    for (int i = 1; i <= board.components.count(); i++) {
      app.freerouting.board.Component currComponent = board.components.get(i);
      if (currComponent.get_package() == pkg) {
        // check that not all items of the component are deleted
        boolean undeletedItemFound = false;
        for (Item currItem : boardItems) {
          if (currItem.get_component_no() == currComponent.no) {
            undeletedItemFound = true;
            break;
          }
        }
        if (undeletedItemFound) {
          if (!componentFound) {
            file.start_scope();
            file.write("component ");
            identifierType.write(pkg.name, file);
            componentFound = true;
          }
          writeComponent(board, identifierType, coordinateTransform, file, currComponent);
        }
      }
    }
    if (componentFound) {
      file.end_scope();
    }
  }

  private static void writeComponent(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file,
      app.freerouting.board.Component component) throws IOException {
    file.new_line();
    file.write("(place ");
    identifierType.write(component.name, file);
    double[] location = coordinateTransform.board_to_dsn(component.get_location().to_float());
    int xCoor = (int) Math.round(location[0]);
    int yCoor = (int) Math.round(location[1]);
    file.write(" ");
    file.write(String.valueOf(xCoor));
    file.write(" ");
    file.write(String.valueOf(yCoor));
    if (component.placed_on_front()) {
      file.write(" front ");
    } else {
      file.write(" back ");
    }
    int rotation = (int) Math.round(component.get_rotation_in_degree());
    file.write(String.valueOf(rotation));
    if (component.position_fixed) {
      file.new_line();
      file.write(" (lock_type position)");
    }
    file.write(")");
  }

  private static void writeWasIs(BasicBoard board, IdentifierType identifierType,
      IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("was_is");
    Collection<Pin> boardPins = board.get_pins();
    for (Pin currPin : boardPins) {
      Pin swappedWith = currPin.get_changed_to();
      if (currPin.get_changed_to() != currPin) {
        file.new_line();
        file.write("(pins ");
        app.freerouting.board.Component currCmp =
            board.components.get(currPin.get_component_no());
        if (currCmp != null) {
          identifierType.write(currCmp.name, file);
          file.write("-");
          Package.Pin packagePin =
              currCmp.get_package().get_pin(currPin.get_index_in_package());
          identifierType.write(packagePin.name, file);
        } else {
          FRLogger.warn("SesWriter.writeWasIs: component not found");
        }
        file.write(" ");
        app.freerouting.board.Component swapCmp =
            board.components.get(swappedWith.get_component_no());
        if (swapCmp != null) {
          identifierType.write(swapCmp.name, file);
          file.write("-");
          Package.Pin packagePin =
              swapCmp.get_package().get_pin(swappedWith.get_index_in_package());
          identifierType.write(packagePin.name, file);
        } else {
          FRLogger.warn("SesWriter.writeWasIs: component not found");
        }
        file.write(")");
      }
    }
    file.end_scope();
  }

  private static void writeRoutes(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("routes ");
    Resolution.write_scope(file, board.communication);
    Parser.write_scope(file, board.communication.specctra_parser_info, identifierType, true);
    writeLibrary(board, identifierType, coordinateTransform, file);
    writeNetwork(board, identifierType, coordinateTransform, file);
    file.end_scope();
  }

  private static void writeLibrary(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("library_out ");
    for (int i = 0; i < board.library.via_padstack_count(); i++) {
      writePadstack(board.library.get_via_padstack(i), board, identifierType, coordinateTransform,
          file);
    }
    file.end_scope();
  }

  private static void writePadstack(Padstack padstack, BasicBoard board,
      IdentifierType identifierType, CoordinateTransform coordinateTransform, IndentFileWriter file)
      throws IOException {
    // determine the layer range covered by this padstack
    int firstLayerNo = 0;
    while (firstLayerNo < board.get_layer_count() && padstack.get_shape(firstLayerNo) == null) {
      ++firstLayerNo;
    }
    int lastLayerNo = board.get_layer_count() - 1;
    while (lastLayerNo >= 0 && padstack.get_shape(lastLayerNo) == null) {
      --lastLayerNo;
    }
    if (firstLayerNo >= board.get_layer_count() || lastLayerNo < 0) {
      FRLogger.warn("SesWriter.writePadstack: padstack shape not found");
      return;
    }

    file.start_scope();
    file.write("padstack ");
    identifierType.write(padstack.name, file);
    for (int i = firstLayerNo; i <= lastLayerNo; i++) {
      app.freerouting.geometry.planar.Shape currBoardShape = padstack.get_shape(i);
      if (currBoardShape == null) {
        continue;
      }
      app.freerouting.board.Layer boardLayer = board.layer_structure.arr[i];
      Layer currLayer = new Layer(boardLayer.name, i, boardLayer.is_signal);
      Shape currShape = coordinateTransform.board_to_dsn_rel(currBoardShape, currLayer);
      file.start_scope();
      file.write("shape");
      currShape.write_scope_int(file, identifierType);
      file.end_scope();
    }
    if (!padstack.attach_allowed) {
      file.new_line();
      file.write("(attach off)");
    }
    file.end_scope();
  }

  private static void writeNetwork(BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("network_out ");
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      writeNet(i, board, identifierType, coordinateTransform, file);
    }
    file.end_scope();
  }

  private static void writeNet(int netNo, BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    Collection<Item> netItems = board.get_connectable_items(netNo);
    boolean headerWritten = false;
    for (Item currItem : netItems) {
      if (currItem.get_fixed_state() == FixedState.SYSTEM_FIXED) {
        continue;
      }
      boolean isWire = currItem instanceof PolylineTrace;
      boolean isVia = currItem instanceof Via;
      boolean isConductionArea = currItem instanceof ConductionArea
          && board.layer_structure.arr[currItem.first_layer()].is_signal;
      if (!headerWritten && (isWire || isVia || isConductionArea)) {
        file.start_scope();
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
        writeConductionArea((ConductionArea) currItem, board, identifierType, coordinateTransform,
            file);
      }
    }
    if (headerWritten) {
      file.end_scope();
    }
  }

  private static void writeWire(PolylineTrace wire, BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    int layerNo = wire.get_layer();
    app.freerouting.board.Layer boardLayer = board.layer_structure.arr[layerNo];
    int wireWidth =
        (int) Math.round(coordinateTransform.board_to_dsn(2 * wire.get_half_width()));
    file.start_scope();
    file.write("wire");
    Point[] cornerArr = wire.polyline().corner_arr();
    int[] coors = new int[2 * cornerArr.length];
    int cornerIndex = 0;
    int[] prevCoors = null;
    for (int i = 0; i < cornerArr.length; i++) {
      double[] currFloatCoors =
          coordinateTransform.board_to_dsn(cornerArr[i].to_float());
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
    writeFixedState(file, wire.get_fixed_state());
    file.end_scope();
  }

  private static void writeVia(Via via, BasicBoard board, IdentifierType identifierType,
      CoordinateTransform coordinateTransform, IndentFileWriter file) throws IOException {
    Padstack viaPadstack = via.get_padstack();
    FloatPoint viaLocation = via.get_center().to_float();
    file.start_scope();
    file.write("via ");
    identifierType.write(viaPadstack.name, file);
    file.write(" ");
    double[] location = coordinateTransform.board_to_dsn(viaLocation);
    int xCoor = (int) Math.round(location[0]);
    file.write(String.valueOf(xCoor));
    file.write(" ");
    int yCoor = (int) Math.round(location[1]);
    file.write(String.valueOf(yCoor));
    writeFixedState(file, via.get_fixed_state());
    file.end_scope();
  }

  private static void writeFixedState(IndentFileWriter file, FixedState fixedState)
      throws IOException {
    if (fixedState.ordinal() <= FixedState.SHOVE_FIXED.ordinal()) {
      return;
    }
    file.new_line();
    file.write("(type ");
    if (fixedState == FixedState.SYSTEM_FIXED) {
      file.write("fix)");
    } else {
      file.write("protect)");
    }
  }

  private static void writePath(String layerName, int width, int[] coors,
      IdentifierType identifierType, IndentFileWriter file) throws IOException {
    file.start_scope();
    file.write("path ");
    identifierType.write(layerName, file);
    file.write(" ");
    file.write(String.valueOf(width));
    int cornerCount = coors.length / 2;
    for (int i = 0; i < cornerCount; i++) {
      file.new_line();
      file.write(String.valueOf(coors[2 * i]));
      file.write(" ");
      file.write(String.valueOf(coors[2 * i + 1]));
    }
    file.end_scope();
  }

  private static void writeConductionArea(ConductionArea conductionArea, BasicBoard board,
      IdentifierType identifierType, CoordinateTransform coordinateTransform, IndentFileWriter file)
      throws IOException {
    int netCount = conductionArea.net_count();
    if (netCount != 1) {
      FRLogger.warn("SesWriter.writeConductionArea: unexpected net count");
      return;
    }
    Area currArea = conductionArea.get_area();
    int layerNo = conductionArea.get_layer();
    app.freerouting.board.Layer boardLayer = board.layer_structure.arr[layerNo];
    Layer conductionLayer = new Layer(boardLayer.name, layerNo, boardLayer.is_signal);
    app.freerouting.geometry.planar.Shape boundaryShape;
    app.freerouting.geometry.planar.Shape[] holes;
    if (currArea instanceof app.freerouting.geometry.planar.Shape shape) {
      boundaryShape = shape;
      holes = new app.freerouting.geometry.planar.Shape[0];
    } else {
      boundaryShape = currArea.get_border();
      holes = currArea.get_holes();
    }
    file.start_scope();
    file.write("wire ");
    Shape dsnShape = coordinateTransform.board_to_dsn(boundaryShape, conductionLayer);
    if (dsnShape != null) {
      dsnShape.write_scope_int(file, identifierType);
    }
    for (int i = 0; i < holes.length; i++) {
      Shape dsnHole = coordinateTransform.board_to_dsn(holes[i], conductionLayer);
      dsnHole.write_hole_scope(file, identifierType);
    }
    file.end_scope();
  }
}

