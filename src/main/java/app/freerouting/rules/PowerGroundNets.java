package app.freerouting.rules;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Single source of truth for classifying PCB net names as power supply rails, ground/return nets,
 * or non-power signal nets across Freerouting.
 *
 * <p>Used by the routing engine, decoupling capacitor detector/router, and differential pair
 * auto-detection to prevent split-brain classifier drift.
 */
public final class PowerGroundNets {

  private static final Pattern GROUND_NET_PATTERN =
      Pattern.compile(
          "(?i)^(?:.*/)?"
              + "(?:(?:PWR_)?GND[0-9_\\-a-z]*"
              + "|GROUND(?:[0-9_\\-].*)?"
              + "|[ADPSECIO]GND[0-9_\\-a-z]*"
              + "|VSS[0-9_\\-a-z]*"
              + "|[ADP]VSS[0-9_\\-a-z]*"
              + "|0V[0-9_\\-a-z]*|V-?0"
              + "|EARTH(?:[0-9_\\-].*)?"
              + "|(?:V-)?PE(?:[0-9_\\-].*)?|PROTECTIVE_EARTH(?:[0-9_\\-].*)?"
              + "|CHASSIS(?:[0-9_\\-].*)?"
              + "|V-GND"
              + ")$");

  private static final Pattern POWER_NET_EXCLUDE_PATTERN =
      Pattern.compile(
          "(?i).*(?:PWR_FLAG|VSW|LINE|NEUT|V-[LN]|PRI_"
              + "|_(?:EN|PG|GOOD|DET|SENSE|FAULT|UVLO|FB|STATUS|OK|ALERT|FLAG)$).*");

  private static final Pattern POWER_NET_PATTERN =
      Pattern.compile(
          "(?i)^(?:.*/)?(?:(?:PWR|POWER)[._\\-])?"
              + "(?:"
              + "[+-]\\d+[cfpCFP]"
              + "|[+-]?(?:\\d+[vV][a-z0-9]*|\\d+\\.\\d+[vV][a-z0-9]*)"
              + "|VS?[+-]{1,2}"
              + "|V-[+-]"
              + "|(?:[+-]?BATT?(?:ERY)?|VBAT)[0-9_\\-+a-z]*"
              + "|[+-]?(?:VDC|VEE|VAA|VAC|VPP|VMEM|VCOM|VREF|VBUS|VDRIVE)[0-9_\\-a-z]*"
              + "|[+-]?[ADPV]?VCC[0-9_\\-a-z]*"
              + "|[+-]?[ADPV]?VDD[0-9_\\-a-z]*"
              + "|[+-]?(?:VIN|VOUT)[0-9_\\-a-z]*"
              + "|[+-]?(?:CORE|POWER|PWR)[0-9_\\-a-z]*"
              + "|HT|VD|VS"
              + ")$");

  private PowerGroundNets() {}

  /**
   * Returns true if the given net name conforms to standard EDA ground or return net naming
   * conventions (e.g. {@code GND}, {@code AGND}, {@code VSS}, {@code 0V}, {@code Earth}, {@code
   * PE}, {@code CHASSIS}).
   *
   * @param netName candidate net name
   * @return true if classified as a ground/return net, false if null, blank, or non-ground
   */
  public static boolean isGroundNetName(String netName) {
    return isGroundNetName(netName, (Collection<String>) null);
  }

  /**
   * Returns true if the net name matches standard ground naming conventions or any user-specified
   * additional ground net names.
   *
   * @param netName candidate net name
   * @param additionalGroundNets user-configured ground net names or overrides
   * @return true if classified as ground
   */
  public static boolean isGroundNetName(String netName, Collection<String> additionalGroundNets) {
    if (netName == null || netName.isBlank()) {
      return false;
    }
    String trimmed = netName.trim();
    if (additionalGroundNets != null && matchesCollection(trimmed, additionalGroundNets)) {
      return true;
    }
    return GROUND_NET_PATTERN.matcher(trimmed).matches();
  }

