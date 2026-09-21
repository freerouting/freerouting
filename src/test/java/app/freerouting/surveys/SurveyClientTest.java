package app.freerouting.surveys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SurveyClient}: happy paths over a local HTTP server, and the
 * offline/timeout contract (silent failures, futures that always complete, no exceptions).
 */
class SurveyClientTest {

  private HttpServer server;
  private SurveyClient client;

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.shutdown();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  private String startServer(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/surveys/active",
        exchange -> {
          byte[] bytes = body == null ? new byte[0] : body.getBytes();
          if (status == 200) {
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          } else {
            exchange.sendResponseHeaders(status, -1);
          }
          exchange.close();
        });
    server.createContext(
        "/v1/surveys/",
        exchange -> {
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
  }

  @Test
  void fetchParsesActiveSurveyDefinition() throws Exception {
    String json =
        "{\"schema_version\":1,\"id\":\"s1\",\"topic\":\"T\",\"question\":\"Q?\","
            + "\"options\":[\"Yes\",\"No\"],\"min_client_version\":\"2.0.0\"}";
    client = new SurveyClient(startServer(200, json));

    SurveyDefinition survey = client.fetchActiveSurveyBlocking();

    assertNotNull(survey);
    assertEquals("s1", survey.id);
    assertEquals("Q?", survey.question);
  }

  @Test
  void fetchReturnsNullOn204NoActiveSurvey() throws Exception {
    client = new SurveyClient(startServer(204, null));

    assertNull(client.fetchActiveSurveyBlocking());
  }

  @Test
  void fetchReturnsNullOnErrorStatusWithoutThrowing() throws Exception {
    client = new SurveyClient(startServer(500, null));

    assertNull(client.fetchActiveSurveyBlocking());
  }

  @Test
  void fetchFailsSilentlyWhenServerIsUnreachable() {
    // Port 1 on localhost is refused immediately — simulates an offline/timeout state.
    client = new SurveyClient("http://127.0.0.1:1/v1/");

    assertNull(client.fetchActiveSurveyBlocking());
  }

  @Test
  void asyncFetchFutureAlwaysCompletesEvenWhenOffline() throws Exception {
    client = new SurveyClient("http://127.0.0.1:1/v1/");

    CompletableFuture<SurveyDefinition> future = client.fetchActiveSurvey();

    assertNull(future.get(12, TimeUnit.SECONDS));
  }

  @Test
  void submitReportsDeliveredOnHttp200() throws Exception {
    client = new SurveyClient(startServer(200, null));

    assertTrue(client.submitResponseBlocking(SurveyResponsePayload.of("s1", "u1", "Yes", "v1")));
  }

  @Test
  void submitTreatsDuplicateAnswerAsDelivered() throws Exception {
    client = new SurveyClient(startServer(409, null));

    assertTrue(client.submitResponseBlocking(SurveyResponsePayload.of("s1", "u1", "Yes", "v1")));
  }

  @Test
  void submitFailsSilentlyWhenServerIsUnreachable() {
    client = new SurveyClient("http://127.0.0.1:1/v1/");

    assertFalse(client.submitResponseBlocking(SurveyResponsePayload.of("s1", "u1", "Yes", "v1")));
  }

  @Test
  void whenCompleteSilentlySwallowsCallbackExceptions() throws Exception {
    CompletableFuture<Object> failing = new CompletableFuture<>();
    failing.complete(new Object());

    SurveyClient.whenCompleteSilently(
        failing,
        value -> {
          throw new RuntimeException("callback bug");
        });

    // The completing thread is unaffected; a succeeding callback still fires.
    CompletableFuture<Object> ok = new CompletableFuture<>();
    ok.complete("value");
    CompletableFuture<Object> seen = new CompletableFuture<>();
    SurveyClient.whenCompleteSilently(ok, seen::complete);

    assertEquals("value", seen.get(1, TimeUnit.SECONDS));
  }

  @Test
  void localActiveSurveyOverrideParsesWithoutNetwork() {
    String json =
        "{\"schema_version\":1,\"id\":\"local-s1\",\"topic\":\"Local\",\"question\":\"Testing?\",\"options\":[\"A\",\"B\"]}";
    System.setProperty(SurveyClient.ACTIVE_SURVEY_PROP, json);
    try {
      SurveyClient localClient = new SurveyClient("http://127.0.0.1:1/v1/");
      SurveyDefinition survey = localClient.fetchActiveSurveyBlocking();
      assertNotNull(survey);
      assertEquals("local-s1", survey.id);
      assertEquals("Local", survey.topic);
      assertTrue(
          localClient.submitResponseBlocking(
              SurveyResponsePayload.of("local-s1", "u", "A", "1.0")));
      localClient.shutdown();
    } finally {
      System.clearProperty(SurveyClient.ACTIVE_SURVEY_PROP);
    }
  }
}
