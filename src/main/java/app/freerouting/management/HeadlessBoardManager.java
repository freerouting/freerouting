package app.freerouting.management;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.board.BoardObservers;
import app.freerouting.board.Communication;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Unit;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.IdentificationNumberGenerator;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnWriter;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages routing board operations in headless (non-GUI) mode for automated processing.
 *
 * <p>This class provides the core board management functionality without requiring a graphical user
 * interface, making it suitable for:
 *
 * <ul>
 *   <li><strong>Batch Processing:</strong> Automated routing of multiple boards
 *   <li><strong>Command-Line Tools:</strong> Server-side or CLI-based routing operations
 *   <li><strong>Testing:</strong> Automated testing of routing algorithms
 *   <li><strong>Integration:</strong> Embedding in other applications or services
 * </ul>
 *
 * <p><strong>Key Responsibilities:</strong>
 *
 * <ul>
 *   <li><strong>Board Creation:</strong> Initialize routing boards from design files
 *   <li><strong>File I/O:</strong> Load DSN files and save SES session files
 *   <li><strong>Board State:</strong> Manage board data and detect changes via checksums
 *   <li><strong>Routing Coordination:</strong> Coordinate with routing algorithms and jobs
 *   <li><strong>Settings Management:</strong> Handle interactive settings in headless context
 * </ul>
 *
 * <p><strong>Design Pattern:</strong> This class implements the {@link BoardManager} interface,
 * providing headless-specific implementations while maintaining compatibility with the broader
 * board management architecture. It can be used as a drop-in replacement for {@link
 * app.freerouting.interactive.GuiBoardManager} when GUI is not needed.
 *
 * <p><strong>Thread Safety:</strong> The {@link #replaceRoutingBoard(RoutingBoard)} method is
 * synchronized to allow thread-safe board replacement during multi-threaded routing operations.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * HeadlessBoardManager manager = new HeadlessBoardManager(routingJob);
 * BoardReadResult result = manager.loadFromSpecctraDsn(inputStream, observers, idGenerator);
 * if (result instanceof BoardReadResult.Success) {
 *     // Perform routing operations
 *     manager.saveAsSpecctraSessionSes(outputStream, "designName");
 * }
 * }</pre>
 *
 * @see BoardManager
 * @see app.freerouting.interactive.GuiBoardManager
 * @see RoutingBoard
 * @see RoutingJob
 */
public class HeadlessBoardManager implements BoardManager {

  private static final String BOARD_EDGE_CLEARANCE_CLASS_NAME = "board_edge";
  private static final String HOLE_EDGE_CLEARANCE_CLASS_NAME = "hole_edge";

  /**
   * Listener for autorouter thread events during automated routing operations.
   *
   * <p>Receives notifications about:
   *
   * <ul>
   *   <li>Routing progress updates
   *   <li>Thread completion or failure
   *   <li>Routing statistics and metrics
   * </ul>
   *
   * <p>Typically used for logging, progress reporting, or coordinating with external systems.
   *
   * @see ThreadActionListener
   * @see app.freerouting.interactive.InteractiveActionThread
   */
  public ThreadActionListener autorouterListener;

  /**
   * The routing board containing all PCB design data and routing state.
   *
   * <p><strong>Design Issue:</strong> Board management architecture has redundancy issues:
   *
   * <ul>
   *   <li>{@link BoardManager} holds a board reference
   *   <li>{@link app.freerouting.autoroute.NamedAlgorithm} may hold a board reference
   *   <li>{@link RoutingJob} holds a board reference
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
   *
   * <ul>
   *   <li>Router settings and algorithm configuration
   *   <li>Logging and error handling
   *   <li>Global settings and feature flags
   *   <li>Analytics and metrics collection
   * </ul>
   *
   * @see RoutingJob
   */
  protected RoutingJob routingJob;

  /**
   * CRC32 checksum of the board in its original/saved state.
   *
   * <p>Used for change detection by comparing against the current board checksum. This allows:
   *
   * <ul>
   *   <li>Detecting unsaved changes before closing
   *   <li>Validating board integrity after operations
   *   <li>Determining if a save operation is needed
   * </ul>
   *
   * <p>The checksum is calculated from the DSN representation of the board, updated after
   * successful load or save operations.
   *
   * @see #calculateCrc32()
   */
  protected long originalBoardChecksum;

