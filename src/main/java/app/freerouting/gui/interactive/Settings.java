package app.freerouting.gui.interactive;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;

/** Contains the interactive GUI settings used by board handling. */
public class Settings implements Serializable {

  /** The array of manual trace half-widths, initially equal to the automatic trace half-widths. */
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

  /** The current layer. */
  int layer;

  /** Allows pushing obstacles aside. */
  boolean pushEnabled;

  /** Allows dragging components with the route. */
  boolean dragComponentsEnabled;

  /**
   * Indicates whether interactive selections are made on all visible layers or only on the current
   * layer.
   */
  boolean selectOnAllVisibleLayers;

  /** Route mode: stitching or dynamic. */
  boolean isStitchRoute;

  /** The width of the pull-tight region of traces around the cursor. */
  int tracePullTightRegionWidth;

  /** Via snaps to SMD center when attaching to an SMD is allowed. */
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
  boolean highlightRoutingObstacle;

  /** The index of the clearance class used for traces in interactive routing. */
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

  /** Indicates whether the data of this class may be changed during interactive board editing. */
  private transient boolean readOnly;

  /** Creates a new interactive settings variable. */
  public Settings(RoutingBoard board) {
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
    highlightRoutingObstacle = false;
    manualTraceClearanceClass = 1;
    manualViaRuleIndex = 0;
    zoomWithWheel = true;
    manualTraceHalfWidthArr = new int[board.getLayerCount()];
    Arrays.fill(manualTraceHalfWidthArr, 1000);
    autorouteSettings = new RouterSettings(board);
    itemSelectionFilter = new ItemSelectionFilter();
  }

  /** Creates a copy of the supplied interactive settings. */
  public Settings(Settings settings) {
    this.readOnly = settings.readOnly;
    this.layer = settings.layer;
    this.pushEnabled = settings.pushEnabled;
    this.dragComponentsEnabled = settings.dragComponentsEnabled;
    this.selectOnAllVisibleLayers = settings.selectOnAllVisibleLayers;
    this.isStitchRoute = settings.isStitchRoute;
    this.tracePullTightRegionWidth = settings.tracePullTightRegionWidth;
    this.viaSnapToSmdCenter = settings.viaSnapToSmdCenter;
    this.horizontalComponentGrid = settings.horizontalComponentGrid;
    this.verticalComponentGrid = settings.verticalComponentGrid;
    this.manualRuleSelection = settings.manualRuleSelection;
    this.highlightRoutingObstacle = settings.highlightRoutingObstacle;
    this.zoomWithWheel = settings.zoomWithWheel;
    this.manualTraceClearanceClass = settings.manualTraceClearanceClass;
    this.manualViaRuleIndex = settings.manualViaRuleIndex;
    this.manualTraceHalfWidthArr = new int[settings.manualTraceHalfWidthArr.length];
    System.arraycopy(
        settings.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr.length);
    this.autorouteSettings = settings.autorouteSettings.clone();
    this.itemSelectionFilter = new ItemSelectionFilter(settings.itemSelectionFilter);
  }

  /** Returns the current interactive routing layer. */
  public int getLayer() {
    return this.layer;
  }

  /** Allows pushing obstacles aside. */
  public boolean getPushEnabled() {
    return this.pushEnabled;
  }

  /** Enables or disables pushing obstacles in interactive routing. */
  public void setPushEnabled(boolean value) {
    if (readOnly) {
      return;
    }
    pushEnabled = value;
  }

  /** Route mode: stitching or dynamic. */
  public boolean getIsStitchRoute() {
    return this.isStitchRoute;
  }

  /** Allows dragging components with the route. */
  public boolean getDragComponentsEnabled() {
    return this.dragComponentsEnabled;
  }

  /** Enables or disables dragging components. */
  public void setDragComponentsEnabled(boolean value) {
    if (readOnly) {
      return;
    }
    dragComponentsEnabled = value;
  }

  /**
   * Indicates whether interactive selections are made on all visible layers or only on the current
   * layer.
   */
  public boolean getSelectOnAllVisibleLayers() {
    return this.selectOnAllVisibleLayers;
  }

  /** Sets, if item selection is on all board layers or only on the current layer. */
  public void setSelectOnAllVisibleLayers(boolean value) {
    if (readOnly) {
      return;
    }
    selectOnAllVisibleLayers = value;
  }

  /**
   * Indicates if the routing rule selection is manual by the user or automatic by the net rules.
   */
  public boolean getManualRuleSelection() {
    return this.manualRuleSelection;
  }

  /** Via snaps to SMD center when attaching to an SMD is allowed. */
  public boolean getViaSnapToSmdCenter() {
    return this.viaSnapToSmdCenter;
  }

