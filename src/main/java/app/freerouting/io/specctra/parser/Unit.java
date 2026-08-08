package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IndentFileWriter;
import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
public class Unit extends ScopeKeyword {

  /** Creates a new instance of Unit */
  public Unit() {
    super("unit");
  }

  public static void writeScope(IndentFileWriter p_file, app.freerouting.board.Unit p_unit)
      throws IOException {
    p_file.newLine();
    p_file.write("(unit ");
    p_file.write(p_unit.toString());
    p_file.write(")");
  }

  @Override
  public boolean readScope(ReadScopeParameter p_par) {
    return false;
  }
}
