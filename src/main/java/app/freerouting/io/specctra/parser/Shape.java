package app.freerouting.io.specctra.parser;

import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Describes a shape in a Specctra dsn file. */
public abstract class Shape {

  public final Layer layer;

  protected Shape(Layer p_layer) {
    layer = p_layer;
  }

  /**
   * Reads shape scope from a Specctra dsn file. If p_layer_structure == null, only Layer.PCB and
   * Layer.Signal are expected, no individual layers.
   */
  public static Shape read_scope(IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      Object nextToken = p_scanner.next_token();
      if (nextToken == Keyword.OPEN_BRACKET) {
        // overread the open bracket
        nextToken = p_scanner.next_token();
      }
      return read_scope_from_keyword(p_scanner, nextToken, p_layer_structure);
    } catch (IOException e) {
      FRLogger.error("Shape.read_scope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Reads a shape scope when the shape keyword (for example {@code path}) has already been scanned.
   */
  static Shape read_scope_from_keyword(
      IJFlexScanner p_scanner, Object keyword, LayerStructure p_layer_structure) {
    if (keyword == Keyword.RECTANGLE) {
      return Shape.read_rectangle_scope(p_scanner, p_layer_structure);
    }
    if (keyword == Keyword.POLYGON) {
      return Shape.read_polygon_scope(p_scanner, p_layer_structure);
    }
    if (keyword == Keyword.CIRCLE) {
      return Shape.read_circle_scope(p_scanner, p_layer_structure);
    }
    if (keyword == Keyword.POLYGON_PATH) {
      return Shape.read_polygon_path_scope(p_scanner, p_layer_structure);
    }
    if (keyword == Keyword.POLYLINE_PATH) {
      return Shape.read_polyline_path_scope(p_scanner, p_layer_structure);
    }
    ScopeKeyword.skip_scope(p_scanner);
    return null;
  }

  /**
   * Gets the layer with a certain name from the layer structure
   *
   * @param p_layer_structure Layer structure to scan
   * @param layerName Name of the layer to scan for
   * @return Layer object with the defined name
   */
  private static Layer get_layer(LayerStructure p_layer_structure, String layerName) {
    Layer layer;

    if (layerName.equals(Keyword.PCB_SCOPE.get_name())) {
      layer = Layer.PCB;
    } else if (layerName.equals(Keyword.SIGNAL.get_name())) {
      layer = Layer.SIGNAL;
    } else {
      if (p_layer_structure == null) {
        FRLogger.warn("Shape.read_circle_scope: p_layer_structure != null expected");
        return null;
      }

      int layerNo = p_layer_structure.get_no(layerName);
      if (layerNo < 0 || layerNo >= p_layer_structure.arr.length) {
        FRLogger.warn(
            "Shape.read_circle_scope: layer with name '"
                + layerName
                + "' not found in layer structure.");
        return null;
      } else {
        layer = p_layer_structure.arr[layerNo];
      }
    }

    return layer;
  }

  /** Reads an object of type PolylinePath from the dsn-file. */
  public static PolylinePath read_polyline_path_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      String layerName = p_scanner.next_string();
      Layer layer = get_layer(p_layer_structure, layerName);

      Object nextToken;
      Collection<Object> cornerList = new LinkedList<>();
      // read the width and the corners of the path
      for (; ; ) {
        nextToken = p_scanner.next_token();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        cornerList.add(nextToken);
      }
      if (cornerList.size() < 5) {
        FRLogger.warn(
            "PolylinePath.read_scope: too few numbers in scope at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      Iterator<Object> it = cornerList.iterator();
      double width = 0;
      Object nextObject = it.next();
      if (nextObject instanceof Double double1) {
        width = double1;
      } else if (nextObject instanceof Integer integer) {
        width = integer;
      } else {
        FRLogger.warn(
            "PolylinePath.read_scope: number expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      double[] cornerArr = new double[cornerList.size() - 1];
      for (int i = 0; i < cornerArr.length; i++) {
        nextObject = it.next();
        if (nextObject instanceof Double double1) {
          cornerArr[i] = double1;
        } else if (nextObject instanceof Integer integer) {
          cornerArr[i] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_polygon_path_scope: number expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }
      return new PolylinePath(layer, width, cornerArr);
    } catch (IOException e) {
      FRLogger.error("PolylinePath.read_scope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Reads a shape , which may contain holes from a specctra dsn-file. The first shape in the
   * shapeList of the result is the border of the area. The other shapes in the shapeList are holes
   * (windows).
   */
  public static ReadAreaScopeResult read_area_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure, boolean p_skip_window_scopes) {
    Collection<Shape> shapeList = new LinkedList<>();
    String clearanceClassName = null;
    String areaName = null;
    boolean resultOk = true;
    Object nextToken;
    try {
      nextToken = p_scanner.next_token();
    } catch (IOException _) {
      FRLogger.warn(
          "Shape.read_area_scope: IO error scanning file at '"
              + p_scanner.get_scope_identifier()
              + "'");
      return null;
    }
    if (nextToken instanceof String currName) {
      p_scanner.set_scope_identifier(currName);
      if (!currName.isEmpty()) {
        areaName = currName;
      }
    }
    Shape currShape = Shape.read_scope(p_scanner, p_layer_structure);
    if (currShape == null) {
      FRLogger.warn(
          "Shape.read_area_scope: could not read shape at '"
              + p_scanner.get_scope_identifier()
              + "'");
      resultOk = false;
    }
    shapeList.add(currShape);
    nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = p_scanner.next_token();
      } catch (IOException e) {
        FRLogger.error("Shape.read_area_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Shape.read_area_scope: unexpected end of file at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        // a new scope is expected
        if (nextToken == Keyword.WINDOW && !p_skip_window_scopes) {
          Shape holeShape = Shape.read_scope(p_scanner, p_layer_structure);
          shapeList.add(holeShape);
          // overread closing bracket
          try {
            nextToken = p_scanner.next_token();
          } catch (IOException e) {
            FRLogger.error("Shape.read_area_scope: IO error scanning file", e);
            return null;
          }
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Shape.read_area_scope: closed bracket expected at '"
                    + p_scanner.get_scope_identifier()
                    + "'");
            return null;
          }

        } else if (nextToken == Keyword.CLEARANCE_CLASS) {
          clearanceClassName = DsnFile.read_string_scope(p_scanner);
        } else {
          // skip unknown scope
          ScopeKeyword.skip_scope(p_scanner);
        }
      }
    }
    if (!resultOk) {
      return null;
    }
    return new ReadAreaScopeResult(areaName, shapeList, clearanceClassName);
  }

  /**
   * Reads a rectangle scope from a Specctra dsn file. If p_layer_structure == null, only Layer.PCB
   * and Layer.Signal are expected, no individual layers.
   */
  public static Rectangle read_rectangle_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      String layerName = p_scanner.next_string();
      Layer rectLayer = get_layer(p_layer_structure, layerName);
      if (rectLayer == null) {
        rectLayer = get_layer(p_layer_structure, Keyword.SIGNAL.get_name());
      }

      Object nextToken;
      double[] rectCoor = new double[4];
      // fill the rectangle
      for (int i = 0; i < 4; i++) {
        nextToken = p_scanner.next_token();
        if (nextToken instanceof Double double1) {
          rectCoor[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          rectCoor[i] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_rectangle_scope: number expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }
      // overread the closing bracket

      nextToken = p_scanner.next_token();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Shape.read_rectangle_scope ) expected at '" + p_scanner.get_scope_identifier() + "'");
        return null;
      }
      if (rectLayer == null) {
        return null;
      }
      return new Rectangle(rectLayer, rectCoor);
    } catch (IOException e) {
      FRLogger.error("Shape.read_rectangle_scope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Reads a closed polygon scope from a Specctra dsn file. If p_layer_structure == null, only
   * Layer.PCB and Layer.Signal are expected, no individual layers.
   */
  public static Polygon read_polygon_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      Layer polygonLayer = null;
      boolean layerOk = true;
      Object nextToken = p_scanner.next_token();
      if (nextToken == Keyword.PCB_SCOPE) {
        polygonLayer = Layer.PCB;
      } else if (nextToken == Keyword.SIGNAL) {
        polygonLayer = Layer.SIGNAL;
      } else {
        if (p_layer_structure == null) {
          FRLogger.warn(
              "Shape.read_polygon_scope: only layer types pcb or signal expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Shape.read_polygon_scope: layer name string expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        int layerNo = p_layer_structure.get_no((String) nextToken);
        if (layerNo < 0 || layerNo >= p_layer_structure.arr.length) {
          FRLogger.warn(
              "Shape.read_polygon_scope: layer name '"
                  + nextToken
                  + "' not found in layer structure  at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          layerOk = false;
        } else {
          polygonLayer = p_layer_structure.arr[layerNo];
        }
      }

      // overread the aperture width
      nextToken = p_scanner.next_token();

      Collection<Object> coorList = new LinkedList<>();

      // read the coordinates of the polygon
      for (; ; ) {
        nextToken = p_scanner.next_token();
        if (nextToken == null) {
          FRLogger.warn(
              "Shape.read_polygon_scope: unexpected end of file at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          // unknown scope
          ScopeKeyword.skip_scope(p_scanner);
          nextToken = p_scanner.next_token();
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        coorList.add(nextToken);
      }
      if (!layerOk) {
        return null;
      }
      double[] coorArr = new double[coorList.size()];
      Iterator<Object> it = coorList.iterator();
      for (int i = 0; i < coorArr.length; i++) {
        Object nextObject = it.next();
        if (nextObject instanceof Double double1) {
          coorArr[i] = double1;
        } else if (nextObject instanceof Integer integer) {
          coorArr[i] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_polygon_scope: number expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }
      return new Polygon(polygonLayer, coorArr);
    } catch (IOException e) {
      FRLogger.error("Rectangle.read_scope: IO error scanning file", e);
      return null;
    }
  }

  /** Reads a circle scope from a Specctra dsn file. */
  public static Circle read_circle_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      String layerName = p_scanner.next_string();
      Layer circleLayer = get_layer(p_layer_structure, layerName);

      if (circleLayer == null) {
        FRLogger.warn(
            "Circle.read_circle_scope: layer with name '"
                + layerName
                + "' not found in layer structure at '"
                + p_scanner.get_scope_identifier()
                + "'");
      }

      // fill the coordinates
      Object nextToken;
      double[] circleCoor = new double[3];
      int currIndex = 0;
      for (; ; ) {
        nextToken = p_scanner.next_token();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (currIndex > 2) {
          FRLogger.warn(
              "Shape.read_circle_scope: closed bracket expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        if (nextToken instanceof Double double1) {
          circleCoor[currIndex] = double1;
        } else if (nextToken instanceof Integer integer) {
          circleCoor[currIndex] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_circle_scope: number expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
        ++currIndex;
      }

      if (circleLayer == null) {
        return null;
      }
      return new Circle(circleLayer, circleCoor);
    } catch (IOException e) {
      FRLogger.error("Shape.read_rectangle_scope: IO error scanning file", e);
      return null;
    }
  }

  /** Reads an object of type Path from the dsn-file. */
  public static PolygonPath read_polygon_path_scope(
      IJFlexScanner p_scanner, LayerStructure p_layer_structure) {
    try {
      String layerName = p_scanner.next_string();
      Layer layer = get_layer(p_layer_structure, layerName);

      Object nextToken;
      Collection<Object> cornerList = new LinkedList<>();
      // read the width and the corners of the path
      for (; ; ) {
        nextToken = p_scanner.next_token();
        if (nextToken == Keyword.OPEN_BRACKET) {
          // unknown scope
          ScopeKeyword.skip_scope(p_scanner);
          nextToken = p_scanner.next_token();
        }
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        cornerList.add(nextToken);
      }

      // cornerList contains width + coordinate pairs
      if (cornerList.size() < 5) {
        // Single-point paths are not valid traces, skip them
        FRLogger.debug(
            "Shape.read_polygon_path_scope: skipping path with too few coordinates (need at least 2 points) at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      if (layer == null) {
        return null;
      }
      Iterator<Object> it = cornerList.iterator();
      double width = 0;
      Object nextObject = it.next();
      if (nextObject instanceof Double double1) {
        width = double1;
      } else if (nextObject instanceof Integer integer) {
        width = integer;
      } else {
        FRLogger.warn(
            "Shape.read_polygon_path_scope: number expected at '"
                + p_scanner.get_scope_identifier()
                + "'");
        return null;
      }
      double[] coordinateArr = new double[cornerList.size() - 1];
      for (int i = 0; i < coordinateArr.length; i++) {
        nextObject = it.next();
        if (nextObject instanceof Double double1) {
          coordinateArr[i] = double1;
        } else if (nextObject instanceof Integer integer) {
          coordinateArr[i] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_polygon_path_scope: number expected at '"
                  + p_scanner.get_scope_identifier()
                  + "'");
          return null;
        }
      }
      return new PolygonPath(layer, width, coordinateArr);
    } catch (IOException e) {
      FRLogger.error("Shape.read_polygon_path_scope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Transforms a shape with holes to the board coordinate system. The first shape in the Collection
   * p_area is the border, the other shapes are holes of the area.
   */
  public static Area transform_area_to_board(
      Collection<Shape> p_area, CoordinateTransform p_coordinate_transform) {
    int holeCount = p_area.size() - 1;
    if (holeCount <= -1) {
      FRLogger.warn("Shape.transform_area_to_board: p_area.size() > 0 expected");
      return null;
    }
    Iterator<Shape> it = p_area.iterator();
    Shape boundary = it.next();
    app.freerouting.geometry.planar.Shape boundaryShape =
        boundary.transform_to_board(p_coordinate_transform);
    Area result;
    if (holeCount == 0) {
      result = boundaryShape;
    } else {
      // Area with holes
      if (!(boundaryShape instanceof PolylineShape border)) {
        FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected");
        return null;
      }
      PolylineShape[] holes = new PolylineShape[holeCount];
      for (int i = 0; i < holes.length; i++) {
        app.freerouting.geometry.planar.Shape holeShape =
            it.next().transform_to_board(p_coordinate_transform);
        if (!(holeShape instanceof PolylineShape)) {
          FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected");
          return null;
        }
        holes[i] = (PolylineShape) holeShape;
      }
      result = new PolylineArea(border, holes);
    }
    return result;
  }

  /**
   * Transforms the relative coordinates of a shape with holes to the board coordinate system. The
   * first shape in the Collection p_area is the border, the other shapes are holes of the area.
   */
  public static Area transform_area_to_board_rel(
      Collection<Shape> p_area, CoordinateTransform p_coordinate_transform) {
    int holeCount = p_area.size() - 1;
    if (holeCount <= -1) {
      FRLogger.warn("Shape.transform_area_to_board_rel: p_area.size() > 0 expected");
      return null;
    }
    Iterator<Shape> it = p_area.iterator();
    Shape boundary = it.next();
    app.freerouting.geometry.planar.Shape boundaryShape =
        boundary.transform_to_board_rel(p_coordinate_transform);
    Area result;
    if (holeCount == 0) {
      result = boundaryShape;
    } else {
      // Area with holes
      if (!(boundaryShape instanceof PolylineShape border)) {
        FRLogger.warn("Shape.transform_area_to_board_rel: PolylineShape expected");
        return null;
      }
      PolylineShape[] holes = new PolylineShape[holeCount];
      for (int i = 0; i < holes.length; i++) {
        app.freerouting.geometry.planar.Shape holeShape =
            it.next().transform_to_board_rel(p_coordinate_transform);
        if (!(holeShape instanceof PolylineShape)) {
          FRLogger.warn("Shape.transform_area_to_board: PolylineShape expected");
          return null;
        }
        holes[i] = (PolylineShape) holeShape;
      }
      result = new PolylineArea(border, holes);
    }
    return result;
  }

  /** Writes a shape scope to a Specctra dsn file. */
  public abstract void write_scope(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException;

  /**
   * Writes a shape scope to a Specctra session file. In a session file all coordinates must be
   * integer.
   */
  public abstract void write_scope_int(IndentFileWriter p_file, IdentifierType p_identifier)
      throws IOException;

  public void write_hole_scope(IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.start_scope();
    p_file.write("window");
    this.write_scope(p_file, p_identifier_type);
    p_file.end_scope();
  }

  /** Transforms a specctra dsn shape to a geometry.planar.Shape. */
  public abstract app.freerouting.geometry.planar.Shape transform_to_board(
      CoordinateTransform p_coordinate_transform);

  /** Returns the smallest axis parallel rectangle containing this shape. */
  public abstract Rectangle bounding_box();

  /**
   * Transforms the relative (vector) coordinates of a specctra dsn shape to a
   * geometry.planar.Shape.
   */
  public abstract app.freerouting.geometry.planar.Shape transform_to_board_rel(
      CoordinateTransform p_coordinate_transform);

  /**
   * Contains the result of the function read_area_scope. areaName or clearanceClassName may be
   * null, which means they are not provided.
   */
  static final class ReadAreaScopeResult {

    final Collection<Shape> shapeList;
    final String clearanceClassName;
    String areaName; // may be generated later on, if areaName is null.

    private ReadAreaScopeResult(
        String p_area_name, Collection<Shape> p_shape_list, String p_clearance_class_name) {
      areaName = p_area_name;
      shapeList = p_shape_list;
      clearanceClassName = p_clearance_class_name;
    }
  }
}
