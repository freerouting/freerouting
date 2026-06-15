package app.freerouting.io.kicad;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardObserverAdaptor;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.Component;
import app.freerouting.board.FixedState;
import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.Package;
import app.freerouting.core.Packages;
import app.freerouting.core.Padstack;
import app.freerouting.core.Padstacks;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.BoardMetadata;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.gson.GsonProvider;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a KiCad board representation from JSON and constructs a fully populated {@link RoutingBoard}.
 */
public final class KiCadJsonReader {

  private KiCadJsonReader() {
  }

  /**
   * Reads a KiCad JSON structure and returns a fully constructed RoutingBoard wrapped in BoardReadResult.
   */
  public static BoardReadResult readBoard(
      Reader reader,
      BoardObservers observers,
      IdentificationNumberGenerator idGenerator) {

    long startTime = System.nanoTime();

    if (reader == null) {
      return new BoardReadResult.ParseError("json_root", "Reader must not be null");
    }

    if (observers == null) {
      observers = new BoardObserverAdaptor();
    }
    if (idGenerator == null) {
      idGenerator = new ItemIdentificationNumberGenerator();
    }

    try {
      // 1. Deserialize JSON
      KiCadBoardJson boardJson = GsonProvider.GSON.fromJson(reader, KiCadBoardJson.class);
      if (boardJson == null) {
        return new BoardReadResult.ParseError("json_root", "JSON payload is empty or invalid");
      }

      long parseEndTime = System.nanoTime();
      double parseMs = (parseEndTime - startTime) / 1_000_000.0;
      FRLogger.debug(String.format("KiCad JSON Deserialization performance: parsed in %.2f ms", parseMs));

      // 2. Set up units and scaling
      Unit userUnit = Unit.MM;
      if (boardJson.unit == KiCadBoardJson.UnitJson.MIL) {
        userUnit = Unit.MIL;
      } else if (boardJson.unit == KiCadBoardJson.UnitJson.UM) {
        userUnit = Unit.UM;
      }

      // We maintain similar resolution scaling factors as DSN mapping (e.g. 1000 for mm)
      int resolution = (int) Math.max(1.0, boardJson.resolution);
      if (boardJson.resolution == 1.0 && userUnit == Unit.MM) {
        resolution = 10000; // 0.1 micrometer resolution is default for mm if unspecified
      }

      double scaleFactor = resolution;
      CoordinateTransform coordinateTransform = new CoordinateTransform(scaleFactor, 0, 0);

      // 3. Layer Structure
      int layerCount = boardJson.layers.isEmpty() ? 2 : boardJson.layers.size();
      Layer[] boardLayers = new Layer[layerCount];
      if (boardJson.layers.isEmpty()) {
        boardLayers[0] = new Layer("F.Cu", true);
        boardLayers[1] = new Layer("B.Cu", true);
      } else {
        for (int i = 0; i < layerCount; i++) {
          KiCadBoardJson.LayerJson layerJson = boardJson.layers.get(i);
          boolean isSignal = !"plane".equalsIgnoreCase(layerJson.type);
          boardLayers[i] = new Layer(layerJson.name, isSignal);
        }
      }
      LayerStructure layerStructure = new LayerStructure(boardLayers);
      app.freerouting.io.specctra.parser.LayerStructure specctraLayerStructure =
          new app.freerouting.io.specctra.parser.LayerStructure(layerStructure); // dummy or map layer names if needed

      // 4. Clearance Matrix
      int clearanceClassCount = Math.max(2, boardJson.netClasses.size() + 2);
      String[] clearanceClassNames = new String[clearanceClassCount];
      clearanceClassNames[0] = "null";
      clearanceClassNames[1] = "default";
      for (int i = 0; i < boardJson.netClasses.size(); i++) {
        clearanceClassNames[i + 2] = boardJson.netClasses.get(i).name;
      }

      ClearanceMatrix clearanceMatrix = new ClearanceMatrix(clearanceClassCount, layerStructure, clearanceClassNames);
      int defaultClearance = (int) Math.round(Unit.scale(0.2, Unit.MM, userUnit) * scaleFactor); // fallback 0.2mm
      clearanceMatrix.set_default_value(defaultClearance);

      // Populate clearance matrix from NetClasses and Custom Clearance Rules
      for (int i = 0; i < boardJson.netClasses.size(); i++) {
        KiCadBoardJson.NetClassJson nc = boardJson.netClasses.get(i);
        int clNo = i + 2;
        int clVal = (int) Math.round(nc.clearance * scaleFactor);
        clearanceMatrix.set_value(clNo, clNo, clVal);
        clearanceMatrix.set_value(1, clNo, clVal); // spacing between default and class
      }

      for (KiCadBoardJson.CustomClearanceRuleJson rule : boardJson.clearanceRules) {
        int idxA = clearanceMatrix.get_no(rule.classA);
        int idxB = clearanceMatrix.get_no(rule.classB);
        if (idxA >= 0 && idxB >= 0) {
          int clearanceVal = (int) Math.round(rule.clearance * scaleFactor);
          clearanceMatrix.set_value(idxA, idxB, clearanceVal);
        }
      }

      BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);

      // 5. Board Outline / Boundary Shape Creation
      PointOutline boundingBoxOutline = new PointOutline();
      List<PolylineShape> outlineShapes = new ArrayList<>();
      if (boardJson.outline == null || boardJson.outline.corners.isEmpty()) {
        // Create an empty bounding box fallback
        boundingBoxOutline.add_point(new FloatPoint(0, 0));
        boundingBoxOutline.add_point(new FloatPoint(0, 1000));
        boundingBoxOutline.add_point(new FloatPoint(1000, 1000));
        boundingBoxOutline.add_point(new FloatPoint(1000, 0));
      } else {
        Point[] points = new Point[boardJson.outline.corners.size()];
        for (int i = 0; i < boardJson.outline.corners.size(); i++) {
          KiCadBoardJson.Point2D pt = boardJson.outline.corners.get(i);
          points[i] = new IntPoint((int) Math.round(pt.x * scaleFactor), (int) Math.round(-pt.y * scaleFactor));
          boundingBoxOutline.add_point(points[i].to_float());
        }
        outlineShapes.add(new PolygonShape(points));
      }

      IntBox boundingBox = boundingBoxOutline.bounding_box();
      int outlineClearanceNo = 1; // Default clearance class

      // 6. Communication object setup
      Communication.SpecctraParserInfo parserInfo = new Communication.SpecctraParserInfo(
          "\"", "KiCad", "v10.0", null, null, false);
      Communication communication = new Communication(userUnit, resolution, parserInfo, coordinateTransform, idGenerator, observers);

      // 7. Construct RoutingBoard
      RoutingBoard board = new RoutingBoard(
          boundingBox,
          layerStructure,
          outlineShapes.toArray(new PolylineShape[0]),
          outlineClearanceNo,
          boardRules,
          communication
      );

      board.library.padstacks = new Padstacks(layerStructure);
      board.library.packages = new Packages(board.library.padstacks);

      // 8. Populate Net Classes & Netlist in Rules (now that board is fully linked)
      boardRules.create_default_net_class();
      Map<String, Integer> netClassIndexMap = new HashMap<>();
      for (int i = 0; i < boardJson.netClasses.size(); i++) {
        KiCadBoardJson.NetClassJson nc = boardJson.netClasses.get(i);
        int clNo = i + 2;
        NetClass boardNetClass = boardRules.net_classes.append(nc.name, layerStructure, clearanceMatrix, false);
        int traceHalfWidth = (int) Math.round(nc.traceWidth * scaleFactor / 2.0);
        for (int l = 0; l < layerCount; l++) {
          boardNetClass.set_trace_half_width(l, traceHalfWidth);
        }
        boardNetClass.set_trace_clearance_class(clNo);
        netClassIndexMap.put(nc.name, clNo);
      }

      // Create via rules and register via padstacks so that the router is allowed to use vias
      double defaultViaDiameter = 0.8;
      double defaultViaDrill = 0.4;
      if (userUnit == Unit.MIL) {
        defaultViaDiameter = 30.0;
        defaultViaDrill = 15.0;
      } else if (userUnit == Unit.UM) {
        defaultViaDiameter = 800.0;
        defaultViaDrill = 400.0;
      }

      NetClass defaultNetClass = boardRules.get_default_net_class();
      double defViaDia = defaultViaDiameter;
      double defViaDrill = defaultViaDrill;
      for (KiCadBoardJson.NetClassJson nc : boardJson.netClasses) {
        if ("default".equalsIgnoreCase(nc.name)) {
          if (nc.viaDiameter > 0) {
            defViaDia = nc.viaDiameter;
          }
          if (nc.viaDrill > 0) {
            defViaDrill = nc.viaDrill;
          }
          break;
        }
      }

      ConvexShape[] defViaShapeArr = new ConvexShape[layerCount];
      double defRadius = defViaDia * scaleFactor / 2.0;
      ConvexShape defViaShape = new IntBox(
          (int) Math.round(-defRadius),
          (int) Math.round(-defRadius),
          (int) Math.round(defRadius),
          (int) Math.round(defRadius)
      ).to_Simplex();
      for (int li = 0; li < layerCount; li++) {
        defViaShapeArr[li] = defViaShape;
      }
      Padstack defaultViaPadstack = board.library.padstacks.add("default_via", defViaShapeArr, true, false);
      board.library.add_via_padstack(defaultViaPadstack);

      int defaultViaClClass = defaultNetClass.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA);
      ViaInfo defaultViaInfo = new ViaInfo("default_via", defaultViaPadstack, defaultViaClClass, true, boardRules);
      boardRules.via_infos.add(defaultViaInfo);

