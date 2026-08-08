package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.util.Locale;

/** Describes Areas on the board, where vias are not allowed. */
public class ViaObstacleArea extends ObstacleArea {

  /** Creates a new area item which may belong to several nets */
  ViaObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(
        p_area,
        p_layer,
        p_translation,
        p_rotation_in_degree,
        p_side_changed,
        p_net_no_arr,
        p_clearance_type,
        p_id_no,
        p_group_no,
        p_name,
        p_fixed_state,
        p_board);
  }

  /** Creates a new area item without net */
  ViaObstacleArea(
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
    return new ViaObstacleArea(
        getRelativeArea(),
        getLayer(),
        getTranslation(),
        getRotationInDegree(),
        getSideChanged(),
        copiedNetNos,
        clearanceClassNo(),
        p_id_no,
        getComponentNo(),
        this.name,
        getFixedState(),
        board);
  }

  @Override
  public boolean isObstacle(Item p_other) {
    if (p_other.sharesNet(this)) {
      return false;
    }
    return p_other instanceof Via;
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
    return p_filter.isSelected(ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT);
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("via_keepout"));
    this.printShapeInfo(p_window, p_locale);
    this.printClearanceInfo(p_window, p_locale);
    this.printClearanceViolationInfo(p_window, p_locale);
    p_window.newline();
  }

  @Override
  public Color[] getDrawColors(GraphicsContext p_graphics_context) {
    return p_graphics_context.getViaObstacleColors();
  }

  @Override
  public double getDrawIntensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.getViaObstacleColorIntensity();
  }
}
