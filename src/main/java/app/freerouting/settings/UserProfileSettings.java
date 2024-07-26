package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.UUID;

public class UserProfileSettings implements Serializable
{
  @SerializedName("id")
  public final String userId;
  @SerializedName("email")
  public String userEmail = "";

  public UserProfileSettings()
  {
    this.userId = UUID.randomUUID().toString();
  }
}