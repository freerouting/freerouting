package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the vias of a board. */
public class BoardStatisticsVias implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("through_hole_count")
  public Integer throughHoleCount;

  @SerializedName("blind_count")
  public Integer blindCount;

  @SerializedName("buried_count")
  public Integer buriedCount;
}
