package app.freerouting.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable representation of a via propagation delay override across a specific layer transition.
 *
 * <p>Overrides the default global via unit delay when a signal transitions between specific signal
 * layers through a via with defined layer boundaries.
 *
 * @param signalLayerFrom signal layer where trace enters the via
 * @param signalLayerTo signal layer where trace leaves the via
 * @param viaLayerFrom starting physical layer of the via barrel
 * @param viaLayerTo ending physical layer of the via barrel
 * @param delayPs explicit propagation delay penalty in picoseconds
 */
public record ViaDelayOverride(
    String signalLayerFrom,
    String signalLayerTo,
    String viaLayerFrom,
    String viaLayerTo,
    double delayPs)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Compact constructor enforcing non-null layer identifiers and non-negative delay. */
  public ViaDelayOverride {
    Objects.requireNonNull(signalLayerFrom, "signalLayerFrom cannot be null");
    Objects.requireNonNull(signalLayerTo, "signalLayerTo cannot be null");
    Objects.requireNonNull(viaLayerFrom, "viaLayerFrom cannot be null");
    Objects.requireNonNull(viaLayerTo, "viaLayerTo cannot be null");

    signalLayerFrom = signalLayerFrom.trim();
    signalLayerTo = signalLayerTo.trim();
    viaLayerFrom = viaLayerFrom.trim();
    viaLayerTo = viaLayerTo.trim();

    if (signalLayerFrom.isEmpty()
        || signalLayerTo.isEmpty()
        || viaLayerFrom.isEmpty()
        || viaLayerTo.isEmpty()) {
      throw new IllegalArgumentException("Layer identifiers in ViaDelayOverride cannot be empty");
    }

    if (Double.isNaN(delayPs) || Double.isInfinite(delayPs) || delayPs < 0.0) {
      delayPs = 0.0;
    }
  }

  /**
   * Checks whether this override matches the given signal layer transition in either direction.
   *
   * @param from candidate start signal layer
   * @param to candidate end signal layer
   * @return true if the signal layer endpoints match
   */
  public boolean matchesSignalTransition(String from, String to) {
    if (from == null || to == null) {
      return false;
    }
    String trimmedFrom = from.trim();
    String trimmedTo = to.trim();
    return (this.signalLayerFrom.equalsIgnoreCase(trimmedFrom)
            && this.signalLayerTo.equalsIgnoreCase(trimmedTo))
        || (this.signalLayerFrom.equalsIgnoreCase(trimmedTo)
            && this.signalLayerTo.equalsIgnoreCase(trimmedFrom));
  }
}
