package app.freerouting.rules;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a trace tuning profile configuring time-domain delay calculations, target
 * impedance, per-layer track propagation speeds, and via delay overrides.
 *
 * @param name profile name identifier (never null or blank)
 * @param type profile topology type (single vs. differential)
 * @param targetImpedanceOhms target controlled impedance in ohms (<= 0 if unconstrained)
 * @param enableTimeDomainTuning whether delay-based time-domain tuning is enabled
 * @param trackPropagations layer-specific track propagation descriptors
 * @param globalViaUnitDelay default via propagation delay rate (e.g. ps/cm)
 * @param viaDelayOverrides layer transition via delay overrides
 */
public record TuningProfile(
    String name,
    TuningProfileType type,
    double targetImpedanceOhms,
    boolean enableTimeDomainTuning,
    List<TrackPropagation> trackPropagations,
    double globalViaUnitDelay,
    List<ViaDelayOverride> viaDelayOverrides)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Compact constructor enforcing non-null parameters, defaults, and unmodifiable lists. */
  public TuningProfile {
    Objects.requireNonNull(name, "name cannot be null");
    name = name.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("TuningProfile name cannot be empty");
    }

    if (type == null) {
      type = TuningProfileType.SINGLE;
    }
    if (Double.isNaN(targetImpedanceOhms)
        || Double.isInfinite(targetImpedanceOhms)
        || targetImpedanceOhms < 0.0) {
      targetImpedanceOhms = 0.0;
    }
    if (Double.isNaN(globalViaUnitDelay)
        || Double.isInfinite(globalViaUnitDelay)
        || globalViaUnitDelay < 0.0) {
      globalViaUnitDelay = 0.0;
    }

    trackPropagations =
        (trackPropagations != null) ? List.copyOf(trackPropagations) : Collections.emptyList();
    viaDelayOverrides =
        (viaDelayOverrides != null) ? List.copyOf(viaDelayOverrides) : Collections.emptyList();
  }

  /**
   * Convenience constructor creating a default unconstrained profile with the given name.
   *
   * @param name profile name
   */
  public TuningProfile(String name) {
    this(name, TuningProfileType.SINGLE, 0.0, false, List.of(), 0.0, List.of());
  }

  /**
   * Finds the track propagation entry matching the specified signal layer.
   *
   * @param signalLayer layer name to match
   * @return matching TrackPropagation or {@code null} if none configured for the layer
   */
  public TrackPropagation getTrackPropagation(String signalLayer) {
    if (signalLayer == null) {
      return null;
    }
    String trimmed = signalLayer.trim();
    for (TrackPropagation entry : this.trackPropagations) {
      if (entry.signalLayer().equalsIgnoreCase(trimmed)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Finds a via delay override matching the specified signal layer transition.
   *
   * @param signalLayerFrom transition start signal layer
   * @param signalLayerTo transition end signal layer
   * @return matching ViaDelayOverride or {@code null} if none matches
   */
  public ViaDelayOverride getViaDelayOverride(String signalLayerFrom, String signalLayerTo) {
    if (signalLayerFrom == null || signalLayerTo == null) {
      return null;
    }
    for (ViaDelayOverride entry : this.viaDelayOverrides) {
      if (entry.matchesSignalTransition(signalLayerFrom, signalLayerTo)) {
        return entry;
      }
    }
    return null;
  }
}
