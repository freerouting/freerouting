package app.freerouting.core.scoring;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ComponentOutline;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Trace;
import app.freerouting.board.Unit;
import app.freerouting.board.Via;
import app.freerouting.constants.Constants;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.settings.ScoringSettings;
import app.freerouting.util.TextManager;
import app.freerouting.util.gson.GsonProvider;
import com.google.gson.annotations.SerializedName;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Statistics of a board. */
public class BoardStatistics implements Serializable {

  @SerializedName("host")
  public String host;

  @SerializedName("unit")
  public String unit;

  @SerializedName("board")
  public BoardStatisticsBoard board = new BoardStatisticsBoard();

  @SerializedName("layers")
  public BoardStatisticsLayers layers = new BoardStatisticsLayers();

  @SerializedName("items")
  public BoardStatisticsItems items = new BoardStatisticsItems();

  @SerializedName("components")
  public BoardStatisticsComponents components = new BoardStatisticsComponents();

  @SerializedName("pads")
  public BoardStatisticsPads pads = new BoardStatisticsPads();

  @SerializedName("nets")
  public BoardStatisticsNets nets = new BoardStatisticsNets();

  @SerializedName("connections")
  public BoardStatisticsConnections connections = new BoardStatisticsConnections();

  @SerializedName("traces")
  public BoardStatisticsTraces traces = new BoardStatisticsTraces();

  @SerializedName("bends")
  public BoardStatisticsBends bends = new BoardStatisticsBends();

  @SerializedName("vias")
  public BoardStatisticsVias vias = new BoardStatisticsVias();

  @SerializedName("clearance_violations")
  public BoardStatisticsClearanceViolations clearanceViolations =
      new BoardStatisticsClearanceViolations();

  @SerializedName("fanout")
  public BoardStatisticsFanout fanout = new BoardStatisticsFanout();

  /** Creates empty board statistics. */
  public BoardStatistics() {}

  /** Creates a new BoardFileStatistics object from a RoutingBoard object. */
  public BoardStatistics(BasicBoard board) {
    this(board, null, true);
  }

  /**
   * Creates a new BoardFileStatistics object from a RoutingBoard object and defines the preferred
   * unit for the statistics.
   */
  public BoardStatistics(BasicBoard board, Unit unit) {
    this(board, unit, true);
  }

  /**
   * Creates a new BoardFileStatistics object from a RoutingBoard object, defines the preferred
   * unit, and allows skipping the clearance checks.
   */
  public BoardStatistics(BasicBoard board, Unit unit, boolean includeClearanceViolations) {
    this(board, unit, includeClearanceViolations, true);
  }

  /**
   * Creates board statistics with optional clearance and connection (incomplete) analysis.
   *
   * @param includeConnections when {@code false}, skips {@code calculateAllIncompletes()} — use
   *     when a {@link app.freerouting.gui.workspace.RatsNest} will be created immediately after
   *     load
   */
  public BoardStatistics(
      BasicBoard board, Unit unit, boolean includeClearanceViolations, boolean includeConnections) {
    final var bb = board.getBoundingBox();

    this.host =
        board.communication.specctraParserInfo.hostCad
            + ","
            + board.communication.specctraParserInfo.hostVersion;
    if ((host == null) || host.isEmpty()) {
      this.host = "Freerouting," + Constants.FREEROUTING_VERSION;
    }
    this.host = TextManager.unescapeUnicode(this.host);

    this.unit = board.communication.unit.toString();

    // Board
    this.board.boundingBox =
        new Rectangle2D.Float(
            (float) bb.ur.x,
            (float) board.getBoundingBox().ur.y,
            (float) board.getBoundingBox().ll.x,
            (float) board.getBoundingBox().ll.y);
    this.board.size =
        new Rectangle2D.Float(
            0,
            0,
            Math.abs((float) board.getBoundingBox().ll.x - (float) board.getBoundingBox().ur.x),
            Math.abs((float) board.getBoundingBox().ll.y - (float) board.getBoundingBox().ur.y));

    // Layers
    this.layers.totalCount = board.getLayerCount();
    this.layers.signalCount = board.layerStructure.signalLayerCount();

    // Items
    this.items.totalCount = 0;
    this.items.traceCount = 0;
    this.items.viaCount = 0;
    this.items.conductionAreaCount = 0;
    this.items.drillItemCount = 0;
    this.items.pinCount = 0;
    this.items.componentOutlineCount = 0;
    this.items.otherCount = 0;
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      this.items.totalCount++;
      if (currentItem instanceof Trace) {
        this.items.traceCount++;
      } else if (currentItem instanceof Via) {
        this.items.viaCount++;
      } else if (currentItem instanceof ConductionArea) {
        this.items.conductionAreaCount++;
      } else if (currentItem instanceof Pin) {
        this.items.pinCount++;
      } else if (currentItem instanceof DrillItem) {
        this.items.drillItemCount++;
      } else if (currentItem instanceof ComponentOutline) {
        this.items.componentOutlineCount++;
      } else {
        this.items.otherCount++;
      }
    }

