package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics describing board connections. */
public class BoardStatisticsConnections implements Serializable {

  @SerializedName("maximum_count")
  public Integer maximumCount;

  @SerializedName("incomplete_count")
  public Integer incompleteCount;

  /** Open connections with an end blocked by the design (e.g. pins on foreign fixed copper). */
  @SerializedName("design_blocked_count")
  public Integer designBlockedCount;
}
