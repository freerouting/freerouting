package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.settings.RouterSettings;

/**
 * Default parameter type used while writing a Specctra dsn-file.
 */
public class WriteScopeParameter {

  public final BasicBoard board;
  public final RouterSettings autoroute_settings;
  public final IndentFileWriter file;
  public final CoordinateTransform coordinate_transform;
  public final boolean compat_mode;
  public final IdentifierType identifier_type;

  /**
   * Creates a new instance of WriteScopeParameter. If p_compat_mode is true, only standard specctra dsb scopes are written, so that any host system with a specctra interface can read them.
   */
  public WriteScopeParameter(BasicBoard p_board, RouterSettings p_autoroute_settings, IndentFileWriter p_file, String p_string_quote, CoordinateTransform p_coordinate_transform, boolean p_compat_mode) {
    board = p_board;
    autoroute_settings = p_autoroute_settings;
    file = p_file;
    coordinate_transform = p_coordinate_transform;
    compat_mode = p_compat_mode;
    String[] reserved_chars = {
        "(",
        ")",
        " ",
        ";",
        "-",
        "_"
    };
    identifier_type = new IdentifierType(reserved_chars, p_string_quote);
  }
}