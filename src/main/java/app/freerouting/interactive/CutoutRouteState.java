package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.ShapeTraceEntries;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

public final class CutoutRouteState extends SelectRegionState {

  private final Collection<PolylineTrace> traceList;

  /** Creates a new instance of CutoutRouteState */
  private CutoutRouteState(
      Collection<PolylineTrace> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    this.traceList = p_item_list;
  }

  /** Returns a new instance of this class. */
  public static CutoutRouteState getInstance(
      Collection<Item> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    return getInstance(p_item_list, null, p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class. */
  public static CutoutRouteState getInstance(
      Collection<Item> p_item_list,
      FloatPoint p_location,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    p_board_handling.displayLayerMessage();
    // filter items, which cannot be cutout
    Collection<PolylineTrace> itemList = new LinkedList<>();

    for (Item currItem : p_item_list) {
      if (!currItem.isUserFixed() && currItem instanceof PolylineTrace trace) {
        itemList.add(trace);
      }
    }

    CutoutRouteState newInstance = new CutoutRouteState(itemList, p_parent_state, p_board_handling);
    newInstance.corner1 = p_location;
    newInstance.hdlg.screenMessages.setStatusMessage(
        newInstance.tm.getText("drag_left_mouse_button_to_select_cutout_rectangle"));
    return newInstance;
  }

  @Override
  public InteractiveState complete() {
    hdlg.screenMessages.setStatusMessage("");
    corner2 = hdlg.getCurrentMousePosition();
    corner2 = hdlg.getCurrentMousePosition();
    this.cutoutRoute();
    return this.returnState;
  }

  /** Selects all items in the rectangle defined by corner1 and corner2. */
  private void cutoutRoute() {
    if (this.corner1 == null || this.corner2 == null) {
      return;
    }

    hdlg.getRoutingBoard().generateSnapshot();

    IntPoint p1 = this.corner1.round();
    IntPoint p2 = this.corner2.round();

    IntBox cutBox =
        new IntBox(
            Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));

    Set<Integer> changedNets = new TreeSet<>();

    for (PolylineTrace currTrace : this.traceList) {
      ShapeTraceEntries.cutoutTrace(currTrace, cutBox, 0);
      for (int i = 0; i < currTrace.netCount(); i++) {
        changedNets.add(currTrace.getNetNo(i));
      }
    }

    for (Integer changed_net : changedNets) {
      hdlg.updateRatsnest(changed_net);
    }
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (traceList == null) {
      return;
    }

    for (PolylineTrace currTrace : this.traceList) {

      currTrace.draw(
          p_graphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHilightColor(),
          hdlg.graphicsContext.getHilightColorIntensity());
    }
    super.draw(p_graphics);
  }
}
