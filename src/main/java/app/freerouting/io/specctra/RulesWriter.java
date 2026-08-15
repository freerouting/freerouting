package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.core.library.Padstack;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.specctra.parser.AutorouteSettings;
import app.freerouting.io.specctra.parser.Library;
import app.freerouting.io.specctra.parser.Network;
import app.freerouting.io.specctra.parser.Rule;
import app.freerouting.io.specctra.parser.Structure;
import app.freerouting.io.specctra.parser.WriteScopeParameter;
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
    IndentFileWriter outputFile = new IndentFileWriter(out);
    WriteScopeParameter par =
        new WriteScopeParameter(
            board,
            null,
            outputFile,
            board.communication.specctraParserInfo.stringQuote,
            board.communication.coordinateTransform,
            false);
    writeRules(par, designName);
    outputFile.flush();
  }

  // -------------------------------------------------------------------------
  // Private helpers for rules-file writing.
  // -------------------------------------------------------------------------

  private static void writeRules(WriteScopeParameter par, String designName) throws IOException {
    par.file.startScope();
    par.file.write("rules PCB ");
    par.file.write(designName);
    Structure.writeSnapAngle(par.file, par.board.rules.getTraceAngleRestriction());
    if (par.autorouteSettings != null) {
      AutorouteSettings.writeScope(
          par.file, par.autorouteSettings, par.board.layerStructure, par.identifierType);
    }
    // write the default rule using 0 as default layer
    Rule.writeDefaultRule(par, 0);
    // write the via padstacks
    for (int i = 1; i <= par.board.library.padstacks.count(); i++) {
      Padstack currPadstack = par.board.library.padstacks.get(i);
      if (par.board.library.getViaPadstack(currPadstack.name) != null) {
        Library.writePadstackScope(par, currPadstack);
      }
    }
    Network.writeViaInfos(par.board.rules, par.file, par.identifierType);
    Network.writeViaRules(par.board.rules, par.file, par.identifierType);
    Network.writeNetClasses(par);
    par.file.endScope();
  }
}
