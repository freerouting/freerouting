package app.freerouting.gui.board;

import app.freerouting.Freerouting;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.gui.rendering.TutorialBoardPalette;
import app.freerouting.gui.windows.board.WindowBase;
import app.freerouting.gui.windows.board.WindowMessage;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.session.InteractiveActionThread;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.io.specctra.SesImportSummary;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.jobs.ThreadActionListener;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.rules.NetClasses;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.GuiSettingsSource;
import app.freerouting.util.TextManager;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/** Manages GUI initialization and board frame creation for the Freerouting application. */
public class GuiManager {

  /**
   * Initializes the Freerouting GUI session and opens its initial board.
   *
   * @param globalSettings application-wide settings used to configure the session
   * @return {@code true} when GUI initialization completes successfully
   */
  // CHECKSTYLE.SUPPRESS: AbbreviationAsWordInName for +1 lines
  public static boolean initializeGUI(GlobalSettings globalSettings) {
    if (!EventQueue.isDispatchThread()) {
      final boolean[] result = new boolean[1];
      try {
        EventQueue.invokeAndWait(() -> result[0] = initializeGUI(globalSettings));
      } catch (Exception e) {
        FRLogger.error("Failed to initialize GUI on EDT", e);
        return false;
      }
      return result[0];
    }

    // Start a new Freerouting session
    var guiSession =
        SessionManager.getInstance()
            .createSession(
                UUID.fromString(globalSettings.userProfileSettings.userId),
                "Freerouting/" + globalSettings.version);
    SessionManager.getInstance().setPrimarySession(guiSession.getId());
    SessionManager.getInstance().setMonitoredSessionId(guiSession.getId());

    // Set default font for buttons and labels
    FontUIResource menuFont = (FontUIResource) UIManager.get("Menu.font");
    FontUIResource defaultFont = (FontUIResource) UIManager.get("Button.font");
    Font newFont = new Font(defaultFont.getName(), Font.PLAIN, menuFont.getSize());
    UIManager.put("Component.font", newFont);
    UIManager.put("Button.font", newFont);
    UIManager.put("Label.font", newFont);
    UIManager.put("ToggleButton.font", newFont);
    UIManager.put("FormattedTextField.font", newFont);
    UIManager.put("TextField.font", newFont);
    UIManager.put("ComboBox.font", newFont);
    UIManager.put("CheckBox.font", newFont);
    UIManager.put("RadioButton.font", newFont);
    UIManager.put("Table.font", newFont);
    UIManager.put("TableHeader.font", newFont);
    UIManager.put("List.font", newFont);
    UIManager.put("Menu.font", newFont);
    UIManager.put("MenuItem.font", newFont);

    // get localization resources
    TextManager tm = new TextManager(GuiManager.class, globalSettings.currentLocale);

    RoutingJob routingJob = null;

    // check if we can load a file instantly at startup
    if (globalSettings.initialInputFile != null) {
      // let's create a job in our session and queue it
      FRLogger.info("Opening '" + globalSettings.initialInputFile + "'...");
      routingJob = new RoutingJob(guiSession.getId());

      try {
        routingJob.setInput(globalSettings.initialInputFile);
      } catch (Exception e) {
        FRLogger.error("Couldn't read the file", e);
      }

      if (routingJob.input == null) {
        FRLogger.warn(tm.getText("file_not_found", globalSettings.initialInputFile));
        return false;
      }

      if (routingJob.input.format == FileFormat.UNKNOWN) {
        FRLogger.warn(tm.getText("file_not_found", globalSettings.initialInputFile));
        return false;
      }
      var settingsMerger = globalSettings.settingsMergerProtype.clone();
      settingsMerger.addOrReplaceSources(
          new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename()),
          new GuiSettingsSource(routingJob.routerSettings));
      routingJob.routerSettings = settingsMerger.merge();
      guiSession.addJob(routingJob);

      String message = tm.getText("loading_design_with_file", globalSettings.initialInputFile);
      WindowMessage welcomeWindow = WindowMessage.show(message);
      final BoardFrame newFrame =
          createBoardFrame(routingJob, null, globalSettings, settingsMerger);
      welcomeWindow.dispose();
      if (newFrame == null) {
        FRLogger.warn("Couldn't create window frame");
        System.exit(1);
        return false;
      }
      var bs =
          new BoardStatistics(newFrame.boardPanel.boardHandling.getRoutingBoard(), null, false);
      newFrame.boardPanel.boardHandling.screenMessages.setBoardScore(
          bs.getNormalizedScore(routingJob.routerSettings.scoring),
          bs.connections.incompleteCount,
          bs.clearanceViolations.totalCount);
      newFrame.boardPanel.boardHandling.setNumThreads(routingJob.routerSettings.maxThreads);
      newFrame.boardPanel.boardHandling.setBoardUpdateStrategy(
          routingJob.routerSettings.optimizer.boardUpdateStrategy);
      newFrame.boardPanel.boardHandling.setHybridRatio(
          routingJob.routerSettings.optimizer.hybridRatio);
      newFrame.boardPanel.boardHandling.setItemSelectionStrategy(
          routingJob.routerSettings.optimizer.itemSelectionStrategy);

      if (globalSettings.initialOutputFile != null) {
        // if the design_output_filename file exists we need to delete it before setting
        // it
        var desiredOutputFile = new File(globalSettings.initialOutputFile);
        if ((desiredOutputFile != null) && desiredOutputFile.exists()) {
          if (!desiredOutputFile.delete()) {
            FRLogger.warn("Couldn't delete the file '" + globalSettings.initialOutputFile + "'");
          }
        }

        routingJob.tryToSetOutputFile(new File(globalSettings.initialOutputFile));

        // we need to set up a listener to save the design file when the autorouter is
        // running
        newFrame.boardPanel.boardHandling.autorouterListener =
            new ThreadActionListener() {
              @Override
              public void autorouterStarted() {}

              @Override
              public void autorouterAborted() {
                exportBoardToFile(globalSettings.initialOutputFile);
              }

              @Override
              public void autorouterFinished() {
                exportBoardToFile(globalSettings.initialOutputFile);
              }

              private void exportBoardToFile(String filename) {
                if (filename == null) {
                  FRLogger.warn("Couldn't export board, filename not specified");
                  return;
                }

                var filenameLowerCase = filename.toLowerCase();

                if (!(filenameLowerCase.endsWith(".dsn")
                    || filenameLowerCase.endsWith(".ses")
                    || filenameLowerCase.endsWith(".scr"))) {
                  FRLogger.warn(
                      "Couldn't export board to '" + filename + "', unsupported extension");
                  return;
                }

                FRLogger.info("Saving '" + filename + "'...");
                try {
                  String filenameOnly = new File(filename).getName();
                  String designName = filenameOnly.substring(0, filenameOnly.length() - 4);
                  String extension = filenameOnly.substring(filenameOnly.length() - 4);

                  try (OutputStream outputStream = new FileOutputStream(filename)) {
                    switch (extension) {
                      case ".dsn" ->
                          newFrame.boardPanel.boardHandling.saveAsSpecctraDesignDsn(
                              outputStream, designName, false);
                      case ".ses" ->
                          newFrame.boardPanel.boardHandling.saveAsSpecctraSessionSes(
                              outputStream, designName);
                      case ".scr" -> {
                        ByteArrayOutputStream sessionOutputStream = new ByteArrayOutputStream();
                        newFrame.boardPanel.boardHandling.saveAsSpecctraSessionSes(
                            sessionOutputStream, filename);
                        InputStream inputStream =
                            new ByteArrayInputStream(sessionOutputStream.toByteArray());
                        newFrame.boardPanel.boardHandling.saveSpecctraSessionSesAsEagleScriptScr(
                            inputStream, outputStream);
                      }
                      default -> {
                        // The output extension was validated before opening the stream.
                      }
                    }
                  }

                  if (globalSettings.guiSettings.exitWhenFinished) {
                    System.exit(0);
                  }
                } catch (Exception e) {
                  FRLogger.error("Couldn't export board to file", e);
                }
              }
            };
      }

      // start the auto-router automatically if both input and output files were
      // passed as a parameter
      if ((globalSettings.initialInputFile != null) && (globalSettings.initialOutputFile != null)) {
        // Add a model dialog with timeout to confirm the autorouter start with the
        // default settings
        final String startNowText = tm.getText("auto_start_routing_startnow_button");
        JButton startNowButton =
            new JButton(
                startNowText + " (" + globalSettings.guiSettings.dialogConfirmationTimeout + ")");

        final String cancelText = tm.getText("auto_start_routing_cancel_button");
        Object[] options = {startNowButton, cancelText};

        final String autostartMsg = tm.getText("auto_start_routing_message");
        JOptionPane autoStartRoutingDialog =
            new JOptionPane(
                autostartMsg,
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                options,
                options[0]);

        startNowButton.addActionListener(_ -> autoStartRoutingDialog.setValue(options[0]));
        startNowButton.addActionListener(
            _ ->
                FRAnalytics.buttonClicked(
                    "auto_start_routing_dialog_start", startNowButton.getText()));

        final String autostartTitle = tm.getText("auto_start_routing_title");

        if (globalSettings.guiSettings.dialogConfirmationTimeout > 0) {
          // Add a timer to the dialog
          JDialog autostartDialog = autoStartRoutingDialog.createDialog(autostartTitle);

          // Update startNowButton text every second
          Timer autostartTimer =
              new Timer(
                  1000,
                  new ActionListener() {
                    private int secondsLeft = globalSettings.guiSettings.dialogConfirmationTimeout;

                    @Override
                    public void actionPerformed(ActionEvent e) {
                      if (--secondsLeft > 0) {
                        startNowButton.setText(startNowText + " (" + secondsLeft + ")");
                      } else {
                        autoStartRoutingDialog.setValue(options[0]);
                        FRAnalytics.buttonClicked(
                            "auto_start_routing_dialog_start_with_timeout",
                            startNowButton.getText());
                      }
                    }
                  });

          autostartTimer.start();
          autostartDialog.setVisible(true); // blocks execution

          autostartDialog.dispose();
          autostartTimer.stop();
        }

        Object choice = autoStartRoutingDialog.getValue();
        // Start the auto-router if the user didn't cancel the dialog
        if ((globalSettings.guiSettings.dialogConfirmationTimeout == 0) || (choice == options[0])) {
          // Start the auto-router
          // Note: routingJob.routerSettings already has CLI settings applied in line
          // 87-91
          InteractiveActionThread thread =
              newFrame.boardPanel.boardHandling.startAutorouterAndRouteOptimizer(routingJob);

          if (newFrame.boardPanel.boardHandling.autorouterListener != null) {
            // Add the auto-router listener to save the design file when the autorouter is
            // running
            thread.addListener(newFrame.boardPanel.boardHandling.autorouterListener);
          }

          globalSettings.guiSettings.exitWhenFinished = true;
        }

        if (choice == options[1]) {
          globalSettings.guiSettings.exitWhenFinished = false;
          FRAnalytics.buttonClicked("auto_start_routing_dialog_cancel", "Cancel");
        }
      }

      newFrame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
              System.exit(0);
            }
          });
    } else {
      // we didn't have any input file passed as a parameter
      // we load a blank board
      var settingsMerger = globalSettings.settingsMergerProtype.clone();
      settingsMerger.addOrReplaceSources(new GuiSettingsSource(null));

      final BoardFrame newFrame = createBoardFrame(null, null, globalSettings, settingsMerger);
      if (newFrame == null) {
        FRLogger.warn("Couldn't create window frame");
        System.exit(1);
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a new board frame containing the data of the input design file. Returns null, if an
   * error occurred.
   */
  private static BoardFrame createBoardFrame(
      RoutingJob routingJob,
      JTextField messageField,
      GlobalSettings globalSettings,
      SettingsMerger settingsMerger) {
    TextManager tm = new TextManager(GuiManager.class, globalSettings.currentLocale);

    InputStream inputStream = null;
    if ((routingJob == null) || (routingJob.input.getFile() == null)) {
      routingJob = new RoutingJob(SessionManager.getInstance().getPrimarySession().id);

      routingJob.setDummyInputFile("tutorial_board.dsn");
      // Load an empty template file from the resources
      ClassLoader classLoader = WindowBase.class.getClassLoader();
      inputStream = classLoader.getResourceAsStream("tutorial_board.dsn");
    } else {
      inputStream = routingJob.input.getData();
      if (inputStream == null) {
        if (messageField != null) {
          messageField.setText(
              tm.getText(
                  "error_design_file_read_failed_with_file", routingJob.input.getFilename()));
        }
        return null;
      }
    }

    BoardFrame newFrame = new BoardFrame(routingJob, globalSettings, settingsMerger);

    boolean readOk = newFrame.load(inputStream, routingJob.input.format, messageField, routingJob);
    if (!readOk) {
      return null;
    }

    // Load session file if specified (after design is loaded, before RULES)
    if (globalSettings.designSessionFilename != null
        && (routingJob.input.format.equals(FileFormat.DSN)
            || routingJob.input.format.equals(FileFormat.KICAD_DESIGN_JSON))) {
      try {
        File sessionFile = new File(globalSettings.designSessionFilename);
        if (sessionFile.exists()) {
          if (globalSettings.designSessionFilename.toLowerCase().endsWith(".json")) {
            FRLogger.info(
                "Loading KiCad JSON session file: " + globalSettings.designSessionFilename);
            try (java.io.FileReader jsonReader = new java.io.FileReader(sessionFile)) {
              app.freerouting.io.kicad.KiCadJsonReader.importSession(
                  jsonReader, newFrame.boardPanel.boardHandling.getRoutingBoard());
              FRLogger.info("KiCad JSON session file loaded successfully");
            }
          } else {
            FRLogger.info("Loading SES file: " + globalSettings.designSessionFilename);
            FileInputStream sesStream = new FileInputStream(sessionFile);
            SesImportSummary summary =
                SesReader.read(sesStream, newFrame.boardPanel.boardHandling.getRoutingBoard());
            FRLogger.info(
                "SES file loaded: "
                    + summary.wiresImported()
                    + " wires, "
                    + summary.viasImported()
                    + " vias imported"
                    + (summary.errorsEncountered() > 0
                        ? " (" + summary.errorsEncountered() + " errors)"
                        : ""));
          }
          newFrame.refreshWindows(); // Refresh UI to show loaded routes
        } else {
          FRLogger.warn("Session file not found: " + globalSettings.designSessionFilename);
        }
      } catch (Exception e) {
        FRLogger.error("Failed to load session file", e);
      }
    }

    // Change the palette if we loaded the tutorial DSN file
    if (TutorialBoardPalette.isTutorialBoard(routingJob.input.getFilename())) {
      TutorialBoardPalette.apply(newFrame.boardPanel.boardHandling.graphicsContext);
      newFrame.boardPanel.setBackground(TutorialBoardPalette.backgroundColor());
    }

    FRAnalytics.buttonClicked("fileio_loaddsn", routingJob.getInputFileDetails());

    if (!globalSettings.featureFlags.inspectionMode) {
      newFrame.boardPanel.boardHandling.setRouteMenuState();
    }

    if (routingJob.input.format.equals(FileFormat.DSN)) {
      // Read the file with the saved rules, if it exists.
      String designName = routingJob.name;

      String rulesFileName;
      String parentFolderName;
      String confirmImportRulesMessage;
      if (globalSettings.initialRulesFile == null) {
        rulesFileName = designName + ".rules";
        parentFolderName = routingJob.input.getDirectoryPath();
        confirmImportRulesMessage = tm.getText("confirm_import_rules");
      } else {
        rulesFileName = globalSettings.initialRulesFile;
        parentFolderName = null;
        confirmImportRulesMessage = null;
      }

      File rulesFile = new File(parentFolderName, rulesFileName);
      if (rulesFile.exists()) {
        // load the .rules file
        readRulesFile(
            designName,
            parentFolderName,
            rulesFileName,
            newFrame.boardPanel.boardHandling,
            confirmImportRulesMessage);
      }

      // ignore net classes if they were defined by a command line argument
      if (routingJob.routerSettings.ignoreNetClasses != null) {
        for (String netClassName : routingJob.routerSettings.ignoreNetClasses) {
          NetClasses netClasses =
              newFrame.boardPanel.boardHandling.getRoutingBoard().rules.netClasses;

          for (int i = 0; i < netClasses.count(); i++) {
            if (netClasses.get(i).getName().equalsIgnoreCase(netClassName)) {
              netClasses.get(i).isIgnoredByAutorouter = true;
            }
          }
        }
      }

      newFrame.refreshWindows();
    }
    return newFrame;
  }

  /** Saves the current global GUI settings to the Freerouting configuration file. */
  public static void saveSettings() throws IOException {
    GlobalSettings.saveAsJson(Freerouting.globalSettings);
  }

  /**
   * Reads a rules file and applies it when the user confirms the import.
   *
   * @param designName the design name associated with the rules
   * @param parentName the directory containing the rules file
   * @param rulesFileName the rules filename
   * @param boardHandling the GUI board manager receiving the rules
   * @param confirmMessage the confirmation prompt, when applicable
   * @return {@code true} when the rules were read successfully
   */
  private static boolean readRulesFile(
      String designName,
      String parentName,
      String rulesFileName,
      GuiBoardManager boardHandling,
      String confirmMessage) {

    boolean dsnFileGeneratedByHost =
        boardHandling.getRoutingBoard().communication.specctraParserInfo.dsnFileGeneratedByHost;

    try {
      File rulesFile = new File(parentName, rulesFileName);
      FRLogger.info("Opening '" + rulesFileName + "'...");
      try (InputStream inputStream = new FileInputStream(rulesFile)) {
        if (dsnFileGeneratedByHost && WindowMessage.confirm(confirmMessage)) {
          return RulesReader.read(
              inputStream,
              designName,
              boardHandling.getRoutingBoard(),
              boardHandling.getCurrentRoutingJob().routerSettings);
        }
      }
    } catch (IOException e) {
      FRLogger.error("Error reading rules file '" + rulesFileName + "'.", e);
    }
    return false;
  }
}
