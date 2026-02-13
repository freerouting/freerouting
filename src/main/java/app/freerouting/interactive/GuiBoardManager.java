package app.freerouting.interactive;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.SearchTreeManager;
import app.freerouting.board.Unit;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.RoutingJob;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.designforms.specctra.SessionToEagle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.ComboBoxLayer;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.logger.TraceEvent;
import app.freerouting.logger.TraceEventListener;
import app.freerouting.management.TextManager;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Manages the routing board operations with a graphical user interface.
 *
 * <p>This class extends {@link HeadlessBoardManager} to provide GUI-specific functionality,
 * enabling visual interaction with the routing board. It serves as the central controller
 * for interactive routing operations, coordinating between:
 * <ul>
 *   <li>User input and mouse interactions</li>
 *   <li>Board display and graphics rendering</li>
 *   <li>Interactive states and routing modes</li>
 *   <li>Autorouting and manual routing operations</li>
 *   <li>Design rule checking and violation display</li>
 *   <li>Undo/redo and board history management</li>
 * </ul>
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>State Management:</strong> Controls interactive states (routing, selecting, dragging, etc.)</li>
 *   <li><strong>Graphics Coordination:</strong> Manages coordinate transformations and display context</li>
 *   <li><strong>User Interaction:</strong> Handles mouse events and keyboard input</li>
 *   <li><strong>Visual Feedback:</strong> Displays rats nest, clearance violations, and trace feedback</li>
 *   <li><strong>Thread Management:</strong> Coordinates background operations (autorouting, optimization)</li>
 *   <li><strong>File Operations:</strong> Manages design loading, saving, and session file handling</li>
 * </ul>
 *
 * <p><strong>Interactive States:</strong>
 * The board manager uses a state pattern to handle different interaction modes:
 * <ul>
 *   <li>RouteMenuState - Default selection and menu state</li>
 *   <li>RouteState - Interactive trace routing</li>
 *   <li>DragState - Moving items on the board</li>
 *   <li>SelectState - Selecting items for operations</li>
 *   <li>And various other specialized states</li>
 * </ul>
 *
 * <p><strong>Coordinate Systems:</strong>
 * This class manages transformations between multiple coordinate spaces:
 * <ul>
 *   <li>Screen coordinates (pixels on display)</li>
 *   <li>User coordinates (design units visible to user)</li>
 *   <li>Board coordinates (internal integer coordinates)</li>
 * </ul>
 *
 * @see HeadlessBoardManager
 * @see InteractiveState
 * @see BoardPanel
 * @see GraphicsContext
 */
public class GuiBoardManager extends HeadlessBoardManager {

  /**
   * The minimum interval in milliseconds between consecutive board panel repaints.
   *
   * <p>This throttle mechanism prevents excessive repainting during intensive operations,
   * maintaining a maximum effective frame rate of 1 FPS (1000ms interval). This is
   * particularly important during autorouting and optimization when many board changes
   * occur rapidly.
   */
  private static final long repaint_interval = 1000;

  /**
   * The timestamp of the most recent board panel repaint operation.
   *
   * <p>Used in conjunction with repaint_interval to implement repaint throttling.
   * Tracked in milliseconds since epoch.
   */
  private static long last_repainted_time;

  /**
   * Manager for on-screen status and information messages.
   *
   * <p>Displays messages to the user including:
   * <ul>
   *   <li>Current operation status</li>
   *   <li>Error and warning counts</li>
   *   <li>Trace event information</li>
   *   <li>Interactive prompts and feedback</li>
   * </ul>
   *
   * @see ScreenMessages
   */
  public final ScreenMessages screen_messages;
  /**
   * Merger that consolidates router settings from multiple sources.
   *
   * <p>Combines settings from:
   * <ul>
   *   <li>Default application settings</li>
   *   <li>Design-specific settings from DSN files</li>
   *   <li>User preferences and overrides</li>
   * </ul>
   *
   * @see SettingsMerger
   */
  public final SettingsMerger settingsMerger;

  /**
   * The graphical panel component that displays and renders the routing board.
   *
   * <p>This panel handles the visual presentation of the board, providing:
   * <ul>
   *   <li>Rendering of board items (traces, vias, pins, etc.)</li>
   *   <li>Display of auxiliary information (rats nest, violations, etc.)</li>
   *   <li>Visual feedback during interactive operations</li>
   *   <li>Screen message display integration</li>
   * </ul>
   *
   * @see BoardPanel
   */
  private final BoardPanel panel;

  /**
   * Text manager for internationalized message strings.
   *
   * <p>Provides localized text for UI elements and messages based on the
   * current locale setting.
   */
  private final TextManager tm;

  /**
   * Collection of listeners notified when the board's read-only state changes.
   *
   * <p>UI components register listeners to update their state (enabled/disabled)
   * when the board becomes read-only (e.g., during autorouting or logfile playback).
   */
  private final List<Consumer<Boolean>> readOnlyEventListeners = new ArrayList<>();
  /**
   * Global application settings container.
   *
   * <p>Provides access to application-wide configuration including locale,
   * thread pool settings, and other global preferences.
   */
  private final GlobalSettings globalSettings;

  /**
   * Listener that responds to new log entries being added.
   *
   * <p>Updates the on-screen error and warning counters when errors or
   * warnings are logged during operations.
   */
  private final LogEntries.LogEntryAddedListener logEntryAddedListener;

  /**
   * Listener that responds to trace events during routing operations.
   *
   * <p>Handles trace-level debugging events, displaying impacted items and
   * points on the board for diagnostic purposes.
   */
  private final TraceEventListener traceEventListener;
  /**
   * Graphics context managing visual display settings for the board.
   *
   * <p>Controls rendering aspects including:
   * <ul>
   *   <li>Layer visibility and transparency</li>
   *   <li>Color schemes for different item types</li>
   *   <li>Display modes and visual options</li>
   *   <li>Rendering quality and performance settings</li>
   * </ul>
   *
   * @see GraphicsContext
   */
  public GraphicsContext graphics_context;

  /**
   * Coordinate transformer for converting between different coordinate systems.
   *
   * <p>Handles transformations between:
   * <ul>
   *   <li>Screen coordinates (pixels)</li>
   *   <li>User coordinates (design units: mm, mil, inch)</li>
   *   <li>Board coordinates (internal integer units)</li>
   * </ul>
   *
   * <p>Also manages zoom level, pan offset, and coordinate system scaling.
   *
   * @see CoordinateTransform
   */
  public CoordinateTransform coordinate_transform;

  /**
   * Manager for detecting and displaying clearance violations between board items.
   *
   * <p>Identifies and visualizes violations including:
   * <ul>
   *   <li>Trace-to-trace clearance violations</li>
   *   <li>Trace-to-via clearance violations</li>
   *   <li>Violations with component pads and keepout areas</li>
   * </ul>
   *
   * <p>Violations are displayed with visual indicators on the board.
   *
   * @see ClearanceViolations
   */
  public ClearanceViolations clearance_violations;

  /**
   * The currently active interactive state controlling user interaction behavior.
   *
   * <p>The state pattern is used to handle different interaction modes:
   * <ul>
   *   <li>RouteMenuState - Selection and menu operations</li>
   *   <li>RouteState - Interactive trace routing</li>
   *   <li>DragState - Moving items</li>
   *   <li>SelectState - Item selection operations</li>
   *   <li>And various other specialized states</li>
   * </ul>
   *
   * <p>Each state handles mouse events and keyboard input differently
   * based on the current operation mode.
   *
   * @see InteractiveState
   */
  InteractiveState interactive_state;

  /**
   * Flag to force immediate board panel repaint, bypassing the throttle mechanism.
   *
   * <p>Used when immediate visual feedback is required, such as:
   * <ul>
   *   <li>Reading and playing back logfiles</li>
   *   <li>Step-by-step operation execution</li>
   *   <li>User-requested manual refresh</li>
   * </ul>
   */
  boolean paint_immediately;
  /**
   * The current locale for internationalized UI text and messages.
   *
   * <p>Determines the language used for all user-facing text elements.
   */
  private Locale locale;

  /**
   * Number of threads to use for parallel routing operations.
   *
   * <p>Controls the thread pool size for autorouting and batch optimization tasks.
   */
  private int num_threads;

  /**
   * Strategy for updating the board during batch operations.
   *
   * <p>Determines how and when the board is updated during autorouting:
   * <ul>
   *   <li>Update frequency</li>
   *   <li>Commit timing</li>
   *   <li>Rollback behavior</li>
   * </ul>
   *
   * @see BoardUpdateStrategy
   */
  private BoardUpdateStrategy board_update_strategy;

  /**
   * The hybrid routing ratio configuration string.
   *
   * <p>Defines the balance between different routing algorithms when
   * using hybrid routing approaches.
   */
  private String hybrid_ratio;

  /**
   * Strategy for selecting which items to route during batch autorouting.
   *
   * <p>Controls the order and selection criteria for:
   * <ul>
   *   <li>Net prioritization</li>
   *   <li>Connection selection</li>
   *   <li>Routing sequence optimization</li>
   * </ul>
   *
   * @see ItemSelectionStrategy
   */
  private ItemSelectionStrategy item_selection_strategy;

  /**
   * Thread managing long-running interactive actions in the background.
   *
   * <p>Handles operations like:
   * <ul>
   *   <li>Batch autorouting</li>
   *   <li>Board optimization</li>
   *   <li>Fanout generation</li>
   * </ul>
   *
   * <p>Allows the UI to remain responsive during lengthy operations.
   *
   * @see InteractiveActionThread
   */
  private InteractiveActionThread interactive_action_thread;

  /**
   * Visual display manager for incomplete connections (air wires/rats nest).
   *
   * <p>Shows unrouted connections between pins as straight lines, helping
   * users understand routing requirements and progress. Recalculated after
   * board changes.
   *
   * @see RatsNest
   */
  private RatsNest ratsnest;

  /**
   * Flag indicating whether the board is in read-only mode.
   *
   * <p>Set to true when:
   * <ul>
   *   <li>Processing logfiles</li>
   *   <li>Running background autorouting</li>
   *   <li>Performing batch operations</li>
   * </ul>
   *
   * <p>Prevents interactive modifications that could interfere with
   * automated operations or corrupt the board state.
   */
  private boolean board_is_read_only;

  /**
   * The current position of the mouse cursor in board coordinates.
   *
   * <p>Updated continuously as the mouse moves, used for:
   * <ul>
   *   <li>Snap-to-grid calculations</li>
   *   <li>Interactive state processing</li>
   *   <li>Visual feedback rendering</li>
   * </ul>
   */
  private FloatPoint current_mouse_position;

  /**
   * Array of points to highlight on the board for trace event visualization.
   *
   * <p>When trace-level logging is active, these points indicate the
   * locations affected by the current routing operation, providing
   * visual debugging feedback.
   */
  private Point[] impactedPoints;

