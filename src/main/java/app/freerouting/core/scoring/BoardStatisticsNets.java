package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the nets (unrouted connections) of a board.
 */
public class BoardStatisticsNets implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount;
  @SerializedName("class_count")
  public Integer classCount;
}
