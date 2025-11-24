package app.freerouting.drc;

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
  public final String $schema = "https://schemas.kicad.org/drc.v1.json";

  /**
   * Coordinate units used in the report (e.g., "mm", "mil")
   */
  public final String coordinate_units;

  /**
   * Date and time when the report was generated
   */
  public final String date;

  /**
   * Version of the tool that generated the report
   */
  public final String freerouting_version;

  /**
   * Source file name
   */
  public final String source;

  /**
   * List of violations found
   */
  public final List<DrcViolation> violations;

  /**
   * List of unconnected items (empty for now)
   */
  public final List<Object> unconnected_items;

  /**
   * Schematic parity issues (empty for now)
   */
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
}