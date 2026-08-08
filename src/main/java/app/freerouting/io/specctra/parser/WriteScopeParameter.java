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
      BasicBoard pBoard,
      RouterSettings pAutorouteSettings,
      IndentFileWriter pFile,
      String pStringQuote,
      CoordinateTransform pCoordinateTransform,
      boolean pCompatMode) {
    board = pBoard;
    autorouteSettings = pAutorouteSettings;
    file = pFile;
    coordinateTransform = pCoordinateTransform;
    compatMode = pCompatMode;
    String[] reservedChars = {"(", ")", " ", ";", "-", "_"};
    identifierType = new IdentifierType(reservedChars, pStringQuote);
  }
}
