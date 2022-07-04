package app.freerouting.designforms.specctra;

import app.freerouting.logger.FRLogger;

/** Class for reading resolution scopes from dsn-files. */
public class Resolution extends ScopeKeyword {

  /** Creates a new instance of Resolution */
  public Resolution() {
    super("resolution");
  }

  public static void write_scope(
      app.freerouting.datastructures.IndentFileWriter p_file,
      app.freerouting.board.Communication p_board_communication)
      throws java.io.IOException {
    p_file.new_line();
    p_file.write("(resolution ");
    p_file.write(p_board_communication.unit.toString());
    p_file.write(" ");
    p_file.write((Integer.valueOf(p_board_communication.resolution)).toString());
    p_file.write(")");
  }

  public boolean read_scope(ReadScopeParameter p_par) {
    try {
      // read the unit
      Object next_token = p_par.scanner.next_token();
      if (!(next_token instanceof String)) {
        FRLogger.warn("Resolution.read_scope: string expected");
        return false;
      }
      p_par.unit = app.freerouting.board.Unit.from_string((String) next_token);
      if (p_par.unit == null) {
        FRLogger.warn("Resolution.read_scope: unit mil, inch or mm expected");
        return false;
      }
      // read the scale factor
      next_token = p_par.scanner.next_token();
      if (!(next_token instanceof Integer)) {
        FRLogger.warn("Resolution.read_scope: integer expected");
        return false;
      }
      p_par.resolution = ((Integer) next_token).intValue();
      // overread the closing bracket
      next_token = p_par.scanner.next_token();
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Resolution.read_scope: closing bracket expected");
        return false;
      }
      return true;
    } catch (java.io.IOException e) {
      FRLogger.error("Resolution.read_scope: IO error scanning file", e);
      return false;
    }
  }
}
