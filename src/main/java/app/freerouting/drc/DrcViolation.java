package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Represents a single DRC violation, matching KiCad's JSON schema.
 */
public class DrcViolation
{
  /**
   * Human-readable description of the violation
   */
  @SerializedName("description")
  public final String description;

  /**
   * Items involved in the violation
   */
  @SerializedName("items")
  public final List<DrcViolationItem> items;

  /**
   * Severity of the violation ("error", "warning", "ignore")
   */
  @SerializedName("severity")
  public final String severity;

  /**
   * Type of violation (e.g., "clearance", "via_dangling", etc.)
   */
  @SerializedName("type")
  public final String type;

  public DrcViolation(String type, String description, String severity, List<DrcViolationItem> items)
  {
    this.type = type;
    this.description = description;
    this.severity = severity;
    this.items = items;
  }
}