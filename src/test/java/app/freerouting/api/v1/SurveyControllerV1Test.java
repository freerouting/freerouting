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
}
