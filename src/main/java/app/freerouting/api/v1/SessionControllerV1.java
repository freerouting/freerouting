package app.freerouting.api.v1;

import app.freerouting.api.BaseController;
import app.freerouting.core.Session;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static app.freerouting.management.gson.GsonProvider.GSON;

@Path("/v1/sessions")
public class SessionControllerV1 extends BaseController
{
  @Context
  private HttpHeaders httpHeaders;

  public SessionControllerV1()
  {
  }

  @GET
  @Path("/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions()
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // filter the list of sessions to only include the ones that the user has access to
    var response = GSON.toJson(SessionManager.getInstance().listSessionIds(userId));
    FRAnalytics.apiEndpointCalled("GET v1/sessions/list", "", response);
    return Response.ok(response).build();
  }

  @POST
  @Path("/create")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createSession()
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();
    String host = httpHeaders.getHeaderString("Freerouting-Environment-Host");

    // create a new session using the authenticated user as the owner
    Session newSession = SessionManager.getInstance().createSession(userId, host);
    if (newSession == null)
    {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{}").build();
    }
    else
    {
      var response = GSON.toJson(newSession);
      FRAnalytics.apiEndpointCalled("POST v1/sessions/create", "", response);
      return Response.ok(response).build();
    }
  }

  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(
      @PathParam("sessionId")
      String sessionId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Return one session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId, userId);
    if (session == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }
    else
    {
      var response = GSON.toJson(session);
      FRAnalytics.apiEndpointCalled("GET v1/sessions/" + sessionId, "", response);
      return Response.ok(response).build();
    }
  }

  @GET
  @Path("/{sessionId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @PathParam("sessionId")
      String sessionId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Return one session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId, userId);
    if (session == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    var logEntries = FRLogger.getLogEntries();
    var logs = logEntries.getEntries(null, session.id);

    var response = GSON.toJson(logs);
    FRAnalytics.apiEndpointCalled("GET v1/sessions/" + sessionId + "/logs", "", response);
    return Response.ok(response).build();
  }
}