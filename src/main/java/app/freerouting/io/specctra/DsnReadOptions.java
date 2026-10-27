package app.freerouting.io.specctra;

import app.freerouting.settings.RouterSettings;

/** Immutable import decisions supplied before the parser allocates board items. */
public record DsnReadOptions(boolean canonicalPlacementOrder) {
  /** Retains legacy source enumeration for standalone/import-only callers. */
  public static final DsnReadOptions INPUT_ORDER = new DsnReadOptions(false);

  /** Allocates uniquely named placements in exact reference-name order. */
  public static final DsnReadOptions REFERENCE_NAME = new DsnReadOptions(true);

  /**
   * Selects reference-name allocation when merged autorouter settings carry an explicit item-order
   * seed, including zero and negative values. A null settings object or a null seed keeps input
   * order.
   */
  public static DsnReadOptions forSettings(RouterSettings settings) {
    if (settings != null
        && settings.autorouter != null
        && settings.autorouter.itemOrderSeed != null) {
      return REFERENCE_NAME;
    }
    return INPUT_ORDER;
  }
}
