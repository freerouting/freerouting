package app.freerouting.api.v1;

import app.freerouting.api.BaseController;
import app.freerouting.api.dto.BoardFilePayload;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.RouterSettings;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static app.freerouting.management.gson.GsonProvider.GSON;

@Path("/v1/jobs")
public class JobControllerV1 extends BaseController
{
  private static final ConcurrentHashMap<String, Long> previousOutputChecksums = new ConcurrentHashMap<>();

  public JobControllerV1()
  {
  }

  /* Enqueue a new job with the given session id. In order to start the job, both an input file and its settings must be uploaded first. */
  @POST
  @Path("/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueJob(String requestBody)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    RoutingJob job = GSON.fromJson(requestBody, RoutingJob.class);
    if (job == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job data is invalid.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var request = GSON.toJson(job);
    try
    {
      // Enqueue the job
      job = RoutingJobScheduler
          .getInstance()
          .enqueueJob(job);
      RoutingJobScheduler
          .getInstance()
          .saveJob(job);

      // Save the job when the settings, input or output was updated
      job.addSettingsUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
      job.addInputUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
      job.addOutputUpdatedEventListener(e -> RoutingJobScheduler
          .getInstance()
          .saveJob(e.getJob()));
    } catch (Exception e)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"" + e.getMessage() + "\"}")
          .build();
    }

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("POST v1/jobs/enqueue", request, response);
    return Response
        .ok(response)
        .build();

  }

  /* Get a list of all jobs in the session with the given id, returning only basic details about them. */
  @GET
  @Path("/list/{sessionId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listJobs(
      @PathParam("sessionId")
      String sessionId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the session with the id of sessionId
    Session session = SessionManager
        .getInstance()
        .getSession(sessionId, userId);

    RoutingJob[] result;
    // If the session does not exist, list all jobs
    if ((session == null) || (sessionId.isEmpty()) || (sessionId.equals("all")))
    {
      result = RoutingJobScheduler
          .getInstance()
          .listJobs(null, userId);
    }
    else
    {
      result = RoutingJobScheduler
          .getInstance()
          .listJobs(sessionId);
    }

    // Return a list of jobs in the session
    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/list/" + sessionId, "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Get the current detailed status of the job with id, including statistical data about the (partially) completed board is the process already started. */
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getJob(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Enqueue the job
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId, "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Start or continue the job with the given id. */
  @PUT
  @Path("/{jobId}/start")
  @Produces(MediaType.APPLICATION_JSON)
  public Response startJob(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    job.state = RoutingJobState.READY_TO_START;
    RoutingJobScheduler
        .getInstance()
        .saveJob(job);

    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/start", "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Stop the job with the given id, and cancels the job. */
  @PUT
  @Path("/{jobId}/cancel")
  @Produces(MediaType.APPLICATION_JSON)
  public Response cancelJob(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // TODO: cancel the job
    job.state = RoutingJobState.CANCELLED;
    RoutingJobScheduler
        .getInstance()
        .saveJob(job);

    var response = GsonProvider.GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("PUT v1/jobs/" + jobId + "/cancel", "", response);

    // Return an error that this method is not implemented yet
    return Response
        .status(Response.Status.NOT_IMPLEMENTED)
        .entity("{\"error\":\"This method is not implemented yet.\"}")
        .build();
  }

  /* Change the settings of the job, such as the router settings. */
  @POST
  @Path("/{jobId}/settings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response changeSettings(
      @PathParam("jobId")
      String jobId, String requestBody)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    RouterSettings routerSettings = GSON.fromJson(requestBody, RouterSettings.class);
    if (routerSettings == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The router settings are invalid.\"}")
          .build();
    }

    // Change the settings of the job
    job.setSettings(routerSettings);

    // Return the job object
    var response = GSON.toJson(job);
    FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/settings", GSON.toJson(routerSettings), response);
    return Response
        .ok(response)
        .build();
  }

  /**
   * Upload the input of the job, typically in Specctra DSN format.
   * Note: the input file limit depends on the server configuration, but it is at least 1MB and typically 30MBs if hosted by ASP.NET Core web server.
   */
  @POST
  @Path("/{jobId}/input")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response uploadInput(
      @PathParam("jobId")
      String jobId, String requestBody)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is queued and have not started yet
    if (job.state != RoutingJobState.QUEUED)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job is already started and cannot be changed.\"}")
          .build();
    }

    BoardFilePayload input = GSON.fromJson(requestBody, BoardFilePayload.class);
    if (input == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }

    if ((input.dataBase64 == null) || (input.dataBase64.isEmpty()))
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data must be base-64 encoded and put into the data_base64 field.\"}")
          .build();
    }

    // Decode the base64 encoded input data to a byte array
    byte[] inputByteArray = java.util.Base64
        .getDecoder()
        .decode(input.dataBase64);
    if (!job.setInput(inputByteArray))
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The input data is invalid.\"}")
          .build();
    }
    else
    {
      if (job.input
          .getFilename()
          .isEmpty())
      {
        job.input.setFilename(job.name);
      }

      job.setSettings(new RouterSettings(job.input.statistics.layers.totalCount));

      var request = GSON
          .toJson(input)
          .replace(input.dataBase64, TextManager.shortenString(input.dataBase64, 4));
      var response = GSON.toJson(job);
      FRAnalytics.apiEndpointCalled("POST v1/jobs/" + jobId + "/input", request, response);
      return Response
          .ok(response)
          .build();
    }
  }

  /* Download the output of the job, typically in Specctra SES format. */
  @GET
  @Path("/{jobId}/output")
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadOutput(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job is completed
    if (job.state != RoutingJobState.COMPLETED)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The job hasn't finished yet.\"}")
          .build();
    }

    var result = new BoardFilePayload();
    result.jobId = job.id;
    result.setFilename(job.output.getFilename());
    result.setData(job.output
        .getData()
        .readAllBytes());
    result.dataBase64 = java.util.Base64
        .getEncoder()
        .encodeToString(result
            .getData()
            .readAllBytes());

    var response = GSON.toJson(result);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/output", "", response.replace(result.dataBase64, TextManager.shortenString(result.dataBase64, 4)));
    return Response
        .ok(response)
        .build();
  }

  /* Stream the output of the job in real-time using Server-Sent Events. */
  @GET
  @Path("/{jobId}/output/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamOutput(
      @PathParam("jobId")
      String jobId,
      @Context
      SseEventSink eventSink,
      @Context
      Sse sse)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist or session is invalid, close the connection
    if (job == null || SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId) == null)
    {
      eventSink.close();
      return;
    }

    // Create a scheduled executor for periodic updates
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Schedule periodic updates every 250ms
    executor.scheduleAtFixedRate(() ->
    {
      try
      {
        if (job.output != null && job.output.getData() != null)
        {
          var result = new BoardFilePayload();
          result.jobId = job.id;
          result.setFilename(job.output.getFilename());
          result.setData(job.output
              .getData()
              .readAllBytes());
          result.dataBase64 = Base64
              .getEncoder()
              .encodeToString(result
                  .getData()
                  .readAllBytes());

          Long previousOutputChecksum = previousOutputChecksums.get(jobId);

          if ((previousOutputChecksum == null) || (result.crc32 != previousOutputChecksum))
          {
            previousOutputChecksums.put(jobId, result.crc32);

            OutboundSseEvent event = sse
                .newEventBuilder()
                .id(String.valueOf(System.currentTimeMillis()))
                .data(GSON.toJson(result))
                .build();

            eventSink.send(event);
          }
        }

        // Close the connection if the job is completed or cancelled
        if (job.state == RoutingJobState.COMPLETED || job.state == RoutingJobState.CANCELLED)
        {
          eventSink.close();
          executor.shutdown();
        }
      } catch (Exception e)
      {
        FRLogger.error("Error while streaming output", e);
        eventSink.close();
        executor.shutdown();
      }
    }, 0, 200, TimeUnit.MILLISECONDS);

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/output/stream", "", "stream-started");
  }

  @GET
  @Path("/{jobId}/logs")
  @Produces(MediaType.APPLICATION_JSON)
  public Response logs(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    var logEntries = FRLogger.getLogEntries();
    var logs = logEntries.getEntries(null, job.id);

    var response = GSON.toJson(logs);
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs", "", response);
    return Response
        .ok(response)
        .build();
  }

  /* Stream the log entries of the job in real-time using Server-Sent Events. */
  @GET
  @Path("/{jobId}/logs/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamLogs(
      @PathParam("jobId")
      String jobId,
      @Context
      SseEventSink eventSink,
      @Context
      Sse sse)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist or session is invalid, close the connection
    if (job == null || SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId) == null)
    {
      eventSink.close();
    }

    // Create a scheduled executor for periodic updates
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // stream a new log entry when the job logsEntryAdded event was fired
    job.addLogEntryAddedEventListener(e ->
    {
      try
      {
        var result = e.getLogEntry();
        OutboundSseEvent event = sse
            .newEventBuilder()
            .id(String.valueOf(System.currentTimeMillis()))
            .data(GSON.toJson(result))
            .build();

        eventSink.send(event);

        // Close the connection if the job is completed or cancelled
        if (job.state == RoutingJobState.COMPLETED || job.state == RoutingJobState.CANCELLED)
        {
          eventSink.close();
          executor.shutdown();
        }
      } catch (Exception ex)
      {
        FRLogger.error("Error while streaming logs", ex);
        eventSink.close();
        executor.shutdown();
      }
    });

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/logs/stream", "", "stream-started");
  }

  /* Get DRC report for a job */
  @GET
  @Path("/{jobId}/drc")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDrcReport(
      @PathParam("jobId")
      String jobId)
  {
    // Authenticate the user
    UUID userId = AuthenticateUser();

    // Get the job based on the jobId
    var job = RoutingJobScheduler
        .getInstance()
        .getJob(jobId);

    // If the job does not exist, return a 404 response
    if (job == null)
    {
      return Response
          .status(Response.Status.NOT_FOUND)
          .entity("{\"error\":\"Job not found.\"}")
          .build();
    }

    // Check if the sessionId references a valid session
    Session session = SessionManager
        .getInstance()
        .getSession(job.sessionId.toString(), userId);
    if (session == null)
    {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"The session ID '" + job.sessionId + "' is invalid.\"}")
          .build();
    }

    // Check if the job has a board loaded, and load it if needed
    if (!app.freerouting.board.BoardLoader.loadBoardIfNeeded(job))
    {
      // Try to load the board if input is available
      if (job.input != null && job.input.format == app.freerouting.gui.FileFormat.DSN)
      {
        try
        {
          app.freerouting.interactive.HeadlessBoardManager boardManager = new app.freerouting.interactive.HeadlessBoardManager(null, job);
          boardManager.loadFromSpecctraDsn(job.input.getData(), null, new app.freerouting.board.ItemIdentificationNumberGenerator());
          job.board = boardManager.get_routing_board();
        } catch (Exception e)
        {
          FRLogger.error("Couldn't load the board for DRC check", e);
          return Response
              .status(Response.Status.INTERNAL_SERVER_ERROR)
              .entity("{\"error\":\"Failed to load board: " + e.getMessage() + "\"}")
              .build();
        }
      }
      else
      {
        return Response
            .status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Failed to load board for DRC check.\"}")
            .build();
      }
    }

    // Run DRC check
    DesignRulesChecker drcChecker = new DesignRulesChecker(job.board, job.drcSettings);

    // Determine coordinate unit (default to mm)
    String coordinateUnit = "mm";

    // Get source file name
    String sourceFileName = job.input != null ? job.input.getFilename() : "unknown";

    // Generate DRC report
    String drcReportJson = drcChecker.generateReportJson(sourceFileName, coordinateUnit);

    // Log the API call
    FRAnalytics.apiEndpointCalled("GET v1/jobs/" + jobId + "/drc", "", "drc-report-generated");

    return Response
        .ok(drcReportJson)
        .build();
  }
}