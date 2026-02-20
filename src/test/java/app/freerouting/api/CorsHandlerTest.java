package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
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

class CorsHandlerTest {

  private Server server;
  private URI baseUri;

  private static void waitForServerStarted(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (!server.isStarted()) {
      if (System.currentTimeMillis() > deadline) {
        fail("Server did not start in time");
      }
      Thread.sleep(50);
    }
  }

  private static void waitForServerStopped(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (!server.isStopped()) {
      if (System.currentTimeMillis() > deadline) {
        return;
      }
      Thread.sleep(50);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings settings = new ApiServerSettings();
    settings.isEnabled = true;
    settings.isHttpAllowed = true;
    settings.endpoints = new String[] {"http://127.0.0.1:0"};
    settings.cors_origins = "http://example.com";

    server = Freerouting.InitializeAPI(settings);
    waitForServerStarted(server);

    int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
      waitForServerStopped(server);
    }
  }

  @Test
  void corsAppliesToV1PathsOnly() throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    HttpRequest v1Preflight = HttpRequest.newBuilder(baseUri.resolve("/v1/system/status"))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .header("Origin", "http://example.com")
        .header("Access-Control-Request-Method", "GET")
        .timeout(Duration.ofSeconds(5))
        .build();

    HttpResponse<Void> v1Response = client.send(v1Preflight, HttpResponse.BodyHandlers.discarding());
    assertEquals("http://example.com",
        v1Response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    assertEquals("true",
        v1Response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null));

    HttpRequest nonApiPreflight = HttpRequest.newBuilder(baseUri.resolve("/not-v1"))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .header("Origin", "http://example.com")
        .header("Access-Control-Request-Method", "GET")
        .timeout(Duration.ofSeconds(5))
        .build();

    HttpResponse<Void> nonApiResponse = client.send(nonApiPreflight, HttpResponse.BodyHandlers.discarding());
    assertTrue(nonApiResponse.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
  }
}