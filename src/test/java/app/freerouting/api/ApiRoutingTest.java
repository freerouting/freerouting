package app.freerouting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.ApiServerSettings;
import app.freerouting.settings.GlobalSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sub-Issue 07 – Guard headless / API code paths against {@code interactiveSettings} usage.
 *
 * <p>Exercises the full REST-API routing pipeline: create session → enqueue job →
 * upload DSN input → start job → poll until terminal state. Asserts that the job
 * reaches a terminal state without any {@link NullPointerException} or
 * {@link IllegalStateException} originating from {@code interactiveSettings} being
 * {@code null} in the headless API code path.
 */
class ApiRoutingTest {

  private static final int POLL_TIMEOUT_MS = 120_000;
  private static final int POLL_INTERVAL_MS = 200;

  /** A fixed test user UUID sent in every request via {@code Freerouting-Profile-ID}. */
  private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

  /**
   * Dummy bearer token used when API-key authentication is disabled (the default in unit tests).
   * When {@code ApiKeyValidationService.isEnabled == false}, any non-empty bearer value is
   * accepted, so this value serves only to satisfy the header-presence check in the filter.
   */
  private static final String TEST_BEARER = "test-api-key";

  private Server server;
  private URI baseUri;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    Freerouting.globalSettings = new GlobalSettings();

    ApiServerSettings settings = new ApiServerSettings();
    settings.isEnabled = true;
    settings.isHttpAllowed = true;
    settings.endpoints = new String[]{"http://127.0.0.1:0"};
    settings.cors_origins = null; // No CORS needed for unit tests

    server = Freerouting.InitializeAPI(settings);

    // Jetty + Jersey cold-start can take >5 s on GitHub Actions shared runners.
// 30 s gives enough headroom without making failures wait too long.
    long deadline = System.currentTimeMillis() + 30_000;
    while (!server.isStarted()) {
      if (server.isFailed()) {
        fail("API server failed to start (Jetty lifecycle state: FAILED)");
      }
      if (System.currentTimeMillis() > deadline) {
        fail("API server did not start within 30 seconds");
      }
      Thread.sleep(50);
    }

