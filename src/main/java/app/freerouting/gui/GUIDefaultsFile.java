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
public final class GUIDefaultsFile {

  private final BoardFrame boardFrame;
  private final GuiBoardManager boardHandling;

  /** Used, when reading a defaults file, null otherwise. */
  private final GUIDefaultsScanner scanner;

  /** Used, when writing a defaults file; null otherwise. */
  private final IndentFileWriter outFile;

  private GUIDefaultsFile(
      BoardFrame p_board_frame,
      GuiBoardManager p_board_handling,
      GUIDefaultsScanner p_scanner,
      IndentFileWriter p_output_file) {
    boardFrame = p_board_frame;
    boardHandling = p_board_handling;
    scanner = p_scanner;
    outFile = p_output_file;
  }

  /**
   * Writes the GUI setting of p_board_frame as default to p_file. Returns false, if an error
   * occurred.
   */
  public static boolean write(
      BoardFrame p_board_frame, GuiBoardManager p_board_handling, OutputStream p_output_stream) {
    if (p_output_stream == null) {
      return false;
    }

    IndentFileWriter outputFile = new IndentFileWriter(p_output_stream);

    GUIDefaultsFile result = new GUIDefaultsFile(p_board_frame, p_board_handling, null, outputFile);
    try {
      result.write_defaults_scope();
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
      BoardFrame p_board_frame, GuiBoardManager p_board_handling, InputStream p_input_stream) {
    if (p_input_stream == null) {
      return false;
    }
    GUIDefaultsScanner scanner = new GUIDefaultsScanner(p_input_stream);
    GUIDefaultsFile newInstance =
        new GUIDefaultsFile(p_board_frame, p_board_handling, scanner, null);
    boolean result;
    try {
      result = newInstance.read_defaults_scope();
    } catch (IOException e) {
      FRLogger.error("unable to read defaults file", e);
      result = false;
    }
    return result;
  }

  /** Skips the current scope. Returns false, if no legal scope was found. */
  private static boolean skip_scope(GUIDefaultsScanner p_scanner) {
    int openBrackedCount = 1;
    while (openBrackedCount > 0) {
      Object currToken;
      try {
        currToken = p_scanner.next_token();
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

  private void write_defaults_scope() throws IOException {
    outFile.start_scope();
    outFile.write("gui_defaults");
    write_windows_scope();
    write_colors_scope();
    write_parameter_scope();
    outFile.end_scope();
  }

  private boolean read_defaults_scope() throws IOException {
    Object nextToken = this.scanner.next_token();

    if (nextToken != Keyword.OPEN_BRACKET) {
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.GUI_DEFAULTS) {
      return false;
    }

    // read the direct subscopes of the gui_defaults scope
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
        if (nextToken == Keyword.COLORS) {
          if (!read_colors_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.WINDOWS) {
          if (!read_windows_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.PARAMETER) {
          if (!read_parameter_scope()) {
            return false;
          }
        } else {
          // overread all scopes except the routes scope for the time being
          skip_scope(this.scanner);
        }
      }
    }
    this.boardFrame.refresh_windows();
    return true;
  }

  private boolean read_windows_scope() throws IOException {
    // read the direct subscopes of the windows scope
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
        if (!(nextToken instanceof Keyword)) {
          FRLogger.warn("GUIDefaultsFile.windows: Keyword expected");
          return false;
        }
        if (!read_frame_scope((Keyword) nextToken)) {

          return false;
        }
      }
    }
    return true;
  }

  private void write_windows_scope() throws IOException {
    outFile.start_scope();
    outFile.write("windows");
    write_frame_scope(this.boardFrame, "boardFrame");
    write_frame_scope(this.boardFrame.colorManager, "colorManager");
    write_frame_scope(this.boardFrame.visibilityWindow, "object_visibility");
    write_frame_scope(this.boardFrame.displayMiscWindow, "display_miscellaneous");

    write_frame_scope(this.boardFrame.selectParameterWindow, "select_parameter");
    write_frame_scope(this.boardFrame.routeParameterWindow, "route_parameter");
    write_frame_scope(this.boardFrame.routeParameterWindow.manualRuleWindow, "manual_rules");
    write_frame_scope(this.boardFrame.moveParameterWindow, "move_parameter");
    write_frame_scope(this.boardFrame.clearanceMatrixWindow, "clearanceMatrix");
    write_frame_scope(this.boardFrame.viaWindow, "viaRules");
    write_frame_scope(this.boardFrame.editViasWindow, "edit_vias");
    write_frame_scope(this.boardFrame.editNetRulesWindow, "edit_net_rules");
    write_frame_scope(this.boardFrame.assignNetClassesWindow, "assign_net_rules");
    write_frame_scope(this.boardFrame.padstacksWindow, "padstack_info");
    write_frame_scope(this.boardFrame.packagesWindow, "package_info");
    write_frame_scope(this.boardFrame.componentsWindow, "component_info");
    write_frame_scope(this.boardFrame.netInfoWindow, "net_info");
    write_frame_scope(this.boardFrame.incompletesWindow, "incompletes_info");
    write_frame_scope(this.boardFrame.clearanceViolationsWindow, "violations_info");
    outFile.end_scope();
  }

  private boolean read_frame_scope(Keyword p_frame) throws IOException {
    boolean isVisible;
    Object nextToken = this.scanner.next_token();
    if (nextToken == Keyword.VISIBLE) {
      isVisible = true;
    } else if (nextToken == Keyword.NOT_VISIBLE) {
      isVisible = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: visible or not_visible expected");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.OPEN_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: open_bracket expected");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.BOUNDS) {
      FRLogger.warn("GUIDefaultsFile.read_frame_scope: bounds expected");
      return false;
    }
    Rectangle bounds = read_rectangle();
    if (bounds == null) {
      return false;
    }
    for (int i = 0; i < 2; i++) {
      nextToken = this.scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn("GUIDefaultsFile.read_frame_scope: closing bracket expected");
        return false;
      }
    }
    JFrame currFrame;
    switch (p_frame) {
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
      if (p_frame == Keyword.BOARD_FRAME) {
        currFrame.setBounds(bounds);
      } else {
        // Set only the location.
        // Do not change the size of the frame because it depends on the layer count.
        currFrame.setLocation(bounds.getLocation());
      }
    }
    return true;
  }

