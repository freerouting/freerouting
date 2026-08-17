package app.freerouting.io.kicad;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete DRC report in KiCad's JSON schema format. Based on
 * https://schemas.kicad.org/drc.v1.json
 */
@Schema(
    name = "KiCadDrcReport",
    description = "Represents a complete DRC report in KiCad's JSON schema format")
public class KiCadDrcReport {

  /** JSON schema URL. */
  @SerializedName("$schema")
  public final String jsonSchema = "https://schemas.kicad.org/drc.v1.json";

  /** Coordinate units used in the report (e.g., "mm", "mil"). */
  @SerializedName("coordinateUnits")
  public final String coordinateUnits;

  /** Date and time when the report was generated. */
  @SerializedName("date")
  public final String date;

  /** Version of KiCad that generated the report (this is "N/A" for Freerouting). */
  @SerializedName("kicadVersion")
  public final String kicadVersion = "N/A";

  /** Version of Freerouting that generated the report. */
  @SerializedName("freeroutingVersion")
  public final String freeroutingVersion;

  /** Source file name. */
  @SerializedName("source")
  public final String source;

  /** List of unconnected items (empty for now). */
  @SerializedName("unconnectedItems")
  public final List<KiCadDrcViolation> unconnectedItems;

  /** List of violations found. */
  @SerializedName("violations")
  public final List<KiCadDrcViolation> violations;

  /** Schematic parity issues (empty for now). */
  @SerializedName("schematicParity")
  public final List<Object> schematicParity;

  /** Quality score of the board. */
  @SerializedName("qualityScore")
  public Double qualityScore;

  /**
   * Creates an empty DRC report with the given metadata.
   *
   * @param coordinateUnits unit used for coordinates in the report
   * @param source source file name
   * @param version Freerouting version string
   */
  public KiCadDrcReport(String coordinateUnits, String source, String version) {
    this.coordinateUnits = coordinateUnits;
    this.source = source;
    this.freeroutingVersion = version;
    this.date = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    this.violations = new ArrayList<>();
    this.unconnectedItems = new ArrayList<>();
    this.schematicParity = new ArrayList<>();
  }

  /** Adds a violation to the report. */
  public void addViolation(KiCadDrcViolation violation) {
    this.violations.add(violation);
  }

  /** Adds an unconnected item to the report. */
  public void addUnconnectedItem(KiCadDrcViolation item) {
    this.unconnectedItems.add(item);
  }
}
