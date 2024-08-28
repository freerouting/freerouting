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
  // The absolute path to the file
  @SerializedName("path")
  public Path path;
  // The filename only without the path
  @SerializedName("filename")
  public String filename;
  // The size of the file in bytes
  @SerializedName("size")
  public long size;
  // The CRC32 checksum of the file
  @SerializedName("crc32")
  public CRC32 crc32;
  // The format of the file
  @SerializedName("format")
  public FileFormat format;
  @SerializedName("layer_count")
  public int layerCount;
  @SerializedName("component_count")
  public int componentCount;
  @SerializedName("netclass_count")
  public int netclassCount;
  @SerializedName("net_count")
  public int netCount;
  @SerializedName("track_count")
  public int trackCount;
  @SerializedName("trace_count")
  public int traceCount;
  @SerializedName("via_count")
  public int viaCount;
  public transient byte[] data;

  public BoardFileDetails()
  {
  }

  /**
   * Creates a new BoardDetails object from a file.
   */
  public BoardFileDetails(File file)
  {
    this.path = Path.of(file.getAbsolutePath());
    this.filename = file.getName();
    this.size = file.length();

    try (FileInputStream fis = new FileInputStream(file))
    {
      this.data = fis.readAllBytes();
    } catch (IOException e)
    {
      // Ignore the exception and continue with the default values
      FRLogger.error("Failed to read file contents.", e);
    }

    InputStream inputStream = new ByteArrayInputStream(this.data);
    this.crc32 = BoardFileDetails.calculateCrc32(inputStream);

    // read the file contents to determine the file format
    this.format = RoutingJob.getFileFormat(this.data);

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
    this.path = null;
    this.filename = null;
    this.size = 0;
    this.format = null;

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
  public void saveAs(String file) throws IOException
  {
    try (Writer writer = Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8))
    {
      writer.write(this.toString());
    }
  }

  /**
   * Sets both the filename and the path.*
   *
   * @param filename The filename to set, optionally with its path.
   */
  public void setFilename(String filename)
  {
    // separate the filename into its absolute path and its filename only
    this.filename = filename;
    this.path = Path.of(filename).toAbsolutePath();
  }


  /**
   * Returns a JSON representation of this object.
   */
  public String toString()
  {
    return GsonProvider.GSON.toJson(this);
  }
}