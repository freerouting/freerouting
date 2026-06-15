package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Settings for the route optimizer which runs after auto-routing to reduce vias and trace length.
 */
public class RouterOptimizerSettings implements Serializable, Cloneable {

  /**
   * Whether the route optimizer is enabled.
   */
  @SerializedName("enabled")
  public Boolean enabled;

  /**
   * The identifier of the optimization algorithm to use (e.g., "freerouting-optimizer").
   */
  @SerializedName("algorithm")
  public String algorithm;

  /**
   * The maximum number of full optimization passes (sweeps over the board's items) to run.
   */
  @SerializedName("max_passes")
  public Integer maxPasses;

  /**
   * The maximum number of threads to use for parallel route optimization.
   */
  @SerializedName("max_threads")
  public Integer maxThreads;

  /**
   * The improvement threshold (as a fraction, e.g., 0.01 for 1%) below which the optimizer terminates.
   * If a pass improves the board by less than this fraction, the optimization process stops.
   */
  @SerializedName("improvement_threshold")
  public Float optimizationImprovementThreshold;

  /**
   * A multiplier applied to the base ripup cost at the start of optimization.
   * Higher values make ripping up existing traces more expensive, prioritizing routing speed.
   */
  @SerializedName("additional_ripup_cost_factor_at_start")
  public Integer additionalRipupCostFactorAtStart;

  /**
   * A cost discount factor applied when ripping up trace items (as opposed to vias).
   * Typically less than 1.0 to make traces easier to rip up and reroute than vias.
   */
  @SerializedName("trace_ripup_cost_factor")
  public Float traceRipupCostFactor;

  /**
   * The maximum number of autoroute passes allowed when ripping up and rerouting a single item (and its connected items) during optimization.
   */
  @SerializedName("max_autoroute_passes")
  public Integer maxAutoroutePasses;

  // -------------------------------

  /**
   * The strategy to update the board: GREEDY (update immediately on any improvement),
   * GLOBAL_OPTIMAL (calculate updates in parallel and apply the single best improvement),
   * or HYBRID (combine GREEDY and GLOBAL_OPTIMAL).
   */
  public transient BoardUpdateStrategy boardUpdateStrategy;

  /**
   * The ratio of GLOBAL_OPTIMAL to GREEDY updates when using the HYBRID strategy (e.g., "1:1").
   */
  public transient String hybridRatio;

  /**
   * The strategy for selecting and ordering the items to be optimized (e.g., SEQUENTIAL, RANDOM, or PRIORITIZED).
   */
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