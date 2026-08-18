package app.freerouting.io.kicad;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.util.gson.GsonProvider;

/** Serializes a {@link RoutingBoard} back to KiCad JSON representation. */
public final class KiCadJsonWriter {

  private KiCadJsonWriter() {}

  /** Serializes the board and returns it as a JSON string. */
  public static String write(RoutingBoard board) {
    return write(board, "KiCad_Design");
  }

  /** Serializes the board and returns it as a JSON string with a specific design name. */
  public static String write(RoutingBoard board, String designName) {
    double scaleFactor = 10000.0; // default mapping factor for millimeters
    if (board.communication != null) {
      if (board.communication.unit == Unit.MIL) {
        scaleFactor = 254.0;
      } else if (board.communication.unit == Unit.UM) {
        scaleFactor = 10.0;
      } else {
        scaleFactor = 10000.0;
      }
    }

    KiCadBoardJson boardJson = new KiCadBoardJson();
    boardJson.designName = designName != null ? designName : "KiCad_Design";
    if (board.communication != null) {
      boardJson.resolution = scaleFactor;
      if (board.communication.unit == Unit.MIL) {
        boardJson.unit = KiCadBoardJson.UnitJson.MIL;
      } else if (board.communication.unit == Unit.UM) {
        boardJson.unit = KiCadBoardJson.UnitJson.UM;
      } else {
        boardJson.unit = KiCadBoardJson.UnitJson.MM;
      }
    } else {
      boardJson.resolution = 10000.0;
      boardJson.unit = KiCadBoardJson.UnitJson.MM;
    }

    // 1. Layers
    for (int i = 0; i < board.getLayerCount(); i++) {
      app.freerouting.board.model.structure.Layer layer = board.layerStructure.layers[i];
      KiCadBoardJson.LayerJson layerJson = new KiCadBoardJson.LayerJson();
      layerJson.index = i;
      layerJson.name = layer.name;
      layerJson.type = layer.isSignal ? "signal" : "plane";
      boardJson.layers.add(layerJson);
    }

    // 2. Nets
    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      Net net = board.rules.nets.get(i);
      if (net == null) {
        continue;
      }
      KiCadBoardJson.NetJson netJson = new KiCadBoardJson.NetJson();
      netJson.id = net.netNumber;
      netJson.name = net.name;
      netJson.className = net.getNetClass() != null ? net.getNetClass().getName() : "default";
      netJson.containsPlane = net.containsPlane();
      boardJson.nets.add(netJson);
    }

    // 3. Net Classes
    for (int i = 0; i < board.rules.netClasses.count(); i++) {
      NetClass netClass = board.rules.netClasses.get(i);
      if (netClass == null) {
        continue;
      }
      KiCadBoardJson.NetClassJson ncJson = new KiCadBoardJson.NetClassJson();
      ncJson.name = netClass.getName();
      int clearanceClassIndex = netClass.getTraceClearanceClass();
      ncJson.clearance =
          board.rules.clearanceMatrix.getValue(clearanceClassIndex, clearanceClassIndex, 0, false)
              / scaleFactor;
      ncJson.traceWidth = (2 * netClass.getTraceHalfWidth(0)) / scaleFactor;

      ncJson.viaDiameter = 0.8; // default fallback
      ncJson.viaDrill = 0.4;
      ViaRule viaRule = netClass.getViaRule();
      if (viaRule != null && viaRule.viaCount() > 0) {
        ViaInfo viaInfo = viaRule.getVia(0);
        if (viaInfo != null && viaInfo.getPadstack() != null) {
          Padstack viaPad = viaInfo.getPadstack();
          ConvexShape shape = viaPad.getShape(0);
          if (shape != null) {
            ncJson.viaDiameter = shape.boundingBox().width() / scaleFactor;
          }
        }
      }
      ncJson.viaDrill = ncJson.viaDiameter * 0.5;

      for (int n = 1; n <= board.rules.nets.maxNetNumber(); n++) {
        Net net = board.rules.nets.get(n);
        if (net != null && net.getNetClass() == netClass) {
          ncJson.netNames.add(net.name);
        }
      }
      boardJson.netClasses.add(ncJson);
    }

