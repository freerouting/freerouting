package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class RouterScoringSettings implements Serializable
{
  public transient double[] preferredDirectionTraceCost;
  public transient double[] undesiredDirectionTraceCost;
  @SerializedName("default_preferred_direction_trace_cost")
  public double defaultPreferredDirectionTraceCost = 1.0;
  @SerializedName("default_undesired_direction_trace_cost")
  public double defaultUndesiredDirectionTraceCost = 2.5;
  @SerializedName("via_costs")
  public int via_costs = 50;
  @SerializedName("plane_via_costs")
  public int plane_via_costs = 5;
  @SerializedName("start_ripup_costs")
  public int start_ripup_costs = 100;
}