package app.freerouting.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/**
 * An {@link ObjectInputStream} that enforces a strict allowlist {@link ObjectInputFilter} to
 * prevent arbitrary code execution and Java deserialization gadget attacks.
 */
public class SafeObjectInputStream extends ObjectInputStream {

  private static final String ALLOWLIST_PATTERN =
      "app.freerouting.**;java.lang.**;java.util.**;java.math.**;java.awt.Point;"
          + "java.awt.Rectangle;java.awt.Color;[L*;!*";

  /**
   * Filter allowing only Freerouting domain classes, JDK standard types, primitives, and geometry.
   */
  public static final ObjectInputFilter STRICT_FILTER =
      ObjectInputFilter.Config.createFilter(ALLOWLIST_PATTERN);

  /**
   * Creates a new SafeObjectInputStream wrapping the provided stream and attaching the strict
   * deserialization filter.
   *
   * @param in the input stream
   * @throws IOException if an I/O error occurs
   */
  public SafeObjectInputStream(InputStream in) throws IOException {
    super(in);
    setObjectInputFilter(STRICT_FILTER);
  }
}
