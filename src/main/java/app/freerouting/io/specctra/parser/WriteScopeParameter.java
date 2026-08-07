package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.settings.RouterSettings;

/** Default parameter type used while writing a Specctra dsn-file. */
public class WriteScopeParameter {

  public final BasicBoard board;
  public final RouterSettings autorouteSettings;
  public final IndentFileWriter file;
  public final CoordinateTransform coordinateTransform;
  public final boolean compatMode;
  public final IdentifierType identifierType;

  /**
   * Creates a new instance of WriteScopeParameter. If p_compat_mode is true, only standard specctra
   * dsb scopes are written, so that any host system with a specctra interface can read them.
   */
  public WriteScopeParameter(
      BasicBoard p_board,
      RouterSettings p_autoroute_settings,
      IndentFileWriter p_file,
      String p_string_quote,
      CoordinateTransform p_coordinate_transform,
      boolean p_compat_mode) {
    board = p_board;
    autorouteSettings = p_autoroute_settings;
    file = p_file;
    coordinateTransform = p_coordinate_transform;
    compatMode = p_compat_mode;
    String[] reservedChars = {"(", ")", " ", ";", "-", "_"};
    identifierType = new IdentifierType(reservedChars, p_string_quote);
  }
}
