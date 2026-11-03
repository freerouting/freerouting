package app.freerouting.io.specctra;

import app.freerouting.settings.RouterSettings;

/** Immutable import decisions supplied before the parser allocates board items. */
public record DsnReadOptions(boolean canonicalPlacementOrder) {
  /** Retains legacy source enumeration when explicitly requested. */
  public static final DsnReadOptions INPUT_ORDER = new DsnReadOptions(false);

  /** Allocates uniquely named placements in exact reference-name order. */
  public static final DsnReadOptions REFERENCE_NAME = new DsnReadOptions(true);

  /** Universal default allocating placements canonically by reference name. */
  public static final DsnReadOptions DEFAULT = REFERENCE_NAME;

  /** Selects reference-name allocation as the universal import policy for all routing jobs. */
  public static DsnReadOptions forSettings(RouterSettings settings) {
    return REFERENCE_NAME;
  }
}
