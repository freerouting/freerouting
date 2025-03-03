package app.freerouting.core.scoring;

import app.freerouting.board.BasicBoard;
import app.freerouting.gui.FileFormat;
import app.freerouting.management.TextManager;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BoardFileStatistics implements Serializable
{
  @SerializedName("host")
  public String host = null;
  @SerializedName("unit")
  public String unit = null;
  @SerializedName("board")
  public BoardFileStatisticsBoard board = new BoardFileStatisticsBoard();
  @SerializedName("layers")
  public BoardFileStatisticsLayers layers = new BoardFileStatisticsLayers();
  @SerializedName("components")
  public BoardFileStatisticsComponents components = new BoardFileStatisticsComponents();
  @SerializedName("pads")
  public BoardFileStatisticsPads pads = new BoardFileStatisticsPads();
  @SerializedName("nets")
  public BoardFileStatisticsNets nets = new BoardFileStatisticsNets();
  @SerializedName("traces")
  public BoardFileStatisticsTraces traces = new BoardFileStatisticsTraces();
  @SerializedName("bends")
  public BoardFileStatisticsBends bends = new BoardFileStatisticsBends();
  @SerializedName("vias")
  public BoardFileStatisticsVias vias = new BoardFileStatisticsVias();
  @SerializedName("clearance_violations")
  public BoardFileStatisticsClearanceViolations clearanceViolations = new BoardFileStatisticsClearanceViolations();

  public BoardFileStatistics()
  {
  }

  /**
   * Creates a new BoardDetails object from a RoutingBoard object.
   */
  public BoardFileStatistics(BasicBoard board)
  {
    var bb = board.get_bounding_box();

    this.host = board.communication.specctra_parser_info.host_cad + "," + board.communication.specctra_parser_info.host_version;
    this.unit = board.communication.unit.toString();
    this.board.boundingBox = new Rectangle2D.Float((float) bb.ur.x, (float) board.get_bounding_box().ur.y, (float) board.get_bounding_box().ll.x, (float) board.get_bounding_box().ll.y);
    this.board.size = new Rectangle2D.Float(0, 0, (float) board.get_bounding_box().ll.x - (float) board.get_bounding_box().ur.x, (float) board.get_bounding_box().ll.y - (float) board.get_bounding_box().ur.y);
    this.layers.totalCount = board.get_layer_count();
    this.layers.signalCount = board.layer_structure.signal_layer_count();
    this.components.totalCount = board.components.count();
    this.traces.totalCount = board
        .get_traces()
        .size();
    this.vias.totalCount = board
        .get_vias()
        .size();
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
        for (int i = 0; i < lines.length; i++)
        {
          String line = lines[i];
          String[] words = line.split(" ");

          if ((i > 0) && (words.length >= 2))
          {
            // get the layer name
            String layer = words[0];
            // add the layer name to the list
            if (!layers.contains(layer))
            {
              layers.add(layer);
            }

          }
        }

        // get the number of components and nets in the SES file
        this.layers.totalCount = layers.size();
        this.components.totalCount = content.split("\\(component").length - 1;
        this.nets.totalCount = content.split("\\(net").length - 1;
        this.traces.totalCount = content.split("\\(wire").length - 1;
        this.vias.totalCount = content.split("\\(via").length - 1;
      }
      else if (format == FileFormat.DSN)
      {
        // extract the host from the DSN file
        String[] lines = content.split("\n");
        String host_cad = null;
        String host_version = null;
        for (String line : lines)
        {
          String value = null;

          line = line.trim();
          if (line.startsWith("(host_cad"))
          {
            value = line
                .substring(9, line.length() - 1)
                .trim();
            host_cad = TextManager.removeQuotes(value);
          }
          else if (line.startsWith("(host_version"))
          {
            value = line
                .substring(13, line.length() - 1)
                .trim();
            host_version = TextManager.removeQuotes(value);
          }

          if ((host_cad != null) && (host_version != null))
          {
            break;
          }
        }

        if ((host_cad != null) && (host_version != null))
        {
          this.host = host_cad + "," + host_version;
        }
        else if (host_cad != null)
        {
          this.host = host_cad;
        }

        // get the number of layers and nets in the DSN file
        this.layers.totalCount = content.split("\\(layer").length - 1;
        this.components.totalCount = content.split("\\(component").length - 1;
        this.nets.classCount = content.split("\\(class").length - 1;
        this.nets.totalCount = content.split("\\(net").length - 1;
        this.traces.totalCount = content.split("\\(wire").length - 1;
        this.vias.totalCount = content.split("\\(via").length - 1;
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