package app.freerouting.rules;

import java.io.Serializable;

/** Type of length-tuning pattern supported for trace length matching. */
public enum MeanderPatternType implements Serializable {
  SINGLE_TRACK,
  DIFF_PAIR,
  DIFF_PAIR_SKEW;

  /**
   * Parses a meander pattern type from a string in a case-insensitive manner.
   *
   * @param name string name of the pattern type
   * @return matching MeanderPatternType, or SINGLE_TRACK by default if null or unrecognised
   */
  public static MeanderPatternType parse(String name) {
    if (name == null || name.isBlank()) {
      return SINGLE_TRACK;
    }
    String normalized = name.trim().toUpperCase().replace('-', '_');
    return switch (normalized) {
      case "DIFF_PAIR", "DIFFPAIR", "DIFFERENTIAL_PAIR", "DIFFERENTIAL" -> DIFF_PAIR;
      case "DIFF_PAIR_SKEW", "DIFFPAIR_SKEW", "SKEW", "DIFFERENTIAL_PAIR_SKEW" -> DIFF_PAIR_SKEW;
      default -> SINGLE_TRACK;
    };
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }
}
