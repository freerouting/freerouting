package app.freerouting.api;

import app.freerouting.logger.FRLogger;
import app.freerouting.api.security.ApiKeyValidationService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;

/**
 * Servlet context lifecycle listener that fires once when the Jetty server finishes starting.
 *
 * <p>On {@link #contextInitialized} it:
 * <ol>
 *   <li>Resolves the actual bound host and port from the first {@link NetworkConnector}.</li>
 *   <li>Logs an INFO message with the base URL and the Swagger UI link.</li>
 *   <li>Logs a WARN message if API authentication is disabled, reminding operators to
 *       enable it before exposing the server to a network.</li>
 * </ol>
 */
@WebListener
public class AppContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    String fullUrl = "http://localhost:37864";

    // In Jetty 12 (EE10) the Server is no longer stored as a plain ServletContext attribute.
    // The supported API is to obtain the wrapping ServletContextHandler via its static helper
    // and then call getServer() on it.
    Server server = null;
    ServletContextHandler contextHandler = ServletContextHandler.getServletContextHandler(sce.getServletContext());
    if (contextHandler != null) {
      server = contextHandler.getServer();
    }
    // Fallback for other servlet containers that still expose the legacy attribute.
    if (server == null) {
      server = (Server) sce.getServletContext().getAttribute("org.eclipse.jetty.server.Server");
    }

    if (server != null) {
      Connector[] connectors = server.getConnectors();
      for (Connector connector : connectors) {
        if (connector instanceof NetworkConnector networkConnector) {
          String host = networkConnector.getHost();
          if (host == null) {
            host = "localhost"; // Default host if not specified
          }
          int port = networkConnector.getPort();

          fullUrl = "http://" + host + ":" + port + sce.getServletContext().getContextPath();
          // Break after finding the first network connector for simplicity
          break;
        }
      }
    } else {
      FRLogger.debug("Could not retrieve Jetty Server instance from ServletContext; using default URL.");
    }

    FRLogger.info("API web server started successfully at " + fullUrl + ". You can ping it at " + fullUrl + "/v1/system/status. Swagger UI is available at " + fullUrl + "/swagger-ui.");

    if (!ApiKeyValidationService.getInstance().isAuthenticationEnabled()) {
      FRLogger.warn("API server authentication is DISABLED. All API endpoints are accessible without an API key. Enable authentication in the configuration before exposing this server to a network.");
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    FRLogger.info("API web server stopped.");
  }
}
