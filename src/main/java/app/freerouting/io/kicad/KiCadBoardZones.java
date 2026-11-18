package app.freerouting.io.kicad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The fill settings of the copper zones of a KiCad board, read from its {@code .kicad_pcb} file.
 *
 * <p>KiCad writes a zone into a Specctra design as a plane: its outline and its net. The clearance
 * of the zone and its minimum width are lost on the way, so the plane gets the clearance of its net
 * class. When KiCad refills the zone after the routing, it keeps the copper further away from the
 * traces than Freerouting assumed and removes every neck narrower than the minimum width: a plane
 * that Freerouting saw in one piece can come back in several. This class reads the two values back.
 *
 * <p>The way a pad is connected to a zone is lost as well: solidly, through the spokes of a thermal
 * relief, or not at all. It is set on the zone, and can be overridden on a footprint or on a pad.
 * The pads are read with the settings that decide it.
 *
 * <p>Only the zones and the pads are read; the fill polygons, which can make up most of the file,
 * are skipped without being stored.
 */
public final class KiCadBoardZones {

  /** How a pad is connected to the fill of a zone ({@code zone_connect} of KiCad). */
  public enum PadConnection {
    /** Not set here: the footprint, then the zone, decides. */
    INHERITED,
    /** The fill keeps its clearance to the pad. */
    NONE,
    /** The fill stops at the thermal gap; up to four spokes cross it. */
    THERMAL,
    /** The fill covers the pad. */
    SOLID,
    /** A thermal relief for a through-hole pad, a solid connection for any other pad. */
    THT_THERMAL;

    static PadConnection fromKiCad(String value) {
      return switch (value) {
        case "0" -> NONE;
        case "1" -> THERMAL;
        case "2" -> SOLID;
        case "3" -> THT_THERMAL;
        default -> INHERITED;
      };
    }
  }

  /**
   * One pad of a footprint of the board, with what decides its connection to a zone. Positions are
   * absolute, in the frame of KiCad (the y axis points down); angles are in degrees,
   * counter-clockwise on screen.
   *
   * @param xUm the position of the pad (its hole, if it has one), in micrometres
   * @param yUm the position of the pad
   * @param shapeXUm the centre of its copper shape, which a drill offset moves away from the hole
   * @param shapeYUm the centre of its copper shape
   * @param angleDeg its orientation on the board
   * @param sizeXUm the width of its shape before rotation
   * @param sizeYUm the height of its shape before rotation
   * @param circular whether its shape is a circle
   * @param throughHole whether it is a plated through-hole pad
   * @param layers its layers as written, the wildcards {@code *.Cu} and {@code F&B.Cu} replaced by
   *     the copper layers of the layer table of the board, which can rename them
   * @param connection {@code zone_connect} of the pad, else of its footprint, else INHERITED
   * @param thermalGapUm {@code thermal_gap} of the pad, 0 if it sets none
   * @param thermalBridgeWidthUm {@code thermal_bridge_width} of the pad, 0 if it sets none
   * @param spokeAngleDeg the angle of its first thermal spoke in the frame of the pad ({@code
   *     thermal_bridge_angle}, or the default of KiCad for its shape)
   */
  public record Pad(
      double xUm,
      double yUm,
      double shapeXUm,
      double shapeYUm,
      double angleDeg,
      double sizeXUm,
      double sizeYUm,
      boolean circular,
      boolean throughHole,
      List<String> layers,
      PadConnection connection,
      double thermalGapUm,
      double thermalBridgeWidthUm,
      double spokeAngleDeg) {

    /** Whether the pad has copper on the layer of this name. */
    public boolean isOnLayer(String layerName) {
      return matchesLayer(layers, layerName);
    }
  }

