package app.freerouting.core;

import com.google.gson.annotations.SerializedName;

public class RouterJobResourceUsage
{
  // Total CPU time used in seconds
  @SerializedName("cpu_time")
  public float cpuTimeUsed = 0.0f;
  // Maximum memory used in MB
  @SerializedName("max_memory")
  public float maxMemoryUsed = 0.0f;
  // Total IO read in MB, including disk only (excluding network and other read operations)
  @SerializedName("io_read")
  public float ioRead = 0.0f;
  // Total IO write in MB, including disk only (excluding network and other write operations)
  @SerializedName("io_written")
  public float ioWrite = 0.0f;
}