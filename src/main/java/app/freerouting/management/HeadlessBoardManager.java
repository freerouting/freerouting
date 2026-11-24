package app.freerouting.management;

import static app.freerouting.util.gson.GsonProvider.GSON;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ComponentOutline;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.PadPlaneConnection;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.BoardObservers;
import app.freerouting.board.state.Communication;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.IdGenerator;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.gui.workspace.WorkspaceSettings;
import app.freerouting.gui.workspace.session.InteractiveActionThread;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadBoardDrawings;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.io.kicad.KiCadDesignRules;
import app.freerouting.io.kicad.KiCadDesignRulesTranslator;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.kicad.KiCadProject;
import app.freerouting.io.specctra.DsnReadOptions;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnWriter;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.jobs.ThreadActionListener;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.CornerStyle;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.DifferentialPairDetector;
import app.freerouting.rules.MeanderPatternType;
import app.freerouting.rules.NetMeanderConstraint;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * app.freerouting.gui.workspace.GuiBoardManager} when GUI is not needed.
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
 * @see app.freerouting.gui.workspace.GuiBoardManager
 * @see RoutingBoard
 * @see RoutingJob
 */
public class HeadlessBoardManager implements BoardManager {

  private static final String BOARD_EDGE_CLEARANCE_CLASS_NAME = "board_edge";
  private static final String HOLE_EDGE_CLEARANCE_CLASS_NAME = "hole_edge";

  /** Suffix of the hole class made for a hole keepout that already has a class of its own. */
  private static final String HOLE_CLASS_SUFFIX = "+" + HOLE_EDGE_CLEARANCE_CLASS_NAME;

  /**
   * True once {@link #applyCopperToEdgeClearanceOverride()} has assigned the {@code board_edge}
   * class to the outline of the current board. The override runs again after the pins are inserted,
   * so the default value can be capped by the real pin-to-outline gaps.
   */
  private boolean edgeClearanceAppliedByOverride;

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
   * @see InteractiveActionThread
   */
  public ThreadActionListener autorouterListener;

  /**
   * The routing board containing all PCB design data and routing state.
   *
   * <p><strong>Design Issue:</strong> Board management architecture has redundancy issues:
   *
   * <ul>
   *   <li>{@link BoardManager} holds a board reference
   *   <li>{@link app.freerouting.autoroute.pipeline.NamedAlgorithm} may hold a board reference
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
   * @see #loadFromSpecctraDsn(InputStream, BoardObservers, IdGenerator)
   * @see #createBoard
   */
  public HeadlessBoardManager(RoutingJob routingJob) {
    this.routingJob = routingJob;
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
    app.freerouting.board.state.BoardComparator.ComparisonResult comparison =
        app.freerouting.board.state.BoardComparator.compare(board, counterpartBoard, 1e-3);
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
   * @see WorkspaceSettings
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
    this.edgeClearanceAppliedByOverride = false;
    applyCopperToEdgeClearanceOverride();
    applyHoleClearanceOverride();
    applyHoleToHole();
    applyExactCopperOverride();
    applyViaInPadAutoDetection();
    applyPlaneNetsOverride();
    applyPlaneAsObstacleOverride();
    applyClearanceToleranceOverride();
    applyMeanderSettingsOverride();
  }

  private void applyHoleToHole() {
    if (this.board == null
        || this.board.rules == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.holeToHoleUm == null) {
      return;
    }
    double um = this.routingJob.routerSettings.holeToHoleUm;
    if (um < 0 || !Double.isFinite(um)) {
      FRLogger.warn("Ignoring router.hole_to_hole_um because it is negative: " + um);
      return;
    }
    this.board.rules.setMinHoleToHole(
        (int) Math.floor(this.board.communication.physicalToBoard(um, Unit.UM) + 0.5));
  }

  private void applyExactCopperOverride() {
    if (this.board == null
        || this.board.rules == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.exactCopper == null) {
      return;
    }
    this.board.rules.setExactCopper(this.routingJob.routerSettings.exactCopper);
  }

  private void applyViaInPadAutoDetection() {
    if (this.board == null || this.routingJob == null || this.routingJob.routerSettings == null) {
      return;
    }
    if (this.routingJob.routerSettings.autorouter == null) {
      this.routingJob.routerSettings.autorouter = new app.freerouting.settings.AutorouterSettings();
    }
    Boolean viaInPad = this.routingJob.routerSettings.autorouter.viaInPad;
    Boolean viaTouchingPad = this.routingJob.routerSettings.autorouter.viaTouchingPad;
    if (viaInPad == null || viaTouchingPad == null) {
      boolean inPadDetected = false;
      boolean touchingDetected = false;
      for (app.freerouting.board.model.items.Via via : this.board.getVias()) {
        if (!inPadDetected
            && app.freerouting.board.actions.ForcedViaInserter.holeInSmdPad(
                this.board, via.getPadstack(), via.getCenter(), via.netNumbers)) {
          inPadDetected = true;
          touchingDetected = true;
          break;
        }
        if (!touchingDetected
            && app.freerouting.board.actions.ForcedViaInserter.crowdsOwnPad(
                this.board,
                via.getPadstack(),
                via.clearanceClassIndex(),
                via.getCenter(),
                via.netNumbers != null && via.netNumbers.length > 0 ? via.netNumbers[0] : 0)) {
          touchingDetected = true;
        }
      }
      if (viaInPad == null) {
        if (inPadDetected && !Boolean.FALSE.equals(viaTouchingPad)) {
          this.routingJob.routerSettings.autorouter.viaInPad = true;
          if (viaTouchingPad == null) {
            this.routingJob.routerSettings.autorouter.viaTouchingPad = true;
          }
          FRLogger.info("The board's own vias decide: router.autorouter.via_in_pad = true.");
        } else if (touchingDetected && viaTouchingPad == null) {
          this.routingJob.routerSettings.autorouter.viaInPad = false;
          this.routingJob.routerSettings.autorouter.viaTouchingPad = true;
          FRLogger.info("The board's own vias decide: router.autorouter.via_touching_pad = true.");
        } else {
          this.routingJob.routerSettings.autorouter.viaInPad = false;
        }
      }
      if (this.routingJob.routerSettings.autorouter.viaTouchingPad == null) {
        this.routingJob.routerSettings.autorouter.viaTouchingPad = touchingDetected;
      }
    }
    this.board.rules.setViaInPad(
        Boolean.TRUE.equals(this.routingJob.routerSettings.autorouter.viaInPad),
        Boolean.TRUE.equals(this.routingJob.routerSettings.autorouter.viaTouchingPad));
  }

