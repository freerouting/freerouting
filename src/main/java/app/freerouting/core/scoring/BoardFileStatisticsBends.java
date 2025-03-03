package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the bends (of traces) of a board.
 */
public class BoardFileStatisticsBends implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
  @SerializedName("90_degree_count")
  public Integer ninetyDegreeCount = null;
  @SerializedName("45_degree_count")
  public Integer fortyFiveDegreeCount = null;
  @SerializedName("other_angle_count")
  public Integer otherAngleCount = null;
}