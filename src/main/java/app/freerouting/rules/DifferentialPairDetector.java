package app.freerouting.rules;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for parsing and identifying differential signal pair naming conventions.
 *
 * <p>Supports KiCad standard styles ({@code +}/{@code -} and {@code _P}/{@code _N}), hierarchical
 * paths, and guards against false positives on power and ground rails.
 */
public final class DifferentialPairDetector {

  private static final Pattern POWER_RAIL_PATTERN =
      Pattern.compile("^[+-]?\\d+(\\.\\d+)?[vV]?$", Pattern.CASE_INSENSITIVE);

  private DifferentialPairDetector() {}

  /** Logical polarity of a differential net. */
  public enum Polarity {
    POSITIVE,
    NEGATIVE
  }

  /** Style of differential pair naming suffix. */
  public enum SuffixStyle {
    PLUS_MINUS,
    UNDERSCORE_P_N,
    DOT_P_N,
    UNDERSCORE_POS_NEG
  }

  /**
   * Result of analyzing a net name for differential pair suffix patterns.
   *
   * @param baseName the common logical base name without suffix
   * @param polarity positive or negative
   * @param style the matched suffix style
   */
  public record ExtractedNetInfo(String baseName, Polarity polarity, SuffixStyle style) {}

  /**
   * Checks whether the given net name is a power, ground, or non-signal net that should be excluded
   * from differential pair auto-detection.
   *
   * @param netName the net name to evaluate
   * @return true if the net should be excluded
   */
  public static boolean isExcludedNet(String netName) {
    if (netName == null || netName.isBlank()) {
      return true;
    }

    String trimmed = netName.trim();
    String upper = trimmed.toUpperCase(Locale.ROOT);

    // Strip optional leading hierarchical slashes for name inspection
    int lastSlash = upper.lastIndexOf('/');
    String localName = (lastSlash >= 0) ? upper.substring(lastSlash + 1) : upper;

    // Direct power rail names and voltages (e.g. +5V, -12V, 3V3, 5V)
    if (POWER_RAIL_PATTERN.matcher(localName).matches()) {
      return true;
    }

    // Common power/ground identifiers
    if (localName.equals("GND")
        || localName.equals("GROUND")
        || localName.startsWith("GND_")
        || localName.startsWith("GND-")
        || localName.endsWith("_GND")
        || localName.equals("VCC")
        || localName.startsWith("VCC_")
        || localName.equals("VDD")
        || localName.startsWith("VDD_")
        || localName.equals("VSS")
        || localName.startsWith("VSS_")
        || localName.equals("VBUS")
        || localName.startsWith("VBUS_")
        || localName.equals("VBAT")
        || localName.startsWith("VBAT_")
        || localName.startsWith("VIN")
        || localName.startsWith("VOUT")
        || localName.equals("PWR")
        || localName.equals("POWER")) {
      return true;
    }

    return false;
  }

  /**
   * Attempts to extract differential pair polarity and base name from the given net name.
   *
   * @param netName candidate net name
   * @return ExtractedNetInfo if a recognized differential suffix is matched, or null otherwise
   */
  public static ExtractedNetInfo extract(String netName) {
    if (netName == null || netName.isBlank() || isExcludedNet(netName)) {
      return null;
    }

    String trimmed = netName.trim();

    // Style 1: Plus / Minus (+ / -)
    if (trimmed.endsWith("+")) {
      String base = trimmed.substring(0, trimmed.length() - 1);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.POSITIVE, SuffixStyle.PLUS_MINUS);
      }
    } else if (trimmed.endsWith("-")) {
      String base = trimmed.substring(0, trimmed.length() - 1);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.NEGATIVE, SuffixStyle.PLUS_MINUS);
      }
    }

    String upper = trimmed.toUpperCase(Locale.ROOT);

    // Style 4: _POS / _NEG
    if (upper.endsWith("_POS")) {
      String base = trimmed.substring(0, trimmed.length() - 4);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.POSITIVE, SuffixStyle.UNDERSCORE_POS_NEG);
      }
    } else if (upper.endsWith("_NEG")) {
      String base = trimmed.substring(0, trimmed.length() - 4);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.NEGATIVE, SuffixStyle.UNDERSCORE_POS_NEG);
      }
    }

    // Style 3: .P / .N
    if (upper.endsWith(".P")) {
      String base = trimmed.substring(0, trimmed.length() - 2);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.POSITIVE, SuffixStyle.DOT_P_N);
      }
    } else if (upper.endsWith(".N")) {
      String base = trimmed.substring(0, trimmed.length() - 2);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.NEGATIVE, SuffixStyle.DOT_P_N);
      }
    }

    // Style 2: _P / _N
    if (upper.endsWith("_P")) {
      String base = trimmed.substring(0, trimmed.length() - 2);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.POSITIVE, SuffixStyle.UNDERSCORE_P_N);
      }
    } else if (upper.endsWith("_N")) {
      String base = trimmed.substring(0, trimmed.length() - 2);
      if (!base.isEmpty() && !isExcludedNet(base)) {
        return new ExtractedNetInfo(base, Polarity.NEGATIVE, SuffixStyle.UNDERSCORE_P_N);
      }
    }

    return null;
  }
}
