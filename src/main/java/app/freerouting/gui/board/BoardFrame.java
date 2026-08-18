package app.freerouting.gui.board;

import app.freerouting.Freerouting;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.board.state.BoardObservers;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.gui.controls.ColorManager;
import app.freerouting.gui.menus.BoardMenuBar;
import app.freerouting.gui.rendering.TutorialBoardPalette;
import app.freerouting.gui.support.GuiDefaultsFile;
import app.freerouting.gui.windows.board.WindowAbout;
import app.freerouting.gui.windows.board.WindowBase;
import app.freerouting.gui.windows.board.WindowComponents;
import app.freerouting.gui.windows.board.WindowDisplayMisc;
import app.freerouting.gui.windows.board.WindowIncompletes;
import app.freerouting.gui.windows.board.WindowMessage;
import app.freerouting.gui.windows.board.WindowNets;
import app.freerouting.gui.windows.board.WindowPackages;
import app.freerouting.gui.windows.board.WindowPadstacks;
import app.freerouting.gui.windows.board.WindowVisibility;
import app.freerouting.gui.windows.routing.WindowAssignNetClass;
import app.freerouting.gui.windows.routing.WindowAutorouteParameter;
import app.freerouting.gui.windows.routing.WindowClearanceMatrix;
import app.freerouting.gui.windows.routing.WindowClearanceViolations;
import app.freerouting.gui.windows.routing.WindowEditVias;
import app.freerouting.gui.windows.routing.WindowLengthViolations;
import app.freerouting.gui.windows.routing.WindowMoveParameter;
import app.freerouting.gui.windows.routing.WindowNetClasses;
import app.freerouting.gui.windows.routing.WindowRouteParameter;
import app.freerouting.gui.windows.routing.WindowRouteStubs;
import app.freerouting.gui.windows.routing.WindowSelectParameter;
import app.freerouting.gui.windows.routing.WindowUnconnectedRoute;
import app.freerouting.gui.windows.routing.WindowVia;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.ports.BoardReplacement;
import app.freerouting.gui.workspace.progress.RatsNest;
import app.freerouting.gui.workspace.progress.ScreenMessages;
import app.freerouting.gui.workspace.session.EditorStateHandle;
import app.freerouting.gui.workspace.session.LoadGeneration;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DsnFileSettings;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;

/** Graphical frame containing the Menu, Toolbar, Canvas and Status bar. */
public class BoardFrame extends WindowBase {

  /** The windows above stored in an array. */
  static final int SUBWINDOW_COUNT = 24;

  static final String GUI_DEFAULTS_FILE_NAME = "gui_defaults.par";
  static final String GUI_DEFAULTS_FILE_BACKUP_NAME = "gui_defaults.par.bak";
  private static final String TUTORIAL_BOARD_FILENAME = "tutorial_board.dsn";
  public static volatile BoardFrame activeFrame;

  /** The menubar of this frame. */
  public final BoardMenuBar menubar;

  /** The scroll pane for the panel of the routing board. */
  final JScrollPane scrollPane;

  /** Handles displaying messages to the user. */
  public final ScreenMessages screenMessages;

  /** The main toolbar panel containing common tools. */
  private final BoardToolbar toolbarPanel;

  /**
   * The toolbar used in the inspected item state (when items are selected). Note: This field is
   * used by InspectedItemState.
   */
  private final JToolBar inspectToolbar;

  /** The panel with the message line/status bar. */
  private final BoardPanelStatus messagePanel;

  private final Locale locale;
  private final SettingsMerger settingsMerger;
  private final List<Consumer<RoutingBoard>> boardLoadedEventListeners = new ArrayList<>();
  private final List<Consumer<RoutingBoard>> boardSavedEventListeners = new ArrayList<>();
  private final BoardObservers boardObservers;
  private final String freeroutingVersion;
  private final BoardWindowLayout windowLayout;
  private final BoardLoadCoordinator loadCoordinator;
  private final BoardExportActions exportActions;

  /** The current routing job (design) being edited. */
  public RoutingJob routingJob;

  /** The panel with the graphical representation of the board. */
  public BoardPanel boardPanel;

  // -- Subwindows for various settings and tools --
  public WindowAbout aboutWindow;
  public WindowRouteParameter routeParameterWindow;
  public WindowAutorouteParameter autorouteParameterWindow;
  public WindowSelectParameter selectParameterWindow;
  public WindowMoveParameter moveParameterWindow;
  public WindowClearanceMatrix clearanceMatrixWindow;
  public WindowVia viaWindow;
  public WindowEditVias editViasWindow;
  public WindowNetClasses editNetRulesWindow;
  public WindowAssignNetClass assignNetClassesWindow;
  public WindowPadstacks padstacksWindow;
  public WindowPackages packagesWindow;
  public WindowIncompletes incompletesWindow;
  public WindowNets netInfoWindow;
  public WindowClearanceViolations clearanceViolationsWindow;
  public WindowLengthViolations lengthViolationsWindow;
  public WindowUnconnectedRoute unconnectedRouteWindow;
  public WindowRouteStubs routeStubsWindow;
  public WindowComponents componentsWindow;
  public WindowVisibility visibilityWindow;
  public WindowDisplayMisc displayMiscWindow;
  public ColorManager colorManager;

  /**
   * Array storing references to all "permanent" subwindows (tool windows that persist). This array
   * allows for collective operations like saving/restoring positions and refreshing.
   */
  BoardSavableSubWindow[] permanentSubwindows = new BoardSavableSubWindow[SUBWINDOW_COUNT];

