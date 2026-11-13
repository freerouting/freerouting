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
 * as a {@code track_width} error. This class reads it back, so that Freerouting does not neck a
 * trace down below it.
 */
public final class KiCadProject {

  private final String sourceName;
  private final Double minTrackWidthUm;

  private KiCadProject(String sourceName, Double minTrackWidthUm) {
    this.sourceName = sourceName;
    this.minTrackWidthUm = minTrackWidthUm;
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
    JsonElement width =
        member(member(member(root.getAsJsonObject(), "board"), "design_settings"), "rules");
    width =
        width != null && width.isJsonObject()
            ? width.getAsJsonObject().get("min_track_width")
            : null;
    Double widthUm = null;
    if (width != null && width.isJsonPrimitive() && width.getAsJsonPrimitive().isNumber()) {
      double um = width.getAsDouble() * 1000.0; // KiCad stores lengths in millimetres
      if (um > 0) {
        widthUm = um;
      }
    }
    return new KiCadProject(sourceName, widthUm);
  }

  private static JsonElement member(JsonElement element, String name) {
    if (element == null || !element.isJsonObject()) {
      return null;
    }
    return ((JsonObject) element).get(name);
  }
}
