package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Generic fixed-window rate-limit settings. */
public class RateLimitSettings implements Serializable {

  /** Whether rate limiting is enabled. */
  @SerializedName("enabled")
  public Boolean enabled = false;

  /** Maximum number of requests accepted in one window. */
  @SerializedName("requests_per_window")
  public Integer requestsPerWindow = 120;

  /** Length of the fixed rate-limit window in seconds. */
  @SerializedName("window_seconds")
  public Integer windowSeconds = 60;
}
