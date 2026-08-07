package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForMcpServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.Freerouting;
import app.freerouting.api.mcp.McpApiKeyValidationService;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.McpServerSettings;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpRateLimitFilterTest {

  private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

  private Server mcpServer;
  private URI mcpBaseUri;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    McpApiKeyValidationService.resetForTesting();
    Freerouting.globalSettings = new GlobalSettings();

    McpServerSettings mcpSettings = new McpServerSettings();
    mcpSettings.isEnabled = true;
    mcpSettings.isHttpAllowed = true;
    mcpSettings.endpoints = new String[] {"http://127.0.0.1:0"};
    mcpSettings.authentication.isEnabled = false;

    Freerouting.globalSettings.mcpServerSettings.authentication.isEnabled = false;
    Freerouting.globalSettings.mcpServerSettings.rateLimit.enabled = true;
    Freerouting.globalSettings.mcpServerSettings.rateLimit.requestsPerWindow = 2;
    Freerouting.globalSettings.mcpServerSettings.rateLimit.windowSeconds = 60;

    mcpServer = Freerouting.InitializeMCP(mcpSettings);
    waitForServerStarted(mcpServer);

    int mcpPort = ((ServerConnector) mcpServer.getConnectors()[0]).getLocalPort();
    mcpBaseUri = URI.create("http://127.0.0.1:" + mcpPort);
    httpClient = HttpClient.newHttpClient();
    waitForMcpServerReady(mcpBaseUri);
  }

  @AfterEach
  void tearDown() throws Exception {
    stopServerGracefully(mcpServer);
    McpApiKeyValidationService.resetForTesting();
  }

  @Test
  void mcpRateLimit_blocksAfterConfiguredThreshold() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 1);
    request.addProperty("method", "initialize");

    HttpResponse<String> r1 =
        httpClient.send(authenticatedRequest(request), HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> r2 =
        httpClient.send(authenticatedRequest(request), HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> r3 =
        httpClient.send(authenticatedRequest(request), HttpResponse.BodyHandlers.ofString());

    assertEquals(200, r1.statusCode());
    assertEquals(200, r2.statusCode());
    assertEquals(429, r3.statusCode());
  }

  private HttpRequest authenticatedRequest(JsonObject body) {
    return HttpRequest.newBuilder(mcpBaseUri.resolve("/v1/mcp"))
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .timeout(HTTP_TIMEOUT)
        .header("Content-Type", "application/json")
        .header("Freerouting-Profile-ID", TEST_USER_ID)
        .header("Freerouting-Environment-Host", "test/1.0")
        .build();
  }
}
