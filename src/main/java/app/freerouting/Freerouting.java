package app.freerouting;

import app.freerouting.api.AppContextListener;
import app.freerouting.constants.Constants;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.gui.DefaultExceptionHandler;
import app.freerouting.gui.WindowWelcome;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.VersionChecker;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/* Entry point class of the application */
public class Freerouting
{
  public static final String WEB_URL = "https://www.freerouting.app";
  public static final String VERSION_NUMBER_STRING = "v" + Constants.FREEROUTING_VERSION + " (build-date: " + Constants.FREEROUTING_BUILD_DATE + ")";
  public static GlobalSettings globalSettings;
  private static Server apiServer; // API server instance

  /**
   * The entry point of the Freerouting application
   *
   * @param args
   */
  public static void main(String[] args)
  {
    FRLogger.traceEntry("MainApplication.main()");

    // the first thing we need to do is to determine the user directory, because all settings and logs will be located there
    // 1, set it to the temp directory by default
    Path userdataPath = Paths.get(System.getProperty("java.io.tmpdir"), "freerouting");
    // 2, check if we need to override it with the "FREEROUTING__USER_DATA_PATH" environment variable value
    if (System.getenv("FREEROUTING__USER_DATA_PATH") != null)
    {
      userdataPath = Paths.get(System.getenv("FREEROUTING__USER_DATA_PATH"));
    }
    // 3, check if we need to override it with the "--user_data_path={directory}" command line argument
    if (args.length > 0 && Arrays
        .stream(args)
        .anyMatch(s -> s.startsWith("--user_data_path=")))
    {
      var userDataPathArg = Arrays
          .stream(args)
          .filter(s -> s.startsWith("--user_data_path="))
          .findFirst();

      if (userDataPathArg.isPresent())
      {
        userdataPath = Paths.get(userDataPathArg
            .get()
            .substring("--user_data_path=".length()));
      }
    }
    // 4, create the directory if it doesn't exist
    if (!userdataPath
        .toFile()
        .exists())
    {
      userdataPath
          .toFile()
          .mkdirs();
    }
    // 5, check if it exists now, and if it does, apply it to FRLogger
    if (userdataPath
        .toFile()
        .exists())
    {
      GlobalSettings.setUserDataPath(userdataPath);
    }
    // 6, make sure that this settings can't be changed later on
    GlobalSettings.lockUserDataPath();

    // we have a special case if logging must be disabled before the general command line arguments
    // are parsed
    if (args.length > 0 && Arrays
        .asList(args)
        .contains("-dl"))
    {
      // disable logging
      FRLogger.disableLogging();
    }
    else if (args.length > 0 && Arrays
        .asList(args)
        .contains("-ll"))
    {
      // get the log level from the command line arguments
      int logLevelIndex = Arrays
          .asList(args)
          .indexOf("-ll") + 1;
      if (logLevelIndex < args.length)
      {
        FRLogger.changeFileLogLevel(args[logLevelIndex]);
      }
    }

    try
    {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException ex)
    {
      FRLogger.error(ex.getLocalizedMessage(), ex);
    }

    // Log system information
    FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);
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
        GlobalSettings.saveAsJson(globalSettings);
      } catch (Exception e)
      {
        // it's ok if we can't save the configuration file
      }
    }

    // apply environment variables to the settings
    globalSettings.applyEnvironmentVariables();

    // if we don't have a GUI enabled then we must use the console as our output
    if ((!globalSettings.guiSettings.isEnabled) && (System.console() == null))
    {
      FRLogger.warn("GUI is disabled and you don't have a console available, so the only feedback from Freerouting is in the log.");
    }

    // get environment parameters and save them in the settings
    globalSettings.environmentSettings.freeroutingVersion = Constants.FREEROUTING_VERSION + "," + Constants.FREEROUTING_BUILD_DATE;
    globalSettings.environmentSettings.appStartedAt = Instant.now();
    globalSettings.environmentSettings.commandLineArguments = String.join(" ", args);
    globalSettings.environmentSettings.architecture = System.getProperty("os.name") + "," + System.getProperty("os.arch") + "," + System.getProperty("os.version");
    globalSettings.environmentSettings.java = System.getProperty("java.version") + "," + System.getProperty("java.vendor");
    globalSettings.environmentSettings.systemLanguage = Locale
        .getDefault()
        .getLanguage() + "," + Locale.getDefault();
    globalSettings.environmentSettings.cpuCores = Runtime
        .getRuntime()
        .availableProcessors();
    globalSettings.environmentSettings.ram = (int) (Runtime
        .getRuntime()
        .maxMemory() / 1024 / 1024);
    FRLogger.debug("Version: " + globalSettings.environmentSettings.freeroutingVersion);
    FRLogger.debug("Command line arguments: '" + globalSettings.environmentSettings.commandLineArguments + "'");
    FRLogger.debug("Architecture: " + globalSettings.environmentSettings.architecture);
    FRLogger.debug("Java: " + globalSettings.environmentSettings.java);
    FRLogger.debug("System Language: " + globalSettings.environmentSettings.systemLanguage);
    FRLogger.debug("Hardware: " + globalSettings.environmentSettings.cpuCores + " CPU cores," + globalSettings.environmentSettings.ram + " MB RAM");
    FRLogger.debug("UTC Time: " + globalSettings.environmentSettings.appStartedAt);

    // parse the command line arguments
    globalSettings.applyCommandLineArguments(args);

    FRLogger.debug("GUI Language: " + globalSettings.currentLocale);

    FRLogger.debug("Host: " + globalSettings.environmentSettings.host);


    // Get some useful information if we are running in a GUI
    int width = 0;
    int height = 0;
    int dpi = 0;
    if (globalSettings.guiSettings.isEnabled)
    {
      try
      {
        // Get default screen device
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        // Get screen resolution
        Dimension screenSize = toolkit.getScreenSize();
        width = screenSize.width;
        height = screenSize.height;

        // Get screen DPI
        dpi = toolkit.getScreenResolution();
        FRLogger.debug("Screen: " + width + "x" + height + ", " + dpi + " DPI");
      } catch (Exception e)
      {
        FRLogger.warn("Couldn't get screen resolution. If you are running in a headless environment, disable the GUI by setting gui.enabled to false.");
        globalSettings.guiSettings.isEnabled = false;
      }
    }

    boolean allowAnalytics = false;

    // initialize analytics
    FRAnalytics.setAccessKey(Constants.FREEROUTING_VERSION, globalSettings.usageAndDiagnosticData.loggerKey);

    // this option allows us to disable analytics for some users (enabled for all if it is set to 1, otherwise it is disabled for every Nth user)
    int analyticsModulo = 1;
    String userIdString = globalSettings.userProfileSettings.userId.length() >= 4 ? globalSettings.userProfileSettings.userId.substring(0, 4) : "0000";
    int userIdValue = Integer.parseInt(userIdString, 16);

    // if the user has disabled analytics, we don't need to check the modulo
    allowAnalytics = !globalSettings.usageAndDiagnosticData.disableAnalytics && (userIdValue % analyticsModulo == 0) && (globalSettings.userProfileSettings.isTelemetryAllowed);

    if (!allowAnalytics)
    {
      FRLogger.debug("Analytics are disabled");
    }
    FRAnalytics.setEnabled(allowAnalytics);
    FRAnalytics.setUserId(globalSettings.userProfileSettings.userId, globalSettings.userProfileSettings.userEmail);
    FRAnalytics.identify();
    try
    {
      Thread.sleep(1000);
    } catch (Exception ignored)
    {
    }
    FRAnalytics.setAppLocation("app.freerouting.gui", "Freerouting");
    FRAnalytics.appStarted(Constants.FREEROUTING_VERSION, Constants.FREEROUTING_BUILD_DATE + " 00:00", String.join(" ", args), System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version"), System.getProperty("java.version"), System.getProperty("java.vendor"), Locale.getDefault(), globalSettings.currentLocale, globalSettings.environmentSettings.cpuCores, globalSettings.environmentSettings.ram, globalSettings.environmentSettings.host, width, height, dpi);

    // check for new version
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    new Thread(checker).start();

    // get localization resources
    TextManager tm = new TextManager(Freerouting.class, globalSettings.currentLocale);

    // check if the user wants to see the help only
    if (globalSettings.show_help_option)
    {
      System.out.print(tm.getText("command_line_help"));
      System.exit(0);
    }

    // Disable GUI and API if in DRC-only mode
    if (globalSettings.drc_report_file != null)
    {
      globalSettings.guiSettings.isEnabled = false;
      globalSettings.apiServerSettings.isEnabled = false;
    }

    // Initialize the API server
    if (globalSettings.apiServerSettings.isEnabled)
    {
      apiServer = InitializeAPI(globalSettings.apiServerSettings);
      globalSettings.apiServerSettings.isEnabled = (apiServer != null);
      globalSettings.apiServerSettings.isRunning = (apiServer != null);
    }

    // Initialize the GUI
    if (globalSettings.guiSettings.isEnabled)
    {
      if (!WindowWelcome.InitializeGUI(globalSettings))
      {
        FRLogger.error("Couldn't initialize the GUI", null);
        globalSettings.guiSettings.isEnabled = false;
      }
      else
      {
        globalSettings.guiSettings.isRunning = true;
      }
    }

    // We both GUI and API are disabled (or failed to start) we are in CLI mode
    if (!globalSettings.guiSettings.isEnabled && !globalSettings.apiServerSettings.isEnabled)
    {
      if ((!globalSettings.routerSettings.enabled) && (globalSettings.drcSettings.enabled))
      {
        InitializeDRC(globalSettings);
      }
      else
      {
        InitializeCLI(globalSettings);
      }
    }

    while (globalSettings.guiSettings.isRunning || globalSettings.apiServerSettings.isRunning)
    {
      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
        break;
      }
    }

    ShutdownApplication();

    FRLogger.traceExit("MainApplication.main()");
    System.exit(0);
  }

  private static void InitializeCLI(GlobalSettings globalSettings)
  {
    if ((globalSettings.design_input_filename == null) || (globalSettings.design_output_filename == null))
    {
      FRLogger.error("Both an input file and an output file must be specified with command line arguments if you are running in CLI mode.", null);
      System.exit(1);
    }

    // Start a new Freerouting session
    var cliSession = SessionManager
        .getInstance()
        .createSession(UUID.fromString(globalSettings.userProfileSettings.userId), "Freerouting/" + globalSettings.version);

    // Create a new routing job
    RoutingJob routingJob = new RoutingJob(cliSession.id);
    try
    {
      routingJob.setInput(globalSettings.design_input_filename);
    } catch (Exception e)
    {
      FRLogger.error("Couldn't load the input file '" + globalSettings.design_input_filename + "'", e);
    }
    cliSession.addJob(routingJob);

    var desiredOutputFile = new File(globalSettings.design_output_filename);
    if ((desiredOutputFile != null) && desiredOutputFile.exists())
    {
      if (!desiredOutputFile.delete())
      {
        FRLogger.warn("Couldn't delete the file '" + globalSettings.design_output_filename + "'");
      }
    }

    routingJob.tryToSetOutputFile(new File(globalSettings.design_output_filename));

    routingJob.routerSettings = Freerouting.globalSettings.routerSettings.clone();
    routingJob.routerSettings.set_stop_pass_no(routingJob.routerSettings.get_start_pass_no() + routingJob.routerSettings.maxPasses - 1);
    routingJob.routerSettings.setLayerCount(routingJob.input.statistics.layers.totalCount);
    routingJob.drcSettings = Freerouting.globalSettings.drcSettings.clone();
    routingJob.state = RoutingJobState.READY_TO_START;

    // Wait for the RoutingJobScheduler to do its work
    while ((routingJob.state != RoutingJobState.COMPLETED) && (routingJob.state != RoutingJobState.TERMINATED))
    {
      try
      {
        Thread.sleep(500);
      } catch (InterruptedException e)
      {
        routingJob.state = RoutingJobState.CANCELLED;
        break;
      }
    }

    // Print the serialized routingJob statistics to the console
    System.out.println(GsonProvider.GSON.toJson(new BoardStatistics(routingJob.board)));

    // Save the output file
    if (routingJob.state == RoutingJobState.COMPLETED)
    {
      try
      {
        Path outputFilePath = Path.of(globalSettings.design_output_filename);
        Files.write(outputFilePath, routingJob.output
            .getData()
            .readAllBytes());
      } catch (IOException e)
      {
        FRLogger.error("Couldn't save the output file '" + globalSettings.design_output_filename + "'", e);
      }
    }
  }

  private static void InitializeDRC(GlobalSettings globalSettings)
  {
    if (globalSettings.design_input_filename == null)
    {
      FRLogger.error("An input file must be specified with -de argument in DRC mode.", null);
      System.exit(1);
    }

    // Start a new Freerouting session
    var drcSession = SessionManager
        .getInstance()
        .createSession(UUID.fromString(globalSettings.userProfileSettings.userId), "Freerouting/" + globalSettings.version);

    // Create a new routing job (but won't route it)
    RoutingJob drcJob = new RoutingJob(drcSession.id);
    drcJob.drc = globalSettings.drc_report_file;
    try
    {
      drcJob.setInput(globalSettings.design_input_filename);
    } catch (Exception e)
    {
      FRLogger.error("Couldn't load the input file '" + globalSettings.design_input_filename + "'", e);
      System.exit(1);
    }

    // Load the board without routing
    if (!app.freerouting.board.BoardLoader.loadBoardIfNeeded(drcJob))
    {
      FRLogger.error("Failed to load board for DRC check", null);
      System.exit(1);
    }

    // Run DRC check
    DesignRulesChecker drcChecker = new DesignRulesChecker(drcJob.board, globalSettings.drcSettings);

    // Determine coordinate unit (default to mm)
    String coordinateUnit = "mm";

    // Generate DRC report
    String sourceFileName = new File(globalSettings.design_input_filename).getName();
    String drcReportJson = drcChecker.generateReportJson(sourceFileName, coordinateUnit);

    // Output the DRC report
    if (drcJob.drc != null)
    {
      String outputFileName = drcJob.drc.getAbsolutePath();
      // Write to file
      try
      {
        Path outputFilePath = Path.of(outputFileName);
        Files.write(outputFilePath, drcReportJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FRLogger.info("DRC report written to: " + outputFileName);
      } catch (IOException e)
      {
        FRLogger.error("Couldn't save the DRC report to '" + outputFileName + "'", e);
        System.exit(1);
      }
    }
    else
    {
      // Print to console
      System.out.println(drcReportJson);
    }
  }

  private static void ShutdownApplication()
  {
    // Stop the API server
    try
    {
      if (apiServer != null)
      {
        apiServer.stop();
      }
    } catch (Exception e)
    {
      FRLogger.error("Error stopping API server", e);
    }

    FRAnalytics.appClosed();
  }

  public static Server InitializeAPI(ApiServerSettings apiServerSettings)
  {
    // Check if there are any endpoints defined
    if (apiServerSettings.endpoints.length == 0)
    {
      FRLogger.warn("Can't start API server, because no endpoints are defined in ApiServerSettings.");
      return null;
    }

    // Start the Jetty server
    Server apiServer = new Server();

    // Add all endpoints as connectors
    for (String endpointUrl : apiServerSettings.endpoints)
    {
      endpointUrl = endpointUrl.toLowerCase();
      String[] endpointParts = endpointUrl.split("://");
      String protocol = endpointParts[0];
      String hostAndPort = endpointParts[1];
      String[] hostAndPortParts = hostAndPort.split(":");
      String host = hostAndPortParts[0];
      int port = Integer.parseInt(hostAndPortParts[1]);

      // Check if the protocol is HTTP or HTTPS
      if (!protocol.equals("http") && !protocol.equals("https"))
      {
        FRLogger.warn("Can't use the endpoint '%s' for the API server, because its protocol is not HTTP or HTTPS.".formatted(endpointUrl));
        continue;
      }

      // Check if the http is allowed
      if (!apiServerSettings.isHttpAllowed && protocol.equals("http"))
      {
        FRLogger.warn("Can't use the endpoint '%s' for the API server, because HTTP is not allowed.".formatted(endpointUrl));
        continue;
      }

      // Warn the user that HTTPS is not implemented yet
      if (protocol.equals("https"))
      {
        FRLogger.warn("HTTPS support is not implemented yet, falling back to HTTP.".formatted(endpointUrl));
      }

      ServerConnector connector = new ServerConnector(apiServer);
      connector.setHost(host);
      connector.setPort(port);
      apiServer.addConnector(connector);
    }

    // Set up the Servlet Context Handler
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    apiServer.setHandler(context);

    // Set up Jersey Servlet that handles the API
    ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/*");
    jerseyServlet.setInitOrder(0);
    jerseyServlet.setInitParameter("jersey.config.server.provider.packages", "app.freerouting.api");

    // Add Listeners
    context.addEventListener(new AppContextListener());

    // Instead of apiServer.join(), start in a new thread
    new Thread(() ->
    {
      try
      {
        apiServer.start();
        apiServer.join(); // This will now run in the new thread
      } catch (Exception e)
      {
        FRLogger.error("Error starting or joining API server", e);
      }
    }).start();

    return apiServer;
  }

}