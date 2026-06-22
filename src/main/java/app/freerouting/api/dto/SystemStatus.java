package app.freerouting.api.dto;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "System status information including resource usage and session count")
public class SystemStatus {

  @SerializedName("status")
  @Schema(name = "status", description = "Overall system status", example = "OK")
  public String status;
  @SerializedName("cpu_load")
  @Schema(name = "cpu_load", description = "Current CPU load percentage (0-100)", example = "45.5")
  public double cpuLoad;
  @SerializedName("ram_used")
  @Schema(name = "ram_used", description = "RAM currently in use (in MB)", example = "512")
  public int ramUsed;
  @SerializedName("ram_available")
  @Schema(name = "ram_available", description = "Available RAM (in MB)", example = "1024")
  public int ramAvailable;
  @SerializedName("storage_available")
  @Schema(name = "storage_available", description = "Available storage space (in MB)", example = "10240")
  public int storageAvailable;
  @SerializedName("session_count")
  @Schema(name = "session_count", description = "Number of active routing sessions", example = "3")
  public int sessionCount;
}