package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** A ObstacleArea, which can be electrically connected to other items. */
public class ConductionArea extends ObstacleArea implements Connectable {

  private static final double PLANE_FILL_SCALE = 2.5;
  private static final double PLANE_HATCH_OPACITY = 0.85;

  /**
   * When the on-screen size of a typical clearance gap is below this threshold (pixels), plane
   * pours are drawn as solid fills. The previous 1.5 px cutoff never triggered at zoom-to-fit,
   * forcing expensive clearance CSG on the first paint of large copper pours.
   */
  private static final double PLANE_SIMPLE_FILL_MAX_CLEARANCE_SCREEN_PX = 15.0;

  private boolean isObstacle;
  private boolean isFilled = true;

  public boolean getIsFilled() {
    return this.isFilled;
  }

  public void setIsFilled(boolean pValue) {
    this.isFilled = pValue;
    this.clearDerivedData();
  }

  private transient java.awt.geom.Area cachedFillArea;
  private transient app.freerouting.boardgraphics.CoordinateTransform cachedFillTransform;
  private transient int cachedBoardRevision = -1;
  private transient java.awt.geom.Area cachedBoardFillArea;

  /** Creates a new instance of ConductionArea */
  ConductionArea(
      Area pArea,
      int pLayer,
      Vector pTranslation,
      double pRotationInDegree,
      boolean pSideChanged,
      int[] pNetNoArr,
      int pClearanceClass,
      int pIdNo,
      int pGroupNo,
      String pName,
      boolean pIsObstacle,
      FixedState pFixedState,
      BasicBoard pBoard) {
    super(
        pArea,
        pLayer,
        pTranslation,
        pRotationInDegree,
        pSideChanged,
        pNetNoArr,
        pClearanceClass,
        pIdNo,
        pGroupNo,
        pName,
        pFixedState,
        pBoard);
    isObstacle = pIsObstacle;
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.cachedFillArea = null;
    this.cachedFillTransform = null;
    this.cachedBoardRevision = -1;
    this.cachedBoardFillArea = null;
  }

