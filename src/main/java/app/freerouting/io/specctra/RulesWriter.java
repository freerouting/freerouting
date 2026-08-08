package app.freerouting.io.specctra;

import app.freerouting.board.BasicBoard;
import app.freerouting.core.Padstack;
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
 * app.freerouting.interactive.GuiBoardManager}.
 *
 * <p>Replaces the write path previously found in {@link
 * app.freerouting.io.specctra.parser.RulesFile} (now an empty shell).
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
  // Private helpers (migrated from RulesFile)
  // -------------------------------------------------------------------------

  private static void writeRules(WriteScopeParameter pPar, String pDesignName) throws IOException {
    pPar.file.startScope();
    pPar.file.write("rules PCB ");
    pPar.file.write(pDesignName);
    Structure.writeSnapAngle(pPar.file, pPar.board.rules.getTraceAngleRestriction());
    if (pPar.autorouteSettings != null) {
      AutorouteSettings.writeScope(
          pPar.file, pPar.autorouteSettings, pPar.board.layerStructure, pPar.identifierType);
    }
    // write the default rule using 0 as default layer
    Rule.writeDefaultRule(pPar, 0);
    // write the via padstacks
    for (int i = 1; i <= pPar.board.library.padstacks.count(); i++) {
      Padstack currPadstack = pPar.board.library.padstacks.get(i);
      if (pPar.board.library.getViaPadstack(currPadstack.name) != null) {
        Library.writePadstackScope(pPar, currPadstack);
      }
    }
    Network.writeViaInfos(pPar.board.rules, pPar.file, pPar.identifierType);
    Network.writeViaRules(pPar.board.rules, pPar.file, pPar.identifierType);
    Network.writeNetClasses(pPar);
    pPar.file.endScope();
  }
}
