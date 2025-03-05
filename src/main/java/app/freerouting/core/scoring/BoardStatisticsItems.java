package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the components of a board.
 */
public class BoardStatisticsItems implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
  @SerializedName("trace_count")
  public Integer traceCount = null;
  @SerializedName("via_count")
  public Integer viaCount;
  @SerializedName("conduction_area_count")
  public Integer conductionAreaCount;
  @SerializedName("drill_item_count")
  public Integer drillItemCount;
  @SerializedName("pin_count")
  public Integer pinCount;
  @SerializedName("component_count")
  public Integer componentOutlineCount;
  @SerializedName("other_count")
  public Integer otherCount;

}