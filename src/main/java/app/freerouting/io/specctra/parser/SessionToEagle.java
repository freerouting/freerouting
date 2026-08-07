package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Pin;
import app.freerouting.board.Unit;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import javax.swing.JFrame;

/** Transforms a Specctra session file into an Eagle script file. */
public class SessionToEagle extends JFrame {

  /** The function for scanning the session file */
  private final IJFlexScanner scanner;

  /** The generated Eagle script file. */
  private final OutputStreamWriter outFile;

  /**
   * Some information is read from the board, because it is not contained in the specctra session
   * file.
   */
  private final BasicBoard board;

  /** The layer structure in specctra format */
  private final LayerStructure specctraLayerStructure;

  private final Unit unit;

  /** The scale factor for transforming coordinates from the session file to Eagle */
  private final double sessionFileScaleDenominator;

  /** The scale factor for transforming coordinates from the board to Eagle */
  private final double boardScaleFactor;

  SessionToEagle(
      IJFlexScanner p_scanner,
      OutputStreamWriter p_out_file,
      BasicBoard p_board,
      Unit p_unit,
      double p_session_file_scale_dominator,
      double p_board_scale_factor) {
    scanner = p_scanner;
    outFile = p_out_file;
    board = p_board;
    this.specctraLayerStructure = new LayerStructure(p_board.layerStructure);
    unit = p_unit;
    sessionFileScaleDenominator = p_session_file_scale_dominator;
    boardScaleFactor = p_board_scale_factor;
  }

  public static boolean get_instance(
      InputStream p_session, OutputStream p_output_stream, BasicBoard p_board) {
    if (p_output_stream == null) {
      return false;
    }

    // create a scanner for reading the session_file.

    IJFlexScanner scanner = new SpecctraDsnStreamReader(p_session);

    // create a fileWriter for the eagle script file.
    OutputStreamWriter fileWriter = new OutputStreamWriter(p_output_stream);

    double boardScaleFactor = p_board.communication.coordinateTransform.board_to_dsn(1);
    SessionToEagle newInstance =
        new SessionToEagle(
            scanner,
            fileWriter,
            p_board,
            p_board.communication.unit,
            p_board.communication.resolution,
            boardScaleFactor);

    boolean result;
    try {
      result = newInstance.process_session_scope();
    } catch (IOException e) {
      FRLogger.error("unable to process session scope", e);
      result = false;
    }

    // close files
    try {
      p_session.close();
      fileWriter.close();
    } catch (IOException e) {
      FRLogger.error("unable to close files", e);
    }
    return result;
  }

