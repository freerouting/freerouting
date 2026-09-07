package app.freerouting.api.dto;

import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DrcSummaryResponse;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composite response returned by single-turn autorouting (POST /v1/autoroute).
 *
 * <p>Contains final job state, duration, board statistics, DRC summary diagnostics, and all
 * requested output files (e.g. SES, KiCad JSON, Fusion SCR, DRC JSON).
 */
@Schema(
    name = "AutorouteResponse",
    description =
        "Single-turn autorouting response containing routed outputs, status, and DRC metrics")
public class AutorouteResponse {

  @SerializedName("job_id")
  @Schema(description = "Unique ID of the executed routing job")
  public String jobId;

  @SerializedName("session_id")
  @Schema(description = "Session ID under which the job ran")
  public String sessionId;

  @SerializedName("status")
  @Schema(description = "Final job status (e.g. COMPLETED, TIMED_OUT, CANCELLED, FAILED)")
  public RoutingJobState status;

  @SerializedName("duration_seconds")
  @Schema(description = "Elapsed execution time in seconds")
  public double durationSeconds;

  @SerializedName("unrouted_connections")
  @Schema(description = "Number of remaining unrouted connections / air-lines")
  public int unroutedConnections;

  @SerializedName("clearance_violations")
  @Schema(description = "Total clearance violations detected")
  public int clearanceViolations;

  @SerializedName("normalized_score")
  @Schema(description = "Normalized routing score (0.0 to 1.0, or higher with penalties)")
  public Float normalizedScore;

  @SerializedName("outputs")
  @Schema(description = "Map of generated output format names to their text or Base64 content")
  public Map<String, String> outputs = new LinkedHashMap<>();

  @SerializedName("drc_summary")
  @Schema(description = "Diagnostic DRC summary with root causes and layout hints if requested")
  public DrcSummaryResponse drcSummary;

  @SerializedName("message")
  @Schema(description = "Status or informational message")
  public String message;
}
