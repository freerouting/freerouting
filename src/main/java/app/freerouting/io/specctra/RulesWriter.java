package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.core.library.Padstack;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.specctra.parser.AutorouteSettings;
import app.freerouting.io.specctra.parser.Library;
import app.freerouting.io.specctra.parser.Network;
import app.freerouting.io.specctra.parser.Rule;
import app.freerouting.io.specctra.parser.Structure;
import app.freerouting.io.specctra.parser.WriteScopeParameter;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes board design rules to a Specctra {@code .rules} file without any dependency on {@link
 * app.freerouting.gui.workspace.GuiBoardManager}.
 *
 * <p>This class is the public write entry point for Specctra rules files.
 */
public final class RulesWriter {

  private RulesWriter() {}

  /**
   * Writes the design rules of {@code board} to {@code out} in Specctra rules format.
   *
   * <p>The stream is <em>not</em> closed by this method — the caller is responsible.
   *
   * @param board the board whose rules are written
   * @param out destination stream
   * @param designName the PCB design name written into the {@code (rules PCB ...)} header
   * @throws IOException if writing fails
   */
  public static void write(BasicBoard board, OutputStream out, String designName)
      throws IOException {
    write(board, null, out, designName);
  }

  /**
   * Writes the design rules of {@code board} and optional {@code settings} to {@code out} in
   * Specctra rules format.
   *
   * <p>The stream is <em>not</em> closed by this method — the caller is responsible.
   *
   * @param board the board whose rules are written
   * @param settings optional router settings to serialize into the {@code (autoroute_settings ...)}
   *     scope
   * @param out destination stream
   * @param designName the PCB design name written into the {@code (rules PCB ...)} header
   * @throws IOException if writing fails
   */
  public static void write(
      BasicBoard board, RouterSettings settings, OutputStream out, String designName)
      throws IOException {
    IndentFileWriter outputFile = new IndentFileWriter(out);
    WriteScopeParameter scopeParameter =
        new WriteScopeParameter(
            board,
            settings,
            outputFile,
            board.communication.specctraParserInfo.stringQuote,
            board.communication.coordinateTransform,
            false);
    writeRules(scopeParameter, designName);
    outputFile.flush();
  }

  // -------------------------------------------------------------------------
  // Private helpers for rules-file writing.
  // -------------------------------------------------------------------------

  private static void writeRules(WriteScopeParameter scopeParameter, String designName)
      throws IOException {
    scopeParameter.file.startScope();
    scopeParameter.file.write("rules PCB ");
    scopeParameter.file.write(designName);
    Structure.writeSnapAngle(
        scopeParameter.file, scopeParameter.board.rules.getTraceAngleRestriction());
    if (scopeParameter.autorouteSettings != null) {
      AutorouteSettings.writeScope(
          scopeParameter.file,
          scopeParameter.autorouteSettings,
          scopeParameter.board.layerStructure,
          scopeParameter.identifierType);
    }
    // write the default rule using 0 as default layer
    Rule.writeDefaultRule(scopeParameter, 0);
    // write the via padstacks
    for (int i = 1; i <= scopeParameter.board.library.padstacks.count(); i++) {
      Padstack currentPadstack = scopeParameter.board.library.padstacks.get(i);
      if (scopeParameter.board.library.getViaPadstack(currentPadstack.name) != null) {
        Library.writePadstackScope(scopeParameter, currentPadstack);
      }
    }
    Network.writeViaInfos(
        scopeParameter.board.rules, scopeParameter.file, scopeParameter.identifierType);
    Network.writeViaRules(
        scopeParameter.board.rules, scopeParameter.file, scopeParameter.identifierType);
    Network.writeNetClasses(scopeParameter);
    scopeParameter.file.endScope();
  }
}
