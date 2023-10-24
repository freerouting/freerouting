package app.freerouting.gui;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.board.TestLevel;
import app.freerouting.constants.Constants;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.ThreadActionListener;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.management.VersionChecker;
import app.freerouting.rules.NetClasses;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

/** Main application for creating frames with new or existing board designs. */
public class MainApplication extends WindowBase {
  static final String WEB_FILE_BASE_NAME = "http://www.freerouting.app";
  static final String VERSION_NUMBER_STRING =
      "v"
          + Constants.FREEROUTING_VERSION
          + " (build-date: "
          + Constants.FREEROUTING_BUILD_DATE
          + ")";
  private static final TestLevel DEBUG_LEVEL = TestLevel.CRITICAL_DEBUGGING_OUTPUT;
  private final ResourceBundle resources;
  private final JButton demonstration_button;
  private final JButton sample_board_button;
  private final JButton open_board_button;
  private final JButton restore_defaults_button;
  private final JTextField message_field;
  private final JPanel main_panel;
  /** A Frame with routing demonstrations in the net. */
  private final WindowNetSamples window_net_demonstrations;
  /** A Frame with sample board designs in the net. */
  private final WindowNetSamples window_net_sample_designs;
  /** The list of open board frames */
  private final Collection<BoardFrame> board_frames = new LinkedList<>();
  private final boolean is_test_version;
  private final Locale locale;
  private final boolean save_intermediate_stages;
  private final float optimization_improvement_threshold;
  private final String[] ignore_net_classes_by_autorouter;
  private String design_dir_name;
  private final int max_passes;
  private final BoardUpdateStrategy board_update_strategy;
  // Issue: adding a new field into AutorouteSettings caused exception when loading
  // an existing design: "Couldn't read design file", "InvalidClassException", incompatible with
  // serialized data
  // so choose to pass this parameter through BoardHandling
  private final String hybrid_ratio;
  private final ItemSelectionStrategy item_selection_strategy;
  private final int num_threads;
  private static StartupOptions startupOptions;
  /**
   * Creates new form MainApplication It takes the directory of the board designs as optional
   * argument.
   *
   * @param startupOptions
   */
  public MainApplication(StartupOptions startupOptions) {
    super(600, 300);

    this.design_dir_name = startupOptions.getDesignDir();
    this.max_passes = startupOptions.getMaxPasses();
    this.num_threads = startupOptions.getNumThreads();
    this.board_update_strategy = startupOptions.getBoardUpdateStrategy();
    this.hybrid_ratio = startupOptions.getHybridRatio();
    this.item_selection_strategy = startupOptions.getItemSelectionStrategy();
    this.is_test_version = startupOptions.isTestVersion();
    this.locale = startupOptions.getCurrentLocale();
    this.save_intermediate_stages = startupOptions.save_intermediate_stages;
    this.optimization_improvement_threshold = startupOptions.optimization_improvement_threshold;
    this.ignore_net_classes_by_autorouter = startupOptions.ignore_net_classes_by_autorouter;
    this.resources =
        ResourceBundle.getBundle("app.freerouting.gui.MainApplication", locale);

    main_panel = new JPanel();
    getContentPane().add(main_panel);
    GridBagLayout gridbag = new GridBagLayout();
    main_panel.setLayout(gridbag);

    GridBagConstraints gridbag_constraints = new GridBagConstraints();
    gridbag_constraints.insets = new Insets(10, 10, 10, 10);
    gridbag_constraints.gridwidth = GridBagConstraints.REMAINDER;

    demonstration_button = new JButton();
    sample_board_button = new JButton();
    open_board_button = new JButton();
    restore_defaults_button = new JButton();

    message_field = new JTextField();
    message_field.setText(resources.getString("command_line_missing_input"));
    this.window_net_demonstrations = new WindowNetDemonstrations(locale);
    Point location = getLocation();
    this.window_net_demonstrations.setLocation(
        (int) location.getX() + 50, (int) location.getY() + 50);
    this.window_net_sample_designs = new WindowNetSampleDesigns(locale);
    this.window_net_sample_designs.setLocation(
        (int) location.getX() + 90, (int) location.getY() + 90);

    setTitle(resources.getString("title") + " " + VERSION_NUMBER_STRING);
    boolean add_buttons = true;

    if (startupOptions.getWebstartOption()) {

      if (add_buttons) {
        demonstration_button.setText(resources.getString("router_demonstrations"));
        demonstration_button.setToolTipText(resources.getString("router_demonstrations_tooltip"));
        demonstration_button.addActionListener(evt -> window_net_demonstrations.setVisible(true));
        demonstration_button.addActionListener(evt -> FRAnalytics.buttonClicked("demonstration_button", demonstration_button.getText()));

        gridbag.setConstraints(demonstration_button, gridbag_constraints);
        main_panel.add(demonstration_button, gridbag_constraints);

        sample_board_button.setText(resources.getString("sample_designs"));
        sample_board_button.setToolTipText(resources.getString("sample_designs_tooltip"));
        sample_board_button.addActionListener(evt -> window_net_sample_designs.setVisible(true));
        sample_board_button.addActionListener(evt -> FRAnalytics.buttonClicked("sample_board_button", sample_board_button.getText()));

        gridbag.setConstraints(sample_board_button, gridbag_constraints);
        main_panel.add(sample_board_button, gridbag_constraints);
      }
    }

    open_board_button.setText(resources.getString("open_own_design"));
    open_board_button.setToolTipText(resources.getString("open_own_design_tooltip"));
    open_board_button.addActionListener(this::open_board_design_action);
    open_board_button.addActionListener(evt -> FRAnalytics.buttonClicked("open_board_button", open_board_button.getText()));

    gridbag.setConstraints(open_board_button, gridbag_constraints);
    if (add_buttons) {
      main_panel.add(open_board_button, gridbag_constraints);
    }

    if (startupOptions.getWebstartOption() && add_buttons) {
      restore_defaults_button.setText(resources.getString("restore_defaults"));
      restore_defaults_button.setToolTipText(resources.getString("restore_defaults_tooltip"));
      restore_defaults_button.addActionListener(evt -> {});
      restore_defaults_button.addActionListener(evt -> FRAnalytics.buttonClicked("restore_defaults_button", restore_defaults_button.getText()));
      gridbag.setConstraints(restore_defaults_button, gridbag_constraints);
      main_panel.add(restore_defaults_button, gridbag_constraints);
    }

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

  /**
   * Main function of the Application
   *
   * @param args
   */
  public static void main(String[] args) {
    // we have a special case if logging must be disabled before the general command line arguments are parsed
    if (args.length > 0 && Arrays.asList(args).contains("-dl")) {
      // disable logging
      FRLogger.disableLogging();
    }

    FRLogger.traceEntry("MainApplication.main()");

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException |
             IllegalAccessException ex) {
      FRLogger.error(ex.getLocalizedMessage(), ex);
    }

    // Log system information
    FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);
    FRLogger.debug(
        " Version: " + Constants.FREEROUTING_VERSION + "," + Constants.FREEROUTING_BUILD_DATE);
    FRLogger.debug(" Command line arguments: '" + String.join(" ", args) + "'");
    FRLogger.debug(
        " Architecture: "
            + System.getProperty("os.name")
            + ","
            + System.getProperty("os.arch")
            + ","
            + System.getProperty("os.version"));
    FRLogger.debug(
        " Java: " + System.getProperty("java.version") + "," + System.getProperty("java.vendor"));
    FRLogger.debug(" System Language: " + Locale.getDefault().getLanguage() + "," + Locale.getDefault());
    FRLogger.debug(
        " Hardware: "
            + Runtime.getRuntime().availableProcessors()
            + " CPU cores,"
            + (Runtime.getRuntime().maxMemory() / 1024 / 1024)
            + " MB RAM");
    FRLogger.debug(" UTC Time: " + Instant.now());

    Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

