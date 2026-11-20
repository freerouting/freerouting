package app.freerouting.io.kicad;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The board settings of a KiCad project, read from its {@code .kicad_pro} file.
 *
 * <p>KiCad writes the net class widths into a Specctra design, but not the minimum track width of
 * the board ({@code Board Setup > Constraints}): the KiCad DRC reports every trace narrower than it
 * as a {@code track_width} error. The same page sets a minimum connection width, and the KiCad DRC
 * reports every trace narrower than that one as a {@code connection_width} error. This class reads
 * both back, so that Freerouting does not neck a trace down below either.
 *
 * <p>The copper to edge clearance and the hole clearance of the board are lost as well: the KiCad
 * DRC reports {@code copper_edge_clearance} and {@code hole_clearance} errors that Freerouting does
 * not see. Where the project does not set them, KiCad checks its own defaults, which this class
 * returns instead.
 */
public final class KiCadProject {

  /** The copper to edge clearance that KiCad checks when the project does not set one. */
  public static final double DEFAULT_COPPER_EDGE_CLEARANCE_UM =
      KiCadBoardZones.DEFAULT_EDGE_CLEARANCE_UM;

  /** The hole clearance that KiCad checks when the project does not set one. */
  public static final double DEFAULT_HOLE_CLEARANCE_UM = 250.0;

  private final String sourceName;
  private final Double minTrackWidthUm;
  private final Double minConnectionWidthUm;
  private final Double copperEdgeClearanceUm;
  private final Double holeClearanceUm;

  private KiCadProject(
      String sourceName,
      Double minTrackWidthUm,
      Double minConnectionWidthUm,
      Double copperEdgeClearanceUm,
      Double holeClearanceUm) {
    this.sourceName = sourceName;
    this.minTrackWidthUm = minTrackWidthUm;
    this.minConnectionWidthUm = minConnectionWidthUm;
    this.copperEdgeClearanceUm = copperEdgeClearanceUm;
    this.holeClearanceUm = holeClearanceUm;
  }

  /** The file name, for messages. */
  public String sourceName() {
    return sourceName;
  }

  /**
   * The minimum track width of the board in micrometres ({@code
   * board.design_settings.rules.min_track_width}), or null if the project does not set a positive
   * one.
   */
  public Double minTrackWidthUm() {
    return minTrackWidthUm;
  }

  /**
   * The minimum connection width of the board in micrometres ({@code
   * board.design_settings.rules.min_connection}), or null if the project does not set a positive
   * one. The KiCad DRC checks it on traces too.
   */
  public Double minConnectionWidthUm() {
    return minConnectionWidthUm;
  }

  /**
   * The narrowest trace the KiCad DRC accepts, in micrometres: the larger of the minimum track
   * width and the minimum connection width, or null if the project sets neither.
   */
  public Double narrowestTraceWidthUm() {
    if (minTrackWidthUm == null) {
      return minConnectionWidthUm;
    }
    if (minConnectionWidthUm == null) {
      return minTrackWidthUm;
    }
    return Math.max(minTrackWidthUm, minConnectionWidthUm);
  }

  /**
   * The board settings of a board given without its project file: the {@code (setup (trace_min
   * ...))} of a board written by KiCad 5 or earlier, read by {@link KiCadBoardZones}, is its
   * minimum track width, and the clearances are the defaults of KiCad, which KiCad checks on such a
   * board.
   *
   * @param sourceName the board file name, for messages
   * @param minTrackWidthUm the minimum track width of the board in micrometres, or null
   */
  public static KiCadProject fromBoardSetup(String sourceName, Double minTrackWidthUm) {
    return new KiCadProject(sourceName, minTrackWidthUm, null, null, null);
  }

  /**
   * The copper to edge clearance that the KiCad DRC checks, in micrometres: that of the project
   * ({@code board.design_settings.rules.min_copper_edge_clearance}), else {@link
   * #DEFAULT_COPPER_EDGE_CLEARANCE_UM}.
   */
  public double copperEdgeClearanceUm() {
    return copperEdgeClearanceUm != null ? copperEdgeClearanceUm : DEFAULT_COPPER_EDGE_CLEARANCE_UM;
  }

  /**
   * The clearance between a drilled hole and the copper of another net that the KiCad DRC checks,
   * in micrometres: that of the project ({@code board.design_settings.rules.min_hole_clearance}),
   * else {@link #DEFAULT_HOLE_CLEARANCE_UM}.
   */
  public double holeClearanceUm() {
    return holeClearanceUm != null ? holeClearanceUm : DEFAULT_HOLE_CLEARANCE_UM;
  }

  /** Reads a {@code .kicad_pro} file. */
  public static KiCadProject read(Path path) throws IOException {
    return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
  }

  /**
   * Parses the text of a {@code .kicad_pro} file.
   *
   * @throws IllegalArgumentException if the text is not a JSON object
   */
  public static KiCadProject parse(String text, String sourceName) {
    JsonElement root;
    try {
      root = JsonParser.parseString(text);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("not a KiCad project file: " + e.getMessage(), e);
    }
    if (!root.isJsonObject()) {
      throw new IllegalArgumentException("not a KiCad project file: no JSON object");
    }
    JsonElement rules =
        member(member(member(root.getAsJsonObject(), "board"), "design_settings"), "rules");
    return new KiCadProject(
        sourceName,
        positiveUm(member(rules, "min_track_width")),
        positiveUm(member(rules, "min_connection")),
        nonNegativeUm(member(rules, "min_copper_edge_clearance")),
        nonNegativeUm(member(rules, "min_hole_clearance")));
  }

  /** A length in millimetres as micrometres, or null if it is not a number of at least 0. */
  private static Double nonNegativeUm(JsonElement value) {
    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
      double um = value.getAsDouble() * 1000.0; // KiCad stores lengths in millimetres
      if (um >= 0) {
        return um;
      }
    }
    return null;
  }

  /** A length in millimetres as micrometres, or null if it is not a positive number. */
  private static Double positiveUm(JsonElement value) {
    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
      double um = value.getAsDouble() * 1000.0; // KiCad stores lengths in millimetres
      if (um > 0) {
        return um;
      }
    }
    return null;
  }

  private static JsonElement member(JsonElement element, String name) {
    if (element == null || !element.isJsonObject()) {
      return null;
    }
    return ((JsonObject) element).get(name);
  }
}
