package app.freerouting.gui;

import app.freerouting.api.AppContextListener;
import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.constants.Constants;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.interactive.ThreadActionListener;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;
import app.freerouting.management.VersionChecker;
import app.freerouting.rules.NetClasses;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.swing.Timer;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Main application for creating frames with new or existing board designs.
 */
public class MainApplication extends WindowBase
{
  static final String WEB_FILE_BASE_NAME = "http://www.freerouting.app";
  static final String VERSION_NUMBER_STRING = "v" + Constants.FREEROUTING_VERSION + " (build-date: " + Constants.FREEROUTING_BUILD_DATE + ")";
  public static GlobalSettings globalSettings;
  private final JButton open_board_button;
  private final JButton restore_defaults_button;
  private final JTextField message_field;
  private final JPanel main_panel;

  /**
   * The list of open board frames
   */
  private final Collection<BoardFrame> board_frames = new LinkedList<>();

  private final boolean is_test_version;
  private final Locale locale;
  private final boolean save_intermediate_stages;
  private final float optimization_improvement_threshold;
  private final String[] ignore_net_classes_by_autorouter;
  private final int max_passes;
  private final BoardUpdateStrategy board_update_strategy;
  // Issue: adding a new field into AutorouteSettings caused exception when loading
  // an existing design: "Couldn't read design file", "InvalidClassException", incompatible with
  // serialized data
  // so choose to pass this parameter through BoardHandling
  private final String hybrid_ratio;
  private final ItemSelectionStrategy item_selection_strategy;
  private final int num_threads;
  private String design_dir_name;

