package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.time.Instant;

public class EnvironmentSettings implements Serializable
{
  @SerializedName("freerouting_version")
  public String freeroutingVersion;
  @SerializedName("app_started_at")
  public Instant appStartedAt;
  @SerializedName("command_line_arguments")
  public String commandLineArguments;
  @SerializedName("architecture")
  public String architecture;
  @SerializedName("java")
  public String java;
  @SerializedName("system_language")
  public String systemLanguage;
  @SerializedName("cpu_cores")
  public int cpuCores;
  @SerializedName("ram")
  public int ram;
  @SerializedName("host")
  public transient String host = "N/A";
}