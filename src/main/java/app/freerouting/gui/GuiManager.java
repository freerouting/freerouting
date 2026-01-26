package app.freerouting.gui;

import app.freerouting.Freerouting;
import app.freerouting.boardgraphics.ColorIntensityTable;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.ThreadActionListener;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.NetClasses;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.GuiSettings;
import java.awt.Color;
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
import java.util.Objects;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/**
 * Manages GUI initialization and board frame creation for the Freerouting
 * application.
 */
public class GuiManager {

    public static boolean InitializeGUI(GlobalSettings globalSettings) {
        // Start a new Freerouting session
        var guiSession = SessionManager
                .getInstance()
                .createSession(UUID.fromString(globalSettings.userProfileSettings.userId),
                        "Freerouting/" + globalSettings.version);
        SessionManager
                .getInstance()
                .setGuiSession(guiSession.id);

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
            routingJob = new RoutingJob(guiSession.id);

            // Apply CLI settings from GlobalSettings to the RoutingJob
            // This ensures that command-line arguments are used by the autorouter
            if (globalSettings.routerSettings != null) {
                routingJob.routerSettings.applyNewValuesFrom(globalSettings.routerSettings);
            } else {
                FRLogger.warn("[CLI Settings] globalSettings.routerSettings is null, CLI settings not applied!");
            }

            try {
                routingJob.setInput(globalSettings.initialInputFile);
            } catch (Exception e) {
                FRLogger.error("Couldn't read the file", e);
            }

            if (routingJob.input.format == FileFormat.UNKNOWN) {
                FRLogger.warn(tm.getText("message_6") + " " + globalSettings.initialInputFile + " "
                                + tm.getText("message_7"));
                return false;
            }
            var settingsMerger = globalSettings.settingsMergerProtype.clone();
            settingsMerger.addOrReplaceSources(
                new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename()),
                new GuiSettings(routingJob.routerSettings));
            guiSession.addJob(routingJob);

