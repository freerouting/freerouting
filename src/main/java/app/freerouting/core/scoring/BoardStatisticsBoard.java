package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * Basic parameters of a board.
 */
public class BoardStatisticsBoard implements Serializable
{
  @SerializedName("bounding_box")
  public Rectangle2D.Float boundingBox = null;
  @SerializedName("size")
  public Rectangle2D.Float size = null;
}