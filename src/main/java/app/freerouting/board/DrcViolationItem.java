package app.freerouting.board;

/**
 * Represents a single item involved in a DRC violation, matching KiCad's JSON schema.
 */
public class DrcViolationItem
{
  /**
   * Human-readable description of the item
   */
  public final String description;
  
  /**
   * Position of the item
   */
  public final DrcPosition pos;
  
  /**
   * Unique identifier of the item
   */
  public final String uuid;

  public DrcViolationItem(String description, DrcPosition pos, String uuid)
  {
    this.description = description;
    this.pos = pos;
    this.uuid = uuid;
  }
}
