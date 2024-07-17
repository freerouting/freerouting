package app.freerouting.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/session")
public class SessionControllerV1
{
  public SessionControllerV1()
  {
  }

  @GET
  @Path("/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions()
  {
    // Return a list of 5 sessions with random data
    return Response.ok("[{\"id\":1,\"name\":\"Session 1\"},{\"id\":2,\"name\":\"Session 2\"},{\"id\":3,\"name\":\"Session 3\"},{\"id\":4,\"name\":\"Session 4\"},{\"id\":5,\"name\":\"Session 5\"}]").build();
  }
}