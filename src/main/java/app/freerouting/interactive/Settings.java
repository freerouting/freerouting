package app.freerouting.interactive;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;

/** Contains the values of the interactive/GUI settings of the board handling. */
public class Settings implements Serializable {

  /** The array of manual trace half widths, initially equal to the automatic trace half widths. */
  final int[] manualTraceHalfWidthArr;

  /**
   * Router parameter: accuracy for trace pull tight operations in interactive routing. Lower values
   * mean more accurate but slower pull tight.
   */
  public int tracePullTightAccuracy = 500;

  /**
   * Router parameter: enables automatic neckdown in interactive routing. When true, traces
   * automatically narrow down when approaching pads.
   */
  public boolean automaticNeckdown = true;

  public RouterSettings autorouteSettings;

  /** the current layer */
  int layer;

  /** allows pushing obstacles aside */
  boolean pushEnabled;

  /** allows dragging components with the route */
  boolean dragComponentsEnabled;

  /**
   * indicates if interactive selections are made on all visible layers or only on the current layer
   */
  boolean selectOnAllVisibleLayers;

  /** Route mode: stitching or dynamic */
  boolean isStitchRoute;

  /** The width of the pull tight region of traces around the cursor */
  int tracePullTightRegionWidth;

  /** Via snaps to smd center, if attach smd is allowed. */
  boolean viaSnapToSmdCenter;

  /** The horizontal placement grid when moving components, if {@literal >} 0. */
  int horizontalComponentGrid;

  /** The vertical placement grid when moving components, if {@literal >} 0. */
  int verticalComponentGrid;

  /**
   * Indicates if the routing rule selection is manual by the user or automatic by the net rules.
   */
  boolean manualRuleSelection;

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  boolean hilightRoutingObstacle;

  /**
   * The index of the clearance class used for traces in interactive routing in the clearance
   * matrix, if manual_route_selection is on.
   */
  int manualTraceClearanceClass;

  /**
   * The index of the via rule used in routing in the board via rules if manual_route_selection is
   * on.
   */
  int manualViaRuleIndex;

  /** If true, the mouse wheel is used for zooming. */
  boolean zoomWithWheel;

  /** The filter used in interactive selection of board items. */
  ItemSelectionFilter itemSelectionFilter;

  /**
   * Indicates, if the data of this class are not allowed to be changed in interactive board
   * editing.
   */
  private transient boolean readOnly;

  /** Creates a new interactive settings variable. */
  public Settings(RoutingBoard pBoard) {
    // Initialise with default values.
    layer = 0;
    pushEnabled = true;
    dragComponentsEnabled = true;
    selectOnAllVisibleLayers = true; // else selection is only on the current layer
    isStitchRoute = false; // else interactive routing is dynamic
    tracePullTightRegionWidth = Integer.MAX_VALUE;
    viaSnapToSmdCenter = true;
    horizontalComponentGrid = 0;
    verticalComponentGrid = 0;
    manualRuleSelection = false;
    hilightRoutingObstacle = false;
    manualTraceClearanceClass = 1;
    manualViaRuleIndex = 0;
    zoomWithWheel = true;
    manualTraceHalfWidthArr = new int[pBoard.getLayerCount()];
    Arrays.fill(manualTraceHalfWidthArr, 1000);
    autorouteSettings = new RouterSettings(pBoard);
    itemSelectionFilter = new ItemSelectionFilter();
  }