      ViaRule defaultViaRule = new ViaRule("default");
      defaultViaRule.append_via(defaultViaInfo);
      boardRules.via_rules.add(defaultViaRule);
      defaultNetClass.set_via_rule(defaultViaRule);

      for (int i = 0; i < boardJson.netClasses.size(); i++) {
        KiCadBoardJson.NetClassJson nc = boardJson.netClasses.get(i);
        if ("default".equalsIgnoreCase(nc.name)) {
          continue;
        }
        int clNo = i + 2;
        NetClass boardNetClass = boardRules.net_classes.get(clNo - 1);

        double viaDia = nc.viaDiameter > 0 ? nc.viaDiameter : defViaDia;
        double viaDrill = nc.viaDrill > 0 ? nc.viaDrill : defViaDrill;

        ConvexShape[] viaShapeArr = new ConvexShape[layerCount];
        double radius = viaDia * scaleFactor / 2.0;
        ConvexShape viaShape = new IntBox(
            (int) Math.round(-radius),
            (int) Math.round(-radius),
            (int) Math.round(radius),
            (int) Math.round(radius)
        ).to_Simplex();
        for (int li = 0; li < layerCount; li++) {
          viaShapeArr[li] = viaShape;
        }

        String viaName = "via_" + nc.name;
        Padstack viaPadstack = board.library.padstacks.add(viaName, viaShapeArr, true, false);
        board.library.add_via_padstack(viaPadstack);

        int viaClClass = boardNetClass.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA);
        ViaInfo viaInfo = new ViaInfo(viaName, viaPadstack, viaClClass, true, boardRules);
        boardRules.via_infos.add(viaInfo);

