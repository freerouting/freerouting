package app.freerouting.api.dto;

import app.freerouting.settings.EnvironmentSettings;
import com.google.gson.annotations.SerializedName;

public class SystemStatus
{
  @SerializedName("status")
  public String status;
  @SerializedName("cpu_load")
  public double cpuLoad;
  @SerializedName("ram_used")
  public int ramUsed;
  @SerializedName("ram_available")
  public int ramAvailable;
  @SerializedName("storage_available")
  public int storageAvailable;
  @SerializedName("session_count")
  public int sessionCount;
  @SerializedName("environment")
  public EnvironmentSettings environment;
}