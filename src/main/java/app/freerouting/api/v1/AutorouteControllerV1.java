package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.api.BaseController;
import app.freerouting.api.dto.AutorouteRequest;
import app.freerouting.api.dto.AutorouteResponse;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.io.FileFormat;
import app.freerouting.io.MultiOutputGenerator;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.CompositeBoardInput;
import app.freerouting.management.sessions.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controller for composite 1-turn autorouting.
 *
 * <p>Accepts multi-file inputs, provisions or binds session and job lifecycle automatically, runs
 * the routing pipeline synchronously (or up to execution timeout budget), and returns all requested
 * output representations along with DRC diagnostics in 1 HTTP turn.
 */
@Path("/v1/autoroute")
@Tag(
    name = "Autoroute",
    description = "Single-turn composite autorouting endpoints for fast LLM and API workflows")
public class AutorouteControllerV1 extends BaseController {

  @Context private HttpHeaders httpHeaders;

  public AutorouteControllerV1() {}

  /** Routes a PCB design in a single synchronous turn. */
  @Operation(
      summary = "Single-turn composite autorouting",
      description =
          """
          Performs end-to-end autorouting in a single HTTP turn. Accepts multi-file inputs
          (primary design .dsn or .json, optional .rules, and optional initial .ses or .json session),
          executes the routing pipeline synchronously within the requested timeout budget,
          and returns all requested output formats (SES, KiCad JSON, Fusion SCR, DRC report/summary)
          along with comprehensive board statistics.
          """,
      parameters = {
        @Parameter(
            name = "Freerouting-Environment-Host",
            in = ParameterIn.HEADER,
            description =
                "Identifies the calling client/tool in the format '<name>/<version>' (e.g. 'KiCad/10.0', 'Claude/3.7').",
            required = true,
            example = "Agent/1.0",
            schema = @Schema(type = "string", pattern = "^[^/]+/[^/]+$"))
      })
  @RequestBody(
      description = "Composite autoroute request configuration and file inputs",
      required = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = AutorouteRequest.class)))
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Routing completed successfully",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AutorouteResponse.class))),
        @ApiResponse(
            responseCode = "202",
            description = "Routing timed out; partial output returned",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = AutorouteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request or input payload"),
        @ApiResponse(responseCode = "500", description = "Internal routing engine failure")
      })
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response autoroute(AutorouteRequest request) {
    UUID userId = authenticateUser();

    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Request body is missing.\"}")
          .build();
    }

    String host =
        httpHeaders != null ? httpHeaders.getHeaderString("Freerouting-Environment-Host") : null;
    if (host == null || host.isBlank()) {
      host = "Agent/1.0";
    }

    // 1. Create or retrieve session
    Session session = SessionManager.getInstance().createSession(userId, host);
    if (session == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Failed to create session for autorouting.\"}")
          .build();
    }

    // 2. Prepare composite input
    CompositeBoardInput inputAssembler = new CompositeBoardInput();
    try {
      if (request.fileContent != null && !request.fileContent.isBlank()) {
        inputAssembler.setDesign(
            request.fileContent.getBytes(StandardCharsets.UTF_8), "design.dsn");
      } else if (request.filePath != null && !request.filePath.isBlank()) {
        File f = validateAndResolveFile(request.filePath);
        inputAssembler.setDesign(f);
      } else {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                "{\"error\":\"Neither file_content nor file_path was provided for primary design.\"}")
            .build();
      }

      if (request.rulesContent != null && !request.rulesContent.isBlank()) {
        inputAssembler.setRules(
            request.rulesContent.getBytes(StandardCharsets.UTF_8), "design.rules");
      } else if (request.rulesPath != null && !request.rulesPath.isBlank()) {
        File rf = validateAndResolveFile(request.rulesPath);
        inputAssembler.setRules(rf);
      }

      if (request.sessionContent != null && !request.sessionContent.isBlank()) {
        inputAssembler.setSession(
            request.sessionContent.getBytes(StandardCharsets.UTF_8), "design.ses");
      } else if (request.sessionPath != null && !request.sessionPath.isBlank()) {
        File sf = validateAndResolveFile(request.sessionPath);
        inputAssembler.setSession(sf);
      }
    } catch (Exception ex) {
      FRLogger.error("Failed to process autoroute input files", ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Failed to read input files: " + ex.getMessage() + "\"}")
          .build();
    }

    // 3. Create RoutingJob
    RoutingJob job = new RoutingJob(session.id);
    if (request.routerSettings != null) {
      job.routerSettings = request.routerSettings;
    }
    if (request.drcSettings != null) {
      job.drcSettings = request.drcSettings;
    }

    try {
      inputAssembler.assembleBoard(job);
    } catch (Exception ex) {
      FRLogger.error("Failed to assemble routing board from composite inputs", ex);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Failed to assemble board: " + ex.getMessage() + "\"}")
          .build();
    }

    session.addJob(job);
    job.state = RoutingJobState.READY_TO_START;

    // Timeout calculation
    int timeoutSec =
        (request.timeoutSeconds != null && request.timeoutSeconds > 0)
            ? request.timeoutSeconds
            : 300;
    job.timeoutAt = Instant.now().plusSeconds(timeoutSec);

    // 4. Synchronous wait for completion or timeout
    Instant waitStart = Instant.now();
    while (!isTerminalState(job.state)) {
      if (Instant.now().isAfter(job.timeoutAt)) {
        if (job.thread != null) {
          job.thread.requestStop();
        }
        job.state = RoutingJobState.TIMED_OUT;
        break;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException _) {
        if (job.thread != null) {
          job.thread.requestStop();
        }
        job.state = RoutingJobState.CANCELLED;
        break;
      }
    }

    double elapsedSeconds = Duration.between(waitStart, Instant.now()).toMillis() / 1000.0;

    // 5. Generate requested outputs
    Set<FileFormat> requestedFormats = new HashSet<>();
    boolean includeDrcSummary = false;

    if (request.outputFormats != null && !request.outputFormats.isEmpty()) {
      for (String fmtStr : request.outputFormats) {
        if (fmtStr == null) {
          continue;
        }
        String cleanFmt = fmtStr.trim().toUpperCase(Locale.ROOT);
        switch (cleanFmt) {
          case "SES" -> requestedFormats.add(FileFormat.SES);
          case "JSON", "KICAD_JSON", "KICAD_SESSION_JSON" ->
              requestedFormats.add(FileFormat.KICAD_SESSION_JSON);
          case "SCR", "FUSION_SCR" -> requestedFormats.add(FileFormat.SCR);
          case "DRC", "DRC_JSON" -> requestedFormats.add(FileFormat.DRC_JSON);
          case "DRC_SUMMARY", "SUMMARY" -> includeDrcSummary = true;
          default -> FRLogger.warn("Unknown requested output format: " + cleanFmt);
        }
      }
    } else {
      requestedFormats.add(FileFormat.SES);
    }

    MultiOutputGenerator.MultiOutputResult multiOut =
        MultiOutputGenerator.generateOutputs(
            job.board, job.name, requestedFormats, job.drcSettings, includeDrcSummary);

    // 6. Build response
    AutorouteResponse responsePayload = new AutorouteResponse();
    responsePayload.jobId = job.id.toString();
    responsePayload.sessionId = session.id.toString();
    responsePayload.status = job.state;
    responsePayload.durationSeconds = elapsedSeconds;

    if (job.board != null) {
      var stats = job.board.getStatistics();
      if (stats != null) {
        responsePayload.unroutedConnections =
            stats.connections != null ? stats.connections.incompleteCount : 0;
        responsePayload.clearanceViolations =
            stats.clearanceViolations != null ? stats.clearanceViolations.totalCount : 0;
        if (job.routerSettings != null && job.routerSettings.scoring != null) {
          responsePayload.normalizedScore = stats.getNormalizedScore(job.routerSettings.scoring);
        }
      }
    }

    for (Map.Entry<FileFormat, byte[]> entry : multiOut.getFiles().entrySet()) {
      responsePayload.outputs.put(
          entry.getKey().name(), new String(entry.getValue(), StandardCharsets.UTF_8));
    }
    responsePayload.drcSummary = multiOut.getDrcSummary();

    if (job.state == RoutingJobState.COMPLETED) {
      responsePayload.message =
          "Routing completed successfully in "
              + String.format(Locale.US, "%.2f", elapsedSeconds)
              + "s.";
    } else if (job.state == RoutingJobState.TIMED_OUT) {
      responsePayload.message =
          "Routing timed out after " + timeoutSec + "s budget; partial output returned.";
    } else {
      responsePayload.message = "Routing ended with state: " + job.state;
    }

    String responseJson = GSON.toJson(responsePayload);
    FRAnalytics.apiEndpointCalled("POST v1/autoroute", "", responseJson, userId);

    if (job.state == RoutingJobState.TIMED_OUT) {
      return Response.status(Response.Status.ACCEPTED).entity(responseJson).build();
    }
    return Response.ok(responseJson).build();
  }

  private static boolean isTerminalState(RoutingJobState state) {
    return state == RoutingJobState.COMPLETED
        || state == RoutingJobState.TIMED_OUT
        || state == RoutingJobState.CANCELLED
        || state == RoutingJobState.TERMINATED
        || state == RoutingJobState.INVALID;
  }

  private static File validateAndResolveFile(String filePath) {
    if (filePath == null || filePath.isBlank()) {
      throw new IllegalArgumentException("File path must not be null or empty.");
    }
    java.nio.file.Path path = java.nio.file.Path.of(filePath).toAbsolutePath().normalize();
    String pathStr = path.toString().toLowerCase(Locale.ROOT);
    if (pathStr.startsWith("/etc")
        || pathStr.startsWith("/proc")
        || pathStr.startsWith("/sys")
        || pathStr.startsWith("/root")
        || pathStr.startsWith("c:\\windows")
        || pathStr.startsWith("c:\\winnt")) {
      throw new IllegalArgumentException("Access to system directory is forbidden: " + filePath);
    }
    File f = path.toFile();
    if (!f.exists()) {
      throw new IllegalArgumentException("File not found: " + filePath);
    }
    return f;
  }
}