    try {
      startupOptions = StartupOptions.load();
      FRLogger.info("Settings were loaded from freerouting.json");
    } catch (Exception e) {
      // we don't want to stop if the configuration file doesn't exist
    }

    if (startupOptions == null)
    {
      startupOptions = new StartupOptions();

      // save the default values
      try {
        StartupOptions.save(startupOptions);
      } catch (Exception e)
      {
        // it's ok if we can't save the configuration file
      }
    }

    // parse the command line arguments
    startupOptions.parseCommandLineArguments(args);

    FRLogger.debug(" GUI Language: " + startupOptions.current_locale);

    FRLogger.debug(" Host: " + startupOptions.host);

    // Get default screen device
    Toolkit toolkit = Toolkit.getDefaultToolkit();

    // Get screen resolution
    Dimension screenSize = toolkit.getScreenSize();
    int width = screenSize.width;
    int height = screenSize.height;

    // Get screen DPI
    int dpi = toolkit.getScreenResolution();
    FRLogger.debug(" Screen: " + width + "x" + height + ", " + dpi + " DPI");


    // initialize analytics
    FRAnalytics.setWriteKey(Constants.FREEROUTING_VERSION,"G24pcCv4BmnqwBa8LsdODYRE6k9IAlqR");
    FRAnalytics.setEnabled(!startupOptions.disable_analytics);
    FRAnalytics.setUserId(startupOptions.user_id);
    FRAnalytics.identify();
    try {
      Thread.sleep(1000);
    }
    catch (Exception ignored) {
    }
    FRAnalytics.setAppLocation("app.freerouting.gui", "Freerouting");
    FRAnalytics.appStarted(
        Constants.FREEROUTING_VERSION,
        Constants.FREEROUTING_BUILD_DATE,
        String.join(" ", args),
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        System.getProperty("os.version"),
        System.getProperty("java.version"),
        System.getProperty("java.vendor"),
        Locale.getDefault(),
        startupOptions.current_locale,
        Runtime.getRuntime().availableProcessors(),
        (Runtime.getRuntime().maxMemory() / 1024 / 1024),
        startupOptions.host,
        width, height, dpi
    );

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

