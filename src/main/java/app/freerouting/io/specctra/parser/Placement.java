package app.freerouting.io.specctra.parser;

import java.io.IOException;

/** Class for writing placement scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Placement extends ScopeKeyword {

  /** Creates a new instance of Placement. */
  public Placement() {
    super("placement");
  }

  public static void writeScope(WriteScopeParameter scopeParameter) throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("placement");
    if (scopeParameter.board.components.getFlipStyleRotateFirst()) {
      scopeParameter.file.newLine();
      scopeParameter.file.write("(place_control (flip_style rotate_first))");
    }

    if (scopeParameter.board.library.packages != null) {
      for (int i = 1; i <= scopeParameter.board.library.packages.count(); i++) {
        Package.writePlacementScope(scopeParameter, scopeParameter.board.library.packages.get(i));
      }
    }
    scopeParameter.file.endScope();
  }
}
