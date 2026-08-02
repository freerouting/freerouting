package app.freerouting.gui;

import app.freerouting.core.RoutingJob;
import app.freerouting.io.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.Cursor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import javax.swing.JOptionPane;

/**
 * Handles drag-and-drop file operations on the board panel.
 * Allows users to drop DSN or JSON files onto the panel to open them.
 *
 * <p>Provides visual feedback using a semi-transparent overlay during drag operations.</p>
 *
 * <p>Note: SessionManager enforces single GUI session per process. Multi-session
 * support may require architectural changes to allow parallel boards.</p>
 */
public class BoardPanelDropTargetListener implements java.awt.dnd.DropTargetListener {

  private final BoardPanel board_panel;
  private boolean is_drag_active = false;
  // Flag to track if we're showing the ghosting overlay
  private boolean is_ghosting_active = false;

  /**
   * Creates a new drop target listener for the board panel.
   *
   * @param p_board_panel The board panel to attach the drop target to
   */
  public BoardPanelDropTargetListener(BoardPanel p_board_panel) {
    this.board_panel = p_board_panel;
  }

  @Override
  public void dragEnter(DropTargetDragEvent event) {
    // Visual feedback when drag enters the panel
    setDragFeedback(true);
    is_drag_active = true;
    // Accept copy or move actions for files
    event.acceptDrag(DnDConstants.ACTION_COPY | DnDConstants.ACTION_MOVE);
  }

  @Override
  public void dragOver(DropTargetDragEvent event) {
    // Maintain visual feedback during drag
    if (!is_drag_active) {
      setDragFeedback(true);
      is_drag_active = true;
    }
    event.acceptDrag(DnDConstants.ACTION_COPY | DnDConstants.ACTION_MOVE);
  }

  @Override
  public void dropActionChanged(DropTargetDragEvent event) {
    // Accept the drag action
    event.acceptDrag(DnDConstants.ACTION_COPY | DnDConstants.ACTION_MOVE);
  }

  @Override
  public void dragExit(DropTargetEvent event) {
    // Remove visual feedback when drag exits
    setDragFeedback(false);
    is_drag_active = false;
  }

  @Override
  public void drop(DropTargetDropEvent event) {
    // Remove visual feedback immediately
    setDragFeedback(false);
    is_drag_active = false;

    Transferable transferable = event.getTransferable();
    if (transferable == null) {
      event.rejectDrop();
      return;
    }

    // Check if it's a file list flavor
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      event.rejectDrop();
      return;
    }

    try {
      // Accept the drop
      event.acceptDrop(DnDConstants.ACTION_COPY);

      // Get the file list
      @SuppressWarnings("unchecked")
      List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

      if (files == null || files.isEmpty()) {
        FRLogger.warn("No files dropped");
        return;
      }

      // Process dropped files
      processDroppedFiles(files);

    } catch (Exception e) {
      FRLogger.error("Error processing dropped files", e);
      if (board_panel != null) {
        JOptionPane.showMessageDialog(board_panel, "Error processing dropped file: " + e.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
    * Processes the list of dropped files.
    * Only the first valid DSN or JSON file is loaded.
    * Additional valid files are logged for future multi-board support.
    *
    * @param p_files The list of dropped files
    */
   private void processDroppedFiles(List<File> p_files) {
    boolean file_loaded = false;

    if (board_panel == null || board_panel.board_frame == null) {
      FRLogger.warn("Board frame is not available for loading dropped file");
      return;
    }

    for (int i = 0; i < p_files.size(); i++) {
      File file = p_files.get(i);

      if (file == null) {
        continue;
      }

      // Check if file exists and is readable
      if (!file.exists()) {
        FRLogger.warn("Dropped file does not exist: " + file.getName());
        continue;
      }

      if (!file.canRead()) {
        FRLogger.warn("Dropped file is not readable: " + file.getName());
        continue;
      }

      // Validate file format by extension first, then content for unknown extensions
      FileFormat format = RoutingJob.getFileFormat(file.toPath());

      if (format == FileFormat.UNKNOWN) {
        try {
          byte[] content = Files.readAllBytes(file.toPath());
          format = RoutingJob.getFileFormat(content);
        } catch (IOException e) {
          FRLogger.warn("Could not read file for format detection: " + file.getName());
          continue;
        }
      }

       if (format == FileFormat.DSN || format == FileFormat.JSON) {
           if (!file_loaded) {
           // Load the first valid file
           board_panel.board_frame.loadDroppedFile(file, format);
           file_loaded = true;
           } else {
          // Log additional valid files for future multi-board support
          FRLogger.warn(
              "Additional dropped file ignored: '" + file.getName() + 
              "'. Multi-board support is planned for future versions.");
        }
      } else {
        // Unknown format
        FRLogger.warn(
            "Dropped file format not supported: '" + file.getName() + 
            "'. Supported formats: DSN, JSON.");
      }
    }

    if (!file_loaded) {
      JOptionPane.showMessageDialog(board_panel, "No valid DSN or JSON files found in drop",
          "Info", JOptionPane.INFORMATION_MESSAGE);
    }
  }

  /**
   * Sets the visual feedback state of the board panel.
   * Changes the background to grayish to indicate drop target.
   *
   * @param p_active Whether the drag is active
   */
  private void setDragFeedback(boolean p_active) {
    if (board_panel == null) {
      return;
    }

    if (p_active) {
      // Show ghosting overlay effect
      is_ghosting_active = true;
      board_panel.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
      board_panel.repaint();
    } else {
      // Remove ghosting overlay effect
      is_ghosting_active = false;
      board_panel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
      board_panel.repaint();
    }
  }

  /**
   * Checks if the ghosting overlay should be visible.
   *
   * @return true if ghosting overlay is active
   */
  public boolean isGhostingActive() {
    return is_ghosting_active;
  }
}
