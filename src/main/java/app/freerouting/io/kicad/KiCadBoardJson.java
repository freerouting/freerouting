package app.freerouting.io.kicad;

import java.util.List;
import java.util.ArrayList;

/**
 * Data Transfer Object (DTO) for KiCad board data serialized as JSON.
 * This class maps all layers, nets, components, pads, traces, vias, and design rules.
 */
public class KiCadBoardJson {
  public String designName;
  public UnitJson unit = UnitJson.MM; // Default unit
  public double resolution = 1.0;     // Default resolution factor (e.g. 10^n to map to internal coords)

  public List<LayerJson> layers = new ArrayList<>();
  public List<NetClassJson> netClasses = new ArrayList<>();
  public List<NetJson> nets = new ArrayList<>();
  public List<CustomClearanceRuleJson> clearanceRules = new ArrayList<>();
  public List<ComponentJson> components = new ArrayList<>();
  public OutlineJson outline = new OutlineJson();

  public List<TraceJson> traces = new ArrayList<>();
  public List<ViaJson> vias = new ArrayList<>();
  public List<ConductionAreaJson> conductionAreas = new ArrayList<>();

  public enum UnitJson {
    MM,
    MIL,
    UM
  }

  public static class LayerJson {
    public int index;
    public String name;
    public String type; // e.g. "signal", "plane"
  }

  public static class NetClassJson {
    public String name;
    public double clearance;
    public double traceWidth;
    public double viaDiameter;
    public double viaDrill;
    public List<String> netNames = new ArrayList<>();
  }

  public static class NetJson {
    public int id;
    public String name;
    public String className;
    public boolean containsPlane;
  }

  public static class CustomClearanceRuleJson {
    public String classA;
    public String classB;
    public double clearance;
  }

  public static class ComponentJson {
    public String reference; // e.g. "U1"
    public String value;     // e.g. "STM32F405"
    public String footprint; // e.g. "Package_QFP:LQFP-64"
    public Point2D position = new Point2D();
    public double rotation;  // in degrees
    public String layer;     // "F.Cu" or "B.Cu"
    public List<PadJson> pads = new ArrayList<>();
  }

  public static class PadJson {
    public String name;    // e.g. "1"
    public String netName; // e.g. "GND"
    public String shape;   // e.g. "rect", "circle", "oval"
    public Point2D size = new Point2D();
    public Point2D offset = new Point2D(); // Relative offset from component origin
    public double drill;
    public List<String> layers = new ArrayList<>(); // Layers this pad exists on
  }

  public static class OutlineJson {
    public List<Point2D> corners = new ArrayList<>();
    public double clearance; // Outline/edge clearance class mapping
  }

  public static class TraceJson {
    public int id;
    public String netName;
    public double width;
    public int layerIndex;
    public List<Point2D> points = new ArrayList<>();
  }

  public static class ViaJson {
    public int id;
    public String netName;
    public Point2D position = new Point2D();
    public double diameter;
    public double drill;
    public int startLayerIndex;
    public int endLayerIndex;
  }

  public static class ConductionAreaJson {
    public int id;
    public String netName;
    public int layerIndex;
    public boolean isObstacle;
    public List<Point2D> polygon = new ArrayList<>();
  }

  public static class Point2D {
    public double x;
    public double y;

    public Point2D() {}

    public Point2D(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }
}