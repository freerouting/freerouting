package app.freerouting.settings;

import app.freerouting.util.TextManager;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Settings controlling usage analytics and diagnostic-data collection. */
public class UsageAndDiagnosticDataSettings implements Serializable {

  /** Whether analytics collection is disabled. */
  @SerializedName("disable_analytics")
  public boolean disableAnalytics;

  /**
   * The {@code bigquery_service_account_key} is a JSON file generated when a new service account
   * key is created in the Google Cloud Console / IAM & Admin / Service accounts / Keys.
   *
   * <p>The value of this key is set to the environment variable
   * FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY.
   *
   * <p>This key is used strictly for analytics purposes and only by the API.
   *
   * <p>The desktop and CLI clients do not use this key; they call the API endpoint for analytics.
   */
  @SerializedName("bigquery_service_account_key")
  public transient String bigqueryServiceAccountKey;

  /** Internal key used to correlate logger events. */
  @SerializedName("logger_key")
  public transient String loggerKey = TextManager.generateRandomAlphanumericString(32);

  /** High-volume, low-value GUI navigation events. Disabled by default to reduce BigQuery cost. */
  @SerializedName("track_window_changed")
  public boolean trackWindowChanged;

  /**
   * High-volume, low-value GUI click events. Disabled by default; use {@code Profile Updated} and
   * {@code File Saved} for actionable signals instead.
   */
  @SerializedName("track_button_clicked")
  public boolean trackButtonClicked;
}