  /**
   * Creates a new headless board manager for the specified routing job.
   *
   * <p>The manager is created in an uninitialized state with no board loaded. Call {@link
   * #loadFromSpecctraDsn} or {@link #createBoard} to initialize the board before performing routing
   * operations.
   *
   * @param routingJob the routing job context that will orchestrate routing operations
   * @see #loadFromSpecctraDsn(InputStream, BoardObservers, IdentificationNumberGenerator)
   * @see #createBoard
   */
  public HeadlessBoardManager(RoutingJob routingJob) {
    this.routingJob = routingJob;
  }

  /**
   * Returns the routing board managed by this instance.
   *
   * <p>The routing board contains all PCB design data including:
   *
   * <ul>
   *   <li>Board outline and layers
   *   <li>Components and their pads
   *   <li>Traces, vias, and routing results
   *   <li>Design rules and net definitions
   * </ul>
   *
   * @return the routing board, or null if no board has been loaded or created
   * @see RoutingBoard
   */
  @Override
  public RoutingBoard getRoutingBoard() {
    return this.board;
  }

  /**
   * Replaces the current routing board with a new instance in a thread-safe manner.
   *
   * <p>This method is synchronized to prevent race conditions when multiple threads might access or
   * modify the board reference. Typical use cases include:
   *
   * <ul>
   *   <li>Swapping boards during multi-board batch processing
   *   <li>Replacing the board after major structural changes
   *   <li>Testing scenarios requiring board substitution
   * </ul>
   *
   * <p><strong>Warning:</strong> Ensure the new board is compatible with the current routing job
   * settings to avoid inconsistencies.
   *
   * @param newRoutingBoard the new routing board to use (must not be null)
   */
  public synchronized void replaceRoutingBoard(RoutingBoard newRoutingBoard) {
    this.board = newRoutingBoard;
  }

  /**
   * Creates and initializes a new routing board with the specified parameters.
   *
   * <p>This method constructs a new {@link RoutingBoard} from scratch with:
   *
   * <ul>
   *   <li><strong>Geometry:</strong> Bounding box, layer structure, and board outline
   *   <li><strong>Rules:</strong> Design rules including clearances and net definitions
   *   <li><strong>Communication:</strong> Integration with external systems (DSN format)
   * </ul>
   *
   * <p>The outline clearance class is determined from the provided class name or defaults to the
   * area clearance class from the default net class.
   *
   * <p>After board creation, interactive settings are initialized to default values based on the
   * board configuration.
   *
   * <p><strong>Note:</strong> If a board already exists, a warning is logged but the operation
   * proceeds, replacing the existing board.
   *
   * @param boundingBox the rectangular boundary of the board
   * @param layerStructure the layer stack-up definition
   * @param outlineShapes array of shapes defining the board outline
   * @param outlineClearanceClassName name of the clearance class for the outline
   * @param rules the board design rules and constraints
   * @param boardCommunication communication interface for external integration
   * @see RoutingBoard#RoutingBoard
   * @see app.freerouting.interactive.InteractiveSettings
   */
  @Override
  public void createBoard(
      IntBox boundingBox,
      LayerStructure layerStructure,
      PolylineShape[] outlineShapes,
      String outlineClearanceClassName,
      BoardRules rules,
      Communication boardCommunication) {
    if (this.board != null) {
      routingJob.logWarning(" BoardHandling.create_board: board already created");
    }
    int outlineClClassNo = 0;

    if (rules != null) {
      if (outlineClearanceClassName != null && rules.clearanceMatrix != null) {
        outlineClClassNo = rules.clearanceMatrix.getNo(outlineClearanceClassName);
        outlineClClassNo = Math.max(outlineClClassNo, 0);
      } else {
        outlineClClassNo =
            rules
                .getDefaultNetClass()
                .defaultItemClearanceClasses
                .get(DefaultItemClearanceClasses.ItemClass.AREA);
      }
    }
    this.board =
        new RoutingBoard(
            boundingBox,
            layerStructure,
            outlineShapes,
            outlineClClassNo,
            rules,
            boardCommunication);
    applyCopperToEdgeClearanceOverride();
    applyHoleClearanceOverride();
  }

