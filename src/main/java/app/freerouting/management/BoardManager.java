package app.freerouting.management;

import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.settings.sources.GuiSettings;

/**
 * Interface defining core board management operations for PCB routing applications.
 *
 * <p>This interface establishes the contract for managing routing boards in both interactive
 * (GUI-based) and headless (batch/automated) modes. Implementations must provide functionality for
 * board creation, configuration, and access to board state.
 *
 * <p><strong>GUI / Headless duality:</strong>
 *
 * <ul>
 *   <li>In <em>GUI mode</em> ({@link app.freerouting.interactive.GuiBoardManager}) the {@link
 *       app.freerouting.interactive.InteractiveSettings} singleton is always non-null and also acts
 *       as the {@link app.freerouting.settings.sources.GuiSettings} source (priority 65) registered
 *       in the {@link app.freerouting.settings.SettingsMerger} pipeline. Use {@link
 *       #getInteractiveSettings()} to obtain it.
 *   <li>In <em>headless mode</em> ({@link app.freerouting.management.HeadlessBoardManager}) there
 *       is no GUI; therefore {@link #getInteractiveSettings()} returns {@code null} and {@link
 *       #isInteractiveModeSupported()} returns {@code false}.
 * </ul>
 *
 * <p><strong>Settings pipeline (GUI mode only):</strong>
 *
 * <pre>
 * InteractiveSettings  →  GuiSettings.getSettings()  →  SettingsMerger  →  RouterSettings
 * </pre>
 *
 * <p><strong>Primary Responsibilities:</strong>
 *
 * <ul>
 *   <li><strong>Board Lifecycle:</strong> Create and initialize routing boards
 *   <li><strong>Configuration:</strong> Manage interactive settings and routing parameters
 *   <li><strong>State Access:</strong> Provide access to board and job state
 *   <li><strong>Coordination:</strong> Bridge between UI/automation and routing engine
 * </ul>
 *
 * <p><strong>Implementation Classes:</strong>
 *
 * <ul>
 *   <li><strong>{@link app.freerouting.interactive.GuiBoardManager}:</strong> Full-featured
 *       implementation with graphical user interface support, handling user interaction, display
 *       updates, and visual feedback
 *   <li><strong>{@link app.freerouting.management.HeadlessBoardManager}:</strong> Lightweight
 *       implementation for batch processing, command-line tools, and automated routing without GUI
 *       overhead
 * </ul>
 *
 * <p><strong>Typical Usage Pattern:</strong>
 *
 * <pre>{@code
 * // Create appropriate manager based on mode
 * BoardManager manager = isGuiMode
 *     ? new app.freerouting.interactive.GuiBoardManager(panel, settings, job, merger)
 *     : new app.freerouting.management.HeadlessBoardManager(job);
 *
 * // Initialize board from design file
 * manager.loadFromSpecctraDsn(inputStream, observers, idGenerator);
 *
 * // Access board for routing operations
 * RoutingBoard board = manager.get_routing_board();
 *
 * // Access interactive settings only when in GUI mode
 * if (manager.isInteractiveModeSupported()) {
 *     InteractiveSettings settings = manager.getInteractiveSettings();
 * }
 * }</pre>
 *
 * @see app.freerouting.interactive.GuiBoardManager
 * @see app.freerouting.management.HeadlessBoardManager
 * @see RoutingBoard
 * @see app.freerouting.interactive.InteractiveSettings
 */
public interface BoardManager {

  /**
   * Returns the routing board managed by this instance.
   *
   * <p>The routing board contains all PCB design data including:
   *
   * <ul>
   *   <li>Physical board structure (outline, layers, stackup)
   *   <li>Components and their pads/pins
   *   <li>Nets and connectivity information
   *   <li>Traces, vias, and routing results
   *   <li>Design rules and constraints
   * </ul>
   *
   * <p>This is the primary interface for routing algorithms to access and modify the board design.
   *
   * @return the routing board instance, or null if no board has been created/loaded
   * @see RoutingBoard
   */
  RoutingBoard getRoutingBoard();

  /**
   * Initializes manual trace half-widths from the board's default net class rules.
   *
   * <p>This method synchronizes the interactive settings' manual trace width array with the default
   * trace widths defined in the board's design rules. This ensures that manual routing operations
   * use appropriate trace widths when manual rule selection is active.
   *
   * <p><strong>When to Call:</strong>
   *
   * <ul>
   *   <li>After loading a board from a design file
   *   <li>After creating a new board programmatically
   *   <li>When switching between boards
   *   <li>After modifying default net class trace widths
   * </ul>
   *
   * <p>The method copies trace half-widths for each layer from the default net class to the manual
   * trace width settings array.
   *
   * @see app.freerouting.interactive.InteractiveSettings#manualTraceHalfWidthArr
   * @see app.freerouting.rules.NetClass#getTraceHalfWidth(int)
   */
  void initializeManualTraceHalfWidths();