  /** Copy constructor */
  public Settings(Settings pSettings) {
    this.readOnly = pSettings.readOnly;
    this.layer = pSettings.layer;
    this.pushEnabled = pSettings.pushEnabled;
    this.dragComponentsEnabled = pSettings.dragComponentsEnabled;
    this.selectOnAllVisibleLayers = pSettings.selectOnAllVisibleLayers;
    this.isStitchRoute = pSettings.isStitchRoute;
    this.tracePullTightRegionWidth = pSettings.tracePullTightRegionWidth;
    this.viaSnapToSmdCenter = pSettings.viaSnapToSmdCenter;
    this.horizontalComponentGrid = pSettings.horizontalComponentGrid;
    this.verticalComponentGrid = pSettings.verticalComponentGrid;
    this.manualRuleSelection = pSettings.manualRuleSelection;
    this.hilightRoutingObstacle = pSettings.hilightRoutingObstacle;
    this.zoomWithWheel = pSettings.zoomWithWheel;
    this.manualTraceClearanceClass = pSettings.manualTraceClearanceClass;
    this.manualViaRuleIndex = pSettings.manualViaRuleIndex;
    this.manualTraceHalfWidthArr = new int[pSettings.manualTraceHalfWidthArr.length];
    System.arraycopy(
        pSettings.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr.length);
    this.autorouteSettings = pSettings.autorouteSettings.clone();
    this.itemSelectionFilter = new ItemSelectionFilter(pSettings.itemSelectionFilter);
  }

  public int getLayer() {
    return this.layer;
  }

  /** allows pushing obstacles aside */
  public boolean getPushEnabled() {
    return this.pushEnabled;
  }

  /** Enables or disables pushing obstacles in interactive routing */
  public void setPushEnabled(boolean pValue) {
    if (readOnly) {
      return;
    }
    pushEnabled = pValue;
  }

  /** Route mode: stitching or dynamic */
  public boolean getIsStitchRoute() {
    return this.isStitchRoute;
  }

  /** allows dragging components with the route */
  public boolean getDragComponentsEnabled() {
    return this.dragComponentsEnabled;
  }

  /** Enables or disables dragging components */
  public void setDragComponentsEnabled(boolean pValue) {
    if (readOnly) {
      return;
    }
    dragComponentsEnabled = pValue;
  }

  /**
   * indicates if interactive selections are made on all visible layers or only on the current
   * layer.
   */
  public boolean getSelectOnAllVisibleLayers() {
    return this.selectOnAllVisibleLayers;
  }

  /** Sets, if item selection is on all board layers or only on the current layer. */
  public void setSelectOnAllVisibleLayers(boolean pValue) {
    if (readOnly) {
      return;
    }
    selectOnAllVisibleLayers = pValue;
  }

  /**
   * Indicates if the routing rule selection is manual by the user or automatic by the net rules.
   */
  public boolean getManualRuleSelection() {
    return this.manualRuleSelection;
  }

  /** Via snaps to smd center, if attach smd is allowed. */
  public boolean getViaSnapToSmdCenter() {
    return this.viaSnapToSmdCenter;
  }

  /** Changes, if vias snap to smd center, if attach smd is allowed. */
  public void setViaSnapToSmdCenter(boolean pValue) {
    if (readOnly) {
      return;
    }
    viaSnapToSmdCenter = pValue;
  }

  /** If true, the current routing obstacle is hilightet in dynamic routing. */
  public boolean getHilightRoutingObstacle() {
    return this.hilightRoutingObstacle;
  }

  /** If true, the current routing obstacle is hilightet in dynamic routing. */
  public void setHilightRoutingObstacle(boolean pValue) {
    if (readOnly) {
      return;
    }
    this.hilightRoutingObstacle = pValue;
  }

  /** If true, the mouse wheel is used for zooming. */
  public boolean getZoomWithWheel() {
    return this.zoomWithWheel;
  }

  /** If true, the wheel is used for zooming. */
  public void setZoomWithWheel(boolean pValue) {
    if (readOnly) {
      return;
    }
    if (zoomWithWheel != pValue) {
      zoomWithWheel = pValue;
    }
  }

  /** The filter used in interactive selection of board items. */
  public ItemSelectionFilter getItemSelectionFilter() {
    return this.itemSelectionFilter;
  }

  /** The filter used in interactive selection of board items. */
  public void setItemSelectionFilter(ItemSelectionFilter pValue) {
    if (readOnly) {
      return;
    }
    this.itemSelectionFilter = pValue;
  }