        ViaRule viaRule = new ViaRule(nc.name);
        viaRule.append_via(viaInfo);
        boardRules.via_rules.add(viaRule);
        boardNetClass.set_via_rule(viaRule);
      }

      // Add actual nets
      int maxNetNo = boardJson.nets.size();
      for (KiCadBoardJson.NetJson nj : boardJson.nets) {
        Net boardNet = boardRules.nets.add(nj.name, 1, nj.containsPlane);
        int clNo = netClassIndexMap.getOrDefault(nj.className, 1);
        boardNet.set_class(boardRules.net_classes.get(clNo - 1)); // NetClass array indices are 0-based
      }

      // Automatically register any referenced nets that were not explicitly defined in the nets list
      java.util.Set<String> referencedNets = new java.util.HashSet<>();
      if (boardJson.components != null) {
        for (KiCadBoardJson.ComponentJson comp : boardJson.components) {
          if (comp.pads != null) {
            for (KiCadBoardJson.PadJson pad : comp.pads) {
              if (pad.netName != null && !pad.netName.isEmpty()) {
                referencedNets.add(pad.netName);
              }
            }
          }
        }
      }
      if (boardJson.conductionAreas != null) {
        for (KiCadBoardJson.ConductionAreaJson zone : boardJson.conductionAreas) {
          if (zone.netName != null && !zone.netName.isEmpty()) {
            referencedNets.add(zone.netName);
          }
        }
      }
      if (boardJson.traces != null) {
        for (KiCadBoardJson.TraceJson tr : boardJson.traces) {
          if (tr.netName != null && !tr.netName.isEmpty()) {
            referencedNets.add(tr.netName);
          }
        }
      }
      if (boardJson.vias != null) {
        for (KiCadBoardJson.ViaJson vj : boardJson.vias) {
          if (vj.netName != null && !vj.netName.isEmpty()) {
            referencedNets.add(vj.netName);
          }
        }
      }

      for (String netName : referencedNets) {
        if (boardRules.nets.get(netName, 1) == null) {
          Net boardNet = boardRules.nets.add(netName, 1, false);
          // Fallback to default class (default net class is at index 0)
          boardNet.set_class(boardRules.net_classes.get(0));
        }
      }

      // 9. Load Components & Library templates
      Padstacks padstacks = board.library.padstacks;
      Packages packages = board.library.packages;

      // Populate footprint templates
      for (KiCadBoardJson.ComponentJson comp : boardJson.components) {
        // Create padstacks and pins for the component package
        List<Package.Pin> packagePins = new ArrayList<>();
        for (KiCadBoardJson.PadJson pad : comp.pads) {
          // Define a default pad shape
          ConvexShape[] shapeArr = new ConvexShape[layerCount];
          double dx = pad.size.x * scaleFactor / 2.0;
          double dy = pad.size.y * scaleFactor / 2.0;
          ConvexShape padShape;
          if ("circle".equalsIgnoreCase(pad.shape)) {
            double radius = Math.min(pad.size.x, pad.size.y) * scaleFactor / 2.0;
            padShape = new Circle(IntPoint.ZERO, (int) Math.round(radius));
          } else if ("oval".equalsIgnoreCase(pad.shape)) {
            int lx = (int) Math.round(-dx);
            int rx = (int) Math.round(dx);
            int ly = (int) Math.round(-dy);
            int uy = (int) Math.round(dy);
            int r = (int) Math.round(Math.min(dx, dy));
            int cut = (int) Math.round((2.0 - Math.sqrt(2.0)) * r);
            IntOctagon oct = new IntOctagon(
                lx, ly, rx, uy,
                lx - uy + cut,
                rx - ly - cut,
                lx + ly + cut,
                rx + uy - cut
            );
            padShape = oct.to_Simplex();
          } else {
            padShape = new IntBox(
                (int) Math.round(-dx),
                (int) Math.round(-dy),
                (int) Math.round(dx),
                (int) Math.round(dy)
            ).to_Simplex();
          }

          // Standardize pad's layer mappings
          int startLayer = 0;
          int endLayer = layerCount - 1;
          if (pad.layers != null && !pad.layers.isEmpty()) {
            // Find active layers by name matching
            int lowestIdx = layerCount - 1;
            int highestIdx = 0;
            for (String lName : pad.layers) {
              for (int li = 0; li < layerCount; li++) {
                if (boardLayers[li].name.equalsIgnoreCase(lName)) {
                  lowestIdx = Math.min(lowestIdx, li);
                  highestIdx = Math.max(highestIdx, li);
                }
              }
            }
            startLayer = lowestIdx;
            endLayer = highestIdx;
          }

          for (int li = startLayer; li <= endLayer; li++) {
            shapeArr[li] = padShape;
          }

          boolean isDrillable = pad.drill > 0.0;
          String padstackName = "padstack_" + (padstacks.count() + 1);
          Padstack padstack = padstacks.add(padstackName, shapeArr, isDrillable, false);
          IntVector relativeLoc = new IntVector(
              (int) Math.round(pad.offset.x * scaleFactor),
              (int) Math.round(-pad.offset.y * scaleFactor)
          );
          packagePins.add(new Package.Pin(pad.name, padstack.no, relativeLoc, 0.0));
        }

        Package componentPackage = packages.add(packagePins.toArray(new Package.Pin[0]));
        IntPoint position = new IntPoint(
            (int) Math.round(comp.position.x * scaleFactor),
            (int) Math.round(-comp.position.y * scaleFactor)
        );

        boolean isFront = !"B.Cu".equalsIgnoreCase(comp.layer);
        Component boardComp = board.components.add(
            comp.reference,
            position,
            -comp.rotation,
            isFront,
            componentPackage,
            componentPackage,
            true,
            comp.value
        );

        // Insert actual pin items mapped to nets
        for (int pIdx = 0; pIdx < comp.pads.size(); pIdx++) {
          KiCadBoardJson.PadJson pad = comp.pads.get(pIdx);
          Net targetNet = boardRules.nets.get(pad.netName, 1);
          int netNo = targetNet != null ? targetNet.net_number : 0;
          int[] netNoArr = netNo > 0 ? new int[]{netNo} : new int[0];
          board.insert_pin(boardComp.no, pIdx, netNoArr, outlineClearanceNo, FixedState.SYSTEM_FIXED);
        }
      }

      // 10. Load conduction areas (copper pours)
      for (KiCadBoardJson.ConductionAreaJson zone : boardJson.conductionAreas) {
        Net targetNet = boardRules.nets.get(zone.netName, 1);
        int netNo = targetNet != null ? targetNet.net_number : 0;
        int[] netNoArr = netNo > 0 ? new int[]{netNo} : new int[0];

        // Build Area path polygon
        Point[] zonePoints = new Point[zone.polygon.size()];
        for (int i = 0; i < zone.polygon.size(); i++) {
          KiCadBoardJson.Point2D pt = zone.polygon.get(i);
          zonePoints[i] = new IntPoint((int) Math.round(pt.x * scaleFactor), (int) Math.round(-pt.y * scaleFactor));
        }
        Area zoneArea = new PolygonShape(zonePoints);
        board.insert_conduction_area(zoneArea, zone.layerIndex, netNoArr, 1, zone.isObstacle, FixedState.USER_FIXED);
      }

      // 11. Load traces and vias (existing wiring)
      for (KiCadBoardJson.TraceJson tr : boardJson.traces) {
        Net targetNet = boardRules.nets.get(tr.netName, 1);
        int netNo = targetNet != null ? targetNet.net_number : 0;
        int[] netNoArr = netNo > 0 ? new int[]{netNo} : new int[0];
        int traceHalfWidth = (int) Math.round(tr.width * scaleFactor / 2.0);

        Point[] points = new Point[tr.points.size()];
        for (int i = 0; i < tr.points.size(); i++) {
          KiCadBoardJson.Point2D pt = tr.points.get(i);
          points[i] = new IntPoint((int) Math.round(pt.x * scaleFactor), (int) Math.round(-pt.y * scaleFactor));
        }
        board.insert_trace(points, tr.layerIndex, traceHalfWidth, netNoArr, 1, FixedState.USER_FIXED);
      }

      for (KiCadBoardJson.ViaJson vj : boardJson.vias) {
        Net targetNet = boardRules.nets.get(vj.netName, 1);
        int netNo = targetNet != null ? targetNet.net_number : 0;
        int[] netNoArr = netNo > 0 ? new int[]{netNo} : new int[0];

        IntPoint center = new IntPoint((int) Math.round(vj.position.x * scaleFactor), (int) Math.round(-vj.position.y * scaleFactor));

        // Dynamically create via padstack
        ConvexShape[] shapeArr = new ConvexShape[layerCount];
        double radius = vj.diameter * scaleFactor / 2.0;
        ConvexShape viaShape = new IntBox(
            (int) Math.round(-radius),
            (int) Math.round(-radius),
            (int) Math.round(radius),
            (int) Math.round(radius)
        ).to_Simplex();

        for (int li = vj.startLayerIndex; li <= vj.endLayerIndex; li++) {
          shapeArr[li] = viaShape;
        }
        Padstack viaPadstack = padstacks.add(shapeArr);
        board.insert_via(viaPadstack, center, netNoArr, 1, FixedState.USER_FIXED, true);
      }

      long endTime = System.nanoTime();
      double durationMs = (endTime - startTime) / 1_000_000.0;
      FRLogger.debug(String.format("KiCad JSON Loader performance: built RoutingBoard in %.2f ms (parsing + structure mapping)", durationMs));

      // Build metadata for the board
      BoardMetadata metadata = new BoardMetadata(
          "KiCad",
          "v10.0",
          layerCount,
          userUnit,
          resolution,
          AngleRestriction.FORTYFIVE_DEGREE,
          null
      );

      return new BoardReadResult.Success(board, metadata, new ArrayList<>());

    } catch (Throwable e) {
      FRLogger.warn("Failed to parse and read KiCad JSON board: " + e.getMessage());
      return new BoardReadResult.ParseError("json_payload", "Exception occurred: " + e.getMessage());
    }
  }

  /**
   * Helper class to trace bounding outer box.
   */
  private static class PointOutline {
    private final List<FloatPoint> points = new ArrayList<>();

    public void add_point(FloatPoint pt) {
      points.add(pt);
    }

    public IntBox bounding_box() {
      if (points.isEmpty()) {
        return IntBox.EMPTY;
      }
      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      for (FloatPoint pt : points) {
        minX = Math.min(minX, pt.x);
        minY = Math.min(minY, pt.y);
        maxX = Math.max(maxX, pt.x);
        maxY = Math.max(maxY, pt.y);
      }
      return new IntBox(
          (int) Math.round(minX),
          (int) Math.round(minY),
          (int) Math.round(maxX),
          (int) Math.round(maxY)
      );
    }
  }
}
