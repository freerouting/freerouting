package app.freerouting.gui;

import app.freerouting.Freerouting;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.boardgraphics.TutorialBoardPalette;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.RatsNest;
import app.freerouting.interactive.ScreenMessages;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.RulesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.util.TextManager;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/** Graphical frame containing the Menu, Toolbar, Canvas and Status bar. */
public class BoardFrame extends WindowBase {

  private static final String TUTORIAL_BOARD_FILENAME = "tutorial_board.dsn";

  public static volatile BoardFrame activeFrame;

  /** The windows above stored in an array. */
  static final int SUBWINDOW_COUNT = 24;

  static final String GUI_DEFAULTS_FILE_NAME = "gui_defaults.par";
  static final String GUI_DEFAULTS_FILE_BACKUP_NAME = "gui_defaults.par.bak";

  /** The current routing job (design) being edited. */
  public RoutingJob routingJob;

  /** The menubar of this frame. */
  public final BoardMenuBar menubar;

  /** The scroll pane for the panel of the routing board. */
  final JScrollPane scrollPane;

  /** Handles displaying messages to the user. */
  final ScreenMessages screenMessages;

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

  /** The panel with the graphical representation of the board. */
  BoardPanel boardPanel;

  // -- Subwindows for various settings and tools --
  WindowAbout aboutWindow;
  WindowRouteParameter routeParameterWindow;
  WindowAutorouteParameter autorouteParameterWindow;
  WindowSelectParameter selectParameterWindow;
  WindowMoveParameter moveParameterWindow;
  WindowClearanceMatrix clearanceMatrixWindow;
  WindowVia viaWindow;
  WindowEditVias editViasWindow;
  WindowNetClasses editNetRulesWindow;
  WindowAssignNetClass assignNetClassesWindow;
  WindowPadstacks padstacksWindow;
  WindowPackages packagesWindow;
  WindowIncompletes incompletesWindow;
  WindowNets netInfoWindow;
  WindowClearanceViolations clearanceViolationsWindow;
  WindowLengthViolations lengthViolationsWindow;
  WindowUnconnectedRoute unconnectedRouteWindow;
  WindowRouteStubs routeStubsWindow;
  WindowComponents componentsWindow;
  WindowVisibility visibilityWindow;
  WindowDisplayMisc displayMiscWindow;

  ColorManager colorManager;

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
  BoardFrame(
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

    // Set the menu bar of this frame.
    this.menubar = new BoardMenuBar(this, globalSettings.featureFlags);

    this.menubar.fileMenu.addOpenEventListener(
        (File selectedFile) -> {
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

            javax.swing.SwingUtilities.invokeLater(
                () -> {
                  String sessionId = SessionManager.getInstance().getGuiSession().id.toString();
                  RoutingJobScheduler.getInstance().clearJobs(sessionId);
                  RoutingJobScheduler.getInstance().enqueueJob(routingJob);

                  String oldInputDirectory = globalSettings.guiSettings.inputDirectory;
                  globalSettings.guiSettings.inputDirectory = routingJob.input.getDirectoryPath();
                  if (!oldInputDirectory.equals(globalSettings.guiSettings.inputDirectory)) {
                    try {
                      GlobalSettings.saveAsJson(globalSettings);
                    } catch (IOException e) {
                      FRLogger.error(
                          "Couldn't save the global settings to the configuration file", e);
                    }
                  }
                  try {
                    GlobalSettings.setDefaultValue(
                        "gui.input_directory", routingJob.input.getDirectoryPath());
                  } catch (Exception e) {
                    FRLogger.error(
                        "Couldn't update the input directory in the configuration file", e);
                  }
                });

            if (boardPanel != null && boardPanel.boardHandling != null) {
              switch (inputFormat) {
                case DSN:
                  loadFromBytesAsync(fileContent, FileFormat.DSN, routingJob);
                  FRAnalytics.buttonClicked(
                      "fileio_loaddsn", this.routingJob.getInputFileDetails());
                  break;
                case KICAD_DESIGN_JSON:
                  loadFromBytesAsync(fileContent, FileFormat.KICAD_DESIGN_JSON, routingJob);
                  FRAnalytics.buttonClicked(
                      "fileio_loadjson", this.routingJob.getInputFileDetails());
                  break;
                case FRB:
                  if (!this.load(
                      new ByteArrayInputStream(fileContent), FileFormat.FRB, null, routingJob)) {
                    restoreTutorialBoardAfterFailedLoad(null);
                  }
                  FRAnalytics.buttonClicked(
                      "fileio_loadfrb", this.routingJob.getInputFileDetails());
                  break;
                default:
                  FRLogger.warn(
                      "Loading the board failed, because the selected file format is not "
                          + "supported.");
                  break;
              }
            }
          }
        });

