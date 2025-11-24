package app.freerouting.drc;

import java.util.List;

/**
 * Represents a single DRC violation, matching KiCad's JSON schema.
 */
public class DrcViolation
{
  /**
   * Type of violation (e.g., "clearance", "via_dangling", etc.)
   */
  public final String type;

  /**
   * Human-readable description of the violation
   */
  public final String description;

  /**
   * Severity of the violation ("error", "warning", "ignore")
   */
  public final String severity;

  /**
   * Items involved in the violation
   */
  public final List<DrcViolationItem> items;

  public DrcViolation(String type, String description, String severity, List<DrcViolationItem> items)
  {
    this.type = type;
    this.description = description;
    this.severity = severity;
    this.items = items;
  }
}