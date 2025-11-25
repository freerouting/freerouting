package app.freerouting.drc;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a position in the DRC report, matching KiCad's JSON schema.
 */
public class DrcPosition
{
  /**
   * X coordinate in the coordinate units specified in the report
   */
  @SerializedName("x")
  public final double x;

  /**
   * Y coordinate in the coordinate units specified in the report
   */
  @SerializedName("y")
  public final double y;

  public DrcPosition(double x, double y)
  {
    this.x = x;
    this.y = y;
  }
}