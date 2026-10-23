package app.freerouting.rules;

import java.io.Serializable;
import java.util.Locale;

/**
 * Defines the routing target topology type for a tuning profile (single-ended vs. differential
 * pair).
 */
public enum TuningProfileType implements Serializable {
  SINGLE,
  DIFFERENTIAL;

  /**
   * Parses a string into a {@link TuningProfileType} in a case-insensitive manner.
   *
   * @param text string to parse
   * @return matching type or {@code SINGLE} if unrecognized/null
   */
  public static TuningProfileType parse(String text) {
    if (text == null || text.isBlank()) {
      return SINGLE;
    }
    String normalized = text.trim().toUpperCase(Locale.ROOT);
    if ("DIFFERENTIAL".equals(normalized) || "DIFF_PAIR".equals(normalized)) {
      return DIFFERENTIAL;
    }
    return SINGLE;
  }
}
