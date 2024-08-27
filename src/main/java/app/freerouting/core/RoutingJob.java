package app.freerouting.core;

import app.freerouting.board.BoardDetails;
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
import java.util.zip.CRC32;

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
  public final Instant finishedAt = null;
  // TODO: pass the router settings as an input and forward it to the router
  private final RouterSettings routerSettings = new RouterSettings(0);
  private final byte[] snapshotFileData = null;
  private final byte[] outputFileData = null;
  public String name;
  public FileFormat inputFileFormat = FileFormat.UNKNOWN;
  public FileFormat outputFileFormat = FileFormat.UNKNOWN;
  public RoutingJobState state = RoutingJobState.INVALID;
  public RoutingJobPriority priority = RoutingJobPriority.NORMAL;
  public RoutingStage stage = RoutingStage.IDLE;
  // TODO: change File type to BinaryStream or byte[] to support both file inputs and web API uploads
  private transient File inputFile;
  private transient File snapshotFile = null;
  private transient File outputFile = null;
  private transient Path inputFilePath = null;
  private transient Path outputFilePath = null;
  private transient Path snapshotFilePath = null;
  private byte[] inputFileData = null;

  /**
   * Creates a new instance of DesignFile and prepares the intermediate file handling.
   */
  public RoutingJob()
  {
    this.name = "J-" + this.id.toString().substring(0, 6).toUpperCase();
  }

  public static CRC32 CalculateCrc32(InputStream inputStream)
  {
    CRC32 crc = new CRC32();
    try
    {
      int cnt;
      while ((cnt = inputStream.read()) != -1)
      {
        crc.update(cnt);
      }
    } catch (IOException e)
    {
      FRLogger.error(e.getLocalizedMessage(), e);
    }
    return crc;
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

  public void setInput(String inputFilePath) throws IOException
  {
    setInput(new File(inputFilePath));
  }

  public void setInput(byte[] inputFileContent)
  {
    this.inputFilePath = null;
    this.tryToSetInput(inputFileContent);
  }

  public void setInput(File inputFile) throws IOException
  {
    // Read the file contents into a byte array and initialize the RoutingJob object with it
    FileInputStream fileInputStream = new FileInputStream(inputFile);
    byte[] content = fileInputStream.readAllBytes();

    setInput(content);
    inputFilePath = inputFile.toPath();
    if (inputFileFormat == FileFormat.UNKNOWN)
    {
      // As a fallback method, set the file format based on its extension
      inputFileFormat = getFileFormat(inputFilePath);
    }

    if (this.inputFileFormat == FileFormat.FRB)
    {
      this.outputFilePath = changeFileExtension(inputFilePath, BINARY_FILE_EXTENSION);
    }

    if (this.inputFileFormat == FileFormat.DSN)
    {
      this.outputFilePath = changeFileExtension(inputFilePath, SES_FILE_EXTENSION);
    }

    if (this.inputFileFormat != FileFormat.UNKNOWN)
    {
      this.inputFile = inputFile;
      this.name = inputFile.getName();
      this.snapshotFile = getSnapshotFilename(this.inputFile);
      this.snapshotFilePath = this.snapshotFile.toPath();
    }
  }

  private File getSnapshotFilename(File inputFile)
  {
    // Calculate the CRC32 checksum of the input file
    long crc32Checksum;
    try (FileInputStream inputStream = new FileInputStream(inputFile.getAbsoluteFile()))
    {
      crc32Checksum = RoutingJob.CalculateCrc32(inputStream).getValue();
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
    return new File(snapshotsFolderPath + File.separator + intermediate_snapshot_file_name);
  }

  /**
   * Gets an InputStream from the file. Returns null, if the algorithm failed.
   */
  public InputStream get_input_stream()
  {
    if (this.inputFile == null)
    {
      return null;
    }
    try
    {
      return new FileInputStream(this.inputFile);
    } catch (Exception e)
    {
      FRLogger.error(e.getLocalizedMessage(), e);
    }
    return null;
  }

  /**
   * Gets the file name as a String. Returns null on failure.
   */
  public String get_name()
  {
    if (this.inputFile != null)
    {
      return this.inputFile.getName();
    }
    return "";
  }

  public File showSaveAsDialog(String p_default_directory, Component p_parent)
  {
    String directoryName;
    if (this.outputFile == null)
    {
      directoryName = p_default_directory;
    }
    else
    {
      directoryName = this.outputFile.getParent();
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
    switch (this.outputFileFormat)
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
    if (this.outputFile != null)
    {
      fileChooser.setSelectedFile(this.outputFile);
    }

    fileChooser.showSaveDialog(p_parent);

    return fileChooser.getSelectedFile();
  }

  public File getOutputFile()
  {
    return this.outputFile;
  }

  public File getInputFile()
  {
    return this.inputFile;
  }

  public File getSnapshotFile()
  {
    return this.snapshotFilePath.toFile();
  }

  public File getRulesFile()
  {
    return changeFileExtension(this.outputFilePath, RULES_FILE_EXTENSION).toFile();
  }

  public File getEagleScriptFile()
  {
    return changeFileExtension(this.outputFilePath, EAGLE_SCRIPT_FILE_EXTENSION).toFile();
  }

  @Deprecated(since = "2.0", forRemoval = true)
  public File get_parent_file()
  {
    if (inputFile != null)
    {
      return inputFile.getParentFile();
    }
    return null;
  }

  // Returns the directory of the design file, or "" if the file is null
  public String getInputFileDirectory()
  {
    if (inputFile == null)
    {
      return "";
    }

    // Get the absolut path without the filename
    return inputFile.getParent();
  }

  // Returns the directory of the design file, or null if the file is null
  @Deprecated(since = "2.0", forRemoval = true)
  public String getInputFileDirectoryOrNull()
  {
    if (inputFile != null)
    {
      return inputFile.getParent();
    }
    return null;
  }

  public void setDummyInputFile(String filename)
  {
    this.outputFileFormat = FileFormat.UNKNOWN;
    this.outputFile = null;

    if ((filename != null) && (filename.toLowerCase().endsWith(DSN_FILE_EXTENSION)))
    {
      this.inputFileFormat = FileFormat.DSN;
      this.inputFile = new File(filename);
      this.snapshotFile = getSnapshotFilename(this.inputFile);
    }
    else
    {
      this.inputFileFormat = FileFormat.UNKNOWN;
      this.inputFile = null;
      this.snapshotFile = null;
    }
  }

  private boolean tryToSetInput(byte[] fileContent)
  {
    if (fileContent == null)
    {
      return false;
    }

    this.inputFileFormat = getFileFormat(fileContent);

    if (this.inputFileFormat != FileFormat.UNKNOWN)
    {
      this.inputFileData = fileContent;
      return true;
    }

    return false;
  }

  // Changes the file extension of the selected file
  private Path changeFileExtension(Path filePath, String newFileExtension)
  {
    // Get the filename and split it into parts
    String originalFullPathWithoutFilename = filePath.getParent().toAbsolutePath().toString();
    String originalFilename = filePath.getFileName().toString();
    String[] nameParts = originalFilename.split("\\.");
    if (nameParts.length > 1)
    {
      String extension = nameParts[nameParts.length - 1].toLowerCase();
      if (extension.equals(newFileExtension))
      {
        return filePath;
      }
      String newFilename = originalFilename.substring(0, originalFilename.length() - extension.length() - 1) + "." + newFileExtension;

      return Path.of(originalFullPathWithoutFilename, newFilename);
    }

    return Path.of(originalFullPathWithoutFilename, originalFilename + "." + newFileExtension);
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
      this.outputFile = outputFile;
      this.outputFileFormat = ff;
      return true;
    }
    else
    {
      return false;
    }
  }

  public String getInputFileDetails()
  {
    return new BoardDetails(this.inputFile).toString();
  }

  public String getOutputFileDetails()
  {
    return new BoardDetails(this.outputFile).toString();
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
}