package app.freerouting.autoroute.scope;

import java.util.Locale;

/** Policy governing 3+ pin signal nets that span both selected and unselected components. */
public enum MultiPinScopePolicy {
  /**
   * Routes from selected anchor pins to the nearest valid electrical target on the net (existing
   * foreign copper trunk or nearest external pin) and terminates once all anchor pins are
   * satisfied, without bridging unrelated unselected-to-unselected gaps.
   */
  ANCHOR_TO_NEAREST,

  /**
   * Routes the entire net to full completion, bridging all pins (anchor and external) on the net.
   */
  FULL_NET,

  /**
   * Only routes selected anchor pins to each other. External pins are excluded from target doors.
   */
  MUTUAL_ONLY;

  /**
   * Parses a string into a {@link MultiPinScopePolicy}, defaulting to {@link #ANCHOR_TO_NEAREST}.
   */
  public static MultiPinScopePolicy of(String value) {
    if (value == null || value.trim().isEmpty()) {
      return ANCHOR_TO_NEAREST;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "FULL_NET", "FULL" -> FULL_NET;
      case "MUTUAL_ONLY", "MUTUAL" -> MUTUAL_ONLY;
      default -> ANCHOR_TO_NEAREST;
    };
  }
}
