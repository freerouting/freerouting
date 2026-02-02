package app.freerouting.core;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.events.RoutingJobLogEntryAddedEvent;
import app.freerouting.core.events.RoutingJobLogEntryAddedEventListener;
import app.freerouting.core.events.RoutingJobUpdatedEvent;
import app.freerouting.core.events.RoutingJobUpdatedEventListener;
import app.freerouting.designforms.specctra.RulesFile;
import app.freerouting.gui.FileFormat;
import app.freerouting.gui.WindowMessage;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntry;
import app.freerouting.settings.DesignRulesCheckerSettings;
import app.freerouting.settings.RouterSettings;
import com.google.gson.annotations.SerializedName;
import java.awt.Component;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Represents a job that needs to be processed by the router.
 */
public class RoutingJob implements Serializable, Comparable<RoutingJob> {

  public static final String DSN_FILE_EXTENSION = "dsn";
  public static final String BINARY_FILE_EXTENSION = "frb";
  private static final String RULES_FILE_EXTENSION = "rules";
  private static final String SES_FILE_EXTENSION = "ses";
  private static final String EAGLE_SCRIPT_FILE_EXTENSION = "scr";

  @SerializedName("id")
  public final UUID id = UUID.randomUUID();
  @SerializedName("created_at")
  public final Instant createdAt = Instant.now();
  // events to signal input and output updates
  protected final transient List<RoutingJobUpdatedEventListener> settingsUpdatedEventListeners = new ArrayList<>();
  protected final transient List<RoutingJobUpdatedEventListener> inputUpdatedEventListeners = new ArrayList<>();
  protected final transient List<RoutingJobUpdatedEventListener> outputUpdatedEventListeners = new ArrayList<>();
  protected final transient List<RoutingJobLogEntryAddedEventListener> logEntryAddedEventListeners = new ArrayList<>();
  @SerializedName("short_name")
  public String shortName = "N/A";
  @SerializedName("name")
  public String name;
  @SerializedName("started_at")
  public Instant startedAt;
  @SerializedName("finished_at")
  public Instant finishedAt;
  @SerializedName("state")
  public RoutingJobState state = RoutingJobState.INVALID;
  @SerializedName("stage")
  public RoutingStage stage = RoutingStage.IDLE;
  @SerializedName("priority")
  public RoutingJobPriority priority = RoutingJobPriority.NORMAL;
  @SerializedName("session_id")
  public UUID sessionId;
  @SerializedName("input")
  public BoardFileDetails input;
  @SerializedName("output")
  public BoardFileDetails output;

  @SerializedName("drc")
  public BoardFileDetails drc;
  @SerializedName("router_settings")
  public RouterSettings routerSettings = new RouterSettings();
  @SerializedName("drc_settings")
  public DesignRulesCheckerSettings drcSettings = new DesignRulesCheckerSettings();
  @SerializedName("resource_usage")
  public RouterJobResourceUsage resourceUsage = new RouterJobResourceUsage();
  public transient StoppableThread thread;
  public transient RoutingBoard board;
  public transient Instant timeoutAt;
  private boolean isCancelledByUser = false;

  public boolean isCancelledByUser() {
    return isCancelledByUser;
  }

  public void setCancelledByUser(boolean cancelledByUser) {
    isCancelledByUser = cancelledByUser;
  }

  @SerializedName("current_pass")
  private int currentPass = 0;

  /**
   * We need a parameterless constructor for the serialization.
   */
  public RoutingJob() {
    this.name = "J-" + this.id
        .toString()
        .substring(0, 6)
        .toUpperCase();
    this.shortName = this.id
        .toString()
        .substring(0, 6)
        .toUpperCase();
  }

  /**
   * Creates a new instance of DesignFile and prepares the intermediate file
   * handling.
   */
  public RoutingJob(UUID sessionId) {
    this();
    this.sessionId = sessionId;
    this.shortName = this.sessionId
        .toString()
        .substring(0, 6)
        .toUpperCase() + "\\"
        + this.id
            .toString()
            .substring(0, 6)
            .toUpperCase();

  }