  /**
   * Creates new form MainApplication It takes the directory of the board designs as optional
   * argument.
   *
   * @param globalSettings
   */
  public MainApplication(GlobalSettings globalSettings)
  {
    super(600, 300);

    this.design_dir_name = globalSettings.getDesignDir();
    this.max_passes = globalSettings.getMaxPasses();
    this.num_threads = globalSettings.getNumThreads();
    this.board_update_strategy = globalSettings.getBoardUpdateStrategy();
    this.hybrid_ratio = globalSettings.getHybridRatio();
    this.item_selection_strategy = globalSettings.getItemSelectionStrategy();
    this.is_test_version = globalSettings.isTestVersion();
    this.locale = globalSettings.getCurrentLocale();
    this.save_intermediate_stages = !globalSettings.disabledFeatures.snapshots;
    this.optimization_improvement_threshold = globalSettings.autoRouterSettings.optimization_improvement_threshold;
    this.ignore_net_classes_by_autorouter = globalSettings.autoRouterSettings.ignore_net_classes_by_autorouter;

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

    setTitle(tm.getText("title") + " " + VERSION_NUMBER_STRING);

    open_board_button.setText(tm.getText("open_own_design"));
    open_board_button.setToolTipText(tm.getText("open_own_design_tooltip"));
    open_board_button.addActionListener(this::open_board_design_action);
    open_board_button.addActionListener(evt -> FRAnalytics.buttonClicked("open_board_button", open_board_button.getText()));

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

  /**
   * The entry point of the Freerouting application
   *
   * @param args
   */
  public static void main(String[] args)
  {
    // we have a special case if logging must be disabled before the general command line arguments
    // are parsed
    if (args.length > 0 && Arrays.asList(args).contains("-dl"))
    {
      // disable logging
      FRLogger.disableLogging();
    }
    else if (args.length > 0 && Arrays.asList(args).contains("-ll"))
    {
      // get the log level from the command line arguments
      int logLevelIndex = Arrays.asList(args).indexOf("-ll") + 1;
      if (logLevelIndex < args.length)
      {
        FRLogger.changeFileLogLevel(args[logLevelIndex]);
      }
    }

    FRLogger.traceEntry("MainApplication.main()");

    try
    {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException ex)
    {
      FRLogger.error(ex.getLocalizedMessage(), ex);
    }

    // Log system information
    FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);
    FRLogger.debug(" Version: " + Constants.FREEROUTING_VERSION + "," + Constants.FREEROUTING_BUILD_DATE);
    FRLogger.debug(" Command line arguments: '" + String.join(" ", args) + "'");
    FRLogger.debug(" Architecture: " + System.getProperty("os.name") + "," + System.getProperty("os.arch") + "," + System.getProperty("os.version"));
    FRLogger.debug(" Java: " + System.getProperty("java.version") + "," + System.getProperty("java.vendor"));
    FRLogger.debug(" System Language: " + Locale.getDefault().getLanguage() + "," + Locale.getDefault());
    FRLogger.debug(" Hardware: " + Runtime.getRuntime().availableProcessors() + " CPU cores," + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB RAM");
    FRLogger.debug(" UTC Time: " + Instant.now());

    Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

    try
    {
      globalSettings = GlobalSettings.load();
      FRLogger.info("Settings were loaded from freerouting.json");
    } catch (Exception e)
    {
      // we don't want to stop if the configuration file doesn't exist
    }

    if ((globalSettings == null) || (globalSettings.version != Constants.FREEROUTING_VERSION))
    {
      globalSettings = new GlobalSettings();

      // save the default values
      try
      {
        GlobalSettings.save(globalSettings);
      } catch (Exception e)
      {
        // it's ok if we can't save the configuration file
      }
    }

    // parse the command line arguments
    globalSettings.parseCommandLineArguments(args);

    FRLogger.debug(" GUI Language: " + globalSettings.current_locale);

    FRLogger.debug(" Host: " + globalSettings.host);

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
    FRAnalytics.setWriteKey(Constants.FREEROUTING_VERSION, "G24pcCv4BmnqwBa8LsdODYRE6k9IAlqR");
    int analyticsModulo = Math.max(globalSettings.usageAndDiagnosticData.analytics_modulo, 1);
    String userIdString = globalSettings.usageAndDiagnosticData.user_id.length() >= 4 ? globalSettings.usageAndDiagnosticData.user_id.substring(0, 4) : "0000";
    int userIdValue = Integer.parseInt(userIdString, 16);
    boolean allowAnalytics = !globalSettings.usageAndDiagnosticData.disable_analytics && (userIdValue % analyticsModulo == 0);
    if (!allowAnalytics)
    {
      FRLogger.debug("Analytics are disabled");
    }
    FRAnalytics.setEnabled(allowAnalytics);
    FRAnalytics.setUserId(globalSettings.usageAndDiagnosticData.user_id);
    FRAnalytics.identify();
    try
    {
      Thread.sleep(1000);
    } catch (Exception ignored)
    {
    }
    FRAnalytics.setAppLocation("app.freerouting.gui", "Freerouting");
    FRAnalytics.appStarted(Constants.FREEROUTING_VERSION, Constants.FREEROUTING_BUILD_DATE, String.join(" ", args), System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version"), System.getProperty("java.version"), System.getProperty("java.vendor"), Locale.getDefault(), globalSettings.current_locale, Runtime.getRuntime().availableProcessors(), (Runtime.getRuntime().maxMemory() / 1024 / 1024), globalSettings.host, width, height, dpi);

    // check for new version
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    new Thread(checker).start();

    // Initialize the GUI
    if (!InitializeGUI())
    {
      // Couldn't initialize the GUI
      return;
    }

    if (globalSettings.apiServerSettings.isEnabled)
    {
      InitializeAPI(globalSettings.apiServerSettings);
    }

    try
    {
      GlobalSettings.save(globalSettings);
    } catch (Exception e)
    {
      // it's ok if we can't update the configuration file, it's just optional
    }

    FRLogger.traceExit("MainApplication.main()");
  }

  private static void InitializeAPI(ApiServerSettings apiServerSettings)
  {
    // Check if there are any endpoints defined
    if (apiServerSettings.endpoints.length == 0)
    {
      FRLogger.warn("Can't start API server, because no endpoints are defined in ApiServerSettings.");
      return;
    }

    // Convert the first endpoint (e.g. "https://localhost:8080") in ApiServerSettings to InetSocketAddress
    String endpoint = apiServerSettings.endpoints[0].toLowerCase();
    // Endpoints following the following format: "protocol://host:port" (although the protocol is not used in this case, because only HTTP/HTTPS is supported)
    String[] endpointParts = endpoint.split("://");
    String protocol = endpointParts[0];
    String hostAndPort = endpointParts[1];
    String[] hostAndPortParts = hostAndPort.split(":");
    String host = hostAndPortParts[0];
    int port = Integer.parseInt(hostAndPortParts[1]);

    // Check if the protocol is HTTP or HTTPS
    if (!protocol.equals("http") && !protocol.equals("https"))
    {
      FRLogger.warn("Can't use the endpoint '%s' for the API server, because its protocol is not HTTP or HTTPS.".formatted(endpoint));
      return;
    }

    // Start the Jetty server
    InetSocketAddress address = new InetSocketAddress(host, port);
    Server server = new Server(address);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);

    // Set up Jersey Servlet
    ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/api/*");
    jerseyServlet.setInitOrder(0);
    jerseyServlet.setInitParameter("jersey.config.server.provider.packages", "app.freerouting.api");
    jerseyServlet.setInitParameter("javax.ws.rs.Application", "app.freerouting.JerseyConfig");
    jerseyServlet.setInitParameter("javax.ws.rs.Application", "app.freerouting.api.OpenAPIConfig");

    // Add the DefaultServlet to handle static content
    ServletHolder defaultServlet = new ServletHolder("defaultServlet", DefaultServlet.class);
    context.addServlet(defaultServlet, "/");

    // Add Context Listeners
    context.addEventListener(new AppContextListener());

    try
    {
      server.start();
      server.join();
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  private static boolean InitializeGUI()
  {
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
    TextManager tm = new TextManager(MainApplication.class, globalSettings.current_locale);

    // check if the user wants to see the help only
    if (globalSettings.show_help_option)
    {
      System.out.print(tm.getText("command_line_help"));
      System.exit(0);
      return false;
    }

    if (globalSettings.design_input_filename != null)
    {
      FRLogger.info("Opening '" + globalSettings.design_input_filename + "'...");
      DesignFile design_file = DesignFile.get_instance(globalSettings.design_input_filename);
      if (design_file == null)
      {
        FRLogger.warn(tm.getText("message_6") + " " + globalSettings.design_input_filename + " " + tm.getText("message_7"));
        return false;
      }
      String message = tm.getText("loading_design") + " " + globalSettings.design_input_filename;
      WindowMessage welcome_window = WindowMessage.show(message);
      final BoardFrame new_frame = create_board_frame(design_file, null, globalSettings.test_version_option, globalSettings.current_locale, globalSettings.design_rules_filename, !globalSettings.disabledFeatures.snapshots, globalSettings.autoRouterSettings.optimization_improvement_threshold, globalSettings.autoRouterSettings.ignore_net_classes_by_autorouter);
      welcome_window.dispose();
      if (new_frame == null)
      {
        FRLogger.warn("Couldn't create window frame");
        System.exit(1);
        return false;
      }

      new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + globalSettings.autoRouterSettings.max_passes - 1);
      new_frame.board_panel.board_handling.set_num_threads(globalSettings.autoRouterSettings.num_threads);
      new_frame.board_panel.board_handling.set_board_update_strategy(globalSettings.autoRouterSettings.board_update_strategy);
      new_frame.board_panel.board_handling.set_hybrid_ratio(globalSettings.autoRouterSettings.hybrid_ratio);
      new_frame.board_panel.board_handling.set_item_selection_strategy(globalSettings.autoRouterSettings.item_selection_strategy);

      if (globalSettings.design_output_filename != null)
      {
        // we need to set up a listener to save the design file when the autorouter is running
        new_frame.board_panel.board_handling.autorouter_listener = new ThreadActionListener()
        {
          @Override
          public void autorouterStarted()
          {
          }

          @Override
          public void autorouterAborted()
          {
            ExportBoardToFile(globalSettings.design_output_filename);
          }

          @Override
          public void autorouterFinished()
          {
            ExportBoardToFile(globalSettings.design_output_filename);
          }

          private void ExportBoardToFile(String filename)
          {
            if (filename == null)
            {
              FRLogger.warn("Couldn't export board, filename not specified");
              return;
            }

            if (!(filename.toLowerCase().endsWith(".dsn") || filename.toLowerCase().endsWith(".ses") || filename.toLowerCase().endsWith(".scr")))
            {
              FRLogger.warn("Couldn't export board to '" + filename + "', unsupported extension");
              return;
            }

            FRLogger.info("Saving '" + filename + "'...");
            try
            {
              String filename_only = new File(filename).getName();
              String design_name = filename_only.substring(0, filename_only.length() - 4);
              String extension = filename_only.substring(filename_only.length() - 4);

              OutputStream output_stream = new FileOutputStream(filename);

              switch (extension)
              {
                case ".dsn" -> new_frame.board_panel.board_handling.saveAsSpecctraDesignDsn(output_stream, design_name, false);
                case ".ses" -> new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(output_stream, design_name);
                case ".scr" ->
                {
                  ByteArrayOutputStream session_output_stream = new ByteArrayOutputStream();
                  new_frame.board_panel.board_handling.saveAsSpecctraSessionSes(session_output_stream, filename);
                  InputStream input_stream = new ByteArrayInputStream(session_output_stream.toByteArray());
                  new_frame.board_panel.board_handling.saveSpecctraSessionSesAsEagleScriptScr(input_stream, output_stream);
                }
              }

              System.exit(0);
            } catch (Exception e)
            {
              FRLogger.error("Couldn't export board to file", e);
            }
          }
        };
      }

      if (new_frame.is_intermediate_stage_file_available())
      {
        LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String load_snapshot_confirmation = String.format(tm.getText("load_snapshot_confirmation"), modification_time.format(formatter));

        if (WindowMessage.confirm(load_snapshot_confirmation))
        {
          new_frame.load_intermediate_stage_file();
        }
      }

      // start the auto-router automatically if both input and output files were passed as a
      // parameter
      if ((globalSettings.design_input_filename != null) && (globalSettings.design_output_filename != null))
      {

        // Add a model dialog with timeout to confirm the autorouter start with the default settings
        final String START_NOW_TEXT = tm.getText("auto_start_routing_startnow_button");
        JButton startNowButton = new JButton(START_NOW_TEXT + " (" + globalSettings.dialog_confirmation_timeout + ")");

        final String CANCEL_TEXT = tm.getText("auto_start_routing_cancel_button");
        Object[] options = {startNowButton, CANCEL_TEXT};

        final String AUTOSTART_MSG = tm.getText("auto_start_routing_message");
        JOptionPane auto_start_routing_dialog = new JOptionPane(AUTOSTART_MSG, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);

        startNowButton.addActionListener(event -> auto_start_routing_dialog.setValue(options[0]));
        startNowButton.addActionListener(evt -> FRAnalytics.buttonClicked("auto_start_routing_dialog_start", startNowButton.getText()));

        final String AUTOSTART_TITLE = tm.getText("auto_start_routing_title");

        if (globalSettings.dialog_confirmation_timeout > 0)
        {
          // Add a timer to the dialog
          JDialog autostartDialog = auto_start_routing_dialog.createDialog(AUTOSTART_TITLE);

          // Update startNowButton text every second
          Timer autostartTimer = new Timer(1000, new ActionListener()
          {
            private int secondsLeft = globalSettings.dialog_confirmation_timeout;

            @Override
            public void actionPerformed(ActionEvent e)
            {
              if (--secondsLeft > 0)
              {
                startNowButton.setText(START_NOW_TEXT + " (" + secondsLeft + ")");
              }
              else
              {
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
        if ((globalSettings.dialog_confirmation_timeout == 0) || (choice == options[0]))
        {
          // Start the auto-router
          InteractiveActionThread thread = new_frame.board_panel.board_handling.start_autorouter_and_route_optimizer();

          if (new_frame.board_panel.board_handling.autorouter_listener != null)
          {
            // Add the auto-router listener to save the design file when the autorouter is running
            thread.addListener(new_frame.board_panel.board_handling.autorouter_listener);
          }
        }

        if (choice == options[1])
        {
          FRAnalytics.buttonClicked("auto_start_routing_dialog_cancel", "Cancel");
        }
      }

      new_frame.addWindowListener(new WindowAdapter()
      {
        @Override
        public void windowClosed(WindowEvent evt)
        {
          System.exit(0);
        }
      });
    }
    else
    {
      if (globalSettings.disabledFeatures.fileLoadDialogAtStartup)
      {
        final BoardFrame new_frame = create_board_frame(null, null, globalSettings.test_version_option, globalSettings.current_locale, globalSettings.design_rules_filename, !globalSettings.disabledFeatures.snapshots, globalSettings.autoRouterSettings.optimization_improvement_threshold, globalSettings.autoRouterSettings.ignore_net_classes_by_autorouter);
        if (new_frame == null)
        {
          FRLogger.warn("Couldn't create window frame");
          System.exit(1);
          return false;
        }
      }
      else
      {
        new MainApplication(globalSettings).setVisible(true);
      }
    }
    return true;
  }

  /**
   * Creates a new board frame containing the data of the input design file. Returns null, if an
   * error occurred.
   */
  private static BoardFrame create_board_frame(DesignFile p_design_file, JTextField p_message_field, boolean p_is_test_version, Locale p_locale, String p_design_rules_file, boolean p_save_intermediate_stages, float p_optimization_improvement_threshold, String[] p_ignore_net_classes_by_autorouter)
  {
    TextManager tm = new TextManager(MainApplication.class, p_locale);

    InputStream input_stream = null;
    if ((p_design_file == null) || (p_design_file.getInputFile() == null))
    {
      p_design_file = new DesignFile(null);
      p_design_file.setDummyInputFile("freerouting_empty_board.dsn");
      // Load an empty template file from the resources
      ClassLoader classLoader = WindowBase.class.getClassLoader();
      input_stream = classLoader.getResourceAsStream("freerouting_empty_board.dsn");
    }
    else
    {
      input_stream = p_design_file.get_input_stream();
      if (input_stream == null)
      {
        if (p_message_field != null)
        {
          p_message_field.setText(tm.getText("message_8") + " " + p_design_file.get_name());
        }
        return null;
      }
    }

    BoardFrame new_frame = new BoardFrame(p_design_file, p_locale, p_save_intermediate_stages, p_optimization_improvement_threshold, globalSettings.disabledFeatures);
    boolean read_ok = new_frame.load(input_stream, p_design_file.inputFileFormat.equals(FileFormat.DSN), p_message_field);
    if (!read_ok)
    {
      return null;
    }

    FRAnalytics.buttonClicked("fileio_loaddsn", p_design_file.getInputFileDetails());

    if (globalSettings.disabledFeatures.selectMode)
    {
      new_frame.board_panel.board_handling.set_route_menu_state();
    }

    if (p_design_file.inputFileFormat.equals(FileFormat.DSN))
    {
      // Read the file with the saved rules, if it exists.

      String file_name = p_design_file.get_name();
      String[] name_parts = file_name.split("\\.");

      String design_name = name_parts[0];

      String rules_file_name;
      String parent_folder_name;
      String confirm_import_rules_message;
      if (p_design_rules_file == null)
      {
        rules_file_name = design_name + ".rules";
        parent_folder_name = p_design_file.getInputFileDirectoryOrNull();
        confirm_import_rules_message = tm.getText("confirm_import_rules");
      }
      else
      {
        rules_file_name = p_design_rules_file;
        parent_folder_name = null;
        confirm_import_rules_message = null;
      }

      File rules_file = new File(parent_folder_name, rules_file_name);
      if (rules_file.exists())
      {
        // load the .rules file
        DesignFile.read_rules_file(design_name, parent_folder_name, rules_file_name, new_frame.board_panel.board_handling, confirm_import_rules_message);
      }

      // ignore net classes if they were defined by a command line argument
      for (String net_class_name : p_ignore_net_classes_by_autorouter)
      {
        NetClasses netClasses = new_frame.board_panel.board_handling.get_routing_board().rules.net_classes;

        for (int i = 0; i < netClasses.count(); i++)
        {
          if (netClasses.get(i).get_name().equalsIgnoreCase(net_class_name))
          {
            netClasses.get(i).is_ignored_by_autorouter = true;
          }
        }
      }

      new_frame.refresh_windows();
    }
    return new_frame;
  }

  public static void saveSettings() throws IOException
  {
    GlobalSettings.save(globalSettings);
  }

  /**
   * Opens a board design from a binary file or a specctra DSN file after the user chooses a file
   * from the file chooser dialog.
   */
  private void open_board_design_action(ActionEvent evt)
  {

    File fileToOpen = DesignFile.showOpenDialog(this.design_dir_name, null);
    DesignFile design_file = new DesignFile(fileToOpen);

    if (design_file.getInputFile() != null)
    {
      if (!Objects.equals(this.design_dir_name, design_file.getInputFileDirectory()))
      {
        this.design_dir_name = design_file.getInputFileDirectory();
        globalSettings.input_directory = this.design_dir_name;

        try
        {
          GlobalSettings.save(globalSettings);
        } catch (Exception e)
        {
          // it's ok if we can't save the configuration file
          FRLogger.error("Couldn't save configuration file", e);
        }
      }
    }

    //    if (design_file == null) {
    //      // The user didn't choose a file from the file chooser control
    //      message_field.setText(resources.getString("message_3"));
    //      return;
    //    }

    FRLogger.info("Opening '" + design_file.get_name() + "'...");

    String message = tm.getText("loading_design") + " " + design_file.get_name();
    message_field.setText(message);
    WindowMessage welcome_window = WindowMessage.show(message);
    welcome_window.setTitle(message);
    BoardFrame new_frame = create_board_frame(design_file, message_field, this.is_test_version, this.locale, null, this.save_intermediate_stages, this.optimization_improvement_threshold, this.ignore_net_classes_by_autorouter);
    welcome_window.dispose();
    if (new_frame == null)
    {
      return;
    }

    new_frame.board_panel.board_handling.settings.autoroute_settings.set_stop_pass_no(new_frame.board_panel.board_handling.settings.autoroute_settings.get_start_pass_no() + this.max_passes - 1);
    new_frame.board_panel.board_handling.set_num_threads(this.num_threads);
    new_frame.board_panel.board_handling.set_board_update_strategy(this.board_update_strategy);
    new_frame.board_panel.board_handling.set_hybrid_ratio(this.hybrid_ratio);
    new_frame.board_panel.board_handling.set_item_selection_strategy(this.item_selection_strategy);

    if (new_frame.is_intermediate_stage_file_available())
    {
      LocalDateTime modification_time = new_frame.get_intermediate_stage_file_modification_time();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      String load_snapshot_confirmation = String.format(tm.getText("load_snapshot_confirmation"), modification_time.format(formatter));

      if (WindowMessage.confirm(load_snapshot_confirmation))
      {
        new_frame.load_intermediate_stage_file();
      }
    }

    message_field.setText(tm.getText("message_4") + " " + design_file.get_name() + " " + tm.getText("message_5"));
    board_frames.add(new_frame);
    new_frame.addWindowListener(new BoardFrameWindowListener(new_frame));
  }

  /**
   * Exit the Application
   */
  private void exitForm(WindowEvent evt)
  {
    FRAnalytics.appClosed();
    System.exit(0);
  }

  private class BoardFrameWindowListener extends WindowAdapter
  {

    private BoardFrame board_frame;

    public BoardFrameWindowListener(BoardFrame p_board_frame)
    {
      this.board_frame = p_board_frame;
    }

    @Override
    public void windowClosed(WindowEvent evt)
    {
      if (board_frame != null)
      {
        // remove this board_frame from the list of board frames
        board_frame.dispose();
        board_frames.remove(board_frame);
        board_frame = null;
      }
    }
  }

  private class WindowStateListener extends WindowAdapter
  {

    @Override
    public void windowClosing(WindowEvent evt)
    {
      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      boolean exit_program = true;
      if (!is_test_version && !board_frames.isEmpty())
      {
        int application_confirm_exit_dialog = JOptionPane.showConfirmDialog(null, tm.getText("confirm_cancel"), null, JOptionPane.YES_NO_OPTION);
        if (application_confirm_exit_dialog == JOptionPane.NO_OPTION)
        {
          setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
          FRAnalytics.buttonClicked("application_confirm_exit_dialog_no", tm.getText("confirm_cancel"));
          exit_program = false;
        }
      }
      if (exit_program)
      {
        exitForm(evt);
      }
    }
  }
}