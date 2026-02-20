package app.freerouting.datastructures;

/** Interface for creating unique identification number. */
public interface IdNoGenerator {
  /** Create a new unique identification number. */
  int new_no();

  /** Return the maximum generated id number so far. */
  int max_generated_no();
}