  /**
   * One copper zone of the board.
   *
   * @param netName the name of its net, or an empty string if it has none
   * @param layers its layers as written ({@code F.Cu}, or a KiCad wildcard such as {@code *.Cu} or
   *     {@code F&B.Cu})
   * @param clearanceUm the clearance of the zone in micrometres ({@code connect_pads (clearance
   *     ...)}), 0 if it sets none
   * @param minThicknessUm its minimum width in micrometres ({@code min_thickness}), 0 if it sets
   *     none
   * @param minXUm the left side of the bounding box of its outline, in micrometres
   * @param minYUm the top side of the bounding box of its outline (the KiCad y axis points down)
   * @param maxXUm the right side of the bounding box of its outline
   * @param maxYUm the bottom side of the bounding box of its outline
   * @param padConnection how its fill is connected to the pads of its net ({@code connect_pads}):
   *     THERMAL, SOLID, NONE or THT_THERMAL
   * @param thermalGapUm the gap of its thermal reliefs in micrometres ({@code thermal_gap})
   * @param thermalBridgeWidthUm the width of the spokes of its thermal reliefs ({@code
   *     thermal_bridge_width})
   */
  public record Zone(
      String netName,
      List<String> layers,
      double clearanceUm,
      double minThicknessUm,
      double minXUm,
      double minYUm,
      double maxXUm,
      double maxYUm,
      PadConnection padConnection,
      double thermalGapUm,
      double thermalBridgeWidthUm) {

    /** Whether the zone is on the copper layer of this name. */
    public boolean isOnLayer(String layerName) {
      return matchesLayer(layers, layerName);
    }
  }

  private static boolean matchesLayer(List<String> layers, String layerName) {
    for (String layer : layers) {
      if (layer.equals(layerName)
          || ("*.Cu".equals(layer) && layerName.endsWith(".Cu"))
          || ("F&B.Cu".equals(layer) && ("F.Cu".equals(layerName) || "B.Cu".equals(layerName)))) {
        return true;
      }
    }
    return false;
  }

  /**
   * The distance in micrometres that the fill of a zone keeps to the edge of the board when the
   * project does not set another one: the default of KiCad for its copper to edge clearance. The
   * value is kept in the project file, not in the board file.
   */
  public static final double DEFAULT_EDGE_CLEARANCE_UM = 500.0;

  private final String sourceName;
  private final List<Zone> zones;
  private final List<Pad> pads;

  private KiCadBoardZones(String sourceName, List<Zone> zones, List<Pad> pads) {
    this.sourceName = sourceName;
    this.zones = List.copyOf(zones);
    this.pads = List.copyOf(pads);
  }

  /** The pads of the footprints of the board, in file order. */
  public List<Pad> pads() {
    return pads;
  }

  /**
   * The distance in micrometres that the fill of the zones keeps to the edge of the board, whatever
   * the clearance of a zone: {@link #DEFAULT_EDGE_CLEARANCE_UM}.
   */
  public double edgeClearanceUm() {
    return DEFAULT_EDGE_CLEARANCE_UM;
  }

  /** The file name, for messages. */
  public String sourceName() {
    return sourceName;
  }

  /** The copper zones of the board, in file order. Rule areas (keepouts) are left out. */
  public List<Zone> zones() {
    return zones;
  }

  /** Reads a {@code .kicad_pcb} file. */
  public static KiCadBoardZones read(Path path) throws IOException {
    return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
  }

