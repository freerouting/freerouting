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
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.ComboBoxLayer;
import app.freerouting.interactive.commands.InteractiveCommand;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.logger.LogEntries;
import app.freerouting.logger.LogEntry;
import app.freerouting.logger.LogEntryType;
import app.freerouting.logger.TraceEvent;
import app.freerouting.logger.TraceEventListener;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.IOException;
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
 * enabling visual interaction with the routing board. It serves as the central controller for
 * interactive routing operations, coordinating between:
 *
 * <ul>
 *   <li>User input and mouse interactions
 *   <li>Board display and graphics rendering
 *   <li>Interactive states and routing modes
 *   <li>Autorouting and manual routing operations
 *   <li>Design rule checking and violation display
 *   <li>Undo/redo and board history management
 * </ul>
 *
 * <p><strong>Key Responsibilities:</strong>
 *
 * <ul>
 *   <li><strong>State Management:</strong> Controls interactive states (routing, selecting,
 *       dragging, etc.)
 *   <li><strong>Graphics Coordination:</strong> Manages coordinate transformations and display
 *       context
 *   <li><strong>User Interaction:</strong> Handles mouse events and keyboard input
 *   <li><strong>Visual Feedback:</strong> Displays rats nest, clearance violations, and trace
 *       feedback
 *   <li><strong>Thread Management:</strong> Coordinates background operations (autorouting,
 *       optimization)
 *   <li><strong>File Operations:</strong> Manages design loading, saving, and session file handling
 * </ul>
 *
 * <p><strong>Interactive States:</strong> The board manager uses a state pattern to handle
 * different interaction modes:
 *
 * <ul>
 *   <li>RouteMenuState - Default selection and menu state
 *   <li>RouteState - Interactive trace routing
 *   <li>DragState - Moving items on the board
 *   <li>SelectState - Selecting items for operations
 *   <li>And various other specialized states
 * </ul>
 *
 * <p><strong>Coordinate Systems:</strong> This class manages transformations between multiple
 * coordinate spaces:
 *
 * <ul>
 *   <li>Screen coordinates (pixels on display)
 *   <li>User coordinates (design units visible to user)
 *   <li>Board coordinates (internal integer coordinates)
 * </ul>
 *
 * @see HeadlessBoardManager
 * @see InteractiveState
 * @see BoardPanel
 * @see GraphicsContext
 */
public class GuiBoardManager extends HeadlessBoardManager {

  /**
   * The minimum interval in milliseconds between consecutive board panel repaints during background
   * operations (autorouting, optimization).
   *
   * <p>This throttle mechanism prevents excessive repainting during intensive background
   * operations, maintaining a maximum effective frame rate of 1 FPS (1000ms interval).
   */
  private static final long background_repaint_interval = 1000;

  /**
   * The minimum interval in milliseconds between consecutive board panel repaints during
   * interactive operations (dragging, moving).
   *
   * <p>This throttle provides smoother visual feedback for interactive operations, targeting
   * approximately 30 FPS.
   */
  private static final long interactive_repaint_interval = 33;

  /**
   * The timestamp of the most recent board panel repaint operation.
   *
   * <p>Used in conjunction with repaint_interval to implement repaint throttling. Tracked in
   * milliseconds since epoch.
   */
  private static long last_repainted_time;

  /**
   * Manager for on-screen status and information messages.
   *
   * <p>Displays messages to the user including:
   *
   * <ul>
   *   <li>Current operation status
   *   <li>Error and warning counts
   *   <li>Trace event information
   *   <li>Interactive prompts and feedback
   * </ul>
   *
   * @see ScreenMessages
   */
  public final ScreenMessages screenMessages;

  /**
   * Merger that consolidates router settings from multiple sources.
   *
   * <p>Combines settings from:
   *
   * <ul>
   *   <li>Default application settings
   *   <li>Design-specific settings from DSN files
   *   <li>User preferences and overrides
   * </ul>
   *
   * @see SettingsMerger
   */
  public final SettingsMerger settingsMerger;

  /**
   * The graphical panel component that displays and renders the routing board.
   *
   * <p>This panel handles the visual presentation of the board, providing:
   *
   * <ul>
   *   <li>Rendering of board items (traces, vias, pins, etc.)
   *   <li>Display of auxiliary information (rats nest, violations, etc.)
   *   <li>Visual feedback during interactive operations
   *   <li>Screen message display integration
   * </ul>
   *
   * @see BoardPanel
   */
  private final BoardPanel panel;

  /**
   * Text manager for internationalized message strings.
   *
   * <p>Provides localized text for UI elements and messages based on the current locale setting.
   */
  private final TextManager tm;

  /**
   * Collection of listeners notified when the board's read-only state changes.
   *
   * <p>UI components register listeners to update their state (enabled/disabled) when the board
   * becomes read-only (e.g., during autorouting or logfile playback).
   */
  private final List<Consumer<Boolean>> readOnlyEventListeners = new ArrayList<>();

  /**
   * Global application settings container.
   *
   * <p>Provides access to application-wide configuration including locale, thread pool settings,
   * and other global preferences.
   */
  private final GlobalSettings globalSettings;

  /**
   * Listener that responds to new log entries being added.
   *
   * <p>Updates the on-screen error and warning counters when errors or warnings are logged during
   * operations.
   */
  private final LogEntries.LogEntryAddedListener logEntryAddedListener;

  /**
   * Listener that responds to trace events during routing operations.
   *
   * <p>Handles trace-level debugging events, displaying impacted items and points on the board for
   * diagnostic purposes.
   */
  private final TraceEventListener traceEventListener;

  /**
   * The current locale for internationalized UI text and messages.
   *
   * <p>Determines the language used for all user-facing text elements.
   */
  private final Locale locale;

  /**
   * Graphics context managing visual display settings for the board.
   *
   * <p>Controls rendering aspects including:
   *
   * <ul>
   *   <li>Layer visibility and transparency
   *   <li>Color schemes for different item types
   *   <li>Display modes and visual options
   *   <li>Rendering quality and performance settings
   * </ul>
   *
   * @see GraphicsContext
   */
  public GraphicsContext graphicsContext;

  /**
   * Coordinate transformer for converting between different coordinate systems.
   *
   * <p>Handles transformations between:
   *
   * <ul>
   *   <li>Screen coordinates (pixels)
   *   <li>User coordinates (design units: mm, mil, inch)
   *   <li>Board coordinates (internal integer units)
   * </ul>
   *
   * <p>Also manages zoom level, pan offset, and coordinate system scaling.
   *
   * @see CoordinateTransform
   */
  public CoordinateTransform coordinateTransform;

  /**
   * Manager for detecting and displaying clearance violations between board items.
   *
   * <p>Identifies and visualizes violations including:
   *
   * <ul>
   *   <li>Trace-to-trace clearance violations
   *   <li>Trace-to-via clearance violations
   *   <li>Violations with component pads and keepout areas
   * </ul>
   *
   * <p>Violations are displayed with visual indicators on the board.
   *
   * @see ClearanceViolations
   */
  public ClearanceViolations clearanceViolations;

  /**
   * The currently active interactive state controlling user interaction behavior.
   *
   * <p>The state pattern is used to handle different interaction modes:
   *
   * <ul>
   *   <li>RouteMenuState - Selection and menu operations
   *   <li>RouteState - Interactive trace routing
   *   <li>DragState - Moving items
   *   <li>SelectState - Item selection operations
   *   <li>And various other specialized states
   * </ul>
   *
   * <p>Each state handles mouse events and keyboard input differently based on the current
   * operation mode.
   *
   * @see InteractiveState
   */
  InteractiveState interactiveState;

  /**
   * Flag to force immediate board panel repaint, bypassing the throttle mechanism.
   *
   * <p>Used when immediate visual feedback is required, such as:
   *
   * <ul>
   *   <li>Reading and playing back logfiles
   *   <li>Step-by-step operation execution
   *   <li>User-requested manual refresh
   * </ul>
   */
  boolean paintImmediately;

  /**
   * The GUI-session singleton for interactive settings.
   *
   * <p>This field holds the {@link InteractiveSettings} singleton that acts as the live {@link
   * app.freerouting.settings.sources.GuiSettings} source (priority 50) for the {@link
   * SettingsMerger} pipeline. It is initialised in {@link #createBoard} and in {@link
   * #loadFromSpecctraDsn} (when DSN reading bypasses {@code create_board}).
   *
   * <p>This field intentionally shadows the removed {@code interactiveSettings} field that
   * previously lived on {@link HeadlessBoardManager}; it is not accessible from headless code.
   *
   * @see InteractiveSettings#getOrCreate(app.freerouting.board.RoutingBoard)
   */
  private InteractiveSettings interactiveSettings;

  /**
   * Direct reference to the {@link app.freerouting.gui.BoardFrame} that owns this manager.
   *
   * <p>Set by {@link #setBoardFrame(app.freerouting.gui.BoardFrame)} immediately after construction
   * (and after every {@link BoardPanel#resetBoardHandling} call). Having a direct back-reference
   * avoids walking the AWT component hierarchy to locate the frame.
   */
  private app.freerouting.gui.BoardFrame boardFrame;

  /**
   * Number of threads to use for parallel routing operations.
   *
   * <p>Controls the thread pool size for autorouting and batch optimization tasks.
   */
  private int numThreads;

  /**
   * Strategy for updating the board during batch operations.
   *
   * <p>Determines how and when the board is updated during autorouting:
   *
   * <ul>
   *   <li>Update frequency
   *   <li>Commit timing
   *   <li>Rollback behavior
   * </ul>
   *
   * @see BoardUpdateStrategy
   */
  private BoardUpdateStrategy boardUpdateStrategy;

  /**
   * The hybrid routing ratio configuration string.
   *
   * <p>Defines the balance between different routing algorithms when using hybrid routing
   * approaches.
   */
  private String hybridRatio;

  /**
   * Strategy for selecting which items to route during batch autorouting.
   *
   * <p>Controls the order and selection criteria for:
   *
   * <ul>
   *   <li>Net prioritization
   *   <li>Connection selection
   *   <li>Routing sequence optimization
   * </ul>
   *
   * @see ItemSelectionStrategy
   */
  private ItemSelectionStrategy itemSelectionStrategy;

  /**
   * Thread managing long-running interactive actions in the background.
   *
   * <p>Handles operations like:
   *
   * <ul>
   *   <li>Batch autorouting
   *   <li>Board optimization
   *   <li>Fanout generation
   * </ul>
   *
   * <p>Allows the UI to remain responsive during lengthy operations.
   *
   * @see InteractiveActionThread
   */
  private InteractiveActionThread interactiveActionThread;

  /**
   * Visual display manager for incomplete connections (air wires/rats nest).
   *
   * <p>Shows unrouted connections between pins as straight lines, helping users understand routing
   * requirements and progress. Recalculated after board changes.
   *
   * @see RatsNest
   */
  private RatsNest ratsnest;

  /**
   * Flag indicating whether the board is in read-only mode.
   *
   * <p>Set to true when:
   *
   * <ul>
   *   <li>Processing logfiles
   *   <li>Running background autorouting
   *   <li>Performing batch operations
   * </ul>
   *
   * <p>Prevents interactive modifications that could interfere with automated operations or corrupt
   * the board state.
   */
  private boolean boardIsReadOnly;

  /**
   * The current position of the mouse cursor in board coordinates.
   *
   * <p>Updated continuously as the mouse moves, used for:
   *
   * <ul>
   *   <li>Snap-to-grid calculations
   *   <li>Interactive state processing
   *   <li>Visual feedback rendering
   * </ul>
   */
  private FloatPoint currentMousePosition;

  /**
   * Array of points to highlight on the board for trace event visualization.
   *
   * <p>When trace-level logging is active, these points indicate the locations affected by the
   * current routing operation, providing visual debugging feedback.
   */
  private Point[] impactedPoints;

  /**
   * Creates a new GUI board manager for interactive routing operations.
   *
   * <p>Initializes all subsystems required for interactive board manipulation including:
   *
   * <ul>
   *   <li>Graphics context and coordinate transformation
   *   <li>Screen message display
   *   <li>Event listeners for logging and tracing
   *   <li>Initial interactive state (RouteMenuState)
   *   <li>Text manager for internationalization
   * </ul>
   *
   * <p>The constructor establishes connections between the board manager and the GUI panel, sets up
   * event handling, and prepares the system for user interaction.
   *
   * @param panel the board panel component for visual display
   * @param globalSettings application-wide configuration settings
   * @param routingJob the routing job containing board and design data
   * @param settingsMerger merger for consolidating settings from multiple sources
   * @see HeadlessBoardManager#HeadlessBoardManager(RoutingJob)
   */
  public GuiBoardManager(
      BoardPanel panel,
      GlobalSettings globalSettings,
      RoutingJob routingJob,
      SettingsMerger settingsMerger) {
    super(routingJob);
    this.globalSettings = globalSettings;
    this.settingsMerger = settingsMerger;
    this.locale = globalSettings.currentLocale;
    this.panel = panel;
    this.screenMessages = panel.screenMessages;
    this.setInteractiveState(RouteMenuState.getInstance(this));

    this.tm = new TextManager(this.getClass(), globalSettings.currentLocale);

    this.logEntryAddedListener = this::logEntryAdded;
    FRLogger.getLogEntries().addLogEntryAddedListener(this.logEntryAddedListener);

    this.traceEventListener = this::handleTraceEvent;
    FRLogger.addTraceEventListener(this.traceEventListener);
  }

