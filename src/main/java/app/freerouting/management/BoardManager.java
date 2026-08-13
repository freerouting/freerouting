package app.freerouting.management;

import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.rules.BoardRules;

/**
 * Interface defining core board management operations for PCB routing applications.
 *
 * <p>This interface is the <strong>headless</strong> contract for managing routing boards. It is
 * deliberately free of GUI-session operations so that the routing pipeline can be driven without
 * any GUI classes (SoC plan Phase 3). Implementations must provide functionality for board
 * creation, configuration, and access to board state.
 *
 * <p><strong>GUI / Headless duality:</strong>
 *
 * <ul>
 *   <li>In <em>GUI mode</em> ({@link app.freerouting.gui.interactive.GuiBoardManager}) the manager
 *       also implements the {@link app.freerouting.gui.interactive.GuiSessionContract}, which
 *       exposes the GUI-session singleton {@link
 *       app.freerouting.gui.interactive.InteractiveSettings} (the live {@code GuiSettings} source
 *       at SettingsMerger priority 50).
 *   <li>In <em>headless mode</em> ({@link app.freerouting.management.HeadlessBoardManager}) there
 *       is no GUI; the manager does <em>not</em> implement {@code GuiSessionContract}, so {@code
 *       InteractiveSettings} is unreachable. To detect GUI mode, use {@code manager instanceof
 *       app.freerouting.gui.interactive.GuiSessionContract}.
 * </ul>
 *
 * <p><strong>Primary Responsibilities:</strong>
 *
 * <ul>
 *   <li><strong>Board Lifecycle:</strong> Create and initialize routing boards
 *   <li><strong>Configuration:</strong> Manage routing parameters
 *   <li><strong>State Access:</strong> Provide access to board and job state
 *   <li><strong>Coordination:</strong> Bridge between automation and the routing engine
 * </ul>
 *
 * <p><strong>Implementation Classes:</strong>
 *
 * <ul>
 *   <li><strong>{@link app.freerouting.gui.interactive.GuiBoardManager}:</strong> Full-featured
 *       implementation with graphical user interface support, handling user interaction, display
 *       updates, and visual feedback; also implements {@code GuiSessionContract}
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
 *     ? new app.freerouting.gui.interactive.GuiBoardManager(panel, settings, job, merger)
 *     : new app.freerouting.management.HeadlessBoardManager(job);
 *
 * // Initialize board from design file
 * manager.loadFromSpecctraDsn(inputStream, observers, idGenerator);
 *
 * // Access board for routing operations
 * RoutingBoard board = manager.getRoutingBoard();
 *
 * // Access interactive settings only when in GUI mode
 * if (manager instanceof app.freerouting.gui.interactive.GuiSessionContract gui) {
 *     InteractiveSettings settings = gui.getInteractiveSettings();
 * }
 * }</pre>
 *
 * @see app.freerouting.gui.interactive.GuiBoardManager
 * @see app.freerouting.gui.interactive.GuiSessionContract
 * @see app.freerouting.management.HeadlessBoardManager
 * @see RoutingBoard
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
   * <p><strong>Communication Interface:</strong> The {@code boardCommunication} parameter enables
   * integration with host CAD systems, supporting coordinate transformations and unit conversions
   * between the internal board representation and external formats.
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
