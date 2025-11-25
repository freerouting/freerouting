package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete DRC report in KiCad's JSON schema format.
 * Based on https://schemas.kicad.org/drc.v1.json
 */
public class DrcReport
{
  /**
   * JSON schema URL
   */
  @SerializedName("$schema")
  public final String $schema = "https://schemas.kicad.org/drc.v1.json";

  /**
   * Coordinate units used in the report (e.g., "mm", "mil")
   */
  @SerializedName("coordinate_units")
  public final String coordinate_units;

  /**
   * Date and time when the report was generated
   */
  @SerializedName("date")
  public final String date;

  /**
   * Version of KiCad that generated the report (this is "N/A" for Freerouting)
   */
  @SerializedName("kicad_version")
  public final String kicad_version = "N/A";

  /**
   * Version of Freerouting that generated the report
   */
  @SerializedName("freerouting_version")
  public final String freerouting_version;

  /**
   * Source file name
   */
  @SerializedName("source")
  public final String source;

  /**
   * List of unconnected items (empty for now)
   */
  @SerializedName("unconnected_items")
  public final List<DrcViolation> unconnected_items;

  /**
   * List of violations found
   */
  @SerializedName("violations")
  public final List<DrcViolation> violations;

  /**
   * Schematic parity issues (empty for now)
   */
  @SerializedName("schematic_parity")
  public final List<Object> schematic_parity;

  public DrcReport(String coordinateUnits, String source, String version)
  {
    this.coordinate_units = coordinateUnits;
    this.source = source;
    this.freerouting_version = version;
    this.date = ZonedDateTime
        .now()
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    this.violations = new ArrayList<>();
    this.unconnected_items = new ArrayList<>();
    this.schematic_parity = new ArrayList<>();
  }

  /**
   * Add a violation to the report
   */
  public void addViolation(DrcViolation violation)
  {
    this.violations.add(violation);
  }

  /**
   * Add an unconnected item to the report
   */
  public void addUnconnectedItem(DrcViolation item)
  {
    this.unconnected_items.add(item);
  }
}