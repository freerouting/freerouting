package app.freerouting.core;

import app.freerouting.board.BasicBoard;
import app.freerouting.gui.FileFormat;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BoardFileStatistics implements Serializable
{
  @SerializedName("layer_count")
  public Integer layerCount = null;
  @SerializedName("component_count")
  public Integer componentCount = null;
  @SerializedName("netclass_count")
  public Integer netclassCount = null;
  @SerializedName("total_net_count")
  public Integer totalNetCount = null;
  @SerializedName("unrouted_net_count")
  public Integer unroutedNetCount = null;
  @SerializedName("routed_net_count")
  public Integer routedNetCount = null;
  @SerializedName("routed_net_length")
  public Float routedNetLength = null;
  @SerializedName("clearance_violation_count")
  public Float clearanceViolationCount = null;
  @SerializedName("via_count")
  public Integer viaCount = null;

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
    this.routedNetCount = board.get_traces().size();
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

          if ((words.length >= 2) && (Objects.equals(words[0], "(path")))
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
        this.totalNetCount = content.split("\\(net").length - 1;
        this.routedNetCount = content.split("\\(wire").length - 1;
        this.viaCount = content.split("\\(via").length - 1;
      }
      else if (format == FileFormat.DSN)
      {
        // get the number of layers and nets in the DSN file
        this.layerCount = content.split("\\(layer").length - 1;
        this.componentCount = content.split("\\(component").length - 1;
        this.netclassCount = content.split("\\(class").length - 1;
        this.totalNetCount = content.split("\\(net").length - 1;
        this.routedNetCount = content.split("\\(wire").length - 1;
        this.viaCount = content.split("\\(via").length - 1;
      }
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