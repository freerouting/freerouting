package app.freerouting.board;

/**
 * Sorted fixed states of board items. The strongest fixed states came last.
 */
public enum FixedState {
  UNFIXED, // The item is not fixed, it is allowed to move or delete it.
  SHOVE_FIXED, // The item is fixed by the user.
  USER_FIXED, // The item is fixed by the system.
  SYSTEM_FIXED
}