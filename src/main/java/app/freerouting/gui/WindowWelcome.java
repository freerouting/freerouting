package app.freerouting.gui;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;


/**
 * The first GUI window that is shown for creating frames with new or existing board designs. DEPRECATED: This class is deprecated and will be removed in the future. It was a welcome window in the
 * past, but it got replaced by the BoardFrame class.
 */
public class WindowWelcome extends WindowBase {

  private final JButton open_board_button;
  private final JButton restore_defaults_button;
  private final JTextField message_field;
  private final JPanel main_panel;
  /**
   * The list of open board frames
   */
  private final Collection<BoardFrame> board_frames = new LinkedList<>();
  private final Locale locale;
  private final int max_passes;
  private final BoardUpdateStrategy board_update_strategy;
  // Issue: adding a new field into AutorouteSettings caused exception when loading
  // an existing design: "Couldn't read design file", "InvalidClassException", incompatible with
  // serialized data
  // so choose to pass this parameter through BoardHandling
  private final String hybrid_ratio;
  private final ItemSelectionStrategy item_selection_strategy;
  private final int num_threads;
  private final GlobalSettings globalSettings;
  private String design_dir_name;

  /**
   * Creates new form MainApplication It takes the directory of the board designs as optional argument.
   *
   * @param globalSettings
   */
  public WindowWelcome(GlobalSettings globalSettings) {
    super(600, 300);

    this.globalSettings = globalSettings;
    this.design_dir_name = globalSettings.getDesignDir();
    this.max_passes = globalSettings.getMaxPasses();
    this.num_threads = globalSettings.getNumThreads();
    this.board_update_strategy = globalSettings.getBoardUpdateStrategy();
    this.hybrid_ratio = globalSettings.getHybridRatio();
    this.item_selection_strategy = globalSettings.getItemSelectionStrategy();
    this.locale = globalSettings.getCurrentLocale();

    this.setLanguage(locale);

    main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);

    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(10, 10, 10, 10);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    open_board_button = new JButton();
    restore_defaults_button = new JButton();

    message_field = new JTextField();
    message_field.setText(tm.getText("command_line_missing_input"));

    setTitle(tm.getText("title") + " " + Freerouting.VERSION_NUMBER_STRING);

    open_board_button.setText(tm.getText("open_own_design"));
    open_board_button.setToolTipText(tm.getText("open_own_design_tooltip"));
    open_board_button.addActionListener(this::open_board_design_action);
    open_board_button.addActionListener(_ -> FRAnalytics.buttonClicked("open_board_button", open_board_button.getText()));

    gridbag.setConstraints(open_board_button, gridbag_constraints);
    main_panel.add(open_board_button, gridbag_constraints);

    int window_width = 620;
    int window_height = 300;

    message_field.setPreferredSize(new Dimension(window_width - 40, 100));
    message_field.setRequestFocusEnabled(false);
    gridbag.setConstraints(message_field, gridbag_constraints);
    main_panel.add(message_field, gridbag_constraints);