  /**
   * Creates a new GUI board manager for interactive routing operations.
   *
   * <p>Initializes all subsystems required for interactive board manipulation including:
   * <ul>
   *   <li>Graphics context and coordinate transformation</li>
   *   <li>Screen message display</li>
   *   <li>Event listeners for logging and tracing</li>
   *   <li>Initial interactive state (RouteMenuState)</li>
   *   <li>Text manager for internationalization</li>
   * </ul>
   *
   * <p>The constructor establishes connections between the board manager and the GUI panel,
   * sets up event handling, and prepares the system for user interaction.
   *
   * @param p_panel the board panel component for visual display
   * @param globalSettings application-wide configuration settings
   * @param routingJob the routing job containing board and design data
   * @param settingsMerger merger for consolidating settings from multiple sources
   *
   * @see HeadlessBoardManager#HeadlessBoardManager(RoutingJob)
   */
  public GuiBoardManager(BoardPanel p_panel, GlobalSettings globalSettings, RoutingJob routingJob, SettingsMerger settingsMerger) {
    super(routingJob);
    this.globalSettings = globalSettings;
    this.settingsMerger = settingsMerger;
    this.locale = globalSettings.currentLocale;
    this.panel = p_panel;
    this.screen_messages = p_panel.screen_messages;
    this.set_interactive_state(RouteMenuState.get_instance(this));

    this.tm = new TextManager(this.getClass(), globalSettings.currentLocale);

    this.logEntryAddedListener = this::logEntryAdded;
    FRLogger
        .getLogEntries()
        .addLogEntryAddedListener(this.logEntryAddedListener);

    this.traceEventListener = this::handleTraceEvent;
    FRLogger.addTraceEventListener(this.traceEventListener);
  }

  /**
   * Handles notification when a new log entry is added to the log system.
   *
   * <p>This listener updates the on-screen error and warning counts displayed
   * to the user. Only errors and warnings trigger UI updates to maintain
   * performance during verbose logging.
   *
   * @param logEntry the newly added log entry
   *
   * @see LogEntry
   * @see ScreenMessages#set_error_and_warning_count(int, int)
   */
  private void logEntryAdded(LogEntry logEntry) {
    if ((logEntry.getType() == LogEntryType.Error) || (logEntry.getType() == LogEntryType.Warning)) {
      LogEntries entries = FRLogger.getLogEntries();
      screen_messages.set_error_and_warning_count(entries.getErrorCount(), entries.getWarningCount());
    }
  }

  /**
   * Handles trace-level debugging events during routing operations.
   *
   * <p>When trace logging is enabled, this method:
   * <ul>
   *   <li>Displays trace messages on screen with operation details</li>
   *   <li>Highlights impacted items and points on the board</li>
   *   <li>Triggers board repaint to show visual feedback</li>
   * </ul>
   *
   * <p>Execution is deferred to the Event Dispatch Thread using SwingUtilities
   * to ensure thread-safe GUI updates.
   *
   * @param event the trace event containing operation details and impacted locations
   *
   * @see TraceEvent
   * @see ScreenMessages#set_trace_message(String, String, String)
   */
  private void handleTraceEvent(TraceEvent event) {
    if (event == null) {
      return;
    }
    SwingUtilities.invokeLater(() -> {
      screen_messages.set_trace_message(event.getOperation(), event.getMessage(), event.getImpactedItems());
      // Store the impacted points for drawing
      impactedPoints = event.getImpactedPoints();
      panel.repaint();
    });
  }

  /**
   * Gets the current routing job containing board data and routing configuration.
   *
   * <p>Interactive states use this to access job-specific router settings, design rules,
   * and board structure. The routing job encapsulates all design-specific information
   * needed for routing operations.
   *
   * @return the current routing job, or null if no job is loaded
   *
   * @see RoutingJob
   */
  @Override
  public RoutingJob getCurrentRoutingJob() {
    return this.routingJob;
  }

  /**
   * Returns whether the board is currently in read-only mode.
   *
   * <p>Read-only mode is enabled during operations that require exclusive board access:
   * <ul>
   *   <li>Logfile playback</li>
   *   <li>Background autorouting</li>
   *   <li>Batch optimization operations</li>
   * </ul>
   *
   * <p>When true, user interactions that modify the board are disabled.
   *
   * @return true if the board is read-only, false if modifications are allowed
   *
   * @see #set_board_read_only(boolean)
   */
  public boolean is_board_read_only() {
    return this.board_is_read_only;
  }

  /**
   * Sets the board's read-only state to prevent or allow user modifications.
   *
   * <p>This method:
   * <ul>
   *   <li>Updates the internal read-only flag</li>
   *   <li>Propagates the state to interactive settings</li>
   *   <li>Notifies all registered listeners of the state change</li>
   * </ul>
   *
   * <p>Listeners typically update UI elements (buttons, menus) to reflect
   * the board's modifiable state.
   *
   * @param p_value true to make board read-only, false to allow modifications
   *
   * @see #is_board_read_only()
   */
  public void set_board_read_only(boolean p_value) {
    this.board_is_read_only = p_value;
    this.interactiveSettings.set_read_only(p_value);

    // Raise an event to notify the observers that the board read only property
    // changed
    this.readOnlyEventListeners.forEach(listener -> listener.accept(p_value));
  }

  /**
   * Returns the current locale for UI text internationalization.
   *
   * <p>The locale determines the language used for all user-visible text
   * including menus, messages, and tooltips.
   *
   * @return the current locale setting
   *
   * @see Locale
   */
  public Locale get_locale() {
    return this.locale;
  }

  /**
   * Returns the number of layers in the board design.
   *
   * <p>Layer count includes all signal layers, power planes, and ground planes
   * defined in the board structure. Returns 0 if no board is loaded.
   *
   * @return the number of board layers, or 0 if board is null
   *
   * @see LayerStructure
   */
  public int get_layer_count() {
    if (board == null) {
      return 0;
    }
    return board.get_layer_count();
  }

  /**
   * Returns the current mouse cursor position in board coordinate space.
   *
   * <p>This position is:
   * <ul>
   *   <li>Updated continuously as the mouse moves</li>
   *   <li>Used by interactive states for operation placement</li>
   *   <li>Affected by snap-to-grid settings</li>
   *   <li>Transformed from screen coordinates through coordinate_transform</li>
   * </ul>
   *
   * @return the current mouse position in board coordinates
   *
   * @see FloatPoint
   * @see CoordinateTransform
   */
  public FloatPoint get_current_mouse_position() {
    return this.current_mouse_position;
  }

  /**
   * Sets whether conduction areas should be treated as obstacles during routing.
   *
   * <p>When conduction areas are ignored (p_value = true):
   * <ul>
   *   <li>Traces can route through conduction areas of foreign nets</li>
   *   <li>Useful for power planes and ground fills</li>
   *   <li>Reduces routing complexity in filled areas</li>
   * </ul>
   *
   * <p>When conduction areas are obstacles (p_value = false):
   * <ul>
   *   <li>Foreign net traces must route around them</li>
   *   <li>Provides stricter isolation between nets</li>
   * </ul>
   *
   * <p>This setting is ignored if the board is read-only.
   *
   * @param p_value true to ignore conduction areas, false to treat them as obstacles
   *
   * @see RoutingBoard#change_conduction_is_obstacle(boolean)
   */
  public void set_ignore_conduction(boolean p_value) {
    if (board_is_read_only) {
      return;
    }
    board.change_conduction_is_obstacle(!p_value);
  }

  /**
   * Sets the minimum distance from pin edges where traces can make their first turn.
   *
   * <p>This constraint controls trace exit geometry from pins:
   * <ul>
   *   <li>Ensures traces extend straight from pins before turning</li>
   *   <li>Improves manufacturing reliability near pads</li>
   *   <li>Prevents acute angles at pin connections</li>
   *   <li>Helps avoid solder mask and assembly issues</li>
   * </ul>
   *
   * <p>When this value changes, existing pin exit stubs that were shove-fixed
   * are released (set to NOT_FIXED) to allow re-optimization with the new constraint.
   * Only simple 2-corner exit traces are unfixed.
   *
   * <p>This setting is ignored if the board is read-only.
   *
   * @param p_value the minimum edge-to-turn distance in user coordinate units
   *
   * @see BoardRules#set_pin_edge_to_turn_dist(double)
   * @see Pin#has_trace_exit_restrictions()
   */
  public void set_pin_edge_to_turn_dist(double p_value) {
    if (board_is_read_only) {
      return;
    }
    double edge_to_turn_dist = this.coordinate_transform.user_to_board(p_value);
    if (edge_to_turn_dist != board.rules.get_pin_edge_to_turn_dist()) {
      // unfix the pin exit stubs
      Collection<Pin> pin_list = board.get_pins();
      for (Pin curr_pin : pin_list) {
        if (curr_pin.has_trace_exit_restrictions()) {
          Collection<Item> contact_list = curr_pin.get_normal_contacts();
          for (Item curr_contact : contact_list) {
            if ((curr_contact instanceof PolylineTrace trace)
                && curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED) {
              if (trace.corner_count() == 2) {
                curr_contact.set_fixed_state(FixedState.NOT_FIXED);
              }
            }
          }
        }
      }
    }
    board.rules.set_pin_edge_to_turn_dist(edge_to_turn_dist);
  }

  /**
   * Changes the visibility and transparency of a specific board layer.
   *
   * <p>Layer visibility controls how prominently items on that layer are displayed:
   * <ul>
   *   <li>Value of 1.0 - fully visible (opaque)</li>
   *   <li>Value between 0 and 1 - partially transparent</li>
   *   <li>Value of 0.0 - invisible (hidden)</li>
   * </ul>
   *
   * <p>If the currently active routing layer becomes invisible, the system
   * automatically switches to the most visible layer to maintain usability.
   *
   * @param p_layer the layer index to modify (0-based)
   * @param p_value the visibility value between 0.0 (invisible) and 1.0 (fully visible)
   *
   * @see GraphicsContext#set_layer_visibility(int, double)
   */
  public void set_layer_visibility(int p_layer, double p_value) {
    if (p_layer >= 0 && p_layer < graphics_context.layer_count()) {
      graphics_context.set_layer_visibility(p_layer, p_value);
      if (p_value == 0 && interactiveSettings.layer == p_layer) {
        // change the current layer to the best visible layer, if it becomes invisible;
        double best_visibility = 0;
        int best_visible_layer = 0;
        for (int i = 0; i < graphics_context.layer_count(); i++) {
          if (graphics_context.get_layer_visibility(i) > best_visibility) {
            best_visibility = graphics_context.get_layer_visibility(i);
            best_visible_layer = i;
          }
        }
        interactiveSettings.layer = best_visible_layer;
      }
    }
  }

  /**
   * Gets the trace half-width (radius) used in interactive routing for the specified net and layer.
   *
   * <p>The trace half-width determines the thickness of traces created during interactive routing.
   * The value returned depends on the routing mode:
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually configured trace width</li>
   *   <li><strong>Automatic rule selection:</strong> Returns the trace width from the net's class rules</li>
   * </ul>
   *
   * <p>Half-width is used because traces expand equally on both sides of their centerline.
   * The actual trace width is twice this value.
   *
   * @param p_net_no the net number to get the trace width for
   * @param p_layer the layer index where the trace will be placed
   * @return the trace half-width in board units
   *
   * @see InteractiveSettings#manual_rule_selection
   * @see BoardRules#get_trace_half_width(int, int)
   */
  public int get_trace_halfwidth(int p_net_no, int p_layer) {
    int result;
    if (interactiveSettings.manual_rule_selection) {
      result = interactiveSettings.manual_trace_half_width_arr[p_layer];
    } else {
      result = board.rules.get_trace_half_width(p_net_no, p_layer);
    }
    return result;
  }

