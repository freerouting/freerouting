package app.freerouting.management;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.GlobalSettings;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * This singleton class is responsible for managing the jobs that will be processed by the router.
 * The jobs are stored in a priority queue where the jobs with the highest priority are processed first.
 * There is only one instance of this class in the Freerouting process.
 */
public class RoutingJobScheduler
{
  private static final RoutingJobScheduler instance = new RoutingJobScheduler();
  public final PriorityBlockingQueue<RoutingJob> jobs = new PriorityBlockingQueue<>();

  // Private constructor to prevent instantiation
  private RoutingJobScheduler()
  {
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
  public RoutingJob enqueueJob(UUID userId, UUID sessionId, RoutingJob job)
  {
    // Check if the job is null or the sessionId doesn't match the job's sessionId and then return an error
    if (job == null || !job.sessionId.equals(sessionId))
    {
      return null;
    }

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
    return (int) (this.jobs.size() - this.jobs.stream().filter(j -> j != job).count());
  }

  public RoutingJob[] listJobs()
  {
    return this.jobs.toArray(new RoutingJob[0]);
  }

  public RoutingJob[] listJobs(String sessionId)
  {
    return this.jobs.stream().filter(j -> j.sessionId.toString().equals(sessionId)).toArray(RoutingJob[]::new);
  }

  public RoutingJob getJob(String jobId)
  {
    return this.jobs.stream().filter(j -> j.id.toString().equals(jobId)).findFirst().orElse(null);
  }
}