    // check for new version
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    new Thread(checker).start();

    // get localization resources
    ResourceBundle resources = ResourceBundle.getBundle("app.freerouting.gui.MainApplication", startupOptions.current_locale);

    // check if the user wants to see the help only
    if (startupOptions.show_help_option) {
      System.out.print(resources.getString("command_line_help"));
      System.exit(0);
      return;
    }

    if (startupOptions.single_design_option) {
      BoardFrame.Option board_option;
      if (startupOptions.session_file_option) {
        board_option = BoardFrame.Option.SESSION_FILE;
      } else {
        board_option = BoardFrame.Option.SINGLE_FRAME;
      }

      FRLogger.info("Opening '" + startupOptions.design_input_filename + "'...");
      DesignFile design_file = DesignFile.get_instance(startupOptions.design_input_filename);
      if (design_file == null) {
        FRLogger.warn(
            resources.getString("message_6")
                + " "
                + startupOptions.design_input_filename
                + " "
                + resources.getString("message_7"));
        return;
      }
      String message =
          resources.getString("loading_design") + " " + startupOptions.design_input_filename;
      WindowMessage welcome_window = WindowMessage.show(message);
      final BoardFrame new_frame =
          create_board_frame(
              design_file,
              null,
              board_option,
              startupOptions.test_version_option,
              startupOptions.current_locale,
              startupOptions.design_rules_filename,
              startupOptions.save_intermediate_stages,
              startupOptions.optimization_improvement_threshold,
              startupOptions.ignore_net_classes_by_autorouter);
      welcome_window.dispose();
      if (new_frame == null) {
        FRLogger.warn("Couldn't create window frame");
        System.exit(1);
        return;
      }

      new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(
          new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no()
              + startupOptions.max_passes
              - 1);
      new_frame.board_panel.board_handling.set_num_threads(startupOptions.num_threads);
      new_frame.board_panel.board_handling.set_board_update_strategy(
          startupOptions.board_update_strategy);
      new_frame.board_panel.board_handling.set_hybrid_ratio(startupOptions.hybrid_ratio);
      new_frame.board_panel.board_handling.set_item_selection_strategy(
          startupOptions.item_selection_strategy);

      if (startupOptions.design_output_filename != null)
      {
        // we need to set up a listener to save the design file when the autorouter is running
        new_frame.board_panel.board_handling.autorouter_listener = new ThreadActionListener() {
          @Override
          public void autorouterStarted() {}

          @Override
          public void autorouterAborted() {
            ExportBoardToFile(startupOptions.design_output_filename);
          }

          @Override
          public void autorouterFinished() {
            ExportBoardToFile(startupOptions.design_output_filename);
          }

          private void ExportBoardToFile(String filename) {
            if (filename == null) {
              FRLogger.warn("Couldn't export board, filename not specified");
              return;
            }

            if (!(filename.toLowerCase().endsWith(".dsn") ||
                  filename.toLowerCase().endsWith(".ses") ||
                  filename.toLowerCase().endsWith(".scr"))) {
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
                case ".dsn" -> new_frame.board_panel.board_handling.export_to_dsn_file(
                    output_stream, design_name, false);
                case ".ses" -> new_frame.board_panel.board_handling.export_specctra_session_file(
                    design_name, output_stream);
                case ".scr" -> {
                  ByteArrayOutputStream session_output_stream =
                      new ByteArrayOutputStream();
                  new_frame.board_panel.board_handling.export_specctra_session_file(
                      filename, session_output_stream);
                  InputStream input_stream =
                      new ByteArrayInputStream(session_output_stream.toByteArray());
                  new_frame.board_panel.board_handling.export_eagle_session_file(
                      input_stream, output_stream);
                }
              }

              System.exit(0);
            } catch (Exception e) {
              FRLogger.error("Couldn't export board to file", e);
            }
          }
        };
      }

