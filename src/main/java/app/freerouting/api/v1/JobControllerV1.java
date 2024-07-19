package app.freerouting.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/jobs")
public class JobControllerV1
{
  public JobControllerV1()
  {
  }

  @GET
  @Path("/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(@PathParam("sessionId") String sessionId)
  {
    // Return a list of 3 jobs with random data
    return Response.ok("[{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "},{\"id\":2,\"name\":\"Job 2\",\"session\":" + sessionId + "},{\"id\":3,\"name\":\"Job 3\",\"session\":" + sessionId + "}]").build();
  }
}