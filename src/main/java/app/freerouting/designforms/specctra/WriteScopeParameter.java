package app.freerouting.designforms.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;

/** Default parameter type used while writing a Specctra dsn-file. */
public class WriteScopeParameter {

  final BasicBoard board;
  final app.freerouting.interactive.AutorouteSettings autoroute_settings;
  final IndentFileWriter file;
  final CoordinateTransform coordinate_transform;
  final boolean compat_mode;
  final IdentifierType identifier_type;
  /**
   * Creates a new instance of WriteScopeParameter. If p_compat_mode is true, only standard
   * speecctra dsb scopes are written, so that any host system with an specctra interface can read
   * them.
   */
  WriteScopeParameter(
      BasicBoard p_board,
      app.freerouting.interactive.AutorouteSettings p_autoroute_settings,
      IndentFileWriter p_file,
      String p_string_quote,
      CoordinateTransform p_coordinate_transform,
      boolean p_compat_mode) {
    board = p_board;
    autoroute_settings = p_autoroute_settings;
    file = p_file;
    coordinate_transform = p_coordinate_transform;
    compat_mode = p_compat_mode;
    String[] reserved_chars = {"(", ")", " ", ";", "-", "_"};
    identifier_type = new IdentifierType(reserved_chars, p_string_quote);
  }
}
