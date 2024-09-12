package app.freerouting.core;

import app.freerouting.board.BoardFileDetails;
import app.freerouting.board.RoutingBoard;
import app.freerouting.designforms.specctra.RulesFile;
import app.freerouting.gui.FileFormat;
import app.freerouting.gui.WindowMessage;
import app.freerouting.interactive.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a job that needs to be processed by the router.
 */
public class RoutingJob implements Serializable, Comparable<RoutingJob>
{
  public static final String DSN_FILE_EXTENSION = "dsn";
  public static final String BINARY_FILE_EXTENSION = "frb";
  private static final String RULES_FILE_EXTENSION = "rules";
  private static final String SES_FILE_EXTENSION = "ses";
  private static final String EAGLE_SCRIPT_FILE_EXTENSION = "scr";

  public final UUID id = UUID.randomUUID();
  public final Instant createdAt = Instant.now();
  public Instant startedAt = null;
  public Instant finishedAt = null;
  public BoardFileDetails input = null;
  public BoardFileDetails snapshot = null;
  public BoardFileDetails output = null;
  // TODO: pass the router settings as an input and forward it to the router
  public RouterSettings routerSettings = new RouterSettings();
  public UUID sessionId;
  public String name;
  public RoutingJobState state = RoutingJobState.INVALID;
  public RoutingJobPriority priority = RoutingJobPriority.NORMAL;
  public RoutingStage stage = RoutingStage.IDLE;
  public transient StoppableThread thread = null;
  public transient RoutingBoard board = null;

  /**
   * We need a parameterless constructor for the serialization.
   */
  public RoutingJob()
  {
    this.name = "J-" + this.id.toString().substring(0, 6).toUpperCase();
  }

  /**
   * Creates a new instance of DesignFile and prepares the intermediate file handling.
   */
  public RoutingJob(UUID sessionId)
  {
    this();
    this.sessionId = sessionId;
  }

