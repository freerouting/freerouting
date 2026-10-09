package app.freerouting;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.AppContextListener;
import app.freerouting.api.mcp.McpApplication;
import app.freerouting.api.mcp.McpContextListener;
import app.freerouting.api.mcp.McpWebSocketEndpoint;
import app.freerouting.cli.BenchmarkAndCompareCommands;
import app.freerouting.constants.Constants;
import app.freerouting.gui.board.GuiManager;
import app.freerouting.gui.support.DefaultExceptionHandler;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.McpServerSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.EnvironmentVariablesSource;
import app.freerouting.settings.sources.JsonFileSettings;
import app.freerouting.startup.AnalyticsBootstrap;
import app.freerouting.startup.GlobalSettingsBootstrap;
import app.freerouting.startup.LoggingBootstrap;
import app.freerouting.startup.UserDataPathResolver;
import app.freerouting.util.TextManager;
import app.freerouting.util.VersionChecker;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
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

/** Entry point for the Freerouting application. */
public class Freerouting {

  public static final String WEB_URL = "https://www.freerouting.app";
  public static final String VERSION_NUMBER_STRING =
      "v"
          + Constants.FREEROUTING_VERSION
          + " (build-date: "
          + Constants.FREEROUTING_BUILD_DATE
          + ")";
  public static GlobalSettings globalSettings;
  public static String bridgeToken = java.util.UUID.randomUUID().toString();
  private static Server apiServer; // API server instance
  private static Server mcpServer; // MCP server instance
  private static java.io.PrintStream originalSystemOut;

  private static boolean initializeCli(GlobalSettings globalSettings) {
    return app.freerouting.cli.CliRunner.initializeCli(globalSettings);
  }

  private static boolean initializeDrc(GlobalSettings globalSettings) {
    return app.freerouting.cli.DrcRunner.initializeDrc(globalSettings);
  }

