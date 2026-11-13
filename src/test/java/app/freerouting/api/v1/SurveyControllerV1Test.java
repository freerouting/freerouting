package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link SurveyControllerV1} endpoints and decision logic. */
class SurveyControllerV1Test {

  private static final String VALID_JSON =
      "{\"schema_version\":1,\"id\":\"s1\",\"topic\":\"T\",\"question\":\"Q?\","
          + "\"options\":[\"Yes\",\"No\"],\"expires_at_utc\":\"2030-01-01T00:00:00Z\"}";

  private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
  private static final String ADMIN_KEY = "test-secret-admin-key-999";

  @AfterEach
  void tearDown() {
    SurveyControllerV1.resetDynamicActiveSurvey();
  }

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

  // --- Admin Publishing & Deletion Tests ---

  @Test
  void publishReturns403WhenServerAdminKeyNotConfigured() {
    Response response =
        SurveyControllerV1.processPublishActiveSurvey(
            "Bearer " + ADMIN_KEY, null, null, VALID_JSON, NOW, json -> {});

    assertEquals(403, response.getStatus());
  }

  @Test
  void publishReturns401WhenAuthHeaderMissingOrInvalid() {
    Response missing =
        SurveyControllerV1.processPublishActiveSurvey(
            null, null, ADMIN_KEY, VALID_JSON, NOW, json -> {});
    Response invalid =
        SurveyControllerV1.processPublishActiveSurvey(
            "Bearer wrong-key", null, ADMIN_KEY, VALID_JSON, NOW, json -> {});

    assertEquals(401, missing.getStatus());
    assertEquals(401, invalid.getStatus());
  }

  @Test
  void publishReturns400OnMissingOrMalformedBody() {
    Response missing =
        SurveyControllerV1.processPublishActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, null, NOW, json -> {});
    Response malformed =
        SurveyControllerV1.processPublishActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, "{ not json", NOW, json -> {});

    assertEquals(400, missing.getStatus());
    assertEquals(400, malformed.getStatus());
  }

  @Test
  void publishReturns400OnInvalidOrExpiredSurvey() {
    String incompleteJson = "{\"schema_version\":1,\"id\":\"s1\"}";
    Response incomplete =
        SurveyControllerV1.processPublishActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, incompleteJson, NOW, json -> {});

    String expiredJson = VALID_JSON.replace("2030-01-01T00:00:00Z", "2020-01-01T00:00:00Z");
    Response expired =
        SurveyControllerV1.processPublishActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, expiredJson, NOW, json -> {});

    assertEquals(400, incomplete.getStatus());
    assertEquals(400, expired.getStatus());
  }

  @Test
  void publishSucceedsWithCustomAdminHeader() {
    AtomicReference<String> publishedJson = new AtomicReference<>();
    Response response =
        SurveyControllerV1.processPublishActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, VALID_JSON, NOW, publishedJson::set);

    assertEquals(200, response.getStatus());
    assertEquals(VALID_JSON, publishedJson.get());
  }

  @Test
  void publishSucceedsWithBearerAuthorizationHeader() {
    AtomicReference<String> publishedJson = new AtomicReference<>();
    Response response =
        SurveyControllerV1.processPublishActiveSurvey(
            "Bearer " + ADMIN_KEY, null, ADMIN_KEY, VALID_JSON, NOW, publishedJson::set);

    assertEquals(200, response.getStatus());
    assertEquals(VALID_JSON, publishedJson.get());
  }

  @Test
  void publishSucceedsWithApiKeyAuthorizationHeader() {
    AtomicReference<String> publishedJson = new AtomicReference<>();
    Response response =
        SurveyControllerV1.processPublishActiveSurvey(
            "ApiKey " + ADMIN_KEY, null, ADMIN_KEY, VALID_JSON, NOW, publishedJson::set);

    assertEquals(200, response.getStatus());
    assertEquals(VALID_JSON, publishedJson.get());
  }

  @Test
  void resolveAdminKeyReadsSystemProperty() {
    System.setProperty(SurveyControllerV1.ADMIN_KEY_PROP, "custom-prop-key");
    try {
      assertEquals("custom-prop-key", SurveyControllerV1.resolveAdminKey());
    } finally {
      System.clearProperty(SurveyControllerV1.ADMIN_KEY_PROP);
    }
  }

  @Test
  void deleteReturns403WhenServerAdminKeyNotConfigured() {
    Response response =
        SurveyControllerV1.processDeleteActiveSurvey("Bearer " + ADMIN_KEY, null, null, () -> {});

    assertEquals(403, response.getStatus());
  }

  @Test
  void deleteReturns401WhenAuthHeaderMissingOrInvalid() {
    Response missing =
        SurveyControllerV1.processDeleteActiveSurvey(null, null, ADMIN_KEY, () -> {});
    Response invalid =
        SurveyControllerV1.processDeleteActiveSurvey(null, "wrong-key", ADMIN_KEY, () -> {});

    assertEquals(401, missing.getStatus());
    assertEquals(401, invalid.getStatus());
  }

  @Test
  void deleteSucceedsWithValidKeyAndInvokesClearer() {
    AtomicBoolean cleared = new AtomicBoolean(false);
    Response response =
        SurveyControllerV1.processDeleteActiveSurvey(
            null, ADMIN_KEY, ADMIN_KEY, () -> cleared.set(true));

    assertEquals(200, response.getStatus());
    assertTrue(cleared.get());
  }

  @Test
  void dynamicActiveSurveyLifecycle() {
    // Initially null, falls back to env var
    SurveyControllerV1.resetDynamicActiveSurvey();

    // Dynamically published
    SurveyControllerV1.setDynamicActiveSurvey(VALID_JSON);
    assertEquals(VALID_JSON, SurveyControllerV1.getEffectiveActiveSurveyJson());

    // Dynamically retired
    SurveyControllerV1.setDynamicActiveSurvey("");
    assertEquals("", SurveyControllerV1.getEffectiveActiveSurveyJson());
    assertEquals(204, SurveyControllerV1.buildActiveSurveyResponse("", NOW).getStatus());

    // Reset back to null
    SurveyControllerV1.resetDynamicActiveSurvey();
  }

  // --- Response Submission Tests ---

  @Test
  void recordsNewSurveyResponseSuccessfully() {
    AtomicBoolean recorded = new AtomicBoolean(false);
    AtomicReference<String> recOption = new AtomicReference<>();

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
    assertTrue(recorded.get());
    assertEquals("Yes", recOption.get());
  }

  @Test
  void deduplicatesWhenResponseAlreadyExists() {
    AtomicBoolean recorded = new AtomicBoolean(false);

    String body = "{\"survey_id\":\"s1\",\"user_id\":\"u-123\",\"option\":\"Yes\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s1", body, (s, u) -> true, (s, u, opt, ver) -> recorded.set(true));

    assertEquals(204, response.getStatus());
    assertFalse(recorded.get());
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
    AtomicReference<String> recSurvey = new AtomicReference<>();

    String body = "{\"user_id\":\"u-123\",\"option\":\"No\"}";
    Response response =
        SurveyControllerV1.processSurveyResponse(
            "s-from-path", body, (s, u) -> false, (s, u, opt, ver) -> recSurvey.set(s));

    assertEquals(200, response.getStatus());
    assertEquals("s-from-path", recSurvey.get());
  }
}