    this.addWindowListener(new WindowStateListener());
    pack();
    setSize(window_width, window_height);
    setResizable(false);
  }

  public static boolean InitializeGUI(GlobalSettings globalSettings) {
    // Start a new Freerouting session
    var guiSession = SessionManager
        .getInstance()
        .createSession(UUID.fromString(globalSettings.userProfileSettings.userId), "Freerouting/" + globalSettings.version);
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
    TextManager tm = new TextManager(WindowWelcome.class, globalSettings.currentLocale);

    RoutingJob routingJob = null;

    // check if we can load a file instantly at startup
    if (globalSettings.design_input_filename != null) {
      // let's create a job in our session and queue it
      FRLogger.info("Opening '" + globalSettings.design_input_filename + "'...");
      routingJob = new RoutingJob(guiSession.id);
      try {
        routingJob.setInput(globalSettings.design_input_filename);
      } catch (Exception e) {
        FRLogger.error("Couldn't read the file", e);
      }

      if (routingJob.input.format == FileFormat.UNKNOWN) {
        FRLogger.warn(tm.getText("message_6") + " " + globalSettings.design_input_filename + " " + tm.getText("message_7"));
        return false;
      }
      guiSession.addJob(routingJob);

      String message = tm.getText("loading_design") + " " + globalSettings.design_input_filename;
      WindowMessage welcome_window = WindowMessage.show(message);
      final BoardFrame new_frame = create_board_frame(routingJob, null, globalSettings);
      welcome_window.dispose();
      if (new_frame == null) {
        FRLogger.warn("Couldn't create window frame");
        System.exit(1);
        return false;
      }
      var bs = new BoardStatistics(new_frame.board_panel.board_handling.get_routing_board());
      new_frame.board_panel.board_handling.screen_messages.set_board_score(bs.getNormalizedScore(routingJob.routerSettings.scoring), bs.connections.incompleteCount, bs.clearanceViolations.totalCount);
      new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(
          new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + globalSettings.routerSettings.maxPasses - 1);
      new_frame.board_panel.board_handling.set_num_threads(globalSettings.routerSettings.optimizer.maxThreads);
      new_frame.board_panel.board_handling.set_board_update_strategy(globalSettings.routerSettings.optimizer.boardUpdateStrategy);
      new_frame.board_panel.board_handling.set_hybrid_ratio(globalSettings.routerSettings.optimizer.hybridRatio);
      new_frame.board_panel.board_handling.set_item_selection_strategy(globalSettings.routerSettings.optimizer.itemSelectionStrategy);

      if (globalSettings.design_output_filename != null) {
        // if the design_output_filename file exists we need to delete it before setting it
        var desiredOutputFile = new File(globalSettings.design_output_filename);
        if ((desiredOutputFile != null) && desiredOutputFile.exists()) {
          if (!desiredOutputFile.delete()) {
            FRLogger.warn("Couldn't delete the file '" + globalSettings.design_output_filename + "'");
          }
        }

        routingJob.tryToSetOutputFile(new File(globalSettings.design_output_filename));

        // we need to set up a listener to save the design file when the autorouter is running
        new_frame.board_panel.board_handling.autorouter_listener = new ThreadActionListener() {
          @Override
          public void autorouterStarted() {
          }

          @Override
          public void autorouterAborted() {
            ExportBoardToFile(globalSettings.design_output_filename);
          }

          @Override
          public void autorouterFinished() {
            ExportBoardToFile(globalSettings.design_output_filename);
          }

          private void ExportBoardToFile(String filename) {
            if (filename == null) {
              FRLogger.warn("Couldn't export board, filename not specified");
              return;
            }

            var filenameLowerCase = filename.toLowerCase();

            if (!(filenameLowerCase.endsWith(".dsn") || filenameLowerCase.endsWith(".ses") || filenameLowerCase.endsWith(".scr"))) {
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
                case ".dsn" -> new_frame.board_panel.board_handling.saveAsSpecctraDesignDsn(output_stream, design_name, false);
                case ".ses" -> new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(output_stream, design_name);
                case ".scr" -> {
                  ByteArrayOutputStream session_output_stream = new ByteArrayOutputStream();
                  new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(session_output_stream, filename);
                  InputStream input_stream = new ByteArrayInputStream(session_output_stream.toByteArray());
                  new_frame.board_panel.board_handling.saveSpecctraSessionSesAsEagleScriptScr(input_stream, output_stream);
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

      if (new_frame.is_intermediate_stage_file_available()) {
        LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String load_snapshot_confirmation = tm.getText("load_snapshot_confirmation").formatted(modification_time.format(formatter));

        if (WindowMessage.confirm(load_snapshot_confirmation)) {
          new_frame.load_intermediate_stage_file();
        }
      }

      // start the auto-router automatically if both input and output files were passed as a parameter
      if ((globalSettings.design_input_filename != null) && (globalSettings.design_output_filename != null)) {
        // Add a model dialog with timeout to confirm the autorouter start with the default settings
        final String START_NOW_TEXT = tm.getText("auto_start_routing_startnow_button");
        JButton startNowButton = new JButton(START_NOW_TEXT + " (" + globalSettings.guiSettings.dialogConfirmationTimeout + ")");

        final String CANCEL_TEXT = tm.getText("auto_start_routing_cancel_button");
        Object[] options = {
            startNowButton,
            CANCEL_TEXT
        };

        final String AUTOSTART_MSG = tm.getText("auto_start_routing_message");
        JOptionPane auto_start_routing_dialog = new JOptionPane(AUTOSTART_MSG, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);

        startNowButton.addActionListener(_ -> auto_start_routing_dialog.setValue(options[0]));
        startNowButton.addActionListener(_ -> FRAnalytics.buttonClicked("auto_start_routing_dialog_start", startNowButton.getText()));

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
                FRAnalytics.buttonClicked("auto_start_routing_dialog_start_with_timeout", startNowButton.getText());
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
          routingJob.routerSettings = Freerouting.globalSettings.routerSettings.clone();
          InteractiveActionThread thread = new_frame.board_panel.board_handling.start_autorouter_and_route_optimizer(routingJob);

          if (new_frame.board_panel.board_handling.autorouter_listener != null) {
            // Add the auto-router listener to save the design file when the autorouter is running
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
      if (!globalSettings.featureFlags.fileLoadDialogAtStartup) {
        // we don't use the file load dialog at startup anymore, we load a blank board instead
        final BoardFrame new_frame = create_board_frame(null, null, globalSettings);
        if (new_frame == null) {
          FRLogger.warn("Couldn't create window frame");
          System.exit(1);
          return false;
        }
      } else {
        // we show the file load dialog (deprecated)
        new WindowWelcome(globalSettings).setVisible(true);
      }
    }
    return true;
  }

  /**
   * Creates a new board frame containing the data of the input design file. Returns null, if an error occurred.
   */
  private static BoardFrame create_board_frame(RoutingJob routingJob, JTextField p_message_field, GlobalSettings globalSettings) {
    TextManager tm = new TextManager(WindowWelcome.class, globalSettings.currentLocale);

    InputStream input_stream = null;
    if ((routingJob == null) || (routingJob.input.getFile() == null)) {
      routingJob = new RoutingJob(SessionManager
          .getInstance()
          .getGuiSession().id);
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

    BoardFrame new_frame = new BoardFrame(routingJob, globalSettings);

    boolean read_ok = new_frame.load(input_stream, routingJob.input.format.equals(FileFormat.DSN), p_message_field, routingJob);
    if (!read_ok) {
      return null;
    }

    // Change the palette if we loaded the tutorial DSN file
    if (Objects.equals(routingJob.input.getFilename(), "tutorial_board.dsn")) {
      var graphicsContext = new_frame.board_panel.board_handling.graphics_context;

      graphicsContext.color_intensity_table.set_value(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), 0.9);
      graphicsContext.item_color_table.set_conduction_colors(new Color[]{
          new Color(232, 204, 135),
          new Color(255, 255, 255)
      });
      graphicsContext.other_color_table.set_background_color(new Color(1, 58, 32));
      graphicsContext.other_color_table.set_outline_color(new Color(255, 255, 255));

      new_frame.board_panel.setBackground(graphicsContext.other_color_table.get_background_color());
    }

    FRAnalytics.buttonClicked("fileio_loaddsn", routingJob.getInputFileDetails());

    if (!globalSettings.featureFlags.selectMode) {
      new_frame.board_panel.board_handling.set_route_menu_state();
    }

    if (routingJob.input.format.equals(FileFormat.DSN)) {
      // Read the file with the saved rules, if it exists.
      String design_name = routingJob.name;

      String rules_file_name;
      String parent_folder_name;
      String confirm_import_rules_message;
      if (globalSettings.design_rules_filename == null) {
        rules_file_name = design_name + ".rules";
        parent_folder_name = routingJob.input.getDirectoryPath();
        confirm_import_rules_message = tm.getText("confirm_import_rules");
      } else {
        rules_file_name = globalSettings.design_rules_filename;
        parent_folder_name = null;
        confirm_import_rules_message = null;
      }

      File rules_file = new File(parent_folder_name, rules_file_name);
      if (rules_file.exists()) {
        // load the .rules file
        RoutingJob.read_rules_file(design_name, parent_folder_name, rules_file_name, new_frame.board_panel.board_handling, confirm_import_rules_message);
      }

      // ignore net classes if they were defined by a command line argument
      for (String net_class_name : globalSettings.routerSettings.ignoreNetClasses) {
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

      new_frame.refresh_windows();
    }
    return new_frame;
  }

  public static void saveSettings() throws IOException {
    GlobalSettings.saveAsJson(Freerouting.globalSettings);
  }

  /**
   * Opens a board design from a binary file or a specctra DSN file after the user chooses a file from the file chooser dialog.
   */
  private void open_board_design_action(ActionEvent evt) {
    File fileToOpen = RoutingJob.showOpenDialog(this.design_dir_name, null);
    RoutingJob routingJob = null;
    try {
      routingJob = new RoutingJob(SessionManager
          .getInstance()
          .getGuiSession().id);
      routingJob.setInput(fileToOpen);

      if (routingJob.input.getFile() != null) {
        if (!Objects.equals(this.design_dir_name, routingJob.input.getDirectoryPath())) {
          this.design_dir_name = routingJob.input.getDirectoryPath();
          this.globalSettings.guiSettings.inputDirectory = this.design_dir_name;

          try {
            GlobalSettings.saveAsJson(this.globalSettings);
          } catch (Exception e) {
            // it's ok if we can't save the configuration file
            FRLogger.error("Couldn't save configuration file", e);
          }
        }
      }
    } catch (Exception e) {
      FRLogger.error("Couldn't read the file", e);
      return;
    }

    //    if (design_file == null) {
    //      // The user didn't choose a file from the file chooser control
    //      message_field.setText(resources.getString("message_3"));
    //      return;
    //    }

    FRLogger.info("Opening '" + routingJob.input.getFilename() + "'...");

    String message = tm.getText("loading_design") + " " + routingJob.input.getFilename();
    message_field.setText(message);
    WindowMessage welcome_window = WindowMessage.show(message);
    welcome_window.setTitle(message);
    BoardFrame new_frame = create_board_frame(routingJob, message_field, globalSettings);
    welcome_window.dispose();
    if (new_frame == null) {
      return;
    }

    new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + this.max_passes - 1);
    new_frame.board_panel.board_handling.set_num_threads(this.num_threads);
    new_frame.board_panel.board_handling.set_board_update_strategy(this.board_update_strategy);
    new_frame.board_panel.board_handling.set_hybrid_ratio(this.hybrid_ratio);
    new_frame.board_panel.board_handling.set_item_selection_strategy(this.item_selection_strategy);

    if (new_frame.is_intermediate_stage_file_available()) {
      LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      String load_snapshot_confirmation = tm.getText("load_snapshot_confirmation").formatted(modification_time.format(formatter));

      if (WindowMessage.confirm(load_snapshot_confirmation)) {
        new_frame.load_intermediate_stage_file();
      }
    }

    message_field.setText(tm.getText("message_4") + " " + routingJob.input.getFilename() + " " + tm.getText("message_5"));
    board_frames.add(new_frame);
    new_frame.addWindowListener(new BoardFrameWindowListener(new_frame));
  }


  // NOTE: Since we use this Window only to start up the GUI this adapter is not used anymore
  private class BoardFrameWindowListener extends WindowAdapter {

    private BoardFrame board_frame;

    public BoardFrameWindowListener(BoardFrame p_board_frame) {
      this.board_frame = p_board_frame;
    }

    @Override
    public void windowClosed(WindowEvent evt) {
      if (board_frame != null) {
        // remove this board_frame from the list of board frames
        board_frame.dispose();
        board_frames.remove(board_frame);
        board_frame = null;
      }
    }
  }

  // NOTE: Since we use this Window only to start up the GUI this adapter is not used anymore
  private class WindowStateListener extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent evt) {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      boolean exit_program = true;
      if (!board_frames.isEmpty()) {
        int application_confirm_exit_dialog = JOptionPane.showConfirmDialog(null, tm.getText("confirm_cancel"), null, JOptionPane.YES_NO_OPTION);
        if (application_confirm_exit_dialog == JOptionPane.NO_OPTION) {
          setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
          FRAnalytics.buttonClicked("application_confirm_exit_dialog_no", tm.getText("confirm_cancel"));
          exit_program = false;
        }
      }
      if (exit_program) {
        System.exit(0);
      }
    }
  }
}