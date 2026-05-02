package app.freerouting.api.v1;

import static app.freerouting.management.gson.GsonProvider.GSON;

import app.freerouting.api.BaseController;
import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.board.BoardLoader;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JAX-RS controller for PCB routing job management.
 *
 * <h2>Typical workflow</h2>
 * <ol>
 *   <li>POST {@code /v1/jobs/enqueue} — create a job inside a session.</li>
 *   <li>POST {@code /v1/jobs/{jobId}/input} — upload the Base64-encoded Specctra DSN file.</li>
 *   <li>POST {@code /v1/jobs/{jobId}/settings} — (optional) override default router settings.</li>
 *   <li>PUT  {@code /v1/jobs/{jobId}/start} — transition the job from QUEUED → READY_TO_START.</li>
 *   <li>GET  {@code /v1/jobs/{jobId}} — poll job state and statistics.</li>
 *   <li>GET  {@code /v1/jobs/{jobId}/output} — download the Specctra SES result (200 when complete,
 *       202 while still running, 204 if routing has not yet produced any output).</li>
 *   <li>GET  {@code /v1/jobs/{jobId}/output/stream} — real-time SSE stream of output updates.</li>
 *   <li>GET  {@code /v1/jobs/{jobId}/drc} — retrieve a KiCad-compatible DRC report.</li>
 * </ol>
 *
 * <p>All endpoints authenticate the caller via {@link app.freerouting.api.BaseController#AuthenticateUser()}
 * and verify that the referenced session belongs to that caller.</p>
 */
@Path("/v1/jobs")
@Tag(name = "Jobs", description = "Routing job management endpoints for creating, monitoring, and controlling PCB routing jobs")
public class JobControllerV1 extends BaseController {

  private static final ConcurrentHashMap<String, Long> previousOutputChecksums = new ConcurrentHashMap<>();

  public JobControllerV1() {
  }

  /**
   * Enqueues a new routing job within the given session.
   * <p>
   * The job is created in {@code QUEUED} state. Both an input file and router settings must be
   * uploaded before the job can be transitioned to {@code READY_TO_START} via
   * {@code PUT /v1/jobs/{jobId}/start}.
   * </p>
   */
  @Operation(summary = "Enqueue new routing job", description = "Creates and enqueues a new PCB routing job within a session. The job must have both input file and settings uploaded before it can be started.")
  @RequestBody(description = "Routing job configuration", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Job enqueued successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "400", description = "Invalid job data or session ID", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The job data is invalid.\"}")))
  })
  @POST
  @Path("/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(String requestBody) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    RoutingJob job = GSON.fromJson(requestBody, RoutingJob.class);
    if (job == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job data is invalid.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var request = GSON.toJson(job);
    try {
      // Enqueue the job
      job = RoutingJobScheduler
          .getInstance()
          .enqueueJob(job);
      RoutingJobScheduler
          .getInstance()
          .saveJob(job);

      // Save the job when the settings, input or output was updated
      job.addSettingsUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
      job.addInputUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
      job.addOutputUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
    } catch (Exception e) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"" + e.getMessage() + "\"}")
          .build();
    }

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("POST v1/jobs/enqueue", request, response, userId);
    return Response
        .ok(response)
        .build();

  }

  /**
   * Lists all routing jobs in the specified session.
   * <p>
   * Pass {@code "all"} (or any value that does not resolve to a known session) as
   * {@code sessionId} to retrieve all jobs belonging to the authenticated user regardless
   * of session.
   * </p>
   */
  @Operation(summary = "List routing jobs", description = "Retrieves a list of all routing jobs in the specified session. Use 'all' as sessionId to list all jobs for the authenticated user.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "List of jobs retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob[].class)))
  })
  @GET
  @Path("/list/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @Parameter(description = "Session ID or 'all' for all jobs", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("sessionId") String sessionId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the session with the id of sessionId
    Session session = SessionManager
        .getInstance()
        .getSession(sessionId, userId);

    RoutingJob[] result;
    // If the session does not exist, list all jobs
    if ((session == null) || (sessionId.isEmpty()) || ("all".equals(sessionId))) {
      result = RoutingJobScheduler
          .getInstance()
          .listJobs(null, userId);
    } else {
      result = RoutingJobScheduler
          .getInstance()
          .listJobs(sessionId);
    }

    // Return a list of jobs in the session
    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/list/" + sessionId, "", response, userId);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Returns detailed status and statistics for a single routing job, including
   * board statistics if routing has already started.
   */
  @Operation(summary = "Get job details", description = "Retrieves detailed status and statistics of a routing job, including progress information if the job has started.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Job details retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "404", description = "Job not found", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{}"))),
      @ApiResponse(responseCode = "400", description = "Invalid session ID")
  })
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Enqueue the job
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId, "", response, userId);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Transitions a job from {@code QUEUED} to {@code READY_TO_START}, signalling the
   * routing scheduler to pick it up for processing.
   * <p>
   * The job must be in {@code QUEUED} state; attempting to start an already-running
   * or completed job returns HTTP 400.
   * </p>
   */
  @Operation(summary = "Start routing job", description = "Starts or continues a queued routing job. The job must have both input file and settings uploaded before it can be started.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Job started successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Job already started or invalid session")
  })
  @PUT
  @Path("/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    job.state = RoutingJobState.READY_TO_START;
    RoutingJobScheduler
        .getInstance()
        .saveJob(job);

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/start", "", response, userId);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Cancels the routing job with the given ID.
   * <p>
   * Delegates to {@link app.freerouting.management.RoutingJobScheduler#cancelJob(RoutingJob)}.
   * The job state is set to {@code CANCELLED}; any in-progress routing pass is interrupted.
   * The partially-completed output (if any) is still accessible via
   * {@code GET /v1/jobs/{jobId}/output} after cancellation.
   * </p>
   */
  @Operation(summary = "Cancel routing job", description = "Cancels a running or queued routing job. The job state is set to CANCELLED and any in-progress routing pass is interrupted. Partial output (if any) remains accessible.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Job cancelled successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Invalid session ID")
  })
  @PUT
  @Path("/{jobId}/cancel")
  @Produces(MediaType.APPLICATION_JSON)
  public Response cancelJob(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    RoutingJobScheduler
        .getInstance()
        .cancelJob(job);

    var response = GsonProvider.GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/cancel", "", response, userId);

    return Response
        .ok(response)
        .build();
  }

  /**
   * Updates the {@link RouterSettings} for a job that is still in {@code QUEUED} state.
   * <p>
   * The body is deserialized as a partial {@code RouterSettings} object; only the fields
   * present in the JSON are applied via the settings merger pipeline.
   * </p>
   */
  @Operation(summary = "Update job settings", description = "Updates the router settings for a queued job. The job must be in QUEUED state and not yet started.")
  @RequestBody(description = "Router settings configuration", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RouterSettings.class)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Settings updated successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Invalid settings or job already started")
  })
  @POST
  @Path("/{jobId}/settings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId,
      String requestBody) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    RouterSettings routerSettings = GSON.fromJson(requestBody, RouterSettings.class);
    if (routerSettings == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The router settings are invalid.\"}")
          .build();
    }

    // Change the settings of the job
    job.setSettings(routerSettings);

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/settings", GSON.toJson(routerSettings), response, userId);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format. Note: the input file limit depends on the server configuration, but it is at least 1MB and typically 30MBs if hosted by ASP.NET Core
   * web server.
   */
  @Operation(summary = "Upload job input file", description = "Uploads the input PCB design file for a routing job, typically in Specctra DSN format. The file must be Base64-encoded. Note: File size limit depends on server configuration (typically 1-30MB).")
  @RequestBody(description = "Board file payload with Base64-encoded data", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardFilePayload.class)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Input uploaded successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = RoutingJob.class))),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Invalid input data or job already started")
  })
  @POST
  @Path("/{jobId}/input")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId,
      String requestBody) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    BoardFilePayload input = GSON.fromJson(requestBody, BoardFilePayload.class);
    if (input == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if ((input.dataBase64 == null) || (input.dataBase64.isEmpty())) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data must be base-64 encoded and put into the \\\"data\\\" field.\"}")
          .build();
    }

    // Decode the base64 encoded input data to a byte array
    byte[] inputByteArray = Base64
        .getDecoder()
        .decode(input.dataBase64);
    if (!job.setInput(inputByteArray)) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    } else {
      if (job.input
          .getFilename()
          .isEmpty()) {
        job.input.setFilename(job.name);
      }

      var routerSettings = new RouterSettings();
      routerSettings.setLayerCount(job.input.statistics.layers.totalCount);
      job.setSettings(routerSettings);

      var request = GSON
          .toJson(input)
          .replace(input.dataBase64, TextManager.shortenString(input.dataBase64, 4));
      var response = GSON.toJson(job);
      FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/input", request, response, userId);
      return Response
          .ok(response)
          .build();
    }
  }

  /**
   * Downloads the output file of a routing job in Specctra SES format.
   * <ul>
   *   <li><b>200 OK</b> — job is {@code COMPLETED}; returns the final SES output.</li>
   *   <li><b>202 Accepted</b> — job is {@code RUNNING}, {@code PAUSED}, or {@code STOPPING};
   *       returns the partial output generated so far.</li>
   *   <li><b>204 No Content</b> — job is in progress but no output bytes are available yet.</li>
   *   <li><b>400 Bad Request</b> — job has not started, or is {@code TERMINATED},
   *       {@code CANCELLED}, {@code TIMED_OUT}, or {@code INVALID}.</li>
   * </ul>
   * <p>The output data is Base64-encoded in the {@code data} field of the response.</p>
   */
  @Operation(summary = "Download job output file", description = "Downloads the output file of a routing job in Specctra SES format. "
      + "If the job is completed, returns the final output. "
      + "If the job is still running or paused, returns the partial output generated so far (202 Accepted). "
      + "The file is returned as Base64-encoded data.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Output downloaded successfully (job completed)", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardFilePayload.class))),
      @ApiResponse(responseCode = "202", description = "Partial output returned (job still in progress)", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardFilePayload.class))),
      @ApiResponse(responseCode = "204", description = "Job is in progress but no output data is available yet"),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Job failed, was cancelled, or session is invalid")
  })
  @GET
  @Path("/{jobId}/output")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(GSON.toJson(java.util.Map.of("error", "The session ID '" + job.sessionId + "' is invalid.")))
          .build();
    }

    // Reject jobs that have failed, been cancelled, or are in an invalid terminal state
    if (job.state == RoutingJobState.TERMINATED
        || job.state == RoutingJobState.CANCELLED
        || job.state == RoutingJobState.TIMED_OUT
        || job.state == RoutingJobState.INVALID) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(GSON.toJson(java.util.Map.of("error", "The job is in state '" + job.state + "' and has no valid output.")))
          .build();
    }

    // For in-progress jobs (RUNNING, PAUSED, STOPPING), return partial output if available
    boolean isInProgress = job.state == RoutingJobState.RUNNING
        || job.state == RoutingJobState.PAUSED
        || job.state == RoutingJobState.STOPPING;

    // Check if output data is available
    if (job.output == null || job.output.getData() == null) {
      if (isInProgress) {
        // Job is running but hasn't written any output yet — return 204 No Content (no body per RFC 7231)
        return Response
            .status(Response.Status.NO_CONTENT)
            .build();
      }
      // QUEUED or READY_TO_START
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(GSON.toJson(java.util.Map.of("error", "The job hasn't started yet.")))
          .build();
    }

    var result = new BoardFilePayload();
    result.jobId = job.id;
    result.setFilename(job.output.getFilename());
    result.setData(job.output
        .getData()
        .readAllBytes());
    result.dataBase64 = Base64
        .getEncoder()
        .encodeToString(result
            .getData()
            .readAllBytes());

    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/output", "",
        response.replace(result.dataBase64, TextManager.shortenString(result.dataBase64, 4)), userId);

    // Return 202 Accepted for in-progress jobs, 200 OK for completed jobs
    if (isInProgress) {
      return Response
          .accepted(response)
          .build();
    }
    return Response
        .ok(response)
        .build();
  }

  /**
   * Streams the job output file in real-time using Server-Sent Events (SSE).
   * <p>
   * A new SSE event is pushed every ~200 ms when the output CRC32 checksum changes.
   * Each event payload is a JSON-serialized {@link app.freerouting.api.dto.BoardFilePayload}
   * with the current Base64-encoded SES data. The stream is closed automatically when the
   * job transitions to {@code COMPLETED} or {@code CANCELLED}.
   * </p>
   */
  @Operation(summary = "Stream job output in real-time", description = "Streams the output file of a routing job in real-time using Server-Sent Events (SSE). Updates are sent every 200ms when the output changes.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "SSE stream established", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  })
  @GET
  @Path("/{jobId}/output/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamOutput(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist or session is invalid, close the connection
    if (job == null || SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId) == null) {
      try {
        eventSink.close();
      } catch (Exception e) {
        FRLogger.error("Error closing SSE event sink", e);
      }
      return;
    }

    // Create a scheduled executor for periodic updates
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Schedule periodic updates every 250ms
    executor.scheduleAtFixedRate(() -> {
      try {
        if (job.output != null && job.output.getData() != null) {
          var result = new BoardFilePayload();
          result.jobId = job.id;
          result.setFilename(job.output.getFilename());
          result.setData(job.output
              .getData()
              .readAllBytes());
          result.dataBase64 = Base64
              .getEncoder()
              .encodeToString(result
                  .getData()
                  .readAllBytes());

          Long previousOutputChecksum = previousOutputChecksums.get(jobId);

          if ((previousOutputChecksum == null) || (result.crc32 != previousOutputChecksum)) {
            previousOutputChecksums.put(jobId, result.crc32);

            OutboundSseEvent event = sse
                .newEventBuilder()
                .id(String.valueOf(System.currentTimeMillis()))
                .data(GSON.toJson(result))
                .build();

            eventSink.send(event);
          }
        }

        // Close the connection if the job is completed or cancelled
        if (job.state == RoutingJobState.COMPLETED || job.state == RoutingJobState.CANCELLED) {
          try {
            eventSink.close();
          } catch (Exception ex) {
            FRLogger.error("Error closing SSE event sink", ex);
          }
          executor.shutdown();
        }
      } catch (Exception e) {
        FRLogger.error("Error while streaming output", e);
        try {
          eventSink.close();
        } catch (Exception ex) {
          FRLogger.error("Error closing SSE event sink", ex);
        }
        executor.shutdown();
      }
    }, 0, 200, TimeUnit.MILLISECONDS);

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/output/stream", "", "stream-started", userId);
  }

  @Operation(summary = "Get job logs", description = "Retrieves all log entries associated with a specific routing job.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Logs retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON)),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Invalid session ID")
  })
  @GET
  @Path("/{jobId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var logEntries = FRLogger.getLogEntries();
    var logs = logEntries.getEntries(null, job.id);

    var response = GSON.toJson(logs);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs", "", response, userId);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Streams log entries for a routing job in real-time using Server-Sent Events (SSE).
   * <p>
   * An SSE event is pushed each time the job fires a {@code logEntryAdded} event. Each event
   * payload is a JSON-serialized log entry. The connection is closed when the job transitions
   * to {@code COMPLETED} or {@code CANCELLED}.
   * </p>
   */
  @Operation(summary = "Stream job logs in real-time", description = "Streams log entries of a routing job in real-time using Server-Sent Events (SSE). New log entries are sent as they are generated.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "SSE stream established", content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS))
  })
  @GET
  @Path("/{jobId}/logs/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamLogs(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist or session is invalid, close the connection
    if (job == null || SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId) == null) {
      try {
        eventSink.close();
      } catch (Exception e) {
        FRLogger.error("Error closing SSE event sink", e);
      }
      return;
    }

    // Create a scheduled executor for periodic updates
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // stream a new log entry when the job logsEntryAdded event was fired
    job.addLogEntryAddedEventListener(e -> {
      try {
        var result = e.getLogEntry();
        OutboundSseEvent event = sse
            .newEventBuilder()
            .id(String.valueOf(System.currentTimeMillis()))
            .data(GSON.toJson(result))
            .build();

        eventSink.send(event);

        // Close the connection if the job is completed or cancelled
        if (job.state == RoutingJobState.COMPLETED || job.state == RoutingJobState.CANCELLED) {
          try {
            eventSink.close();
          } catch (Exception closeEx) {
            FRLogger.error("Error closing SSE event sink", closeEx);
          }
          executor.shutdown();
        }
      } catch (Exception ex) {
        FRLogger.error("Error while streaming logs", ex);
        try {
          eventSink.close();
        } catch (Exception closeEx) {
          FRLogger.error("Error closing SSE event sink", closeEx);
        }
        executor.shutdown();
      }
    });

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs/stream", "", "stream-started", userId);
  }

  /**
   * Generates and returns a KiCad-compatible DRC (Design Rules Check) report for a routing job.
   * <p>
   * If the job's board is not already loaded in memory, the input DSN file is loaded on demand.
   * The report JSON follows the {@code https://schemas.kicad.org/drc.v1.json} schema and
   * includes unconnected items and clearance violations. Returns HTTP 500 if the board cannot
   * be loaded from the stored DSN input.
   * </p>
   */
  @Operation(summary = "Get DRC report", description = "Generates and retrieves a Design Rules Check (DRC) report for a routing job. The report includes violations and statistics in JSON format.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "DRC report generated successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON)),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Invalid session or failed to load board"),
      @ApiResponse(responseCode = "500", description = "Failed to load board for DRC check")
  })
  @GET
  @Path("/{jobId}/drc")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDrcReport(
      @Parameter(description = "Unique identifier of the job", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("jobId") String jobId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{\"error\":\"Job not found.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job has a board loaded, and load it if needed
    if (!BoardLoader.loadBoardIfNeeded(job)) {
      // Try to load the board if input is available
      if (job.input != null && job.input.format == FileFormat.DSN) {
        try {
          HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
          boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
          job.board = boardManager.get_routing_board();
        } catch (Exception e) {
          FRLogger.error("Couldn't load the board for DRC check", e);
          return Response
              .status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity("{\"error\":\"Failed to load board: " + e.getMessage() + "\"}")
              .build();
        }
      } else {
        return Response
            .status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Failed to load board for DRC check.\"}")
            .build();
      }
    }

    // Run DRC check
    DesignRulesChecker drcChecker = new DesignRulesChecker(job.board, job.drcSettings);

    // Determine coordinate unit (default to mm)
    String coordinateUnit = "mm";

    // Get source file name
    String sourceFileName = job.input != null ? job.input.getFilename() : "unknown";

    // Generate DRC report
    String drcReportJson = drcChecker.generateReportJson(sourceFileName, coordinateUnit);

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/drc", "", "drc-report-generated", userId);

    return Response
        .ok(drcReportJson)
        .build();
  }
}