package app.freerouting.management;

import app.freerouting.Freerouting;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.RulesReader;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.ApiSettings;
import app.freerouting.settings.sources.DsnFileSettings;
import app.freerouting.settings.sources.RulesFileSettings;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Encapsulates composite multi-file inputs (primary design + optional rules + optional session),
 * providing uniform assembly into a fully configured {@link RoutingBoard} and merged {@link
 * RouterSettings}.
 */
public class CompositeBoardInput {

  private byte[] designData;
  private String designFilename;
  private FileFormat designFormat = FileFormat.UNKNOWN;

  private byte[] rulesData;
  private String rulesFilename;

  private byte[] sessionData;
  private String sessionFilename;
  private FileFormat sessionFormat = FileFormat.UNKNOWN;

  /** Default constructor for CompositeBoardInput. */
  public CompositeBoardInput() {}

  /** Sets the primary design file content and filename. */
  public CompositeBoardInput setDesign(byte[] data, String filename) {
    this.designData = data;
    this.designFilename = filename;
    if (data != null) {
      this.designFormat = RoutingJob.getFileFormat(data);
      if (this.designFormat == FileFormat.UNKNOWN && filename != null) {
        this.designFormat = RoutingJob.getFileFormat(Path.of(filename));
      }
    }
    return this;
  }

  /** Sets the primary design file from a local file. */
  public CompositeBoardInput setDesign(File file) throws Exception {
    if (file != null && file.exists()) {
      setDesign(Files.readAllBytes(file.toPath()), file.getName());
    }
    return this;
  }

  /** Sets the rules file content and filename. */
  public CompositeBoardInput setRules(byte[] data, String filename) {
    this.rulesData = data;
    this.rulesFilename = filename;
    return this;
  }

  /** Sets the rules file from a local file. */
  public CompositeBoardInput setRules(File file) throws Exception {
    if (file != null && file.exists()) {
      setRules(Files.readAllBytes(file.toPath()), file.getName());
    }
    return this;
  }

  /** Sets the session file content and filename. */
  public CompositeBoardInput setSession(byte[] data, String filename) {
    this.sessionData = data;
    this.sessionFilename = filename;
    if (data != null) {
      this.sessionFormat = RoutingJob.getFileFormat(data);
      if (this.sessionFormat == FileFormat.UNKNOWN && filename != null) {
        this.sessionFormat = RoutingJob.getFileFormat(Path.of(filename));
      }
    }
    return this;
  }

  /** Sets the session file from a local file. */
  public CompositeBoardInput setSession(File file) throws Exception {
    if (file != null && file.exists()) {
      setSession(Files.readAllBytes(file.toPath()), file.getName());
    }
    return this;
  }

  public byte[] getDesignData() {
    return designData;
  }

  public String getDesignFilename() {
    return designFilename;
  }

  public FileFormat getDesignFormat() {
    return designFormat;
  }

  public byte[] getRulesData() {
    return rulesData;
  }

  public String getRulesFilename() {
    return rulesFilename;
  }

  public byte[] getSessionData() {
    return sessionData;
  }

  public String getSessionFilename() {
    return sessionFilename;
  }

  public FileFormat getSessionFormat() {
    return sessionFormat;
  }

