package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ApiRateLimitFilterTest {

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
    Freerouting.globalSettings.apiServerSettings.rateLimit.enabled = true;
    Freerouting.globalSettings.apiServerSettings.rateLimit.requestsPerWindow = 2;
    Freerouting.globalSettings.apiServerSettings.rateLimit.windowSeconds = 60;

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
  void apiRateLimit_blocksAfterConfiguredThreshold() throws Exception {
    HttpRequest req = HttpRequest.newBuilder(baseUri.resolve("/v1/system/status"))
        .GET()
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> r1 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> r2 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    HttpResponse<String> r3 = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, r1.statusCode());
    assertEquals(200, r2.statusCode());
    assertEquals(429, r3.statusCode());
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