  /** The width of the pull tight region of traces around the cursor */
  public int getTracePullTightRegionWidth() {
    return this.tracePullTightRegionWidth;
  }

  /** The horizontal placement grid when moving components, if {@literal >} 0. */
  public int getHorizontalComponentGrid() {
    return this.horizontalComponentGrid;
  }

  /** The horizontal placement grid when moving components, if {@literal >} 0. */
  public void setHorizontalComponentGrid(int pValue) {
    if (readOnly) {
      return;
    }
    this.horizontalComponentGrid = pValue;
  }

  /** The vertical placement grid when moving components, if {@literal >} 0. */
  public int getVerticalComponentGrid() {
    return this.verticalComponentGrid;
  }

  /** The vertical placement grid when moving components, if {@literal >} 0. */
  public void setVerticalComponentGrid(int pValue) {
    if (readOnly) {
      return;
    }
    this.verticalComponentGrid = pValue;
  }

  /**
   * The index of the clearance class used for traces in interactive routing in the clearance
   * matrix, if manual_route_selection is on.
   */
  public int getManualTraceClearanceClass() {
    return this.manualTraceClearanceClass;
  }

  /**
   * The index of the clearance class used for traces in interactive routing in the clearance
   * matrix, if manual_route_selection is on.
   */
  public void setManualTraceClearanceClass(int pIndex) {
    if (readOnly) {
      return;
    }
    manualTraceClearanceClass = pIndex;
  }

  /**
   * The index of the via rule used in routing in the board via rules if manual_route_selection is
   * on.
   */
  public int getManualViaRuleIndex() {
    return this.manualViaRuleIndex;
  }

  /**
   * The index of the via rule used in routing in the board via rules if manual_route_selection is
   * on.
   */
  public void setManualViaRuleIndex(int pValue) {
    if (readOnly) {
      return;
    }
    this.manualViaRuleIndex = pValue;
  }

  /** Get the trace half width in manual routing mode on layer p_layer_no */
  public int getManualTraceHalfWidth(int pLayerNo) {
    if (pLayerNo < 0 || pLayerNo >= this.manualTraceHalfWidthArr.length) {
      FRLogger.warn("Settings.get_manual_trace_half_width p_layer_no out of range");
      return 0;
    }
    return this.manualTraceHalfWidthArr[pLayerNo];
  }

  /** Route mode: stitching or dynamic */
  public void setStitchRoute(boolean pValue) {
    if (readOnly) {
      return;
    }
    isStitchRoute = pValue;
  }

  /** Changes the current width of the tidy region for traces. */
  public void setCurrentPullTightRegionWidth(int pValue) {
    if (readOnly) {
      return;
    }
    tracePullTightRegionWidth = pValue;
  }

  /** Sets the current trace width selection to manual or automatic. */
  public void setManualTracewidthSelection(boolean pValue) {
    if (readOnly) {
      return;
    }
    manualRuleSelection = pValue;
  }

  /** Sets the manual trace half width used in interactive routing. */
  public void setManualTraceHalfWidth(int pLayerNo, int pValue) {
    if (readOnly) {
      return;
    }
    manualTraceHalfWidthArr[pLayerNo] = pValue;
  }

  /** Changes the interactive selectability of p_item_type. */
  public void setSelectable(ItemSelectionFilter.SelectableChoices pItemType, boolean pValue) {
    if (readOnly) {
      return;
    }
    itemSelectionFilter.setSelected(pItemType, pValue);
  }

  /** Defines, if the setting attributes are allowed to be changed interactively or not. */
  public void setReadOnly(Boolean pValue) {
    this.readOnly = pValue;
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    pStream.defaultReadObject();
    if (this.itemSelectionFilter == null) {
      FRLogger.warn("Settings.readObject: itemSelectionFilter is null");
      this.itemSelectionFilter = new ItemSelectionFilter();
    }
    this.readOnly = false;
  }
}
