package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Nullable settings for the router-board score. */
public class RouterScoreSettings implements Serializable, Cloneable {

  @SerializedName("version")
  public RouterScoringVersion version;

  @SerializedName("unrouted_connection_weight")
  public Float unroutedConnectionWeight;

  @SerializedName("unrouted_free_fraction")
  public Float unroutedFreeFraction;

  @SerializedName("unrouted_first_half_weight")
  public Float unroutedFirstHalfWeight;

  @SerializedName("unrouted_second_half_weight")
  public Float unroutedSecondHalfWeight;

  @SerializedName("clearance_violation_count_weight")
  public Float clearanceViolationCountWeight;

  @SerializedName("clearance_violation_depth_weight")
  public Float clearanceViolationDepthWeight;

  @SerializedName("clearance_violation_depth_scale")
  public Float clearanceViolationDepthScale;

  @Override
  public RouterScoreSettings clone() {
    try {
      return (RouterScoreSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
