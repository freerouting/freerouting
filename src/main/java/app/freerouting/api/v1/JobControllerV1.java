package app.freerouting.api.v1;

import app.freerouting.core.Session;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.gson.GsonProvider;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/jobs")
public class JobControllerV1
{
  public JobControllerV1()
  {
  }

  /* Get a list of all jobs in the session with the given id, returning only basic details about them. */
  @GET
  @Path("/{sessionId}/list")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @PathParam("sessionId")
      String sessionId)
  {
    // Get the session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId);

    // If the session does not exist, return a 404 response
    if (session == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Return a list of jobs in the session
    // TODO: filter the list of jobs to only include the ones that belong to the session
    return Response.ok(GsonProvider.GSON.toJson(RoutingJobScheduler.getInstance().getJobs())).build();
  }

  /* Get the current detailed status of the job with id, including statistical data about the (partially) completed board is the process already started. */
  @GET
  @Path("/{sessionId}/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job with the id of jobId
    return Response.ok("{\"id\":" + jobId + ",\"name\":\"Job " + jobId + "\",\"session\":" + sessionId + "}").build();
  }

  /* Queue a new job with the given session id. In order to start the job, both an input file and its settings must be uploaded first. */
  @POST
  @Path("/{sessionId}/queue")
  @Produces(MediaType.APPLICATION_JSON)
  public Response queueJob(
      @PathParam("sessionId")
      String sessionId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Start or continue the job with the given id. */
  @PUT
  @Path("/{sessionId}/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Pause the job with the given id, and keeps it in the job queue for later. */
  @PUT
  @Path("/{sessionId}/{jobId}/pause")
  @Produces(MediaType.APPLICATION_JSON)
  public Response pauseJob(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Stop the job with the given id, and cancels the job. */
  @PUT
  @Path("/{sessionId}/{jobId}/stop")
  @Produces(MediaType.APPLICATION_JSON)
  public Response stopJob(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Change the settings of the job, such as the router settings. */
  @POST
  @Path("/{sessionId}/{jobId}/changeSettings")
  @Produces(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Upload the input of the job, typically in Specctra DSN format. */
  @POST
  @Path("/{sessionId}/{jobId}/uploadInput")
  @Produces(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }

  /* Download the output of the job, typically in Specctra SES format. */
  @GET
  @Path("/{sessionId}/{jobId}/downloadOutput")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId)
  {
    // Return a random job object with the session id of sessionId
    return Response.ok("{\"id\":1,\"name\":\"Job 1\",\"session\":" + sessionId + "}").build();
  }
}