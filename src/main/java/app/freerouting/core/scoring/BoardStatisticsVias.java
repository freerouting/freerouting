package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Statistics of the vias of a board.
 */
public class BoardStatisticsVias implements Serializable {

  // Total count of vias
  @SerializedName("total_count")
  public Integer totalCount;
  // Through-hole vias are spanning all layers
  @SerializedName("through_hole_count")
  public Integer throughHoleCount;
  // Blind vias are connecting outer to inner layers
  @SerializedName("blind_count")
  public Integer blindCount;
  // Buried vias are connecting inner layers only
  @SerializedName("buried_count")
  public Integer buriedCount;

}