  private void applyHoleClearanceOverride() {
    if (this.board == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.holeClearanceUm == null) {
      return;
    }

    double configuredClearanceUm = this.routingJob.routerSettings.holeClearanceUm;
    if (configuredClearanceUm < 0) {
      FRLogger.warn(
          "Ignoring router.hole_clearance_um because it is negative: " + configuredClearanceUm);
      return;
    }
    if (this.board.rules == null) {
      FRLogger.warn("Ignoring router.hole_clearance_um because board rules are unavailable.");
      return;
    }

    int boardResolution = Math.max(1, this.board.communication.resolution);
    int configuredClearanceBoardUnits =
        (int)
            Math.round(
                Unit.scale(
                    configuredClearanceUm * boardResolution,
                    Unit.UM,
                    this.board.communication.unit));
    boolean changed = configuredClearanceBoardUnits != this.board.rules.getHoleClearance();
    this.board.rules.setHoleClearance(configuredClearanceBoardUnits);
    int holeKeepouts = 0;
    if (configuredClearanceBoardUnits > 0) {
      holeKeepouts = assignHoleKeepoutClearanceClass(configuredClearanceBoardUnits);
    }
    if (changed || holeKeepouts > 0) {
      // Tree shapes are precalculated at insert time; items loaded before the override
      // (all of them, on a DSN load) must be re-inserted so their obstacle shapes include
      // the drill-hole inflation. Otherwise DRC (default tree) under-reports and routing
      // trees created later disagree with it.
      this.board.searchTreeManager.reinsertTreeItems();
    }

    if (configuredClearanceBoardUnits > 0) {
      FRLogger.info(
          "Applied drill-hole clearance override: "
              + configuredClearanceUm
              + " um ("
              + configuredClearanceBoardUnits
              + " board units)"
              + (holeKeepouts > 0 ? ", " + holeKeepouts + " hole keepouts reclassified." : "."));
    }
  }