  /**
   * Shows a file chooser for opening a design file.
   */
  public static File showOpenDialog(String p_default_directory, Component p_parent) {
    JFileChooser fileChooser = new JFileChooser(p_default_directory);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter = new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter = new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Set a file filter as the default one
    fileChooser.setFileFilter(dsnFilter);

    fileChooser.showOpenDialog(p_parent);
    return fileChooser.getSelectedFile();
  }

  public static boolean read_rules_file(String p_design_name, String p_parent_name, String rules_file_name,
      GuiBoardManager p_board_handling, String p_confirm_message) {

    boolean dsn_file_generated_by_host = p_board_handling
        .get_routing_board().communication.specctra_parser_info.dsn_file_generated_by_host;

    try {
      File rules_file = new File(p_parent_name, rules_file_name);
      FRLogger.info("Opening '" + rules_file_name + "'...");
      InputStream input_stream = new FileInputStream(rules_file);
      if (dsn_file_generated_by_host && WindowMessage.confirm(p_confirm_message)) {
        return RulesFile.read(input_stream, p_design_name, p_board_handling);
      }
    } catch (IOException _) {
      FRLogger.error("File '" + rules_file_name + "' was not found.", null);
    }
    return false;
  }

  public static FileFormat getFileFormat(byte[] content) {
    // Open the file as a binary file and read the first 6 bytes to determine the
    // file format
    try (InputStream fileInputStream = new ByteArrayInputStream(content)) {
      byte[] buffer = new byte[6];
      int bytesRead = fileInputStream.read(buffer, 0, 6);
      if (bytesRead == 6) {
        // Check if the file is a binary file
        if (buffer[0] == (byte) 0xAC && buffer[1] == (byte) 0xED && buffer[2] == (byte) 0x00
            && buffer[3] == (byte) 0x05) {
          return FileFormat.FRB;
        }

        // If the first few bytes are 0x0A or 0x13, ignore them
        while (buffer[0] == (byte) 0x0A || buffer[0] == (byte) 0x0D) {
          buffer[0] = buffer[1];
          buffer[1] = buffer[2];
          buffer[2] = buffer[3];
          buffer[3] = buffer[4];
          buffer[4] = buffer[5];
        }

        // Check if the file is a DSN file (it starts with "(pcb" or "(PCB")
        if ((buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x70 && buffer[2] == (byte) 0x63
            && buffer[3] == (byte) 0x62)
            || (buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x50
                && buffer[2] == (byte) 0x43 && buffer[3] == (byte) 0x42)) {
          return FileFormat.DSN;
        }

        // Check if the file is a SES file (it starts with "(ses" or "(SES")
        if ((buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x73 && buffer[2] == (byte) 0x65
            && buffer[3] == (byte) 0x73)
            || (buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x53
                && buffer[2] == (byte) 0x45 && buffer[3] == (byte) 0x53)) {
          return FileFormat.SES;
        }
      }
    } catch (IOException _) {
      // Ignore the exception, it can happen with the build-in template or if the user
      // doesn't choose any file in the file dialog
    }

    return FileFormat.UNKNOWN;
  }

  public static FileFormat getFileFormat(Path path) {
    String filename = path
        .toString()
        .toLowerCase();
    String[] parts = filename.split("\\.");
    if (parts.length > 1) {
      String extension = parts[parts.length - 1].toLowerCase();
      return switch (extension) {
        case DSN_FILE_EXTENSION -> FileFormat.DSN;
        case BINARY_FILE_EXTENSION -> FileFormat.FRB;
        case "ses" -> FileFormat.SES;
        case "scr" -> FileFormat.SCR;
        default -> FileFormat.UNKNOWN;
      };
    }

    return FileFormat.UNKNOWN;
  }

  public int getCurrentPass() {
    return currentPass;
  }

  public void setCurrentPass(int currentPass) {
    this.currentPass = currentPass;
  }