  /**
   * Checks if the specified layer is active for interactive trace routing on the given net.
   *
   * <p>Layer activity determines whether traces can be routed on a particular layer for a net.
   * The behavior depends on the routing mode:
   * <ul>
   *   <li><strong>Manual rule selection:</strong> All layers are considered active</li>
   *   <li><strong>Automatic rule selection:</strong> Layer activity is determined by the net class configuration</li>
   * </ul>
   *
   * <p>Returns true if the net or net class is not found (permissive default).
   *
   * @param p_net_no the net number to check
   * @param p_layer the layer index to check
   * @return true if the layer is active for routing this net, false otherwise
   *
   * @see NetClass#is_active_routing_layer(int)
   */
  public boolean is_active_routing_layer(int p_net_no, int p_layer) {
    if (interactiveSettings.manual_rule_selection) {
      return true;
    }
    Net curr_net = this.board.rules.nets.get(p_net_no);
    if (curr_net == null) {
      return true;
    }
    NetClass curr_net_class = curr_net.get_class();
    if (curr_net_class == null) {
      return true;
    }
    return curr_net_class.is_active_routing_layer(p_layer);
  }

  /**
   * Gets the clearance class used in interactive routing for the specified net.
   *
   * <p>The clearance class determines minimum spacing requirements between traces and other
   * board objects. The value returned depends on the routing mode:
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually configured clearance class</li>
   *   <li><strong>Automatic rule selection:</strong> Returns the clearance class from the net's class rules</li>
   * </ul>
   *
   * <p>The clearance class is an index into the board's clearance matrix.
   *
   * @param p_net_no the net number to get the clearance class for
   * @return the clearance class index
   *
   * @see app.freerouting.rules.ClearanceMatrix
   * @see NetClass#get_trace_clearance_class()
   */
  public int get_trace_clearance_class(int p_net_no) {
    int result;
    if (interactiveSettings.manual_rule_selection) {
      result = interactiveSettings.manual_trace_clearance_class;
    } else {
      result = board.rules.nets
          .get(p_net_no)
          .get_class()
          .get_trace_clearance_class();
    }
    return result;
  }

  /**
   * Gets the via rule used in interactive routing for the specified net.
   *
   * <p>The via rule defines which via types (padstacks) are allowed and their priority order
   * for layer transitions. The value returned depends on the routing mode:
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually selected via rule if valid</li>
   *   <li><strong>Automatic rule selection:</strong> Returns the via rule from the net's class rules</li>
   * </ul>
   *
   * <p>If manual selection is active but the index is invalid, falls back to the net class rule.
   *
   * @param p_net_no the net number to get the via rule for
   * @return the via rule defining allowed via types and priorities
   *
   * @see ViaRule
   * @see NetClass#get_via_rule()
   */
  public ViaRule get_via_rule(int p_net_no) {
    ViaRule result = null;
    if (interactiveSettings.manual_rule_selection) {
      result = board.rules.via_rules.get(this.interactiveSettings.manual_via_rule_index);
    }
    if (result == null) {
      result = board.rules.nets
          .get(p_net_no)
          .get_class()
          .get_via_rule();
    }
    return result;
  }

  /**
   * Changes the default trace half-width currently used in interactive routing on the specified layer.
   *
   * <p>This sets the default trace width for nets that don't have specific width rules.
   * The change affects future routing operations on the layer.
   *
   * <p>This operation is ignored if:
   * <ul>
   *   <li>The board is in read-only mode</li>
   *   <li>The layer index is out of valid range</li>
   * </ul>
   *
   * @param p_layer the layer index to set the default width for
   * @param p_value the new default trace half-width in board units
   *
   * @see BoardRules#set_default_trace_half_width(int, int)
   */
  public void set_default_trace_halfwidth(int p_layer, int p_value) {
    if (board_is_read_only) {
      return;
    }
    if (p_layer >= 0 && p_layer <= board.get_layer_count()) {
      board.rules.set_default_trace_half_width(p_layer, p_value);
    }
  }

  /**
   * Switches clearance compensation on or off for the search tree.
   *
   * <p>Clearance compensation is a performance optimization technique where search tree
   * shapes are pre-expanded by their clearance requirements. This:
   * <ul>
   *   <li><strong>When enabled:</strong> Faster clearance checking but higher memory usage</li>
   *   <li><strong>When disabled:</strong> Lower memory usage but slower clearance checks</li>
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param p_value true to enable clearance compensation, false to disable
   *
   * @see SearchTreeManager#set_clearance_compensation_used(boolean)
   */
  public void set_clearance_compensation(boolean p_value) {
    if (board_is_read_only) {
      return;
    }
    board.search_tree_manager.set_clearance_compensation_used(p_value);
  }

  /**
   * Changes the current snap angle restriction for interactive routing.
   *
   * <p>The snap angle determines which trace angles are allowed during routing:
   * <ul>
   *   <li><strong>FORTYFIVE_DEGREE:</strong> Traces limited to 0°, 45°, 90°, 135°, etc.</li>
   *   <li><strong>NINETY_DEGREE:</strong> Traces limited to 0°, 90°, 180°, 270° (orthogonal only)</li>
   *   <li><strong>NONE:</strong> Any angle allowed (free-angle routing)</li>
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param p_snap_angle the angle restriction to apply
   *
   * @see AngleRestriction
   * @see BoardRules#set_trace_angle_restriction(AngleRestriction)
   */
  public void set_current_snap_angle(AngleRestriction p_snap_angle) {
    if (board_is_read_only) {
      return;
    }
    board.rules.set_trace_angle_restriction(p_snap_angle);
  }

