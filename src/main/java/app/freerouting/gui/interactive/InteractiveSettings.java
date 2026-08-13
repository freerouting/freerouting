package app.freerouting.gui.interactive;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.GuiSettings;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Contains the values of the interactive/GUI settings of the board handling.
 *
 * <p>This class is the concrete {@link GuiSettings} source (priority 50) supplied to the {@link
 * app.freerouting.settings.SettingsMerger}. Any field mutation is therefore visible to the router
 * settings pipeline on the next {@code merge()} call.
 *
 * <p>In GUI mode this class acts as the concrete {@link GuiSettings} source at priority 50 in the
 * {@link app.freerouting.settings.SettingsMerger} pipeline. Use {@link #getOrCreate(RoutingBoard)}
 * to obtain the singleton instance; never construct it directly from GUI code.
 *
 * <p><strong>Singleton contract:</strong> exactly one instance exists for the lifetime of a GUI
 * session. Use {@link #getOrCreate(RoutingBoard)} to obtain it. In headless mode the instance is
 * {@code null}; use {@link app.freerouting.management.BoardManager#getInteractiveSettings()} to
 * safely obtain it.
 *
 * <p><strong>Two-way binding:</strong> every setter fires a named {@link
 * java.beans.PropertyChangeEvent} via {@link PropertyChangeSupport}. GUI panels should register as
 * {@link PropertyChangeListener}s on this instance and call {@code refresh()} (or update individual
 * controls) in their {@code propertyChange} callback. Use {@link #addPropertyChangeListener} /
 * {@link #removePropertyChangeListener} to subscribe.
 *
 * @see GuiSettings
 * @see app.freerouting.settings.SettingsMerger
 */
public class InteractiveSettings extends GuiSettings implements Serializable {

  // -------------------------------------------------------------------------
  // Named property keys – use these constants everywhere to avoid typos.
  // -------------------------------------------------------------------------
  public static final String PROP_LAYER = "layer";
  public static final String PROP_PUSH_ENABLED = "pushEnabled";
  public static final String PROP_DRAG_COMPONENTS_ENABLED = "dragComponentsEnabled";
  public static final String PROP_SELECT_ON_ALL_VISIBLE_LAYERS = "selectOnAllVisibleLayers";
  public static final String PROP_IS_STITCH_ROUTE = "isStitchRoute";
  public static final String PROP_TRACE_PULL_TIGHT_REGION_WIDTH = "tracePullTightRegionWidth";
  public static final String PROP_VIA_SNAP_TO_SMD_CENTER = "viaSnapToSmdCenter";
  public static final String PROP_HORIZONTAL_COMPONENT_GRID = "horizontalComponentGrid";
  public static final String PROP_VERTICAL_COMPONENT_GRID = "verticalComponentGrid";
  public static final String PROP_MANUAL_RULE_SELECTION = "manualRuleSelection";
  public static final String PROP_HIGHLIGHT_ROUTING_OBSTACLE = "highlightRoutingObstacle";
  public static final String PROP_MANUAL_TRACE_CLEARANCE_CLASS = "manualTraceClearanceClass";
  public static final String PROP_MANUAL_VIA_RULE_INDEX = "manualViaRuleIndex";
  public static final String PROP_ZOOM_WITH_WHEEL = "zoomWithWheel";
  public static final String PROP_ITEM_SELECTION_FILTER = "itemSelectionFilter";
  public static final String PROP_TRACE_PULL_TIGHT_ACCURACY = "tracePullTightAccuracy";
  public static final String PROP_AUTOMATIC_NECKDOWN = "automaticNeckdown";
  public static final String PROP_MANUAL_TRACE_HALF_WIDTH = "manual_trace_half_width";

  /** The single GUI-session instance; {@code null} when running headless. */
  private static volatile InteractiveSettings instance;

  /**
   * The array of manual trace half widths, initially equal to the automatic trace half widths. This
   * is a {@code final} array reference; individual entries are mutated via {@link
   * #setManualTraceHalfWidth(int, int)}.
   */
  final int[] manualTraceHalfWidthArr;

  // -------------------------------------------------------------------------
  // PropertyChangeSupport — transient so it is not serialised; re-created in readObject.
  // -------------------------------------------------------------------------
  private transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  /** Router parameter: accuracy for trace pull tight operations in interactive routing. */
  private int tracePullTightAccuracy = 500;

  /** Router parameter: enables automatic neckdown in interactive routing. */
  private boolean automaticNeckdown = true;

  /** The current layer index. */
  private int layer;

  /** Allows pushing obstacles aside. */
  private boolean pushEnabled;

  /** Allows dragging components with the route. */
  private boolean dragComponentsEnabled;

  // -------------------------------------------------------------------------
  // PropertyChangeListener API
  // -------------------------------------------------------------------------
  /**
   * Indicates if interactive selections are made on all visible layers or only on the current
   * layer.
   */
  private boolean selectOnAllVisibleLayers;

  /** Route mode: stitching or dynamic. */
  private boolean isStitchRoute;

  /** The width of the pull tight region of traces around the cursor. */
  private int tracePullTightRegionWidth;

  /** Via snaps to smd center, if attach smd is allowed. */
  private boolean viaSnapToSmdCenter;

  /** The horizontal placement grid when moving components, if positive. */
  private int horizontalComponentGrid;

  /** The vertical placement grid when moving components, if positive. */
  private int verticalComponentGrid;

  /**
   * Indicates if the routing rule selection is manual by the user or automatic by the net rules.
   */
  private boolean manualRuleSelection;

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  private boolean highlightRoutingObstacle;

  /** The index of the clearance class used for traces in interactive routing. */
  private int manualTraceClearanceClass;

  /**
   * The index of the via rule used in routing in the board via rules if manual_route_selection is
   * on.
   */
  private int manualViaRuleIndex;

  /** If true, the mouse wheel is used for zooming. */
  private boolean zoomWithWheel;

  /** The filter used in interactive selection of board items. */
  private ItemSelectionFilter itemSelectionFilter;

  /**
   * Indicates, if the data of this class are not allowed to be changed in interactive board
   * editing.
   */
  private transient boolean readOnly;

  /** Creates a new interactive settings variable. */
  public InteractiveSettings(RoutingBoard board) {
    super(null);
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
    tracePullTightAccuracy = 500;
    automaticNeckdown = true;
    manualTraceHalfWidthArr = new int[board.getLayerCount()];
    Arrays.fill(manualTraceHalfWidthArr, 1000);
    itemSelectionFilter = new ItemSelectionFilter();
  }

  /** Creates a new interactive settings variable bound to the active job settings. */
  public InteractiveSettings(RoutingBoard board, RouterSettings settings) {
    this(board);
    setSettings(settings);
  }

  /** Creates a copy of another interactive settings instance. */
  public InteractiveSettings(InteractiveSettings settings) {
    super(null);
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
    this.tracePullTightAccuracy = settings.tracePullTightAccuracy;
    this.automaticNeckdown = settings.automaticNeckdown;
    this.manualTraceHalfWidthArr = new int[settings.manualTraceHalfWidthArr.length];
    System.arraycopy(
        settings.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr,
        0,
        this.manualTraceHalfWidthArr.length);
    this.itemSelectionFilter = new ItemSelectionFilter(settings.itemSelectionFilter);
  }

  /**
   * Returns the singleton, creating it (bound to {@code board}) if not yet initialised.
   *
   * <p>In headless mode this method is never called; use {@link
   * app.freerouting.management.BoardManager#getInteractiveSettings()} to safely obtain the instance
   * (returns {@code null} when headless).
   *
   * @param board the routing board to bind the settings to on first creation
   * @param routerSettings the active job settings to bind to the GUI
   * @return the singleton {@link InteractiveSettings} instance
   */
  public static InteractiveSettings getOrCreate(RoutingBoard board, RouterSettings routerSettings) {
    if (instance == null) {
      synchronized (InteractiveSettings.class) {
        if (instance == null) {
          instance = new InteractiveSettings(board, routerSettings);
        }
      }
    } else if (routerSettings != null) {
      instance.setSettings(routerSettings);
    }
    return instance;
  }

  /**
   * Returns the singleton, creating it (bound to {@code board}) if not yet initialised.
   *
   * @param board the routing board to bind the settings to on first creation
   * @return the singleton {@link InteractiveSettings} instance
   */
  public static InteractiveSettings getOrCreate(RoutingBoard board) {
    return getOrCreate(board, null);
  }

  /**
   * Discards the current singleton and creates a fresh one bound to {@code board}.
   *
   * @param board the newly loaded {@link RoutingBoard}; must not be {@code null}
   * @param routerSettings the active job settings to bind to the GUI
   * @return the new singleton instance
   */
  public static InteractiveSettings reset(RoutingBoard board, RouterSettings routerSettings) {
    synchronized (InteractiveSettings.class) {
      instance = new InteractiveSettings(board, routerSettings);
      return instance;
    }
  }

  /**
   * Discards the current singleton and creates a fresh one bound to {@code board}.
   *
   * @param board the newly loaded {@link RoutingBoard}; must not be {@code null}
   * @return the new singleton instance
   */
  public static InteractiveSettings reset(RoutingBoard board) {
    return reset(board, null);
  }

  /**
   * Replaces the singleton with an already-constructed instance.
   *
   * <p>Used after binary deserialisation where the {@link InteractiveSettings} object is read
   * directly from the stream. The deserialized instance must become the authoritative singleton so
   * that subsequent {@link #getOrCreate} calls return it.
   *
   * @param is the deserialized instance; must not be {@code null}
   */
  static void setInstance(InteractiveSettings is) {
    synchronized (InteractiveSettings.class) {
      instance = is;
    }
  }

  /** Resets the singleton to {@code null}. <strong>For test use only.</strong> */
  static void resetForTesting() {
    instance = null;
  }

  /**
   * Registers a {@link PropertyChangeListener} that will be notified whenever a field on this
   * instance is mutated. GUI panels should register here and call {@code refresh()} (or update
   * individual controls) inside their {@code propertyChange} callback.
   *
   * @param listener the listener to add; ignored if {@code null}
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (listener != null) {
      pcs.addPropertyChangeListener(listener);
    }
  }

  /**
   * Registers a {@link PropertyChangeListener} for a specific named property.
   *
   * @param propertyName one of the {@code PROP_*} constants defined on this class
   * @param listener the listener to add; ignored if {@code null}
   */
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (listener != null) {
      pcs.addPropertyChangeListener(propertyName, listener);
    }
  }

  /**
   * Removes a previously registered {@link PropertyChangeListener}.
   *
   * @param listener the listener to remove; ignored if {@code null}
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (listener != null) {
      pcs.removePropertyChangeListener(listener);
    }
  }

  /**
   * Removes a {@link PropertyChangeListener} for a specific named property.
   *
   * @param propertyName one of the {@code PROP_*} constants defined on this class
   * @param listener the listener to remove; ignored if {@code null}
   */
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (listener != null) {
      pcs.removePropertyChangeListener(propertyName, listener);
    }
  }

  // -------------------------------------------------------------------------
  // Override getSettings() to return a live RouterSettings snapshot.
  // -------------------------------------------------------------------------

  /**
   * Returns a live {@link RouterSettings} snapshot built from the current field values of this
   * instance.
   *
   * <p>This override ensures that the {@link app.freerouting.settings.SettingsMerger} always reads
   * up-to-date GUI state at priority 50 rather than a stale static snapshot. It is called on every
   * {@code merge()} invocation (e.g. when the user starts the autorouter, saves settings, or the
   * toolbar rebuilds settings).
   *
   * @return a new {@link RouterSettings} containing the current GUI-controlled values
   */
  @Override
  public RouterSettings getSettings() {
    RouterSettings baseSettings = super.getSettings();
    if (baseSettings != null) {
      RouterSettings clone = baseSettings.clone();
      clone.tracePullTightAccuracy = this.getTracePullTightAccuracy();
      clone.automaticNeckdown = this.getAutomaticNeckdown();
      return clone;
    }
    RouterSettings snapshot = new RouterSettings();
    snapshot.tracePullTightAccuracy = this.tracePullTightAccuracy;
    snapshot.automaticNeckdown = this.automaticNeckdown;
    return snapshot;
  }

  /**
   * Returns the number of layers this settings object was created for. Equivalent to {@code
   * manualTraceHalfWidthArr.length}.
   *
   * @return the layer count
   */
  public int getLayerCount() {
    return manualTraceHalfWidthArr.length;
  }

  /** Returns the currently active layer index. */
  public int getLayer() {
    return this.layer;
  }

  /**
   * Sets the current active layer index and fires {@link #PROP_LAYER}.
   *
   * @param layerNo the new layer index
   */
  public void setLayer(int layerNo) {
    if (readOnly) {
      return;
    }
    int old = this.layer;
    layer = layerNo;
    pcs.firePropertyChange(PROP_LAYER, old, layerNo);
  }

  /** Returns the trace pull tight accuracy. */
  public int getTracePullTightAccuracy() {
    return tracePullTightAccuracy;
  }

  /** Sets the trace pull tight accuracy and fires {@link #PROP_TRACE_PULL_TIGHT_ACCURACY}. */
  public void setTracePullTightAccuracy(int value) {
    if (readOnly) {
      return;
    }
    int old = this.tracePullTightAccuracy;
    tracePullTightAccuracy = value;
    pcs.firePropertyChange(PROP_TRACE_PULL_TIGHT_ACCURACY, old, value);
  }

  /** Returns whether automatic neckdown is enabled in interactive routing. */
  public boolean getAutomaticNeckdown() {
    return automaticNeckdown;
  }

  /** Enables or disables automatic neckdown and fires {@link #PROP_AUTOMATIC_NECKDOWN}. */
  public void setAutomaticNeckdown(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.automaticNeckdown;
    automaticNeckdown = value;
    pcs.firePropertyChange(PROP_AUTOMATIC_NECKDOWN, old, value);
  }

  /** Allows pushing obstacles aside. */
  public boolean getPushEnabled() {
    return this.pushEnabled;
  }

  /** Enables or disables pushing obstacles and fires {@link #PROP_PUSH_ENABLED}. */
  public void setPushEnabled(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.pushEnabled;
    pushEnabled = value;
    pcs.firePropertyChange(PROP_PUSH_ENABLED, old, value);
  }

  /** Route mode: stitching or dynamic. */
  public boolean getIsStitchRoute() {
    return this.isStitchRoute;
  }

  /** Allows dragging components with the route. */
  public boolean getDragComponentsEnabled() {
    return this.dragComponentsEnabled;
  }

  /** Enables or disables dragging components and fires {@link #PROP_DRAG_COMPONENTS_ENABLED}. */
  public void setDragComponentsEnabled(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.dragComponentsEnabled;
    dragComponentsEnabled = value;
    pcs.firePropertyChange(PROP_DRAG_COMPONENTS_ENABLED, old, value);
  }

  /**
   * Indicates if interactive selections are made on all visible layers or only on the current
   * layer.
   */
  public boolean getSelectOnAllVisibleLayers() {
    return this.selectOnAllVisibleLayers;
  }

  /** Sets layer-selection scope and fires {@link #PROP_SELECT_ON_ALL_VISIBLE_LAYERS}. */
  public void setSelectOnAllVisibleLayers(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.selectOnAllVisibleLayers;
    selectOnAllVisibleLayers = value;
    pcs.firePropertyChange(PROP_SELECT_ON_ALL_VISIBLE_LAYERS, old, value);
  }

  /** Indicates if the routing rule selection is manual or automatic. */
  public boolean getManualRuleSelection() {
    return this.manualRuleSelection;
  }

  /** Via snaps to smd center, if attach smd is allowed. */
  public boolean getViaSnapToSmdCenter() {
    return this.viaSnapToSmdCenter;
  }

  /** Changes via snap to SMD center and fires {@link #PROP_VIA_SNAP_TO_SMD_CENTER}. */
  public void setViaSnapToSmdCenter(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.viaSnapToSmdCenter;
    viaSnapToSmdCenter = value;
    pcs.firePropertyChange(PROP_VIA_SNAP_TO_SMD_CENTER, old, value);
  }

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  public boolean getHighlightRoutingObstacle() {
    return this.highlightRoutingObstacle;
  }

  /** Sets highlight routing obstacle and fires {@link #PROP_HIGHLIGHT_ROUTING_OBSTACLE}. */
  public void setHighlightRoutingObstacle(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.highlightRoutingObstacle;
    this.highlightRoutingObstacle = value;
    pcs.firePropertyChange(PROP_HIGHLIGHT_ROUTING_OBSTACLE, old, value);
  }

  /** If true, the mouse wheel is used for zooming. */
  public boolean getZoomWithWheel() {
    return this.zoomWithWheel;
  }

  /** Sets zoom-with-wheel and fires {@link #PROP_ZOOM_WITH_WHEEL}. */
  public void setZoomWithWheel(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.zoomWithWheel;
    if (zoomWithWheel != value) {
      zoomWithWheel = value;
      pcs.firePropertyChange(PROP_ZOOM_WITH_WHEEL, old, value);
    }
  }

  /** The filter used in interactive selection of board items. */
  public ItemSelectionFilter getItemSelectionFilter() {
    return this.itemSelectionFilter;
  }

  /** Sets the item selection filter and fires {@link #PROP_ITEM_SELECTION_FILTER}. */
  public void setItemSelectionFilter(ItemSelectionFilter value) {
    if (readOnly) {
      return;
    }
    ItemSelectionFilter old = this.itemSelectionFilter;
    this.itemSelectionFilter = value;
    pcs.firePropertyChange(PROP_ITEM_SELECTION_FILTER, old, value);
  }

  /** The width of the pull tight region of traces around the cursor. */
  public int getTracePullTightRegionWidth() {
    return this.tracePullTightRegionWidth;
  }

  /** The horizontal placement grid when moving components, if positive. */
  public int getHorizontalComponentGrid() {
    return this.horizontalComponentGrid;
  }

  /** Sets the horizontal component grid and fires {@link #PROP_HORIZONTAL_COMPONENT_GRID}. */
  public void setHorizontalComponentGrid(int value) {
    if (readOnly) {
      return;
    }
    int old = this.horizontalComponentGrid;
    this.horizontalComponentGrid = value;
    pcs.firePropertyChange(PROP_HORIZONTAL_COMPONENT_GRID, old, value);
  }

  /** The vertical placement grid when moving components, if positive. */
  public int getVerticalComponentGrid() {
    return this.verticalComponentGrid;
  }

  /** Sets the vertical component grid and fires {@link #PROP_VERTICAL_COMPONENT_GRID}. */
  public void setVerticalComponentGrid(int value) {
    if (readOnly) {
      return;
    }
    int old = this.verticalComponentGrid;
    this.verticalComponentGrid = value;
    pcs.firePropertyChange(PROP_VERTICAL_COMPONENT_GRID, old, value);
  }

  /** The index of the clearance class used for traces in interactive routing. */
  public int getManualTraceClearanceClass() {
    return this.manualTraceClearanceClass;
  }

  /** Sets the manual trace clearance class and fires {@link #PROP_MANUAL_TRACE_CLEARANCE_CLASS}. */
  public void setManualTraceClearanceClass(int index) {
    if (readOnly) {
      return;
    }
    int old = this.manualTraceClearanceClass;
    manualTraceClearanceClass = index;
    pcs.firePropertyChange(PROP_MANUAL_TRACE_CLEARANCE_CLASS, old, index);
  }

  /** The index of the via rule used in routing. */
  public int getManualViaRuleIndex() {
    return this.manualViaRuleIndex;
  }

  /** Sets the manual via rule index and fires {@link #PROP_MANUAL_VIA_RULE_INDEX}. */
  public void setManualViaRuleIndex(int value) {
    if (readOnly) {
      return;
    }
    int old = this.manualViaRuleIndex;
    this.manualViaRuleIndex = value;
    pcs.firePropertyChange(PROP_MANUAL_VIA_RULE_INDEX, old, value);
  }

  /** Returns the trace half-width in manual routing mode on the specified layer. */
  public int getManualTraceHalfWidth(int layerNo) {
    if (layerNo < 0 || layerNo >= this.manualTraceHalfWidthArr.length) {
      FRLogger.warn("InteractiveSettings.get_manual_trace_half_width layer number out of range");
      return 0;
    }
    return this.manualTraceHalfWidthArr[layerNo];
  }

  /** Route mode: stitching or dynamic. Fires {@link #PROP_IS_STITCH_ROUTE}. */
  public void setStitchRoute(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.isStitchRoute;
    isStitchRoute = value;
    pcs.firePropertyChange(PROP_IS_STITCH_ROUTE, old, value);
  }

  /**
   * Changes the current width of the tidy region for traces. Fires {@link
   * #PROP_TRACE_PULL_TIGHT_REGION_WIDTH}.
   */
  public void setCurrentPullTightRegionWidth(int value) {
    if (readOnly) {
      return;
    }
    int old = this.tracePullTightRegionWidth;
    tracePullTightRegionWidth = value;
    pcs.firePropertyChange(PROP_TRACE_PULL_TIGHT_REGION_WIDTH, old, value);
  }

  /**
   * Sets the current trace width selection to manual or automatic. Fires {@link
   * #PROP_MANUAL_RULE_SELECTION}.
   */
  public void setManualTracewidthSelection(boolean value) {
    if (readOnly) {
      return;
    }
    boolean old = this.manualRuleSelection;
    manualRuleSelection = value;
    pcs.firePropertyChange(PROP_MANUAL_RULE_SELECTION, old, value);
  }

  /**
   * Sets the manual trace half width used in interactive routing. Fires {@link
   * #PROP_MANUAL_TRACE_HALF_WIDTH}.
   */
  public void setManualTraceHalfWidth(int layerNo, int value) {
    if (readOnly) {
      return;
    }
    int old = manualTraceHalfWidthArr[layerNo];
    manualTraceHalfWidthArr[layerNo] = value;
    pcs.firePropertyChange(PROP_MANUAL_TRACE_HALF_WIDTH, old, value);
  }

  /**
   * Changes the interactive selectability of the specified item type. Fires {@link
   * #PROP_ITEM_SELECTION_FILTER}.
   */
  public void setSelectable(ItemSelectionFilter.SelectableChoices itemType, boolean value) {
    if (readOnly) {
      return;
    }
    itemSelectionFilter.setSelected(itemType, value);
    pcs.firePropertyChange(PROP_ITEM_SELECTION_FILTER, null, itemSelectionFilter);
  }

  /** Defines, if the setting attributes are allowed to be changed interactively or not. */
  public void setReadOnly(Boolean value) {
    this.readOnly = value;
  }

  /**
   * Reads an instance of this class from a file. Re-initialises the transient {@link
   * PropertyChangeSupport} that is not part of the serialised form.
   */
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    // Re-create the transient PropertyChangeSupport after deserialisation.
    this.pcs = new PropertyChangeSupport(this);
    if (this.itemSelectionFilter == null) {
      FRLogger.warn("InteractiveSettings.readObject: itemSelectionFilter is null");
      this.itemSelectionFilter = new ItemSelectionFilter();
    }
    this.readOnly = false;
  }
}
