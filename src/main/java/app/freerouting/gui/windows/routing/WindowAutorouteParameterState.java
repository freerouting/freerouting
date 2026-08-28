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
  static final long MAX_TIMEOUT_SECONDS = 4 * 7 * 24 * 3600L; // 4 weeks (28 days = 672 hours)

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
    return stringValue.matches("^(\\d+\\.)?\\d+:\\d{2}:\\d{2}$") ? stringValue : oldValue;
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

  public enum TimeoutUnit {
    MINUTES(60L, 40_320L),
    HOURS(3600L, 672L),
    DAYS(86400L, 28L),
    WEEKS(7 * 86400L, 4L);

    private final long secondsPerUnit;
    private final long maxUnits;

    TimeoutUnit(long secondsPerUnit, long maxUnits) {
      this.secondsPerUnit = secondsPerUnit;
      this.maxUnits = maxUnits;
    }

    public long getSecondsPerUnit() {
      return secondsPerUnit;
    }

    public long getMaxUnits() {
      return maxUnits;
    }
  }

  public record DecomposedTimeout(long value, TimeoutUnit unit) {}

  public static DecomposedTimeout decomposeTimeout(long totalSeconds) {
    long clampedSeconds = Math.clamp(totalSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    if (clampedSeconds <= 0) {
      return new DecomposedTimeout(0, TimeoutUnit.MINUTES);
    }
    if (clampedSeconds % TimeoutUnit.WEEKS.getSecondsPerUnit() == 0) {
      return new DecomposedTimeout(
          clampedSeconds / TimeoutUnit.WEEKS.getSecondsPerUnit(), TimeoutUnit.WEEKS);
    }
    if (clampedSeconds % TimeoutUnit.DAYS.getSecondsPerUnit() == 0) {
      return new DecomposedTimeout(
          clampedSeconds / TimeoutUnit.DAYS.getSecondsPerUnit(), TimeoutUnit.DAYS);
    }
    if (clampedSeconds % TimeoutUnit.HOURS.getSecondsPerUnit() == 0) {
      return new DecomposedTimeout(
          clampedSeconds / TimeoutUnit.HOURS.getSecondsPerUnit(), TimeoutUnit.HOURS);
    }
    return new DecomposedTimeout(
        Math.max(1, clampedSeconds / TimeoutUnit.MINUTES.getSecondsPerUnit()), TimeoutUnit.MINUTES);
  }

  public static long toSeconds(long value, TimeoutUnit unit) {
    if (unit == null) {
      unit = TimeoutUnit.HOURS;
    }
    long clampedValue = Math.clamp(value, 0, unit.getMaxUnits());
    long totalSeconds = clampedValue * unit.getSecondsPerUnit();
    return Math.clamp(totalSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
  }

  public static String buildTimeout(long value, TimeoutUnit unit) {
    long totalSeconds = toSeconds(value, unit);
    long formattedHours = totalSeconds / 3600L;
    long remainder = totalSeconds % 3600L;
    return String.format(
        java.util.Locale.ROOT, "%02d:%02d:%02d", formattedHours, remainder / 60L, remainder % 60L);
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
    long clampedSeconds = Math.clamp(totalSeconds, DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
    long minutes = (clampedSeconds % 3600L) / 60L;

    if (clampedSeconds > 86400L) {
      long days = clampedSeconds / 86400L;
      long hours = (clampedSeconds % 86400L) / 3600L;
      return days + " days " + hours + " hours " + minutes + " minutes";
    }

    if (clampedSeconds >= 3600L) {
      long hours = clampedSeconds / 3600L;
      return hours + " hours " + minutes + " minutes";
    }

    return minutes + " minutes";
  }

  public record Timeout(long hours, long minutes, long seconds, long totalSeconds) {}
}
