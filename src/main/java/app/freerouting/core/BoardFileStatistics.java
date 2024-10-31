package app.freerouting.core;

import app.freerouting.board.BasicBoard;
import app.freerouting.gui.FileFormat;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BoardFileStatistics implements Serializable
{
  @SerializedName("layer_count")
  public int layerCount = 0;
  @SerializedName("component_count")
  public int componentCount = 0;
  @SerializedName("netclass_count")
  public Integer netclassCount = null;
  @SerializedName("net_count")
  public int netCount = 0;
  @SerializedName("track_count")
  public int trackCount = 0;
  @SerializedName("trace_count")
  public int traceCount = 0;
  @SerializedName("via_count")
  public int viaCount = 0;

  public BoardFileStatistics()
  {
  }

  /**
   * Creates a new BoardDetails object from a RoutingBoard object.
   */
  public BoardFileStatistics(BasicBoard board)
  {
    this.layerCount = board.get_layer_count();
    this.componentCount = board.components.count();
    this.netclassCount = 0;
    this.netCount = 0;
    this.trackCount = 0;
    this.traceCount = board.get_traces().size();
    this.viaCount = board.get_vias().size();
  }

  public BoardFileStatistics(byte[] data, FileFormat format)
  {
    // set the statistical data based on the file content
    if ((format == FileFormat.SES) || (format == FileFormat.DSN))
    {
      // read the content as text
      String content = new String(data, StandardCharsets.UTF_8);

      if (format == FileFormat.SES)
      {
        // to get the affected layers, we need to all "(path {layer}" occurrences, and count the different layers
        // find all occurrences of "(path " substring, and collect these lines in to a list
        String[] lines = content.split("\\(path ");

        // create a list to store the layer names
        List<String> layers = new ArrayList<>();
        // iterate over the lines
        for (String line : lines)
        {
          String[] words = line.split(" ");

          if (words.length >= 2)
          {
            // get the layer name
            String layer = words[1];
            // add the layer name to the list
            if (!layers.contains(layer))
            {
              layers.add(layer);
            }

          }
        }

        // get the number of components and nets in the SES file
        this.layerCount = layers.size();
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = null;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = 0;
        this.traceCount = content.split("\\(wire").length - 1;
        this.viaCount = content.split("\\(via").length - 1;
      }
      else if (format == FileFormat.DSN)
      {
        // get the number of layers and nets in the DSN file
        this.layerCount = content.split("\\(layer").length - 1;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = content.split("\\(class").length - 1;
        this.netCount = content.split("\\(net").length - 1;
        this.trackCount = content.split("\\(wire").length - 1;
        this.traceCount = 0;
        this.viaCount = content.split("\\(via").length - 1;
      }
    }
    else
    {
      this.layerCount = 0;
      this.componentCount = 0;
      this.netclassCount = 0;
      this.netCount = 0;
      this.trackCount = 0;
      this.traceCount = 0;
      this.viaCount = 0;
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