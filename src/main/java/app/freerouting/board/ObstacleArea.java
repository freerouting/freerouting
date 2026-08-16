package app.freerouting.board;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** An item on the board with a relativeArea shape, for example keepout, conduction relativeArea. */
public class ObstacleArea extends Item implements Serializable {

  /**
   * The name of this ObstacleArea, which is null, if the ObstacleArea does not belong to a
   * component.
   */
  public final String name;

  private final Area relativeArea;

  /** The layer of this relativeArea. */
  private int layer;

  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean sideChanged;

  /**
   * Creates a new relativeArea item which may belong to several nets. name is null, if the
   * ObstacleArea does not belong to a component.
   */
  ObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int[] netNumbers,
      int clearanceClassIndex,
      int id,
      int componentId,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    super(netNumbers, clearanceClassIndex, id, componentId, fixedState, board);
    this.relativeArea = area;
    this.layer = layer;
    this.translation = translation;
    this.rotationInDegree = rotationInDegree;
    this.sideChanged = sideChanged;
    this.name = name;
  }

  /**
   * Creates a new relativeArea item without net. name is null, if the ObstacleArea does not belong
   * to a component.
   */
  ObstacleArea(
      Area area,
      int layer,
      Vector translation,
      double rotationInDegree,
      boolean sideChanged,
      int clearanceClassIndex,
      int id,
      int groupId,
      String name,
      FixedState fixedState,
      BasicBoard board) {
    this(
        area,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        new int[0],
        clearanceClassIndex,
        id,
        groupId,
        name,
        fixedState,
        board);
  }

  @Override
  public Item copy(int id) {
    int[] copiedNetNos = new int[netNumbers.length];
    System.arraycopy(netNumbers, 0, copiedNetNos, 0, netNumbers.length);
    return new ObstacleArea(
        relativeArea,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        copiedNetNos,
        clearanceClassIndex(),
        id,
        getComponentId(),
        name,
        getFixedState(),
        board);
  }

  /** GetArea. */
  public Area getArea() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (this.sideChanged && !this.board.components.getFlipStyleRotateFirst()) {
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
      if (this.sideChanged && this.board.components.getFlipStyleRotateFirst()) {
        turnedArea = turnedArea.mirrorVertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translateBy(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  protected Area getRelativeArea() {
    return this.relativeArea;
  }

  @Override
  public boolean isOnLayer(int layer) {
    return this.layer == layer;
  }

  @Override
  public int firstLayer() {
    return this.layer;
  }

  @Override
  public int lastLayer() {
    return this.layer;
  }

  public int getLayer() {
    return this.layer;
  }

  @Override
  public IntBox boundingBox() {
    return this.getArea().boundingBox();
  }

  @Override
  public boolean isObstacle(Item other) {
    if (other.sharesNet(this)) {
      return false;
    }
    return other instanceof Trace || other instanceof Via;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree searchTree) {
    return searchTree.calculateTreeShapes(this);
  }

  @Override
  public int tileShapeCount() {
    TileShape[] tileShapes = this.splitToConvex();
    if (tileShapes == null) {
      // an error occurred while dividing the relativeArea
      return 0;
    }
    return tileShapes.length;
  }

  @Override
  public TileShape getTileShape(int no) {
    TileShape[] tileShapes = this.splitToConvex();
    if (tileShapes == null || no < 0 || no >= tileShapes.length) {
      FRLogger.warn("ConvexObstacle.get_tile_shape: no out of range");
      return null;
    }
    return tileShapes[no];
  }

  @Override
  public void translateBy(Vector vector) {
    this.translation = this.translation.add(vector);
    this.clearDerivedData();
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
    this.clearDerivedData();
  }

  @Override
  public void rotateApprox(double angleInDegree, FloatPoint pole) {
    double turnAngle = angleInDegree;
    if (this.sideChanged && this.board.components.getFlipStyleRotateFirst()) {
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
    this.clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint pole) {
    this.sideChanged = !this.sideChanged;
    if (this.board != null) {
      this.layer = board.getLayerCount() - this.layer - 1;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(pole).differenceBy(Point.ZERO);
    this.clearDerivedData();
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter filter) {
    if (!this.isSelectedByFixedFilter(filter)) {
      return false;
    }
    return filter.isSelected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  @Override
  public int shapeLayer(int index) {
    return layer;
  }

  protected Vector getTranslation() {
    return translation;
  }

  protected double getRotationInDegree() {
    return rotationInDegree;
  }

  protected boolean getSideChanged() {
    return sideChanged;
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("keepout"));
    int cmpNo = this.getComponentId();
    if (cmpNo > 0) {
      printer.append(" " + tm.getText("of_component") + " ");
      Component component = board.components.get(cmpNo);
      printer.append(component.name, tm.getText("component_info"), component);
    }
    this.printShapeInfo(printer, locale);
    this.printItemInfo(printer, locale);
    printer.newline();
  }

  /** Used in the implementation of print_info for this class and derived classes. */
  protected final void printShapeInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.append(" " + tm.getText("at") + " ");
    FloatPoint center = this.getArea().getBorder().centreOfGravity();
    printer.append(center);
    Integer holeCount = this.relativeArea.getHoles().length;
    if (holeCount > 0) {
      printer.append(" " + tm.getText("with") + " ");
      NumberFormat nf = NumberFormat.getInstance(locale);
      printer.append(nf.format(holeCount));
      if (holeCount == 1) {
        printer.append(" " + tm.getText("hole"));
      } else {
        printer.append(" " + tm.getText("holes"));
      }
    }
    printer.append(" " + tm.getText("on_layer") + " ");
    printer.append(this.board.layerStructure.layers[this.getLayer()].name);
  }

  TileShape[] splitToConvex() {
    if (this.relativeArea == null) {
      FRLogger.warn("ObstacleArea.split_to_convex: area is null");
      return null;
    }
    return this.getArea().splitToConvex();
  }

  @Override
  public void clearDerivedData() {
    super.clearDerivedData();
    this.precalculatedAbsoluteArea = null;
  }

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