  /**
   * Returns true if the net name matches standard ground naming conventions or any user-specified
   * additional ground net names.
   *
   * @param netName candidate net name
   * @param additionalGroundNets user-configured ground net names or overrides
   * @return true if classified as ground
   */
  public static boolean isGroundNetName(String netName, String[] additionalGroundNets) {
    return isGroundNetName(
        netName, additionalGroundNets != null ? Arrays.asList(additionalGroundNets) : null);
  }

  /**
   * Returns true if the given net name conforms to standard EDA DC power rail naming conventions
   * (e.g. {@code VCC}, {@code VDD}, {@code +3V3}, {@code 1V8}, {@code +2V8}, {@code +5V}, {@code
   * VBAT}, {@code VBUS}, {@code V+}).
   *
   * <p>Intentionally returns {@code false} for ground/return nets and AC/switching nodes.
   *
   * @param netName candidate net name
   * @return true if classified as a DC power supply rail
   */
  public static boolean isPowerNetName(String netName) {
    return isPowerNetName(netName, (Collection<String>) null);
  }

  /**
   * Returns true if the net name matches standard power rail conventions or any user-specified
   * additional power net names.
   *
   * @param netName candidate net name
   * @param additionalPowerNets user-configured power rail names or overrides
   * @return true if classified as power rail
   */
  public static boolean isPowerNetName(String netName, Collection<String> additionalPowerNets) {
    if (netName == null || netName.isBlank()) {
      return false;
    }
    String trimmed = netName.trim();
    if (additionalPowerNets != null && matchesCollection(trimmed, additionalPowerNets)) {
      return true;
    }
    if (isGroundNetName(trimmed, (Collection<String>) null)
        || POWER_NET_EXCLUDE_PATTERN.matcher(trimmed).matches()) {
      return false;
    }
    return POWER_NET_PATTERN.matcher(trimmed).matches();
  }

  /**
   * Returns true if the net name matches standard power rail conventions or any user-specified
   * additional power net names.
   *
   * @param netName candidate net name
   * @param additionalPowerNets user-configured power rail names or overrides
   * @return true if classified as power rail
   */
  public static boolean isPowerNetName(String netName, String[] additionalPowerNets) {
    return isPowerNetName(
        netName, additionalPowerNets != null ? Arrays.asList(additionalPowerNets) : null);
  }

  /**
   * Returns true if the given net name is either a power supply rail or a ground/return net.
   *
   * @param netName candidate net name
   * @return true if classified as power or ground
   */
  public static boolean isSupplyOrGroundNet(String netName) {
    return isSupplyOrGroundNet(netName, (Collection<String>) null, null);
  }

  /**
   * Returns true if the given net name is either a power rail or a ground net, taking into account
   * user-configured additional names.
   *
   * @param netName candidate net name
   * @param additionalPowerNets user-configured power rail names
   * @param additionalGroundNets user-configured ground net names
   * @return true if classified as power or ground
   */
  public static boolean isSupplyOrGroundNet(
      String netName,
      Collection<String> additionalPowerNets,
      Collection<String> additionalGroundNets) {
    if (netName == null || netName.isBlank()) {
      return false;
    }
    return isGroundNetName(netName, additionalGroundNets)
        || isPowerNetName(netName, additionalPowerNets);
  }

  /**
   * Returns true if the given net name is either a power rail or a ground net, taking into account
   * user-configured additional names.
   *
   * @param netName candidate net name
   * @param additionalPowerNets user-configured power rail names
   * @param additionalGroundNets user-configured ground net names
   * @return true if classified as power or ground
   */
  public static boolean isSupplyOrGroundNet(
      String netName, String[] additionalPowerNets, String[] additionalGroundNets) {
    return isSupplyOrGroundNet(
        netName,
        additionalPowerNets != null ? Arrays.asList(additionalPowerNets) : null,
        additionalGroundNets != null ? Arrays.asList(additionalGroundNets) : null);
  }

  private static boolean matchesCollection(String name, Collection<String> collection) {
    int lastSlash = name.lastIndexOf('/');
    String localName = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
    for (String item : collection) {
      if (item != null) {
        String trimmedItem = item.trim();
        if (trimmedItem.equalsIgnoreCase(name) || trimmedItem.equalsIgnoreCase(localName)) {
          return true;
        }
      }
    }
    return false;
  }
}
