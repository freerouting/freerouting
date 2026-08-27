package app.freerouting.io.specctra.parser;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;

/** Transforms a Specctra session file into an Autodesk Fusion command script (.scr) file. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class SessionToFusion {

  /** The function for scanning the session file. */
  private final IJFlexScanner scanner;

  /** The generated Autodesk Fusion script file writer. */
  private final OutputStreamWriter outFile;

  /** Some information is read from the board, because it is not contained in the session file. */
  private final BasicBoard board;

  /** The layer structure in specctra format. */
  private final LayerStructure specctraLayerStructure;

  private final app.freerouting.board.model.structure.Unit unit;

  /** The scale factor for transforming coordinates from the session file to Fusion. */
  private final double sessionFileScaleDenominator;

  /** The scale factor for transforming coordinates from the board to Fusion. */
  private final double boardScaleFactor;

  SessionToFusion(
      IJFlexScanner scanner,
      OutputStreamWriter outFile,
      BasicBoard board,
      app.freerouting.board.model.structure.Unit unit,
      double sessionFileScaleDominator,
      double boardScaleFactor) {
    this.scanner = scanner;
    this.outFile = outFile;
    this.board = board;
    this.specctraLayerStructure = new LayerStructure(board.layerStructure);
    this.unit = unit;
    this.sessionFileScaleDenominator = sessionFileScaleDominator;
    this.boardScaleFactor = boardScaleFactor;
  }

  public static boolean getInstance(
      InputStream session, OutputStream outputStream, BasicBoard board) {
    if (outputStream == null || session == null || board == null) {
      return false;
    }

    IJFlexScanner scanner = new SpecctraDsnStreamReader(session);
    OutputStreamWriter fileWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

    double boardScaleFactor = board.communication.coordinateTransform.boardToDsn(1);
    SessionToFusion newInstance =
        new SessionToFusion(
            scanner,
            fileWriter,
            board,
            board.communication.unit,
            board.communication.resolution,
            boardScaleFactor);

    boolean result;
    try {
      result = newInstance.processSessionScope();
    } catch (IOException e) {
      FRLogger.error("unable to process session scope", e);
      result = false;
    }

    try {
      session.close();
      fileWriter.close();
    } catch (IOException e) {
      FRLogger.error("unable to close files", e);
    }
    return result;
  }

  /** Processes the outmost scope of the session file. Returns false, if an error occurred. */
  private boolean processSessionScope() throws IOException {
    Object nextToken = null;
    for (int i = 0; i < 3; i++) {
      nextToken = this.scanner.nextToken();
      boolean keywordOk = true;
      if (i == 0) {
        keywordOk = nextToken == Keyword.OPEN_BRACKET;
      } else if (i == 1) {
        keywordOk = nextToken == Keyword.SESSION;
        this.scanner.yybegin(
            SpecctraDsnStreamReader.NAME); // to overread the name of the pcb for i = 2
      }
      if (!keywordOk) {
        FRLogger.warn(
            "SessionToFusion.process_session_scope specctra session file format expected");
        return false;
      }
    }

    // Write header of the script file
    this.outFile.write("GRID ");
    this.outFile.write(this.unit.toString());
    this.outFile.write("\n");
    this.outFile.write("SET WIRE_BEND 2\n");
    this.outFile.write("SET OPTIMIZING OFF\n");

    // Activate all signal layers
    for (int i = 0; i < this.board.layerStructure.layers.length; i++) {
      this.outFile.write("LAYER " + this.getFusionLayerString(i) + ";\n");
    }

    // Standard CAD layers: 17 (Pads), 18 (Vias), 19 (Unrouted), 20 (Dimension)
    this.outFile.write("LAYER 17;\n");
    this.outFile.write("LAYER 18;\n");
    this.outFile.write("LAYER 19;\n");
    this.outFile.write("LAYER 20;\n");

    // Remove the complete existing route using GROUP and RIPUP
    IntBox boardBoundingBox = this.board.getBoundingBox();
    float minX = (float) this.boardScaleFactor * (boardBoundingBox.ll.x - 1);
    float minY = (float) this.boardScaleFactor * (boardBoundingBox.ll.y - 1);
    float maxX = (float) this.boardScaleFactor * (boardBoundingBox.ur.x + 1);
    float maxY = (float) this.boardScaleFactor * (boardBoundingBox.ur.y + 1);

    this.outFile.write("GROUP (");
    this.outFile.write(formatCoordinate(minX));
    this.outFile.write(" ");
    this.outFile.write(formatCoordinate(minY));
    this.outFile.write(") (");
    this.outFile.write(formatCoordinate(maxX));
    this.outFile.write(" ");
    this.outFile.write(formatCoordinate(maxY));
    this.outFile.write(");\n");
    this.outFile.write("RIPUP;\n");

    // Read direct subscopes of session scope
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return true;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ROUTES) {
          if (!processRoutesScope()) {
            return false;
          }
        } else if (nextToken == Keyword.PLACEMENT_SCOPE) {
          if (!processPlacementScope()) {
            return false;
          }
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }

    this.outFile.write("RATSNEST\n");
    return true;
  }

  private boolean processPlacementScope() throws IOException {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.COMPONENT_SCOPE) {
          if (!processComponentPlacement()) {
            return false;
          }
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
    processSwappedPins();
    return true;
  }

  private boolean processComponentPlacement() throws IOException {
    ComponentPlacement componentPlacement = Component.readScope(this.scanner);
    if (componentPlacement == null) {
      return false;
    }
    for (ComponentPlacement.ComponentLocation currentLocation : componentPlacement.locations) {
      this.outFile.write("ROTATE =");
      int rotation = (int) Math.round(currentLocation.rotation);
      String rotationString = currentLocation.isFront ? ("R" + rotation) : ("MR" + rotation);
      this.outFile.write(rotationString);
      this.outFile.write(" '");
      this.outFile.write(currentLocation.name);
      this.outFile.write("';\n");
      this.outFile.write("move '");
      this.outFile.write(currentLocation.name);
      this.outFile.write("' (");
      double xcoordinate = currentLocation.coor[0] / this.sessionFileScaleDenominator;
      this.outFile.write(formatCoordinate(xcoordinate));
      this.outFile.write(" ");
      double ycoordinate = currentLocation.coor[1] / this.sessionFileScaleDenominator;
      this.outFile.write(formatCoordinate(ycoordinate));
      this.outFile.write(");\n");
    }
    return true;
  }

  private boolean processRoutesScope() throws IOException {
    boolean result = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.NETWORK_OUT) {
          result = processNetworkScope();
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean processNetworkScope() throws IOException {
    boolean result = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.NET) {
          result = processNetScope();
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean processNetScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof String netName)) {
      FRLogger.warn(
          "SessionToFusion.process_net_scope: String expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    this.scanner.setScopeIdentifier(netName);

    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        return true;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.WIRE) {
          if (!processWireScope(netName)) {
            return false;
          }
        } else if (nextToken == Keyword.VIA) {
          if (!processViaScope(netName)) {
            return false;
          }
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
    return true;
  }

  private boolean processWireScope(String netName) throws IOException {
    PolygonPath wirePath = null;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        FRLogger.warn(
            "SessionToFusion.process_wire_scope: unexpected end of file at '"
                + this.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.POLYGON_PATH) {
          wirePath = Shape.readPolygonPathScope(this.scanner, this.specctraLayerStructure);
        } else {
          ScopeKeyword.skipScope(this.scanner);
        }
      }
    }
    if (wirePath == null) {
      return true;
    }

    this.outFile.write("CHANGE LAYER ");
    int layerIndex = this.specctraLayerStructure.getNo(wirePath.layer.name);
    if (layerIndex >= 0) {
      this.outFile.write(getFusionLayerString(layerIndex));
    } else {
      String[] namePieces = wirePath.layer.name.split("#", 2);
      this.outFile.write(namePieces[0]);
    }
    this.outFile.write(";\n");

    this.outFile.write("WIRE '");
    this.outFile.write(netName);
    this.outFile.write("' ");
    final double wireWidth = wirePath.width / this.sessionFileScaleDenominator;
    this.outFile.write(formatCoordinate(wireWidth));
    this.outFile.write(" (");
    for (int i = 0; i < wirePath.coordinateArr.length; i++) {
      double wireCoor = wirePath.coordinateArr[i] / this.sessionFileScaleDenominator;
      this.outFile.write(formatCoordinate(wireCoor));
      if (i % 2 == 0) {
        this.outFile.write(" ");
      } else {
        if (i == wirePath.coordinateArr.length - 1) {
          this.outFile.write(")");
        } else {
          this.outFile.write(") (");
        }
      }
    }
    this.outFile.write(";\n");

    return true;
  }

  private boolean processViaScope(String netName) throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof String padstackName)) {
      FRLogger.warn(
          "SessionToFusion.process_via_scope: padstack name expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }
    this.scanner.setScopeIdentifier(padstackName);

    double[] location = new double[2];
    for (int i = 0; i < 2; i++) {
      nextToken = this.scanner.nextToken();
      if (nextToken instanceof Double double1) {
        location[i] = double1;
      } else if (nextToken instanceof Integer integer) {
        location[i] = integer;
      } else {
        FRLogger.warn(
            "SessionToFusion.process_via_scope: number expected at '"
                + this.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
    }
    nextToken = this.scanner.nextToken();
    while (nextToken == Keyword.OPEN_BRACKET) {
      ScopeKeyword.skipScope(this.scanner);
      nextToken = this.scanner.nextToken();
    }
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "SessionToFusion.process_via_scope: closing bracket expected at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    Padstack viaPadstack = this.board.library.padstacks.get(padstackName);
    if (viaPadstack == null) {
      FRLogger.warn(
          "SessionToFusion.process_via_scope: via padstack not found at '"
              + this.scanner.getScopeIdentifier()
              + "'");
      return false;
    }

    ConvexShape viaShape = viaPadstack.getShape(viaPadstack.fromLayer());
    double viaDiameter = viaShape.maxWidth() * this.boardScaleFactor;

    String[] nameParts = viaPadstack.name.split("\\$", 3);

    this.outFile.write("CHANGE DRILL ");
    if (nameParts.length > 1) {
      this.outFile.write(nameParts[1]);
    } else {
      this.outFile.write("0.1");
    }
    this.outFile.write(";\n");

    this.outFile.write("VIA '");
    this.outFile.write(netName);
    this.outFile.write("' ");
    this.outFile.write(formatCoordinate(viaDiameter));

    if (viaShape instanceof Circle) {
      this.outFile.write(" round ");
    } else if (viaShape instanceof IntOctagon) {
      this.outFile.write(" octagon ");
    } else {
      this.outFile.write(" square ");
    }

    boolean isThroughVia =
        (viaPadstack.fromLayer() <= 0
            && viaPadstack.toLayer() >= this.board.layerStructure.layers.length - 1);
    if (!isThroughVia) {
      this.outFile.write(getFusionLayerString(viaPadstack.fromLayer()));
      this.outFile.write("-");
      this.outFile.write(getFusionLayerString(viaPadstack.toLayer()));
      this.outFile.write(" ");
    }

    this.outFile.write("(");
    double xcoordinate = location[0] / this.sessionFileScaleDenominator;
    this.outFile.write(formatCoordinate(xcoordinate));
    this.outFile.write(" ");
    double ycoordinate = location[1] / this.sessionFileScaleDenominator;
    this.outFile.write(formatCoordinate(ycoordinate));
    this.outFile.write(");\n");

    return true;
  }

  private String getFusionLayerString(int layerIndex) {
    if (layerIndex < 0 || layerIndex >= specctraLayerStructure.layers.length) {
      return "0";
    }
    String layerName = this.specctraLayerStructure.layers[layerIndex].name;
    String[] namePieces = layerName.split("#", 2);
    if (namePieces[0].matches("\\d+")) {
      return namePieces[0];
    }
    return String.valueOf(layerIndex + 1);
  }

  private boolean processSwappedPins() throws IOException {
    for (int i = 1; i <= this.board.components.count(); i++) {
      if (!processSwappedPins(i)) {
        return false;
      }
    }
    return true;
  }

  private boolean processSwappedPins(int componentId) throws IOException {
    Collection<Pin> componentPins = this.board.getComponentPins(componentId);
    boolean componentHasSwappedPins = false;
    for (Pin currentPin : componentPins) {
      if (currentPin.getChangedTo() != currentPin) {
        componentHasSwappedPins = true;
        break;
      }
    }
    if (!componentHasSwappedPins) {
      return true;
    }
    PinInfo[] pinInfoArr = new PinInfo[componentPins.size()];
    int i = 0;
    for (Pin currentPin : componentPins) {
      pinInfoArr[i] = new PinInfo(currentPin);
      ++i;
    }
    for (i = 0; i < pinInfoArr.length; i++) {
      PinInfo currentPinInfo = pinInfoArr[i];
      if (currentPinInfo.currentChangedTo != currentPinInfo.pin.getChangedTo()) {
        PinInfo otherPinInfo = null;
        for (int j = i + 1; j < pinInfoArr.length; j++) {
          if (pinInfoArr[j].pin.getChangedTo() == currentPinInfo.pin) {
            otherPinInfo = pinInfoArr[j];
          }
        }
        if (otherPinInfo == null) {
          FRLogger.warn(
              "SessionToFusion.process_swapped_pins: otherPinInfo not found at '"
                  + this.scanner.getScopeIdentifier()
                  + "'");
          return false;
        }
        writePinSwap(currentPinInfo.pin, otherPinInfo.pin);
        currentPinInfo.currentChangedTo = otherPinInfo.pin;
        otherPinInfo.currentChangedTo = currentPinInfo.pin;
      }
    }
    return true;
  }

  private void writePinSwap(Pin pin1, Pin pin2) throws IOException {
    int layerIndex = Math.max(pin1.firstLayer(), pin2.firstLayer());
    this.outFile.write("CHANGE LAYER ");
    this.outFile.write(getFusionLayerString(layerIndex));
    this.outFile.write(";\n");

    double[] location1 =
        this.board.communication.coordinateTransform.boardToDsn(pin1.getCenter().toFloat());
    final double[] location2 =
        this.board.communication.coordinateTransform.boardToDsn(pin2.getCenter().toFloat());

    this.outFile.write("PINSWAP ");
    this.outFile.write(" (");
    this.outFile.write(formatCoordinate(location1[0]));
    this.outFile.write(" ");
    this.outFile.write(formatCoordinate(location1[1]));
    this.outFile.write(") (");
    this.outFile.write(formatCoordinate(location2[0]));
    this.outFile.write(" ");
    this.outFile.write(formatCoordinate(location2[1]));
    this.outFile.write(");\n");
  }

  private static String formatCoordinate(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "0";
    }
    String str = String.format(Locale.US, "%.6f", value);
    if (str.contains(".")) {
      str = str.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    return str.isEmpty() ? "0" : str;
  }

  private static class PinInfo {

    final Pin pin;
    Pin currentChangedTo;

    PinInfo(Pin pin) {
      this.pin = pin;
      currentChangedTo = pin;
    }
  }
}
