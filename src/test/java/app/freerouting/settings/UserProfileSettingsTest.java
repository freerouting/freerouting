package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.util.gson.GsonProvider;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserProfileSettings#allowSurveys} and its resolution rule. */
class UserProfileSettingsTest {

  @Test
  void allowSurveysIsNullByDefaultSoSourcesCarryNoOpinion() {
    UserProfileSettings settings = new UserProfileSettings();

    assertNull(settings.allowSurveys);
  }

  @Test
  void nullAllowSurveysInheritsTelemetryPreference() {
    UserProfileSettings settings = new UserProfileSettings();
    settings.isTelemetryAllowed = true;
    assertTrue(settings.isSurveysAllowed(false));
    assertFalse(settings.isSurveysAllowed(true));

    settings.isTelemetryAllowed = false;
    assertFalse(settings.isSurveysAllowed(false));
  }

  @Test
  void explicitAllowSurveysOverridesTelemetryPreference() {
    UserProfileSettings settings = new UserProfileSettings();
    settings.isTelemetryAllowed = false;
    settings.allowSurveys = true;
    assertTrue(settings.isSurveysAllowed(false));

    settings.allowSurveys = false;
    settings.isTelemetryAllowed = true;
    assertFalse(settings.isSurveysAllowed(false));
  }

  @Test
  void analyticsDisabledAlwaysDisablesSurveys() {
    UserProfileSettings settings = new UserProfileSettings();
    settings.allowSurveys = true;

    assertFalse(settings.isSurveysAllowed(true));
  }

  @Test
  void roundTripsThroughSettingsJson() {
    UserProfileSettings settings = new UserProfileSettings();
    settings.allowSurveys = false;

    UserProfileSettings parsed =
        GsonProvider.GSON.fromJson(GsonProvider.GSON.toJson(settings), UserProfileSettings.class);
    assertEquals(Boolean.FALSE, parsed.allowSurveys);
  }

  @Test
  void unsetAllowSurveysIsOmittedFromJson() {
    UserProfileSettings settings = new UserProfileSettings();
    String json = GsonProvider.GSON.toJson(settings);

    assertFalse(json.contains("allow_surveys"));
  }
}