  /**
   * Translates the KiCad custom design rules of the job into clearance classes. Runs before the
   * other clearance overrides, so that they also reach the classes it adds, and after the net class
   * exclusions, which the split net classes inherit.
   */
  private void applyKiCadDesignRules() {
    KiCadDesignRules designRules = this.routingJob.kicadDesignRules;
    if (designRules == null || this.board == null) {
      return;
    }
    KiCadDesignRulesTranslator.Result result =
        KiCadDesignRulesTranslator.apply(this.board, designRules);
    for (String warning : result.warnings()) {
      this.routingJob.logWarning("KiCad design rules: " + warning);
    }
    this.routingJob.logInfo(KiCadDesignRulesTranslator.summary(result, designRules.sourceName()));
  }

  /**
   * Makes the minimum track width of the KiCad project of the job the narrowest trace the board
   * allows. The neck width taken from the project is only a router setting, which the fallback
   * strategies relax (no-neck, continue-no-neck, continue-fine-neck, neck-100um); every automatic
   * narrowing also stops at the narrowest allowed trace, so none of them goes below the width the
   * KiCad DRC checks.
   */
  private void applyKiCadProjectMinTrackWidth() {
    KiCadProject project = this.routingJob.kicadProject;
    if (project == null || project.minTrackWidthUm() == null || this.board == null) {
      return;
    }
    double minWidth = this.board.communication.physicalToBoard(project.minTrackWidthUm(), Unit.UM);
    int minHalfWidth = (int) Math.ceil(minWidth / 2);
    if (minHalfWidth > this.board.rules.getMinAllowedTraceHalfWidth()) {
      this.board.rules.setMinAllowedTraceHalfWidth(minHalfWidth);
      this.routingJob.logInfo(
          "KiCad project: no automatic narrowing below the minimum track width of "
              + project.minTrackWidthUm()
              + " um.");
    }
  }

