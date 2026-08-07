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

  private static SpecctraParserInfo.WriteResolution read_write_solution(ReadScopeParameter p_par) {
    try {
      Object nextToken = p_par.scanner.next_token();
      if (!(nextToken instanceof String resolution_string)) {
        FRLogger.warn(
            "Parser.read_write_solution: string expected at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return null;
      }
      nextToken = p_par.scanner.next_token();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Parser.read_write_solution: integer expected expected at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return null;
      }
      int resolutionValue = (Integer) nextToken;
      nextToken = p_par.scanner.next_token();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_write_solution: closing_bracket expected at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return null;
      }
      return new SpecctraParserInfo.WriteResolution(resolution_string, resolutionValue);
    } catch (IOException e) {
      FRLogger.error("Parser.read_write_solution: IO error scanning file", e);
      return null;
    }
  }

  private static String[] read_constant(ReadScopeParameter p_par) {
    try {
      String[] result = new String[2];
      p_par.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      Object nextToken = p_par.scanner.next_token();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return null;
      }
      result[0] = (String) nextToken;
      p_par.scanner.yybegin(SpecctraDsnStreamReader.NAME);
      nextToken = p_par.scanner.next_token();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Parser.read_constant: string expected at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return null;
      }
      result[1] = (String) nextToken;
      nextToken = p_par.scanner.next_token();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_constant: closing_bracket expected at '"
                + p_par.scanner.get_scope_identifier()
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
  public static void write_scope(
      IndentFileWriter p_file,
      SpecctraParserInfo p_parser_info,
      IdentifierType p_identifier_type,
      boolean p_reduced)
      throws IOException {
    p_file.start_scope();
    p_file.write("parser");
    if (!p_reduced) {
      p_file.new_line();
      p_file.write("(stringQuote ");
      p_file.write(p_parser_info.stringQuote);
      p_file.write(")");
      p_file.new_line();
      p_file.write("(space_in_quoted_tokens on)");
    }
    if (p_parser_info.hostCad != null) {
      p_file.new_line();
      p_file.write("(hostCad ");
      p_identifier_type.write(p_parser_info.hostCad, p_file);
      p_file.write(")");
    }
    if (p_parser_info.hostVersion != null) {
      p_file.new_line();
      p_file.write("(hostVersion ");
      p_identifier_type.write(p_parser_info.hostVersion, p_file);
      p_file.write(")");
    }
    if (p_parser_info.constants != null) {
      for (String[] currConstant : p_parser_info.constants) {
        p_file.new_line();
        p_file.write("(constant ");
        for (int i = 0; i < currConstant.length; i++) {
          p_identifier_type.write(currConstant[i], p_file);
          p_file.write(" ");
        }
        p_file.write(")");
      }
    }
    if (p_parser_info.writeResolution != null) {
      p_file.new_line();
      p_file.write("(writeResolution ");
      p_file.write(p_parser_info.writeResolution.charName.substring(0, 1));
      p_file.write(" ");
      int positiveInt = p_parser_info.writeResolution.positiveInt;
      p_file.write(String.valueOf(positiveInt));
      p_file.write(")");
    }
    if (!p_reduced) {
      p_file.new_line();
      p_file.write("(generated_by_freerouting)");
    }
    p_file.end_scope();
  }

  private static String read_quote_char(IJFlexScanner p_scanner) {
    try {
      Object nextToken = p_scanner.next_token();
      if (!(nextToken instanceof String result)) {
        FRLogger.warn(
            "Parser.read_quote_char: string expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      nextToken = p_scanner.next_token();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Parser.read_quote_char: closing bracket expected at '"
                + p_scanner.get_scope_identifier()
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
  public boolean read_scope(ReadScopeParameter p_par) {
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_par.scanner.next_token();
      } catch (IOException _) {
        FRLogger.warn(
            "Parser.read_scope: IO error scanning file at '"
                + p_par.scanner.get_scope_identifier()
                + "'");
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Parser.read_scope: unexpected end of file at '"
                + p_par.scanner.get_scope_identifier()
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
          String quoteChar = read_quote_char(p_par.scanner);
          if (quoteChar == null) {
            return false;
          }
          p_par.stringQuote = quoteChar;
        } else if (nextToken == HOST_CAD) {
          p_par.hostCad = DsnFile.read_string_scope(p_par.scanner);
        } else if (nextToken == HOST_VERSION) {
          p_par.hostVersion = DsnFile.read_string_scope(p_par.scanner);
        } else if (nextToken == CONSTANT) {
          String[] currConstant = read_constant(p_par);
          if (currConstant != null) {
            p_par.constants.add(currConstant);
          }
        } else if (nextToken == WRITE_RESOLUTION) {
          p_par.writeResolution = read_write_solution(p_par);
        } else if (nextToken == GENERATED_BY_FREEROUTING) {
          p_par.dsnFileGeneratedByHost = false;
          // skip the closing bracket
          skip_scope(p_par.scanner);
        } else {
          skip_scope(p_par.scanner);
        }
      }
      if (!readOk) {
        return false;
      }
    }
    return true;
  }
}