  /**
   * Assembles the composite inputs into the provided routing job: 1. Loads design data into
   * job.board. 2. Merges settings layers (Default -> DSN -> Rules -> API overrides). 3. Applies
   * design rules onto board and router settings. 4. Imports initial session (SES or KiCad JSON) if
   * present.
   *
   * @param job Target routing job
   * @throws Exception If design loading or parsing fails
   */
  public void assembleBoard(RoutingJob job) throws Exception {
    Objects.requireNonNull(job, "job must not be null");

    if (designData == null || designData.length == 0) {
      throw new IllegalArgumentException(
          "CompositeBoardInput: designData is empty or not provided.");
    }

    // Set job input details
    job.input = new BoardFileDetails();
    job.input.setData(designData);
    job.input.setFilename(designFilename != null ? designFilename : "design.dsn");
    job.input.format = designFormat;
    if (job.input.format == FileFormat.UNKNOWN) {
      job.input.format = FileFormat.DSN;
    }
    job.name = job.input.getFilenameWithoutExtension();

    boolean isDsn = job.input.format == FileFormat.DSN;
    boolean isJson = job.input.format == FileFormat.KICAD_DESIGN_JSON;

    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    if (isDsn) {
      boardManager.loadFromSpecctraDsn(
          new ByteArrayInputStream(designData), null, new ItemIdGenerator());
    } else if (isJson) {
      boardManager.loadFromKiCadJson(
          new ByteArrayInputStream(designData), null, new ItemIdGenerator());
    } else {
      throw new IllegalArgumentException(
          "Unsupported primary design format: "
              + job.input.format
              + ". Expected DSN or KICAD_DESIGN_JSON.");
    }
    job.board = boardManager.getRoutingBoard();
    if (job.board == null) {
      throw new IllegalStateException("Failed to create routing board from design data.");
    }

    // Settings merging pipeline
    var settingsMerger = Freerouting.globalSettings.settingsMergerProtype.clone();
    if (isDsn) {
      settingsMerger.addOrReplaceSources(
          new DsnFileSettings(new ByteArrayInputStream(designData), job.input.getFilename()));
    }

    // Check for rules data or auto-locate adjacent rules if not directly provided
    byte[] effectiveRulesData = rulesData;
    String effectiveRulesFilename = rulesFilename;

    if (effectiveRulesData == null && job.rules != null && job.rules.getData() != null) {
      effectiveRulesData = job.rules.getData().readAllBytes();
      effectiveRulesFilename = job.rules.getFilename();
    } else if (effectiveRulesData == null && isDsn && job.input.getDirectoryPath() != null) {
      String baseName = job.input.getFilename();
      if (baseName.lastIndexOf('.') > 0) {
        baseName = baseName.substring(0, baseName.lastIndexOf('.'));
      }
      File autoRules = new File(job.input.getDirectoryPath(), baseName + ".rules");
      if (autoRules.exists()) {
        try {
          effectiveRulesData = Files.readAllBytes(autoRules.toPath());
          effectiveRulesFilename = autoRules.getName();
        } catch (Exception e) {
          FRLogger.warn(
              "Failed to read adjacent rules file: " + autoRules.getPath() + ": " + e.getMessage());
        }
      }
    }

    if (effectiveRulesData != null) {
      job.setRules(effectiveRulesData);
      if (effectiveRulesFilename != null) {
        job.rules.setFilename(effectiveRulesFilename);
      }
      settingsMerger.addOrReplaceSources(
          new RulesFileSettings(
              new ByteArrayInputStream(effectiveRulesData), effectiveRulesFilename));
    }

    // API settings (from caller / job settings overrides)
    if (job.routerSettings != null) {
      settingsMerger.addOrReplaceSources(new ApiSettings(job.routerSettings));
    }

    job.routerSettings = settingsMerger.merge();

    // Apply rules onto the board and routerSettings
    if (effectiveRulesData != null && job.board != null) {
      try {
        String designNameStr = job.name != null ? job.name : "board";
        RulesReader.read(
            new ByteArrayInputStream(effectiveRulesData),
            designNameStr,
            job.board,
            job.routerSettings);
      } catch (Exception e) {
        FRLogger.error("Failed to apply rules from rules file onto board", e);
      }
    }

    job.routerSettings.applyBoardSpecificOptimizations(job.board);

    // Import initial session (SES or KiCad JSON) if present
    byte[] effectiveSessionData = sessionData;
    String effectiveSessionFilename = sessionFilename;
    FileFormat effectiveSessionFormat = sessionFormat;

    if (effectiveSessionData == null
        && job.initialSession != null
        && job.initialSession.getData() != null) {
      effectiveSessionData = job.initialSession.getData().readAllBytes();
      effectiveSessionFilename = job.initialSession.getFilename();
      effectiveSessionFormat = job.initialSession.format;
    }

    if (effectiveSessionData != null && effectiveSessionData.length > 0 && job.board != null) {
      try {
        if (effectiveSessionFormat == FileFormat.KICAD_SESSION_JSON
            || (effectiveSessionFilename != null
                && effectiveSessionFilename.toLowerCase().endsWith(".json"))) {
          FRLogger.info("Importing KiCad JSON session data onto board");
          try (Reader r =
              new InputStreamReader(
                  new ByteArrayInputStream(effectiveSessionData), StandardCharsets.UTF_8)) {
            KiCadJsonReader.importSession(r, job.board);
          }
        } else {
          FRLogger.info("Importing Specctra SES session data onto board");
          SesReader.read(new ByteArrayInputStream(effectiveSessionData), job.board);
        }
      } catch (Exception e) {
        FRLogger.error("Failed to import session data onto board", e);
      }
    }
  }
}
