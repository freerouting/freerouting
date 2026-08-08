package app.freerouting.board;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

public class ComponentOutline extends Item implements Serializable {

  private final Area relativeArea;
  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean isFront;
  private final boolean isCourtyard;
  private final boolean isFabrication;
  private final boolean isClosed;

  /** Creates a new instance of ComponentOutline */
  public ComponentOutline(
      Area p_area,
      boolean p_is_front,
      Vector p_translation,
      double p_rotation_in_degree,
      int p_id_no,
      int p_component_no,
      boolean p_is_courtyard,
      boolean p_is_fabrication,
      boolean p_is_closed,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(new int[0], 0, p_id_no, p_component_no, p_fixed_state, p_board);
    this.relativeArea = p_area;
    this.isFront = p_is_front;
    this.translation = p_translation;
    this.rotationInDegree = p_rotation_in_degree;
    this.isCourtyard = p_is_courtyard;
    this.isFabrication = p_is_fabrication;
    this.isClosed = p_is_closed;
  }

  @Override
  public Item copy(int p_id_no) {
    return new ComponentOutline(
        this.relativeArea,
        this.isFront,
        this.translation,
        this.rotationInDegree,
        p_id_no,
        this.getComponentNo(),
        this.isCourtyard,
        this.isFabrication,
        this.isClosed,
        this.getFixedState(),
        this.board);
  }

  public boolean isFront() {
    return this.isFront;
  }

  public boolean isCourtyard() {
    return this.isCourtyard;
  }

  public boolean isFabrication() {
    return this.isFabrication;
  }

  public boolean isClosed() {
    return this.isClosed;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    return false;
  }

  public int getLayer() {
    int result;
    if (this.isFront) {
      result = 0;
    } else {
      result = this.board.getLayerCount() - 1;
    }
    return result;
  }

  @Override
  public int firstLayer() {
    return getLayer();
  }

  @Override
  public int lastLayer() {
    return getLayer();
  }

  @Override
  public boolean isOnLayer(int p_layer) {
    return getLayer() == p_layer;
  }

  @Override
  public boolean isObstacle(Item p_item) {
    return false;
  }

  @Override
  public int shapeLayer(int p_index) {
    return getLayer();
  }

  @Override
  public int tileShapeCount() {
    return 0;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree p_search_tree) {
    return new TileShape[0];
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.getComponentOutlineColorIntensity();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color frontDrawColor;
    Color backDrawColor;
    if (this.isCourtyard) {
      frontDrawColor = p_graphics_context.otherColorTable.getCourtyardColor(true);
      backDrawColor = p_graphics_context.otherColorTable.getCourtyardColor(false);
    } else if (this.isFabrication) {
      frontDrawColor = p_graphics_context.otherColorTable.getFabColor(true);
      backDrawColor = p_graphics_context.otherColorTable.getFabColor(false);
    } else {
      frontDrawColor = p_graphics_context.otherColorTable.getSilkscreenColor(true);
      backDrawColor = p_graphics_context.otherColorTable.getSilkscreenColor(false);
    }
    for (int i = 0; i < colorArr.length - 1; i++) {
      colorArr[i] = frontDrawColor;
    }
    if (colorArr.length > 1) {
      colorArr[colorArr.length - 1] = backDrawColor;
    }
    return colorArr;
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MIDDLE_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int virtualLayerIdx;
    if (this.isCourtyard) {
      virtualLayerIdx = this.isFront ? 2 : 3;
    } else if (this.isFabrication) {
      virtualLayerIdx = this.isFront ? 4 : 5;
    } else {
      virtualLayerIdx = this.isFront ? 0 : 1;
    }
    double virtualVisibility = p_graphics_context.getVirtualLayerVisibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = p_color_arr[this.getLayer()];
    double intensity = virtualVisibility * p_intensity;

    if (this.isCourtyard || this.isClosed) {
      double drawWidth = Math.min(this.board.communication.getResolution(Unit.MIL), 100);
      p_graphics_context.drawBoundary(this.getArea(), drawWidth, color, p_g, intensity);
    } else {
      p_graphics_context.fillArea(this.getArea(), p_g, color, intensity);
    }
  }

  @Override
  public IntBox boundingBox() {
    return getArea().boundingBox();
  }

  @Override
  public void translateBy(Vector p_vector) {
    this.translation = this.translation.add(p_vector);
    clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint p_pole) {
    this.isFront = !this.isFront;
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(p_pole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void rotateApprox(double p_angle_in_degree, FloatPoint p_pole) {
    double turnAngle = p_angle_in_degree;
    if (!this.isFront && this.board.components.getFlipStyleRotateFirst()) {
      turnAngle = 360 - p_angle_in_degree;
    }
    this.rotationInDegree += turnAngle;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    FloatPoint newTranslation =
        this.translation.toFloat().rotate(Math.toRadians(p_angle_in_degree), p_pole);
    this.translation = newTranslation.round().differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void turn90Degree(int p_factor, IntPoint p_pole) {
    this.rotationInDegree += p_factor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.turn90Degree(p_factor, p_pole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  public Area getArea() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (!this.isFront && !this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      if (this.rotationInDegree != 0) {
        double rotation = this.rotationInDegree;
        if (rotation % 90 == 0) {
          turnedArea = turnedArea.turn90Degree(((int) rotation) / 90, Point.ZERO);
        } else {
          turnedArea = turnedArea.rotateApprox(Math.toRadians(rotation), FloatPoint.ZERO);
        }
      }
      if (!this.isFront && this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translateBy(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  @Override
  public void clearDerivedData() {
    precalculatedAbsoluteArea = null;
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {}

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
