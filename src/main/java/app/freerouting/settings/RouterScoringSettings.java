package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

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
  public double defaultPreferredDirectionTraceCost = 1.0;
  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction.
  @SerializedName("default_undesired_direction_trace_cost")
  public double defaultUndesiredDirectionTraceCost = 2.5;
  // The cost of a via.
  @SerializedName("via_costs")
  public int via_costs = 50;
  // The cost of a via if the via is placed on a plane.
  @SerializedName("plane_via_costs")
  public int plane_via_costs = 5;
  // The starting cost of a ripup operation. It might be increased during the
  // auto-routing process.
  @SerializedName("start_ripup_costs")
  public int start_ripup_costs = 100;
  // The penalty for an unrouted net.
  public float unroutedNetPenalty = 4000;
  // The penalty for a clearance violation.
  public float clearanceViolationPenalty = 1000;
  // The penalty for a bend.
  public float bendPenalty = 10;

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