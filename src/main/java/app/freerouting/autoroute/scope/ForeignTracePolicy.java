package app.freerouting.autoroute.scope;

import java.util.Locale;

/** Policy governing how unselected (foreign) non-fixed copper is treated during scoped routing. */
public enum ForeignTracePolicy {
  /**
   * Tier 1: Lossless push-and-shove nudging in early passes (1..K). Tier 2: High-barrier
   * conditional rip-up with mandatory same-pass recovery in later passes (> K).
   */
  NUDGE_THEN_CONDITIONAL_RIPUP,

  /**
   * Lossless push-and-shove nudging only across all passes. Maze rip-up of foreign items is
   * prohibited.
   */
  NUDGE_ONLY,

  /** Strict fixed: foreign items cannot be shoved or ripped up. */
  FIXED;

  /**
   * Parses a string into a {@link ForeignTracePolicy}, defaulting to {@link
   * #NUDGE_THEN_CONDITIONAL_RIPUP}.
   */
  public static ForeignTracePolicy of(String value) {
    if (value == null || value.trim().isEmpty()) {
      return NUDGE_THEN_CONDITIONAL_RIPUP;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "NUDGE_ONLY", "NUDGE" -> NUDGE_ONLY;
      case "FIXED", "STRICT_FIXED" -> FIXED;
      default -> NUDGE_THEN_CONDITIONAL_RIPUP;
    };
  }
}
