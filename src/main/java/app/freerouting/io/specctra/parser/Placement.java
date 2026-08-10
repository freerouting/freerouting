package app.freerouting.io.specctra.parser;

import java.io.IOException;

/** Class for writing placement scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Placement extends ScopeKeyword {

  /** Creates a new instance of Placement. */
  public Placement() {
    super("placement");
  }

  public static void writeScope(WriteScopeParameter par) throws IOException {
    par.file.startScope();
    par.file.write("placement");
    if (par.board.components.getFlipStyleRotateFirst()) {
      par.file.newLine();
      par.file.write("(place_control (flip_style rotate_first))");
    }

    if (par.board.library.packages != null) {
      for (int i = 1; i <= par.board.library.packages.count(); i++) {
        Package.writePlacementScope(par, par.board.library.packages.get(i));
      }
    }
    par.file.endScope();
  }
}
