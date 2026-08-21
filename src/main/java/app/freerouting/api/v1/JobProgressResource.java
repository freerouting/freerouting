package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.BaseController;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.util.gson.GsonProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * JAX-RS resource for the routing job API.
 *
 * <p>This resource is intentionally split from the compatibility façade {@link JobControllerV1} so
 * that the public endpoint contract remains unchanged while the API surface is easier to maintain.
 */
@Path("/v1/jobs")
@Tag(
    name = "Jobs",
    description =
        "Routing job management endpoints for creating, monitoring, and controlling PCB routing"
            + " jobs")
public class JobProgressResource extends BaseController {

  /** Default constructor for JobProgressResource. */
  public JobProgressResource() {}

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
  @GET
  @Path("/list/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @Parameter(
              description = "Session ID or 'all' for all jobs",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("sessionId")
          String sessionId) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Get the session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId, userId);

    RoutingJob[] result;
    // If the session does not exist, list all jobs
    if ((session == null) || (sessionId.isEmpty()) || ("all".equals(sessionId))) {
      result = RoutingJobScheduler.getInstance().listJobs(null, userId);
    } else {
      result = RoutingJobScheduler.getInstance().listJobs(sessionId);
    }

    // Return a list of jobs in the session
    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/list/" + sessionId, "", response, userId);
    return Response.ok(response).build();
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
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Enqueue the job
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId, "", response, userId);
    return Response.ok(response).build();
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
  @PUT
  @Path("/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    job.state = RoutingJobState.READY_TO_START;
    RoutingJobScheduler.getInstance().saveJob(job);

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/start", "", response, userId);
    return Response.ok(response).build();
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
  @PUT
  @Path("/{jobId}/cancel")
  @Produces(MediaType.APPLICATION_JSON)
  public Response cancelJob(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    RoutingJobScheduler.getInstance().cancelJob(job);

    var response = GsonProvider.GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/cancel", "", response, userId);

    return Response.ok(response).build();
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
  @GET
  @Path("/{jobId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var logEntries = FRLogger.getLogEntries();
    var logs = logEntries.getEntries(null, job.id);

    var response = GSON.toJson(logs);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs", "", response, userId);
    return Response.ok(response).build();
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
  @GET
  @Path("/{jobId}/logs/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamLogs(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    // Authenticate the user
    UUID userId = authenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist or session is invalid, close the connection
    if (job == null
        || SessionManager.getInstance().getSession(job.sessionId.toString(), userId) == null) {
      try {
        eventSink.close();
      } catch (Exception e) {
        FRLogger.error("Error closing SSE event sink", e);
      }
      return;
    }

    // Create a scheduled executor for periodic updates
    ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "job-progress-stream-worker");
              t.setDaemon(true);
              return t;
            });

    // stream a new log entry when the job logsEntryAdded event was fired
    job.addLogEntryAddedEventListener(
        e -> {
          try {
            var result = e.getLogEntry();
            OutboundSseEvent event =
                sse.newEventBuilder()
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
    FRAnalytics.apiEndpointCalled(
        "GET v1/jobs/" + jobId + "/logs/stream", "", "stream-started", userId);
  }
}
