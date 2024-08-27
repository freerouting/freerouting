package app.freerouting.api.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/system")
public class SystemControllerDev
{
  public SystemControllerDev()
  {
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get job by session and job ID", description = "Returns a job object based on session and job ID")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Successful response"),
      @ApiResponse(responseCode = "404", description = "Job not found")
  })
  public Response getStatus(
      @Parameter(description = "Session ID", required = true)
      @PathParam("sessionId")
      String sessionId,
      @Parameter(description = "Job ID", required = true)
      @PathParam("jobId")
      String jobId)
  {
    // Return a random system status JSON object with status, cpu, memory, session count, etc. fields
    return Response.ok("{\"status\":\"OK\",\"cpu\":0.5,\"memory\":1855,\"sessions\":5}").build();
  }
}