package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** User identity and communication preferences. */
public class UserProfileSettings implements Serializable {

  /** Stable identifier for the user profile. */
  @SerializedName("id")
  public String userId;

  /** Email address associated with the user profile. */
  @SerializedName("email")
  public String userEmail = "";

  /** Whether telemetry collection is permitted. */
  @SerializedName("allow_telemetry")
  public Boolean isTelemetryAllowed = true;

  /** Whether Freerouting may contact the user. */
  @SerializedName("allow_contact")
  public Boolean isContactAllowed = true;

  /**
   * Whether the user accepts in-app micro-surveys. Nullable on purpose: {@code null} means "no
   * opinion" and resolves to the telemetry preference, so the SettingsMerger can distinguish a
   * source that sets this field from one that does not.
   */
  @SerializedName("allow_surveys")
  public Boolean allowSurveys;

  /**
   * Resolves whether survey participation is allowed, given the global analytics switch.
   *
   * @param analyticsDisabled the value of {@code usageAndDiagnosticData.disableAnalytics}
   * @return {@code false} when analytics is disabled; otherwise the explicit {@code allowSurveys}
   *     preference, falling back to the telemetry preference when unset
   */
  public boolean isSurveysAllowed(boolean analyticsDisabled) {
    if (analyticsDisabled) {
      return false;
    }
    if (allowSurveys != null) {
      return allowSurveys;
    }
    return Boolean.TRUE.equals(isTelemetryAllowed);
  }
}
