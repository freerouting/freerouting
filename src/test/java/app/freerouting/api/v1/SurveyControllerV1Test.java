package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@code GET /v1/surveys/active} decision logic of {@link SurveyControllerV1}.
 */
class SurveyControllerV1Test {

  private static final String VALID_JSON =
      "{\"schema_version\":1,\"id\":\"s1\",\"topic\":\"T\",\"question\":\"Q?\","
          + "\"options\":[\"Yes\",\"No\"],\"expires_at_utc\":\"2030-01-01T00:00:00Z\"}";

  private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

  @Test
  void returns204WhenEnvVarIsUnset() {
    assertEquals(204, SurveyControllerV1.buildActiveSurveyResponse(null, NOW).getStatus());
    assertEquals(204, SurveyControllerV1.buildActiveSurveyResponse("   ", NOW).getStatus());
  }

  @Test
  void returns200WithVerbatimJsonForValidSurvey() {
    Response response = SurveyControllerV1.buildActiveSurveyResponse(VALID_JSON, NOW);

    assertEquals(200, response.getStatus());
    assertEquals(VALID_JSON, response.getEntity());
  }

  @Test
  void returns204ForExpiredSurvey() {
    String expiredJson = VALID_JSON.replace("2030-01-01T00:00:00Z", "2020-01-01T00:00:00Z");

    assertEquals(204, SurveyControllerV1.buildActiveSurveyResponse(expiredJson, NOW).getStatus());
  }

  @Test
  void returns204ForSurveyMissingRequiredFields() {
    String invalidJson = "{\"schema_version\":1,\"id\":\"s1\"}";

    assertEquals(204, SurveyControllerV1.buildActiveSurveyResponse(invalidJson, NOW).getStatus());
  }

  @Test
  void returns500ForMalformedJson() {
    Response response = SurveyControllerV1.buildActiveSurveyResponse("{ this is not json", NOW);

    assertEquals(500, response.getStatus());
  }

  @Test
  void recordsNewSurveyResponseSuccessfully() {
    java.util.concurrent.atomic.AtomicBoolean recorded =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicReference<String> recOption =
        new java.util.concurrent.atomic.AtomicReference<>();

    String body =
        "{\"survey_id\":\"s1\",\"user_id\":\"u-123\",\"option\":\"Yes\",\"client_version\":\"2.5.0\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1",
            body,
            (s, u) -> false,
            (s, u, opt, ver) -> {
              recorded.set(true);
              recOption.set(opt);
            });

    assertEquals(200, response.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(recorded.get());
    assertEquals("Yes", recOption.get());
  }

  @Test
  void deduplicatesWhenResponseAlreadyExists() {
    java.util.concurrent.atomic.AtomicBoolean recorded =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    String body = "{\"survey_id\":\"s1\",\"user_id\":\"u-123\",\"option\":\"Yes\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", body, (s, u) -> true, (s, u, opt, ver) -> recorded.set(true));

    assertEquals(204, response.getStatus());
    org.junit.jupiter.api.Assertions.assertFalse(recorded.get());
  }

  @Test
  void rejectsMissingPathSurveyId() {
    String body = "{\"user_id\":\"u-123\",\"option\":\"Yes\"}";
    Response resp1 =
        SurveyControllerV1.processSurveyResponse(
            null, body, (s, u) -> false, (s, u, opt, ver) -> {});
    Response resp2 =
        SurveyControllerV1.processSurveyResponse(
            "   ", body, (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, resp1.getStatus());
    assertEquals(400, resp2.getStatus());
  }

  @Test
  void rejectsMissingRequestBody() {
    Response resp1 =
        SurveyControllerV1.processSurveyResponse(
            "s1", null, (s, u) -> false, (s, u, opt, ver) -> {});
    Response resp2 =
        SurveyControllerV1.processSurveyResponse(
            "s1", "   ", (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, resp1.getStatus());
    assertEquals(400, resp2.getStatus());
  }

  @Test
  void rejectsMalformedJsonBody() {
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", "{ not json", (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMissingUserId() {
    String body = "{\"survey_id\":\"s1\",\"option\":\"Yes\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", body, (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMissingOption() {
    String body = "{\"survey_id\":\"s1\",\"user_id\":\"u-123\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", body, (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, response.getStatus());
  }

  @Test
  void rejectsMismatchedSurveyId() {
    String body = "{\"survey_id\":\"other-survey\",\"user_id\":\"u-123\",\"option\":\"Yes\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", body, (s, u) -> false, (s, u, opt, ver) -> {});

    assertEquals(400, response.getStatus());
  }

  @Test
  void acceptsPayloadWithOmittedSurveyIdAndUsesPathId() {
    java.util.concurrent.atomic.AtomicReference<String> recSurvey =
        new java.util.concurrent.atomic.AtomicReference<>();

    String body = "{\"user_id\":\"u-123\",\"option\":\"No\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s-from-path", body, (s, u) -> false, (s, u, opt, ver) -> recSurvey.set(s));

    assertEquals(200, response.getStatus());
    assertEquals("s-from-path", recSurvey.get());
  }
}
