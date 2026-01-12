package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Represents a single DRC violation, matching KiCad's JSON schema.
 */
public class DrcViolation {

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
   * "unconnected_items" - This entry represents a specific Net that is not fully continuous. Instead of listing a single isolated item, this object contains an array of two specific items (e.g., Pad A and Pad B, or a Track End and a Pad) that belong to the same net but have no copper path connecting them.
   * "clearance" - This occurs when two pieces of copper from different nets (e.g., a power track and a ground pad) are closer to each other than the minimum safety distance defined in your design rules.
   * "track_dangling" - A segment of copper track that is connected at one end but stops in the middle of nowhere at the other end.
   * "via_dangling" - A via that doesn’t actually connect two valid layers.
   * "hole_clearance" - This is a violation of the distance between the edge of a drilled hole (whether plated or non-plated) and nearby copper of a different net.
   */
  @SerializedName("type")
  public final String type;

  public DrcViolation(String type, String description, String severity, List<DrcViolationItem> items) {
    this.type = type;
    this.description = description;
    this.severity = severity;
    this.items = items;
  }
}