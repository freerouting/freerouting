package app.freerouting.api.v1;

import static app.freerouting.Freerouting.globalSettings;
import static app.freerouting.management.gson.GsonProvider.GSON;

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
      // TODO: implement some caching mechanism, and insert the cached items in a
      // batch

      // Send the payload to the BigQuery analytics service
      var bigqueryClient = new BigQueryClient(Constants.FREEROUTING_VERSION,
          globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey);
      bigqueryClient.track(trackPayload.userId, trackPayload.anonymousId, trackPayload.event, trackPayload.properties);
    } catch (Exception e) {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("""
              {
                "error": "An error occurred while processing the request."
                "message": "%s"
              }
              """.formatted(e.getMessage()))
          .build();
    }

    return Response
        .ok()
        .build();
  }

  @Operation(summary = "Identify user", description = "Associates user traits with a user ID for analytics purposes. This endpoint is currently not implemented.")
  @RequestBody(description = "User identification payload containing user traits", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Payload.class)))
  @ApiResponses(value = {
      @ApiResponse(responseCode = "501", description = "Operation not implemented", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The operation is not implemented.\"}"))),
      @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{\"error\":\"The input data is invalid.\"}"))),
      @ApiResponse(responseCode = "500", description = "Server error or BigQuery configuration issue")
  })
  @POST
  @Path("/identify")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response identity(String requestBody) {
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

    return Response
        .status(Response.Status.NOT_IMPLEMENTED)
        .entity("{\"error\":\"The operation is not implemented.\"}")
        .build();
  }
}