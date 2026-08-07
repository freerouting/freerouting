package app.freerouting.gui;

import app.freerouting.Freerouting;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.boardgraphics.TutorialBoardPalette;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.RulesWriter;
import app.freerouting.io.FileFormat;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.RatsNest;
import app.freerouting.interactive.ScreenMessages;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Graphical frame containing the Menu, Toolbar, Canvas and Status bar.
 */
public class BoardFrame extends WindowBase {

  private static final String TUTORIAL_BOARD_FILENAME = "tutorial_board.dsn";

  public static volatile BoardFrame activeFrame = null;

  /**
   * The windows above stored in an array
   */
  static final int SUBWINDOW_COUNT = 24;

  static final String GUI_DEFAULTS_FILE_NAME = "gui_defaults.par";
  static final String GUI_DEFAULTS_FILE_BACKUP_NAME = "gui_defaults.par.bak";

  /**
   * The current routing job (design) being edited.
   */
  public RoutingJob routingJob;
  /**
   * The menubar of this frame.
   */
  public final BoardMenuBar menubar;
  /**
   * The scroll pane for the panel of the routing board.
   */
  final JScrollPane scroll_pane;
  /**
   * Handles displaying messages to the user.
   */
  final ScreenMessages screen_messages;
  /**
   * The main toolbar panel containing common tools.
   */
  private final BoardToolbar toolbar_panel;
  /**
   * The toolbar used in the inspected item state (when items are selected).
   * Note: This field is used by InspectedItemState.
   */
  private final JToolBar inspect_toolbar;
  /**
   * The panel with the message line/status bar.
   */
  private final BoardPanelStatus message_panel;
  private final Locale locale;
  private final SettingsMerger settingsMerger;
  private final List<Consumer<RoutingBoard>> boardLoadedEventListeners = new ArrayList<>();
  private final List<Consumer<RoutingBoard>> boardSavedEventListeners = new ArrayList<>();
  private final BoardObservers board_observers;
  private final String freerouting_version;
  /**
   * The panel with the graphical representation of the board.
   */
  BoardPanel board_panel;

  // -- Subwindows for various settings and tools --
  WindowAbout about_window;
  WindowRouteParameter route_parameter_window;
  WindowAutorouteParameter autoroute_parameter_window;
  WindowSelectParameter select_parameter_window;
  WindowMoveParameter move_parameter_window;
  WindowClearanceMatrix clearance_matrix_window;
  WindowVia via_window;
  WindowEditVias edit_vias_window;
  WindowNetClasses edit_net_rules_window;
  WindowAssignNetClass assign_net_classes_window;
  WindowPadstacks padstacks_window;
  WindowPackages packages_window;
  WindowIncompletes incompletes_window;
  WindowNets net_info_window;
  WindowClearanceViolations clearance_violations_window;
  WindowLengthViolations length_violations_window;
  WindowUnconnectedRoute unconnected_route_window;
  WindowRouteStubs route_stubs_window;
  WindowComponents components_window;
  WindowVisibility visibility_window;
  WindowDisplayMisc display_misc_window;

  ColorManager color_manager;

  /**
   * Array storing references to all "permanent" subwindows (tool windows that
   * persist).
   * This array allows for collective operations like saving/restoring positions
   * and refreshing.
   */
  BoardSavableSubWindow[] permanent_subwindows = new BoardSavableSubWindow[SUBWINDOW_COUNT];
  Collection<BoardTemporarySubWindow> temporary_subwindows = new LinkedList<>();
  private LogEntries.LogEntryAddedListener log_entry_added_listener;

  /**
   * Creates a new BoardFrame that is the GUI element containing the Menu,
   * Toolbar, Canvas and Status bar.
   */
  public BoardFrame(RoutingJob p_design, GlobalSettings globalSettings, SettingsMerger settingsMerger) {
    this(p_design, new BoardObserverAdaptor(), globalSettings, settingsMerger);
  }

