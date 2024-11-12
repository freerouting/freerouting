package app.freerouting.api.v1;

import app.freerouting.constants.Constants;
import app.freerouting.management.analytics.BigQueryClient;
import app.freerouting.management.analytics.dto.Payload;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static app.freerouting.Freerouting.globalSettings;
import static app.freerouting.management.gson.GsonProvider.GSON;

@Path("/v1/analytics")
public class AnalyticsControllerV1
{
  /**
   * Tracks an action performed by a user.
   *
   * @param requestBody The request body containing the tracking data.
   * @return A response indicating the success or failure of the operation.
   */
  @POST
  @Path("/track")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response trackAction(String requestBody)
  {
    Payload trackPayload = GSON.fromJson(requestBody, Payload.class);
    if (trackPayload == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if (globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey == null)
    {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"The BigQuery service account key is not configured. It must be set to the 'FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY' environment variable in JSON format.\"}")
          .build();
    }

    try
    {
      // TODO: implement some caching mechanism, and insert the cached items in a batch

      // Send the payload to the BigQuery analytics service
      var bigqueryClient = new BigQueryClient(Constants.FREEROUTING_VERSION, globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey);
      bigqueryClient.track(trackPayload.userId, trackPayload.anonymousId, trackPayload.event, trackPayload.properties);
    } catch (Exception e)
    {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(String.format("""
                                {
                                  "error": "An error occurred while processing the request."
                                  "message": "%s"
                                }
                                """, e.getMessage()))
          .build();
    }

    return Response
        .ok()
        .build();
  }

  @POST
  @Path("/identify")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response identity(String requestBody)
  {
    Payload trackPayload = GSON.fromJson(requestBody, Payload.class);
    if (trackPayload == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if (globalSettings.usageAndDiagnosticData.bigqueryServiceAccountKey == null)
    {
      return Response
          .status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"The BigQuery service account key is not configured. It must be set to the 'FREEROUTING__USAGE_AND_DIAGNOSTIC_DATA__BIGQUERY_SERVICE_ACCOUNT_KEY' environment variable in JSON format.\"}")
          .build();
    }

    return Response
        .status(Response.Status.NOT_IMPLEMENTED)
        .entity("{\"error\":\"The operation is not implemented.\"}")
        .build();
  }
}