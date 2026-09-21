package app.freerouting.surveys;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.util.gson.GsonProvider;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SurveyDefinition} and {@link SurveyResponsePayload}. */
class SurveyDefinitionTest {

  private static final String SAMPLE_JSON =
      """
      {
        "schema_version": 1,
        "id": "2026-09-routed-quality",
        "topic": "Routing quality",
        "question": "Are you happy with the routing results?",
        "options": ["Yes", "Mostly", "No"],
        "min_client_version": "2.4.0",
        "expires_at_utc": "2026-10-01T00:00:00Z"
      }
      """;

  @Test
  void deserializesFullDefinitionFromJson() {
    SurveyDefinition survey = GsonProvider.GSON.fromJson(SAMPLE_JSON, SurveyDefinition.class);

    assertEquals(1, survey.schemaVersion);
    assertEquals("2026-09-routed-quality", survey.id);
    assertEquals("Routing quality", survey.topic);
    assertEquals("Are you happy with the routing results?", survey.question);
    assertArrayEquals(new String[] {"Yes", "Mostly", "No"}, survey.options);
    assertEquals("2.4.0", survey.minClientVersion);
    assertEquals(Instant.parse("2026-10-01T00:00:00Z"), survey.expiresAtUtc);
    assertTrue(survey.isValid());
    assertTrue(survey.isSupportedSchema());
  }

  @Test
  void roundTripsThroughGsonWithoutDataLoss() {
    SurveyDefinition original = GsonProvider.GSON.fromJson(SAMPLE_JSON, SurveyDefinition.class);
    SurveyDefinition parsed =
        GsonProvider.GSON.fromJson(GsonProvider.GSON.toJson(original), SurveyDefinition.class);

    assertEquals(original.id, parsed.id);
    assertEquals(original.question, parsed.question);
    assertArrayEquals(original.options, parsed.options);
    assertEquals(original.expiresAtUtc, parsed.expiresAtUtc);
  }

  @Test
  void expiryChecks() {
    SurveyDefinition survey = GsonProvider.GSON.fromJson(SAMPLE_JSON, SurveyDefinition.class);

    assertFalse(survey.isExpired(Instant.parse("2026-09-21T12:00:00Z")));
    assertTrue(survey.isExpired(Instant.parse("2026-10-01T00:00:01Z")));
    // Missing expiry never counts as expired.
    survey.expiresAtUtc = null;
    assertFalse(survey.isExpired(Instant.now()));
  }

  @Test
  void invalidDefinitionsAreRejected() {
    SurveyDefinition survey = new SurveyDefinition();
    assertFalse(survey.isValid());

    survey.id = "x";
    assertFalse(survey.isValid());

    survey.question = "Q?";
    assertFalse(survey.isValid());

    survey.options = new String[] {"only one"};
    assertFalse(survey.isValid());

    survey.options = new String[] {"Yes", "No"};
    assertTrue(survey.isValid());
  }

  @Test
  void unknownSchemaVersionIsFlaggedUnsupported() {
    SurveyDefinition survey = GsonProvider.GSON.fromJson(SAMPLE_JSON, SurveyDefinition.class);
    assertTrue(survey.isSupportedSchema());

    survey.schemaVersion = 99;
    assertFalse(survey.isSupportedSchema());
  }

  @Test
  void responsePayloadSerializesExactlyTheFourPrivacySafeFields() {
    SurveyResponsePayload payload =
        SurveyResponsePayload.of("2026-09-routed-quality", "uuid-1234", "Yes", "v2.5.0");

    String json = GsonProvider.GSON.toJson(payload);

    // Privacy guarantee: exactly four keys, no more.
    var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    assertEquals(Set.of("survey_id", "user_id", "option", "client_version"), obj.keySet());

    assertEquals("2026-09-routed-quality", obj.get("survey_id").getAsString());
    assertEquals("uuid-1234", obj.get("user_id").getAsString());
    assertEquals("Yes", obj.get("option").getAsString());
    assertEquals("v2.5.0", obj.get("client_version").getAsString());
  }
}
