package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Configuration settings for selective component-scoped routing.
 *
 * <p>All fields are nullable wrapper types with no inline initializers to ensure that {@link
 * SettingsMerger} can distinguish between "not specified" and explicit values across priority
 * sources.
 */
public class RouterScopeSettings implements Serializable, Cloneable {

  @SerializedName("components")
  public String[] components;

  @SerializedName("multi_pin_policy")
  public String multiPinPolicy;

  @SerializedName("foreign_trace_policy")
  public String foreignTracePolicy;

  @SerializedName("foreign_ripup_cost_multiplier")
  public Double foreignRipupCostMultiplier;

  @SerializedName("nudge_only_passes")
  public Integer nudgeOnlyPasses;

  @SerializedName("include_decoupling_companions")
  public Boolean includeDecouplingCompanions;

  /** Copies non-null values from the source settings. */
  public void applyNewValuesFrom(RouterScopeSettings source) {
    if (source == null) {
      return;
    }
    if (source.components != null) {
      this.components = Arrays.copyOf(source.components, source.components.length);
    }
    if (source.multiPinPolicy != null) {
      this.multiPinPolicy = source.multiPinPolicy;
    }
    if (source.foreignTracePolicy != null) {
      this.foreignTracePolicy = source.foreignTracePolicy;
    }
    if (source.foreignRipupCostMultiplier != null) {
      this.foreignRipupCostMultiplier = source.foreignRipupCostMultiplier;
    }
    if (source.nudgeOnlyPasses != null) {
      this.nudgeOnlyPasses = source.nudgeOnlyPasses;
    }
    if (source.includeDecouplingCompanions != null) {
      this.includeDecouplingCompanions = source.includeDecouplingCompanions;
    }
  }

  @Override
  public RouterScopeSettings clone() {
    try {
      RouterScopeSettings cloned = (RouterScopeSettings) super.clone();
      if (this.components != null) {
        cloned.components = Arrays.copyOf(this.components, this.components.length);
      }
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }
}
