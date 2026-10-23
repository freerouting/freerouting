package app.freerouting.io.specctra;

/** Immutable import decisions supplied before the parser allocates board items. */
public record DsnReadOptions(boolean canonicalPlacementOrder) {
  /** Retains legacy source enumeration for standalone/import-only callers. */
  public static final DsnReadOptions INPUT_ORDER = new DsnReadOptions(false);

  /** Allocates uniquely named placements in exact reference-name order. */
  public static final DsnReadOptions REFERENCE_NAME = new DsnReadOptions(true);
}