  @Override
  public void draw(
      Graphics pG, GraphicsContext pGraphicsContext, Color[] pColorArr, double pIntensity) {
    if (pGraphicsContext == null || pIntensity <= 0) {
      return;
    }
    int layerNo = this.getLayer();
    double layerVis = pGraphicsContext.getLayerVisibility(layerNo);
    if (layerVis <= 0) {
      return;
    }

    Color color = pColorArr[layerNo];
    if (this.isFilled) {
      double fillOpacity = Math.min(layerVis * pIntensity * PLANE_FILL_SCALE, 1.0);

      double maxClearanceLookupBoard = 2000.0 * this.board.communication.getResolution(Unit.UM);
      if (this.board.rules != null && this.board.rules.clearanceMatrix != null) {
        double maxMatrixClearance =
            this.board.rules.clearanceMatrix.maxValue(this.clearanceClassNo(), layerNo);
        maxClearanceLookupBoard =
            Math.max(
                maxClearanceLookupBoard,
                maxMatrixClearance + 100.0 * this.board.communication.getResolution(Unit.UM));
      }
      double clearanceScreenPx =
          pGraphicsContext.coordinateTransform.boardToScreen(maxClearanceLookupBoard);
      boolean useSimpleFill =
          clearanceScreenPx < PLANE_SIMPLE_FILL_MAX_CLEARANCE_SCREEN_PX
              || pGraphicsContext.isSimplifiedPlaneRendering();

      if (useSimpleFill) {
        pGraphicsContext.fillArea(this.getArea(), pG, color, fillOpacity);
      } else {
        ensureDetailedFillCache(maxClearanceLookupBoard, layerNo);

        if (cachedBoardFillArea != null && !cachedBoardFillArea.isEmpty()) {
          Point2D p0 = pGraphicsContext.coordinateTransform.boardToScreen(FloatPoint.ZERO);
          Point2D px = pGraphicsContext.coordinateTransform.boardToScreen(new FloatPoint(1, 0));
          Point2D py = pGraphicsContext.coordinateTransform.boardToScreen(new FloatPoint(0, 1));

          double m00 = px.getX() - p0.getX();
          double m10 = px.getY() - p0.getY();
          double m01 = py.getX() - p0.getX();
          double m11 = py.getY() - p0.getY();
          double m02 = p0.getX();
          double m12 = p0.getY();

          java.awt.geom.AffineTransform boardToScreen =
              new java.awt.geom.AffineTransform(m00, m10, m01, m11, m02, m12);
          java.awt.geom.Area screenArea = cachedBoardFillArea.createTransformedArea(boardToScreen);

          java.awt.Graphics2D g2 = (java.awt.Graphics2D) pG;
          java.awt.Paint oldPaint = g2.getPaint();
          java.awt.Composite oldComposite = g2.getComposite();

          g2.setColor(color);
          g2.setComposite(
              java.awt.AlphaComposite.getInstance(
                  java.awt.AlphaComposite.SRC_OVER, (float) fillOpacity));
          g2.setRenderingHint(
              java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          g2.fill(screenArea);

          g2.setPaint(oldPaint);
          g2.setComposite(oldComposite);
        }
      }
    }

    // Hatch border (0.5 mm in board units)
    double hatchPitch = 500.0 * this.board.communication.getResolution(Unit.UM);
    pGraphicsContext.drawPlaneHatch(
        this.getArea(), pG, color, layerVis * pIntensity * PLANE_HATCH_OPACITY, hatchPitch);

    // Border outline
    pGraphicsContext.drawBoundary(this.getArea(), 0.0, color, pG, layerVis);
  }

  /** Pre-computes detailed plane-fill geometry off the EDT so zoom-in paints stay responsive. */
  public void warmDetailedFillCache() {
    if (!this.isFilled) {
      return;
    }
    int layerNo = this.getLayer();
    double maxClearanceLookupBoard = 2000.0 * this.board.communication.getResolution(Unit.UM);
    if (this.board.rules != null && this.board.rules.clearanceMatrix != null) {
      double maxMatrixClearance =
          this.board.rules.clearanceMatrix.maxValue(this.clearanceClassNo(), layerNo);
      maxClearanceLookupBoard =
          Math.max(
              maxClearanceLookupBoard,
              maxMatrixClearance + 100.0 * this.board.communication.getResolution(Unit.UM));
    }
    ensureDetailedFillCache(maxClearanceLookupBoard, layerNo);
  }

  private void ensureDetailedFillCache(double maxClearanceLookupBoard, int layerNo) {
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

      Set<SearchTreeObject> overlaps = this.board.overlappingObjects(inflatedBbox, layerNo);
      for (SearchTreeObject ob : overlaps) {
        if (!(ob instanceof Item currItem) || currItem == this) {
          continue;
        }
        if (!currItem.sharesLayer(this)) {
          continue;
        }

        if (currItem instanceof Trace || currItem instanceof ConductionArea) {
          if (currItem.sharesNet(this)) {
            continue;
          }
        }

        int clClass1 = this.clearanceClassNo();
        int clClass2 = currItem.clearanceClassNo();
        double clearanceDist = this.board.clearanceValue(clClass1, clClass2, layerNo);

        if (currItem.sharesNet(this)) {
          if (currItem instanceof DrillItem drillItem) {
            FloatPoint center = drillItem.getCenter().toFloat();
            Shape shape = drillItem.getShapeOnLayer(layerNo);
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
          if (currItem instanceof DrillItem drillItem) {
            Shape shape = drillItem.getShapeOnLayer(layerNo);
            if (shape != null) {
              Shape enlargedShape = shape.enlarge(clearanceDist);
              java.awt.geom.Area clearanceAwt = getAwtAreaFromShapeInBoardUnits(enlargedShape);
              if (clearanceAwt != null) {
                foreignClearances.add(clearanceAwt);
              }
            }
          } else {
            int shapeCount = currItem.tileShapeCount();
            for (int i = 0; i < shapeCount; i++) {
              if (currItem.shapeLayer(i) == layerNo) {
                TileShape tileShape = currItem.getTileShape(i);
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

  private static java.awt.geom.Area getAwtAreaInBoardUnits(Area pArea) {
    if (pArea == null || pArea.isEmpty()) {
      return null;
    }
    if (pArea instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new java.awt.geom.Ellipse2D.Double(
              circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }

    Shape borderShape = pArea.getBorder();
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

    Shape[] holes = pArea.getHoles();
    for (Shape hole : holes) {
      java.awt.geom.Area holeArea = getAwtAreaFromShapeInBoardUnits(hole);
      if (holeArea != null) {
        awtArea.subtract(holeArea);
      }
    }
    return awtArea;
  }

  private static java.awt.geom.Area getAwtAreaFromShapeInBoardUnits(Shape pShape) {
    if (pShape == null) {
      return null;
    }
    if (pShape instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new java.awt.geom.Ellipse2D.Double(
              circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }
    if (pShape instanceof IntBox box) {
      return new java.awt.geom.Area(
          new java.awt.geom.Rectangle2D.Double(box.ll.x, box.ll.y, box.width(), box.height()));
    }
    if (pShape instanceof app.freerouting.geometry.planar.PolylineShape poly) {
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
  public Item copy(int pIdNo) {
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
        netNoArr,
        clearanceClassNo(),
        pIdNo,
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
      TileShape currShape = getTileShape(i);
      Set<SearchTreeObject> overlaps = board.overlappingObjects(currShape, getLayer());
      for (SearchTreeObject currOb : overlaps) {
        if (!(currOb instanceof Item currItem)) {
          continue;
        }
        if (currItem != this && currItem.sharesNet(this) && currItem.sharesLayer(this)) {
          if (currItem instanceof Trace currTrace) {
            if (currShape.contains(currTrace.firstCorner())
                || currShape.contains(currTrace.lastCorner())) {
              result.add(currItem);
            }
          } else if (currItem instanceof DrillItem curr_drill_item) {
            if (currShape.contains(curr_drill_item.getCenter())) {
              result.add(currItem);
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public TileShape getTraceConnectionShape(ShapeSearchTree pSearchTree, int pIndex) {
    if (pIndex < 0 || pIndex >= this.treeShapeCount(pSearchTree)) {
      FRLogger.warn("ConductionArea.get_trace_connection_shape p_index out of range");
      return null;
    }
    return this.getTreeShape(pSearchTree, pIndex);
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
  public boolean isObstacle(Item pOther) {
    if (this.isObstacle) {
      return super.isObstacle(pOther);
    }
    return false;
  }

  /** Returns if this conduction area is regarded as obstacle to traces of foreign nets. */
  public boolean getIsObstacle() {
    return this.isObstacle;
  }

  /** Sets, if this conduction area is regarded as obstacle to traces and vias of foreign nets. */
  public void setIsObstacle(boolean pValue) {
    this.isObstacle = pValue;
  }

  @Override
  public boolean isTraceObstacle(int pNetNo) {
    return this.isObstacle && !this.containsNet(pNetNo);
  }

  @Override
  public boolean isDrillable(int pNetNo) {
    return !this.isObstacle || this.containsNet(pNetNo);
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter pFilter) {
    if (!this.isSelectedByFixedFilter(pFilter)) {
      return false;
    }
    return pFilter.isSelected(ItemSelectionFilter.SelectableChoices.CONDUCTION);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getTraceColors(true);
  }

  @Override
  public double getDrawIntensity(GraphicsContext pGraphicsContext) {
    return pGraphicsContext.getConductionColorIntensity();
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("conductionArea"));
    this.printShapeInfo(pWindow, pLocale);
    this.printConnectableItemInfo(pWindow, pLocale);
    pWindow.newline();
  }
}
