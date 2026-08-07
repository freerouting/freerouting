package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.jetty.server.Server;

/**
 * Shared helpers for tests that start embedded Jetty API/MCP servers.
 *
 * <p>Under {@code maxParallelForks > 1}, cold-start and HTTP calls can be slow enough that short
 * timeouts and {@code server.isStarted()} alone are flaky.
 */
public final class EmbeddedServerTestSupport {

  public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

  private static final long STARTUP_TIMEOUT_MS = 30_000;
  private static final long STOP_TIMEOUT_MS = 15_000;

  private EmbeddedServerTestSupport() {}

  public static void waitForServerStarted(Server server) throws InterruptedException {
    long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
    while (!server.isStarted()) {
      if (server.isFailed()) {
        fail("Server failed to start");
      }
      if (System.currentTimeMillis() > deadline) {
        fail("Server did not start within " + STARTUP_TIMEOUT_MS + " ms");
      }
      Thread.sleep(50);
    }
  }

  public static void waitForApiServerReady(URI baseUri) throws Exception {
    waitForHttpOk(baseUri, "/v1/system/status");
  }

  public static void waitForMcpServerReady(URI baseUri) throws Exception {
    waitForHttpOk(baseUri, "/.well-known/agent.json");
  }

  public static void stopServerGracefully(Server server) throws Exception {
    if (server == null || server.isStopped()) {
      return;
    }
    server.setStopTimeout(STOP_TIMEOUT_MS);
    server.stop();
    long deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS + 2_000;
    while (!server.isStopped() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
  }

  private static void waitForHttpOk(URI baseUri, String path) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder(baseUri.resolve(path)).GET().timeout(Duration.ofSeconds(5)).build();

    long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
    Exception lastFailure = null;
    while (System.currentTimeMillis() < deadline) {
      try {
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (Exception e) {
        lastFailure = e;
      }
      Thread.sleep(100);
    }

    String detail = lastFailure != null ? lastFailure.getMessage() : "non-200 responses";
    fail("Server at " + baseUri + " did not respond to GET " + path + " in time: " + detail);
  }
}
