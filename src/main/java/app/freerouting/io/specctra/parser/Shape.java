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
@SuppressWarnings({
  "checkstyle:MissingJavadocMethod",
  "checkstyle:MissingJavadocType",
  "checkstyle:VariableDeclarationUsageDistance"
})
public abstract class Shape {

  public Layer layer;

  protected Shape(Layer layer) {
    this.layer = layer;
  }

  /**
   * Reads shape scope from a Specctra dsn file. If layerStructure == null, only Layer.PCB and
   * Layer.Signal are expected, no individual layers.
   */
  public static Shape readScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      Object nextToken = scanner.nextToken();
      if (nextToken == Keyword.OPEN_BRACKET) {
        // overread the open bracket
        nextToken = scanner.nextToken();
      }
      return readScopeFromKeyword(scanner, nextToken, layerStructure);
    } catch (IOException e) {
      FRLogger.error("Shape.read_scope: IO error scanning file", e);
      return null;
    }
  }

  /**
   * Reads a shape scope when the shape keyword (for example {@code path}) has already been scanned.
   */
  static Shape readScopeFromKeyword(
      IJFlexScanner scanner, Object keyword, LayerStructure layerStructure) {
    if (keyword == Keyword.RECTANGLE) {
      return Shape.readRectangleScope(scanner, layerStructure);
    }
    if (keyword == Keyword.POLYGON) {
      return Shape.readPolygonScope(scanner, layerStructure);
    }
    if (keyword == Keyword.CIRCLE) {
      return Shape.readCircleScope(scanner, layerStructure);
    }
    if (keyword == Keyword.POLYGON_PATH) {
      return Shape.readPolygonPathScope(scanner, layerStructure);
    }
    if (keyword == Keyword.POLYLINE_PATH) {
      return Shape.readPolylinePathScope(scanner, layerStructure);
    }
    ScopeKeyword.skipScope(scanner);
    return null;
  }

  /**
   * Gets the layer with a certain name from the layer structure.
   *
   * @param layerStructure Layer structure to scan
   * @param layerName Name of the layer to scan for
   * @return Layer object with the defined name
   */
  private static Layer getLayer(LayerStructure layerStructure, String layerName) {
    Layer layer;

    if (layerName.equals(Keyword.PCB_SCOPE.getName())) {
      layer = Layer.PCB;
    } else if (layerName.equals(Keyword.SIGNAL.getName())) {
      layer = Layer.SIGNAL;
    } else {
      if (layerStructure == null) {
        FRLogger.warn("Shape.read_circle_scope: layerStructure != null expected");
        return null;
      }

      int layerIndex = layerStructure.getNo(layerName);
      if (layerIndex < 0 || layerIndex >= layerStructure.arr.length) {
        FRLogger.warn(
            "Shape.read_circle_scope: layer with name '"
                + layerName
                + "' not found in layer structure.");
        return null;
      } else {
        layer = layerStructure.arr[layerIndex];
      }
    }

    return layer;
  }

  /** Reads an object of type PolylinePath from the dsn-file. */
  public static PolylinePath readPolylinePathScope(
      IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      String layerName = scanner.nextString();
      final Layer layer = getLayer(layerStructure, layerName);

      Object nextToken;
      Collection<Object> cornerList = new LinkedList<>();
      // read the width and the corners of the path
      for (; ; ) {
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        cornerList.add(nextToken);
      }
      if (cornerList.size() < 5) {
        FRLogger.warn(
            "PolylinePath.read_scope: too few numbers in scope at '"
                + scanner.getScopeIdentifier()
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
            "PolylinePath.read_scope: number expected at '" + scanner.getScopeIdentifier() + "'");
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
                  + scanner.getScopeIdentifier()
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
  public static ReadAreaScopeResult readAreaScope(
      IJFlexScanner scanner, LayerStructure layerStructure, boolean skipWindowScopes) {
    Collection<Shape> shapeList = new LinkedList<>();
    String clearanceClassName = null;
    String areaName = null;
    boolean resultOk = true;
    Object nextToken;
    try {
      nextToken = scanner.nextToken();
    } catch (IOException _) {
      FRLogger.warn(
          "Shape.read_area_scope: IO error scanning file at '"
              + scanner.getScopeIdentifier()
              + "'");
      return null;
    }
    if (nextToken instanceof String currentName) {
      scanner.setScopeIdentifier(currentName);
      if (!currentName.isEmpty()) {
        areaName = currentName;
      }
    }
    Shape currentShape = Shape.readScope(scanner, layerStructure);
    if (currentShape == null) {
      FRLogger.warn(
          "Shape.read_area_scope: could not read shape at '" + scanner.getScopeIdentifier() + "'");
      resultOk = false;
    }
    shapeList.add(currentShape);
    nextToken = null;
    for (; ; ) {
      Object prevToken = nextToken;
      try {
        nextToken = scanner.nextToken();
      } catch (IOException e) {
        FRLogger.error("Shape.read_area_scope: IO error scanning file", e);
        return null;
      }
      if (nextToken == null) {
        FRLogger.warn(
            "Shape.read_area_scope: unexpected end of file at '"
                + scanner.getScopeIdentifier()
                + "'");
        return null;
      }
      if (nextToken == Keyword.CLOSED_BRACKET) {
        // end of scope
        break;
      }

      if (prevToken == Keyword.OPEN_BRACKET) {
        // a new scope is expected
        if (nextToken == Keyword.WINDOW && !skipWindowScopes) {
          Shape holeShape = Shape.readScope(scanner, layerStructure);
          shapeList.add(holeShape);
          // overread closing bracket
          try {
            nextToken = scanner.nextToken();
          } catch (IOException e) {
            FRLogger.error("Shape.read_area_scope: IO error scanning file", e);
            return null;
          }
          if (nextToken != Keyword.CLOSED_BRACKET) {
            FRLogger.warn(
                "Shape.read_area_scope: closed bracket expected at '"
                    + scanner.getScopeIdentifier()
                    + "'");
            return null;
          }

        } else if (nextToken == Keyword.CLEARANCE_CLASS) {
          clearanceClassName = DsnFile.readStringScope(scanner);
        } else {
          // skip unknown scope
          ScopeKeyword.skipScope(scanner);
        }
      }
    }
    if (!resultOk) {
      return null;
    }
    return new ReadAreaScopeResult(areaName, shapeList, clearanceClassName);
  }

  /**
   * Reads a rectangle scope from a Specctra dsn file. If layerStructure == null, only Layer.PCB and
   * Layer.Signal are expected, no individual layers.
   */
  public static Rectangle readRectangleScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      String layerName = scanner.nextString();
      Layer rectLayer = getLayer(layerStructure, layerName);
      if (rectLayer == null) {
        rectLayer = getLayer(layerStructure, Keyword.SIGNAL.getName());
      }

      Object nextToken;
      double[] rectCoor = new double[4];
      // fill the rectangle
      for (int i = 0; i < 4; i++) {
        nextToken = scanner.nextToken();
        if (nextToken instanceof Double double1) {
          rectCoor[i] = double1;
        } else if (nextToken instanceof Integer integer) {
          rectCoor[i] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_rectangle_scope: number expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
      }
      // overread the closing bracket

      nextToken = scanner.nextToken();
      if (nextToken != Keyword.CLOSED_BRACKET) {
        FRLogger.warn(
            "Shape.read_rectangle_scope ) expected at '" + scanner.getScopeIdentifier() + "'");
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
   * Reads a closed polygon scope from a Specctra dsn file. If layerStructure == null, only
   * Layer.PCB and Layer.Signal are expected, no individual layers.
   */
  public static Polygon readPolygonScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      Layer polygonLayer = null;
      boolean layerOk = true;
      Object nextToken = scanner.nextToken();
      if (nextToken == Keyword.PCB_SCOPE) {
        polygonLayer = Layer.PCB;
      } else if (nextToken == Keyword.SIGNAL) {
        polygonLayer = Layer.SIGNAL;
      } else {
        if (layerStructure == null) {
          FRLogger.warn(
              "Shape.read_polygon_scope: only layer types pcb or signal expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (!(nextToken instanceof String)) {
          FRLogger.warn(
              "Shape.read_polygon_scope: layer name string expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        int layerIndex = layerStructure.getNo((String) nextToken);
        if (layerIndex < 0 || layerIndex >= layerStructure.arr.length) {
          FRLogger.warn(
              "Shape.read_polygon_scope: layer name '"
                  + nextToken
                  + "' not found in layer structure  at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          layerOk = false;
        } else {
          polygonLayer = layerStructure.arr[layerIndex];
        }
      }

      // overread the aperture width
      nextToken = scanner.nextToken();

      Collection<Object> coorList = new LinkedList<>();

      // read the coordinates of the polygon
      for (; ; ) {
        nextToken = scanner.nextToken();
        if (nextToken == null) {
          FRLogger.warn(
              "Shape.read_polygon_scope: unexpected end of file at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken == Keyword.OPEN_BRACKET) {
          // unknown scope
          ScopeKeyword.skipScope(scanner);
          nextToken = scanner.nextToken();
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
                  + scanner.getScopeIdentifier()
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
  public static Circle readCircleScope(IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      String layerName = scanner.nextString();
      Layer circleLayer = getLayer(layerStructure, layerName);

      if (circleLayer == null) {
        FRLogger.warn(
            "Circle.read_circle_scope: layer with name '"
                + layerName
                + "' not found in layer structure at '"
                + scanner.getScopeIdentifier()
                + "'");
      }

      // fill the coordinates
      Object nextToken;
      double[] circleCoor = new double[3];
      int currentIndex = 0;
      for (; ; ) {
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.CLOSED_BRACKET) {
          break;
        }
        if (currentIndex > 2) {
          FRLogger.warn(
              "Shape.read_circle_scope: closed bracket expected at '"
                  + scanner.getScopeIdentifier()
                  + "'");
          return null;
        }
        if (nextToken instanceof Double double1) {
          circleCoor[currentIndex] = double1;
        } else if (nextToken instanceof Integer integer) {
          circleCoor[currentIndex] = integer;
        } else {
          FRLogger.warn(
              "Shape.read_circle_scope: number expected at '" + scanner.getScopeIdentifier() + "'");
          return null;
        }
        ++currentIndex;
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
  public static PolygonPath readPolygonPathScope(
      IJFlexScanner scanner, LayerStructure layerStructure) {
    try {
      String layerName = scanner.nextString();
      Layer layer = getLayer(layerStructure, layerName);

      Object nextToken;
      Collection<Object> cornerList = new LinkedList<>();
      // read the width and the corners of the path
      for (; ; ) {
        nextToken = scanner.nextToken();
        if (nextToken == Keyword.OPEN_BRACKET) {
          // unknown scope
          ScopeKeyword.skipScope(scanner);
          nextToken = scanner.nextToken();
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
            "Shape.read_polygon_path_scope: skipping path with too few coordinates "
                + "(need at least 2 points) at '"
                + scanner.getScopeIdentifier()
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
                + scanner.getScopeIdentifier()
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
                  + scanner.getScopeIdentifier()
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
   * area is the border, the other shapes are holes of the area.
   */
  public static Area transformAreaToBoard(
      Collection<Shape> area, CoordinateTransform coordinateTransform) {
    int holeCount = area.size() - 1;
    if (holeCount <= -1) {
      FRLogger.warn("Shape.transform_area_to_board: area.size() > 0 expected");
      return null;
    }
    Iterator<Shape> it = area.iterator();
    Shape boundary = it.next();
    app.freerouting.geometry.planar.Shape boundaryShape =
        boundary.transformToBoard(coordinateTransform);
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
            it.next().transformToBoard(coordinateTransform);
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
   * first shape in the Collection area is the border, the other shapes are holes of the area.
   */
  public static Area transformAreaToBoardRel(
      Collection<Shape> area, CoordinateTransform coordinateTransform) {
    int holeCount = area.size() - 1;
    if (holeCount <= -1) {
      FRLogger.warn("Shape.transform_area_to_board_rel: area.size() > 0 expected");
      return null;
    }
    Iterator<Shape> it = area.iterator();
    Shape boundary = it.next();
    app.freerouting.geometry.planar.Shape boundaryShape =
        boundary.transformToBoardRel(coordinateTransform);
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
            it.next().transformToBoardRel(coordinateTransform);
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
  public abstract void writeScope(IndentFileWriter file, IdentifierType identifier)
      throws IOException;

  /**
   * Writes a shape scope to a Specctra session file. In a session file all coordinates must be
   * integer.
   */
  public abstract void writeScopeInt(IndentFileWriter file, IdentifierType identifier)
      throws IOException;

  public void writeHoleScope(IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.startScope();
    file.write("window");
    this.writeScope(file, identifierType);
    file.endScope();
  }

  /** Transforms a specctra dsn shape to a geometry.planar.Shape. */
  public abstract app.freerouting.geometry.planar.Shape transformToBoard(
      CoordinateTransform coordinateTransform);

  /** Returns the smallest axis parallel rectangle containing this shape. */
  public abstract Rectangle boundingBox();

  /**
   * Transforms the relative (vector) coordinates of a specctra dsn shape to a
   * geometry.planar.Shape.
   */
  public abstract app.freerouting.geometry.planar.Shape transformToBoardRel(
      CoordinateTransform coordinateTransform);

  /**
   * Contains the result of the function read_area_scope. areaName or clearanceClassName may be
   * null, which means they are not provided.
   */
  static final class ReadAreaScopeResult {

    final Collection<Shape> shapeList;
    final String clearanceClassName;
    String areaName; // may be generated later on, if areaName is null.

    private ReadAreaScopeResult(
        String areaName, Collection<Shape> shapeList, String clearanceClassName) {
      this.areaName = areaName;
      this.shapeList = shapeList;
      this.clearanceClassName = clearanceClassName;
    }
  }
}
