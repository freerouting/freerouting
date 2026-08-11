package app.freerouting.io.specctra.parser;

import app.freerouting.board.Communication.SpecctraParserInfo;
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

  private static SpecctraParserInfo.WriteResolution readWriteSolution(ReadScopeParameter par) {
    try {
      Object nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof String resolutionString)) {
        FRLogger.warn(
            "Parser.read_write_solution: string expected at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Parser.read_write_solution: integer expected expected at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int resolutionValue = (Integer) nextToken;
      nextToken = par.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_write_solution: closing_bracket expected at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new SpecctraParserInfo.WriteResolution(resolutionString, resolutionValue);
    } catch (IOException e) {
      FRLogger.error("Parser.read_write_solution: IO error scanning file", e);
      return null;
    }
  }

  private static String[] readConstant(ReadScopeParameter par) {
    try {
      String[] result = new String[2];
      par.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '" + par.scanner.getScopeIdentifier() + "'");
        return null;
      }
      result[0] = (String) nextToken;
      par.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = par.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '" + par.scanner.getScopeIdentifier() + "'");
        return null;
      }
      result[1] = (String) nextToken;
      nextToken = par.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_constant: closing_bracket expected at '"
                + par.scanner.getScopeIdentifier()
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
      file.write("(stringQuote ");
      file.write(parserInfo.stringQuote);
      file.write(")");
      file.newLine();
      file.write("(space_in_quoted_tokens on)");
    }
    if (parserInfo.hostCad != null) {
      file.newLine();
      file.write("(hostCad ");
      identifierType.write(parserInfo.hostCad, file);
      file.write(")");
    }
    if (parserInfo.hostVersion != null) {
      file.newLine();
      file.write("(hostVersion ");
      identifierType.write(parserInfo.hostVersion, file);
      file.write(")");
    }
    if (parserInfo.constants != null) {
      for (String[] currConstant : parserInfo.constants) {
        file.newLine();
        file.write("(constant ");
        for (int i = 0; i < currConstant.length; i++) {
          identifierType.write(currConstant[i], file);
          file.write(" ");
        }
        file.write(")");
      }
    }
    if (parserInfo.writeResolution != null) {
      file.newLine();
      file.write("(writeResolution ");
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
  public boolean readScope(ReadScopeParameter par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = par.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Parser.read_scope: IO error scanning file at '"
                + par.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Parser.read_scope: unexpected end of file at '"
                + par.scanner.getScopeIdentifier()
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
          String quoteChar = readQuoteChar(par.scanner);
          if (quoteChar == null) {
            return false;
          }
          par.stringQuote = quoteChar;
        } else if (nextToken == HOST_CAD) {
          par.hostCad = DsnFile.readStringScope(par.scanner);
        } else if (nextToken == HOST_VERSION) {
          par.hostVersion = DsnFile.readStringScope(par.scanner);
        } else if (nextToken == CONSTANT) {
          String[] currConstant = readConstant(par);
          if (currConstant != null) {
            par.constants.add(currConstant);
          }
        } else if (nextToken == WRITE_RESOLUTION) {
          par.writeResolution = readWriteSolution(par);
        } else if (nextToken == GENERATED_BY_FREEROUTING) {
          par.dsnFileGeneratedByHost = false;
          // skip the closing bracket
          skipScope(par.scanner);
        } else {
          skipScope(par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    return true;
  }
}
