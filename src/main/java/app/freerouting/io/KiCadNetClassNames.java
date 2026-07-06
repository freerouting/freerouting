package app.freerouting.io;

import app.freerouting.rules.BoardRules;
import app.freerouting.rules.NetClass;

/**
 * Normalizes KiCad net-class naming between Specctra DSN export and KiCad JSON/IPC export.
 * <p>
 * KiCad renames its {@code Default} net class to {@code kicad_default} in Specctra DSN files to
 * avoid colliding with Freerouting's reserved internal {@code default} class. JSON export keeps the
 * KiCad name {@code Default}.
 */
public final class KiCadNetClassNames {

  public static final String KICAD_DSN_DEFAULT = "kicad_default";

  private KiCadNetClassNames() {
  }

  public static boolean isKiCadDefaultNetClassName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    return "default".equalsIgnoreCase(name) || KICAD_DSN_DEFAULT.equalsIgnoreCase(name);
  }

  public static NetClass resolveNetClass(BoardRules rules, String name) {
    if (isKiCadDefaultNetClassName(name)) {
      return rules.get_default_net_class();
    }
    return rules.net_classes.get(name);
  }
}
