package app.freerouting.api.v1;

import jakarta.ws.rs.*;
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

  @GET
  @Path("/{sessionId}/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(@PathParam("sessionId") String sessionId, @PathParam("jobId") String jobId)
  {
    // Return a random job with the id of jobId
    return Response.ok("{\"id\":" + jobId + ",\"name\":\"Job " + jobId + "\",\"session\":" + sessionId + "}").build();
  }

  @POST
  @Path("/{sessionId}/queue")
  @Produces(MediaType.APPLICATION_JSON)
  public Response queueJob(@PathParam("sessionId") String sessionId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  @PUT
  @Path("/{sessionId}/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(@PathParam("sessionId") String sessionId, @PathParam("jobId") String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  @PUT
  @Path("/{sessionId}/{jobId}/stop")
  @Produces(MediaType.APPLICATION_JSON)
  public Response stopJob(@PathParam("sessionId") String sessionId, @PathParam("jobId") String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }
}