package app.freerouting.interactive;

import static app.freerouting.management.gson.GsonProvider.GSON;

import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.designforms.specctra.DsnFile;
import app.freerouting.designforms.specctra.SpecctraSesFileWriter;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages routing board operations in headless (non-GUI) mode for automated processing.
 *
 * <p>This class provides the core board management functionality without requiring a graphical
 * user interface, making it suitable for:
 * <ul>
 *   <li><strong>Batch Processing:</strong> Automated routing of multiple boards</li>
 *   <li><strong>Command-Line Tools:</strong> Server-side or CLI-based routing operations</li>
 *   <li><strong>Testing:</strong> Automated testing of routing algorithms</li>
 *   <li><strong>Integration:</strong> Embedding in other applications or services</li>
 * </ul>
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Board Creation:</strong> Initialize routing boards from design files</li>
 *   <li><strong>File I/O:</strong> Load DSN files and save SES session files</li>
 *   <li><strong>Board State:</strong> Manage board data and detect changes via checksums</li>
 *   <li><strong>Routing Coordination:</strong> Coordinate with routing algorithms and jobs</li>
 *   <li><strong>Settings Management:</strong> Handle interactive settings in headless context</li>
 * </ul>
 *
 * <p><strong>Design Pattern:</strong>
 * This class implements the {@link BoardManager} interface, providing headless-specific
 * implementations while maintaining compatibility with the broader board management
 * architecture. It can be used as a drop-in replacement for {@link GuiBoardManager}
 * when GUI is not needed.
 *
 * <p><strong>Thread Safety:</strong>
 * The {@link #replaceRoutingBoard(RoutingBoard)} method is synchronized to allow
 * thread-safe board replacement during multi-threaded routing operations.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * HeadlessBoardManager manager = new HeadlessBoardManager(routingJob);
 * DsnFile.ReadResult result = manager.loadFromSpecctraDsn(inputStream, observers, idGenerator);
 * if (result == DsnFile.ReadResult.OK) {
 *     // Perform routing operations
 *     manager.saveAsSpecctraSessionSes(outputStream, "design_name");
 * }
 * }</pre>
 *
 * @see BoardManager
 * @see GuiBoardManager
 * @see RoutingBoard
 * @see RoutingJob
 */
public class HeadlessBoardManager implements BoardManager {

  /**
   * Settings for interactive actions, maintained for compatibility with the BoardManager interface.
   *
   * <p><strong>Note:</strong> In headless mode, these settings are primarily used for:
   * <ul>
   *   <li>Default trace width configurations</li>
   *   <li>Manual routing rule selections (when applicable)</li>
   *   <li>Layer and item selection filters</li>
   * </ul>
   *
   * <p><strong>TODO:</strong> The headless manager should not require interactive settings.
   * This dependency should be refactored to use a more appropriate configuration mechanism
   * for non-interactive contexts.
   *
   * @see InteractiveSettings
   */
  public InteractiveSettings interactiveSettings;

  /**
   * Listener for autorouter thread events during automated routing operations.
   *
   * <p>Receives notifications about:
   * <ul>
   *   <li>Routing progress updates</li>
   *   <li>Thread completion or failure</li>
   *   <li>Routing statistics and metrics</li>
   * </ul>
   *
   * <p>Typically used for logging, progress reporting, or coordinating with external systems.
   *
   * @see ThreadActionListener
   * @see InteractiveActionThread
   */
  public ThreadActionListener autorouter_listener;
  /**
   * The routing board containing all PCB design data and routing state.
   *
   * <p><strong>Design Issue:</strong> Board management architecture has redundancy issues:
   * <ul>
   *   <li>{@link BoardManager} holds a board reference</li>
   *   <li>{@link app.freerouting.autoroute.NamedAlgorithm} may hold a board reference</li>
   *   <li>{@link RoutingJob} holds a board reference</li>
   * </ul>
   *
   * <p>These references must be kept synchronized to avoid inconsistencies. This is a known
   * architectural issue that should be addressed in future refactoring.
   *
   * @see RoutingBoard
   */
  protected RoutingBoard board;

  /**
   * The routing job context that orchestrates the routing process.
   *
   * <p>Contains:
   * <ul>
   *   <li>Router settings and algorithm configuration</li>
   *   <li>Logging and error handling</li>
   *   <li>Global settings and feature flags</li>
   *   <li>Analytics and metrics collection</li>
   * </ul>
   *
   * @see RoutingJob
   */
  protected RoutingJob routingJob;

