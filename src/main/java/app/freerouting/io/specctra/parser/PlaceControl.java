package app.freerouting.io.specctra.parser;

import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading place_control scopes from dsn-files. */
public class PlaceControl extends ScopeKeyword {

  /** Creates a new instance of PlaceControl */
  public PlaceControl() {
    super("place_control");
  }

  /** Returns true, if rotate_first is read, else false. */
  static boolean readFlipStyleRotateFirst(IJFlexScanner pScanner) {
    try {
      boolean result = false;
      Object nextToken = pScanner.nextToken();
      if (nextToken == ROTATE_FIRST) {
        result = true;
      }
      nextToken = pScanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Structure.read_flip_style: closing bracket expected at '"
                + pScanner.getScopeIdentifier()
                + "'");
        return false;
      }
      return result;
    } catch (IOException e) {
      FRLogger.error("Structure.read_flip_style: IO error scanning file", e);
      return false;
    }
  }

  /** Reads the flip_style */
  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    boolean flipStyleRotateFirst = false;
    Object nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = pPar.scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("PlaceControl.read_scope: IO error scanning file", e);
        return false;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "PlaceControl.read_scope: unexpected end of file at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      if (nextToken == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prevToken == OPEN_BRACKET) {
        if (nextToken == FLIP_STYLE) {
          flipStyleRotateFirst = readFlipStyleRotateFirst(pPar.scanner);
        }
      }
    }
    if (flipStyleRotateFirst) {
      pPar.boardHandling.getRoutingBoard().components.setFlipStyleRotateFirst(true);
    }
    return true;
  }
}
