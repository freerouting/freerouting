package app.freerouting.designforms.specctra;

import app.freerouting.logger.FRLogger;

/** Class for reading place_control scopes from dsn-files. */
public class PlaceControl extends ScopeKeyword {

  /** Creates a new instance of PlaceControl */
  public PlaceControl() {
    super("place_control");
  }

  /** Returns true, if rotate_first is read, else false. */
  static boolean read_flip_style_rotate_first(IJFlexScanner p_scanner) {
    try {
      boolean result = false;
      Object next_token = p_scanner.next_token();
      if (next_token == ROTATE_FIRST) {
        if (next_token == ROTATE_FIRST) {
          result = true;
        }
      }
      next_token = p_scanner.next_token();
      if (next_token != CLOSED_BRACKET) {
        FRLogger.warn("Structure.read_flip_style: closing bracket expected");
        return false;
      }
      return result;
    } catch (java.io.IOException e) {
      FRLogger.error("Structure.read_flip_style: IO error scanning file", e);
      return false;
    }
  }

  /** Reads the flip_style */
  public boolean read_scope(ReadScopeParameter p_par) {
    boolean flip_style_rotate_first = false;
    Object next_token = null;
    for (; ; ) {
      Object prev_token = next_token;
      try {
        next_token = p_par.scanner.next_token();
      } catch (java.io.IOException e) {
        FRLogger.error("PlaceControl.read_scope: IO error scanning file", e);
        return false;
      }
      if (next_token == null) {
        FRLogger.warn("PlaceControl.read_scope: unexpected end of file");
        return false;
      }
      if (next_token == CLOSED_BRACKET) {
        // end of scope
        break;
      }
      if (prev_token == OPEN_BRACKET) {
        if (next_token == FLIP_STYLE) {
          flip_style_rotate_first = read_flip_style_rotate_first(p_par.scanner);
        }
      }
    }
    if (flip_style_rotate_first) {
      p_par.board_handling.get_routing_board().components.set_flip_style_rotate_first(true);
    }
    return true;
  }
}
