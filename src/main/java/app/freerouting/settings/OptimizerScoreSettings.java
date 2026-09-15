package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Nullable settings for the optimizer-board score. */
public class OptimizerScoreSettings implements Serializable, Cloneable {

  @SerializedName("version")
  public OptimizerScoringVersion version;

  @SerializedName("excess_wire_length_weight")
  public Float excessWireLengthWeight;

  @SerializedName("excess_via_weight")
  public Float excessViaWeight;

  @SerializedName("excess_bend_weight")
  public Float excessBendWeight;

  @SerializedName("length_floor")
  public Float lengthFloor;

  @SerializedName("difficulty_scale_floor")
  public Float difficultyScaleFloor;

  @Override
  public OptimizerScoreSettings clone() {
    try {
      return (OptimizerScoreSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
