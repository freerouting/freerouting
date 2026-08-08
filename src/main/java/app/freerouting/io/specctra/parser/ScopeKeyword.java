package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Keywords defining a scope object */
public class ScopeKeyword extends Keyword {

  public ScopeKeyword(String pName) {
    super(pName);
  }

  /**
   * Skips the current scope while reading a dsn file. Returns false, if no legal scope was found.
   */
  public static boolean skipScope(IJFlexScanner pScanner) {
    int openBrackedCount = 1;
    while (openBrackedCount > 0) {
      pScanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object currToken;
      try {
        currToken = pScanner.nextToken();
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
  public boolean readScope(ReadScopeParameter pPar) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
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
          if (!nextScope.readScope(pPar)) {
            return false;
          }
        } else {
          // skip unknown scope
          skipScope(pPar.scanner);
        }
      }
    }
    return true;
  }
}
