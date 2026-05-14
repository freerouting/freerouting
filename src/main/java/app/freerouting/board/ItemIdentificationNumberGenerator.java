package app.freerouting.board;

import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * Creates unique identification numbers for items on the board with overflow protection built-in.
 * <p>
 * The counter starts at 1 and increments monotonically. Once it reaches
 * {@link #c_max_id_no} ({@code Integer.MAX_VALUE / 2} ≈ 1 billion), a single warning is emitted
 * and the counter wraps back to 1 to avoid {@code int} overflow and negative IDs.
 * In practice, wrap-around is reached only after billions of item insertions, which corresponds
 * to many days of continuous routing on a large board. Wrapping avoids the log-flooding that
 * the original code caused by warning on every insertion after the threshold (see Issue #684).
 * </p>
 * <p>
 * IDs are not required to be globally unique across the board's lifetime; they only need to be
 * unique among currently-live board items. Because items are rapidly created and deleted during
 * routing, wrap-around is safe in practice.
 * </p>
 */
public class ItemIdentificationNumberGenerator implements IdentificationNumberGenerator, Serializable {

  private static final int c_max_id_no = Integer.MAX_VALUE / 2;
  private int last_generated_id_no;
  /**
   * Tracks how many times the counter has wrapped around (for diagnostics).
   * {@code long} is used so the diagnostic counter itself never overflows.
   */
  private long wrapAroundCount;

  /**
   * Creates a new ItemIdentificationNumberGenerator.
   */
  public ItemIdentificationNumberGenerator() {
  }

  /**
   * Create a new unique identification number. Use eventually the id_no generated from the host system for synchronisation.
   */
  @Override
  public int new_no() {
    if (last_generated_id_no >= c_max_id_no) {
      // Wrap around to 1 instead of overflowing into negative territory.
      // Emit a single warning per wrap so the log is not flooded.
      wrapAroundCount++;
      FRLogger.warn("IdNoGenerator: ID counter reached " + c_max_id_no
          + " and wrapped around to 1 (wrap #" + wrapAroundCount + ")."
          + " IDs that were previously assigned to now-deleted items may be"
          + " assigned again to newly created items."
          + " Consider restarting the router to regenerate IDs from scratch.");
      last_generated_id_no = 0;
    }
    ++last_generated_id_no;
    return last_generated_id_no;
  }

  /**
   * Return the maximum generated id number so far.
   */
  @Override
  public int max_generated_no() {
    return last_generated_id_no;
  }
}