  private Rectangle read_rectangle() throws IOException {
    int[] coor = new int[4];
    for (int i = 0; i < 4; i++) {
      Object nextToken = this.scanner.next_token();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn("GUIDefaultsFile.read_rectangle: Integer expected");
        return null;
      }
      coor[i] = (Integer) nextToken;
    }
    return new Rectangle(coor[0], coor[1], coor[2], coor[3]);
  }

  private void write_frame_scope(JFrame p_frame, String p_frame_name) throws IOException {
    if (p_frame == null) {
      return;
    }
    outFile.start_scope();
    outFile.write(p_frame_name);
    outFile.new_line();
    if (p_frame.isVisible()) {
      outFile.write("visible");
    } else {
      outFile.write("not_visible");
    }
    write_bounds(p_frame.getBounds());
    outFile.end_scope();
  }

  private void write_bounds(Rectangle p_bounds) throws IOException {
    outFile.start_scope();
    outFile.write("bounds");
    outFile.new_line();
    int x = (int) p_bounds.getX();
    outFile.write(String.valueOf(x));
    int y = (int) p_bounds.getY();
    outFile.write(" ");
    outFile.write(String.valueOf(y));
    int width = (int) p_bounds.getWidth();
    outFile.write(" ");
    outFile.write(String.valueOf(width));
    int height = (int) p_bounds.getHeight();
    outFile.write(" ");
    outFile.write(String.valueOf(height));
    outFile.end_scope();
  }

  private boolean read_colors_scope() throws IOException {
    // read the direct subscopes of the colors scope
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

        if (nextToken == Keyword.BACKGROUND) {
          if (!read_background_color()) {
            return false;
          }
        } else if (nextToken == Keyword.CONDUCTION) {
          if (!read_conduction_colors()) {
            return false;
          }
        } else if (nextToken == Keyword.HILIGHT) {
          if (!read_hilight_color()) {
            return false;
          }
        } else if (nextToken == Keyword.INCOMPLETES) {
          if (!read_incompletes_color()) {
            return false;
          }
        } else if (nextToken == Keyword.KEEPOUT) {
          if (!read_keepout_colors()) {
            return false;
          }
        } else if (nextToken == Keyword.OUTLINE) {
          if (!read_outline_color()) {
            return false;
          }
        } else if (nextToken == Keyword.COMPONENT_FRONT) {
          if (!read_component_color(true)) {
            return false;
          }
        } else if (nextToken == Keyword.COMPONENT_BACK) {
          if (!read_component_color(false)) {
            return false;
          }
        } else if (nextToken == Keyword.LENGTH_MATCHING) {
          if (!read_length_matching_color()) {
            return false;
          }
        } else if (nextToken == Keyword.PINS) {
          if (!read_pin_colors()) {
            return false;
          }
        } else if (nextToken == Keyword.TRACES) {
          if (!read_trace_colors(false)) {
            return false;
          }
        } else if (nextToken == Keyword.FIXED_TRACES) {
          if (!read_trace_colors(true)) {
            return false;
          }
        } else if (nextToken == Keyword.VIA_KEEPOUT) {
          if (!read_via_keepout_colors()) {
            return false;
          }
        } else if (nextToken == Keyword.VIAS) {
          if (!read_via_colors(false)) {
            return false;
          }
        } else if (nextToken == Keyword.FIXED_VIAS) {
          if (!read_via_colors(true)) {
            return false;
          }
        } else if (nextToken == Keyword.VIOLATIONS) {
          if (!read_violations_color()) {
            return false;
          }
        } else {
          // skip unknown scope
          skip_scope(this.scanner);
        }
      }
    }
    return true;
  }

  private boolean read_trace_colors(boolean p_fixed) throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_trace_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_trace_colors(currColors, p_fixed);
    return true;
  }

  private boolean read_via_colors(boolean p_fixed) throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_via_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_via_colors(currColors, p_fixed);
    return true;
  }

  private boolean read_pin_colors() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_pin_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_pin_colors(currColors);
    return true;
  }

  private boolean read_conduction_colors() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_conduction_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_conduction_colors(currColors);
    return true;
  }

  private boolean read_keepout_colors() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_obstacle_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_keepout_colors(currColors);
    return true;
  }

  private boolean read_via_keepout_colors() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_via_obstacle_color_intensity(intensity);
    Color[] currColors = read_color_array();
    if (currColors.length < 1) {
      return false;
    }
    this.boardHandling.graphicsContext.itemColorTable.set_via_keepout_colors(currColors);
    return true;
  }

  private boolean read_background_color() throws IOException {
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_background_color(currColor);
    this.boardFrame.set_board_background(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_background_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_hilight_color() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_hilight_color_intensity(intensity);
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_hilight_color(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_higlight_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_incompletes_color() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_incomplete_color_intensity(intensity);
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_incomplete_color(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_incompletes_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_length_matching_color() throws IOException {
    double intensity = read_color_intensity();
    if (intensity < 0) {
      return false;
    }
    this.boardHandling.graphicsContext.set_length_matching_area_color_intensity(intensity);
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_length_matching_area_color(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_length_matching_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_violations_color() throws IOException {
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_violations_color(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_violations_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_outline_color() throws IOException {
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_outline_color(currColor);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_outline_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private boolean read_component_color(boolean p_front) throws IOException {
    Color currColor = read_color();
    if (currColor == null) {
      return false;
    }
    this.boardHandling.graphicsContext.otherColorTable.set_component_color(currColor, p_front);
    Object nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_component_color: closing bracket expected");
      return false;
    }
    return true;
  }

  private double read_color_intensity() throws IOException {
    double result;
    Object nextToken = this.scanner.next_token();
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

  /** reads a java.awt.Color from the defaults file. Returns null, if no valid color was found. */
  private Color read_color() throws IOException {
    int[] rgbColorArr = new int[3];
    for (int i = 0; i < 3; i++) {
      Object nextToken = this.scanner.next_token();
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
   * reads an array java.awt.Color from the defaults file. Returns null, if no valid colors were
   * found.
   */
  private Color[] read_color_array() throws IOException {
    Collection<Color> colorList = new LinkedList<>();
    for (; ; ) {
      Color currColor = read_color();
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

  private void write_colors_scope() throws IOException {
    GraphicsContext graphicsContext = this.boardHandling.graphicsContext;
    outFile.start_scope();
    outFile.write("colors");
    outFile.start_scope();
    outFile.write("background");
    write_color_scope(graphicsContext.get_background_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("hilight");
    write_color_intensity(graphicsContext.get_hilight_color_intensity());
    write_color_scope(graphicsContext.get_hilight_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("incompletes");
    write_color_intensity(graphicsContext.get_incomplete_color_intensity());
    write_color_scope(graphicsContext.get_incomplete_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("outline");
    write_color_scope(graphicsContext.get_outline_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("component_front");
    write_color_scope(graphicsContext.get_component_color(true));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("component_back");
    write_color_scope(graphicsContext.get_component_color(false));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("violations");
    write_color_scope(graphicsContext.get_violations_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("length_matching");
    write_color_intensity(graphicsContext.get_length_matching_area_color_intensity());
    write_color_scope(graphicsContext.get_length_matching_area_color());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("traces");
    write_color_intensity(graphicsContext.get_trace_color_intensity());
    write_color(graphicsContext.get_trace_colors(false));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("fixed_traces");
    write_color_intensity(graphicsContext.get_trace_color_intensity());
    write_color(graphicsContext.get_trace_colors(true));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("vias");
    write_color_intensity(graphicsContext.get_via_color_intensity());
    write_color(graphicsContext.get_via_colors(false));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("fixed_vias");
    write_color_intensity(graphicsContext.get_via_color_intensity());
    write_color(graphicsContext.get_via_colors(true));
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("pins");
    write_color_intensity(graphicsContext.get_pin_color_intensity());
    write_color(graphicsContext.get_pin_colors());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("conduction");
    write_color_intensity(graphicsContext.get_conduction_color_intensity());
    write_color(graphicsContext.get_conduction_colors());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("keepout");
    write_color_intensity(graphicsContext.get_obstacle_color_intensity());
    write_color(graphicsContext.get_obstacle_colors());
    outFile.end_scope();
    outFile.start_scope();
    outFile.write("via_keepout");
    write_color_intensity(graphicsContext.get_via_obstacle_color_intensity());
    write_color(graphicsContext.get_via_obstacle_colors());
    outFile.end_scope();
    outFile.end_scope();
  }

  private void write_color_intensity(double p_value) throws IOException {
    outFile.write(" ");
    float value = (float) p_value;
    outFile.write(String.valueOf(value));
  }

  private void write_color_scope(Color p_color) throws IOException {
    outFile.new_line();
    int red = p_color.getRed();
    outFile.write(String.valueOf(red));
    outFile.write(" ");
    int green = p_color.getGreen();
    outFile.write(String.valueOf(green));
    outFile.write(" ");
    int blue = p_color.getBlue();
    outFile.write(String.valueOf(blue));
  }

  private void write_color(Color[] p_colors) throws IOException {
    for (int i = 0; i < p_colors.length; i++) {
      write_color_scope(p_colors[i]);
    }
  }

  private boolean read_parameter_scope() throws IOException {
    // read the subscopes of the parameter scope
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

        if (nextToken == Keyword.SELECTION_LAYERS) {
          if (!read_selection_layer_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.VIA_SNAP_TO_SMD_CENTER) {
          if (!read_via_snap_to_smd_center_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.SHOVE_ENABLED) {
          if (!read_shove_enabled_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.DRAG_COMPONENTS_ENABLED) {
          if (!read_drag_components_enabled_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.ROUTE_MODE) {
          if (!read_route_mode_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.PULL_TIGHT_REGION) {
          if (!read_pull_tight_region_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.PULL_TIGHT_ACCURACY) {
          if (!read_pull_tight_accuracy_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.IGNORE_CONDUCTION_AREAS) {
          if (!read_ignore_conduction_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.AUTOMATIC_LAYER_DIMMING) {
          if (!read_automatic_layer_dimming_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.CLEARANCE_COMPENSATION) {
          if (!read_clearance_compensation_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.HILIGHT_ROUTING_OBSTACLE) {
          if (!read_hilight_routing_obstacle_scope()) {
            return false;
          }
        } else if (nextToken == Keyword.SELECTABLE_ITEMS) {
          if (!read_selectable_item_scope()) {
            return false;
          }

        } else {
          // skip unknown scope
          skip_scope(this.scanner);
        }
      }
    }
    return true;
  }

  private void write_parameter_scope() throws IOException {
    outFile.start_scope();
    outFile.write("parameter");
    write_selection_layer_scope();
    write_selectable_item_scope();
    write_via_snap_to_smd_center_scope();
    write_route_mode_scope();
    write_shove_enabled_scope();
    write_drag_components_enabled_scope();
    write_hilight_routing_obstacle_scope();
    write_pull_tight_region_scope();
    write_pull_tight_accuracy_scope();
    write_clearance_compensation_scope();
    write_ignore_conduction_scope();
    write_automatic_layer_dimming_scope();

    outFile.end_scope();
  }

  private boolean read_selection_layer_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean selectOnAllLayers;
    if (nextToken == Keyword.ALL_VISIBLE) {
      selectOnAllLayers = true;
    } else if (nextToken == Keyword.CURRENT_ONLY) {
      selectOnAllLayers = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scop: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_select_on_all_visible_layers(selectOnAllLayers);
    return true;
  }

  private boolean read_shove_enabled_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean shoveEnabled;
    if (nextToken == Keyword.ON) {
      shoveEnabled = true;
    } else if (nextToken == Keyword.OFF) {
      shoveEnabled = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_shove_enabled_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_shove_enabled_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_push_enabled(shoveEnabled);
    return true;
  }

  private boolean read_drag_components_enabled_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean dragComponentsEnabled;
    if (nextToken == Keyword.ON) {
      dragComponentsEnabled = true;
    } else if (nextToken == Keyword.OFF) {
      dragComponentsEnabled = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_drag_components_enabled_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_drag_components_enabled_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_drag_components_enabled(dragComponentsEnabled);
    return true;
  }

  private boolean read_ignore_conduction_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean ignoreConduction;
    if (nextToken == Keyword.ON) {
      ignoreConduction = true;
    } else if (nextToken == Keyword.OFF) {
      ignoreConduction = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_ignore_conduction_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_ignore_conduction_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.set_ignore_conduction(ignoreConduction);
    return true;
  }

  private void write_shove_enabled_scope() throws IOException {
    outFile.start_scope();
    outFile.write("shoveEnabled ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_push_enabled()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private void write_drag_components_enabled_scope() throws IOException {
    outFile.start_scope();
    outFile.write("dragComponentsEnabled ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_drag_components_enabled()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private void write_ignore_conduction_scope() throws IOException {
    outFile.start_scope();
    outFile.write("ignore_conduction_areas ");
    outFile.new_line();
    if (this.boardHandling.get_routing_board().rules.get_ignore_conduction()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private void write_selection_layer_scope() throws IOException {
    outFile.start_scope();
    outFile.write("selection_layers ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_select_on_all_visible_layers()) {
      outFile.write("all_visible");
    } else {
      outFile.write("current_only");
    }
    outFile.end_scope();
  }

  private boolean read_route_mode_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean isStitchMode;
    if (nextToken == Keyword.STITCHING) {
      isStitchMode = true;
    } else if (nextToken == Keyword.DYNAMIC) {
      isStitchMode = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_route_mode_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_selection_layer_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_stitch_route(isStitchMode);
    return true;
  }

  private void write_route_mode_scope() throws IOException {
    outFile.start_scope();
    outFile.write("route_mode ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_is_stitch_route()) {
      outFile.write("stitching");
    } else {
      outFile.write("dynamic");
    }
    outFile.end_scope();
  }

  private boolean read_pull_tight_region_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    if (!(nextToken instanceof Integer)) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_region_scope: Integer expected");
      return false;
    }
    int pullTightRegion = (Integer) nextToken;
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_region_scope: closing bracket expected");
      return false;
    }
    this.boardHandling
        .getInteractiveSettings()
        .set_current_pull_tight_region_width(pullTightRegion);
    return true;
  }

  private void write_pull_tight_region_scope() throws IOException {
    outFile.start_scope();
    outFile.write("pullTightRegion ");
    outFile.new_line();
    int pullTightRegion =
        this.boardHandling.getInteractiveSettings().get_trace_pull_tight_region_width();
    outFile.write(String.valueOf(pullTightRegion));
    outFile.end_scope();
  }

  private boolean read_pull_tight_accuracy_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    if (!(nextToken instanceof Integer)) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_accuracy_scope: Integer expected");
      return false;
    }
    int pullTightAccuracy = (Integer) nextToken;
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_pull_tight_accuracy_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_trace_pull_tight_accuracy(pullTightAccuracy);
    return true;
  }

  private void write_pull_tight_accuracy_scope() throws IOException {
    outFile.start_scope();
    outFile.write("pullTightAccuracy ");
    outFile.new_line();
    int pullTightAccuracy =
        this.boardHandling.getInteractiveSettings().get_trace_pull_tight_accuracy();
    outFile.write(String.valueOf(pullTightAccuracy));
    outFile.end_scope();
  }

  private boolean read_automatic_layer_dimming_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    double intensity;
    if (nextToken instanceof Double double1) {
      intensity = double1;
    } else if (nextToken instanceof Integer integer) {
      intensity = integer;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_automatic_layer_dimming_scope: Integer expected");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_automatic_layer_dimming_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.graphicsContext.set_auto_layer_dim_factor(intensity);
    return true;
  }

  private void write_automatic_layer_dimming_scope() throws IOException {
    outFile.start_scope();
    outFile.write("automatic_layer_dimming ");
    outFile.new_line();
    float layerDimming = (float) this.boardHandling.graphicsContext.get_auto_layer_dim_factor();
    outFile.write(String.valueOf(layerDimming));
    outFile.end_scope();
  }

  private boolean read_hilight_routing_obstacle_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean hilightObstacle;
    if (nextToken == Keyword.ON) {
      hilightObstacle = true;
    } else if (nextToken == Keyword.OFF) {
      hilightObstacle = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_hilight_routing_obstacle_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn(
          "GUIDefaultsFile.read_hilight_routing_obstacle_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_hilight_routing_obstacle(hilightObstacle);
    return true;
  }

  private void write_hilight_routing_obstacle_scope() throws IOException {
    outFile.start_scope();
    outFile.write("hilightRoutingObstacle ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_hilight_routing_obstacle()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private boolean read_clearance_compensation_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean clearanceCompensation;
    if (nextToken == Keyword.ON) {
      clearanceCompensation = true;
    } else if (nextToken == Keyword.OFF) {
      clearanceCompensation = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_clearance_compensation_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_clearance_compensation_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.set_clearance_compensation(clearanceCompensation);
    return true;
  }

  private void write_clearance_compensation_scope() throws IOException {
    outFile.start_scope();
    outFile.write("clearanceCompensation ");
    outFile.new_line();
    if (this.boardHandling.get_routing_board().searchTreeManager.is_clearance_compensation_used()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private boolean read_via_snap_to_smd_center_scope() throws IOException {
    Object nextToken = this.scanner.next_token();
    boolean snap;
    if (nextToken == Keyword.ON) {
      snap = true;
    } else if (nextToken == Keyword.OFF) {
      snap = false;
    } else {
      FRLogger.warn("GUIDefaultsFile.read_via_snap_to_smd_center_scope: unexpected token");
      return false;
    }
    nextToken = this.scanner.next_token();
    if (nextToken != Keyword.CLOSED_BRACKET) {
      FRLogger.warn("GUIDefaultsFile.read_via_snap_to_smd_center_scope: closing bracket expected");
      return false;
    }
    this.boardHandling.getInteractiveSettings().set_via_snap_to_smd_center(snap);
    return true;
  }

  private void write_via_snap_to_smd_center_scope() throws IOException {
    outFile.start_scope();
    outFile.write("viaSnapToSmdCenter ");
    outFile.new_line();
    if (this.boardHandling.getInteractiveSettings().get_via_snap_to_smd_center()) {
      outFile.write("on");
    } else {
      outFile.write("off");
    }
    outFile.end_scope();
  }

  private boolean read_selectable_item_scope() throws IOException {
    ItemSelectionFilter itemSelectionFilter =
        this.boardHandling.getInteractiveSettings().get_item_selection_filter();
    itemSelectionFilter.deselect_all();
    for (; ; ) {
      Object nextToken = this.scanner.next_token();
      if (nextToken == Keyword.CLOSED_BRACKET) {
        break;
      }
      if (nextToken == Keyword.TRACES) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.TRACES, true);
      } else if (nextToken == Keyword.VIAS) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.VIAS, true);
      } else if (nextToken == Keyword.PINS) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.PINS, true);
      } else if (nextToken == Keyword.CONDUCTION) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.CONDUCTION, true);
      } else if (nextToken == Keyword.KEEPOUT) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.KEEPOUT, true);
      } else if (nextToken == Keyword.VIA_KEEPOUT) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT, true);
      } else if (nextToken == Keyword.FIXED) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.FIXED, true);
      } else if (nextToken == Keyword.UNFIXED) {
        itemSelectionFilter.set_selected(ItemSelectionFilter.SelectableChoices.UNFIXED, true);
      } else {
        FRLogger.warn("GUIDefaultsFile.read_selectable_item_scope: unexpected token");
        return false;
      }
    }
    return true;
  }

  private void write_selectable_item_scope() throws IOException {
    outFile.start_scope();
    outFile.write("selectable_items ");
    outFile.new_line();
    ItemSelectionFilter itemSelectionFilter =
        this.boardHandling.getInteractiveSettings().get_item_selection_filter();
    ItemSelectionFilter.SelectableChoices[] selectableChoices =
        ItemSelectionFilter.SelectableChoices.values();
    for (int i = 0; i < selectableChoices.length; i++) {
      if (itemSelectionFilter.is_selected(selectableChoices[i])) {
        outFile.write(selectableChoices[i].toString());
        outFile.write(" ");
      }
    }
    outFile.end_scope();
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
    HILIGHT,
    HILIGHT_ROUTING_OBSTACLE,
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
