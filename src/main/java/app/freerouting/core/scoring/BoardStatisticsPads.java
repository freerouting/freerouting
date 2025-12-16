package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Statistics of the pads of a board.
 */
public class BoardStatisticsPads implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;
}