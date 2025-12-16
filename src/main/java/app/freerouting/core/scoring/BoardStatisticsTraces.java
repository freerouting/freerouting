package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the traces (routed connections) of a board.
 */
public class BoardStatisticsTraces implements Serializable
{
  // The total number of traces.
  @SerializedName("total_count")
  public Integer totalCount;
  // The total number of segments in all traces.
  @SerializedName("total_segment_count")
  public Integer totalSegmentCount;
  // The total length of all traces.
  @SerializedName("total_length")
  public Float totalLength;
  @SerializedName("total_weighted_length")
  public Float totalWeightedLength;
  // The average length of the traces.
  @SerializedName("average_length")
  public Float averageLength;
  // The total vertical length of all traces.
  @SerializedName("total_vertical_length")
  public Float totalVerticalLength;
  // The total horizontal length of all traces.
  @SerializedName("total_horizontal_length")
  public Float totalHorizontalLength;
  // The total angled (non-horizontal and non-vertical) length of all traces.
  @SerializedName("total_angled_length")
  public Float totalAngledLength;
}
