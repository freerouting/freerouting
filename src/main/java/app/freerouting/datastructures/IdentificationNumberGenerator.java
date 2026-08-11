package app.freerouting.datastructures;

/** Interface for creating unique identification number. */
public interface IdentificationNumberGenerator {

  /** Create a new unique identification number. */
  int newNo();

  /** Return the maximum generated id number so far. */
  int maxGeneratedNo();
}
