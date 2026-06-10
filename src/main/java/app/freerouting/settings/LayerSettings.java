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

  @Override
  public LayerSettings clone() {
    try {
      return (LayerSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      LayerSettings copy = new LayerSettings();
      copy.routable = this.routable;
      copy.preferredDirectionHorizontal = this.preferredDirectionHorizontal;
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
    return java.util.Objects.equals(routable, that.routable) &&
        java.util.Objects.equals(preferredDirectionHorizontal, that.preferredDirectionHorizontal);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(routable, preferredDirectionHorizontal);
  }
}
