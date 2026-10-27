package app.freerouting.board.model.structure;

/** Sorted fixed states of board items. The strongest fixed states came last. */
public enum FixedState {
  UNFIXED, // The item is not fixed, it is allowed to move or delete it.
  SHOVE_FIXED, // The item is fixed by the user against shoves.
  USER_FIXED, // The item is fixed by the user.
  SYSTEM_FIXED, // The item is temporarily locked by an algorithm/stage.
  /**
   * Fixed by the input, as the DSN reader makes a {@code fix} wire or via (e.g. locked KiCad track,
   * via, or arc stand-in). Routing treats it as permanent: never moved, ripped, or deleted, and
   * sessions leave it out. A trace in this state is also a preserved route (taking centerline
   * contacts).
   */
  INPUT_FIXED;

  /** Whether this fixed state represents a DSN fix (SYSTEM_FIXED or INPUT_FIXED). */
  public boolean isFix() {
    return this == SYSTEM_FIXED || this == INPUT_FIXED;
  }

  /** Whether a trace in this state represents a preserved route (USER_FIXED or INPUT_FIXED). */
  public boolean isPreservedRoute() {
    return this == USER_FIXED || this == INPUT_FIXED;
  }
}
