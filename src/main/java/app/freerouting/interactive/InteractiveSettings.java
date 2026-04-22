package app.freerouting.interactive;

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
 * <p>This class is the concrete {@link GuiSettings} source (priority 50) supplied to the
 * {@link app.freerouting.settings.SettingsMerger}. Any field mutation is therefore visible to the
 * router settings pipeline on the next {@code merge()} call.
 *
 * <p>In GUI mode this class acts as the concrete {@link GuiSettings} source at priority 50 in the
 * {@link app.freerouting.settings.SettingsMerger} pipeline. Use {@link #getOrCreate(RoutingBoard)}
 * to obtain the singleton instance; never construct it directly from GUI code.
 *
 * <p><strong>Singleton contract:</strong> exactly one instance exists for the lifetime of a GUI
 * session. Use {@link #getOrCreate(RoutingBoard)} to obtain it. In headless mode the instance is
 * {@code null}; use {@link BoardManager#getInteractiveSettings()} to safely obtain it.
 *
 * <p><strong>Two-way binding:</strong> every setter fires a named {@link java.beans.PropertyChangeEvent}
 * via {@link PropertyChangeSupport}. GUI panels should register as {@link PropertyChangeListener}s
 * on this instance and call {@link #refresh()} (or update individual controls) in their
 * {@code propertyChange} callback. Use {@link #addPropertyChangeListener} /
 * {@link #removePropertyChangeListener} to subscribe.
 *
 * @see GuiSettings
 * @see app.freerouting.settings.SettingsMerger
 */
public class InteractiveSettings extends GuiSettings implements Serializable {

  // -------------------------------------------------------------------------
  // Named property keys – use these constants everywhere to avoid typos.
  // -------------------------------------------------------------------------
  public static final String PROP_LAYER                      = "layer";
  public static final String PROP_PUSH_ENABLED               = "push_enabled";
  public static final String PROP_DRAG_COMPONENTS_ENABLED    = "drag_components_enabled";
  public static final String PROP_SELECT_ON_ALL_VISIBLE_LAYERS = "select_on_all_visible_layers";
  public static final String PROP_IS_STITCH_ROUTE            = "is_stitch_route";
  public static final String PROP_TRACE_PULL_TIGHT_REGION_WIDTH = "trace_pull_tight_region_width";
  public static final String PROP_VIA_SNAP_TO_SMD_CENTER     = "via_snap_to_smd_center";
  public static final String PROP_HORIZONTAL_COMPONENT_GRID  = "horizontal_component_grid";
  public static final String PROP_VERTICAL_COMPONENT_GRID    = "vertical_component_grid";
  public static final String PROP_MANUAL_RULE_SELECTION      = "manual_rule_selection";
  public static final String PROP_HILIGHT_ROUTING_OBSTACLE   = "hilight_routing_obstacle";
  public static final String PROP_MANUAL_TRACE_CLEARANCE_CLASS = "manual_trace_clearance_class";
  public static final String PROP_MANUAL_VIA_RULE_INDEX      = "manual_via_rule_index";
  public static final String PROP_ZOOM_WITH_WHEEL            = "zoom_with_wheel";
  public static final String PROP_ITEM_SELECTION_FILTER      = "item_selection_filter";
  public static final String PROP_TRACE_PULL_TIGHT_ACCURACY  = "trace_pull_tight_accuracy";
  public static final String PROP_AUTOMATIC_NECKDOWN         = "automatic_neckdown";
  public static final String PROP_MANUAL_TRACE_HALF_WIDTH    = "manual_trace_half_width";

  /** The single GUI-session instance; {@code null} when running headless. */
  private static volatile InteractiveSettings instance;

  // -------------------------------------------------------------------------
  // PropertyChangeSupport — transient so it is not serialised; re-created in readObject.
  // -------------------------------------------------------------------------
  private transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  /**
   * Returns the singleton, creating it (bound to {@code board}) if not yet initialised.
   *
   * <p>In headless mode this method is never called; use
   * {@link BoardManager#getInteractiveSettings()} to safely obtain the instance (returns
   * {@code null} when headless).
   *
   * @param board the routing board to bind the settings to on first creation
   * @return the singleton {@link InteractiveSettings} instance
   */
  public static InteractiveSettings getOrCreate(RoutingBoard board) {
    if (instance == null) {
      synchronized (InteractiveSettings.class) {
        if (instance == null) {
          instance = new InteractiveSettings(board);
        }
      }
    }
    return instance;
  }

  /**
   * Discards the current singleton and creates a fresh one bound to {@code board}.
   *
   * <p>Must be called whenever a new design is loaded into the GUI session (DSN or binary load),
   * because the new board may have a different layer count, net list, or design rules than the
   * previous one. An {@code InteractiveSettings} constructed for the old board is invalid for the
   * new one.
   *
   * <p>After this call all {@code PropertyChangeListener} panels must be re-registered on the new
   * instance. {@link GuiBoardManager#refreshGuiFromSettings()} is the canonical place to do this.
   *
   * @param board the newly loaded {@link RoutingBoard}; must not be {@code null}
   * @return the new singleton instance
   */
  public static InteractiveSettings reset(RoutingBoard board) {
    synchronized (InteractiveSettings.class) {
      instance = new InteractiveSettings(board);
      return instance;
    }
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

  /**
   * Resets the singleton to {@code null}. <strong>For test use only.</strong>
   */
  static void resetForTesting() {
    instance = null;
  }

  // -------------------------------------------------------------------------
  // PropertyChangeListener API
  // -------------------------------------------------------------------------

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
   * Registers a {@link PropertyChangeListener} for a specific named property.
   *
   * @param propertyName one of the {@code PROP_*} constants defined on this class
   * @param listener     the listener to add; ignored if {@code null}
   */
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (listener != null) {
      pcs.addPropertyChangeListener(propertyName, listener);
    }
  }

  /**
   * Removes a {@link PropertyChangeListener} for a specific named property.
   *
   * @param propertyName one of the {@code PROP_*} constants defined on this class
   * @param listener     the listener to remove; ignored if {@code null}
   */
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    if (listener != null) {
      pcs.removePropertyChangeListener(propertyName, listener);
    }
  }

  /**
   * The array of manual trace half widths, initially equal to the automatic trace half widths.
   * This is a {@code final} array reference; individual entries are mutated via
   * {@link #set_manual_trace_half_width(int, int)}.
   */
  final int[] manual_trace_half_width_arr;

  /** Router parameter: accuracy for trace pull tight operations in interactive routing. */
  private int trace_pull_tight_accuracy = 500;
  /** Router parameter: enables automatic neckdown in interactive routing. */
  private boolean automatic_neckdown = true;
  /** The current layer index. */
  private int layer;
  /** Allows pushing obstacles aside. */
  private boolean push_enabled;
  /** Allows dragging components with the route. */
  private boolean drag_components_enabled;
  /** Indicates if interactive selections are made on all visible layers or only on the current layer. */
  private boolean select_on_all_visible_layers;
  /** Route mode: stitching or dynamic. */
  private boolean is_stitch_route;
  /** The width of the pull tight region of traces around the cursor. */
  private int trace_pull_tight_region_width;
  /** Via snaps to smd center, if attach smd is allowed. */
  private boolean via_snap_to_smd_center;
  /** The horizontal placement grid when moving components, if &gt; 0. */
  private int horizontal_component_grid;
  /** The vertical placement grid when moving components, if &gt; 0. */
  private int vertical_component_grid;
  /** Indicates if the routing rule selection is manual by the user or automatic by the net rules. */
  private boolean manual_rule_selection;
  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  private boolean hilight_routing_obstacle;
  /** The index of the clearance class used for traces in interactive routing. */
  private int manual_trace_clearance_class;
  /** The index of the via rule used in routing in the board via rules if manual_route_selection is on. */
  private int manual_via_rule_index;
  /** If true, the mouse wheel is used for zooming. */
  private boolean zoom_with_wheel;
  /** The filter used in interactive selection of board items. */
  private ItemSelectionFilter item_selection_filter;

  /**
   * Indicates, if the data of this class are not allowed to be changed in
   * interactive board editing.
   */
  private transient boolean read_only;

  /**
   * Creates a new interactive settings variable.
   */
  public InteractiveSettings(RoutingBoard p_board) {
    super(null);
    // Initialise with default values.
    layer = 0;
    push_enabled = true;
    drag_components_enabled = true;
    select_on_all_visible_layers = true; // else selection is only on the current layer
    is_stitch_route = false; // else interactive routing is dynamic
    trace_pull_tight_region_width = Integer.MAX_VALUE;
    via_snap_to_smd_center = true;
    horizontal_component_grid = 0;
    vertical_component_grid = 0;
    manual_rule_selection = false;
    hilight_routing_obstacle = false;
    manual_trace_clearance_class = 1;
    manual_via_rule_index = 0;
    zoom_with_wheel = true;
    trace_pull_tight_accuracy = 500;
    automatic_neckdown = true;
    manual_trace_half_width_arr = new int[p_board.get_layer_count()];
    Arrays.fill(manual_trace_half_width_arr, 1000);
    item_selection_filter = new ItemSelectionFilter();
  }

  /**
   * Copy constructor
   */
  public InteractiveSettings(InteractiveSettings p_settings) {
    super(null);
    this.read_only = p_settings.read_only;
    this.layer = p_settings.layer;
    this.push_enabled = p_settings.push_enabled;
    this.drag_components_enabled = p_settings.drag_components_enabled;
    this.select_on_all_visible_layers = p_settings.select_on_all_visible_layers;
    this.is_stitch_route = p_settings.is_stitch_route;
    this.trace_pull_tight_region_width = p_settings.trace_pull_tight_region_width;
    this.via_snap_to_smd_center = p_settings.via_snap_to_smd_center;
    this.horizontal_component_grid = p_settings.horizontal_component_grid;
    this.vertical_component_grid = p_settings.vertical_component_grid;
    this.manual_rule_selection = p_settings.manual_rule_selection;
    this.hilight_routing_obstacle = p_settings.hilight_routing_obstacle;
    this.zoom_with_wheel = p_settings.zoom_with_wheel;
    this.manual_trace_clearance_class = p_settings.manual_trace_clearance_class;
    this.manual_via_rule_index = p_settings.manual_via_rule_index;
    this.trace_pull_tight_accuracy = p_settings.trace_pull_tight_accuracy;
    this.automatic_neckdown = p_settings.automatic_neckdown;
    this.manual_trace_half_width_arr = new int[p_settings.manual_trace_half_width_arr.length];
    System.arraycopy(p_settings.manual_trace_half_width_arr, 0, this.manual_trace_half_width_arr, 0,
        this.manual_trace_half_width_arr.length);
    this.item_selection_filter = new ItemSelectionFilter(p_settings.item_selection_filter);
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
   * <p>Only fields that {@code InteractiveSettings} actually owns are populated; fields that belong
   * exclusively to other settings sources (e.g. {@code maxPasses}, {@code maxThreads}) are left
   * {@code null} so the merger correctly resolves them from their authoritative sources.
   *
   * <p>Currently mapped fields:
   * <ul>
   *   <li>{@link RouterSettings#trace_pull_tight_accuracy} ← {@link #trace_pull_tight_accuracy}</li>
   *   <li>{@link RouterSettings#automatic_neckdown} ← {@link #automatic_neckdown}</li>
   * </ul>
   *
   * @return a new {@link RouterSettings} containing the current GUI-controlled values
   */
  @Override
  public RouterSettings getSettings() {
    RouterSettings snapshot = new RouterSettings();
    snapshot.trace_pull_tight_accuracy = this.trace_pull_tight_accuracy;
    snapshot.automatic_neckdown = this.automatic_neckdown;
    return snapshot;
  }

  /**
   * Returns the number of layers this settings object was created for.
   * Equivalent to {@code manual_trace_half_width_arr.length}.
   *
   * @return the layer count
   */
  public int get_layer_count() {
    return manual_trace_half_width_arr.length;
  }

  public int get_layer() {
    return this.layer;
  }

  /**
   * Sets the current active layer index and fires {@link #PROP_LAYER}.
   *
   * @param p_layer_no the new layer index
   */
  public void set_layer(int p_layer_no) {
    if (read_only) {
      return;
    }
    int old = this.layer;
    layer = p_layer_no;
    pcs.firePropertyChange(PROP_LAYER, old, p_layer_no);
  }

  /** Returns the trace pull tight accuracy. */
  public int get_trace_pull_tight_accuracy() {
    return trace_pull_tight_accuracy;
  }

  /**
   * Sets the trace pull tight accuracy and fires {@link #PROP_TRACE_PULL_TIGHT_ACCURACY}.
   */
  public void set_trace_pull_tight_accuracy(int p_value) {
    if (read_only) {
      return;
    }
    int old = this.trace_pull_tight_accuracy;
    trace_pull_tight_accuracy = p_value;
    pcs.firePropertyChange(PROP_TRACE_PULL_TIGHT_ACCURACY, old, p_value);
  }

  /** Returns whether automatic neckdown is enabled in interactive routing. */
  public boolean get_automatic_neckdown() {
    return automatic_neckdown;
  }

  /**
   * Enables or disables automatic neckdown and fires {@link #PROP_AUTOMATIC_NECKDOWN}.
   */
  public void set_automatic_neckdown(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.automatic_neckdown;
    automatic_neckdown = p_value;
    pcs.firePropertyChange(PROP_AUTOMATIC_NECKDOWN, old, p_value);
  }

  /** Allows pushing obstacles aside. */
  public boolean get_push_enabled() {
    return this.push_enabled;
  }

  /**
   * Enables or disables pushing obstacles and fires {@link #PROP_PUSH_ENABLED}.
   */
  public void set_push_enabled(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.push_enabled;
    push_enabled = p_value;
    pcs.firePropertyChange(PROP_PUSH_ENABLED, old, p_value);
  }

  /** Route mode: stitching or dynamic. */
  public boolean get_is_stitch_route() {
    return this.is_stitch_route;
  }

  /** Allows dragging components with the route. */
  public boolean get_drag_components_enabled() {
    return this.drag_components_enabled;
  }

  /**
   * Enables or disables dragging components and fires {@link #PROP_DRAG_COMPONENTS_ENABLED}.
   */
  public void set_drag_components_enabled(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.drag_components_enabled;
    drag_components_enabled = p_value;
    pcs.firePropertyChange(PROP_DRAG_COMPONENTS_ENABLED, old, p_value);
  }

  /** Indicates if interactive selections are made on all visible layers or only on the current layer. */
  public boolean get_select_on_all_visible_layers() {
    return this.select_on_all_visible_layers;
  }

  /**
   * Sets layer-selection scope and fires {@link #PROP_SELECT_ON_ALL_VISIBLE_LAYERS}.
   */
  public void set_select_on_all_visible_layers(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.select_on_all_visible_layers;
    select_on_all_visible_layers = p_value;
    pcs.firePropertyChange(PROP_SELECT_ON_ALL_VISIBLE_LAYERS, old, p_value);
  }

  /** Indicates if the routing rule selection is manual or automatic. */
  public boolean get_manual_rule_selection() {
    return this.manual_rule_selection;
  }

  /** Via snaps to smd center, if attach smd is allowed. */
  public boolean get_via_snap_to_smd_center() {
    return this.via_snap_to_smd_center;
  }

  /**
   * Changes via snap to SMD center and fires {@link #PROP_VIA_SNAP_TO_SMD_CENTER}.
   */
  public void set_via_snap_to_smd_center(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.via_snap_to_smd_center;
    via_snap_to_smd_center = p_value;
    pcs.firePropertyChange(PROP_VIA_SNAP_TO_SMD_CENTER, old, p_value);
  }

  /** If true, the current routing obstacle is highlighted in dynamic routing. */
  public boolean get_hilight_routing_obstacle() {
    return this.hilight_routing_obstacle;
  }

  /**
   * Sets hilight routing obstacle and fires {@link #PROP_HILIGHT_ROUTING_OBSTACLE}.
   */
  public void set_hilight_routing_obstacle(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.hilight_routing_obstacle;
    this.hilight_routing_obstacle = p_value;
    pcs.firePropertyChange(PROP_HILIGHT_ROUTING_OBSTACLE, old, p_value);
  }

  /** If true, the mouse wheel is used for zooming. */
  public boolean get_zoom_with_wheel() {
    return this.zoom_with_wheel;
  }

  /**
   * Sets zoom-with-wheel and fires {@link #PROP_ZOOM_WITH_WHEEL}.
   */
  public void set_zoom_with_wheel(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.zoom_with_wheel;
    if (zoom_with_wheel != p_value) {
      zoom_with_wheel = p_value;
      pcs.firePropertyChange(PROP_ZOOM_WITH_WHEEL, old, p_value);
    }
  }

  /** The filter used in interactive selection of board items. */
  public ItemSelectionFilter get_item_selection_filter() {
    return this.item_selection_filter;
  }

  /**
   * Sets the item selection filter and fires {@link #PROP_ITEM_SELECTION_FILTER}.
   */
  public void set_item_selection_filter(ItemSelectionFilter p_value) {
    if (read_only) {
      return;
    }
    ItemSelectionFilter old = this.item_selection_filter;
    this.item_selection_filter = p_value;
    pcs.firePropertyChange(PROP_ITEM_SELECTION_FILTER, old, p_value);
  }

  /** The width of the pull tight region of traces around the cursor. */
  public int get_trace_pull_tight_region_width() {
    return this.trace_pull_tight_region_width;
  }

  /** The horizontal placement grid when moving components, if &gt; 0. */
  public int get_horizontal_component_grid() {
    return this.horizontal_component_grid;
  }

  /**
   * Sets the horizontal component grid and fires {@link #PROP_HORIZONTAL_COMPONENT_GRID}.
   */
  public void set_horizontal_component_grid(int p_value) {
    if (read_only) {
      return;
    }
    int old = this.horizontal_component_grid;
    this.horizontal_component_grid = p_value;
    pcs.firePropertyChange(PROP_HORIZONTAL_COMPONENT_GRID, old, p_value);
  }

  /** The vertical placement grid when moving components, if &gt; 0. */
  public int get_vertical_component_grid() {
    return this.vertical_component_grid;
  }

  /**
   * Sets the vertical component grid and fires {@link #PROP_VERTICAL_COMPONENT_GRID}.
   */
  public void set_vertical_component_grid(int p_value) {
    if (read_only) {
      return;
    }
    int old = this.vertical_component_grid;
    this.vertical_component_grid = p_value;
    pcs.firePropertyChange(PROP_VERTICAL_COMPONENT_GRID, old, p_value);
  }

  /**
   * The index of the clearance class used for traces in interactive routing.
   */
  public int get_manual_trace_clearance_class() {
    return this.manual_trace_clearance_class;
  }

  /**
   * Sets the manual trace clearance class and fires {@link #PROP_MANUAL_TRACE_CLEARANCE_CLASS}.
   */
  public void set_manual_trace_clearance_class(int p_index) {
    if (read_only) {
      return;
    }
    int old = this.manual_trace_clearance_class;
    manual_trace_clearance_class = p_index;
    pcs.firePropertyChange(PROP_MANUAL_TRACE_CLEARANCE_CLASS, old, p_index);
  }

  /**
   * The index of the via rule used in routing.
   */
  public int get_manual_via_rule_index() {
    return this.manual_via_rule_index;
  }

  /**
   * Sets the manual via rule index and fires {@link #PROP_MANUAL_VIA_RULE_INDEX}.
   */
  public void set_manual_via_rule_index(int p_value) {
    if (read_only) {
      return;
    }
    int old = this.manual_via_rule_index;
    this.manual_via_rule_index = p_value;
    pcs.firePropertyChange(PROP_MANUAL_VIA_RULE_INDEX, old, p_value);
  }

  /**
   * Get the trace half width in manual routing mode on layer p_layer_no.
   */
  public int get_manual_trace_half_width(int p_layer_no) {
    if (p_layer_no < 0 || p_layer_no >= this.manual_trace_half_width_arr.length) {
      FRLogger.warn("InteractiveSettings.get_manual_trace_half_width p_layer_no out of range");
      return 0;
    }
    return this.manual_trace_half_width_arr[p_layer_no];
  }

  /**
   * Route mode: stitching or dynamic. Fires {@link #PROP_IS_STITCH_ROUTE}.
   */
  public void set_stitch_route(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.is_stitch_route;
    is_stitch_route = p_value;
    pcs.firePropertyChange(PROP_IS_STITCH_ROUTE, old, p_value);
  }

  /**
   * Changes the current width of the tidy region for traces. Fires {@link #PROP_TRACE_PULL_TIGHT_REGION_WIDTH}.
   */
  public void set_current_pull_tight_region_width(int p_value) {
    if (read_only) {
      return;
    }
    int old = this.trace_pull_tight_region_width;
    trace_pull_tight_region_width = p_value;
    pcs.firePropertyChange(PROP_TRACE_PULL_TIGHT_REGION_WIDTH, old, p_value);
  }

  /**
   * Sets the current trace width selection to manual or automatic. Fires {@link #PROP_MANUAL_RULE_SELECTION}.
   */
  public void set_manual_tracewidth_selection(boolean p_value) {
    if (read_only) {
      return;
    }
    boolean old = this.manual_rule_selection;
    manual_rule_selection = p_value;
    pcs.firePropertyChange(PROP_MANUAL_RULE_SELECTION, old, p_value);
  }

  /**
   * Sets the manual trace half width used in interactive routing. Fires {@link #PROP_MANUAL_TRACE_HALF_WIDTH}.
   */
  public void set_manual_trace_half_width(int p_layer_no, int p_value) {
    if (read_only) {
      return;
    }
    int old = manual_trace_half_width_arr[p_layer_no];
    manual_trace_half_width_arr[p_layer_no] = p_value;
    pcs.firePropertyChange(PROP_MANUAL_TRACE_HALF_WIDTH, old, p_value);
  }

  /**
   * Changes the interactive selectability of p_item_type. Fires {@link #PROP_ITEM_SELECTION_FILTER}.
   */
  public void set_selectable(ItemSelectionFilter.SelectableChoices p_item_type, boolean p_value) {
    if (read_only) {
      return;
    }
    item_selection_filter.set_selected(p_item_type, p_value);
    pcs.firePropertyChange(PROP_ITEM_SELECTION_FILTER, null, item_selection_filter);
  }

  /**
   * Defines, if the setting attributes are allowed to be changed interactively or not.
   */
  public void set_read_only(Boolean p_value) {
    this.read_only = p_value;
  }

  /**
   * Reads an instance of this class from a file. Re-initialises the transient
   * {@link PropertyChangeSupport} that is not part of the serialised form.
   */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    // Re-create the transient PropertyChangeSupport after deserialisation.
    this.pcs = new PropertyChangeSupport(this);
    if (this.item_selection_filter == null) {
      FRLogger.warn("InteractiveSettings.readObject: item_selection_filter is null");
      this.item_selection_filter = new ItemSelectionFilter();
    }
    this.read_only = false;
  }
}

