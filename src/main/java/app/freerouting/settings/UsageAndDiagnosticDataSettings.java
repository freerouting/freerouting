package app.freerouting.settings;

import app.freerouting.management.TextManager;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class UsageAndDiagnosticDataSettings implements Serializable
{
  @SerializedName("disable_analytics")
  public boolean disableAnalytics = false;
  @SerializedName("segment_write_key")
  public transient String segmentWriteKey = "G24pcCv4BmnqwBa8LsdODYRE6k9IAlqR";
  @SerializedName("bigquery_service_account_key")
  public transient String bigqueryServiceAccountKey = null;
  @SerializedName("logger_key")
  public transient String loggerKey = TextManager.generateRandomAlphanumericString(32);
}