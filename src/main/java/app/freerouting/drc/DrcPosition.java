package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;

/** Represents a position in the DRC report, matching KiCad's JSON schema. */
public class DrcPosition {

  /** X coordinate in the coordinate units specified in the report. */
  @SerializedName("x")
  public final double coordX;

  /** Y coordinate in the coordinate units specified in the report. */
  @SerializedName("y")
  public final double coordY;

  /**
   * Creates a DRC position from board coordinates converted to the report unit.
   *
   * @param coordX the X coordinate
   * @param coordY the Y coordinate
   */
  public DrcPosition(double coordX, double coordY) {
    this.coordX = coordX;
    this.coordY = coordY;
  }
}