  /**
   * Creates new form BoardFrame.
   */
  BoardFrame(RoutingJob routingJob, BoardObservers boardObservers, GlobalSettings globalSettings, SettingsMerger settingsMerger) {
    super(800, 150);
    activeFrame = this;

    this.routingJob = routingJob;
    this.settingsMerger = settingsMerger;
    this.board_observers = boardObservers;
    this.locale = globalSettings.currentLocale;
    this.setLanguage(this.locale);
    this.freerouting_version = globalSettings.version;

    // Set the menu bar of this frame.
    this.menubar = new BoardMenuBar(this, globalSettings.featureFlags);

    this.menubar.fileMenu.addOpenEventListener((File selectedFile) -> {
      if (selectedFile == null) {
        // There was no file selected in the dialog, so we do nothing
        return;
      }

      // Let's categorize the file based on its extension
      try {
        routingJob.setInput(selectedFile);
        if (routingJob.input.format == FileFormat.UNKNOWN) {
          // The file is not in a valid format
          FRLogger.warn("The input file format was not recognised.");
          return;
        }
      } catch (Exception e) {
        FRLogger.error("There was an error while reading the input file.", e);
        return;
      }

      if (routingJob.input.getFile() != null) {
        final byte[] fileContent = routingJob.input.getData().readAllBytes();
        final FileFormat inputFormat = routingJob.input.format;

        javax.swing.SwingUtilities.invokeLater(() -> {
          String sessionId = SessionManager
              .getInstance()
              .getGuiSession().id.toString();
          RoutingJobScheduler
              .getInstance()
              .clearJobs(sessionId);
          RoutingJobScheduler
              .getInstance()
              .enqueueJob(routingJob);

          String oldInputDirectory = globalSettings.guiSettings.inputDirectory;
          globalSettings.guiSettings.inputDirectory = routingJob.input.getDirectoryPath();
          if (!oldInputDirectory.equals(globalSettings.guiSettings.inputDirectory)) {
            try {
              GlobalSettings.saveAsJson(globalSettings);
            } catch (IOException e) {
              FRLogger.error("Couldn't save the global settings to the configuration file", e);
            }
          }
          try {
            GlobalSettings.setDefaultValue("gui.input_directory", routingJob.input.getDirectoryPath());
          } catch (Exception e) {
            FRLogger.error("Couldn't update the input directory in the configuration file", e);
          }
        });

        if (board_panel != null && board_panel.board_handling != null) {
          switch (inputFormat) {
            case DSN:
              loadFromBytesAsync(fileContent, FileFormat.DSN, routingJob);
              FRAnalytics.buttonClicked("fileio_loaddsn", this.routingJob.getInputFileDetails());
              break;
            case KICAD_DESIGN_JSON:
              loadFromBytesAsync(fileContent, FileFormat.KICAD_DESIGN_JSON, routingJob);
              FRAnalytics.buttonClicked("fileio_loadjson", this.routingJob.getInputFileDetails());
              break;
            case FRB:
              if (!this.load(new ByteArrayInputStream(fileContent), FileFormat.FRB, null, routingJob)) {
                restoreTutorialBoardAfterFailedLoad(null);
              }
              FRAnalytics.buttonClicked("fileio_loadfrb", this.routingJob.getInputFileDetails());
              break;
            default:
              FRLogger.warn("Loading the board failed, because the selected file format is not supported.");
              break;
          }
        }
      }
    });

    this.menubar.fileMenu.addSaveAsEventListener((File selectedFile) -> {
      if (selectedFile == null) {
        // There was no file selected in the dialog, so we do nothing
        return;
      }

      // Let's categorize the file based on its extension
      if (!routingJob.tryToSetOutputFile(selectedFile)) {
        // The file is not in a valid format
        return;
      }

      switch (routingJob.output.format) {
        case SES:
          // Save the file as a Specctra SES file
          boolean sesFileSaved = this.saveAsSpecctraSessionSes(this.routingJob.output.getFile(),
              this.routingJob.input.getFilename());
          // Save the rules file as well, if the user wants to
          if (sesFileSaved && WindowMessage.confirm(tm.getText("confirm_rules_save"), JOptionPane.NO_OPTION)) {
            saveRulesAs(this.routingJob.getRulesFile(), this.routingJob.input.getFilename(),
                board_panel.board_handling);
          }
          FRAnalytics.fileSaved("SES", this.routingJob.getOutputFileDetails());
          FRAnalytics.buttonClicked("fileio_saveses", this.routingJob.getOutputFileDetails());
          break;
        case KICAD_SESSION_JSON:
          // Save the file as a KiCad session JSON file
          this.saveAsKiCadJson(this.routingJob.output.getFile(), this.routingJob.input.getFilename());
          FRAnalytics.fileSaved("KICAD_SESSION_JSON", this.routingJob.getOutputFileDetails());
          FRAnalytics.buttonClicked("fileio_savekicadjson", this.routingJob.getOutputFileDetails());
          break;
        case DSN:
          // Save the file as a Specctra DSN file
          this.saveAsSpecctraDesignDsn(this.routingJob.output.getFile(), this.routingJob.input.getFilename(), false);
          FRAnalytics.fileSaved("DSN", this.routingJob.getOutputFileDetails());
          FRAnalytics.buttonClicked("fileio_savedsn", this.routingJob.getOutputFileDetails());
          break;
        case FRB:
          // Save the file as a freerouting binary file
          // The binary data is captured into routingJob.output.data during serialization
          // so it can be reused without re-serializing
          this.saveAsBinary(this.routingJob.output.getFile());
          FRAnalytics.fileSaved("FRB", this.routingJob.getOutputFileDetails());
          FRAnalytics.buttonClicked("fileio_savefrb", this.routingJob.getOutputFileDetails());
          break;
        case SCR:
          // Save the file as an Eagle script file
          this.saveAsEagleScriptScr(this.routingJob.getEagleScriptFile(), this.routingJob.input.getFilename());
          FRAnalytics.fileSaved("SCR", this.routingJob.input.getFilename());
          FRAnalytics.buttonClicked("fileio_savescr", "");
          break;
        default:
          // The file format is not supported
          FRLogger.warn("Saving the board failed, because the selected file format is not supported.");
          break;
      }
    });

    setJMenuBar(this.menubar);

    // Set the toolbar panel to the top of the frame, just above the canvas.
    this.toolbar_panel = new BoardToolbar(this, !globalSettings.featureFlags.inspectionMode);
    this.add(this.toolbar_panel, BorderLayout.NORTH);

    // Create and move the status bar one-liners (like current layer, cursor
    // position, etc.) below the canvas.
    this.message_panel = new BoardPanelStatus(this.locale);
    this.add(this.message_panel, BorderLayout.SOUTH);

    this.message_panel.addErrorOrWarningLabelClickedListener(() -> {
      LogEntries logEntries = FRLogger.getLogEntries();

      // Filter the log entries that are not errors or warnings
      LogEntries filteredLogEntries = new LogEntries();
      for (LogEntry entry : logEntries.getEntries(null, null)) {
        if (entry.getType() == LogEntryType.Error || entry.getType() == LogEntryType.Warning
            || entry.getType() == LogEntryType.Info) {
          filteredLogEntries.add(entry.getType(), entry.getMessage(), entry.getTopic());
        }
      }

      // Show a dialog box with the latest log entries
      JTextArea textArea = new JTextArea(filteredLogEntries.getAsString());
      JScrollPane scrollPane = new JScrollPane(textArea);
      scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
      scrollPane.setPreferredSize(new Dimension(1000, 600));

      // Append the new log entries to the text area
      log_entry_added_listener = (LogEntry logEntry) -> {
        var type = logEntry.getType();
        if (type == LogEntryType.Error || type == LogEntryType.Warning || type == LogEntryType.Info) {
          textArea.append(logEntry + "\n");
        }
      };
      logEntries.addLogEntryAddedListener(log_entry_added_listener);

      int messageType = filteredLogEntries.getErrorCount() > 0 ? JOptionPane.ERROR_MESSAGE
          : JOptionPane.WARNING_MESSAGE;

      JOptionPane.showMessageDialog(null, scrollPane, tm.getText("logs_window_title"), messageType);
    });

    // Toolbar for inspected items (e.g. when a component is selected)
    this.inspect_toolbar = new BoardToolbarInspectedItem(this);

    // Screen messages are displayed in the status bar, below the canvas.
    this.screen_messages = new ScreenMessages(this.message_panel.errorLabel, this.message_panel.warningLabel,
        this.message_panel.statusMessage, this.message_panel.additionalMessage,
        this.message_panel.currentLayer, this.message_panel.currentBoardScore, this.message_panel.mousePosition,
        this.message_panel.unitLabel, this.locale);

    // The scroll pane for the canvas of the routing board.
    this.scroll_pane = new JScrollPane();
    this.scroll_pane.setPreferredSize(new Dimension(1150, 800));
    this.scroll_pane.setVerifyInputWhenFocusTarget(false);
    this.add(scroll_pane, BorderLayout.CENTER);

    this.board_panel = new BoardPanel(screen_messages, this, globalSettings, routingJob, settingsMerger);
    this.scroll_pane.setViewportView(board_panel);

    this.addWindowListener(new WindowStateListener());

    this.addBoardLoadedEventListener((RoutingBoard board) -> {
      boolean isBoardEmpty = (board == null) || (board.components.count() == 0);
      this.menubar.fileMenu.file_save_as_menuitem.setEnabled(!isBoardEmpty);
      this.menubar.appereanceMenu.setEnabled(!isBoardEmpty);
      this.menubar.settingsMenu.setEnabled(!isBoardEmpty);
      this.menubar.rulesMenu.setEnabled(!isBoardEmpty);
      this.menubar.infoMenu.setEnabled(!isBoardEmpty);

      this.toolbar_panel.setEnabled(!isBoardEmpty);
    });

    this.updateTexts();
    this.pack();
  }

  /**
   * Loads a RoutingBoard natively (e.g. from an API session) without file transfer overhead,
   * rendering it instantly in the GUI for real-time monitoring.
   */
  public void loadBoardNatively(RoutingBoard board, RoutingJob job) {
    if (board == null) {
      this.updateTexts();
      return;
    }
    this.routingJob = job;
    board_panel.reset_board_handling(job);
    board_panel.board_handling.replaceRoutingBoard(board);

    // Close other child windows
    for (int i = 0; i < this.permanent_subwindows.length; i++) {
      if (this.permanent_subwindows[i] != null) {
        this.permanent_subwindows[i].dispose();
        this.permanent_subwindows[i] = null;
      }
    }

    // Initialize standard state
    int boardLayerCount = board.get_layer_count();
    this.routingJob.routerSettings.setLayerCount(boardLayerCount);
    this.routingJob.routerSettings.applyBoardSpecificOptimizations(board);

    if (this.settingsMerger != null) {
      var mergedSettings = this.settingsMerger.merge();
      this.routingJob.setSettings(mergedSettings);
      var interactiveSettings = board_panel.board_handling.getInteractiveSettings();
      if (interactiveSettings != null) {
        interactiveSettings.setSettings(this.routingJob.routerSettings);
      }
    }

    initialize_windows();
    this.boardLoadedEventListeners.forEach(listener -> listener.accept(board));
    this.refresh_windows();
    this.updateTexts();
    this.repaint();
  }

