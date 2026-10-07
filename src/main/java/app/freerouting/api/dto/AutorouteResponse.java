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

  @SerializedName(
      value = "job_id",
      alternate = {"jobId"})
  @Schema(name = "job_id", description = "Unique ID of the executed routing job")
  public String jobId;

  @SerializedName(
      value = "session_id",
      alternate = {"sessionId"})
  @Schema(name = "session_id", description = "Session ID under which the job ran")
  public String sessionId;

  @SerializedName("status")
  @Schema(
      name = "status",
      description = "Final job status (e.g. COMPLETED, TIMED_OUT, CANCELLED, FAILED)")
  public RoutingJobState status;

  @SerializedName(
      value = "duration_seconds",
      alternate = {"durationSeconds"})
  @Schema(name = "duration_seconds", description = "Elapsed execution time in seconds")
  public double durationSeconds;

  @SerializedName(
      value = "unrouted_connections",
      alternate = {"unroutedConnections"})
  @Schema(
      name = "unrouted_connections",
      description = "Number of remaining unrouted connections / air-lines")
  public int unroutedConnections;

  @SerializedName(
      value = "clearance_violations",
      alternate = {"clearanceViolations"})
  @Schema(name = "clearance_violations", description = "Total clearance violations detected")
  public int clearanceViolations;

  @SerializedName(
      value = "normalized_score",
      alternate = {"normalizedScore"})
  @Schema(
      name = "normalized_score",
      description = "V2 router board score (0.0 to 1000.0, higher is better)")
  public Float normalizedScore;

  @SerializedName(
      value = "optimizer_score",
      alternate = {"optimizerScore"})
  @Schema(
      name = "optimizer_score",
      description = "V2 optimizer board score (0.0 to 1000.0, higher is better)")
  public Float optimizerScore;

  @SerializedName("outputs")
  @Schema(
      name = "outputs",
      description = "Map of generated output format names to their text or Base64 content")
  public Map<String, String> outputs = new LinkedHashMap<>();

  @SerializedName(
      value = "drc_summary",
      alternate = {"drcSummary"})
  @Schema(
      name = "drc_summary",
      description = "Diagnostic DRC summary with root causes and layout hints if requested")
  public DrcSummaryResponse drcSummary;

  @SerializedName("message")
  @Schema(name = "message", description = "Status or informational message")
  public String message;
}
