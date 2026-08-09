package app.freerouting.board;

import app.freerouting.boardgraphics.ColorIntensityTable;
import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Common superclass for Pins and Vias. */
public abstract class DrillItem extends Item implements Connectable, Serializable {

  /** The center point of the drillitem. */
  private Point center;

  /**
   * Contains the precalculated minimal width of the shapes of this DrillItem on all layers. If
   * {@literal <} 0, the value is not yet calculated
   */
  private double precalculatedMinWidth = -1;

  /**
   * Contains the precalculated first layer, where this DrillItem contains a pad shape. If {@literal
   * <} 0, the value is not yet calculated
   */
  private int precalculatedFirstLayer = -1;

  /**
   * Contains the precalculated last layer, where this DrillItem contains a pad shape. If {@literal
   * <} 0, the value is not yet calculated
   */
  private int precalculatedLastLayer = -1;

  /** DrillItem. */
  protected DrillItem(
      Point center,
      int[] netNoArr,
      int clearanceType,
      int idNo,
      int groupNo,
      FixedState fixedState,
      BasicBoard board) {
    super(netNoArr, clearanceType, idNo, groupNo, fixedState, board);
    this.center = center;
  }

  /** Works only for symmetric DrillItems. */
  @Override
  public void translateBy(Vector vector) {
    if (center != null) {
      center = center.translateBy(vector);
    }
    this.clearDerivedData();
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    if (center != null) {
      center = center.turn90Degree(factor, pole);
    }
    this.clearDerivedData();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    if (center != null) {
      FloatPoint newCenter = center.toFloat().rotate(Math.toRadians(angleInDegree), pole);
      this.center = newCenter.round();
    }
    this.clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    if (center != null) {
      center = center.mirrorVertical(pole);
    }
    this.clearDerivedData();
  }

