package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Weights that control how the autorouter scores a board state.
 *
 * <p>All fields are nullable so that the {@code SettingsMerger} / {@code ReflectionUtil.copyFields}
 * pipeline can distinguish "this source has no opinion" (null) from "this source explicitly sets
 * a value".  Hard-coded defaults must live exclusively in
 * {@link app.freerouting.settings.sources.DefaultSettings}.
 *
 * <p>Naming conventions:
 * <ul>
 *   <li><b>penalty</b> — subtracted when a quality constraint is violated (unrouted net, DRC
 *       violation, bend).  These appear in the board-score formula.</li>
 *   <li><b>costs</b> — subtracted as an absolute cost proportional to resource usage (via count,
 *       trace length).  These also appear in the board-score formula.</li>
 *   <li><b>startRipupCosts</b> — a routing-control parameter passed to the maze-search engine;
 *       it does <em>not</em> appear in the board-score formula.</li>
 * </ul>
 */
public class RouterScoringSettings implements Serializable, Cloneable {

  // The cost of 1 mm of trace length if the trace is routed in the preferred
  // direction, defined for each layer.
  public transient double[] preferredDirectionTraceCost;
  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction, defined for each layer.
  public transient double[] undesiredDirectionTraceCost;
  // The cost of 1 mm of trace length if the trace is routed in the preferred
  // direction.
  @SerializedName("default_preferred_direction_trace_cost")
  public Double defaultPreferredDirectionTraceCost;
  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction.
  @SerializedName("default_undesired_direction_trace_cost")
  public Double defaultUndesiredDirectionTraceCost;
  // The cost of a via on a regular (non-plane) net.
  @SerializedName("via_costs")
  public Integer viaCosts;
  // The cost of a via if the via is placed on a plane.
  @SerializedName("plane_via_costs")
  public Integer planeViaCosts;
  /**
   * Base ripup cost for the first ripup-and-reroute pass.
   * This is a routing-control parameter multiplied by the pass number inside
   * {@code BatchAutorouter}; it does NOT appear in the board-score formula.
   */
  @SerializedName("start_ripup_costs")
  public Integer startRipupCosts;
  // The penalty for an unrouted net.
  @SerializedName("unrouted_net_penalty")
  public Float unroutedNetPenalty;
  // The penalty for a clearance violation.
  @SerializedName("clearance_violation_penalty")
  public Float clearanceViolationPenalty;
  // The penalty for a bend.
  @SerializedName("bend_penalty")
  public Float bendPenalty;

  /**
   * Creates a deep copy of this RouterScoringSettings object.
   * All fields including arrays are cloned.
   *
   * @return A new RouterScoringSettings instance with the same values
   */
  @Override
  public RouterScoringSettings clone() {
    try {
      RouterScoringSettings result = (RouterScoringSettings) super.clone();
      // Clone array fields to ensure deep copy
      if (this.preferredDirectionTraceCost != null) {
        result.preferredDirectionTraceCost = this.preferredDirectionTraceCost.clone();
      }
      if (this.undesiredDirectionTraceCost != null) {
        result.undesiredDirectionTraceCost = this.undesiredDirectionTraceCost.clone();
      }
      // Primitive types and their wrappers are copied by super.clone()
      return result;
    } catch (CloneNotSupportedException e) {
      // This should never happen since we implement Cloneable
      throw new AssertionError("Clone not supported", e);
    }
  }
}