  Collection<BoardTemporarySubWindow> temporarySubwindows = new LinkedList<>();
  private LogEntries.LogEntryAddedListener logEntryAddedListener;

  /**
   * Creates a new BoardFrame that is the GUI element containing the Menu, Toolbar, Canvas and
   * Status bar.
   */
  public BoardFrame(
      RoutingJob design, GlobalSettings globalSettings, SettingsMerger settingsMerger) {
    this(design, new BoardObserverAdaptor(), globalSettings, settingsMerger);
  }

  /** Creates new form BoardFrame. */
  public BoardFrame(
      RoutingJob routingJob,
      BoardObservers boardObservers,
      GlobalSettings globalSettings,
      SettingsMerger settingsMerger) {
    super(800, 150);
    activeFrame = this;

    this.routingJob = routingJob;
    this.settingsMerger = settingsMerger;
    this.boardObservers = boardObservers;
    this.locale = globalSettings.currentLocale;
    this.setLanguage(this.locale);
    this.freeroutingVersion = globalSettings.version;
    this.windowLayout = new BoardWindowLayout(this, this.locale, this.freeroutingVersion);
    this.loadCoordinator = new BoardLoadCoordinator(this);
    this.exportActions = new BoardExportActions(this);

    // Set the menu bar of this frame.
    this.menubar = new BoardMenuBar(this, globalSettings.featureFlags);

    new BoardFrameFileActions(this, globalSettings).install();

    setJMenuBar(this.menubar);

    // Set the toolbar panel to the top of the frame, just above the canvas.
    this.toolbarPanel = new BoardToolbar(this, !globalSettings.featureFlags.inspectionMode);
    this.add(this.toolbarPanel, BorderLayout.NORTH);

    // Create and move the status bar one-liners (like current layer, cursor
    // position, etc.) below the canvas.
    this.messagePanel = new BoardPanelStatus(this.locale);
    this.add(this.messagePanel, BorderLayout.SOUTH);

    this.messagePanel.addErrorOrWarningLabelClickedListener(
        () -> {
          LogEntries logEntries = FRLogger.getLogEntries();

          // Filter the log entries that are not errors or warnings
          LogEntries filteredLogEntries = new LogEntries();
          for (LogEntry entry : logEntries.getEntries(null, null)) {
            if (entry.getType() == LogEntryType.Error
                || entry.getType() == LogEntryType.Warning
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
          logEntryAddedListener =
              (LogEntry logEntry) -> {
                var type = logEntry.getType();
                if (type == LogEntryType.Error
                    || type == LogEntryType.Warning
                    || type == LogEntryType.Info) {
                  textArea.append(logEntry + "\n");
                }
              };
          logEntries.addLogEntryAddedListener(logEntryAddedListener);

          int messageType =
              filteredLogEntries.getErrorCount() > 0
                  ? JOptionPane.ERROR_MESSAGE
                  : JOptionPane.WARNING_MESSAGE;

          JOptionPane.showMessageDialog(
              null, scrollPane, tm.getText("logs_window_title"), messageType);
        });

    // Toolbar for inspected items (e.g. when a component is selected)
    this.inspectToolbar = new BoardToolbarInspectedItem(this);

    // Screen messages are displayed in the status bar, below the canvas.
    this.screenMessages =
        new ScreenMessages(
            this.messagePanel.errorLabel,
            this.messagePanel.warningLabel,
            this.messagePanel.statusMessage,
            this.messagePanel.additionalMessage,
            this.messagePanel.currentLayer,
            this.messagePanel.currentBoardScore,
            this.messagePanel.mousePosition,
            this.messagePanel.unitLabel,
            this.locale);

    // The scroll pane for the canvas of the routing board.
    this.scrollPane = new JScrollPane();
    this.scrollPane.setPreferredSize(new Dimension(1150, 800));
    this.scrollPane.setVerifyInputWhenFocusTarget(false);
    this.add(scrollPane, BorderLayout.CENTER);

    this.boardPanel =
        new BoardPanel(screenMessages, this, globalSettings, routingJob, settingsMerger);
    this.scrollPane.setViewportView(boardPanel);

    this.addWindowListener(new WindowStateListener());

    this.addBoardLoadedEventListener(
        (RoutingBoard board) -> {
          boolean isBoardEmpty = (board == null) || (board.components.count() == 0);
          this.menubar.fileMenu.fileSaveAsMenuitem.setEnabled(!isBoardEmpty);
          this.menubar.appereanceMenu.setEnabled(!isBoardEmpty);
          this.menubar.settingsMenu.setEnabled(!isBoardEmpty);
          this.menubar.rulesMenu.setEnabled(!isBoardEmpty);
          this.menubar.infoMenu.setEnabled(!isBoardEmpty);

          this.toolbarPanel.setEnabled(!isBoardEmpty);
        });

    this.updateTexts();
    this.pack();
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

  /**
   * Loads a RoutingBoard natively (e.g. from an API session) without file transfer overhead,
   * rendering it instantly in the GUI for real-time monitoring.
   */
  public void loadBoardNatively(RoutingBoard board, RoutingJob job) {
    if (board == null) {
      this.updateTexts();
      return;
    }
    LoadGeneration generation = boardPanel.boardHandling.getSessionPort().beginBoardLoad();
    this.routingJob = job;
    boardPanel.resetBoardHandling(job);
    boardPanel.boardHandling.getSessionPort().replaceBoard(new BoardReplacement(generation, board));

    // Close other child windows
    for (int i = 0; i < this.permanentSubwindows.length; i++) {
      if (this.permanentSubwindows[i] != null) {
        this.permanentSubwindows[i].dispose();
        this.permanentSubwindows[i] = null;
      }
    }

    // Initialize standard state
    int boardLayerCount = board.getLayerCount();
    this.routingJob.routerSettings.setLayerCount(boardLayerCount);
    this.routingJob.routerSettings.applyBoardSpecificOptimizations(board);

    if (this.settingsMerger != null) {
      var mergedSettings = this.settingsMerger.merge();
      this.routingJob.setSettings(mergedSettings);
      var interactiveSettings = boardPanel.boardHandling.getWorkspaceSettings();
      if (interactiveSettings != null) {
        interactiveSettings.setSettings(this.routingJob.routerSettings);
      }
    }

    initializeWindows();
    this.boardLoadedEventListeners.forEach(listener -> listener.accept(board));
    this.refreshWindows();
    this.updateTexts();
    this.repaint();
  }

  @Override
  public void updateTexts() {
    String boardName = null;
    if (this.routingJob != null) {
      if (this.routingJob.input != null) {
        String filename = this.routingJob.input.getFilename();
        if (filename != null
            && !filename.isBlank()
            && !"tutorial_board.dsn".equals(filename)
            && !"empty_board.dsn".equals(filename)) {
          boardName = filename;
        }
      }
      if (boardName == null
          && this.routingJob.name != null
          && !this.routingJob.name.isBlank()
          && !this.routingJob.name.startsWith("J-")) {
        boardName = this.routingJob.name;
      }
    }

    String appTitle = tm.getText("title", this.freeroutingVersion);
    if (boardName != null && !boardName.isBlank()) {
      this.setTitle(boardName + " - " + appTitle);
    } else {
      this.setTitle(appTitle);
    }
  }

  /**
   * Parses a design file on a background thread, then completes GUI setup on the EDT in phases.
   * Keeps the BoardFrame and tool windows responsive during DSN/JSON parsing and heavy post-load
   * work.
   */
  public void loadFromBytesAsync(byte[] fileContent, FileFormat format, RoutingJob job) {
    loadCoordinator.loadFromBytesAsync(fileContent, format, job);
  }

  public LoadGeneration beginBoardLoadForCoordinator() {
    return boardPanel.boardHandling.getSessionPort().beginBoardLoad();
  }

  private boolean isCurrentLoad(LoadGeneration generation) {
    return boardPanel != null
        && boardPanel.boardHandling != null
        && boardPanel.boardHandling.getSessionPort().isCurrent(generation);
  }

  public void ensureGeneralSettingsVisibleDuringLoad() {
    if (boardPanel == null
        || boardPanel.boardHandling == null
        || boardPanel.boardHandling.graphicsContext == null) {
      return;
    }
    allocateEssentialSubwindows();
    this.selectParameterWindow.setLocation(0, 0);
    this.selectParameterWindow.setVisible(true);
    this.selectParameterWindow.toFront();
  }

  private void disposePermanentSubwindows() {
    windowLayout.disposePermanentSubwindows();
  }

  public void finishLoadFromParseResult(
      BoardReadResult readResult,
      byte[] fileContent,
      FileFormat format,
      RoutingJob routingJob,
      WindowMessage loadingWindow,
      LoadGeneration generation) {
    if (!isCurrentLoad(generation)) {
      loadingWindow.dispose();
      return;
    }
    long attachStart = System.nanoTime();
    boolean scheduleInitialPaint = false;
    try {
      if (!attachParsedBoard(readResult, fileContent, format, routingJob)) {
        FRLogger.warn("Loading the board file failed. Restoring " + TUTORIAL_BOARD_FILENAME + ".");
        restoreTutorialBoardAfterFailedLoad(loadingWindow);
        return;
      }

      if (readResult instanceof BoardReadResult.Success) {
        initializeWindows(true);
        boardPanel.boardHandling.refreshGuiFromSettings();
        updateGui(format, readResult, new Point(0, 0), null, true);
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
      scheduleInitialBoardPaint(loadingWindow, format, readResult, generation);
    } else {
      javax.swing.SwingUtilities.invokeLater(
          () -> {
            if (isCurrentLoad(generation)) {
              completeHeavyGuiAfterLoad(format, readResult, generation);
            }
          });
    }
  }

  /**
   * Shows rendering feedback, paints the board with fast simplified plane fills, then warms
   * detailed plane geometry on a background thread before a full-quality repaint.
   */
  private void scheduleInitialBoardPaint(
      WindowMessage loadingWindow,
      FileFormat format,
      BoardReadResult readResult,
      LoadGeneration generation) {
    if (!isCurrentLoad(generation)) {
      loadingWindow.dispose();
      return;
    }
    var graphicsContext = boardPanel.boardHandling.graphicsContext;
    String renderingStatus = tm.getText("rendering_board");
    boardPanel.showRenderingOverlay(renderingStatus);
    screenMessages.setStatusMessage(renderingStatus);
    graphicsContext.setSimplifiedPlaneRendering(true);

    javax.swing.SwingUtilities.invokeLater(
        () -> {
          if (!isCurrentLoad(generation)) {
            loadingWindow.dispose();
            return;
          }
          boardPanel.paintImmediately(0, 0, boardPanel.getWidth(), boardPanel.getHeight());

          javax.swing.SwingUtilities.invokeLater(
              () -> {
                long paintStart = System.nanoTime();
                try {
                  if (loadingWindow != null) {
                    loadingWindow.dispose();
                  }
                  this.zoomAll();
                  boardPanel.repaint();
                } finally {
                  long paintMs = (System.nanoTime() - paintStart) / 1_000_000L;
                  FRLogger.debug("Board load: first paint completed in " + paintMs + " ms");
                  boardPanel.clearRenderingOverlay();
                  graphicsContext.setSimplifiedPlaneRendering(false);
                  this.updateTexts();
                }
                schedulePlaneFillCacheWarm(generation);
                javax.swing.SwingUtilities.invokeLater(
                    () -> {
                      if (isCurrentLoad(generation)) {
                        completeHeavyGuiAfterLoad(format, readResult, generation);
                      }
                    });
              });
        });
  }

  private void schedulePlaneFillCacheWarm(LoadGeneration generation) {
    GuiBoardManager boardHandling = boardPanel.boardHandling;
    if (boardHandling == null) {
      return;
    }
    RoutingBoard board = boardHandling.getRoutingBoard();
    if (board == null) {
      return;
    }
    var conductionAreas = board.getConductionAreas();
    if (conductionAreas.isEmpty()) {
      return;
    }
    Thread.ofVirtual()
        .name("plane-fill-cache-warm")
        .start(
            () -> {
              long warmStart = System.nanoTime();
              for (app.freerouting.board.model.items.ConductionArea area : conductionAreas) {
                area.warmDetailedFillCache();
              }
              long warmMs = (System.nanoTime() - warmStart) / 1_000_000L;
              FRLogger.debug("Board load: plane fill cache warmed in " + warmMs + " ms");
              javax.swing.SwingUtilities.invokeLater(
                  () -> {
                    GuiBoardManager currentBoardHandling = boardPanel.boardHandling;
                    if (isCurrentLoad(generation)
                        && currentBoardHandling != null
                        && currentBoardHandling.getRoutingBoard() == board) {
                      boardPanel.repaint();
                    }
                  });
            });
  }

  private boolean attachParsedBoard(
      BoardReadResult readResult, byte[] fileContent, FileFormat format, RoutingJob routingJob) {
    if (!canAttachParsedBoard(readResult)) {
      showBoardLoadError(readResult);
      return false;
    }

    this.routingJob = routingJob;
    boardPanel.resetBoardHandling(routingJob);
    disposePermanentSubwindows();

    String inputFilename = routingJob.input != null ? routingJob.input.getFilename() : null;
    String analyticsFormat = format == FileFormat.KICAD_DESIGN_JSON ? "KICAD_JSON" : "DSN";
    boardPanel.boardHandling.applyParsedBoardResult(readResult, inputFilename, analyticsFormat);

    if (readResult instanceof BoardReadResult.Success) {
      RoutingBoard board = boardPanel.boardHandling.getRoutingBoard();
      if (this.settingsMerger != null) {
        if (format == FileFormat.DSN && inputFilename != null) {
          this.settingsMerger.addOrReplaceSources(
              new DsnFileSettings(new ByteArrayInputStream(fileContent), inputFilename));
        }
        var mergedSettings = this.settingsMerger.merge();
        int boardLayerCount = board.getLayerCount();
        if (mergedSettings.getLayerCount() == 0
            || mergedSettings.getLayerCount() != boardLayerCount) {
          mergedSettings.setLayerCount(boardLayerCount);
        }
        mergedSettings.applyBoardSpecificOptimizations(board);
        this.routingJob.setSettings(mergedSettings);
        var interactiveSettings = boardPanel.boardHandling.getWorkspaceSettings();
        if (interactiveSettings != null) {
          interactiveSettings.setSettings(mergedSettings);
        }
      }

      this.boardLoadedEventListeners.forEach(
          listener -> listener.accept(boardPanel.boardHandling.getRoutingBoard()));
      return true;
    }

    return readResult instanceof BoardReadResult.OutlineMissing;
  }

  private void showBoardLoadError(BoardReadResult readResult) {
    if (readResult instanceof BoardReadResult.OutlineMissing) {
      screenMessages.setStatusMessage(tm.getText("error_dsn_outline_missing"));
    } else if (readResult instanceof BoardReadResult.IoError
        || readResult instanceof BoardReadResult.ParseError) {
      screenMessages.setStatusMessage(tm.getText("error_dsn_read_failed"));
    } else {
      screenMessages.setStatusMessage(tm.getText("error_design_file_read_failed"));
    }
    refreshLogCountsInToolbar();
  }

  /**
   * Reloads the default tutorial design after a failed user-initiated load, without clearing log
   * entries.
   *
   * @return {@code true} when the tutorial board was attached and initial paint was scheduled
   */
  public boolean restoreTutorialBoardAfterFailedLoad(WindowMessage loadingWindow) {
    LoadGeneration generation = boardPanel.boardHandling.getSessionPort().beginBoardLoad();
    refreshLogCountsInToolbar();
    try (InputStream tutorialStream =
        BoardFrame.class.getClassLoader().getResourceAsStream(TUTORIAL_BOARD_FILENAME)) {
      if (tutorialStream == null) {
        FRLogger.error(
            "Could not restore " + TUTORIAL_BOARD_FILENAME + ": classpath resource missing", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        return false;
      }
      byte[] tutorialBytes = tutorialStream.readAllBytes();
      BoardReadResult tutorialResult =
          BoardLoadCoordinator.parseBoardFromBytes(
              tutorialBytes, FileFormat.DSN, TUTORIAL_BOARD_FILENAME);
      if (!(tutorialResult instanceof BoardReadResult.Success)) {
        FRLogger.error(
            "Could not restore " + TUTORIAL_BOARD_FILENAME + " after a failed load", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        refreshLogCountsInToolbar();
        return false;
      }

      routingJob.setDummyInputFile(TUTORIAL_BOARD_FILENAME);
      routingJob.input.setData(tutorialBytes);

      if (!attachParsedBoard(tutorialResult, tutorialBytes, FileFormat.DSN, routingJob)) {
        FRLogger.error(
            "Failed to attach " + TUTORIAL_BOARD_FILENAME + " after a failed load", null);
        if (loadingWindow != null) {
          loadingWindow.dispose();
        }
        refreshLogCountsInToolbar();
        return false;
      }

      FRLogger.info("Restored " + TUTORIAL_BOARD_FILENAME + " after a failed board load");
      initializeWindows(true);
      boardPanel.boardHandling.refreshGuiFromSettings();
      applyTutorialBoardPalette();
      updateGui(FileFormat.DSN, tutorialResult, new Point(0, 0), null, true);
      scheduleBackgroundRatsNestBuild();
      scheduleInitialBoardPaint(loadingWindow, FileFormat.DSN, tutorialResult, generation);
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
    TutorialBoardPalette.apply(boardPanel.boardHandling.graphicsContext);
    boardPanel.setBackground(TutorialBoardPalette.backgroundColor());
  }

  private void refreshLogCountsInToolbar() {
    LogEntries entries = FRLogger.getLogEntries();
    screenMessages.setErrorAndWarningCount(entries.getErrorCount(), entries.getWarningCount());
  }

  private void scheduleBackgroundRatsNestBuild() {
    RoutingBoard board = boardPanel.boardHandling.getRoutingBoard();
    if (board == null) {
      return;
    }
    Thread.ofVirtual()
        .name("gui-ratsnest-build")
        .start(
            () -> {
              long ratsNestStart = System.nanoTime();
              RatsNest prepared = new RatsNest(board);
              long ratsNestMs = (System.nanoTime() - ratsNestStart) / 1_000_000L;
              FRLogger.debug("Board load: rats nest built in " + ratsNestMs + " ms");
              javax.swing.SwingUtilities.invokeLater(
                  () -> {
                    if (boardPanel.boardHandling.getRoutingBoard() == board) {
                      boardPanel.boardHandling.attachPreparedRatsNest(prepared);
                    }
                  });
            });
  }

  private void completeHeavyGuiAfterLoad(
      FileFormat format, BoardReadResult readResult, LoadGeneration generation) {
    if (!isCurrentLoad(generation)) {
      return;
    }
    if (!(readResult instanceof BoardReadResult.Success)) {
      return;
    }
    boardPanel.createPopupMenus();
    if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
      InputStream inputStream = null;
      boolean defaultsFileFound;
      File defaultsFile = new File(this.routingJob.input.getAbsolutePath(), GUI_DEFAULTS_FILE_NAME);
      defaultsFileFound = true;
      try {
        inputStream = new FileInputStream(defaultsFile);
      } catch (FileNotFoundException _) {
        defaultsFileFound = false;
      }
      if (defaultsFileFound) {
        boolean readOk = GuiDefaultsFile.read(this, boardPanel.boardHandling, inputStream);
        if (!readOk) {
          screenMessages.setStatusMessage(tm.getText("error_gui_defaults_read_failed"));
        }
        try {
          if (inputStream != null) {
            inputStream.close();
          }
        } catch (IOException _) {
          return;
        }
        this.zoomAll();
        boardPanel.repaint();
      }
    }
    if (TutorialBoardPalette.isTutorialBoard(routingJob.input.getFilename())) {
      applyTutorialBoardPalette();
    }
    this.updateTexts();
  }

  /**
   * Reads an existing board design from file. If format is DSN or JSON, the design is read from a
   * specctra dsn / kicad json file. Returns false, if the file is invalid.
   */
  public boolean load(
      InputStream inputStream, FileFormat format, JTextField messageField, RoutingJob routingJob) {
    boardPanel.boardHandling.getSessionPort().beginBoardLoad();
    Point viewportPosition = null;
    BoardReadResult readResult = null;

    boardPanel.resetBoardHandling(routingJob);
    disposePermanentSubwindows();

    if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        readResult =
            boardPanel.boardHandling.loadFromKiCadJson(
                inputStream, this.boardObservers, new ItemIdGenerator());
      } else {
        readResult =
            boardPanel.boardHandling.loadFromSpecctraDsn(
                inputStream, this.boardObservers, new ItemIdGenerator());
      }

      // If the file was read successfully, initialize the windows
      if (readResult instanceof BoardReadResult.Success) {
        viewportPosition = new Point(0, 0);

        // Initialize the RouterSettings layer count to match the loaded board
        RoutingBoard board = boardPanel.boardHandling.getRoutingBoard();
        int boardLayerCount = board.getLayerCount();

        if (this.routingJob.routerSettings.getLayerCount() == 0
            || this.routingJob.routerSettings.getLayerCount() != boardLayerCount) {
          // Initialize layer arrays and apply board-specific optimizations
          this.routingJob.routerSettings.setLayerCount(boardLayerCount);
          this.routingJob.routerSettings.applyBoardSpecificOptimizations(board);
        }

        // Merge all settings sources (DefaultSettings, DsnFileSettings, CliSettings, …)
        // so that routerSettings has fully-populated non-null values before any GUI window
        // tries to read fields like scoring.viaCosts.  Without this step the windows would
        // NPE on the first access to any nullable RouterSettings field.
        if (this.settingsMerger != null) {
          var mergedSettings = this.settingsMerger.merge();
          this.routingJob.setSettings(mergedSettings);
          var interactiveSettings = boardPanel.boardHandling.getWorkspaceSettings();
          if (interactiveSettings != null) {
            interactiveSettings.setSettings(this.routingJob.routerSettings);
          }
        }

        initializeWindows();

        // Raise an event to notify the observers that a new board has been loaded
        this.boardLoadedEventListeners.forEach(
            listener -> listener.accept(boardPanel.boardHandling.getRoutingBoard()));
      }
    } else {
      ObjectInputStream objectStream;
      try {
        objectStream = new ObjectInputStream(inputStream);
      } catch (IOException _) {
        this.updateTexts();
        return false;
      }
      boolean readOk = boardPanel.boardHandling.loadFromBinary(objectStream);
      if (!readOk) {
        this.updateTexts();
        return restoreTutorialBoardAfterFailedLoad(null);
      }

      // Raise an event to notify the observers that a new board has been loaded
      this.boardLoadedEventListeners.forEach(
          listener -> listener.accept(boardPanel.boardHandling.getRoutingBoard()));

      // Read and set the GUI settings from the binary file
      Point frameLocation;
      Rectangle frameBounds;
      try {
        viewportPosition = (Point) objectStream.readObject();
        frameLocation = (Point) objectStream.readObject();
        frameBounds = (Rectangle) objectStream.readObject();
      } catch (Exception _) {
        this.updateTexts();
        return false;
      }
      this.setLocation(frameLocation);
      this.setBounds(frameBounds);

      allocatePermanentSubwindows();

      for (int i = 0; i < this.permanentSubwindows.length; i++) {
        if (this.permanentSubwindows[i] != null) {
          this.permanentSubwindows[i].read(objectStream);
        }
      }
    }

    try {
      inputStream.close();
    } catch (IOException _) {
      this.updateTexts();
      return restoreTutorialBoardAfterFailedLoad(null);
    }

    boolean guiUpdated = updateGui(format, readResult, viewportPosition, messageField, false);
    if (!guiUpdated) {
      return restoreTutorialBoardAfterFailedLoad(null);
    }
    return true;
  }

  private boolean updateGui(
      FileFormat format,
      BoardReadResult readResult,
      Point viewportPosition,
      JTextField messageField,
      boolean deferHeavyWork) {
    boolean isTextDsnOrJson = format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON;
    if (isTextDsnOrJson) {
      if (!(readResult instanceof BoardReadResult.Success)) {
        if (messageField != null) {
          if (readResult instanceof BoardReadResult.OutlineMissing) {
            messageField.setText(tm.getText("error_dsn_outline_missing"));
          } else {
            messageField.setText(tm.getText("error_dsn_read_failed"));
          }
        }
        this.updateTexts();
        return false;
      }
    }

    Dimension panelSize = boardPanel.boardHandling.graphicsContext.getPanelSize();
    boardPanel.setSize(panelSize);
    boardPanel.setPreferredSize(panelSize);
    if (viewportPosition != null) {
      boardPanel.setViewportPosition(viewportPosition);
    }
    if (!deferHeavyWork) {
      boardPanel.createPopupMenus();
    }
    boardPanel.initColors();
    if (!deferHeavyWork) {
      boardPanel.boardHandling.createRatsnestIfAbsent();
    }
    this.setToolbarModeSelectionPanelValue(boardPanel.boardHandling.getEditorState());
    this.setToolbarUnitSelectionPanelValue(boardPanel.boardHandling.coordinateTransform.userUnit);
    this.setVisible(true);
    if (isTextDsnOrJson) {
      if (!deferHeavyWork) {
        // Read the default gui settings, if gui default file exists.
        InputStream inputStream = null;
        boolean defaultsFileFound;

        File defaultsFile =
            new File(this.routingJob.input.getAbsolutePath(), GUI_DEFAULTS_FILE_NAME);
        defaultsFileFound = true;
        try {
          inputStream = new FileInputStream(defaultsFile);
        } catch (FileNotFoundException _) {
          defaultsFileFound = false;
        }

        if (defaultsFileFound) {
          boolean readOk = GuiDefaultsFile.read(this, boardPanel.boardHandling, inputStream);
          if (!readOk) {
            screenMessages.setStatusMessage(tm.getText("error_gui_defaults_read_failed"));
          }
          try {
            inputStream.close();
          } catch (IOException _) {
            return false;
          }
        }
        this.zoomAll();
        boardPanel.repaint();
      }
    }
    if (!deferHeavyWork) {
      this.updateTexts();
    }
    return true;
  }

  private boolean updateGui(
      FileFormat format,
      BoardReadResult readResult,
      Point viewportPosition,
      JTextField messageField) {
    return updateGui(format, readResult, viewportPosition, messageField, false);
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a version-specific binary stream.
   * Returns false, if the save failed.
   */
  private boolean saveAsBinary(OutputStream outputStream) throws Exception {
    return exportActions.saveAsBinary(outputStream);
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a binary file. Returns false, if the
   * save failed.
   */
  public boolean saveAsBinary(File outputFile) {
    return exportActions.saveAsBinary(outputFile);
  }

  /**
   * Writes a Specctra Session File (SES). Returns false, if write operation fails. DEPRECATED: use
   * HeadlessBoardManager.saveAsSpecctraSessionSes instead
   */
  @Deprecated
  public boolean saveAsSpecctraSessionSes(File outputFile, String designName) {
    return exportActions.saveAsSpecctraSessionSes(outputFile, designName);
  }

  /** Writes a KiCad Session JSON File. Returns false if write operation fails. */
  public boolean saveAsKiCadJson(File outputFile, String designName) {
    return exportActions.saveAsKiCadJson(outputFile, designName);
  }

  /**
   * Displays the save-file chooser using the current output format.
   *
   * @param defaultDirectory the directory used when no output file has been selected
   * @param output the output file details and format
   * @return the file selected by the user, or {@code null} when the dialog is cancelled
   */
  public File showSaveAsDialog(String defaultDirectory, BoardFileDetails output) {
    return exportActions.showSaveAsDialog(defaultDirectory, output);
  }

  /** Saves the board rule to file, so that they can be reused later on. */
  public boolean saveRulesAs(File rulesFile, String designName, GuiBoardManager boardHandling) {
    return exportActions.saveRulesAs(rulesFile, designName, boardHandling);
  }

  /**
   * Saves the current routing session as an Eagle script file.
   *
   * @param outputFile the destination script file
   * @param designName the design name written into the intermediate session
   */
  public void saveAsEagleScriptScr(File outputFile, String designName) {
    exportActions.saveAsEagleScriptScr(outputFile, designName);
  }

  /** Writes a Specctra Design File (DSN). Returns false, if write operation fails. */
  public boolean saveAsSpecctraDesignDsn(
      File outputFile, String designName, boolean compatibilityMode) {
    return exportActions.saveAsSpecctraDesignDsn(outputFile, designName, compatibilityMode);
  }

  /** Sets the toolbar to the buttons of the selected item state. */
  public void setInspectToolbar() {
    getContentPane().remove(toolbarPanel);
    getContentPane().add(inspectToolbar, BorderLayout.NORTH);
    repaint();
  }

  /** Sets the toolbar buttons to the select. route and drag menu buttons of the main menu. */
  public void setMenuToolbar() {
    getContentPane().remove(inspectToolbar);
    getContentPane().add(toolbarPanel, BorderLayout.NORTH);
    repaint();
  }

  /** Calculates the absolute location of the board frame in his outmost parent frame. */
  public Point absolutePanelLocation() {
    int x = this.scrollPane.getX();
    int y = this.scrollPane.getY();
    Container currentParent = this.scrollPane.getParent();
    while (currentParent != null) {
      x += currentParent.getX();
      y += currentParent.getY();
      currentParent = currentParent.getParent();
    }
    return new Point(x, y);
  }

  /** Sets the displayed region to the whole board. */
  public void zoomAll() {
    boardPanel.boardHandling.adjustDesignBounds();
    Rectangle displayRect = boardPanel.getViewportBounds();
    Rectangle designBounds = boardPanel.boardHandling.graphicsContext.getDesignBounds();
    double widthFactor = displayRect.getWidth() / designBounds.getWidth();
    double heightFactor = displayRect.getHeight() / designBounds.getHeight();
    double zoomFactor = Math.min(widthFactor, heightFactor);
    Point2D zoomCenter = boardPanel.boardHandling.graphicsContext.getDesignCenter();
    boardPanel.zoom(zoomFactor, zoomCenter);
    Point2D newVieportCenter = boardPanel.boardHandling.graphicsContext.getDesignCenter();
    boardPanel.setViewportCenter(newVieportCenter);
  }

  /** Actions to be taken when this frame vanishes. */
  @Override
  public void dispose() {
    if (activeFrame == this) {
      activeFrame = null;
    }
    windowLayout.disposePermanentSubwindows();
    for (BoardTemporarySubWindow currentSubwindow : this.temporarySubwindows) {
      if (currentSubwindow != null) {
        currentSubwindow.boardFrameDisposed();
      }
    }
    if (boardPanel.boardHandling != null) {
      boardPanel.boardHandling.dispose();
      boardPanel.boardHandling = null;
    }
    if (this.logEntryAddedListener != null) {
      FRLogger.getLogEntries().removeLogEntryAddedListener(this.logEntryAddedListener);
    }
    super.dispose();
  }

  /**
   * Initializes and creates instances for all the "permanent" subwindows. These are the utility
   * windows (parameters, colors, visibility, etc.) that can be toggled via the menu but exist for
   * the lifetime of the BoardFrame. They are stored in the {@code permanentSubwindows} array for
   * easy management.
   */
  private void allocatePermanentSubwindows() {
    windowLayout.allocatePermanentSubwindows();
  }

  private void allocateEssentialSubwindows() {
    windowLayout.allocateEssentialSubwindows();
  }

  private void allocateRemainingSubwindows() {
    windowLayout.allocateRemainingSubwindows();
  }

  /**
   * Creates the additional frames of the board frame.
   *
   * @param showEssentialImmediately when {@code true}, General Settings is shown in this EDT pass;
   *     remaining tool windows are still created on a later cycle
   */
  private void initializeWindows(boolean showEssentialImmediately) {
    windowLayout.initializeWindows(showEssentialImmediately);
  }

  private void initializeWindows() {
    windowLayout.initializeWindows();
  }

  /** Returns the locale used for language-dependent output. */
  public Locale getLocale() {
    return this.locale;
  }

  /** Sets the background color of the board panel. */
  public void setBoardBackground(Color color) {
    this.boardPanel.setBackground(color);
  }

  /** Refreshes all displayed coordinates after the user unit has changed. */
  public void refreshWindows() {
    windowLayout.refreshWindows();
  }

  /** Sets the mode value on mode selection component of the toolbar. */
  public void setToolbarModeSelectionPanelValue(EditorStateHandle editorState) {
    this.toolbarPanel.setModeSelectionPanelValue(editorState);
  }

  private void setToolbarUnitSelectionPanelValue(Unit unit) {
    this.toolbarPanel.setUnitSelectionPanelValue(unit);
  }

  /** Repaints this board frame and all the subwindows of the board. */
  public void repaintAll() {
    windowLayout.repaintAll();
  }

  /** Registers a listener that is notified after a board has been loaded. */
  public void addBoardLoadedEventListener(Consumer<RoutingBoard> listener) {
    boardLoadedEventListeners.add(listener);
  }

  /**
   * Returns the array of permanent subwindows for use by {@code
   * GuiBoardManager.refreshGuiFromSettings()}.
   */
  public BoardSavableSubWindow[] getPermanentSubwindows() {
    return windowLayout.getPermanentSubwindows();
  }

  /** Registers a listener that is notified after a board has been saved. */
  public void addReadOnlyEventListener(Consumer<RoutingBoard> listener) {
    boardSavedEventListeners.add(listener);
  }

  /**
   * Loads a file that was dropped onto the board panel. Follows the same pattern as the File menu
   * open operation. Shows a save confirmation dialog if the current board has unsaved changes.
   *
   * @param file the file to load
   * @param format the format of the file (DSN or KiCad design JSON)
   */
  public void loadDroppedFile(File file, FileFormat format) {
    if (file == null) {
      return;
    }

    // Validate format is supported
    if (format != FileFormat.DSN && format != FileFormat.KICAD_DESIGN_JSON) {
      FRLogger.warn("Dropped file format not supported: " + format);
      return;
    }

    // Check if there's a board with unsaved changes and prompt to save
    if (boardPanel != null && boardPanel.boardHandling != null) {
      boolean shouldProceed = confirmSaveBeforeLoad(this);
      if (!shouldProceed) {
        return;
      }
    }

    // Clear any existing jobs for this session (single board support)
    String sessionId = SessionManager.getInstance().getPrimarySession().id.toString();
    RoutingJobScheduler.getInstance().clearJobs(sessionId);

    try {
      routingJob.setInput(file);
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
    if (boardPanel != null
        && boardPanel.boardHandling != null
        && routingJob.input.format != FileFormat.UNKNOWN) {
      // Read file content
      byte[] fileContent;
      try {
        fileContent = Files.readAllBytes(file.toPath());
      } catch (IOException e) {
        FRLogger.error("Could not read dropped file content", e);
        return;
      }

      if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
        loadFromBytesAsync(fileContent, format, routingJob);
        FRAnalytics.buttonClicked(
            "file_dropped_" + format.name().toLowerCase(), routingJob.getInputFileDetails());
      }
    }
  }

  /**
   * Convenience overload that auto-detects format.
   *
   * @param file the file to load
   */
  public void loadDroppedFile(File file) {
    if (file == null) {
      return;
    }

    FileFormat format = RoutingJob.getFileFormat(file.toPath());
    if (format == FileFormat.UNKNOWN) {
      try {
        byte[] content = Files.readAllBytes(file.toPath());
        format = RoutingJob.getFileFormat(content);
      } catch (IOException e) {
        FRLogger.error("Could not read dropped file for format detection", e);
        return;
      }
    }

    loadDroppedFile(file, format);
  }

  /**
   * Shows a save confirmation dialog if the board has been modified.
   *
   * @param parent the parent frame for the dialog
   * @return true if loading should proceed, false if cancelled
   */
  private boolean confirmSaveBeforeLoad(BoardFrame parent) {
    if (boardPanel == null || boardPanel.boardHandling == null) {
      return true;
    }

    try {
      boolean isChanged = boardPanel.boardHandling.isBoardChanged();
      if (isChanged) {
        Object[] options = {
          tm.getText("confirm_save_yes"),
          tm.getText("confirm_save_no"),
          tm.getText("confirm_save_cancel")
        };
        JOptionPane optionPane =
            new JOptionPane(
                tm.getText("confirm_save_changes"),
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.YES_NO_CANCEL_OPTION,
                null,
                options,
                options[2] // Default to "Cancel"
                );
        JDialog dialog = optionPane.createDialog(parent, tm.getText("confirm_save_title"));
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
      boolean wasBoardChanged = boardPanel.boardHandling.isBoardChanged();
      if (wasBoardChanged) {
        // Create a JOptionPane with a warning icon and set the default option to NO
        Object[] options = {tm.getText("confirm_exit_yes"), tm.getText("confirm_exit_no")};
        JOptionPane optionPane =
            new JOptionPane(
                tm.getText("confirm_cancel"),
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null,
                options,
                options[1] // Default to "No"
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
      for (int i = 0; i < permanentSubwindows.length; i++) {
        if (permanentSubwindows[i] != null) {
          permanentSubwindows[i].parentIconified();
        }
      }
      for (BoardSubWindow currentSubwindow : temporarySubwindows) {
        if (currentSubwindow != null) {
          currentSubwindow.parentIconified();
        }
      }
    }

    @Override
    public void windowDeiconified(WindowEvent evt) {
      for (BoardSavableSubWindow permanentSubwindow : permanentSubwindows) {
        if (permanentSubwindow != null) {
          permanentSubwindow.parentDeiconified();
        }
      }
      for (BoardSubWindow currentSubwindow : temporarySubwindows) {
        if (currentSubwindow != null) {
          currentSubwindow.parentDeiconified();
        }
      }
    }
  }
}
