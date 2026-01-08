package app.freerouting.api.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/sessions")
@Tag(name = "Dev - Sessions", description = "Mock session endpoints for testing and development. Returns static test data.")
public class SessionControllerMocked {

  public SessionControllerMocked() {
  }

  @Operation(summary = "List all sessions (mock)", description = "Returns a mock list of session IDs for testing purposes.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock session list retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions() {
    return Response.ok("""
        [
             "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
             "f7306a32-2cec-44be-a467-cc8d901f98e3",
             "1054bc5f-1660-4f25-acf9-ac2d2db26cb9"
        ]
        """).build();
  }

  @Operation(summary = "Create a new session (mock)", description = "Returns mock data for a newly created session for testing purposes.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock session created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @POST
  @Path("/create")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createSession() {
    return Response.ok("""
        {
            "id": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
            "userId": "d0071163-7ba3-46b3-b3af-bc2ebfd4d1a0",
            "host": "KiCad/8.0.1",
            "isGuiSession": false
        }
        """).build();
  }

  @Operation(summary = "Get session details (mock)", description = "Returns mock session details for testing purposes.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock session details retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(
      @Parameter(description = "Session ID (ignored in mock)", example = "8c6b2f64-b6db-4fb6-9a2f-17610acad966") @PathParam("sessionId") String sessionId) {
    return Response.ok("""
        {
             "id": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
             "userId": "d0071163-7ba3-46b3-b3af-bc2ebfd4d1a0",
             "host": "Postman/11.14",
             "isGuiSession": false
        }
        """).build();
  }

  @Operation(summary = "Get session logs (mock)", description = "Returns empty mock logs for testing purposes.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Mock logs retrieved successfully (always empty)", content = @Content(mediaType = MediaType.APPLICATION_JSON))
  })
  @GET
  @Path("/{sessionId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @Parameter(description = "Session ID (ignored in mock)", example = "8c6b2f64-b6db-4fb6-9a2f-17610acad966") @PathParam("sessionId") String sessionId) {
    return Response.ok("""
        [
        ]
        """).build();
  }
}