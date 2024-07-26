package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class UserProfileSettings
{
  @SerializedName("id")
  public final String user_id;
  @SerializedName("email")
  public String user_email = "";

  public UserProfileSettings()
  {
    this.user_id = UUID.randomUUID().toString();
  }
}