  /**
   * Parses the text of a {@code .kicad_pcb} file.
   *
   * @throws IllegalArgumentException if the text is not a KiCad board
   */
  public static KiCadBoardZones parse(String text, String sourceName) {
    Reader reader = new Reader(text);
    reader.skipBlanks();
    if (!reader.enterList() || !"kicad_pcb".equals(reader.readAtom())) {
      throw new IllegalArgumentException("not a KiCad board file: no (kicad_pcb ...)");
    }
    List<Zone> zones = new ArrayList<>();
    List<Pad> pads = new ArrayList<>();
    List<String> copperLayers = new ArrayList<>();
    int version = 0;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: missing ')'");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      switch (reader.readAtom()) {
        case "zone" -> {
          Zone zone = readZone(reader);
          if (zone != null) {
            zones.add(zone);
          }
        }
        case "footprint", "module" -> readFootprint(reader, version, copperLayers, pads);
        case "layers" -> readCopperLayers(reader, copperLayers);
        case "version" -> {
          version = (int) toNumber(reader.readAtom());
          reader.skipToEndOfList();
        }
        default -> reader.skipToEndOfList();
      }
    }
    return new KiCadBoardZones(sourceName, zones, pads);
  }

  /**
   * Reads the rest of the layer table of the board, {@code (layers (0 F.Cu signal) (31 B.Cu signal)
   * (32 B.Adhes user) ...)}, for the names of its copper layers, from front to back. A board can
   * rename them: {@code (0 Top signal)} up to KiCad 5, {@code (0 "F.Cu" signal "Top")} since.
   */
  private static void readCopperLayers(Reader reader, List<String> copperLayers) {
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      reader.readAtom(); // its number
      String name = reader.readAtom();
      String type = reader.readAtom();
      if (!name.isEmpty()
          && ("signal".equals(type)
              || "power".equals(type)
              || "mixed".equals(type)
              || "jumper".equals(type))) {
        copperLayers.add(name);
      }
      reader.skipToEndOfList();
    }
  }

  /**
   * The layers of a pad with the wildcards of KiCad replaced by the copper layers of the board they
   * stand for: {@code *.Cu} by all of them, {@code F&B.Cu} by the first and the last. A board that
   * renames its copper layers still writes the wildcards. Without a layer table the layers are
   * returned as written.
   */
  private static List<String> resolveLayers(List<String> layers, List<String> copperLayers) {
    if (copperLayers.isEmpty()) {
      return List.copyOf(layers);
    }
    List<String> result = new ArrayList<>();
    for (String layer : layers) {
      if ("*.Cu".equals(layer)) {
        result.addAll(copperLayers);
      } else if ("F&B.Cu".equals(layer)) {
        result.add(copperLayers.get(0));
        result.add(copperLayers.get(copperLayers.size() - 1));
      } else {
        result.add(layer);
      }
    }
    return List.copyOf(result);
  }

  /** Reads the rest of a {@code (footprint ...)} and adds its pads. */
  private static void readFootprint(
      Reader reader, int version, List<String> copperLayers, List<Pad> pads) {
    double[] at = new double[3];
    PadConnection connection = PadConnection.INHERITED;
    List<PadDraft> drafts = new ArrayList<>();
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: unfinished footprint");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      switch (reader.readAtom()) {
        case "at" -> {
          readNumbers(reader, at);
          at[0] *= 1000.0;
          at[1] *= 1000.0;
          reader.skipToEndOfList();
        }
        case "zone_connect" -> {
          connection = PadConnection.fromKiCad(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "pad" -> drafts.add(readPad(reader, version));
        default -> reader.skipToEndOfList();
      }
    }
    // the position of the footprint can follow its pads in the file
    double cos = Math.cos(Math.toRadians(at[2]));
    double sin = Math.sin(Math.toRadians(at[2]));
    for (PadDraft draft : drafts) {
      // RotatePoint of KiCad: the y axis points down, a positive angle turns counter-clockwise
      double x = at[0] + draft.at[0] * cos + draft.at[1] * sin;
      double y = at[1] - draft.at[0] * sin + draft.at[1] * cos;
      double padCos = Math.cos(Math.toRadians(draft.at[2]));
      double padSin = Math.sin(Math.toRadians(draft.at[2]));
      double shapeX = x + draft.offset[0] * padCos + draft.offset[1] * padSin;
      double shapeY = y - draft.offset[0] * padSin + draft.offset[1] * padCos;
      pads.add(
          new Pad(
              x,
              y,
              shapeX,
              shapeY,
              draft.at[2],
              draft.size[0],
              draft.size[1],
              draft.circular,
              draft.throughHole,
              resolveLayers(draft.layers, copperLayers),
              draft.connection != PadConnection.INHERITED ? draft.connection : connection,
              draft.thermalGap,
              draft.thermalBridgeWidth,
              draft.spokeAngle));
    }
  }

  /** A pad as written in its footprint: positions relative to it, in micrometres. */
  private static final class PadDraft {
    final double[] at = new double[3];
    final double[] size = new double[2];
    final double[] offset = new double[2];
    final List<String> layers = new ArrayList<>();
    boolean circular;
    boolean throughHole;
    PadConnection connection = PadConnection.INHERITED;
    double thermalGap;
    double thermalBridgeWidth;
    double spokeAngle;
  }

  /** Reads the rest of {@code (pad "1" thru_hole circle (at ...) (size ...) ...)}. */
  private static PadDraft readPad(Reader reader, int version) {
    PadDraft pad = new PadDraft();
    reader.readAtom(); // its number, which can be empty
    pad.throughHole = "thru_hole".equals(reader.readAtom());
    String shape = reader.readAtom();
    boolean circleAnchor = false;
    Double spokeAngle = null;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: unfinished pad");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      switch (reader.readAtom()) {
        case "at" -> {
          readNumbers(reader, pad.at);
          pad.at[0] *= 1000.0;
          pad.at[1] *= 1000.0;
          reader.skipToEndOfList();
        }
        case "size" -> {
          readNumbers(reader, pad.size);
          pad.size[0] *= 1000.0;
          pad.size[1] *= 1000.0;
          reader.skipToEndOfList();
        }
        case "drill" -> readDrillOffset(reader, pad.offset);
        case "layers" -> {
          for (String layer = reader.readAtom(); !layer.isEmpty(); layer = reader.readAtom()) {
            pad.layers.add(layer);
          }
          reader.skipToEndOfList();
        }
        case "zone_connect" -> {
          pad.connection = PadConnection.fromKiCad(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "thermal_gap" -> {
          pad.thermalGap = toUm(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "thermal_bridge_width", "thermal_width" -> {
          pad.thermalBridgeWidth = toUm(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "thermal_bridge_angle" -> {
          spokeAngle = toNumber(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "options" -> circleAnchor = readCircleAnchor(reader);
        default -> reader.skipToEndOfList();
      }
    }
    pad.circular = "circle".equals(shape) || ("oval".equals(shape) && pad.size[0] == pad.size[1]);
    if (spokeAngle != null) {
      pad.spokeAngle = spokeAngle;
    } else if ("circle".equals(shape)
        || ("custom".equals(shape) && circleAnchor && version > 20211014)) {
      // the defaults of the board reader of KiCad
      pad.spokeAngle = 45;
    } else {
      pad.spokeAngle = 90;
    }
    return pad;
  }

  /** Reads the numbers that follow, at most as many as the array holds. */
  private static void readNumbers(Reader reader, double[] numbers) {
    for (int i = 0; i < numbers.length; i++) {
      String value = reader.readAtom();
      if (value.isEmpty() || !isNumber(value)) {
        return;
      }
      numbers[i] = Double.parseDouble(value);
    }
  }

  /** Reads the rest of {@code (drill [oval] 0.8 [1.2] (offset 0.1 0))} for the offset, in um. */
  private static void readDrillOffset(Reader reader, double[] offset) {
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      if ("offset".equals(reader.readAtom())) {
        readNumbers(reader, offset);
        offset[0] *= 1000.0;
        offset[1] *= 1000.0;
      }
      reader.skipToEndOfList();
    }
  }

  /** Reads the rest of {@code (options (clearance outline) (anchor circle))}. */
  private static boolean readCircleAnchor(Reader reader) {
    boolean circle = false;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return circle;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      if ("anchor".equals(reader.readAtom())) {
        circle = "circle".equals(reader.readAtom());
      }
      reader.skipToEndOfList();
    }
  }

  /** Reads the rest of a {@code (zone ...)}; returns null for a rule area. */
  private static Zone readZone(Reader reader) {
    String netName = "";
    List<String> layers = new ArrayList<>();
    double clearance = 0;
    double minThickness = 0;
    boolean ruleArea = false;
    PadConnection[] padConnection = {PadConnection.THERMAL};
    double[] thermal = new double[2];
    double[] box = {
      Double.POSITIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY,
      Double.NEGATIVE_INFINITY
    };
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: unfinished zone");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      String keyword = reader.readAtom();
      switch (keyword) {
        case "net_name" -> {
          netName = reader.readAtom();
          reader.skipToEndOfList();
        }
        case "net" -> {
          // KiCad 10 writes the name here; older versions a number, with the name in net_name
          String value = reader.readAtom();
          if (netName.isEmpty() && !isNumber(value)) {
            netName = value;
          }
          reader.skipToEndOfList();
        }
        case "layer", "layers" -> {
          for (String layer = reader.readAtom(); !layer.isEmpty(); layer = reader.readAtom()) {
            layers.add(layer);
          }
          reader.skipToEndOfList();
        }
        case "keepout" -> {
          ruleArea = true;
          reader.skipToEndOfList();
        }
        case "min_thickness" -> {
          minThickness = toUm(reader.readAtom());
          reader.skipToEndOfList();
        }
        case "connect_pads" -> clearance = readConnectPads(reader, padConnection);
        case "fill" -> readFill(reader, thermal);
        case "polygon" -> readOutline(reader, box);
        default -> reader.skipToEndOfList();
      }
    }
    if (ruleArea || box[0] > box[2]) {
      return null;
    }
    return new Zone(
        netName,
        layers,
        clearance,
        minThickness,
        box[0],
        box[1],
        box[2],
        box[3],
        padConnection[0],
        thermal[0],
        thermal[1]);
  }

  /** Reads the rest of {@code (fill [yes] (thermal_gap 0.5) (thermal_bridge_width 0.5) ...)}. */
  private static void readFill(Reader reader, double[] thermal) {
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      switch (reader.readAtom()) {
        case "thermal_gap" -> thermal[0] = toUm(reader.readAtom());
        case "thermal_bridge_width" -> thermal[1] = toUm(reader.readAtom());
        default -> {
          // nothing else is needed
        }
      }
      reader.skipToEndOfList();
    }
  }

  /**
   * Reads the rest of {@code (connect_pads [yes|no|thru_hole_only] (clearance 0.5))}: returns the
   * clearance and sets the connection, a thermal relief when no keyword is written.
   */
  private static double readConnectPads(Reader reader, PadConnection[] padConnection) {
    double clearance = 0;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return clearance;
      }
      if (!reader.enterList()) {
        switch (reader.readAtom()) {
          case "yes" -> padConnection[0] = PadConnection.SOLID;
          case "no" -> padConnection[0] = PadConnection.NONE;
          case "thru_hole_only" -> padConnection[0] = PadConnection.THT_THERMAL;
          default -> {
            // an unknown keyword changes nothing
          }
        }
        continue;
      }
      if ("clearance".equals(reader.readAtom())) {
        clearance = toUm(reader.readAtom());
      }
      reader.skipToEndOfList();
    }
  }

  /** Reads the rest of {@code (polygon (pts (xy 1 2) ...))} into the bounding box. */
  private static void readOutline(Reader reader, double[] box) {
    int depth = 1;
    while (depth > 0) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        return;
      }
      if (reader.leaveList()) {
        depth--;
      } else if (reader.enterList()) {
        depth++;
        if ("xy".equals(reader.readAtom())) {
          double x = toUm(reader.readAtom());
          double y = toUm(reader.readAtom());
          box[0] = Math.min(box[0], x);
          box[1] = Math.min(box[1], y);
          box[2] = Math.max(box[2], x);
          box[3] = Math.max(box[3], y);
        }
      } else {
        reader.readAtom();
      }
    }
  }

  private static boolean isNumber(String value) {
    try {
      Double.parseDouble(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** A number as written; 0 if it is not a number. */
  private static double toNumber(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** A length written in millimetres as micrometres; 0 if it is not a number. */
  private static double toUm(String millimetres) {
    try {
      return Double.parseDouble(millimetres) * 1000.0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** A cursor over the S-expressions of the file, which stores nothing it is not asked for. */
  private static final class Reader {
    private final String text;
    private int pos;

    Reader(String text) {
      this.text = text;
    }

    boolean isFinished() {
      return pos >= text.length();
    }

    void skipBlanks() {
      while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
        pos++;
      }
    }

    /** Consumes a '(' if it is next. */
    boolean enterList() {
      if (pos < text.length() && text.charAt(pos) == '(') {
        pos++;
        return true;
      }
      return false;
    }

    /** Consumes a ')' if it is next. */
    boolean leaveList() {
      if (pos < text.length() && text.charAt(pos) == ')') {
        pos++;
        return true;
      }
      return false;
    }

    /** Reads a symbol or a quoted string; an empty string if a parenthesis or the end is next. */
    String readAtom() {
      skipBlanks();
      if (pos >= text.length()) {
        return "";
      }
      char c = text.charAt(pos);
      if (c == '(' || c == ')') {
        return "";
      }
      if (c == '"') {
        pos++;
        StringBuilder result = new StringBuilder();
        while (pos < text.length() && text.charAt(pos) != '"') {
          char current = text.charAt(pos++);
          if (current == '\\' && pos < text.length()) {
            current = text.charAt(pos++);
          }
          result.append(current);
        }
        pos++;
        return result.toString();
      }
      int start = pos;
      while (pos < text.length()
          && !Character.isWhitespace(text.charAt(pos))
          && text.charAt(pos) != '('
          && text.charAt(pos) != ')') {
        pos++;
      }
      return text.substring(start, pos);
    }

    /** Skips the rest of the current list, its closing parenthesis included. */
    void skipToEndOfList() {
      int depth = 1;
      while (pos < text.length() && depth > 0) {
        char c = text.charAt(pos);
        if (c == '"') {
          pos++;
          while (pos < text.length() && text.charAt(pos) != '"') {
            if (text.charAt(pos) == '\\') {
              pos++;
            }
            pos++;
          }
        } else if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        }
        pos++;
      }
    }
  }
}
