package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForApiServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
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
import org.junit.jupiter.api.Test;

class CorsHandlerTest {

  private Server server;
  private URI baseUri;

  @BeforeEach
  void setUp() throws Exception {
    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings settings = new ApiServerSettings();
    settings.isEnabled = true;
    settings.isHttpAllowed = true;
    settings.endpoints = new String[] {"http://127.0.0.1:0"};
    settings.corsOrigins = "http://example.com";

    server = Freerouting.initializeAPI(settings);
    waitForServerStarted(server);

    int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
    waitForApiServerReady(baseUri);
  }

  @AfterEach
  void tearDown() throws Exception {
    stopServerGracefully(server);
  }

  @Test
  void corsAppliesToV1PathsOnly() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    HttpRequest v1Preflight =
        HttpRequest.newBuilder(baseUri.resolve("/v1/system/status"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", "http://example.com")
            .header("Access-Control-Request-Method", "GET")
            .timeout(HTTP_TIMEOUT)
            .build();

    HttpResponse<Void> v1Response =
        client.send(v1Preflight, HttpResponse.BodyHandlers.discarding());
    assertEquals(
        "http://example.com",
        v1Response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    assertEquals(
        "true", v1Response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null));

    HttpRequest nonApiPreflight =
        HttpRequest.newBuilder(baseUri.resolve("/not-v1"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", "http://example.com")
            .header("Access-Control-Request-Method", "GET")
            .timeout(HTTP_TIMEOUT)
            .build();

    HttpResponse<Void> nonApiResponse =
        client.send(nonApiPreflight, HttpResponse.BodyHandlers.discarding());
    assertTrue(nonApiResponse.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
  }

  @Test
  void corsPreflightAllowsFreeroutingCustomHeaders() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    // Simulate a browser preflight for Freerouting-Profile-ID (as sent by EasyEDA / browser
    // clients)
    HttpRequest preflight =
        HttpRequest.newBuilder(baseUri.resolve("/v1/system/status"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", "http://example.com")
            .header("Access-Control-Request-Method", "GET")
            .header(
                "Access-Control-Request-Headers",
                "Freerouting-Profile-ID,Freerouting-Environment-Host,Freerouting-Profile-Email")
            .timeout(HTTP_TIMEOUT)
            .build();

    HttpResponse<Void> response = client.send(preflight, HttpResponse.BodyHandlers.discarding());

    // The preflight must be accepted (200 or 204)
    assertTrue(
        response.statusCode() == 200 || response.statusCode() == 204,
        "Expected 200 or 204 for CORS preflight, got: " + response.statusCode());

    // Access-Control-Allow-Origin must be echoed back
    assertEquals(
        "http://example.com",
        response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

    // Access-Control-Allow-Headers must include all three Freerouting-specific headers
    String allowedHeaders =
        response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase();
    assertTrue(
        allowedHeaders.contains("freerouting-profile-id"),
        "Access-Control-Allow-Headers should include 'Freerouting-Profile-ID', got: "
            + allowedHeaders);
    assertTrue(
        allowedHeaders.contains("freerouting-profile-email"),
        "Access-Control-Allow-Headers should include 'Freerouting-Profile-Email', got: "
            + allowedHeaders);
    assertTrue(
        allowedHeaders.contains("freerouting-environment-host"),
        "Access-Control-Allow-Headers should include 'Freerouting-Environment-Host', got: "
            + allowedHeaders);
  }
}
