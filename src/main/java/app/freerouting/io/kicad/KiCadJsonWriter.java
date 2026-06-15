package app.freerouting.io.kicad;

import app.freerouting.board.*;
import app.freerouting.core.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.util.gson.GsonProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Serializes a {@link RoutingBoard} back to KiCad JSON representation.
 */
public final class KiCadJsonWriter {

  private KiCadJsonWriter() {
  }

  /**
   * Serializes the board and returns it as a JSON string.
   */
  public static String write(RoutingBoard board) {
    return write(board, "KiCad_Design");
  }

  /**
   * Serializes the board and returns it as a JSON string with a specific design name.
   */
  public static String write(RoutingBoard board, String designName) {
    double scaleFactor = 10000.0; // default mapping factor for millimeters
    if (board.communication != null && board.communication.coordinate_transform != null) {
      scaleFactor = board.communication.coordinate_transform.dsn_to_board(1) / board.communication.resolution;
      if (scaleFactor == 0.0) {
        scaleFactor = 10000.0;
      }
    }

    KiCadBoardJson boardJson = new KiCadBoardJson();
    boardJson.designName = designName != null ? designName : "KiCad_Design";
    if (board.communication != null) {
      boardJson.resolution = board.communication.resolution;
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
    for (int i = 0; i < board.get_layer_count(); i++) {
      app.freerouting.board.Layer layer = board.layer_structure.arr[i];
      KiCadBoardJson.LayerJson layerJson = new KiCadBoardJson.LayerJson();
      layerJson.index = i;
      layerJson.name = layer.name;
      layerJson.type = layer.is_signal ? "signal" : "plane";
      boardJson.layers.add(layerJson);
    }

    // 2. Nets
    for (int i = 1; i <= board.rules.nets.max_net_no(); i++) {
      Net net = board.rules.nets.get(i);
      if (net == null) {
        continue;
      }
      KiCadBoardJson.NetJson netJson = new KiCadBoardJson.NetJson();
      netJson.id = net.net_number;
      netJson.name = net.name;
      netJson.className = net.get_class() != null ? net.get_class().get_name() : "default";
      netJson.containsPlane = net.contains_plane();
      boardJson.nets.add(netJson);
    }

    // 3. Net Classes
    for (int i = 0; i < board.rules.net_classes.count(); i++) {
      NetClass netClass = board.rules.net_classes.get(i);
      if (netClass == null) {
        continue;
      }
      KiCadBoardJson.NetClassJson ncJson = new KiCadBoardJson.NetClassJson();
      ncJson.name = netClass.get_name();
      int clearanceClassNo = netClass.get_trace_clearance_class();
      ncJson.clearance = board.rules.clearance_matrix.get_value(clearanceClassNo, clearanceClassNo, 0, false) / scaleFactor;
      ncJson.traceWidth = (2 * netClass.get_trace_half_width(0)) / scaleFactor;

      ncJson.viaDiameter = 0.8; // default fallback
      ncJson.viaDrill = 0.4;
      ViaRule viaRule = netClass.get_via_rule();
      if (viaRule != null && viaRule.via_count() > 0) {
        ViaInfo viaInfo = viaRule.get_via(0);
        if (viaInfo != null && viaInfo.get_padstack() != null) {
          Padstack viaPad = viaInfo.get_padstack();
          ConvexShape shape = viaPad.get_shape(0);
          if (shape != null) {
            ncJson.viaDiameter = shape.bounding_box().width() / scaleFactor;
          }
        }
      }
      ncJson.viaDrill = ncJson.viaDiameter * 0.5;

      for (int n = 1; n <= board.rules.nets.max_net_no(); n++) {
        Net net = board.rules.nets.get(n);
        if (net != null && net.get_class() == netClass) {
          ncJson.netNames.add(net.name);
        }
      }
      boardJson.netClasses.add(ncJson);
    }

    // 4. Outline
    BoardOutline outline = board.get_outline();
    if (outline != null) {
      boardJson.outline = new KiCadBoardJson.OutlineJson();
      int clearanceClassNo = outline.clearance_class_no();
      boardJson.outline.clearance = board.rules.clearance_matrix.get_value(clearanceClassNo, clearanceClassNo, 0, false) / scaleFactor;
      for (int i = 0; i < outline.shape_count(); i++) {
        PolylineShape polyShape = outline.get_shape(i);
        if (polyShape != null) {
          for (Point pt : polyShape.bounded_corners()) {
            boardJson.outline.corners.add(new KiCadBoardJson.Point2D(pt.to_float().x / scaleFactor, pt.to_float().y / scaleFactor));
          }
        }
      }
    }

    // 5. Traces
    int traceId = 1;
    for (Trace trace : board.get_traces()) {
      if (trace instanceof PolylineTrace polyTrace) {
        KiCadBoardJson.TraceJson tJson = new KiCadBoardJson.TraceJson();
        tJson.id = traceId++;
        tJson.layerIndex = polyTrace.get_layer();
        tJson.width = (2 * polyTrace.get_half_width()) / scaleFactor;
        if (polyTrace.net_count() > 0) {
          int netNo = polyTrace.get_net_no(0);
          Net net = board.rules.nets.get(netNo);
          if (net != null) {
            tJson.netName = net.name;
          }
        }
        for (Point pt : polyTrace.polyline().corner_arr()) {
          tJson.points.add(new KiCadBoardJson.Point2D(pt.to_float().x / scaleFactor, pt.to_float().y / scaleFactor));
        }
        boardJson.traces.add(tJson);
      }
    }

    // 6. Vias
    int viaId = 1;
    for (Via via : board.get_vias()) {
      KiCadBoardJson.ViaJson vJson = new KiCadBoardJson.ViaJson();
      vJson.id = viaId++;
      if (via.net_count() > 0) {
        int netNo = via.get_net_no(0);
        Net net = board.rules.nets.get(netNo);
        if (net != null) {
          vJson.netName = net.name;
        }
      }
      Point center = via.get_center();
      vJson.position = new KiCadBoardJson.Point2D(center.to_float().x / scaleFactor, center.to_float().y / scaleFactor);

      Padstack padstack = via.get_padstack();
      int firstLayer = 0;
      while (firstLayer < board.get_layer_count() && padstack.get_shape(firstLayer) == null) {
        firstLayer++;
      }
      int lastLayer = board.get_layer_count() - 1;
      while (lastLayer >= 0 && padstack.get_shape(lastLayer) == null) {
        lastLayer--;
      }
      vJson.startLayerIndex = firstLayer;
      vJson.endLayerIndex = lastLayer;

      app.freerouting.geometry.planar.Shape shape = padstack.get_shape(firstLayer);
      if (shape != null) {
        vJson.diameter = shape.bounding_box().width() / scaleFactor;
      } else {
        vJson.diameter = 0.8;
      }
      vJson.drill = vJson.diameter * 0.5;
      boardJson.vias.add(vJson);
    }

    // 7. Conduction Areas
    int areaId = 1;
    for (ConductionArea area : board.get_conduction_areas()) {
      KiCadBoardJson.ConductionAreaJson aJson = new KiCadBoardJson.ConductionAreaJson();
      aJson.id = areaId++;
      if (area.net_count() > 0) {
        int netNo = area.get_net_no(0);
        Net net = board.rules.nets.get(netNo);
        if (net != null) {
          aJson.netName = net.name;
        }
      }
      aJson.layerIndex = area.get_layer();
      aJson.isObstacle = area.get_is_obstacle();
      for (FloatPoint pt : area.get_area().corner_approx_arr()) {
        aJson.polygon.add(new KiCadBoardJson.Point2D(pt.x / scaleFactor, pt.y / scaleFactor));
      }
      boardJson.conductionAreas.add(aJson);
    }

    return GsonProvider.GSON.toJson(boardJson);
  }
}