  /**
   * Shows a file chooser for opening a design file.
   */
  public static File showOpenDialog(String p_default_directory, Component p_parent)
  {
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

  public static boolean read_rules_file(String p_design_name, String p_parent_name, String rules_file_name, GuiBoardManager p_board_handling, String p_confirm_message)
  {

    boolean dsn_file_generated_by_host = p_board_handling.get_routing_board().communication.specctra_parser_info.dsn_file_generated_by_host;

    try
    {
      File rules_file = new File(p_parent_name, rules_file_name);
      FRLogger.info("Opening '" + rules_file_name + "'...");
      InputStream input_stream = new FileInputStream(rules_file);
      if (dsn_file_generated_by_host && WindowMessage.confirm(p_confirm_message))
      {
        return RulesFile.read(input_stream, p_design_name, p_board_handling);
      }
    } catch (IOException e)
    {
      FRLogger.error("File '" + rules_file_name + "' was not found.", null);
    }
    return false;
  }

  public static FileFormat getFileFormat(byte[] content)
  {
    // Open the file as a binary file and read the first 6 bytes to determine the file format
    try (InputStream fileInputStream = new ByteArrayInputStream(content))
    {
      byte[] buffer = new byte[6];
      int bytesRead = fileInputStream.read(buffer, 0, 6);
      if (bytesRead == 6)
      {
        // Check if the file is a binary file
        if (buffer[0] == (byte) 0xAC && buffer[1] == (byte) 0xED && buffer[2] == (byte) 0x00 && buffer[3] == (byte) 0x05)
        {
          return FileFormat.FRB;
        }

        // If the first few bytes are 0x0A or 0x13, ignore them
        while (buffer[0] == (byte) 0x0A || buffer[0] == (byte) 0x0D)
        {
          buffer[0] = buffer[1];
          buffer[1] = buffer[2];
          buffer[2] = buffer[3];
          buffer[3] = buffer[4];
          buffer[4] = buffer[5];
        }

        // Check if the file is a DSN file (it starts with "(pcb" or "(PCB")
        if ((buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x70 && buffer[2] == (byte) 0x63 && buffer[3] == (byte) 0x62) || (buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x50 && buffer[2] == (byte) 0x43 && buffer[3] == (byte) 0x42))
        {
          return FileFormat.DSN;
        }

        // Check if the file is a SES file (it starts with "(ses" or "(SES")
        if ((buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x73 && buffer[2] == (byte) 0x65 && buffer[3] == (byte) 0x73) || (buffer[0] == (byte) 0x28 && buffer[1] == (byte) 0x53 && buffer[2] == (byte) 0x45 && buffer[3] == (byte) 0x53))
        {
          return FileFormat.SES;
        }
      }
    } catch (IOException e)
    {
      // Ignore the exception, it can happen with the build-in template or if the user doesn't choose any file in the file dialog
    }

    return FileFormat.UNKNOWN;
  }

  public static FileFormat getFileFormat(Path path)
  {
    String filename = path.toString().toLowerCase();
    String[] parts = filename.split("\\.");
    if (parts.length > 1)
    {
      String extension = parts[parts.length - 1].toLowerCase();
      switch (extension)
      {
        case DSN_FILE_EXTENSION:
          return FileFormat.DSN;
        case BINARY_FILE_EXTENSION:
          return FileFormat.FRB;
        case "ses":
          return FileFormat.SES;
        case "scr":
          return FileFormat.SCR;
        default:
          return FileFormat.UNKNOWN;
      }
    }

    return FileFormat.UNKNOWN;
  }

  public boolean setInput(byte[] inputFileContent)
  {
    this.input = new BoardFileDetails();
    return this.tryToSetInput(inputFileContent);
  }

  private String getSnapshotFilename(File inputFile)
  {
    // Calculate the CRC32 checksum of the input file
    long crc32Checksum;
    try (FileInputStream inputStream = new FileInputStream(inputFile.getAbsoluteFile()))
    {
      crc32Checksum = BoardFileDetails.calculateCrc32(inputStream).getValue();
    } catch (IOException e)
    {
      crc32Checksum = 0;
    }

    if (crc32Checksum == 0)
    {
      // We don't have a valid checksum, we can't generate the intermediate snapshot file
      return null;
    }

    // Get the temporary folder path
    Path snapshotsFolderPath = GlobalSettings.userdataPath.resolve("snapshots");

    try
    {
      // Make sure that we have the directory structure in place, and create it if it doesn't exist
      Files.createDirectories(snapshotsFolderPath);
    } catch (IOException e)
    {
      FRLogger.error("Failed to create the snapshots directory.", e);
    }

    // Set the intermediate snapshot file name based on the checksum
    String intermediate_snapshot_file_name = "snapshot-" + Long.toHexString(crc32Checksum) + "." + RoutingJob.BINARY_FILE_EXTENSION;
    return snapshotsFolderPath + File.separator + intermediate_snapshot_file_name;
  }

  public File showSaveAsDialog(String p_default_directory, Component p_parent)
  {
    String directoryName;
    var outputFile = this.output.getFile();
    if (outputFile == null)
    {
      directoryName = p_default_directory;
    }
    else
    {
      directoryName = outputFile.getParent();
    }

    JFileChooser fileChooser = new JFileChooser(directoryName);
    fileChooser.setMinimumSize(new Dimension(500, 250));

    // Add the file filter for SPECCTRA Session .SES files
    FileNameExtensionFilter sesFilter = new FileNameExtensionFilter("SPECCTRA Session file (*.ses)", "ses");
    fileChooser.addChoosableFileFilter(sesFilter);

    // Add the file filter for Freerouting binary .FRB files
    FileNameExtensionFilter frbFilter = new FileNameExtensionFilter("Freerouting binary file (*.frb)", "frb");
    fileChooser.addChoosableFileFilter(frbFilter);

    // Add the file filter for Eagle script .SCR files
    FileNameExtensionFilter scrFilter = new FileNameExtensionFilter("Eagle Session Script file (*.scr)", "scr");
    fileChooser.addChoosableFileFilter(scrFilter);

    // Add the file filter for SPECCTRA Design .DSN files
    FileNameExtensionFilter dsnFilter = new FileNameExtensionFilter("SPECCTRA Design file (*.dsn)", "dsn");
    fileChooser.addChoosableFileFilter(dsnFilter);

    // Set the file filter based on the output file format
    switch (this.output.getFormat())
    {
      case SES:
        fileChooser.setFileFilter(sesFilter);
        break;
      case FRB:
        fileChooser.setFileFilter(frbFilter);
        break;
      case SCR:
        fileChooser.setFileFilter(scrFilter);
        break;
      case DSN:
        fileChooser.setFileFilter(dsnFilter);
        break;
      default:
        fileChooser.setFileFilter(sesFilter);
        break;
    }

    // Set the default file name based on the output file name
    if (!this.output.getFilename().isEmpty())
    {
      fileChooser.setSelectedFile(this.output.getFile());
    }

    fileChooser.showSaveDialog(p_parent);

    return fileChooser.getSelectedFile();
  }

  public File getRulesFile()
  {
    return new File(changeFileExtension(this.output.getAbsolutePath(), RULES_FILE_EXTENSION));
  }

  public File getEagleScriptFile()
  {
    return new File(changeFileExtension(this.output.getAbsolutePath(), EAGLE_SCRIPT_FILE_EXTENSION));
  }

  public void setDummyInputFile(String filename)
  {
    this.input = new BoardFileDetails();
    this.snapshot = new BoardFileDetails();
    this.output = new BoardFileDetails();

    if ((filename != null) && (filename.toLowerCase().endsWith(DSN_FILE_EXTENSION)))
    {
      this.input.setFormat(FileFormat.DSN);
      this.input.setFilename(filename);
      this.snapshot.setFilename(getSnapshotFilename(this.input.getFile()));
    }
  }

  private boolean tryToSetInput(byte[] fileContent)
  {
    if (fileContent == null)
    {
      return false;
    }

    this.input.setFormat(getFileFormat(fileContent));

    if (this.input.getFormat() != FileFormat.UNKNOWN)
    {
      this.input.setData(fileContent);
      return true;
    }

    return false;
  }

  // Changes the file extension of the selected file
  private String changeFileExtension(String filename, String newFileExtension)
  {
    Path filePath = Path.of(filename);

    // Get the filename and split it into parts
    String originalFullPathWithoutFilename = filePath.getParent().toAbsolutePath().toString();
    String originalFilename = filePath.getFileName().toString();
    String[] nameParts = originalFilename.split("\\.");
    if (nameParts.length > 1)
    {
      String extension = nameParts[nameParts.length - 1].toLowerCase();
      if (extension.equals(newFileExtension))
      {
        return filePath.toString();
      }
      String newFilename = originalFilename.substring(0, originalFilename.length() - extension.length() - 1) + "." + newFileExtension;

      return Path.of(originalFullPathWithoutFilename, newFilename).toString();
    }

    return Path.of(originalFullPathWithoutFilename, originalFilename + "." + newFileExtension).toString();
  }

  public boolean tryToSetOutputFile(File outputFile)
  {
    if (outputFile == null)
    {
      return false;
    }

    FileFormat ff = getFileFormat(outputFile.toPath());

    if ((ff == FileFormat.DSN) || (ff == FileFormat.FRB) || (ff == FileFormat.SES) || (ff == FileFormat.SCR))
    {
      this.output = new BoardFileDetails(outputFile);
      this.output.setFormat(ff);
      return true;
    }
    else
    {
      return false;
    }
  }

  public String getInputFileDetails()
  {
    return new BoardFileDetails(this.input.getFile()).toString();
  }

  public String getOutputFileDetails()
  {
    return new BoardFileDetails(this.output.getFile()).toString();
  }

  @Override
  public int compareTo(RoutingJob o)
  {
    if (this.priority.ordinal() < o.priority.ordinal())
    {
      return -1;
    }
    else if (this.priority.ordinal() > o.priority.ordinal())
    {
      return 1;
    }
    else
    {
      return 0;
    }
  }

  public BoardFileDetails getInput()
  {
    return input;
  }

  public void setInput(String inputFilePath) throws IOException
  {
    setInput(new File(inputFilePath));
  }

  public void setInput(File inputFile) throws IOException
  {
    // Read the file contents into a byte array and initialize the RoutingJob object with it
    FileInputStream fileInputStream = new FileInputStream(inputFile);
    byte[] content = fileInputStream.readAllBytes();

    setInput(content);
    input.setFilename(inputFile.getAbsolutePath());
    if (input.getFormat() == FileFormat.UNKNOWN)
    {
      // As a fallback method, set the file format based on its extension
      input.setFormat(getFileFormat(Path.of(input.getAbsolutePath())));
    }

    if (this.input.getFormat() == FileFormat.FRB)
    {
      this.output = new BoardFileDetails();
      this.output.setFilename(changeFileExtension(input.getAbsolutePath(), BINARY_FILE_EXTENSION));
    }

    if (this.input.getFormat() == FileFormat.DSN)
    {
      this.output = new BoardFileDetails();
      this.output.setFilename(changeFileExtension(input.getAbsolutePath(), SES_FILE_EXTENSION));
    }

    if (this.input.getFormat() != FileFormat.UNKNOWN)
    {
      this.input = new BoardFileDetails(inputFile);
      this.name = input.getFilenameWithoutExtension();
      this.snapshot = new BoardFileDetails();
      this.snapshot.setFilename(getSnapshotFilename(this.input.getFile()));
    }
  }
}