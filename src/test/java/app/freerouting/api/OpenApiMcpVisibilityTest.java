package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.api.security.ApiKeyValidationService;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiMcpVisibilityTest {

  private Server apiServer;
  private URI baseUri;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    ApiKeyValidationService.resetForTesting();
    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings settings = new ApiServerSettings();
    settings.isEnabled = true;
    settings.isHttpAllowed = true;
    settings.endpoints = new String[] {"http://127.0.0.1:0"};
    settings.authentication.isEnabled = false;

    Freerouting.globalSettings.apiServerSettings.authentication.isEnabled = false;

    apiServer = Freerouting.InitializeAPI(settings);
    waitForServerStarted(apiServer);

    int port = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
    httpClient = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (apiServer != null) {
      apiServer.stop();
      waitForServerStopped(apiServer);
    }
    ApiKeyValidationService.resetForTesting();
  }

  @Test
  void openApiJson_includesMcpAndAgentCardPaths() throws Exception {
    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/openapi/openapi.json"))
        .GET()
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());

    String body = response.body();
    assertTrue(body.contains("\"/v1/mcp\""), "OpenAPI should include MCP JSON-RPC path");
    assertTrue(body.contains("\"/.well-known/agent.json\""), "OpenAPI should include A2A agent-card path");
  }

  private static void waitForServerStarted(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (!server.isStarted()) {
      if (server.isFailed()) {
        fail("API server failed to start");
      }
      if (System.currentTimeMillis() > deadline) {
        fail("API server did not start in time");
      }
      Thread.sleep(50);
    }
  }

  private static void waitForServerStopped(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (!server.isStopped() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
  }
}