  /** Changes whether vias snap to SMD centers when attaching to an SMD is allowed. */
  public void setViaSnapToSmdCenter(boolean value) {
    if (readOnly) {
      return;
    }
    viaSnapToSmdCenter = value;
  }

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  public boolean getHighlightRoutingObstacle() {
    return this.highlightRoutingObstacle;
  }

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  public void setHighlightRoutingObstacle(boolean value) {
    if (readOnly) {
      return;
    }
    this.highlightRoutingObstacle = value;
  }

  /** If true, the mouse wheel is used for zooming. */
  public boolean getZoomWithWheel() {
    return this.zoomWithWheel;
  }

  /** If true, the wheel is used for zooming. */
  public void setZoomWithWheel(boolean value) {
    if (readOnly) {
      return;
    }
    if (zoomWithWheel != value) {
      zoomWithWheel = value;
    }
  }

  /** The filter used in interactive selection of board items. */
  public ItemSelectionFilter getItemSelectionFilter() {
    return this.itemSelectionFilter;
  }

  /** The filter used in interactive selection of board items. */
  public void setItemSelectionFilter(ItemSelectionFilter value) {
    if (readOnly) {
      return;
    }
    this.itemSelectionFilter = value;
  }

  /** The width of the pull-tight region of traces around the cursor. */
  public int getTracePullTightRegionWidth() {
    return this.tracePullTightRegionWidth;
  }

  /** The horizontal placement grid when moving components, if {@literal >} 0. */
  public int getHorizontalComponentGrid() {
    return this.horizontalComponentGrid;
  }

  /** The horizontal placement grid when moving components, if {@literal >} 0. */
  public void setHorizontalComponentGrid(int value) {
    if (readOnly) {
      return;
    }
    this.horizontalComponentGrid = value;
  }

  /** The vertical placement grid when moving components, if {@literal >} 0. */
  public int getVerticalComponentGrid() {
    return this.verticalComponentGrid;
  }

  /** The vertical placement grid when moving components, if {@literal >} 0. */
  public void setVerticalComponentGrid(int value) {
    if (readOnly) {
      return;
    }
    this.verticalComponentGrid = value;
  }

  /** Sets the clearance class used for traces in interactive routing. */
  public int getManualTraceClearanceClass() {
    return this.manualTraceClearanceClass;
  }

  /**
   * The index of the clearance class used for traces in interactive routing in the clearance
   * matrix, if manual_route_selection is on.
   */
  public void setManualTraceClearanceClass(int index) {
    if (readOnly) {
      return;
    }
    manualTraceClearanceClass = index;
  }

  /** The index of the via rule used in routing in the board via rules. */
  public int getManualViaRuleIndex() {
    return this.manualViaRuleIndex;
  }

  /** Sets the via rule used in routing in the board via rules. */
  public void setManualViaRuleIndex(int value) {
    if (readOnly) {
      return;
    }
    this.manualViaRuleIndex = value;
  }

  /** Returns the trace half-width in manual routing mode on the specified layer. */
  public int getManualTraceHalfWidth(int layerNo) {
    if (layerNo < 0 || layerNo >= this.manualTraceHalfWidthArr.length) {
      FRLogger.warn("Settings.get_manual_trace_half_width layer number out of range");
      return 0;
    }
    return this.manualTraceHalfWidthArr[layerNo];
  }

  /** Sets the route mode to stitching or dynamic. */
  public void setStitchRoute(boolean value) {
    if (readOnly) {
      return;
    }
    isStitchRoute = value;
  }

  /** Changes the current width of the tidy region for traces. */
  public void setCurrentPullTightRegionWidth(int value) {
    if (readOnly) {
      return;
    }
    tracePullTightRegionWidth = value;
  }

  /** Sets the current trace width selection to manual or automatic. */
  public void setManualTracewidthSelection(boolean value) {
    if (readOnly) {
      return;
    }
    manualRuleSelection = value;
  }

  /** Sets the manual trace half width used in interactive routing. */
  public void setManualTraceHalfWidth(int layerNo, int value) {
    if (readOnly) {
      return;
    }
    manualTraceHalfWidthArr[layerNo] = value;
  }

  /** Changes whether the specified item type is selectable interactively. */
  public void setSelectable(ItemSelectionFilter.SelectableChoices itemType, boolean value) {
    if (readOnly) {
      return;
    }
    itemSelectionFilter.setSelected(itemType, value);
  }

  /** Defines whether the setting attributes may be changed interactively. */
  public void setReadOnly(Boolean value) {
    this.readOnly = value;
  }

  /** Reads an instance of this class from a file. */
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    if (this.itemSelectionFilter == null) {
      FRLogger.warn("Settings.readObject: itemSelectionFilter is null");
      this.itemSelectionFilter = new ItemSelectionFilter();
    }
    this.readOnly = false;
  }
}