  @Override
  public void moveBy(Vector vector) {
    Point oldCenter = this.getCenter();
    // remember the contact situation of this drillitem to traces on each layer
    Set<TraceInfo> contactTraceInfo = new TreeSet<>();
    Collection<Item> contacts = this.getNormalContacts();
    for (Item currContact : contacts) {
      if (currContact instanceof Trace currTrace) {
        TraceInfo currTraceInfo =
            new TraceInfo(
                currTrace.getLayer(), currTrace.getHalfWidth(), currTrace.clearanceClassNo());
        contactTraceInfo.add(currTraceInfo);
      }
    }
    super.moveBy(vector);

    // Insert a Trace from the old center to the new center, on all layers, where
    // this DrillItem was connected to a Trace.
    Collection<Point> connectPointList = new LinkedList<>();
    connectPointList.add(oldCenter);
    Point newCenter = this.getCenter();
    IntPoint addCorner = null;
    if (oldCenter instanceof IntPoint point && newCenter instanceof IntPoint point1) {
      // Make sure, that the traces will remain 90- or 45-degree.
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        addCorner = point.ninetyDegreeCorner(point1, true);
      } else if (board.rules.getTraceAngleRestriction() == AngleRestriction.FORTYFIVE_DEGREE) {
        addCorner = point.fortyfiveDegreeCorner(point1, true);
      }
    }
    if (addCorner != null) {
      connectPointList.add(addCorner);
    }
    connectPointList.add(newCenter);
    Point[] connectPoints = new Point[connectPointList.size()];
    Iterator<Point> it3 = connectPointList.iterator();
    for (int i = 0; i < connectPoints.length; i++) {
      connectPoints[i] = it3.next();
    }
    for (TraceInfo currTraceInfo : contactTraceInfo) {
      board.insertTrace(
          connectPoints,
          currTraceInfo.layer,
          currTraceInfo.halfWidth,
          this.netNoArr,
          currTraceInfo.clearanceType,
          FixedState.UNFIXED);
    }
  }

  @Override
  public int shapeLayer(int index) {
    index = Math.max(index, 0);
    int fromLayer = firstLayer();
    int toLayer = lastLayer();
    index = Math.min(index, toLayer - fromLayer);
    return fromLayer + index;
  }

  @Override
  public boolean isOnLayer(int layer) {
    return layer >= firstLayer() && layer <= lastLayer();
  }

  @Override
  public int firstLayer() {
    if (this.precalculatedFirstLayer < 0) {
      Padstack padstack = getPadstack();
      if (this.isPlacedOnFront() || padstack.placedAbsolute) {
        this.precalculatedFirstLayer = padstack.fromLayer();
      } else {
        this.precalculatedFirstLayer = padstack.boardLayerCount() - padstack.toLayer() - 1;
      }
    }
    return this.precalculatedFirstLayer;
  }

  @Override
  public int lastLayer() {
    if (this.precalculatedLastLayer < 0) {
      Padstack padstack = getPadstack();
      if (this.isPlacedOnFront() || padstack.placedAbsolute) {
        this.precalculatedLastLayer = padstack.toLayer();
      } else {
        this.precalculatedLastLayer = padstack.boardLayerCount() - padstack.fromLayer() - 1;
      }
    }
    return this.precalculatedLastLayer;
  }


  /** Get shape. */
  public abstract Shape getShape(int index);

  @Override
  public IntBox boundingBox() {
    IntBox result = IntBox.EMPTY;
    for (int i = 0; i < tileShapeCount(); i++) {
      Shape currShape = this.getShape(i);
      if (currShape != null) {
        result = result.union(currShape.boundingBox());
      }
    }
    return result;
  }

  @Override
  public int tileShapeCount() {
    Padstack padstack = getPadstack();
    int fromLayer = padstack.fromLayer();
    int toLayer = padstack.toLayer();
    return toLayer - fromLayer + 1;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree searchTree) {
    return searchTree.calculateTreeShapes(this);
  }

  /** Returns the smallest distance from the center to the border of the shape on any layer. */
  public double smallestRadius() {
    double result = Double.MAX_VALUE;
    FloatPoint c = getCenter().toFloat();
    for (int i = 0; i < tileShapeCount(); i++) {
      Shape currShape = getShape(i);
      if (currShape != null) {
        result = Math.min(result, currShape.borderDistance(c));
      }
    }
    return result;
  }

  /** Returns the center point of this DrillItem. */
  public Point getCenter() {
    return center;
  }

  protected void setCenter(Point center) {
    this.center = center;
  }

  /** Returns the padstack of this drillitem. */
  public abstract Padstack getPadstack();

  /** Get tree shape on layer. */
  public TileShape getTreeShapeOnLayer(ShapeSearchTree tree, int layer) {
    int fromLayer = firstLayer();
    /** Last layer. */
    int toLayer = lastLayer();
    if (layer < fromLayer || layer > toLayer) {
      FRLogger.warn("DrillItem.get_tree_shape_on_layer: p_layer out of range");
      return null;
    }
    return getTreeShape(tree, layer - fromLayer);
  }

  public TileShape getTileShapeOnLayer(int layer) {
    int fromLayer = firstLayer();
    /** Last layer. */
    int toLayer = lastLayer();
    if (layer < fromLayer || layer > toLayer) {
      FRLogger.warn("DrillItem.get_tile_shape_on_layer: p_layer out of range");
      return null;
    }
    return getTileShape(layer - fromLayer);
  }

  public Shape getShapeOnLayer(int layer) {
    int fromLayer = firstLayer();
    /** Last layer. */
    int toLayer = lastLayer();
    if (layer < fromLayer || layer > toLayer) {
      FRLogger.warn("DrillItem.get_shape_on_layer: p_layer out of range");
      return null;
    }
    return getShape(layer - fromLayer);
  }

  @Override
  public Set<Item> getNormalContacts() {
    Point drillCenter = this.getCenter();
    TileShape searchShape = TileShape.getInstance(drillCenter);
    Set<SearchTreeObject> overlaps = board.overlappingObjects(searchShape, -1);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currOb : overlaps) {
      if (!(currOb instanceof Item currItem)) {
        continue;
      }
      if (currItem != this && currItem.sharesNet(this) && currItem.sharesLayer(this)) {
        if (currItem instanceof Trace currTrace) {
          // Use exact matching to match trace endpoints to pin/via center.
          // Tolerance-based matching causes false cycle detection during trace normalization
          // when nearby trace endpoints (but not at pin center) are incorrectly treated as
          // contacts.
          if (drillCenter.equals(currTrace.firstCorner())
              || drillCenter.equals(currTrace.lastCorner())) {
            result.add(currItem);
          }
        } else if (currItem instanceof DrillItem currDrillItem) {
          if (drillCenter.equals(currDrillItem.getCenter())) {
            result.add(currItem);
          }
        } else if (currItem instanceof ConductionArea currArea) {
          if (currArea.getArea().contains(drillCenter)) {
            result.add(currItem);
          }
        }
      }
    }
    return result;
  }

  /**
   * Checks if two points are within the specified tolerance distance. Uses Manhattan distance for
   * efficiency.
   */
  private boolean isWithinTolerance(Point p1, Point p2, int tolerance) {
    if (p1 == null || p2 == null) {
      return false;
    }
    // Convert to FloatPoint for distance calculation
    FloatPoint fp1 = p1.toFloat();
    FloatPoint fp2 = p2.toFloat();

    // Use Manhattan distance (|x1-x2| + |y1-y2|) which is faster than Euclidean
    // and sufficient for connectivity detection
    double dx = Math.abs(fp1.x - fp2.x);
    double dy = Math.abs(fp1.y - fp2.y);
    return (dx + dy) <= tolerance;
  }

  @Override
  public Point normalContactPoint(Item other) {
    return other.normalContactPoint(this);
  }

  @Override
  Point normalContactPoint(DrillItem other) {
    if (this.sharesLayer(other) && this.getCenter().equals(other.getCenter())) {
      return this.getCenter();
    }
    return null;
  }

  @Override
  Point normalContactPoint(Trace trace) {
    if (!this.sharesLayer(trace)) {
      return null;
    }
    Point drillCenter = this.getCenter();
    if (drillCenter.equals(trace.firstCorner()) || drillCenter.equals(trace.lastCorner())) {
      return drillCenter;
    }
    return null;
  }

  @Override
  public Point[] getRatsnestCorners() {
    Point[] result = new Point[1];
    result[0] = this.getCenter();
    return result;
  }

  @Override
  public TileShape getTraceConnectionShape(ShapeSearchTree searchTree, int index) {
    return TileShape.getInstance(this.getCenter());
  }

  /** False, if this drillitem is places on the back side of the board. */
  public boolean isPlacedOnFront() {
    return true;
  }

  /** Return the minimal width of the shapes of this DrillItem on all signal layers. */
  public double minWidth() {
    if (this.precalculatedMinWidth < 0) {
      double minWidth = Integer.MAX_VALUE;
      int beginLayer = this.firstLayer();
      int endLayer = this.lastLayer();
      for (int currLayer = beginLayer; currLayer <= endLayer; currLayer++) {
        if (this.board != null && !this.board.layerStructure.arr[currLayer].isSignal) {
          continue;
        }
        Shape currShape = this.getShapeOnLayer(currLayer);
        if (currShape != null) {
          IntBox currBoundingBox = currShape.boundingBox();
          minWidth = Math.min(minWidth, currBoundingBox.width());
          minWidth = Math.min(minWidth, currBoundingBox.height());
        }
      }
      this.precalculatedMinWidth = minWidth;
    }
    return this.precalculatedMinWidth;
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.precalculatedFirstLayer = -1;
    this.precalculatedLastLayer = -1;
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MIDDLE_DRAW_PRIORITY;
  }

  @Override
  public void drawLayer(
      Graphics g,
      GraphicsContext graphicsContext,
      Color[] colorArr,
      double intensity,
      int layerNo) {
    if (graphicsContext == null || intensity <= 0) {
      return;
    }
    int fromLayer = firstLayer();
    int toLayer = lastLayer();
    if (layerNo < fromLayer || layerNo > toLayer) {
      return;
    }

    // Determine if this is the last physical layer step drawn (for through-hole drill hole
    // rendering)
    boolean isLastPhysicalLayer = false;
    if (this instanceof Pin && fromLayer != toLayer) {
      int lastPhysicalLayer;
      int activeLayer = graphicsContext.getFullyVisibleLayer();
      if (activeLayer != -1) {
        lastPhysicalLayer = activeLayer;
      } else {
        int activeVirtual = graphicsContext.getFullyVisibleVirtualLayer();
        boolean isBack = false;
        if (activeVirtual != -1) {
          isBack =
              activeVirtual % 2
                  != 0; // odd indices are Back (B.Silkscreen=1, B.Courtyard=3, B.Fab=5)
        }
        int layerCount = board.getLayerCount();
        lastPhysicalLayer = isBack ? (layerCount - 1) : 0;
      }
      if (layerNo == lastPhysicalLayer) {
        isLastPhysicalLayer = true;
      }
    }

    double visibilityFactor = 0;
    for (int i = fromLayer; i <= toLayer; i++) {
      visibilityFactor += graphicsContext.getLayerVisibility(i);
    }

    if (visibilityFactor >= 0.001) {
      double currIntensity = intensity;
      if (!(this instanceof Pin)) {
        currIntensity = currIntensity / Math.max(visibilityFactor, 1);
      }
      Shape currShape = this.getShape(layerNo - fromLayer);
      if (currShape != null) {
        double layerVis = graphicsContext.getLayerVisibility(layerNo);
        if (layerVis > 0.001) {
          Color color = colorArr[layerNo];
          double layerIntensity = this instanceof Pin ? intensity : intensity * layerVis;
          graphicsContext.fillArea(currShape, g, color, layerIntensity);
        }
      }
    }

    // Render drill hole for through-hole pins only (not vias)
    if (isLastPhysicalLayer) {
      Padstack padstack = getPadstack();
      if (padstack != null) {
        double drillRadius = padstack.getDrillRadius();
        if (drillRadius > 0) {
          Color drillColor = graphicsContext.otherColorTable.getDrillHoleColor();
          double drillIntensity =
              graphicsContext.colorIntensityTable.getValue(
                  ColorIntensityTable.ObjectNames.DRILL_HOLES.ordinal());
          IntPoint centerPoint = getCenter().toFloat().round();
          Circle drillCircle = new Circle(centerPoint, (int) Math.round(drillRadius));
          graphicsContext.fillCircle(drillCircle, g, drillColor, drillIntensity);
        }
      }
    }
  }

  @Override
  public void draw(
      Graphics g, GraphicsContext graphicsContext, Color[] colorArr, double intensity) {
    if (graphicsContext == null || intensity <= 0) {
      return;
    }
    int fromLayer = firstLayer();
    int toLayer = lastLayer();
    // Decrease the drawing intensity for items with many layers.
    double visibilityFactor = 0;
    for (int i = fromLayer; i <= toLayer; i++) {
      visibilityFactor += graphicsContext.getLayerVisibility(i);
    }

    if (visibilityFactor >= 0.001) {
      double currIntensity = intensity;
      if (!(this instanceof Pin)) {
        currIntensity = currIntensity / Math.max(visibilityFactor, 1);
      }
      for (int i = 0; i <= toLayer - fromLayer; i++) {
        Shape currShape = this.getShape(i);
        if (currShape == null) {
          continue;
        }
        double layerVis = graphicsContext.getLayerVisibility(fromLayer + i);
        if (layerVis <= 0.001) {
          continue;
        }
        Color color = colorArr[fromLayer + i];
        double layerIntensity = this instanceof Pin ? intensity : intensity * layerVis;
        graphicsContext.fillArea(currShape, g, color, layerIntensity);
      }
    }

    // Render drill hole for through-hole pins only (not vias)
    if (this instanceof Pin && fromLayer != toLayer) {
      Padstack padstack = getPadstack();
      if (padstack != null) {
        double drillRadius = padstack.getDrillRadius();
        if (drillRadius > 0) {
          Color drillColor = graphicsContext.otherColorTable.getDrillHoleColor();
          double drillIntensity =
              graphicsContext.colorIntensityTable.getValue(
                  ColorIntensityTable.ObjectNames.DRILL_HOLES.ordinal());
          IntPoint centerPoint = getCenter().toFloat().round();
          Circle drillCircle = new Circle(centerPoint, (int) Math.round(drillRadius));
          graphicsContext.fillCircle(drillCircle, g, drillColor, drillIntensity);
        }
      }
    }
  }

  /** Auxiliary class used in the method move_by. */
  private static class TraceInfo implements Comparable<TraceInfo> {

    int layer;
    int halfWidth;
    int clearanceType;

    TraceInfo(int layer, int halfWidth, int clearanceType) {
      this.layer = layer;
      this.halfWidth = halfWidth;
      this.clearanceType = clearanceType;
    }

    /** Implements the comparable interface. */
    @Override
    public int compareTo(TraceInfo other) {
      return other.layer - this.layer;
    }
  }
}
