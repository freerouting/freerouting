package app.freerouting.api.v1;

import static app.freerouting.Freerouting.globalSettings;
import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.constants.Constants;
import app.freerouting.management.analytics.BigQueryClient;
import app.freerouting.management.analytics.dto.Payload;
import io.swagger.v3.oas.annotations.Operation;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS controller that receives analytics event payloads from Freerouting client libraries
 * and writes them to BigQuery via the singleton {@link app.freerouting.management.analytics.BigQueryClient}.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /v1/analytics/track} — record a single analytics event (e.g.
 *       {@code "API Endpoint Called"}). Requires the BigQuery service-account key to be
 *       configured via {@code FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY}.</li>
 *   <li>{@code POST /v1/analytics/identify} — associate user traits with a user ID or anonymous
 *       ID and persist them to BigQuery ({@code identify} table).</li>
 * </ul>
 *
 * <p>Both endpoints are excluded from API-key validation and environment-host validation
 * (public access), so that remote Freerouting instances can send analytics without needing
 * a caller API key.</p>
 */
@Path("/v1/analytics")
@Tag(name = "Analytics", description = "Endpoints for tracking user actions and analytics data")
public class AnalyticsControllerV1 {

  @Operation(summary = "Track user action", description = "Records an analytics event for tracking user actions and behavior. This endpoint accepts event data and stores it in BigQuery for analysis.")
  @RequestBody(description = "Analytics tracking payload containing user identification and event data", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Payload.class), examples = @ExampleObject(name = "Job Started Event", value = """
      {
        "userId": "user_12345",
        "anonymousId": "anon_67890",
        "event": "job_started",
        "properties": {
          "jobId": "550e8400-e29b-41d4-a716-446655440000",
          "sessionId": "660e8400-e29b-41d4-a716-446655440001"
        }
      }
      """)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Event tracked successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The input data is invalid.\"}"))),
      @ApiResponse(responseCode = "500", description = "Server error or BigQuery configuration issue", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The BigQuery service account key is not configured.\"}")))
  })
  @POST
  @Path("/track")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response trackAction(String requestBody) {
    Payload trackPayload = GSON.fromJson(requestBody, Payload.class);
    if (trackPayload == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if (globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey == null) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(
              "{\"error\":\"The BigQuery service account key is not configured. It must be set to the 'FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY' environment variable in JSON format.\"}")
          .build();
    }

    try {
      // Reuse the singleton BigQueryClient — credential refresh and GCP service
      // construction happen at most once per process (or on key rotation).
      BigQueryClient.getInstance(Constants.FREEROUTING_VERSION,
          globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey)
          .track(trackPayload.userId, trackPayload.anonymousId, trackPayload.event, trackPayload.properties);
    } catch (Exception e) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("""
              {
                "error": "An error occurred while processing the request.",
                "message": "%s"
              }
              """.formatted(e.getMessage()))
          .build();
    }

    return Response
        .ok()
        .build();
  }

  @Operation(summary = "Identify user", description = "Associates user traits (e.g. anonymous, user_id, user_email, first_seen, client_version, os_name, os_version) with a user ID or anonymous ID and persists them to BigQuery for analytics purposes.")
  @RequestBody(description = "User identification payload containing user traits", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Payload.class), examples = @ExampleObject(name = "Anonymous client session", value = """
      {
        "anonymousId": "550e8400-e29b-41d4-a716-446655440000",
        "traits": {
          "anonymous": "true",
          "user_id": "550e8400-e29b-41d4-a716-446655440000",
          "user_email": "",
          "first_seen": "2026-06-09T10:00:00Z",
          "client_version": "2.0.0",
          "os_name": "Linux",
          "os_version": "5.15.0"
        }
      }
      """)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "User identified and traits persisted to BigQuery successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid request data — payload is missing or both userId and anonymousId are null", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The input data is invalid. At least one of userId or anonymousId must be provided.\"}"))),
      @ApiResponse(responseCode = "500", description = "Server error or BigQuery configuration issue", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The BigQuery service account key is not configured.\"}")))
  })
  @POST
  @Path("/identify")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response identity(String requestBody) {
    Payload identifyPayload = GSON.fromJson(requestBody, Payload.class);
    if (identifyPayload == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    // At least one identity anchor must be present.
    if ((identifyPayload.userId == null || identifyPayload.userId.isBlank())
        && (identifyPayload.anonymousId == null || identifyPayload.anonymousId.isBlank())) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid. At least one of userId or anonymousId must be provided.\"}")
          .build();
    }

    if (globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey == null) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(
              "{\"error\":\"The BigQuery service account key is not configured. It must be set to the 'FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY' environment variable in JSON format.\"}")
          .build();
    }

    try {
      BigQueryClient.getInstance(Constants.FREEROUTING_VERSION,
          globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey)
          .identify(identifyPayload.userId, identifyPayload.anonymousId, identifyPayload.traits);
    } catch (Exception e) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("""
              {
                "error": "An error occurred while processing the request.",
                "message": "%s"
              }
              """.formatted(e.getMessage()))
          .build();
    }

    return Response
        .ok()
        .build();
  }
}
