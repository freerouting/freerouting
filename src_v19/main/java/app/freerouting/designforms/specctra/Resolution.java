package app.freerouting.designforms.specctra;

import app.freerouting.board.Communication;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;

import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
public class Resolution extends ScopeKeyword {

  /** Creates a new instance of Resolution */
  public Resolution() {
    super("resolution");
  }

  public static void write_scope(
      IndentFileWriter p_file,
      Communication p_board_communication)
      throws IOException {
    p_file.new_line();
    p_file.write("(resolution ");
    p_file.write(p_board_communication.unit.toString());
    p_file.write(" ");
    p_file.write(String.valueOf(p_board_communication.resolution));
    p_file.write(")");
  }

  @Override
  public boolean read_scope(ReadScopeParameter p_par) {
    try {
      // read the unit
      Object next_token = p_par.scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Resolution.read_scope: string expected at '" + p_par.scanner.get_scope_identifier() + "'");
        return false;
      }
      p_par.unit = app.freerouting.board.Unit.from_string((String) next_token);
      if (p_par.unit == null) {
        FRLogger.warn("Resolution.read_scope: unit mil, inch or mm expected at '" + p_par.scanner.get_scope_identifier() + "'");
        return false;
      }
      // read the scale factor
      next_token = p_par.scanner.next_token();
      if (!(next_token instanceof Integer)) {
        FRLogger.warn("Resolution.read_scope: integer expected at '" + p_par.scanner.get_scope_identifier() + "'");
        return false;
      }
      p_par.resolution = (Integer) next_token;
      // overread the closing bracket
      next_token = p_par.scanner.next_token();
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Resolution.read_scope: closing bracket expected at '" + p_par.scanner.get_scope_identifier() + "'");
        return false;
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("Resolution.read_scope: IO error scanning file", e);
      return false;
    }
  }
}
