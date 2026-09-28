package app.freerouting.gui.workspace.ports;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.state.CoordinateTransform;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;
import app.freerouting.io.specctra.DsnWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * Handles GUI board serialization and export operations behind the {@link GuiBoardManager} façade.
 *
 * <p>The serialized field order is intentionally kept here unchanged: board, workspace settings,
 * coordinate transform, and graphics context. The manager remains responsible for the live session
 * references and delegates persistence operations to this package-private collaborator.
 */
public final class GuiBoardPersistence {

  private final GuiBoardManager manager;

  public GuiBoardPersistence(GuiBoardManager manager) {
    this.manager = manager;
  }

  public boolean loadFromBinary(ObjectInputStream design) {
    String inputFilename =
        manager.getPersistenceRoutingJob() != null
                && manager.getPersistenceRoutingJob().input != null
            ? manager.getPersistenceRoutingJob().input.getFilename()
            : null;
    if (manager.getPersistenceRoutingJob() != null) {
      manager
          .getPersistenceRoutingJob()
          .logInfo(
              "Loading board file"
                  + (inputFilename != null ? " '" + inputFilename + "'" : "")
                  + "...");
    } else {
      FRLogger.info(
          "Loading board file" + (inputFilename != null ? " '" + inputFilename + "'" : "") + "...");
    }

    try {
      manager.setPersistenceBoard((RoutingBoard) design.readObject());
      WorkspaceSettings workspaceSettings = (WorkspaceSettings) design.readObject();
      manager.setPersistenceWorkspaceSettings(workspaceSettings);
      WorkspaceSettings.setInstance(workspaceSettings);
      manager.settingsMerger.addOrReplaceSources(workspaceSettings);
      manager.setPersistenceCoordinateTransform((CoordinateTransform) design.readObject());
      manager.setPersistenceGraphicsContext((GraphicsContext) design.readObject());
      manager.setOriginalBoardChecksum(manager.calculateCrc32());
    } catch (Exception e) {
      manager.getPersistenceRoutingJob().logError("Couldn't read design file", e);
      return false;
    }

    RoutingBoard board = manager.getPersistenceBoard();
    WorkspaceSettings workspaceSettings = manager.getPersistenceWorkspaceSettings();
    manager.screenMessages.setLayer(board.layerStructure.layers[workspaceSettings.getLayer()].name);
    javax.swing.SwingUtilities.invokeLater(manager::refreshGuiFromSettings);
    return true;
  }

  public boolean saveAsSpecctraDesignDsn(
      OutputStream outputStream, String designName, boolean compatibilityMode) {
    if (manager.isBoardReadOnlyForPersistence() || outputStream == null) {
      return false;
    }

    boolean wasSaveSuccessful;
    try {
      DsnWriter.write(manager.getRoutingBoard(), outputStream, designName, compatibilityMode);
      wasSaveSuccessful = true;
    } catch (IOException e) {
      FRLogger.error("unable to write Specctra DSN file", e);
      wasSaveSuccessful = false;
    }

    if (wasSaveSuccessful) {
      manager.setOriginalBoardChecksum(manager.calculateCrc32());
    }
    return wasSaveSuccessful;
  }

  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    if (manager.isBoardReadOnlyForPersistence()) {
      return false;
    }
    return manager.saveHeadlessSpecctraSessionSes(outputStream, designName);
  }

  public boolean saveSpecctraSessionSesAsFusionScriptScr(
      InputStream inputStream, OutputStream outputStream) {
    if (manager.isBoardReadOnlyForPersistence()) {
      return false;
    }
    return app.freerouting.io.specctra.SesReader.saveSpecctraSessionSesAsFusionScriptScr(
        inputStream, outputStream, manager.getPersistenceBoard());
  }

  public boolean saveAsBinary(ObjectOutputStream objectStream) {
    boolean result = true;
    try {
      objectStream.writeObject(manager.getPersistenceBoard());
      objectStream.writeObject(manager.getPersistenceWorkspaceSettings());
      objectStream.writeObject(manager.getPersistenceCoordinateTransform());
      objectStream.writeObject(manager.getPersistenceGraphicsContext());
      manager.setOriginalBoardChecksum(manager.calculateCrc32());
    } catch (Exception _) {
      manager.screenMessages.setStatusMessage(manager.getPersistenceText("save_error"));
      result = false;
    }
    return result;
  }
}