  /** Processes the outmost scope of the session file. Returns false, if an error occurred. */
  private boolean process_session_scope() throws IOException {

    // read the first line of the session file
    Object nextToken = null;
    for (int i = 0; i < 3; i++) {
      nextToken = this.scanner.next_token();
      boolean keywordOk = true;
      if (i == 0) {
        keywordOk = nextToken == Keyword.OPEN_BRACKET;
      } else if (i == 1) {
        keywordOk = nextToken == Keyword.SESSION;
        this.scanner.yybegin(
            SpecctraDsnStreamReader.NAME); // to overread the name of the pcb for i = 2
      }
      if (!keywordOk) {
        FRLogger.warn("SessionToEagle.process_session_scope specctra session file format expected");
        return false;
      }
    }

    // Write the header of the eagle script file.

    this.outFile.write("GRID ");
    this.outFile.write(this.unit.toString());
    this.outFile.write("\n");
    this.outFile.write("SET WIRE_BEND 2\n");
    this.outFile.write("SET OPTIMIZING OFF\n");

    // Activate all layers in Eagle.

    for (int i = 0; i < this.board.layerStructure.arr.length; i++) {
      this.outFile.write("LAYER " + this.get_eagle_layer_string(i) + ";\n");
    }

    this.outFile.write("LAYER 17;\n");
    this.outFile.write("LAYER 18;\n");
    this.outFile.write("LAYER 19;\n");
    this.outFile.write("LAYER 20;\n");
    this.outFile.write("LAYER 23;\n");
    this.outFile.write("LAYER 24;\n");

    // Generate Code to remove the complete route.
    // Write a bounding rectangle with GROUP (Min_X-1 Min_Y-1) (Max_X+1 Max_Y+1);

    IntBox boardBoundingBox = this.board.get_bounding_box();

    float minX = (float) this.boardScaleFactor * (boardBoundingBox.ll.x - 1);
    float minY = (float) this.boardScaleFactor * (boardBoundingBox.ll.y - 1);
    float maxX = (float) this.boardScaleFactor * (boardBoundingBox.ur.x + 1);
    float maxY = (float) this.boardScaleFactor * (boardBoundingBox.ur.y + 1);

    this.outFile.write("GROUP (");
    this.outFile.write(String.valueOf(minX));
    this.outFile.write(" ");
    this.outFile.write(String.valueOf(minY));
    this.outFile.write(") (");
    this.outFile.write(String.valueOf(maxX));
    this.outFile.write(" ");
    this.outFile.write(String.valueOf(maxY));
    this.outFile.write(");\n");
    this.outFile.write("RIPUP;\n");

    // read the direct subscopes of the session scope
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        // end of file
        return true;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.ROUTES) {
          if (!process_routes_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.PLACEMENT_SCOPE) {
          if (!process_placement_scope()) {
            return false;
          }
        } else {
          // overread all scopes except the routes scope for the time being
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    // Wird nur einmal am End benoetigt!
    this.outFile.write("RATSNEST\n");
    return true;
  }

  private boolean process_placement_scope() throws IOException {
    // read the component scopes
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {

        if (nextToken == Keyword.COMPONENT_SCOPE) {
          if (!process_component_placement()) {
            return false;
          }
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    process_swapped_pins();
    return true;
  }

  private boolean process_component_placement() throws IOException {
    ComponentPlacement componentPlacement = Component.read_scope(this.scanner);
    if (componentPlacement == null) {
      return false;
    }
    for (ComponentPlacement.ComponentLocation curr_location : componentPlacement.locations) {
      this.outFile.write("ROTATE =");
      int rotation = (int) Math.round(curr_location.rotation);
      String rotationString;
      if (curr_location.isFront) {
        rotationString = "R" + rotation;
      } else {
        rotationString = "MR" + rotation;
      }
      this.outFile.write(rotationString);
      this.outFile.write(" '");
      this.outFile.write(curr_location.name);
      this.outFile.write("';\n");
      this.outFile.write("move '");
      this.outFile.write(curr_location.name);
      this.outFile.write("' (");
      double xCoor = curr_location.coor[0] / this.sessionFileScaleDenominator;
      this.outFile.write(String.valueOf(xCoor));
      this.outFile.write(" ");
      double yCoor = curr_location.coor[1] / this.sessionFileScaleDenominator;
      this.outFile.write(String.valueOf(yCoor));
      this.outFile.write(");\n");
    }
    return true;
  }

  private boolean process_routes_scope() throws IOException {
    // read the direct subscopes of the routes scope
    boolean result = true;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {

        if (nextToken == Keyword.NETWORK_OUT) {
          result = process_network_scope();
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean process_network_scope() throws IOException {
    boolean result = true;
    Object nextToken = null;
    // read the net scopes
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {

        if (nextToken == Keyword.NET) {
          result = process_net_scope();
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return result;
  }

  private boolean process_net_scope() throws IOException {
    // read the net name
    Object nextToken = this.scanner.next_token();
    if (!(nextToken instanceof String netName)) {
      FRLogger.warn(
          "SessionToEagle.process_net_scope: String expected at '"
              + this.scanner.get_scope_identifier()
              + "'");
      return false;
    }
    this.scanner.set_scope_identifier(netName);

    // Delete all unfixed traces and vias for net netName in Eagle's database.

    // read the wires and vias of this net
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        // end of file
        return true;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.WIRE) {
          if (!process_wire_scope(netName)) {
            return false;
          }
        } else if (nextToken == Keyword.VIA) {
          if (!process_via_scope(netName)) {
            return false;
          }
        } else {
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    return true;
  }

  private boolean process_wire_scope(String p_net_name) throws IOException {
    PolygonPath wirePath = null;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      nextToken = this.scanner.next_token();
      if (nextToken == null) {
        FRLogger.warn(
            "SessionToEagle.process_wire_scope: unexpected end of file at '"
                + this.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.POLYGON_PATH) {
          wirePath = Shape.read_polygon_path_scope(this.scanner, this.specctraLayerStructure);
        } else {
          ScopeKeyword.skip_scope(this.scanner);
        }
      }
    }
    if (wirePath == null) {
      // conduction areas are skipped
      return true;
    }

    this.outFile.write("CHANGE LAYER ");

    this.outFile.write(wirePath.layer.name);
    this.outFile.write(";\n");

    // WIRE ['signal_name'] [width] [ROUND | FLAT]  [curve | @radius]

    this.outFile.write("WIRE '");

    this.outFile.write(p_net_name);
    this.outFile.write("' ");
    double wireWidth = wirePath.width / this.sessionFileScaleDenominator;
    this.outFile.write(String.valueOf(wireWidth));
    this.outFile.write(" (");
    for (int i = 0; i < wirePath.coordinateArr.length; i++) {
      double wireCoor = wirePath.coordinateArr[i] / this.sessionFileScaleDenominator;
      this.outFile.write(String.valueOf(wireCoor));
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

  private boolean process_via_scope(String p_net_name) throws IOException {
    // read the padstack name
    Object nextToken = this.scanner.next_token();
    if (!(nextToken instanceof String padstackName)) {
      FRLogger.warn(
          "SessionToEagle.process_via_scope: padstack name expected at '"
              + this.scanner.get_scope_identifier()
              + "'");
      return false;
    }
    this.scanner.set_scope_identifier(padstackName);
    // read the location
    double[] location = new double[2];
    for (int i = 0; i < 2; i++) {
      nextToken = this.scanner.next_token();
      if (nextToken instanceof Double double1) {
        location[i] = double1;
      } else if (nextToken instanceof Integer integer) {
        location[i] = integer;
      } else {
        FRLogger.warn(
            "SessionToEagle.process_via_scope: number expected at '"
                + this.scanner.get_scope_identifier()
                + "'");
        return false;
      }
    }
    nextToken = this.scanner.next_token();
    while (nextToken == Keyword.OPEN_BRACKET) {
      // skip unknown scopes
      ScopeKeyword.skip_scope(this.scanner);
      nextToken = this.scanner.next_token();
    }
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "SessionToEagle.process_via_scope: closing bracket expected at '"
              + this.scanner.get_scope_identifier()
              + "'");
      return false;
    }

    Padstack viaPadstack = this.board.library.padstacks.get(padstackName);

    if (viaPadstack == null) {
      FRLogger.warn(
          "SessionToEagle.process_via_scope: via padstack not found at '"
              + this.scanner.get_scope_identifier()
              + "'");
      return false;
    }

    ConvexShape viaShape = viaPadstack.get_shape(viaPadstack.from_layer());

    double viaDiameter = viaShape.max_width() * this.boardScaleFactor;

    // The Padstack name is of the form Name$drill_diameter$fromLayer-toLayer

    String[] nameParts = viaPadstack.name.split("\\$", 3);

    // example CHANGE DRILL 0.2

    this.outFile.write("CHANGE DRILL ");
    if (nameParts.length > 1) {
      this.outFile.write(nameParts[1]);
    } else {
      // create a default drill, because it is needed in Eagle
      this.outFile.write("0.1");
    }
    this.outFile.write(";\n");

    // VIA ['signal_name'] [diameter] [shape] [layers] [flags]
    // Via Net2 0.6 round 1-4 (20.0, 222.0);
    this.outFile.write("VIA '");

    this.outFile.write(p_net_name);
    this.outFile.write("' ");

    // Durchmesser aus Padstack
    this.outFile.write(String.valueOf(viaDiameter));

    // Shape lesen und einsetzen Square / Round / Octagon
    if (viaShape instanceof Circle) {
      this.outFile.write(" round ");
    } else if (viaShape instanceof IntOctagon) {
      this.outFile.write(" octagon ");
    } else {
      this.outFile.write(" square ");
    }
    this.outFile.write(get_eagle_layer_string(viaPadstack.from_layer()));
    this.outFile.write("-");
    this.outFile.write(get_eagle_layer_string(viaPadstack.to_layer()));
    this.outFile.write(" (");
    double xCoor = location[0] / this.sessionFileScaleDenominator;
    this.outFile.write(String.valueOf(xCoor));
    this.outFile.write(" ");
    double yCoor = location[1] / this.sessionFileScaleDenominator;
    this.outFile.write(String.valueOf(yCoor));
    this.outFile.write(");\n");

    return true;
  }

  private String get_eagle_layer_string(int p_layer_no) {
    if (p_layer_no < 0 || p_layer_no >= specctraLayerStructure.arr.length) {
      return "0";
    }
    String[] namePieces = this.specctraLayerStructure.arr[p_layer_no].name.split("#", 2);
    return namePieces[0];
  }

  private boolean process_swapped_pins() throws IOException {
    for (int i = 1; i <= this.board.components.count(); i++) {
      if (!process_swapped_pins(i)) {
        return false;
      }
    }
    return true;
  }

  private boolean process_swapped_pins(int p_component_no) throws IOException {
    Collection<Pin> componentPins = this.board.get_component_pins(p_component_no);
    boolean componentHasSwappedPins = false;
    for (Pin currPin : componentPins) {
      if (currPin.get_changed_to() != currPin) {
        componentHasSwappedPins = true;
        break;
      }
    }
    if (!componentHasSwappedPins) {
      return true;
    }
    PinInfo[] pinInfoArr = new PinInfo[componentPins.size()];
    int i = 0;
    for (Pin currPin : componentPins) {
      pinInfoArr[i] = new PinInfo(currPin);
      ++i;
    }
    for (i = 0; i < pinInfoArr.length; i++) {
      PinInfo currPinInfo = pinInfoArr[i];
      if (currPinInfo.currChangedTo != currPinInfo.pin.get_changed_to()) {
        PinInfo otherPinInfo = null;
        for (int j = i + 1; j < pinInfoArr.length; j++) {
          if (pinInfoArr[j].pin.get_changed_to() == currPinInfo.pin) {
            otherPinInfo = pinInfoArr[j];
          }
        }
        if (otherPinInfo == null) {
          FRLogger.warn(
              "SessuinToEagle.process_swapped_pins: otherPinInfo not found at '"
                  + this.scanner.get_scope_identifier()
                  + "'");
          return false;
        }
        write_pin_swap(currPinInfo.pin, otherPinInfo.pin);
        currPinInfo.currChangedTo = otherPinInfo.pin;
        otherPinInfo.currChangedTo = currPinInfo.pin;
      }
    }
    return true;
  }

  private void write_pin_swap(Pin p_pin_1, Pin p_pin_2) throws IOException {
    int layerNo = Math.max(p_pin_1.first_layer(), p_pin_2.first_layer());
    String layerName = board.layerStructure.arr[layerNo].name;

    this.outFile.write("CHANGE LAYER ");
    this.outFile.write(layerName);
    this.outFile.write(";\n");

    double[] location1 =
        this.board.communication.coordinateTransform.board_to_dsn(p_pin_1.get_center().to_float());
    double[] location2 =
        this.board.communication.coordinateTransform.board_to_dsn(p_pin_2.get_center().to_float());

    this.outFile.write("PINSWAP ");
    this.outFile.write(" (");
    double currCoor = location1[0];
    this.outFile.write(String.valueOf(currCoor));
    this.outFile.write(" ");
    currCoor = location1[1];
    this.outFile.write(String.valueOf(currCoor));
    this.outFile.write(") (");
    currCoor = location2[0];
    this.outFile.write(String.valueOf(currCoor));
    this.outFile.write(" ");
    currCoor = location2[1];
    this.outFile.write(String.valueOf(currCoor));
    this.outFile.write(");\n");
  }

  private static class PinInfo {

    final Pin pin;
    Pin currChangedTo;

    PinInfo(Pin p_pin) {
      pin = p_pin;
      currChangedTo = p_pin;
    }
  }
}