  private static void shutdownApplication() {
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

  /**
   * Initializes and starts the API server.
   *
   * @param apiServerSettings settings used to configure the API server
   * @return the initialized API server, or {@code null} if it could not be started
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public static Server initializeAPI(ApiServerSettings apiServerSettings) {
    // Check if there are any endpoints defined
    if (apiServerSettings.endpoints.length == 0) {
      FRLogger.warn(
          "Can't start API server, because no endpoints are defined in ApiServerSettings.");
      return null;
    }

    // Start the Jetty server
    Server apiServer = new Server();

    // Add all endpoints as connectors
    for (String endpointUrl : apiServerSettings.endpoints) {
      endpointUrl = endpointUrl.toLowerCase();
      String[] endpointParts = endpointUrl.split("://");
      String protocol = endpointParts[0];

      // Check if the protocol is HTTP or HTTPS
      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        FRLogger.warn(
            ("Can't use the endpoint '%s' for the API server, because its protocol is not HTTP "
                    + "or HTTPS.")
                .formatted(endpointUrl));
        continue;
      }

      // Check if the http is allowed
      if (!apiServerSettings.isHttpAllowed && "http".equals(protocol)) {
        FRLogger.warn(
            "Can't use the endpoint '%s' for the API server, because HTTP is not allowed."
                .formatted(endpointUrl));
        continue;
      }

      // Fail closed when HTTPS is requested, because TLS is not implemented yet
      if ("https".equals(protocol)) {
        FRLogger.warn(
            "HTTPS endpoint '%s' cannot be initialized because TLS is not implemented yet; rejecting plaintext fallback."
                .formatted(endpointUrl));
        continue;
      }

      String hostAndPort = endpointParts[1];
      String[] hostAndPortParts = hostAndPort.split(":");
      String host = hostAndPortParts[0];
      int port = Integer.parseInt(hostAndPortParts[1]);
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
    if (apiServerSettings.corsOrigins != null && !"".equals(apiServerSettings.corsOrigins)) {
      String allowedOrigins = apiServerSettings.corsOrigins;
      Set<String> originPatterns = splitCommaSeparated(allowedOrigins);
      boolean hasWildcard = originPatterns.contains("*");
      if (hasWildcard) {
        FRLogger.warn("CORS configured with wildcard origin; disabling allowCredentials.");
      }

      CrossOriginHandler corsHandler = new CrossOriginHandler();
      corsHandler.setAllowCredentials(!hasWildcard);
      corsHandler.setAllowedOriginPatterns(originPatterns);
      corsHandler.setAllowedMethods(Set.of("HEAD", "GET", "POST", "PUT", "DELETE", "OPTIONS"));
      corsHandler.setAllowedHeaders(
          Set.of(
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
    jerseyServlet.setInitParameter(
        "jakarta.ws.rs.Application", "app.freerouting.api.FreeroutingApplication");
    jerseyServlet.setInitParameter("jersey.config.application.disableJsonBinding", "true");

    // Add Listeners
    context.addEventListener(new AppContextListener());

    try {
      apiServer.start();
    } catch (Exception e) {
      FRLogger.error("Error starting API server", e);
      if (globalSettings != null) {
        globalSettings.apiServerSettings.isRunning = false;
      }
      return null;
    }

    // Keep the caller responsive after the server has bound its connectors.
    Thread apiJoinThread =
        new Thread(
            () -> {
              try {
                apiServer.join();
              } catch (Exception e) {
                FRLogger.error("Error joining API server", e);
                if (globalSettings != null) {
                  globalSettings.apiServerSettings.isRunning = false;
                }
              }
            },
            "api-server-join");
    apiJoinThread.setDaemon(true);
    apiJoinThread.start();

    return apiServer;
  }

  /** Stops the API server if it is currently running. */
  public static void stopApiServer() {
    if (apiServer != null && apiServer.isRunning()) {
      try {
        apiServer.stop();
      } catch (Exception e) {
        FRLogger.error("Error stopping API server", e);
      }
    }
  }

  /**
   * Initializes and starts the MCP server.
   *
   * @param mcpServerSettings settings used to configure the MCP server
   * @return the initialized MCP server, or {@code null} if it could not be started
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public static Server initializeMCP(McpServerSettings mcpServerSettings) {
    if (mcpServerSettings.endpoints.length == 0) {
      FRLogger.warn(
          "Can't start MCP server, because no endpoints are defined in McpServerSettings.");
      return null;
    }

    Server mcpServer = new Server();

    for (String endpointUrl : mcpServerSettings.endpoints) {
      endpointUrl = endpointUrl.toLowerCase();
      String[] endpointParts = endpointUrl.split("://");
      String protocol = endpointParts[0];

      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        FRLogger.warn(
            ("Can't use the endpoint '%s' for the MCP server, because its protocol is not HTTP "
                    + "or HTTPS.")
                .formatted(endpointUrl));
        continue;
      }

      if (!mcpServerSettings.isHttpAllowed && "http".equals(protocol)) {
        FRLogger.warn(
            "Can't use the endpoint '%s' for the MCP server, because HTTP is not allowed."
                .formatted(endpointUrl));
        continue;
      }

      // Fail closed when HTTPS is requested, because TLS is not implemented yet
      if ("https".equals(protocol)) {
        FRLogger.warn(
            "HTTPS endpoint '%s' cannot be initialized because TLS is not implemented yet; rejecting plaintext fallback."
                .formatted(endpointUrl));
        continue;
      }

      String hostAndPort = endpointParts[1];
      String[] hostAndPortParts = hostAndPort.split(":");
      String host = hostAndPortParts[0];
      int port = Integer.parseInt(hostAndPortParts[1]);
      ServerConnector connector = new ServerConnector(mcpServer);
      connector.setHost(host);
      connector.setPort(port);
      mcpServer.addConnector(connector);
    }

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    Handler mcpHandler = context;

    if (mcpServerSettings.corsOrigins != null && !"".equals(mcpServerSettings.corsOrigins)) {
      String allowedOrigins = mcpServerSettings.corsOrigins;
      Set<String> originPatterns = splitCommaSeparated(allowedOrigins);
      boolean hasWildcard = originPatterns.contains("*");
      if (hasWildcard) {
        FRLogger.warn("MCP CORS configured with wildcard origin; disabling allowCredentials.");
      }

      CrossOriginHandler corsHandler = new CrossOriginHandler();
      corsHandler.setAllowCredentials(!hasWildcard);
      corsHandler.setAllowedOriginPatterns(originPatterns);
      corsHandler.setAllowedMethods(Set.of("HEAD", "GET", "POST", "PUT", "DELETE", "OPTIONS"));
      corsHandler.setAllowedHeaders(
          Set.of(
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
    jerseyServlet.setInitParameter("jersey.config.application.disableJsonBinding", "true");

    context.addEventListener(new McpContextListener());

    JakartaWebSocketServletContainerInitializer.configure(
        context,
        (servletContext, wsContainer) -> wsContainer.addEndpoint(McpWebSocketEndpoint.class));

    try {
      mcpServer.start();
    } catch (Exception e) {
      FRLogger.error("Error starting MCP server", e);
      if (globalSettings != null) {
        globalSettings.mcpServerSettings.isRunning = false;
      }
      return null;
    }

    // Keep the caller responsive after the server has bound its connectors.
    Thread mcpJoinThread =
        new Thread(
            () -> {
              try {
                mcpServer.join();
              } catch (Exception e) {
                FRLogger.error("Error joining MCP server", e);
                if (globalSettings != null) {
                  globalSettings.mcpServerSettings.isRunning = false;
                }
              }
            },
            "mcp-server-join");
    mcpJoinThread.setDaemon(true);
    mcpJoinThread.start();

    return mcpServer;
  }

  /**
   * Starts the MCP standard-input/output bridge.
   *
   * @param originalOut stream receiving bridge output
   * @param server MCP server whose local endpoint is used by the bridge
   */
  public static void startMcpStdioBridge(java.io.PrintStream originalOut, Server server) {
    Thread bridgeThread =
        new Thread(
            () -> {
              try (java.io.BufferedReader reader =
                  new java.io.BufferedReader(
                      new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                int localPort = -1;
                while (localPort <= 0) {
                  if (server != null
                      && server.getConnectors().length > 0
                      && server.getConnectors()[0] instanceof ServerConnector connector) {
                    localPort = connector.getLocalPort();
                  }
                  if (localPort <= 0) {
                    try {
                      Thread.sleep(50);
                    } catch (InterruptedException _) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                  }
                }

                String resolvedProfileId = System.getenv("FREEROUTING_PROFILE_ID");
                if (resolvedProfileId == null || resolvedProfileId.isBlank()) {
                  resolvedProfileId = System.getenv("FREEROUTING__PROFILE__ID");
                }
                if ((resolvedProfileId == null || resolvedProfileId.isBlank())
                    && globalSettings != null
                    && globalSettings.userProfileSettings != null) {
                  resolvedProfileId = globalSettings.userProfileSettings.userId;
                }
                if (resolvedProfileId == null || resolvedProfileId.isBlank()) {
                  resolvedProfileId = "00000000-0000-0000-0000-000000000000";
                }

                String resolvedProfileEmail = System.getenv("FREEROUTING_PROFILE_EMAIL");
                if (resolvedProfileEmail == null || resolvedProfileEmail.isBlank()) {
                  resolvedProfileEmail = System.getenv("FREEROUTING__PROFILE__EMAIL");
                }
                if ((resolvedProfileEmail == null || resolvedProfileEmail.isBlank())
                    && globalSettings != null
                    && globalSettings.userProfileSettings != null) {
                  resolvedProfileEmail = globalSettings.userProfileSettings.userEmail;
                }

                String resolvedHost = System.getenv("FREEROUTING_ENVIRONMENT_HOST");
                if (resolvedHost == null || resolvedHost.isBlank()) {
                  resolvedHost = System.getenv("FREEROUTING__ENVIRONMENT__HOST");
                }

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.URI targetUri =
                    java.net.URI.create("http://127.0.0.1:" + localPort + "/v1/mcp");

                String line;
                while ((line = reader.readLine()) != null) {
                  if (line.trim().isEmpty()) {
                    continue;
                  }
                  try {
                    java.net.http.HttpRequest.Builder reqBuilder =
                        java.net.http.HttpRequest.newBuilder(targetUri)
                            .header("Content-Type", "application/json")
                            .header("X-Internal-Bridge-Token", bridgeToken)
                            .header("Freerouting-Profile-ID", resolvedProfileId);

                    if (resolvedProfileEmail != null && !resolvedProfileEmail.isBlank()) {
                      reqBuilder.header("Freerouting-Profile-Email", resolvedProfileEmail);
                    }
                    if (resolvedHost != null && !resolvedHost.isBlank()) {
                      reqBuilder.header("Freerouting-Environment-Host", resolvedHost);
                    }

                    java.net.http.HttpRequest request =
                        reqBuilder
                            .POST(
                                java.net.http.HttpRequest.BodyPublishers.ofString(
                                    line, StandardCharsets.UTF_8))
                            .build();

                    java.net.http.HttpResponse<String> response =
                        client.send(
                            request,
                            java.net.http.HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8));
                    String responseBody = response.body();
                    if (responseBody != null) {
                      String singleLineResponse = responseBody.replace("\r", "").replace("\n", "");
                      originalOut.println(singleLineResponse);
                      originalOut.flush();
                    }
                  } catch (Exception e) {
                    FRLogger.error("Error in MCP stdio bridge request forwarding", e);
                  }
                }
                FRLogger.info("MCP stdio bridge detected EOF, shutting down application.");
                System.exit(0);
              } catch (IOException e) {
                FRLogger.error("Error reading from System.in in MCP stdio bridge", e);
                System.exit(1);
              }
            },
            "mcp-stdio-bridge");
    bridgeThread.setDaemon(true);
    bridgeThread.start();
  }

  private static Set<String> splitCommaSeparated(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .collect(Collectors.toSet());
  }

  private static Path resolveLogPath(String input, Path defaultDir) {
    return app.freerouting.startup.LoggingBootstrap.resolveLogPath(input, defaultDir);
  }

  private static boolean compareBoardFiles(String file1Path, String file2Path) {
    return app.freerouting.cli.BenchmarkAndCompareCommands.compareBoardFiles(file1Path, file2Path);
  }

  /**
   * The entry point of the Freerouting application.
   *
   * @param args command-line arguments
   */
  void main(String[] args) {
    originalSystemOut = System.out;
    boolean isStdioMode = false;
    if (args.length > 0) {
      for (String arg : args) {
        if (arg.startsWith("--mcp_server.stdio=")) {
          String val = arg.substring("--mcp_server.stdio=".length());
          if ("true".equalsIgnoreCase(val) || "1".equals(val)) {
            isStdioMode = true;
          }
        }
      }
    }
    if (System.getenv("FREEROUTING__MCP_SERVER__STDIO") != null) {
      String envVal = System.getenv("FREEROUTING__MCP_SERVER__STDIO");
      if ("true".equalsIgnoreCase(envVal) || "1".equals(envVal)) {
        isStdioMode = true;
      }
    }
    if (isStdioMode) {
      System.setOut(System.err);
    }

    // Set up user-data path and logging configuration BEFORE any logging occurs
    UserDataPathResolver.ResolvedUserDataPath userdataPath =
        UserDataPathResolver.resolveAndLock(args);

    LoggingBootstrap.LoggingConfig loggingConfig =
        LoggingBootstrap.initialize(args, userdataPath.path(), userdataPath.source());

    // NOW we can start logging - log4j2 will initialize with our configuration
    FRLogger.traceEntry("MainApplication.main()");

    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException
        | InstantiationException
        | UnsupportedLookAndFeelException
        | IllegalAccessException ex) {
      FRLogger.error(ex.getLocalizedMessage(), ex);
    }

    // Log system information
    FRLogger.info("Freerouting " + VERSION_NUMBER_STRING);
    FRLogger.debug(
        "[startup] user-data path : "
            + GlobalSettings.getUserDataPath()
            + "  (source: "
            + userdataPath.source()
            + ")");
    FRLogger.debug("[startup] log file       : " + loggingConfig.fileLoggingLocation());
    Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

    globalSettings = GlobalSettingsBootstrap.initialize(args, loggingConfig.fileLoggingLocation());

    // Warn if mcp_server.stdio was set in freerouting.json but not via CLI/env.
    if (!isStdioMode
        && globalSettings != null
        && globalSettings.mcpServerSettings != null
        && Boolean.TRUE.equals(globalSettings.mcpServerSettings.isStdioMode)) {
      FRLogger.warn(
          """
          [startup] 'mcp_server.stdio=true' was found in freerouting.json but is being ignored. \
          The stdio redirect must be requested before logging is initialised and therefore \
          can only be set via the '--mcp_server.stdio=true' CLI argument or the \
          'FREEROUTING__MCP_SERVER__STDIO=true' environment variable. \
          The JSON setting has no effect and the MCP stdio transport will NOT work correctly.
          """);
    }

    // if we don't have a GUI enabled then we must use the console as our output
    if ((!globalSettings.guiSettings.isEnabled) && (System.console() == null)) {
      FRLogger.warn(
          "GUI is disabled and you don't have a console available, so the only feedback from "
              + "Freerouting is in the log.");
    }

    if (globalSettings.compareFile1 != null && globalSettings.compareFile2 != null) {
      boolean success = compareBoardFiles(globalSettings.compareFile1, globalSettings.compareFile2);
      System.exit(success ? 0 : 1);
    }

    if (globalSettings.calculateBenchmarkScores) {
      int exitCode =
          BenchmarkAndCompareCommands.calculateBenchmarkScores(
              globalSettings.benchmarkScoresInput, globalSettings.benchmarkScoresOutput);
      System.exit(exitCode);
    }

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
            "Couldn't get screen resolution. If you are running in a headless environment, "
                + "disable the GUI by setting gui.enabled to false.");
        globalSettings.guiSettings.isEnabled = false;
      }
    }

    AnalyticsBootstrap.initialize(globalSettings, args, true, width, height, dpi);

    // check for new version
    VersionChecker checker = new VersionChecker(Constants.FREEROUTING_VERSION);
    Thread versionThread = new Thread(checker, "version-checker");
    versionThread.setDaemon(true);
    versionThread.start();

    // Check if the user requested help
    if (globalSettings.showHelpOption) {
      TextManager ctm = new TextManager(Freerouting.class, globalSettings.currentLocale);
      System.out.print(ctm.getText("command_line_help"));
      System.exit(0);
    }

    // Disable GUI and API if in DRC-only mode
    if (globalSettings.drcReportFile != null) {
      globalSettings.guiSettings.isEnabled = false;
      globalSettings.apiServerSettings.isEnabled = false;
      globalSettings.mcpServerSettings.isEnabled = false;
    }

    // Create the settings merger prototype based on the sources that will not change at runtime
    globalSettings.settingsMergerProtype =
        new SettingsMerger(
            new DefaultSettings(),
            new JsonFileSettings(),
            new CliSettings(args),
            new EnvironmentVariablesSource());

    // Initialize the API server
    if (globalSettings.apiServerSettings.isEnabled) {
      apiServer = initializeAPI(globalSettings.apiServerSettings);
      globalSettings.apiServerSettings.isEnabled = apiServer != null;
      globalSettings.apiServerSettings.isRunning = apiServer != null;

      if (apiServer != null
          && (globalSettings.mcpServerSettings.targetApiBaseUrl == null
              || globalSettings.mcpServerSettings.targetApiBaseUrl.isBlank()
              || "http://127.0.0.1:37864"
                  .equals(globalSettings.mcpServerSettings.targetApiBaseUrl))) {
        if (apiServer.getConnectors().length > 0
            && apiServer.getConnectors()[0] instanceof ServerConnector connector) {
          int port = connector.getLocalPort();
          if (port <= 0) {
            port = connector.getPort();
          }
          globalSettings.mcpServerSettings.targetApiBaseUrl = "http://127.0.0.1:" + port;
        }
      }
    }

    if (globalSettings.mcpServerSettings.isEnabled) {
      mcpServer = initializeMCP(globalSettings.mcpServerSettings);
      globalSettings.mcpServerSettings.isEnabled = mcpServer != null;
      globalSettings.mcpServerSettings.isRunning = mcpServer != null;

      if (mcpServer != null && Boolean.TRUE.equals(globalSettings.mcpServerSettings.isStdioMode)) {
        startMcpStdioBridge(originalSystemOut, mcpServer);
      }
    }

    // Initialize the GUI
    if (globalSettings.guiSettings.isEnabled) {
      if (!GuiManager.initializeGUI(globalSettings)) {
        FRLogger.error("Couldn't initialize the GUI", null);
        globalSettings.guiSettings.isEnabled = false;
      } else {
        globalSettings.guiSettings.isRunning = true;
      }
    }

    // If the GUI is disabled, execute CLI routing / DRC or run in daemon mode
    boolean cliResult = true;
    if (!globalSettings.guiSettings.isEnabled) {
      if (globalSettings.drcReportFile != null) {
        cliResult = initializeDrc(globalSettings);
      } else if (globalSettings.initialInputFile != null
          || (!globalSettings.apiServerSettings.isRunning
              && !globalSettings.mcpServerSettings.isRunning)) {
        cliResult = initializeCli(globalSettings);
      }
    }

    if ((!cliResult)
        && !globalSettings.apiServerSettings.isEnabled
        && !globalSettings.mcpServerSettings.isEnabled) {
      shutdownApplication();
      FRLogger.traceExit("MainApplication.main()");
      System.exit(1);
    }

    // If a batch CLI job was executed with an input file, shut down and exit immediately
    if (globalSettings.initialInputFile != null && !globalSettings.guiSettings.isEnabled) {
      shutdownApplication();
      FRLogger.traceExit("MainApplication.main()");
      System.exit(globalSettings.cliExitCode);
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

    shutdownApplication();

    FRLogger.traceExit("MainApplication.main()");
    if (!globalSettings.guiSettings.isEnabled
        && !globalSettings.apiServerSettings.isRunning
        && !globalSettings.mcpServerSettings.isRunning) {
      System.exit(globalSettings.cliExitCode);
    }
    System.exit(0);
  }
}