  /**
   * Changes the current active layer for interactive routing.
   *
   * <p>The current layer is where new traces and vias will be created during interactive
   * routing operations. This method:
   * <ul>
   *   <li>Clamps the layer index to valid range [0, layer_count - 1]</li>
   *   <li>Updates the display to show the new active layer</li>
   *   <li>Updates UI components to reflect the layer change</li>
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param p_layer the layer index to make active (will be clamped to valid range)
   *
   * @see #set_layer(int)
   */
  public void set_current_layer(int p_layer) {
    if (board_is_read_only) {
      return;
    }
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, board.get_layer_count() - 1);
    set_layer(layer);
  }

  /**
   * Changes the current layer without saving the change to logfile.
   *
   * <p>This internal method performs the actual layer change operation:
   * <ul>
   *   <li>Updates the screen message display with the layer name</li>
   *   <li>Sets the layer in interactive settings</li>
   *   <li>Updates the layer selector in the UI panel (for signal layers)</li>
   *   <li>Makes the layer visible if it was hidden</li>
   *   <li>Sets the layer as fully visible in the graphics context</li>
   *   <li>Triggers a board repaint</li>
   * </ul>
   *
   * <p><strong>Note:</strong> This is for internal use. External code should use
   * {@link #set_current_layer(int)} which provides validation and logging.
   *
   * @param p_layer_no the layer index to switch to (assumed to be valid)
   *
   * @see #set_current_layer(int)
   */
  void set_layer(int p_layer_no) {
    Layer curr_layer = board.layer_structure.arr[p_layer_no];
    screen_messages.set_layer(curr_layer.name);
    interactiveSettings.layer = p_layer_no;

    // Change the selected layer in the select parameter window.
    if ((!this.board_is_read_only) && (curr_layer.is_signal)) {
      this.panel.set_selected_signal_layer(p_layer_no);
    }

    // make the layer visible, if it is invisible
    if (graphics_context.get_layer_visibility(p_layer_no) == 0) {
      graphics_context.set_layer_visibility(p_layer_no, 1);
      panel.board_frame.refresh_windows();
    }
    graphics_context.set_fully_visible_layer(p_layer_no);
    repaint();
  }

  /**
   * Updates the layer message display to show the current active layer name.
   *
   * <p>This method:
   * <ul>
   *   <li>Clears the additional message field</li>
   *   <li>Displays the current layer name in the layer message field</li>
   * </ul>
   *
   * <p>Useful for refreshing the display after layer-related operations.
   *
   * @see ScreenMessages#set_layer(String)
   */
  public void display_layer_message() {
    screen_messages.clear_add_field();
    Layer curr_layer = board.layer_structure.arr[this.interactiveSettings.layer];
    screen_messages.set_layer(curr_layer.name);
  }

  /**
   * Sets the manual trace half-width used in interactive routing for specified layers.
   *
   * <p>This method supports setting trace width for:
   * <ul>
   *   <li><strong>All layers:</strong> When p_layer_no == {@link ComboBoxLayer#ALL_LAYER_INDEX}</li>
   *   <li><strong>Inner layers only:</strong> When p_layer_no == {@link ComboBoxLayer#INNER_LAYER_INDEX}</li>
   *   <li><strong>Single layer:</strong> When p_layer_no is a specific layer index</li>
   * </ul>
   *
   * <p>The manual trace width is only used when manual rule selection is active.
   *
   * @param p_layer_no the layer index, or special index for all/inner layers
   * @param p_value the trace half-width to set in board units
   *
   * @see InteractiveSettings#set_manual_trace_half_width(int, int)
   * @see ComboBoxLayer
   */
  public void set_manual_trace_half_width(int p_layer_no, int p_value) {
    if (p_layer_no == ComboBoxLayer.ALL_LAYER_INDEX) {
      for (int i = 0; i < interactiveSettings.manual_trace_half_width_arr.length; i++) {
        this.interactiveSettings.set_manual_trace_half_width(i, p_value);
      }
    } else if (p_layer_no == ComboBoxLayer.INNER_LAYER_INDEX) {
      for (int i = 1; i < interactiveSettings.manual_trace_half_width_arr.length - 1; i++) {
        this.interactiveSettings.set_manual_trace_half_width(i, p_value);
      }
    } else {
      this.interactiveSettings.set_manual_trace_half_width(p_layer_no, p_value);
    }
  }

  /**
   * Changes the interactive selectability of a specific item type.
   *
   * <p>When an item type is set to non-selectable:
   * <ul>
   *   <li>It cannot be picked or selected during interactive operations</li>
   *   <li>If currently selected items become non-selectable, they are filtered out</li>
   * </ul>
   *
   * <p>This is useful for focusing on specific types of objects during editing.
   *
   * @param p_item_type the item type to make selectable or non-selectable
   * @param p_value true to make the item type selectable, false to disable selection
   *
   * @see ItemSelectionFilter.SelectableChoices
   * @see InteractiveSettings#set_selectable(ItemSelectionFilter.SelectableChoices, boolean)
   */
  public void set_selectable(ItemSelectionFilter.SelectableChoices p_item_type, boolean p_value) {
    interactiveSettings.set_selectable(p_item_type, p_value);
    if (!p_value && this.interactive_state instanceof InspectedItemState) {
      set_interactive_state(((InspectedItemState) interactive_state).filter());
    }
  }

  /**
   * Toggles the display of incomplete connections (rats nest) on the board.
   *
   * <p>The rats nest shows unrouted connections as straight lines between pins:
   * <ul>
   *   <li><strong>If hidden or null:</strong> Creates and displays the rats nest</li>
   *   <li><strong>If visible:</strong> Hides the rats nest</li>
   * </ul>
   *
   * <p>Triggers a board repaint to update the display.
   *
   * @see RatsNest
   * @see #create_ratsnest()
   */
  public void toggle_ratsnest() {
    if (ratsnest == null || ratsnest.is_hidden()) {
      create_ratsnest();
    } else {
      ratsnest = null;
    }
    repaint();
  }

  /**
   * Toggles the display of clearance violations on the board.
   *
   * <p>Clearance violations indicate locations where items are too close together:
   * <ul>
   *   <li><strong>If not displayed:</strong> Calculates and displays all violations with count message</li>
   *   <li><strong>If displayed:</strong> Hides the violations and clears the status message</li>
   * </ul>
   *
   * <p>Triggers a board repaint to update the display.
   *
   * @see ClearanceViolations
   */
  public void toggle_clearance_violations() {
    if (clearance_violations == null) {
      clearance_violations = new ClearanceViolations(this.board.get_items());
      Integer violation_count = (clearance_violations.list.size() + 1) / 2;
      String curr_message = violation_count + " " + tm.getText("clearance_violations_found");
      screen_messages.set_status_message(curr_message);
    } else {
      clearance_violations = null;
      screen_messages.set_status_message("");
    }
    repaint();
  }

  /**
   * Creates and displays the rats nest showing all incomplete connections.
   *
   * <p>This method:
   * <ul>
   *   <li>Creates a new RatsNest object analyzing all incomplete connections</li>
   *   <li>Counts incomplete connections and length violations</li>
   *   <li>Displays a status message with connection statistics</li>
   * </ul>
   *
   * <p>The rats nest shows unrouted connections as straight lines (air wires) between pins.
   *
   * @see RatsNest
   * @see #toggle_ratsnest()
   */
  public void create_ratsnest() {
    ratsnest = new RatsNest(this.board);
    Integer incomplete_count = ratsnest.incomplete_count();
    int length_violation_count = ratsnest.length_violation_count();
    String curr_message;
    if (length_violation_count == 0) {
      curr_message = incomplete_count + " " + tm.getText("incomplete_connections_to_route");
    } else {
      curr_message = incomplete_count + " " + tm.getText("incompletes") + " " + length_violation_count + " "
          + tm.getText("length_violations");
    }
    screen_messages.set_status_message(curr_message);
  }

  /**
   * Recalculates and updates the incomplete connections for the specified net.
   *
   * <p>If the rats nest is currently displayed, this method recalculates the air wires
   * for the given net and updates the display. Ignored if rats nest is not active or
   * net number is invalid.
   *
   * @param p_net_no the net number to recalculate connections for (must be > 0)
   *
   * @see RatsNest#recalculate(int, BasicBoard)
   */
  void update_ratsnest(int p_net_no) {
    if (ratsnest != null && p_net_no > 0) {
      ratsnest.recalculate(p_net_no, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates incomplete connections for the specified net, considering only the given items.
   *
   * <p>This optimized version recalculates connections only for items in the provided collection,
   * which is more efficient when only a subset of items has changed.
   *
   * @param p_net_no the net number to recalculate connections for (must be > 0)
   * @param p_item_list the collection of items to consider in the recalculation
   *
   * @see RatsNest#recalculate(int, Collection, BasicBoard)
   */
  void update_ratsnest(int p_net_no, Collection<Item> p_item_list) {
    if (ratsnest != null && p_net_no > 0) {
      ratsnest.recalculate(p_net_no, p_item_list, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates all incomplete connections if the rats nest is currently active.
   *
   * <p>This full recalculation creates a new rats nest from scratch, analyzing all
   * nets and items on the board. Used when significant board changes have occurred
   * that affect multiple nets.
   *
   * @see RatsNest#RatsNest(BasicBoard)
   */
  void update_ratsnest() {
    if (ratsnest != null) {
      ratsnest = new RatsNest(this.board);
    }
  }

  /**
   * Hides the rats nest display without destroying the data structure.
   *
   * <p>The rats nest object is retained in memory but not rendered. This allows
   * quick re-display without recalculation. Use {@link #toggle_ratsnest()} to
   * show it again.
   *
   * @see RatsNest#hide()
   * @see #toggle_ratsnest()
   */
  public void hide_ratsnest() {
    if (ratsnest != null) {
      ratsnest.hide();
    }
  }

  /**
   * Shows the rats nest display if it is currently active.
   *
   * <p>Makes the rats nest visible on the board, displaying all incomplete connections
   * as air wires. The rats nest object must already exist.
   *
   * @see RatsNest#show()
   * @see #hide_ratsnest()
   */
  public void show_ratsnest() {
    if (ratsnest != null) {
      ratsnest.show();
    }
  }

  /**
   * Removes the rats nest object, deallocating its data structure.
   *
   * <p>This fully destroys the rats nest. Creating it again will require
   * recalculation from scratch. Use {@link #hide_ratsnest()} if you want
   * to preserve the data for quick re-display.
   *
   * @see #get_ratsnest()
   * @see #hide_ratsnest()
   */
  public void remove_ratsnest() {
    // TODO: test these two versions combined with get_ratsnest() method

    // Version A
    ratsnest = null;

    // Version B
    // do nothing as we create a new instance of ratsnest every time
  }

  /**
   * Returns the rats nest object containing incomplete connection information.
   *
   * <p>If the rats nest doesn't exist, creates a new one by analyzing the board.
   * The rats nest contains:
   * <ul>
   *   <li>All incomplete (unrouted) connections</li>
   *   <li>Connection length information</li>
   *   <li>Length violation data</li>
   * </ul>
   *
   * @return the rats nest object with connection analysis
   *
   * @see RatsNest
   * @see #remove_ratsnest()
   */
  public RatsNest get_ratsnest() {
    // TODO: test these two versions combined with remove_ratsnest() method

    // Version A
    if (ratsnest == null) {
      ratsnest = new RatsNest(this.board);
    }
    return this.ratsnest;

    // Version B
    // return new RatsNest(this.board);
  }

  /**
   * Recalculates length violations for all nets in the rats nest.
   *
   * <p>Checks all incomplete connections against maximum length constraints and
   * updates violation status. If violations changed and the rats nest is visible,
   * triggers a board repaint to update the display.
   *
   * @see RatsNest#recalculate_length_violations()
   */
  public void recalculate_length_violations() {
    if (this.ratsnest != null) {
      if (this.ratsnest.recalculate_length_violations()) {
        if (!this.ratsnest.is_hidden()) {
          this.repaint();
        }
      }
    }
  }

  /**
   * Sets the visibility filter for incomplete connections of the specified net.
   *
   * <p>Controls whether the incomplete connections (air wires) for a specific net
   * are displayed in the rats nest:
   * <ul>
   *   <li><strong>true:</strong> Show incompletes for this net</li>
   *   <li><strong>false:</strong> Hide incompletes for this net</li>
   * </ul>
   *
   * <p>Useful for focusing on specific nets while hiding others for clarity.
   *
   * @param p_net_no the net number to filter
   * @param p_value true to show incompletes, false to hide them
   *
   * @see RatsNest#set_filter(int, boolean)
   */
  public void set_incompletes_filter(int p_net_no, boolean p_value) {
    if (ratsnest != null) {
      ratsnest.set_filter(p_net_no, p_value);
    }
  }

  /**
   * Creates the routing board with GUI-specific initialization.
   *
   * <p>This method extends the base board creation with GUI components:
   * <ol>
   *   <li>Calls super to create the basic routing board structure</li>
   *   <li>Initializes the coordinate transform for unit conversions</li>
   *   <li>Creates the graphics context for visual rendering</li>
   * </ol>
   *
   * <p>The coordinate transform handles conversions between:
   * <ul>
   *   <li>User units (mm, mil, inch) for display</li>
   *   <li>Board internal units for calculations</li>
   *   <li>DSN file units for import/export</li>
   * </ul>
   *
   * @param p_bounding_box the rectangular boundary of the board
   * @param p_layer_structure the layer stack-up definition
   * @param p_outline_shapes array of shapes defining the board outline
   * @param p_outline_clearance_class_name clearance class name for the outline
   * @param p_rules the board design rules and constraints
   * @param p_board_communication communication interface for external integration
   *
   * @see HeadlessBoardManager#create_board
   * @see GraphicsContext
   * @see CoordinateTransform
   */
  @Override
  public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
      String p_outline_clearance_class_name, BoardRules p_rules,
      Communication p_board_communication) {
    super.create_board(p_bounding_box, p_layer_structure, p_outline_shapes, p_outline_clearance_class_name, p_rules,
        p_board_communication);

    // Apply CLI settings from GlobalSettings to the board's autoroute_settings
    // This ensures that command-line arguments are respected in GUI mode
    // NOTE: This is critical because
    // InteractiveActionThread.get_autorouter_and_route_optimizer_instance()
    // clones boardManager.settings.autoroute_settings to job.routerSettings, so we
    // need to ensure
    // the board's settings have the CLI arguments applied
    if (globalSettings != null && globalSettings.routerSettings != null) {
      FRLogger.info("[CLI Settings] Applying CLI settings to board's autoroute_settings...");
      this.settings.autoroute_settings.applyNewValuesFrom(globalSettings.routerSettings);
    }

    // create the interactive/GUI settings with default values
    double unit_factor = p_board_communication.coordinate_transform.board_to_dsn(1);
    this.coordinate_transform = new CoordinateTransform(1, p_board_communication.unit, unit_factor,
        p_board_communication.unit);

    // create a graphics context for the board
    Dimension panel_size = panel.getPreferredSize();
    graphics_context = new GraphicsContext(p_bounding_box, panel_size, p_layer_structure, this.locale);
  }

  /**
   * Changes the user unit for coordinate display and input.
   *
   * <p>Updates the unit used throughout the GUI for:
   * <ul>
   *   <li>Coordinate display in status messages</li>
   *   <li>Dimension entry in dialogs</li>
   *   <li>Measurement displays</li>
   * </ul>
   *
   * <p>The coordinate transform is recreated to maintain correct scaling factors
   * between user units and board internal units.
   *
   * @param p_unit the new unit for user display (mm, mil, inch, etc.)
   *
   * @see Unit
   * @see CoordinateTransform
   */
  public void change_user_unit(Unit p_unit) {
    screen_messages.set_unit_label(p_unit.toString());
    CoordinateTransform old_transform = this.coordinate_transform;
    this.coordinate_transform = new CoordinateTransform(old_transform.user_unit_factor, p_unit,
        old_transform.board_unit_factor, old_transform.board_unit);
  }

  /**
   * Requests a repaint of the entire board panel.
   *
   * <p>Repaint behavior depends on the paint_immediately flag:
   * <ul>
   *   <li><strong>Immediate mode (paint_immediately=true):</strong> Forces synchronous repaint
   *       (used during logfile playback)</li>
   *   <li><strong>Throttled mode (paint_immediately=false):</strong> Respects minimum interval
   *       between repaints to maintain reasonable frame rate</li>
   * </ul>
   *
   * <p>Throttling prevents excessive repainting during rapid board changes, improving
   * performance during autorouting and batch operations.
   *
   * @see #repaint_interval
   * @see #paint_immediately
   */
  public void repaint() {
    if (this.paint_immediately) {
      final Rectangle MAX_RECTAMLE = new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
      panel.paintImmediately(MAX_RECTAMLE);
    } else {
      if (last_repainted_time < System.currentTimeMillis() - repaint_interval) {
        last_repainted_time = System.currentTimeMillis();

        panel.repaint();
      }
    }
  }

  /**
   * Requests a repaint of a specific rectangular region of the board panel.
   *
   * <p>Partial repaint is more efficient than full repaint when only a small area
   * has changed. Behavior depends on paint_immediately:
   * <ul>
   *   <li><strong>Immediate mode:</strong> Synchronous repaint of the rectangle</li>
   *   <li><strong>Normal mode:</strong> Asynchronous repaint request</li>
   * </ul>
   *
   * @param p_rect the rectangular region to repaint in screen coordinates
   *
   * @see #repaint()
   */
  public void repaint(Rectangle p_rect) {
    if (this.paint_immediately) {
      panel.paintImmediately(p_rect);
    } else {
      panel.repaint(p_rect);
    }
  }

  /**
   * Returns the board panel component used for graphical display.
   *
   * <p>The panel provides the visual interface for board rendering, user interaction,
   * and screen message display.
   *
   * @return the BoardPanel instance managing board visualization
   *
   * @see BoardPanel
   */
  public BoardPanel get_panel() {
    return this.panel;
  }

  /**
   * Returns the popup menu for the current interactive state, if applicable.
   *
   * <p>Different interactive states may provide different popup menus with
   * context-specific actions. Some states do not use popup menus at all.
   *
   * @return the popup menu for the current state, or null if no menu is available
   *
   * @see InteractiveState#get_popup_menu()
   */
  public JPopupMenu get_current_popup_menu() {
    JPopupMenu result;
    if (interactive_state != null) {
      result = interactive_state.get_popup_menu();
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Renders the complete board display including all visual elements.
   *
   * <p>This method draws all board elements in layers:
   * <ol>
   *   <li>The routing board (traces, vias, pads, etc.)</li>
   *   <li>The rats nest (incomplete connections) if visible</li>
   *   <li>Clearance violations if visible</li>
   *   <li>Interactive state graphics (rubber-band lines, temporary items)</li>
   *   <li>Interactive action thread graphics (autoroute progress)</li>
   *   <li>Trace event indicators (debugging visualization)</li>
   * </ol>
   *
   * <p>Called automatically by the panel during repaint operations.
   *
   * @param p_graphics the Graphics context for rendering
   *
   * @see RoutingBoard#draw(Graphics, GraphicsContext)
   * @see InteractiveState#draw(Graphics)
   */
  public void draw(Graphics p_graphics) {
    if (board == null) {
      return;
    }
    board.draw(p_graphics, graphics_context);

    if (ratsnest != null) {
      ratsnest.draw(p_graphics, graphics_context);
    }
    if (clearance_violations != null) {
      clearance_violations.draw(p_graphics, graphics_context);
    }
    if (interactive_state != null) {
      interactive_state.draw(p_graphics);
    }
    if (interactive_action_thread != null) {
      interactive_action_thread.draw(p_graphics);
    }

    // Draw indicators for impacted points from trace events
    if (impactedPoints != null && impactedPoints.length > 0) {
      drawImpactedPointsIndicators(p_graphics);
    }
  }

  /**
   * Draws visual indicators (crosshairs and circles) at impacted points for trace debugging.
   *
   * <p>When trace-level logging is active, this method visualizes the locations affected
   * by routing operations. Each impacted point is marked with:
   * <ul>
   *   <li>An X-shaped crosshair (two diagonal lines)</li>
   *   <li>A circle around the point</li>
   * </ul>
   *
   * <p>The indicator size is based on the default trace width, with a minimum size
   * for visibility. This provides visual feedback for debugging routing algorithms.
   *
   * @param p_graphics the Graphics context for rendering
   *
   * @see #handleTraceEvent(TraceEvent)
   */
  private void drawImpactedPointsIndicators(Graphics p_graphics) {
    Color draw_color = graphics_context.get_hilight_color();
    double draw_intensity = graphics_context.get_hilight_color_intensity();
    int default_trace_half_width = board.rules.get_default_trace_half_width(0);
    double radius = Math.max(5 * default_trace_half_width / 10, 500); // Minimum radius of 500
    final double draw_width = 50.0;

    for (Point point : impactedPoints) {
      if (point != null) {
        FloatPoint center = point.to_float();

        // Draw an X marker (crosshair)
        FloatPoint[] draw_points = new FloatPoint[2];

        // Draw first diagonal line (top-left to bottom-right)
        draw_points[0] = new FloatPoint(center.x - radius, center.y - radius);
        draw_points[1] = new FloatPoint(center.x + radius, center.y + radius);
        graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);

        // Draw second diagonal line (top-right to bottom-left)
        draw_points[0] = new FloatPoint(center.x + radius, center.y - radius);
        draw_points[1] = new FloatPoint(center.x - radius, center.y + radius);
        graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);

        // Draw a circle around the point
        graphics_context.draw_circle(center, radius, draw_width, draw_color, p_graphics, draw_intensity);
      }
    }
  }

  /**
   * Creates a snapshot of the current board state for undo functionality.
   *
   * <p>Snapshots should be created before operations that users may want to reverse.
   * This operation is ignored if the board is in read-only mode.
   *
   * @see RoutingBoard#generate_snapshot()
   * @see #undo()
   */
  public void generate_snapshot() {
    if (board_is_read_only) {
      return;
    }
    board.generate_snapshot();
  }

  /**
   * Restores the board to the state of the previous snapshot (undo operation).
   *
   * <p>This method:
   * <ul>
   *   <li>Reverts board changes to the last snapshot</li>
   *   <li>Updates the rats nest for all affected nets</li>
   *   <li>Displays a status message indicating success or failure</li>
   *   <li>Triggers a board repaint</li>
   * </ul>
   *
   * <p>The operation is ignored if:
   * <ul>
   *   <li>The board is in read-only mode</li>
   *   <li>The current state is not a MenuState (to prevent undo during active operations)</li>
   * </ul>
   *
   * @see #redo()
   * @see #generate_snapshot()
   * @see RoutingBoard#undo(Set)
   */
  public void undo() {
    if (board_is_read_only || !(interactive_state instanceof MenuState)) {
      return;
    }
    Set<Integer> changed_nets = new TreeSet<>();
    if (board.undo(changed_nets)) {
      for (Integer changed_net : changed_nets) {
        this.update_ratsnest(changed_net);
      }
      if (!changed_nets.isEmpty()) {
        // reset the start pass number in the autorouter in case
        // a batch autorouter is undone.
        // Pass tracking is now handled locally in the router algorithms
      }
      screen_messages.set_status_message(tm.getText("undo"));
    } else {
      screen_messages.set_status_message(tm.getText("no_more_undo_possible"));
    }
    repaint();
  }

  /**
   * Restores the board to the state before the last undo operation (redo operation).
   *
   * <p>This method re-applies changes that were undone, moving forward in the undo/redo
   * history. The process:
   * <ul>
   *   <li>Restores board changes that were undone</li>
   *   <li>Updates the rats nest for all affected nets</li>
   *   <li>Displays a status message indicating success or failure</li>
   *   <li>Triggers a board repaint</li>
   * </ul>
   *
   * <p>The operation is ignored if:
   * <ul>
   *   <li>The board is in read-only mode</li>
   *   <li>The current state is not a MenuState</li>
   * </ul>
   *
   * @see #undo()
   * @see RoutingBoard#redo(Set)
   */
  public void redo() {
    if (board_is_read_only || !(interactive_state instanceof MenuState)) {
      return;
    }
    Set<Integer> changed_nets = new TreeSet<>();
    if (board.redo(changed_nets)) {
      for (Integer changed_net : changed_nets) {
        this.update_ratsnest(changed_net);
      }
      screen_messages.set_status_message(tm.getText("redo"));
    } else {
      screen_messages.set_status_message(tm.getText("no_more_redo_possible"));
    }
    repaint();
  }

  /**
   * Handles left mouse button click events.
   *
   * <p>Behavior depends on board state:
   * <ul>
   *   <li><strong>Read-only mode:</strong> Stops any running autorouter or optimizer</li>
   *   <li><strong>Interactive mode:</strong> Delegates to the current interactive state for
   *       state-specific handling (e.g., starting routes, selecting items, placing vias)</li>
   * </ul>
   *
   * <p>The screen point is converted to board coordinates before being passed to the
   * interactive state.
   *
   * @param p_point the mouse click location in screen coordinates
   *
   * @see InteractiveState#left_button_clicked(FloatPoint)
   * @see #stop_autorouter_and_route_optimizer()
   */
  public void left_button_clicked(Point2D p_point) {
    if (board_is_read_only) {
      // We are currently busy working on the board and the user clicked on the canvas
      // with the left mouse button.
      this.stop_autorouter_and_route_optimizer();
      return;
    }
    if (interactive_state != null && graphics_context != null) {
      FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.left_button_clicked(location);
      if (return_state != interactive_state && return_state != null) {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Handles mouse movement events, updating cursor position and providing hover information.
   *
   * <p>This method performs several tasks:
   * <ul>
   *   <li>Updates the current mouse position (in both read-only and interactive modes)</li>
   *   <li>Displays mouse coordinates in the status area</li>
   *   <li>In interactive mode: delegates movement handling to the current state</li>
   *   <li>Detects items under the cursor and displays tooltips with item information</li>
   *   <li>Updates the display if the state changes</li>
   * </ul>
   *
   * <p><strong>Note:</strong> Automatic repaint is avoided here to maintain performance
   * during interactive routing. States that need repainting should handle it explicitly.
   *
   * @param p_point the mouse position in screen coordinates
   *
   * @see InteractiveState#mouse_moved()
   * @see #pick_items(FloatPoint)
   */
  public void mouse_moved(Point2D p_point) {
    if (interactive_state != null && graphics_context != null) {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);

      // Always update the mouse position display, even when board is read-only
      FloatPoint mouse_position = coordinate_transform.board_to_user(this.current_mouse_position);
      screen_messages.set_mouse_position(mouse_position);

      if (board_is_read_only) {
        // no interactive action when logfile is running, but mouse position is still updated
        return;
      }

      InteractiveState return_state = interactive_state.mouse_moved();
      Set<Item> hover_item = pick_items(this.current_mouse_position);
      if (hover_item.size() == 1) {
        String hover_info = hover_item
            .iterator()
            .next()
            .get_hover_info(locale);
        this.panel.setToolTipText(hover_info);
      } else {
        this.panel.setToolTipText(null);
      }
      // An automatic repaint here would slow down the display
      // performance in interactive route.
      // If a repaint is necessary, it should be done in the individual mouse_moved
      // method of the class derived from InteractiveState
      if (return_state != this.interactive_state) {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Handles mouse button press events.
   *
   * <p>Updates the current mouse position and delegates the event to the interactive
   * state for state-specific handling (e.g., initiating drag operations, starting
   * selection rectangles).
   *
   * @param p_point the mouse position in screen coordinates where the button was pressed
   *
   * @see InteractiveState#mouse_pressed(FloatPoint)
   */
  public void mouse_pressed(Point2D p_point) {
    if (interactive_state != null && graphics_context != null) {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      set_interactive_state(interactive_state.mouse_pressed(this.current_mouse_position));
    }
  }

  /**
   * Handles mouse drag events (mouse moved while button pressed).
   *
   * <p>Updates the current mouse position and delegates to the interactive state
   * for state-specific drag handling (e.g., dragging items, drawing selection
   * rectangles, extending routes).
   *
   * <p>If the state changes during the drag, triggers a repaint.
   *
   * @param p_point the current mouse position in screen coordinates during the drag
   *
   * @see InteractiveState#mouse_dragged(FloatPoint)
   */
  public void mouse_dragged(Point2D p_point) {
    if (interactive_state != null && graphics_context != null) {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.mouse_dragged(this.current_mouse_position);
      if (return_state != interactive_state) {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Handles mouse button release events.
   *
   * <p>Delegates to the interactive state to complete operations initiated by button
   * press or drag (e.g., completing item moves, finalizing selection rectangles,
   * finishing drag operations).
   *
   * <p>If the state changes upon button release, triggers a repaint.
   *
   * @see InteractiveState#button_released()
   */
  public void button_released() {
    if (interactive_state != null) {
      InteractiveState return_state = interactive_state.button_released();
      if (return_state != interactive_state) {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Handles mouse wheel movement events for zoom and other scroll operations.
   *
   * <p>Updates the current mouse position and delegates to the interactive state.
   * Typically used for:
   * <ul>
   *   <li>Zooming in/out centered on the mouse position</li>
   *   <li>Layer switching</li>
   *   <li>State-specific scroll behaviors</li>
   * </ul>
   *
   * @param p_point the mouse position in screen coordinates during wheel movement
   * @param p_rotation the wheel rotation amount (positive for up/away, negative for down/toward)
   *
   * @see InteractiveState#mouse_wheel_moved(int)
   */
  public void mouse_wheel_moved(Point2D p_point, int p_rotation) {
    if (interactive_state != null && graphics_context != null) {
      this.current_mouse_position = graphics_context.coordinate_transform.screen_to_board(p_point);
      InteractiveState return_state = interactive_state.mouse_wheel_moved(p_rotation);
      if (return_state != interactive_state) {
        set_interactive_state(return_state);
        repaint();
      }
    }
  }

  /**
   * Handles keyboard input events for interactive commands.
   *
   * <p>Delegates to the current interactive state to handle keyboard shortcuts and
   * commands (e.g., 'ESC' to cancel, numeric keys for layer selection, letter keys
   * for tool switching).
   *
   * <p>If the state changes, updates the toolbar to reflect the new state.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_key_char the character typed on the keyboard
   *
   * @see InteractiveState#key_typed(char)
   */
  public void key_typed_action(char p_key_char) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    InteractiveState return_state = interactive_state.key_typed(p_key_char);
    if (return_state != null && return_state != interactive_state) {
      set_interactive_state(return_state);
      panel.board_frame.setToolbarModeSelectionPanelValue(get_interactive_state());
      repaint();
    }
  }

  /**
   * Completes the current interactive state and returns to its parent/return state.
   *
   * <p>This typically finalizes the current operation and returns to a more general
   * state (e.g., completing a route returns to route menu state).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @see InteractiveState#complete()
   * @see #cancel_state()
   */
  public void return_from_state() {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState new_state = interactive_state.complete();
    {
      if (new_state != interactive_state) {
        set_interactive_state(new_state);
        repaint();
      }
    }
  }

  /**
   * Cancels the current interactive state, discarding any uncommitted changes.
   *
   * <p>Returns to the parent state without completing or saving the current operation
   * (e.g., canceling a route in progress removes any temporary routing without creating
   * traces).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @see InteractiveState#cancel()
   * @see #return_from_state()
   */
  public void cancel_state() {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState new_state = interactive_state.cancel();
    {
      if (new_state != interactive_state) {
        set_interactive_state(new_state);
        repaint();
      }
    }
  }

  /**
   * Requests a layer change in the current interactive state.
   *
   * <p>Delegates the layer change request to the interactive state, which may accept
   * or reject it based on the current operation (e.g., mid-route layer changes are
   * allowed via vias, but other states may reject layer changes).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_new_layer the target layer index to change to
   * @return true if the layer change was successful, false if it failed
   *
   * @see InteractiveState#change_layer_action(int)
   */
  public boolean change_layer_action(int p_new_layer) {
    boolean result = true;
    if (interactive_state != null && !board_is_read_only) {
      result = interactive_state.change_layer_action(p_new_layer);
    }
    return result;
  }

  /**
   * Sets the interactive state to InspectMenuState for item selection and inspection.
   *
   * <p>InspectMenuState allows users to select and examine board items, view their
   * properties, and perform operations on selected items.
   *
   * @see InspectMenuState
   * @see #set_route_menu_state()
   */
  public void set_inspect_menu_state() {
    this.interactive_state = InspectMenuState.get_instance(this);
    screen_messages.set_status_message(tm.getText("select_menu"));
  }

  /**
   * Sets the interactive state to RouteMenuState for routing operations.
   *
   * <p>RouteMenuState is the default state, allowing users to start routing,
   * select items, and access routing-related operations.
   *
   * @see RouteMenuState
   * @see #set_inspect_menu_state()
   */
  public void set_route_menu_state() {
    this.interactive_state = RouteMenuState.get_instance(this);
    screen_messages.set_status_message(tm.getText("route_menu"));
  }

  /**
   * Sets the interactive state to DragMenuState for moving items.
   *
   * <p>DragMenuState allows users to select and drag board items to new positions.
   *
   * @see DragMenuState
   */
  public void set_drag_menu_state() {
    this.interactive_state = DragMenuState.get_instance(this);
    screen_messages.set_status_message(tm.getText("drag_menu"));
  }

  /**
   * Checks if the board has been modified since it was last saved or loaded.
   *
   * <p>Uses CRC32 checksum comparison to detect changes. This allows prompting
   * the user before closing or loading a new design if unsaved changes exist.
   *
   * @return true if the board has unsaved changes, false otherwise
   *
   * @see #calculateCrc32()
   */
  public boolean isBoardChanged() {
    return calculateCrc32() != originalBoardChecksum;
  }

  /**
   * Loads an existing board design from a binary input stream.
   *
   * <p>Deserializes the board and all associated data structures:
   * <ul>
   *   <li>The routing board with all items</li>
   *   <li>Interactive settings</li>
   *   <li>Coordinate transform</li>
   *   <li>Graphics context</li>
   * </ul>
   *
   * <p>After successful loading, updates the layer display and stores a checksum
   * for change detection.
   *
   * @param p_design the input stream containing serialized board data
   * @return true if loading succeeded, false if an error occurred
   *
   * @see #saveAsBinary(ObjectOutputStream)
   */
  public boolean loadFromBinary(ObjectInputStream p_design) {
    try {
      board = (RoutingBoard) p_design.readObject();
      interactiveSettings = (InteractiveSettings) p_design.readObject();
      coordinate_transform = (CoordinateTransform) p_design.readObject();
      graphics_context = (GraphicsContext) p_design.readObject();
      originalBoardChecksum = calculateCrc32();
    } catch (Exception e) {
      routingJob.logError("Couldn't read design file", e);
      return false;
    }
    screen_messages.set_layer(board.layer_structure.arr[interactiveSettings.layer].name);
    return true;
  }

  /**
   * Writes the currently edited board design to a Specctra DSN format file.
   *
   * <p>The DSN (Design) format is an industry-standard PCB interchange format that
   * can be read by various PCB tools. The compatibility mode parameter controls
   * the scope of information written:
   * <ul>
   *   <li><strong>Compatibility mode (true):</strong> Writes only standard DSN scopes for
   *       maximum compatibility with other tools</li>
   *   <li><strong>Full mode (false):</strong> Writes Freerouting-specific extensions and
   *       additional information</li>
   * </ul>
   *
   * <p>Updates the board checksum on successful save.
   *
   * @param p_output_stream the stream to write the DSN data to
   * @param p_design_name the name for the design
   * @param p_compat_mode true for compatibility mode, false for full format
   * @return true if save succeeded, false otherwise
   *
   * @see DsnFile#write
   */
  public boolean saveAsSpecctraDesignDsn(OutputStream p_output_stream, String p_design_name, boolean p_compat_mode) {
    if (board_is_read_only || p_output_stream == null) {
      return false;
    }

    boolean wasSaveSuccessful = DsnFile.write(this, p_output_stream, p_design_name, p_compat_mode);

    if (wasSaveSuccessful) {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

  /**
   * Writes a Specctra session (.SES) file containing routing results.
   *
   * <p>The SES (Session) format records the routing solution, including all traces
   * and vias created during routing. This file can be imported back into the
   * original PCB design tool.
   *
   * @param outputStream the stream to write the session data to
   * @param designName the name for the design
   * @return true if save succeeded, false otherwise
   *
   * @see HeadlessBoardManager#saveAsSpecctraSessionSes
   */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    if (board_is_read_only) {
      return false;
    }

    return super.saveAsSpecctraSessionSes(outputStream, designName);
  }

  /**
   * Converts a Specctra session file to an Eagle script (.SCR) format.
   *
   * <p>This allows routing results to be imported into Autodesk Eagle PCB software
   * by reading a .SES file and converting it to Eagle script commands.
   *
   * @param p_input_stream the stream containing the .SES session data
   * @param p_output_stream the stream to write the Eagle script to
   * @return true if conversion succeeded, false otherwise
   *
   * @see SessionToEagle
   */
  public boolean saveSpecctraSessionSesAsEagleScriptScr(InputStream p_input_stream, OutputStream p_output_stream) {
    if (board_is_read_only) {
      return false;
    }
    return SessionToEagle.get_instance(p_input_stream, p_output_stream, this.board);
  }

  /**
   * Loads a board design from a Specctra DSN format file.
   *
   * <p>Extends the base implementation by setting the initial layer to 0 after
   * loading completes.
   *
   * @param inputStream the stream containing the DSN data
   * @param boardObservers observers to be notified of board changes
   * @param identificationNumberGenerator generator for assigning unique IDs to board items
   * @return the result of the load operation including success status and any warnings
   *
   * @see HeadlessBoardManager#loadFromSpecctraDsn
   */
  @Override
  public DsnFile.ReadResult loadFromSpecctraDsn(InputStream inputStream, BoardObservers boardObservers,
      IdentificationNumberGenerator identificationNumberGenerator) {
    var result = super.loadFromSpecctraDsn(inputStream, boardObservers, identificationNumberGenerator);
    this.set_layer(0);
    return result;
  }

  /**
   * Saves the currently edited board design to a binary format file.
   *
   * <p>Serializes all board data structures:
   * <ul>
   *   <li>The routing board with all items</li>
   *   <li>Interactive settings</li>
   *   <li>Coordinate transform</li>
   *   <li>Graphics context</li>
   * </ul>
   *
   * <p>Updates the board checksum on successful save for change tracking.
   *
   * @param p_object_stream the stream to write serialized data to
   * @return true if save succeeded, false if an error occurred
   *
   * @see #loadFromBinary(ObjectInputStream)
   */
  public boolean saveAsBinary(ObjectOutputStream p_object_stream) {
    boolean result = true;
    try {
      p_object_stream.writeObject(board);
      p_object_stream.writeObject(interactiveSettings);
      p_object_stream.writeObject(coordinate_transform);
      p_object_stream.writeObject(graphics_context);

      originalBoardChecksum = calculateCrc32();
    } catch (Exception _) {
      screen_messages.set_status_message(tm.getText("save_error"));
      result = false;
    }
    return result;
  }

  /**
   * Closes all currently used files to ensure file buffers are written to disk.
   *
   * <p>Currently a no-op placeholder method. File closing is handled elsewhere
   * or by the Java runtime.
   */
  public void close_files() {
  }

  /**
   * Initiates interactive routing starting from the specified location.
   *
   * <p>Transitions to RouteState, which handles interactive trace routing.
   * The starting location is converted from screen to board coordinates.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_point the starting position in screen coordinates
   *
   * @see RouteState
   */
  public void start_route(Point2D p_point) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState new_state = RouteState.get_instance(location, this.interactive_state, this);
    set_interactive_state(new_state);
  }

  /**
   * Selects board items at the specified screen location.
   *
   * <p>Delegates to the current MenuState to handle item selection at the point.
   * Multiple items at the same location may cycle through selection.
   *
   * <p>This operation requires the interactive state to be a MenuState and is
   * ignored if the board is in read-only mode.
   *
   * @param p_point the location in screen coordinates where items should be selected
   *
   * @see MenuState#select_items(FloatPoint)
   */
  public void select_items(Point2D p_point) {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState)) {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState return_state = ((MenuState) interactive_state).select_items(location);
    set_interactive_state(return_state);
  }

  /**
   * Selects all board items within an interactively defined rectangular region.
   *
   * <p>Initiates a state where the user can drag to define a selection rectangle.
   * All items within or intersecting the rectangle will be selected.
   *
   * <p>This operation requires the interactive state to be a MenuState and is
   * ignored if the board is in read-only mode.
   *
   * @see MenuState
   */
  public void select_items_in_region() {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState)) {
      return;
    }
    set_interactive_state(InspectItemsInRegionState.get_instance(this.interactive_state, this));
  }

  /**
   * Selects all items in the provided collection programmatically.
   *
   * <p>Behavior depends on the current interactive state:
   * <ul>
   *   <li><strong>MenuState:</strong> Transitions to InspectedItemState with the items selected</li>
   *   <li><strong>InspectedItemState:</strong> Adds the items to the existing selection</li>
   * </ul>
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_items the collection of items to select
   *
   * @see InspectedItemState
   */
  public void select_items(Set<Item> p_items) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    this.display_layer_message();
    if (interactive_state instanceof MenuState) {
      set_interactive_state(InspectedItemState.get_instance(p_items, interactive_state, this));
    } else if (interactive_state instanceof InspectedItemState state) {
      state
          .get_item_list()
          .addAll(p_items);
      repaint();
    }
  }

  /**
   * Searches for a swappable pin at the specified location and prepares for pin swap.
   *
   * <p>Pin swapping allows rearranging equivalent pins within a component (e.g., swapping
   * gates in a logic IC). If a swappable pin is found, initiates the pin swap operation.
   *
   * <p>This operation requires the interactive state to be a MenuState and is
   * ignored if the board is in read-only mode.
   *
   * @param p_location the location in screen coordinates to search for a swappable pin
   *
   * @see MenuState#swap_pin(FloatPoint)
   */
  public void swap_pin(Point2D p_location) {
    if (board_is_read_only || !(this.interactive_state instanceof MenuState)) {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_location);
    InteractiveState return_state = ((MenuState) interactive_state).swap_pin(location);
    set_interactive_state(return_state);
  }

  /**
   * Zooms the display to show all currently selected items.
   *
   * <p>Calculates a bounding box around all selected items (with margins based on
   * trace widths) and adjusts the view to frame them. Useful for quickly navigating
   * to a selection.
   *
   * <p>This operation requires the interactive state to be InspectedItemState.
   *
   * @see BoardPanel#zoom_frame(Point2D, Point2D)
   */
  public void zoom_selection() {
    if (!(interactive_state instanceof InspectedItemState)) {
      return;
    }
    IntBox bounding_box = this.board.get_bounding_box(((InspectedItemState) interactive_state).get_item_list());
    bounding_box = bounding_box.offset(this.board.rules.get_max_trace_half_width());
    Point2D lower_left = this.graphics_context.coordinate_transform.board_to_screen(bounding_box.ll.to_float());
    Point2D upper_right = this.graphics_context.coordinate_transform.board_to_screen(bounding_box.ur.to_float());
    this.panel.zoom_frame(lower_left, upper_right);
  }

  /**
   * Toggles the selection state of the item at the specified location.
   *
   * <p>Behavior:
   * <ul>
   *   <li>If the item is already selected: removes it from the selection</li>
   *   <li>If the item is not selected: adds it to the selection</li>
   * </ul>
   *
   * <p>This allows building up a multi-item selection by clicking items one at a time.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @param p_point the location in screen coordinates to pick the item
   *
   * @see InspectedItemState#toggle_select(FloatPoint)
   */
  public void toggle_select_action(Point2D p_point) {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState return_state = ((InspectedItemState) interactive_state).toggle_select(location);
    if (return_state != this.interactive_state) {
      set_interactive_state(return_state);
      repaint();
    }
  }

  /**
   * Sets the fixed state of selected items to prevent them from being moved or modified.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void fix_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Removes the fixed state from selected items, allowing them to be moved or modified.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void unfix_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Displays detailed information about the selected item in a text window.
   *
   * <p>Shows properties such as net assignment, layer, clearance class, and other
   * item-specific attributes in a dedicated info window.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#info()
   */
  public void display_selected_item_info() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    ((InspectedItemState) interactive_state).info();
  }

  /**
   * Makes all selected items connectable and assigns them to a new net.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void assign_selected_to_new_net() {
    // Editing disabled in inspection mode
  }

  /**
   * Assigns all selected items to a new group (e.g., creating a new component).
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void assign_selected_to_new_group() {
    // Editing disabled in inspection mode
  }

  /**
   * Deletes all unfixed selected items from the board.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void delete_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Deletes all unfixed selected traces and vias inside a rectangular region.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void cutout_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Assigns the specified clearance class to all selected items.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   *
   * @param p_cl_class_index the clearance class index to assign
   */
  public void assign_clearance_classs_to_selected_items(int p_cl_class_index) {
    // Editing disabled in inspection mode
  }

  /**
   * Moves or rotates the selected items starting from the specified location.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   *
   * @param p_from_location the starting location for the move/rotate operation
   */
  public void move_selected_items(Point2D p_from_location) {
    // Editing disabled in inspection mode
  }

  /**
   * Copies all selected items to a new location.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   *
   * @param p_from_location the starting location for the copy operation
   */
  public void copy_selected_items(Point2D p_from_location) {
    // Editing disabled in inspection mode
  }

  /**
   * Optimizes the routing of selected items (pull-tight, smoothing).
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void optimize_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Runs the autorouter on selected items only.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode.
   * The method is a placeholder for future functionality.
   */
  public void autoroute_selected_items() {
    // Editing disabled in inspection mode
  }

  /**
   * Starts the autorouter and route optimizer to process the entire board.
   *
   * <p>This method:
   * <ul>
   *   <li>Creates a snapshot of the current board state (for undo)</li>
   *   <li>Sets the board to read-only mode to prevent user modifications</li>
   *   <li>Starts a background thread to run autorouting and optimization</li>
   * </ul>
   *
   * <p>The operation runs in a separate thread (InteractiveActionThread), allowing
   * the UI to remain responsive. The user can click to stop the operation.
   *
   * @param job the routing job containing board and router configuration
   * @return the interactive action thread running the autorouter, or null if board is read-only
   *
   * @see InteractiveActionThread
   * @see #stop_autorouter_and_route_optimizer()
   */
  public InteractiveActionThread start_autorouter_and_route_optimizer(RoutingJob job) {
    // The auto-router and route optimizer can only be started if the board is not
    // read only
    if (board_is_read_only) {
      return null;
    }

    // Generate a snapshot of the board before starting the autorouter
    board.generate_snapshot();

    // Start the auto-router and route optimizer
    // TODO: ideally we should only pass the board and the routerSettings to the
    // thread, and let the thread create the router and optimizer
    this.interactive_action_thread = InteractiveActionThread.get_autorouter_and_route_optimizer_instance(this, job);
    this.interactive_action_thread.start();

    return this.interactive_action_thread;
  }

  /**
   * Stops the currently running autorouter and route optimizer.
   *
   * <p>Requests the background thread to stop and restores the board to
   * interactive mode (not read-only). The operation may not stop immediately
   * if the router is in the middle of routing a connection.
   *
   * @see #start_autorouter_and_route_optimizer(RoutingJob)
   * @see InteractiveActionThread#requestStop()
   */
  public void stop_autorouter_and_route_optimizer() {
    if (this.interactive_action_thread != null) {
      // The left button is used to stop the interactive action thread.
      this.interactive_action_thread.requestStop();
    }

    this.set_board_read_only(false);
  }

  /**
   * Extends the selection to include all items belonging to the same nets as selected items.
   *
   * <p>Useful for selecting all traces and vias of a net after selecting just one item
   * on that net.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#extent_to_whole_nets()
   */
  public void extend_selection_to_whole_nets() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    set_interactive_state(((InspectedItemState) interactive_state).extent_to_whole_nets());
  }

  /**
   * Extends the selection to include all items belonging to the same components as selected items.
   *
   * <p>Useful for selecting an entire component (all pads, silkscreen, etc.) after
   * selecting just one pad.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#extent_to_whole_components()
   */
  public void extend_selection_to_whole_components() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    set_interactive_state(((InspectedItemState) interactive_state).extent_to_whole_components());
  }

  /**
   * Extends the selection to include all items in the same connected sets as selected items.
   *
   * <p>A connected set includes all items electrically connected, possibly spanning
   * multiple nets through components.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#extent_to_whole_connected_sets()
   */
  public void extend_selection_to_whole_connected_sets() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    set_interactive_state(((InspectedItemState) interactive_state).extent_to_whole_connected_sets());
  }

  /**
   * Extends the selection to include all items in the same connections as selected items.
   *
   * <p>A connection is a routed path between two pins on the same net, including
   * all traces and vias in that path.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#extent_to_whole_connections()
   */
  public void extend_selection_to_whole_connections() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    set_interactive_state(((InspectedItemState) interactive_state).extent_to_whole_connections());
  }

  /**
   * Toggles the display of clearance violations for selected items only.
   *
   * <p>Shows or hides clearance violations specifically related to the currently
   * selected items, allowing focused inspection of potential design rule violations.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board
   * is in read-only mode.
   *
   * @see InspectedItemState#toggle_clearance_violations()
   */
  public void toggle_selected_item_violations() {
    if (board_is_read_only || !(interactive_state instanceof InspectedItemState)) {
      return;
    }
    ((InspectedItemState) interactive_state).toggle_clearance_violations();
  }

  /**
   * Rotates items being moved by 45 degrees.
   *
   * <p>The rotation direction is determined by p_factor:
   * <ul>
   *   <li>Positive factor: rotate counter-clockwise</li>
   *   <li>Negative factor: rotate clockwise</li>
   * </ul>
   *
   * <p>This operation requires MoveItemState and is ignored if the board is
   * in read-only mode.
   *
   * @param p_factor the rotation direction and magnitude
   *
   * @see MoveItemState#turn_45_degree(int)
   */
  public void turn_45_degree(int p_factor) {
    if (board_is_read_only || !(interactive_state instanceof MoveItemState)) {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactive_state).turn_45_degree(p_factor);
  }

  /**
   * Flips components being moved to the opposite side of the board.
   *
   * <p>Changes component placement from top to bottom side or vice versa,
   * useful for component layout operations.
   *
   * <p>This operation requires MoveItemState and is ignored if the board is
   * in read-only mode.
   *
   * @see MoveItemState#change_placement_side()
   */
  public void change_placement_side() {
    if (board_is_read_only || !(interactive_state instanceof MoveItemState)) {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactive_state).change_placement_side();
  }

  /**
   * Initiates interactive zoom region selection.
   *
   * <p>Allows the user to drag a rectangle on the screen, then zooms the display
   * to show that rectangular region.
   *
   * @see ZoomRegionState
   */
  public void zoom_region() {
    interactive_state = ZoomRegionState.get_instance(this.interactive_state, this);
  }

  /**
   * Starts interactive creation of a circular obstacle.
   *
   * <p>Transitions to CircleConstructionState where the user can define the
   * circle's center and radius. Circular obstacles are used for keepouts,
   * mounting holes, or other circular restrictions.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_point the starting position in screen coordinates for the circle center
   *
   * @see CircleConstructionState
   */
  public void start_circle(Point2D p_point) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(
        CircleConstructionState.get_instance(location, this.interactive_state, this));
  }

  /**
   * Starts interactive creation of a tile-shaped obstacle.
   *
   * <p>Transitions to TileConstructionState where the user can define a rectangular
   * or tile-shaped obstacle. Tiles are used for keepout areas, component outlines,
   * or routing restrictions.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_point the starting position in screen coordinates for the tile
   *
   * @see TileConstructionState
   */
  public void start_tile(Point2D p_point) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(
        TileConstructionState.get_instance(location, this.interactive_state, this));
  }

  /**
   * Starts interactive creation of a polygon-shaped obstacle.
   *
   * <p>Transitions to PolygonShapeConstructionState where the user can define
   * arbitrary polygon shapes by clicking corners. Used for complex keepout areas
   * or irregular obstacles.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_point the starting position in screen coordinates for the first corner
   *
   * @see PolygonShapeConstructionState
   */
  public void start_polygonshape_item(Point2D p_point) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    set_interactive_state(
        PolygonShapeConstructionState.get_instance(location, this.interactive_state, this));
  }

  /**
   * Starts interactive addition of a hole to an existing obstacle shape.
   *
   * <p>Transitions to HoleConstructionState where the user can define holes
   * (cutouts) within existing obstacles. Useful for creating complex shapes
   * with interior voids.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_point the starting position in screen coordinates for the hole
   *
   * @see HoleConstructionState
   */
  public void start_adding_hole(Point2D p_point) {
    if (board_is_read_only) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphics_context.coordinate_transform.screen_to_board(p_point);
    InteractiveState new_state = HoleConstructionState.get_instance(location, this.interactive_state, this);
    set_interactive_state(new_state);
  }

  /**
   * Returns the screen rectangle that requires repainting due to recent interactive actions.
   *
   * <p>Calculates the minimal rectangular region on screen that needs to be updated,
   * based on board items that have changed. The rectangle includes a margin for
   * trace widths to ensure complete visual updates.
   *
   * @return the rectangle in screen coordinates that needs repainting
   *
   * @see RoutingBoard#get_graphics_update_box()
   */
  Rectangle get_graphics_update_rectangle() {
    Rectangle result;
    IntBox update_box = board.get_graphics_update_box();
    if (update_box == null || update_box.is_empty()) {
      result = new Rectangle(0, 0, 0, 0);
    } else {
      IntBox offset_box = update_box.offset(board.get_max_trace_half_width());
      result = graphics_context.coordinate_transform.board_to_screen(offset_box);
    }
    return result;
  }

  /**
   * Finds all board items at the specified location on the active layer.
   *
   * <p>Uses the current item selection filter from interactive settings. If nothing
   * is found on the active layer and select_on_all_visible_layers is enabled, searches
   * all visible layers.
   *
   * @param p_location the position in board coordinates to search
   * @return a set of items at that location (may be empty)
   *
   * @see #pick_items(FloatPoint, ItemSelectionFilter)
   * @see InteractiveSettings#item_selection_filter
   */
  Set<Item> pick_items(FloatPoint p_location) {
    return pick_items(p_location, interactiveSettings.item_selection_filter);
  }

  /**
   * Finds all board items at the specified location with a custom item filter.
   *
   * <p>Searches the active layer first. If nothing is found and select_on_all_visible_layers
   * is enabled, expands the search to all visible layers (excluding the active layer).
   * The item filter determines which item types are considered.
   *
   * @param p_location the position in board coordinates to search
   * @param p_item_filter the filter defining which item types to include
   * @return a set of items matching the filter at that location (may be empty)
   *
   * @see RoutingBoard#pick_items(Point, int, ItemSelectionFilter)
   * @see ItemSelectionFilter
   */
  Set<Item> pick_items(FloatPoint p_location, ItemSelectionFilter p_item_filter) {
    IntPoint location = p_location.round();
    Set<Item> result = board.pick_items(location, interactiveSettings.layer, p_item_filter);
    if (result.isEmpty() && interactiveSettings.select_on_all_visible_layers) {
      for (int i = 0; i < graphics_context.layer_count(); i++) {
        if (i == interactiveSettings.layer || graphics_context.get_layer_visibility(i) <= 0) {
          continue;
        }
        result.addAll(board.pick_items(location, i, p_item_filter));
      }
    }
    return result;
  }

  /**
   * Programmatically moves the mouse cursor to the specified board location.
   *
   * <p>Converts the board coordinates to screen coordinates and moves the system
   * mouse cursor. Used by interactive states to provide visual feedback or guide
   * user attention.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param p_to_location the target position in board coordinates
   *
   * @see BoardPanel#move_mouse(Point2D)
   */
  void move_mouse(FloatPoint p_to_location) {
    if (!board_is_read_only) {
      panel.move_mouse(graphics_context.coordinate_transform.board_to_screen(p_to_location));
    }
  }

  /**
   * Returns the current interactive state (mode) of the board manager.
   *
   * <p>The interactive state determines how user input is interpreted and
   * what operations are available (e.g., select, route, drag, construct).
   *
   * @return the current interactive state
   *
   * @see InteractiveState
   * @see #set_interactive_state(InteractiveState)
   */
  public InteractiveState get_interactive_state() {
    return this.interactive_state;
  }

  /**
   * Sets the current interactive state and updates the toolbar accordingly.
   *
   * <p>Transitions to a new interactive mode if the provided state is different
   * from the current one. The toolbar is updated to reflect the new mode's
   * available operations.
   *
   * <p>Toolbar update is skipped when the board is in read-only mode.
   *
   * @param p_state the new interactive state to activate
   *
   * @see InteractiveState#set_toolbar()
   */
  public void set_interactive_state(InteractiveState p_state) {
    if (p_state != null && p_state != interactive_state) {
      this.interactive_state = p_state;
      if (!this.board_is_read_only) {
        p_state.set_toolbar();
      }
    }
  }

  /**
   * Adjusts the design bounds to encompass all board items, including those outside the outline.
   *
   * <p>Recalculates the bounding box to include all items on the board, even if they
   * extend beyond the board outline. This ensures the graphics context can properly
   * display all content.
   *
   * <p>Useful after loading designs or when items have been placed outside normal bounds.
   *
   * @see GraphicsContext#change_design_bounds(IntBox)
   */
  public void adjust_design_bounds() {
    IntBox new_bounding_box = this.board.get_bounding_box();
    Collection<Item> board_items = this.board.get_items();
    for (Item curr_item : board_items) {
      IntBox curr_bounding_box = curr_item.bounding_box();
      if (curr_bounding_box.ur.x < Integer.MAX_VALUE) {
        new_bounding_box = new_bounding_box.union(curr_bounding_box);
      }
    }
    this.graphics_context.change_design_bounds(new_bounding_box);
  }

  /**
   * Cleans up resources and prepares the board manager for garbage collection.
   *
   * <p>This method:
   * <ul>
   *   <li>Removes event listeners to prevent memory leaks</li>
   *   <li>Closes any open files</li>
   *   <li>Nullifies all major object references to allow garbage collection</li>
   * </ul>
   *
   * <p>Should be called when the board manager is no longer needed.
   */
  public void dispose() {
    FRLogger
        .getLogEntries()
        .removeLogEntryAddedListener(this.logEntryAddedListener);
    FRLogger.removeTraceEventListener(this.traceEventListener);
    close_files();
    graphics_context = null;
    coordinate_transform = null;
    interactiveSettings = null;
    interactive_state = null;
    ratsnest = null;
    clearance_violations = null;
    board = null;
  }

  /**
   * Returns the board update strategy for batch operations.
   *
   * <p>The update strategy controls how the board is updated during autorouting
   * and optimization operations.
   *
   * @return the current board update strategy
   *
   * @see BoardUpdateStrategy
   */
  public BoardUpdateStrategy get_board_update_strategy() {
    return board_update_strategy;
  }

  /**
   * Sets the board update strategy for batch operations.
   *
   * @param p_board_update_strategy the new board update strategy
   *
   * @see BoardUpdateStrategy
   */
  public void set_board_update_strategy(BoardUpdateStrategy p_board_update_strategy) {
    board_update_strategy = p_board_update_strategy;
  }

  /**
   * Returns the hybrid routing ratio configuration.
   *
   * <p>The hybrid ratio defines the balance between different routing algorithms
   * when using hybrid routing approaches.
   *
   * @return the hybrid ratio string
   */
  public String get_hybrid_ratio() {
    return hybrid_ratio;
  }

  /**
   * Sets the hybrid routing ratio configuration.
   *
   * @param p_hybrid_ratio the hybrid ratio configuration string
   */
  public void set_hybrid_ratio(String p_hybrid_ratio) {
    hybrid_ratio = p_hybrid_ratio;
  }

  /**
   * Returns the item selection strategy for batch autorouting.
   *
   * <p>The strategy determines which items/nets are selected for routing and
   * in what order during batch operations.
   *
   * @return the current item selection strategy
   *
   * @see ItemSelectionStrategy
   */
  public ItemSelectionStrategy get_item_selection_strategy() {
    return item_selection_strategy;
  }

  /**
   * Sets the item selection strategy for batch autorouting.
   *
   * @param p_item_selection_strategy the new item selection strategy
   *
   * @see ItemSelectionStrategy
   */
  public void set_item_selection_strategy(ItemSelectionStrategy p_item_selection_strategy) {
    item_selection_strategy = p_item_selection_strategy;
  }

  /**
   * Returns the number of threads to use for parallel routing operations.
   *
   * <p>If multi-threading is disabled in global settings, this method automatically
   * returns 1 and logs an informational message. This ensures safe operation even
   * if threading is misconfigured.
   *
   * @return the effective number of threads (respecting global settings)
   */
  public int get_num_threads() {
    if ((num_threads > 1) && (!globalSettings.featureFlags.multiThreading)) {
      routingJob.logInfo("Multi-threading is disabled in the settings. Using single thread.");
      num_threads = 1;
    }

    return num_threads;
  }

  /**
   * Sets the number of threads to use for parallel routing operations.
   *
   * <p>The actual number used may be limited by global settings and system capabilities.
   *
   * @param p_value the number of threads to use (should be >= 1)
   *
   * @see #get_num_threads()
   */
  public void set_num_threads(int p_value) {
    num_threads = p_value;
  }

  /**
   * Registers a listener to be notified when the board's read-only state changes.
   *
   * <p>Listeners are typically UI components that need to enable/disable controls
   * based on whether the board is in read-only mode (e.g., during autorouting).
   *
   * @param listener the consumer to notify with the new read-only state (true/false)
   *
   * @see #set_board_read_only(boolean)
   */
  public void addReadOnlyEventListener(Consumer<Boolean> listener) {
    readOnlyEventListeners.add(listener);
  }
}