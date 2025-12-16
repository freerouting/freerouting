package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.UUID;

public class UserProfileSettings implements Serializable {

  @SerializedName("id")
  public final String userId;
  @SerializedName("email")
  public String userEmail = "";
  @SerializedName("allow_telemetry")
  public Boolean isTelemetryAllowed = true;
  @SerializedName("allow_contact")
  public Boolean isContactAllowed = true;

  public UserProfileSettings() {
    this.userId = UUID.randomUUID().toString();
  }
}