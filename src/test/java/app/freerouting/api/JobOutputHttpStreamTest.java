package app.freerouting.api;

import static app.freerouting.api.EmbeddedServerTestSupport.HTTP_TIMEOUT;
import static app.freerouting.api.EmbeddedServerTestSupport.stopServerGracefully;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForApiServerReady;
import static app.freerouting.api.EmbeddedServerTestSupport.waitForServerStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.security.ApiKeyValidationService;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.io.kicad.KiCadJsonWriter;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies stream completion through real HTTP, Jersey sinks, and embedded Jetty. */
@Tag("serial")
class JobOutputHttpStreamTest {
  private final UUID userId = UUID.randomUUID();
  private final RoutingJob job = new RoutingJob();
  private GlobalSettings previousSettings;
  private Session session;
  private Server server;
  private URI baseUri;

  @BeforeEach
  void setUp() throws Exception {
    previousSettings = Freerouting.globalSettings;
    GlobalSettings settings = new GlobalSettings();
    GlobalSettings.setCurrent(settings);
    Freerouting.globalSettings = settings;
    FRAnalytics.setEnabled(false);
    settings.apiServerSettings.isEnabled = true;
    settings.apiServerSettings.isHttpAllowed = true;
    settings.apiServerSettings.endpoints = new String[] {"http://127.0.0.1:0"};
    settings.apiServerSettings.authentication.isEnabled = false;
    ApiKeyValidationService.resetForTesting();
    session = SessionManager.getInstance().createSession(userId, "StreamTest/1.0");
    job.sessionId = session.id;
    RoutingJobScheduler.getInstance().enqueueJob(job);
    job.board = DsnTestFixtures.loadBoard("Issue026-J2_reference.dsn");
    job.state = RoutingJobState.RUNNING;
    server = Freerouting.initializeAPI(settings.apiServerSettings);
    waitForServerStarted(server);
    int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    baseUri = URI.create("http://127.0.0.1:" + port);
    waitForApiServerReady(baseUri);
  }

  @AfterEach
  void tearDown() throws Exception {
    job.state = RoutingJobState.CANCELLED;
    try {
      stopServerGracefully(server);
    } finally {
      if (session != null) {
        RoutingJobScheduler.getInstance().clearJobs(session.id.toString());
        SessionManager.getInstance().removeSession(session.id.toString());
      }
      Freerouting.globalSettings = previousSettings;
      GlobalSettings.setCurrent(previousSettings);
      ApiKeyValidationService.resetForTesting();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = RoutingJobState.class,
      names = {"INVALID", "CANCELLED", "TIMED_OUT", "TERMINATED"})
  void terminalErrorClosesBothHttpStreamsAndTheirWorkers(RoutingJobState state) throws Exception {
    final Set<Thread> previousWorkers = workers();
    try (HttpClient client = HttpClient.newHttpClient()) {
      try {
        var sesResponse = client.sendAsync(request(false), HttpResponse.BodyHandlers.ofString());
        final var jsonResponse =
            client.sendAsync(request(true), HttpResponse.BodyHandlers.ofString());
        assertTrue(awaitWorkerCount(previousWorkers, 2), "Both HTTP streams must start polling");
        job.state = state;
        assertEquals(200, sesResponse.get(5, TimeUnit.SECONDS).statusCode());
        assertEquals(200, jsonResponse.get(5, TimeUnit.SECONDS).statusCode());
        assertTrue(
            awaitWorkerCount(previousWorkers, 0), "HTTP stream closure must release both workers");
      } finally {
        // The failing baseline must not hang HttpClient.close() with unfinished responses.
        job.state = RoutingJobState.CANCELLED;
        client.shutdownNow();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completedHttpStreamIncludesFinalSnapshotBeforeEndOfResponse(boolean json) throws Exception {
    final Set<Thread> previousWorkers = workers();
    job.output = new BoardFileDetails();
    HeadlessBoardManager manager = new HeadlessBoardManager(job);
    manager.replaceRoutingBoard(job.board);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      assertTrue(manager.saveAsSpecctraSessionSes(bytes, job.name));
      job.output.setData(bytes.toByteArray());
    }
    job.state = RoutingJobState.COMPLETED;
    try (HttpClient client = HttpClient.newHttpClient()) {
      try {
        HttpResponse<String> response =
            client
                .sendAsync(request(json), HttpResponse.BodyHandlers.ofString())
                .get(5, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("data:"));
        if (json) {
          assertTrue(response.body().contains("event: json-output"));
          String payload =
              response
                  .body()
                  .lines()
                  .filter(line -> line.startsWith("data:"))
                  .map(line -> line.substring(5).stripLeading())
                  .collect(Collectors.joining("\n"));
          assertEquals(
              JsonParser.parseString(KiCadJsonWriter.write(job.board, job.name)),
              JsonParser.parseString(payload),
              "The complete final JSON must reach HTTP clients");
        } else {
          String encoded = Base64.getEncoder().encodeToString(job.output.getData().readAllBytes());
          assertTrue(
              response.body().contains(encoded), "The complete final SES must reach HTTP clients");
        }
        assertTrue(awaitWorkerCount(previousWorkers, 0));
      } finally {
        job.state = RoutingJobState.CANCELLED;
        client.shutdownNow();
      }
    }
  }

  private HttpRequest request(boolean json) {
    String path = "/v1/jobs/" + job.id + (json ? "/output/json/stream" : "/output/stream");
    return HttpRequest.newBuilder(baseUri.resolve(path))
        .header("Freerouting-Profile-ID", userId.toString())
        .header("Freerouting-Environment-Host", "StreamTest/1.0")
        .header("Accept", "text/event-stream")
        .timeout(HTTP_TIMEOUT)
        .GET()
        .build();
  }

  private static Set<Thread> workers() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> thread.getName().startsWith("job-output-") && thread.isAlive())
        .collect(Collectors.toSet());
  }

  private static boolean awaitWorkerCount(Set<Thread> previous, int count)
      throws InterruptedException {
    long deadline = System.nanoTime() + 5_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (workers().stream().filter(thread -> !previous.contains(thread)).count() == count) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }
}
