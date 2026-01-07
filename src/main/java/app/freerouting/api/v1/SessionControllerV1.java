package app.freerouting.api.v1;

import static app.freerouting.management.gson.GsonProvider.GSON;

import app.freerouting.api.BaseController;
import app.freerouting.core.Session;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.SessionManager;
import app.freerouting.management.analytics.FRAnalytics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/v1/sessions")
@Tag(name = "Sessions", description = "Session management endpoints for creating and managing routing sessions")
public class SessionControllerV1 extends BaseController {

  @Context
  private HttpHeaders httpHeaders;

  public SessionControllerV1() {
  }

  @Operation(summary = "List all sessions", description = "Retrieves a list of all routing sessions accessible to the authenticated user.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "List of session IDs retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "[\"550e8400-e29b-41d4-a716-446655440000\", \"660e8400-e29b-41d4-a716-446655440001\"]")))
  })
  @GET
  @Path("/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions() {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // filter the list of sessions to only include the ones that the user has access
    // to
    var response = GSON.toJson(SessionManager.getInstance().listSessionIds(userId));
    FRAnalytics.apiEndpointCalled("GET v1/sessions/list", "", response);
    return Response.ok(response).build();
  }

  @Operation(summary = "Create new session", description = "Creates a new routing session for the authenticated user. The session will be associated with the user's ID and the specified host environment.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Session created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Session.class))),
      @ApiResponse(responseCode = "500", description = "Failed to create session", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{}")))
  })
  @POST
  @Path("/create")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createSession() {
    // Authenticate the user
    UUID userId = AuthenticateUser();
    String host = httpHeaders.getHeaderString("Freerouting-Environment-Host");

    // create a new session using the authenticated user as the owner
    Session newSession = SessionManager.getInstance().createSession(userId, host);
    if (newSession == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{}").build();
    } else {
      var response = GSON.toJson(newSession);
      FRAnalytics.apiEndpointCalled("POST v1/sessions/create", "", response);
      return Response.ok(response).build();
    }
  }

  @Operation(summary = "Get session details", description = "Retrieves detailed information about a specific routing session by its ID.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Session details retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Session.class))),
      @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{}")))
  })
  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(
      @Parameter(description = "Unique identifier of the session", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("sessionId") String sessionId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Return one session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId, userId);
    if (session == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    } else {
      var response = GSON.toJson(session);
      FRAnalytics.apiEndpointCalled("GET v1/sessions/" + sessionId, "", response);
      return Response.ok(response).build();
    }
  }

  @Operation(summary = "Get session logs", description = "Retrieves all log entries associated with a specific routing session.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Session logs retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON)),
      @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(mediaType = MediaType.APPLICATION_JSON, examples = @ExampleObject(value = "{}")))
  })
  @GET
  @Path("/{sessionId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @Parameter(description = "Unique identifier of the session", example = "550e8400-e29b-41d4-a716-446655440000") @PathParam("sessionId") String sessionId) {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Return one session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId, userId);
    if (session == null) {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    var logEntries = FRLogger.getLogEntries();
    var logs = logEntries.getEntries(null, session.id);

    var response = GSON.toJson(logs);
    FRAnalytics.apiEndpointCalled("GET v1/sessions/" + sessionId + "/logs", "", response);
    return Response.ok(response).build();
  }
}