package app.freerouting.datastructures;

/** Interface for creating unique identification numbers. */
public interface IdGenerator {

  /** Creates a new unique identification number. */
  int newId();

  /** Returns the maximum generated ID number so far. */
  int maxGeneratedId();
}
