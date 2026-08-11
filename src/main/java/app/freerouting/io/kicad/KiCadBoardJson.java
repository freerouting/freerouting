package app.freerouting.io.kicad;

import java.util.ArrayList;
import java.util.List;

/** Data Transfer Object for KiCad board data serialized as JSON. */
public class KiCadBoardJson {
  public String designName;
  public UnitJson unit = UnitJson.MM; // Default unit.
  public double resolution = 1.0; // Default resolution factor.

  public List<LayerJson> layers = new ArrayList<>();
  public List<NetClassJson> netClasses = new ArrayList<>();
  public List<NetJson> nets = new ArrayList<>();
  public List<CustomClearanceRuleJson> clearanceRules = new ArrayList<>();
  public List<ComponentJson> components = new ArrayList<>();
  public OutlineJson outline = new OutlineJson();

  public List<TraceJson> traces = new ArrayList<>();
  public List<ViaJson> vias = new ArrayList<>();
  public List<ConductionAreaJson> conductionAreas = new ArrayList<>();

  /** Coordinate units used by the JSON representation. */
  public enum UnitJson {
    MM,
    MIL,
    UM
  }

  /** Layer definition in the JSON representation. */
  public static class LayerJson {
    public int index;
    public String name;
    public String type; // e.g. "signal" or "plane".
  }

  /** Net-class definition in the JSON representation. */
  public static class NetClassJson {
    public String name;
    public double clearance;
    public double traceWidth;
    public double viaDiameter;
    public double viaDrill;
    public List<String> netNames = new ArrayList<>();
  }

  /** Net definition in the JSON representation. */
  public static class NetJson {
    public int id;
    public String name;
    public String className;
    public boolean containsPlane;
  }

  /** Custom clearance rule in the JSON representation. */
  public static class CustomClearanceRuleJson {
    public String classA;
    public String classB;
    public double clearance;
  }

  /** Component definition in the JSON representation. */
  public static class ComponentJson {
    public String reference; // e.g. "U1".
    public String value; // e.g. "STM32F405".
    public String footprint; // e.g. "Package_QFP:LQFP-64".
    public Point2D position = new Point2D();
    public double rotation; // In degrees.
    public String layer; // "F.Cu" or "B.Cu".
    public List<PadJson> pads = new ArrayList<>();
  }

  /** Pad definition in the JSON representation. */
  public static class PadJson {
    public String name; // e.g. "1".
    public String netName; // e.g. "GND".
    public String shape; // e.g. "rect", "circle", or "oval".
    public Point2D size = new Point2D();
    public Point2D offset = new Point2D(); // Relative offset from component origin.
    public Point2D position; // Absolute position on the board.
    public double drill;
    public List<String> layers = new ArrayList<>(); // Layers this pad exists on.
  }

  /** Board-outline definition in the JSON representation. */
  public static class OutlineJson {
    public List<Point2D> corners = new ArrayList<>();
    public double clearance; // Outline/edge clearance class mapping
  }

  /** Trace definition in the JSON representation. */
  public static class TraceJson {
    public int id;
    public String netName;
    public double width;
    public int layerIndex;
    public List<Point2D> points = new ArrayList<>();
  }

  /** Via definition in the JSON representation. */
  public static class ViaJson {
    public int id;
    public String netName;
    public Point2D position = new Point2D();
    public double diameter;
    public double drill;
    public int startLayerIndex;
    public int endLayerIndex;
  }

  /** Conduction-area definition in the JSON representation. */
  public static class ConductionAreaJson {
    public int id;
    public String netName;
    public int layerIndex;
    public boolean isObstacle;
    public List<Point2D> polygon = new ArrayList<>();
  }

  /** Two-dimensional point in the JSON representation. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public static class Point2D {
    public double x;
    public double y;

    /** Creates a point at the origin. */
    public Point2D() {}

    /**
     * Creates a point with the supplied coordinates.
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     */
    public Point2D(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }
}
