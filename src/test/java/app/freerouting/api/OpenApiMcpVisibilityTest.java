package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForApiServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.api.security.ApiKeyValidationService;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
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

    apiServer = Freerouting.initializeAPI(settings);
    waitForServerStarted(apiServer);

    int port = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
    httpClient = HttpClient.newHttpClient();
    waitForApiServerReady(baseUri);
  }

  @AfterEach
  void tearDown() throws Exception {
    stopServerGracefully(apiServer);
    ApiKeyValidationService.resetForTesting();
  }

  @Test
  void openApiJsonIncludesMcpAndAgentCardPaths() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(baseUri.resolve("/openapi/openapi.json"))
            .GET()
            .timeout(HTTP_TIMEOUT)
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(
        200, response.statusCode(), () -> "Unexpected OpenAPI response: " + response.body());

    String body = response.body();
    assertTrue(
        body.contains("\"/v1/mcp\""),
        () ->
            "OpenAPI should include MCP JSON-RPC path; body starts with: "
                + body.substring(0, Math.min(body.length(), 300)));
    assertTrue(
        body.contains("\"/.well-known/agent.json\""),
        () ->
            "OpenAPI should include A2A agent-card path; body starts with: "
                + body.substring(0, Math.min(body.length(), 300)));
  }
}
