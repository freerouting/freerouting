package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Network and lifecycle configuration for the REST API server. */
public class ApiServerSettings implements Serializable {

  /** Whether the API server should be started. */
  @SerializedName("enabled")
  public Boolean isEnabled = false;

  /** Whether the API server is currently running. */
  @SerializedName("running")
  public transient Boolean isRunning = false;

  /** Whether plain HTTP endpoints are permitted. */
  @SerializedName("http_allowed")
  public Boolean isHttpAllowed = true;

  /** Endpoint URLs on which the API server listens. */
  @SerializedName("endpoints")
  public String[] endpoints = {"http://127.0.0.1:37864"};

  /** Authentication configuration for API requests. */
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

  /** Maximum number of routing jobs the scheduler runs concurrently. */
  @SerializedName("max_parallel_jobs")
  public Integer maxParallelJobs = 5;
}
