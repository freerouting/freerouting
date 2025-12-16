package app.freerouting.board;

import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * Creates unique identification numbers for items on the board with overflow protection built-in.
 */
public class ItemIdentificationNumberGenerator implements IdentificationNumberGenerator, Serializable {

  private static final int c_max_id_no = Integer.MAX_VALUE / 2;
  private int last_generated_id_no;

  /**
   * Creates a new ItemIdNoGenerator
   */
  public ItemIdentificationNumberGenerator() {
  }

  /**
   * Create a new unique identification number. Use eventually the id_no generated from the host system for synchronisation
   */
  @Override
  public int new_no() {
    if (last_generated_id_no >= c_max_id_no) {
      FRLogger.warn("IdNoGenerator: danger of overflow, please regenerate id numbers from scratch!");
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