  public Duration getDuration() {
    if (startedAt == null) {
      return null;
    }
    if (finishedAt != null) {
      return Duration.between(startedAt, finishedAt);
    }
    return Duration.between(startedAt, Instant.now());
  }

  public boolean setInput(byte[] inputFileContent) {
    this.input = new BoardFileDetails();
    this.input.addUpdatedEventListener(_ -> this.fireInputUpdatedEvent());
    return this.tryToSetInput(inputFileContent);
  }

  public File getRulesFile() {
    return new File(changeFileExtension(this.output.getAbsolutePath(), RULES_FILE_EXTENSION));
  }

  public File getEagleScriptFile() {
    return new File(changeFileExtension(this.output.getAbsolutePath(), EAGLE_SCRIPT_FILE_EXTENSION));
  }

  public void setDummyInputFile(String filename) {
    this.input = new BoardFileDetails();

    this.output = new BoardFileDetails();

    if ((filename != null) && (filename
        .toLowerCase()
        .endsWith(DSN_FILE_EXTENSION))) {
      this.input.format = FileFormat.DSN;
      this.input.setFilename(filename);

    }
  }

  private boolean tryToSetInput(byte[] fileContent) {
    if (fileContent == null) {
      return false;
    }

    this.input.format = getFileFormat(fileContent);

    if (this.input.format != FileFormat.UNKNOWN) {
      this.input.setData(fileContent);
      fireInputUpdatedEvent();
      return true;
    }

    return false;
  }

  // Changes the file extension of the selected file
  private String changeFileExtension(String filename, String newFileExtension) {
    Path filePath = Path.of(filename);

    // Get the filename and split it into parts
    String originalFullPathWithoutFilename = filePath
        .getParent()
        .toAbsolutePath()
        .toString();
    String originalFilename = filePath
        .getFileName()
        .toString();
    String[] nameParts = originalFilename.split("\\.");
    if (nameParts.length > 1) {
      String extension = nameParts[nameParts.length - 1].toLowerCase();
      if (extension.equals(newFileExtension)) {
        return filePath.toString();
      }
      String newFilename = originalFilename.substring(0, originalFilename.length() - extension.length() - 1) + "."
          + newFileExtension;

      return Path
          .of(originalFullPathWithoutFilename, newFilename)
          .toString();
    }

    return Path
        .of(originalFullPathWithoutFilename, originalFilename + "." + newFileExtension)
        .toString();
  }

  public boolean tryToSetOutputFile(File outputFile) {
    if (outputFile == null) {
      return false;
    }

    FileFormat ff = getFileFormat(outputFile.toPath());

    if ((ff == FileFormat.DSN) || (ff == FileFormat.FRB) || (ff == FileFormat.SES) || (ff == FileFormat.SCR)) {
      this.output = new BoardFileDetails(outputFile);
      this.output.addUpdatedEventListener(_ -> this.fireInputUpdatedEvent());
      this.output.format = ff;
      fireOutputUpdatedEvent();
      return true;
    } else {
      return false;
    }
  }

  public String getInputFileDetails() {
    return new BoardFileDetails(this.input.getFile()).toString();
  }

  public String getOutputFileDetails() {
    return new BoardFileDetails(this.output.getFile()).toString();
  }

  @Override
  public int compareTo(RoutingJob o) {
    if (this.priority.ordinal() < o.priority.ordinal()) {
      return -1;
    } else if (this.priority.ordinal() > o.priority.ordinal()) {
      return 1;
    } else {
      return 0;
    }
  }

  public BoardFileDetails getInput() {
    return input;
  }

  public void setInput(String inputFilePath) throws IOException {
    setInput(new File(inputFilePath));
  }