  /**
   * CRC32 checksum of the board in its original/saved state.
   *
   * <p>Used for change detection by comparing against the current board checksum.
   * This allows:
   * <ul>
   *   <li>Detecting unsaved changes before closing</li>
   *   <li>Validating board integrity after operations</li>
   *   <li>Determining if a save operation is needed</li>
   * </ul>
   *
   * <p>The checksum is calculated from the DSN representation of the board,
   * updated after successful load or save operations.
   *
   * @see #calculateCrc32()
   */
  protected long originalBoardChecksum;

  /**
   * Creates a new headless board manager for the specified routing job.
   *
   * <p>The manager is created in an uninitialized state with no board loaded.
   * Call {@link #loadFromSpecctraDsn} or {@link #create_board} to initialize
   * the board before performing routing operations.
   *
   * @param routingJob the routing job context that will orchestrate routing operations
   *
   * @see #loadFromSpecctraDsn(InputStream, BoardObservers, IdentificationNumberGenerator)
   * @see #create_board
   */
  public HeadlessBoardManager(RoutingJob routingJob) {
    this.routingJob = routingJob;
  }

  /**
   * Returns the routing board managed by this instance.
   *
   * <p>The routing board contains all PCB design data including:
   * <ul>
   *   <li>Board outline and layers</li>
   *   <li>Components and their pads</li>
   *   <li>Traces, vias, and routing results</li>
   *   <li>Design rules and net definitions</li>
   * </ul>
   *
   * @return the routing board, or null if no board has been loaded or created
   *
   * @see RoutingBoard
   */
  @Override
  public RoutingBoard get_routing_board() {
    return this.board;
  }

  /**
   * Replaces the current routing board with a new instance in a thread-safe manner.
   *
   * <p>This method is synchronized to prevent race conditions when multiple threads
   * might access or modify the board reference. Typical use cases include:
   * <ul>
   *   <li>Swapping boards during multi-board batch processing</li>
   *   <li>Replacing the board after major structural changes</li>
   *   <li>Testing scenarios requiring board substitution</li>
   * </ul>
   *
   * <p><strong>Warning:</strong> Ensure the new board is compatible with the current
   * routing job settings to avoid inconsistencies.
   *
   * @param newRoutingBoard the new routing board to use (must not be null)
   */
  public synchronized void replaceRoutingBoard(RoutingBoard newRoutingBoard) {
    this.board = newRoutingBoard;
  }

  /**
   * Returns the interactive settings for this board manager.
   *
   * <p>In headless mode, these settings are maintained primarily for compatibility
   * with the {@link BoardManager} interface. They control default trace widths,
   * manual routing rules, and selection filters.
   *
   * @return the interactive settings, or null if not yet initialized
   *
   * @see InteractiveSettings
   */
  @Override
  public InteractiveSettings get_settings() {
    return interactiveSettings;
  }

  /**
   * Initializes manual trace half-widths from the board's default net class rules.
   *
   * <p>This method copies the default trace widths for each layer from the board's
   * default net class into the interactive settings' manual trace width array.
   * This ensures that manual trace width values are available even in headless mode.
   *
   * <p>Should be called after the board is created or loaded to ensure manual
   * trace widths reflect the board's design rules.
   *
   * @see InteractiveSettings#manual_trace_half_width_arr
   * @see app.freerouting.rules.NetClass#get_trace_half_width(int)
   */
  @Override
  public void initialize_manual_trace_half_widths() {
    for (int i = 0; i < interactiveSettings.manual_trace_half_width_arr.length; i++) {
      interactiveSettings.manual_trace_half_width_arr[i] = this.board.rules
          .get_default_net_class()
          .get_trace_half_width(i);
    }
  }

