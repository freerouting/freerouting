package app.freerouting.gui;

import app.freerouting.Freerouting;
import app.freerouting.boardgraphics.TutorialBoardPalette;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.io.specctra.SesImportSummary;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.ThreadActionListener;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.NetClasses;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.GuiSettings;
import app.freerouting.util.TextManager;
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

  public static boolean initializeGUI(GlobalSettings globalSettings) {
    // Start a new Freerouting session
    var guiSession =
        SessionManager.getInstance()
            .createSession(
                UUID.fromString(globalSettings.userProfileSettings.userId),
                "Freerouting/" + globalSettings.version);
    SessionManager.getInstance().setGuiSession(guiSession.getId());
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
          new GuiSettings(routingJob.routerSettings));
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
        final String START_NOW_TEXT = tm.getText("auto_start_routing_startnow_button");
        JButton startNowButton =
            new JButton(
                START_NOW_TEXT + " (" + globalSettings.guiSettings.dialogConfirmationTimeout + ")");

        final String CANCEL_TEXT = tm.getText("auto_start_routing_cancel_button");
        Object[] options = {startNowButton, CANCEL_TEXT};

        final String AUTOSTART_MSG = tm.getText("auto_start_routing_message");
        JOptionPane autoStartRoutingDialog =
            new JOptionPane(
                AUTOSTART_MSG,
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

        final String AUTOSTART_TITLE = tm.getText("auto_start_routing_title");

        if (globalSettings.guiSettings.dialogConfirmationTimeout > 0) {
          // Add a timer to the dialog
          JDialog autostartDialog = autoStartRoutingDialog.createDialog(AUTOSTART_TITLE);

          // Update startNowButton text every second
          Timer autostartTimer =
              new Timer(
                  1000,
                  new ActionListener() {
                    private int secondsLeft = globalSettings.guiSettings.dialogConfirmationTimeout;

                    @Override
                    public void actionPerformed(ActionEvent e) {
                      if (--secondsLeft > 0) {
                        startNowButton.setText(START_NOW_TEXT + " (" + secondsLeft + ")");
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
      settingsMerger.addOrReplaceSources(new GuiSettings(null));

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
      JTextField p_message_field,
      GlobalSettings globalSettings,
      SettingsMerger settingsMerger) {
    TextManager tm = new TextManager(GuiManager.class, globalSettings.currentLocale);

    InputStream inputStream = null;
    if ((routingJob == null) || (routingJob.input.getFile() == null)) {
      routingJob = new RoutingJob(SessionManager.getInstance().getGuiSession().id);

      routingJob.setDummyInputFile("tutorial_board.dsn");
      // Load an empty template file from the resources
      ClassLoader classLoader = WindowBase.class.getClassLoader();
      inputStream = classLoader.getResourceAsStream("tutorial_board.dsn");
    } else {
      inputStream = routingJob.input.getData();
      if (inputStream == null) {
        if (p_message_field != null) {
          p_message_field.setText(
              tm.getText(
                  "error_design_file_read_failed_with_file", routingJob.input.getFilename()));
        }
        return null;
      }
    }

    BoardFrame newFrame = new BoardFrame(routingJob, globalSettings, settingsMerger);

    boolean readOk =
        newFrame.load(inputStream, routingJob.input.format, p_message_field, routingJob);
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

  public static void saveSettings() throws IOException {
    GlobalSettings.saveAsJson(Freerouting.globalSettings);
  }

  private static boolean readRulesFile(
      String p_design_name,
      String p_parent_name,
      String rulesFileName,
      GuiBoardManager p_board_handling,
      String p_confirm_message) {

    boolean dsnFileGeneratedByHost =
        p_board_handling.getRoutingBoard()
            .communication
            .specctraParserInfo
            .dsnFileGeneratedByHost;

    try {
      File rulesFile = new File(p_parent_name, rulesFileName);
      FRLogger.info("Opening '" + rulesFileName + "'...");
      try (InputStream inputStream = new FileInputStream(rulesFile)) {
        if (dsnFileGeneratedByHost && WindowMessage.confirm(p_confirm_message)) {
          return RulesReader.read(inputStream, p_design_name, p_board_handling.getRoutingBoard());
        }
      }
    } catch (IOException e) {
      FRLogger.error("Error reading rules file '" + rulesFileName + "'.", e);
    }
    return false;
  }
}