  public void setInput(File inputFile) throws IOException {
    // Read the file contents into a byte array and initialize the RoutingJob object
    // with it
    FileInputStream fileInputStream = new FileInputStream(inputFile);
    byte[] content = fileInputStream.readAllBytes();

    setInput(content);
    input.setFilename(inputFile.getAbsolutePath());
    if (input.format == FileFormat.UNKNOWN) {
      // As a fallback method, set the file format based on its extension
      input.format = getFileFormat(Path.of(input.getAbsolutePath()));
    }

    if (this.input.format == FileFormat.FRB) {
      this.output = new BoardFileDetails();
      this.output.addUpdatedEventListener(_ -> this.fireOutputUpdatedEvent());
      this.output.setFilename(changeFileExtension(input.getAbsolutePath(), BINARY_FILE_EXTENSION));
    }

    if (this.input.format == FileFormat.DSN) {
      this.output = new BoardFileDetails();
      this.output.addUpdatedEventListener(_ -> this.fireOutputUpdatedEvent());
      this.output.setFilename(changeFileExtension(input.getAbsolutePath(), SES_FILE_EXTENSION));
    }

    if (this.input.format != FileFormat.UNKNOWN) {
      this.input = new BoardFileDetails(inputFile);
      this.input.addUpdatedEventListener(_ -> this.fireInputUpdatedEvent());
      this.name = input.getFilenameWithoutExtension();

    }

    fireInputUpdatedEvent();
  }

  public boolean setSettings(RouterSettings settings) {
    // Update the router settings that are defined in the settings parameter. All
    // other settings should remain the same.
    boolean wereSettingsChanged = this.routerSettings.applyNewValuesFrom(settings) > 0;
    fireSettingsUpdatedEvent();

    return wereSettingsChanged;
  }

  public void addSettingsUpdatedEventListener(RoutingJobUpdatedEventListener listener) {
    settingsUpdatedEventListeners.add(listener);
  }

  public void fireSettingsUpdatedEvent() {
    RoutingJobUpdatedEvent event = new RoutingJobUpdatedEvent(this, this);
    for (RoutingJobUpdatedEventListener listener : settingsUpdatedEventListeners) {
      listener.onRoutingJobUpdated(event);
    }
  }

  public void addInputUpdatedEventListener(RoutingJobUpdatedEventListener listener) {
    inputUpdatedEventListeners.add(listener);
  }

  public void fireInputUpdatedEvent() {
    RoutingJobUpdatedEvent event = new RoutingJobUpdatedEvent(this, this);
    for (RoutingJobUpdatedEventListener listener : inputUpdatedEventListeners) {
      listener.onRoutingJobUpdated(event);
    }
  }

  public void addOutputUpdatedEventListener(RoutingJobUpdatedEventListener listener) {
    outputUpdatedEventListeners.add(listener);
  }

  public void fireOutputUpdatedEvent() {
    RoutingJobUpdatedEvent event = new RoutingJobUpdatedEvent(this, this);
    for (RoutingJobUpdatedEventListener listener : outputUpdatedEventListeners) {
      listener.onRoutingJobUpdated(event);
    }
  }

  public void addLogEntryAddedEventListener(RoutingJobLogEntryAddedEventListener listener) {
    logEntryAddedEventListeners.add(listener);
  }

  public void fireLogEntryAddedEvent(LogEntry logEntry) {
    RoutingJobLogEntryAddedEvent event = new RoutingJobLogEntryAddedEvent(this, this, logEntry);
    for (RoutingJobLogEntryAddedEventListener listener : logEntryAddedEventListeners) {
      listener.onLogEntryAdded(event);
    }
  }

  public void logInfo(String message) {
    LogEntry logEntry = FRLogger.info("[" + this.shortName + "] " + message, this.id);
    fireLogEntryAddedEvent(logEntry);
  }

  public void logWarning(String message) {
    LogEntry logEntry = FRLogger.warn("[" + this.shortName + "] " + message, this.id);
    fireLogEntryAddedEvent(logEntry);
  }

  public void logError(String message, Throwable ex) {
    LogEntry logEntry = FRLogger.error("[" + this.shortName + "] " + message, this.id, ex);
    fireLogEntryAddedEvent(logEntry);
  }

  public void logDebug(String message) {
    LogEntry logEntry = FRLogger.debug("[" + this.shortName + "] " + message, this.id);
    fireLogEntryAddedEvent(logEntry);
  }
}