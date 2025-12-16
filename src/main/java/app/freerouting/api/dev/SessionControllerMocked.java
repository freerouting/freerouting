package app.freerouting.api.dev;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dev/sessions")
public class SessionControllerMocked
{
  public SessionControllerMocked()
  {
  }

  @GET
  @Path("/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listSessions()
  {
    return Response.ok("""
                       [
                            "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "f7306a32-2cec-44be-a467-cc8d901f98e3",
                            "1054bc5f-1660-4f25-acf9-ac2d2db26cb9"
                       ]
                       """).build();
  }

  @POST
  @Path("/create")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createSession()
  {
    return Response.ok("""
                       {
                           "id": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                           "userId": "d0071163-7ba3-46b3-b3af-bc2ebfd4d1a0",
                           "host": "KiCad/8.0.1",
                           "isGuiSession": false
                       }
                       """).build();
  }

  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSession(
      @PathParam("sessionId")
      String sessionId)
  {
    return Response.ok("""
                       {
                            "id": "8c6b2f64-b6db-4fb6-9a2f-17610acad966",
                            "userId": "d0071163-7ba3-46b3-b3af-bc2ebfd4d1a0",
                            "host": "Postman/11.14",
                            "isGuiSession": false
                       }
                       """).build();
  }

  @GET
  @Path("/{sessionId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @PathParam("sessionId")
      String sessionId)
  {
    return Response.ok("""
                       [
                       ]
                       """).build();
  }
}
