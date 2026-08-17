package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.BaseController;
import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.board.ItemIdGenerator;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadDrcReport;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.BoardLoader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.util.TextManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
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
public class JobOutputResource extends BaseController {

  private static final ConcurrentHashMap<String, Long> previousOutputChecksums =
      new ConcurrentHashMap<>();

  /** Default constructor for JobOutputResource. */
  public JobOutputResource() {}

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
  @GET
  @Path("/{jobId}/output")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
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
          .entity(
              GSON.toJson(
                  java.util.Map.of("error", "The session ID '" + job.sessionId + "' is invalid.")))
          .build();
    }

    // Reject jobs that have failed, been cancelled, or are in an invalid terminal state
    if (job.state == RoutingJobState.TERMINATED
        || job.state == RoutingJobState.CANCELLED
        || job.state == RoutingJobState.TIMED_OUT
        || job.state == RoutingJobState.INVALID) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              GSON.toJson(
                  java.util.Map.of(
                      "error", "The job is in state '" + job.state + "' and has no valid output.")))
          .build();
    }

    // For in-progress jobs (RUNNING, PAUSED, STOPPING), return partial output if available
    boolean isInProgress =
        job.state == RoutingJobState.RUNNING
            || job.state == RoutingJobState.PAUSED
            || job.state == RoutingJobState.STOPPING;

    // Check if output data is available
    if (job.output == null || job.output.getData() == null) {
      if (isInProgress) {
        // Job is running but hasn't written any output yet — return 204 No Content (no body per RFC
        // 7231)
        return Response.status(Response.Status.NO_CONTENT).build();
      }
      // QUEUED or READY_TO_START
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(GSON.toJson(java.util.Map.of("error", "The job hasn't started yet.")))
          .build();
    }

    var result = new BoardFilePayload();
    result.jobId = job.id;
    result.setFilename(job.output.getFilename());
    result.setData(job.output.getData().readAllBytes());
    result.dataBase64 = Base64.getEncoder().encodeToString(result.getData().readAllBytes());

    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled(
        "GET v1/jobs/" + jobId + "/output",
        "",
        response.replace(result.dataBase64, TextManager.shortenString(result.dataBase64, 4)),
        userId);

    // Return 202 Accepted for in-progress jobs, 200 OK for completed jobs
    if (isInProgress) {
      return Response.accepted(response).build();
    }
    return Response.ok(response).build();
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
  @GET
  @Path("/{jobId}/output/json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId) {
    UUID userId = authenticateUser();

    var job = RoutingJobScheduler.getInstance().getJob(jobId);
    if (job == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              GSON.toJson(
                  java.util.Map.of("error", "The session ID '" + job.sessionId + "' is invalid.")))
          .build();
    }

    // Reject terminal error states
    if (job.state == RoutingJobState.TERMINATED
        || job.state == RoutingJobState.CANCELLED
        || job.state == RoutingJobState.TIMED_OUT
        || job.state == RoutingJobState.INVALID) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              GSON.toJson(
                  java.util.Map.of(
                      "error", "The job is in state '" + job.state + "' and has no valid output.")))
          .build();
    }

    boolean isInProgress =
        job.state == RoutingJobState.RUNNING
            || job.state == RoutingJobState.PAUSED
            || job.state == RoutingJobState.STOPPING;

    if (job.output == null || job.output.getData() == null) {
      if (isInProgress) {
        return Response.status(Response.Status.NO_CONTENT).build();
      }
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(GSON.toJson(java.util.Map.of("error", "The job hasn't started yet.")))
          .build();
    }

    // If the output is not JSON format, attempt to generate JSON from the board
    if (job.output.format != FileFormat.KICAD_SESSION_JSON) {
      // If we have a board loaded, we can generate JSON on the fly
      if (job.board != null) {
        try {
          String jsonStr = app.freerouting.io.kicad.KiCadJsonWriter.write(job.board, job.name);
          FRAnalytics.apiEndpointCalled(
              "GET v1/jobs/" + jobId + "/output/json", "", "json-generated-from-board", userId);
          if (isInProgress) {
            return Response.accepted(jsonStr).build();
          }
          return Response.ok(jsonStr).build();
        } catch (Exception e) {
          FRLogger.error("Couldn't generate JSON output from board", e);
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity(
                  GSON.toJson(
                      java.util.Map.of(
                          "error", "Failed to generate JSON output: " + e.getMessage())))
              .build();
        }
      }
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              GSON.toJson(
                  java.util.Map.of(
                      "error",
                      "The job output is not in JSON format (format: "
                          + job.output.format
                          + "). Use /v1/jobs/{jobId}/output for SES format.")))
          .build();
    }

    // Output is JSON — return it as raw JSON (not Base64-encoded)
    try {
      byte[] outputBytes = job.output.getData().readAllBytes();
      String jsonOutput = new String(outputBytes, java.nio.charset.StandardCharsets.UTF_8);
      FRAnalytics.apiEndpointCalled(
          "GET v1/jobs/" + jobId + "/output/json",
          "",
          TextManager.shortenString(jsonOutput, 200),
          userId);
      if (isInProgress) {
        return Response.accepted(jsonOutput).build();
      }
      return Response.ok(jsonOutput).build();
    } catch (Exception e) {
      FRLogger.error("Couldn't read JSON output data", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(
              GSON.toJson(
                  java.util.Map.of("error", "Failed to read JSON output: " + e.getMessage())))
          .build();
    }
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
  @GET
  @Path("/{jobId}/output/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamOutput(
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
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Schedule periodic updates every 250ms
    executor.scheduleAtFixedRate(
        () -> {
          try {
            if (job.output != null && job.output.getData() != null) {
              var result = new BoardFilePayload();
              result.jobId = job.id;
              result.setFilename(job.output.getFilename());
              result.setData(job.output.getData().readAllBytes());
              result.dataBase64 =
                  Base64.getEncoder().encodeToString(result.getData().readAllBytes());

              Long previousOutputChecksum = previousOutputChecksums.get(jobId);

              if ((previousOutputChecksum == null) || (result.crc32 != previousOutputChecksum)) {
                previousOutputChecksums.put(jobId, result.crc32);

                OutboundSseEvent event =
                    sse.newEventBuilder()
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
        },
        0,
        200,
        TimeUnit.MILLISECONDS);

    // Log the API call
    FRAnalytics.apiEndpointCalled(
        "GET v1/jobs/" + jobId + "/output/stream", "", "stream-started", userId);
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
  @GET
  @Path("/{jobId}/output/json/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamOutputJson(
      @Parameter(
              description = "Unique identifier of the job",
              example = "550e8400-e29b-41d4-a716-446655440000")
          @PathParam("jobId")
          String jobId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    UUID userId = authenticateUser();

    var job = RoutingJobScheduler.getInstance().getJob(jobId);
    if (job == null
        || SessionManager.getInstance().getSession(job.sessionId.toString(), userId) == null) {
      try {
        eventSink.close();
      } catch (Exception e) {
        FRLogger.error("Error closing SSE event sink", e);
      }
      return;
    }

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    java.util.concurrent.atomic.AtomicLong previousChecksum =
        new java.util.concurrent.atomic.AtomicLong(-1);

    executor.scheduleAtFixedRate(
        () -> {
          try {
            if (job.board != null) {
              // Generate JSON from the current board state
              String jsonStr = app.freerouting.io.kicad.KiCadJsonWriter.write(job.board, job.name);
              java.util.zip.CRC32 crc = new java.util.zip.CRC32();
              crc.update(jsonStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
              long crcValue = crc.getValue();

              if (crcValue != previousChecksum.get()) {
                previousChecksum.set(crcValue);
                OutboundSseEvent event =
                    sse.newEventBuilder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name("json-output")
                        .data(jsonStr)
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
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
            FRLogger.error("Error while streaming JSON output", e);
            try {
              eventSink.close();
            } catch (Exception ex) {
              FRLogger.error("Error closing SSE event sink", ex);
            }
            executor.shutdown();
          }
        },
        0,
        500,
        TimeUnit.MILLISECONDS);

    FRAnalytics.apiEndpointCalled(
        "GET v1/jobs/" + jobId + "/output/json/stream", "", "json-stream-started", userId);
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
  @GET
  @Path("/{jobId}/drc")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDrcReport(
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
      return Response.status(Response.Status.NOT_FOUND)
          .entity("{\"error\":\"Job not found.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString(), userId);
    if (session == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job has a board loaded, and load it if needed
    if (!BoardLoader.loadBoardIfNeeded(job)) {
      // Try to load the board if input is available
      if (job.input != null) {
        try {
          HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
          if (job.input.format == FileFormat.KICAD_DESIGN_JSON) {
            boardManager.loadFromKiCadJson(job.input.getData(), null, new ItemIdGenerator());
          } else {
            boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
          }
          job.board = boardManager.getRoutingBoard();
        } catch (Exception e) {
          FRLogger.error("Couldn't load the board for DRC check", e);
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity("{\"error\":\"Failed to load board: " + e.getMessage() + "\"}")
              .build();
        }
      } else {
        return Response.status(Response.Status.BAD_REQUEST)
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
    FRAnalytics.apiEndpointCalled(
        "GET v1/jobs/" + jobId + "/drc", "", "drc-report-generated", userId);

    return Response.ok(drcReportJson).build();
  }
}
