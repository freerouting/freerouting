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
}
