package app.freerouting.gui;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.JFrame;

/** Description of a text file, where the board independent interactive settings are stored. */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public final class GUIDefaultsFile {

  private final BoardFrame boardFrame;
  private final GuiBoardManager boardHandling;

  /** Used, when reading a defaults file, null otherwise. */
  private final GUIDefaultsScanner scanner;

  /** Used, when writing a defaults file; null otherwise. */
  private final IndentFileWriter outFile;

  private GUIDefaultsFile(
      BoardFrame boardFrame,
      GuiBoardManager boardHandling,
      GUIDefaultsScanner scanner,
      IndentFileWriter outputFile) {
    this.boardFrame = boardFrame;
    this.boardHandling = boardHandling;
    this.scanner = scanner;
    this.outFile = outputFile;
  }

  /**
   * Writes the GUI setting of p_board_frame as default to p_file. Returns false, if an error
   * occurred.
   */
  public static boolean write(
      BoardFrame boardFrame, GuiBoardManager boardHandling, OutputStream outputStream) {
    if (outputStream == null) {
      return false;
    }

    IndentFileWriter outputFile = new IndentFileWriter(outputStream);

    GUIDefaultsFile result = new GUIDefaultsFile(boardFrame, boardHandling, null, outputFile);
    try {
      result.writeDefaultsScope();
    } catch (IOException _) {
      FRLogger.warn("unable to write defaults file");
      return false;
    }

    try {
      outputFile.close();
    } catch (IOException e) {
      FRLogger.error("unable to close defaults file", e);
      return false;
    }
    return true;
  }

  /**
   * Reads the GUI setting of p_board_frame from file. Returns false, if an error occurred while
   * reading the file.
   */
  public static boolean read(
      BoardFrame boardFrame, GuiBoardManager boardHandling, InputStream inputStream) {
    if (inputStream == null) {
      return false;
    }
    GUIDefaultsScanner scanner = new GUIDefaultsScanner(inputStream);
    GUIDefaultsFile newInstance = new GUIDefaultsFile(boardFrame, boardHandling, scanner, null);
    boolean result;
    try {
      result = newInstance.readDefaultsScope();
    } catch (IOException e) {
      FRLogger.error("unable to read defaults file", e);
      result = false;
    }
    return result;
  }

  /** Skips the current scope. Returns false, if no legal scope was found. */
  private static boolean skipScope(GUIDefaultsScanner scanner) {
    int openBrackedCount = 1;
    while (openBrackedCount > 0) {
      Object currToken;
      try {
        currToken = scanner.nextToken();
      } catch (Exception e) {
        FRLogger.error("GUIDefaultsFile.skip_scope: Error while scanning file", e);
        return false;
      }
      if (currToken == null) {
        return false; // end of file
      }
      if (currToken == Keyword.OPEN_BRACKET) {
        ++openBrackedCount;
      } else if (currToken == Keyword.CLOSED_BRACKET) {
        --openBrackedCount;
      }
    }
    FRLogger.warn("GUIDefaultsFile.skip_scope: unknown scope skipped");
    return true;
  }

  private void writeDefaultsScope() throws IOException {
    outFile.startScope();
    outFile.write("gui_defaults");
    writeWindowsScope();
    writeColorsScope();
    writeParameterScope();
    outFile.endScope();
  }

  private boolean readDefaultsScope() throws IOException {
    Object nextToken = this.scanner.nextToken();

    if (nextToken != Keyword.OPEN_BRACKET) {
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.GUI_DEFAULTS) {
      return false;
    }

    // read the direct subscopes of the gui_defaults scope
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        // end of file
        return true;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (nextToken == Keyword.COLORS) {
          if (!readColorsScope()) {
            return false;
          }
        } else if (nextToken == Keyword.WINDOWS) {
          if (!readWindowsScope()) {
            return false;
          }
        } else if (nextToken == Keyword.PARAMETER) {
          if (!readParameterScope()) {
            return false;
          }
        } else {
          // overread all scopes except the routes scope for the time being
          skipScope(this.scanner);
        }
      }
    }
    this.boardFrame.refreshWindows();
    return true;
  }

  private boolean readWindowsScope() throws IOException {
    // read the direct subscopes of the windows scope
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        if (!(nextToken instanceof Keyword)) {
          FRLogger.warn("GUIDefaultsFile.windows: Keyword expected");
          return false;
        }
        if (!readFrameScope((Keyword) nextToken)) {

          return false;
        }
      }
    }
    return true;
  }

  private void writeWindowsScope() throws IOException {
    outFile.startScope();
    outFile.write("windows");
    writeFrameScope(this.boardFrame, "boardFrame");
    writeFrameScope(this.boardFrame.colorManager, "colorManager");
    writeFrameScope(this.boardFrame.visibilityWindow, "object_visibility");
    writeFrameScope(this.boardFrame.displayMiscWindow, "display_miscellaneous");

    writeFrameScope(this.boardFrame.selectParameterWindow, "select_parameter");
    writeFrameScope(this.boardFrame.routeParameterWindow, "route_parameter");
    writeFrameScope(this.boardFrame.routeParameterWindow.manualRuleWindow, "manual_rules");
    writeFrameScope(this.boardFrame.moveParameterWindow, "move_parameter");
    writeFrameScope(this.boardFrame.clearanceMatrixWindow, "clearanceMatrix");
    writeFrameScope(this.boardFrame.viaWindow, "viaRules");
    writeFrameScope(this.boardFrame.editViasWindow, "edit_vias");
    writeFrameScope(this.boardFrame.editNetRulesWindow, "edit_net_rules");
    writeFrameScope(this.boardFrame.assignNetClassesWindow, "assign_net_rules");
    writeFrameScope(this.boardFrame.padstacksWindow, "padstack_info");
    writeFrameScope(this.boardFrame.packagesWindow, "package_info");
    writeFrameScope(this.boardFrame.componentsWindow, "component_info");
    writeFrameScope(this.boardFrame.netInfoWindow, "net_info");
    writeFrameScope(this.boardFrame.incompletesWindow, "incompletes_info");
    writeFrameScope(this.boardFrame.clearanceViolationsWindow, "violations_info");
    outFile.endScope();
  }

  private boolean readFrameScope(Keyword frame) throws IOException {
    boolean isVisible;
    Object nextToken = this.scanner.nextToken();
    if (nextToken == Keyword.VISIBLE) {
      isVisible = true;
    } else if (nextToken == Keyword.NOT_VISIBLE) {
      isVisible = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: visible or not_visible expected");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.OPEN_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: open_bracket expected");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.BOUNDS) {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: bounds expected");
      return false;
    }
    Rectangle bounds = readRectangle();
    if (bounds == null) {
      return false;
    }
    for (int i = 0; i < 2; i++) {
      nextToken = this.scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("GUIDefaultsFile.read_frame_scope: closing bracket expected");
        return false;
      }
    }
    JFrame currFrame;
    switch (frame) {
      case BOARD_FRAME -> currFrame = this.boardFrame;
      case COLOR_MANAGER -> currFrame = this.boardFrame.colorManager;
      case OBJECT_VISIBILITY, LAYER_VISIBILITY -> currFrame = this.boardFrame.visibilityWindow;
      case DISPLAY_MISCELLANEOUS -> currFrame = this.boardFrame.displayMiscWindow;

      case SELECT_PARAMETER -> currFrame = this.boardFrame.selectParameterWindow;
      case ROUTE_PARAMETER -> currFrame = this.boardFrame.routeParameterWindow;
      case MANUAL_RULES -> currFrame = this.boardFrame.routeParameterWindow.manualRuleWindow;
      case MOVE_PARAMETER -> currFrame = this.boardFrame.moveParameterWindow;
      case CLEARANCE_MATRIX -> currFrame = this.boardFrame.clearanceMatrixWindow;
      case VIA_RULES -> currFrame = this.boardFrame.viaWindow;
      case EDIT_VIAS -> currFrame = this.boardFrame.editViasWindow;
      case EDIT_NET_RULES -> currFrame = this.boardFrame.editNetRulesWindow;
      case ASSIGN_NET_RULES -> currFrame = this.boardFrame.assignNetClassesWindow;
      case PADSTACK_INFO -> currFrame = this.boardFrame.padstacksWindow;
      case PACKAGE_INFO -> currFrame = this.boardFrame.packagesWindow;
      case COMPONENT_INFO -> currFrame = this.boardFrame.componentsWindow;
      case NET_INFO -> currFrame = this.boardFrame.netInfoWindow;
      case INCOMPLETES_INFO -> currFrame = this.boardFrame.incompletesWindow;
      case VIOLATIONS_INFO -> currFrame = this.boardFrame.clearanceViolationsWindow;
      default -> {
        FRLogger.warn("GUIDefaultsFile.read_frame_scope: unknown frame");
        return false;
      }
    }
    if (currFrame != null) {
      currFrame.setVisible(isVisible);
      if (frame == Keyword.BOARD_FRAME) {
        currFrame.setBounds(bounds);
      } else {
        // Set only the location.
        // Do not change the size of the frame because it depends on the layer count.
        currFrame.setLocation(bounds.getLocation());
      }
    }
    return true;
  }

  private Rectangle readRectangle() throws IOException {
    int[] coor = new int[4];
    for (int i = 0; i < 4; i++) {
      Object nextToken = this.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn("GUIDefaultsFile.read_rectangle: Integer expected");
        return null;
      }
      coor[i] = (Integer) nextToken;
    }
    return new Rectangle(coor[0], coor[1], coor[2], coor[3]);
  }

  private void writeFrameScope(JFrame frame, String frameName) throws IOException {
    if (frame == null) {
      return;
    }
    outFile.startScope();
    outFile.write(frameName);
    outFile.newLine();
    if (frame.isVisible()) {
      outFile.write("visible");
    } else {
      outFile.write("not_visible");
    }
    writeBounds(frame.getBounds());
    outFile.endScope();
  }

  private void writeBounds(Rectangle bounds) throws IOException {
    outFile.startScope();
    outFile.write("bounds");
    outFile.newLine();
    int x = (int) bounds.getX();
    outFile.write(String.valueOf(x));
    int y = (int) bounds.getY();
    outFile.write(" ");
    outFile.write(String.valueOf(y));
    int width = (int) bounds.getWidth();
    outFile.write(" ");
    outFile.write(String.valueOf(width));
    int height = (int) bounds.getHeight();
    outFile.write(" ");
    outFile.write(String.valueOf(height));
    outFile.endScope();
  }

  private boolean readColorsScope() throws IOException {
    // read the direct subscopes of the colors scope
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {

        if (nextToken == Keyword.BACKGROUND) {
          if (!readBackgroundColor()) {
            return false;
          }
        } else if (nextToken == Keyword.CONDUCTION) {
          if (!readConductionColors()) {
            return false;
          }
        } else if (nextToken == Keyword.HIGHLIGHT) {
          if (!readHighlightColor()) {
            return false;
          }
        } else if (nextToken == Keyword.INCOMPLETES) {
          if (!readIncompletesColor()) {
            return false;
          }
        } else if (nextToken == Keyword.KEEPOUT) {
          if (!readKeepoutColors()) {
            return false;
          }
        } else if (nextToken == Keyword.OUTLINE) {
          if (!readOutlineColor()) {
            return false;
          }
        } else if (nextToken == Keyword.COMPONENT_FRONT) {
          if (!readComponentColor(true)) {
            return false;
          }
        } else if (nextToken == Keyword.COMPONENT_BACK) {
          if (!readComponentColor(false)) {
            return false;
          }
        } else if (nextToken == Keyword.LENGTH_MATCHING) {
          if (!readLengthMatchingColor()) {
            return false;
          }
        } else if (nextToken == Keyword.PINS) {
          if (!readPinColors()) {
            return false;
          }
        } else if (nextToken == Keyword.TRACES) {
          if (!readTraceColors(false)) {
            return false;
          }
        } else if (nextToken == Keyword.FIXED_TRACES) {
          if (!readTraceColors(true)) {
            return false;
          }
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (!readViaKeepoutColors()) {
            return false;
          }
        } else if (nextToken == Keyword.VIAS) {
          if (!readViaColors(false)) {
            return false;
          }
        } else if (nextToken == Keyword.FIXED_VIAS) {
          if (!readViaColors(true)) {
            return false;
          }
        } else if (nextToken == Keyword.VIOLATIONS) {
          if (!readViolationsColor()) {
            return false;
          }
        } else {
          // skip unknown scope
          skipScope(this.scanner);
        }
      }
    }
    return true;
  }

  private boolean readTraceColors(boolean fixed) throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setTraceColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setTraceColors(currColors, fixed);
    return true;
  }

  private boolean readViaColors(boolean fixed) throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setViaColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setViaColors(currColors, fixed);
    return true;
  }

  private boolean readPinColors() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setPinColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setPinColors(currColors);
    return true;
  }

  private boolean readConductionColors() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setConductionColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setConductionColors(currColors);
    return true;
  }

  private boolean readKeepoutColors() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setObstacleColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setKeepoutColors(currColors);
    return true;
  }

  private boolean readViaKeepoutColors() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setViaObstacleColorIntensity(intensity);
    Color[] currColors = readColorArray();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.setViaKeepoutColors(currColors);
    return true;
  }

  private boolean readBackgroundColor() throws IOException {
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setBackgroundColor(currColor);
    this.boardFrame.setBoardBackground(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_background_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readHighlightColor() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setHighlightColorIntensity(intensity);
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setHighlightColor(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_higlight_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readIncompletesColor() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setIncompleteColorIntensity(intensity);
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setIncompleteColor(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_incompletes_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readLengthMatchingColor() throws IOException {
    double intensity = readColorIntensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.setLengthMatchingAreaColorIntensity(intensity);
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setLengthMatchingAreaColor(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_length_matching_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readViolationsColor() throws IOException {
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setViolationsColor(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_violations_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readOutlineColor() throws IOException {
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setOutlineColor(currColor);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_outline_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean readComponentColor(boolean front) throws IOException {
    Color currColor = readColor();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.setComponentColor(currColor, front);
    Object nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_component_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private double readColorIntensity() throws IOException {
    double result;
    Object nextToken = this.scanner.nextToken();
    if (nextToken instanceof Double double1) {
      result = double1;
    } else if (nextToken instanceof Integer integer) {
      result = integer;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_color_intensity: Number expected");
      result = -1;
    }
    return result;
  }

  /** Reads a {@link Color} from the defaults file, or returns {@code null} when invalid. */
  private Color readColor() throws IOException {
    int[] rgbColorArr = new int[3];
    for (int i = 0; i < 3; i++) {
      Object nextToken = this.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        if (nextToken != Keyword.CLOSED_BRACKET) {
          FRLogger.warn("GUIDefaultsFile.read_color: closing bracket expected");
        }
        return null;
      }
      rgbColorArr[i] = (Integer) nextToken;
    }
    return new Color(rgbColorArr[0], rgbColorArr[1], rgbColorArr[2]);
  }

  /**
   * Reads an array of {@link Color} values from the defaults file.
   *
   * @return the parsed colors, or an empty array when none are valid
   */
  private Color[] readColorArray() throws IOException {
    Collection<Color> colorList = new LinkedList<>();
    for (; ; ) {
      Color currColor = readColor();
      if (currColor == null) {
        break;
      }
      colorList.add(currColor);
    }
    Color[] result = new Color[colorList.size()];
    Iterator<Color> it = colorList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  private void writeColorsScope() throws IOException {
    final GraphicsContext graphicsContext = this.boardHandling.graphicsContext;
    outFile.startScope();
    outFile.write("colors");
    outFile.startScope();
    outFile.write("background");
    writeColorScope(graphicsContext.getBackgroundColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("highlight");
    writeColorIntensity(graphicsContext.getHighlightColorIntensity());
    writeColorScope(graphicsContext.getHighlightColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("incompletes");
    writeColorIntensity(graphicsContext.getIncompleteColorIntensity());
    writeColorScope(graphicsContext.getIncompleteColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("outline");
    writeColorScope(graphicsContext.getOutlineColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("component_front");
    writeColorScope(graphicsContext.getComponentColor(true));
    outFile.endScope();
    outFile.startScope();
    outFile.write("component_back");
    writeColorScope(graphicsContext.getComponentColor(false));
    outFile.endScope();
    outFile.startScope();
    outFile.write("violations");
    writeColorScope(graphicsContext.getViolationsColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("length_matching");
    writeColorIntensity(graphicsContext.getLengthMatchingAreaColorIntensity());
    writeColorScope(graphicsContext.getLengthMatchingAreaColor());
    outFile.endScope();
    outFile.startScope();
    outFile.write("traces");
    writeColorIntensity(graphicsContext.getTraceColorIntensity());
    writeColor(graphicsContext.getTraceColors(false));
    outFile.endScope();
    outFile.startScope();
    outFile.write("fixed_traces");
    writeColorIntensity(graphicsContext.getTraceColorIntensity());
    writeColor(graphicsContext.getTraceColors(true));
    outFile.endScope();
    outFile.startScope();
    outFile.write("vias");
    writeColorIntensity(graphicsContext.getViaColorIntensity());
    writeColor(graphicsContext.getViaColors(false));
    outFile.endScope();
    outFile.startScope();
    outFile.write("fixed_vias");
    writeColorIntensity(graphicsContext.getViaColorIntensity());
    writeColor(graphicsContext.getViaColors(true));
    outFile.endScope();
    outFile.startScope();
    outFile.write("pins");
    writeColorIntensity(graphicsContext.getPinColorIntensity());
    writeColor(graphicsContext.getPinColors());
    outFile.endScope();
    outFile.startScope();
    outFile.write("conduction");
    writeColorIntensity(graphicsContext.getConductionColorIntensity());
    writeColor(graphicsContext.getConductionColors());
    outFile.endScope();
    outFile.startScope();
    outFile.write("keepout");
    writeColorIntensity(graphicsContext.getObstacleColorIntensity());
    writeColor(graphicsContext.getObstacleColors());
    outFile.endScope();
    outFile.startScope();
    outFile.write("via_keepout");
    writeColorIntensity(graphicsContext.getViaObstacleColorIntensity());
    writeColor(graphicsContext.getViaObstacleColors());
    outFile.endScope();
    outFile.endScope();
  }

  private void writeColorIntensity(double value) throws IOException {
    outFile.write(" ");
    float intensity = (float) value;
    outFile.write(String.valueOf(intensity));
  }

  private void writeColorScope(Color color) throws IOException {
    outFile.newLine();
    int red = color.getRed();
    outFile.write(String.valueOf(red));
    outFile.write(" ");
    int green = color.getGreen();
    outFile.write(String.valueOf(green));
    outFile.write(" ");
    int blue = color.getBlue();
    outFile.write(String.valueOf(blue));
  }

  private void writeColor(Color[] colors) throws IOException {
    for (int i = 0; i < colors.length; i++) {
      writeColorScope(colors[i]);
    }
  }

  private boolean readParameterScope() throws IOException {
    // read the subscopes of the parameter scope
    Object nextToken = null;
    for (; ; ) {
      final Object prevToken = nextToken;
      nextToken = this.scanner.nextToken();
      if (nextToken == null) {
        // unexpected end of file
        return false;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {

        if (nextToken == Keyword.SELECTION_LAYERS) {
          if (!readSelectionLayerScope()) {
            return false;
          }
        } else if (nextToken == Keyword.VIA_SNAP_TO_SMD_CENTER) {
          if (!readViaSnapToSmdCenterScope()) {
            return false;
          }
        } else if (nextToken == Keyword.SHOVE_ENABLED) {
          if (!readShoveEnabledScope()) {
            return false;
          }
        } else if (nextToken == Keyword.DRAG_COMPONENTS_ENABLED) {
          if (!readDragComponentsEnabledScope()) {
            return false;
          }
        } else if (nextToken == Keyword.ROUTE_MODE) {
          if (!readRouteModeScope()) {
            return false;
          }
        } else if (nextToken == Keyword.PULL_TIGHT_REGION) {
          if (!readPullTightRegionScope()) {
            return false;
          }
        } else if (nextToken == Keyword.PULL_TIGHT_ACCURACY) {
          if (!readPullTightAccuracyScope()) {
            return false;
          }
        } else if (nextToken == Keyword.IGNORE_CONDUCTION_AREAS) {
          if (!readIgnoreConductionScope()) {
            return false;
          }
        } else if (nextToken == Keyword.AUTOMATIC_LAYER_DIMMING) {
          if (!readAutomaticLayerDimmingScope()) {
            return false;
          }
        } else if (nextToken == Keyword.CLEARANCE_COMPENSATION) {
          if (!readClearanceCompensationScope()) {
            return false;
          }
        } else if (nextToken == Keyword.HIGHLIGHT_ROUTING_OBSTACLE) {
          if (!readHighlightRoutingObstacleScope()) {
            return false;
          }
        } else if (nextToken == Keyword.SELECTABLE_ITEMS) {
          if (!readSelectableItemScope()) {
            return false;
          }

        } else {
          // skip unknown scope
          skipScope(this.scanner);
        }
      }
    }
    return true;
  }

  private void writeParameterScope() throws IOException {
    outFile.startScope();
    outFile.write("parameter");
    writeSelectionLayerScope();
    writeSelectableItemScope();
    writeViaSnapToSmdCenterScope();
    writeRouteModeScope();
    writeShoveEnabledScope();
    writeDragComponentsEnabledScope();
    writeHighlightRoutingObstacleScope();
    writePullTightRegionScope();
    writePullTightAccuracyScope();
    writeClearanceCompensationScope();
    writeIgnoreConductionScope();
    writeAutomaticLayerDimmingScope();

    outFile.endScope();
  }

  private boolean readSelectionLayerScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean selectOnAllLayers;
    if (nextToken == Keyword.ALL_VISIBLE) {
      selectOnAllLayers = true;
    } else if (nextToken == Keyword.CURRENT_ONLY) {
      selectOnAllLayers = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scop: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setSelectOnAllVisibleLayers(selectOnAllLayers);
    return true;
  }

  private boolean readShoveEnabledScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean shoveEnabled;
    if (nextToken == Keyword.ON) {
      shoveEnabled = true;
    } else if (nextToken == Keyword.OFF) {
      shoveEnabled = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_shove_enabled_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_shove_enabled_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setPushEnabled(shoveEnabled);
    return true;
  }

  private boolean readDragComponentsEnabledScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean dragComponentsEnabled;
    if (nextToken == Keyword.ON) {
      dragComponentsEnabled = true;
    } else if (nextToken == Keyword.OFF) {
      dragComponentsEnabled = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_drag_components_enabled_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_drag_components_enabled_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setDragComponentsEnabled(dragComponentsEnabled);
    return true;
  }

  private boolean readIgnoreConductionScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean ignoreConduction;
    if (nextToken == Keyword.ON) {
      ignoreConduction = true;
    } else if (nextToken == Keyword.OFF) {
      ignoreConduction = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_ignore_conduction_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_ignore_conduction_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.setIgnoreConduction(ignoreConduction);
    return true;
  }

  private void writeShoveEnabledScope() throws IOException {
    outFile.startScope();
    outFile.write("shoveEnabled ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getPushEnabled()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private void writeDragComponentsEnabledScope() throws IOException {
    outFile.startScope();
    outFile.write("dragComponentsEnabled ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getDragComponentsEnabled()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private void writeIgnoreConductionScope() throws IOException {
    outFile.startScope();
    outFile.write("ignore_conduction_areas ");
    outFile.newLine();
    if (this.boardHandling.getRoutingBoard().rules.getIgnoreConduction()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private void writeSelectionLayerScope() throws IOException {
    outFile.startScope();
    outFile.write("selection_layers ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      outFile.write("all_visible");
    } else {
      outFile.write("current_only");
    }
    outFile.endScope();
  }

  private boolean readRouteModeScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean isStitchMode;
    if (nextToken == Keyword.STITCHING) {
      isStitchMode = true;
    } else if (nextToken == Keyword.DYNAMIC) {
      isStitchMode = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_route_mode_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setStitchRoute(isStitchMode);
    return true;
  }

  private void writeRouteModeScope() throws IOException {
    outFile.startScope();
    outFile.write("route_mode ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getIsStitchRoute()) {
      outFile.write("stitching");
    } else {
      outFile.write("dynamic");
    }
    outFile.endScope();
  }

  private boolean readPullTightRegionScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof Integer)) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_region_scope: Integer expected");
      return false;
    }
    int pullTightRegion = (Integer) nextToken;
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_region_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setCurrentPullTightRegionWidth(pullTightRegion);
    return true;
  }

  private void writePullTightRegionScope() throws IOException {
    outFile.startScope();
    outFile.write("pullTightRegion ");
    outFile.newLine();
    int pullTightRegion =
        this.boardHandling.getInteractiveSettings().getTracePullTightRegionWidth();
    outFile.write(String.valueOf(pullTightRegion));
    outFile.endScope();
  }

  private boolean readPullTightAccuracyScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    if (!(nextToken instanceof Integer)) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_accuracy_scope: Integer expected");
      return false;
    }
    int pullTightAccuracy = (Integer) nextToken;
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_accuracy_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setTracePullTightAccuracy(pullTightAccuracy);
    return true;
  }

  private void writePullTightAccuracyScope() throws IOException {
    outFile.startScope();
    outFile.write("pullTightAccuracy ");
    outFile.newLine();
    int pullTightAccuracy = this.boardHandling.getInteractiveSettings().getTracePullTightAccuracy();
    outFile.write(String.valueOf(pullTightAccuracy));
    outFile.endScope();
  }

  private boolean readAutomaticLayerDimmingScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    double intensity;
    if (nextToken instanceof Double double1) {
      intensity = double1;
    } else if (nextToken instanceof Integer integer) {
      intensity = integer;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_automatic_layer_dimming_scope: Integer expected");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_automatic_layer_dimming_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.graphicsContext.setAutoLayerDimFactor(intensity);
    return true;
  }

  private void writeAutomaticLayerDimmingScope() throws IOException {
    outFile.startScope();
    outFile.write("automatic_layer_dimming ");
    outFile.newLine();
    float layerDimming = (float) this.boardHandling.graphicsContext.getAutoLayerDimFactor();
    outFile.write(String.valueOf(layerDimming));
    outFile.endScope();
  }

  private boolean readHighlightRoutingObstacleScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean highlightObstacle;
    if (nextToken == Keyword.ON) {
      highlightObstacle = true;
    } else if (nextToken == Keyword.OFF) {
      highlightObstacle = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_highlight_routing_obstacle_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "GUIDefaultsFile.read_highlight_routing_obstacle_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setHighlightRoutingObstacle(highlightObstacle);
    return true;
  }

  private void writeHighlightRoutingObstacleScope() throws IOException {
    outFile.startScope();
    outFile.write("highlightRoutingObstacle ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getHighlightRoutingObstacle()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private boolean readClearanceCompensationScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean clearanceCompensation;
    if (nextToken == Keyword.ON) {
      clearanceCompensation = true;
    } else if (nextToken == Keyword.OFF) {
      clearanceCompensation = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_clearance_compensation_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_clearance_compensation_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.setClearanceCompensation(clearanceCompensation);
    return true;
  }

  private void writeClearanceCompensationScope() throws IOException {
    outFile.startScope();
    outFile.write("clearanceCompensation ");
    outFile.newLine();
    if (this.boardHandling.getRoutingBoard().searchTreeManager.isClearanceCompensationUsed()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private boolean readViaSnapToSmdCenterScope() throws IOException {
    Object nextToken = this.scanner.nextToken();
    boolean snap;
    if (nextToken == Keyword.ON) {
      snap = true;
    } else if (nextToken == Keyword.OFF) {
      snap = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_via_snap_to_smd_center_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.nextToken();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_via_snap_to_smd_center_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().setViaSnapToSmdCenter(snap);
    return true;
  }

  private void writeViaSnapToSmdCenterScope() throws IOException {
    outFile.startScope();
    outFile.write("viaSnapToSmdCenter ");
    outFile.newLine();
    if (this.boardHandling.getInteractiveSettings().getViaSnapToSmdCenter()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.endScope();
  }

  private boolean readSelectableItemScope() throws IOException {
    ItemSelectionFilter itemSelectionFilter =
        this.boardHandling.getInteractiveSettings().getItemSelectionFilter();
    itemSelectionFilter.deselectAll();
    for (; ; ) {
      Object nextToken = this.scanner.nextToken();
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (nextToken == Keyword.TRACES) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.TRACES, true);
      } else if (nextToken == Keyword.VIAS) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.VIAS, true);
      } else if (nextToken == Keyword.PINS) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.PINS, true);
      } else if (nextToken == Keyword.CONDUCTION) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.CONDUCTION, true);
      } else if (nextToken == Keyword.KEEPOUT) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.KEEPOUT, true);
      } else if (nextToken == Keyword.VIA_KEEPOUT) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT, true);
      } else if (nextToken == Keyword.FIXED) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.FIXED, true);
      } else if (nextToken == Keyword.UNFIXED) {
        itemSelectionFilter.setSelected(ItemSelectionFilter.SelectableChoices.UNFIXED, true);
      } else {
        FRLogger.warn("GUIDefaultsFile.read_selectable_item_scope: unexpected token");
        return false;
      }
    }
    return true;
  }

  private void writeSelectableItemScope() throws IOException {
    outFile.startScope();
    outFile.write("selectable_items ");
    outFile.newLine();
    ItemSelectionFilter itemSelectionFilter =
        this.boardHandling.getInteractiveSettings().getItemSelectionFilter();
    ItemSelectionFilter.SelectableChoices[] selectableChoices =
        ItemSelectionFilter.SelectableChoices.values();
    for (int i = 0; i < selectableChoices.length; i++) {
      if (itemSelectionFilter.isSelected(selectableChoices[i])) {
        outFile.write(selectableChoices[i].toString());
        outFile.write(" ");
      }
    }
    outFile.endScope();
  }

  /** Keywords in the gui defaults file. */
  enum Keyword {
    ALL_VISIBLE,
    ASSIGN_NET_RULES,
    AUTOMATIC_LAYER_DIMMING,
    BACKGROUND,
    BOARD_FRAME,
    BOUNDS,
    CLEARANCE_COMPENSATION,
    CLEARANCE_MATRIX,
    CLOSED_BRACKET,
    COLOR_MANAGER,
    COLORS,
    COMPONENT_BACK,
    COMPONENT_FRONT,
    COMPONENT_GRID,
    COMPONENT_INFO,
    CONDUCTION,
    CURRENT_LAYER,
    CURRENT_ONLY,
    DISPLAY_MISCELLANEOUS,
    DISPLAY_REGION,
    DRAG_COMPONENTS_ENABLED,
    DYNAMIC,
    EDIT_VIAS,
    EDIT_NET_RULES,
    FIXED,
    FIXED_TRACES,
    FIXED_VIAS,
    FORTYFIVE_DEGREE,
    GUI_DEFAULTS,
    HIGHLIGHT,
    HIGHLIGHT_ROUTING_OBSTACLE,
    IGNORE_CONDUCTION_AREAS,
    INCOMPLETES,
    INCOMPLETES_INFO,
    INTERACTIVE_STATE,
    KEEPOUT,
    LAYER_VISIBILITY,
    LENGTH_MATCHING,
    MANUAL_RULES,
    MANUAL_RULE_SETTINGS,
    MOVE_PARAMETER,
    NET_INFO,
    NINETY_DEGREE,
    NONE,
    NOT_VISIBLE,
    OBJECT_COLORS,
    OBJECT_VISIBILITY,
    OPEN_BRACKET,
    OFF,
    ON,
    OUTLINE,
    PARAMETER,
    PACKAGE_INFO,
    PADSTACK_INFO,
    PINS,
    PULL_TIGHT_ACCURACY,
    PULL_TIGHT_REGION,
    PUSH_AND_SHOVE_ENABLED,
    ROUTE_DETAILS,
    ROUTE_MODE,
    ROUTE_PARAMETER,
    RULE_SELECTION,
    SELECT_PARAMETER,
    SELECTABLE_ITEMS,
    SELECTION_LAYERS,
    SHOVE_ENABLED,
    STITCHING,
    TRACES,
    UNFIXED,
    VIA_KEEPOUT,
    VISIBLE,
    VIA_RULES,
    VIA_SNAP_TO_SMD_CENTER,
    VIAS,
    VIOLATIONS,
    VIOLATIONS_INFO,
    WINDOWS
  }
}
