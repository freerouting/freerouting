package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the components of a board. */
public class BoardStatisticsItems implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("traceCount")
  public Integer traceCount;

  @SerializedName("viaCount")
  public Integer viaCount;

  @SerializedName("conductionAreaCount")
  public Integer conductionAreaCount;

  @SerializedName("drill_item_count")
  public Integer drillItemCount;

  @SerializedName("pinCount")
  public Integer pinCount;

  @SerializedName("component_count")
  public Integer componentOutlineCount;

  @SerializedName("other_count")
  public Integer otherCount;
}
