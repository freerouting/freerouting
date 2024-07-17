package app.freerouting.api;

import app.freerouting.logger.FRLogger;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;

@WebListener
public class AppContextListener implements ServletContextListener
{

  @Override
  public void contextInitialized(ServletContextEvent sce)
  {
    String fullUrl = "http://localhost:8080";

    // Try to get the Jetty server instance from the ServletContext and extract the host and port
    Server server = (Server) sce.getServletContext().getAttribute("org.eclipse.jetty.server.Server");
    if (server != null)
    {
      Connector[] connectors = server.getConnectors();
      for (Connector connector : connectors)
      {
        if (connector instanceof NetworkConnector networkConnector)
        {
          String host = networkConnector.getHost();
          if (host == null)
          {
            host = "localhost"; // Default host if not specified
          }
          int port = networkConnector.getPort();

          fullUrl = "http://" + host + ":" + port + sce.getServletContext().getContextPath();
          // Break after finding the first network connector for simplicity
          break;
        }
      }
    }
    else
    {
      FRLogger.warn("Could not retrieve Jetty Server instance from ServletContext.");
    }

    FRLogger.info("API web server started successfully at " + fullUrl + ". You can ping it at " + fullUrl + "/api/v1/system/status.");
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce)
  {
    FRLogger.info("API web server stopped.");
  }
}