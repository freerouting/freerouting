package app.freerouting.rules;

import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility for parsing and identifying differential signal pair naming conventions.
 *
 * <p>Supports KiCad standard styles ({@code +}/{@code -} and {@code _P}/{@code _N}), hierarchical
 * paths, and guards against false positives on power and ground rails.
 */
public final class DifferentialPairDetector {

  private static final Pattern POWER_RAIL_PATTERN =
      Pattern.compile("^[+-]?\\d+([.vV]\\d*)?[vV]?$", Pattern.CASE_INSENSITIVE);

  private DifferentialPairDetector() {}

  /** Logical polarity of a differential net. */
  public enum Polarity {
    POSITIVE,
    NEGATIVE
  }

  /** Style of differential pair naming suffix. */
  public enum SuffixStyle {
    P_N,
    DP_DM,
    D_PLUS_MINUS,
    PLUS_MINUS,
    POS_NEG,
    BARE_D_PLUS_MINUS
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

    // Direct power rail names and voltages (e.g. +5V, -12V, 3V3, 1V8, 5V)
    if (POWER_RAIL_PATTERN.matcher(localName).matches()) {
      return true;
    }

    // Common power/ground identifiers
    if (localName.equals("GND")
        || localName.equals("GROUND")
        || localName.startsWith("GND_")
        || localName.startsWith("GND-")
        || localName.endsWith("GND")
        || localName.equals("VCC")
        || localName.startsWith("VCC_")
        || localName.endsWith("VCC")
        || localName.equals("VDD")
        || localName.startsWith("VDD_")
        || localName.endsWith("VDD")
        || localName.equals("VSS")
        || localName.startsWith("VSS_")
        || localName.endsWith("VSS")
        || localName.equals("VBUS")
        || localName.startsWith("VBUS_")
        || localName.equals("VBAT")
        || localName.startsWith("VBAT_")
        || localName.startsWith("BAT")
        || localName.equals("V+")
        || localName.equals("V-")
        || localName.equals("VS+")
        || localName.equals("VS-")
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
    String upper = trimmed.toUpperCase(Locale.ROOT);

    for (SuffixTokenPattern pattern : DELIMITED_PATTERNS) {
      ExtractedNetInfo info =
          tryExtractDelimited(trimmed, upper, pattern.posToken, pattern.negToken, pattern.style);
      if (info != null) {
        return info;
      }
    }

    // Scoped bare D+ / D- exception for USB legacy/standard nets
    int lastSlash = upper.lastIndexOf('/');
    String localUpper = (lastSlash >= 0) ? upper.substring(lastSlash + 1) : upper;
    if (localUpper.equals("D+")) {
      String base = trimmed.substring(0, trimmed.length() - 1);
      return new ExtractedNetInfo(base, Polarity.POSITIVE, SuffixStyle.BARE_D_PLUS_MINUS);
    } else if (localUpper.equals("D-")) {
      String base = trimmed.substring(0, trimmed.length() - 1);
      return new ExtractedNetInfo(base, Polarity.NEGATIVE, SuffixStyle.BARE_D_PLUS_MINUS);
    }

    return null;
  }

  private record SuffixTokenPattern(String posToken, String negToken, SuffixStyle style) {}

  private static final SuffixTokenPattern[] DELIMITED_PATTERNS = {
    new SuffixTokenPattern("POS", "NEG", SuffixStyle.POS_NEG),
    new SuffixTokenPattern("DP", "DM", SuffixStyle.DP_DM),
    new SuffixTokenPattern("D+", "D-", SuffixStyle.D_PLUS_MINUS),
    new SuffixTokenPattern("P", "N", SuffixStyle.P_N),
    new SuffixTokenPattern("+", "-", SuffixStyle.PLUS_MINUS)
  };

  private static ExtractedNetInfo tryExtractDelimited(
      String trimmed, String upper, String posToken, String negToken, SuffixStyle style) {
    Polarity polarity;
    int tokenLen;
    if (upper.endsWith(posToken)) {
      polarity = Polarity.POSITIVE;
      tokenLen = posToken.length();
    } else if (upper.endsWith(negToken)) {
      polarity = Polarity.NEGATIVE;
      tokenLen = negToken.length();
    } else {
      return null;
    }

    int delimIndex = trimmed.length() - tokenLen - 1;
    if (delimIndex >= 0) {
      char delim = trimmed.charAt(delimIndex);
      if (delim == '_' || delim == '.') {
        String base = trimmed.substring(0, delimIndex);
        if (!base.isEmpty() && !isExcludedNet(base)) {
          return new ExtractedNetInfo(base, polarity, style);
        }
      }
    }
    return null;
  }