  /**
   * Sets the owning {@link app.freerouting.gui.BoardFrame} for this manager.
   *
   * <p>Must be called by {@link app.freerouting.gui.BoardPanel} immediately after constructing or
   * resetting the {@code GuiBoardManager} instance so that {@link #refreshGuiFromSettings()} can
   * reach the frame's permanent subwindows without walking the AWT component hierarchy.
   *
   * @param boardFrame the frame that owns this manager; {@code null} clears the reference
   */
  public void setBoardFrame(app.freerouting.gui.BoardFrame boardFrame) {
    this.boardFrame = boardFrame;
  }

  /**
   * Handles notification when a new log entry is added to the log system.
   *
   * <p>This listener updates the on-screen error and warning counts displayed to the user. Only
   * errors and warnings trigger UI updates to maintain performance during verbose logging.
   *
   * @param logEntry the newly added log entry
   * @see LogEntry
   * @see ScreenMessages#setErrorAndWarningCount(int, int)
   */
  private void logEntryAdded(LogEntry logEntry) {
    if ((logEntry.getType() == LogEntryType.Error)
        || (logEntry.getType() == LogEntryType.Warning)) {
      LogEntries entries = FRLogger.getLogEntries();
      screenMessages.setErrorAndWarningCount(entries.getErrorCount(), entries.getWarningCount());
    }
  }

