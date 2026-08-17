package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Describes legal identifiers together with the character used for string quotes. */
public class IdentifierType {

  private final String stringQuote;
  private final String[] reservedChars;

  /**
   * Defines the reserved characters and the string for quoting identifiers containing reserved
   * characters for a new instance of Identifier.
   */
  public IdentifierType(String[] reservedChars, String stringQuote) {
    this.reservedChars = reservedChars;
    this.stringQuote = stringQuote;
  }

  /** Writes name after putting it into quotes, if it contains reserved characters or blanks. */
  public void write(String name, OutputStreamWriter file) {
    // remove the double quotes from the identifiers
    while ((name.length() > 2)
        && (name.charAt(0) == '"')
        && (name.charAt(name.length() - 1) == '"')) {
      name = name.substring(1, name.length() - 2);
    }

    try {
      // if the name contains our quote character, we must remove it
      if (name.contains(stringQuote)) {
        name = name.replace(stringQuote, "");
      }

      boolean needQuotes = false;
      // if the name contains a reserved character, we must put it into quotes
      for (String reservedChar : reservedChars) {
        if (name.contains(reservedChar)) {
          needQuotes = true;
          break;
        }
      }

      // if the name contains a non-ASCII character, we must put it into quotes
      for (byte ch : name.getBytes(StandardCharsets.UTF_8)) {
        if (ch <= 0) {
          needQuotes = true;
          break;
        }
      }

      if (!needQuotes) {
        if (name.matches("^-?\\d.*")) {
          needQuotes = true;
        }
      }
      if (needQuotes) {
        name = quote(name);
      }
      file.write(name);
    } catch (IOException _) {
      FRLogger.warn("IdentifierType.write: unable to write to file");
    }
  }

  /** Looks, if string does not contain reserved characters or blanks. */
  private boolean isLegal(String string) {
    if (string == null) {
      FRLogger.warn("IdentifierType.is_legal: string is null");
      return false;
    }
    for (int i = 0; i < reservedChars.length; i++) {
      if (string.contains(reservedChars[i])) {
        return false;
      }
    }
    return true;
  }

  /** Puts string into quotes. */
  private String quote(String string) {
    return stringQuote + string + stringQuote;
  }
}
