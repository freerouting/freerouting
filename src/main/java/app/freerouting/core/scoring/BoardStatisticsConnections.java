package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class BoardStatisticsConnections implements Serializable {

  @SerializedName("maximum_count")
  public Integer maximumCount;
  @SerializedName("incomplete_count")
  public Integer incompleteCount;
}