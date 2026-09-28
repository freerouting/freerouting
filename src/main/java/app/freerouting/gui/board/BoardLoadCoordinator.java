package app.freerouting.gui.board;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.gui.windows.board.WindowMessage;
import app.freerouting.gui.workspace.session.LoadGeneration;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Coordinates background parsing for design-file loads.
 *
 * <p>The frame still owns board attachment and all generation checks. This class only moves the
 * potentially expensive parse off the EDT and returns the result to the frame on the EDT.
 */
public class BoardLoadCoordinator {

  private final BoardFrame boardFrame;

  public BoardLoadCoordinator(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
  }

  public static BoardReadResult parseBoardFromBytes(
      byte[] fileContent, FileFormat format, String filename) {
    try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
      if (format == FileFormat.DSN) {
        return DsnReader.readBoard(inputStream, null, new ItemIdGenerator(), filename);
      }
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        try (java.io.Reader reader =
            new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
          return KiCadJsonReader.readBoard(reader, null, new ItemIdGenerator());
        }
      }
      throw new IllegalArgumentException("Unsupported format for async load: " + format);
    } catch (Exception e) {
      FRLogger.error("Failed to parse board file", e);
      return new BoardReadResult.IoError(new IOException("Failed to parse board file", e));
    }
  }

  public void loadFromBytesAsync(byte[] fileContent, FileFormat format, RoutingJob job) {
    LoadGeneration generation = boardFrame.beginBoardLoadForCoordinator();
    boardFrame.ensureGeneralSettingsVisibleDuringLoad();

    String filename = job.input != null ? job.input.getFilename() : null;
    TextManager guiTm = new TextManager(GuiManager.class, boardFrame.getLocale());
    String loadingMessage =
        filename != null
            ? guiTm.getText("loading_design_with_file", filename)
            : guiTm.getText("loading_design");
    WindowMessage loadingWindow = WindowMessage.show(loadingMessage);
    loadingWindow.setLocationRelativeTo(boardFrame);

    Thread.ofVirtual()
        .name("gui-board-load")
        .start(
            () -> {
              long parseStart = System.nanoTime();
              BoardReadResult readResult = parseBoardFromBytes(fileContent, format, filename);
              long parseMs = (System.nanoTime() - parseStart) / 1_000_000L;
              FRLogger.debug(
                  "Board load: DSN/JSON parse completed in "
                      + parseMs
                      + " ms"
                      + (filename != null ? " ('" + filename + "')" : ""));

              javax.swing.SwingUtilities.invokeLater(
                  () ->
                      boardFrame.finishLoadFromParseResult(
                          readResult, fileContent, format, job, loadingWindow, generation));
            });
  }
}
