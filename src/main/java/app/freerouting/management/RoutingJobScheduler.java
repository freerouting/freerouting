package app.freerouting.management;

import static app.freerouting.Freerouting.globalSettings;

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
 * This singleton class is responsible for managing the jobs that will be
 * processed by the router. The jobs are stored in a priority queue where the
 * jobs with the highest priority are processed first.
 * There is only one instance of this class in the Freerouting process.
 */
public class RoutingJobScheduler {

  private static final RoutingJobScheduler instance = new RoutingJobScheduler();
  public final LinkedList<RoutingJob> jobs = new LinkedList<>();
  private final int maxParallelJobs = 5;

  // Private constructor to prevent instantiation
  private RoutingJobScheduler() {
    // start a loop to process the jobs on another thread
    Thread loopThread = new Thread(() -> {
      while (true) {
        try {
          // loop through jobs with the READY_TO_START state, order them according to
          // their priority and start them up to the maximum number of parallel jobs
          while (jobs
              .stream()
              .count() > 0) {
            RoutingJob[] jobsArray;
            synchronized (jobs) {
              // sort the jobs by priority
              Collections.sort(jobs);

              jobsArray = jobs.toArray(RoutingJob[]::new);
            }

            // start the jobs up to the maximum number of parallel jobs (and make a copy of
            // the list to avoid concurrent modification)
            for (RoutingJob job : jobsArray) {
              if (job.state == RoutingJobState.READY_TO_START) {
                int parallelJobs = (int) jobs
                    .stream()
                    .filter(j -> j.state == RoutingJobState.RUNNING)
                    .count();

                if (parallelJobs < maxParallelJobs) {
                  if ((job.input == null) || (job.input.getData() == null)) {
                    FRLogger.warn("RoutingJob input is null, it is skipped.");
                    job.state = RoutingJobState.INVALID;
                    continue;
                  }

                  // load the board from the input into a RoutingBoard object
                  if (job.input.format == FileFormat.DSN) {
                    HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
                    boardManager.loadFromSpecctraDsn(job.input.getData(), null,
                        new ItemIdentificationNumberGenerator());
                    job.board = boardManager.get_routing_board();

                    // DEBUG: Log settings BEFORE board initialization
                    FRLogger.info("=== BEFORE board initialization ===");
                    FRLogger.info("Board dimensions: " + job.board.bounding_box.width() + " x "
                        + job.board.bounding_box.height());
                    FRLogger.info("Board layer count: " + job.board.get_layer_count());
                    FRLogger.info("RouterSettings layer count: "
                        + (job.routerSettings.isLayerActive != null ? job.routerSettings.isLayerActive.length
                            : "null"));
                    if (job.routerSettings.isLayerActive != null) {
                      for (int i = 0; i < job.routerSettings.isLayerActive.length; i++) {
                        FRLogger.info("  Layer " + i + ": active=" + job.routerSettings.isLayerActive[i] +
                            ", horizontal="
                            + (job.routerSettings.isPreferredDirectionHorizontalOnLayer != null
                                ? job.routerSettings.isPreferredDirectionHorizontalOnLayer[i]
                                : "null"));
                      }
                    }

                    // CRITICAL FIX: Only reinitialize RouterSettings with board-specific
                    // optimizations
                    // if the settings were created with defaults (layer count = 0 or null).
                    // If settings already have proper layer configuration (from test or DSN),
                    // preserve them to avoid changing preferred directions which can break routing.

                    boolean hasLayerConfiguration = (job.routerSettings.isLayerActive != null &&
                        job.routerSettings.isLayerActive.length > 0);

                    if (!hasLayerConfiguration) {
                      FRLogger.info("Applying board-specific initialization (settings have no layer configuration)");

                      // Save the current settings (which include command-line overrides)
                      app.freerouting.settings.RouterSettings cliSettings = job.routerSettings;

                      // Create new settings with board-specific optimizations
                      job.routerSettings = new app.freerouting.settings.RouterSettings(job.board);

                      // DEBUG: Log settings AFTER board initialization
                      FRLogger.info("=== AFTER board initialization ===");
                      FRLogger.info("RouterSettings layer count: "
                          + (job.routerSettings.isLayerActive != null ? job.routerSettings.isLayerActive.length
                              : "null"));
                      if (job.routerSettings.isLayerActive != null) {
                        for (int i = 0; i < job.routerSettings.isLayerActive.length; i++) {
                          FRLogger.info("  Layer " + i + ": active=" + job.routerSettings.isLayerActive[i] +
                              ", horizontal="
                              + (job.routerSettings.isPreferredDirectionHorizontalOnLayer != null
                                  ? job.routerSettings.isPreferredDirectionHorizontalOnLayer[i]
                                  : "null"));
                        }
                      }

                      // Manually copy critical command-line parameters that must be preserved
                      // (reflection-based copying doesn't work reliably for all fields)
                      job.routerSettings.enabled = cliSettings.enabled;
                      job.routerSettings.algorithm = cliSettings.algorithm;
                      job.routerSettings.jobTimeoutString = cliSettings.jobTimeoutString;
                      job.routerSettings.maxPasses = cliSettings.maxPasses;
                      job.routerSettings.trace_pull_tight_accuracy = cliSettings.trace_pull_tight_accuracy;
                      job.routerSettings.vias_allowed = cliSettings.vias_allowed;
                      job.routerSettings.automatic_neckdown = cliSettings.automatic_neckdown;
                      job.routerSettings.maxThreads = cliSettings.maxThreads;
                      job.routerSettings.ignoreNetClasses = cliSettings.ignoreNetClasses;
                      // Copy nested settings objects using reflection
                      job.setSettings(cliSettings);
                    } else {
                      FRLogger.info("Preserving existing layer configuration (settings already initialized)");
                      // Settings already have proper layer configuration from test/DSN
                      // Verify layer count matches the board
                      if (job.routerSettings.isLayerActive.length != job.board.get_layer_count()) {
                        FRLogger.warn("Layer count mismatch: settings=" + job.routerSettings.isLayerActive.length +
                            ", board=" + job.board.get_layer_count() +
                            " - This should not happen and may cause routing issues!");
                        // DO NOT call setLayerCount() here as it would reset all layer preferences!
                      }
                    }

                    // Load SES file if specified
                    if (globalSettings.design_session_filename != null) {
                      try {
                        java.io.File sesFile = new java.io.File(globalSettings.design_session_filename);
                        if (sesFile.exists()) {
                          FRLogger.info("Loading SES file: " + globalSettings.design_session_filename);
                          java.io.FileInputStream sesStream = new java.io.FileInputStream(sesFile);
                          app.freerouting.designforms.specctra.SesFileReader.read(sesStream, job.board);
                          sesStream.close();
                        } else {
                          FRLogger.warn("SES file not found: " + globalSettings.design_session_filename);
                        }
                      } catch (Exception e) {
                        FRLogger.error("Failed to load SES file", e);
                      }
                    }
                  } else {
                    FRLogger.warn("Only DSN format is supported as an input.");
                    job.state = RoutingJobState.INVALID;
                    continue;
                  }

                  // All pre-checks look fine, start the routing process on a new thread
                  StoppableThread routerThread = new RoutingJobSchedulerActionThread(job);
                  job.thread = routerThread;
                  job.thread.start();
                  job.state = RoutingJobState.RUNNING;
                } else {
                  break;
                }
              }
            }
          }

          // wait for a short time before checking the queue again
          Thread.sleep(250);
        } catch (InterruptedException e) {
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
  public static RoutingJobScheduler getInstance() {
    return instance;
  }

  private String UUIDtoShortCode(UUID uuid) {
    return uuid
        .toString()
        .substring(0, 6)
        .toUpperCase();
  }

  /**
   * Enqueues a job to be processed by the router.
   *
   * @param job The job to enqueue.
   * @return The job that was enqueued.
   */
  public RoutingJob enqueueJob(RoutingJob job) {
    // Get the session object from the SessionManager and user ID from the job
    UUID sessionId = job.sessionId;
    if (sessionId == null) {
      throw new IllegalArgumentException("The job must have a session ID.");
    }

    var session = SessionManager
        .getInstance()
        .getSession(sessionId.toString());
    if (session == null) {
      throw new IllegalArgumentException("The session does not exist.");
    }

    UUID userId = session.userId;
    if (userId == null) {
      throw new IllegalArgumentException("The session must have a user ID.");
    }

    job.state = RoutingJobState.QUEUED;

    synchronized (jobs) {
      this.jobs.add(job);
    }

    globalSettings.statistics.incrementJobsStarted();

    return job;
  }

  public void saveJob(RoutingJob job) {
    if (globalSettings.featureFlags.saveJobs) {
      String sessionIdString = "null";
      String userIdString = "null";

      try {
        Session session = SessionManager
            .getInstance()
            .getSession(job.sessionId.toString());

        if (session == null) {
          FRLogger.error("Failed to save job in session '%s' to disk, because the session does not exist."
              .formatted(job.sessionId), null);
        }

        sessionIdString = session.id.toString();
        userIdString = session.userId.toString();

        saveJob("U-" + UUIDtoShortCode(session.userId), "S-" + UUIDtoShortCode(session.id), job);
      } catch (IOException e) {
        FRLogger.error(
            "Failed to save job for user '%s' in session '%s' to disk.".formatted(userIdString, sessionIdString), e);
      }
    }

  }

  private void saveJob(String userFolder, String sessionFolder, RoutingJob job) throws IOException {
    // Create the user's folder if it doesn't exist
    Path userFolderPath = GlobalSettings
        .getUserDataPath()
        .resolve("data")
        .resolve(userFolder);

    // Make sure that we have the directory structure in place, and create it if it
    // doesn't exist
    Files.createDirectories(userFolderPath);

    // Check if we already have a directory that has a name with the ending of
    // sessionFolder
    Path sessionFolderPath = Files
        .list(userFolderPath)
        .filter(Files::isDirectory)
        .filter(p -> p
            .getFileName()
            .toString()
            .endsWith(sessionFolder))
        .findFirst()
        .orElse(null);

    if (sessionFolderPath == null) {
      // List all directories in the user folder and check if they start with a number
      // If they do, then they are job folders, and we can get the highest number and
      // increment it
      int jobFolderCount = Files
          .list(userFolderPath)
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .map(s -> s.split("_")[0])// Extract the numeric prefix before the underscore
          .filter(s -> s.matches("\\d+"))// Ensure it is numeric
          .mapToInt(Integer::parseInt)
          .max()
          .orElse(0);

      sessionFolderPath = userFolderPath.resolve("%04d".formatted(jobFolderCount + 1) + "_" + sessionFolder);
    }

    // Create the session's folder if it doesn't exist
    Files.createDirectories(sessionFolderPath);

    // Save the job to the session's folder using ISO standard date and time format
    String jobFilename = "FRJ_" + TextManager.convertInstantToString(job.createdAt) + "__J-" + UUIDtoShortCode(job.id)
        + ".json";
    Path jobFilePath = sessionFolderPath.resolve(jobFilename);

    try (Writer writer = Files.newBufferedWriter(jobFilePath, StandardCharsets.UTF_8)) {
      GsonProvider.GSON.toJson(job, writer);
    } catch (Exception e) {
      FRLogger.error("Failed to save job '%s' to disk.".formatted(job.id), e);
    }

    // Save the input file if the filename is defined and there is data stored in it
    if (job.input != null && job.input.getFilename() != null && !job.input
        .getFilename()
        .isEmpty() && job.input.getData() != null) {
      Path inputFilePath = sessionFolderPath.resolve(job.input.getFilename());
      Files.write(inputFilePath, job.input
          .getData()
          .readAllBytes());
    }

    // Save the output file if the filename is defined and there is data stored in
    // it
    if (job.output != null && job.output.getFilename() != null && !job.output
        .getFilename()
        .isEmpty() && job.output.getData() != null) {
      Path outputFilePath = sessionFolderPath.resolve(job.output.getFilename());
      Files.write(outputFilePath, job.output
          .getData()
          .readAllBytes());
    }
  }

  /**
   * Returns the position of the job in the queue.
   *
   * @param job The job to get the position of.
   * @return The position of the job in the queue or -1 if the job is not in the
   *         queue. 0 means the job is next in line.
   */
  public int getQueuePosition(RoutingJob job) {
    synchronized (jobs) {
      return this.jobs.indexOf(job);
    }
  }

  public RoutingJob[] listJobs() {
    synchronized (jobs) {
      return this.jobs.toArray(RoutingJob[]::new);
    }
  }

  public RoutingJob[] listJobs(String sessionId) {
    synchronized (jobs) {
      return this.jobs
          .stream()
          .filter(j -> j.sessionId
              .toString()
              .equals(sessionId))
          .toArray(RoutingJob[]::new);
    }
  }

  public RoutingJob[] listJobs(String sessionId, UUID userId) {
    SessionManager sessionManager = SessionManager.getInstance();

    if (sessionId == null) {
      // Get all sessions that belong to the user
      Session[] sessions = sessionManager.getSessions(null, userId);

      // Iterate through the sessions and list all jobs belonging to them
      List<RoutingJob> result = new LinkedList<>();
      for (Session session : sessions) {
        // List all jobs belonging to the user in the session
        result.addAll(List.of(listJobs(session.id.toString())));
      }

      return result.toArray(RoutingJob[]::new);
    } else {
      Session session = sessionManager.getSession(sessionId, userId);

      if (session != null) {
        // List all jobs belonging to the user in the session
        return listJobs(session.id.toString());
      }
    }

    return new RoutingJob[0];
  }

  public RoutingJob getJob(String jobId) {
    synchronized (jobs) {
      return this.jobs
          .stream()
          .filter(j -> j.id
              .toString()
              .equals(jobId))
          .findFirst()
          .orElse(null);
    }
  }

  public void clearJobs(String sessionId) {
    synchronized (jobs) {
      this.jobs.removeIf(j -> j.sessionId
          .toString()
          .equals(sessionId));
    }
  }
}