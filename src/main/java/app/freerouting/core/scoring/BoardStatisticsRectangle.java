package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * 2D rectangle representation for board bounding box and dimensions in scoring statistics.
 *
 * <p>Replaces {@link java.awt.geom.Rectangle2D.Float} with identical JSON field mapping ({@code x},
 * {@code y}, {@code width}, {@code height}) to decouple core scoring statistics from {@code
 * java.desktop}.
 */
public class BoardStatisticsRectangle implements Serializable {

  @SerializedName("x")
  public float x;

  @SerializedName("y")
  public float y;

  @SerializedName("width")
  public float width;

  @SerializedName("height")
  public float height;

  /** Default constructor for deserialization. */
  public BoardStatisticsRectangle() {}

  /**
   * Constructs a rectangle with the specified coordinates and dimensions.
   *
   * @param x the x-coordinate of the lower-left or top-left corner
   * @param y the y-coordinate of the lower-left or top-left corner
   * @param width the rectangle width
   * @param height the rectangle height
   */
  public BoardStatisticsRectangle(float x, float y, float width, float height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }
}