    int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
    httpClient = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  /**
   * Full routing workflow via the REST API using a small, real-world DSN file:
   *
   * <ol>
   *   <li>POST {@code /v1/sessions/create} – obtain a session ID.</li>
   *   <li>POST {@code /v1/jobs/enqueue} – create and enqueue a job.</li>
   *   <li>POST {@code /v1/jobs/{id}/settings} – upload minimal router settings.</li>
   *   <li>POST {@code /v1/jobs/{id}/input} – upload the Base64-encoded DSN file.</li>
   *   <li>PUT {@code /v1/jobs/{id}/start} – transition to READY_TO_START.</li>
   *   <li>Poll GET {@code /v1/jobs/{id}} – wait for terminal state.</li>
   * </ol>
   *
   * <p>The test verifies that the entire pipeline completes without any NPE or
   * {@code IllegalStateException} triggered by null {@code interactiveSettings} access in
   * the headless routing engine.
   */
  @Test
  void apiRouting_completesWithoutInteractiveSettingsNpe() throws Exception {
    // ── Step 1: Create a session ──────────────────────────────────────────────
    HttpRequest createSessionReq = authenticatedRequest(baseUri.resolve("/v1/sessions/create"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> createSessionResp = httpClient.send(
        createSessionReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, createSessionResp.statusCode(),
        "POST /v1/sessions/create must return HTTP 200. Body: " + createSessionResp.body());

    JsonObject sessionJson = JsonParser.parseString(createSessionResp.body()).getAsJsonObject();
    String sessionId = sessionJson.get("id").getAsString();
    assertNotNull(sessionId, "Session ID must not be null");

    // ── Step 2: Enqueue a job ─────────────────────────────────────────────────
    String enqueueBody = "{\"session_id\":\"" + sessionId + "\"}";
    HttpRequest enqueueReq = authenticatedRequest(baseUri.resolve("/v1/jobs/enqueue"))
        .POST(HttpRequest.BodyPublishers.ofString(enqueueBody))
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> enqueueResp = httpClient.send(
        enqueueReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, enqueueResp.statusCode(),
        "POST /v1/jobs/enqueue must return HTTP 200. Body: " + enqueueResp.body());

    JsonObject jobJson = JsonParser.parseString(enqueueResp.body()).getAsJsonObject();
    String jobId = jobJson.get("id").getAsString();
    assertNotNull(jobId, "Job ID must not be null");

    // ── Step 2b: Upload minimal router settings to keep the test fast ────────
    String settingsBody = "{\"max_passes\":5,\"job_timeout\":\"00:02:00\"}";
    HttpRequest uploadSettingsReq = authenticatedRequest(baseUri.resolve("/v1/jobs/" + jobId + "/settings"))
        .POST(HttpRequest.BodyPublishers.ofString(settingsBody))
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> uploadSettingsResp = httpClient.send(
        uploadSettingsReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, uploadSettingsResp.statusCode(),
        "POST /v1/jobs/{id}/settings must return HTTP 200. Body: " + uploadSettingsResp.body());

    // ── Step 3: Upload input DSN file ─────────────────────────────────────────
    // Use the minimal empty_board.dsn so the routing pass completes almost instantly.
    Path dsnFile = findTestFile("empty_board.dsn");
    byte[] dsnBytes = Files.readAllBytes(dsnFile);
    String dsnBase64 = Base64.getEncoder().encodeToString(dsnBytes);

    String inputBody = "{\"data\":\"" + dsnBase64 + "\","
        + "\"filename\":\"empty_board.dsn\"}";
    HttpRequest uploadInputReq = authenticatedRequest(baseUri.resolve("/v1/jobs/" + jobId + "/input"))
        .POST(HttpRequest.BodyPublishers.ofString(inputBody))
        .timeout(Duration.ofSeconds(30))
        .build();

    HttpResponse<String> uploadInputResp = httpClient.send(
        uploadInputReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, uploadInputResp.statusCode(),
        "POST /v1/jobs/{id}/input must return HTTP 200. Body: " + uploadInputResp.body());

    // ── Step 4: Start the job ─────────────────────────────────────────────────
    HttpRequest startReq = authenticatedRequest(baseUri.resolve("/v1/jobs/" + jobId + "/start"))
        .PUT(HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(10))
        .build();

    HttpResponse<String> startResp = httpClient.send(
        startReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, startResp.statusCode(),
        "PUT /v1/jobs/{id}/start must return HTTP 200. Body: " + startResp.body());

    // ── Step 5: Poll until terminal state ─────────────────────────────────────
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
    String terminalState = null;

    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);

      HttpRequest pollReq = authenticatedRequest(baseUri.resolve("/v1/jobs/" + jobId))
          .GET()
          .timeout(Duration.ofSeconds(5))
          .build();

      HttpResponse<String> pollResp = httpClient.send(
          pollReq, HttpResponse.BodyHandlers.ofString());

      if (pollResp.statusCode() == 200) {
        JsonObject polledJob = JsonParser.parseString(pollResp.body()).getAsJsonObject();
        String state = polledJob.has("state") ? polledJob.get("state").getAsString() : null;

        if (RoutingJobState.COMPLETED.name().equals(state)
            || RoutingJobState.CANCELLED.name().equals(state)
            || RoutingJobState.TERMINATED.name().equals(state)
            || RoutingJobState.TIMED_OUT.name().equals(state)) {
          terminalState = state;
          break;
        }
      }
    }

    assertNotNull(terminalState,
        "Routing job must reach a terminal state (COMPLETED/CANCELLED/TERMINATED/TIMED_OUT) within "
            + (POLL_TIMEOUT_MS / 1000) + " seconds via the REST API");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Returns an {@link HttpRequest.Builder} pre-populated with the authentication headers
   * required by {@code ApiKeyValidationFilter} and {@code BaseController.AuthenticateUser()}.
   */
  private HttpRequest.Builder authenticatedRequest(URI uri) {
    return HttpRequest.newBuilder(uri)
        .header("Authorization", "Bearer " + TEST_BEARER)
        .header("Freerouting-Profile-ID", TEST_USER_ID)
        .header("Freerouting-Environment-Host", "test/1.0")
        .header("Content-Type", "application/json");
  }

  private static Path findTestFile(String filename) throws IOException {
    Path dir = Path.of(".").toAbsolutePath();
    for (int i = 0; i < 10; i++) {
      Path candidate = dir.resolve("tests").resolve(filename);
      if (Files.exists(candidate)) {
        return candidate;
      }
      Path dir2 = dir.getParent();
      if (dir2 == null) {
        break;
      }
      dir = dir2;
    }
    throw new IOException("Test file not found: " + filename);
  }
}