            String message = tm.getText("loading_design") + " " + globalSettings.initialInputFile;
            WindowMessage welcome_window = WindowMessage.show(message);
            final BoardFrame new_frame = create_board_frame(routingJob, null, globalSettings, settingsMerger);
            welcome_window.dispose();
            if (new_frame == null) {
                FRLogger.warn("Couldn't create window frame");
                System.exit(1);
                return false;
            }
            var bs = new BoardStatistics(new_frame.board_panel.board_handling.get_routing_board());
            new_frame.board_panel.board_handling.screen_messages.set_board_score(
                    bs.getNormalizedScore(routingJob.routerSettings.scoring), bs.connections.incompleteCount,
                    bs.clearanceViolations.totalCount);
            new_frame.board_panel.board_handling.set_num_threads(globalSettings.routerSettings.maxThreads);
            new_frame.board_panel.board_handling
                    .set_board_update_strategy(globalSettings.routerSettings.optimizer.boardUpdateStrategy);
            new_frame.board_panel.board_handling.set_hybrid_ratio(globalSettings.routerSettings.optimizer.hybridRatio);
            new_frame.board_panel.board_handling
                    .set_item_selection_strategy(globalSettings.routerSettings.optimizer.itemSelectionStrategy);

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
                new_frame.board_panel.board_handling.autorouter_listener = new ThreadActionListener() {
                    @Override
                    public void autorouterStarted() {
                    }

                    @Override
                    public void autorouterAborted() {
                        ExportBoardToFile(globalSettings.initialOutputFile);
                    }

                    @Override
                    public void autorouterFinished() {
                        ExportBoardToFile(globalSettings.initialOutputFile);
                    }

                    private void ExportBoardToFile(String filename) {
                        if (filename == null) {
                            FRLogger.warn("Couldn't export board, filename not specified");
                            return;
                        }

                        var filenameLowerCase = filename.toLowerCase();

                        if (!(filenameLowerCase.endsWith(".dsn") || filenameLowerCase.endsWith(".ses")
                                || filenameLowerCase.endsWith(".scr"))) {
                            FRLogger.warn("Couldn't export board to '" + filename + "', unsupported extension");
                            return;
                        }

                        FRLogger.info("Saving '" + filename + "'...");
                        try {
                            String filename_only = new File(filename).getName();
                            String design_name = filename_only.substring(0, filename_only.length() - 4);
                            String extension = filename_only.substring(filename_only.length() - 4);

                            OutputStream output_stream = new FileOutputStream(filename);

                            switch (extension) {
                                case ".dsn" ->
                                    new_frame.board_panel.board_handling.saveAsSpecctraDesignDsn(output_stream,
                                            design_name, false);
                                case ".ses" ->
                                    new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(output_stream,
                                            design_name);
                                case ".scr" -> {
                                    ByteArrayOutputStream session_output_stream = new ByteArrayOutputStream();
                                    new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(session_output_stream,
                                            filename);
                                    InputStream input_stream = new ByteArrayInputStream(
                                            session_output_stream.toByteArray());
                                    new_frame.board_panel.board_handling.saveSpecctraSessionSesAsEagleScriptScr(
                                            input_stream,
                                            output_stream);
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
                JButton startNowButton = new JButton(
                        START_NOW_TEXT + " (" + globalSettings.guiSettings.dialogConfirmationTimeout + ")");

                final String CANCEL_TEXT = tm.getText("auto_start_routing_cancel_button");
                Object[] options = {
                        startNowButton,
                        CANCEL_TEXT
                };

                final String AUTOSTART_MSG = tm.getText("auto_start_routing_message");
                JOptionPane auto_start_routing_dialog = new JOptionPane(AUTOSTART_MSG, JOptionPane.WARNING_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);

                startNowButton.addActionListener(_ -> auto_start_routing_dialog.setValue(options[0]));
                startNowButton.addActionListener(
                        _ -> FRAnalytics.buttonClicked("auto_start_routing_dialog_start", startNowButton.getText()));

                final String AUTOSTART_TITLE = tm.getText("auto_start_routing_title");

                if (globalSettings.guiSettings.dialogConfirmationTimeout > 0) {
                    // Add a timer to the dialog
                    JDialog autostartDialog = auto_start_routing_dialog.createDialog(AUTOSTART_TITLE);

                    // Update startNowButton text every second
                    Timer autostartTimer = new Timer(1000, new ActionListener() {
                        private int secondsLeft = globalSettings.guiSettings.dialogConfirmationTimeout;

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (--secondsLeft > 0) {
                                startNowButton.setText(START_NOW_TEXT + " (" + secondsLeft + ")");
                            } else {
                                auto_start_routing_dialog.setValue(options[0]);
                                FRAnalytics.buttonClicked("auto_start_routing_dialog_start_with_timeout",
                                        startNowButton.getText());
                            }
                        }
                    });

                    autostartTimer.start();
                    autostartDialog.setVisible(true); // blocks execution

                    autostartDialog.dispose();
                    autostartTimer.stop();
                }

                Object choice = auto_start_routing_dialog.getValue();
                // Start the auto-router if the user didn't cancel the dialog
                if ((globalSettings.guiSettings.dialogConfirmationTimeout == 0) || (choice == options[0])) {
                    // Start the auto-router
                    // Note: routingJob.routerSettings already has CLI settings applied in line
                    // 87-91
                    InteractiveActionThread thread = new_frame.board_panel.board_handling
                            .start_autorouter_and_route_optimizer(routingJob);

                    if (new_frame.board_panel.board_handling.autorouter_listener != null) {
                        // Add the auto-router listener to save the design file when the autorouter is
                        // running
                        thread.addListener(new_frame.board_panel.board_handling.autorouter_listener);
                    }

                    globalSettings.guiSettings.exitWhenFinished = true;
                }

                if (choice == options[1]) {
                    globalSettings.guiSettings.exitWhenFinished = false;
                    FRAnalytics.buttonClicked("auto_start_routing_dialog_cancel", "Cancel");
                }
            }

            new_frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent evt) {
                    System.exit(0);
                }
            });
        } else {
            // we didn't have any input file passed as a parameter
            // we load a blank board
            var settingsMerger = globalSettings.settingsMergerProtype.clone();
            settingsMerger.addOrReplaceSources(
                new GuiSettings(routingJob.routerSettings));

            final BoardFrame new_frame = create_board_frame(null, null, globalSettings, settingsMerger);
            if (new_frame == null) {
                FRLogger.warn("Couldn't create window frame");
                System.exit(1);
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new board frame containing the data of the input design file.
     * Returns null, if an error occurred.
     */
    private static BoardFrame create_board_frame(RoutingJob routingJob, JTextField p_message_field,
            GlobalSettings globalSettings, SettingsMerger settingsMerger) {
        TextManager tm = new TextManager(GuiManager.class, globalSettings.currentLocale);

        InputStream input_stream = null;
        if ((routingJob == null) || (routingJob.input.getFile() == null)) {
            routingJob = new RoutingJob(SessionManager
                    .getInstance()
                    .getGuiSession().id);

            // Apply CLI settings from GlobalSettings to the RoutingJob
            routingJob.routerSettings.applyNewValuesFrom(globalSettings.routerSettings);

            routingJob.setDummyInputFile("tutorial_board.dsn");
            // Load an empty template file from the resources
            ClassLoader classLoader = WindowBase.class.getClassLoader();
            input_stream = classLoader.getResourceAsStream("tutorial_board.dsn");
        } else {
            input_stream = routingJob.input.getData();
            if (input_stream == null) {
                if (p_message_field != null) {
                    p_message_field.setText(tm.getText("message_8") + " " + routingJob.input.getFilename());
                }
                return null;
            }
        }

        BoardFrame new_frame = new BoardFrame(routingJob, globalSettings, settingsMerger);

        boolean read_ok = new_frame.load(input_stream, routingJob.input.format.equals(FileFormat.DSN), p_message_field,
                routingJob);
        if (!read_ok) {
            return null;
        }

        // Load SES file if specified (after DSN is loaded, before RULES)
        if (globalSettings.design_session_filename != null && routingJob.input.format.equals(FileFormat.DSN)) {
            try {
                File sesFile = new File(globalSettings.design_session_filename);
                if (sesFile.exists()) {
                    FRLogger.info("Loading SES file: " + globalSettings.design_session_filename);
                    FileInputStream sesStream = new FileInputStream(sesFile);
                    app.freerouting.designforms.specctra.SesFileReader.read(sesStream,
                            new_frame.board_panel.board_handling.get_routing_board());
                    sesStream.close();
                    new_frame.refresh_windows(); // Refresh UI to show loaded routes
                } else {
                    FRLogger.warn("SES file not found: " + globalSettings.design_session_filename);
                }
            } catch (Exception e) {
                FRLogger.error("Failed to load SES file", e);
            }
        }

        // Change the palette if we loaded the tutorial DSN file
        if (Objects.equals(routingJob.input.getFilename(), "tutorial_board.dsn")) {
            var graphicsContext = new_frame.board_panel.board_handling.graphics_context;

            graphicsContext.color_intensity_table.set_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(),
                    0.9);
            graphicsContext.item_color_table.set_conduction_colors(new Color[] {
                    new Color(232, 204, 135),
                    new Color(255, 255, 255)
            });
            graphicsContext.other_color_table.set_background_color(new Color(1, 58, 32));
            graphicsContext.other_color_table.set_outline_color(new Color(255, 255, 255));

            new_frame.board_panel.setBackground(graphicsContext.other_color_table.get_background_color());
        }

        FRAnalytics.buttonClicked("fileio_loaddsn", routingJob.getInputFileDetails());

        if (!globalSettings.featureFlags.inspectionMode) {
            new_frame.board_panel.board_handling.set_route_menu_state();
        }

        if (routingJob.input.format.equals(FileFormat.DSN)) {
            // Read the file with the saved rules, if it exists.
            String design_name = routingJob.name;

            String rules_file_name;
            String parent_folder_name;
            String confirm_import_rules_message;
            if (globalSettings.initialRulesFile == null) {
                rules_file_name = design_name + ".rules";
                parent_folder_name = routingJob.input.getDirectoryPath();
                confirm_import_rules_message = tm.getText("confirm_import_rules");
            } else {
                rules_file_name = globalSettings.initialRulesFile;
                parent_folder_name = null;
                confirm_import_rules_message = null;
            }

            File rules_file = new File(parent_folder_name, rules_file_name);
            if (rules_file.exists()) {
                // load the .rules file
                RoutingJob.read_rules_file(design_name, parent_folder_name, rules_file_name,
                        new_frame.board_panel.board_handling, confirm_import_rules_message);
            }

            // ignore net classes if they were defined by a command line argument
            if (routingJob.routerSettings.ignoreNetClasses != null) {
                for (String net_class_name : routingJob.routerSettings.ignoreNetClasses) {
                    NetClasses netClasses = new_frame.board_panel.board_handling.get_routing_board().rules.net_classes;

                    for (int i = 0; i < netClasses.count(); i++) {
                        if (netClasses
                            .get(i)
                            .get_name()
                            .equalsIgnoreCase(net_class_name)) {
                            netClasses.get(i).is_ignored_by_autorouter = true;
                        }
                    }
                }
            }

            new_frame.refresh_windows();
        }
        return new_frame;
    }

    public static void saveSettings() throws IOException {
        GlobalSettings.saveAsJson(Freerouting.globalSettings);
    }
}