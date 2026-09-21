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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * JAX-RS controller serving micro-surveys and recording responses.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>{@code GET /v1/surveys/active} — returns the active survey definition (JSON {@code
 *       SurveyDefinition}), or {@code 204 No Content} when none is configured, it is malformed, or
 *       it has expired. No API key required — surveys must reach fresh installs that have no key.
 *   <li>{@code POST /v1/surveys/active} — publishes or updates the active survey in-memory.
 *       Protected by {@code FREEROUTING__SURVEYS__ADMIN_KEY} via {@code X-Survey-Admin-Key} or
 *       {@code Authorization: Bearer <key>}.
 *   <li>{@code DELETE /v1/surveys/active} — retires the active survey so that subsequent GET
 *       requests return 204 No Content. Protected by {@code FREEROUTING__SURVEYS__ADMIN_KEY}.
 *   <li>{@code POST /v1/surveys/{surveyId}/response} — records a survey response payload to
 *       BigQuery ({@code survey_response} table) with server-side deduplication on {@code
 *       (survey_id, user_id)}. Replayed responses return {@code 204 No Content}.
 * </ul>
 *
 * <p>Maintainers can publish or retire a survey either dynamically via the admin endpoints, or by
 * changing the {@code FREEROUTING__SURVEYS__ACTIVE_SURVEY} environment variable on the API host —
 * no desktop client release and no API redeploy needed. Survey IDs must never be recycled; use a
 * new ID for every new survey run.
 */
@Path("/v1/surveys")
@Tag(name = "Surveys", description = "In-app micro-survey endpoints")
public class SurveyControllerV1 {

  /** Environment variable holding the JSON definition of the active survey. */
  static final String ACTIVE_SURVEY_ENV = "FREEROUTING__SURVEYS__ACTIVE_SURVEY";

  /** Environment variable holding the pre-shared admin secret for survey lifecycle management. */
  static final String ADMIN_KEY_ENV = "FREEROUTING__SURVEYS__ADMIN_KEY";

  /** Custom HTTP header for providing the survey admin key. */
  static final String ADMIN_KEY_HEADER = "X-Survey-Admin-Key";

  /**
   * In-memory override for the active survey definition, settable via {@code POST
   * /v1/surveys/active}. When {@code null}, the controller falls back to {@link
   * #ACTIVE_SURVEY_ENV}.
   */
  private static volatile String dynamicActiveSurveyJson = null;

