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

@Path("/v1/jobs")
@Tag(name = "Jobs", description = "Routing job management endpoints for creating, monitoring, and controlling PCB routing jobs")
public class JobControllerV1 extends BaseController {

  private static final ConcurrentHashMap<String, Long> previousOutputChecksums = new ConcurrentHashMap<>();

  public JobControllerV1() {
  }

  /*
   * Enqueue a new job with the given session id. In order to start the job, both
   * an input file and its settings must be uploaded first.
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
    FRAnalytics.apiEndpointCalled("POST v1/jobs/enqueue", request, response);
    return Response
        .ok(response)
        .build();

  }

  /*
   * Get a list of all jobs in the session with the given id, returning only basic
   * details about them.
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
    FRAnalytics.apiEndpointCalled("GET v1/jobs/list/" + sessionId, "", response);
    return Response
        .ok(response)
        .build();
  }

  /*
   * Get the current detailed status of the job with id, including statistical
   * data about the (partially) completed board is the process already started.
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
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId, "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Start or continue the job with the given id. */
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
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/start", "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Stop the job with the given id, and cancels the job. */
  @Operation(summary = "Cancel routing job", description = "Cancels a routing job. Note: This endpoint is currently not fully implemented.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "501", description = "Not implemented", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"This method is not implemented yet.\"}"))),
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

    // TODO: cancel the job
    job.state = RoutingJobState.CANCELLED;
    RoutingJobScheduler
        .getInstance()
        .saveJob(job);

    var response = GsonProvider.GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/cancel", "", response);

    // Return an error that this method is not implemented yet
    return Response
        .status(Response.Status.NOT_IMPLEMENTED)
        .entity("{\"error\":\"This method is not implemented yet.\"}")
        .build();
  }

  /* Change the settings of the job, such as the router settings. */
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
    FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/settings", GSON.toJson(routerSettings), response);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format. Note: the
   * input file limit depends on the server configuration, but it is at least 1MB
   * and typically 30MBs if hosted by ASP.NET Core
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
          .entity("{\"error\":\"The input data must be base-64 encoded and put into the data_base64 field.\"}")
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

      job.setSettings(new RouterSettings(job.input.statistics.layers.totalCount));

      var request = GSON
          .toJson(input)
          .replace(input.dataBase64, TextManager.shortenString(input.dataBase64, 4));
      var response = GSON.toJson(job);
      FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/input", request, response);
      return Response
          .ok(response)
          .build();
    }
  }

  /* Download the output of the job, typically in Specctra SES format. */
  @Operation(summary = "Download job output file", description = "Downloads the output file of a completed routing job, typically in Specctra SES format. The file is returned as Base64-encoded data.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Output downloaded successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BoardFilePayload.class))),
      @ApiResponse(responseCode = "404", description = "Job not found"),
      @ApiResponse(responseCode = "400", description = "Job not completed or invalid session")
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
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is completed
    if (job.state != RoutingJobState.COMPLETED) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job hasn't finished yet.\"}")
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
        response.replace(result.dataBase64, TextManager.shortenString(result.dataBase64, 4)));
    return Response
        .ok(response)
        .build();
  }

  /* Stream the output of the job in real-time using Server-Sent Events. */
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
      eventSink.close();
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
          eventSink.close();
          executor.shutdown();
        }
      } catch (Exception e) {
        FRLogger.error("Error while streaming output", e);
        eventSink.close();
        executor.shutdown();
      }
    }, 0, 200, TimeUnit.MILLISECONDS);

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/output/stream", "", "stream-started");
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
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs", "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Stream the log entries of the job in real-time using Server-Sent Events. */
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
      eventSink.close();
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
          eventSink.close();
          executor.shutdown();
        }
      } catch (Exception ex) {
        FRLogger.error("Error while streaming logs", ex);
        eventSink.close();
        executor.shutdown();
      }
    });

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs/stream", "", "stream-started");
  }

  /* Get DRC report for a job */
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
          HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
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
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/drc", "", "drc-report-generated");

    return Response
        .ok(drcReportJson)
        .build();
  }
}