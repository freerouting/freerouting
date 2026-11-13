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
 */
public final class KiCadProject {

  private final String sourceName;
  private final Double minTrackWidthUm;
  private final Double minConnectionWidthUm;

  private KiCadProject(String sourceName, Double minTrackWidthUm, Double minConnectionWidthUm) {
    this.sourceName = sourceName;
    this.minTrackWidthUm = minTrackWidthUm;
    this.minConnectionWidthUm = minConnectionWidthUm;
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
        positiveUm(member(rules, "min_connection")));
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
