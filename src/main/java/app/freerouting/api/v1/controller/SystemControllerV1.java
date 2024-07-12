package app.freerouting.api.v1.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/system")
public class SystemControllerV1
{

  public SystemControllerV1()
  {
  }

  @GET
  @Path("/health")
  @Produces(MediaType.APPLICATION_JSON)
  public Response checkHealth()
  {
    return Response.ok("{\"status\":\"UP\"}").build();
  }
}