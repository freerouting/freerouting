package app.freerouting.board;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** A ObstacleArea, which can be electrically connected to other items. */
public class ConductionArea extends ObstacleArea implements Connectable {

  private static final double PLANE_HATCH_OPACITY = 0.85;

  private boolean isObstacle;
  private boolean isFilled = true;

  public boolean getIsFilled() {
    return this.isFilled;
  }

  /** SetIsFilled. */
  public void setIsFilled(boolean value) {
    this.isFilled = value;
    this.clearDerivedData();
  }

  private transient int cachedBoardRevision = -1;
  private transient java.awt.geom.Area cachedBoardFillArea;

  /** Creates a new instance of ConductionArea. */
  ConductionArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int[] netNumbers,
      int clearanceClass,
      int idNo,
      int groupNo,
      String name,
      boolean isObstacle,
      FixedState fixedState,
      BasicBoard board) {
    super(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        netNumbers,
        clearanceClass,
        idNo,
        groupNo,
        name,
        fixedState,
        board);
    this.isObstacle = isObstacle;
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.cachedBoardRevision = -1;
    this.cachedBoardFillArea = null;
  }

  /** Pre-computes detailed plane-fill geometry off the EDT so zoom-in paints stay responsive. */
  public void warmDetailedFillCache() {
    if (!this.isFilled) {
      return;
    }
    int layerIndex = this.getLayer();
    double maxClearanceLookupBoard = 2000.0 * this.board.communication.getResolution(Unit.UM);
    if (this.board.rules != null && this.board.rules.clearanceMatrix != null) {
      double maxMatrixClearance =
          this.board.rules.clearanceMatrix.maxValue(this.clearanceClassIndex(), layerIndex);
      maxClearanceLookupBoard =
          Math.max(
              maxClearanceLookupBoard,
              maxMatrixClearance + 100.0 * this.board.communication.getResolution(Unit.UM));
    }
    ensureDetailedFillCache(maxClearanceLookupBoard, layerIndex);
  }

  /**
   * Returns the cached detailed fill geometry for renderer-owned painting.
   *
   * <p>The cache remains owned by the conduction-area model because it depends on board revision
   * and clearance geometry. The renderer owns the AWT paint operation that consumes this geometry.
   */
  public java.awt.geom.Area getDetailedFillArea(double maxClearanceLookupBoard, int layerIndex) {
    ensureDetailedFillCache(maxClearanceLookupBoard, layerIndex);
    return cachedBoardFillArea;
  }

  private void ensureDetailedFillCache(double maxClearanceLookupBoard, int layerIndex) {
    boolean boardChanged = this.board.getRevision() != cachedBoardRevision;
    if (cachedBoardFillArea != null && !boardChanged) {
      return;
    }

    java.awt.geom.Area fillArea = getAwtAreaInBoardUnits(this.getArea());
    if (fillArea == null) {
      fillArea = new java.awt.geom.Area();
    }
    if (!fillArea.isEmpty()) {
      IntBox bbox = this.boundingBox();
      double spokeWidth = 400.0 * this.board.communication.getResolution(Unit.UM);
      int maxCl = (int) Math.round(maxClearanceLookupBoard);
      IntBox inflatedBbox =
          new IntBox(
              new IntPoint(bbox.ll.x - maxCl, bbox.ll.y - maxCl),
              new IntPoint(bbox.ur.x + maxCl, bbox.ur.y + maxCl));

      java.util.List<java.awt.geom.Area> foreignClearances = new java.util.ArrayList<>();
      java.util.List<java.awt.geom.Area> sameNetClearances = new java.util.ArrayList<>();
      java.util.List<java.awt.geom.Area> sameNetSpokesList = new java.util.ArrayList<>();

      Set<SearchTreeObject> overlaps = this.board.overlappingObjects(inflatedBbox, layerIndex);
      for (SearchTreeObject ob : overlaps) {
        if (!(ob instanceof Item currentItem) || currentItem == this) {
          continue;
        }
        if (!currentItem.sharesLayer(this)) {
          continue;
        }

        if (currentItem instanceof Trace || currentItem instanceof ConductionArea) {
          if (currentItem.sharesNet(this)) {
            continue;
          }
        }

        int clClass1 = this.clearanceClassIndex();
        int clClass2 = currentItem.clearanceClassIndex();
        double clearanceDist = this.board.clearanceValue(clClass1, clClass2, layerIndex);

        if (currentItem.sharesNet(this)) {
          if (currentItem instanceof DrillItem drillItem) {
            FloatPoint center = drillItem.getCenter().toFloat();
            Shape shape = drillItem.getShapeOnLayer(layerIndex);
            if (shape == null) {
              continue;
            }

            Shape enlargedShape = shape.enlarge(clearanceDist);
            java.awt.geom.Area clearanceAwt = getAwtAreaFromShapeInBoardUnits(enlargedShape);
            if (clearanceAwt == null) {
              continue;
            }

            IntBox itemBbox = drillItem.boundingBox();
            double maxDim = Math.max(itemBbox.width(), itemBbox.height());
            double expansionRadiusBoard = (maxDim / 2.0) + clearanceDist;

            double halfSpoke = spokeWidth / 2.0;
            java.awt.geom.Rectangle2D.Double baseSpoke =
                new java.awt.geom.Rectangle2D.Double(
                    center.x - halfSpoke,
                    center.y - expansionRadiusBoard,
                    spokeWidth,
                    2 * expansionRadiusBoard);

            java.awt.geom.AffineTransform rotP45 =
                java.awt.geom.AffineTransform.getRotateInstance(Math.PI / 4.0, center.x, center.y);
            java.awt.geom.AffineTransform rotM45 =
                java.awt.geom.AffineTransform.getRotateInstance(-Math.PI / 4.0, center.x, center.y);

            java.awt.geom.Area spokes =
                new java.awt.geom.Area(rotP45.createTransformedShape(baseSpoke));
            spokes.add(new java.awt.geom.Area(rotM45.createTransformedShape(baseSpoke)));

            spokes.intersect(clearanceAwt);

            sameNetClearances.add(clearanceAwt);
            sameNetSpokesList.add(spokes);
          }
        } else {
          if (currentItem instanceof DrillItem drillItem) {
            Shape shape = drillItem.getShapeOnLayer(layerIndex);
            if (shape != null) {
              Shape enlargedShape = shape.enlarge(clearanceDist);
              java.awt.geom.Area clearanceAwt = getAwtAreaFromShapeInBoardUnits(enlargedShape);
              if (clearanceAwt != null) {
                foreignClearances.add(clearanceAwt);
              }
            }
          } else {
            int shapeCount = currentItem.tileShapeCount();
            for (int i = 0; i < shapeCount; i++) {
              if (currentItem.shapeLayer(i) == layerIndex) {
                TileShape tileShape = currentItem.getTileShape(i);
                if (tileShape != null) {
                  Shape enlargedShape = tileShape.enlarge(clearanceDist);
                  java.awt.geom.Area clearanceAwt = getAwtAreaFromShapeInBoardUnits(enlargedShape);
                  if (clearanceAwt != null) {
                    foreignClearances.add(clearanceAwt);
                  }
                }
              }
            }
          }
        }
      }

      for (java.awt.geom.Area fa : foreignClearances) {
        fillArea.subtract(fa);
      }
      for (java.awt.geom.Area sa : sameNetClearances) {
        fillArea.subtract(sa);
      }
      for (java.awt.geom.Area sp : sameNetSpokesList) {
        fillArea.add(sp);
      }
    }
    cachedBoardFillArea = fillArea;
    cachedBoardRevision = this.board.getRevision();
  }

  private static java.awt.geom.Area getAwtAreaInBoardUnits(Area area) {
    if (area == null || area.isEmpty()) {
      return null;
    }
    if (area instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new java.awt.geom.Ellipse2D.Double(
              circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }

    Shape borderShape = area.getBorder();
    if (!(borderShape instanceof app.freerouting.geometry.planar.PolylineShape border)
        || !border.isBounded()) {
      return null;
    }

    java.awt.geom.Path2D.Double borderPath = new java.awt.geom.Path2D.Double();
    int count = border.borderLineCount();
    if (count > 0) {
      FloatPoint p0 = border.cornerApprox(0);
      borderPath.moveTo(p0.x, p0.y);
      for (int i = 1; i < count; i++) {
        FloatPoint pi = border.cornerApprox(i);
        borderPath.lineTo(pi.x, pi.y);
      }
      borderPath.closePath();
    }
    java.awt.geom.Area awtArea = new java.awt.geom.Area(borderPath);

    Shape[] holes = area.getHoles();
    for (Shape hole : holes) {
      java.awt.geom.Area holeArea = getAwtAreaFromShapeInBoardUnits(hole);
      if (holeArea != null) {
        awtArea.subtract(holeArea);
      }
    }
    return awtArea;
  }

  private static java.awt.geom.Area getAwtAreaFromShapeInBoardUnits(Shape shape) {
    if (shape == null) {
      return null;
    }
    if (shape instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new java.awt.geom.Ellipse2D.Double(
              circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }
    if (shape instanceof IntBox box) {
      return new java.awt.geom.Area(
          new java.awt.geom.Rectangle2D.Double(box.ll.x, box.ll.y, box.width(), box.height()));
    }
    if (shape instanceof app.freerouting.geometry.planar.PolylineShape poly) {
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      int count = poly.borderLineCount();
      if (count > 0) {
        FloatPoint p0 = poly.cornerApprox(0);
        path.moveTo(p0.x, p0.y);
        for (int i = 1; i < count; i++) {
          FloatPoint pi = poly.cornerApprox(i);
          path.lineTo(pi.x, pi.y);
        }
        path.closePath();
      }
      return new java.awt.geom.Area(path);
    }
    return null;
  }

  @Override
  public Item copy(int idNo) {
    if (this.netCount() != 1) {
      FRLogger.warn("ConductionArea.copy not yet implemented for areas with more than 1 net");
      return null;
    }
    return new ConductionArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        netNumbers,
        clearanceClassIndex(),
        idNo,
        getComponentNo(),
        this.name,
        isObstacle,
        getFixedState(),
        board);
  }

  @Override
  public Set<Item> getNormalContacts() {
    Set<Item> result = new TreeSet<>();
    for (int i = 0; i < tileShapeCount(); i++) {
      TileShape currentShape = getTileShape(i);
      Set<SearchTreeObject> overlaps = board.overlappingObjects(currentShape, getLayer());
      for (SearchTreeObject currentObject : overlaps) {
        if (!(currentObject instanceof Item currentItem)) {
          continue;
        }
        if (currentItem != this && currentItem.sharesNet(this) && currentItem.sharesLayer(this)) {
          if (currentItem instanceof Trace currentTrace) {
            if (currentShape.contains(currentTrace.firstCorner())
                || currentShape.contains(currentTrace.lastCorner())) {
              result.add(currentItem);
            }
          } else if (currentItem instanceof DrillItem currentDrillItem) {
            if (currentShape.contains(currentDrillItem.getCenter())) {
              result.add(currentItem);
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public TileShape getTraceConnectionShape(ShapeSearchTree searchTree, int index) {
    if (index < 0 || index >= this.treeShapeCount(searchTree)) {
      FRLogger.warn("ConductionArea.get_trace_connection_shape index out of range");
      return null;
    }
    return this.getTreeShape(searchTree, index);
  }

  @Override
  public Point[] getRatsnestCorners() {
    Point[] result;
    FloatPoint[] corners = this.getArea().cornerApproxArr();
    result = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      result[i] = corners[i].round();
    }

    return result;
  }

  @Override
  public boolean isObstacle(Item other) {
    if (this.isObstacle) {
      return super.isObstacle(other);
    }
    return false;
  }

  /** Returns if this conduction area is regarded as obstacle to traces of foreign nets. */
  public boolean getIsObstacle() {
    return this.isObstacle;
  }

  /** Sets, if this conduction area is regarded as obstacle to traces and vias of foreign nets. */
  public void setIsObstacle(boolean value) {
    this.isObstacle = value;
  }

  @Override
  public boolean isTraceObstacle(int netNumber) {
    return this.isObstacle && !this.containsNet(netNumber);
  }

  @Override
  public boolean isDrillable(int netNumber) {
    return !this.isObstacle || this.containsNet(netNumber);
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.CONDUCTION);
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("conductionArea"));
    this.printShapeInfo(window, locale);
    this.printConnectableItemInfo(window, locale);
    window.newline();
  }
}