    // Components
    this.components.totalCount = board.components.count();

    // Pads
    this.pads.totalCount = board.getPins().size();

    // Nets
    this.nets.totalCount = board.rules.nets.maxNetNumber();
    this.nets.classCount = board.rules.netClasses.count();

    // Traces
    this.traces.totalCount = board.getTraces().size();
    this.traces.totalLength =
        (float) board.getTraces().stream().mapToDouble(Trace::getLength).sum();
    // Normalise trace length to millimetres so that calculateScore() uses a
    // physically meaningful cost regardless of DSN coordinate resolution.
    // Raw board units vary wildly: KiCad exports at 1e-6 mm/unit (1 nm), while
    // EAGLE/Benchmark DSNs use ~0.1 µm/unit.  Without normalisation the trace-cost
    // term in getNormalizedScore() is thousands of times larger than
    // maxConnections * unroutedNetPenalty for high-resolution boards, forcing the
    // score to 0 even for a perfectly-routed, zero-violation layout.
    double boardUnitToMmFactor =
        Unit.scale(1.0, board.communication.unit, Unit.MM)
            / (board.communication.resolution > 0 ? board.communication.resolution : 1);
    this.traces.totalLengthMm = (float) (this.traces.totalLength * boardUnitToMmFactor);
    if (this.traces.totalCount > 0) {
      this.traces.averageLength = this.traces.totalLength / this.traces.totalCount;
    } else {
      this.traces.averageLength = 0.0f;
    }
    this.traces.totalSegmentCount = 0;
    this.traces.totalHorizontalLength = 0.0f;
    this.traces.totalVerticalLength = 0.0f;
    this.traces.totalAngledLength = 0.0f;
    for (Trace trace : board.getTraces()) {
      // Calculate segments for this trace
      if (trace instanceof PolylineTrace polylineTrace) {
        Polyline polyline = polylineTrace.polyline();
        int cornerCount = polyline.cornerCount();
        // Number of segments is cornerCount - 1
        if (cornerCount > 1) {
          this.traces.totalSegmentCount += cornerCount - 1;
        }

        for (Line line : polyline.arr) {
          FloatPoint a = line.a.toFloat();
          FloatPoint b = line.b.toFloat();
          float length = (float) Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));

          if (a.x == b.x) {
            this.traces.totalVerticalLength += length;
          } else if (a.y == b.y) {
            this.traces.totalHorizontalLength += length;
          } else {
            this.traces.totalAngledLength += length;
          }
        }
      }
    }

    this.traces.totalWeightedLength = 0.0f;
    int defaultClearanceClass = BoardRules.defaultClearanceClass();
    Iterator<UndoableObjects.UndoableObjectNode> it2 = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentItem = board.itemList.readObject(it2);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof Trace currentTrace) {
        FixedState fixedState = currentTrace.getFixedState();
        if (fixedState == FixedState.UNFIXED || fixedState == FixedState.SHOVE_FIXED) {
          double weightedTraceLength =
              currentTrace.getLength()
                  * (currentTrace.getHalfWidth()
                      + board.clearanceValue(
                          currentTrace.clearanceClassIndex(),
                          defaultClearanceClass,
                          currentTrace.getLayer()));
          if (fixedState == FixedState.SHOVE_FIXED) {
            // to produce less violations with pin exit directions.
            weightedTraceLength /= 2;
          }
          this.traces.totalWeightedLength += (float) weightedTraceLength;
        }
      }
    }

    // Connections
    if (includeConnections) {
      var drc = new app.freerouting.drc.DesignRulesChecker(board, null);
      drc.calculateAllIncompletes();
      this.connections.maximumCount = drc.maxConnections;
      this.connections.incompleteCount = drc.getIncompleteCount();
    }

    // Bends
    this.bends.totalCount = 0;
    this.bends.ninetyDegreeCount = 0;
    this.bends.fortyFiveDegreeCount = 0;
    this.bends.otherAngleCount = 0;
    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polylineTrace) {
        // Polyline traces can have bends between consecutive line segments
        Polyline polyline = polylineTrace.polyline();
        int cornerCount = polyline.cornerCount();

        // We have (cornerCount - 2) internal corners, each representing a potential
        // bend
        if (cornerCount >= 3) {
          // Count all internal corners as bends
          int bendsInTrace = cornerCount - 2;
          this.bends.totalCount += bendsInTrace;

          // Now classify each bend by angle
          for (int i = 1; i < cornerCount - 1; i++) {
            FloatPoint prev = polyline.corner(i - 1).toFloat();
            FloatPoint current = polyline.corner(i).toFloat();
            FloatPoint next = polyline.corner(i + 1).toFloat();

            // Calculate vectors for the two segments
            double dx1 = current.x - prev.x;
            double dy1 = current.y - prev.y;
            double dx2 = next.x - current.x;
            double dy2 = next.y - current.y;

            // Calculate the angle between the two segments
            double angle = Math.abs(Math.toDegrees(Math.atan2(dy2, dx2) - Math.atan2(dy1, dx1)));
            // Normalize the angle to [0, 180]
            angle = Math.min(angle, 360 - angle);
            angle = angle > 180 ? 360 - angle : angle;

            // Classify the bend - use a small tolerance for comparison
            if (Math.abs(angle - 90) < 1) {
              this.bends.ninetyDegreeCount++;
            } else if (Math.abs(angle - 45) < 1 || Math.abs(angle - 135) < 1) {
              this.bends.fortyFiveDegreeCount++;
            } else {
              this.bends.otherAngleCount++;
            }
          }
        }
      }
    }

    // Vias
    this.vias.totalCount = board.getVias().size();
    this.vias.throughHoleCount = 0;
    this.vias.blindCount = 0;
    this.vias.buriedCount = 0;
    for (Via via : board.getVias()) {
      if ((via.firstLayer() == 0) && (via.lastLayer() == this.layers.totalCount - 1)) {
        this.vias.throughHoleCount++;
      } else if ((via.firstLayer() == 0) || (via.lastLayer() == this.layers.totalCount - 1)) {
        this.vias.blindCount++;
      } else {
        this.vias.buriedCount++;
      }
    }

    if (includeClearanceViolations) {
      var clearanceDrc = new app.freerouting.drc.DesignRulesChecker(board, null);
      this.clearanceViolations.totalCount = clearanceDrc.getAllClearanceViolations().size();
    } else {
      this.clearanceViolations.totalCount = 0;
    }

    // Convert all length values from board.communication.unit to the preferred unit
    if (unit == null) {
      unit = Unit.MM;
    }

    if (unit != board.communication.unit) {
      // convert all length values to the preferred unit
      Unit fromUnit = board.communication.unit;
      Unit toUnit = unit;
      this.unit = unit.toString();

      // Board
      this.board.boundingBox =
          new Rectangle2D.Float(
              (float) Unit.scale(this.board.boundingBox.x, fromUnit, toUnit),
              (float) Unit.scale(this.board.boundingBox.y, fromUnit, toUnit),
              (float) Unit.scale(this.board.boundingBox.width, fromUnit, toUnit),
              (float) Unit.scale(this.board.boundingBox.height, fromUnit, toUnit));
      this.board.size =
          new Rectangle2D.Float(
              0,
              0,
              (float) Unit.scale(this.board.size.width, fromUnit, toUnit),
              (float) Unit.scale(this.board.size.height, fromUnit, toUnit));

      // Traces
      this.traces.totalLength = (float) Unit.scale(this.traces.totalLength, fromUnit, toUnit);
      this.traces.totalWeightedLength =
          (float) Unit.scale(this.traces.totalWeightedLength, fromUnit, toUnit);
      this.traces.averageLength = (float) Unit.scale(this.traces.averageLength, fromUnit, toUnit);
      this.traces.totalHorizontalLength =
          (float) Unit.scale(this.traces.totalHorizontalLength, fromUnit, toUnit);
      this.traces.totalVerticalLength =
          (float) Unit.scale(this.traces.totalVerticalLength, fromUnit, toUnit);
      this.traces.totalAngledLength =
          (float) Unit.scale(this.traces.totalAngledLength, fromUnit, toUnit);
    }

    // Calculate fanout statistics
    java.util.Collection<Pin> smdPins = board.getSmdPins();
    int total = 0;
    int escaped = 0;
    int alreadyConnected = 0;
    for (Pin pin : smdPins) {
      if (pin.netCount() > 0) {
        total++;
        int netNumber = pin.getNetNumber(0);
        if (pin.getUnconnectedSet(netNumber).isEmpty()) {
          alreadyConnected++;
        }
        if (isPinEscaped(pin)) {
          escaped++;
        }
      }
    }
    this.fanout.totalSmdPins = total;
    this.fanout.pinsToEscape = total - alreadyConnected;
    this.fanout.escapedCount = escaped;
  }

  /**
   * Creates a new BoardFileStatistics object from a file. This method should be used only if the
   * board object is not available, because the board object based method is more detailed.
   *
   * @param data Binary data of the file.
   * @param format Format of the file. Only SES and DSN formats are supported at the moment.
   */
  public BoardStatistics(byte[] data, FileFormat format) {
    if (data == null || format == null) {
      return;
    }
    // set the statistical data based on the file content
    if (format == FileFormat.SES) {
      // read the content as text
      String content = new String(data, StandardCharsets.UTF_8);

      // to get the affected layers, we need to all "(path {layer}" occurrences, and
      // count the different layers
      // find all occurrences of "(path " substring, and collect these lines in to a
      // list
      String[] lines = content.split("\\(path ");

      // create a list to store the layer names
      List<String> layers = new ArrayList<>();
      // iterate over the lines
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        String[] words = line.split(" ");

        if ((i > 0) && (words.length >= 2)) {
          // get the layer name
          String layer = words[0];
          // add the layer name to the list
          if (!layers.contains(layer)) {
            layers.add(layer);
          }
        }
      }

      // get the number of components and nets in the SES file
      this.layers.totalCount = layers.size();
      this.components.totalCount = countOccurrences(content, "(component");
      this.nets.totalCount = countOccurrences(content, "(net");
      this.traces.totalCount = countOccurrences(content, "(wire");
      this.vias.totalCount = countOccurrences(content, "(via");
    } else if (format == FileFormat.DSN) {
      // read the content as text
      String content = new String(data, StandardCharsets.UTF_8);
      // extract the host from the DSN file without splitting the whole content by lines
      String hostCad = null;
      String hostVersion = null;
      int parserIndex = content.indexOf("(parser");
      if (parserIndex != -1) {
        int searchLimit = content.indexOf(")", parserIndex);
        if (searchLimit == -1) {
          searchLimit = Math.min(content.length(), parserIndex + 1000);
        } else {
          searchLimit = Math.min(content.length(), searchLimit + 1);
        }
        String parserScope = content.substring(parserIndex, searchLimit);
        int hcIdx = parserScope.indexOf("(hostCad");
        if (hcIdx != -1) {
          int hcEnd = parserScope.indexOf(")", hcIdx);
          if (hcEnd != -1) {
            String val = parserScope.substring(hcIdx + 9, hcEnd).trim();
            hostCad = TextManager.removeQuotes(val);
          }
        }
        int hvIdx = parserScope.indexOf("(hostVersion");
        if (hvIdx != -1) {
          int hvEnd = parserScope.indexOf(")", hvIdx);
          if (hvEnd != -1) {
            String val = parserScope.substring(hvIdx + 13, hvEnd).trim();
            hostVersion = TextManager.removeQuotes(val);
          }
        }
      }

      if ((hostCad != null) && (hostVersion != null)) {
        this.host = hostCad + "," + hostVersion;
      } else if (hostCad != null) {
        this.host = hostCad;
      }

      // get the number of layers and nets in the DSN file
      this.layers.totalCount = countOccurrences(content, "(layer");
      this.components.totalCount = countOccurrences(content, "(component");
      this.nets.classCount = countOccurrences(content, "(class");
      this.nets.totalCount = countOccurrences(content, "(net");
      this.traces.totalCount = countOccurrences(content, "(wire");
      this.vias.totalCount = countOccurrences(content, "(via");
    } else if (format == FileFormat.KICAD_DESIGN_JSON) {
      try {
        String content = new String(data, StandardCharsets.UTF_8);
        com.google.gson.JsonObject json =
            GsonProvider.GSON.fromJson(content, com.google.gson.JsonObject.class);
        if (json != null) {
          if (json.has("layers")) {
            this.layers.totalCount = json.getAsJsonArray("layers").size();
          }
          if (json.has("components")) {
            this.components.totalCount = json.getAsJsonArray("components").size();
          }
          if (json.has("netClasses")) {
            this.nets.classCount = json.getAsJsonArray("netClasses").size();
          }
          if (json.has("nets")) {
            this.nets.totalCount = json.getAsJsonArray("nets").size();
          }
          if (json.has("traces")) {
            this.traces.totalCount = json.getAsJsonArray("traces").size();
          }
          if (json.has("vias")) {
            this.vias.totalCount = json.getAsJsonArray("vias").size();
          }
          if (json.has("designName")) {
            this.host = "KiCad JSON," + json.get("designName").getAsString();
          }
        }
      } catch (Exception e) {
        FRLogger.error("Failed to parse JSON statistics: " + e.getMessage(), e);
      }
    }
  }

  /** Returns whether a pin has a valid trace or via escape. */
  public static boolean isPinEscaped(Pin pin) {
    java.util.Set<Item> contacts = pin.getNormalContacts();
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        if (trace.clearanceViolations().isEmpty()) {
          return true;
        }
      } else if (contact instanceof Via via) {
        if (via.clearanceViolations().isEmpty()) {
          java.util.Set<Item> viaContacts = via.getNormalContacts();
          for (Item viaContact : viaContacts) {
            if (viaContact instanceof Trace || viaContact instanceof ConductionArea) {
              return true;
            }
          }
        }
      } else if (contact instanceof ConductionArea) {
        return true;
      }
    }
    return false;
  }

  private static int countOccurrences(String text, String target) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) != -1) {
      count++;
      index += target.length();
    }
    return count;
  }

  /** Returns a JSON representation of this object. */
  public String toString() {
    return GsonProvider.GSON.toJson(this);
  }

  /**
   * Calculates the score/cost of the board based on the given scoring settings. Higher score means
   * better board.
   */
  public float calculateScore(ScoringSettings scoringSettings) {
    float maximumScore = getMaximumScore(scoringSettings);
    float penalties =
        this.connections.incompleteCount * scoringSettings.unroutedNetPenalty
            + this.clearanceViolations.totalCount * scoringSettings.clearanceViolationPenalty
            + this.bends.totalCount * scoringSettings.bendPenalty;
    // Use the mm-normalised trace length so that the trace-cost term is comparable
    // to the unroutedNetPenalty regardless of the DSN internal coordinate resolution.
    // totalLength is in raw board units which vary wildly between DSN files
    // (e.g. 1 nm for KiCad at resolution 1e6, vs 0.1 µm for EAGLE/benchmark boards),
    // and would make the score collapse to 0 for high-resolution KiCad exports.
    float traceLengthForCost =
        this.traces.totalLengthMm != null ? this.traces.totalLengthMm : this.traces.totalLength;
    float costs =
        (float)
            (traceLengthForCost * scoringSettings.defaultPreferredDirectionTraceCost
                + this.vias.totalCount * scoringSettings.viaCosts);

    return maximumScore - penalties - costs;
  }

  /** Returns the maximum score for the supplied scoring settings. */
  public float getMaximumScore(ScoringSettings scoringSettings) {
    return this.connections.maximumCount * scoringSettings.unroutedNetPenalty;
  }

  /** Returns the score normalized to a range from zero to one thousand. */
  public float getNormalizedScore(ScoringSettings scoringSettings) {
    float maximumScore = getMaximumScore(scoringSettings);
    if (maximumScore <= 0f) {
      // Guard against division by zero and negative maximum scores (e.g. boards with no
      // connections, or boards where all nets are single-pin nets). Return 0 so that the
      // score is a defined value and comparisons like "score > threshold" work predictably.
      // This also prevents NaN propagation which could silently break stagnation detection
      // (NaN comparisons always return false, causing stagnation counters to never advance).
      return 0f;
    }
    return Math.max(0, calculateScore(scoringSettings) / maximumScore) * 1000;
  }

  /** Statistics for surface-mount pin fanout. */
  public static class BoardStatisticsFanout implements Serializable {
    @SerializedName("total_smd_pins")
    public int totalSmdPins;

    @SerializedName("pins_to_escape")
    public int pinsToEscape;

    @SerializedName("escaped_count")
    public int escapedCount;
  }
}
