package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Keywords defining a scope object. */
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public class ScopeKeyword extends Keyword {

  public ScopeKeyword(String name) {
    super(name);
  }

  /**
   * Skips the current scope while reading a dsn file. Returns false, if no legal scope was found.
   */
  public static boolean skipScope(IJFlexScanner scanner) {
    int openBrackedCount = 1;
    while (openBrackedCount > 0) {
      scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object currentToken;
      try {
        currentToken = scanner.nextToken();
      } catch (Exception e) {
        FRLogger.error("ScopeKeyword.skip_scope: Error while scanning file", e);
        return false;
      }
      if (currentToken == null) {
        return false; // end of file
      }
      if (currentToken == Keyword.OPEN_BRACKET) {
        ++openBrackedCount;
      } else if (currentToken == Keyword.CLOSED_BRACKET) {
        --openBrackedCount;
      }
    }
    scanner.yybegin(SpecctraDsnStreamReader.YYINITIAL);
    return true;
  }

  /** Reads the next scope of this keyword from dsn file. */
  public boolean readScope(ReadScopeParameter scopeParameter) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
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
          if (!nextScope.readScope(scopeParameter)) {
            return false;
          }
        } else {
          // skip unknown scope
          skipScope(scopeParameter.scanner);
        }
      }
    }
    return true;
  }
}
