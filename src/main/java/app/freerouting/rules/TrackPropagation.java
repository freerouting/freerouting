package app.freerouting.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable representation of layer-specific trace propagation properties within a tuning profile.
 *
 * <p>Maps trace width and propagation speed (time length factor) to a copper signal layer, along
 * with optional top and bottom reference plane layers used for controlled-impedance modeling.
 *
 * @param signalLayer the signal layer name (never null or blank)
 * @param topReference the top reference plane layer name, or {@code null} if unreferenced (e.g.
 *     outer microstrip)
 * @param bottomReference the bottom reference plane layer name, or {@code null} if unreferenced
 * @param trackWidth the trace width in internal board units, or {@code <= 0} if using netclass
 *     default
 * @param timeLengthFactor the unit propagation delay rate (e.g. ps/cm, aligning with Specctra DSN
 *     {@code time_length_factor})
 */
public record TrackPropagation(
    String signalLayer,
    String topReference,
    String bottomReference,
    double trackWidth,
    double timeLengthFactor)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Compact constructor enforcing non-null signal layer and non-negative parameters. */
  public TrackPropagation {
    Objects.requireNonNull(signalLayer, "signalLayer cannot be null");
    signalLayer = signalLayer.trim();
    if (signalLayer.isEmpty()) {
      throw new IllegalArgumentException("signalLayer cannot be empty");
    }

    if (topReference != null) {
      topReference = topReference.trim();
      if (topReference.isEmpty()
          || topReference.equalsIgnoreCase("none")
          || topReference.equalsIgnoreCase("<none>")) {
        topReference = null;
      } else if (topReference.equalsIgnoreCase(signalLayer)) {
        // A signal layer cannot serve as its own reference plane
        topReference = null;
      }
    }

    if (bottomReference != null) {
      bottomReference = bottomReference.trim();
      if (bottomReference.isEmpty()
          || bottomReference.equalsIgnoreCase("none")
          || bottomReference.equalsIgnoreCase("<none>")) {
        bottomReference = null;
      } else if (bottomReference.equalsIgnoreCase(signalLayer)) {
        // A signal layer cannot serve as its own reference plane
        bottomReference = null;
      }
    }

    if (Double.isNaN(trackWidth) || Double.isInfinite(trackWidth) || trackWidth < 0.0) {
      trackWidth = 0.0;
    }
    if (Double.isNaN(timeLengthFactor)
        || Double.isInfinite(timeLengthFactor)
        || timeLengthFactor < 0.0) {
      timeLengthFactor = 0.0;
    }
  }

  /**
   * Returns whether this layer has a top reference plane configured.
   *
   * @return true if top reference is non-null
   */
  public boolean hasTopReference() {
    return this.topReference != null;
  }

  /**
   * Returns whether this layer has a bottom reference plane configured.
   *
   * @return true if bottom reference is non-null
   */
  public boolean hasBottomReference() {
    return this.bottomReference != null;
  }
}
