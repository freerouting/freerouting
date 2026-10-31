package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Statistics of the clearance violations of a board. */
public class BoardStatisticsClearanceViolations implements Serializable {

  @SerializedName("total_count")
  public Integer totalCount;

  @SerializedName("pre_existing_count")
  public Integer preExistingCount = 0;

  @SerializedName("unfixable_count")
  public Integer unfixableCount = 0;

  @SerializedName("router_introduced_count")
  public Integer routerIntroducedCount = 0;

  @SerializedName("new_or_changed_count")
  public Integer newOrChangedCount;

  @SerializedName("total_violation_um")
  public Double totalViolationUm;

  /** Summed clearance shortfall of the violations counted by {@link #fixableCount()}. */
  @SerializedName("fixable_violation_um")
  public Double fixableViolationUm;

  @SerializedName("min_violation_um")
  public Double minViolationUm;

  @SerializedName("max_violation_um")
  public Double maxViolationUm;

  @SerializedName("avg_violation_um")
  public Double avgViolationUm;

  /**
   * Returns the number of violations that routing can resolve: all except the unfixable ones
   * between two items the router never moves. Only these count against the board scores, because
   * the unfixable ones are the same on every routing result of a board.
   */
  public int fixableCount() {
    int total = totalCount != null ? totalCount : 0;
    int unfixable = unfixableCount != null ? unfixableCount : 0;
    return Math.max(0, total - unfixable);
  }

  /**
   * Returns the summed shortfall of the fixable violations in um, or the total shortfall for
   * statistics recorded without that split.
   */
  public double fixableShortfallUm() {
    Double shortfall = fixableViolationUm != null ? fixableViolationUm : totalViolationUm;
    return shortfall != null ? Math.max(0.0, shortfall) : 0.0;
  }
}
