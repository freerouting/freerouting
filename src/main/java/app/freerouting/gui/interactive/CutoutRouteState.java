package app.freerouting.gui.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.ShapeTraceEntries;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Interactive state for cutting selected traces within a rectangular region. */
public final class CutoutRouteState extends SelectRegionState {

  private final Collection<PolylineTrace> traceList;

  /** Creates a new instance of CutoutRouteState. */
  private CutoutRouteState(
      Collection<PolylineTrace> itemList,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    this.traceList = itemList;
  }

  /** Returns a new instance of this class. */
  public static CutoutRouteState getInstance(
      Collection<Item> itemList, InteractiveState parentState, GuiBoardManager boardHandling) {
    return getInstance(itemList, null, parentState, boardHandling);
  }

  /** Returns a new instance of this class. */
  public static CutoutRouteState getInstance(
      Collection<Item> itemList,
      FloatPoint location,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    boardHandling.displayLayerMessage();
    // filter items, which cannot be cutout
    Collection<PolylineTrace> traceList = new LinkedList<>();

    for (Item currentItem : itemList) {
      if (!currentItem.isUserFixed() && currentItem instanceof PolylineTrace trace) {
        traceList.add(trace);
      }
    }

    CutoutRouteState newInstance = new CutoutRouteState(traceList, parentState, boardHandling);
    newInstance.corner1 = location;
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

    for (PolylineTrace currentTrace : this.traceList) {
      ShapeTraceEntries.cutoutTrace(currentTrace, cutBox, 0);
      for (int i = 0; i < currentTrace.netCount(); i++) {
        changedNets.add(currentTrace.getNetNo(i));
      }
    }

    for (Integer changedNet : changedNets) {
      hdlg.updateRatsnest(changedNet);
    }
  }

  @Override
  public void draw(Graphics graphics) {
    if (traceList == null) {
      return;
    }

    for (PolylineTrace currentTrace : this.traceList) {

      BoardRenderer.drawOverlayItem(
          currentTrace,
          graphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.getHighlightColor(),
          hdlg.graphicsContext.getHighlightColorIntensity());
    }
    super.draw(graphics);
  }
}
