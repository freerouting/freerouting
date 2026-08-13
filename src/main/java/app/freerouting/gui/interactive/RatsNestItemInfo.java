package app.freerouting.gui.interactive;

/** Describes an item reported by the rats-nest inspection UI. */
public class RatsNestItemInfo {

  /** The kind of board item. */
  public RatsNestItemType type;

  /** The associated component name, when available. */
  public String componentName;

  /** The item name, when available. */
  public String name;

  /** The formatted item description shown to the user. */
  public String text;
}
