package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Settings configuration for a single board layer.
 */
public class LayerSettings implements Serializable, Cloneable {

  @SerializedName("routable")
  public Boolean routable;

  @SerializedName("preferred_direction_horizontal")
  public Boolean preferredDirectionHorizontal;

  /**
   * Per-layer bend cost added to the maze expansion value each time the router
   * changes direction on this layer. Null means "use the board default".
   * Valid range when non-null: 0.0 (no penalty) to 9.9 (strongly avoids bends).
   */
  @SerializedName("bend_cost")
  public Double bendCost;

  /**
   * Default constructor required for reflection and serialization.
   */
  public LayerSettings() {
  }

  /**
   * Convenience constructor.
   *
   * @param routable                     whether the layer is routable by the autorouter
   * @param preferredDirectionHorizontal whether the preferred direction on this layer is horizontal
   */
  public LayerSettings(Boolean routable, Boolean preferredDirectionHorizontal) {
    this.routable = routable;
    this.preferredDirectionHorizontal = preferredDirectionHorizontal;
  }

  /**
   * Full constructor.
   */
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
      return copy;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LayerSettings that = (LayerSettings) o;
    return java.util.Objects.equals(routable, that.routable)
        && java.util.Objects.equals(preferredDirectionHorizontal, that.preferredDirectionHorizontal)
        && java.util.Objects.equals(bendCost, that.bendCost);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(routable, preferredDirectionHorizontal, bendCost);
  }
}
