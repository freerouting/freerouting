package app.freerouting.rules;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe registry and collection of named {@link TuningProfile} instances on a board.
 *
 * <p>Enables O(1) case-insensitive lookups by profile name while preserving declaration order.
 */
public class TuningProfiles implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Primary storage keyed by lowercase profile name to preserve insertion order. */
  private final Map<String, TuningProfile> profilesByName = new LinkedHashMap<>();

  /** Creates an empty TuningProfiles collection. */
  public TuningProfiles() {}

  /**
   * Adds or replaces a tuning profile in this registry.
   *
   * @param profile profile to register
   * @throws NullPointerException if {@code profile} is null
   */
  public synchronized void add(TuningProfile profile) {
    Objects.requireNonNull(profile, "profile cannot be null");
    String key = toKey(profile.name());
    this.profilesByName.put(key, profile);
  }

  /**
   * Retrieves a tuning profile by its name in a case-insensitive manner.
   *
   * @param name profile name
   * @return matching profile or {@code null} if not found
   */
  public synchronized TuningProfile get(String name) {
    String key = toKey(name);
    return (key != null) ? this.profilesByName.get(key) : null;
  }

  /**
   * Checks whether a profile with the given name exists in this registry.
   *
   * @param name profile name
   * @return true if registered
   */
  public synchronized boolean contains(String name) {
    String key = toKey(name);
    return (key != null) && this.profilesByName.containsKey(key);
  }

  /**
   * Returns the number of registered tuning profiles.
   *
   * @return profile count
   */
  public synchronized int count() {
    return this.profilesByName.size();
  }

  /**
   * Returns an unmodifiable view of all registered tuning profiles in declaration order.
   *
   * @return collection of tuning profiles
   */
  public synchronized Collection<TuningProfile> getAll() {
    return Collections.unmodifiableCollection(
        new java.util.ArrayList<>(this.profilesByName.values()));
  }

  /**
   * Removes a tuning profile by name.
   *
   * @param name profile name to remove
   * @return removed profile, or {@code null} if none was registered
   */
  public synchronized TuningProfile remove(String name) {
    String key = toKey(name);
    return (key != null) ? this.profilesByName.remove(key) : null;
  }

  /** Clears all registered tuning profiles. */
  public synchronized void clear() {
    this.profilesByName.clear();
  }

  private static String toKey(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
  }
}
