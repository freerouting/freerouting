package app.freerouting.api.v1;

import app.freerouting.core.Session;
import app.freerouting.management.SessionManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static app.freerouting.management.gson.GsonProvider.GSON;

@Path("/v1/sessions")
public class SessionControllerV1
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
    // TODO: filter the list of sessions to only include the ones that the user has access to
    return Response.ok(GSON.toJson(SessionManager.getInstance().listSessionIds())).build();
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
      return Response.ok(GSON.toJson(newSession)).build();
    }
  }

  private UUID AuthenticateUser()
  {
    String userIdString = httpHeaders.getHeaderString("Freerouting-Profile-ID");
    String userEmailString = httpHeaders.getHeaderString("Freerouting-Profile-Email");

    if (((userIdString == null) || (userIdString.isEmpty())) && ((userEmailString == null) || (userEmailString.isEmpty())))
    {
      throw new IllegalArgumentException("Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header must be set in order to get authenticated.");
    }

    UUID userId = null;

    // We need to get the userId from the e-mail address first
    if ((userIdString != null) && (!userIdString.isEmpty()))
    {
      try
      {
        userId = UUID.fromString(userIdString);
      } catch (IllegalArgumentException e)
      {
        // We couldn't parse the userId, so we fall back to e-mail address
      }
    }

    if ((userEmailString != null) && (!userEmailString.isEmpty()))
    {
      // TODO: get userId from e-mail address
    }

    if (userId == null)
    {
      throw new IllegalArgumentException("The user couldn't be authenticated based on the Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header values.");
    }

    return userId;
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