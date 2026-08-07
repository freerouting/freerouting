package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Keywords defining a scope object */
public class ScopeKeyword extends Keyword {

  public ScopeKeyword(String p_name) {
    super(p_name);
  }

  /**
   * Skips the current scope while reading a dsn file. Returns false, if no legal scope was found.
   */
  public static boolean skip_scope(IJFlexScanner p_scanner) {
    int openBrackedCount = 1;
    while (openBrackedCount > 0) {
      p_scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object currToken;
      try {
        currToken = p_scanner.next_token();
      } catch (Exception e) {
        FRLogger.error("ScopeKeyword.skip_scope: Error while scanning file", e);
        return false;
      }
      if (currToken == null) {
        return false; // end of file
      }
      if (currToken == Keyword.OPEN_BRACKET) {
        ++openBrackedCount;
      } else if (currToken == Keyword.CLOSED_BRACKET) {
        --openBrackedCount;
      }
    }
    return true;
  }

  /** Reads the next scope of this keyword from dsn file. */
  public boolean read_scope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("ScopeKeyword.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        // end of file
        return true;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == OPEN_BRACKET) {
        ScopeKeyword nextScope;
        // a new scope is expected
        if (nextToken instanceof ScopeKeyword keyword) {
          // read the next scope, which is the "structure" part of the DSN file
          nextScope = keyword;
          if (!nextScope.read_scope(p_par)) {
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
