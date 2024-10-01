package app.freerouting.management;

import app.freerouting.Freerouting;
import app.freerouting.board.BoardFileDetails;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.core.StoppableThread;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.GlobalSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * This singleton class is responsible for managing the jobs that will be processed by the router.
 * The jobs are stored in a priority queue where the jobs with the highest priority are processed first.
 * There is only one instance of this class in the Freerouting process.
 */
public class RoutingJobScheduler
{
  private static final RoutingJobScheduler instance = new RoutingJobScheduler();
  public final LinkedList<RoutingJob> jobs = new LinkedList<>();
  private final int maxParallelJobs = 5;

  // Private constructor to prevent instantiation
  private RoutingJobScheduler()
  {
    // start a loop to process the jobs on another thread
    Thread loopThread = new Thread(() ->
    {
      while (true)
      {
        try
        {
          // loop through jobs with the READY_TO_START state, order them according to their priority and start them up to the maximum number of parallel jobs
          while (jobs.stream().count() > 0)
          {
            // sort the jobs by priority
            Collections.sort(jobs);

            // start the jobs up to the maximum number of parallel jobs
            for (RoutingJob job : jobs)
            {
              if (job.state == RoutingJobState.READY_TO_START)
              {
                int parallelJobs = (int) jobs.stream().filter(j -> j.state == RoutingJobState.RUNNING).count();

                if (parallelJobs < maxParallelJobs)
                {
                  if ((job.input == null) || (job.input.getData() == null))
                  {
                    FRLogger.warn("RoutingJob input is null, it is skipped.");
                    job.state = RoutingJobState.INVALID;
                    continue;
                  }

                  // load the board from the input into a RoutingBoard object
                  if (job.input.format == FileFormat.DSN)
                  {
                    HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
                    boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
                    job.board = boardManager.get_routing_board();
                  }
                  else
                  {
                    FRLogger.warn("Only DSN format is supported as an input.");
                    job.state = RoutingJobState.INVALID;
                    continue;
                  }

                  // All pre-checks look fine, start the routing process on a new thread
                  StoppableThread routerThread = new RoutingJobSchedulerActionThread(job);
                  job.thread = routerThread;
                  job.thread.start();
                  job.state = RoutingJobState.RUNNING;
                }
                else
                {
                  break;
                }
              }

              if ((job.state == RoutingJobState.COMPLETED) && ((job.output == null) || (job.output.size == 0)))
              {
                if (job.output == null)
                {
                  job.output = new BoardFileDetails(job.board);
                  job.output.format = FileFormat.SES;
                  job.output.setFilename(job.input.getFilenameWithoutExtension() + ".ses");
                }

                // save the result to the output field as a Specctra SES file
                if (job.output.format == FileFormat.SES)
                {
                  HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
                  boardManager.update_routing_board(job.board);

                  // Save the SES file after the auto-router has finished
                  try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
                  {
                    if (boardManager.saveAsSpecctraSessionSes(baos, job.name))
                    {
                      job.output.setData(baos.toByteArray());
                    }
                  } catch (Exception e)
                  {
                    FRLogger.error("Couldn't save the output into the job object.", e);
                  }
                }
              }
            }
          }

          // wait for a short time before checking the queue again
          Thread.sleep(250);
        } catch (InterruptedException e)
        {
          FRLogger.error("RoutingJobScheduler thread was interrupted.", e);
        }
      }
    });

    loopThread.start();
  }

  /**
   * Returns the singleton instance of the RoutingJobScheduler.
   *
   * @return The singleton instance.
   */
  public static RoutingJobScheduler getInstance()
  {
    return instance;
  }

  private String UUIDtoShortCode(UUID uuid)
  {
    return uuid.toString().substring(0, 6).toUpperCase();
  }

