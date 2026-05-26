package app.freerouting.api.mcp;

import app.freerouting.logger.FRLogger;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;

/**
 * Servlet context listener for the dedicated MCP server.
 */
@WebListener
public class McpContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    String fullUrl = "http://localhost:37964";

    Server server = null;
    ServletContextHandler contextHandler = ServletContextHandler.getServletContextHandler(sce.getServletContext());
    if (contextHandler != null) {
      server = contextHandler.getServer();
    }
    if (server == null) {
      server = (Server) sce.getServletContext().getAttribute("org.eclipse.jetty.server.Server");
    }

    if (server != null) {
      Connector[] connectors = server.getConnectors();
      for (Connector connector : connectors) {
        if (connector instanceof NetworkConnector networkConnector) {
          String host = networkConnector.getHost();
          if (host == null) {
            host = "localhost";
          }
          int port = networkConnector.getPort();
          fullUrl = "http://" + host + ":" + port + sce.getServletContext().getContextPath();
          break;
        }
      }
    }

    FRLogger.info("MCP server started successfully at " + fullUrl
        + ". JSON-RPC endpoint: " + fullUrl + "/v1/mcp, SSE: " + fullUrl
        + "/v1/mcp/events, WebSocket: " + fullUrl + "/v1/mcp/ws.");

    if (!McpApiKeyValidationService.getInstance().isAuthenticationEnabled()) {
      FRLogger.warn("MCP server authentication is DISABLED. Enable it before exposing MCP endpoints to a network.");
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    FRLogger.info("MCP server stopped.");
  }
}