package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Statistics of the components of a board.
 */
public class BoardStatisticsComponents implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;
}