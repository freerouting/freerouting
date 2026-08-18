package app.freerouting.io.specctra;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.io.specctra.parser.Library;
import app.freerouting.io.specctra.parser.Network;
import app.freerouting.io.specctra.parser.Parser;
import app.freerouting.io.specctra.parser.PartLibrary;
import app.freerouting.io.specctra.parser.Placement;
import app.freerouting.io.specctra.parser.Resolution;
import app.freerouting.io.specctra.parser.Structure;
import app.freerouting.io.specctra.parser.Unit;
import app.freerouting.io.specctra.parser.Wiring;
import app.freerouting.io.specctra.parser.WriteScopeParameter;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Serialises a {@link BasicBoard} (or any subclass) to the Specctra DSN format.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class. It
 * operates purely on the board's data model.
 *
 * <p>Replaces the write path previously found in {@link app.freerouting.io.specctra.parser.DsnFile}
 * ({@code DsnFile.write} is now a {@link Deprecated} delegate to this class).
 */
public final class DsnWriter {

  private DsnWriter() {}

  /**
   * Writes a {@link BasicBoard} (or subclass, including {@code RoutingBoard}) to Specctra DSN
   * format on the given stream.
   *
   * <p>The stream is <em>flushed</em> after writing but is <strong>not closed</strong> — the caller
   * retains ownership of the stream lifecycle.
   *
   * @param board the board to serialise (must not be {@code null})
   * @param outputStream target stream (caller owns lifecycle)
   * @param designName PCB name written into the {@code (pcb ...)} scope header
   * @param compatMode if {@code true}, omit non-standard Freerouting extensions so any
   *     Specctra-compatible tool can read the output
   * @throws IOException if an I/O error occurs during writing
   */
  public static void write(
      BasicBoard board, OutputStream outputStream, String designName, boolean compatMode)
      throws IOException {

    IndentFileWriter outputFile = new IndentFileWriter(outputStream);
    writePcbScope(board, outputFile, designName, compatMode);
    outputFile.flush();
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private static void writePcbScope(
      BasicBoard board, IndentFileWriter outputFile, String designName, boolean compatMode)
      throws IOException {

    WriteScopeParameter writeScopeParam =
        new WriteScopeParameter(
            board,
            null,
            outputFile,
            board.communication.specctraParserInfo.stringQuote,
            board.communication.coordinateTransform,
            compatMode);

    outputFile.startScope(false);
    outputFile.write("pcb ");
    writeScopeParam.identifierType.write(designName, outputFile);

    Parser.writeScope(
        writeScopeParam.file,
        writeScopeParam.board.communication.specctraParserInfo,
        writeScopeParam.identifierType,
        false);

    Resolution.writeScope(outputFile, board.communication);
    Unit.writeScope(outputFile, board.communication.unit);
    Structure.writeScope(writeScopeParam);
    Placement.writeScope(writeScopeParam);
    Library.writeScope(writeScopeParam);
    PartLibrary.writeScope(writeScopeParam);
    Network.writeScope(writeScopeParam);
    Wiring.writeScope(writeScopeParam);

    outputFile.endScope();
  }
}
