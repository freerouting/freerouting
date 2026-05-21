package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Configuration for the dedicated MCP server.
 */
public class McpServerSettings implements Serializable {

  @SerializedName("enabled")
  public Boolean isEnabled = false;

  @SerializedName("running")
  public transient Boolean isRunning = false;

  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = true;

  @SerializedName("endpoints")
  public String[] endpoints = {"http://127.0.0.1:37964"};

  @SerializedName("authentication")
  public ApiAuthenticationSettings authentication = new ApiAuthenticationSettings();

  @SerializedName("cors_origins")
  public String cors_origins = "";

  @SerializedName("rate_limit")
  public RateLimitSettings rateLimit = new RateLimitSettings();

  @SerializedName("target_api_base_url")
  public String targetApiBaseUrl = "http://127.0.0.1:37864";
}