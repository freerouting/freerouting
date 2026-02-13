package app.freerouting.interactive;

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
 * <p>This interface establishes the contract for managing routing boards in both interactive
 * (GUI-based) and headless (batch/automated) modes. Implementations must provide functionality
 * for board creation, configuration, and access to board state.
 *
 * <p><strong>Primary Responsibilities:</strong>
 * <ul>
 *   <li><strong>Board Lifecycle:</strong> Create and initialize routing boards</li>
 *   <li><strong>Configuration:</strong> Manage interactive settings and routing parameters</li>
 *   <li><strong>State Access:</strong> Provide access to board and job state</li>
 *   <li><strong>Coordination:</strong> Bridge between UI/automation and routing engine</li>
 * </ul>
 *
 * <p><strong>Implementation Classes:</strong>
 * <ul>
 *   <li><strong>{@link GuiBoardManager}:</strong> Full-featured implementation with graphical
 *       user interface support, handling user interaction, display updates, and visual feedback</li>
 *   <li><strong>{@link HeadlessBoardManager}:</strong> Lightweight implementation for
 *       batch processing, command-line tools, and automated routing without GUI overhead</li>
 * </ul>
 *
 * <p><strong>Typical Usage Pattern:</strong>
 * <pre>{@code
 * // Create appropriate manager based on mode
 * BoardManager manager = isGuiMode
 *     ? new GuiBoardManager(panel, settings, job, merger)
 *     : new HeadlessBoardManager(job);
 *
 * // Initialize board from design file
 * manager.loadFromSpecctraDsn(inputStream, observers, idGenerator);
 *
 * // Access board for routing operations
 * RoutingBoard board = manager.get_routing_board();
 *
 * // Configure settings
 * manager.initialize_manual_trace_half_widths();
 * }</pre>
 *
 * <p><strong>Design Considerations:</strong>
 * The interface is intentionally minimal to support diverse implementations while ensuring
 * compatibility between interactive and headless modes. This allows the same routing algorithms
 * to work seamlessly regardless of execution context.
 *
 * @see GuiBoardManager
 * @see HeadlessBoardManager
 * @see RoutingBoard
 * @see InteractiveSettings
 */
public interface BoardManager {

  /**
   * Returns the routing board managed by this instance.
   *
   * <p>The routing board contains all PCB design data including:
   * <ul>
   *   <li>Physical board structure (outline, layers, stackup)</li>
   *   <li>Components and their pads/pins</li>
   *   <li>Nets and connectivity information</li>
   *   <li>Traces, vias, and routing results</li>
   *   <li>Design rules and constraints</li>
   * </ul>
   *
   * <p>This is the primary interface for routing algorithms to access and modify
   * the board design.
   *
   * @return the routing board instance, or null if no board has been created/loaded
   *
   * @see RoutingBoard
   */
  RoutingBoard get_routing_board();

  /**
   * Initializes manual trace half-widths from the board's default net class rules.
   *
   * <p>This method synchronizes the interactive settings' manual trace width array
   * with the default trace widths defined in the board's design rules. This ensures
   * that manual routing operations use appropriate trace widths when manual rule
   * selection is active.
   *
   * <p><strong>When to Call:</strong>
   * <ul>
   *   <li>After loading a board from a design file</li>
   *   <li>After creating a new board programmatically</li>
   *   <li>When switching between boards</li>
   *   <li>After modifying default net class trace widths</li>
   * </ul>
   *
   * <p>The method copies trace half-widths for each layer from the default net class
   * to the manual trace width settings array.
   *
   * @see InteractiveSettings#manual_trace_half_width_arr
   * @see app.freerouting.rules.NetClass#get_trace_half_width(int)
   */
  void initialize_manual_trace_half_widths();

  /**
   * Creates and initializes a new routing board with the specified parameters.
   *
   * <p>This method constructs a routing board from scratch using the provided
   * geometric and rule definitions. It is typically called when:
   * <ul>
   *   <li>Creating a new blank board for manual design</li>
   *   <li>Importing board structure from a non-DSN format</li>
   *   <li>Programmatically generating test boards</li>
   * </ul>
   *
   * <p><strong>Board Creation Process:</strong>
   * <ol>
   *   <li>Initialize board geometry (bounding box, layers)</li>
   *   <li>Create board outline from polyline shapes</li>
   *   <li>Set up design rules and clearance classes</li>
   *   <li>Configure communication interface for external integration</li>
   *   <li>Initialize interactive settings to defaults</li>
   * </ol>
   *
   * <p><strong>Outline Clearance:</strong>
   * The {@code p_outline_clearance_class_name} parameter specifies which clearance
   * class to use for the board outline. If the name doesn't match an existing class,
   * the default area clearance class is used.
   *
   * <p><strong>Communication Interface:</strong>
   * The {@code p_board_communication} parameter enables integration with host CAD
   * systems, supporting coordinate transformations and unit conversions between
   * the internal board representation and external formats.
   *
   * @param p_bounding_box the rectangular boundary containing all board geometry
   * @param p_layer_structure the layer stack-up definition (names, types, thicknesses)
   * @param p_outline_shapes array of polyline shapes defining the board outline perimeter
   * @param p_outline_clearance_class_name name of clearance class for board outline
   * @param p_rules the complete set of design rules (clearances, widths, via rules)
   * @param p_board_communication communication interface for external system integration
   *
   * @see RoutingBoard#RoutingBoard
   * @see LayerStructure
   * @see BoardRules
   * @see Communication
   */
  void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
      String p_outline_clearance_class_name, BoardRules p_rules,
      Communication p_board_communication);

  /**
   * Returns the interactive settings that control routing behavior and user preferences.
   *
   * <p>Interactive settings include:
   * <ul>
   *   <li><strong>Layer settings:</strong> Current active layer, layer visibility</li>
   *   <li><strong>Trace widths:</strong> Manual trace width overrides per layer</li>
   *   <li><strong>Selection filters:</strong> Which item types can be selected</li>
   *   <li><strong>Routing modes:</strong> Manual vs. automatic rule selection</li>
   *   <li><strong>Via preferences:</strong> Preferred via types and rules</li>
   * </ul>
   *
   * <p><strong>Note:</strong> Even in headless mode, these settings may be used to
   * control routing behavior, though many interactive-specific settings are not
   * relevant without a GUI.
   *
   * @return the interactive settings instance, or null if not initialized
   *
   * @see InteractiveSettings
   */
  Settings get_settings();

  /**
   * Returns the current routing job context associated with this board manager.
   *
   * <p>The routing job provides the execution context for routing operations,
   * including:
   * <ul>
   *   <li><strong>Algorithm configuration:</strong> Router settings and parameters</li>
   *   <li><strong>Logging:</strong> Error and information logging facilities</li>
   *   <li><strong>Global settings:</strong> Feature flags and system preferences</li>
   *   <li><strong>Analytics:</strong> Metrics collection and reporting</li>
   *   <li><strong>Progress tracking:</strong> Job status and completion monitoring</li>
   * </ul>
   *
   * <p>The routing job acts as the orchestrator for automated routing operations,
   * coordinating between the board manager, routing algorithms, and external systems.
   *
   * @return the current routing job, or null if no job is set
   *
   * @see RoutingJob
   */
  RoutingJob getCurrentRoutingJob();
}