  /**
   * Enqueues a job to be processed by the router.
   *
   * @param job The job to enqueue.
   * @return The job that was enqueued.
   */
  public RoutingJob enqueueJob(RoutingJob job)
  {
    // Get the session object from the SessionManager and user ID from the job
    UUID sessionId = job.sessionId;
    if (sessionId == null)
    {
      throw new IllegalArgumentException("The job must have a session ID.");
    }

    var session = SessionManager.getInstance().getSession(sessionId.toString());
    if (session == null)
    {
      throw new IllegalArgumentException("The session does not exist.");
    }

    UUID userId = session.userId;
    if (userId == null)
    {
      throw new IllegalArgumentException("The session must have a user ID.");
    }

    job.state = RoutingJobState.QUEUED;
    this.jobs.add(job);

    if (Freerouting.globalSettings.featureFlags.saveJobs)
    {
      try
      {
        saveJob("U-" + UUIDtoShortCode(userId), "S-" + UUIDtoShortCode(sessionId), job);
      } catch (IOException e)
      {
        FRLogger.error(String.format("Failed to save job for user '%s' in session '%s' to disk.", userId, sessionId), e);
      }
    }
    return job;
  }

  private void saveJob(String userFolder, String sessionFolder, RoutingJob job) throws IOException
  {
    // Create the user's folder if it doesn't exist
    Path userFolderPath = GlobalSettings.userdataPath.resolve("data").resolve(userFolder);

    // Make sure that we have the directory structure in place, and create it if it doesn't exist
    Files.createDirectories(userFolderPath);

    // List all directories in the user folder and check if they start with a number
    // If they do, then they are job folders, and we can get the highest number and increment it
    int jobFolderCount = Files.list(userFolderPath).filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).map(s -> s.split("_")[0]) // Extract the numeric prefix before the underscore
                              .filter(s -> s.matches("\\d+")) // Ensure it is numeric
                              .mapToInt(Integer::parseInt).max().orElse(0);

    // Create the session's folder if it doesn't exist
    Path sessionFolderPath = userFolderPath.resolve(String.format("%04d", jobFolderCount + 1) + "_" + sessionFolder);
    Files.createDirectories(sessionFolderPath);

    // Save the job to the session's folder using ISO standard date and time format
    String jobFilename = "FRJ_" + TextManager.convertInstantToString(job.createdAt) + "__J-" + UUIDtoShortCode(job.id) + ".json";
    Path jobFilePath = sessionFolderPath.resolve(jobFilename);

    try (Writer writer = Files.newBufferedWriter(jobFilePath, StandardCharsets.UTF_8))
    {
      GsonProvider.GSON.toJson(job, writer);
    } catch (Exception e)
    {
      FRLogger.error(String.format("Failed to save job '%s' to disk.", job.id), e);
    }
  }

  /**
   * Returns the position of the job in the queue.
   *
   * @param job The job to get the position of.
   * @return The position of the job in the queue or -1 if the job is not in the queue. 0 means the job is next in line.
   */
  public int getQueuePosition(RoutingJob job)
  {
    return this.jobs.indexOf(job);
  }

  public RoutingJob[] listJobs()
  {
    return this.jobs.toArray(RoutingJob[]::new);
  }

  public RoutingJob[] listJobs(String sessionId)
  {
    return this.jobs.stream().filter(j -> j.sessionId.toString().equals(sessionId)).toArray(RoutingJob[]::new);
  }

  public RoutingJob[] listJobs(String sessionId, UUID userId)
  {
    SessionManager sessionManager = SessionManager.getInstance();

    if (sessionId == null)
    {
      // Get all sessions that belong to the user
      Session[] sessions = sessionManager.getSessions(null, userId);

      // Iterate through the sessions and list all jobs belonging to them
      List<RoutingJob> result = new LinkedList<>();
      for (Session session : sessions)
      {
        // List all jobs belonging to the user in the session
        result.addAll(List.of(listJobs(session.id.toString())));
      }

      return result.toArray(RoutingJob[]::new);
    }
    else
    {
      Session session = sessionManager.getSession(sessionId, userId);

      if (session != null)
      {
        // List all jobs belonging to the user in the session
        return listJobs(session.id.toString());
      }
    }

    return new RoutingJob[0];
  }

  public RoutingJob getJob(String jobId)
  {
    return this.jobs.stream().filter(j -> j.id.toString().equals(jobId)).findFirst().orElse(null);
  }

  public void clearJobs(String sessionId)
  {
    this.jobs.removeIf(j -> j.sessionId.toString().equals(sessionId));
  }
}