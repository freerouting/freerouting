package app.freerouting.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/sessions")
public class SessionControllerV1
{
  public SessionControllerV1()
  {
  }

  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions()
  {
    // Return a list of 5 sessions with random data
    return Response.ok("[{\"id\":1,\"name\":\"Session 1\"},{\"id\":2,\"name\":\"Session 2\"},{\"id\":3,\"name\":\"Session 3\"},{\"id\":4,\"name\":\"Session 4\"},{\"id\":5,\"name\":\"Session 5\"}]").build();
  }

  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(@PathParam("sessionId") String sessionId)
  {
    // Return one session with the id of sessionId
    return Response.ok("{\"id\":" + sessionId + ",\"name\":\"Session " + sessionId + "\"}").build();
  }
}