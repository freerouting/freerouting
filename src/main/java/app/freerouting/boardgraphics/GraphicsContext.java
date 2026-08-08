package app.freerouting.boardgraphics;

import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.Ellipse;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

/** Context for drawing items in the board package to the screen. */
public class GraphicsContext implements Serializable {

  private static final int update_offset = 10000;
  private static final int virtual_layer_count = 6;
  private static final boolean show_line_segments = false;
  private static final boolean show_area_division = false;
  public transient ItemColorTableModel itemColorTable;
  public transient OtherColorTableModel otherColorTable;
  public ColorIntensityTable colorIntensityTable;
  public CoordinateTransform coordinateTransform;

  /**
   * layerVisibilityArr[i] is between 0 and 1, for each layer i, 0 is invisible and 1 fully visible.
   */
  private double[] layerVisibilityArr;

  /**
   * The factor for automatic layer dimming of layers different from the current layer. Values are
   * between 0 and 1. If 1, there is no automatic layer dimming.
   */
  private double autoLayerDimFactor = 0.7;

  /** The layer, which is not automatically dimmed. */
  private int fullyVisibleLayer = -1;

  private boolean[] virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
  private int fullyVisibleVirtualLayer = -1;

  /** When true, copper pours use fast solid fills (used during the first paint after load). */
  private transient boolean simplifiedPlaneRendering;

  public GraphicsContext(
      IntBox pDesignBounds,
      Dimension pPanelBounds,
      LayerStructure pLayerStructure,
      Locale pLocale) {
    coordinateTransform = new CoordinateTransform(pDesignBounds, pPanelBounds);
    itemColorTable = new ItemColorTableModel(pLayerStructure, pLocale);
    otherColorTable = new OtherColorTableModel(pLocale);
    colorIntensityTable = new ColorIntensityTable();
    layerVisibilityArr = new double[pLayerStructure.arr.length];
    for (int i = 0; i < layerVisibilityArr.length; i++) {
      if (pLayerStructure.arr[i].isSignal) {
        layerVisibilityArr[i] = 1.00;
      } else {
        layerVisibilityArr[i] = 0.25;
      }
    }
  }

  /** Copy constructor */
  public GraphicsContext(GraphicsContext pGraphicsContext) {
    this.coordinateTransform = new CoordinateTransform(pGraphicsContext.coordinateTransform);
    this.itemColorTable = new ItemColorTableModel(pGraphicsContext.itemColorTable);
    this.otherColorTable = new OtherColorTableModel(pGraphicsContext.otherColorTable);
    this.colorIntensityTable = new ColorIntensityTable(pGraphicsContext.colorIntensityTable);
    this.layerVisibilityArr = pGraphicsContext.copyLayerVisibilityArr();
    this.virtualLayerVisibilityArr = pGraphicsContext.getVirtualLayerVisibilityArr().clone();
    this.fullyVisibleVirtualLayer = pGraphicsContext.fullyVisibleVirtualLayer;
  }

  private static boolean[] createDefaultVirtualLayerVisibilityArr() {
    return new boolean[] {true, true, true, true, true, true};
  }

  private boolean[] getVirtualLayerVisibilityArr() {
    if (virtualLayerVisibilityArr == null || virtualLayerVisibilityArr.length == 0) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    return virtualLayerVisibilityArr;
  }

