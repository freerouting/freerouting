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
 * <p>Only the zones are read; the fill polygons, which can make up most of the file, are skipped
 * without being stored.
 */
public final class KiCadBoardZones {

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
   */
  public record Zone(
      String netName,
      List<String> layers,
      double clearanceUm,
      double minThicknessUm,
      double minXUm,
      double minYUm,
      double maxXUm,
      double maxYUm) {

    /** Whether the zone is on the copper layer of this name. */
    public boolean isOnLayer(String layerName) {
      for (String layer : layers) {
        if (layer.equals(layerName)
            || ("*.Cu".equals(layer) && layerName.endsWith(".Cu"))
            || ("F&B.Cu".equals(layer) && ("F.Cu".equals(layerName) || "B.Cu".equals(layerName)))) {
          return true;
        }
      }
      return false;
    }
  }

  private final String sourceName;
  private final List<Zone> zones;

  private KiCadBoardZones(String sourceName, List<Zone> zones) {
    this.sourceName = sourceName;
    this.zones = List.copyOf(zones);
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
      if ("zone".equals(reader.readAtom())) {
        Zone zone = readZone(reader);
        if (zone != null) {
          zones.add(zone);
        }
      } else {
        reader.skipToEndOfList();
      }
    }
    return new KiCadBoardZones(sourceName, zones);
  }

  /** Reads the rest of a {@code (zone ...)}; returns null for a rule area. */
  private static Zone readZone(Reader reader) {
    String netName = "";
    List<String> layers = new ArrayList<>();
    double clearance = 0;
    double minThickness = 0;
    boolean ruleArea = false;
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
        case "connect_pads" -> clearance = readClearance(reader);
        case "polygon" -> readOutline(reader, box);
        default -> reader.skipToEndOfList();
      }
    }
    if (ruleArea || box[0] > box[2]) {
      return null;
    }
    return new Zone(netName, layers, clearance, minThickness, box[0], box[1], box[2], box[3]);
  }

  /** Reads the rest of {@code (connect_pads [yes|no|thru_hole_only] (clearance 0.5))}. */
  private static double readClearance(Reader reader) {
    double clearance = 0;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished() || reader.leaveList()) {
        return clearance;
      }
      if (!reader.enterList()) {
        reader.readAtom();
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
