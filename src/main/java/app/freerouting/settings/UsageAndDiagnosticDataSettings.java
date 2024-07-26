package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class UsageAndDiagnosticDataSettings implements Serializable
{
  @SerializedName("disable_analytics")
  public boolean disableAnalytics = false;
  @SerializedName("segment_write_key")
  public String segmentWriteKey = "";
  @SerializedName("analytics_modulo")
  public int analyticsModulo = 16;
}