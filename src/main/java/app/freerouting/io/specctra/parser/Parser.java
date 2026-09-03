package app.freerouting.io.specctra.parser;

import app.freerouting.board.state.Communication.SpecctraParserInfo;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading and writing parser scopes from dsn-files. */
@SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
public class Parser extends ScopeKeyword {

  /** Creates a new instance of Parser. */
  public Parser() {
    super("parser");
  }

  private static SpecctraParserInfo.WriteResolution readWriteSolution(
      ReadScopeParameter scopeParameter) {
    try {
      Object nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String resolutionString)) {
        FRLogger.warn(
            "Parser.read_write_solution: string expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Parser.read_write_solution: integer expected expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int resolutionValue = (Integer) nextToken;
      nextToken = scopeParameter.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_write_solution: closing_bracket expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new SpecctraParserInfo.WriteResolution(resolutionString, resolutionValue);
    } catch (IOException e) {
      FRLogger.error("Parser.read_write_solution: IO error scanning file", e);
      return null;
    }
  }

  private static String[] readConstant(ReadScopeParameter scopeParameter) {
    try {
      String[] result = new String[2];
      scopeParameter.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      result[0] = (String) nextToken;
      scopeParameter.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      result[1] = (String) nextToken;
      nextToken = scopeParameter.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_constant: closing_bracket expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Parser.read_constant: IO error scanning file", e);
      return null;
    }
  }

  /** Writes parser settings, optionally using the reduced session-file representation. */
  public static void writeScope(
      IndentFileWriter file,
      SpecctraParserInfo parserInfo,
      IdentifierType identifierType,
      boolean reduced)
      throws IOException {
    file.startScope();
    file.write("parser");
    if (!reduced) {
      file.newLine();
      file.write("(string_quote ");
      file.write(parserInfo.stringQuote);
      file.write(")");
      file.newLine();
      file.write("(space_in_quoted_tokens on)");
    }
    if (parserInfo.hostCad != null) {
      file.newLine();
      file.write("(host_cad ");
      identifierType.write(parserInfo.hostCad, file);
      file.write(")");
    }
    if (parserInfo.hostVersion != null) {
      file.newLine();
      file.write("(host_version ");
      identifierType.write(parserInfo.hostVersion, file);
      file.write(")");
    }
    if (parserInfo.constants != null) {
      for (String[] currentConstant : parserInfo.constants) {
        file.newLine();
        file.write("(constant ");
        for (int i = 0; i < currentConstant.length; i++) {
          identifierType.write(currentConstant[i], file);
          file.write(" ");
        }
        file.write(")");
      }
    }
    if (parserInfo.writeResolution != null) {
      file.newLine();
      file.write("(write_resolution ");
      file.write(parserInfo.writeResolution.charName.substring(0, 1));
      file.write(" ");
      int positiveInt = parserInfo.writeResolution.positiveInt;
      file.write(String.valueOf(positiveInt));
      file.write(")");
    }
    if (!reduced) {
      file.newLine();
      file.write("(generated_by_freerouting)");
    }
    file.endScope();
  }

  private static String readQuoteChar(IJFlexScanner scanner) {
    try {
      Object nextToken = scanner.nextToken();
      if (!(nextToken instanceof String result)) {
        FRLogger.warn(
            "Parser.read_quote_char: string expected at '" + scanner.getScopeIdentifier() + "'");
        return null;
      }
      nextToken = scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_quote_char: closing bracket expected at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Parser.read_quote_char: IO error scanning file", e);
      return null;
    }
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scopeParameter.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Parser.read_scope: IO error scanning file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Parser.read_scope: unexpected end of file at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      boolean readOk = true;
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == STRING_QUOTE) {
          String quoteChar = readQuoteChar(scopeParameter.scanner);
          if (quoteChar == null) {
            return false;
          }
          scopeParameter.stringQuote = quoteChar;
        } else if (nextToken == HOST_CAD) {
          scopeParameter.hostCad = DsnFile.readStringScope(scopeParameter.scanner);
        } else if (nextToken == HOST_VERSION) {
          scopeParameter.hostVersion = DsnFile.readStringScope(scopeParameter.scanner);
        } else if (nextToken == CONSTANT) {
          String[] currentConstant = readConstant(scopeParameter);
          if (currentConstant != null) {
            scopeParameter.constants.add(currentConstant);
          }
        } else if (nextToken == WRITE_RESOLUTION) {
          scopeParameter.writeResolution = readWriteSolution(scopeParameter);
        } else if (nextToken == GENERATED_BY_FREEROUTING) {
          scopeParameter.dsnFileGeneratedByHost = false;
          // skip the closing bracket
          skipScope(scopeParameter.scanner);
        } else {
          skipScope(scopeParameter.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    return true;
  }
}
