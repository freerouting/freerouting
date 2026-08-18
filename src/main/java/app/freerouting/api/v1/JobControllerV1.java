package app.freerouting.api.v1;

import app.freerouting.api.BaseController;
import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.kicad.KiCadDrcReport;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.settings.RouterSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

/**
 * Backward-compatible façade for the v1 routing job API.
 *
 * <p>HTTP endpoint implementations live in {@link JobInputResource}, {@link JobOutputResource}, and
 * {@link JobProgressResource}. This façade retains the historical public class and method
 * signatures for callers that instantiate {@code JobControllerV1} directly.
 */
@Tag(
    name = "Jobs",
    description =
        "Routing job management endpoints for creating, monitoring, and controlling PCB routing"
            + " jobs")
public class JobControllerV1 extends BaseController {

  /** Default constructor for JobControllerV1. */
  public JobControllerV1() {}

  /**
   * Enqueues a new routing job within the given session.
   *
   * <p>The job is created in {@code QUEUED} state. Both an input file and router settings must be
   * uploaded before the job can be transitioned to {@code READY_TO_START} via {@code PUT
   * /v1/jobs/{jobId}/start}.
   */
  @Operation(
      summary = "Enqueue new routing job",
      description =
          "Creates and enqueues a new PCB routing job within a session. This is Step 2 of the"
              + " routing pipeline. Next, call upload_job_input_file using the returned jobId.")
  @RequestBody(
      description = "Routing job configuration",
      required = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = RoutingJob.class)))
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job enqueued successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid job data or session ID",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(value = "{\"error\":\"The job data is invalid.\"}")))
      })
  @Path("/enqueue")
  public Response enqueueJob(String requestBody) {
    return new JobInputResource().enqueueJob(requestBody);
  }

  /**
   * Lists all routing jobs in the specified session.
   *
   * <p>Pass {@code "all"} (or any value that does not resolve to a known session) as {@code
   * sessionId} to retrieve all jobs belonging to the authenticated user regardless of session.
   */
  @Operation(
      summary = "List routing jobs",
      description =
          "Retrieves a list of all routing jobs in the specified session. Use 'all' as sessionId"
              + " to list all jobs for the authenticated user.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "List of jobs retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob[].class)))
      })
  @Path("/list/{sessionId}")
  public Response listJobs(
      @Parameter(
              description = "Session ID or 'all' for all jobs",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("sessionId")
          String sessionId) {
    return new JobProgressResource().listJobs(sessionId);
  }

  /**
   * Returns detailed status and statistics for a single routing job, including board statistics if
   * routing has already started.
   */
  @Operation(
      summary = "Get job details",
      description =
          "Retrieves detailed status and progress of a routing job. When polling this endpoint,"
              + " use an interval of 2 to 5 seconds to prevent server overload. For more real-time"
              + " feedback, you can stream logs using stream_job_logs.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job details retrieved successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Job not found",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(value = "{}"))),
        @ApiResponse(responseCode = "400", description = "Invalid session ID")
      })
  @Path("/{jobId}")
  public Response getJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobProgressResource().getJob(jobId);
  }

  /**
   * Transitions a job from {@code QUEUED} to {@code READY_TO_START}, signalling the routing
   * scheduler to pick it up for processing.
   *
   * <p>The job must be in {@code QUEUED} state; attempting to start an already-running or completed
   * job returns HTTP 400.
   */
  @Operation(
      summary = "Start routing job",
      description =
          "Starts or continues a queued routing job. This is Step 4 of the routing pipeline."
              + " Next, poll the status using get_job_details until it is COMPLETED.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job started successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Job already started or invalid session")
      })
  @Path("/{jobId}/start")
  public Response startJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobProgressResource().startJob(jobId);
  }

  /**
   * Cancels the routing job with the given ID.
   *
   * <p>Delegates to {@link RoutingJobScheduler#cancelJob(RoutingJob)}. The job state is set to
   * {@code CANCELLED}; any in-progress routing pass is interrupted. The partially-completed output
   * (if any) is still accessible via {@code GET /v1/jobs/{jobId}/output} after cancellation.
   */
  @Operation(
      summary = "Cancel routing job",
      description =
          "Cancels a running or queued routing job. The job state is set to CANCELLED and any"
              + " in-progress routing pass is interrupted. Partial output (if any) remains"
              + " accessible.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job cancelled successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Invalid session ID")
      })
  @Path("/{jobId}/cancel")
  public Response cancelJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobProgressResource().cancelJob(jobId);
  }

  /**
   * Updates the {@link RouterSettings} for a job that is still in {@code QUEUED} state.
   *
   * <p>The body is deserialized as a partial {@code RouterSettings} object; only the fields present
   * in the JSON are applied via the settings merger pipeline.
   */
  @Operation(
      summary = "Update job settings",
      description =
          "Updates the router settings for a queued job. This is optional Step 3.5 before"
              + " starting. Next, call start_job.")
  @RequestBody(
      description = "Router settings configuration",
      required = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = RouterSettings.class)))
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Settings updated successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Invalid settings or job already started")
      })
  @Path("/{jobId}/settings")
  public Response changeSettings(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String requestBody) {
    return new JobInputResource().changeSettings(jobId, requestBody);
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format. Note: the input file limit
   * depends on the server configuration, but it is at least 1MB and typically 30MBs if hosted by
   * ASP.NET Core web server.
   */
  @Operation(
      summary = "Upload job input file",
      description =
          "Uploads the input PCB design file for a routing job, typically in Specctra DSN format."
              + " The file must be Base64-encoded. For MCP/LLM clients, it is recommended to use"
              + " the local 'encode_base64' tool to perform this conversion rather than running"
              + " terminal commands (like powershell or base64). Note: File size limit depends on"
              + " server configuration (typically 1-30MB).")
  @RequestBody(
      description = "Board file payload with Base64-encoded data",
      required = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = BoardFilePayload.class)))
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Input uploaded successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data or job already started")
      })
  @Path("/{jobId}/input")
  public Response uploadInput(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String requestBody) {
    return new JobInputResource().uploadInput(jobId, requestBody);
  }

  /**
   * Upload the input of the job in KiCad JSON format. The JSON payload is sent as the raw request
   * body (not Base64-encoded), which is more efficient for the IPC bridge workflow where the Python
   * plugin serializes the KiCad board directly to JSON.
   */
  @Operation(
      summary = "Upload job input in KiCad JSON format",
      description =
          """
          Uploads the input PCB design file in KiCad JSON format. The JSON is sent as the raw
          request body (not Base64-encoded). This endpoint is optimized for the KiCad IPC bridge
          workflow.
          """)
  @RequestBody(
      description = "KiCad JSON board data (raw JSON, not Base64-encoded)",
      required = true,
      content = @Content(mediaType = MediaType.APPLICATION_JSON))
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "JSON input uploaded successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = RoutingJob.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Invalid JSON data or job already started")
      })
  @Path("/{jobId}/input/json")
  public Response uploadInputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String jsonBody) {
    return new JobInputResource().uploadInputJson(jobId, jsonBody);
  }

  /**
   * Downloads the output file of a routing job in Specctra SES format.
   *
   * <ul>
   *   <li><b>200 OK</b> — job is {@code COMPLETED}; returns the final SES output.
   *   <li><b>202 Accepted</b> — job is {@code RUNNING}, {@code PAUSED}, or {@code STOPPING};
   *       returns the partial output generated so far.
   *   <li><b>204 No Content</b> — job is in progress but no output bytes are available yet.
   *   <li><b>400 Bad Request</b> — job has not started, or is {@code TERMINATED}, {@code
   *       CANCELLED}, {@code TIMED_OUT}, or {@code INVALID}.
   * </ul>
   *
   * <p>The output data is Base64-encoded in the {@code data} field of the response.
   */
  @Operation(
      summary = "Download job output file",
      description =
          """
          Downloads the output file of a routing job in Specctra SES format. If the job is
          completed, returns the final output. If the job is still running or paused, returns the
          partial output generated so far (202 Accepted). The file is returned as Base64-encoded
          data. For MCP/LLM clients, it is recommended to use the local 'decode_base64' tool to
          decode this output into a text/SES file rather than running external terminal shell
          commands (like powershell or base64) to perform base64 decoding.
          """)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Output downloaded successfully (job completed)",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = BoardFilePayload.class))),
        @ApiResponse(
            responseCode = "202",
            description = "Partial output returned (job still in progress)",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = BoardFilePayload.class))),
        @ApiResponse(
            responseCode = "204",
            description = "Job is in progress but no output data is available yet"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(
            responseCode = "400",
            description = "Job failed, was cancelled, or session is invalid")
      })
  @Path("/{jobId}/output")
  public Response downloadOutput(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobOutputResource().downloadOutput(jobId);
  }

  /**
   * Downloads the output file of a routing job in KiCad JSON format.
   *
   * <ul>
   *   <li><b>200 OK</b> — job is {@code COMPLETED}; returns the final JSON output.
   *   <li><b>202 Accepted</b> — job is {@code RUNNING}, {@code PAUSED}, or {@code STOPPING};
   *       returns the partial JSON output generated so far.
   *   <li><b>204 No Content</b> — job is in progress but no output bytes are available yet.
   *   <li><b>400 Bad Request</b> — job output is not in JSON format, or is in a terminal error
   *       state.
   * </ul>
   *
   * <p>Unlike the SES output endpoint, the JSON output is returned as raw JSON (not
   * Base64-encoded), making it directly usable by the KiCad IPC bridge.
   */
  @Operation(
      summary = "Download job output in KiCad JSON format",
      description =
          """
          Downloads the output of a routing job in KiCad JSON format. The JSON is returned as raw
          JSON (not Base64-encoded), optimized for the KiCad IPC bridge. If the job was submitted
          with JSON input, the output will also be in JSON format.
          """)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "JSON output downloaded successfully (job completed)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @ApiResponse(
            responseCode = "202",
            description = "Partial JSON output returned (job still in progress)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @ApiResponse(
            responseCode = "204",
            description = "Job is in progress but no output data is available yet"),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(
            responseCode = "400",
            description = "Job output is not in JSON format, or job failed/was cancelled")
      })
  @Path("/{jobId}/output/json")
  public Response downloadOutputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobOutputResource().downloadOutputJson(jobId);
  }

  /**
   * Streams the job output file in real-time using Server-Sent Events (SSE).
   *
   * <p>A new SSE event is pushed every ~200 ms when the output CRC32 checksum changes. Each event
   * payload is a JSON-serialized {@link app.freerouting.api.dto.BoardFilePayload} with the current
   * Base64-encoded SES data. The stream is closed automatically when the job transitions to {@code
   * COMPLETED} or {@code CANCELLED}.
   */
  @Operation(
      summary = "Stream job output in real-time",
      description =
          "Streams the output file of a routing job in real-time using Server-Sent Events (SSE)."
              + " Updates are sent every 200ms when the output changes.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "SSE stream established",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
      })
  @Path("/{jobId}/output/stream")
  public void streamOutput(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    new JobOutputResource().streamOutput(jobId, eventSink, sse);
  }

  /**
   * Streams the job output in KiCad JSON format in real-time using Server-Sent Events (SSE).
   *
   * <p>A new SSE event is pushed every ~500 ms when the board state changes (detected via CRC32).
   * Each event payload is raw KiCad JSON (not Base64-encoded), making it directly consumable by the
   * KiCad IPC bridge. The stream is closed automatically when the job transitions to {@code
   * COMPLETED} or {@code CANCELLED}.
   */
  @Operation(
      summary = "Stream job JSON output in real-time",
      description =
          """
          Streams the KiCad JSON output of a routing job in real-time using Server-Sent Events (SSE).
          Each event contains raw JSON (not Base64-encoded), optimized for the KiCad IPC bridge.
          Updates are sent every 500ms when the board state changes.
          """)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "SSE stream established",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
      })
  @Path("/{jobId}/output/json/stream")
  public void streamOutputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    new JobOutputResource().streamOutputJson(jobId, eventSink, sse);
  }

  /**
   * Retrieves logs for a job.
   *
   * @param jobId Job ID parameter
   * @return Response containing job logs
   */
  @Operation(
      summary = "Get job logs",
      description = "Retrieves all log entries associated with a specific routing job.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logs retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Invalid session ID")
      })
  @Path("/{jobId}/logs")
  public Response logs(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobProgressResource().logs(jobId);
  }

  /**
   * Streams log entries for a routing job in real-time using Server-Sent Events (SSE).
   *
   * <p>An SSE event is pushed each time the job fires a {@code logEntryAdded} event. Each event
   * payload is a JSON-serialized log entry. The connection is closed when the job transitions to
   * {@code COMPLETED} or {@code CANCELLED}.
   */
  @Operation(
      summary = "Stream job logs in real-time",
      description =
          "Streams log entries of a routing job in real-time using Server-Sent Events (SSE). New"
              + " log entries are sent as they are generated.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "SSE stream established",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
      })
  @Path("/{jobId}/logs/stream")
  public void streamLogs(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    new JobProgressResource().streamLogs(jobId, eventSink, sse);
  }

  /**
   * Generates and returns a KiCad-compatible DRC (Design Rules Check) report for a routing job.
   *
   * <p>If the job's board is not already loaded in memory, the input DSN file is loaded on demand.
   * The report JSON follows the {@code https://schemas.kicad.org/drc.v1.json} schema and includes
   * unconnected items and clearance violations. Returns HTTP 500 if the board cannot be loaded from
   * the stored DSN input.
   */
  @Operation(
      summary = "Get DRC report",
      description =
          "Generates and retrieves a Design Rules Check (DRC) report for a routing job. The report"
              + " includes violations and statistics in JSON format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "DRC report generated successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = KiCadDrcReport.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "400", description = "Invalid session or failed to load board"),
        @ApiResponse(responseCode = "500", description = "Failed to load board for DRC check")
      })
  @Path("/{jobId}/drc")
  public Response getDrcReport(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    return new JobOutputResource().getDrcReport(jobId);
  }
}
