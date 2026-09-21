package app.freerouting.surveys;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

/**
 * Definition of the currently active micro-survey, served by {@code GET /v1/surveys/active}.
 *
 * <p>This is a data-transfer object deserialized with {@link
 * app.freerouting.util.gson.GsonProvider} and must stay free of any UI or API-server dependencies.
 *
 * <p>Maintainers change the active survey at runtime by updating the {@code
 * FREEROUTING__SURVEYS__ACTIVE_SURVEY} environment variable on the API host — no desktop client
 * release is required.
 */
public class SurveyDefinition {

  /** Schema version of the survey payload. Clients skip definitions with an unknown version. */
  @SerializedName("schema_version")
  public int schemaVersion = 1;

  /** Unique, never-recycled survey identifier. */
  @SerializedName("id")
  public String id;

  /** Short topic label shown in the trigger pill (e.g. "Autorouting quality"). */
  @SerializedName("topic")
  public String topic;

  /** The question shown in the popover. */
  @SerializedName("question")
  public String question;

  /** Two to three answer options. Clicking one is the submission — there is no submit button. */
  @SerializedName("options")
  public String[] options = new String[0];

  /**
   * Minimum desktop client version required to display this survey. Prevents surveys about UI
   * features that do not exist in older clients.
   */
  @SerializedName("min_client_version")
  public String minClientVersion;

  /** Expiry timestamp (ISO-8601). Expired surveys are hidden client- and server-side. */
  @SerializedName("expires_at_utc")
  public Instant expiresAtUtc;

  /** The lowest schema version this client understands. */
  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  /**
   * Returns {@code true} if this survey has expired relative to {@code now}. A missing expiry never
   * counts as expired.
   */
  public boolean isExpired(Instant now) {
    return expiresAtUtc != null && now != null && now.isAfter(expiresAtUtc);
  }

  /** Returns {@code true} if this payload uses a schema version this client can render. */
  public boolean isSupportedSchema() {
    return schemaVersion == SUPPORTED_SCHEMA_VERSION;
  }

  /** Returns {@code true} if the definition carries the minimum data needed to be displayed. */
  public boolean isValid() {
    return id != null
        && !id.isBlank()
        && question != null
        && !question.isBlank()
        && options != null
        && options.length >= 2;
  }
}
