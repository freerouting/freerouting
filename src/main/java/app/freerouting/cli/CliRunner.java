package app.freerouting.cli;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.analytics.model.JobLifecycleStatus;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.io.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Executes a batch CLI routing job from command line arguments and settings. */
public final class CliRunner {

  private CliRunner() {}

  /**
   * Runs the batch routing process based on the configured input and output files.
   *
   * @param globalSettings the configured global settings
   * @return true if the job completed cleanly with exit code 0, false otherwise
   */
  public static boolean initializeCli(GlobalSettings globalSettings) {
    if ((globalSettings.initialInputFile == null) || (globalSettings.initialOutputFile == null)) {
      FRLogger.error(
          "Both an input file and an output file must be specified with command line arguments "
              + "if you are running in CLI mode.",
          null);
      return false;
    }

    var cliSession =
        SessionManager.getInstance()
            .createSession(
                UUID.fromString(globalSettings.userProfileSettings.userId),
                "Freerouting/" + globalSettings.version);

    RoutingJob routingJob = new RoutingJob(cliSession.id);

    try {
      routingJob.setInput(globalSettings.initialInputFile);
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
    }

    if (routingJob.input == null) {
      FRLogger.warn(
          "Couldn't read the input file '" + globalSettings.initialInputFile + "', aborting.");
      return false;
    }

    cliSession.addJob(routingJob);

    var desiredOutputFile = new File(globalSettings.initialOutputFile);
    if (desiredOutputFile.exists()) {
      if (!desiredOutputFile.delete()) {
        FRLogger.warn("Couldn't delete the file '" + globalSettings.initialOutputFile + "'");
      }
    }

    routingJob.tryToSetOutputFile(new File(globalSettings.initialOutputFile));

    var settingsMerger = globalSettings.settingsMergerProtype.clone();
    settingsMerger.addOrReplaceSources(
        new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename()));

    if (globalSettings.initialRulesFile != null) {
      try {
        routingJob.setRules(globalSettings.initialRulesFile);
        if (routingJob.rules != null && routingJob.rules.getData() != null) {
          settingsMerger.addOrReplaceSources(
              new app.freerouting.settings.sources.RulesFileSettings(
                  routingJob.rules.getData(), routingJob.rules.getFilename()));
        }
      } catch (Exception e) {
        FRLogger.warn(
            "Couldn't load rules file '"
                + globalSettings.initialRulesFile
                + "': "
                + e.getMessage());
      }
    }

    routingJob.routerSettings = settingsMerger.merge();
    routingJob.drcSettings = globalSettings.drcSettings.clone();
    routingJob.state = RoutingJobState.READY_TO_START;

