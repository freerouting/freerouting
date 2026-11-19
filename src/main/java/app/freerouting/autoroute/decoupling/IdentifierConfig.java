package app.freerouting.autoroute.decoupling;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable configuration parameters for decoupling capacitor identification.
 *
 * @param maxOwnerRadiusMm maximum search radius (in mm) from a capacitor candidate to candidate IC
 *     supply pins
 * @param minPitchMm minimum allowable center-to-center pitch (in mm) between capacitor pads
 * @param maxPitchMm maximum allowable center-to-center pitch (in mm) between capacitor pads
 * @param minPadAreaRatio minimum allowable ratio of min(areaA, areaB) / max(areaA, areaB)
 * @param decouplingThreshold confidence score at or above which a candidate is marked DECOUPLING
 * @param reviewThreshold confidence score at or above which a candidate is marked REVIEW
 * @param ownerTopK maximum number of nearby IC supply pin owners to retain per candidate
 * @param runRefDesAudit whether to perform reference designator audit cross-checks
 * @param auditDemotesToReview whether audit conflicts demote DECOUPLING verdicts to REVIEW
 * @param groundNameHints patterns matched against net names to identify ground nets
 * @param forcedGroundNets explicit user overrides for net numbers that must be treated as ground
 * @param forcedRejectComponents explicit user overrides for component IDs that must be rejected
 */
public record IdentifierConfig(
    double maxOwnerRadiusMm,
    double minPitchMm,
    double maxPitchMm,
    double minPadAreaRatio,
    int decouplingThreshold,
    int reviewThreshold,
    int ownerTopK,
    boolean runRefDesAudit,
    boolean auditDemotesToReview,
    List<Pattern> groundNameHints,
    Set<Integer> forcedGroundNets,
    Set<Integer> forcedRejectComponents) {

  public IdentifierConfig {
    groundNameHints = groundNameHints == null ? List.of() : List.copyOf(groundNameHints);
    forcedGroundNets = forcedGroundNets == null ? Set.of() : Set.copyOf(forcedGroundNets);
    forcedRejectComponents =
        forcedRejectComponents == null ? Set.of() : Set.copyOf(forcedRejectComponents);
  }

  private static final List<Pattern> DEFAULT_GROUND_PATTERNS =
      List.of(
          Pattern.compile(
              "(?i)^(GND|VSS|AGND|DGND|PGND|GNDA|GNDD|GNDPWR|GNDREF|GNDS|VSSA|0V|V-0|V-GND|V-PE|PE|EARTH|CHASSIS|PWR_GND).*"),
          Pattern.compile(
              "(?i).*[._\\-/](GND|VSS|AGND|DGND|PGND|GNDA|GNDD|GNDPWR|GNDREF|GNDS|VSSA|0V|PE|EARTH|PWR_GND)$"));

  /** Creates a default configuration instance. */
  public static IdentifierConfig defaultConfig() {
    return new IdentifierConfig(
        15.0, 0.20, 12.0, 0.85, 60, 40, 4, true, true, DEFAULT_GROUND_PATTERNS, Set.of(), Set.of());
  }
}
