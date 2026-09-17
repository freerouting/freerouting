package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/** Settings configuration for a single board layer. */
@Schema(name = "LayerSettings", description = "Settings configuration for a single board layer")
public class LayerSettings implements Serializable, Cloneable {

  @SerializedName("routable")
  @Schema(description = "Whether this layer is routable by the autorouter")
  public Boolean routable;

  @SerializedName("preferred_direction_horizontal")
  @Schema(
      description =
          "Whether the preferred direction on this layer is horizontal (false = vertical)")
  public Boolean preferredDirectionHorizontal;

  /**
   * Per-layer bend cost added to the maze expansion value each time the router changes direction on
   * this layer. Null means "use the board default". Valid range when non-null: 0.0 (no penalty) to
   * 9.9 (strongly avoids bends).
   */
  @SerializedName("bend_cost")
  @Schema(description = "Per-layer bend cost penalty (0.0 to 9.9). Null uses the board default.")
  public Double bendCost;

  /**
   * Trace cost per mm in the preferred direction on this layer. Null means "use computed board
   * default".
   */
  @SerializedName("preferred_direction_trace_cost")
  @Schema(
      description =
          "Trace cost per mm in preferred direction on layer. Null uses computed board default.")
  public Double preferredDirectionTraceCost;

  /**
   * Trace cost per mm against the preferred direction on this layer. Null means "use computed board
   * default".
   */
  @SerializedName("undesired_direction_trace_cost")
  @Schema(
      description =
          "Trace cost against preferred direction on layer. Null uses computed board default.")
  public Double undesiredDirectionTraceCost;

  /** Default constructor required for reflection and serialization. */
  public LayerSettings() {}

  /**
   * Convenience constructor.
   *
   * @param routable whether the layer is routable by the autorouter
   * @param preferredDirectionHorizontal whether the preferred direction on this layer is horizontal
   */
  public LayerSettings(Boolean routable, Boolean preferredDirectionHorizontal) {
    this.routable = routable;
    this.preferredDirectionHorizontal = preferredDirectionHorizontal;
  }

  /** Full constructor. */
  public LayerSettings(Boolean routable, Boolean preferredDirectionHorizontal, Double bendCost) {
    this.routable = routable;
    this.preferredDirectionHorizontal = preferredDirectionHorizontal;
    this.bendCost = bendCost;
  }

  @Override
  public LayerSettings clone() {
    try {
      return (LayerSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      LayerSettings copy = new LayerSettings();
      copy.routable = this.routable;
      copy.preferredDirectionHorizontal = this.preferredDirectionHorizontal;
      copy.bendCost = this.bendCost;
      copy.preferredDirectionTraceCost = this.preferredDirectionTraceCost;
      copy.undesiredDirectionTraceCost = this.undesiredDirectionTraceCost;
      return copy;
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    LayerSettings that = (LayerSettings) other;
    return java.util.Objects.equals(routable, that.routable)
        && java.util.Objects.equals(preferredDirectionHorizontal, that.preferredDirectionHorizontal)
        && java.util.Objects.equals(bendCost, that.bendCost)
        && java.util.Objects.equals(preferredDirectionTraceCost, that.preferredDirectionTraceCost)
        && java.util.Objects.equals(undesiredDirectionTraceCost, that.undesiredDirectionTraceCost);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        routable,
        preferredDirectionHorizontal,
        bendCost,
        preferredDirectionTraceCost,
        undesiredDirectionTraceCost);
  }
}