    while (!isCliTerminalState(routingJob.state)) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException _) {
        routingJob.state = RoutingJobState.CANCELLED;
        break;
      }
    }

    boolean outputWritten = writeCliOutputIfAvailable(globalSettings, routingJob);
    int cliExitCode = computeCliExitCode(routingJob, outputWritten);
    writeCliResultManifestIfRequested(globalSettings, routingJob, outputWritten, cliExitCode);

    if (outputWritten
        && (globalSettings.statistics.jobsCompleted >= 5)
        && globalSettings.userProfileSettings.userEmail.isEmpty()) {
      String nl = System.lineSeparator();
      IO.println(
          nl
              + "╔══════════════════════════════════════════════════════════════════╗"
              + nl
              + "║           Thank you for using Freerouting!                       ║"
              + nl
              + "║                                                                  ║"
              + nl
              + "║  If you would like to support the project, please visit          ║"
              + nl
              + "║  https://www.freerouting.app/donate.html                         ║"
              + nl
              + "║  Every contribution helps keep this project open and active!     ║"
              + nl
              + "╚══════════════════════════════════════════════════════════════════╝");
    }

    try {
      JobLifecycleStatus status =
          switch (routingJob.state) {
            case COMPLETED ->
                (cliExitCode == 0 ? JobLifecycleStatus.SUCCEEDED : JobLifecycleStatus.FAILED);
            case TIMED_OUT -> JobLifecycleStatus.TIMED_OUT;
            case CANCELLED -> JobLifecycleStatus.CANCELLED;
            default -> JobLifecycleStatus.FAILED;
          };
      String failureReason =
          cliExitCode != 0 ? "CLI exit code " + cliExitCode + " (" + routingJob.state + ")" : null;
      var stats = routingJob.board != null ? routingJob.board.getStatistics() : null;
      Integer netsTotal = stats != null && stats.nets != null ? stats.nets.totalCount : null;
      Integer netsIncomplete =
          stats != null && stats.connections != null ? stats.connections.incompleteCount : null;
      Integer clearanceViolations =
          stats != null && stats.clearanceViolations != null
              ? stats.clearanceViolations.totalCount
              : null;
      Float normalizedScore =
          stats != null && routingJob.routerSettings != null
              ? stats.getRouterScore(routingJob.routerSettings)
              : null;
      int totalPasses =
          routingJob.routerSettings != null
                  && routingJob.routerSettings.autorouter.maxPasses != null
              ? routingJob.routerSettings.autorouter.maxPasses
              : 0;
      double runtimeSeconds =
          routingJob.startedAt != null
              ? java.time.Duration.between(routingJob.startedAt, java.time.Instant.now()).toMillis()
                  / 1000.0
              : 0.0;
      String inputBasename =
          globalSettings.initialInputFile != null
              ? java.nio.file.Path.of(globalSettings.initialInputFile).getFileName().toString()
              : "unknown.dsn";

      FRAnalytics.recordBatchJobSummary(
          routingJob.id.toString(),
          cliSession.id.toString(),
          inputBasename,
          cliExitCode,
          status,
          failureReason,
          netsTotal,
          netsIncomplete,
          clearanceViolations,
          normalizedScore,
          totalPasses,
          runtimeSeconds,
          routingJob.resourceUsage.cpuTimeUsed,
          routingJob.resourceUsage.peakMemoryUsed,
          routingJob.getDetectedHost(),
          null);
      FRAnalytics.flush(1500);
    } catch (Throwable ex) {
      FRLogger.warn("Failed to record batch job summary: " + ex.getMessage());
    }

    globalSettings.cliExitCode = cliExitCode;
    return cliExitCode == 0;
  }

  public static boolean isCliTerminalState(RoutingJobState state) {
    return state == RoutingJobState.COMPLETED
        || state == RoutingJobState.TERMINATED
        || state == RoutingJobState.TIMED_OUT
        || state == RoutingJobState.CANCELLED;
  }

  public static boolean writeCliOutputIfAvailable(
      GlobalSettings globalSettings, RoutingJob routingJob) {
    if (routingJob.output == null || routingJob.output.getData() == null) {
      return false;
    }
    if (routingJob.state != RoutingJobState.COMPLETED
        && routingJob.state != RoutingJobState.TIMED_OUT) {
      return false;
    }

    boolean anyWritten = false;
    try {
      Path outputFilePath = Path.of(globalSettings.initialOutputFile);
      FRLogger.info(
          "Saving output file '"
              + outputFilePath.toAbsolutePath()
              + "' ("
              + routingJob.output.format
              + ")...");
      Files.write(outputFilePath, routingJob.output.getData().readAllBytes());
      if (Files.exists(outputFilePath) && Files.size(outputFilePath) > 0) {
        FRLogger.info(
            "Successfully saved output file '"
                + outputFilePath.toAbsolutePath()
                + "' ("
                + Files.size(outputFilePath)
                + " bytes).");
        anyWritten = true;
      }
    } catch (IOException e) {
      FRLogger.error("Couldn't save the output file '" + globalSettings.initialOutputFile + "'", e);
    }

    if (globalSettings.additionalOutputFiles != null
        && !globalSettings.additionalOutputFiles.isEmpty()
        && routingJob.board != null) {
      for (String addPathStr : globalSettings.additionalOutputFiles) {
        try {
          Path addPath = Path.of(addPathStr);
          FileFormat fmt = RoutingJob.getFileFormat(addPath);
          if (fmt == FileFormat.UNKNOWN) {
            String lower = addPathStr.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".drc.json") || lower.endsWith(".drc")) {
              fmt = FileFormat.DRC_JSON;
            }
          }
          if (fmt != FileFormat.UNKNOWN) {
            var outResult =
                app.freerouting.io.MultiOutputGenerator.generateOutputs(
                    routingJob.board,
                    routingJob.name,
                    java.util.Set.of(fmt),
                    routingJob.drcSettings,
                    false);
            byte[] bytes = outResult.getFile(fmt);
            if (bytes != null) {
              if (addPath.getParent() != null) {
                Files.createDirectories(addPath.getParent());
              }
              Files.write(addPath, bytes);
              FRLogger.info(
                  "Successfully saved additional output file '"
                      + addPath.toAbsolutePath()
                      + "' ("
                      + Files.size(addPath)
                      + " bytes).");
              anyWritten = true;
            }
          }
        } catch (Exception ex) {
          FRLogger.error("Failed to write additional output file '" + addPathStr + "'", ex);
        }
      }
    }

    return anyWritten;
  }

  public static int computeCliExitCode(RoutingJob routingJob, boolean outputWritten) {
    if (routingJob.state == RoutingJobState.COMPLETED && outputWritten) {
      return 0;
    }
    if (routingJob.state == RoutingJobState.TIMED_OUT && outputWritten) {
      return 0;
    }
    return 1;
  }

  public static void writeCliResultManifestIfRequested(
      GlobalSettings globalSettings, RoutingJob routingJob, boolean outputWritten, int exitCode) {
    if (routingJob.routerSettings == null
        || routingJob.routerSettings.resultJsonPath == null
        || routingJob.routerSettings.resultJsonPath.isBlank()) {
      return;
    }
    try {
      RoutingResultManifest manifest =
          RoutingResultManifest.fromJob(
              routingJob,
              globalSettings.initialInputFile,
              outputWritten,
              exitCode,
              globalSettings.runtimeEnvironment.cpuScore);
      RoutingResultManifest.write(Path.of(routingJob.routerSettings.resultJsonPath), manifest);
    } catch (IOException e) {
      FRLogger.error(
          "Couldn't write routing result manifest to '"
              + routingJob.routerSettings.resultJsonPath
              + "'",
          e);
    }
  }
}