    this.menubar.fileMenu.addSaveAsEventListener(
        (File selectedFile) -> {
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
              boolean sesFileSaved =
                  this.saveAsSpecctraSessionSes(
                      this.routingJob.output.getFile(), this.routingJob.input.getFilename());
              // Save the rules file as well, if the user wants to
              if (sesFileSaved
                  && WindowMessage.confirm(
                      tm.getText("confirm_rules_save"), JOptionPane.NO_OPTION)) {
                saveRulesAs(
                    this.routingJob.getRulesFile(),
                    this.routingJob.input.getFilename(),
                    boardPanel.boardHandling);
              }
              FRAnalytics.fileSaved("SES", this.routingJob.getOutputFileDetails());
              FRAnalytics.buttonClicked("fileio_saveses", this.routingJob.getOutputFileDetails());
              break;
            case KICAD_SESSION_JSON:
              // Save the file as a KiCad session JSON file
              this.saveAsKiCadJson(
                  this.routingJob.output.getFile(), this.routingJob.input.getFilename());
              FRAnalytics.fileSaved("KICAD_SESSION_JSON", this.routingJob.getOutputFileDetails());
              FRAnalytics.buttonClicked(
                  "fileio_savekicadjson", this.routingJob.getOutputFileDetails());
              break;
            case DSN:
              // Save the file as a Specctra DSN file
              this.saveAsSpecctraDesignDsn(
                  this.routingJob.output.getFile(), this.routingJob.input.getFilename(), false);
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
              this.saveAsEagleScriptScr(
                  this.routingJob.getEagleScriptFile(), this.routingJob.input.getFilename());
              FRAnalytics.fileSaved("SCR", this.routingJob.input.getFilename());
              FRAnalytics.buttonClicked("fileio_savescr", "");
              break;
            default:
              // The file format is not supported
              FRLogger.warn(
                  "Saving the board failed, because the selected file format is not supported.");
              break;
          }
        });

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
    boardPanel.resetBoardHandling(job);
    boardPanel.boardHandling.replaceRoutingBoard(board);

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
      var interactiveSettings = boardPanel.boardHandling.getInteractiveSettings();
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
  private void loadFromBytesAsync(byte[] fileContent, FileFormat format, RoutingJob job) {
    ensureGeneralSettingsVisibleDuringLoad();

    String filename = job.input != null ? job.input.getFilename() : null;
    TextManager guiTm = new TextManager(GuiManager.class, locale);
    String loadingMessage =
        filename != null
            ? guiTm.getText("loading_design_with_file", filename)
            : guiTm.getText("loading_design");
    WindowMessage loadingWindow = WindowMessage.show(loadingMessage);
    loadingWindow.setLocationRelativeTo(this);

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
                      finishLoadFromParseResult(
                          readResult, fileContent, format, job, loadingWindow));
            });
  }

  private static BoardReadResult parseBoardFromBytes(
      byte[] fileContent, FileFormat format, String filename) {
    try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
      if (format == FileFormat.DSN) {
        return DsnReader.readBoard(
            inputStream, null, new ItemIdentificationNumberGenerator(), filename);
      }
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        try (java.io.Reader reader =
            new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
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
    for (int i = 0; i < this.permanentSubwindows.length; i++) {
      if (this.permanentSubwindows[i] != null) {
        this.permanentSubwindows[i].dispose();
        this.permanentSubwindows[i] = null;
      }
    }
    selectParameterWindow = null;
    colorManager = null;
    visibilityWindow = null;
    displayMiscWindow = null;
    routeParameterWindow = null;
    autorouteParameterWindow = null;
    moveParameterWindow = null;
    clearanceMatrixWindow = null;
    viaWindow = null;
    editViasWindow = null;
    editNetRulesWindow = null;
    assignNetClassesWindow = null;
    padstacksWindow = null;
    packagesWindow = null;
    componentsWindow = null;
    incompletesWindow = null;
    clearanceViolationsWindow = null;
    lengthViolationsWindow = null;
    netInfoWindow = null;
    unconnectedRouteWindow = null;
    routeStubsWindow = null;
    aboutWindow = null;
  }

  private void finishLoadFromParseResult(
      BoardReadResult readResult,
      byte[] fileContent,
      FileFormat format,
      RoutingJob routingJob,
      WindowMessage loadingWindow) {
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
      scheduleInitialBoardPaint(loadingWindow, format, readResult);
    } else {
      javax.swing.SwingUtilities.invokeLater(() -> completeHeavyGuiAfterLoad(format, readResult));
    }
  }

  /**
   * Shows rendering feedback, paints the board with fast simplified plane fills, then warms
   * detailed plane geometry on a background thread before a full-quality repaint.
   */
  private void scheduleInitialBoardPaint(
      WindowMessage loadingWindow, FileFormat format, BoardReadResult readResult) {
    var graphicsContext = boardPanel.boardHandling.graphicsContext;
    String renderingStatus = tm.getText("rendering_board");
    boardPanel.showRenderingOverlay(renderingStatus);
    screenMessages.setStatusMessage(renderingStatus);
    graphicsContext.setSimplifiedPlaneRendering(true);

    javax.swing.SwingUtilities.invokeLater(
        () -> {
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
                schedulePlaneFillCacheWarm();
                javax.swing.SwingUtilities.invokeLater(
                    () -> completeHeavyGuiAfterLoad(format, readResult));
              });
        });
  }

  private void schedulePlaneFillCacheWarm() {
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
              for (app.freerouting.board.ConductionArea area : conductionAreas) {
                area.warmDetailedFillCache();
              }
              long warmMs = (System.nanoTime() - warmStart) / 1_000_000L;
              FRLogger.debug("Board load: plane fill cache warmed in " + warmMs + " ms");
              javax.swing.SwingUtilities.invokeLater(
                  () -> {
                    GuiBoardManager currentBoardHandling = boardPanel.boardHandling;
                    if (currentBoardHandling != null
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
        var interactiveSettings = boardPanel.boardHandling.getInteractiveSettings();
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
  private boolean restoreTutorialBoardAfterFailedLoad(WindowMessage loadingWindow) {
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
          parseBoardFromBytes(tutorialBytes, FileFormat.DSN, TUTORIAL_BOARD_FILENAME);
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

  private void completeHeavyGuiAfterLoad(FileFormat format, BoardReadResult readResult) {
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
        boolean readOk = GUIDefaultsFile.read(this, boardPanel.boardHandling, inputStream);
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
  boolean load(
      InputStream inputStream, FileFormat format, JTextField messageField, RoutingJob routingJob) {
    Point viewportPosition = null;
    BoardReadResult readResult = null;

    boardPanel.resetBoardHandling(routingJob);
    disposePermanentSubwindows();

    if (format == FileFormat.DSN || format == FileFormat.KICAD_DESIGN_JSON) {
      if (format == FileFormat.KICAD_DESIGN_JSON) {
        readResult =
            boardPanel.boardHandling.loadFromKiCadJson(
                inputStream, this.boardObservers, new ItemIdentificationNumberGenerator());
      } else {
        readResult =
            boardPanel.boardHandling.loadFromSpecctraDsn(
                inputStream, this.boardObservers, new ItemIdentificationNumberGenerator());
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
          var interactiveSettings = boardPanel.boardHandling.getInteractiveSettings();
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
    this.setToolbarModeSelectionPanelValue(boardPanel.boardHandling.getInteractiveState());
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
          boolean readOk = GUIDefaultsFile.read(this, boardPanel.boardHandling, inputStream);
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
    ObjectOutputStream objectStream;
    objectStream = new ObjectOutputStream(outputStream);

    // (1) Save the board as binary file
    boolean saveOk = boardPanel.boardHandling.saveAsBinary(objectStream);
    if (!saveOk) {
      return false;
    }

    // (2) Save the GUI settings as binary file
    objectStream.writeObject(boardPanel.getViewportPosition());
    objectStream.writeObject(this.getLocation());
    objectStream.writeObject(this.getBounds());

    // (3) Save the permanent subwindows as binary file
    for (int i = 0; i < this.permanentSubwindows.length; i++) {
      if (this.permanentSubwindows[i] != null) {
        this.permanentSubwindows[i].save(objectStream);
      }
    }

    // (4) Flush the binary file
    objectStream.flush();
    return true;
  }

  /**
   * Saves the board, GUI settings and subwindows to disk as a binary file. Returns false, if the
   * save failed.
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

      screenMessages.setStatusMessage(
          tm.getText("message_binary_file_saved", outputFile.getPath()));
      return true;
    } catch (Exception _) {
      screenMessages.setStatusMessage(
          tm.getText("message_binary_file_save_failed", outputFile.getPath()));
      return false;
    }
  }

  /**
   * Writes a Specctra Session File (SES). Returns false, if write operation fails. DEPRECATED: use
   * HeadlessBoardManager.saveAsSpecctraSessionSes instead
   */
  @Deprecated
  public boolean saveAsSpecctraSessionSes(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (!boardPanel.boardHandling.saveAsSpecctraSessionSes(outputStream, designName)) {
        this.screenMessages.setStatusMessage(
            tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
        return false;
      }
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra session file '" + outputFile.getPath() + "'", e);
      this.screenMessages.setStatusMessage(
          tm.getText("message_specctra_ses_save_failed", outputFile.getPath()));
      return false;
    }

    this.screenMessages.setStatusMessage(
        tm.getText("message_specctra_ses_saved", outputFile.getPath()));

    return true;
  }

  /** Writes a KiCad Session JSON File. Returns false if write operation fails. */
  public boolean saveAsKiCadJson(File outputFile, String designName) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (java.io.FileWriter writer = new java.io.FileWriter(outputFile)) {
      String json =
          app.freerouting.io.kicad.KiCadJsonWriter.write(
              boardPanel.boardHandling.getRoutingBoard(), designName);
      writer.write(json);
    } catch (Exception e) {
      FRLogger.error("Unable to write KiCad JSON file", e);
      this.screenMessages.setStatusMessage(
          tm.getText("message_kicad_session_json_save_failed", outputFile.getPath()));
      return false;
    }

    this.screenMessages.setStatusMessage(
        tm.getText("message_kicad_session_json_saved", outputFile.getPath()));
    return true;
  }

  /**
   * Displays the save-file chooser using the current output format.
   *
   * @param defaultDirectory the directory used when no output file has been selected
   * @param output the output file details and format
   * @return the file selected by the user, or {@code null} when the dialog is cancelled
   */
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

    // Add the file filter for SPECCTRA Session .SES files
    FileNameExtensionFilter sesFilter =
        new FileNameExtensionFilter("SPECCTRA Session file (*.ses)", "ses");
    fileChooser.addChoosableFileFilter(sesFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter =
        new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Add the file filter for Eagle script .SCR files
    FileNameExtensionFilter scrFilter =
        new FileNameExtensionFilter("Eagle Session Script file (*.scr)", "scr");
    fileChooser.addChoosableFileFilter(scrFilter);

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter =
        new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Add the file filter for KiCad Session JSON files
    FileNameExtensionFilter jsonSessionFilter =
        new FileNameExtensionFilter("KiCad Session JSON file (*.json)", "json");
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
    if (!output.getFilename().isEmpty()) {
      fileChooser.setSelectedFile(output.getFile());
    }

    fileChooser.showSaveDialog(this);

    return fileChooser.getSelectedFile();
  }

  /** Saves the board rule to file, so that they can be reused later on. */
  private boolean saveRulesAs(File rulesFile, String designName, GuiBoardManager boardHandling) {
    FRLogger.info("Saving '" + rulesFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(rulesFile)) {
      RulesWriter.write(boardHandling.getRoutingBoard(), outputStream, designName);
      return true;
    } catch (IOException e) {
      FRLogger.error("unable to save rules file for design '" + designName + "'", e);
      return false;
    }
  }

  /**
   * Saves the current routing session as an Eagle script file.
   *
   * @param outputFile the destination script file
   * @param designName the design name written into the intermediate session
   */
  public void saveAsEagleScriptScr(File outputFile, String designName) {
    ByteArrayOutputStream sesOutputStream = new ByteArrayOutputStream();
    if (!boardPanel.boardHandling.saveAsSpecctraSessionSes(sesOutputStream, designName)) {
      return;
    }
    InputStream sesInputStream = new ByteArrayInputStream(sesOutputStream.toByteArray());

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");

    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      if (boardPanel.boardHandling.saveSpecctraSessionSesAsEagleScriptScr(
          sesInputStream, outputStream)) {
        screenMessages.setStatusMessage(tm.getText("message_eagle_saved", outputFile.getPath()));
      } else {
        screenMessages.setStatusMessage(
            tm.getText("message_eagle_save_failed", outputFile.getPath()));
      }
    } catch (IOException e) {
      FRLogger.error("unable to save Eagle script file '" + outputFile.getPath() + "'", e);
      screenMessages.setStatusMessage(
          tm.getText("message_eagle_save_failed", outputFile.getPath()));
    }
  }

  /** Writes a Specctra Design File (DSN). Returns false, if write operation fails. */
  public boolean saveAsSpecctraDesignDsn(
      File outputFile, String designName, boolean compatibilityMode) {
    if (outputFile == null) {
      return false;
    }

    FRLogger.info("Saving '" + outputFile.getPath() + "'...");
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      return boardPanel.boardHandling.saveAsSpecctraDesignDsn(
          outputStream, designName, compatibilityMode);
    } catch (IOException e) {
      FRLogger.error("unable to save Specctra design file '" + outputFile.getPath() + "'", e);
      return false;
    }
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
  Point absolutePanelLocation() {
    int x = this.scrollPane.getX();
    int y = this.scrollPane.getY();
    Container currParent = this.scrollPane.getParent();
    while (currParent != null) {
      x += currParent.getX();
      y += currParent.getY();
      currParent = currParent.getParent();
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
    for (int i = 0; i < this.permanentSubwindows.length; i++) {
      if (this.permanentSubwindows[i] != null) {
        this.permanentSubwindows[i].dispose();
        this.permanentSubwindows[i] = null;
      }
    }
    for (BoardTemporarySubWindow currSubwindow : this.temporarySubwindows) {
      if (currSubwindow != null) {
        currSubwindow.boardFrameDisposed();
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
    allocateEssentialSubwindows();
    allocateRemainingSubwindows();
  }

  private void allocateEssentialSubwindows() {
    if (this.selectParameterWindow == null) {
      this.selectParameterWindow = new WindowSelectParameter(this);
      this.permanentSubwindows[6] = this.selectParameterWindow;
    }
  }

  private void allocateRemainingSubwindows() {
    if (this.colorManager != null) {
      return;
    }
    this.colorManager = new ColorManager(this);
    this.permanentSubwindows[0] = this.colorManager;
    this.visibilityWindow = new WindowVisibility(this);
    this.permanentSubwindows[1] = this.visibilityWindow;
    this.permanentSubwindows[2] = null;
    this.displayMiscWindow = new WindowDisplayMisc(this);
    this.permanentSubwindows[3] = this.displayMiscWindow;

    this.routeParameterWindow = new WindowRouteParameter(this);
    this.permanentSubwindows[5] = this.routeParameterWindow;
    this.clearanceMatrixWindow = new WindowClearanceMatrix(this);
    this.permanentSubwindows[7] = this.clearanceMatrixWindow;
    this.padstacksWindow = new WindowPadstacks(this);
    this.permanentSubwindows[8] = this.padstacksWindow;
    this.packagesWindow = new WindowPackages(this);
    this.permanentSubwindows[9] = this.packagesWindow;
    this.componentsWindow = new WindowComponents(this);
    this.permanentSubwindows[10] = this.componentsWindow;
    this.incompletesWindow = new WindowIncompletes(this);
    this.permanentSubwindows[11] = this.incompletesWindow;
    this.clearanceViolationsWindow = new WindowClearanceViolations(this);
    this.permanentSubwindows[12] = this.clearanceViolationsWindow;
    this.netInfoWindow = new WindowNets(this);
    this.permanentSubwindows[13] = this.netInfoWindow;
    this.viaWindow = new WindowVia(this);
    this.permanentSubwindows[14] = this.viaWindow;
    this.editViasWindow = new WindowEditVias(this);
    this.permanentSubwindows[15] = this.editViasWindow;
    this.editNetRulesWindow = new WindowNetClasses(this);
    this.permanentSubwindows[16] = this.editNetRulesWindow;
    this.assignNetClassesWindow = new WindowAssignNetClass(this);
    this.permanentSubwindows[17] = this.assignNetClassesWindow;
    this.lengthViolationsWindow = new WindowLengthViolations(this);
    this.permanentSubwindows[18] = this.lengthViolationsWindow;
    this.aboutWindow = new WindowAbout(this.locale, this.freeroutingVersion);
    this.permanentSubwindows[19] = this.aboutWindow;
    this.moveParameterWindow = new WindowMoveParameter(this);
    this.permanentSubwindows[20] = this.moveParameterWindow;
    this.unconnectedRouteWindow = new WindowUnconnectedRoute(this);
    this.permanentSubwindows[21] = this.unconnectedRouteWindow;
    this.routeStubsWindow = new WindowRouteStubs(this);
    this.permanentSubwindows[22] = this.routeStubsWindow;
    this.autorouteParameterWindow = new WindowAutorouteParameter(this);
    this.permanentSubwindows[23] = this.autorouteParameterWindow;
  }

  /**
   * Creates the additional frames of the board frame.
   *
   * @param showEssentialImmediately when {@code true}, General Settings is shown in this EDT pass;
   *     remaining tool windows are still created on a later cycle
   */
  private void initializeWindows(boolean showEssentialImmediately) {
    allocateEssentialSubwindows();

    this.setLocation(120, 0);

    this.selectParameterWindow.setLocation(0, 0);

    if (showEssentialImmediately) {
      this.selectParameterWindow.setVisible(true);
      javax.swing.SwingUtilities.invokeLater(
          () -> {
            allocateRemainingSubwindows();
            positionRemainingSubwindows();
          });
    } else {
      javax.swing.SwingUtilities.invokeLater(
          () -> {
            this.selectParameterWindow.setVisible(true);
            allocateRemainingSubwindows();
            positionRemainingSubwindows();
          });
    }
  }

  private void initializeWindows() {
    initializeWindows(false);
  }

  private void positionRemainingSubwindows() {
    this.routeParameterWindow.setLocation(0, 100);
    this.autorouteParameterWindow.setLocation(0, 200);
    this.moveParameterWindow.setLocation(0, 50);
    this.clearanceMatrixWindow.setLocation(0, 150);
    this.viaWindow.setLocation(50, 150);
    this.editViasWindow.setLocation(100, 150);
    this.editNetRulesWindow.setLocation(100, 200);
    this.assignNetClassesWindow.setLocation(100, 250);
    this.padstacksWindow.setLocation(100, 30);
    this.packagesWindow.setLocation(200, 30);
    this.componentsWindow.setLocation(300, 30);
    this.incompletesWindow.setLocation(400, 30);
    this.clearanceViolationsWindow.setLocation(500, 30);
    this.lengthViolationsWindow.setLocation(550, 30);
    this.netInfoWindow.setLocation(350, 30);
    this.unconnectedRouteWindow.setLocation(650, 30);
    this.routeStubsWindow.setLocation(600, 30);

    this.visibilityWindow.setLocation(0, 450);
    this.displayMiscWindow.setLocation(0, 350);
    this.colorManager.setLocation(0, 600);
    this.aboutWindow.setLocation(200, 200);
  }

  /** Returns the locale used for language-dependent output. */
  // CHECKSTYLE.SUPPRESS: MethodName for +1 lines
  public Locale get_locale() {
    return this.locale;
  }

  /** Sets the background color of the board panel. */
  public void setBoardBackground(Color color) {
    this.boardPanel.setBackground(color);
  }

  /** Refreshes all displayed coordinates after the user unit has changed. */
  public void refreshWindows() {
    for (int i = 0; i < this.permanentSubwindows.length; i++) {
      if (permanentSubwindows[i] != null) {
        permanentSubwindows[i].refresh();
      }
    }
  }

  /** Sets the mode value on mode selection component of the toolbar. */
  public void setToolbarModeSelectionPanelValue(InteractiveState interactiveState) {
    this.toolbarPanel.setModeSelectionPanelValue(interactiveState);
  }

  private void setToolbarUnitSelectionPanelValue(Unit unit) {
    this.toolbarPanel.setUnitSelectionPanelValue(unit);
  }

  /** Repaints this board frame and all the subwindows of the board. */
  public void repaintAll() {
    this.repaint();
    for (int i = 0; i < permanentSubwindows.length; i++) {
      if (permanentSubwindows[i] != null) {
        permanentSubwindows[i].repaint();
      }
    }
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
    return permanentSubwindows;
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
    String sessionId = SessionManager.getInstance().getGuiSession().id.toString();
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
      for (BoardSubWindow currSubwindow : temporarySubwindows) {
        if (currSubwindow != null) {
          currSubwindow.parentIconified();
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
      for (BoardSubWindow currSubwindow : temporarySubwindows) {
        if (currSubwindow != null) {
          currSubwindow.parentDeiconified();
        }
      }
    }
  }
}
