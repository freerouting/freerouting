package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the clearance violations of a board. */
public class BoardStatisticsClearanceViolations implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("min_violation_mm")
  public Double minViolationMm;

  @SerializedName("max_violation_mm")
  public Double maxViolationMm;

  @SerializedName("avg_violation_mm")
  public Double avgViolationMm;
}
