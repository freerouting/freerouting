package app.freerouting.io.specctra.parser;

import app.freerouting.board.state.Communication;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;

/** Class for reading resolution scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Resolution extends ScopeKeyword {

  /** Creates a new instance of Resolution. */
  public Resolution() {
    super("resolution");
  }

  public static void writeScope(IndentFileWriter file, Communication boardCommunication)
      throws IOException {
    file.newLine();
    file.write("(resolution ");
    file.write(boardCommunication.unit.toString());
    file.write(" ");
    file.write(String.valueOf(boardCommunication.resolution));
    file.write(")");
  }

  @Override
  public boolean readScope(ReadScopeParameter scopeParameter) {
    try {
      // read the unit
      Object nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof String)) {
        FRLogger.warn(
            "Resolution.read_scope: string expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      scopeParameter.unit =
          app.freerouting.board.model.structure.Unit.fromString((String) nextToken);
      if (scopeParameter.unit == null) {
        FRLogger.warn(
            "Resolution.read_scope: unit mil, inch or mm expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      // read the scale factor
      nextToken = scopeParameter.scanner.nextToken();
      if (!(nextToken instanceof Integer)) {
        FRLogger.warn(
            "Resolution.read_scope: integer expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
                + "'");
        return false;
      }
      scopeParameter.resolution = (Integer) nextToken;
      // overread the closing bracket
      nextToken = scopeParameter.scanner.nextToken();
      if (nextToken != CLOSED_BRACKET) {
        FRLogger.warn(
            "Resolution.read_scope: closing bracket expected at '"
                + scopeParameter.scanner.getScopeIdentifier()
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
