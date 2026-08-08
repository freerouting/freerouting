package app.freerouting.io.specctra.parser;

import app.freerouting.board.Communication.SpecctraParserInfo;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading and writing parser scopes from dsn-files. */
public class Parser extends ScopeKeyword {

  /** Creates a new instance of Parser */
  public Parser() {
    super("parser");
  }

  private static SpecctraParserInfo.WriteResolution readWriteSolution(ReadScopeParameter pPar) {
    try {
      Object nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof String resolution_string)) {
        FRLogger.warn(
            "Parser.read_write_solution: string expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Parser.read_write_solution: integer expected expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      int resolutionValue = (Integer) nextToken;
      nextToken = pPar.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_write_solution: closing_bracket expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return new SpecctraParserInfo.WriteResolution(resolution_string, resolutionValue);
    } catch (IOException e) {
      FRLogger.error("Parser.read_write_solution: IO error scanning file", e);
      return null;
    }
  }

  private static String[] readConstant(ReadScopeParameter pPar) {
    try {
      String[] result = new String[2];
      pPar.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '" + pPar.scanner.getScopeIdentifier() + "'");
        return null;
      }
      result[0] = (String) nextToken;
      pPar.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '" + pPar.scanner.getScopeIdentifier() + "'");
        return null;
      }
      result[1] = (String) nextToken;
      nextToken = pPar.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_constant: closing_bracket expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Parser.read_constant: IO error scanning file", e);
      return null;
    }
  }

  /** p_reduced is true if the scope is written to a session file. */
  public static void writeScope(
      IndentFileWriter pFile,
      SpecctraParserInfo pParserInfo,
      IdentifierType pIdentifierType,
      boolean pReduced)
      throws IOException {
    pFile.startScope();
    pFile.write("parser");
    if (!pReduced) {
      pFile.newLine();
      pFile.write("(stringQuote ");
      pFile.write(pParserInfo.stringQuote);
      pFile.write(")");
      pFile.newLine();
      pFile.write("(space_in_quoted_tokens on)");
    }
    if (pParserInfo.hostCad != null) {
      pFile.newLine();
      pFile.write("(hostCad ");
      pIdentifierType.write(pParserInfo.hostCad, pFile);
      pFile.write(")");
    }
    if (pParserInfo.hostVersion != null) {
      pFile.newLine();
      pFile.write("(hostVersion ");
      pIdentifierType.write(pParserInfo.hostVersion, pFile);
      pFile.write(")");
    }
    if (pParserInfo.constants != null) {
      for (String[] currConstant : pParserInfo.constants) {
        pFile.newLine();
        pFile.write("(constant ");
        for (int i = 0; i < currConstant.length; i++) {
          pIdentifierType.write(currConstant[i], pFile);
          pFile.write(" ");
        }
        pFile.write(")");
      }
    }
    if (pParserInfo.writeResolution != null) {
      pFile.newLine();
      pFile.write("(writeResolution ");
      pFile.write(pParserInfo.writeResolution.charName.substring(0, 1));
      pFile.write(" ");
      int positiveInt = pParserInfo.writeResolution.positiveInt;
      pFile.write(String.valueOf(positiveInt));
      pFile.write(")");
    }
    if (!pReduced) {
      pFile.newLine();
      pFile.write("(generated_by_freerouting)");
    }
    pFile.endScope();
  }

  private static String readQuoteChar(IJFlexScanner pScanner) {
    try {
      Object nextToken = pScanner.nextToken();
      if (!(nextToken instanceof String result)) {
        FRLogger.warn(
            "Parser.read_quote_char: string expected at '" + pScanner.getScopeIdentifier() + "'");
        return null;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_quote_char: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
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
  public boolean readScope(ReadScopeParameter pPar) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
      } catch (IOException _) {
        FRLogger.warn(
            "Parser.read_scope: IO error scanning file at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Parser.read_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
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
          String quoteChar = readQuoteChar(pPar.scanner);
          if (quoteChar == null) {
            return false;
          }
          pPar.stringQuote = quoteChar;
        } else if (nextToken == HOST_CAD) {
          pPar.hostCad = DsnFile.readStringScope(pPar.scanner);
        } else if (nextToken == HOST_VERSION) {
          pPar.hostVersion = DsnFile.readStringScope(pPar.scanner);
        } else if (nextToken == CONSTANT) {
          String[] currConstant = readConstant(pPar);
          if (currConstant != null) {
            pPar.constants.add(currConstant);
          }
        } else if (nextToken == WRITE_RESOLUTION) {
          pPar.writeResolution = readWriteSolution(pPar);
        } else if (nextToken == GENERATED_BY_FREEROUTING) {
          pPar.dsnFileGeneratedByHost = false;
          // skip the closing bracket
          skipScope(pPar.scanner);
        } else {
          skipScope(pPar.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    return true;
  }
}