  /**
   * Creates and initializes a new routing board with the specified parameters.
   *
   * <p>This method constructs a routing board from scratch using the provided geometric and rule
   * definitions. It is typically called when:
   *
   * <ul>
   *   <li>Creating a new blank board for manual design
   *   <li>Importing board structure from a non-DSN format
   *   <li>Programmatically generating test boards
   * </ul>
   *
   * <p><strong>Board Creation Process:</strong>
   *
   * <ol>
   *   <li>Initialize board geometry (bounding box, layers)
   *   <li>Create board outline from polyline shapes
   *   <li>Set up design rules and clearance classes
   *   <li>Configure communication interface for external integration
   *   <li>Initialize interactive settings to defaults
   * </ol>
   *
   * <p><strong>Outline Clearance:</strong> The {@code outlineClearanceClassName} parameter
   * specifies which clearance class to use for the board outline. If the name doesn't match an
   * existing class, the default area clearance class is used.
   *
   * <p><strong>Communication Interface:</strong> The {@code boardCommunication} parameter
   * enables integration with host CAD systems, supporting coordinate transformations and unit
   * conversions between the internal board representation and external formats.
   *
   * @param boundingBox the rectangular boundary containing all board geometry
   * @param layerStructure the layer stack-up definition (names, types, thicknesses)
   * @param outlineShapes array of polyline shapes defining the board outline perimeter
   * @param outlineClearanceClassName name of clearance class for board outline
   * @param rules the complete set of design rules (clearances, widths, via rules)
   * @param boardCommunication communication interface for external system integration
   * @see RoutingBoard#RoutingBoard
   * @see LayerStructure
   * @see BoardRules
   * @see Communication
   */
  void createBoard(
      IntBox boundingBox,
      LayerStructure layerStructure,
      PolylineShape[] outlineShapes,
      String outlineClearanceClassName,
      BoardRules rules,
      Communication boardCommunication);

  /**
   * Returns the interactive GUI settings singleton, or {@code null} when running headless.
   *
   * <p>The returned {@link app.freerouting.interactive.InteractiveSettings} instance is also the
   * {@link app.freerouting.settings.sources.GuiSettings} source registered in the {@link
   * app.freerouting.settings.SettingsMerger} at priority 65. Callers must not cache this reference;
   * always obtain it through this accessor.
   *
   * <p>Check {@link #isInteractiveModeSupported()} before calling if you are unsure which
   * implementation is in use.
   *
   * @return the singleton {@link app.freerouting.interactive.InteractiveSettings}, or {@code null}
   *     in headless mode
   */
  GuiSettings getInteractiveSettings();

  /**
   * Returns {@code true} if this manager runs with an active GUI and therefore guarantees that
   * {@link #getInteractiveSettings()} returns a non-null value after board initialisation.
   *
   * <p>Defaults to {@code false}. Only {@link app.freerouting.interactive.GuiBoardManager}
   * overrides this to {@code true}.
   *
   * @return {@code true} when a GUI is active; {@code false} in headless mode
   */
  default boolean isInteractiveModeSupported() {
    return false;
  }

  /**
   * Returns the interactive settings that control routing behavior and user preferences.
   *
   * @return the interactive settings instance, or {@code null} if not initialized / headless
   * @deprecated Use {@link #getInteractiveSettings()} instead.
   */
  @Deprecated
  default GuiSettings getSettings() {
    return getInteractiveSettings();
  }

  /**
   * Returns the current routing job context associated with this board manager.
   *
   * <p>The routing job provides the execution context for routing operations, including:
   *
   * <ul>
   *   <li><strong>Algorithm configuration:</strong> Router settings and parameters
   *   <li><strong>Logging:</strong> Error and information logging facilities
   *   <li><strong>Global settings:</strong> Feature flags and system preferences
   *   <li><strong>Analytics:</strong> Metrics collection and reporting
   *   <li><strong>Progress tracking:</strong> Job status and completion monitoring
   * </ul>
   *
   * <p>The routing job acts as the orchestrator for automated routing operations, coordinating
   * between the board manager, routing algorithms, and external systems.
   *
   * @return the current routing job, or null if no job is set
   * @see RoutingJob
   */
  RoutingJob getCurrentRoutingJob();
}
