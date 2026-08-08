package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IndentFileWriter;
import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
public class Unit extends ScopeKeyword {

  /** Creates a new instance of Unit */
  public Unit() {
    super("unit");
  }

  public static void writeScope(IndentFileWriter pFile, app.freerouting.board.Unit pUnit)
      throws IOException {
    pFile.newLine();
    pFile.write("(unit ");
    pFile.write(pUnit.toString());
    pFile.write(")");
  }

  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    return false;
  }
}
