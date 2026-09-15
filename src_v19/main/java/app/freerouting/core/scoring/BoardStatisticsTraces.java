package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the traces (routed connections) of a board. */
public class BoardStatisticsTraces implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("total_segment_count")
  public Integer totalSegmentCount;

  @SerializedName("total_length")
  public Float totalLength;

  @SerializedName("total_length_mm")
  public Float totalLengthMm;

  @SerializedName("total_weighted_length")
  public Float totalWeightedLength;

  @SerializedName("average_length")
  public Float averageLength;

  @SerializedName("total_vertical_length")
  public Float totalVerticalLength;

  @SerializedName("total_horizontal_length")
  public Float totalHorizontalLength;

  @SerializedName("total_angled_length")
  public Float totalAngledLength;
}
