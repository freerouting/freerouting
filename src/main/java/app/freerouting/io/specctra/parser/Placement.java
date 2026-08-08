package app.freerouting.io.specctra.parser;

import java.io.IOException;

/** Class for writing placement scopes from dsn-files. */
public class Placement extends ScopeKeyword {

  /** Creates a new instance of Placement */
  public Placement() {
    super("placement");
  }

  public static void writeScope(WriteScopeParameter p_par) throws IOException {
    p_par.file.startScope();
    p_par.file.write("placement");
    if (p_par.board.components.getFlipStyleRotateFirst()) {
      p_par.file.newLine();
      p_par.file.write("(place_control (flip_style rotate_first))");
    }

    if (p_par.board.library.packages != null) {
      for (int i = 1; i <= p_par.board.library.packages.count(); i++) {
        Package.writePlacementScope(p_par, p_par.board.library.packages.get(i));
      }
    }
    p_par.file.endScope();
  }
}
