package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Generic fixed-window rate-limit settings.
 */
public class RateLimitSettings implements Serializable {

  @SerializedName("enabled")
  public Boolean enabled = false;

  @SerializedName("requests_per_window")
  public Integer requestsPerWindow = 120;

  @SerializedName("window_seconds")
  public Integer windowSeconds = 60;
}