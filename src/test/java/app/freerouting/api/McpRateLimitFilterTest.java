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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("serial")
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

    mcpServer = Freerouting.initializeMCP(mcpSettings);
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
  void mcpRateLimitBlocksAfterConfiguredThreshold() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", 1);
    request.addProperty("method", "initialize");

    HttpResponse<String> r1 = sendWithRetry(request);
    HttpResponse<String> r2 = sendWithRetry(request);
    HttpResponse<String> r3 = sendWithRetry(request);

    assertEquals(200, r1.statusCode(), () -> "first request must pass, got: " + describe(r1));
    assertEquals(200, r2.statusCode(), () -> "second request must pass, got: " + describe(r2));
    assertEquals(
        429, r3.statusCode(), () -> "third request must be rate-limited, got: " + describe(r3));
  }

  /**
   * Sends one MCP request, retrying once on a transport-level {@link IOException}. The rate-limited
   * 429 is produced via {@code abortWith} before the request entity is consumed, which can reset
   * the pooled keep-alive connection on Windows loopback. The retry dials a fresh connection (the
   * failed pool entry is evicted). A persistent problem still fails the test via the rethrown
   * exception or the status assertions below.
   */
  private HttpResponse<String> sendWithRetry(JsonObject body) throws Exception {
    try {
      return httpClient.send(authenticatedRequest(body), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      return httpClient.send(authenticatedRequest(body), HttpResponse.BodyHandlers.ofString());
    }
  }

  private static String describe(HttpResponse<String> response) {
    return "HTTP " + response.statusCode() + " body=" + response.body();
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
