package app.freerouting;

import app.freerouting.api.AppContextListener;
import app.freerouting.board.BoardLoader;
import app.freerouting.constants.Constants;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.gui.DefaultExceptionHandler;
import app.freerouting.gui.GuiManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.VersionChecker;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.jersey.servlet.ServletContainer;

/* Entry point class of the application */
public class Freerouting {

  public static final String WEB_URL = "https://www.freerouting.app";
  public static final String VERSION_NUMBER_STRING = "v" + Constants.FREEROUTING_VERSION + " (build-date: "
      + Constants.FREEROUTING_BUILD_DATE + ")";
  public static GlobalSettings globalSettings;
  private static Server apiServer; // API server instance

  private static boolean InitializeCLI(GlobalSettings globalSettings) {
    if ((globalSettings.initialInputFile == null) || (globalSettings.initialOutputFile == null)) {
      FRLogger.error(
          "Both an input file and an output file must be specified with command line arguments if you are running in CLI mode.",
          null);
      return false;
    }

    // Start a new Freerouting session
    var cliSession = SessionManager
        .getInstance()
        .createSession(UUID.fromString(globalSettings.userProfileSettings.userId),
            "Freerouting/" + globalSettings.version);

    // Create a new routing job
    RoutingJob routingJob = new RoutingJob(cliSession.id);
    try {
      routingJob.setInput(globalSettings.initialInputFile);
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
    }
    cliSession.addJob(routingJob);

    var desiredOutputFile = new File(globalSettings.initialOutputFile);
    if ((desiredOutputFile != null) && desiredOutputFile.exists()) {
      if (!desiredOutputFile.delete()) {
        FRLogger.warn("Couldn't delete the file '" + globalSettings.initialOutputFile + "'");
      }
    }

    routingJob.tryToSetOutputFile(new File(globalSettings.initialOutputFile));

    routingJob.routerSettings = Freerouting.globalSettings.routerSettings.clone();
    routingJob.routerSettings.setLayerCount(routingJob.input.statistics.layers.totalCount);
    routingJob.drcSettings = Freerouting.globalSettings.drcSettings.clone();
    routingJob.state = RoutingJobState.READY_TO_START;

    // Wait for the RoutingJobScheduler to do its work
    while ((routingJob.state != RoutingJobState.COMPLETED) && (routingJob.state != RoutingJobState.TERMINATED)) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException _) {
        routingJob.state = RoutingJobState.CANCELLED;
        break;
      }
    }

    // Save the output file
    if (routingJob.state == RoutingJobState.COMPLETED) {
      try {
        Path outputFilePath = Path.of(globalSettings.initialOutputFile);
        Files.write(outputFilePath, routingJob.output
            .getData()
            .readAllBytes());
      } catch (IOException e) {
        FRLogger.error("Couldn't save the output file '" + globalSettings.initialOutputFile + "'", e);
      }
    }

    return true;
  }

  private static boolean InitializeDRC(GlobalSettings globalSettings) {
    if (globalSettings.initialInputFile == null) {
      FRLogger.error("An input file must be specified with -de argument in DRC mode.", null);
      return false;
    }

    // Start a new Freerouting session
    var drcSession = SessionManager
        .getInstance()
        .createSession(UUID.fromString(globalSettings.userProfileSettings.userId),
            "Freerouting/" + globalSettings.version);

    // Create a new routing job (but won't route it)
    RoutingJob drcJob = new RoutingJob(drcSession.id);
    drcJob.drc = globalSettings.drc_report_file;
    try {
      drcJob.setInput(globalSettings.initialInputFile);
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
      System.exit(1);
    }

    // Load the board without routing
    if (!BoardLoader.loadBoardIfNeeded(drcJob)) {
      FRLogger.error("Failed to load board for DRC check", null);
      System.exit(1);
    }

    // Run DRC check
    DesignRulesChecker drcChecker = new DesignRulesChecker(drcJob.board, globalSettings.drcSettings);

    // Determine coordinate unit (default to mm)
    String coordinateUnit = "mm";

    // Generate DRC report
    String sourceFileName = new File(globalSettings.initialInputFile).getName();
    String drcReportJson = drcChecker.generateReportJson(sourceFileName, coordinateUnit);

    // Output the DRC report
    if (drcJob.drc != null) {
      String outputFileName = drcJob.drc.getAbsolutePath();
      // Write to file
      try {
        Path outputFilePath = Path.of(outputFileName);
        Files.write(outputFilePath, drcReportJson.getBytes(StandardCharsets.UTF_8));
        FRLogger.info("DRC report written to: " + outputFileName);
      } catch (IOException e) {
        FRLogger.error("Couldn't save the DRC report to '" + outputFileName + "'", e);
        System.exit(1);
      }
    } else {
      // Print to console
      IO.println(drcReportJson);
    }

    return true;
  }

  private static void ShutdownApplication() {
    // Stop the API server
    try {
      if (apiServer != null) {
        apiServer.stop();
      }
    } catch (Exception e) {
      FRLogger.error("Error stopping API server", e);
    }

    FRAnalytics.appClosed();
  }

  public static Server InitializeAPI(ApiServerSettings apiServerSettings) {
    // Check if there are any endpoints defined
    if (apiServerSettings.endpoints.length == 0) {
      FRLogger.warn("Can't start API server, because no endpoints are defined in ApiServerSettings.");
      return null;
    }

    // Start the Jetty server
    Server apiServer = new Server();

    // Add all endpoints as connectors
    for (String endpointUrl : apiServerSettings.endpoints) {
      endpointUrl = endpointUrl.toLowerCase();
      String[] endpointParts = endpointUrl.split("://");
      String protocol = endpointParts[0];
      String hostAndPort = endpointParts[1];
      String[] hostAndPortParts = hostAndPort.split(":");
      String host = hostAndPortParts[0];
      int port = Integer.parseInt(hostAndPortParts[1]);

      // Check if the protocol is HTTP or HTTPS
      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        FRLogger.warn("Can't use the endpoint '%s' for the API server, because its protocol is not HTTP or HTTPS."
            .formatted(endpointUrl));
        continue;
      }

      // Check if the http is allowed
      if (!apiServerSettings.isHttpAllowed && "http".equals(protocol)) {
        FRLogger.warn(
            "Can't use the endpoint '%s' for the API server, because HTTP is not allowed.".formatted(endpointUrl));
        continue;
      }

      // Warn the user that HTTPS is not implemented yet
      if ("https".equals(protocol)) {
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
    new Thread(() -> {
      try {
        apiServer.start();
        apiServer.join(); // This will now run in the new thread
      } catch (Exception e) {
        FRLogger.error("Error starting or joining API server", e);
        if (globalSettings != null) {
          globalSettings.apiServerSettings.isRunning = false;
        }
      }
    }).start();

    return apiServer;
  }

  private static Path resolveLogPath(String input, Path defaultDir) {
    if (input == null || input.isBlank()) {
      return defaultDir.resolve("freerouting.log").normalize().toAbsolutePath();
    }

    // In Windows the leading "." character means current directory
    if (input.startsWith(".")) {
      var currentDir = Path.of(System.getProperty("user.dir"));
      input = currentDir + input.substring(1);
    }

    Path path = Path.of(input).normalize().toAbsolutePath();
    boolean isFile = path.getFileName().toString().toLowerCase().endsWith(".log");
    String filename = isFile ? path.getFileName().toString() : "freerouting.log";
    Path folderPath = isFile ? path.getParent() : path;

    // Check if the directory exists, and create it if needed
    if (folderPath != null && !folderPath.toFile().exists()) {
      try {
        Files.createDirectories(folderPath);
      } catch (IOException e) {
        // Failed to create directory, fallback to default
        return defaultDir.resolve(filename).normalize().toAbsolutePath();
      }
    }

    return folderPath.resolve(filename).normalize().toAbsolutePath();
  }

  /**
   * The entry point of the Freerouting application
   *
   * @param args
   */
  void main(String[] args) {
    // CRITICAL: Set up logging configuration BEFORE any logging occurs
    // This must happen before FRLogger.traceEntry() or any other logging call

    // the first thing we need to do is to determine the user directory, because all
    // settings and logs will be located there
    // 1, set it to the temp directory by default
    Path userdataPath = Path.of(System.getProperty("java.io.tmpdir"), "freerouting");
    // 2, check if we need to override it with the "FREEROUTING__USER_DATA_PATH"
    // environment variable value
    if (System.getenv("FREEROUTING__USER_DATA_PATH") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__USER_DATA_PATH"));
    } else if (System.getenv("FREEROUTING__LOGGING__FILE__LOCATION") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__LOGGING__FILE__LOCATION"));
    }
    // 3, check if we need to override it with the "--user_data_path={directory}"
    // command line argument
    if (args.length > 0 && Arrays
        .stream(args)
        .anyMatch(s -> s.startsWith("--user_data_path="))) {
      var userDataPathArg = Arrays
          .stream(args)
          .filter(s -> s.startsWith("--user_data_path="))
          .findFirst();

      if (userDataPathArg.isPresent()) {
        userdataPath = Path.of(userDataPathArg
            .get()
            .substring("--user_data_path=".length()));
      }
    }
    // 4, create the directory if it doesn't exist
    if (!userdataPath
        .toFile()
        .exists()) {
      userdataPath
          .toFile()
          .mkdirs();
    }
    // 5, check if it exists now, and if it does, apply it to GlobalSettings
    if (userdataPath
        .toFile()
        .exists()) {
      GlobalSettings.setUserDataPath(userdataPath);
    }
    // 6, make sure that this settings can't be changed later on
    GlobalSettings.lockUserDataPath();

    // Parse logging settings from environment variables and command line arguments
    // These will be used to configure log4j2 BEFORE it initializes
    boolean fileLoggingEnabled = true;
    boolean consoleLoggingEnabled = true;
    String fileLoggingLevel = "DEBUG";
    String consoleLoggingLevel = "INFO";
    String fileLoggingLocation = null;

    if (System.getenv("FREEROUTING__LOGGING__FILE__ENABLED") != null) {
      fileLoggingEnabled = Boolean.parseBoolean(System.getenv("FREEROUTING__LOGGING__FILE__ENABLED"));
    }
    if (System.getenv("FREEROUTING__LOGGING__CONSOLE__ENABLED") != null) {
      consoleLoggingEnabled = Boolean.parseBoolean(System.getenv("FREEROUTING__LOGGING__CONSOLE__ENABLED"));
    }
    if (System.getenv("FREEROUTING__LOGGING__FILE__LEVEL") != null) {
      fileLoggingLevel = System.getenv("FREEROUTING__LOGGING__FILE__LEVEL");
    }
    if (System.getenv("FREEROUTING__LOGGING__CONSOLE__LEVEL") != null) {
      consoleLoggingLevel = System.getenv("FREEROUTING__LOGGING__CONSOLE__LEVEL");
    }
    if (System.getenv("FREEROUTING__LOGGING__FILE__LOCATION") != null) {
      fileLoggingLocation = System.getenv("FREEROUTING__LOGGING__FILE__LOCATION");
    }

    if (args.length > 0) {
      for (String arg : args) {
        if (arg.startsWith("--logging.file.enabled=")) {
          fileLoggingEnabled = Boolean.parseBoolean(arg.substring("--logging.file.enabled=".length()));
        } else if (arg.startsWith("--logging.console.enabled=")) {
          consoleLoggingEnabled = Boolean.parseBoolean(arg.substring("--logging.console.enabled=".length()));
        } else if (arg.startsWith("--logging.file.level=")) {
          fileLoggingLevel = arg.substring("--logging.file.level=".length());
        } else if (arg.startsWith("--logging.console.level=")) {
          consoleLoggingLevel = arg.substring("--logging.console.level=".length());
        } else if (arg.startsWith("--logging.file.location=")) {
          fileLoggingLocation = arg.substring("--logging.file.location=".length());
        } else if ("-dl".equals(arg)) {
          fileLoggingEnabled = false;
        } else if ("-ll".equals(arg)) {
          // simple peek for -ll
          int index = Arrays.asList(args).indexOf("-ll");
          if (index >= 0 && index < args.length - 1) {
            consoleLoggingLevel = args[index + 1];
          }
        }
      }
    }

    // Resolve the log file location
    if (fileLoggingLocation == null || fileLoggingLocation.isBlank()) {
      fileLoggingLocation = resolveLogPath(null, userdataPath).toString();
    } else {
      fileLoggingLocation = resolveLogPath(fileLoggingLocation, userdataPath).toString();
    }

    // Set system properties for log4j2 ConfigurationFactory to read
    // This MUST happen before any logging calls
    System.setProperty("log4j2.configurationFactory", "app.freerouting.logger.Log4j2ConfigurationFactory");
    System.setProperty("freerouting.logging.console.enabled", String.valueOf(consoleLoggingEnabled));
    System.setProperty("freerouting.logging.console.level", consoleLoggingLevel);
    System.setProperty("freerouting.logging.file.enabled", String.valueOf(fileLoggingEnabled));
    System.setProperty("freerouting.logging.file.level", fileLoggingLevel);
    System.setProperty("freerouting.logging.file.location", fileLoggingLocation);

    // FORCE RECONFIGURATION
    // Log4j2 might have initialized early (before we set these properties).
    // We force it to reload the configuration using our Factory, which will now see
    // the correct properties.
    ((LoggerContext) LogManager.getContext(false)).reconfigure();

    // NOW we can start logging - log4j2 will initialize with our configuration
    FRLogger.traceEntry("MainApplication.main()");

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException
        | IllegalAccessException ex) {
      FRLogger.error(ex.getLocalizedMessage(), ex);
    }

    // Log system information
    FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);
    Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

    try {
      globalSettings = GlobalSettings.load();
      FRLogger.debug("Settings were loaded from freerouting.json");
    } catch (Exception _) {
      // we don't want to stop if the configuration file doesn't exist
    }

    if ((globalSettings == null) || (globalSettings.version != Constants.FREEROUTING_VERSION)) {
      // let's see if we can preserve the user ID
      String userId = globalSettings == null ? UUID.randomUUID().toString() : globalSettings.userProfileSettings.userId;

      globalSettings = new GlobalSettings();
      globalSettings.userProfileSettings.userId = userId;
      globalSettings.version = Constants.FREEROUTING_VERSION;

      // save the default values
      try {
        GlobalSettings.saveAsJson(globalSettings);
      } catch (Exception _) {
        // it's ok if we can't save the configuration file
      }
    }

    // apply environment variables to the settings
    globalSettings.applyNonRouterEnvironmentVariables();

    // Note: Logging is already configured via system properties set earlier
    // No need to call ApplyLoggingSettings() - it would cause runtime manipulation
    // errors

    // if we don't have a GUI enabled then we must use the console as our output
    if ((!globalSettings.guiSettings.isEnabled) && (System.console() == null)) {
      FRLogger.warn(
          "GUI is disabled and you don't have a console available, so the only feedback from Freerouting is in the log.");
    }

    // get environment parameters and save them in the settings
    globalSettings.runtimeEnvironment.freeroutingVersion = Constants.FREEROUTING_VERSION + ","
        + Constants.FREEROUTING_BUILD_DATE;
    globalSettings.runtimeEnvironment.appStartedAt = Instant.now();
    globalSettings.runtimeEnvironment.commandLineArguments = String.join(" ", args);
    globalSettings.runtimeEnvironment.architecture = System.getProperty("os.name") + ","
        + System.getProperty("os.arch") + "," + System.getProperty("os.version");
    globalSettings.runtimeEnvironment.java = System.getProperty("java.version") + ","
        + System.getProperty("java.vendor");
    globalSettings.runtimeEnvironment.systemLanguage = Locale
        .getDefault()
        .getLanguage() + "," + Locale.getDefault();
    globalSettings.runtimeEnvironment.cpuCores = Runtime
        .getRuntime()
        .availableProcessors();
    globalSettings.runtimeEnvironment.ram = (int) (Runtime
        .getRuntime()
        .maxMemory() / 1024 / 1024);
    FRLogger.debug("Version: " + globalSettings.runtimeEnvironment.freeroutingVersion);
    FRLogger.debug("Command line arguments: '" + globalSettings.runtimeEnvironment.commandLineArguments + "'");
    FRLogger.debug("Architecture: " + globalSettings.runtimeEnvironment.architecture);
    FRLogger.debug("Java: " + globalSettings.runtimeEnvironment.java);
    FRLogger.debug("System Language: " + globalSettings.runtimeEnvironment.systemLanguage);
    FRLogger.debug("Hardware: " + globalSettings.runtimeEnvironment.cpuCores + " CPU cores,"
        + globalSettings.runtimeEnvironment.ram + " MB RAM");
    FRLogger.debug("UTC Time: " + globalSettings.runtimeEnvironment.appStartedAt);

    // parse the command line arguments
    globalSettings.applyCommandLineArguments(args);

    FRLogger.debug("GUI Language: " + globalSettings.currentLocale);

    FRLogger.debug("Host: " + globalSettings.runtimeEnvironment.host);

    // Get some useful information if we are running in a GUI
    int width = 0;
    int height = 0;
    int dpi = 0;
    if (globalSettings.guiSettings.isEnabled) {
      try {
        // Get default screen device
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        // Get screen resolution
        Dimension screenSize = toolkit.getScreenSize();
        width = screenSize.width;
        height = screenSize.height;

        // Get screen DPI
        dpi = toolkit.getScreenResolution();
        FRLogger.debug("Screen: " + width + "x" + height + ", " + dpi + " DPI");
      } catch (Exception _) {
        FRLogger.warn(
            "Couldn't get screen resolution. If you are running in a headless environment, disable the GUI by setting gui.enabled to false.");
        globalSettings.guiSettings.isEnabled = false;
      }
    }

    boolean allowAnalytics = false;

    // initialize analytics
    FRAnalytics.setAccessKey(Constants.FREEROUTING_VERSION, globalSettings.usageAndDiagnosticData.loggerKey);

    // this option allows us to disable analytics for some users (enabled for all if
    // it is set to 1, otherwise it is disabled for every Nth user)
    int analyticsModulo = 1;
    String userIdString = globalSettings.userProfileSettings.userId.length() >= 4
        ? globalSettings.userProfileSettings.userId.substring(0, 4)
        : "0000";
    int userIdValue = Integer.parseInt(userIdString, 16);

    // if the user has disabled analytics, we don't need to check the modulo
    allowAnalytics = !globalSettings.usageAndDiagnosticData.disableAnalytics && (userIdValue % analyticsModulo == 0)
        && (globalSettings.userProfileSettings.isTelemetryAllowed);

    if (!allowAnalytics) {
      FRLogger.debug("Analytics are disabled");
    }
    FRAnalytics.setEnabled(allowAnalytics);
    FRAnalytics.setUserId(globalSettings.userProfileSettings.userId, globalSettings.userProfileSettings.userEmail);
    FRAnalytics.identify();
    try {
      Thread.sleep(1000);
    } catch (Exception _) {
    }
    FRAnalytics.setAppLocation("app.freerouting.gui", "Freerouting");
    FRAnalytics.appStarted(Constants.FREEROUTING_VERSION, Constants.FREEROUTING_BUILD_DATE + " 00:00",
        String.join(" ", args), System.getProperty("os.name"), System.getProperty("os.arch"),
        System.getProperty("os.version"), System.getProperty("java.version"), System.getProperty("java.vendor"),
        Locale.getDefault(), globalSettings.currentLocale,
        globalSettings.runtimeEnvironment.cpuCores, globalSettings.runtimeEnvironment.ram,
        globalSettings.runtimeEnvironment.host, width, height, dpi);

    // check for new version
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    new Thread(checker).start();

    // get localization resources

    // check if the user wants to see the help only
    if (globalSettings.show_help_option) {
      TextManager ctm = new TextManager(Freerouting.class, globalSettings.currentLocale);
      IO.print(ctm.getText("command_line_help"));
      System.exit(0);
    }

    // Disable GUI and API if in DRC-only mode
    if (globalSettings.drc_report_file != null) {
      globalSettings.guiSettings.isEnabled = false;
      globalSettings.apiServerSettings.isEnabled = false;
    }

    // Initialize the API server
    if (globalSettings.apiServerSettings.isEnabled) {
      apiServer = InitializeAPI(globalSettings.apiServerSettings);
      globalSettings.apiServerSettings.isEnabled = apiServer != null;
      globalSettings.apiServerSettings.isRunning = apiServer != null;
    }

    // Initialize the GUI
    if (globalSettings.guiSettings.isEnabled) {
      if (!GuiManager.InitializeGUI(globalSettings)) {
        FRLogger.error("Couldn't initialize the GUI", null);
        globalSettings.guiSettings.isEnabled = false;
      } else {
        globalSettings.guiSettings.isRunning = true;
      }
    }

    // If the GUI is disabled then we are in CLI mode
    boolean cliResult = true;
    if (!globalSettings.guiSettings.isEnabled) {
      if ((!globalSettings.routerSettings.enabled) && (globalSettings.drcSettings.enabled)) {
        cliResult = InitializeDRC(globalSettings);
      } else {
        cliResult = InitializeCLI(globalSettings);
      }
    }

    if ((!cliResult) && !globalSettings.apiServerSettings.isEnabled) {
      ShutdownApplication();
      FRLogger.traceExit("MainApplication.main()");
      System.exit(1);
    }

    while (globalSettings.guiSettings.isRunning || globalSettings.apiServerSettings.isRunning) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException _) {
        break;
      }
    }

    ShutdownApplication();

    FRLogger.traceExit("MainApplication.main()");
    System.exit(0);
  }

}