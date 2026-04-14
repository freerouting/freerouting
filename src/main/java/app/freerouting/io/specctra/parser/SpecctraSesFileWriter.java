package app.freerouting.io.specctra.parser;

import app.freerouting.board.BasicBoard;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Methods to handle a Specctra session file.
 *
 * @deprecated Use {@link SesWriter} instead, which exposes a typed
 *             {@code throws IOException} API and has no dependency on
 *             {@code BoardManager} or any GUI class.
 */
@Deprecated
public class SpecctraSesFileWriter {

  private SpecctraSesFileWriter() {
  }

  /**
   * Creates a Specctra session file to update the host system from the RoutingBoard.
   *
   * @deprecated Use {@link SesWriter#write(BasicBoard, OutputStream, String)} instead.
   */
  @Deprecated
  public static boolean write(BasicBoard p_board, OutputStream p_output_stream, String p_design_name) {
    try {
      SesWriter.write(p_board, p_output_stream, p_design_name);
      return true;
    } catch (IOException e) {
      FRLogger.error("unable to write session file", e);
      return false;
    }
  }
}