  private record CandidateKey(String baseNameKey, SuffixStyle style) {}

  private static final class CandidateEntry {
    private final String baseName;
    private Net positiveNet;
    private Net negativeNet;
    private boolean ambiguous;

    CandidateEntry(String baseName) {
      this.baseName = baseName;
    }
  }

  /**
   * Scans the nets on the board, auto-detects complementary differential pairs based on standard
   * naming conventions, and returns the detected pairs without modifying the board rules.
   *
   * @param boardRules the board rules containing the netlist
   * @return unmodifiable list of detected DifferentialPair instances
   */
  public static List<DifferentialPair> detect(BoardRules boardRules) {
    if (boardRules == null || boardRules.nets == null || boardRules.differentialPairs == null) {
      return Collections.emptyList();
    }

    Map<CandidateKey, CandidateEntry> candidates = new LinkedHashMap<>();
    int maxNetNumber = boardRules.nets.maxNetNumber();

    for (int i = 1; i <= maxNetNumber; i++) {
      Net net = boardRules.nets.get(i);
      if (net == null || !Nets.isNormalNetNumber(net.netNumber)) {
        continue;
      }
      if (boardRules.differentialPairs.findForNet(net) != null) {
        continue;
      }

      ExtractedNetInfo info = extract(net.name);
      if (info == null) {
        continue;
      }
      if (boardRules.differentialPairs.get(info.baseName()) != null) {
        continue;
      }

      CandidateKey key = new CandidateKey(info.baseName().toLowerCase(Locale.ROOT), info.style());
      CandidateEntry entry =
          candidates.computeIfAbsent(key, k -> new CandidateEntry(info.baseName()));

      if (info.polarity() == Polarity.POSITIVE) {
        if (entry.positiveNet != null) {
          if (!entry.positiveNet.name.equalsIgnoreCase(net.name)
              || entry.positiveNet.subnetNumber == net.subnetNumber) {
            entry.ambiguous = true;
          }
        } else {
          entry.positiveNet = net;
        }
      } else {
        if (entry.negativeNet != null) {
          if (!entry.negativeNet.name.equalsIgnoreCase(net.name)
              || entry.negativeNet.subnetNumber == net.subnetNumber) {
            entry.ambiguous = true;
          }
        } else {
          entry.negativeNet = net;
        }
      }
    }

    List<DifferentialPair> result = new ArrayList<>();
    for (CandidateEntry entry : candidates.values()) {
      if (!entry.ambiguous && entry.positiveNet != null && entry.negativeNet != null) {
        if (boardRules.differentialPairs.findForNet(entry.positiveNet) == null
            && boardRules.differentialPairs.findForNet(entry.negativeNet) == null
            && boardRules.differentialPairs.get(entry.baseName) == null) {
          result.add(new DifferentialPair(entry.positiveNet, entry.negativeNet, entry.baseName));
        }
      }
    }

    return Collections.unmodifiableList(result);
  }

  /**
   * Scans the nets on the board, auto-detects differential pairs based on standard naming
   * conventions, and registers any newly detected pairs into the board rules.
   *
   * @param boardRules the board rules containing nets and the differential pair registry
   * @return unmodifiable list of newly registered differential pairs
   */
  public static List<DifferentialPair> detectAndRegister(BoardRules boardRules) {
    if (boardRules == null || boardRules.differentialPairs == null) {
      return Collections.emptyList();
    }

    List<DifferentialPair> detected = detect(boardRules);
    for (DifferentialPair pair : detected) {
      boardRules.differentialPairs.add(pair);
      FRLogger.info(
          "Auto-detected differential pair: "
              + pair.baseName()
              + " ("
              + pair.positiveNet().name
              + ", "
              + pair.negativeNet().name
              + ")");
    }
    return detected;
  }
}