  @Override
  public void updateTexts() {
    String boardName = null;
    if (this.routingJob != null) {
      if (this.routingJob.input != null) {
        String filename = this.routingJob.input.getFilename();
        if (filename != null && !filename.isBlank() && !filename.equals("tutorial_board.dsn") && !filename.equals("empty_board.dsn")) {
          boardName = filename;
        }
      }
      if (boardName == null && this.routingJob.name != null && !this.routingJob.name.isBlank() && !this.routingJob.name.startsWith("J-")) {
        boardName = this.routingJob.name;
      }
    }

    String appTitle = tm.getText("title", this.freerouting_version);
    if (boardName != null && !boardName.isBlank()) {
      this.setTitle(boardName + " - " + appTitle);
    } else {
      this.setTitle(appTitle);
    }
  }

  /**
   * Parses a design file on a background thread, then completes GUI setup on the EDT in phases.
   * Keeps the BoardFrame and tool windows responsive during DSN/JSON parsing and heavy post-load work.
   */
  private void loadFromBytesAsync(byte[] fileContent, FileFormat format, RoutingJob job) {
    ensureGeneralSettingsVisibleDuringLoad();

    String filename = job.input != null ? job.input.getFilename() : null;
    TextManager guiTm = new TextManager(GuiManager.class, locale);
    String loadingMessage = filename != null
        ? guiTm.getText("loading_design_with_file", filename)
        : guiTm.getText("loading_design");
    WindowMessage loadingWindow = WindowMessage.show(loadingMessage);
    loadingWindow.setLocationRelativeTo(this);

    Thread.ofVirtual().name("gui-board-load").start(() -> {
      long parseStart = System.nanoTime();
      BoardReadResult readResult = parseBoardFromBytes(fileContent, format, filename);
      long parseMs = (System.nanoTime() - parseStart) / 1_000_000L;
      FRLogger.debug("Board load: DSN/JSON parse completed in " + parseMs + " ms"
          + (filename != null ? " ('" + filename + "')" : ""));

      javax.swing.SwingUtilities.invokeLater(
          () -> finishLoadFromParseResult(readResult, fileContent, format, job, loadingWindow));
    });
  }

