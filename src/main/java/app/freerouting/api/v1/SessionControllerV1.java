package app.freerouting.api.v1;

import app.freerouting.core.Session;
import app.freerouting.management.SessionManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static app.freerouting.management.gson.GsonProvider.GSON;

@Path("/v1/sessions")
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
    // TODO: filter the list of sessions to only include the ones that the user has access to
    return Response.ok(GSON.toJson(SessionManager.getInstance().listSessionIds())).build();
  }

  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(
      @PathParam("sessionId")
      String sessionId)
  {
    // Return one session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId);
    if (session == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }
    else
    {
      return Response.ok(GSON.toJson(session)).build();
    }
  }
}