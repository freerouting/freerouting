package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the traces (routed connections) of a board.
 */
public class BoardFileStatisticsTraces implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
  @SerializedName("total_segment_count")
  public Integer totalSegmentCount = null;
  @SerializedName("total_length")
  public Float totalLength = null;
  @SerializedName("average_length")
  public Float averageLength = null;
  @SerializedName("total_vertical_length")
  public Float totalVerticalLength = null;
  @SerializedName("total_horizontal_length")
  public Float totalHorizontalLength = null;
  @SerializedName("total_angled_length")
  public Float totalAngledLength = null;
}