package app.freerouting.io.specctra.parser;

import app.freerouting.board.Communication;
import app.freerouting.board.Unit;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
public class Resolution extends ScopeKeyword {

  /** Creates a new instance of Resolution */
  public Resolution() {
    super("resolution");
  }

  public static void writeScope(IndentFileWriter pFile, Communication pBoardCommunication)
      throws IOException {
    pFile.newLine();
    pFile.write("(resolution ");
    pFile.write(pBoardCommunication.unit.toString());
    pFile.write(" ");
    pFile.write(String.valueOf(pBoardCommunication.resolution));
    pFile.write(")");
  }

  @Override
  public boolean readScope(ReadScopeParameter pPar) {
    try {
      // read the unit
      Object nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Resolution.read_scope: string expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      pPar.unit = Unit.fromString((String) nextToken);
      if (pPar.unit == null) {
        FRLogger.warn(
            "Resolution.read_scope: unit mil, inch or mm expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      // read the scale factor
      nextToken = pPar.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Resolution.read_scope: integer expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      pPar.resolution = (Integer) nextToken;
      // overread the closing bracket
      nextToken = pPar.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Resolution.read_scope: closing bracket expected at '"
                + pPar.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      return true;
    } catch (IOException e) {
      FRLogger.error("Resolution.read_scope: IO error scanning file", e);
      return false;
    }
  }
}
