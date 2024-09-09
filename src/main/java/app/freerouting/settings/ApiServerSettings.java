package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ApiServerSettings implements Serializable
{
  @SerializedName("enabled")
  public Boolean isEnabled = false;
  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = false;
  @SerializedName("endpoints")
  public String[] endpoints = {"https://localhost:37864"};
}