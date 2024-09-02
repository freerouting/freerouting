package app.freerouting.api.v1;

import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.RouterSettings;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/jobs")
public class JobControllerV1
{
  public JobControllerV1()
  {
  }

  /* Enqueue a new job with the given session id. In order to start the job, both an input file and its settings must be uploaded first. */
  @POST
  @Path("/{sessionId}/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(
      @PathParam("sessionId")
      String sessionId,
      @RequestBody
      RoutingJob job)
  {
    // Check if the sessionId in the job object matches the path parameter
    if (!job.sessionId.toString().equals(sessionId))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID in the job object does not match the path parameter.\"}").build();
    }

    try
    {
      // Enqueue the job
      job = RoutingJobScheduler.getInstance().enqueueJob(job);
    } catch (Exception e)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"" + e.getMessage() + "\"}").build();
    }

    // Return the job object
    return Response.ok(GsonProvider.GSON.toJson(job)).build();

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
    return Response.ok(GsonProvider.GSON.toJson(RoutingJobScheduler.getInstance().listJobs(sessionId))).build();
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
    // Enqueue the job
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId in the job object matches the path parameter
    if (!job.sessionId.toString().equals(sessionId))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID in the job object does not match the path parameter.\"}").build();
    }

    return Response.ok(GsonProvider.GSON.toJson(job)).build();
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
    // Return an error that this method is not implemented yet
    return Response.status(Response.Status.NOT_IMPLEMENTED).entity("{\"error\":\"This method is not implemented yet.\"}").build();
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
    // Return an error that this method is not implemented yet
    return Response.status(Response.Status.NOT_IMPLEMENTED).entity("{\"error\":\"This method is not implemented yet.\"}").build();
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
    // Return an error that this method is not implemented yet
    return Response.status(Response.Status.NOT_IMPLEMENTED).entity("{\"error\":\"This method is not implemented yet.\"}").build();
  }

  /* Change the settings of the job, such as the router settings. */
  @POST
  @Path("/{sessionId}/{jobId}/changeSettings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId,
      @RequestBody()
      RouterSettings routerSettings)
  {
    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId in the job object matches the path parameter
    if (!job.sessionId.toString().equals(sessionId))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID in the job object does not match the path parameter.\"}").build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The job is already started and cannot be changed.\"}").build();
    }

    // Change the settings of the job
    job.routerSettings = routerSettings;

    // Return the job object
    return Response.ok(GsonProvider.GSON.toJson(job)).build();
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format.
   * Note: the input file limit depends on the server configuration, but it is at least 1MB and typically 30MBs if hosted by ASP.NET Core web server.
   */
  @POST
  @Path("/{sessionId}/{jobId}/uploadInput")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @PathParam("sessionId")
      String sessionId,
      @PathParam("jobId")
      String jobId,
      @RequestBody()
      BoardFilePayload input)
  {
    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId in the job object matches the path parameter
    if (!job.sessionId.toString().equals(sessionId))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID in the job object does not match the path parameter.\"}").build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The job is already started and cannot be changed.\"}").build();
    }

    if (input == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The input data is invalid.\"}").build();
    }

    if ((input.dataBase64 == null) || (input.dataBase64.isEmpty()))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The input data must be encoded and put into the dataBase64 field.\"}").build();
    }

    // Decode the base64 encoded input data to a byte array
    byte[] inputByteArray = java.util.Base64.getDecoder().decode(input.dataBase64);
    if (job.setInput(inputByteArray))
    {
      if (job.input.getFilename().isEmpty())
      {
        job.input.setFilename(job.name);
      }

      return Response.ok(GsonProvider.GSON.toJson(job)).build();
    }
    else
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The input data is invalid.\"}").build();
    }
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
    // Get the job based on the jobId
    var job = RoutingJobScheduler.getInstance().getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response.status(Response.Status.NOT_FOUND).entity("{}").build();
    }

    // Check if the sessionId in the job object matches the path parameter
    if (!job.sessionId.toString().equals(sessionId))
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID in the job object does not match the path parameter.\"}").build();
    }

    // Check if the job is completed
    if (job.state != RoutingJobState.COMPLETED)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The job hasn't finished yet.\"}").build();
    }

    var result = new BoardFilePayload();
    result.jobId = job.id;
    result.setFilename(job.output.getFilename());
    result.setData(job.output.getData().readAllBytes());
    result.dataBase64 = java.util.Base64.getEncoder().encodeToString(result.getData().readAllBytes());

    return Response.ok(GsonProvider.GSON.toJson(result)).build();
  }
}