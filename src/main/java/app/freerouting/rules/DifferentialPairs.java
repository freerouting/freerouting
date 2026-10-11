package app.freerouting.rules;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe collection and bidirectional index for differential signal pairs on a board.
 *
 * <p>Enables O(1) lookups by common base name, by Net reference, or by net number.
 */
public class DifferentialPairs implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Primary storage keyed by lowercase base name to preserve insertion order. */
  private final Map<String, DifferentialPair> pairsByName = new LinkedHashMap<>();

  /** Fast index mapping net numbers to their differential pair. */
  private final Map<Integer, DifferentialPair> pairsByNetNumber = new HashMap<>();

  /** Creates an empty DifferentialPairs collection. */
  public DifferentialPairs() {}

  /**
   * Adds a differential pair to this registry.
   *
   * @param pair the differential pair to add
   * @throws NullPointerException if {@code pair} is null
   * @throws IllegalArgumentException if either net is already registered in another pair
   */
  public synchronized void add(DifferentialPair pair) {
    Objects.requireNonNull(pair, "pair cannot be null");

    DifferentialPair existingPos = pairsByNetNumber.get(pair.positiveNet().netNumber);
    if (existingPos != null && !existingPos.equals(pair)) {
      throw new IllegalArgumentException(
          "Net "
              + pair.positiveNet().name
              + " is already registered in pair "
              + existingPos.baseName());
    }

    DifferentialPair existingNeg = pairsByNetNumber.get(pair.negativeNet().netNumber);
    if (existingNeg != null && !existingNeg.equals(pair)) {
      throw new IllegalArgumentException(
          "Net "
              + pair.negativeNet().name
              + " is already registered in pair "
              + existingNeg.baseName());
    }

    String key = pair.baseName().toLowerCase(Locale.ROOT);
    this.pairsByName.put(key, pair);
    this.pairsByNetNumber.put(pair.positiveNet().netNumber, pair);
    this.pairsByNetNumber.put(pair.negativeNet().netNumber, pair);
  }

  /**
   * Retrieves a differential pair by its base name (case-insensitive).
   *
   * @param baseName the base name to search for
   * @return matching DifferentialPair, or null if not found
   */
  public synchronized DifferentialPair get(String baseName) {
    if (baseName == null) {
      return null;
    }
    return this.pairsByName.get(baseName.toLowerCase(Locale.ROOT));
  }

  /**
   * Finds the differential pair containing the given net.
   *
   * @param net the net to search for
   * @return the DifferentialPair containing {@code net}, or null if single-ended
   */
  public synchronized DifferentialPair findForNet(Net net) {
    if (net == null) {
      return null;
    }
    return findForNet(net.netNumber);
  }

  /**
   * Finds the differential pair containing the net with the given net number.
   *
   * @param netNumber the net number to search for
   * @return the DifferentialPair containing the net, or null if single-ended
   */
  public synchronized DifferentialPair findForNet(int netNumber) {
    return this.pairsByNetNumber.get(netNumber);
  }

  /**
   * Removes the given differential pair from the registry.
   *
   * @param pair the pair to remove
   * @return true if the pair was present and removed, false otherwise
   */
  public synchronized boolean remove(DifferentialPair pair) {
    if (pair == null) {
      return false;
    }
    String key = pair.baseName().toLowerCase(Locale.ROOT);
    DifferentialPair removed = this.pairsByName.remove(key);
    if (removed != null) {
      this.pairsByNetNumber.remove(pair.positiveNet().netNumber);
      this.pairsByNetNumber.remove(pair.negativeNet().netNumber);
      return true;
    }
    return false;
  }

  /** Returns an unmodifiable collection of all registered differential pairs. */
  public synchronized Collection<DifferentialPair> all() {
    return Collections.unmodifiableCollection(this.pairsByName.values());
  }

  /** Returns the total count of registered differential pairs. */
  public synchronized int count() {
    return this.pairsByName.size();
  }

  /** Returns true if no differential pairs are registered. */
  public synchronized boolean isEmpty() {
    return this.pairsByName.isEmpty();
  }

  /** Clears all differential pairs from the registry. */
  public synchronized void clear() {
    this.pairsByName.clear();
    this.pairsByNetNumber.clear();
  }
}