  /** initialise some values in p_graphics */
  private static void initDrawGraphics(Graphics2D pGraphics, Color pColor, float pWidth) {
    BasicStroke bs =
        new BasicStroke(Math.max(pWidth, 0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    pGraphics.setStroke(bs);
    pGraphics.setColor(pColor);
    pGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  static void setTranslucency(Graphics2D pG2, double pFactor) {
    AlphaComposite currAlphaComposite;
    if (pFactor >= 0) {
      currAlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) pFactor);
    } else {
      currAlphaComposite = AlphaComposite.getInstance(AlphaComposite.DST_OVER, (float) -pFactor);
    }
    pG2.setComposite(currAlphaComposite);
  }

  /**
   * Changes the bounds of the board design to p_design_bounds. Useful when components are still
   * placed outside the board.
   */
  public void changeDesignBounds(IntBox pNewDesignBounds) {
    if (pNewDesignBounds.equals(this.coordinateTransform.designBox)) {
      return;
    }
    Dimension screenBounds = this.coordinateTransform.screenBounds;
    this.coordinateTransform = new CoordinateTransform(pNewDesignBounds, screenBounds);
  }

  /** changes the size of the panel to p_new_bounds */
  public void changePanelSize(Dimension pNewBounds) {
    if (coordinateTransform == null) {
      return;
    }
    IntBox designBox = coordinateTransform.designBox;
    boolean leftRightSwapped = coordinateTransform.isMirrorLeftRight();
    boolean topBottomSwapped = coordinateTransform.isMirrorTopBottom();
    double rotation = coordinateTransform.getRotation();
    coordinateTransform = new CoordinateTransform(designBox, pNewBounds);
    coordinateTransform.setMirrorLeftRight(leftRightSwapped);
    coordinateTransform.setMirrorTopBottom(topBottomSwapped);
    coordinateTransform.setRotation(rotation);
  }

  /** draws a polygon with corners p_points */
  public void draw(
      FloatPoint[] pPoints,
      double pHalfWidth,
      Color pColor,
      Graphics pG,
      double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) pG;
    Rectangle clipShape = pG.getClip().getBounds();
    // the class member updateBox cannot be used here, because
    // the dirty rectangle is internally enlarged by the system.
    // Therefore, we can not improve the performance by using an
    // update octagon instead of a box.
    IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
    double scaledWidth = coordinateTransform.boardToScreen(pHalfWidth);

    initDrawGraphics(g2, pColor, (float) scaledWidth * 2);
    setTranslucency(g2, pTranslucencyFactor);

    GeneralPath drawPath = null;
    if (!show_line_segments) {
      drawPath = new GeneralPath();
    }

    for (int i = 0; i < (pPoints.length - 1); i++) {
      if (lineOutsideUpdateBox(pPoints[i], pPoints[i + 1], pHalfWidth + update_offset, clipBox)) {
        // this check should be unnecessary here,
        // the system should do it in the draw(line) function
        continue;
      }
      Point2D p1 = coordinateTransform.boardToScreen(pPoints[i]);
      Point2D p2 = coordinateTransform.boardToScreen(pPoints[i + 1]);
      Line2D line = new Line2D.Double(p1, p2);

      if (show_line_segments) {
        g2.draw(line);
      } else {
        drawPath.append(line, false);
      }
    }
    if (!show_line_segments) {
      g2.draw(drawPath);
    }
  }

  /*
   * draws the boundary of a circle
   */
  public void drawCircle(
      FloatPoint pCenter,
      double pRadius,
      double pDrawHalfWidth,
      Color pColor,
      Graphics pG,
      double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) pG;
    Point2D center = coordinateTransform.boardToScreen(pCenter);

