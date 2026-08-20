package app.freerouting.core.scoring;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ClearanceViolation;
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
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.interactive.ClearanceViolations;
import app.freerouting.interactive.RatsNest;
import app.freerouting.rules.BoardRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/** Statistics of a board for v1.9 compatibility manifest generation. */
public class BoardStatistics implements Serializable {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

  public BoardStatistics() {}

  public BoardStatistics(BasicBoard board) {
    this(board, null, true, true);
  }

  public BoardStatistics(
      BasicBoard board, Unit unit, boolean includeClearanceViolations, boolean includeConnections) {
    if (board == null) {
      return;
    }

    final IntBox bb = board.get_bounding_box();

    if (board.communication != null && board.communication.specctra_parser_info != null) {
      this.host =
          board.communication.specctra_parser_info.host_cad
              + ","
              + board.communication.specctra_parser_info.host_version;
    }
    if (this.host == null || this.host.isEmpty() || this.host.equals("null,null")) {
      this.host = "Freerouting," + Constants.FREEROUTING_VERSION;
    }

    this.unit = board.communication != null && board.communication.unit != null
        ? board.communication.unit.toString()
        : "mm";

    // Board bounding box
    if (bb != null) {
      this.board.boundingBox =
          new Rectangle2D.Float(
              (float) bb.ur.x,
              (float) bb.ur.y,
              (float) bb.ll.x,
              (float) bb.ll.y);
      this.board.size =
          new Rectangle2D.Float(
              0,
              0,
              Math.abs((float) bb.ll.x - (float) bb.ur.x),
              Math.abs((float) bb.ll.y - (float) bb.ur.y));
    }

    // Layers
    this.layers.totalCount = board.get_layer_count();
    this.layers.signalCount = board.layer_structure != null ? board.layer_structure.signal_layer_count() : board.get_layer_count();

    // Items count
    this.items.totalCount = 0;
    this.items.traceCount = 0;
    this.items.viaCount = 0;
    this.items.conductionAreaCount = 0;
    this.items.drillItemCount = 0;
    this.items.pinCount = 0;
    this.items.componentOutlineCount = 0;
    this.items.otherCount = 0;

    if (board.item_list != null) {
      Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
      for (;;) {
        Item currentItem = (Item) board.item_list.read_object(it);
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
    }

    // Components & Pads
    this.components.totalCount = board.components != null ? board.components.count() : 0;
    Collection<Pin> pins = board.get_pins();
    this.pads.totalCount = pins != null ? pins.size() : 0;

    // Nets
    this.nets.totalCount = board.rules != null && board.rules.nets != null ? board.rules.nets.max_net_no() : 0;
    this.nets.classCount = board.rules != null && board.rules.net_classes != null ? board.rules.net_classes.count() : 0;

    // Traces
    Collection<Trace> tracesCollection = board.get_traces();
    this.traces.totalCount = tracesCollection != null ? tracesCollection.size() : 0;
    this.traces.totalLength = 0.0f;
    if (tracesCollection != null) {
      for (Trace t : tracesCollection) {
        this.traces.totalLength += (float) t.get_length();
      }
    }

    double boardUnitToMmFactor = 1.0;
    if (board.communication != null && board.communication.unit != null) {
      boardUnitToMmFactor =
          Unit.scale(1.0, board.communication.unit, Unit.MM)
              / (board.communication.resolution > 0 ? board.communication.resolution : 1);
    }
    this.traces.totalLengthMm = (float) (this.traces.totalLength * boardUnitToMmFactor);
    this.traces.averageLength = this.traces.totalCount > 0 ? this.traces.totalLength / this.traces.totalCount : 0.0f;

    this.traces.totalSegmentCount = 0;
    this.traces.totalHorizontalLength = 0.0f;
    this.traces.totalVerticalLength = 0.0f;
    this.traces.totalAngledLength = 0.0f;

    if (tracesCollection != null) {
      for (Trace trace : tracesCollection) {
        if (trace instanceof PolylineTrace polylineTrace) {
          Polyline polyline = polylineTrace.polyline();
          if (polyline != null) {
            int cornerCount = polyline.corner_count();
            if (cornerCount > 1) {
              this.traces.totalSegmentCount += cornerCount - 1;
            }
            if (polyline.arr != null) {
              for (Line line : polyline.arr) {
                if (line != null && line.a != null && line.b != null) {
                  FloatPoint a = line.a.to_float();
                  FloatPoint b = line.b.to_float();
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
          }
        }
      }
    }

    this.traces.totalWeightedLength = 0.0f;
    int defaultClearanceClass = BoardRules.default_clearance_class();
    if (board.item_list != null) {
      Iterator<UndoableObjects.UndoableObjectNode> it2 = board.item_list.start_read_object();
      for (;;) {
        Item currentItem = (Item) board.item_list.read_object(it2);
        if (currentItem == null) {
          break;
        }
        if (currentItem instanceof Trace currentTrace) {
          FixedState fixedState = currentTrace.get_fixed_state();
          if (fixedState == FixedState.UNFIXED || fixedState == FixedState.SHOVE_FIXED) {
            double weightedTraceLength =
                currentTrace.get_length()
                    * (currentTrace.get_half_width()
                        + board.clearance_value(
                            currentTrace.clearance_class_no(),
                            defaultClearanceClass,
                            currentTrace.get_layer()));
            if (fixedState == FixedState.SHOVE_FIXED) {
              weightedTraceLength /= 2;
            }
            this.traces.totalWeightedLength += (float) weightedTraceLength;
          }
        }
      }
    }

    // Connections (Incompletes via RatsNest)
    if (includeConnections) {
      RatsNest ratsNest = new RatsNest(board, Locale.ENGLISH);
      this.connections.incompleteCount = ratsNest.incomplete_count();
      // Calculate max connections
      int maxConn = 0;
      if (pins != null) {
        maxConn = Math.max(0, pins.size() - this.nets.totalCount);
      }
      this.connections.maximumCount = Math.max(maxConn, this.connections.incompleteCount);
    }

    // Bends
    this.bends.totalCount = 0;
    this.bends.ninetyDegreeCount = 0;
    this.bends.fortyFiveDegreeCount = 0;
    this.bends.otherAngleCount = 0;

    if (tracesCollection != null) {
      for (Trace trace : tracesCollection) {
        if (trace instanceof PolylineTrace polylineTrace) {
          Polyline polyline = polylineTrace.polyline();
          if (polyline != null) {
            int cornerCount = polyline.corner_count();
            if (cornerCount >= 3) {
              int bendsInTrace = cornerCount - 2;
              this.bends.totalCount += bendsInTrace;
              for (int i = 1; i < cornerCount - 1; i++) {
                FloatPoint prev = polyline.corner_approx(i - 1);
                FloatPoint current = polyline.corner_approx(i);
                FloatPoint next = polyline.corner_approx(i + 1);

                double dx1 = current.x - prev.x;
                double dy1 = current.y - prev.y;
                double dx2 = next.x - current.x;
                double dy2 = next.y - current.y;

                double angle = Math.abs(Math.toDegrees(Math.atan2(dy2, dx2) - Math.atan2(dy1, dx1)));
                angle = Math.min(angle, 360 - angle);
                angle = angle > 180 ? 360 - angle : angle;

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
      }
    }

    // Vias
    Collection<Via> viasCollection = board.get_vias();
    this.vias.totalCount = viasCollection != null ? viasCollection.size() : 0;
    this.vias.throughHoleCount = 0;
    this.vias.blindCount = 0;
    this.vias.buriedCount = 0;
    if (viasCollection != null) {
      for (Via via : viasCollection) {
        if ((via.first_layer() == 0) && (via.last_layer() == this.layers.totalCount - 1)) {
          this.vias.throughHoleCount++;
        } else if ((via.first_layer() == 0) || (via.last_layer() == this.layers.totalCount - 1)) {
          this.vias.blindCount++;
        } else {
          this.vias.buriedCount++;
        }
      }
    }

    // Clearance violations
    if (includeClearanceViolations) {
      ClearanceViolations cvList = new ClearanceViolations(board.get_items());
      // Dedup pair count
      this.clearanceViolations.totalCount = (cvList.list.size() + 1) / 2;
      if (!cvList.list.isEmpty()) {
        double minViolation = Double.MAX_VALUE;
        double maxViolation = 0.0;
        double sumViolation = 0.0;
        Set<String> processedPairs = new HashSet<>();

        for (ClearanceViolation cv : cvList.list) {
          String key = cv.first_item.get_id_no() + "-" + cv.second_item.get_id_no();
          String reverseKey = cv.second_item.get_id_no() + "-" + cv.first_item.get_id_no();
          if (processedPairs.contains(reverseKey)) {
            continue;
          }
          processedPairs.add(key);

          double shortfall = Math.max(0.0, cv.expected_clearance - cv.actual_clearance);
          double shortfallMm = shortfall * boardUnitToMmFactor;
          minViolation = Math.min(minViolation, shortfallMm);
          maxViolation = Math.max(maxViolation, shortfallMm);
          sumViolation += shortfallMm;
        }

        int uniqueCount = processedPairs.size();
        this.clearanceViolations.minViolationMm = uniqueCount > 0 ? minViolation : 0.0;
        this.clearanceViolations.maxViolationMm = maxViolation;
        this.clearanceViolations.avgViolationMm = uniqueCount > 0 ? sumViolation / uniqueCount : 0.0;
      } else {
        this.clearanceViolations.minViolationMm = 0.0;
        this.clearanceViolations.maxViolationMm = 0.0;
        this.clearanceViolations.avgViolationMm = 0.0;
      }
    }

    // Fanout
    Collection<Pin> smdPins = board.get_smd_pins();
    this.fanout.totalSmdPins = smdPins != null ? smdPins.size() : 0;
    this.fanout.pinsToEscape = this.fanout.totalSmdPins;
    this.fanout.escapedCount = 0;
    if (smdPins != null) {
      for (Pin p : smdPins) {
        if (isPinEscaped(p)) {
          this.fanout.escapedCount++;
        }
      }
    }
  }

  public static boolean isPinEscaped(Pin pin) {
    if (pin == null) {
      return false;
    }
    Set<Item> contacts = pin.get_normal_contacts();
    if (contacts == null) {
      return false;
    }
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        if (trace.clearance_violations().isEmpty()) {
          return true;
        }
      } else if (contact instanceof Via via) {
        if (via.clearance_violations().isEmpty()) {
          Set<Item> viaContacts = via.get_normal_contacts();
          if (viaContacts != null) {
            for (Item viaContact : viaContacts) {
              if (viaContact instanceof Trace || viaContact instanceof ConductionArea) {
                return true;
              }
            }
          }
        }
      } else if (contact instanceof ConductionArea) {
        return true;
      }
    }
    return false;
  }

  /** Calculates normalized quality score (0 - 1000). */
  public float calculateNormalizedScore() {
    float maxConnections = this.connections.maximumCount != null && this.connections.maximumCount > 0
        ? this.connections.maximumCount
        : 1.0f;
    float unroutedPenalty = 1_000_000.0f;
    float clearancePenalty = 1_000.0f;
    float bendPenalty = 100.0f;
    float traceCost = 1.0f;
    float viaCost = 50.0f;

    float maxScore = maxConnections * unroutedPenalty;
    float penalties =
        (this.connections.incompleteCount != null ? this.connections.incompleteCount : 0) * unroutedPenalty
            + (this.clearanceViolations.totalCount != null ? this.clearanceViolations.totalCount : 0) * clearancePenalty
            + (this.bends.totalCount != null ? this.bends.totalCount : 0) * bendPenalty;

    float traceLengthForCost = this.traces.totalLengthMm != null ? this.traces.totalLengthMm : this.traces.totalLength;
    float costs = (traceLengthForCost * traceCost) + ((this.vias.totalCount != null ? this.vias.totalCount : 0) * viaCost);

    float rawScore = maxScore - penalties - costs;
    if (maxScore <= 0f) {
      return 0.0f;
    }
    return Math.max(0.0f, (rawScore / maxScore) * 1000.0f);
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  public static class BoardStatisticsFanout implements Serializable {
    @SerializedName("total_smd_pins")
    public int totalSmdPins;

    @SerializedName("pins_to_escape")
    public int pinsToEscape;

    @SerializedName("escaped_count")
    public int escapedCount;
  }
}
