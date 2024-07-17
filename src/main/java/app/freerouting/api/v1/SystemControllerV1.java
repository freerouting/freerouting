package app.freerouting.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/system")
public class SystemControllerV1
{
  public SystemControllerV1()
  {
  }

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStatus()
  {
    // Return a random system status JSON object with status, cpu, memory, session count, etc. fields
    return Response.ok("{\"status\":\"OK\",\"cpu\":0.5,\"memory\":1855,\"sessions\":5}").build();
  }
}