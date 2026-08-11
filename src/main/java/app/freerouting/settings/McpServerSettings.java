package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Configuration for the dedicated MCP server. */
public class McpServerSettings implements Serializable {

  /** Whether the MCP server should be started. */
  @SerializedName("enabled")
  public Boolean isEnabled = false;

  /** Whether the MCP server communicates over standard input and output. */
  @SerializedName("stdio")
  public Boolean isStdioMode = false;

  /** Whether the MCP server is currently running. */
  @SerializedName("running")
  public transient Boolean isRunning = false;

  /** Whether plain HTTP endpoints are permitted. */
  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = true;

  /** Endpoint URLs on which the MCP server listens. */
  @SerializedName("endpoints")
  public String[] endpoints = {"http://127.0.0.1:37964"};

  /** Authentication configuration for MCP requests. */
  @SerializedName("authentication")
  public ApiAuthenticationSettings authentication = new ApiAuthenticationSettings();

  /** Comma-separated origins allowed for cross-origin requests. */
  @SerializedName(
      value = "cors_origins",
      alternate = {"corsOrigins"})
  public String corsOrigins = "";

  /** Fixed-window request-rate limiting configuration. */
  @SerializedName("rate_limit")
  public RateLimitSettings rateLimit = new RateLimitSettings();

  /** Base URL of the REST API used by MCP tool calls. */
  @SerializedName("target_api_base_url")
  public String targetApiBaseUrl = "http://127.0.0.1:37864";
}
