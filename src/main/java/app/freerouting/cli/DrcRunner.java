package app.freerouting.cli;

import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.kicad.KiCadDrcReport;
import app.freerouting.io.specctra.SesImportSummary;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.BoardLoader;
import app.freerouting.management.sessions.SessionManager;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Executes a Design Rules Check (DRC) on a board without autorouting. */
public final class DrcRunner {

  private DrcRunner() {}

  /**
   * Initializes and executes DRC analysis based on GlobalSettings.
   *
   * @param globalSettings the configured global settings
   * @return true if DRC completed successfully, false otherwise
   */
  public static boolean initializeDrc(GlobalSettings globalSettings) {
    if (globalSettings.initialInputFile == null) {
      FRLogger.error("An input file must be specified with -de argument in DRC mode.", null);
      return false;
    }

    var drcSession =
        SessionManager.getInstance()
            .createSession(
                UUID.fromString(globalSettings.userProfileSettings.userId),
                "Freerouting/" + globalSettings.version);

    app.freerouting.core.RoutingJob drcJob = new app.freerouting.core.RoutingJob(drcSession.id);
    drcJob.drc = globalSettings.drcReportFile;
    try {
      FRLogger.info("Loading DSN file for DRC: " + globalSettings.initialInputFile);
      drcJob.setInput(globalSettings.initialInputFile);
    } catch (Exception e) {
      FRLogger.error("Couldn't load the input file '" + globalSettings.initialInputFile + "'", e);
      return false;
    }

    if (!BoardLoader.loadBoardIfNeeded(drcJob)) {
      FRLogger.error("Failed to load board for DRC check", null);
      return false;
    }

    if (globalSettings.initialRulesFile != null) {
      try {
        File rulesFile = new File(globalSettings.initialRulesFile);
        if (rulesFile.exists()) {
          FRLogger.info("Loading RULES file for DRC: " + globalSettings.initialRulesFile);
          try (java.io.FileInputStream rulesStream = new java.io.FileInputStream(rulesFile)) {
            String designName = drcJob.name != null ? drcJob.name : "board";
            app.freerouting.io.specctra.RulesReader.read(
                rulesStream, designName, drcJob.board, drcJob.routerSettings);
            FRLogger.info("RULES file loaded for DRC successfully");
          }
        } else {
          FRLogger.warn("RULES file for DRC not found: " + globalSettings.initialRulesFile);
        }
      } catch (Exception e) {
        FRLogger.error("Failed to load RULES file for DRC", e);
      }
    }

    if (globalSettings.designSessionFilename != null) {
      try {
        File sessionFile = new File(globalSettings.designSessionFilename);
        if (sessionFile.exists()) {
          if (globalSettings.designSessionFilename.toLowerCase().endsWith(".json")) {
            FRLogger.info(
                "Loading KiCad JSON session file for DRC: " + globalSettings.designSessionFilename);
            try (java.io.FileReader jsonReader = new java.io.FileReader(sessionFile)) {
              app.freerouting.io.kicad.KiCadJsonReader.importSession(jsonReader, drcJob.board);
              FRLogger.info("KiCad JSON session file loaded for DRC successfully");
            }
          } else {
            FRLogger.info("Loading SES file for DRC: " + globalSettings.designSessionFilename);
            try (java.io.FileInputStream sesStream = new java.io.FileInputStream(sessionFile)) {
              SesImportSummary summary = SesReader.read(sesStream, drcJob.board);
              FRLogger.info(
                  "SES file loaded for DRC: "
                      + summary.wiresImported()
                      + " wires, "
                      + summary.viasImported()
                      + " vias imported"
                      + (summary.errorsEncountered() > 0
                          ? " (" + summary.errorsEncountered() + " errors)"
                          : ""));
            }
          }
        } else {
          FRLogger.warn("Session file for DRC not found: " + globalSettings.designSessionFilename);
        }
      } catch (Exception e) {
        FRLogger.error("Failed to load session file for DRC", e);
      }
    }

    DesignRulesChecker drcChecker =
        new DesignRulesChecker(drcJob.board, globalSettings.drcSettings);

    String coordinateUnit = "mm";
    String sourceFileName = new File(globalSettings.initialInputFile).getName();
    KiCadDrcReport report = drcChecker.generateReport(sourceFileName, coordinateUnit);

    try {
      var settingsMerger = globalSettings.settingsMergerProtype.clone();
      settingsMerger.addOrReplaceSources(
          new DsnFileSettings(drcJob.input.getData(), drcJob.input.getFilename()));
      var routerSettings = settingsMerger.merge();
      var finalStats = drcJob.board.getStatistics();
      report.qualityScore = (double) finalStats.getRouterScore(routerSettings);
      report.optimizerScore = (double) finalStats.getOptimizerScore(routerSettings);
    } catch (Exception e) {
      FRLogger.warn("Failed to calculate quality score for DRC report: " + e.getMessage());
    }

    String drcReportJson = app.freerouting.util.gson.GsonProvider.GSON.toJson(report);

    if (drcJob.drc != null) {
      String outputFileName = drcJob.drc.getAbsolutePath();
      try {
        Path outputFilePath = Path.of(outputFileName);
        Files.write(outputFilePath, drcReportJson.getBytes(StandardCharsets.UTF_8));
        FRLogger.info("DRC report written to: " + outputFileName);
      } catch (IOException e) {
        FRLogger.error("Couldn't save the DRC report to '" + outputFileName + "'", e);
        return false;
      }
    } else {
      System.out.println(drcReportJson);
    }

    return true;
  }
}
