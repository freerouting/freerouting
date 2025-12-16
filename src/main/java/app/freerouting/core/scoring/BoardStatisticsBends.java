package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Statistics of the bends (of traces) of a board.
 */
public class BoardStatisticsBends implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;
  @SerializedName("90_degree_count")
  public Integer ninetyDegreeCount;
  @SerializedName("45_degree_count")
  public Integer fortyFiveDegreeCount;
  @SerializedName("other_angle_count")
  public Integer otherAngleCount;
}