package app.freerouting.api.v1;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.logger.FRLogger;
import app.freerouting.surveys.SurveyDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

/**
 * JAX-RS controller serving the currently active micro-survey.
 *
 * <h2>Endpoints</h2>
 *
 * <ul>
 *   <li>{@code GET /v1/surveys/active} — returns the survey defined by the {@code
 *       FREEROUTING__SURVEYS__ACTIVE_SURVEY} environment variable on the API host (JSON {@code
 *       SurveyDefinition}), or {@code 204 No Content} when none is configured, it is malformed, or
 *       it has expired. No API key required — surveys must reach fresh installs that have no key.
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
}
