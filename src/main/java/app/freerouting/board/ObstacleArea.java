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
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** An item on the board with a relativeArea shape, for example keepout, conduction relativeArea */
public class ObstacleArea extends Item implements Serializable {

  /** For debugging the division into tree shapes */
  private static final boolean display_tree_shapes = false;

  /**
   * The name of this ObstacleArea, which is null, if the ObstacleArea does not belong to a
   * component.
   */
  public final String name;

  private final Area relativeArea;

  /** the layer of this relativeArea */
  private int layer;

  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean sideChanged;

  /**
   * Creates a new relativeArea item which may belong to several nets. p_name is null, if the
   * ObstacleArea does not belong to a component.
   */
  ObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_cmp_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(p_net_no_arr, p_clearance_type, p_id_no, p_cmp_no, p_fixed_state, p_board);
    this.relativeArea = p_area;
    this.layer = p_layer;
    this.translation = p_translation;
    this.rotationInDegree = p_rotation_in_degree;
    this.sideChanged = p_side_changed;
    this.name = p_name;
  }

  /**
   * Creates a new relativeArea item without net. p_name is null, if the ObstacleArea does not
   * belong to a component.
   */
  ObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    this(
        p_area,
        p_layer,
        p_translation,
        p_rotation_in_degree,
        p_side_changed,
        new int[0],
        p_clearance_type,
        p_id_no,
        p_group_no,
        p_name,
        p_fixed_state,
        p_board);
  }

  @Override
  public Item copy(int p_id_no) {
    int[] copiedNetNos = new int[netNoArr.length];
    System.arraycopy(netNoArr, 0, copiedNetNos, 0, netNoArr.length);
    return new ObstacleArea(
        relativeArea,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        copiedNetNos,
        clearanceClassNo(),
        p_id_no,
        getComponentNo(),
        name,
        getFixedState(),
        board);
  }

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
  public boolean isOnLayer(int p_layer) {
    return layer == p_layer;
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
  public boolean isObstacle(Item p_other) {
    if (p_other.sharesNet(this)) {
      return false;
    }
    return p_other instanceof Trace || p_other instanceof Via;
  }

  @Override
  protected TileShape[] calculateTreeShapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculateTreeShapes(this);
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
  public TileShape getTileShape(int p_no) {
    TileShape[] tileShapes = this.splitToConvex();
    if (tileShapes == null || p_no < 0 || p_no >= tileShapes.length) {
      FRLogger.warn("ConvexObstacle.get_tile_shape: p_no out of range");
      return null;
    }
    return tileShapes[p_no];
  }

  @Override
  public void translateBy(Vector p_vector) {
    this.translation = this.translation.add(p_vector);
    this.clearDerivedData();
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
    this.clearDerivedData();
  }

  @Override
  public void rotateApprox(double p_angle_in_degree, FloatPoint p_pole) {
    double turnAngle = p_angle_in_degree;
    if (this.sideChanged && this.board.components.getFlipStyleRotateFirst()) {
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
    this.clearDerivedData();
  }

  @Override
  public void changePlacementSide(IntPoint p_pole) {
    this.sideChanged = !this.sideChanged;
    if (this.board != null) {
      this.layer = board.getLayerCount() - this.layer - 1;
    }
    Point relLocation = Point.ZERO.translateBy(this.translation);
    this.translation = relLocation.mirrorVertical(p_pole).differenceBy(Point.ZERO);
    this.clearDerivedData();
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    if (!this.isSelectedByFixedFilter(p_filter)) {
      return false;
    }
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    return p_graphics_context.getObstacleColors();
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.getObstacleColorIntensity();
  }

  @Override
  public int getDrawPriority() {
    return Drawable.MIN_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    Color color = p_color_arr[this.layer];
    double intensity = p_graphics_context.getLayerVisibility(this.layer) * p_intensity;
    p_graphics_context.fillArea(this.getArea(), p_g, color, intensity);
    if (intensity > 0 && display_tree_shapes) {
      ShapeSearchTree defaultTree = this.board.searchTreeManager.getDefaultTree();
      for (int i = 0; i < this.treeShapeCount(defaultTree); i++) {
        p_graphics_context.drawBoundary(
            this.getTreeShape(defaultTree, i), 1, Color.white, p_g, 1);
      }
    }
  }

  @Override
  public int shapeLayer(int p_index) {
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
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("keepout"));
    int cmpNo = this.getComponentNo();
    if (cmpNo > 0) {
      p_window.append(" " + tm.getText("of_component") + " ");
      Component component = board.components.get(cmpNo);
      p_window.append(component.name, tm.getText("component_info"), component);
    }
    this.printShapeInfo(p_window, p_locale);
    this.printItemInfo(p_window, p_locale);
    p_window.newline();
  }

  /** Used in the implementation of print_info for this class and derived classes. */
  protected final void printShapeInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append(" " + tm.getText("at") + " ");
    FloatPoint center = this.getArea().getBorder().centreOfGravity();
    p_window.append(center);
    Integer holeCount = this.relativeArea.getHoles().length;
    if (holeCount > 0) {
      p_window.append(" " + tm.getText("with") + " ");
      NumberFormat nf = NumberFormat.getInstance(p_locale);
      p_window.append(nf.format(holeCount));
      if (holeCount == 1) {
        p_window.append(" " + tm.getText("hole"));
      } else {
        p_window.append(" " + tm.getText("holes"));
      }
    }
    p_window.append(" " + tm.getText("on_layer") + " ");
    p_window.append(this.board.layerStructure.arr[this.getLayer()].name);
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
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}
