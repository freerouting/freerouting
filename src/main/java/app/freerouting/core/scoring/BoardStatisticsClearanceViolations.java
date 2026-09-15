package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the clearance violations of a board. */
public class BoardStatisticsClearanceViolations implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("pre_existing_count")
  public Integer preExistingCount = 0;

  @SerializedName("router_introduced_count")
  public Integer routerIntroducedCount = 0;

  @SerializedName("total_violation_um")
  public Double totalViolationUm;

  @SerializedName("min_violation_um")
  public Double minViolationUm;

  @SerializedName("max_violation_um")
  public Double maxViolationUm;

  @SerializedName("avg_violation_um")
  public Double avgViolationUm;
}
