package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;

/** Represents a single item involved in a DRC violation, matching KiCad's JSON schema. */
public class DrcViolationItem {

  /** Human-readable description of the item. */
  @SerializedName("description")
  public final String description;

  /** Position of the item. */
  @SerializedName("pos")
  public final DrcPosition pos;

  /** Unique identifier of the item. */
  @SerializedName("uuid")
  public final String uuid;

  /**
   * Creates a report entry for one item involved in a violation.
   *
   * @param description human-readable item description
   * @param pos item position in report coordinates
   * @param uuid unique item identifier
   */
  public DrcViolationItem(String description, DrcPosition pos, String uuid) {
    this.description = description;
    this.pos = pos;
    this.uuid = uuid;
  }
}
