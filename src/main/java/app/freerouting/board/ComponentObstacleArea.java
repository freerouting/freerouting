package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Locale;

/** Describes areas of the board, where components are not allowed. */
public class ComponentObstacleArea extends ObstacleArea {

  /**
   * Creates a new instance of ComponentObstacleArea If p_is_obstacle is false, the new instance is
   * not regarded as obstacle and used only for displaying on the screen.
   */
  ComponentObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int p_clearance_type,
      int p_id_no,
      int p_component_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(
        p_area,
        p_layer,
        p_translation,
        p_rotation_in_degree,
        p_side_changed,
        new int[0],
        p_clearance_type,
        p_id_no,
        p_component_no,
        p_name,
        p_fixed_state,
        p_board);
  }

  @Override
  public Item copy(int p_id_no) {
    return new ComponentObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        clearanceClassNo(),
        p_id_no,
        getComponentNo(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item p_other) {
    return p_other != this
        && p_other instanceof ComponentObstacleArea
        && p_other.getComponentNo() != this.getComponentNo();
  }

  @Override
  public boolean isTraceObstacle(int p_net_no) {
    return false;
  }

  @Override
  public boolean isSelectedByFilter(ItemSelectionFilter p_filter) {
    if (!this.isSelectedByFixedFilter(p_filter)) {
      return false;
    }
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  public boolean isFront() {
    Component component = board.components.get(this.getComponentNo());
    return component == null || component.placedOnFront();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color frontDrawColor = p_graphics_context.otherColorTable.getCourtyardColor(true);
    for (int i = 0; i < colorArr.length - 1; i++) {
      colorArr[i] = frontDrawColor;
    }
    if (colorArr.length > 1) {
      colorArr[colorArr.length - 1] = p_graphics_context.otherColorTable.getCourtyardColor(false);
    }
    return colorArr;
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.getComponentOutlineColorIntensity();
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int virtualLayerIdx = this.isFront() ? 2 : 3;
    double virtualVisibility = p_graphics_context.getVirtualLayerVisibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = p_color_arr[this.getLayer()];
    double intensity = virtualVisibility * p_intensity;

    double drawWidth = Math.min(this.board.communication.getResolution(Unit.MIL), 100);
    p_graphics_context.drawBoundary(this.getArea(), drawWidth, color, p_g, intensity);
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("component_keepout"));
    this.printShapeInfo(p_window, p_locale);
    this.printClearanceInfo(p_window, p_locale);
    this.printClearanceViolationInfo(p_window, p_locale);
    p_window.newline();
  }
}