      if (new_frame.is_intermediate_stage_file_available())
      {
        LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String load_snapshot_confirmation = String.format(resources.getString("load_snapshot_confirmation"), modification_time.format(formatter));

        if (WindowMessage.confirm(load_snapshot_confirmation))
        {
          new_frame.load_intermediate_stage_file();
        }
      }

      // start the auto-router automatically if both input and output files were passed as a parameter
      if ((startupOptions.design_input_filename != null)
          && (startupOptions.design_output_filename != null)) {

        // Add a model dialog with timeout to confirm the autorouter start with the default settings
        final String START_NOW_TEXT = resources.getString("auto_start_routing_startnow_button");
        JButton startNowButton = new JButton(START_NOW_TEXT + " (" + startupOptions.dialog_confirmation_timeout + ")");

        final String CANCEL_TEXT = resources.getString("auto_start_routing_cancel_button");
        Object[] options = {startNowButton, CANCEL_TEXT};

        final String AUTOSTART_MSG = resources.getString("auto_start_routing_message");
        JOptionPane auto_start_routing_dialog = new JOptionPane(
            AUTOSTART_MSG,
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION,
            null,
            options,
            options[0]
        );

        startNowButton.addActionListener(event -> auto_start_routing_dialog.setValue(options[0]));
        startNowButton.addActionListener(evt -> FRAnalytics.buttonClicked("auto_start_routing_dialog_start", startNowButton.getText()));

        final String AUTOSTART_TITLE = resources.getString("auto_start_routing_title");

        if (startupOptions.dialog_confirmation_timeout > 0) {
          // Add a timer to the dialog
          JDialog autostartDialog = auto_start_routing_dialog.createDialog(AUTOSTART_TITLE);

          // Update startNowButton text every second
          Timer autostartTimer =
              new Timer(
                  1000,
                  new ActionListener() {
                    private int secondsLeft = startupOptions.dialog_confirmation_timeout;

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
        if ((startupOptions.dialog_confirmation_timeout == 0) || (choice == options[0]))
        {
          // Start the auto-router
          InteractiveActionThread thread = new_frame.board_panel.board_handling.start_batch_autorouter();

          if (new_frame.board_panel.board_handling.autorouter_listener != null) {
            // Add the auto-router listener to save the design file when the autorouter is running
            thread.addListener(new_frame.board_panel.board_handling.autorouter_listener);
          }
        }

        if (choice == options[1]) {
          FRAnalytics.buttonClicked("auto_start_routing_dialog_cancel", "Cancel");
        }
      }

      new_frame.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
              System.exit(0);
            }
          });
    } else {
      new MainApplication(startupOptions).setVisible(true);
    }

    FRLogger.traceExit("MainApplication.main()");
  }

  /**
   * Creates a new board frame containing the data of the input design file. Returns null, if an
   * error occurred.
   */
  private static BoardFrame create_board_frame(
      DesignFile p_design_file,
      JTextField p_message_field,
      BoardFrame.Option p_option,
      boolean p_is_test_version,
      Locale p_locale,
      String p_design_rules_file,
      boolean p_save_intermediate_stages,
      float p_optimization_improvement_threshold,
      String[] p_ignore_net_classes_by_autorouter) {
    ResourceBundle resources =
        ResourceBundle.getBundle("app.freerouting.gui.MainApplication", p_locale);

    InputStream input_stream = p_design_file.get_input_stream();
    if (input_stream == null) {
      if (p_message_field != null) {
        p_message_field.setText(resources.getString("message_8") + " " + p_design_file.get_name());
      }
      return null;
    }

    TestLevel test_level = p_is_test_version ? DEBUG_LEVEL : TestLevel.RELEASE_VERSION;
    BoardFrame new_frame =
        new BoardFrame(
            p_design_file,
            p_option,
            test_level,
            p_locale,
            !p_is_test_version,
            p_save_intermediate_stages,
            p_optimization_improvement_threshold);
    boolean read_ok =
        new_frame.read(input_stream, p_design_file.is_created_from_text_file(), p_message_field);
    if (!read_ok) {
      return null;
    }
    new_frame.menubar.add_design_dependent_items();
    if (p_design_file.is_created_from_text_file()) {
      // Read the file  with the saved rules, if it is existing.

      String file_name = p_design_file.get_name();
      String[] name_parts = file_name.split("\\.");

      String design_name = name_parts[0];

      String rules_file_name;
      String parent_folder_name;
      String confirm_import_rules_message;
      if (p_design_rules_file == null) {
        rules_file_name = design_name + ".rules";
        parent_folder_name = p_design_file.get_parent();
        confirm_import_rules_message = resources.getString("confirm_import_rules");
      } else {
        rules_file_name = p_design_rules_file;
        parent_folder_name = null;
        confirm_import_rules_message = null;
      }

      File rules_file = new File(parent_folder_name, rules_file_name);
      if (rules_file.exists()) {
        // load the .rules file
        DesignFile.read_rules_file(
            design_name,
            parent_folder_name,
            rules_file_name,
            new_frame.board_panel.board_handling,
            confirm_import_rules_message);
      }

      // ignore net classes if they were defined by a command line argument
      for (String net_class_name : p_ignore_net_classes_by_autorouter) {
        NetClasses netClasses =
            new_frame.board_panel.board_handling.get_routing_board().rules.net_classes;

        for (int i = 0; i < netClasses.count(); i++) {
          if (netClasses.get(i).get_name().equalsIgnoreCase(net_class_name)) {
            netClasses.get(i).is_ignored_by_autorouter = true;
          }
        }
      }

      new_frame.refresh_windows();
    }
    return new_frame;
  }

  /** opens a board design from a binary file or a specctra dsn file. */
  private void open_board_design_action(ActionEvent evt) {
    DesignFile design_file = DesignFile.open_dialog(this.design_dir_name);

    if (design_file != null) {
      if (!Objects.equals(this.design_dir_name, design_file.get_directory())) {
        this.design_dir_name = design_file.get_directory();
        startupOptions.input_directory = this.design_dir_name;

        try {
          StartupOptions.save(startupOptions);
        } catch (Exception e) {
          // it's ok if we can't save the configuration file
          FRLogger.error("Couldn't save configuration file", e);
        }
      }
    }

    if (design_file == null) {
      message_field.setText(resources.getString("message_3"));
      return;
    }

    FRLogger.info("Opening '" + design_file.get_name() + "'...");

    BoardFrame.Option option;
    option = BoardFrame.Option.FROM_START_MENU;
    String message = resources.getString("loading_design") + " " + design_file.get_name();
    message_field.setText(message);
    WindowMessage welcome_window = WindowMessage.show(message);
    welcome_window.setTitle(message);
    BoardFrame new_frame =
        create_board_frame(
            design_file,
            message_field,
            option,
            this.is_test_version,
            this.locale,
            null,
            this.save_intermediate_stages,
            this.optimization_improvement_threshold,
            this.ignore_net_classes_by_autorouter);
    welcome_window.dispose();
    if (new_frame == null) {
      return;
    }

    new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(
        new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no()
            + this.max_passes
            - 1);
    new_frame.board_panel.board_handling.set_num_threads(this.num_threads);
    new_frame.board_panel.board_handling.set_board_update_strategy(this.board_update_strategy);
    new_frame.board_panel.board_handling.set_hybrid_ratio(this.hybrid_ratio);
    new_frame.board_panel.board_handling.set_item_selection_strategy(this.item_selection_strategy);

    if (new_frame.is_intermediate_stage_file_available())
    {
      LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      String load_snapshot_confirmation = String.format(resources.getString("load_snapshot_confirmation"), modification_time.format(formatter));

      if (WindowMessage.confirm(load_snapshot_confirmation))
      {
        new_frame.load_intermediate_stage_file();
      }
    }

    message_field.setText(
        resources.getString("message_4")
            + " "
            + design_file.get_name()
            + " "
            + resources.getString("message_5"));
    board_frames.add(new_frame);
    new_frame.addWindowListener(new BoardFrameWindowListener(new_frame));
  }

  /** Exit the Application */
  private void exitForm(WindowEvent evt) {
    FRAnalytics.appClosed();
    System.exit(0);
  }

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

  private class WindowStateListener extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent evt) {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      boolean exit_program = true;
      if (!is_test_version && !board_frames.isEmpty()) {
        int application_confirm_exit_dialog =
            JOptionPane.showConfirmDialog(
                null,
                resources.getString("confirm_cancel"),
                null,
                JOptionPane.YES_NO_OPTION);
        if (application_confirm_exit_dialog == JOptionPane.NO_OPTION) {
          setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
          FRAnalytics.buttonClicked("application_confirm_exit_dialog_no", resources.getString("confirm_cancel"));
          exit_program = false;
        }
      }
      if (exit_program) {
        exitForm(evt);
      }
    }

    @Override
    public void windowIconified(WindowEvent evt) {
      window_net_sample_designs.parent_iconified();
    }

    @Override
    public void windowDeiconified(WindowEvent evt) {
      window_net_sample_designs.parent_deiconified();
    }
  }
}
