package app.freerouting.surveys;

import com.google.gson.annotations.SerializedName;

/**
 * Payload POSTed to {@code POST /v1/surveys/{surveyId}/response}.
 *
 * <p>Privacy: this is deliberately the entire payload — no board files, OS usernames, IP or MAC
 * addresses are ever included.
 */
public class SurveyResponsePayload {

  /** The survey that was answered. */
  @SerializedName("survey_id")
  public String surveyId;

  /** The anonymous profile UUID ({@code globalSettings.userProfileSettings.userId}). */
  @SerializedName("user_id")
  public String userId;

  /** The option the user clicked. Clicking the option IS the submission. */
  @SerializedName("option")
  public String option;

  /** The desktop client version, for cohort analysis. */
  @SerializedName("client_version")
  public String clientVersion;

  /** Creates a fully populated response payload. */
  public static SurveyResponsePayload of(
      String surveyId, String userId, String option, String clientVersion) {
    SurveyResponsePayload payload = new SurveyResponsePayload();
    payload.surveyId = surveyId;
    payload.userId = userId;
    payload.option = option;
    payload.clientVersion = clientVersion;
    return payload;
  }
}
