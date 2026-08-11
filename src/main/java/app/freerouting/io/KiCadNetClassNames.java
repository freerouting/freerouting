package app.freerouting.io;

import app.freerouting.rules.BoardRules;
import app.freerouting.rules.NetClass;

/**
 * Normalizes KiCad net-class naming between Specctra DSN export and KiCad JSON/IPC export.
 *
 * <p>KiCad renames its {@code Default} net class to {@code kicad_default} in Specctra DSN files to
 * avoid colliding with Freerouting's reserved internal {@code default} class. JSON export keeps the
 * KiCad name {@code Default}.
 */
public final class KiCadNetClassNames {

  public static final String KICAD_DSN_DEFAULT = "kicad_default";

  private KiCadNetClassNames() {}

  /**
   * Returns whether a name identifies KiCad's default net class.
   *
   * @param name net-class name to inspect
   * @return {@code true} when the name is KiCad's default net-class name
   */
  public static boolean isKiCadDefaultNetClassName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    return "default".equalsIgnoreCase(name) || KICAD_DSN_DEFAULT.equalsIgnoreCase(name);
  }

  /**
   * Resolves a KiCad net-class name against the board rules.
   *
   * @param rules board rules containing the net classes
   * @param name net-class name to resolve
   * @return the matching net class, or {@code null} when no class exists
   */
  public static NetClass resolveNetClass(BoardRules rules, String name) {
    if (isKiCadDefaultNetClassName(name)) {
      return rules.getDefaultNetClass();
    }
    return rules.netClasses.get(name);
  }
}