  private static BoardReadResult parseBoardFromBytes(byte[] fileContent, FileFormat format, String filename) {
    try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
      if (format == FileFormat.DSN) {
        return DsnReader.readBoard(inputStream, null, new ItemIdentificationNumberGenerator(), filename);
      }
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        try (java.io.Reader reader = new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
          return KiCadJsonReader.readBoard(reader, null, new ItemIdentificationNumberGenerator());
        }
      }
      throw new IllegalArgumentException("Unsupported format for async load: " + format);
    } catch (Exception e) {
      FRLogger.error("Failed to parse board file", e);
      return new BoardReadResult.IoError(new IOException("Failed to parse board file", e));
    }
  }

  private void ensureGeneralSettingsVisibleDuringLoad() {
    if (board_panel == null || board_panel.board_handling == null
        || board_panel.board_handling.graphics_context == null) {
      return;
    }
    allocateEssentialSubwindows();
    this.select_parameter_window.setLocation(0, 0);
    this.select_parameter_window.setVisible(true);
    this.select_parameter_window.toFront();
  }

  private void disposePermanentSubwindows() {
    for (int i = 0; i < this.permanent_subwindows.length; i++) {
      if (this.permanent_subwindows[i] != null) {
        this.permanent_subwindows[i].dispose();
        this.permanent_subwindows[i] = null;
      }
    }
    select_parameter_window = null;
    color_manager = null;
    visibility_window = null;
    display_misc_window = null;
    route_parameter_window = null;
    autoroute_parameter_window = null;
    move_parameter_window = null;
    clearance_matrix_window = null;
    via_window = null;
    edit_vias_window = null;
    edit_net_rules_window = null;
    assign_net_classes_window = null;
    padstacks_window = null;
    packages_window = null;
    components_window = null;
    incompletes_window = null;
    clearance_violations_window = null;
    length_violations_window = null;
    net_info_window = null;
    unconnected_route_window = null;
    route_stubs_window = null;
    about_window = null;
  }

  private void finishLoadFromParseResult(BoardReadResult readResult, byte[] fileContent, FileFormat format,
      RoutingJob routingJob, WindowMessage loadingWindow) {
    long attachStart = System.nanoTime();
    boolean scheduleInitialPaint = false;
    try {
      if (!attachParsedBoard(readResult, fileContent, format, routingJob)) {
        FRLogger.warn("Loading the board file failed. Restoring " + TUTORIAL_BOARD_FILENAME + ".");
        restoreTutorialBoardAfterFailedLoad(loadingWindow);
        return;
      }

      if (readResult instanceof BoardReadResult.Success) {
        initialize_windows(true);
        board_panel.board_handling.refreshGuiFromSettings();
        update_gui(format, readResult, new Point(0, 0), null, true);
        scheduleBackgroundRatsNestBuild();
        scheduleInitialPaint = true;
      }
    } catch (Exception e) {
      FRLogger.error("Failed to attach loaded board", e);
      restoreTutorialBoardAfterFailedLoad(loadingWindow);
      return;
    } finally {
      long attachMs = (System.nanoTime() - attachStart) / 1_000_000L;
      FRLogger.debug("Board load: GUI attach completed in " + attachMs + " ms");
      if (!scheduleInitialPaint) {
        loadingWindow.dispose();
      }
    }

    if (scheduleInitialPaint) {
      scheduleInitialBoardPaint(loadingWindow, format, readResult);
    } else {
      javax.swing.SwingUtilities.invokeLater(() -> completeHeavyGuiAfterLoad(format, readResult));
    }
  }

  /**
   * Shows rendering feedback, paints the board with fast simplified plane fills, then warms detailed
   * plane geometry on a background thread before a full-quality repaint.
   */
  private void scheduleInitialBoardPaint(WindowMessage loadingWindow, FileFormat format, BoardReadResult readResult) {
    var graphicsContext = board_panel.board_handling.graphics_context;
    String renderingStatus = tm.getText("rendering_board");
    board_panel.showRenderingOverlay(renderingStatus);
    screen_messages.set_status_message(renderingStatus);
    graphicsContext.setSimplifiedPlaneRendering(true);

    javax.swing.SwingUtilities.invokeLater(() -> {
      board_panel.paintImmediately(0, 0, board_panel.getWidth(), board_panel.getHeight());

      javax.swing.SwingUtilities.invokeLater(() -> {
        long paintStart = System.nanoTime();
        try {
          if (loadingWindow != null) {
            loadingWindow.dispose();
          }
          this.zoom_all();
          board_panel.repaint();
        } finally {
          long paintMs = (System.nanoTime() - paintStart) / 1_000_000L;
          FRLogger.debug("Board load: first paint completed in " + paintMs + " ms");
          board_panel.clearRenderingOverlay();
          graphicsContext.setSimplifiedPlaneRendering(false);
          this.updateTexts();
        }
        schedulePlaneFillCacheWarm();
        javax.swing.SwingUtilities.invokeLater(() -> completeHeavyGuiAfterLoad(format, readResult));
      });
    });
  }

  private void schedulePlaneFillCacheWarm() {
    RoutingBoard board = board_panel.board_handling.get_routing_board();
    if (board == null) {
      return;
    }
    var conductionAreas = board.get_conduction_areas();
    if (conductionAreas.isEmpty()) {
      return;
    }
    Thread.ofVirtual().name("plane-fill-cache-warm").start(() -> {
      long warmStart = System.nanoTime();
      for (app.freerouting.board.ConductionArea area : conductionAreas) {
        area.warmDetailedFillCache();
      }
      long warmMs = (System.nanoTime() - warmStart) / 1_000_000L;
      FRLogger.debug("Board load: plane fill cache warmed in " + warmMs + " ms");
      javax.swing.SwingUtilities.invokeLater(() -> {
        if (board_panel.board_handling.get_routing_board() == board) {
          board_panel.repaint();
        }
      });
    });
  }

  private boolean attachParsedBoard(BoardReadResult readResult, byte[] fileContent, FileFormat format,
      RoutingJob routingJob) {
    if (!canAttachParsedBoard(readResult)) {
      showBoardLoadError(readResult);
      return false;
    }

    this.routingJob = routingJob;
    board_panel.reset_board_handling(routingJob);
    disposePermanentSubwindows();

    String inputFilename = routingJob.input != null ? routingJob.input.getFilename() : null;
    String analyticsFormat = format == FileFormat.KICAD_DESIGN_JSON ? "KICAD_JSON" : "DSN";
    board_panel.board_handling.applyParsedBoardResult(readResult, inputFilename, analyticsFormat);

    if (readResult instanceof BoardReadResult.Success) {
      RoutingBoard board = board_panel.board_handling.get_routing_board();
      if (this.settingsMerger != null) {
        if (format == FileFormat.DSN && inputFilename != null) {
          this.settingsMerger.addOrReplaceSources(new DsnFileSettings(new ByteArrayInputStream(fileContent), inputFilename));
        }
        var mergedSettings = this.settingsMerger.merge();
        int boardLayerCount = board.get_layer_count();
        if (mergedSettings.getLayerCount() == 0 || mergedSettings.getLayerCount() != boardLayerCount) {
          mergedSettings.setLayerCount(boardLayerCount);
        }
        mergedSettings.applyBoardSpecificOptimizations(board);
        this.routingJob.setSettings(mergedSettings);
        var interactiveSettings = board_panel.board_handling.getInteractiveSettings();
        if (interactiveSettings != null) {
          interactiveSettings.setSettings(mergedSettings);
        }
      }

      this.boardLoadedEventListeners
          .forEach(listener -> listener.accept(board_panel.board_handling.get_routing_board()));
      return true;
    }

    return readResult instanceof BoardReadResult.OutlineMissing;
  }

  private static boolean canAttachParsedBoard(BoardReadResult readResult) {
    if (readResult instanceof BoardReadResult.Success) {
      return true;
    }
    if (readResult instanceof BoardReadResult.OutlineMissing outlineMissing) {
      return outlineMissing.board() != null;
    }
    return false;
  }

  private void showBoardLoadError(BoardReadResult readResult) {
    if (readResult instanceof BoardReadResult.OutlineMissing) {
      screen_messages.set_status_message(tm.getText("error_dsn_outline_missing"));
    } else if (readResult instanceof BoardReadResult.IoError || readResult instanceof BoardReadResult.ParseError) {
      screen_messages.set_status_message(tm.getText("error_dsn_read_failed"));
    } else {
      screen_messages.set_status_message(tm.getText("error_design_file_read_failed"));
    }
    refreshLogCountsInToolbar();
  }

  /**
   * Reloads the default tutorial design after a failed user-initiated load, without clearing log entries.
   *
   * @return {@code true} when the tutorial board was attached and initial paint was scheduled
   */
  private boolean restoreTutorialBoardAfterFailedLoad(WindowMessage loadingWindow) {
    refreshLogCountsInToolbar();
    try (InputStream tutorialStream = BoardFrame.class.getClassLoader().getResourceAsStream(TUTORIAL_BOARD_FILENAME)) {
      if (tutorialStream == null) {
        FRLogger.error("Could not restore " + TUTORIAL_BOARD_FILENAME + ": classpath resource missing", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        return false;
      }
      byte[] tutorialBytes = tutorialStream.readAllBytes();
      BoardReadResult tutorialResult = parseBoardFromBytes(tutorialBytes, FileFormat.DSN, TUTORIAL_BOARD_FILENAME);
      if (!(tutorialResult instanceof BoardReadResult.Success)) {
        FRLogger.error("Could not restore " + TUTORIAL_BOARD_FILENAME + " after a failed load", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        refreshLogCountsInToolbar();
        return false;
      }

      routingJob.setDummyInputFile(TUTORIAL_BOARD_FILENAME);
      routingJob.input.setData(tutorialBytes);

      if (!attachParsedBoard(tutorialResult, tutorialBytes, FileFormat.DSN, routingJob)) {
        FRLogger.error("Failed to attach " + TUTORIAL_BOARD_FILENAME + " after a failed load", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        refreshLogCountsInToolbar();
        return false;
      }

      FRLogger.info("Restored " + TUTORIAL_BOARD_FILENAME + " after a failed board load");
      initialize_windows(true);
      board_panel.board_handling.refreshGuiFromSettings();
      applyTutorialBoardPalette();
      update_gui(FileFormat.DSN, tutorialResult, new Point(0, 0), null, true);
      scheduleBackgroundRatsNestBuild();
      scheduleInitialBoardPaint(loadingWindow, FileFormat.DSN, tutorialResult);
      refreshLogCountsInToolbar();
      return true;
    } catch (IOException e) {
      FRLogger.error("Could not restore " + TUTORIAL_BOARD_FILENAME + " after a failed load", e);
      if (loadingWindow != null) {
        loadingWindow.dispose();
      }
      refreshLogCountsInToolbar();
      return false;
    }
  }

  private void applyTutorialBoardPalette() {
    TutorialBoardPalette.apply(board_panel.board_handling.graphics_context);
    board_panel.setBackground(TutorialBoardPalette.backgroundColor());
  }

  private void refreshLogCountsInToolbar() {
    LogEntries entries = FRLogger.getLogEntries();
    screen_messages.set_error_and_warning_count(entries.getErrorCount(), entries.getWarningCount());
  }

  private void scheduleBackgroundRatsNestBuild() {
    RoutingBoard board = board_panel.board_handling.get_routing_board();
    if (board == null) {
      return;
    }
    Thread.ofVirtual().name("gui-ratsnest-build").start(() -> {
      long ratsNestStart = System.nanoTime();
      RatsNest prepared = new RatsNest(board);
      long ratsNestMs = (System.nanoTime() - ratsNestStart) / 1_000_000L;
      FRLogger.debug("Board load: rats nest built in " + ratsNestMs + " ms");
      javax.swing.SwingUtilities.invokeLater(() -> {
        if (board_panel.board_handling.get_routing_board() == board) {
          board_panel.board_handling.attachPreparedRatsNest(prepared);
        }
      });
    });
  }

  private void completeHeavyGuiAfterLoad(FileFormat format, BoardReadResult readResult) {
    if (!(readResult instanceof BoardReadResult.Success)) {
      return;
    }
    board_panel.create_popup_menus();
    if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
      InputStream input_stream = null;
      boolean defaults_file_found;
      File defaults_file = new File(this.routingJob.input.getAbsolutePath(), GUI_DEFAULTS_FILE_NAME);
      defaults_file_found = true;
      try {
        input_stream = new FileInputStream(defaults_file);
      } catch (FileNotFoundException _) {
        defaults_file_found = false;
      }
      if (defaults_file_found) {
        boolean read_ok = GUIDefaultsFile.read(this, board_panel.board_handling, input_stream);
        if (!read_ok) {
          screen_messages.set_status_message(tm.getText("error_gui_defaults_read_failed"));
        }
        try {
          if (input_stream != null) {
            input_stream.close();
          }
        } catch (IOException _) {
          return;
        }
        this.zoom_all();
        board_panel.repaint();
      }
    }
    if (TutorialBoardPalette.isTutorialBoard(routingJob.input.getFilename())) {
      applyTutorialBoardPalette();
    }
    this.updateTexts();
  }

  /**
   * Reads an existing board design from file. If format is DSN or JSON, the design is
   * read from a specctra dsn / kicad json file. Returns false, if the file is invalid.
   */
  boolean load(InputStream inputStream, FileFormat format, JTextField p_message_field, RoutingJob routingJob) {
    Point viewport_position = null;
    BoardReadResult read_result = null;

    board_panel.reset_board_handling(routingJob);
    disposePermanentSubwindows();

    if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        read_result = board_panel.board_handling.loadFromKiCadJson(inputStream, this.board_observers,
            new ItemIdentificationNumberGenerator());
      } else {
        read_result = board_panel.board_handling.loadFromSpecctraDsn(inputStream, this.board_observers,
            new ItemIdentificationNumberGenerator());
      }

      // If the file was read successfully, initialize the windows
      if (read_result instanceof BoardReadResult.Success) {
        viewport_position = new Point(0, 0);

        // Initialize the RouterSettings layer count to match the loaded board
        RoutingBoard board = board_panel.board_handling.get_routing_board();
        int boardLayerCount = board.get_layer_count();

        if (this.routingJob.routerSettings.getLayerCount() == 0 ||
            this.routingJob.routerSettings.getLayerCount() != boardLayerCount) {
          // Initialize layer arrays and apply board-specific optimizations
          this.routingJob.routerSettings.setLayerCount(boardLayerCount);
          this.routingJob.routerSettings.applyBoardSpecificOptimizations(board);
        }

        // Merge all settings sources (DefaultSettings, DsnFileSettings, CliSettings, …)
        // so that routerSettings has fully-populated non-null values before any GUI window
        // tries to read fields like scoring.via_costs.  Without this step the windows would
        // NPE on the first access to any nullable RouterSettings field.
        if (this.settingsMerger != null) {
          var mergedSettings = this.settingsMerger.merge();
          this.routingJob.setSettings(mergedSettings);
          var interactiveSettings = board_panel.board_handling.getInteractiveSettings();
          if (interactiveSettings != null) {
            interactiveSettings.setSettings(this.routingJob.routerSettings);
          }
        }

        initialize_windows();

        // Raise an event to notify the observers that a new board has been loaded
        this.boardLoadedEventListeners
            .forEach(listener -> listener.accept(board_panel.board_handling.get_routing_board()));
      }
    } else {
      ObjectInputStream object_stream;
      try {
        object_stream = new ObjectInputStream(inputStream);
      } catch (IOException _) {
        this.updateTexts();
        return false;
      }
      boolean read_ok = board_panel.board_handling.loadFromBinary(object_stream);
      if (!read_ok) {
        this.updateTexts();
        return restoreTutorialBoardAfterFailedLoad(null);
      }

      // Raise an event to notify the observers that a new board has been loaded
      this.boardLoadedEventListeners
          .forEach(listener -> listener.accept(board_panel.board_handling.get_routing_board()));

      // Read and set the GUI settings from the binary file
      Point frame_location;
      Rectangle frame_bounds;
      try {
        viewport_position = (Point) object_stream.readObject();
        frame_location = (Point) object_stream.readObject();
        frame_bounds = (Rectangle) object_stream.readObject();
      } catch (Exception _) {
        this.updateTexts();
        return false;
      }
      this.setLocation(frame_location);
      this.setBounds(frame_bounds);

      allocate_permanent_subwindows();

      for (int i = 0; i < this.permanent_subwindows.length; i++) {
        if (this.permanent_subwindows[i] != null) {
          this.permanent_subwindows[i].read(object_stream);
        }
      }
    }

    try {
      inputStream.close();
    } catch (IOException _) {
      this.updateTexts();
      return restoreTutorialBoardAfterFailedLoad(null);
    }

    boolean guiUpdated = update_gui(format, read_result, viewport_position, p_message_field, false);
    if (!guiUpdated) {
      return restoreTutorialBoardAfterFailedLoad(null);
    }
    return true;
  }

  private boolean update_gui(FileFormat format, BoardReadResult read_result, Point viewport_position,
      JTextField p_message_field, boolean deferHeavyWork) {
    boolean isTextDsnOrJson = (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON);
    if (isTextDsnOrJson) {
      if (!(read_result instanceof BoardReadResult.Success)) {
        if (p_message_field != null) {
          if (read_result instanceof BoardReadResult.OutlineMissing) {
            p_message_field.setText(tm.getText("error_dsn_outline_missing"));
          } else {
            p_message_field.setText(tm.getText("error_dsn_read_failed"));
          }
        }
        this.updateTexts();
        return false;
      }
    }

    Dimension panel_size = board_panel.board_handling.graphics_context.get_panel_size();
    board_panel.setSize(panel_size);
    board_panel.setPreferredSize(panel_size);
    if (viewport_position != null) {
      board_panel.set_viewport_position(viewport_position);
    }
    if (!deferHeavyWork) {
      board_panel.create_popup_menus();
    }
    board_panel.init_colors();
    if (!deferHeavyWork) {
      board_panel.board_handling.create_ratsnestIfAbsent();
    }
    this.setToolbarModeSelectionPanelValue(board_panel.board_handling.get_interactive_state());
    this.setToolbarUnitSelectionPanelValue(board_panel.board_handling.coordinate_transform.user_unit);
    this.setVisible(true);
    if (isTextDsnOrJson) {
      if (!deferHeavyWork) {
        // Read the default gui settings, if gui default file exists.
        InputStream input_stream = null;
        boolean defaults_file_found;

        File defaults_file = new File(this.routingJob.input.getAbsolutePath(), GUI_DEFAULTS_FILE_NAME);
        defaults_file_found = true;
        try {
          input_stream = new FileInputStream(defaults_file);
        } catch (FileNotFoundException _) {
          defaults_file_found = false;
        }

        if (defaults_file_found) {
          boolean read_ok = GUIDefaultsFile.read(this, board_panel.board_handling, input_stream);
          if (!read_ok) {
            screen_messages.set_status_message(tm.getText("error_gui_defaults_read_failed"));
          }
          try {
            input_stream.close();
          } catch (IOException _) {
            return false;
          }
        }
        this.zoom_all();
        board_panel.repaint();
      }
    }
    if (!deferHeavyWork) {
      this.updateTexts();
    }
    return true;
  }

  private boolean update_gui(FileFormat format, BoardReadResult read_result, Point viewport_position,
      JTextField p_message_field) {
    return update_gui(format, read_result, viewport_position, p_message_field, false);
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a version-specific
   * binary stream. Returns false, if the save failed.
   */
  private boolean saveAsBinary(OutputStream outputStream) throws Exception {
    ObjectOutputStream objectStream;
    objectStream = new ObjectOutputStream(outputStream);

    // (1) Save the board as binary file
    boolean save_ok = board_panel.board_handling.saveAsBinary(objectStream);
    if (!save_ok) {
      return false;
    }

    // (2) Save the GUI settings as binary file
    objectStream.writeObject(board_panel.get_viewport_position());
    objectStream.writeObject(this.getLocation());
    objectStream.writeObject(this.getBounds());

    // (3) Save the permanent subwindows as binary file
    for (int i = 0; i < this.permanent_subwindows.length; i++) {
      if (this.permanent_subwindows[i] != null) {
        this.permanent_subwindows[i].save(objectStream);
      }
    }

    // (4) Flush the binary file
    objectStream.flush();
    return true;
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a binary file.
   * Returns false, if the save failed.
   */
  private boolean saveAsBinary(File outputFile) {
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
      this.routingJob.output.setData(data);

      // Write to the file
      try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
        fileOutputStream.write(data);
      }

      screen_messages.set_status_message(tm.getText("message_binary_file_saved", outputFile.getPath()));
      return true;
    } catch (Exception _) {
      screen_messages.set_status_message(tm.getText("message_binary_file_save_failed", outputFile.getPath()));
      return false;
    }
  }

  /**
   * Writes a Specctra Session File (SES). Returns false, if write operation
   * fails. DEPRECATED: use HeadlessBoardManager.saveAsSpecctraSessionSes instead
   */
  @Deprecated
  public boolean saveAsSpecctraSessionSes(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (!board_panel.board_handling.saveAsSpecctraSessionSes(outputStream, designName)) {
        this.screen_messages.set_status_message(tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
        return false;
      }
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra session file '" + outputFile.getPath() + "'", e);
      this.screen_messages.set_status_message(tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
      return false;
    }

    this.screen_messages.set_status_message(tm.getText("message_specctra_ses_saved", outputFile.getPath()));

    return true;
  }

  /**
   * Writes a KiCad Session JSON File. Returns false if write operation fails.
   */
  public boolean saveAsKiCadJson(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (java.io.FileWriter writer = new java.io.FileWriter(outputFile)) {
      String json = app.freerouting.io.kicad.KiCadJsonWriter.write(board_panel.board_handling.get_routing_board(), designName);
      writer.write(json);
    } catch (Exception e) {
      FRLogger.error("Unable to write KiCad JSON file", e);
      this.screen_messages.set_status_message(tm.getText("message_kicad_session_json_save_failed", outputFile.getPath()));
      return false;
    }

    this.screen_messages.set_status_message(tm.getText("message_kicad_session_json_saved", outputFile.getPath()));
    return true;
  }

  public File showSaveAsDialog(String p_default_directory, BoardFileDetails output) {
    var p_parent = this;

    String directoryName;
    var outputFile = output.getFile();
    if (outputFile == null) {
      directoryName = p_default_directory;
    } else {
      directoryName = outputFile.getParent();
    }

    JFileChooser fileChooser = new JFileChooser(directoryName);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Session .SES files
    FileNameExtensionFilter sesFilter = new FileNameExtensionFilter("SPECCTRA Session file (*.ses)", "ses");
    fileChooser.addChoosableFileFilter(sesFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter = new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Add the file filter for Eagle script .SCR files
    FileNameExtensionFilter scrFilter = new FileNameExtensionFilter("Eagle Session Script file (*.scr)", "scr");
    fileChooser.addChoosableFileFilter(scrFilter);

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter = new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Add the file filter for KiCad Session JSON files
    FileNameExtensionFilter jsonSessionFilter = new FileNameExtensionFilter("KiCad Session JSON file (*.json)", "json");
    fileChooser.addChoosableFileFilter(jsonSessionFilter);

    // Set the file filter based on the output file format
    switch (output.format) {
      case SES:
        fileChooser.setFileFilter(sesFilter);
        break;
      case FRB:
        fileChooser.setFileFilter(frbFilter);
        break;
      case SCR:
        fileChooser.setFileFilter(scrFilter);
        break;
      case DSN:
        fileChooser.setFileFilter(dsnFilter);
        break;
      case KICAD_SESSION_JSON:
        fileChooser.setFileFilter(jsonSessionFilter);
        break;
      default:
        fileChooser.setFileFilter(sesFilter);
        break;
    }

    // Set the default file name based on the output file name
    if (!output
        .getFilename()
        .isEmpty()) {
      fileChooser.setSelectedFile(output.getFile());
    }

    fileChooser.showSaveDialog(p_parent);

    return fileChooser.getSelectedFile();
  }

  /**
   * Saves the board rule to file, so that they can be reused later on.
   */
  private boolean saveRulesAs(File rulesFile, String designName, GuiBoardManager p_board_handling) {
    FRLogger.info("Saving '" + rulesFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(rulesFile)) {
      RulesWriter.write(p_board_handling.get_routing_board(), outputStream, designName);
      return true;
    } catch (IOException e) {
      FRLogger.error("unable to save rules file for design '" + designName + "'", e);
      return false;
    }
  }

  public void saveAsEagleScriptScr(File outputFile, String design_name) {
    ByteArrayOutputStream sesOutputStream = new ByteArrayOutputStream();
    if (!board_panel.board_handling.saveAsSpecctraSessionSes(sesOutputStream, design_name)) {
      return;
    }
    InputStream sesInputStream = new ByteArrayInputStream(sesOutputStream.toByteArray());

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (board_panel.board_handling.saveSpecctraSessionSesAsEagleScriptScr(sesInputStream, outputStream)) {
        screen_messages.set_status_message(tm.getText("message_eagle_saved", outputFile.getPath()));
      } else {
        screen_messages.set_status_message(tm.getText("message_eagle_save_failed", outputFile.getPath()));
      }
    } catch (IOException e) {
      FRLogger.error("unable to save Eagle script file '" + outputFile.getPath() + "'", e);
      screen_messages.set_status_message(tm.getText("message_eagle_save_failed", outputFile.getPath()));
    }
  }

  /**
   * Writes a Specctra Design File (DSN). Returns false, if write operation fails.
   */
  public boolean saveAsSpecctraDesignDsn(File outputFile, String designName, boolean compatibilityMode) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      return board_panel.board_handling.saveAsSpecctraDesignDsn(outputStream, designName, compatibilityMode);
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra design file '" + outputFile.getPath() + "'", e);
      return false;
    }
  }

  /**
   * Sets the toolbar to the buttons of the selected item state.
   */
  public void set_inspect_toolbar() {
    getContentPane().remove(toolbar_panel);
    getContentPane().add(inspect_toolbar, BorderLayout.NORTH);
    repaint();
  }

  /**
   * Sets the toolbar buttons to the select. route and drag menu buttons of the
   * main menu.
   */
  public void set_menu_toolbar() {
    getContentPane().remove(inspect_toolbar);
    getContentPane().add(toolbar_panel, BorderLayout.NORTH);
    repaint();
  }

  /**
   * Calculates the absolute location of the board frame in his outmost parent
   * frame.
   */
  Point absolute_panel_location() {
    int x = this.scroll_pane.getX();
    int y = this.scroll_pane.getY();
    Container curr_parent = this.scroll_pane.getParent();
    while (curr_parent != null) {
      x += curr_parent.getX();
      y += curr_parent.getY();
      curr_parent = curr_parent.getParent();
    }
    return new Point(x, y);
  }

  /**
   * Sets the displayed region to the whole board.
   */
  public void zoom_all() {
    board_panel.board_handling.adjust_design_bounds();
    Rectangle display_rect = board_panel.get_viewport_bounds();
    Rectangle design_bounds = board_panel.board_handling.graphics_context.get_design_bounds();
    double width_factor = display_rect.getWidth() / design_bounds.getWidth();
    double height_factor = display_rect.getHeight() / design_bounds.getHeight();
    double zoom_factor = Math.min(width_factor, height_factor);
    Point2D zoom_center = board_panel.board_handling.graphics_context.get_design_center();
    board_panel.zoom(zoom_factor, zoom_center);
    Point2D new_vieport_center = board_panel.board_handling.graphics_context.get_design_center();
    board_panel.set_viewport_center(new_vieport_center);
  }

  /**
   * Actions to be taken when this frame vanishes.
   */
  @Override
  public void dispose() {
    if (activeFrame == this) {
      activeFrame = null;
    }
    for (int i = 0; i < this.permanent_subwindows.length; i++) {
      if (this.permanent_subwindows[i] != null) {
        this.permanent_subwindows[i].dispose();
        this.permanent_subwindows[i] = null;
      }
    }
    for (BoardTemporarySubWindow curr_subwindow : this.temporary_subwindows) {
      if (curr_subwindow != null) {
        curr_subwindow.board_frame_disposed();
      }
    }
    if (board_panel.board_handling != null) {
      board_panel.board_handling.dispose();
      board_panel.board_handling = null;
    }
    if (this.log_entry_added_listener != null) {
      FRLogger
          .getLogEntries()
          .removeLogEntryAddedListener(this.log_entry_added_listener);
    }
    super.dispose();
  }

  /**
   * Initializes and creates instances for all the "permanent" subwindows.
   * These are the utility windows (parameters, colors, visibility, etc.) that
   * can be toggled via the menu but exist for the lifetime of the BoardFrame.
   * They are stored in the {@code permanent_subwindows} array for easy
   * management.
   */
  private void allocate_permanent_subwindows() {
    allocateEssentialSubwindows();
    allocateRemainingSubwindows();
  }

  private void allocateEssentialSubwindows() {
    if (this.select_parameter_window == null) {
      this.select_parameter_window = new WindowSelectParameter(this);
      this.permanent_subwindows[6] = this.select_parameter_window;
    }
  }

  private void allocateRemainingSubwindows() {
    if (this.color_manager != null) {
      return;
    }
    this.color_manager = new ColorManager(this);
    this.permanent_subwindows[0] = this.color_manager;
    this.visibility_window = new WindowVisibility(this);
    this.permanent_subwindows[1] = this.visibility_window;
    this.permanent_subwindows[2] = null;
    this.display_misc_window = new WindowDisplayMisc(this);
    this.permanent_subwindows[3] = this.display_misc_window;

    this.route_parameter_window = new WindowRouteParameter(this);
    this.permanent_subwindows[5] = this.route_parameter_window;
    this.clearance_matrix_window = new WindowClearanceMatrix(this);
    this.permanent_subwindows[7] = this.clearance_matrix_window;
    this.padstacks_window = new WindowPadstacks(this);
    this.permanent_subwindows[8] = this.padstacks_window;
    this.packages_window = new WindowPackages(this);
    this.permanent_subwindows[9] = this.packages_window;
    this.components_window = new WindowComponents(this);
    this.permanent_subwindows[10] = this.components_window;
    this.incompletes_window = new WindowIncompletes(this);
    this.permanent_subwindows[11] = this.incompletes_window;
    this.clearance_violations_window = new WindowClearanceViolations(this);
    this.permanent_subwindows[12] = this.clearance_violations_window;
    this.net_info_window = new WindowNets(this);
    this.permanent_subwindows[13] = this.net_info_window;
    this.via_window = new WindowVia(this);
    this.permanent_subwindows[14] = this.via_window;
    this.edit_vias_window = new WindowEditVias(this);
    this.permanent_subwindows[15] = this.edit_vias_window;
    this.edit_net_rules_window = new WindowNetClasses(this);
    this.permanent_subwindows[16] = this.edit_net_rules_window;
    this.assign_net_classes_window = new WindowAssignNetClass(this);
    this.permanent_subwindows[17] = this.assign_net_classes_window;
    this.length_violations_window = new WindowLengthViolations(this);
    this.permanent_subwindows[18] = this.length_violations_window;
    this.about_window = new WindowAbout(this.locale, this.freerouting_version);
    this.permanent_subwindows[19] = this.about_window;
    this.move_parameter_window = new WindowMoveParameter(this);
    this.permanent_subwindows[20] = this.move_parameter_window;
    this.unconnected_route_window = new WindowUnconnectedRoute(this);
    this.permanent_subwindows[21] = this.unconnected_route_window;
    this.route_stubs_window = new WindowRouteStubs(this);
    this.permanent_subwindows[22] = this.route_stubs_window;
    this.autoroute_parameter_window = new WindowAutorouteParameter(this);
    this.permanent_subwindows[23] = this.autoroute_parameter_window;
  }

  /**
   * Creates the additional frames of the board frame.
   *
   * @param showEssentialImmediately when {@code true}, General Settings is shown in this EDT pass;
   *     remaining tool windows are still created on a later cycle
   */
  private void initialize_windows(boolean showEssentialImmediately) {
    allocateEssentialSubwindows();

    this.setLocation(120, 0);

    this.select_parameter_window.setLocation(0, 0);

    if (showEssentialImmediately) {
      this.select_parameter_window.setVisible(true);
      javax.swing.SwingUtilities.invokeLater(() -> {
        allocateRemainingSubwindows();
        positionRemainingSubwindows();
      });
    } else {
      javax.swing.SwingUtilities.invokeLater(() -> {
        this.select_parameter_window.setVisible(true);
        allocateRemainingSubwindows();
        positionRemainingSubwindows();
      });
    }
  }

  private void initialize_windows() {
    initialize_windows(false);
  }

  private void positionRemainingSubwindows() {
    this.route_parameter_window.setLocation(0, 100);
    this.autoroute_parameter_window.setLocation(0, 200);
    this.move_parameter_window.setLocation(0, 50);
    this.clearance_matrix_window.setLocation(0, 150);
    this.via_window.setLocation(50, 150);
    this.edit_vias_window.setLocation(100, 150);
    this.edit_net_rules_window.setLocation(100, 200);
    this.assign_net_classes_window.setLocation(100, 250);
    this.padstacks_window.setLocation(100, 30);
    this.packages_window.setLocation(200, 30);
    this.components_window.setLocation(300, 30);
    this.incompletes_window.setLocation(400, 30);
    this.clearance_violations_window.setLocation(500, 30);
    this.length_violations_window.setLocation(550, 30);
    this.net_info_window.setLocation(350, 30);
    this.unconnected_route_window.setLocation(650, 30);
    this.route_stubs_window.setLocation(600, 30);

    this.visibility_window.setLocation(0, 450);
    this.display_misc_window.setLocation(0, 350);
    this.color_manager.setLocation(0, 600);
    this.about_window.setLocation(200, 200);
  }

  /**
   * Returns the currently used locale for the language dependent output.
   */
  public Locale get_locale() {
    return this.locale;
  }

  /**
   * Sets the background of the board panel
   */
  public void set_board_background(Color p_color) {
    this.board_panel.setBackground(p_color);
  }

  /**
   * Refreshes all displayed coordinates after the user unit has changed.
   */
  public void refresh_windows() {
    for (int i = 0; i < this.permanent_subwindows.length; i++) {
      if (permanent_subwindows[i] != null) {
        permanent_subwindows[i].refresh();
      }
    }
  }

  /**
   * Sets the mode value on mode selection component of the toolbar
   */
  public void setToolbarModeSelectionPanelValue(InteractiveState interactiveState) {
    this.toolbar_panel.setModeSelectionPanelValue(interactiveState);
  }

  private void setToolbarUnitSelectionPanelValue(Unit unit) {
    this.toolbar_panel.setUnitSelectionPanelValue(unit);
  }

  /**
   * Repaints this board frame and all the subwindows of the board.
   */
  public void repaint_all() {
    this.repaint();
    for (int i = 0; i < permanent_subwindows.length; i++) {
      if (permanent_subwindows[i] != null) {
        permanent_subwindows[i].repaint();
      }
    }
  }

  public void addBoardLoadedEventListener(Consumer<RoutingBoard> listener) {
    boardLoadedEventListeners.add(listener);
  }

  /**
   * Returns the array of permanent subwindows for use by {@code GuiBoardManager.refreshGuiFromSettings()}.
   */
  public BoardSavableSubWindow[] getPermanentSubwindows() {
    return permanent_subwindows;
  }

  public void addReadOnlyEventListener(Consumer<RoutingBoard> listener) {
    boardSavedEventListeners.add(listener);
  }

  /**
   * Loads a file that was dropped onto the board panel.
   * Follows the same pattern as the File menu open operation.
   * Shows a save confirmation dialog if the current board has unsaved changes.
   *
   * @param p_file The file to load
   * @param p_format The format of the file (DSN or KiCad design JSON)
   */
  public void loadDroppedFile(File p_file, FileFormat p_format) {
    if (p_file == null) {
      return;
    }

    FileFormat format = p_format;

    // Validate format is supported
    if (format != FileFormat.DSN && format != FileFormat.KICAD_DESIGN_JSON) {
      FRLogger.warn("Dropped file format not supported: " + format);
      return;
    }

    // Check if there's a board with unsaved changes and prompt to save
    if (board_panel != null && board_panel.board_handling != null) {
      boolean shouldProceed = confirmSaveBeforeLoad(this);
      if (!shouldProceed) {
        return;
      }
    }

    // Clear any existing jobs for this session (single board support)
    String sessionId = SessionManager.getInstance().getGuiSession().id.toString();
    RoutingJobScheduler.getInstance().clearJobs(sessionId);

    try {
      routingJob.setInput(p_file);
    } catch (Exception e) {
      FRLogger.error("Error setting input for dropped file", e);
      return;
    }

    // Enqueue the job to the routing queue (needed for autorouting)
    RoutingJobScheduler.getInstance().enqueueJob(routingJob);

    // Set the input directory in the global settings
    String oldInputDirectory = Freerouting.globalSettings.guiSettings.inputDirectory;
    Freerouting.globalSettings.guiSettings.inputDirectory = routingJob.input.getDirectoryPath();

    // Save the global settings to the configuration file if the input directory was changed
    if (!oldInputDirectory.equals(Freerouting.globalSettings.guiSettings.inputDirectory)) {
      try {
        GlobalSettings.saveAsJson(Freerouting.globalSettings);
      } catch (IOException e) {
        FRLogger.error("Couldn't save the global settings to the configuration file", e);
      }
    }

    // Load the file into the frame based on its recognized format
    if (board_panel != null && board_panel.board_handling != null
        && routingJob.input.format != FileFormat.UNKNOWN) {
      // Read file content
      byte[] fileContent;
      try {
        fileContent = Files.readAllBytes(p_file.toPath());
      } catch (IOException e) {
        FRLogger.error("Could not read dropped file content", e);
        return;
      }

      if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
        loadFromBytesAsync(fileContent, format, routingJob);
        FRAnalytics.buttonClicked("file_dropped_" + format.name().toLowerCase(), routingJob.getInputFileDetails());
      }
    }
  }

  /**
   * Convenience overload that auto-detects format.
   *
   * @param p_file The file to load
   */
  public void loadDroppedFile(File p_file) {
    if (p_file == null) {
      return;
    }

    FileFormat format = RoutingJob.getFileFormat(p_file.toPath());
    if (format == FileFormat.UNKNOWN) {
      try {
        byte[] content = Files.readAllBytes(p_file.toPath());
        format = RoutingJob.getFileFormat(content);
      } catch (IOException e) {
        FRLogger.error("Could not read dropped file for format detection", e);
        return;
      }
    }

    loadDroppedFile(p_file, format);
  }

  /**
   * Shows a save confirmation dialog if the board has been modified.
   *
   * @param p_parent The parent frame for the dialog
   * @return true if loading should proceed, false if cancelled
   */
  private boolean confirmSaveBeforeLoad(BoardFrame p_parent) {
    if (board_panel == null || board_panel.board_handling == null) {
      return true;
    }

    try {
      boolean isChanged = board_panel.board_handling.isBoardChanged();
      if (isChanged) {
        Object[] options = {
            tm.getText("confirm_save_yes"),
            tm.getText("confirm_save_no"),
            tm.getText("confirm_save_cancel")
        };
        JOptionPane optionPane = new JOptionPane(
            tm.getText("confirm_save_changes"),
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.YES_NO_CANCEL_OPTION,
            null,
            options,
            options[2] // Default to "Cancel"
        );
        JDialog dialog = optionPane.createDialog(p_parent, tm.getText("confirm_save_title"));
        dialog.setVisible(true);

        Object selectedValue = optionPane.getValue();
        if (selectedValue == null) {
          return false; // Dialog was closed
        }

        String cancelOption = tm.getText("confirm_save_cancel");
        String noOption = tm.getText("confirm_save_no");

        if (selectedValue.equals(cancelOption)) {
          return false; // Cancel loading
        } else if (selectedValue.equals(tm.getText("confirm_save_yes"))) {
          // User wants to save - trigger save dialog via menu action
          FRAnalytics.buttonClicked("drop_load_confirm_save", "save");
          // The actual save would be handled by the file menu save action
          // For now, we proceed with loading since the user confirmed they want to save
        }
      }
    } catch (Exception e) {
      // If there's an error checking board state, proceed with loading
      FRLogger.warn("Could not check if board has unsaved changes");
    }

    return true;
  }

  private class WindowStateListener extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent evt) {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      boolean wasBoardChanged = board_panel.board_handling.isBoardChanged();
      if (wasBoardChanged) {
        // Create a JOptionPane with a warning icon and set the default option to NO
        Object[] options = {
            tm.getText("confirm_exit_yes"),
            tm.getText("confirm_exit_no")
        };
        JOptionPane optionPane = new JOptionPane(tm.getText("confirm_cancel"), JOptionPane.WARNING_MESSAGE,
            JOptionPane.YES_NO_OPTION, null, options, options[1] // Default to "No"
        );
        JDialog dialog = optionPane.createDialog(null, "Warning");
        dialog.setVisible(true);

        // Check the user's choice
        Object selectedValue = optionPane.getValue();
        if (selectedValue == null || selectedValue.equals(tm.getText("confirm_exit_no"))) {
          setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
          FRAnalytics.buttonClicked("board_confirm_exit_dialog_no", tm.getText("confirm_cancel"));
          return;
        }
      }

      try {
        GuiManager.saveSettings();
      } catch (IOException e) {
        FRLogger.error("Error saving settings to the freerouting.json file.", e);
      }

      // If we started the GUI, we must shut down both the GUI and the API (if it's
      // running)
      Freerouting.globalSettings.guiSettings.isRunning = false;
      Freerouting.globalSettings.apiServerSettings.isRunning = false;
    }

    @Override
    public void windowIconified(WindowEvent evt) {
      for (int i = 0; i < permanent_subwindows.length; i++) {
        if (permanent_subwindows[i] != null) {
          permanent_subwindows[i].parent_iconified();
        }
      }
      for (BoardSubWindow curr_subwindow : temporary_subwindows) {
        if (curr_subwindow != null) {
          curr_subwindow.parent_iconified();
        }
      }
    }

    @Override
    public void windowDeiconified(WindowEvent evt) {
      for (BoardSavableSubWindow permanentSubwindow : permanent_subwindows) {
        if (permanentSubwindow != null) {
          permanentSubwindow.parent_deiconified();
        }
      }
      for (BoardSubWindow curr_subwindow : temporary_subwindows) {
        if (curr_subwindow != null) {
          curr_subwindow.parent_deiconified();
        }
      }
    }
  }
}