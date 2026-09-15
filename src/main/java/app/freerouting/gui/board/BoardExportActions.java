package app.freerouting.gui.board;

import app.freerouting.core.BoardFileDetails;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.io.kicad.KiCadJsonWriter;
import app.freerouting.io.specctra.RulesWriter;
import app.freerouting.logger.FRLogger;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Owns the file-export actions exposed by {@link BoardFrame}.
 *
 * <p>The frame remains the public façade. In particular, binary serialization order is kept here
 * exactly as it was in the frame: board data, viewport, frame location, frame bounds, then the
 * permanent subwindows.
 */
public class BoardExportActions {

  private final BoardFrame boardFrame;

  public BoardExportActions(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
  }

  public boolean saveAsBinary(OutputStream outputStream) throws Exception {
    ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);

    // (1) Save the board as binary file
    boolean saveOk = boardFrame.boardPanel.boardHandling.saveAsBinary(objectStream);
    if (!saveOk) {
      return false;
    }

    // (2) Save the GUI settings as binary file
    objectStream.writeObject(boardFrame.boardPanel.getViewportPosition());
    objectStream.writeObject(boardFrame.getLocation());
    objectStream.writeObject(boardFrame.getBounds());

    // (3) Save the permanent subwindows as binary file
    for (BoardSavableSubWindow subwindow : boardFrame.getPermanentSubwindows()) {
      if (subwindow != null) {
        subwindow.save(objectStream);
      }
    }

    // (4) Flush the binary file
    objectStream.flush();
    return true;
  }

  public boolean saveAsBinary(File outputFile) {
    if (outputFile == null) {
      return false;
    }

    try {
      FRLogger.info("Saving '" + outputFile.getPath() + "'...");

      // Serialize to a byte array first to capture the data
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      saveAsBinary(byteArrayOutputStream);

      // Store the serialized data in routingJob.output
      byte[] data = byteArrayOutputStream.toByteArray();
      boardFrame.routingJob.output.setData(data);

      // Write to the file
      try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
        fileOutputStream.write(data);
      }

      boardFrame.screenMessages.setStatusMessage(
          boardFrame.tm.getText("message_binary_file_saved", outputFile.getPath()));
      return true;
    } catch (Exception _) {
      boardFrame.screenMessages.setStatusMessage(
          boardFrame.tm.getText("message_binary_file_save_failed", outputFile.getPath()));
      return false;
    }
  }

  public boolean saveAsSpecctraSessionSes(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (!boardFrame.boardPanel.boardHandling.saveAsSpecctraSessionSes(outputStream, designName)) {
        boardFrame.screenMessages.setStatusMessage(
            boardFrame.tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
        return false;
      }
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra session file '" + outputFile.getPath() + "'", e);
      boardFrame.screenMessages.setStatusMessage(
          boardFrame.tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
      return false;
    }

    boardFrame.screenMessages.setStatusMessage(
        boardFrame.tm.getText("message_specctra_ses_saved", outputFile.getPath()));
    return true;
  }

  public boolean saveAsKiCadJson(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (java.io.FileWriter writer = new java.io.FileWriter(outputFile)) {
      String json =
          KiCadJsonWriter.write(boardFrame.boardPanel.boardHandling.getRoutingBoard(), designName);
      writer.write(json);
    } catch (Exception e) {
      FRLogger.error("Unable to write KiCad JSON file", e);
      boardFrame.screenMessages.setStatusMessage(
          boardFrame.tm.getText("message_kicad_session_json_save_failed", outputFile.getPath()));
      return false;
    }

    boardFrame.screenMessages.setStatusMessage(
        boardFrame.tm.getText("message_kicad_session_json_saved", outputFile.getPath()));
    return true;
  }

  public File showSaveAsDialog(String defaultDirectory, BoardFileDetails output) {
    String directoryName;
    var outputFile = output.getFile();
    if (outputFile == null) {
      directoryName = defaultDirectory;
    } else {
      directoryName = outputFile.getParent();
    }

    JFileChooser fileChooser = new JFileChooser(directoryName);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    FileNameExtensionFilter sesFilter =
        new FileNameExtensionFilter("Specctra session file (*.ses)", "ses");
    fileChooser.addChoosableFileFilter(sesFilter);

    FileNameExtensionFilter scrFilter =
        new FileNameExtensionFilter("Autodesk Fusion script (*.scr)", "scr");
    fileChooser.addChoosableFileFilter(scrFilter);

    FileNameExtensionFilter dsnFilter =
        new FileNameExtensionFilter("Specctra design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    FileNameExtensionFilter jsonSessionFilter =
        new FileNameExtensionFilter("KiCad session JSON file (*.json)", "json");
    fileChooser.addChoosableFileFilter(jsonSessionFilter);

    fileChooser.setFileFilter(
        switch (output.format) {
          case SCR -> scrFilter;
          case DSN -> dsnFilter;
          case KICAD_SESSION_JSON -> jsonSessionFilter;
          default -> sesFilter;
        });

    if (!output.getFilename().isEmpty()) {
      fileChooser.setSelectedFile(output.getFile());
    }

    fileChooser.showSaveDialog(boardFrame);
    return fileChooser.getSelectedFile();
  }

  public boolean saveRulesAs(File rulesFile, String designName, GuiBoardManager boardHandling) {
    FRLogger.info("Saving '" + rulesFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(rulesFile)) {
      RulesWriter.write(boardHandling.getRoutingBoard(), outputStream, designName);
      return true;
    } catch (IOException e) {
      FRLogger.error("unable to save rules file for design '" + designName + "'", e);
      return false;
    }
  }

  public void saveAsFusionScriptScr(File outputFile, String designName) {
    ByteArrayOutputStream sesOutputStream = new ByteArrayOutputStream();
    GuiBoardManager boardHandling = boardFrame.boardPanel.boardHandling;
    if (!boardHandling.saveAsSpecctraSessionSes(sesOutputStream, designName)) {
      return;
    }
    InputStream sesInputStream = new ByteArrayInputStream(sesOutputStream.toByteArray());

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (boardHandling.saveSpecctraSessionSesAsFusionScriptScr(sesInputStream, outputStream)) {
        boardFrame.screenMessages.setStatusMessage(
            boardFrame.tm.getText("message_fusion_saved", outputFile.getPath()));
      } else {
        boardFrame.screenMessages.setStatusMessage(
            boardFrame.tm.getText("message_fusion_save_failed", outputFile.getPath()));
      }
    } catch (IOException e) {
      FRLogger.error(
          "unable to save Autodesk Fusion script file '" + outputFile.getPath() + "'", e);
      boardFrame.screenMessages.setStatusMessage(
          boardFrame.tm.getText("message_fusion_save_failed", outputFile.getPath()));
    }
  }

  public boolean saveAsSpecctraDesignDsn(
      File outputFile, String designName, boolean compatibilityMode) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      return boardFrame.boardPanel.boardHandling.saveAsSpecctraDesignDsn(
          outputStream, designName, compatibilityMode);
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra design file '" + outputFile.getPath() + "'", e);
      return false;
    }
  }
}
