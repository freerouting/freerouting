package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Describes legal identifiers together with the character used for string quotes. */
public class IdentifierType {
  private final String string_quote;
  private final String[] reserved_chars;

  /**
   * Defines the reserved characters and the string for quoting identifiers containing reserved
   * characters for a new instance of Identifier.
   */
  public IdentifierType(String[] p_reserved_chars, String p_string_quote) {
    reserved_chars = p_reserved_chars;
    string_quote = p_string_quote;
  }

  /** Writes p_name after putting it into quotes, if it contains reserved characters or blanks. */
  public void write(String p_name, OutputStreamWriter p_file) {
    // remove the double quotes from the identifiers
    while ((p_name.length() > 2)
        && (p_name.charAt(0) == '"')
        && (p_name.charAt(p_name.length() - 1) == '"')) {
      p_name = p_name.substring(1, p_name.length() - 2);
    }

    try {

      // if the name contains our quote character, we must remove it
      if (p_name.contains(string_quote))
      {
        p_name = p_name.replace(string_quote, "");
      }

      boolean need_quotes = false;
      // if the name contains a reserved character, we must put it into quotes
      for (String reserved_char : reserved_chars) {
        if (p_name.contains(reserved_char)) {
          need_quotes = true;
        }
      }

      // if the name contains a non-ASCII character, we must put it into quotes
      for (byte ch : p_name.getBytes(StandardCharsets.UTF_8)) {
        if (ch <= 0) {
          need_quotes = true;
        }
      }

      if (need_quotes)
      {
        p_name = quote(p_name);
      }

      // always put quotes around the identifiers even if they don't have illegal characters
      p_file.write(p_name);
    } catch (java.io.IOException e) {
      FRLogger.warn("IdentifierType.write: unable to write to file");
    }
  }

  /** Looks, if p_string does not contain reserved characters or blanks. */
  private boolean is_legal(String p_string) {
    if (p_string == null) {
      FRLogger.warn("IdentifierType.is_legal: p_string is null");
      return false;
    }
    for (int i = 0; i < reserved_chars.length; ++i) {
      if (p_string.contains(reserved_chars[i])) {
        return false;
      }
    }
    return true;
  }

  /** Puts p_sting into quotes. */
  private String quote(String p_string) {
    return string_quote + p_string + string_quote;
  }
}
