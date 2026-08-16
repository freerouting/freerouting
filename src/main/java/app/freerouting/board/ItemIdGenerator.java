package app.freerouting.board;

import app.freerouting.datastructures.IdGenerator;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * Creates unique identification numbers for items on the board with overflow protection built-in.
 *
 * <p>The counter starts at 1 and increments monotonically. Once it reaches {@link #MAX_ID} ({@code
 * Integer.MAX_VALUE / 2} ≈ 1 billion), a single warning is emitted and the counter wraps back to 1
 * to avoid {@code int} overflow and negative IDs. In practice, wrap-around is reached only after
 * billions of item insertions, which corresponds to many days of continuous routing on a large
 * board. Wrapping avoids the log-flooding that the original code caused by warning on every
 * insertion after the threshold (see Issue #684).
 *
 * <p>IDs are not required to be globally unique across the board's lifetime; they only need to be
 * unique among currently-live board items. Because items are rapidly created and deleted during
 * routing, wrap-around is safe in practice.
 */
public class ItemIdGenerator implements IdGenerator, Serializable {

  private static final int MAX_ID = Integer.MAX_VALUE / 2;
  private int lastGeneratedId;

  /**
   * Tracks how many times the counter has wrapped around (for diagnostics). {@code long} is used so
   * the diagnostic counter itself never overflows.
   */
  private long wrapAroundCount;

  /** Creates a new ItemIdGenerator. */
  public ItemIdGenerator() {}

  /** Creates a new unique identification number. */
  @Override
  public int newId() {
    if (lastGeneratedId >= MAX_ID) {
      // Wrap around to 1 instead of overflowing into negative territory.
      // Emit a single warning per wrap so the log is not flooded.
      wrapAroundCount++;
      FRLogger.warn(
          "IdGenerator: ID counter reached "
              + MAX_ID
              + " and wrapped around to 1 (wrap #"
              + wrapAroundCount
              + ")."
              + " IDs that were previously assigned to now-deleted items may be"
              + " assigned again to newly created items."
              + " Consider restarting the router to regenerate IDs from scratch.");
      lastGeneratedId = 0;
    }
    ++lastGeneratedId;
    return lastGeneratedId;
  }

  /** Returns the maximum generated ID number so far. */
  @Override
  public int maxGeneratedId() {
    return lastGeneratedId;
  }
}
