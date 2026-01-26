package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class RouterOptimizerSettings implements Serializable, Cloneable {

  @SerializedName("enabled")
  public Boolean enabled;
  @SerializedName("algorithm")
  public String algorithm;
  @SerializedName("max_passes")
  public Integer maxPasses;
  @SerializedName("max_threads")
  public Integer maxThreads;
  @SerializedName("improvement_threshold")
  public Float optimizationImprovementThreshold;
  public transient BoardUpdateStrategy boardUpdateStrategy;
  public transient String hybridRatio;
  public transient ItemSelectionStrategy itemSelectionStrategy;

  public RouterOptimizerSettings() {
  }

  /**
   * Creates a deep copy of this RouterOptimizerSettings object.
   * All fields including transient ones are cloned.
   *
   * @return A new RouterOptimizerSettings instance with the same values
   */
  @Override
  public RouterOptimizerSettings clone() {
    try {
      RouterOptimizerSettings result = (RouterOptimizerSettings) super.clone();
      // Primitive wrappers and Strings are immutable, so no need to clone them
      // But we need to ensure transient fields are copied
      result.boardUpdateStrategy = this.boardUpdateStrategy;
      result.hybridRatio = this.hybridRatio;
      result.itemSelectionStrategy = this.itemSelectionStrategy;
      return result;
    } catch (CloneNotSupportedException e) {
      // This should never happen since we implement Cloneable
      throw new AssertionError("Clone not supported", e);
    }
  }
}