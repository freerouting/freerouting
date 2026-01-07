package app.freerouting.settings;

import app.freerouting.management.TextManager;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class UsageAndDiagnosticDataSettings implements Serializable {

  @SerializedName("disable_analytics")
  public boolean disableAnalytics;
  /*
   * The bigquery_service_account_key is JSON file that is generated when a new
   * service account key is created in the Google Cloud Console / IAM & Admin /
   * Service accounts / Keys.
   * 
   * The value of this key is set to the environment variable
   * FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY.
   * 
   * This key is used strictly for analytics purposes and only by the API.
   * 
   * The desktop and CLI clients do not use this key, they call the API endpoint
   * for analytics.
   */
  @SerializedName("bigquery_service_account_key")
  public transient String bigqueryServiceAccountKey;
  @SerializedName("logger_key")
  public transient String loggerKey = TextManager.generateRandomAlphanumericString(32);
}