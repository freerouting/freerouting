package app.freerouting.board;

import app.freerouting.core.RoutingJob;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BoardDetails
{
  @SerializedName("path")
  public String path;
  @SerializedName("filename")
  public String filename;
  @SerializedName("size")
  public long filesize;
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

  /**
   * Creates a new BoardDetails object from a file.
   */
  public BoardDetails(File file)
  {
    this.path = file.getAbsolutePath();
    this.filename = file.getName();
    this.filesize = file.length();

    try (FileInputStream fis = new FileInputStream(file))
    {
      // read the file contents to determine the file format
      this.format = RoutingJob.getFileFormat(fis.readAllBytes());

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
    } catch (IOException e)
    {
      // Ignore the exception and continue with the default values
      FRLogger.error("Failed to read file contents.", e);
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
  public BoardDetails(BasicBoard board)
  {
    this.path = null;
    this.filename = null;
    this.filesize = 0;
    this.format = null;

    this.layerCount = board.get_layer_count();
    this.componentCount = board.components.count();
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = board.get_traces().size();
    this.viaCount = board.get_vias().size();
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
   * Returns a JSON representation of this object.
   */
  public String toString()
  {
    return GsonProvider.GSON.toJson(this);
  }
}