  /**
   * Creates and initializes a new routing board with the specified parameters.
   *
   * <p>This method constructs a new {@link RoutingBoard} from scratch with:
   * <ul>
   *   <li><strong>Geometry:</strong> Bounding box, layer structure, and board outline</li>
   *   <li><strong>Rules:</strong> Design rules including clearances and net definitions</li>
   *   <li><strong>Communication:</strong> Integration with external systems (DSN format)</li>
   * </ul>
   *
   * <p>The outline clearance class is determined from the provided class name or defaults
   * to the area clearance class from the default net class.
   *
   * <p>After board creation, interactive settings are initialized to default values
   * based on the board configuration.
   *
   * <p><strong>Note:</strong> If a board already exists, a warning is logged but the
   * operation proceeds, replacing the existing board.
   *
   * @param p_bounding_box the rectangular boundary of the board
   * @param p_layer_structure the layer stack-up definition
   * @param p_outline_shapes array of shapes defining the board outline
   * @param p_outline_clearance_class_name name of the clearance class for the outline
   * @param p_rules the board design rules and constraints
   * @param p_board_communication communication interface for external integration
   *
   * @see RoutingBoard#RoutingBoard
   * @see InteractiveSettings
   */
  @Override
  public void create_board(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes, String p_outline_clearance_class_name, BoardRules p_rules,
      Communication p_board_communication) {
    if (this.board != null) {
      routingJob.logWarning(" BoardHandling.create_board: board already created");
    }
    int outline_cl_class_no = 0;

    if (p_rules != null) {
      if (p_outline_clearance_class_name != null && p_rules.clearance_matrix != null) {
        outline_cl_class_no = p_rules.clearance_matrix.get_no(p_outline_clearance_class_name);
        outline_cl_class_no = Math.max(outline_cl_class_no, 0);
      } else {
        outline_cl_class_no = p_rules.get_default_net_class().default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
    }
    this.board = new RoutingBoard(p_bounding_box, p_layer_structure, p_outline_shapes, outline_cl_class_no, p_rules, p_board_communication);

    this.interactiveSettings = new InteractiveSettings(this.board);
  }

  /**
   * Returns the current routing job context associated with this board manager.
   *
   * <p>The routing job orchestrates the routing process and provides:
   * <ul>
   *   <li>Router algorithm settings and configuration</li>
   *   <li>Logging and error handling facilities</li>
   *   <li>Global settings and feature flags</li>
   *   <li>Analytics and metrics collection</li>
   * </ul>
   *
   * @return the current routing job, or null if no job is set
   *
   * @see RoutingJob
   */
  @Override
  public RoutingJob getCurrentRoutingJob() {
    return this.routingJob;
  }

  /**
   * Calculates a CRC32 checksum of the current board state.
   *
   * <p>The checksum is computed from the DSN (Specctra Design) representation of the board,
   * which includes:
   * <ul>
   *   <li>Board geometry and layer structure</li>
   *   <li>All placed components and their positions</li>
   *   <li>All routed traces and vias</li>
   *   <li>Design rules and net definitions</li>
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li><strong>Change Detection:</strong> Compare against {@link #originalBoardChecksum}
   *       to detect if the board has been modified</li>
   *   <li><strong>Integrity Verification:</strong> Ensure board data hasn't been corrupted</li>
   *   <li><strong>Version Control:</strong> Track board modifications over time</li>
   * </ul>
   *
   * <p><strong>Performance Note:</strong> This operation serializes the entire board to
   * DSN format in memory, which can be expensive for large boards. Use sparingly.
   *
   * @return the CRC32 checksum value of the board's DSN representation
   *
   * @see #originalBoardChecksum
   * @see BoardFileDetails#calculateCrc32(InputStream)
   */
  public long calculateCrc32() {
    // Create a memory stream
    ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
    DsnFile.write(this, memoryStream, "N/A", false);

    // Transform the output stream to an input stream
    InputStream inputStream = new ByteArrayInputStream(memoryStream.toByteArray());

    return BoardFileDetails
        .calculateCrc32(inputStream)
        .getValue();
  }

  /**
   * Loads a board design from a Specctra DSN (Design) format file.
   *
   * <p>The DSN format is an industry-standard PCB interchange format that describes:
   * <ul>
   *   <li>Board physical structure (layers, outline, stackup)</li>
   *   <li>Component placement and footprints</li>
   *   <li>Net definitions and connectivity</li>
   *   <li>Design rules and constraints</li>
   *   <li>Existing routing (if any)</li>
   * </ul>
   *
   * <p><strong>Loading Process:</strong>
   * <ol>
   *   <li>Parse the DSN file and create board structure</li>
   *   <li>Apply board-specific optimizations to router settings</li>
   *   <li>Reduce/optimize net data structures</li>
   *   <li>Calculate and store initial board checksum</li>
   *   <li>Send analytics about the loaded board</li>
   * </ol>
   *
   * <p><strong>Integration Parameters:</strong>
   * The {@code boardObservers} and {@code identificationNumberGenerator} parameters
   * support embedding Freerouting into host CAD systems, allowing:
   * <ul>
   *   <li>Real-time synchronization of board changes with the host</li>
   *   <li>Consistent item identification across systems</li>
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   * Returns {@link DsnFile.ReadResult#ERROR} if:
   * <ul>
   *   <li>Input stream is null or invalid</li>
   *   <li>DSN file is corrupted or malformed</li>
   *   <li>File format version is not supported</li>
   *   <li>I/O errors occur during reading</li>
   * </ul>
   *
   * <p><strong>Side Effects:</strong>
   * On success, replaces any existing board and updates router settings to match
   * the new board's characteristics (layer count, optimizations).
   *
   * @param inputStream the input stream containing DSN file data (will be closed after reading)
   * @param boardObservers optional observers for board item changes (can be null for standalone use)
   * @param identificationNumberGenerator optional ID generator for board items (can be null)
   * @return the read result indicating success, warnings, or errors
   *
   * @see DsnFile#read
   * @see DsnFile.ReadResult
   * @see BoardObservers
   */
  public DsnFile.ReadResult loadFromSpecctraDsn(InputStream inputStream, BoardObservers boardObservers, IdentificationNumberGenerator identificationNumberGenerator) {
    if (inputStream == null) {
      return DsnFile.ReadResult.ERROR;
    }

    DsnFile.ReadResult read_result;
    try {
      // TODO: we should have a returned object that represent the DSN file, and we should create a RoutingBoard/BasicBoard based on that as a next step
      // we create the board inside the DSN file reader instead at the moment, and save it in the board field of the BoardHandling class
      read_result = DsnFile.read(inputStream, this, boardObservers, identificationNumberGenerator);

      // Apply board-specific optimizations to RouterSettings after board is loaded
      if (read_result == DsnFile.ReadResult.OK && this.board != null && this.routingJob != null) {
        int boardLayerCount = this.board.get_layer_count();
        if (this.routingJob.routerSettings.getLayerCount() != boardLayerCount) {
          this.routingJob.routerSettings.setLayerCount(boardLayerCount);
        }
        // Apply board-specific optimizations for better routing performance
        this.routingJob.routerSettings.applyBoardSpecificOptimizations(this.board);
      }
    } catch (Exception e) {
      read_result = DsnFile.ReadResult.ERROR;
      routingJob.logError("There was an error while reading DSN file.", e);
    }
    if (read_result == DsnFile.ReadResult.OK) {
      var boardStats = new BoardStatistics(this.board);
      FRAnalytics.fileLoaded("DSN", GSON.toJson(boardStats));
      this.board.reduce_nets_of_route_items();
      originalBoardChecksum = calculateCrc32();
      FRAnalytics.boardLoaded(this.board.communication.specctra_parser_info.host_cad, this.board.communication.specctra_parser_info.host_version, this.board.get_layer_count(),
          this.board.components.count(), this.board.rules.nets.max_net_no());
    }

    try {
      inputStream.close();
    } catch (IOException _) {
      read_result = DsnFile.ReadResult.ERROR;
    }
    return read_result;
  }

  /**
   * Writes the routing results to a Specctra SES (Session) format file.
   *
   * <p>The SES format is the companion output format to DSN, containing:
   * <ul>
   *   <li>All routed traces and their exact paths</li>
   *   <li>Via placements and layer transitions</li>
   *   <li>Routing modifications to original design</li>
   *   <li>Completion status and statistics</li>
   * </ul>
   *
   * <p><strong>Typical Workflow:</strong>
   * <ol>
   *   <li>CAD tool exports design as DSN file</li>
   *   <li>Freerouting loads DSN and performs autorouting</li>
   *   <li>Freerouting exports results as SES file</li>
   *   <li>CAD tool imports SES to update design with routing</li>
   * </ol>
   *
   * <p><strong>Post-Save Operations:</strong>
   * On successful save:
   * <ul>
   *   <li>Updates {@link #originalBoardChecksum} to reflect the saved state</li>
   *   <li>Marks the board as having no unsaved changes</li>
   * </ul>
   *
   * <p><strong>Note:</strong> The output stream is NOT closed by this method.
   * The caller is responsible for closing it.
   *
   * @param outputStream the stream to write SES data to (caller must close)
   * @param designName the design name to include in the SES file header
   * @return true if save was successful, false if an error occurred
   *
   * @see SpecctraSesFileWriter#write
   * @see #loadFromSpecctraDsn
   */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    boolean wasSaveSuccessful = SpecctraSesFileWriter.write(this.get_routing_board(), outputStream, designName);

    if (wasSaveSuccessful) {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

}