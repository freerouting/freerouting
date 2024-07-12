package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

public class ApiServerSettings
{
  @SerializedName("enabled")
  public Boolean isEnabled = false;
  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = false;
  @SerializedName("endpoints")
  public String[] endpoints = {"https://localhost:8080"};
}