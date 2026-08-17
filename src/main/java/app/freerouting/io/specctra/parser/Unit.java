package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IndentFileWriter;
import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Unit extends ScopeKeyword {

  /** Creates a new instance of Unit. */
  public Unit() {
    super("unit");
  }

  public static void writeScope(IndentFileWriter file, app.freerouting.board.Unit unit)
      throws IOException {
    file.newLine();
    file.write("(unit ");
    file.write(unit.toString());
    file.write(")");
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    return false;
  }
}
