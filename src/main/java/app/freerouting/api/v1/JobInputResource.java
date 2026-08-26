package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.BaseController;
import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.io.FileFormat;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.util.TextManager;
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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.UUID;

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
public class JobInputResource extends BaseController {

  private static final int MAX_INPUT_PAYLOAD_BYTES = 100 * 1024 * 1024; // 100 MB max payload limit

  /** Default constructor for JobInputResource. */
  public JobInputResource() {}

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
  @POST
  @Path("/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(String requestBody) {
    // Authenticate the user
    UUID userId = authenticateUser();

    RoutingJob job = GSON.fromJson(requestBody, RoutingJob.class);
    if (job == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job data is invalid.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var request = GSON.toJson(job);
    try {
      // Enqueue the job
      job = RoutingJobScheduler.getInstance().enqueueJob(job);
      RoutingJobScheduler.getInstance().saveJob(job);

      // Save the job when the settings, input or output was updated
      job.addSettingsUpdatedEventListener(
          e -> RoutingJobScheduler.getInstance().saveJob(e.getJob()));
      job.addInputUpdatedEventListener(e -> RoutingJobScheduler.getInstance().saveJob(e.getJob()));
      job.addOutputUpdatedEventListener(e -> RoutingJobScheduler.getInstance().saveJob(e.getJob()));
    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"" + e.getMessage() + "\"}")
          .build();
    }

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("POST v1/jobs/enqueue", request, response, userId);
    return Response.ok(response).build();
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
  @POST
  @Path("/{jobId}/settings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String requestBody) {
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

    RouterSettings routerSettings = GSON.fromJson(requestBody, RouterSettings.class);
    if (routerSettings == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The router settings are invalid.\"}")
          .build();
    }

    // Change the settings of the job
    job.setSettings(routerSettings);

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled(
        "POST v1/jobs/" + jobId + "/settings", GSON.toJson(routerSettings), response, userId);
    return Response.ok(response).build();
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
  @POST
  @Path("/{jobId}/input")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String requestBody) {
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

    BoardFilePayload input = GSON.fromJson(requestBody, BoardFilePayload.class);
    if (input == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if ((input.dataBase64 == null) || (input.dataBase64.isEmpty())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              "{\"error\":\"The input data must be base-64 encoded and put into the"
                  + " \\\"data\\\" field.\"}")
          .build();
    }

    if (input.dataBase64.length() > (long) MAX_INPUT_PAYLOAD_BYTES * 4 / 3 + 1024) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Input file exceeds maximum allowed size of 100MB.\"}")
          .build();
    }

    // Decode the base64 encoded input data to a byte array
    byte[] inputByteArray;
    try {
      inputByteArray = Base64.getDecoder().decode(input.dataBase64);
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Invalid base64 encoding for input file.\"}")
          .build();
    }

    if (inputByteArray.length > MAX_INPUT_PAYLOAD_BYTES) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Input file exceeds maximum allowed size of 100MB.\"}")
          .build();
    }

    if (!job.setInput(inputByteArray)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    } else {
      if (job.input.getFilename().isEmpty()) {
        job.input.setFilename(job.name);
      }

      var routerSettings = new RouterSettings();
      routerSettings.setLayerCount(job.input.statistics.layers.totalCount);
      job.setSettings(routerSettings);

      var request =
          GSON.toJson(input)
              .replace(input.dataBase64, TextManager.shortenString(input.dataBase64, 4));
      var response = GSON.toJson(job);
      FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/input", request, response, userId);
      return Response.ok(response).build();
    }
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
  @POST
  @Path("/{jobId}/input/json")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      String jsonBody) {
    UUID userId = authenticateUser();

    var job = RoutingJobScheduler.getInstance().getJob(jobId);
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    if (job.state != RoutingJobState.QUEUED) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    if (jsonBody == null || jsonBody.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The JSON input data must not be empty.\"}")
          .build();
    }

    if (jsonBody.length() > MAX_INPUT_PAYLOAD_BYTES) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"JSON payload exceeds maximum allowed size of 100MB.\"}")
          .build();
    }

    // Store the raw JSON bytes as the input, marking format as JSON
    byte[] jsonBytes = jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (!job.setInput(jsonBytes)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The JSON input data is invalid.\"}")
          .build();
    }

    // Force the format to JSON (setInput may auto-detect, but be explicit)
    job.input.format = FileFormat.KICAD_DESIGN_JSON;
    if (job.input.getFilename().isEmpty()) {
      job.input.setFilename(job.name);
    }

    var routerSettings = new RouterSettings();
    routerSettings.setLayerCount(job.input.statistics.layers.totalCount);
    job.setSettings(routerSettings);

    var request = TextManager.shortenString(jsonBody, 200);
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled(
        "POST v1/jobs/" + jobId + "/input/json", request, response, userId);
    return Response.ok(response).build();
  }
}
