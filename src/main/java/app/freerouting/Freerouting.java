package app.freerouting;

import app.freerouting.api.AppContextListener;
import app.freerouting.api.mcp.McpApplication;
import app.freerouting.api.mcp.McpContextListener;
import app.freerouting.api.mcp.McpWebSocketEndpoint;
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
import app.freerouting.settings.McpServerSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import app.freerouting.settings.sources.JsonFileSettings;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.CrossOriginHandler;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.glassfish.jersey.servlet.ServletContainer;

/* Entry point class of the application */
public class Freerouting {

  public static final String WEB_URL = "https://www.freerouting.app";
  public static final String VERSION_NUMBER_STRING = "v" + Constants.FREEROUTING_VERSION + " (build-date: "
      + Constants.FREEROUTING_BUILD_DATE + ")";
  public static GlobalSettings globalSettings;
  private static Server apiServer; // API server instance
  private static Server mcpServer; // MCP server instance

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

    // Load the input file
    DsnFileSettings inputFileSettings = null;
    try {
      routingJob.setInput(globalSettings.initialInputFile);
      inputFileSettings = new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename());
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
    }

    if (routingJob.input == null) {
      FRLogger.warn("Couldn't read the input file '" + globalSettings.initialInputFile + "', aborting.");
      return false;
    }

    cliSession.addJob(routingJob);

    var desiredOutputFile = new File(globalSettings.initialOutputFile);
    if ((desiredOutputFile != null) && desiredOutputFile.exists()) {
      if (!desiredOutputFile.delete()) {
        FRLogger.warn("Couldn't delete the file '" + globalSettings.initialOutputFile + "'");
      }
    }

    routingJob.tryToSetOutputFile(new File(globalSettings.initialOutputFile));

    var settingsMerger = globalSettings.settingsMergerProtype.clone();
    settingsMerger.addOrReplaceSources(
        new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename()));

    routingJob.routerSettings = settingsMerger.merge();
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

      // Print a sponsor/success-story message to stdout (not the log) once the
      // condition is met: ≥5 completed jobs and the user has not yet saved their email
      if ((globalSettings.statistics.jobsCompleted >= 5)
          && globalSettings.userProfileSettings.userEmail.isEmpty()) {
        String nl = System.lineSeparator();
        System.out.println(
            nl
            + "╔══════════════════════════════════════════════════════════════════╗" + nl
            + "║           Thank you for using Freerouting!                       ║" + nl
            + "║                                                                  ║" + nl
            + "║  If you would like to support the project, please consider       ║" + nl
            + "║  sponsoring me at https://github.com/sponsors/andrasfuchs        ║" + nl
            + "║  Even a small monthly donation is greatly appreciated!           ║" + nl
            + "║                                                                  ║" + nl
            + "║  You can also fuel my passion by sharing your success stories    ║" + nl
            + "║  with Freerouting — send them to info@freerouting.app            ║" + nl
            + "║  I would love to read every one of them!                         ║" + nl
            + "╚══════════════════════════════════════════════════════════════════╝"
        );
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
      if (mcpServer != null) {
        mcpServer.stop();
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

    Handler apiHandler = context;

    // Configure CORS if origins are provided
    if (apiServerSettings.cors_origins != null && !apiServerSettings.cors_origins.equals("")) {
      String allowedOrigins = apiServerSettings.cors_origins;

      CrossOriginHandler corsHandler = new CrossOriginHandler();
      corsHandler.setAllowCredentials(true);
      corsHandler.setAllowedOriginPatterns(splitCommaSeparated(allowedOrigins));
      corsHandler.setAllowedMethods(Set.of("HEAD", "GET", "POST", "PUT", "DELETE", "OPTIONS"));
      corsHandler.setAllowedHeaders(Set.of(
          "X-Requested-With",
          "Content-Type",
          "Accept",
          "Origin",
          "Authorization",
          "Freerouting-Profile-ID",
          "Freerouting-Profile-Email",
          "Freerouting-Environment-Host"));
      corsHandler.setHandler(context);

      PathMappingsHandler pathMappingsHandler = new PathMappingsHandler();
      pathMappingsHandler.addMapping(new ServletPathSpec("/v1/*"), corsHandler);
      pathMappingsHandler.addMapping(new ServletPathSpec("/*"), context);
      apiHandler = pathMappingsHandler;

      FRLogger.info("CORS configured for origins: " + allowedOrigins);
    }

    apiServer.setHandler(apiHandler);

    // Set up the Jersey Servlet that handles the API
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

  public static Server InitializeMCP(McpServerSettings mcpServerSettings) {
    if (mcpServerSettings.endpoints.length == 0) {
      FRLogger.warn("Can't start MCP server, because no endpoints are defined in McpServerSettings.");
      return null;
    }

    Server mcpServer = new Server();

    for (String endpointUrl : mcpServerSettings.endpoints) {
      endpointUrl = endpointUrl.toLowerCase();
      String[] endpointParts = endpointUrl.split("://");
      String protocol = endpointParts[0];
      String hostAndPort = endpointParts[1];
      String[] hostAndPortParts = hostAndPort.split(":");
      String host = hostAndPortParts[0];
      int port = Integer.parseInt(hostAndPortParts[1]);

      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        FRLogger.warn("Can't use the endpoint '%s' for the MCP server, because its protocol is not HTTP or HTTPS."
            .formatted(endpointUrl));
        continue;
      }

      if (!mcpServerSettings.isHttpAllowed && "http".equals(protocol)) {
        FRLogger.warn(
            "Can't use the endpoint '%s' for the MCP server, because HTTP is not allowed.".formatted(endpointUrl));
        continue;
      }

      if ("https".equals(protocol)) {
        FRLogger.warn("HTTPS support is not implemented yet, falling back to HTTP.".formatted(endpointUrl));
      }

      ServerConnector connector = new ServerConnector(mcpServer);
      connector.setHost(host);
      connector.setPort(port);
      mcpServer.addConnector(connector);
    }

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    Handler mcpHandler = context;

    if (mcpServerSettings.cors_origins != null && !mcpServerSettings.cors_origins.equals("")) {
      String allowedOrigins = mcpServerSettings.cors_origins;

      CrossOriginHandler corsHandler = new CrossOriginHandler();
      corsHandler.setAllowCredentials(true);
      corsHandler.setAllowedOriginPatterns(splitCommaSeparated(allowedOrigins));
      corsHandler.setAllowedMethods(Set.of("HEAD", "GET", "POST", "PUT", "DELETE", "OPTIONS"));
      corsHandler.setAllowedHeaders(Set.of(
          "X-Requested-With",
          "Content-Type",
          "Accept",
          "Origin",
          "Authorization",
          "Freerouting-Profile-ID",
          "Freerouting-Profile-Email",
          "Freerouting-Environment-Host"));
      corsHandler.setHandler(context);

      PathMappingsHandler pathMappingsHandler = new PathMappingsHandler();
      pathMappingsHandler.addMapping(new ServletPathSpec("/v1/mcp/*"), corsHandler);
      pathMappingsHandler.addMapping(new ServletPathSpec("/*"), context);
      mcpHandler = pathMappingsHandler;

      FRLogger.info("MCP CORS configured for origins: " + allowedOrigins);
    }

    mcpServer.setHandler(mcpHandler);

    ServletHolder jerseyServlet = context.addServlet(ServletContainer.class, "/*");
    jerseyServlet.setInitOrder(0);
    jerseyServlet.setInitParameter("jakarta.ws.rs.Application", McpApplication.class.getName());

    context.addEventListener(new McpContextListener());

    JakartaWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
      wsContainer.addEndpoint(McpWebSocketEndpoint.class);
    });

    new Thread(() -> {
      try {
        mcpServer.start();
        mcpServer.join();
      } catch (Exception e) {
        FRLogger.error("Error starting or joining MCP server", e);
        if (globalSettings != null) {
          globalSettings.mcpServerSettings.isRunning = false;
        }
      }
    }).start();

    return mcpServer;
  }

  private static Set<String> splitCommaSeparated(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .collect(Collectors.toSet());
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
    String userdataPathSource = "default (java.io.tmpdir)";
    // 2, check if we need to override it with the "FREEROUTING__USER_DATA_PATH"
    // environment variable value
    if (System.getenv("FREEROUTING__USER_DATA_PATH") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__USER_DATA_PATH"));
      userdataPathSource = "environment variable FREEROUTING__USER_DATA_PATH";
    } else if (System.getenv("FREEROUTING__LOGGING__FILE__LOCATION") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__LOGGING__FILE__LOCATION"));
      userdataPathSource = "environment variable FREEROUTING__LOGGING__FILE__LOCATION (deprecated fallback)";
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
        userdataPathSource = "CLI argument --user_data_path";
      }
    }
    // 4, create the directory if it doesn't exist yet; directory creation is also
    // attempted lazily when the first write happens (in saveAsJson), so a failure
    // here is non-fatal – but we print a warning to stderr because logging is not
    // initialised at this point.
    if (!userdataPath.toFile().exists()) {
      if (!userdataPath.toFile().mkdirs()) {
        System.err.println("WARNING: Could not create user-data directory '" + userdataPath
            + "' (source: " + userdataPathSource + "). "
            + "Freerouting will attempt to create it when writing files. "
            + "If this persists, check permissions for the specified path.");
      }
    } else {
      // Directory exists — proactively check read and write permissions so that
      // permission problems are surfaced immediately rather than at first I/O.
      if (!userdataPath.toFile().canRead()) {
        System.err.println("WARNING: User-data directory '" + userdataPath
            + "' (source: " + userdataPathSource + ") exists but is NOT READABLE. "
            + "freerouting.json cannot be loaded. "
            + "Check that the process has read permission on the directory. "
            + "In Docker deployments, verify the volume mount and file ownership.");
      }
      if (!userdataPath.toFile().canWrite()) {
        System.err.println("WARNING: User-data directory '" + userdataPath
            + "' (source: " + userdataPathSource + ") exists but is NOT WRITABLE. "
            + "freerouting.json cannot be saved and settings won't be persisted. "
            + "Check that the process has write permission on the directory. "
            + "In Docker deployments, verify the volume mount and file ownership.");
      }
    }
    // capture for later use once logging is available
    final String resolvedUserdataPathSource = userdataPathSource;
    // 5, always register the resolved path with GlobalSettings – even when the
    // directory could not be created yet.  saveAsJson() calls
    // Files.createDirectories() before each write, so the directory will be
    // created on demand.  Prior to this fix the path was silently ignored (and
    // the default temp-dir path was locked in) whenever mkdirs() returned false.
    GlobalSettings.setUserDataPath(userdataPath);
    // 6, make sure that this setting can't be changed later on
    GlobalSettings.lockUserDataPath();

    // Parse logging settings from environment variables and command line arguments
    // These will be used to configure log4j2 BEFORE it initializes
    boolean fileLoggingEnabled = true;
    boolean consoleLoggingEnabled = true;
    String fileLoggingLevel = "DEBUG";
    String consoleLoggingLevel = "INFO";
    String fileLoggingLocation = null;
    String fileLoggingPattern = null;

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
    if (System.getenv("FREEROUTING__LOGGING__FILE__PATTERN") != null) {
      fileLoggingPattern = System.getenv("FREEROUTING__LOGGING__FILE__PATTERN");
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
        } else if (arg.startsWith("--logging.file.pattern=")) {
          fileLoggingPattern = arg.substring("--logging.file.pattern=".length());
        } else if (arg.startsWith("--debug.enable_detailed_logging=")) {
          boolean detailed = Boolean.parseBoolean(arg.substring("--debug.enable_detailed_logging=".length()));
          if (detailed) {
            fileLoggingLevel = "TRACE";
            FRLogger.granularTraceEnabled = true;
          }
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

    // Disable JNDI lookups — Freerouting does not use them, and the java.naming module
    // is intentionally excluded from the jlink runtime to reduce attack surface
    // (Log4Shell / CVE-2021-44228). Without this property Log4j2 tries to load
    // javax.naming.Context at bootstrap and emits a noisy WARN for every lookup plugin.
    System.setProperty("log4j2.disableJndi", "true");

    System.setProperty("log4j2.configurationFactory", "app.freerouting.logger.Log4j2ConfigurationFactory");
    System.setProperty("freerouting.logging.console.enabled", String.valueOf(consoleLoggingEnabled));
    System.setProperty("freerouting.logging.console.level", consoleLoggingLevel);
    System.setProperty("freerouting.logging.file.enabled", String.valueOf(fileLoggingEnabled));
    System.setProperty("freerouting.logging.file.level", fileLoggingLevel);
    System.setProperty("freerouting.logging.file.location", fileLoggingLocation);

    if (fileLoggingPattern != null) {
      System.setProperty("freerouting.logging.file.pattern", fileLoggingPattern);
    }

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
    FRLogger.debug("[startup] user-data path : " + GlobalSettings.getUserDataPath() + "  (source: " + resolvedUserdataPathSource + ")");
    FRLogger.debug("[startup] log file       : " + fileLoggingLocation);
    Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

    try {
      globalSettings = GlobalSettings.load();
      FRLogger.debug("Settings loaded from '" + GlobalSettings.getConfigurationFilePath() + "'.");
    } catch (NoSuchFileException _) {
      // Normal first-run condition — the file does not exist yet and will be
      // created below with default values.
      FRLogger.debug("No freerouting.json found at '" + GlobalSettings.getConfigurationFilePath()
          + "' — will create one with default settings.");
    } catch (AccessDeniedException e) {
      FRLogger.warn("Cannot read freerouting.json at '"
          + GlobalSettings.getConfigurationFilePath() + "': " + e.getReason()
          + ". The file and/or its parent directory may have incorrect permissions. "
          + "Check that the process has read access. "
          + "In Docker deployments, verify the volume mount configuration. "
          + "Freerouting will start with default settings.");
    } catch (IOException e) {
      FRLogger.warn("Failed to load freerouting.json from '"
          + GlobalSettings.getConfigurationFilePath() + "': " + e.getMessage()
          + ". Freerouting will start with default settings.");
    }

    // Detect stale logging.file.location in the loaded JSON.
    // Old versions of Freerouting stored the absolute log path in freerouting.json and
    // applied it at startup, potentially redirecting the log away from the volume mount.
    // The current code does NOT re-apply the stored path (logging is already configured
    // above via system properties), but we warn if we detect a mismatch so operators
    // can spot a stale config.
    if (globalSettings != null
        && globalSettings.logging.file.location != null
        && !globalSettings.logging.file.location.isBlank()
        && !globalSettings.logging.file.location.equals(fileLoggingLocation)) {
      FRLogger.warn("[startup] freerouting.json contains a stale 'logging.file.location' value: '"
          + globalSettings.logging.file.location + "'. "
          + "The actual log file is being written to '" + fileLoggingLocation + "' (as resolved at startup). "
          + "The stale value will be corrected in freerouting.json on next save. "
          + "If you see this in Docker, the old JSON was written by an earlier version that stored "
          + "the host path; the fix is to delete freerouting.json so it is regenerated with the correct path.");
    }
    // Always keep the stored log path in sync with the resolved path so the JSON
    // self-heals and old images that do apply the stored path will get the right value.
    if (globalSettings != null) {
      globalSettings.logging.file.location = fileLoggingLocation;
    }

    if ((globalSettings == null) || !GlobalSettings.getReleaseSafeVersion().equals(globalSettings.version)) {
      // let's see if we can preserve the user ID
      String userId = globalSettings == null ? UUID.randomUUID().toString() : globalSettings.userProfileSettings.userId;

      globalSettings = new GlobalSettings();
      globalSettings.userProfileSettings.userId = userId;
      globalSettings.version = GlobalSettings.getReleaseSafeVersion();
      // Stamp the correct log path into the new settings object too.
      globalSettings.logging.file.location = fileLoggingLocation;

      // save the default values
      try {
        GlobalSettings.saveAsJson(globalSettings);
        FRLogger.debug("Default settings saved to '" + GlobalSettings.getConfigurationFilePath() + "'.");
      } catch (AccessDeniedException e) {
        FRLogger.warn("Cannot write freerouting.json to '"
            + GlobalSettings.getConfigurationFilePath() + "': " + e.getReason()
            + ". The directory and/or file may have incorrect permissions. "
            + "Check that the process has write access. "
            + "In Docker deployments, verify the volume mount configuration. "
            + "Settings won't be persisted across restarts.");
      } catch (IOException e) {
        FRLogger.warn("Failed to save freerouting.json to '"
            + GlobalSettings.getConfigurationFilePath() + "': " + e.getMessage()
            + ". Settings won't be persisted across restarts.");
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

    // parse the command line arguments (for the non-router settings)
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
    allowAnalytics = !globalSettings.usageAndDiagnosticData.disableAnalytics && (globalSettings.userProfileSettings.isTelemetryAllowed);

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

    // Check if the user requested help
    if (globalSettings.show_help_option) {
      TextManager ctm = new TextManager(Freerouting.class, globalSettings.currentLocale);
      IO.print(ctm.getText("command_line_help"));
      System.exit(0);
    }

    // Disable GUI and API if in DRC-only mode
    if (globalSettings.drc_report_file != null) {
      globalSettings.guiSettings.isEnabled = false;
      globalSettings.apiServerSettings.isEnabled = false;
      globalSettings.mcpServerSettings.isEnabled = false;
    }

    // Create the settings merger prototype based on the sources that will not change at runtime
    globalSettings.settingsMergerProtype = new SettingsMerger(
        new DefaultSettings(),
        new JsonFileSettings(),
        new CliSettings(args),
        new EnvironmentVariablesSource());

    // Initialize the API server
    if (globalSettings.apiServerSettings.isEnabled) {
      apiServer = InitializeAPI(globalSettings.apiServerSettings);
      globalSettings.apiServerSettings.isEnabled = apiServer != null;
      globalSettings.apiServerSettings.isRunning = apiServer != null;

      if (apiServer != null
          && (globalSettings.mcpServerSettings.targetApiBaseUrl == null
          || globalSettings.mcpServerSettings.targetApiBaseUrl.isBlank()
          || "http://127.0.0.1:37864".equals(globalSettings.mcpServerSettings.targetApiBaseUrl))) {
        if (apiServer.getConnectors().length > 0 && apiServer.getConnectors()[0] instanceof ServerConnector connector) {
          globalSettings.mcpServerSettings.targetApiBaseUrl = "http://127.0.0.1:" + connector.getLocalPort();
        }
      }
    }

    if (globalSettings.mcpServerSettings.isEnabled) {
      mcpServer = InitializeMCP(globalSettings.mcpServerSettings);
      globalSettings.mcpServerSettings.isEnabled = mcpServer != null;
      globalSettings.mcpServerSettings.isRunning = mcpServer != null;
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

    // If the GUI is disabled and the API server is not running, then we are in CLI mode
    boolean cliResult = true;
    if (!globalSettings.guiSettings.isEnabled
        && !globalSettings.apiServerSettings.isRunning
        && !globalSettings.mcpServerSettings.isRunning) {
      var mergedRouterSettings = globalSettings.settingsMergerProtype.merge();
      if ((mergedRouterSettings.enabled != null && !mergedRouterSettings.enabled) && (globalSettings.drcSettings.enabled)) {
        cliResult = InitializeDRC(globalSettings);
      } else {
        cliResult = InitializeCLI(globalSettings);
      }
    }

    if ((!cliResult) && !globalSettings.apiServerSettings.isEnabled && !globalSettings.mcpServerSettings.isEnabled) {
      ShutdownApplication();
      FRLogger.traceExit("MainApplication.main()");
      System.exit(1);
    }

    while (globalSettings.guiSettings.isRunning
        || globalSettings.apiServerSettings.isRunning
        || globalSettings.mcpServerSettings.isRunning) {
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