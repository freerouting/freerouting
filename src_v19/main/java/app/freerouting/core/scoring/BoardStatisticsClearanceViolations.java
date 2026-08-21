package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the clearance violations of a board. */
public class BoardStatisticsClearanceViolations implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("min_violation_um")
  public Double minViolationUm;

  @SerializedName("max_violation_um")
  public Double maxViolationUm;

  @SerializedName("avg_violation_um")
  public Double avgViolationUm;
}