  /**
   * Handles trace-level debugging events during routing operations.
   *
   * <p>When trace logging is enabled, this method:
   *
   * <ul>
   *   <li>Displays trace messages on screen with operation details
   *   <li>Highlights impacted items and points on the board
   *   <li>Triggers board repaint to show visual feedback
   * </ul>
   *
   * <p>Execution is deferred to the Event Dispatch Thread using SwingUtilities to ensure
   * thread-safe GUI updates.
   *
   * @param event the trace event containing operation details and impacted locations
   * @see TraceEvent
   * @see ScreenMessages#setTraceMessage(String, String, String)
   */
  private void handleTraceEvent(TraceEvent event) {
    if (event == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          screenMessages.setTraceMessage(
              event.getOperation(), event.getMessage(), event.getImpactedItems());
          // Store the impacted points for drawing
          impactedPoints = event.getImpactedPoints();
          panel.repaint();
        });
  }

  /**
   * Gets the current routing job containing board data and routing configuration.
   *
   * <p>Interactive states use this to access job-specific router settings, design rules, and board
   * structure. The routing job encapsulates all design-specific information needed for routing
   * operations.
   *
   * @return the current routing job, or null if no job is loaded
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
   *
   * <ul>
   *   <li>Logfile playback
   *   <li>Background autorouting
   *   <li>Batch optimization operations
   * </ul>
   *
   * <p>When true, user interactions that modify the board are disabled.
   *
   * @return true if the board is read-only, false if modifications are allowed
   * @see #setBoardReadOnly(boolean)
   */
  public boolean isBoardReadOnly() {
    return this.boardIsReadOnly;
  }

  /**
   * Sets the board's read-only state to prevent or allow user modifications.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Updates the internal read-only flag
   *   <li>Propagates the state to interactive settings
   *   <li>Notifies all registered listeners of the state change
   * </ul>
   *
   * <p>Listeners typically update UI elements (buttons, menus) to reflect the board's modifiable
   * state.
   *
   * @param value true to make board read-only, false to allow modifications
   * @see #isBoardReadOnly()
   */
  public void setBoardReadOnly(boolean value) {
    this.boardIsReadOnly = value;
    this.interactiveSettings.setReadOnly(value);

    // Raise an event to notify the observers that the board read only property
    // changed
    this.readOnlyEventListeners.forEach(listener -> listener.accept(value));
  }

  /**
   * Returns the current locale for UI text internationalization.
   *
   * <p>The locale determines the language used for all user-visible text including menus, messages,
   * and tooltips.
   *
   * @return the current locale setting
   * @see Locale
   */
  public Locale getLocale() {
    return this.locale;
  }

  /**
   * Returns the number of layers in the board design.
   *
   * <p>Layer count includes all signal layers, power planes, and ground planes defined in the board
   * structure. Returns 0 if no board is loaded.
   *
   * @return the number of board layers, or 0 if board is null
   * @see LayerStructure
   */
  public int getLayerCount() {
    if (board == null) {
      return 0;
    }
    return board.getLayerCount();
  }

  /**
   * Returns the current mouse cursor position in board coordinate space.
   *
   * <p>This position is:
   *
   * <ul>
   *   <li>Updated continuously as the mouse moves
   *   <li>Used by interactive states for operation placement
   *   <li>Affected by snap-to-grid settings
   *   <li>Transformed from screen coordinates through coordinateTransform
   * </ul>
   *
   * @return the current mouse position in board coordinates
   * @see FloatPoint
   * @see CoordinateTransform
   */
  public FloatPoint getCurrentMousePosition() {
    return this.currentMousePosition;
  }

  /**
   * Sets whether conduction areas should be treated as obstacles during routing.
   *
   * <p>When conduction areas are ignored (p_value = true):
   *
   * <ul>
   *   <li>Traces can route through conduction areas of foreign nets
   *   <li>Useful for power planes and ground fills
   *   <li>Reduces routing complexity in filled areas
   * </ul>
   *
   * <p>When conduction areas are obstacles (p_value = false):
   *
   * <ul>
   *   <li>Foreign net traces must route around them
   *   <li>Provides stricter isolation between nets
   * </ul>
   *
   * <p>This setting is ignored if the board is read-only.
   *
   * @param value true to ignore conduction areas, false to treat them as obstacles
   * @see RoutingBoard#changeConductionIsObstacle(boolean)
   */
  public void setIgnoreConduction(boolean value) {
    if (boardIsReadOnly) {
      return;
    }
    board.changeConductionIsObstacle(!value);
  }

  /**
   * Sets the minimum distance from pin edges where traces can make their first turn.
   *
   * <p>This constraint controls trace exit geometry from pins:
   *
   * <ul>
   *   <li>Ensures traces extend straight from pins before turning
   *   <li>Improves manufacturing reliability near pads
   *   <li>Prevents acute angles at pin connections
   *   <li>Helps avoid solder mask and assembly issues
   * </ul>
   *
   * <p>When this value changes, existing pin exit stubs that were shove-fixed are released (set to
   * UNFIXED) to allow re-optimization with the new constraint. Only simple 2-corner exit traces are
   * unfixed.
   *
   * <p>This setting is ignored if the board is read-only.
   *
   * @param value the minimum edge-to-turn distance in user coordinate units
   * @see BoardRules#setPinEdgeToTurnDist(double)
   * @see Pin#hasTraceExitRestrictions()
   */
  public void setPinEdgeToTurnDist(double value) {
    if (boardIsReadOnly) {
      return;
    }
    double edgeToTurnDist = this.coordinateTransform.userToBoard(value);
    if (edgeToTurnDist != board.rules.getPinEdgeToTurnDist()) {
      // unfix the pin exit stubs
      Collection<Pin> pinList = board.getPins();
      for (Pin currPin : pinList) {
        if (currPin.hasTraceExitRestrictions()) {
          Collection<Item> contactList = currPin.getNormalContacts();
          for (Item currContact : contactList) {
            if ((currContact instanceof PolylineTrace trace)
                && currContact.getFixedState() == FixedState.SHOVE_FIXED) {
              if (trace.cornerCount() == 2) {
                currContact.setFixedState(FixedState.UNFIXED);
              }
            }
          }
        }
      }
    }
    board.rules.setPinEdgeToTurnDist(edgeToTurnDist);
  }

  /**
   * Changes the visibility and transparency of a specific board layer.
   *
   * <p>Layer visibility controls how prominently items on that layer are displayed:
   *
   * <ul>
   *   <li>Value of 1.0 - fully visible (opaque)
   *   <li>Value between 0 and 1 - partially transparent
   *   <li>Value of 0.0 - invisible (hidden)
   * </ul>
   *
   * <p>If the currently active routing layer becomes invisible, the system automatically switches
   * to the most visible layer to maintain usability.
   *
   * @param layer the layer index to modify (0-based)
   * @param value the visibility value between 0.0 (invisible) and 1.0 (fully visible)
   * @see GraphicsContext#setLayerVisibility(int, double)
   */
  public void setLayerVisibility(int layer, double value) {
    if (layer >= 0 && layer < graphicsContext.layerCount()) {
      graphicsContext.setLayerVisibility(layer, value);
      if (value == 0 && interactiveSettings.getLayer() == layer) {
        // change the current layer to the best visible layer, if it becomes invisible;
        double bestVisibility = 0;
        int bestVisibleLayer = 0;
        for (int i = 0; i < graphicsContext.layerCount(); i++) {
          if (graphicsContext.getLayerVisibility(i) > bestVisibility) {
            bestVisibility = graphicsContext.getLayerVisibility(i);
            bestVisibleLayer = i;
          }
        }
        interactiveSettings.setLayer(bestVisibleLayer);
      }
    }
  }

  /**
   * Gets the trace half-width (radius) used in interactive routing for the specified net and layer.
   *
   * <p>The trace half-width determines the thickness of traces created during interactive routing.
   * The value returned depends on the routing mode:
   *
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually configured trace width
   *   <li><strong>Automatic rule selection:</strong> Returns the trace width from the net's class
   *       rules
   * </ul>
   *
   * <p>Half-width is used because traces expand equally on both sides of their centerline. The
   * actual trace width is twice this value.
   *
   * @param netNo the net number to get the trace width for
   * @param layer the layer index where the trace will be placed
   * @return the trace half-width in board units
   * @see InteractiveSettings#manualRuleSelection
   * @see BoardRules#getTraceHalfWidth(int, int)
   */
  public int getTraceHalfwidth(int netNo, int layer) {
    int result;
    if (interactiveSettings.getManualRuleSelection()) {
      result = interactiveSettings.manualTraceHalfWidthArr[layer];
    } else {
      result = board.rules.getTraceHalfWidth(netNo, layer);
    }
    return result;
  }

  /**
   * Checks if the specified layer is active for interactive trace routing on the given net.
   *
   * <p>Layer activity determines whether traces can be routed on a particular layer for a net. The
   * behavior depends on the routing mode:
   *
   * <ul>
   *   <li><strong>Manual rule selection:</strong> All layers are considered active
   *   <li><strong>Automatic rule selection:</strong> Layer activity is determined by the net class
   *       configuration
   * </ul>
   *
   * <p>Returns true if the net or net class is not found (permissive default).
   *
   * @param netNo the net number to check
   * @param layer the layer index to check
   * @return true if the layer is active for routing this net, false otherwise
   * @see NetClass#isActiveRoutingLayer(int)
   */
  public boolean isActiveRoutingLayer(int netNo, int layer) {
    if (interactiveSettings.getManualRuleSelection()) {
      return true;
    }
    Net currentNet = this.board.rules.nets.get(netNo);
    if (currentNet == null) {
      return true;
    }
    NetClass currNetClass = currentNet.getNetClass();
    if (currNetClass == null) {
      return true;
    }
    return currNetClass.isActiveRoutingLayer(layer);
  }

  /**
   * Gets the clearance class used in interactive routing for the specified net.
   *
   * <p>The clearance class determines minimum spacing requirements between traces and other board
   * objects. The value returned depends on the routing mode:
   *
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually configured clearance class
   *   <li><strong>Automatic rule selection:</strong> Returns the clearance class from the net's
   *       class rules
   * </ul>
   *
   * <p>The clearance class is an index into the board's clearance matrix.
   *
   * @param netNo the net number to get the clearance class for
   * @return the clearance class index
   * @see app.freerouting.rules.ClearanceMatrix
   * @see NetClass#getTraceClearanceClass()
   */
  public int getTraceClearanceClass(int netNo) {
    int result;
    if (interactiveSettings.getManualRuleSelection()) {
      result = interactiveSettings.getManualTraceClearanceClass();
    } else {
      result = board.rules.nets.get(netNo).getNetClass().getTraceClearanceClass();
    }
    return result;
  }

  /**
   * Gets the via rule used in interactive routing for the specified net.
   *
   * <p>The via rule defines which via types (padstacks) are allowed and their priority order for
   * layer transitions. The value returned depends on the routing mode:
   *
   * <ul>
   *   <li><strong>Manual rule selection:</strong> Returns the manually selected via rule if valid
   *   <li><strong>Automatic rule selection:</strong> Returns the via rule from the net's class
   *       rules
   * </ul>
   *
   * <p>If manual selection is active but the index is invalid, falls back to the net class rule.
   *
   * @param netNo the net number to get the via rule for
   * @return the via rule defining allowed via types and priorities
   * @see ViaRule
   * @see NetClass#getViaRule()
   */
  public ViaRule getViaRule(int netNo) {
    ViaRule result = null;
    if (interactiveSettings.getManualRuleSelection()) {
      result = board.rules.viaRules.get(this.interactiveSettings.getManualViaRuleIndex());
    }
    if (result == null) {
      result = board.rules.nets.get(netNo).getNetClass().getViaRule();
    }
    return result;
  }

  /**
   * Changes the default trace half-width currently used in interactive routing on the specified
   * layer.
   *
   * <p>This sets the default trace width for nets that don't have specific width rules. The change
   * affects future routing operations on the layer.
   *
   * <p>This operation is ignored if:
   *
   * <ul>
   *   <li>The board is in read-only mode
   *   <li>The layer index is out of valid range
   * </ul>
   *
   * @param layer the layer index to set the default width for
   * @param value the new default trace half-width in board units
   * @see BoardRules#setDefaultTraceHalfWidth(int, int)
   */
  public void setDefaultTraceHalfwidth(int layer, int value) {
    if (boardIsReadOnly) {
      return;
    }
    if (layer >= 0 && layer <= board.getLayerCount()) {
      board.rules.setDefaultTraceHalfWidth(layer, value);
    }
  }

  /**
   * Switches clearance compensation on or off for the search tree.
   *
   * <p>Clearance compensation is a performance optimization technique where search tree shapes are
   * pre-expanded by their clearance requirements. This:
   *
   * <ul>
   *   <li><strong>When enabled:</strong> Faster clearance checking but higher memory usage
   *   <li><strong>When disabled:</strong> Lower memory usage but slower clearance checks
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param value true to enable clearance compensation, false to disable
   * @see SearchTreeManager#setClearanceCompensationUsed(boolean)
   */
  public void setClearanceCompensation(boolean value) {
    if (boardIsReadOnly) {
      return;
    }
    board.searchTreeManager.setClearanceCompensationUsed(value);
  }

  /**
   * Changes the current snap angle restriction for interactive routing.
   *
   * <p>The snap angle determines which trace angles are allowed during routing:
   *
   * <ul>
   *   <li><strong>FORTYFIVE_DEGREE:</strong> Traces limited to 0°, 45°, 90°, 135°, etc.
   *   <li><strong>NINETY_DEGREE:</strong> Traces limited to 0°, 90°, 180°, 270° (orthogonal only)
   *   <li><strong>NONE:</strong> Any angle allowed (free-angle routing)
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param snapAngle the angle restriction to apply
   * @see AngleRestriction
   * @see BoardRules#setTraceAngleRestriction(AngleRestriction)
   */
  public void setCurrentSnapAngle(AngleRestriction snapAngle) {
    if (boardIsReadOnly) {
      return;
    }
    board.rules.setTraceAngleRestriction(snapAngle);
  }

  /**
   * Changes the current active layer for interactive routing.
   *
   * <p>The current layer is where new traces and vias will be created during interactive routing
   * operations. This method:
   *
   * <ul>
   *   <li>Clamps the layer index to valid range [0, layerCount - 1]
   *   <li>Updates the display to show the new active layer
   *   <li>Updates UI components to reflect the layer change
   * </ul>
   *
   * <p>This setting is ignored if the board is in read-only mode.
   *
   * @param layer the layer index to make active (will be clamped to valid range)
   * @see #setLayer(int)
   */
  public void setCurrentLayer(int layer) {
    if (boardIsReadOnly) {
      return;
    }
    layer = Math.max(layer, 0);
    layer = Math.min(layer, board.getLayerCount() - 1);
    setLayer(layer);
  }

  /**
   * Changes the current layer without saving the change to logfile.
   *
   * <p>This internal method performs the actual layer change operation:
   *
   * <ul>
   *   <li>Updates the screen message display with the layer name
   *   <li>Sets the layer in interactive settings
   *   <li>Updates the layer selector in the UI panel (for signal layers)
   *   <li>Makes the layer visible if it was hidden
   *   <li>Sets the layer as fully visible in the graphics context
   *   <li>Triggers a board repaint
   * </ul>
   *
   * <p><strong>Note:</strong> This is for internal use. External code should use {@link
   * #setCurrentLayer(int)} which provides validation and logging.
   *
   * @param layerNo the layer index to switch to (assumed to be valid)
   * @see #setCurrentLayer(int)
   */
  void setLayer(int layerNo) {
    Layer currLayer = board.layerStructure.arr[layerNo];
    screenMessages.setLayer(currLayer.name);
    interactiveSettings.setLayer(layerNo);

    // Change the selected layer in the select parameter window.
    if ((!this.boardIsReadOnly) && (currLayer.isSignal)) {
      this.panel.setSelectedSignalLayer(layerNo);
    }

    // make the layer visible, if it is invisible
    if (graphicsContext != null) {
      if (graphicsContext.getLayerVisibility(layerNo) == 0) {
        graphicsContext.setLayerVisibility(layerNo, 1);
        if (panel != null && panel.boardFrame != null) {
          panel.boardFrame.refreshWindows();
        }
      }
      graphicsContext.setFullyVisibleLayer(layerNo);
    }
    repaint();
  }

  /**
   * Updates the layer message display to show the current active layer name.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Clears the additional message field
   *   <li>Displays the current layer name in the layer message field
   * </ul>
   *
   * <p>Useful for refreshing the display after layer-related operations.
   *
   * @see ScreenMessages#setLayer(String)
   */
  public void displayLayerMessage() {
    screenMessages.clearAddField();
    Layer currLayer = board.layerStructure.arr[this.interactiveSettings.getLayer()];
    screenMessages.setLayer(currLayer.name);
  }

  /**
   * Sets the manual trace half-width used in interactive routing for specified layers.
   *
   * <p>This method supports setting trace width for:
   *
   * <ul>
   *   <li><strong>All layers:</strong> When p_layer_no == {@link ComboBoxLayer#ALL_LAYER_INDEX}
   *   <li><strong>Inner layers only:</strong> When p_layer_no == {@link
   *       ComboBoxLayer#INNER_LAYER_INDEX}
   *   <li><strong>Single layer:</strong> When p_layer_no is a specific layer index
   * </ul>
   *
   * <p>The manual trace width is only used when manual rule selection is active.
   *
   * @param layerNo the layer index, or special index for all/inner layers
   * @param value the trace half-width to set in board units
   * @see InteractiveSettings#setManualTraceHalfWidth(int, int)
   * @see ComboBoxLayer
   */
  public void setManualTraceHalfWidth(int layerNo, int value) {
    if (layerNo == ComboBoxLayer.ALL_LAYER_INDEX) {
      for (int i = 0; i < interactiveSettings.getLayerCount(); i++) {
        this.interactiveSettings.setManualTraceHalfWidth(i, value);
      }
    } else if (layerNo == ComboBoxLayer.INNER_LAYER_INDEX) {
      for (int i = 1; i < interactiveSettings.getLayerCount() - 1; i++) {
        this.interactiveSettings.setManualTraceHalfWidth(i, value);
      }
    } else {
      this.interactiveSettings.setManualTraceHalfWidth(layerNo, value);
    }
  }

  /**
   * Changes the interactive selectability of a specific item type.
   *
   * <p>When an item type is set to non-selectable:
   *
   * <ul>
   *   <li>It cannot be picked or selected during interactive operations
   *   <li>If currently selected items become non-selectable, they are filtered out
   * </ul>
   *
   * <p>This is useful for focusing on specific types of objects during editing.
   *
   * @param itemType the item type to make selectable or non-selectable
   * @param value true to make the item type selectable, false to disable selection
   * @see ItemSelectionFilter.SelectableChoices
   * @see InteractiveSettings#setSelectable(ItemSelectionFilter.SelectableChoices, boolean)
   */
  public void setSelectable(ItemSelectionFilter.SelectableChoices itemType, boolean value) {
    interactiveSettings.setSelectable(itemType, value);
    if (!value && this.interactiveState instanceof InspectedItemState) {
      setInteractiveState(((InspectedItemState) interactiveState).filter());
    }
  }

  /**
   * Toggles the display of incomplete connections (rats nest) on the board.
   *
   * <p>The rats nest shows unrouted connections as straight lines between pins:
   *
   * <ul>
   *   <li><strong>If hidden or null:</strong> Creates and displays the rats nest
   *   <li><strong>If visible:</strong> Hides the rats nest
   * </ul>
   *
   * <p>Triggers a board repaint to update the display.
   *
   * @see RatsNest
   * @see #createRatsnest()
   */
  public void toggleRatsnest() {
    if (ratsnest == null || ratsnest.isHidden()) {
      createRatsnest();
    } else {
      ratsnest = null;
    }
    repaint();
  }

  /**
   * Toggles the display of clearance violations on the board.
   *
   * <p>Clearance violations indicate locations where items are too close together:
   *
   * <ul>
   *   <li><strong>If not displayed:</strong> Calculates and displays all violations with count
   *       message
   *   <li><strong>If displayed:</strong> Hides the violations and clears the status message
   * </ul>
   *
   * <p>Triggers a board repaint to update the display.
   *
   * @see ClearanceViolations
   */
  public void toggleClearanceViolations() {
    if (clearanceViolations == null) {
      clearanceViolations = new ClearanceViolations(this.board.getItems());
      Integer violationCount = (clearanceViolations.list.size() + 1) / 2;
      String currMessage = violationCount + " " + tm.getText("clearance_violations_found");
      screenMessages.setStatusMessage(currMessage);
    } else {
      clearanceViolations = null;
      screenMessages.setStatusMessage("");
    }
    repaint();
  }

  /**
   * Creates and displays the rats nest showing all incomplete connections.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Creates a new RatsNest object analyzing all incomplete connections
   *   <li>Counts incomplete connections and length violations
   *   <li>Displays a status message with connection statistics
   * </ul>
   *
   * <p>The rats nest shows unrouted connections as straight lines (air wires) between pins.
   *
   * @see RatsNest
   * @see #toggleRatsnest()
   */
  public void createRatsnest() {
    ratsnest = new RatsNest(this.board);
    updateRatsnestStatusMessage();
  }

  /** Attaches a rats nest built during background board loading and updates the status bar. */
  public void attachPreparedRatsNest(RatsNest preparedRatsNest) {
    this.ratsnest = preparedRatsNest;
    updateRatsnestStatusMessage();
  }

  /** Creates a rats nest only when one is not already present (for example after async load). */
  public void createRatsnestIfAbsent() {
    if (ratsnest == null) {
      createRatsnest();
    } else {
      updateRatsnestStatusMessage();
    }
  }

  private void updateRatsnestStatusMessage() {
    if (ratsnest == null) {
      return;
    }
    Integer incompleteCount = ratsnest.incompleteCount();
    int lengthViolationCount = ratsnest.lengthViolationCount();
    String currMessage;
    if (lengthViolationCount == 0) {
      currMessage =
          tm.getText("ratsnest_status_incomplete_only", Integer.toString(incompleteCount));
    } else {
      currMessage =
          tm.getText(
              "ratsnest_status_with_length_violations",
              Integer.toString(incompleteCount),
              Integer.toString(lengthViolationCount));
    }
    screenMessages.setStatusMessage(currMessage);
  }

  /**
   * Recalculates and updates the incomplete connections for the specified net.
   *
   * <p>If the rats nest is currently displayed, this method recalculates the air wires for the
   * given net and updates the display. Ignored if rats nest is not active or net number is invalid.
   *
   * @param netNo the net number to recalculate connections for (must be > 0)
   * @see RatsNest#recalculate(int, BasicBoard)
   */
  void updateRatsnest(int netNo) {
    if (ratsnest != null && netNo > 0) {
      ratsnest.recalculate(netNo, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates incomplete connections for the specified net, considering only the given items.
   *
   * <p>This optimized version recalculates connections only for items in the provided collection,
   * which is more efficient when only a subset of items has changed.
   *
   * @param netNo the net number to recalculate connections for (must be > 0)
   * @param itemList the collection of items to consider in the recalculation
   * @see RatsNest#recalculate(int, Collection, BasicBoard)
   */
  void updateRatsnest(int netNo, Collection<Item> itemList) {
    if (ratsnest != null && netNo > 0) {
      ratsnest.recalculate(netNo, itemList, this.board);
      ratsnest.show();
    }
  }

  /**
   * Recalculates all incomplete connections if the rats nest is currently active.
   *
   * <p>This full recalculation creates a new rats nest from scratch, analyzing all nets and items
   * on the board. Used when significant board changes have occurred that affect multiple nets.
   *
   * @see RatsNest#RatsNest(BasicBoard)
   */
  void updateRatsnest() {
    if (ratsnest != null) {
      ratsnest = new RatsNest(this.board);
    }
  }

  /**
   * Hides the rats nest display without destroying the data structure.
   *
   * <p>The rats nest object is retained in memory but not rendered. This allows quick re-display
   * without recalculation. Use {@link #toggleRatsnest()} to show it again.
   *
   * @see RatsNest#hide()
   * @see #toggleRatsnest()
   */
  public void hideRatsnest() {
    if (ratsnest != null) {
      ratsnest.hide();
    }
  }

  /**
   * Shows the rats nest display if it is currently active.
   *
   * <p>Makes the rats nest visible on the board, displaying all incomplete connections as air
   * wires. The rats nest object must already exist.
   *
   * @see RatsNest#show()
   * @see #hideRatsnest()
   */
  public void showRatsnest() {
    if (ratsnest != null) {
      ratsnest.show();
    }
  }

  /**
   * Removes the rats nest object, deallocating its data structure.
   *
   * <p>This fully destroys the rats nest. Creating it again will require recalculation from
   * scratch. Use {@link #hideRatsnest()} if you want to preserve the data for quick re-display.
   *
   * @see #getRatsnest()
   * @see #hideRatsnest()
   */
  public void removeRatsnest() {
    ratsnest = null;
  }

  /**
   * Returns the rats nest object containing incomplete connection information.
   *
   * <p>If the rats nest doesn't exist, creates a new one by analyzing the board. The rats nest
   * contains:
   *
   * <ul>
   *   <li>All incomplete (unrouted) connections
   *   <li>Connection length information
   *   <li>Length violation data
   * </ul>
   *
   * @return the rats nest object with connection analysis
   * @see RatsNest
   * @see #removeRatsnest()
   */
  public RatsNest getRatsnest() {
    if (ratsnest == null) {
      ratsnest = new RatsNest(this.board);
    }
    return this.ratsnest;
  }

  /**
   * Recalculates length violations for all nets in the rats nest.
   *
   * <p>Checks all incomplete connections against maximum length constraints and updates violation
   * status. If violations changed and the rats nest is visible, triggers a board repaint to update
   * the display.
   *
   * @see RatsNest#recalculateLengthViolations()
   */
  public void recalculateLengthViolations() {
    if (this.ratsnest != null) {
      if (this.ratsnest.recalculateLengthViolations()) {
        if (!this.ratsnest.isHidden()) {
          this.repaint();
        }
      }
    }
  }

  /**
   * Sets the visibility filter for incomplete connections of the specified net.
   *
   * <p>Controls whether the incomplete connections (air wires) for a specific net are displayed in
   * the rats nest:
   *
   * <ul>
   *   <li><strong>true:</strong> Show incompletes for this net
   *   <li><strong>false:</strong> Hide incompletes for this net
   * </ul>
   *
   * <p>Useful for focusing on specific nets while hiding others for clarity.
   *
   * @param netNo the net number to filter
   * @param value true to show incompletes, false to hide them
   * @see RatsNest#setFilter(int, boolean)
   */
  public void setIncompletesFilter(int netNo, boolean value) {
    if (ratsnest != null) {
      ratsnest.setFilter(netNo, value);
    }
  }

  /**
   * Creates the routing board with GUI-specific initialization.
   *
   * <p>This method extends the base board creation with GUI components:
   *
   * <ol>
   *   <li>Calls super to create the basic routing board structure
   *   <li>Initializes the coordinate transform for unit conversions
   *   <li>Creates the graphics context for visual rendering
   * </ol>
   *
   * <p>The coordinate transform handles conversions between:
   *
   * <ul>
   *   <li>User units (mm, mil, inch) for display
   *   <li>Board internal units for calculations
   *   <li>DSN file units for import/export
   * </ul>
   *
   * @param boundingBox the rectangular boundary of the board
   * @param layerStructure the layer stack-up definition
   * @param outlineShapes array of shapes defining the board outline
   * @param outlineClearanceClassName clearance class name for the outline
   * @param rules the board design rules and constraints
   * @param boardCommunication communication interface for external integration
   * @see HeadlessBoardManager#createBoard
   * @see GraphicsContext
   * @see CoordinateTransform
   */
  @Override
  public void createBoard(
      IntBox boundingBox,
      LayerStructure layerStructure,
      PolylineShape[] outlineShapes,
      String outlineClearanceClassName,
      BoardRules rules,
      Communication boardCommunication) {
    super.createBoard(
        boundingBox,
        layerStructure,
        outlineShapes,
        outlineClearanceClassName,
        rules,
        boardCommunication);

    // Reset and rebind the GUI-session singleton for the newly created board.
    this.interactiveSettings =
        InteractiveSettings.reset(this.board, this.routingJob.routerSettings);
    this.initializeManualTraceHalfWidths();

    // create the interactive/GUI settings with default values
    double unitFactor = boardCommunication.coordinateTransform.boardToDsn(1);
    this.coordinateTransform =
        new CoordinateTransform(1, boardCommunication.unit, unitFactor, boardCommunication.unit);

    // create a graphics context for the board
    Dimension panelSize = panel.getPreferredSize();
    graphicsContext = new GraphicsContext(boundingBox, panelSize, layerStructure, this.locale);
  }

  /**
   * Returns the GUI-session {@link InteractiveSettings} singleton.
   *
   * <p>The returned instance is also the live {@link app.freerouting.settings.sources.GuiSettings}
   * source (priority 50) registered in the {@link SettingsMerger} pipeline. It is always non-null
   * after a board has been created or loaded.
   *
   * @return the {@link InteractiveSettings} singleton; non-null after board initialisation
   */
  @Override
  public InteractiveSettings getInteractiveSettings() {
    return interactiveSettings;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns {@code true} for {@link GuiBoardManager}: the GUI session guarantees a
   * non-null {@link InteractiveSettings} singleton after board initialisation.
   */
  @Override
  public boolean isInteractiveModeSupported() {
    return true;
  }

  /**
   * Returns the GUI-session {@link InteractiveSettings} singleton.
   *
   * @return the {@link InteractiveSettings} singleton; non-null after board initialisation
   * @deprecated Use {@link #getInteractiveSettings()} instead.
   */
  @Deprecated
  @Override
  public InteractiveSettings getSettings() {
    return interactiveSettings;
  }

  /**
   * Initialises manual trace half-widths from the board's default net class rules.
   *
   * <p>Copies the default trace width for each layer from the board's default net class into {@link
   * InteractiveSettings#manualTraceHalfWidthArr}. Must be called after the board is created or
   * loaded.
   *
   * @see InteractiveSettings#manualTraceHalfWidthArr
   * @see app.freerouting.rules.NetClass#getTraceHalfWidth(int)
   */
  @Override
  public void initializeManualTraceHalfWidths() {
    if (interactiveSettings == null || this.board == null) {
      return;
    }
    for (int i = 0; i < interactiveSettings.getLayerCount(); i++) {
      interactiveSettings.manualTraceHalfWidthArr[i] =
          this.board.rules.getDefaultNetClass().getTraceHalfWidth(i);
    }
  }

  /**
   * Re-subscribes all permanent GUI subwindows as {@link java.beans.PropertyChangeListener}s on the
   * current {@link InteractiveSettings} singleton and pushes the fresh settings values to their
   * controls by calling {@code refresh()} on each window.
   *
   * <p>Must be called after every design load (DSN or binary) once the new {@link
   * InteractiveSettings} singleton has been bound to the new board, and after every {@link
   * InteractiveSettings#reset(app.freerouting.board.RoutingBoard)} call since the old singleton
   * (and its listener list) is discarded.
   *
   * <p>This method is a no-op when {@code interactiveSettings} is {@code null} (headless mode) or
   * when there is no {@link app.freerouting.gui.BoardFrame} attached.
   */
  public void refreshGuiFromSettings() {
    if (interactiveSettings == null || panel == null) {
      return;
    }
    // Use the direct BoardFrame back-reference set by BoardPanel.
    if (boardFrame == null) {
      return;
    }

    // Re-subscribe every permanent subwindow as a PropertyChangeListener.
    // The listener simply calls refresh() on the next EDT cycle to pull the new values.
    for (app.freerouting.gui.BoardSavableSubWindow subwindow :
        boardFrame.getPermanentSubwindows()) {
      if (subwindow == null) {
        continue;
      }
      // Capture the subwindow in a local effectively-final variable for the lambda.
      final app.freerouting.gui.BoardSavableSubWindow sw = subwindow;
      interactiveSettings.addPropertyChangeListener(
          _ -> javax.swing.SwingUtilities.invokeLater(sw::refresh));
      // Push current values immediately.
      subwindow.refresh();
    }
  }

  /**
   * Changes the user unit for coordinate display and input.
   *
   * <p>Updates the unit used throughout the GUI for:
   *
   * <ul>
   *   <li>Coordinate display in status messages
   *   <li>Dimension entry in dialogs
   *   <li>Measurement displays
   * </ul>
   *
   * <p>The coordinate transform is recreated to maintain correct scaling factors between user units
   * and board internal units.
   *
   * @param unit the new unit for user display (mm, mil, inch, etc.)
   * @see Unit
   * @see CoordinateTransform
   */
  public void changeUserUnit(Unit unit) {
    screenMessages.setUnitLabel(unit.toString());
    CoordinateTransform oldTransform = this.coordinateTransform;
    this.coordinateTransform =
        new CoordinateTransform(
            oldTransform.userUnitFactor,
            unit,
            oldTransform.boardUnitFactor,
            oldTransform.boardUnit);
  }

  /**
   * Requests a repaint of the board panel.
   *
   * <p>Repaint behavior depends on the paintImmediately flag:
   *
   * <ul>
   *   <li><strong>Immediate mode (paintImmediately=true):</strong> Forces synchronous repaint (used
   *       during logfile playback)
   *   <li><strong>Throttled mode (paintImmediately=false):</strong> Respects minimum interval
   *       between repaints to maintain responsive frame rate
   * </ul>
   *
   * <p>Throttling prevents excessive repainting during rapid board changes. The interval is
   * adjusted dynamically based on whether the user is interactively dragging items.
   *
   * @see #interactive_repaint_interval
   * @see #background_repaint_interval
   * @see #paintImmediately
   */
  public void repaint() {
    if (this.paintImmediately) {
      final Rectangle maxRectangle = new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
      panel.paintImmediately(maxRectangle);
    } else {
      // Use shorter interval for interactive dragging to ensure smooth visual feedback
      long effectiveInterval =
          isInInteractiveDrag() ? interactive_repaint_interval : background_repaint_interval;
      if (last_repainted_time < System.currentTimeMillis() - effectiveInterval) {
        last_repainted_time = System.currentTimeMillis();

        // Use partial repaint if we have an update box (more efficient)
        Rectangle updateRect = getGraphicsUpdateRectangle();
        if (updateRect.width > 0 && updateRect.height > 0) {
          panel.repaint(updateRect);
        } else {
          panel.repaint();
        }
      }
    }
  }

  /**
   * Requests a repaint of a specific rectangular region of the board panel.
   *
   * <p>Partial repaint is more efficient than full repaint when only a small area has changed.
   * Behavior depends on paintImmediately:
   *
   * <ul>
   *   <li><strong>Immediate mode:</strong> Synchronous repaint of the rectangle
   *   <li><strong>Normal mode:</strong> Asynchronous repaint request
   * </ul>
   *
   * @param rectangle the rectangular region to repaint in screen coordinates
   * @see #repaint()
   */
  public void repaint(Rectangle rectangle) {
    if (this.paintImmediately) {
      panel.paintImmediately(rectangle);
    } else {
      panel.repaint(rectangle);
    }
  }

  /**
   * Checks if the current interactive state is an active drag operation.
   *
   * <p>Used to determine repaint throttling behavior - interactive drags need higher frame rate for
   * smooth visual feedback.
   *
   * @return true if currently in a drag state (DragState or its subclasses)
   */
  private boolean isInInteractiveDrag() {
    return interactiveState instanceof DragState;
  }

  /**
   * Returns the board panel component used for graphical display.
   *
   * <p>The panel provides the visual interface for board rendering, user interaction, and screen
   * message display.
   *
   * @return the BoardPanel instance managing board visualization
   * @see BoardPanel
   */
  public BoardPanel getPanel() {
    return this.panel;
  }

  /**
   * Returns the popup menu for the current interactive state, if applicable.
   *
   * <p>Different interactive states may provide different popup menus with context-specific
   * actions. Some states do not use popup menus at all.
   *
   * @return the popup menu for the current state, or null if no menu is available
   * @see InteractiveState#getPopupMenu()
   */
  public JPopupMenu getCurrentPopupMenu() {
    JPopupMenu result;
    if (interactiveState != null) {
      result = interactiveState.getPopupMenu();
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Renders the complete board display including all visual elements.
   *
   * <p>This method draws all board elements in layers:
   *
   * <ol>
   *   <li>The routing board (traces, vias, pads, etc.)
   *   <li>The rats nest (incomplete connections) if visible
   *   <li>Clearance violations if visible
   *   <li>Interactive state graphics (rubber-band lines, temporary items)
   *   <li>Interactive action thread graphics (autoroute progress)
   *   <li>Trace event indicators (debugging visualization)
   * </ol>
   *
   * <p>Called automatically by the panel during repaint operations.
   *
   * @param graphics the Graphics context for rendering
   * @see RoutingBoard#draw(Graphics, GraphicsContext)
   * @see InteractiveState#draw(Graphics)
   */
  public void draw(Graphics graphics) {
    if (board == null) {
      return;
    }
    board.draw(graphics, graphicsContext);

    if (ratsnest != null) {
      ratsnest.draw(graphics, graphicsContext);
    }
    if (clearanceViolations != null) {
      clearanceViolations.draw(graphics, graphicsContext);
    }
    if (interactiveState != null) {
      interactiveState.draw(graphics);
    }
    if (interactiveActionThread != null) {
      interactiveActionThread.draw(graphics);
    }

    // Draw indicators for impacted points from trace events
    if (impactedPoints != null && impactedPoints.length > 0) {
      drawImpactedPointsIndicators(graphics);
    }
  }

  /**
   * Draws visual indicators (crosshairs and circles) at impacted points for trace debugging.
   *
   * <p>When trace-level logging is active, this method visualizes the locations affected by routing
   * operations. Each impacted point is marked with:
   *
   * <ul>
   *   <li>An X-shaped crosshair (two diagonal lines)
   *   <li>A circle around the point
   * </ul>
   *
   * <p>The indicator size is based on the default trace width, with a minimum size for visibility.
   * This provides visual feedback for debugging routing algorithms.
   *
   * @param graphics the Graphics context for rendering
   * @see #handleTraceEvent(TraceEvent)
   */
  private void drawImpactedPointsIndicators(Graphics graphics) {
    Color drawColor = graphicsContext.getHighlightColor();
    double drawIntensity = graphicsContext.getHighlightColorIntensity();
    int defaultTraceHalfWidth = board.rules.getDefaultTraceHalfWidth(0);
    double radius = Math.max(5 * defaultTraceHalfWidth / 10, 500); // Minimum radius of 500
    final double drawWidth = 50.0;

    for (Point point : impactedPoints) {
      if (point != null) {
        FloatPoint center = point.toFloat();

        // Draw an X marker (crosshair)
        FloatPoint[] drawPoints = new FloatPoint[2];

        // Draw first diagonal line (top-left to bottom-right)
        drawPoints[0] = new FloatPoint(center.x - radius, center.y - radius);
        drawPoints[1] = new FloatPoint(center.x + radius, center.y + radius);
        graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);

        // Draw second diagonal line (top-right to bottom-left)
        drawPoints[0] = new FloatPoint(center.x + radius, center.y - radius);
        drawPoints[1] = new FloatPoint(center.x - radius, center.y + radius);
        graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);

        // Draw a circle around the point
        graphicsContext.drawCircle(center, radius, drawWidth, drawColor, graphics, drawIntensity);
      }
    }
  }

  /**
   * Creates a snapshot of the current board state for undo functionality.
   *
   * <p>Snapshots should be created before operations that users may want to reverse. This operation
   * is ignored if the board is in read-only mode.
   *
   * @see RoutingBoard#generateSnapshot()
   * @see #undo()
   */
  public void generateSnapshot() {
    if (boardIsReadOnly) {
      return;
    }
    board.generateSnapshot();
  }

  /**
   * Restores the board to the state of the previous snapshot (undo operation).
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Reverts board changes to the last snapshot
   *   <li>Updates the rats nest for all affected nets
   *   <li>Displays a status message indicating success or failure
   *   <li>Triggers a board repaint
   * </ul>
   *
   * <p>The operation is ignored if:
   *
   * <ul>
   *   <li>The board is in read-only mode
   *   <li>The current state is not a MenuState (to prevent undo during active operations)
   * </ul>
   *
   * @see #redo()
   * @see #generateSnapshot()
   * @see RoutingBoard#undo(Set)
   */
  public void undo() {
    if (boardIsReadOnly || !(interactiveState instanceof MenuState)) {
      return;
    }
    Set<Integer> changedNets = new TreeSet<>();
    if (board.undo(changedNets)) {
      for (Integer changedNet : changedNets) {
        this.updateRatsnest(changedNet);
      }
      if (!changedNets.isEmpty()) {
        // reset the start pass number in the autorouter in case
        // a batch autorouter is undone.
        // Pass tracking is now handled locally in the router algorithms
      }
      screenMessages.setStatusMessage(tm.getText("undo"));
    } else {
      screenMessages.setStatusMessage(tm.getText("no_more_undo_possible"));
    }
    repaint();
  }

  /**
   * Restores the board to the state before the last undo operation (redo operation).
   *
   * <p>This method re-applies changes that were undone, moving forward in the undo/redo history.
   * The process:
   *
   * <ul>
   *   <li>Restores board changes that were undone
   *   <li>Updates the rats nest for all affected nets
   *   <li>Displays a status message indicating success or failure
   *   <li>Triggers a board repaint
   * </ul>
   *
   * <p>The operation is ignored if:
   *
   * <ul>
   *   <li>The board is in read-only mode
   *   <li>The current state is not a MenuState
   * </ul>
   *
   * @see #undo()
   * @see RoutingBoard#redo(Set)
   */
  public void redo() {
    if (boardIsReadOnly || !(interactiveState instanceof MenuState)) {
      return;
    }
    Set<Integer> changedNets = new TreeSet<>();
    if (board.redo(changedNets)) {
      for (Integer changedNet : changedNets) {
        this.updateRatsnest(changedNet);
      }
      screenMessages.setStatusMessage(tm.getText("redo"));
    } else {
      screenMessages.setStatusMessage(tm.getText("no_more_redo_possible"));
    }
    repaint();
  }

  /**
   * Handles left mouse button click events.
   *
   * <p>Behavior depends on board state:
   *
   * <ul>
   *   <li><strong>Read-only mode:</strong> Stops any running autorouter or optimizer
   *   <li><strong>Interactive mode:</strong> Delegates to the current interactive state for
   *       state-specific handling (e.g., starting routes, selecting items, placing vias)
   * </ul>
   *
   * <p>The screen point is converted to board coordinates before being passed to the interactive
   * state.
   *
   * @param point the mouse click location in screen coordinates
   * @see InteractiveState#leftButtonClicked(FloatPoint)
   * @see #stopAutorouterAndRouteOptimizer()
   */
  public void leftButtonClicked(Point2D point) {
    if (boardIsReadOnly) {
      // We are currently busy working on the board and the user clicked on the canvas
      // with the left mouse button.
      this.stopAutorouterAndRouteOptimizer();
      return;
    }
    if (interactiveState != null && graphicsContext != null) {
      FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
      InteractiveState returnState =
          executeStateCommand(interactiveState.leftButtonClickedCommand(location));
      applyInteractiveStateChange(returnState, true, false);
    }
  }

  /**
   * Handles mouse movement events, updating cursor position and providing hover information.
   *
   * <p>This method performs several tasks:
   *
   * <ul>
   *   <li>Updates the current mouse position (in both read-only and interactive modes)
   *   <li>Displays mouse coordinates in the status area
   *   <li>In interactive mode: delegates movement handling to the current state
   *   <li>Detects items under the cursor and displays tooltips with item information
   *   <li>Updates the display if the state changes
   * </ul>
   *
   * <p><strong>Note:</strong> Automatic repaint is avoided here to maintain performance during
   * interactive routing. States that need repainting should handle it explicitly.
   *
   * @param point the mouse position in screen coordinates
   * @see InteractiveState#mouseMoved()
   * @see #pickItems(FloatPoint)
   */
  public void mouseMoved(Point2D point) {
    if (interactiveState != null && graphicsContext != null) {
      this.currentMousePosition = graphicsContext.coordinateTransform.screenToBoard(point);

      // Always update the mouse position display, even when board is read-only
      FloatPoint mousePosition = coordinateTransform.boardToUser(this.currentMousePosition);
      screenMessages.setMousePosition(mousePosition);

      if (boardIsReadOnly) {
        // no interactive action when logfile is running, but mouse position is still updated
        return;
      }

      InteractiveState returnState = executeStateCommand(interactiveState.mouseMovedCommand());
      Set<Item> hoverItem = pickItems(this.currentMousePosition);
      if (hoverItem.size() == 1) {
        String hoverInfo = hoverItem.iterator().next().getHoverInfo(locale);
        this.panel.setToolTipText(hoverInfo);
      } else {
        this.panel.setToolTipText(null);
      }
      // An automatic repaint here would slow down the display
      // performance in interactive route.
      // If a repaint is necessary, it should be done in the individual mouse_moved
      // method of the class derived from InteractiveState
      applyInteractiveStateChange(returnState, true, false);
    }
  }

  /**
   * Handles mouse button press events.
   *
   * <p>Updates the current mouse position and delegates the event to the interactive state for
   * state-specific handling (e.g., initiating drag operations, starting selection rectangles).
   *
   * @param point the mouse position in screen coordinates where the button was pressed
   * @see InteractiveState#mousePressed(FloatPoint)
   */
  public void mousePressed(Point2D point) {
    if (interactiveState != null && graphicsContext != null) {
      this.currentMousePosition = graphicsContext.coordinateTransform.screenToBoard(point);
      InteractiveState returnState =
          executeStateCommand(interactiveState.mousePressedCommand(this.currentMousePosition));
      applyInteractiveStateChange(returnState, false, false);
    }
  }

  /**
   * Handles mouse drag events (mouse moved while button pressed).
   *
   * <p>Updates the current mouse position and delegates to the interactive state for state-specific
   * drag handling (e.g., dragging items, drawing selection rectangles, extending routes).
   *
   * <p>If the state changes during the drag, triggers a repaint.
   *
   * @param point the current mouse position in screen coordinates during the drag
   * @see InteractiveState#mouseDragged(FloatPoint)
   */
  public void mouseDragged(Point2D point) {
    if (interactiveState != null && graphicsContext != null) {
      this.currentMousePosition = graphicsContext.coordinateTransform.screenToBoard(point);
      InteractiveState returnState =
          executeStateCommand(interactiveState.mouseDraggedCommand(this.currentMousePosition));
      applyInteractiveStateChange(returnState, true, false);
    }
  }

  /**
   * Handles mouse button release events.
   *
   * <p>Delegates to the interactive state to complete operations initiated by button press or drag
   * (e.g., completing item moves, finalizing selection rectangles, finishing drag operations).
   *
   * <p>If the state changes upon button release, triggers a repaint.
   *
   * @see InteractiveState#buttonReleased()
   */
  public void buttonReleased() {
    if (interactiveState != null) {
      InteractiveState returnState = executeStateCommand(interactiveState.buttonReleasedCommand());
      applyInteractiveStateChange(returnState, true, false);
    }
  }

  /**
   * Handles mouse wheel movement events for zoom and other scroll operations.
   *
   * <p>Updates the current mouse position and delegates to the interactive state. Typically used
   * for:
   *
   * <ul>
   *   <li>Zooming in/out centered on the mouse position
   *   <li>Layer switching
   *   <li>State-specific scroll behaviors
   * </ul>
   *
   * @param point the mouse position in screen coordinates during wheel movement
   * @param rotation the wheel rotation amount (positive for up/away, negative for down/toward)
   * @see InteractiveState#mouseWheelMoved(int)
   */
  public void mouseWheelMoved(Point2D point, int rotation) {
    if (interactiveState != null && graphicsContext != null) {
      this.currentMousePosition = graphicsContext.coordinateTransform.screenToBoard(point);
      InteractiveState returnState =
          executeStateCommand(interactiveState.mouseWheelMovedCommand(rotation));
      applyInteractiveStateChange(returnState, true, false);
    }
  }

  /**
   * Handles keyboard input events for interactive commands.
   *
   * <p>Delegates to the current interactive state to handle keyboard shortcuts and commands (e.g.,
   * 'ESC' to cancel, numeric keys for layer selection, letter keys for tool switching).
   *
   * <p>If the state changes, updates the toolbar to reflect the new state.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param keyChar the character typed on the keyboard
   * @see InteractiveState#keyTyped(char)
   */
  public void keyTypedAction(char keyChar) {
    if (boardIsReadOnly || interactiveState == null || graphicsContext == null) {
      // no interactive action when logfile is running or board graphics are not ready
      return;
    }
    InteractiveState returnState = executeStateCommand(interactiveState.keyTypedCommand(keyChar));
    applyInteractiveStateChange(returnState, true, true);
  }

  /**
   * Completes the current interactive state and returns to its parent/return state.
   *
   * <p>This typically finalizes the current operation and returns to a more general state (e.g.,
   * completing a route returns to route menu state).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @see InteractiveState#complete()
   * @see #cancelState()
   */
  public void returnFromState() {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState newState = executeStateCommand(interactiveState.completeCommand());
    applyInteractiveStateChange(newState, true, false);
  }

  /**
   * Cancels the current interactive state, discarding any uncommitted changes.
   *
   * <p>Returns to the parent state without completing or saving the current operation (e.g.,
   * canceling a route in progress removes any temporary routing without creating traces).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @see InteractiveState#cancel()
   * @see #returnFromState()
   */
  public void cancelState() {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }

    InteractiveState newState = executeStateCommand(interactiveState.cancelCommand());
    applyInteractiveStateChange(newState, true, false);
  }

  /**
   * Requests a layer change in the current interactive state.
   *
   * <p>Delegates the layer change request to the interactive state, which may accept or reject it
   * based on the current operation (e.g., mid-route layer changes are allowed via vias, but other
   * states may reject layer changes).
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param newLayer the target layer index to change to
   * @return true if the layer change was successful, false if it failed
   * @see InteractiveState#changeLayerAction(int)
   */
  public boolean changeLayerAction(int newLayer) {
    boolean result = true;
    if (interactiveState != null && !boardIsReadOnly) {
      result = interactiveState.changeLayerAction(newLayer);
    }
    return result;
  }

  /**
   * Sets the interactive state to InspectMenuState for item selection and inspection.
   *
   * <p>InspectMenuState allows users to select and examine board items, view their properties, and
   * perform operations on selected items.
   *
   * @see InspectMenuState
   * @see #setRouteMenuState()
   */
  public void setInspectMenuState() {
    this.interactiveState = InspectMenuState.getInstance(this);
    screenMessages.setStatusMessage(tm.getText("select_menu"));
  }

  /**
   * Sets the interactive state to RouteMenuState for routing operations.
   *
   * <p>RouteMenuState is the default state, allowing users to start routing, select items, and
   * access routing-related operations.
   *
   * @see RouteMenuState
   * @see #setInspectMenuState()
   */
  public void setRouteMenuState() {
    this.interactiveState = RouteMenuState.getInstance(this);
    screenMessages.setStatusMessage(tm.getText("route_menu"));
  }

  /**
   * Sets the interactive state to DragMenuState for moving items.
   *
   * <p>DragMenuState allows users to select and drag board items to new positions.
   *
   * @see DragMenuState
   */
  public void setDragMenuState() {
    this.interactiveState = DragMenuState.getInstance(this);
    screenMessages.setStatusMessage(tm.getText("drag_menu"));
  }

  /**
   * Checks if the board has been modified since it was last saved or loaded.
   *
   * <p>Uses CRC32 checksum comparison to detect changes. This allows prompting the user before
   * closing or loading a new design if unsaved changes exist.
   *
   * @return true if the board has unsaved changes, false otherwise
   * @see #calculateCrc32()
   */
  public boolean isBoardChanged() {
    return calculateCrc32() != originalBoardChecksum;
  }

  /**
   * Loads an existing board design from a binary input stream.
   *
   * <p>Deserializes the board and all associated data structures:
   *
   * <ul>
   *   <li>The routing board with all items
   *   <li>Interactive settings
   *   <li>Coordinate transform
   *   <li>Graphics context
   * </ul>
   *
   * <p>After successful loading, updates the layer display and stores a checksum for change
   * detection.
   *
   * @param design the input stream containing serialized board data
   * @return true if loading succeeded, false if an error occurred
   * @see #saveAsBinary(ObjectOutputStream)
   */
  public boolean loadFromBinary(ObjectInputStream design) {
    String inputFilename =
        this.routingJob != null && this.routingJob.input != null
            ? this.routingJob.input.getFilename()
            : null;
    if (this.routingJob != null) {
      this.routingJob.logInfo(
          "Loading board file" + (inputFilename != null ? " '" + inputFilename + "'" : "") + "...");
    } else {
      FRLogger.info(
          "Loading board file" + (inputFilename != null ? " '" + inputFilename + "'" : "") + "...");
    }

    try {
      board = (RoutingBoard) design.readObject();
      interactiveSettings = (InteractiveSettings) design.readObject();
      // Adopt the deserialized instance as the authoritative singleton so that subsequent
      // getOrCreate / getInteractiveSettings calls return the same object.
      InteractiveSettings.setInstance(interactiveSettings);
      // Register the singleton as the live GuiSettings source (priority 50) in the merger so
      // that every subsequent merge() call reflects the current interactive GUI state.
      this.settingsMerger.addOrReplaceSources(interactiveSettings);
      coordinateTransform = (CoordinateTransform) design.readObject();
      graphicsContext = (GraphicsContext) design.readObject();
      originalBoardChecksum = calculateCrc32();
    } catch (Exception e) {
      routingJob.logError("Couldn't read design file", e);
      return false;
    }
    screenMessages.setLayer(board.layerStructure.arr[interactiveSettings.getLayer()].name);
    // Defer GUI refresh until surrounding load flow has recreated frame-managed subwindows.
    javax.swing.SwingUtilities.invokeLater(this::refreshGuiFromSettings);
    return true;
  }

  /**
   * Writes the currently edited board design to a Specctra DSN format file.
   *
   * <p>The DSN (Design) format is an industry-standard PCB interchange format that can be read by
   * various PCB tools. The compatibility mode parameter controls the scope of information written:
   *
   * <ul>
   *   <li><strong>Compatibility mode (true):</strong> Writes only standard DSN scopes for maximum
   *       compatibility with other tools
   *   <li><strong>Full mode (false):</strong> Writes Freerouting-specific extensions and additional
   *       information
   * </ul>
   *
   * <p>Updates the board checksum on successful save.
   *
   * @param outputStream the stream to write the DSN data to
   * @param designName the name for the design
   * @param compatMode true for compatibility mode, false for full format
   * @return true if save succeeded, false otherwise
   * @see DsnWriter#write
   */
  public boolean saveAsSpecctraDesignDsn(
      OutputStream outputStream, String designName, boolean compatMode) {
    if (boardIsReadOnly || outputStream == null) {
      return false;
    }

    boolean wasSaveSuccessful;
    try {
      DsnWriter.write(getRoutingBoard(), outputStream, designName, compatMode);
      wasSaveSuccessful = true;
    } catch (IOException e) {
      FRLogger.error("unable to write Specctra DSN file", e);
      wasSaveSuccessful = false;
    }

    if (wasSaveSuccessful) {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

  /**
   * Writes a Specctra session (.SES) file containing routing results.
   *
   * <p>The SES (Session) format records the routing solution, including all traces and vias created
   * during routing. This file can be imported back into the original PCB design tool.
   *
   * @param outputStream the stream to write the session data to
   * @param designName the name for the design
   * @return true if save succeeded, false otherwise
   * @see HeadlessBoardManager#saveAsSpecctraSessionSes
   */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    if (boardIsReadOnly) {
      return false;
    }

    return super.saveAsSpecctraSessionSes(outputStream, designName);
  }

  /**
   * Converts a Specctra session file to an Eagle script (.SCR) format.
   *
   * <p>This allows routing results to be imported into Autodesk Eagle PCB software by reading a
   * .SES file and converting it to Eagle script commands.
   *
   * @param inputStream the stream containing the .SES session data
   * @param outputStream the stream to write the Eagle script to
   * @return true if conversion succeeded, false otherwise
   * @see app.freerouting.io.specctra.parser.SessionToEagle
   */
  public boolean saveSpecctraSessionSesAsEagleScriptScr(
      InputStream inputStream, OutputStream outputStream) {
    if (boardIsReadOnly) {
      return false;
    }
    return app.freerouting.io.specctra.SesReader.saveSpecctraSessionSesAsEagleScriptScr(
        inputStream, outputStream, this.board);
  }

  /**
   * Applies a previously parsed board result and rebinds GUI interactive settings.
   *
   * <p>Extends the base implementation by resetting and rebinding the {@link InteractiveSettings}
   * singleton to the newly loaded board. DSN reading via {@link
   * app.freerouting.io.specctra.DsnReader#readBoard} bypasses {@code create_board} and therefore
   * does not initialise {@code interactiveSettings}; this override guarantees it is always valid
   * and bound to the current board.
   *
   * @param dsnResult the parsed board result to apply
   * @param inputFilename the source filename used for analytics and logging
   * @param analyticsFormat the analytics format label for the load event
   * @return the result of the load operation including success status and any warnings
   * @see HeadlessBoardManager#applyParsedBoardResult
   */
  @Override
  public BoardReadResult applyParsedBoardResult(
      BoardReadResult dsnResult, String inputFilename, String analyticsFormat) {
    BoardReadResult result =
        super.applyParsedBoardResult(dsnResult, inputFilename, analyticsFormat);
    setupGuiAfterBoardLoad(result);
    return result;
  }

  /**
   * Loads a board design from a Specctra DSN format file.
   *
   * <p>Extends the base implementation by scheduling a GUI refresh after a successful load. Calling
   * this method a second time (e.g. to open a new design in the same window) discards the previous
   * {@link InteractiveSettings} instance and creates a fresh one via {@link
   * InteractiveSettings#reset(RoutingBoard)}.
   *
   * @param inputStream the stream containing the DSN data
   * @param boardObservers observers to be notified of board changes
   * @param identificationNumberGenerator generator for assigning unique IDs to board items
   * @return the result of the load operation including success status and any warnings
   * @see HeadlessBoardManager#loadFromSpecctraDsn
   */
  @Override
  public BoardReadResult loadFromSpecctraDsn(
      InputStream inputStream,
      BoardObservers boardObservers,
      IdentificationNumberGenerator identificationNumberGenerator) {
    var result =
        super.loadFromSpecctraDsn(inputStream, boardObservers, identificationNumberGenerator);
    scheduleGuiRefreshAfterLoad(result);
    return result;
  }

  @Override
  public BoardReadResult loadFromKiCadJson(
      InputStream inputStream,
      BoardObservers boardObservers,
      IdentificationNumberGenerator identificationNumberGenerator) {
    var result =
        super.loadFromKiCadJson(inputStream, boardObservers, identificationNumberGenerator);
    scheduleGuiRefreshAfterLoad(result);
    return result;
  }

  private void setupGuiAfterBoardLoad(BoardReadResult result) {
    if (!(result instanceof BoardReadResult.Success
            || result instanceof BoardReadResult.OutlineMissing)
        || this.board == null) {
      return;
    }

    this.interactiveSettings =
        InteractiveSettings.reset(this.board, this.routingJob.routerSettings);
    this.initializeManualTraceHalfWidths();
    this.settingsMerger.addOrReplaceSources(this.interactiveSettings);

    double unitFactor = this.board.communication.coordinateTransform.boardToDsn(1);
    this.coordinateTransform =
        new CoordinateTransform(
            1, this.board.communication.unit, unitFactor, this.board.communication.unit);
    Dimension panelSize = panel != null ? panel.getPreferredSize() : new Dimension(800, 600);
    this.graphicsContext =
        new GraphicsContext(
            this.board.boundingBox, panelSize, this.board.layerStructure, this.locale);
    this.setLayer(0);
  }

  private void scheduleGuiRefreshAfterLoad(BoardReadResult result) {
    if ((result instanceof BoardReadResult.Success
            || result instanceof BoardReadResult.OutlineMissing)
        && this.board != null) {
      javax.swing.SwingUtilities.invokeLater(this::refreshGuiFromSettings);
    }
  }

  /**
   * Saves the currently edited board design to a binary format file.
   *
   * <p>Serializes all board data structures:
   *
   * <ul>
   *   <li>The routing board with all items
   *   <li>Interactive settings
   *   <li>Coordinate transform
   *   <li>Graphics context
   * </ul>
   *
   * <p>Updates the board checksum on successful save for change tracking.
   *
   * @param objectStream the stream to write serialized data to
   * @return true if save succeeded, false if an error occurred
   * @see #loadFromBinary(ObjectInputStream)
   */
  public boolean saveAsBinary(ObjectOutputStream objectStream) {
    boolean result = true;
    try {
      objectStream.writeObject(board);
      objectStream.writeObject(interactiveSettings);
      objectStream.writeObject(coordinateTransform);
      objectStream.writeObject(graphicsContext);

      originalBoardChecksum = calculateCrc32();
    } catch (Exception _) {
      screenMessages.setStatusMessage(tm.getText("save_error"));
      result = false;
    }
    return result;
  }

  /**
   * Closes all currently used files to ensure file buffers are written to disk.
   *
   * <p>Currently a no-op placeholder method. File closing is handled elsewhere or by the Java
   * runtime.
   */
  public void closeFiles() {}

  /**
   * Initiates interactive routing starting from the specified location.
   *
   * <p>Transitions to RouteState, which handles interactive trace routing. The starting location is
   * converted from screen to board coordinates.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param point the starting position in screen coordinates
   * @see RouteState
   */
  public void startRoute(Point2D point) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    InteractiveState newState = RouteState.getInstance(location, this.interactiveState, this);
    setInteractiveState(newState);
  }

  /**
   * Selects board items at the specified screen location.
   *
   * <p>Delegates to the current MenuState to handle item selection at the point. Multiple items at
   * the same location may cycle through selection.
   *
   * <p>This operation requires the interactive state to be a MenuState and is ignored if the board
   * is in read-only mode.
   *
   * @param point the location in screen coordinates where items should be selected
   * @see MenuState#selectItems(FloatPoint)
   */
  public void selectItems(Point2D point) {
    if (boardIsReadOnly || !(this.interactiveState instanceof MenuState)) {
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    InteractiveState returnState = ((MenuState) interactiveState).selectItems(location);
    setInteractiveState(returnState);
  }

  /**
   * Selects all items in the provided collection programmatically.
   *
   * <p>Behavior depends on the current interactive state:
   *
   * <ul>
   *   <li><strong>MenuState:</strong> Transitions to InspectedItemState with the items selected
   *   <li><strong>InspectedItemState:</strong> Adds the items to the existing selection
   * </ul>
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param items the collection of items to select
   * @see InspectedItemState
   */
  public void selectItems(Set<Item> items) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    this.displayLayerMessage();
    if (interactiveState instanceof MenuState) {
      setInteractiveState(InspectedItemState.getInstance(items, interactiveState, this));
    } else if (interactiveState instanceof InspectedItemState state) {
      state.getItemList().clear();
      state.getItemList().addAll(items);
      repaint();
    }
  }

  /**
   * Selects all board items within an interactively defined rectangular region.
   *
   * <p>Initiates a state where the user can drag to define a selection rectangle. All items within
   * or intersecting the rectangle will be selected.
   *
   * <p>This operation requires the interactive state to be a MenuState and is ignored if the board
   * is in read-only mode.
   *
   * @see MenuState
   */
  public void selectItemsInRegion() {
    if (boardIsReadOnly || !(this.interactiveState instanceof MenuState)) {
      return;
    }
    setInteractiveState(InspectItemsInRegionState.getInstance(this.interactiveState, this));
  }

  /**
   * Searches for a swappable pin at the specified location and prepares for pin swap.
   *
   * <p>Pin swapping allows rearranging equivalent pins within a component (e.g., swapping gates in
   * a logic IC). If a swappable pin is found, initiates the pin swap operation.
   *
   * <p>This operation requires the interactive state to be a MenuState and is ignored if the board
   * is in read-only mode.
   *
   * @param location the location in screen coordinates to search for a swappable pin
   * @see MenuState#swapPins(FloatPoint)
   */
  public void swapPins(Point2D location) {
    if (boardIsReadOnly || !(this.interactiveState instanceof MenuState)) {
      return;
    }
    FloatPoint boardLocation = graphicsContext.coordinateTransform.screenToBoard(location);
    InteractiveState returnState = ((MenuState) interactiveState).swapPins(boardLocation);
    setInteractiveState(returnState);
  }

  /**
   * Zooms the display to show all currently selected items.
   *
   * <p>Calculates a bounding box around all selected items (with margins based on trace widths) and
   * adjusts the view to frame them. Useful for quickly navigating to a selection.
   *
   * <p>This operation requires the interactive state to be InspectedItemState.
   *
   * @see BoardPanel#zoomFrame(Point2D, Point2D)
   */
  public void zoomSelection() {
    if (!(interactiveState instanceof InspectedItemState)) {
      return;
    }
    IntBox boundingBox =
        this.board.getBoundingBox(((InspectedItemState) interactiveState).getItemList());
    boundingBox = boundingBox.offset(this.board.rules.getMaxTraceHalfWidth());
    Point2D lowerLeft =
        this.graphicsContext.coordinateTransform.boardToScreen(boundingBox.ll.toFloat());
    Point2D upperRight =
        this.graphicsContext.coordinateTransform.boardToScreen(boundingBox.ur.toFloat());
    this.panel.zoomFrame(lowerLeft, upperRight);
  }

  /**
   * Toggles the selection state of the item at the specified location.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If the item is already selected: removes it from the selection
   *   <li>If the item is not selected: adds it to the selection
   * </ul>
   *
   * <p>This allows building up a multi-item selection by clicking items one at a time.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @param point the location in screen coordinates to pick the item
   * @see InspectedItemState#toggleSelect(FloatPoint)
   */
  public void toggleSelectAction(Point2D point) {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    InteractiveState returnState = ((InspectedItemState) interactiveState).toggleSelect(location);
    if (returnState != this.interactiveState) {
      setInteractiveState(returnState);
      repaint();
    }
  }

  /**
   * Sets the fixed state of selected items to prevent them from being moved or modified.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void fixSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Removes the fixed state from selected items, allowing them to be moved or modified.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void unfixSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Displays detailed information about the selected item in a text window.
   *
   * <p>Shows properties such as net assignment, layer, clearance class, and other item-specific
   * attributes in a dedicated info window.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#info()
   */
  public void displaySelectedItemInfo() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    ((InspectedItemState) interactiveState).info();
  }

  /**
   * Makes all selected items connectable and assigns them to a new net.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void assignSelectedToNewNet() {
    // Editing disabled in inspection mode
  }

  /**
   * Assigns all selected items to a new group (e.g., creating a new component).
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void assignSelectedToNewGroup() {
    // Editing disabled in inspection mode
  }

  /**
   * Deletes all unfixed selected items from the board.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void deleteSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Deletes all unfixed selected traces and vias inside a rectangular region.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void cutoutSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Assigns the specified clearance class to all selected items.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   *
   * @param clearanceClassIndex the clearance class index to assign
   */
  public void assignClearanceClasssToSelectedItems(int clearanceClassIndex) {
    // Editing disabled in inspection mode
  }

  /**
   * Moves or rotates the selected items starting from the specified location.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   *
   * @param fromLocation the starting location for the move/rotate operation
   */
  public void moveSelectedItems(Point2D fromLocation) {
    // Editing disabled in inspection mode
  }

  /**
   * Copies all selected items to a new location.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   *
   * @param fromLocation the starting location for the copy operation
   */
  public void copySelectedItems(Point2D fromLocation) {
    // Editing disabled in inspection mode
  }

  /**
   * Optimizes the routing of selected items (pull-tight, smoothing).
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void optimizeSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Runs the autorouter on selected items only.
   *
   * <p><strong>Note:</strong> This operation is currently disabled in inspection mode. The method
   * is a placeholder for future functionality.
   */
  public void autorouteSelectedItems() {
    // Editing disabled in inspection mode
  }

  /**
   * Starts the autorouter and route optimizer to process the entire board.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Creates a snapshot of the current board state (for undo)
   *   <li>Sets the board to read-only mode to prevent user modifications
   *   <li>Starts a background thread to run autorouting and optimization
   * </ul>
   *
   * <p>The operation runs in a separate thread (InteractiveActionThread), allowing the UI to remain
   * responsive. The user can click to stop the operation.
   *
   * @param job the routing job containing board and router configuration
   * @return the interactive action thread running the autorouter, or null if board is read-only
   * @see InteractiveActionThread
   * @see #stopAutorouterAndRouteOptimizer()
   */
  public InteractiveActionThread startAutorouterAndRouteOptimizer(RoutingJob job) {
    // The auto-router and route optimizer can only be started if the board is not
    // read only
    if (boardIsReadOnly) {
      return null;
    }

    // Generate a snapshot of the board before starting the autorouter
    board.generateSnapshot();

    // Start the auto-router and route optimizer
    // TODO: ideally we should only pass the board and the routerSettings to the
    // thread, and let the thread create the router and optimizer
    this.interactiveActionThread =
        InteractiveActionThread.getAutorouterAndRouteOptimizerInstance(this, job);
    this.interactiveActionThread.start();

    return this.interactiveActionThread;
  }

  /**
   * Stops the currently running autorouter and route optimizer.
   *
   * <p>Requests the background thread to stop and restores the board to interactive mode (not
   * read-only). The operation may not stop immediately if the router is in the middle of routing a
   * connection.
   *
   * @see #startAutorouterAndRouteOptimizer(RoutingJob)
   * @see InteractiveActionThread#requestStop()
   */
  public void stopAutorouterAndRouteOptimizer() {
    if (this.interactiveActionThread != null) {
      // The left button is used to stop the interactive action thread.
      this.interactiveActionThread.requestStop();
    }

    this.setBoardReadOnly(false);
  }

  /**
   * Extends the selection to include all items belonging to the same nets as selected items.
   *
   * <p>Useful for selecting all traces and vias of a net after selecting just one item on that net.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#extentToWholeNets()
   */
  public void extendSelectionToWholeNets() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    setInteractiveState(((InspectedItemState) interactiveState).extentToWholeNets());
  }

  /**
   * Extends the selection to include all items belonging to the same components as selected items.
   *
   * <p>Useful for selecting an entire component (all pads, silkscreen, etc.) after selecting just
   * one pad.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#extentToWholeComponents()
   */
  public void extendSelectionToWholeComponents() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    setInteractiveState(((InspectedItemState) interactiveState).extentToWholeComponents());
  }

  /**
   * Extends the selection to include all items in the same connected sets as selected items.
   *
   * <p>A connected set includes all items electrically connected, possibly spanning multiple nets
   * through components.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#extentToWholeConnectedSets()
   */
  public void extendSelectionToWholeConnectedSets() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    setInteractiveState(((InspectedItemState) interactiveState).extentToWholeConnectedSets());
  }

  /**
   * Extends the selection to include all items in the same connections as selected items.
   *
   * <p>A connection is a routed path between two pins on the same net, including all traces and
   * vias in that path.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#extentToWholeConnections()
   */
  public void extendSelectionToWholeConnections() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    setInteractiveState(((InspectedItemState) interactiveState).extentToWholeConnections());
  }

  /**
   * Toggles the display of clearance violations for selected items only.
   *
   * <p>Shows or hides clearance violations specifically related to the currently selected items,
   * allowing focused inspection of potential design rule violations.
   *
   * <p>This operation requires InspectedItemState and is ignored if the board is in read-only mode.
   *
   * @see InspectedItemState#toggleClearanceViolations()
   */
  public void toggleSelectedItemViolations() {
    if (boardIsReadOnly || !(interactiveState instanceof InspectedItemState)) {
      return;
    }
    ((InspectedItemState) interactiveState).toggleClearanceViolations();
  }

  /**
   * Rotates items being moved by 45 degrees.
   *
   * <p>The rotation direction is determined by factor:
   *
   * <ul>
   *   <li>Positive factor: rotate counter-clockwise
   *   <li>Negative factor: rotate clockwise
   * </ul>
   *
   * <p>This operation requires MoveItemState and is ignored if the board is in read-only mode.
   *
   * @param factor the rotation direction and magnitude
   * @see MoveItemState#turn45Degree(int)
   */
  public void turn45Degree(int factor) {
    if (boardIsReadOnly || !(interactiveState instanceof MoveItemState)) {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactiveState).turn45Degree(factor);
  }

  /**
   * Flips components being moved to the opposite side of the board.
   *
   * <p>Changes component placement from top to bottom side or vice versa, useful for component
   * layout operations.
   *
   * <p>This operation requires MoveItemState and is ignored if the board is in read-only mode.
   *
   * @see MoveItemState#changePlacementSide()
   */
  public void changePlacementSide() {
    if (boardIsReadOnly || !(interactiveState instanceof MoveItemState)) {
      // no interactive action when logfile is running
      return;
    }
    ((MoveItemState) interactiveState).changePlacementSide();
  }

  /**
   * Initiates interactive zoom region selection.
   *
   * <p>Allows the user to drag a rectangle on the screen, then zooms the display to show that
   * rectangular region.
   *
   * @see ZoomRegionState
   */
  public void zoomRegion() {
    interactiveState = ZoomRegionState.getInstance(this.interactiveState, this);
  }

  /**
   * Starts interactive creation of a circular obstacle.
   *
   * <p>Transitions to CircleConstructionState where the user can define the circle's center and
   * radius. Circular obstacles are used for keepouts, mounting holes, or other circular
   * restrictions.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param point the starting position in screen coordinates for the circle center
   * @see CircleConstructionState
   */
  public void startCircle(Point2D point) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    setInteractiveState(CircleConstructionState.getInstance(location, this.interactiveState, this));
  }

  /**
   * Starts interactive creation of a tile-shaped obstacle.
   *
   * <p>Transitions to TileConstructionState where the user can define a rectangular or tile-shaped
   * obstacle. Tiles are used for keepout areas, component outlines, or routing restrictions.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param point the starting position in screen coordinates for the tile
   * @see TileConstructionState
   */
  public void startTile(Point2D point) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    setInteractiveState(TileConstructionState.getInstance(location, this.interactiveState, this));
  }

  /**
   * Starts interactive creation of a polygon-shaped obstacle.
   *
   * <p>Transitions to PolygonShapeConstructionState where the user can define arbitrary polygon
   * shapes by clicking corners. Used for complex keepout areas or irregular obstacles.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param point the starting position in screen coordinates for the first corner
   * @see PolygonShapeConstructionState
   */
  public void startPolygonshapeItem(Point2D point) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    setInteractiveState(
        PolygonShapeConstructionState.getInstance(location, this.interactiveState, this));
  }

  /**
   * Starts interactive addition of a hole to an existing obstacle shape.
   *
   * <p>Transitions to HoleConstructionState where the user can define holes (cutouts) within
   * existing obstacles. Useful for creating complex shapes with interior voids.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param point the starting position in screen coordinates for the hole
   * @see HoleConstructionState
   */
  public void startAddingHole(Point2D point) {
    if (boardIsReadOnly) {
      // no interactive action when logfile is running
      return;
    }
    FloatPoint location = graphicsContext.coordinateTransform.screenToBoard(point);
    InteractiveState newState =
        HoleConstructionState.getInstance(location, this.interactiveState, this);
    setInteractiveState(newState);
  }

  /**
   * Returns the screen rectangle that requires repainting due to recent interactive actions.
   *
   * <p>Calculates the minimal rectangular region on screen that needs to be updated, based on board
   * items that have changed. The rectangle includes a margin for trace widths to ensure complete
   * visual updates.
   *
   * @return the rectangle in screen coordinates that needs repainting
   * @see RoutingBoard#getGraphicsUpdateBox()
   */
  Rectangle getGraphicsUpdateRectangle() {
    Rectangle result;
    IntBox updateBox = board.getGraphicsUpdateBox();
    if (updateBox == null || updateBox.isEmpty()) {
      result = new Rectangle(0, 0, 0, 0);
    } else {
      IntBox offsetBox = updateBox.offset(board.getMaxTraceHalfWidth());
      result = graphicsContext.coordinateTransform.boardToScreen(offsetBox);
    }
    return result;
  }

  /**
   * Finds all board items at the specified location on the active layer.
   *
   * <p>Uses the current item selection filter from interactive settings. If nothing is found on the
   * active layer and selectOnAllVisibleLayers is enabled, searches all visible layers.
   *
   * @param location the position in board coordinates to search
   * @return a set of items at that location (may be empty)
   * @see #pickItems(FloatPoint, ItemSelectionFilter)
   * @see InteractiveSettings#itemSelectionFilter
   */
  Set<Item> pickItems(FloatPoint location) {
    return pickItems(location, interactiveSettings.getItemSelectionFilter());
  }

  /**
   * Finds all board items at the specified location with a custom item filter.
   *
   * <p>Searches the active layer first. If nothing is found and selectOnAllVisibleLayers is
   * enabled, expands the search to all visible layers (excluding the active layer). The item filter
   * determines which item types are considered.
   *
   * @param location the position in board coordinates to search
   * @param itemFilter the filter defining which item types to include
   * @return a set of items matching the filter at that location (may be empty)
   * @see RoutingBoard#pickItems(Point, int, ItemSelectionFilter)
   * @see ItemSelectionFilter
   */
  Set<Item> pickItems(FloatPoint point, ItemSelectionFilter itemFilter) {
    IntPoint location = point.round();
    Set<Item> result = board.pickItems(location, interactiveSettings.getLayer(), itemFilter);
    if (result.isEmpty() && interactiveSettings.getSelectOnAllVisibleLayers()) {
      for (int i = 0; i < graphicsContext.layerCount(); i++) {
        if (i == interactiveSettings.getLayer() || graphicsContext.getLayerVisibility(i) <= 0) {
          continue;
        }
        result.addAll(board.pickItems(location, i, itemFilter));
      }
    }
    return result;
  }

  /**
   * Programmatically moves the mouse cursor to the specified board location.
   *
   * <p>Converts the board coordinates to screen coordinates and moves the system mouse cursor. Used
   * by interactive states to provide visual feedback or guide user attention.
   *
   * <p>This operation is ignored if the board is in read-only mode.
   *
   * @param toLocation the target position in board coordinates
   * @see BoardPanel#moveMouse(Point2D)
   */
  void moveMouse(FloatPoint toLocation) {
    if (!boardIsReadOnly) {
      panel.moveMouse(graphicsContext.coordinateTransform.boardToScreen(toLocation));
    }
  }

  /**
   * Returns the current interactive state (mode) of the board manager.
   *
   * <p>The interactive state determines how user input is interpreted and what operations are
   * available (e.g., select, route, drag, construct).
   *
   * @return the current interactive state
   * @see InteractiveState
   * @see #setInteractiveState(InteractiveState)
   */
  public InteractiveState getInteractiveState() {
    return this.interactiveState;
  }

  /**
   * Sets the current interactive state and updates the toolbar accordingly.
   *
   * <p>Transitions to a new interactive mode if the provided state is different from the current
   * one. The toolbar is updated to reflect the new mode's available operations.
   *
   * <p>Toolbar update is skipped when the board is in read-only mode.
   *
   * @param state the new interactive state to activate
   * @see InteractiveState#setToolbar()
   */
  public void setInteractiveState(InteractiveState state) {
    if (state != null && state != interactiveState) {
      this.interactiveState = state;
      if (!this.boardIsReadOnly) {
        state.setToolbar();
      }
    }
  }

  /**
   * Executes a command produced by an interactive state.
   *
   * <p>When the command is null, cannot execute, or returns null, the current state is kept.
   */
  private InteractiveState executeStateCommand(InteractiveCommand command) {
    if (command == null || !command.canExecute()) {
      return this.interactiveState;
    }
    InteractiveState nextState = command.execute();
    return nextState != null ? nextState : this.interactiveState;
  }

  /** Applies a state transition and optional side effects in one place. */
  private void applyInteractiveStateChange(
      InteractiveState nextState, boolean repaintAfterChange, boolean updateToolbarSelection) {
    if (nextState == null || nextState == this.interactiveState) {
      return;
    }
    setInteractiveState(nextState);
    if (updateToolbarSelection) {
      updateToolbarSelectionPanel();
    }
    if (repaintAfterChange) {
      repaint();
    }
  }

  private void updateToolbarSelectionPanel() {
    if (panel != null && panel.boardFrame != null) {
      panel.boardFrame.setToolbarModeSelectionPanelValue(getInteractiveState());
    }
  }

  /**
   * Adjusts the design bounds to encompass all board items, including those outside the outline.
   *
   * <p>Recalculates the bounding box to include all items on the board, even if they extend beyond
   * the board outline. This ensures the graphics context can properly display all content.
   *
   * <p>Useful after loading designs or when items have been placed outside normal bounds.
   *
   * @see GraphicsContext#changeDesignBounds(IntBox)
   */
  public void adjustDesignBounds() {
    IntBox newBoundingBox = this.board.getBoundingBox();
    Collection<Item> boardItems = this.board.getItems();
    for (Item currItem : boardItems) {
      IntBox currBoundingBox = currItem.boundingBox();
      if (currBoundingBox.ur.x < Integer.MAX_VALUE) {
        newBoundingBox = newBoundingBox.union(currBoundingBox);
      }
    }
    this.graphicsContext.changeDesignBounds(newBoundingBox);
  }

  /**
   * Cleans up resources and prepares the board manager for garbage collection.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Removes event listeners to prevent memory leaks
   *   <li>Closes any open files
   *   <li>Nullifies all major object references to allow garbage collection
   * </ul>
   *
   * <p>Should be called when the board manager is no longer needed.
   */
  public void dispose() {
    FRLogger.getLogEntries().removeLogEntryAddedListener(this.logEntryAddedListener);
    FRLogger.removeTraceEventListener(this.traceEventListener);
    closeFiles();
    graphicsContext = null;
    coordinateTransform = null;
    // Clear the instance field and the static singleton so that a subsequent
    // getOrCreate/reset call (e.g. when reopening the application) starts fresh.
    interactiveSettings = null;
    InteractiveSettings.resetForTesting();
    interactiveState = null;
    ratsnest = null;
    clearanceViolations = null;
    board = null;
  }

  /**
   * Returns the board update strategy for batch operations.
   *
   * <p>The update strategy controls how the board is updated during autorouting and optimization
   * operations.
   *
   * @return the current board update strategy
   * @see BoardUpdateStrategy
   */
  public BoardUpdateStrategy getBoardUpdateStrategy() {
    return boardUpdateStrategy;
  }

  /**
   * Sets the board update strategy for batch operations.
   *
   * @param boardUpdateStrategy the new board update strategy
   * @see BoardUpdateStrategy
   */
  public void setBoardUpdateStrategy(BoardUpdateStrategy boardUpdateStrategy) {
    this.boardUpdateStrategy = boardUpdateStrategy;
  }

  /**
   * Returns the hybrid routing ratio configuration.
   *
   * <p>The hybrid ratio defines the balance between different routing algorithms when using hybrid
   * routing approaches.
   *
   * @return the hybrid ratio string
   */
  public String getHybridRatio() {
    return hybridRatio;
  }

  /**
   * Sets the hybrid routing ratio configuration.
   *
   * @param hybridRatio the hybrid ratio configuration string
   */
  public void setHybridRatio(String hybridRatio) {
    this.hybridRatio = hybridRatio;
  }

  /**
   * Returns the item selection strategy for batch autorouting.
   *
   * <p>The strategy determines which items/nets are selected for routing and in what order during
   * batch operations.
   *
   * @return the current item selection strategy
   * @see ItemSelectionStrategy
   */
  public ItemSelectionStrategy getItemSelectionStrategy() {
    return itemSelectionStrategy;
  }

  /**
   * Sets the item selection strategy for batch autorouting.
   *
   * @param itemSelectionStrategy the new item selection strategy
   * @see ItemSelectionStrategy
   */
  public void setItemSelectionStrategy(ItemSelectionStrategy itemSelectionStrategy) {
    this.itemSelectionStrategy = itemSelectionStrategy;
  }

  /**
   * Returns the number of threads to use for parallel routing operations.
   *
   * <p>If multi-threading is disabled in global settings, this method automatically returns 1 and
   * logs an informational message. This ensures safe operation even if threading is misconfigured.
   *
   * @return the effective number of threads (respecting global settings)
   */
  public int getNumThreads() {
    if ((numThreads > 1) && (!globalSettings.featureFlags.multiThreading)) {
      routingJob.logInfo("Multi-threading is disabled in the settings. Using single thread.");
      numThreads = 1;
    }

    return numThreads;
  }

  /**
   * Sets the number of threads to use for parallel routing operations.
   *
   * <p>The actual number used may be limited by global settings and system capabilities.
   *
   * @param value the number of threads to use (should be >= 1)
   * @see #getNumThreads()
   */
  public void setNumThreads(int value) {
    numThreads = value;
  }

  /**
   * Registers a listener to be notified when the board's read-only state changes.
   *
   * <p>Listeners are typically UI components that need to enable/disable controls based on whether
   * the board is in read-only mode (e.g., during autorouting).
   *
   * @param listener the consumer to notify with the new read-only state (true/false)
   * @see #setBoardReadOnly(boolean)
   */
  public void addReadOnlyEventListener(Consumer<Boolean> listener) {
    readOnlyEventListeners.add(listener);
  }
}