  /** Serves the active survey, or {@code 204} when there is nothing to ask. */
  @Operation(
      summary = "Get the active micro-survey",
      description =
          "Returns the active survey definition, or 204 No Content when no eligible survey is"
              + " configured. Publicly accessible without authentication.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "An active survey is available"),
    @ApiResponse(responseCode = "204", description = "No active survey — nothing to ask"),
    @ApiResponse(
        responseCode = "500",
        description = "The active survey configuration contains invalid JSON")
  })
  @GET
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getActiveSurvey() {
    return buildActiveSurveyResponse(getEffectiveActiveSurveyJson(), Instant.now());
  }

  /** Returns the dynamic active survey if set, falling back to the environment variable. */
  static String getEffectiveActiveSurveyJson() {
    String dynamic = dynamicActiveSurveyJson;
    return dynamic != null ? dynamic : System.getenv(ACTIVE_SURVEY_ENV);
  }

  /** Sets the dynamic in-memory active survey JSON. */
  static void setDynamicActiveSurvey(String json) {
    dynamicActiveSurveyJson = json;
  }

  /** Resets the dynamic active survey JSON back to {@code null} (env var fallback). */
  static void resetDynamicActiveSurvey() {
    dynamicActiveSurveyJson = null;
  }

  /**
   * Pure decision logic for the {@code active} endpoint, separated from {@link System#getenv} so it
   * is directly unit-testable.
   *
   * @param json the raw environment-variable or dynamic content, may be {@code null} or blank
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
      FRLogger.warn("Survey configuration contains invalid JSON, ignoring: " + e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Survey configuration contains invalid JSON.\"}")
          .build();
    }
    if (survey == null || !survey.isValid() || survey.isExpired(now)) {
      return Response.noContent().build();
    }
    // Serve the raw configured JSON verbatim — the server adds nothing to it.
    return Response.ok(json).build();
  }

  /**
   * Publishes or updates the active micro-survey definition.
   *
   * @param authHeader HTTP Authorization header
   * @param customHeader custom X-Survey-Admin-Key header
   * @param requestBody survey definition JSON
   * @return 200 on success, 400 on invalid/expired survey, 401 on unauthorized, 403 when admin key
   *     is unset
   */
  @Operation(
      summary = "Publish or update the active micro-survey",
      description =
          "Publishes or replaces the active micro-survey in-memory. Requires the admin pre-shared"
              + " key via X-Survey-Admin-Key or Authorization: Bearer <key>.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Survey published successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid or expired survey definition"),
    @ApiResponse(responseCode = "401", description = "Missing or invalid admin authorization"),
    @ApiResponse(responseCode = "403", description = "Admin key not configured on server")
  })
  @POST
  @Path("/active")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response publishActiveSurvey(
      @HeaderParam("Authorization") String authHeader,
      @HeaderParam(ADMIN_KEY_HEADER) String customHeader,
      String requestBody) {
    return processPublishActiveSurvey(
        authHeader,
        customHeader,
        System.getenv(ADMIN_KEY_ENV),
        requestBody,
        Instant.now(),
        SurveyControllerV1::setDynamicActiveSurvey);
  }

  /**
   * Retires the active micro-survey so subsequent queries return 204 No Content.
   *
   * @param authHeader HTTP Authorization header
   * @param customHeader custom X-Survey-Admin-Key header
   * @return 200 on success, 401 on unauthorized, 403 when admin key is unset
   */
  @Operation(
      summary = "Retire the active micro-survey",
      description =
          "Retires the active micro-survey so GET /v1/surveys/active returns 204 No Content."
              + " Requires the admin pre-shared key via X-Survey-Admin-Key or Authorization: Bearer <key>.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Survey retired successfully"),
    @ApiResponse(responseCode = "401", description = "Missing or invalid admin authorization"),
    @ApiResponse(responseCode = "403", description = "Admin key not configured on server")
  })
  @DELETE
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public Response deleteActiveSurvey(
      @HeaderParam("Authorization") String authHeader,
      @HeaderParam(ADMIN_KEY_HEADER) String customHeader) {
    return processDeleteActiveSurvey(
        authHeader, customHeader, System.getenv(ADMIN_KEY_ENV), () -> setDynamicActiveSurvey(""));
  }

  /** Pure decision logic for publishing an active survey. */
  static Response processPublishActiveSurvey(
      String authHeader,
      String customHeader,
      String expectedAdminKey,
      String requestBody,
      Instant now,
      Consumer<String> surveySetter) {
    if (expectedAdminKey == null || expectedAdminKey.isBlank()) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(
              "{\"error\":\"Survey publishing is disabled: admin key not configured on server.\"}")
          .build();
    }
    if (!isAuthorizedAdmin(authHeader, customHeader, expectedAdminKey)) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity("{\"error\":\"Missing or invalid admin authorization.\"}")
          .build();
    }
    if (requestBody == null || requestBody.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Request body is required.\"}")
          .build();
    }

    SurveyDefinition survey;
    try {
      survey = GSON.fromJson(requestBody, SurveyDefinition.class);
    } catch (Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Invalid JSON payload: " + e.getMessage() + "\"}")
          .build();
    }

    if (survey == null || !survey.isValid()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              "{\"error\":\"Invalid survey definition. Required fields: id, question, and at least"
                  + " 2 options.\"}")
          .build();
    }

    if (survey.isExpired(now)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"Cannot publish a survey that is already expired.\"}")
          .build();
    }

    if (surveySetter != null) {
      surveySetter.accept(requestBody);
    }

    return Response.ok("{\"status\":\"published\",\"id\":\"" + survey.id + "\"}").build();
  }

  /** Pure decision logic for retiring an active survey. */
  static Response processDeleteActiveSurvey(
      String authHeader, String customHeader, String expectedAdminKey, Runnable surveyClearer) {
    if (expectedAdminKey == null || expectedAdminKey.isBlank()) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(
              "{\"error\":\"Survey publishing is disabled: admin key not configured on server.\"}")
          .build();
    }
    if (!isAuthorizedAdmin(authHeader, customHeader, expectedAdminKey)) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity("{\"error\":\"Missing or invalid admin authorization.\"}")
          .build();
    }

    if (surveyClearer != null) {
      surveyClearer.run();
    }

    return Response.ok("{\"status\":\"retired\"}").build();
  }

  /** Verifies admin authorization in constant-time using {@link MessageDigest#isEqual}. */
  static boolean isAuthorizedAdmin(
      String authHeader, String customHeader, String expectedAdminKey) {
    if (expectedAdminKey == null || expectedAdminKey.isBlank()) {
      return false;
    }
    String providedKey = extractProvidedKey(authHeader, customHeader);
    if (providedKey == null || providedKey.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        providedKey.getBytes(StandardCharsets.UTF_8),
        expectedAdminKey.getBytes(StandardCharsets.UTF_8));
  }

  /** Extracts the admin key from custom or Authorization headers. */
  static String extractProvidedKey(String authHeader, String customHeader) {
    if (customHeader != null && !customHeader.isBlank()) {
      return customHeader.trim();
    }
    if (authHeader != null && !authHeader.isBlank()) {
      String trimmed = authHeader.trim();
      if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
        return trimmed.substring(7).trim();
      }
      return trimmed;
    }
    return null;
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
