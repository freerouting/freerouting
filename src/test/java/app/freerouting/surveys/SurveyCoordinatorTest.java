package app.freerouting.surveys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link SurveyCoordinator} eligibility gating and submit/dismiss behaviour. */
class SurveyCoordinatorTest {

  private static final String JSON =
      "{\"schema_version\":1,\"id\":\"s1\",\"topic\":\"T\",\"question\":\"Q?\","
          + "\"options\":[\"Yes\",\"No\"],\"min_client_version\":\"2.0.0\"}";

  @TempDir Path tempDir;

  private SurveyDefinition survey;

  private static SurveyDefinition parse(String json) {
    return app.freerouting.util.gson.GsonProvider.GSON.fromJson(json, SurveyDefinition.class);
  }

  @BeforeEach
  void setUp() {
    survey = parse(JSON);
  }

  private SurveyDefinition pollAndGet(SurveyCoordinator coordinator) throws Exception {
    return coordinator.pollForSurvey().get(5, TimeUnit.SECONDS);
  }

  private SurveyCoordinator coordinatorWith(
      SurveyClient mockClient, SurveyCache cache, String version, boolean allowed) {
    return new SurveyCoordinator(mockClient, cache, "u", version, () -> allowed);
  }

  private SurveyClient clientReturning(SurveyDefinition result) {
    SurveyClient mockClient = mock(SurveyClient.class);
    when(mockClient.fetchActiveSurvey())
        .thenReturn(
            result == null
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.completedFuture(result));
    return mockClient;
  }

  @Test
  void returnsNullWhenSurveysAreDisabledWithoutContactingServer() throws Exception {
    SurveyClient mockClient = mock(SurveyClient.class);

    assertNull(pollAndGet(coordinatorWith(mockClient, new SurveyCache(tempDir), "2.5.0", false)));
    verify(mockClient, never()).fetchActiveSurvey();
  }

  @Test
  void pollsOnlyOncePerSession() throws Exception {
    SurveyCoordinator coordinator =
        coordinatorWith(clientReturning(survey), new SurveyCache(tempDir), "2.5.0", true);

    assertSame(survey, pollAndGet(coordinator));
    // Second poll must not re-fetch: the coordinator already asked once this session.
    assertNull(pollAndGet(coordinator));
  }

  @Test
  void returnsNullWhenSurveyWasAlreadyAnsweredOrDismissed() throws Exception {
    SurveyCache cache = new SurveyCache(tempDir);
    cache.markAnswered("s1");

    assertNull(pollAndGet(coordinatorWith(clientReturning(survey), cache, "2.5.0", true)));
  }

  @Test
  void returnsNullWhenSurveyIsExpired() throws Exception {
    survey.expiresAtUtc = Instant.now().minusSeconds(3600);

    assertNull(
        pollAndGet(
            coordinatorWith(clientReturning(survey), new SurveyCache(tempDir), "2.5.0", true)));
  }

  @Test
  void returnsNullWhenClientIsOlderThanMinClientVersion() throws Exception {
    assertNull(
        pollAndGet(
            coordinatorWith(clientReturning(survey), new SurveyCache(tempDir), "1.9.9", true)));
  }

  @Test
  void returnsSurveyWhenClientMeetsMinClientVersion() throws Exception {
    assertSame(
        survey,
        pollAndGet(
            coordinatorWith(clientReturning(survey), new SurveyCache(tempDir), "2.0.0", true)));
  }

  @Test
  void transportFailureYieldsNullSurveyInsteadOfFailedFuture() throws Exception {
    SurveyClient mockClient = mock(SurveyClient.class);
    when(mockClient.fetchActiveSurvey())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));

    assertNull(pollAndGet(coordinatorWith(mockClient, new SurveyCache(tempDir), "2.5.0", true)));
  }

  @Test
  void submitAnswerMarksCacheAndPostsPrivacySafePayload() {
    SurveyCache cache = new SurveyCache(tempDir);
    SurveyClient mockClient = mock(SurveyClient.class);
    when(mockClient.submitResponse(any())).thenReturn(CompletableFuture.completedFuture(true));
    SurveyCoordinator coordinator =
        new SurveyCoordinator(mockClient, cache, "user-uuid", "v2.5.0", () -> true);

    coordinator.submitAnswer(survey, "Yes");

    assertTrue(cache.isHandled("s1"));
    ArgumentCaptor<SurveyResponsePayload> captor =
        ArgumentCaptor.forClass(SurveyResponsePayload.class);
    verify(mockClient).submitResponse(captor.capture());
    SurveyResponsePayload payload = captor.getValue();
    assertEquals("s1", payload.surveyId);
    assertEquals("user-uuid", payload.userId);
    assertEquals("Yes", payload.option);
    assertEquals("v2.5.0", payload.clientVersion);
  }

  @Test
  void dismissMarksCacheWithoutPosting() {
    SurveyCache cache = new SurveyCache(tempDir);
    SurveyClient mockClient = mock(SurveyClient.class);
    SurveyCoordinator coordinator =
        new SurveyCoordinator(mockClient, cache, "u", "2.5.0", () -> true);

    coordinator.dismiss(survey);

    assertTrue(cache.isHandled("s1"));
    verify(mockClient, never()).submitResponse(any());
  }

  @Test
  void failingSubmitFutureIsSwallowed() {
    SurveyCache cache = new SurveyCache(tempDir);
    SurveyClient mockClient = mock(SurveyClient.class);
    when(mockClient.submitResponse(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
    SurveyCoordinator coordinator =
        new SurveyCoordinator(mockClient, cache, "u", "2.5.0", () -> true);

    coordinator.submitAnswer(survey, "Yes");

    // Ask-once holds even though the network request failed.
    assertTrue(cache.isHandled("s1"));
  }
}
