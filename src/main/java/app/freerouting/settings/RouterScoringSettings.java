package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Nullable settings for the router-board score. */
public class RouterScoringSettings implements Serializable, Cloneable {

  @SerializedName("version")
  public RouterScoringVersion version;

  @SerializedName("unrouted_connection_weight")
  public Float unroutedConnectionWeight;

  @SerializedName("clearance_violation_count_weight")
  public Float clearanceViolationCountWeight;

  @SerializedName("clearance_violation_depth_weight")
  public Float clearanceViolationDepthWeight;

  @SerializedName("clearance_violation_depth_scale")
  public Float clearanceViolationDepthScale;

  @Override
  public RouterScoringSettings clone() {
    try {
      return (RouterScoringSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
