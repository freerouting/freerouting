package app.freerouting.api.v1;

import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.api.dto.RoutingJobDTO;
import app.freerouting.core.*;
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
  @Path("/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(@RequestBody String requestBody) {
    try {
      // Log the received request body
      System.out.println("Received request body: " + requestBody);

      // Manually deserialize the request body
      RoutingJobDTO jobDTO = GsonProvider.GSON.fromJson(requestBody, RoutingJobDTO.class);

      // Check if the sessionId references a valid session
      Session session = SessionManager.getInstance().getSession(jobDTO.getSessionId().toString());
      if (session == null) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"The session ID '" + jobDTO.getSessionId() + "' is invalid.\"}")
                .build();
      }

      // Convert DTO to RoutingJob
      RoutingJob job = convertDtoToRoutingJob(jobDTO);

      // Enqueue the job
      job = RoutingJobScheduler.getInstance().enqueueJob(job);

      // Convert the enqueued job back to DTO for response
      RoutingJobDTO responseDto = convertRoutingJobToDto(job);

      return Response.ok(GsonProvider.GSON.toJson(responseDto)).build();
    } catch (Exception e) {
      e.printStackTrace(); // Log the full stack trace
      return Response.status(Response.Status.BAD_REQUEST)
              .entity("{\"error\":\"Error processing request: " + e.getMessage() + "\"}")
              .build();
    }
  }

  private RoutingJob convertDtoToRoutingJob(RoutingJobDTO dto) {
    RoutingJob job = new RoutingJob(dto.getSessionId());
    job.name = dto.getName();
    job.state = convertJobState(dto.getState());
    job.priority = convertJobPriority(dto.getPriority());
    job.stage = convertJobStage(dto.getStage());
    return job;
  }

  private RoutingJobDTO convertRoutingJobToDto(RoutingJob job) {
    RoutingJobDTO dto = new RoutingJobDTO();
    dto.setId(job.id);
    dto.setSessionId(job.sessionId);
    dto.setName(job.name);
    dto.setState(convertRoutingJobState(job.state));
    dto.setPriority(convertRoutingJobPriority(job.priority));
    dto.setStage(convertRoutingStage(job.stage));
    return dto;
  }

  // Conversion methods
  private RoutingJobState convertJobState(RoutingJobDTO.JobState state) {
    switch (state) {
      case INVALID: return RoutingJobState.INVALID;
      case QUEUED: return RoutingJobState.QUEUED;
      case READY_TO_START: return RoutingJobState.READY_TO_START;
      case RUNNING: return RoutingJobState.RUNNING;
      case COMPLETED: return RoutingJobState.COMPLETED;
//      case FAILED: return RoutingJobState.FAILED;
      case CANCELLED: return RoutingJobState.CANCELLED;
      default: throw new IllegalArgumentException("Unknown job state: " + state);
    }
  }

  private RoutingJobDTO.JobState convertRoutingJobState(RoutingJobState state) {
    switch (state) {
      case INVALID: return RoutingJobDTO.JobState.INVALID;
      case QUEUED: return RoutingJobDTO.JobState.QUEUED;
      case READY_TO_START: return RoutingJobDTO.JobState.READY_TO_START;
      case RUNNING: return RoutingJobDTO.JobState.RUNNING;
      case COMPLETED: return RoutingJobDTO.JobState.COMPLETED;
//      case FAILED: return RoutingJobDTO.JobState.FAILED;
      case CANCELLED: return RoutingJobDTO.JobState.CANCELLED;
      default: throw new IllegalArgumentException("Unknown routing job state: " + state);
    }
  }

  private RoutingJobPriority convertJobPriority(RoutingJobDTO.JobPriority priority) {
    switch (priority) {
      case LOW: return RoutingJobPriority.LOW;
      case NORMAL: return RoutingJobPriority.NORMAL;
      case HIGH: return RoutingJobPriority.HIGH;
      default: throw new IllegalArgumentException("Unknown job priority: " + priority);
    }
  }

  private RoutingJobDTO.JobPriority convertRoutingJobPriority(RoutingJobPriority priority) {
    switch (priority) {
      case LOW: return RoutingJobDTO.JobPriority.LOW;
      case NORMAL: return RoutingJobDTO.JobPriority.NORMAL;
      case HIGH: return RoutingJobDTO.JobPriority.HIGH;
      default: throw new IllegalArgumentException("Unknown routing job priority: " + priority);
    }
  }

  private RoutingStage convertJobStage(RoutingJobDTO.JobStage stage) {
    return RoutingStage.fromString(stage.name());
  }

  private RoutingJobDTO.JobStage convertRoutingStage(RoutingStage stage) {
    try {
      return RoutingJobDTO.JobStage.valueOf(stage.name());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown routing stage: " + stage);
    }
  }

  /* Get a list of all jobs in the session with the given id, returning only basic details about them. */
  @GET
  @Path("/list/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @PathParam("sessionId")
      String sessionId)
  {
    // Get the session with the id of sessionId
    Session session = SessionManager.getInstance().getSession(sessionId);

    RoutingJob[] result;
    // If the session does not exist, list all jobs
    if ((session == null) || (sessionId.isEmpty()) || (sessionId.equals("all")))
    {
      result = RoutingJobScheduler.getInstance().listJobs();
    }
    else
    {
      result = RoutingJobScheduler.getInstance().listJobs(sessionId);
    }

    // Return a list of jobs in the session
    return Response.ok(GsonProvider.GSON.toJson(result)).build();
  }

  /* Get the current detailed status of the job with id, including statistical data about the (partially) completed board is the process already started. */
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}").build();
    }

    return Response.ok(GsonProvider.GSON.toJson(job)).build();
  }

  /* Start or continue the job with the given id. */
  @PUT
  @Path("/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}").build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The job is already started and cannot be changed.\"}").build();
    }

    job.state = RoutingJobState.READY_TO_START;

    return Response.ok(GsonProvider.GSON.toJson(job)).build();
  }

  /* Stop the job with the given id, and cancels the job. */
  @PUT
  @Path("/{jobId}/cancel")
  @Produces(MediaType.APPLICATION_JSON)
  public Response cancelJob(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "'is invalid.\"}").build();
    }

    // Return an error that this method is not implemented yet
    return Response.status(Response.Status.NOT_IMPLEMENTED).entity("{\"error\":\"This method is not implemented yet.\"}").build();
  }

  /* Change the settings of the job, such as the router settings. */
  @POST
  @Path("/{jobId}/changeSettings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "'is invalid.\"}").build();
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
  @Path("/{jobId}/uploadInput")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "'is invalid.\"}").build();
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

      // Set the viaCount in the job's input
      job.input.setViaCount(input.getViaCount());

      return Response.ok(GsonProvider.GSON.toJson(job)).build();
    }
    else
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The input data is invalid.\"}").build();
    }
  }

  /* Download the output of the job, typically in Specctra SES format. */
  @GET
  @Path("/{jobId}/downloadOutput")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
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

    // Check if the sessionId references a valid session
    Session session = SessionManager.getInstance().getSession(job.sessionId.toString());
    if (session == null)
    {
      return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"The session ID '" + job.sessionId + "'is invalid.\"}").build();
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