    // 4. Outline
    BoardOutline outline = board.getOutline();
    if (outline != null) {
      boardJson.outline = new KiCadBoardJson.OutlineJson();
      int clearanceClassIndex = outline.clearanceClassIndex();
      boardJson.outline.clearance =
          board.rules.clearanceMatrix.getValue(clearanceClassIndex, clearanceClassIndex, 0, false)
              / scaleFactor;
      for (int i = 0; i < outline.shapeCount(); i++) {
        PolylineShape polyShape = outline.getShape(i);
        if (polyShape != null) {
          for (Point pt : polyShape.boundedCorners()) {
            boardJson.outline.corners.add(
                new KiCadBoardJson.Point2D(
                    pt.toFloat().x / scaleFactor, -pt.toFloat().y / scaleFactor));
          }
        }
      }
    }

    // 5. Traces
    int traceId = 1;
    for (Trace trace : board.getTraces()) {
      if (trace instanceof PolylineTrace polyTrace) {
        KiCadBoardJson.TraceJson traceJson = new KiCadBoardJson.TraceJson();
        traceJson.id = traceId++;
        traceJson.layerIndex = polyTrace.getLayer();
        traceJson.width = (2 * polyTrace.getHalfWidth()) / scaleFactor;
        if (polyTrace.netCount() > 0) {
          int netNumber = polyTrace.getNetNumber(0);
          Net net = board.rules.nets.get(netNumber);
          if (net != null) {
            traceJson.netName = net.name;
          }
        }
        for (Point pt : polyTrace.polyline().corners()) {
          traceJson.points.add(
              new KiCadBoardJson.Point2D(
                  pt.toFloat().x / scaleFactor, -pt.toFloat().y / scaleFactor));
        }
        boardJson.traces.add(traceJson);
      }
    }

    // 6. Vias
    int viaId = 1;
    for (Via via : board.getVias()) {
      KiCadBoardJson.ViaJson viaJson = new KiCadBoardJson.ViaJson();
      viaJson.id = viaId++;
      if (via.netCount() > 0) {
        int netNumber = via.getNetNumber(0);
        Net net = board.rules.nets.get(netNumber);
        if (net != null) {
          viaJson.netName = net.name;
        }
      }
      Point center = via.getCenter();
      viaJson.position =
          new KiCadBoardJson.Point2D(
              center.toFloat().x / scaleFactor, -center.toFloat().y / scaleFactor);

      Padstack padstack = via.getPadstack();
      int firstLayer = 0;
      while (firstLayer < board.getLayerCount() && padstack.getShape(firstLayer) == null) {
        firstLayer++;
      }
      int lastLayer = board.getLayerCount() - 1;
      while (lastLayer >= 0 && padstack.getShape(lastLayer) == null) {
        lastLayer--;
      }
      viaJson.startLayerIndex = firstLayer;
      viaJson.endLayerIndex = lastLayer;

      app.freerouting.geometry.planar.Shape shape = padstack.getShape(firstLayer);
      if (shape != null) {
        viaJson.diameter = shape.boundingBox().width() / scaleFactor;
      } else {
        viaJson.diameter = 0.8;
      }
      viaJson.drill = viaJson.diameter * 0.5;
      boardJson.vias.add(viaJson);
    }

    // 7. Conduction Areas
    int areaId = 1;
    for (ConductionArea area : board.getConductionAreas()) {
      KiCadBoardJson.ConductionAreaJson areaJson = new KiCadBoardJson.ConductionAreaJson();
      areaJson.id = areaId++;
      if (area.netCount() > 0) {
        int netNumber = area.getNetNumber(0);
        Net net = board.rules.nets.get(netNumber);
        if (net != null) {
          areaJson.netName = net.name;
        }
      }
      areaJson.layerIndex = area.getLayer();
      areaJson.isObstacle = area.getIsObstacle();
      for (FloatPoint pt : area.getArea().cornerApproxArr()) {
        areaJson.polygon.add(new KiCadBoardJson.Point2D(pt.x / scaleFactor, -pt.y / scaleFactor));
      }
      boardJson.conductionAreas.add(areaJson);
    }

    return GsonProvider.GSON.toJson(boardJson);
  }
}
