package app.freerouting.board.model.items;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.board.actions.ItemSelectionFilter;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

/** ComponentOutline. */
public class ComponentOutline extends Item implements Serializable {

  private final Area relativeArea;
  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean isFront;
  private final boolean isCourtyard;
  private final boolean isFabrication;
  private final boolean isClosed;

  /** Creates a new instance of ComponentOutline. */
  public ComponentOutline(
      Area area,
      boolean isFront,
      Vector translation,
      double rotationInDegree,
      int id,
      int componentId,
      boolean isCourtyard,
      boolean isFabrication,
      boolean isClosed,
      FixedState fixedState,
      BasicBoard board) {
    super(new int[0], 0, id, componentId, fixedState, board);
    this.relativeArea = area;
    this.isFront = isFront;
    this.translation = translation;
    this.rotationInDegree = rotationInDegree;
    this.isCourtyard = isCourtyard;
    this.isFabrication = isFabrication;
    this.isClosed = isClosed;
  }

  @Override
  public Item copy(int id) {
    return new ComponentOutline(
        this.relativeArea,
        this.isFront,
        this.translation,
        this.rotationInDegree,
        id,
        this.getComponentId(),
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
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    return false;
  }

  /** GetLayer. */
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
  public boolean isOnLayer(int layer) {
    return getLayer() == layer;
  }

  @Override
  public boolean isObstacle(Item item) {
    return false;
  }

  @Override
  public int shapeLayer(int index) {
    return getLayer();
  }

  @Override
  public int tileShapeCount() {
    return 0;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree searchTree) {
    return new TileShape[0];
  }

  @Override
  public IntBox boundingBox() {
    return getArea().boundingBox();
  }

  @Override
  public void translateBy(Vector vector) {
    this.translation = this.translation.add(vector);
    clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    this.isFront = !this.isFront;
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(pole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    double turnAngle = angleInDegree;
    if (!this.isFront && this.board.components.getFlipStyleRotateFirst()) {
      turnAngle = 360 - angleInDegree;
    }
    this.rotationInDegree += turnAngle;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    FloatPoint newTranslation =
        this.translation.toFloat().rotate(Math.toRadians(angleInDegree), pole);
    this.translation = newTranslation.round().differenceBy(Point.ZERO);
    clearDerivedData();
  }

  @Override
  public void turn90Degree(int factor, IntPoint pole) {
    this.rotationInDegree += factor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.turn90Degree(factor, pole).differenceBy(Point.ZERO);
    clearDerivedData();
  }

  /** Get area. */
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
  public void printInfo(ItemInfoPrinter printer, Locale locale) {}

  @Override
  public boolean write(ObjectOutputStream stream) {
    try {
      stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
