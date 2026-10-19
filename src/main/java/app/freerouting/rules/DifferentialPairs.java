package app.freerouting.rules;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
   * Adds a differential pair to this registry, or updates constraints if a pair with the same base
   * name and nets is already registered.
   *
   * @param pair the differential pair to add
   * @throws NullPointerException if {@code pair} is null
   * @throws IllegalArgumentException if either net or the base name is already registered with
   *     different nets
   */
  public synchronized void add(DifferentialPair pair) {
    Objects.requireNonNull(pair, "pair cannot be null");

    String key = pair.baseName().toLowerCase(Locale.ROOT);
    DifferentialPair existingByName = this.pairsByName.get(key);
    if (existingByName != null && !hasSameIdentity(existingByName, pair)) {
      throw new IllegalArgumentException(
          "Differential pair "
              + pair.baseName()
              + " is already registered with different nets ("
              + existingByName.positiveNet().name
              + ", "
              + existingByName.negativeNet().name
              + ")");
    }

    checkNetAvailability(pair.positiveNet(), pair);
    checkNetAvailability(pair.negativeNet(), pair);

    this.pairsByName.put(key, pair);
    indexSubnets(pair.positiveNet(), pair);
    indexSubnets(pair.negativeNet(), pair);
  }

  private static boolean hasSameIdentity(DifferentialPair a, DifferentialPair b) {
    return a.baseName().equalsIgnoreCase(b.baseName())
        && a.positiveNet().equals(b.positiveNet())
        && a.negativeNet().equals(b.negativeNet());
  }

  private void checkNetAvailability(Net net, DifferentialPair pair) {
    if (net == null) {
      return;
    }
    DifferentialPair existing = pairsByNetNumber.get(net.netNumber);
    if (existing != null && !hasSameIdentity(existing, pair)) {
      throw new IllegalArgumentException(
          "Net " + net.name + " is already registered in pair " + existing.baseName());
    }
    if (net.netList != null) {
      for (Net subnet : net.netList.get(net.name)) {
        DifferentialPair existingSub = pairsByNetNumber.get(subnet.netNumber);
        if (existingSub != null && !hasSameIdentity(existingSub, pair)) {
          throw new IllegalArgumentException(
              "Net " + subnet.name + " is already registered in pair " + existingSub.baseName());
        }
      }
    }
  }

  private void indexSubnets(Net net, DifferentialPair pair) {
    if (net == null) {
      return;
    }
    this.pairsByNetNumber.put(net.netNumber, pair);
    if (net.netList != null) {
      for (Net subnet : net.netList.get(net.name)) {
        this.pairsByNetNumber.put(subnet.netNumber, pair);
      }
    }
  }

  private void removeSubnets(Net net) {
    if (net == null) {
      return;
    }
    this.pairsByNetNumber.remove(net.netNumber);
    if (net.netList != null) {
      for (Net subnet : net.netList.get(net.name)) {
        this.pairsByNetNumber.remove(subnet.netNumber);
      }
    }
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
    DifferentialPair existing = this.pairsByName.get(key);
    if (existing != null && hasSameIdentity(existing, pair)) {
      this.pairsByName.remove(key);
      removeSubnets(existing.positiveNet());
      removeSubnets(existing.negativeNet());
      return true;
    }
    return false;
  }

  /** Returns an unmodifiable snapshot collection of all registered differential pairs. */
  public synchronized Collection<DifferentialPair> all() {
    return List.copyOf(this.pairsByName.values());
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
