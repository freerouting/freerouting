package app.freerouting.gui.windows.routing;

import app.freerouting.util.TextManager;

/**
 * Pure dialog-state rules for {@link WindowAutorouteParameter}.
 *
 * <p>The Swing window remains responsible for controls and EDT listeners. Keeping normalization and
 * timeout conversion here makes those rules independently characterizable without constructing a
 * frame or accessing a board.
 */
public class WindowAutorouteParameterState {

  static final long DEFAULT_TIMEOUT_SECONDS = 0L;
  static final long MAX_TIMEOUT_SECONDS = 86400L;

  private WindowAutorouteParameterState() {}

  public static int normalizeIntInput(Object input, int oldValue, int minValue, int maxValue) {
    if (!(input instanceof Number number)) {
      return oldValue;
    }
    return Math.clamp(number.intValue(), minValue, maxValue);
  }

  public static double normalizePositiveDoubleInput(Object input, double oldValue) {
    if (!(input instanceof Number number)) {
      return oldValue;
    }
    double parsedValue = number.doubleValue();
    return parsedValue > 0 ? parsedValue : oldValue;
  }

  public static String normalizeTimeoutInput(Object input, String oldValue) {
    if (!(input instanceof String stringValue)) {
      return oldValue;
    }
    return stringValue.matches("^(\\d+\\.)?\\d{1,2}:\\d{2}:\\d{2}$") ? stringValue : oldValue;
  }

  public static Timeout parseTimeout(String timeoutString) {
    Long parsedSeconds =
        timeoutString == null ? null : TextManager.parseTimespanString(timeoutString);
    long totalSeconds = parsedSeconds == null ? DEFAULT_TIMEOUT_SECONDS : parsedSeconds;
    totalSeconds = Math.clamp(totalSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    long hours = totalSeconds / 3600L;
    long remainingSeconds = totalSeconds % 3600L;
    return new Timeout(hours, remainingSeconds / 60L, remainingSeconds % 60L, totalSeconds);
  }

  public static String buildTimeout(long hours, long minutes, long seconds) {
    long totalSeconds = (hours * 3600L) + (minutes * 60L) + seconds;
    totalSeconds = Math.clamp(totalSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    long formattedHours = totalSeconds / 3600L;
    long remainder = totalSeconds % 3600L;
    return String.format(
        java.util.Locale.ROOT, "%02d:%02d:%02d", formattedHours, remainder / 60L, remainder % 60L);
  }

  public static String formatTimeout(long totalSeconds) {
    Timeout timeout = parseTimeout(Long.toString(totalSeconds));
    StringBuilder summary = new StringBuilder();
    appendTimeoutUnit(summary, timeout.hours(), "h");
    appendTimeoutUnit(summary, timeout.minutes(), "m");
    appendTimeoutUnit(summary, timeout.seconds(), "s");
    return summary.isEmpty() ? "0s" : summary.toString();
  }

  private static void appendTimeoutUnit(StringBuilder summary, long value, String unit) {
    if (value <= 0) {
      return;
    }
    if (!summary.isEmpty()) {
      summary.append(' ');
    }
    summary.append(value).append(unit);
  }

  public record Timeout(long hours, long minutes, long seconds, long totalSeconds) {}
}
