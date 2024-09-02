package app.freerouting.board;

import app.freerouting.core.RoutingJob;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class BoardFileDetails
{
  // The size of the file in bytes
  @SerializedName("size")
  public long size = 0;
  // The CRC32 checksum of the data
  @SerializedName("crc32")
  public long crc32 = 0;
  // The format of the file
  @SerializedName("format")
  public FileFormat format = FileFormat.UNKNOWN;
  @SerializedName("layer_count")
  public int layerCount = 0;
  @SerializedName("component_count")
  public int componentCount = 0;
  @SerializedName("netclass_count")
  public int netclassCount = 0;
  @SerializedName("net_count")
  public int netCount = 0;
  @SerializedName("track_count")
  public int trackCount = 0;
  @SerializedName("trace_count")
  public int traceCount = 0;
  @SerializedName("via_count")
  public int viaCount = 0;
  // The filename only without the path
  @SerializedName("filename")
  protected String filename = "";
  // The absolute path to the directory of the file
  @SerializedName("path")
  protected String directoryPath = "";
  protected transient byte[] data = new byte[0];

  public BoardFileDetails()
  {
  }

  /**
   * Creates a new BoardDetails object from a file.
   */
  public BoardFileDetails(File file)
  {
    this.setFilename(file.getAbsolutePath());

    try (FileInputStream fis = new FileInputStream(file))
    {
      this.setData(fis.readAllBytes());
    } catch (IOException e)
    {
      // Ignore the exception and continue with the default values
      FRLogger.error("Failed to read file contents.", e);
    }

    if ((this.format == FileFormat.SES) || (this.format == FileFormat.DSN))
    {
      String content = "";
      try
      {
        // read the content of the output file as text
        content = Files.readString(file.toPath());
      } catch (IOException e)
      {
        // Ignore the exception and continue with the default values
      }

      if (this.format == FileFormat.SES)
      {
        // get the number of components and nets in the SES file
        this.layerCount = 0;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = 0;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = 0;
        this.traceCount = 0;
        this.viaCount = 0;
        return;
      }
      else if (this.format == FileFormat.DSN)
      {
        // get the number of layers and nets in the DSN file
        this.layerCount = content.split("\\(layer").length - 1;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = content.split("\\(class").length - 1;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = content.split("\\(wire").length - 1;
        this.traceCount = 0;
        this.viaCount = content.split("\\(via").length - 1;
        return;
      }
    }

    this.layerCount = 0;
    this.componentCount = 0;
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = 0;
    this.viaCount = 0;
  }

  /**
   * Creates a new BoardDetails object from a RoutingBoard object.
   */
  public BoardFileDetails(BasicBoard board)
  {
    this.layerCount = board.get_layer_count();
    this.componentCount = board.components.count();
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = board.get_traces().size();
    this.viaCount = board.get_vias().size();
  }

  public static CRC32 calculateCrc32(InputStream inputStream)
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
   * Saves this object to a UTF-8 JSON file.
   */
  public void saveAs(String filename) throws IOException
  {
    try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8))
    {
      writer.write(this.toString());
    }
  }

  public String getAbsolutePath()
  {
    return Path.of(this.directoryPath, this.filename).toString();
  }

  public ByteArrayInputStream getData()
  {
    return new ByteArrayInputStream(this.data);
  }

  public void setData(byte[] data)
  {
    this.data = data;
    this.size = data.length;
    InputStream inputStream = new ByteArrayInputStream(this.data);
    this.crc32 = BoardFileDetails.calculateCrc32(inputStream).getValue();

    // read the file contents to determine the file format
    this.format = RoutingJob.getFileFormat(this.data);
  }

  /**
   * Returns a JSON representation of this object.
   */
  public String toString()
  {
    return GsonProvider.GSON.toJson(this);
  }

  public File getFile()
  {
    if (!this.filename.isEmpty())
    {
      return new File(Path.of(this.directoryPath, this.filename).toString());
    }
    return null;
  }

  public String getDirectoryPath()
  {
    return this.directoryPath;
  }

  public String getFilename()
  {
    return this.filename;
  }

  /**
   * Sets both the filename and the path.*
   *
   * @param filename The filename to set, optionally with its path.
   */
  public void setFilename(String filename)
  {
    if (filename == null)
    {
      this.directoryPath = "";
      this.filename = "";
      return;
    }

    var path = Path.of(filename).toAbsolutePath();

    if (filename.contains(File.separator))
    {
      // separate the filename into its absolute path and its filename only
      this.directoryPath = path.getParent().toString();
      // replace the redundant "\.\" with a simple "\"
      this.directoryPath = this.directoryPath.replace("\\.\\", "\\");
      // remove the "/", "\" from the end of the directory path
      this.directoryPath = this.directoryPath.replaceAll("[/\\\\]+$", "");
      // remove the "\." from the end of the directory path
      this.directoryPath = this.directoryPath.replaceAll("\\\\.$", "");
    }
    else
    {
      this.directoryPath = "";
    }

    // set the filename only
    this.filename = path.getFileName().toString();

    if (this.format == FileFormat.UNKNOWN)
    {
      // try to read the file contents to determine the file format
      this.format = RoutingJob.getFileFormat(Path.of(this.filename));
    }

    // add the default file extension if it is missing
    if ((this.format != FileFormat.UNKNOWN) && (!this.filename.contains(".")))
    {
      String extension = "";
      switch (this.format)
      {
        case SES:
          extension = "ses";
          break;
        case DSN:
          extension = "dsn";
          break;
        case FRB:
          extension = "frb";
          break;
        case RULES:
          extension = "rules";
          break;
        case SCR:
          extension = "scr";
          break;
        default:
          break;
      }

      if (!extension.isEmpty())
      {
        this.filename = this.filename + "." + extension;
      }
    }
  }

  public String getFilenameWithoutExtension()
  {
    if (this.filename.contains("."))
    {
      return this.filename.substring(0, this.filename.lastIndexOf('.'));
    }
    return this.filename;
  }
}