package app.freerouting.core.scoring;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.constants.Constants;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.gui.FileFormat;
import app.freerouting.management.TextManager;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Statistics of a board.
 */
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
    if ((host == null) || host.isEmpty())
    {
      this.host = "Freerouting," + Constants.FREEROUTING_VERSION;
    }
    this.host = TextManager.unescapeUnicode(this.host);

    this.unit = board.communication.unit.toString();

    // Board
    this.board.boundingBox = new Rectangle2D.Float((float) bb.ur.x, (float) board.get_bounding_box().ur.y, (float) board.get_bounding_box().ll.x, (float) board.get_bounding_box().ll.y);
    this.board.size = new Rectangle2D.Float(0, 0, Math.abs((float) board.get_bounding_box().ll.x - (float) board.get_bounding_box().ur.x), Math.abs((float) board.get_bounding_box().ll.y - (float) board.get_bounding_box().ur.y));

    // Layers
    this.layers.totalCount = board.get_layer_count();
    this.layers.signalCount = board.layer_structure.signal_layer_count();

    // Components
    this.components.totalCount = board.components.count();

    // Pads
    this.pads.totalCount = board
        .get_pins()
        .size();

    // Nets
    this.nets.totalCount = board.rules.nets.max_net_no();
    this.nets.classCount = board.rules.net_classes.count();

    // Traces
    this.traces.totalCount = board
        .get_traces()
        .size();
    this.traces.totalLength = (float) board
        .get_traces()
        .stream()
        .mapToDouble(trace -> trace.get_length())
        .sum();
    if (this.traces.totalCount > 0)
    {
      this.traces.averageLength = this.traces.totalLength / this.traces.totalCount;
    }
    else
    {
      this.traces.averageLength = 0.0f;
    }
    this.traces.totalSegmentCount = 0;
    this.traces.totalHorizontalLength = 0.0f;
    this.traces.totalVerticalLength = 0.0f;
    this.traces.totalAngledLength = 0.0f;
    for (Trace trace : board.get_traces())
    {
      // Calculate segments for this trace
      if (trace instanceof app.freerouting.board.PolylineTrace polylineTrace)
      {
        Polyline polyline = polylineTrace.polyline();
        int cornerCount = polyline.corner_count();
        // Number of segments is cornerCount - 1
        if (cornerCount > 1)
        {
          this.traces.totalSegmentCount += cornerCount - 1;
        }

        for (Line line : polyline.arr)
        {
          FloatPoint a = line.a.to_float();
          FloatPoint b = line.b.to_float();
          float length = (float) Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));

          if (a.x == b.x)
          {
            this.traces.totalVerticalLength += length;
          }
          else if (a.y == b.y)
          {
            this.traces.totalHorizontalLength += length;
          }
          else
          {
            this.traces.totalAngledLength += length;
          }

        }
      }
    }

    // Bends
    this.bends.totalCount = 0;
    this.bends.ninetyDegreeCount = 0;
    this.bends.fortyFiveDegreeCount = 0;
    this.bends.otherAngleCount = 0;
    for (Trace trace : board.get_traces())
    {
      if (trace instanceof app.freerouting.board.PolylineTrace polylineTrace)
      {
        // Polyline traces can have bends between consecutive line segments
        app.freerouting.geometry.planar.Polyline polyline = polylineTrace.polyline();
        int cornerCount = polyline.corner_count();

        // We have (cornerCount - 2) internal corners, each representing a potential bend
        if (cornerCount >= 3)
        {
          // Count all internal corners as bends
          int bendsInTrace = cornerCount - 2;
          this.bends.totalCount += bendsInTrace;

          // Now classify each bend by angle
          for (int i = 1; i < cornerCount - 1; i++)
          {
            FloatPoint prev = polyline
                .corner(i - 1)
                .to_float();
            FloatPoint curr = polyline
                .corner(i)
                .to_float();
            FloatPoint next = polyline
                .corner(i + 1)
                .to_float();

            // Calculate vectors for the two segments
            double dx1 = curr.x - prev.x;
            double dy1 = curr.y - prev.y;
            double dx2 = next.x - curr.x;
            double dy2 = next.y - curr.y;

            // Calculate the angle between the two segments
            double angle = Math.abs(Math.toDegrees(Math.atan2(dy2, dx2) - Math.atan2(dy1, dx1)));
            // Normalize the angle to [0, 180]
            angle = Math.min(angle, 360 - angle);
            angle = (angle > 180) ? 360 - angle : angle;

            // Classify the bend - use a small tolerance for comparison
            if (Math.abs(angle - 90) < 1)
            {
              this.bends.ninetyDegreeCount++;
            }
            else if (Math.abs(angle - 45) < 1 || Math.abs(angle - 135) < 1)
            {
              this.bends.fortyFiveDegreeCount++;
            }
            else
            {
              this.bends.otherAngleCount++;
            }
          }
        }
      }
    }

    // Vias
    this.vias.totalCount = board
        .get_vias()
        .size();
    this.vias.throughHoleCount = 0;
    this.vias.blindCount = 0;
    this.vias.buriedCount = 0;
    for (Via via : board.get_vias())
    {
      if ((via.first_layer() == 0) && (via.last_layer() == this.layers.totalCount - 1))
      {
        this.vias.throughHoleCount++;
      }
      else if ((via.first_layer() == 0) || (via.last_layer() == this.layers.totalCount - 1))
      {
        this.vias.blindCount++;
      }
      else
      {
        this.vias.buriedCount++;
      }
    }

    // Clearance violations
    this.clearanceViolations.totalCount = board
        .get_outline()
        .clearance_violation_count();
  }

  /**
   * Creates a new BoardDetails object from a file. This method should be used only if the board object is not available, because the board object based method is more detailed.
   *
   * @param data   Binary data of the file.
   * @param format Format of the file. Only SES and DSN formats are supported at the moment.
   */
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