package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ApiServerSettings implements Serializable {

  @SerializedName("enabled")
  public Boolean isEnabled = false;
  @SerializedName("running")
  public transient Boolean isRunning = false;
  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = true;
  @SerializedName("endpoints")
  public String[] endpoints = {"https://0.0.0.0:37864"};
  @SerializedName("keys_location")
  public ApiKeysLocationSettings keysLocation = new ApiKeysLocationSettings();
  @SerializedName("cors_origins")
  public String cors_origins = "";
}