  /**
   * KiCad's DSN export represents non-plated holes as circular per-copper-layer package keepouts,
   * which by default only get the generic AREA copper clearance. Assign those circle keepouts to a
   * dedicated "hole_edge" clearance class so other-net copper keeps hole clearance (not just copper
   * clearance) from the hole boundary. Returns the number of keepouts reclassified.
   */
  private int assignHoleKeepoutClearanceClass(int holeClearanceBoardUnits) {
    var matrix = this.board.rules.clearanceMatrix;
    if (matrix == null) {
      return 0;
    }
    java.util.List<app.freerouting.board.ObstacleArea> holeKeepouts = new java.util.ArrayList<>();
    for (app.freerouting.board.Item item : this.board.getItems()) {
      if (item.getClass() != app.freerouting.board.ObstacleArea.class) {
        continue;
      }
      app.freerouting.board.ObstacleArea keepout = (app.freerouting.board.ObstacleArea) item;
      // Package keepouts belong to a component; a circular one is a drilled hole in the
      // footprint (the only way KiCad expresses NPTH in DSN).
      if (keepout.getComponentNo() > 0
          && keepout.getArea() instanceof app.freerouting.geometry.planar.Circle) {
        holeKeepouts.add(keepout);
      }
    }
    if (holeKeepouts.isEmpty()) {
      return 0;
    }
    int holeEdgeClassNo = matrix.getNo(HOLE_EDGE_CLEARANCE_CLASS_NAME);
    if (holeEdgeClassNo < 0) {
      matrix.appendClass(HOLE_EDGE_CLEARANCE_CLASS_NAME);
      holeEdgeClassNo = matrix.getNo(HOLE_EDGE_CLEARANCE_CLASS_NAME);
    }
    if (holeEdgeClassNo < 0) {
      FRLogger.warn(
          "Unable to create/find the hole_edge clearance class for the hole clearance override.");
      return 0;
    }
    int defaultAreaClassNo =
        this.board
            .rules
            .getDefaultNetClass()
            .defaultItemClearanceClasses
            .get(DefaultItemClearanceClasses.ItemClass.AREA);
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      for (int classNo = 1; classNo < matrix.getClassCount(); classNo++) {
        // Never reduce an existing requirement: hole clearance is a floor on top of the
        // normal copper clearance the keepout would otherwise get.
        int value =
            Math.max(
                holeClearanceBoardUnits,
                matrix.getValue(defaultAreaClassNo, classNo, layer, false));
        matrix.setValue(holeEdgeClassNo, classNo, layer, value);
        matrix.setValue(classNo, holeEdgeClassNo, layer, value);
      }
    }
    int reclassified = 0;
    for (app.freerouting.board.ObstacleArea keepout : holeKeepouts) {
      if (keepout.clearanceClassNo() != holeEdgeClassNo) {
        keepout.setClearanceClassNo(holeEdgeClassNo);
        keepout.clearDerivedData();
        reclassified++;
      }
    }
    return reclassified;
  }

  private void applyCopperToEdgeClearanceOverride() {
    if (this.board == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.copperToEdgeClearanceUm == null) {
      return;
    }

    double configuredClearanceUm = this.routingJob.routerSettings.copperToEdgeClearanceUm;
    if (configuredClearanceUm < 0) {
      FRLogger.warn(
          "Ignoring router.copper_to_edge_clearance_um because it is negative: "
              + configuredClearanceUm);
      return;
    }
    if (this.board.rules == null || this.board.rules.clearanceMatrix == null) {
      FRLogger.warn(
          "Ignoring router.copper_to_edge_clearance_um because board rules are unavailable.");
      return;
    }

    var outline = this.board.getOutline();
    if (outline == null) {
      FRLogger.warn(
          "Ignoring router.copper_to_edge_clearance_um because the board outline is unavailable.");
      return;
    }

    int defaultAreaClassNo =
        this.board
            .rules
            .getDefaultNetClass()
            .defaultItemClearanceClasses
            .get(DefaultItemClearanceClasses.ItemClass.AREA);
    boolean usesFallbackOutlineClass = outline.clearanceClassNo() == defaultAreaClassNo;
    boolean usesDefaultEdgeClearanceValue =
        Math.abs(configuredClearanceUm - DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM)
            < 1e-9;
    // Keep explicit DSN outline-clearance classes untouched when only the global default is active.
    if (usesDefaultEdgeClearanceValue && !usesFallbackOutlineClass) {
      return;
    }

    int boardResolution = Math.max(1, this.board.communication.resolution);
    int configuredClearanceBoardUnits =
        (int)
            Math.round(
                Unit.scale(
                    configuredClearanceUm * boardResolution,
                    Unit.UM,
                    this.board.communication.unit));

    var matrix = this.board.rules.clearanceMatrix;
    int boardEdgeClassNo = matrix.getNo(BOARD_EDGE_CLEARANCE_CLASS_NAME);
    if (boardEdgeClassNo < 0) {
      matrix.appendClass(BOARD_EDGE_CLEARANCE_CLASS_NAME);
      boardEdgeClassNo = matrix.getNo(BOARD_EDGE_CLEARANCE_CLASS_NAME);
    }
    if (boardEdgeClassNo < 0) {
      FRLogger.warn(
          "Unable to create/find the board_edge clearance class for copper-to-edge override.");
      return;
    }

    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      for (int classNo = 1; classNo < matrix.getClassCount(); classNo++) {
        matrix.setValue(boardEdgeClassNo, classNo, layer, configuredClearanceBoardUnits);
        matrix.setValue(classNo, boardEdgeClassNo, layer, configuredClearanceBoardUnits);
      }
    }

    if (this.board.searchTreeManager != null) {
      this.board.searchTreeManager.remove(outline);
    }
    outline.setClearanceClassNo(boardEdgeClassNo);
    outline.clearDerivedData();
    if (this.board.searchTreeManager != null) {
      this.board.searchTreeManager.insert(outline);
    }

    FRLogger.debug(
        "Applied copper-to-edge clearance override: "
            + configuredClearanceUm
            + " um ("
            + configuredClearanceBoardUnits
            + " board units).");
  }

  /**
   * Returns the current routing job context associated with this board manager.
   *
   * <p>The routing job orchestrates the routing process and provides:
   *
   * <ul>
   *   <li>Router algorithm settings and configuration
   *   <li>Logging and error handling facilities
   *   <li>Global settings and feature flags
   *   <li>Analytics and metrics collection
   * </ul>
   *
   * @return the current routing job, or null if no job is set
   * @see RoutingJob
   */
  @Override
  public RoutingJob getCurrentRoutingJob() {
    return this.routingJob;
  }

  /**
   * Calculates a CRC32 checksum of the current board state.
   *
   * <p>The checksum is computed from the DSN (Specctra Design) representation of the board, which
   * includes:
   *
   * <ul>
   *   <li>Board geometry and layer structure
   *   <li>All placed components and their positions
   *   <li>All routed traces and vias
   *   <li>Design rules and net definitions
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   *
   * <ul>
   *   <li><strong>Change Detection:</strong> Compare against {@link #originalBoardChecksum} to
   *       detect if the board has been modified
   *   <li><strong>Integrity Verification:</strong> Ensure board data hasn't been corrupted
   *   <li><strong>Version Control:</strong> Track board modifications over time
   * </ul>
   *
   * <p><strong>Performance Note:</strong> This operation serializes the entire board to DSN format
   * in memory, which can be expensive for large boards. Use sparingly.
   *
   * @return the CRC32 checksum value of the board's DSN representation
   * @see #originalBoardChecksum
   * @see BoardFileDetails#calculateCrc32(InputStream)
   */
  public long calculateCrc32() {
    return calculateCrc32ForBoard(this.getRoutingBoard());
  }

  private long calculateCrc32ForBoard(RoutingBoard board) {
    ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
    try {
      DsnWriter.write(board, memoryStream, "N/A", false);
    } catch (IOException e) {
      FRLogger.error(
          "HeadlessBoardManager.calculateCrc32ForBoard: unable to serialise board to DSN", e);
      throw new IllegalStateException("Unable to serialize board to DSN for CRC32 calculation", e);
    }
    InputStream inputStream = new ByteArrayInputStream(memoryStream.toByteArray());
    return BoardFileDetails.calculateCrc32(inputStream).getValue();
  }

  /**
   * Loads a board design from a Specctra DSN (Design) format file.
   *
   * <p>The DSN format is an industry-standard PCB interchange format that describes:
   *
   * <ul>
   *   <li>Board physical structure (layers, outline, stackup)
   *   <li>Component placement and footprints
   *   <li>Net definitions and connectivity
   *   <li>Design rules and constraints
   *   <li>Existing routing (if any)
   * </ul>
   *
   * <p><strong>Loading Process:</strong>
   *
   * <ol>
   *   <li>Parse the DSN file and create board structure
   *   <li>Apply board-specific optimizations to router settings
   *   <li>Reduce/optimize net data structures
   *   <li>Calculate and store initial board checksum
   *   <li>Send analytics about the loaded board
   * </ol>
   *
   * <p><strong>Integration Parameters:</strong> The {@code boardObservers} and {@code
   * identificationNumberGenerator} parameters support embedding Freerouting into host CAD systems,
   * allowing:
   *
   * <ul>
   *   <li>Real-time synchronization of board changes with the host
   *   <li>Consistent item identification across systems
   * </ul>
   *
   * <p><strong>Error Handling:</strong> Returns subclass of {@link BoardReadResult} (like {@link
   * app.freerouting.io.BoardReadResult.ParseError} or {@link
   * app.freerouting.io.BoardReadResult.IoError}) if:
   *
   * <ul>
   *   <li>Input stream is null or invalid
   *   <li>DSN file is corrupted or malformed
   *   <li>File format version is not supported
   *   <li>I/O errors occur during reading
   * </ul>
   *
   * <p><strong>Side Effects:</strong> On success, replaces any existing board and updates router
   * settings to match the new board's characteristics (layer count, optimizations).
   *
   * @param inputStream the input stream containing DSN file data (will be closed after reading)
   * @param boardObservers optional observers for board item changes (can be null for standalone
   *     use)
   * @param identificationNumberGenerator optional ID generator for board items (can be null)
   * @return the read result indicating success, warnings, or errors
   * @see app.freerouting.io.specctra.DsnReader#readBoard
   * @see BoardObservers
   */
  public BoardReadResult loadFromSpecctraDsn(
      InputStream inputStream,
      BoardObservers boardObservers,
      IdentificationNumberGenerator identificationNumberGenerator) {
    if (inputStream == null) {
      return new BoardReadResult.IoError(new java.io.IOException("inputStream is null"));
    }

    try {
      String inputFilename =
          this.routingJob != null && this.routingJob.input != null
              ? this.routingJob.input.getFilename()
              : null;
      if (this.routingJob != null) {
        this.routingJob.logInfo(
            "Loading board file"
                + (inputFilename != null ? " '" + inputFilename + "'" : "")
                + "...");
      } else {
        FRLogger.info(
            "Loading board file"
                + (inputFilename != null ? " '" + inputFilename + "'" : "")
                + "...");
      }
      BoardReadResult dsnResult =
          DsnReader.readBoard(
              inputStream, boardObservers, identificationNumberGenerator, inputFilename);

      applyParsedBoardResult(dsnResult, inputFilename, "DSN");
      return dsnResult;

    } catch (Exception e) {
      routingJob.logError("There was an error while reading DSN file.", e);
      return new BoardReadResult.IoError(new java.io.IOException("Error reading DSN file", e));
    }
  }

  /**
   * Applies a previously parsed board read result to this manager without performing I/O. Used by
   * the GUI to finish loading on the EDT after background parsing.
   */
  public BoardReadResult applyParsedBoardResult(
      BoardReadResult dsnResult, String inputFilename, String analyticsFormat) {
    if (dsnResult instanceof BoardReadResult.Success success) {
      this.board = (RoutingBoard) success.board();
    } else if (dsnResult instanceof BoardReadResult.OutlineMissing outlineMissing) {
      this.board = (RoutingBoard) outlineMissing.board();
    } else {
      if (routingJob != null) {
        if (dsnResult instanceof BoardReadResult.IoError ioError) {
          routingJob.logError("There was an IO error while reading board file.", ioError.cause());
        } else if (dsnResult instanceof BoardReadResult.ParseError parseError) {
          routingJob.logError(
              "There was a parse error while reading board file at '"
                  + parseError.location()
                  + "': "
                  + parseError.detail(),
              null);
        }
      }
      return dsnResult;
    }

    applyRouterSettingsForLoadedBoard();
    applyImmediatePostLoadProcessing();
    scheduleDeferredPostLoadProcessing(inputFilename, analyticsFormat);
    return dsnResult;
  }

  private void applyRouterSettingsForLoadedBoard() {
    if (this.board != null && this.routingJob != null) {
      int boardLayerCount = this.board.getLayerCount();
      if (this.routingJob.routerSettings.getLayerCount() != boardLayerCount) {
        this.routingJob.routerSettings.setLayerCount(boardLayerCount);
      }
      this.routingJob.routerSettings.applyBoardSpecificOptimizations(this.board);
      applyCopperToEdgeClearanceOverride();
      applyHoleClearanceOverride();
    }
  }

  private void applyImmediatePostLoadProcessing() {
    if (this.board == null) {
      return;
    }
    this.board.reduceNetsOfRouteItems();
    validatePowerPlanes();
  }

  private void scheduleDeferredPostLoadProcessing(String inputFilename, String analyticsFormat) {
    if (this.board == null) {
      return;
    }
    RoutingBoard loadedBoard = this.board;
    HeadlessBoardManager manager = this;
    Thread.ofVirtual()
        .name("board-post-load")
        .start(
            () -> {
              try {
                var boardStats = new BoardStatistics(loadedBoard, null, false, false);
                FRAnalytics.fileLoaded(analyticsFormat, GSON.toJson(boardStats));
                FRAnalytics.boardLoaded(
                    loadedBoard.communication.specctraParserInfo.hostCad,
                    loadedBoard.communication.specctraParserInfo.hostVersion,
                    loadedBoard.getLayerCount(),
                    loadedBoard.components.count(),
                    loadedBoard.rules.nets.maxNetNo());
                manager.originalBoardChecksum = manager.calculateCrc32ForBoard(loadedBoard);
                compareCounterpartBoardIfPresent(loadedBoard, inputFilename);
              } catch (Exception e) {
                FRLogger.error("Deferred post-load processing failed", e);
              }
            });
  }

  private static void compareCounterpartBoardIfPresent(RoutingBoard board, String inputFilename) {
    if (inputFilename == null) {
      return;
    }
    String counterpartPath = null;
    if (inputFilename.toLowerCase().endsWith(".dsn")) {
      counterpartPath = inputFilename.substring(0, inputFilename.length() - 4) + ".json";
    } else if (inputFilename.toLowerCase().endsWith(".json")) {
      counterpartPath = inputFilename.substring(0, inputFilename.length() - 5) + ".dsn";
    }
    if (counterpartPath == null) {
      return;
    }
    java.io.File counterpartFile = new java.io.File(counterpartPath);
    if (!counterpartFile.exists()) {
      return;
    }
    RoutingBoard counterpartBoard = loadBoardFromFileForComparison(counterpartFile);
    if (counterpartBoard == null) {
      return;
    }
    app.freerouting.board.BoardComparator.ComparisonResult comparison =
        app.freerouting.board.BoardComparator.compare(board, counterpartBoard, 1e-3);
    if (comparison.areEqual) {
      FRLogger.debug(
          "Counterpart comparison: The loaded board and its counterpart '"
              + counterpartFile.getName()
              + "' are identical in representation.");
    } else {
      FRLogger.warn(
          "Counterpart comparison: Differences detected between loaded board and counterpart '"
              + counterpartFile.getName()
              + "'.");
      FRLogger.debug(comparison.report);
    }
  }

  /**
   * Loads a board design from a KiCad JSON format file/stream.
   *
   * @param inputStream the input stream containing KiCad JSON data (will be closed after reading)
   * @param boardObservers optional observers for board item changes (can be null)
   * @param identificationNumberGenerator optional ID generator for board items (can be null)
   * @return the read result indicating success, warnings, or errors
   */
  public BoardReadResult loadFromKiCadJson(
      InputStream inputStream,
      BoardObservers boardObservers,
      IdentificationNumberGenerator identificationNumberGenerator) {
    if (inputStream == null) {
      return new BoardReadResult.IoError(new java.io.IOException("inputStream is null"));
    }

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

    try (java.io.Reader reader =
        new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8)) {
      BoardReadResult dsnResult =
          KiCadJsonReader.readBoard(reader, boardObservers, identificationNumberGenerator);
      applyParsedBoardResult(dsnResult, inputFilename, "KICAD_JSON");
      return dsnResult;

    } catch (Exception e) {
      routingJob.logError("There was an error while reading KiCad JSON file.", e);
      return new BoardReadResult.IoError(
          new java.io.IOException("Error reading KiCad JSON file", e));
    }
  }

  /**
   * Writes the routing results to a Specctra SES (Session) format file.
   *
   * <p>The SES format is the companion output format to DSN, containing:
   *
   * <ul>
   *   <li>All routed traces and their exact paths
   *   <li>Via placements and layer transitions
   *   <li>Routing modifications to original design
   *   <li>Completion status and statistics
   * </ul>
   *
   * <p><strong>Typical Workflow:</strong>
   *
   * <ol>
   *   <li>CAD tool exports design as DSN file
   *   <li>Freerouting loads DSN and performs autorouting
   *   <li>Freerouting exports results as SES file
   *   <li>CAD tool imports SES to update design with routing
   * </ol>
   *
   * <p><strong>Post-Save Operations:</strong> On successful save:
   *
   * <ul>
   *   <li>Updates {@link #originalBoardChecksum} to reflect the saved state
   *   <li>Marks the board as having no unsaved changes
   * </ul>
   *
   * <p><strong>Note:</strong> The output stream is NOT closed by this method. The caller is
   * responsible for closing it.
   *
   * @param outputStream the stream to write SES data to (caller must close)
   * @param designName the design name to include in the SES file header
   * @return true if save was successful, false if an error occurred
   * @see SesWriter#write
   * @see #loadFromSpecctraDsn
   */
  public boolean saveAsSpecctraSessionSes(OutputStream outputStream, String designName) {
    boolean wasSaveSuccessful;
    try {
      SesWriter.write(this.getRoutingBoard(), outputStream, designName);
      wasSaveSuccessful = true;
    } catch (IOException e) {
      FRLogger.error("unable to write session file", e);
      wasSaveSuccessful = false;
    }

    if (wasSaveSuccessful) {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

  private static RoutingBoard loadBoardFromFileForComparison(java.io.File file) {
    try (java.io.InputStream is = new java.io.FileInputStream(file)) {
      if (file.getName().toLowerCase().endsWith(".json")) {
        try (java.io.Reader r =
            new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)) {
          app.freerouting.io.BoardReadResult readResult =
              app.freerouting.io.kicad.KiCadJsonReader.readBoard(r, null, null);
          if (readResult instanceof app.freerouting.io.BoardReadResult.Success success) {
            return (RoutingBoard) success.board();
          } else if (readResult
              instanceof app.freerouting.io.BoardReadResult.OutlineMissing outlineMissing) {
            return (RoutingBoard) outlineMissing.board();
          }
        }
      } else {
        app.freerouting.io.BoardReadResult readResult =
            app.freerouting.io.specctra.DsnReader.readBoard(is, null, null, file.getName());
        if (readResult instanceof app.freerouting.io.BoardReadResult.Success success) {
          return (RoutingBoard) success.board();
        } else if (readResult
            instanceof app.freerouting.io.BoardReadResult.OutlineMissing outlineMissing) {
          return (RoutingBoard) outlineMissing.board();
        }
      }
    } catch (Exception e) {
      FRLogger.error("Failed to load counterpart board: " + e.getMessage(), e);
    }
    return null;
  }

  boolean conductionAreasOverlap(
      app.freerouting.board.ConductionArea ca1, app.freerouting.board.ConductionArea ca2) {
    app.freerouting.geometry.planar.TileShape[] pieces1 = ca1.getArea().splitToConvex();
    app.freerouting.geometry.planar.TileShape[] pieces2 = ca2.getArea().splitToConvex();
    if (pieces1 == null || pieces2 == null) {
      return false;
    }
    for (app.freerouting.geometry.planar.TileShape p1 : pieces1) {
      for (app.freerouting.geometry.planar.TileShape p2 : pieces2) {
        if (p1.intersects(p2)) {
          app.freerouting.geometry.planar.TileShape intersection = p1.intersection(p2);
          if (intersection != null && !intersection.isEmpty() && intersection.dimension() == 2) {
            return true;
          }
        }
      }
    }
    return false;
  }

  String getConductionAreaNetNames(app.freerouting.board.ConductionArea ca) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (int i = 0; i < ca.netCount(); i++) {
      int netNo = ca.getNetNo(i);
      app.freerouting.rules.Net net = this.board.rules.nets.get(netNo);
      if (net != null) {
        names.add(net.name);
      } else {
        names.add(String.valueOf(netNo));
      }
    }
    return String.join(", ", names);
  }

  void validatePowerPlanes() {
    if (this.board == null) {
      return;
    }

    boolean validationFailed = false;
    java.util.List<String> violations = new java.util.ArrayList<>();

    for (int i = 0; i < this.board.getLayerCount(); i++) {
      app.freerouting.board.Layer layer = this.board.layerStructure.arr[i];
      if (!layer.isSignal) {
        final int layerNo = i;

        // 1. Check for signal wires/traces
        long traceCount =
            this.board.getTraces().stream().filter(trace -> trace.getLayer() == layerNo).count();
        if (traceCount > 0) {
          validationFailed = true;
          violations.add(
              "- Dedicated power layer '"
                  + layer.name
                  + "' contains "
                  + traceCount
                  + " signal wire(s)/trace(s).");
        }

        // 2. Check for at least one conduction area
        java.util.List<app.freerouting.board.ConductionArea> layerAreas =
            this.board.getConductionAreas().stream()
                .filter(ca -> ca.getLayer() == layerNo)
                .toList();
        if (layerAreas.isEmpty()) {
          validationFailed = true;
          violations.add(
              "- Dedicated power layer '" + layer.name + "' has no conduction areas defined.");
        }

        // 3. Check for overlapping conduction areas
        for (int j = 0; j < layerAreas.size(); j++) {
          for (int k = j + 1; k < layerAreas.size(); k++) {
            if (conductionAreasOverlap(layerAreas.get(j), layerAreas.get(k))) {
              validationFailed = true;
              String nets1 = getConductionAreaNetNames(layerAreas.get(j));
              String nets2 = getConductionAreaNetNames(layerAreas.get(k));
              violations.add(
                  "- Dedicated power layer '"
                      + layer.name
                      + "' has overlapping conduction areas: "
                      + "Area (ID "
                      + layerAreas.get(j).getIdNo()
                      + ", Net(s): ["
                      + nets1
                      + "]) and "
                      + "Area (ID "
                      + layerAreas.get(k).getIdNo()
                      + ", Net(s): ["
                      + nets2
                      + "]) overlap.");
            }
          }
        }
      }
    }

    if (validationFailed) {
      StringBuilder sb = new StringBuilder();
      sb.append("Power-plane validation failed:\n");
      for (String violation : violations) {
        sb.append(violation).append("\n");
      }
      sb.append("\nProper Definition and Best Practices for Power Planes:\n")
          .append("1. What Belongs on a Power Plane:\n")
          .append(
              "   - Solid Copper Pours: A single, uninterrupted sheet of copper assigned to one "
                  + "voltage (e.g., +3.3V).\n")
          .append(
              "   - Split Planes: Multiple distinct voltage zones divided by thin isolation gaps "
                  + "(puzzle pieces).\n")
          .append(
              "   - Vias and Anti-Pads: Plated holes passing through the board, surrounded by "
                  + "circular voids (anti-pads) to prevent shorting.\n")
          .append(
              "   - Thermal Reliefs: Spoked connections for vias or pins that connect to the "
                  + "plane, facilitating soldering.\n")
          .append("2. Why Signal Wires/Traces are Banned:\n")
          .append(
              "   - Destroyed Return Paths: High-speed signals on adjacent layers couple to the "
                  + "solid plane below/above as return paths. Crossing a trace gap detours return "
                  + "currents, causing severe EMI and signal integrity issues.\n")
          .append(
              "   - Compromised Power Delivery: Power planes should provide the lowest impedance "
                  + "path. Routing traces chops up the copper, creating bottleneck restrictions "
                  + "and voltage drops.\n");

      FRLogger.warn(sb.toString());
    }
  }
}
