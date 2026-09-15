package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Costs used by the router's search and rip-up algorithms.
 *
 * <p>This object is deliberately separate from router and optimizer board-score weights. Its values
 * affect route search behavior; only the legacy score's length and via terms read the corresponding
 * legacy values.
 */
public class RoutingCostSettings implements Serializable, Cloneable {

  // The cost of 1 mm of trace length if the trace is routed in the preferred
  // direction, defined for each layer.
  @SerializedName("preferred_direction_trace_cost")
  public transient double[] preferredDirectionTraceCost;

  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction, defined for each layer.
  @SerializedName("undesired_direction_trace_cost")
  public transient double[] undesiredDirectionTraceCost;

  // The default cost of 1 mm of trace length in the preferred direction.
  @SerializedName("default_preferred_direction_trace_cost")
  public Double defaultPreferredDirectionTraceCost;

  // The default cost of 1 mm of trace length in the undesired direction.
  @SerializedName("default_undesired_direction_trace_cost")
  public Double defaultUndesiredDirectionTraceCost;

  // The cost of a via on a regular (non-plane) net.
  @SerializedName(
      value = "via_costs",
      alternate = {"viaCosts"})
  public Integer viaCosts;

  // The cost of a via if the via is placed on a plane.
  @SerializedName("plane_via_costs")
  public Integer planeViaCosts;

  /**
   * Base rip-up cost for the first rip-up-and-reroute pass. This is a routing-control parameter
   * multiplied by the pass number inside {@code BatchAutorouter}; it does not appear in the board
   * score formula.
   */
  @SerializedName(
      value = "start_ripup_costs",
      alternate = {"startRipupCosts"})
  public Integer startRipupCosts;

  // Default bend cost/penalty per direction change on a layer.
  @SerializedName("default_bend_cost")
  public Double defaultBendCost;

  /**
   * Legacy board-score weights retained here for JSON and V1 compatibility. They will move to
   * {@code RouterScoreSettings} when the V1 formula is separated from search costs.
   */
  @SerializedName("unrouted_net_penalty")
  public Float unroutedNetPenalty;

  @SerializedName("clearance_violation_penalty")
  public Float clearanceViolationPenalty;

  @SerializedName("bend_penalty")
  public Float bendPenalty;

  /** Creates a deep copy, including layer-specific trace-cost arrays. */
  @Override
  public RoutingCostSettings clone() {
    try {
      RoutingCostSettings result = (RoutingCostSettings) super.clone();
      if (this.preferredDirectionTraceCost != null) {
        result.preferredDirectionTraceCost = this.preferredDirectionTraceCost.clone();
      }
      if (this.undesiredDirectionTraceCost != null) {
        result.undesiredDirectionTraceCost = this.undesiredDirectionTraceCost.clone();
      }
      return result;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