  /**
   * Gives each plane the clearance and the minimum width of its zone in the KiCad board of the job.
   * A plane is matched to the zone of its net and layer; if the net has several zones on the layer,
   * to the one whose outline has the nearest bounding box.
   */
  private void applyKiCadZoneFillRules() {
    KiCadBoardZones boardZones = this.routingJob.kicadBoardZones;
    if (boardZones == null || this.board == null) {
      return;
    }
    Communication communication = this.board.communication;
    int matched = 0;
    // board order, so that the log is the same on every run
    List<ConductionArea> areas = new ArrayList<>(this.board.getConductionAreas());
    areas.sort(Comparator.comparingInt(Item::getId));
    for (ConductionArea area : areas) {
      if (area.netCount() != 1 || this.board.rules.nets.get(area.getNetNumber(0)) == null) {
        continue;
      }
      String netName = this.board.rules.nets.get(area.getNetNumber(0)).name;
      String layerName = this.board.layerStructure.layers[area.getLayer()].name;
      // the bounding box of the plane in micrometres, as KiCad writes it into the DSN
      double[] box = communication.coordinateTransform.boardToDsn(area.boundingBox());
      for (int i = 0; i < box.length; i++) {
        box[i] = Unit.scale(box[i], communication.unit, Unit.UM);
      }
      KiCadBoardZones.Zone best = null;
      double bestDistance = Double.MAX_VALUE;
      for (KiCadBoardZones.Zone zone : boardZones.zones()) {
        if (!zone.netName().equals(netName) || !zone.isOnLayer(layerName)) {
          continue;
        }
        // the y axis of KiCad points down, the one of the DSN up
        double distance =
            Math.abs(zone.minXUm() - box[0])
                + Math.abs(-zone.maxYUm() - box[1])
                + Math.abs(zone.maxXUm() - box[2])
                + Math.abs(-zone.minYUm() - box[3]);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = zone;
        }
      }
      if (best == null) {
        continue;
      }
      matched++;
      int clearance = (int) Math.ceil(communication.physicalToBoard(best.clearanceUm(), Unit.UM));
      int minWidth = (int) Math.ceil(communication.physicalToBoard(best.minThicknessUm(), Unit.UM));
      if (clearance != area.getFillClearance() || minWidth != area.getFillMinWidth()) {
        area.setFillRules(clearance, minWidth);
      }
      PadPlaneConnection.Mode padConnection =
          PadPlaneConnection.Mode.valueOf(best.padConnection().name());
      int thermalGap =
          (int) Math.round(communication.physicalToBoard(best.thermalGapUm(), Unit.UM));
      int spokeWidth =
          (int) Math.round(communication.physicalToBoard(best.thermalBridgeWidthUm(), Unit.UM));
      if (padConnection != area.getPadConnection()
          || thermalGap != area.getThermalGap()
          || spokeWidth != area.getThermalSpokeWidth()) {
        area.setPadConnectionRules(padConnection, thermalGap, spokeWidth);
      }
      double edgeClearanceUm =
          this.routingJob.kicadProject != null
              ? this.routingJob.kicadProject.copperEdgeClearanceUm()
              : boardZones.edgeClearanceUm();
      int edgeClearance = (int) Math.round(communication.physicalToBoard(edgeClearanceUm, Unit.UM));
      if (edgeClearance != area.getFillEdgeClearance()) {
        area.setFillEdgeClearance(edgeClearance);
      }
      boolean fillets = best.cornerSmoothing() == KiCadBoardZones.CornerSmoothing.FILLET;
      int corner =
          best.cornerSmoothing() == KiCadBoardZones.CornerSmoothing.NONE
              ? 0
              : (int) Math.round(communication.physicalToBoard(best.cornerRadiusUm(), Unit.UM));
      if (fillets != area.hasCornerFillets() || corner != area.getCornerSmoothing()) {
        area.setFillCornerSmoothing(fillets, corner);
      }
    }
    int matchedPads = applyKiCadPadConnections(boardZones);
    this.routingJob.logInfo(
        "KiCad board '"
            + boardZones.sourceName()
            + "': zone clearance and minimum width applied to "
            + matched
            + " of "
            + areas.size()
            + " planes, pad connection to "
            + matchedPads
            + " pads.");
  }

  /**
   * Inserts the copper drawings and texts without net of the KiCad board as obstacles: the Specctra
   * export of KiCad leaves them out of the design.
   */
  private void applyKiCadNetlessCopper() {
    KiCadBoardDrawings drawings = this.routingJob.kicadBoardDrawings;
    if (drawings == null || this.board == null || drawings.netlessCopper().isEmpty()) {
      return;
    }
    KiCadNetlessCopper.Result result = KiCadNetlessCopper.insert(this.board, drawings);
    this.routingJob.logInfo(
        "KiCad board '"
            + drawings.sourceName()
            + "': "
            + result.drawings()
            + " copper drawings and text characters without net inserted as "
            + result.areas()
            + " obstacles ("
            + result.connected()
            + " of them touching a pin, a trace or a via, an obstacle for the other nets only"
            + (result.skipped() > 0
                ? "; " + result.skipped() + " on a layer absent from the design"
                : "")
            + ").");
  }

  /**
   * Gives each pin the zone connection of its pad in the KiCad board: the pad at the position of
   * the pin, on a layer of the pin. Returns the number of pins that found their pad.
   */
  private int applyKiCadPadConnections(KiCadBoardZones boardZones) {
    Communication communication = this.board.communication;
    // the pads by cells of 10 um, to find the pad of a pin without comparing each with each
    double cell = 10.0;
    Map<Long, List<KiCadBoardZones.Pad>> padsByCell = new HashMap<>();
    for (KiCadBoardZones.Pad pad : boardZones.pads()) {
      long key = cellKey(Math.round(pad.xUm() / cell), Math.round(-pad.yUm() / cell));
      padsByCell.computeIfAbsent(key, k -> new ArrayList<>()).add(pad);
    }
    int matched = 0;
    List<Pin> pins = new ArrayList<>(this.board.getPins());
    pins.sort(Comparator.comparingInt(Item::getId));
    for (Pin pin : pins) {
      // the centre of the pin in micrometres, the y axis pointing up as in the DSN
      double[] centre = communication.coordinateTransform.boardToDsn(pin.getCenter().toFloat());
      double x = Unit.scale(centre[0], communication.unit, Unit.UM);
      double y = Unit.scale(centre[1], communication.unit, Unit.UM);
      String firstLayer = this.board.layerStructure.layers[pin.firstLayer()].name;
      String lastLayer = this.board.layerStructure.layers[pin.lastLayer()].name;
      KiCadBoardZones.Pad best = null;
      double bestDistance = 5.0;
      long cellX = Math.round(x / cell);
      long cellY = Math.round(y / cell);
      for (long dx = -1; dx <= 1; dx++) {
        for (long dy = -1; dy <= 1; dy++) {
          List<KiCadBoardZones.Pad> candidates = padsByCell.get(cellKey(cellX + dx, cellY + dy));
          if (candidates == null) {
            continue;
          }
          for (KiCadBoardZones.Pad pad : candidates) {
            double distance = Math.hypot(pad.xUm() - x, -pad.yUm() - y);
            if (distance < bestDistance
                && (pad.isOnLayer(firstLayer) || pad.isOnLayer(lastLayer))) {
              bestDistance = distance;
              best = pad;
            }
          }
        }
      }
      if (best == null) {
        continue;
      }
      matched++;
      FloatPoint shapeCentre =
          communication.coordinateTransform.dsnToBoard(
              new double[] {
                Unit.scale(best.shapeXUm(), Unit.UM, communication.unit),
                Unit.scale(-best.shapeYUm(), Unit.UM, communication.unit)
              });
      pin.setPlaneConnection(
          new PadPlaneConnection(
              PadPlaneConnection.Mode.valueOf(best.connection().name()),
              best.throughHole(),
              shapeCentre.x,
              shapeCentre.y,
              communication.physicalToBoard(best.sizeXUm() / 2.0, Unit.UM),
              communication.physicalToBoard(best.sizeYUm() / 2.0, Unit.UM),
              best.angleDeg(),
              best.spokeAngleDeg(),
              best.circular(),
              (int) Math.round(communication.physicalToBoard(best.thermalGapUm(), Unit.UM)),
              (int)
                  Math.round(communication.physicalToBoard(best.thermalBridgeWidthUm(), Unit.UM))));
    }
    return matched;
  }

  private static long cellKey(long x, long y) {
    return x * 4_000_037L + y;
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

    int configuredClearanceBoardUnits =
        (int) Math.round(this.board.communication.physicalToBoard(configuredClearanceUm, Unit.UM));
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
    java.util.List<app.freerouting.board.model.items.ObstacleArea> holeKeepouts =
        new java.util.ArrayList<>();
    for (app.freerouting.board.model.items.Item item : this.board.getItems()) {
      if (item.getClass() != app.freerouting.board.model.items.ObstacleArea.class) {
        continue;
      }
      app.freerouting.board.model.items.ObstacleArea keepout =
          (app.freerouting.board.model.items.ObstacleArea) item;
      // Package keepouts belong to a component; a circular one is a drilled hole in the
      // footprint (the only way KiCad expresses NPTH in DSN).
      if (keepout.getComponentId() > 0
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
    // A keepout that has a class of its own keeps it, with the hole clearance as a floor: the
    // translation of a .kicad_dru gives the pads without net that KiCad exports as keepouts the
    // clearances of their rules, and this runs again after it. Each hole class is built from a
    // source class: hole_edge from the generic AREA class, a "<class>+hole_edge" from <class>.
    Map<Integer, Integer> sourceOfHoleClass = new LinkedHashMap<>();
    sourceOfHoleClass.put(holeEdgeClassNo, defaultAreaClassNo);
    Map<app.freerouting.board.model.items.ObstacleArea, Integer> targetOfKeepout =
        new LinkedHashMap<>();
    for (app.freerouting.board.model.items.ObstacleArea keepout : holeKeepouts) {
      int current = keepout.clearanceClassIndex();
      int target;
      if (current == holeEdgeClassNo || current <= 0 || current == defaultAreaClassNo) {
        target = holeEdgeClassNo;
      } else if (matrix.getName(current).endsWith(HOLE_CLASS_SUFFIX)) {
        target = current;
        String base = matrix.getName(current);
        int source = matrix.getNo(base.substring(0, base.length() - HOLE_CLASS_SUFFIX.length()));
        sourceOfHoleClass.putIfAbsent(current, source > 0 ? source : current);
      } else {
        String name = matrix.getName(current) + HOLE_CLASS_SUFFIX;
        matrix.appendClass(name);
        target = matrix.getNo(name);
        if (target < 0) {
          FRLogger.warn("Unable to create the " + name + " clearance class: keepout left as is.");
          continue;
        }
        sourceOfHoleClass.putIfAbsent(target, current);
      }
      targetOfKeepout.put(keepout, target);
    }
    for (Map.Entry<Integer, Integer> holeClass : sourceOfHoleClass.entrySet()) {
      int classNo = holeClass.getKey();
      int source = holeClass.getValue();
      for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
        for (int other = 1; other < matrix.getClassCount(); other++) {
          // Never reduce an existing requirement: hole clearance is a floor on top of the
          // clearance the keepout would otherwise get. Between two hole classes, from both sources.
          int otherSource = sourceOfHoleClass.getOrDefault(other, other);
          int value =
              Math.max(holeClearanceBoardUnits, matrix.getValue(source, otherSource, layer, false));
          matrix.setValue(classNo, other, layer, value);
          matrix.setValue(other, classNo, layer, value);
        }
      }
    }
    int reclassified = 0;
    for (Map.Entry<app.freerouting.board.model.items.ObstacleArea, Integer> entry :
        targetOfKeepout.entrySet()) {
      app.freerouting.board.model.items.ObstacleArea keepout = entry.getKey();
      if (keepout.clearanceClassIndex() != entry.getValue()) {
        keepout.setClearanceClassIndex(entry.getValue());
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
    boolean usesFallbackOutlineClass = outline.clearanceClassIndex() == defaultAreaClassNo;
    boolean usesDefaultEdgeClearanceValue =
        Math.abs(configuredClearanceUm - DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM)
            < 1e-9;
    // Keep explicit DSN outline-clearance classes untouched when only the global default is active.
    if (usesDefaultEdgeClearanceValue
        && !usesFallbackOutlineClass
        && !edgeClearanceAppliedByOverride) {
      return;
    }

    int configuredClearanceBoardUnits =
        (int) Math.round(this.board.communication.physicalToBoard(configuredClearanceUm, Unit.UM));

    if (usesDefaultEdgeClearanceValue) {
      // The default value is only a guess. Never demand more edge clearance than the input already
      // has: a pin that is closer to the outline than the default would block every connection.
      double minimumPinGap = outline.minimumPinGap();
      if (minimumPinGap < configuredClearanceBoardUnits) {
        int cappedClearanceBoardUnits = (int) Math.max(0, Math.floor(minimumPinGap));
        FRLogger.debug(
            "Copper-to-edge clearance capped from "
                + configuredClearanceBoardUnits
                + " to "
                + cappedClearanceBoardUnits
                + " board units because a pin is closer to the board outline.");
        configuredClearanceBoardUnits = cappedClearanceBoardUnits;
      }
    }

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
    outline.setClearanceClassIndex(boardEdgeClassNo);
    edgeClearanceAppliedByOverride = true;
    outline.clearDerivedData();
    if (this.board.searchTreeManager != null) {
      this.board.searchTreeManager.insert(outline);
    }
    outline.updateSlicesToTraceWidths();

    FRLogger.debug(
        "Applied copper-to-edge clearance override: "
            + configuredClearanceUm
            + " um ("
            + configuredClearanceBoardUnits
            + " board units).");
  }

  private void applyPlaneNetsOverride() {
    if (this.board == null
        || this.board.rules == null
        || this.board.rules.nets == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.planeNets == null) {
      return;
    }

    for (String netName : this.routingJob.routerSettings.planeNets) {
      if (netName == null || netName.isBlank()) {
        continue;
      }
      java.util.Collection<app.freerouting.rules.Net> matchingNets =
          this.board.rules.nets.get(netName.trim());
      for (app.freerouting.rules.Net net : matchingNets) {
        if (!net.containsPlane()) {
          net.setContainsPlane(true);
          FRLogger.info(
              "Configured net '" + net.name + "' as a power plane net via router.plane_nets.");
        }
      }
    }
  }

  private void applyPlaneAsObstacleOverride() {
    if (this.board == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.planeAsObstacle == null) {
      return;
    }

    boolean asObstacle = this.routingJob.routerSettings.planeAsObstacle;
    this.board.changePlaneAsObstacle(asObstacle);
    FRLogger.debug("Applied plane_as_obstacle override: " + asObstacle);
  }

  private void applyClearanceToleranceOverride() {
    if (this.board == null
        || this.board.rules == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null
        || this.routingJob.routerSettings.clearanceToleranceUm == null) {
      return;
    }

    double toleranceUm = this.routingJob.routerSettings.clearanceToleranceUm;
    if (!Double.isFinite(toleranceUm) || toleranceUm < 0) {
      FRLogger.warn(
          "Ignoring router.clearance_tolerance_um because it is invalid (must be finite and >= 0): "
              + toleranceUm);
      return;
    }

    this.board.rules.clearanceToleranceUm = toleranceUm;
    if (this.routingJob.drcSettings != null) {
      this.routingJob.drcSettings.clearanceToleranceUm = toleranceUm;
    }
    FRLogger.debug("Applied clearance tolerance: " + toleranceUm + " um.");
  }

  private void applyMeanderSettingsOverride() {
    if (this.board == null
        || this.board.rules == null
        || this.board.communication == null
        || this.routingJob == null
        || this.routingJob.routerSettings == null) {
      return;
    }

    int boardResolution = Math.max(1, this.board.communication.resolution);
    double mmToBoardScale = Unit.scale(boardResolution, Unit.MM, this.board.communication.unit);

    // Ensure default meander constraints are properly scaled to board units
    this.board.rules.initDefaultMeanderConstraints(mmToBoardScale);

    var settings = this.routingJob.routerSettings;
    if (settings.meanderMinAmplitudeMm == null
        && settings.meanderMaxAmplitudeMm == null
        && settings.meanderSpacingMm == null
        && settings.meanderCornerStyle == null
        && settings.meanderRadiusPercentage == null
        && settings.meanderSingleSided == null) {
      return;
    }

    boolean usesDefaultValues =
        (settings.meanderMinAmplitudeMm == null
                || Math.abs(
                        settings.meanderMinAmplitudeMm
                            - DefaultSettings.DEFAULT_MEANDER_MIN_AMPLITUDE_MM)
                    < 1e-9)
            && (settings.meanderMaxAmplitudeMm == null
                || Math.abs(
                        settings.meanderMaxAmplitudeMm
                            - DefaultSettings.DEFAULT_MEANDER_MAX_AMPLITUDE_MM)
                    < 1e-9)
            && (settings.meanderSpacingMm == null
                || Math.abs(settings.meanderSpacingMm - DefaultSettings.DEFAULT_MEANDER_SPACING_MM)
                    < 1e-9)
            && (settings.meanderCornerStyle == null
                || DefaultSettings.DEFAULT_MEANDER_CORNER_STYLE.equalsIgnoreCase(
                    settings.meanderCornerStyle))
            && (settings.meanderRadiusPercentage == null
                || Math.abs(
                        settings.meanderRadiusPercentage
                            - DefaultSettings.DEFAULT_MEANDER_RADIUS_PERCENTAGE)
                    < 1e-9)
            && (settings.meanderSingleSided == null
                || settings.meanderSingleSided == DefaultSettings.DEFAULT_MEANDER_SINGLE_SIDED);

    // Keep explicit DSN meander rules untouched when only the global default settings are active.
    if (usesDefaultValues) {
      return;
    }

    double minAmpMm =
        settings.meanderMinAmplitudeMm != null
            ? settings.meanderMinAmplitudeMm
            : DefaultSettings.DEFAULT_MEANDER_MIN_AMPLITUDE_MM;
    double maxAmpMm =
        settings.meanderMaxAmplitudeMm != null
            ? settings.meanderMaxAmplitudeMm
            : DefaultSettings.DEFAULT_MEANDER_MAX_AMPLITUDE_MM;
    double spacingMm =
        settings.meanderSpacingMm != null
            ? settings.meanderSpacingMm
            : DefaultSettings.DEFAULT_MEANDER_SPACING_MM;
    CornerStyle cornerStyle =
        settings.meanderCornerStyle != null
            ? CornerStyle.parse(settings.meanderCornerStyle)
            : CornerStyle.valueOf(DefaultSettings.DEFAULT_MEANDER_CORNER_STYLE);
    double radiusPct =
        settings.meanderRadiusPercentage != null
            ? settings.meanderRadiusPercentage
            : DefaultSettings.DEFAULT_MEANDER_RADIUS_PERCENTAGE;
    boolean singleSided =
        settings.meanderSingleSided != null
            ? settings.meanderSingleSided
            : DefaultSettings.DEFAULT_MEANDER_SINGLE_SIDED;

    for (MeanderPatternType type : MeanderPatternType.values()) {
      double defaultTypeSpacingMm =
          switch (type) {
            case DIFF_PAIR -> NetMeanderConstraint.DEFAULT_SPACING_DIFF_PAIR_MM;
            case DIFF_PAIR_SKEW -> NetMeanderConstraint.DEFAULT_SPACING_DIFF_PAIR_SKEW_MM;
            case SINGLE_TRACK -> NetMeanderConstraint.DEFAULT_SPACING_SINGLE_TRACK_MM;
          };
      double typeSpacingMm =
          (settings.meanderSpacingMm != null) ? settings.meanderSpacingMm : defaultTypeSpacingMm;

      NetMeanderConstraint constraint =
          NetMeanderConstraint.ofMm(
              minAmpMm,
              maxAmpMm,
              typeSpacingMm,
              cornerStyle,
              radiusPct,
              singleSided,
              mmToBoardScale);

      this.board.rules.setDefaultMeanderConstraint(type, constraint);
      FRLogger.debug(
          "Applied meander pattern settings to board rules default constraint for "
              + type
              + ": "
              + constraint);
    }
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
   * <p><strong>Integration Parameters:</strong> The {@code boardObservers} and {@code idGenerator}
   * parameters support embedding Freerouting into host CAD systems, allowing:
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
   * @param idGenerator optional ID generator for board items (can be null)
   * @return the read result indicating success, warnings, or errors
   * @see app.freerouting.io.specctra.DsnReader#readBoard
   * @see BoardObservers
   */
  public BoardReadResult loadFromSpecctraDsn(
      InputStream inputStream, BoardObservers boardObservers, IdGenerator idGenerator) {
    return loadFromSpecctraDsn(inputStream, boardObservers, idGenerator, DsnReadOptions.DEFAULT);
  }

  /** Loads a DSN with allocation options resolved before parsing by job orchestration. */
  public BoardReadResult loadFromSpecctraDsn(
      InputStream inputStream,
      BoardObservers boardObservers,
      IdGenerator idGenerator,
      DsnReadOptions options) {
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
          DsnReader.readBoard(inputStream, boardObservers, idGenerator, inputFilename, options);

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

  void applyRouterSettingsForLoadedBoard() {
    if (this.board != null && this.routingJob != null) {
      int boardLayerCount = this.board.getLayerCount();
      if (this.routingJob.routerSettings.getLayerCount() != boardLayerCount) {
        this.routingJob.routerSettings.setLayerCount(boardLayerCount);
      }
      this.routingJob.routerSettings.applyBoardSpecificOptimizations(this.board);
      this.routingJob.routerSettings.applyNetClassExclusions(this.board);
      applyKiCadDesignRules();
      applyKiCadProjectMinTrackWidth();
      applyKiCadZoneFillRules();
      applyKiCadNetlessCopper();
      applyCopperToEdgeClearanceOverride();
      applyHoleClearanceOverride();
      applyHoleToHole();
      applyExactCopperOverride();
      applyViaInPadAutoDetection();
      applyPlaneNetsOverride();
      applyPlaneAsObstacleOverride();
      applyClearanceToleranceOverride();
      applyMeanderSettingsOverride();
    }
  }

  void applyImmediatePostLoadProcessing() {
    if (this.board == null) {
      return;
    }
    this.board.expandBoundingBoxToIncludeAllItems();
    this.board.reduceNetsOfRouteItems();
    boolean isKicadBoard =
        this.routingJob != null
            && this.routingJob.input != null
            && this.routingJob.input.format == FileFormat.KICAD_DESIGN_JSON;
    boolean fixKicadFootprints =
        this.routingJob != null
            && this.routingJob.routerSettings != null
            && Boolean.TRUE.equals(this.routingJob.routerSettings.fixKicadFootprints);
    int[] tiePads = new int[1];
    int assigned = this.board.connectNetlessPads(isKicadBoard, fixKicadFootprints, tiePads);
    if (assigned > 0) {
      FRLogger.info(
          assigned
              + " netless pad(s) overlapping a netted pad take that pad's net"
              + (tiePads[0] > 0 ? " (" + tiePads[0] + " tie pad(s) carry several nets)" : "")
              + ".");
    }
    int netTieClusters = this.board.clusterNetTies();
    if (netTieClusters > 0) {
      FRLogger.info(netTieClusters + " multi-pin net-tie cluster(s) identified and indexed.");
    }
    validatePowerPlanes();
    validateBoardDesignErrors();
    DifferentialPairDetector.detectAndRegister(this.board.rules);
    // NOTE: The full-board DRC (getAllClearanceViolations) is O(n²) and is deferred to the
    // background thread in scheduleDeferredPostLoadProcessing() to avoid blocking every board
    // load (including GUI loads) on large designs.
  }

  private void scheduleDeferredPostLoadProcessing(String inputFilename, String analyticsFormat) {
    if (this.board == null) {
      return;
    }
    RoutingBoard loadedBoard = this.board;
    final java.util.concurrent.CompletableFuture<Void> future =
        new java.util.concurrent.CompletableFuture<>();
    loadedBoard.postLoadFuture = future;
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
                    loadedBoard.rules.nets.maxNetNumber());
                manager.originalBoardChecksum = manager.calculateCrc32ForBoard(loadedBoard);
                compareCounterpartBoardIfPresent(loadedBoard, inputFilename);
                // Run the full-board DRC here (O(n²)) so it does not block the load path.
                var drc = new app.freerouting.drc.DesignRulesChecker(loadedBoard, null);
                var violations = drc.getAllClearanceViolations();
                loadedBoard.preExistingClearanceViolationsCount = violations.size();
                int unfixable = 0;
                for (var v : violations) {
                  if (v.isUnfixable()) {
                    unfixable++;
                  }
                }
                loadedBoard.unfixableClearanceViolationsCount = unfixable;
                loadedBoard.clearanceBaseline =
                    new app.freerouting.drc.ClearanceBaseline(violations);
                if (!violations.isEmpty()) {
                  warnPreExistingClearanceViolations(loadedBoard, violations);
                }
                future.complete(null);
              } catch (Throwable e) {
                future.completeExceptionally(e);
                FRLogger.error("Deferred post-load processing failed", e);
              }
            });
  }

  /**
   * Loads a board design from a KiCad JSON format file/stream.
   *
   * @param inputStream the input stream containing KiCad JSON data (will be closed after reading)
   * @param boardObservers optional observers for board item changes (can be null)
   * @param idGenerator optional ID generator for board items (can be null)
   * @return the read result indicating success, warnings, or errors
   */
  public BoardReadResult loadFromKiCadJson(
      InputStream inputStream, BoardObservers boardObservers, IdGenerator idGenerator) {
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
      BoardReadResult dsnResult = KiCadJsonReader.readBoard(reader, boardObservers, idGenerator);
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
    boolean wasSaveSuccessful = exportSpecctraSessionSes(outputStream, designName);

    if (wasSaveSuccessful) {
      originalBoardChecksum = calculateCrc32();
    }

    return wasSaveSuccessful;
  }

  /**
   * Writes the current routing board state as a Specctra session (.ses) without recording it as the
   * saved state.
   *
   * <p>Produces the same bytes as {@link #saveAsSpecctraSessionSes}, but leaves {@link
   * #originalBoardChecksum} untouched, so the board is not serialised a second time as DSN for the
   * checksum. Meant for output snapshots published while a job is still routing.
   *
   * @param outputStream the stream to write SES data to (caller must close)
   * @param designName the design name to include in the SES file header
   * @return true if the export was successful, false if an error occurred
   */
  public boolean exportSpecctraSessionSes(OutputStream outputStream, String designName) {
    try {
      SesWriter.write(this.getRoutingBoard(), outputStream, designName);
      return true;
    } catch (IOException e) {
      FRLogger.error("unable to write session file", e);
      return false;
    }
  }

  /**
   * Saves the current routing board state as an Autodesk Fusion script (.scr).
   *
   * @param outputStream target output stream for the script
   * @param designName name of the design
   * @return true if save was successful, false otherwise
   */
  public boolean saveAsFusionScriptScr(OutputStream outputStream, String designName) {
    return writeFusionScriptScr(outputStream, designName, true);
  }

  /**
   * Writes the current routing board state as an Autodesk Fusion script (.scr) without recording it
   * as the saved state; see {@link #exportSpecctraSessionSes}.
   *
   * @param outputStream target output stream for the script
   * @param designName name of the design
   * @return true if the export was successful, false otherwise
   */
  public boolean exportFusionScriptScr(OutputStream outputStream, String designName) {
    return writeFusionScriptScr(outputStream, designName, false);
  }

  private boolean writeFusionScriptScr(
      OutputStream outputStream, String designName, boolean recordSavedState) {
    try (java.io.ByteArrayOutputStream sesBaos = new java.io.ByteArrayOutputStream()) {
      boolean sessionWritten =
          recordSavedState
              ? saveAsSpecctraSessionSes(sesBaos, designName)
              : exportSpecctraSessionSes(sesBaos, designName);
      if (!sessionWritten) {
        return false;
      }
      try (java.io.ByteArrayInputStream sesBais =
          new java.io.ByteArrayInputStream(sesBaos.toByteArray())) {
        return app.freerouting.io.specctra.SesReader.saveSpecctraSessionSesAsFusionScriptScr(
            sesBais, outputStream, this.getRoutingBoard());
      }
    } catch (IOException e) {
      FRLogger.error("unable to write Fusion script file", e);
      return false;
    }
  }

  boolean conductionAreasOverlap(
      app.freerouting.board.model.items.ConductionArea ca1,
      app.freerouting.board.model.items.ConductionArea ca2) {
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

  String getConductionAreaNetNames(app.freerouting.board.model.items.ConductionArea ca) {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (int i = 0; i < ca.netCount(); i++) {
      int netNumber = ca.getNetNumber(i);
      app.freerouting.rules.Net net = this.board.rules.nets.get(netNumber);
      if (net != null) {
        names.add(net.name);
      } else {
        names.add(String.valueOf(netNumber));
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
      app.freerouting.board.model.structure.Layer layer = this.board.layerStructure.layers[i];
      if (!layer.isSignal) {
        final int layerIndex = i;

        // 1. Check for signal wires/traces
        long traceCount =
            this.board.getTraces().stream().filter(trace -> trace.getLayer() == layerIndex).count();
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
        java.util.List<app.freerouting.board.model.items.ConductionArea> layerAreas =
            this.board.getConductionAreas().stream()
                .filter(ca -> ca.getLayer() == layerIndex)
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
                      + layerAreas.get(j).getId()
                      + ", Net(s): ["
                      + nets1
                      + "]) and "
                      + "Area (ID "
                      + layerAreas.get(k).getId()
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

  /**
   * Emits a WARNING that categorises all pre-existing clearance violations found in the loaded
   * board. The message distinguishes between unfixable violations (such as pin-to-pin,
   * pin-to-outline/keepout, and fixed traces/vias which Freerouting cannot resolve) and potentially
   * fixable violations (involving unfixed traces/vias). For each category, up to 5 violation pairs
   * are listed.
   */
  static void warnPreExistingClearanceViolations(
      RoutingBoard board, Collection<ClearanceViolation> violations) {
    FRLogger.warn(formatPreExistingClearanceViolationsWarning(board, violations));
  }

  /**
   * Formats the warning message for pre-existing clearance violations. Package-private for testing.
   */
  static String formatPreExistingClearanceViolationsWarning(
      RoutingBoard board, Collection<ClearanceViolation> violations) {
    List<ClearanceViolation> pinToPin = new ArrayList<>();
    List<ClearanceViolation> pinToOutline = new ArrayList<>();
    List<ClearanceViolation> fixedRoute = new ArrayList<>();
    List<ClearanceViolation> otherUnfixable = new ArrayList<>();
    List<ClearanceViolation> potentiallyFixable = new ArrayList<>();

    for (var v : violations) {
      switch (v.getCategory()) {
        case PIN_TO_PIN -> pinToPin.add(v);
        case PIN_TO_OUTLINE_OR_KEEPOUT -> pinToOutline.add(v);
        case FIXED_ROUTE -> fixedRoute.add(v);
        case OTHER_UNFIXABLE -> otherUnfixable.add(v);
        case POTENTIALLY_FIXABLE -> potentiallyFixable.add(v);
        default -> {}
      }
    }

    int totalUnfixable =
        pinToPin.size() + pinToOutline.size() + fixedRoute.size() + otherUnfixable.size();

    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Design Warning: Board has %d pre-existing clearance violation(s) in the loaded"
                + " design (before routing):%n",
            violations.size()));

    if (totalUnfixable > 0) {
      sb.append(
          String.format(
              "  - %d unfixable violation(s) (cannot be resolved by Freerouting):%n",
              totalUnfixable));
      appendCategoryViolations(sb, board, "pin-to-pin clearance violations", pinToPin);
      appendCategoryViolations(
          sb, board, "pin-to-keepout / board-outline clearance violations", pinToOutline);
      appendCategoryViolations(sb, board, "fixed trace/via clearance violations", fixedRoute);
      appendCategoryViolations(sb, board, "other unfixable clearance violations", otherUnfixable);
    } else {
      sb.append(String.format("  - 0 unfixable violation(s)%n"));
    }

    if (!potentiallyFixable.isEmpty()) {
      int count = potentiallyFixable.size();
      if (count <= 5) {
        sb.append(
            String.format(
                "  - %d potentially fixable violation(s) (involving unfixed traces/vias):%n",
                count));
      } else {
        sb.append(
            String.format(
                "  - %d potentially fixable violation(s) (involving unfixed traces/vias, first 5"
                    + " shown):%n",
                count));
      }
      appendViolationList(sb, board, potentiallyFixable, "      ");
    } else {
      sb.append(String.format("  - 0 potentially fixable violations%n"));
    }

    if (totalUnfixable > 0) {
      sb.append(
          String.format(
              "Notice: Freerouting does not modify component placement, board outlines, or fixed"
                  + " items.%nResolving these %d unfixable violation(s) is the responsibility of"
                  + " the board author in their EDA tool (e.g. KiCad).",
              totalUnfixable));
    } else {
      sb.append(
          "Notice: Freerouting will attempt to resolve potentially fixable violations by ripping"
              + " up and rerouting traces/vias.");
    }

    return sb.toString();
  }

  private static void appendCategoryViolations(
      StringBuilder sb,
      RoutingBoard board,
      String categoryTitle,
      List<ClearanceViolation> categoryViolations) {
    if (categoryViolations.isEmpty()) {
      return;
    }
    int count = categoryViolations.size();
    if (count <= 5) {
      sb.append(String.format("      * %d %s:%n", count, categoryTitle));
    } else {
      sb.append(String.format("      * %d %s (first 5 shown):%n", count, categoryTitle));
    }
    appendViolationList(sb, board, categoryViolations, "          ");
  }

  private static void appendViolationList(
      StringBuilder sb, RoutingBoard board, List<ClearanceViolation> violations, String indent) {
    int shown = Math.min(5, violations.size());
    for (int i = 0; i < shown; i++) {
      var v = violations.get(i);
      sb.append(
          String.format(
              "%s- %s <-> %s%n",
              indent,
              describeViolationItem(board, v.firstItem),
              describeViolationItem(board, v.secondItem)));
    }
    if (violations.size() > 5) {
      sb.append(String.format("%s... and %d more%n", indent, violations.size() - 5));
    }
  }

  /**
   * Builds a human-readable descriptor for an item involved in a clearance violation, including its
   * net name if available.
   */
  private static String describeViolationItem(RoutingBoard board, Item item) {
    if (item == null) {
      return "(none)";
    }
    String name;
    if (item instanceof Pin pin) {
      Component comp =
          (board != null && board.components != null)
              ? board.components.get(pin.getComponentId())
              : null;
      String compName = comp != null ? comp.name : "?";
      String pinName =
          (comp != null && comp.getPackage() != null && pin.pinIndex < comp.getPackage().pinCount())
              ? comp.getPackage().getPin(pin.pinIndex).name
              : String.valueOf(pin.pinIndex);
      name = compName + "." + pinName;
    } else if (item instanceof BoardOutline) {
      name = "BoardOutline";
    } else if (item instanceof ComponentOutline co) {
      Component comp =
          (board != null && board.components != null)
              ? board.components.get(co.getComponentId())
              : null;
      name = "ComponentOutline(" + (comp != null ? comp.name : "?") + ")";
    } else if (item instanceof ObstacleArea) {
      name = "ObstacleArea#" + item.getId();
    } else if (item instanceof Trace) {
      name = (item.isUserFixed() ? "FixedTrace#" : "Trace#") + item.getId();
    } else if (item instanceof Via) {
      name = (item.isUserFixed() ? "FixedVia#" : "Via#") + item.getId();
    } else {
      name = item.getClass().getSimpleName() + "#" + item.getId();
    }

    String netName = null;
    if (board != null
        && board.rules != null
        && board.rules.nets != null
        && item.netNumbers != null
        && item.netNumbers.length > 0) {
      for (int netNo : item.netNumbers) {
        app.freerouting.rules.Net net = board.rules.nets.get(netNo);
        if (net != null && net.name != null && !net.name.isBlank()) {
          netName = net.name;
          break;
        }
      }
    }
    return netName != null ? name + " [net " + netName + "]" : name;
  }

  void validateBoardDesignErrors() {
    if (this.board == null) {
      return;
    }
    BoardOutline outline = this.board.getOutline();
    if (outline == null || outline.shapeCount() == 0) {
      FRLogger.warn(
          "Design Error: Board outline is missing. Routing without a defined board boundary may"
              + " lead to unconstrained routing or DRC issues.");
      return;
    }

    for (Pin pin : this.board.getPins()) {
      Point center = pin.getCenter();
      if (center != null && !outline.contains(center)) {
        Component comp = this.board.components.get(pin.getComponentId());
        String compName = comp != null ? comp.name : "Unknown";
        String pinName =
            (comp != null
                    && comp.getPackage() != null
                    && pin.pinIndex < comp.getPackage().pinCount())
                ? comp.getPackage().getPin(pin.pinIndex).name
                : String.valueOf(pin.pinIndex);
        FRLogger.warn(
            String.format(
                "Design Error: Component '%s' pin '%s' (ID %d) is outside board outline at %s.",
                compName, pinName, pin.getId(), center));
      }
    }
  }
}
