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
  public IdentifierType(String[] pReservedChars, String pStringQuote) {
    reservedChars = pReservedChars;
    stringQuote = pStringQuote;
  }

  /** Writes p_name after putting it into quotes, if it contains reserved characters or blanks. */
  public void write(String pName, OutputStreamWriter pFile) {
    // remove the double quotes from the identifiers
    while ((pName.length() > 2)
        && (pName.charAt(0) == '"')
        && (pName.charAt(pName.length() - 1) == '"')) {
      pName = pName.substring(1, pName.length() - 2);
    }

    try {
      // if the name contains our quote character, we must remove it
      if (pName.contains(stringQuote)) {
        pName = pName.replace(stringQuote, "");
      }

      boolean needQuotes = false;
      // if the name contains a reserved character, we must put it into quotes
      for (String reservedChar : reservedChars) {
        if (pName.contains(reservedChar)) {
          needQuotes = true;
          break;
        }
      }

      // if the name contains a non-ASCII character, we must put it into quotes
      for (byte ch : pName.getBytes(StandardCharsets.UTF_8)) {
        if (ch <= 0) {
          needQuotes = true;
          break;
        }
      }

      if (!needQuotes) {
        if (pName.matches("^-?\\d.*")) {
          needQuotes = true;
        }
      }
      if (needQuotes) {
        pName = quote(pName);
      }
      pFile.write(pName);
    } catch (IOException _) {
      FRLogger.warn("IdentifierType.write: unable to write to file");
    }
  }

  /** Looks, if p_string does not contain reserved characters or blanks. */
  private boolean isLegal(String pString) {
    if (pString == null) {
      FRLogger.warn("IdentifierType.is_legal: p_string is null");
      return false;
    }
    for (int i = 0; i < reservedChars.length; i++) {
      if (pString.contains(reservedChars[i])) {
        return false;
      }
    }
    return true;
  }

  /** Puts p_sting into quotes. */
  private String quote(String pString) {
    return stringQuote + pString + stringQuote;
  }
}