    double radius = coordinateTransform.boardToScreen(pRadius);
    double diameter = 2 * radius;
    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(pDrawHalfWidth));
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    setTranslucency(g2, pTranslucencyFactor);
    initDrawGraphics(g2, pColor, drawWidth);
    g2.draw(circle);
  }

  /*
   * draws a rectangle
   */
  public void drawRectangle(
      FloatPoint pCorner1,
      FloatPoint pCorner2,
      double pDrawHalfWidth,
      Color pColor,
      Graphics pG,
      double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) pG;
    Point2D corner1 = coordinateTransform.boardToScreen(pCorner1);
    Point2D corner2 = coordinateTransform.boardToScreen(pCorner2);

    double xmin = Math.min(corner1.getX(), corner2.getX());
    double ymin = Math.min(corner1.getY(), corner2.getY());

    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(pDrawHalfWidth));
    double width = Math.abs(corner2.getX() - corner1.getX());
    double height = Math.abs(corner2.getY() - corner1.getY());
    Rectangle2D rectangle = new Rectangle2D.Double(xmin, ymin, width, height);
    setTranslucency(g2, pTranslucencyFactor);
    initDrawGraphics(g2, pColor, drawWidth);
    g2.draw(rectangle);
  }

  /** Draws the boundary of p_shape. */
  public void drawBoundary(
      Shape pShape, double pDrawHalfWidth, Color pColor, Graphics pG, double pTranslucencyFactor) {
    if (pShape instanceof PolylineShape) {
      FloatPoint[] drawCorners = pShape.cornerApproxArr();
      if (drawCorners.length <= 1) {
        return;
      }
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      this.draw(closedDrawCorners, pDrawHalfWidth, pColor, pG, pTranslucencyFactor);
    } else if (pShape instanceof Circle curr_circle) {
      this.drawCircle(
          curr_circle.center.toFloat(),
          curr_circle.radius,
          pDrawHalfWidth,
          pColor,
          pG,
          pTranslucencyFactor);
    }
  }

  /** Draws the boundary of p_area. */
  public void drawBoundary(
      Area pArea, double pDrawHalfWidth, Color pColor, Graphics pG, double pTranslucencyFactor) {
    drawBoundary(pArea.getBorder(), pDrawHalfWidth, pColor, pG, pTranslucencyFactor);
    Shape[] holes = pArea.getHoles();
    for (int i = 0; i < holes.length; i++) {
      drawBoundary(holes[i], pDrawHalfWidth, pColor, pG, pTranslucencyFactor);
    }
  }

  private transient java.awt.TexturePaint cachedHatchPaint;
  private transient double cachedHatchPitchPx = -1.0;
  private transient Color cachedHatchColor;

  public java.awt.geom.Area getAwtArea(Area pArea) {
    if (pArea == null || pArea.isEmpty()) {
      return null;
    }
    if (pArea instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }

    Shape borderShape = pArea.getBorder();
    if (!(borderShape instanceof PolylineShape border) || !border.isBounded()) {
      return null;
    }

    java.awt.geom.Path2D.Double borderPath = new java.awt.geom.Path2D.Double();
    int count = border.borderLineCount();
    if (count > 0) {
      Point2D p0 = coordinateTransform.boardToScreen(border.cornerApprox(0));
      borderPath.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.boardToScreen(border.cornerApprox(i));
        borderPath.lineTo(pi.getX(), pi.getY());
      }
      borderPath.closePath();
    }
    java.awt.geom.Area awtArea = new java.awt.geom.Area(borderPath);

    Shape[] holes = pArea.getHoles();
    for (Shape hole : holes) {
      if (hole instanceof PolylineShape holePoly) {
        int hCount = holePoly.borderLineCount();
        if (hCount > 0) {
          java.awt.geom.Path2D.Double holePath = new java.awt.geom.Path2D.Double();
          Point2D hp0 = coordinateTransform.boardToScreen(holePoly.cornerApprox(0));
          holePath.moveTo(hp0.getX(), hp0.getY());
          for (int i = 1; i < hCount; i++) {
            Point2D hpi = coordinateTransform.boardToScreen(holePoly.cornerApprox(i));
            holePath.lineTo(hpi.getX(), hpi.getY());
          }
          holePath.closePath();
          awtArea.subtract(new java.awt.geom.Area(holePath));
        }
      } else if (hole instanceof Circle circle) {
        Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
        double radius = coordinateTransform.boardToScreen(circle.radius);
        double diameter = 2 * radius;
        awtArea.subtract(
            new java.awt.geom.Area(
                new Ellipse2D.Double(
                    center.getX() - radius, center.getY() - radius, diameter, diameter)));
      }
    }
    return awtArea;
  }

  public void drawPlaneHatch(
      Area pArea, Graphics pG, Color pColor, double pTranslucencyFactor, double pPitchBoardUnits) {
    if (pColor == null || pArea == null || pArea.isEmpty() || pTranslucencyFactor <= 0) {
      return;
    }
    double pitchPx = coordinateTransform.boardToScreen(pPitchBoardUnits);
    if (pitchPx < 2.0) {
      return;
    }
    if (pitchPx > 1000.0) {
      pitchPx = 1000.0;
    }
    int pInt = (int) Math.round(pitchPx);

    java.awt.geom.Area outerArea = getAwtArea(pArea);
    if (outerArea == null || outerArea.isEmpty()) {
      return;
    }

    Graphics2D g2 = (Graphics2D) pG;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();

    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (cachedHatchPaint == null
        || cachedHatchPitchPx != pitchPx
        || !pColor.equals(cachedHatchColor)) {
      java.awt.image.BufferedImage bi =
          new java.awt.image.BufferedImage(pInt, pInt, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2t = bi.createGraphics();
      g2t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2t.setColor(pColor);
      g2t.setStroke(new BasicStroke(1.0f));
      g2t.drawLine(0, pInt, pInt, 0);
      g2t.drawLine(-1, pInt + 1, pInt + 1, -1);
      g2t.dispose();
      cachedHatchPaint = new java.awt.TexturePaint(bi, new Rectangle2D.Double(0, 0, pInt, pInt));
      cachedHatchPitchPx = pitchPx;
      cachedHatchColor = pColor;
    }

    java.awt.Shape oldClip = g2.getClip();
    java.awt.Stroke oldStroke = g2.getStroke();
    g2.clip(outerArea);
    g2.setPaint(cachedHatchPaint);
    g2.setStroke(
        new BasicStroke((float) (2 * pitchPx), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    g2.draw(outerArea);

    g2.setStroke(oldStroke);
    g2.setClip(oldClip);
    g2.setPaint(oldPaint);
    g2.setComposite(oldComposite);
  }

  public java.awt.geom.Area getAwtAreaFromShape(Shape pShape) {
    if (pShape == null) {
      return null;
    }
    if (pShape instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }
    if (pShape instanceof PolylineShape poly) {
      int count = poly.borderLineCount();
      if (count <= 0) {
        return null;
      }
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      Point2D p0 = coordinateTransform.boardToScreen(poly.cornerApprox(0));
      path.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.boardToScreen(poly.cornerApprox(i));
        path.lineTo(pi.getX(), pi.getY());
      }
      path.closePath();
      return new java.awt.geom.Area(path);
    }
    return null;
  }

  public record ClearanceItem(java.awt.geom.Area area) {}

  public record ThermalReliefItem(
      java.awt.geom.Area clearanceArea,
      double cx,
      double cy,
      double expansionRadiusPx,
      double spokeWidthPx) {}

  public void fillPlaneArea(
      Area pArea,
      Graphics pG,
      Color pColor,
      double pTranslucencyFactor,
      java.util.List<ClearanceItem> pClearances,
      java.util.List<ThermalReliefItem> pThermals) {
    if (pColor == null || pArea == null || pArea.isEmpty() || pTranslucencyFactor <= 0) {
      return;
    }

    java.awt.geom.Area fillArea = getAwtArea(pArea);
    if (fillArea == null || fillArea.isEmpty()) {
      return;
    }

    // Subtract foreign clearances
    if (pClearances != null) {
      for (ClearanceItem item : pClearances) {
        if (item != null && item.area != null) {
          fillArea.subtract(item.area);
        }
      }
    }

    // Process thermal reliefs
    if (pThermals != null) {
      for (ThermalReliefItem thermal : pThermals) {
        if (thermal == null || thermal.clearanceArea == null) {
          continue;
        }
        fillArea.subtract(thermal.clearanceArea);

        // Create 4 diagonal spokes at 45 degrees
        double halfSpoke = thermal.spokeWidthPx / 2.0;
        double r = thermal.expansionRadiusPx;

        // Rotated rectangles (NE-SW and NW-SE)
        Rectangle2D.Double baseSpoke =
            new Rectangle2D.Double(
                thermal.cx - halfSpoke, thermal.cy - r, thermal.spokeWidthPx, 2 * r);

        AffineTransform rotP45 =
            AffineTransform.getRotateInstance(Math.PI / 4.0, thermal.cx, thermal.cy);
        AffineTransform rotM45 =
            AffineTransform.getRotateInstance(-Math.PI / 4.0, thermal.cx, thermal.cy);

        java.awt.geom.Area spokes =
            new java.awt.geom.Area(rotP45.createTransformedShape(baseSpoke));
        spokes.add(new java.awt.geom.Area(rotM45.createTransformedShape(baseSpoke)));

        // Restrict spokes to the clearance gap
        spokes.intersect(thermal.clearanceArea);

        fillArea.add(spokes);
      }
    }

    if (fillArea.isEmpty()) {
      return;
    }

    Graphics2D g2 = (Graphics2D) pG;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();

    g2.setColor(pColor);
    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(fillArea);

    g2.setPaint(oldPaint);
    g2.setComposite(oldComposite);
  }

  /** Draws the interior of a circle */
  public void fillCircle(Circle pCircle, Graphics pG, Color pColor, double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    Point2D center = coordinateTransform.boardToScreen(pCircle.center.toFloat());
    double radius = coordinateTransform.boardToScreen(pCircle.radius);
    if (!pointNearRectangle(center.getX(), center.getY(), pG.getClip().getBounds(), radius)) {
      return;
    }
    double diameter = 2 * radius;
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    Graphics2D g2 = (Graphics2D) pG;
    g2.setColor(pColor);
    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(circle);
  }

  /** Draws the interior of an ellipse. */
  public void fillEllipse(Ellipse pEllipse, Graphics pG, Color pColor, double pTranslucencyFactor) {
    Ellipse[] ellipseArr = new Ellipse[1];
    ellipseArr[0] = pEllipse;
    fillEllipseArr(ellipseArr, pG, pColor, pTranslucencyFactor);
  }

  /**
   * Draws the interior of an array of ellipses. Ellipses contained in another ellipse are treated
   * as holes.
   */
  public void fillEllipseArr(
      Ellipse[] pEllipseArr, Graphics pG, Color pColor, double pTranslucencyFactor) {
    if (pColor == null || pEllipseArr.length == 0) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (Ellipse currEllipse : pEllipseArr) {
      Point2D center = coordinateTransform.boardToScreen(currEllipse.center);
      double biggerRadius = coordinateTransform.boardToScreen(currEllipse.biggerRadius);
      if (!pointNearRectangle(
          center.getX(), center.getY(), pG.getClip().getBounds(), biggerRadius)) {
        continue;
      }
      double smallerRadius = coordinateTransform.boardToScreen(currEllipse.smallerRadius);
      Ellipse2D drawEllipse =
          new Ellipse2D.Double(
              center.getX() - biggerRadius,
              center.getY() - smallerRadius,
              2 * biggerRadius,
              2 * smallerRadius);
      double rotation = coordinateTransform.boardToScreenAngle(currEllipse.rotation);
      AffineTransform affineTransform = new AffineTransform();
      affineTransform.rotate(rotation, center.getX(), center.getY());
      java.awt.Shape rotatedEllipse = affineTransform.createTransformedShape(drawEllipse);
      drawPath.append(rotatedEllipse, false);
    }
    Graphics2D g2 = (Graphics2D) pG;
    g2.setColor(pColor);
    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** Checks, if the distance of the point with coordinates p_x, p_y to p_rect is at most p_dist. */
  private boolean pointNearRectangle(double pX, double pY, Rectangle pRect, double pDist) {
    if (pX < pRect.x - pDist) {
      return false;
    }
    if (pY < pRect.y - pDist) {
      return false;
    }
    if (pX > pRect.x + pRect.width + pDist) {
      return false;
    }
    return pY <= pRect.y + pRect.height + pDist;
  }

  /** Fill the interior of the polygon shape represented by p_points. */
  public void fillShape(
      FloatPoint[] pPoints, Graphics pG, Color pColor, double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) pG;
    Polygon drawPolygon = new Polygon();
    for (int i = 0; i < pPoints.length; i++) {
      Point2D currCorner = coordinateTransform.boardToScreen(pPoints[i]);
      drawPolygon.addPoint(
          (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
    }
    g2.setColor(pColor);
    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPolygon);
  }

  /**
   * Fill the interior of a list of polygons. Used for example with an area consisting of a border
   * polygon and some holes.
   */
  public void fillArea(
      FloatPoint[][] pPointLists, Graphics pG, Color pColor, double pTranslucencyFactor) {
    if (pColor == null) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (int j = 0; j < pPointLists.length; j++) {
      Polygon drawPolygon = new Polygon();
      FloatPoint[] currPointList = pPointLists[j];
      for (int i = 0; i < currPointList.length; i++) {
        Point2D currCorner = coordinateTransform.boardToScreen(currPointList[i]);
        drawPolygon.addPoint(
            (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
      }
      drawPath.append(drawPolygon, false);
    }
    Graphics2D g2 = (Graphics2D) pG;
    g2.setColor(pColor);
    setTranslucency(g2, pTranslucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** draws the interior of an item of class geometry.planar.Area */
  public void fillArea(Area pArea, Graphics pG, Color pColor, double pTranslucencyFactor) {
    if (pColor == null || pArea.isEmpty()) {
      return;
    }
    if (pArea instanceof Circle circle) {
      fillCircle(circle, pG, pColor, pTranslucencyFactor);
    } else {
      PolylineShape border = (PolylineShape) pArea.getBorder();
      if (!border.isBounded()) {
        FRLogger.warn("GraphicsContext.fill_area: shape not bounded");
        return;
      }
      Rectangle clipShape = pG.getClip().getBounds();
      IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
      if (!border.boundingBox().intersects(clipBox)) {
        return;
      }
      Shape[] holes = pArea.getHoles();

      FloatPoint[][] drawPolygons = new FloatPoint[holes.length + 1][];
      for (int j = 0; j < drawPolygons.length; j++) {
        PolylineShape currDrawShape;
        if (j == 0) {
          currDrawShape = border;
        } else {
          currDrawShape = (PolylineShape) holes[j - 1];
        }
        drawPolygons[j] = new FloatPoint[currDrawShape.borderLineCount() + 1];
        FloatPoint[] currDrawPolygon = drawPolygons[j];
        for (int i = 0; i < currDrawPolygon.length - 1; i++) {
          currDrawPolygon[i] = currDrawShape.cornerApprox(i);
        }
        // close the polygon
        currDrawPolygon[currDrawPolygon.length - 1] = currDrawPolygon[0];
      }
      fillArea(drawPolygons, pG, pColor, pTranslucencyFactor);
    }
    if (show_area_division) {
      TileShape[] tiles = pArea.splitToConvex();
      for (int i = 0; i < tiles.length; i++) {
        FloatPoint[] corners = new FloatPoint[tiles[i].borderLineCount() + 1];
        TileShape currTile = tiles[i];
        for (int j = 0; j < corners.length - 1; j++) {
          corners[j] = currTile.cornerApprox(j);
        }
        corners[corners.length - 1] = corners[0];
        draw(corners, 1, Color.white, pG, 0.7);
      }
    }
  }

  public Color getBackgroundColor() {
    return otherColorTable.getBackgroundColor();
  }

  public Color getHilightColor() {
    return otherColorTable.getHilightColor();
  }

  public Color getIncompleteColor() {
    return otherColorTable.getIncompleteColor();
  }

  public Color getOutlineColor() {
    return otherColorTable.getOutlineColor();
  }

  public Color getComponentColor(boolean pFront) {
    return otherColorTable.getComponentColor(pFront);
  }

  public Color getViolationsColor() {
    return otherColorTable.getViolationsColor();
  }

  public Color getLengthMatchingAreaColor() {
    return otherColorTable.getLengthMatchingAreaColor();
  }

  public Color[] getTraceColors(boolean pFixed) {

    return itemColorTable.getTraceColors(pFixed);
  }

  public Color[] getViaColors(boolean pFixed) {
    return itemColorTable.getViaColors(pFixed);
  }

  public Color[] getPinColors() {
    return itemColorTable.getPinColors();
  }

  public Color[] getConductionColors() {
    return itemColorTable.getConductionColors();
  }

  public Color[] getObstacleColors() {
    return itemColorTable.getObstacleColors();
  }

  public Color[] getViaObstacleColors() {
    return itemColorTable.getViaObstacleColors();
  }

  public Color[] getPlaceObstacleColors() {
    return itemColorTable.getPlaceObstacleColors();
  }

  public double getTraceColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.TRACES.ordinal());
  }

  public void setTraceColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.TRACES.ordinal(), pValue);
  }

  public double getViaColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIAS.ordinal());
  }

  public void setViaColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIAS.ordinal(), pValue);
  }

  public double getPinColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PINS.ordinal());
  }

  public void setPinColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.PINS.ordinal(), pValue);
  }

  public double getConductionColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal());
  }

  public void setConductionColorIntensity(double pValue) {
    colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), pValue);
  }

  public double getObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal());
  }

  public void setObstacleColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), pValue);
  }

  public double getViaObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal());
  }

  public void setViaObstacleColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal(), pValue);
  }

  public double getPlaceObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal());
  }

  public double getComponentOutlineColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal());
  }

  public double getHighlightColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.HIGHLIGHT.ordinal());
  }

  public void setHighlightColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.HIGHLIGHT.ordinal(), pValue);
  }

  @Deprecated
  public double getHilightColorIntensity() {
    return getHighlightColorIntensity();
  }

  @Deprecated
  public void setHilightColorIntensity(double pValue) {
    setHighlightColorIntensity(pValue);
  }

  public double getIncompleteColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal());
  }

  public void setIncompleteColorIntensity(double pValue) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), pValue);
  }

  public double getLengthMatchingAreaColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal());
  }

  public void setLengthMatchingAreaColorIntensity(double pValue) {
    colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal(), pValue);
  }

  public Dimension getPanelSize() {
    return coordinateTransform.screenBounds;
  }

  /** Returns the center of the design on the screen. */
  public Point2D getDesignCenter() {
    FloatPoint center = coordinateTransform.designBoxWithOffset.centreOfGravity();
    return coordinateTransform.boardToScreen(center);
  }

  /** Returns the bounding box of the design in screen coordinates. */
  public Rectangle getDesignBounds() {
    return coordinateTransform.boardToScreen(coordinateTransform.designBox);
  }

  /** gets the factor for automatic layer dimming */
  public double getAutoLayerDimFactor() {
    return this.autoLayerDimFactor;
  }

  /**
   * Sets the factor for automatic layer dimming. Values are between 0 and 1. If 1, there is no
   * automatic layer dimming.
   */
  public void setAutoLayerDimFactor(double pValue) {
    autoLayerDimFactor = pValue;
  }

  /** Sets the layer, which will be excluded from automatic layer dimming. */
  public void setFullyVisibleLayer(int pLayerNo) {
    fullyVisibleLayer = pLayerNo;
    if (pLayerNo != -1) {
      fullyVisibleVirtualLayer = -1;
    }
  }

  public int getFullyVisibleLayer() {
    return fullyVisibleLayer;
  }

  public void setSimplifiedPlaneRendering(boolean simplifiedPlaneRendering) {
    this.simplifiedPlaneRendering = simplifiedPlaneRendering;
  }

  public boolean isSimplifiedPlaneRendering() {
    return simplifiedPlaneRendering;
  }

  public int getFullyVisibleVirtualLayer() {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (fullyVisibleVirtualLayer < -1 || fullyVisibleVirtualLayer >= visibilityArr.length) {
      return -1;
    }
    return fullyVisibleVirtualLayer;
  }

  public boolean isFrontSelected() {
    int selectedVirtualLayer = getFullyVisibleVirtualLayer();
    if (selectedVirtualLayer != -1) {
      return selectedVirtualLayer % 2 == 0;
    }
    if (fullyVisibleLayer != -1) {
      return fullyVisibleLayer < layerVisibilityArr.length / 2;
    }
    return true;
  }

  public boolean getVirtualLayerVisible(int idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      return visibilityArr[idx];
    }
    return true;
  }

  public void setVirtualLayerVisible(int idx, boolean visible) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      visibilityArr[idx] = visible;
    }
  }

  public void setFullyVisibleVirtualLayer(int idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx < -1 || idx >= visibilityArr.length) {
      idx = -1;
    }
    fullyVisibleVirtualLayer = idx;
    if (idx != -1) {
      fullyVisibleLayer = -1;
    }
  }

  public double getVirtualLayerVisibility(int virtualLayerIdx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (virtualLayerIdx < 0 || virtualLayerIdx >= visibilityArr.length) {
      return 1.0;
    }
    if (!visibilityArr[virtualLayerIdx]) {
      return 0.0;
    }
    if (fullyVisibleLayer != -1) {
      return this.autoLayerDimFactor;
    }
    int selectedVirtualLayer = getFullyVisibleVirtualLayer();
    if (selectedVirtualLayer != -1) {
      if (selectedVirtualLayer == virtualLayerIdx) {
        return 1.0;
      } else {
        return this.autoLayerDimFactor;
      }
    }
    return 1.0;
  }

  /**
   * Gets the visibility factor of the input layer. The result is between 0 and 1. If the result is
   * 0, the layer is invisible, if the result is 1, the layer is fully visible.
   */
  public double getLayerVisibility(int pLayerNo) {
    double result;
    if (fullyVisibleVirtualLayer != -1) {
      result = this.autoLayerDimFactor * layerVisibilityArr[pLayerNo];
    } else if (pLayerNo == this.fullyVisibleLayer) {
      result = layerVisibilityArr[pLayerNo];
    } else {
      result = this.autoLayerDimFactor * layerVisibilityArr[pLayerNo];
    }
    return result;
  }

  /** Gets the visibility factor of the input layer without the automatic layer dimming. */
  public double getRawLayerVisibility(int pLayerNo) {
    return layerVisibilityArr[pLayerNo];
  }

  /**
   * Gets the visibility factor of the input layer. The value is expected between 0 and 1. If the
   * value is 0, the layer is invisible, if the value is 1, the layer is fully visible.
   */
  public void setLayerVisibility(int pLayerNo, double pValue) {
    layerVisibilityArr[pLayerNo] = Math.max(0, Math.min(pValue, 1));
  }

  public void setLayerVisibilityArr(double[] pLayerVisibilityArr) {
    this.layerVisibilityArr = pLayerVisibilityArr;
  }

  public double[] copyLayerVisibilityArr() {
    double[] result = new double[this.layerVisibilityArr.length];
    System.arraycopy(this.layerVisibilityArr, 0, result, 0, this.layerVisibilityArr.length);
    return result;
  }

  /** Returns the number of layers on the board */
  public int layerCount() {
    return layerVisibilityArr.length;
  }

  /**
   * filter lines, which cannot touch the updateBox to improve the performance of the draw function
   * by avoiding unnecessary calls of draw (line)
   */
  private boolean lineOutsideUpdateBox(
      FloatPoint p1, FloatPoint p2, double pUpdateOffset, IntBox pUpdateBox) {
    if (p1 == null || p2 == null) {
      return true;
    }
    if (Math.max(p1.x, p2.x) < pUpdateBox.ll.x - pUpdateOffset) {
      return true;
    }
    if (Math.max(p1.y, p2.y) < pUpdateBox.ll.y - pUpdateOffset) {
      return true;
    }
    if (Math.min(p1.x, p2.x) > pUpdateBox.ur.x + pUpdateOffset) {
      return true;
    }
    return Math.min(p1.y, p2.y) > pUpdateBox.ur.y + pUpdateOffset;
  }

  /** Writes an instance of this class to a file. */
  private void writeObject(ObjectOutputStream pStream) throws IOException {
    pStream.defaultWriteObject();
    itemColorTable.writeObject(pStream);
    otherColorTable.writeObject(pStream);
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    pStream.defaultReadObject();
    if (virtualLayerVisibilityArr == null
        || virtualLayerVisibilityArr.length != virtual_layer_count) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    fullyVisibleVirtualLayer = getFullyVisibleVirtualLayer();
    this.itemColorTable = new ItemColorTableModel(pStream);
    this.otherColorTable = new OtherColorTableModel(pStream);
  }
}
