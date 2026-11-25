package app.freerouting.cli;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.analytics.model.JobLifecycleStatus;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.facade.CancellationToken;
import app.freerouting.core.facade.FreeroutingHeadlessEngine;
import app.freerouting.core.facade.HeadlessRoutingResult;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReadOptions;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.management.jobs.RoutingJobOutputWriter;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.ApiSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.KiCadProjectSettings;
import app.freerouting.settings.sources.RulesFileSettings;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
      globalSettings.cliExitCode = 1;
      return false;
    }

    UUID sessionId =
        globalSettings.userProfileSettings != null
                && globalSettings.userProfileSettings.userId != null
                && !globalSettings.userProfileSettings.userId.isBlank()
            ? UUID.fromString(globalSettings.userProfileSettings.userId)
            : UUID.randomUUID();

    RoutingJob routingJob = new RoutingJob(sessionId);
    routingJob.startedAt = Instant.now();

    try {
      routingJob.setInput(globalSettings.initialInputFile);
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
    }

    if (routingJob.input == null) {
      FRLogger.warn(
          "Couldn't read the input file '" + globalSettings.initialInputFile + "', aborting.");
      globalSettings.cliExitCode = 1;
      return false;
    }

    var desiredOutputFile = new File(globalSettings.initialOutputFile);
    if (desiredOutputFile.exists()) {
      if (!desiredOutputFile.delete()) {
        FRLogger.warn("Couldn't delete the file '" + globalSettings.initialOutputFile + "'");
      }
    }

    routingJob.tryToSetOutputFile(new File(globalSettings.initialOutputFile));

    var settingsMerger =
        globalSettings.settingsMergerProtype != null
            ? globalSettings.settingsMergerProtype.clone()
            : new SettingsMerger(new DefaultSettings());

    if (routingJob.input.format == FileFormat.DSN) {
      settingsMerger.addOrReplaceSources(
          new DsnFileSettings(routingJob.input.getData(), routingJob.input.getFilename()));
    }

    // Explicit or adjacent rules file
    if (globalSettings.initialRulesFile != null) {
      try {
        routingJob.setRules(globalSettings.initialRulesFile);
      } catch (Exception e) {
        FRLogger.warn(
            "Couldn't load rules file '"
                + globalSettings.initialRulesFile
                + "': "
                + e.getMessage());
      }
    } else if (routingJob.input.format == FileFormat.DSN
        && routingJob.input.getDirectoryPath() != null
        && !routingJob.input.getDirectoryPath().isBlank()) {
      // Auto-discover adjacent <boardname>.rules
      String baseName = routingJob.input.getFilename();
      if (baseName.lastIndexOf('.') > 0) {
        baseName = baseName.substring(0, baseName.lastIndexOf('.'));
      }
      File autoRules = new File(routingJob.input.getDirectoryPath(), baseName + ".rules");
      if (autoRules.exists()) {
        try {
          routingJob.setRules(autoRules);
          FRLogger.info("Auto-discovered adjacent rules file '" + autoRules.getPath() + "'");
        } catch (Exception e) {
          FRLogger.warn(
              "Failed to read adjacent rules file '"
                  + autoRules.getPath()
                  + "': "
                  + e.getMessage());
        }
      }
    }

    if (routingJob.rules != null && routingJob.rules.getData() != null) {
      settingsMerger.addOrReplaceSources(
          new RulesFileSettings(routingJob.rules.getData(), routingJob.rules.getFilename()));
    }

    // Routing without the rules of a .kicad_dru given on the command line would produce a board
    // that fails the KiCad DRC.
    if (!routingJob.loadKiCadDesignRules(globalSettings.initialKiCadDesignRulesFile)) {
      globalSettings.cliExitCode = 1;
      return false;
    }
    // Without the minimum track width of the .kicad_pro, the router may neck traces down below it.
    if (!addKiCadProjectSettings(
        routingJob, globalSettings.initialKiCadProjectFile, settingsMerger)) {
      globalSettings.cliExitCode = 1;
      return false;
    }
    // Without the fill settings of the zones of the .kicad_pcb, a plane that KiCad refills in
    // several pieces counts as one.
    if (!routingJob.loadKiCadBoardZones(globalSettings.initialKiCadBoardFile)) {
      globalSettings.cliExitCode = 1;
      return false;
    }
    // A board of KiCad 5 has no .kicad_pro: its minimum track width is in the .kicad_pcb.
    if (globalSettings.initialKiCadProjectFile == null && routingJob.kicadProject != null) {
      settingsMerger.addOrReplaceSources(new KiCadProjectSettings(routingJob.kicadProject));
    }

    // Programmatic test overrides / API level settings
    if (globalSettings.routerSettings != null) {
      settingsMerger.addOrReplaceSources(new ApiSettings(globalSettings.routerSettings));
    }

    var importSettings = settingsMerger.merge();
    var importOptions = DsnReadOptions.forSettings(importSettings);
    routingJob.routerSettings = importSettings;
    routingJob.drcSettings = globalSettings.drcSettings.clone();

    HeadlessBoardManager boardManager = new HeadlessBoardManager(routingJob);
    if (routingJob.input.format == FileFormat.DSN) {
      boardManager.loadFromSpecctraDsn(
          routingJob.input.getData(), null, new ItemIdGenerator(), importOptions);
    } else {
      boardManager.loadFromKiCadJson(routingJob.input.getData(), null, new ItemIdGenerator());
    }
    routingJob.board = boardManager.getRoutingBoard();
    if (routingJob.board == null) {
      FRLogger.error("Failed to construct routing board from input", null);
      globalSettings.cliExitCode = 1;
      return false;
    }

    // Apply rules onto the board
    if (routingJob.rules != null && routingJob.rules.getData() != null) {
      try {
        RulesReader.read(
            routingJob.rules.getData(),
            routingJob.name != null ? routingJob.name : "board",
            routingJob.board,
            routingJob.routerSettings);
      } catch (Exception e) {
        FRLogger.error("Failed to apply rules from rules file onto board", e);
      }
    }

    routingJob.routerSettings.applyBoardSpecificOptimizations(routingJob.board);
    routingJob.routerSettings.applyNetClassExclusions(routingJob.board);

    // Import initial session (SES or KiCad JSON) if present
    byte[] sessionData = null;
    String sessionFilename = null;
    if (routingJob.initialSession != null && routingJob.initialSession.getData() != null) {
      sessionData = routingJob.initialSession.getData().readAllBytes();
      sessionFilename = routingJob.initialSession.getFilename();
    } else if (globalSettings.designSessionFilename != null) {
      File sessionFile = new File(globalSettings.designSessionFilename);
      if (sessionFile.exists()) {
        try {
          sessionData = Files.readAllBytes(sessionFile.toPath());
          sessionFilename = sessionFile.getName();
        } catch (IOException e) {
          FRLogger.warn("Failed to read session file: " + sessionFile.getPath());
        }
      } else {
        FRLogger.warn("Session file not found: " + globalSettings.designSessionFilename);
      }
    }

    if (sessionData != null && sessionData.length > 0) {
      try {
        boolean isJson =
            (sessionFilename != null && sessionFilename.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (!isJson) {
          for (byte b : sessionData) {
            if (!Character.isWhitespace((char) b)) {
              if (b == '{') {
                isJson = true;
              }
              break;
            }
          }
        }
        if (isJson) {
          FRLogger.info("Importing KiCad JSON session data onto board");
          try (Reader r =
              new InputStreamReader(
                  new ByteArrayInputStream(sessionData), StandardCharsets.UTF_8)) {
            KiCadJsonReader.importSession(r, routingJob.board);
          }
        } else {
          FRLogger.info("Importing Specctra SES session data onto board");
          SesReader.read(new ByteArrayInputStream(sessionData), routingJob.board);
        }
      } catch (Exception e) {
        FRLogger.error("Failed to import session data onto board", e);
      }
    }

    // Graceful cancellation setup: bounded wait on SIGINT
    CountDownLatch completionLatch = new CountDownLatch(1);
    AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    CancellationToken cancellationToken = shutdownRequested::get;

    Thread shutdownHook =
        new Thread(
            () -> {
              shutdownRequested.set(true);
              try {
                // Give engine up to 5 seconds to unwind, save partial output, and flush analytics
                completionLatch.await(5000, TimeUnit.MILLISECONDS);
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
              }
            },
            "cli-shutdown-hook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    boolean isSesOutput = routingJob.output != null && routingJob.output.format == FileFormat.SES;

    HeadlessRoutingResult result;
    try {
      result =
          FreeroutingHeadlessEngine.route(routingJob, cancellationToken, null, true, isSesOutput);
    } finally {
      completionLatch.countDown();
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException _) {
        // Shutdown already started, hook removal is impossible and harmless
      }
    }

    routingJob.finishedAt = Instant.now();

    if (result.board() != null) {
      routingJob.board = result.board();
    }

    // Publish output using RoutingJobOutputWriter
    RoutingJobOutputWriter.write(routingJob);
    boolean outputWritten = writeCliOutputIfAvailable(globalSettings, routingJob);

    int cliExitCode = computeCliExitCode(routingJob, outputWritten);
    writeCliResultManifestIfRequested(globalSettings, routingJob, outputWritten, cliExitCode);

    if (outputWritten
        && (globalSettings.statistics.jobsCompleted >= 5)
        && globalSettings.userProfileSettings.userEmail.isEmpty()) {
      String nl = System.lineSeparator();
      System.out.println(
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
          java.nio.file.Path.of(globalSettings.initialInputFile).getFileName().toString();

      FRAnalytics.recordBatchJobSummary(
          routingJob.id.toString(),
          sessionId.toString(),
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

  /**
   * Reads the .kicad_pro given with the design, if any, and adds its settings to the merger.
   *
   * @return false if the file cannot be read
   */
  static boolean addKiCadProjectSettings(
      RoutingJob routingJob, String kicadProjectFile, SettingsMerger settingsMerger) {
    if (!routingJob.loadKiCadProject(kicadProjectFile)) {
      return false;
    }
    if (routingJob.kicadProject != null) {
      settingsMerger.addOrReplaceSources(new KiCadProjectSettings(routingJob.kicadProject));
    }
    return true;
  }

  /** Returns whether the CLI should stop waiting for routing updates. */
  public static boolean isCliTerminalState(RoutingJobState state) {
    return state != null && state.isTerminal();
  }

  /** Writes nonempty output for completed or timed-out jobs without overwriting with empty data. */
  public static boolean writeCliOutputIfAvailable(
      GlobalSettings globalSettings, RoutingJob routingJob) {
    var outputData = routingJob.output != null ? routingJob.output.getData() : null;
    if (outputData == null || outputData.available() == 0) {
      return false;
    }
    if (routingJob.state != RoutingJobState.COMPLETED
        && routingJob.state != RoutingJobState.TIMED_OUT
        && routingJob.state != RoutingJobState.CANCELLED) {
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
      Files.write(outputFilePath, outputData.readAllBytes());
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
              globalSettings.runtimeEnvironment.getCpuScore());
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
