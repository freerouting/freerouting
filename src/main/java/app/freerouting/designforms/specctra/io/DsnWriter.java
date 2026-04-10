package app.freerouting.designforms.specctra.io;

import app.freerouting.board.BasicBoard;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.designforms.specctra.Library;
import app.freerouting.designforms.specctra.Network;
import app.freerouting.designforms.specctra.PartLibrary;
import app.freerouting.designforms.specctra.Parser;
import app.freerouting.designforms.specctra.Placement;
import app.freerouting.designforms.specctra.Resolution;
import app.freerouting.designforms.specctra.Structure;
import app.freerouting.designforms.specctra.Unit;
import app.freerouting.designforms.specctra.Wiring;
import app.freerouting.designforms.specctra.WriteScopeParameter;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Serialises a {@link BasicBoard} (or any subclass) to the Specctra DSN format.
 *
 * <p>This class has no dependency on {@code BoardManager}, {@code RoutingJob}, or any GUI class.
 * It operates purely on the board's data model.
 *
 * <p>Replaces the write path previously found in {@link app.freerouting.designforms.specctra.DsnFile}
 * ({@code DsnFile.write} is now a {@link Deprecated} delegate to this class).
 */
public final class DsnWriter {

  private DsnWriter() {
  }

  /**
   * Writes a {@link BasicBoard} (or subclass, including {@code RoutingBoard}) to Specctra DSN
   * format on the given stream.
   *
   * <p>The stream is <em>flushed</em> after writing but is <strong>not closed</strong> — the
   * caller retains ownership of the stream lifecycle.
   *
   * @param board        the board to serialise (must not be {@code null})
   * @param outputStream target stream (caller owns lifecycle)
   * @param designName   PCB name written into the {@code (pcb ...)} scope header
   * @param compatMode   if {@code true}, omit non-standard Freerouting extensions so any
   *                     Specctra-compatible tool can read the output
   * @throws IOException if an I/O error occurs during writing
   */
  public static void write(
      BasicBoard board,
      OutputStream outputStream,
      String designName,
      boolean compatMode) throws IOException {

    IndentFileWriter outputFile = new IndentFileWriter(outputStream);
    writePcbScope(board, outputFile, designName, compatMode);
    outputFile.flush();
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private static void writePcbScope(
      BasicBoard board,
      IndentFileWriter outputFile,
      String designName,
      boolean compatMode) throws IOException {

    WriteScopeParameter writeScopeParam = new WriteScopeParameter(
        board,
        null,
        outputFile,
        board.communication.specctra_parser_info.string_quote,
        board.communication.coordinate_transform,
        compatMode);

    outputFile.start_scope(false);
    outputFile.write("pcb ");
    writeScopeParam.identifier_type.write(designName, outputFile);

    Parser.write_scope(
        writeScopeParam.file,
        writeScopeParam.board.communication.specctra_parser_info,
        writeScopeParam.identifier_type,
        false);

    Resolution.write_scope(outputFile, board.communication);
    Unit.write_scope(outputFile, board.communication.unit);
    Structure.write_scope(writeScopeParam);
    Placement.write_scope(writeScopeParam);
    Library.write_scope(writeScopeParam);
    PartLibrary.write_scope(writeScopeParam);
    Network.write_scope(writeScopeParam);
    Wiring.write_scope(writeScopeParam);

    outputFile.end_scope();
  }
}

