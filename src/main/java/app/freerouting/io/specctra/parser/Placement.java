package app.freerouting.io.specctra.parser;

import java.io.IOException;

/** Class for writing placement scopes from dsn-files. */
public class Placement extends ScopeKeyword {

  /** Creates a new instance of Placement */
  public Placement() {
    super("placement");
  }

  public static void writeScope(WriteScopeParameter pPar) throws IOException {
    pPar.file.startScope();
    pPar.file.write("placement");
    if (pPar.board.components.getFlipStyleRotateFirst()) {
      pPar.file.newLine();
      pPar.file.write("(place_control (flip_style rotate_first))");
    }

    if (pPar.board.library.packages != null) {
      for (int i = 1; i <= pPar.board.library.packages.count(); i++) {
        Package.writePlacementScope(pPar, pPar.board.library.packages.get(i));
      }
    }
    pPar.file.endScope();
  }
}
