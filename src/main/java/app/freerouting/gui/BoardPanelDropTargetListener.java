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
 * Allows users to drop DSN, JSON, or FRB files onto the panel to open them.
 *
 * <p>Note: Currently only DSN and JSON formats are supported. FRB format support
 * (with GUI state restoration) is planned for Phase 2.</p>
 *
 * <p>Visual feedback is provided during drag operations: the cursor changes
 * to indicate a drop is in progress.</p>
 *
 * <p>Note: SessionManager enforces single GUI session per process. Multi-session
 * support may require architectural changes to allow parallel boards.</p>
 */
public class BoardPanelDropTargetListener implements java.awt.dnd.DropTargetListener {

  private final BoardPanel board_panel;
  private boolean is_drag_active = false;

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
      JOptionPane.showMessageDialog(board_panel, "Error processing dropped file: " + e.getMessage(),
          "Error", JOptionPane.ERROR_MESSAGE);
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
        }
      }

      // Only process DSN and JSON for Phase 1
      if (format == FileFormat.DSN || format == FileFormat.JSON) {
        if (!file_loaded) {
          // Load the first valid file
          board_panel.board_frame.loadDroppedFile(file);
          FRAnalytics.buttonClicked("file_dropped", file.getName());
          file_loaded = true;
        } else {
          // Log additional valid files for future multi-board support
          FRLogger.warn(
              "Additional dropped file ignored: '" + file.getName() + 
              "'. Multi-board support is planned for future versions.");
        }
      } else if (format == FileFormat.FRB) {
        // FRB support is Phase 2
        FRLogger.warn(
            "FRB file dropped: '" + file.getName() + 
            "'. FRB format support (with GUI state restoration) is planned for Phase 2.");
      } else {
        // Unknown format
        FRLogger.warn(
            "Dropped file format not supported: '" + file.getName() + 
            "'. Supported formats: DSN, JSON (FRB in Phase 2).");
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
      // Store original cursor for restoration later
      board_panel.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    } else {
      // Restore original cursor
      board_panel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }
  }
}