package app.freerouting.designforms.specctra;

import app.freerouting.logger.FRLogger;

/** Keywords defining a scope object */
public class ScopeKeyword extends Keyword {
  public ScopeKeyword(String p_name) {
    super(p_name);
  }

  /**
   * Scips the current scope while reading a dsn file. Returns false, if no legal scope was found.
   */
  public static boolean skip_scope(IJFlexScanner p_scanner) {
    int open_bracked_count = 1;
    while (open_bracked_count > 0) {
      p_scanner.yybegin(SpecctraDsnFileReader.NAME);
      Object curr_token = null;
      try {
        curr_token = p_scanner.next_token();
      } catch (Exception e) {
        FRLogger.error("ScopeKeyword.skip_scope: Error while scanning file", e);
        return false;
      }
      if (curr_token == null) {
        return false; // end of file
      }
      if (curr_token == Keyword.OPEN_BRACKET) {
        ++open_bracked_count;
      } else if (curr_token == Keyword.CLOSED_BRACKET) {
        --open_bracked_count;
      }
    }
    return true;
  }

  /** Reads the next scope of this keyword from dsn file. */
  public boolean read_scope(ReadScopeParameter p_par) {
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("ScopeKeyword.read_scope: IO error scanning file", e);
        return false;
      }
      if (next_token == null) {
        // end of file
        return true;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prev_token == OPEN_BRACKET) {
        ScopeKeyword next_scope;
        // a new scope is expected
        if (next_token instanceof ScopeKeyword) {
          next_scope = (ScopeKeyword) next_token;
          if (!next_scope.read_scope(p_par)) {
            return false;
          }

        } else {
          // skip unknown scope
          skip_scope(p_par.scanner);
        }
      }
    }
    return true;
  }
}
