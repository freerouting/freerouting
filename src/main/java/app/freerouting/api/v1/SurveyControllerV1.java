package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.Freerouting;
import app.freerouting.analytics.BigQueryClient;
import app.freerouting.constants.Constants;
import app.freerouting.logger.FRLogger;
import app.freerouting.surveys.SurveyDefinition;
import app.freerouting.surveys.SurveyResponsePayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

/**
 * JAX-RS controller serving micro-surveys and recording responses.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>{@code GET /v1/surveys/active} — returns the survey defined by the {@code
 *       FREEROUTING__SURVEYS__ACTIVE_SURVEY} environment variable on the API host (JSON {@code
 *       SurveyDefinition}), or {@code 204 No Content} when none is configured, it is malformed, or
 *       it has expired. No API key required — surveys must reach fresh installs that have no key.
 *   <li>{@code POST /v1/surveys/{surveyId}/response} — records a survey response payload to
 *       BigQuery ({@code survey_response} table) with server-side deduplication on {@code
 *       (survey_id, user_id)}. Replayed responses return {@code 204 No Content}.
 * </ul>
 *
 * <p>Maintainers publish or retire a survey purely by changing the environment variable — no
 * desktop client release and no API redeploy. Survey IDs must never be recycled; use a new ID for
 * every new survey run.
 */
@Path("/v1/surveys")
@Tag(name = "Surveys", description = "In-app micro-survey endpoints (public, unauthenticated)")
public class SurveyControllerV1 {

  /** Environment variable holding the JSON definition of the active survey. */
  static final String ACTIVE_SURVEY_ENV = "FREEROUTING__SURVEYS__ACTIVE_SURVEY";

  /** Serves the active survey, or {@code 204} when there is nothing to ask. */
  @Operation(
      summary = "Get the active micro-survey",
      description =
          "Returns the survey definition configured via the FREEROUTING__SURVEYS__ACTIVE_SURVEY"
              + " environment variable, or 204 No Content when no eligible survey is configured.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "An active survey is available"),
    @ApiResponse(responseCode = "204", description = "No active survey — nothing to ask"),
    @ApiResponse(
        responseCode = "500",
        description = "The active survey environment variable contains invalid JSON")
  })
  @GET
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getActiveSurvey() {
    return buildActiveSurveyResponse(System.getenv(ACTIVE_SURVEY_ENV), Instant.now());
  }

  /**
   * Pure decision logic for the {@code active} endpoint, separated from {@link System#getenv} so it
   * is directly unit-testable.
   *
   * @param json the raw environment-variable content, may be {@code null} or blank
   * @param now the current server time for expiry checking
   * @return 200 with the survey JSON, 204 when unset/expired, 500 on malformed JSON
   */
  static Response buildActiveSurveyResponse(String json, Instant now) {
    if (json == null || json.isBlank()) {
      return Response.noContent().build();
    }
    SurveyDefinition survey;
    try {
      survey = GSON.fromJson(json, SurveyDefinition.class);
    } catch (Exception e) {
      FRLogger.warn(
          "FREEROUTING__SURVEYS__ACTIVE_SURVEY contains invalid JSON, ignoring: " + e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"FREEROUTING__SURVEYS__ACTIVE_SURVEY contains invalid JSON.\"}")
          .build();
    }
    if (survey == null || !survey.isValid() || survey.isExpired(now)) {
      return Response.noContent().build();
    }
    // Serve the raw configured JSON verbatim — the server adds nothing to it.
    return Response.ok(json).build();
  }

  /** Functional interface for checking whether a user has already answered a survey. */
  @FunctionalInterface
  interface DedupChecker {
    boolean hasSurveyResponse(String surveyId, String userId);
  }

  /** Functional interface for asynchronously recording a survey response. */
  @FunctionalInterface
  interface ResponseRecorder {
    void recordSurveyResponse(String surveyId, String userId, String option, String clientVersion);
  }

  /**
   * Submits a user response to a micro-survey.
   *
   * @param surveyId the survey ID from the path
   * @param requestBody JSON string adhering to {@link SurveyResponsePayload}
   * @return 200 on new response recorded, 204 when duplicate (already recorded), 400 on invalid
   *     payload, 500 on server configuration error
   */
  @Operation(
      summary = "Submit a survey response",
      description =
          "Records a user response to a micro-survey. Enforces server-side deduplication"
              + " per (survey_id, user_id); replayed responses return 204 No Content.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Survey response recorded successfully"),
    @ApiResponse(responseCode = "204", description = "Response already recorded (deduplicated)"),
    @ApiResponse(responseCode = "400", description = "Invalid request payload"),
    @ApiResponse(responseCode = "500", description = "BigQuery service not configured or error")
  })
  @POST
  @Path("/{surveyId}/response")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response submitResponse(@PathParam("surveyId") String surveyId, String requestBody) {
    if (Freerouting.globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(
              "{\"error\":\"The BigQuery service account key is not configured. It must be set"
                  + " to the"
                  + " 'FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY'"
                  + " environment variable in JSON format.\"}")
          .build();
    }

    BigQueryClient bqClient;
    try {
      bqClient =
          BigQueryClient.getInstance(
              Constants.FREEROUTING_VERSION,
              Freerouting.globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey);
    } catch (Exception e) {
      FRLogger.error("Failed to initialize BigQueryClient for survey response", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Failed to initialize BigQuery client: " + e.getMessage() + "\"}")
          .build();
    }

    return processSurveyResponse(
        surveyId, requestBody, bqClient::hasSurveyResponse, bqClient::recordSurveyResponse);
  }

  /**
   * Pure decision logic for response submission and server-side deduplication, decoupled from
   * BigQuery infrastructure so it is directly unit-testable.
   *
   * @param pathSurveyId survey ID from path parameter
   * @param requestBody JSON string representing {@link SurveyResponsePayload}
   * @param dedupChecker functional checker for existing response
   * @param responseRecorder consumer to record newly submitted response
   * @return HTTP response (200, 204, or 400)
   */
  static Response processSurveyResponse(
      String pathSurveyId,
      String requestBody,
      DedupChecker dedupChecker,
      ResponseRecorder responseRecorder) {
    if (pathSurveyId == null || pathSurveyId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"survey_id in path is required.\"}")
          .build();
    }
    if (requestBody == null || requestBody.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Request body is required.\"}")
          .build();
    }

    SurveyResponsePayload payload;
    try {
      payload = GSON.fromJson(requestBody, SurveyResponsePayload.class);
    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Invalid JSON payload: " + e.getMessage() + "\"}")
          .build();
    }

    if (payload == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Payload cannot be empty.\"}")
          .build();
    }

    if (payload.userId == null || payload.userId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"user_id is required.\"}")
          .build();
    }

    if (payload.option == null || payload.option.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"option is required.\"}")
          .build();
    }

    if (payload.surveyId != null
        && !payload.surveyId.isBlank()
        && !pathSurveyId.equals(payload.surveyId)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"survey_id in body does not match survey_id in path.\"}")
          .build();
    }

    if (dedupChecker != null && dedupChecker.hasSurveyResponse(pathSurveyId, payload.userId)) {
      return Response.noContent().build();
    }

    if (responseRecorder != null) {
      responseRecorder.recordSurveyResponse(
          pathSurveyId, payload.userId, payload.option, payload.clientVersion);
    }

    return Response.ok("{\"status\":\"recorded\"}").build();
  }
}
