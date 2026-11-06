package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Basic parameters of a board. */
public class BoardStatisticsBoard implements Serializable {

  @SerializedName("bounding_box")
  public BoardStatisticsRectangle boundingBox;

  @SerializedName("size")
  public BoardStatisticsRectangle size;

  @SerializedName("area_cm2")
  public Float areaCm2;
}
