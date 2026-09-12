package app.freerouting.settings.sources;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.settings.OptimizerScoringVersion;
import app.freerouting.settings.RouterScoringVersion;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides hardcoded default values for all router settings. This has the lowest priority and
 * serves as the base for all other settings.
 *
 * <p>All {@code DEFAULT_*} constants are the single authoritative source of truth for every scoring
 * weight. Reference these constants instead of repeating magic numbers throughout the codebase or
 * in tests.
 */
public class DefaultSettings implements SettingsSource {

  // -----------------------------------------------------------------------
  // Scoring weight defaults
  // -----------------------------------------------------------------------

  /**
   * Penalty subtracted from the board score for each unrouted connection. This is intentionally the
   * largest penalty so that routing completion always dominates trace-length and via-count
   * considerations.
   */
  public static final float DEFAULT_UNROUTED_NET_PENALTY = 5_000_000.0F;

  /**
   * Penalty subtracted from the board score for each clearance (DRC) violation. Set relative to the
   * unrouted penalty to allow a balance where completing all nets with a few violations can be
   * preferred over leaving nets unrouted.
   */
  public static final float DEFAULT_CLEARANCE_VIOLATION_PENALTY = 1_000_000.0F;

  /**
   * Penalty per bend (direction-change corner) in any trace. Kept small so that bend reduction is a
   * tie-breaker after completion and clearance quality, not a primary objective.
   */
  public static final float DEFAULT_BEND_PENALTY = 10.0F;

  /**
   * Cost per via placed on a regular (non-plane) net. Via costs drive the autorouter's layer-change
   * decisions during maze search; they also appear in the board score as an absolute cost term.
   */
  public static final int DEFAULT_VIA_COSTS = 50;

  /**
   * Reduced via cost for vias that connect to a copper pour / power plane. Lower than {@link
   * #DEFAULT_VIA_COSTS} to encourage short stubs into the plane rather than long surface traces.
   */
  public static final int DEFAULT_PLANE_VIA_COSTS = 5;

  /**
   * Base ripup cost used at the start of each ripup-and-reroute pass. This is multiplied by the
   * pass number inside {@code BatchAutorouter}, so later passes are progressively more willing to
   * rip up existing traces. Note: this is a routing-control parameter; it does not appear in the
   * board score formula.
   */
  public static final int DEFAULT_START_RIPUP_COSTS = 100;

  /**
   * Cost multiplier per millimetre of trace routed in the preferred direction on a given layer. A
   * value of {@code 1.0} means "1 cost unit per mm". Board-specific geometry adjustment is applied
   * on top in {@link app.freerouting.settings.RouterSettings#applyBoardSpecificOptimizations}.
   */
  public static final double DEFAULT_PREFERRED_DIRECTION_TRACE_COST = 1.0;

  /**
   * Cost multiplier per millimetre of trace routed against the preferred direction. Set to the same
   * value as {@link #DEFAULT_PREFERRED_DIRECTION_TRACE_COST} here; {@code
   * applyBoardSpecificOptimizations} adds a board-aspect-ratio penalty on top so cross-direction
   * routing is naturally more expensive on rectangular boards.
   */
  public static final double DEFAULT_UNDESIRED_DIRECTION_TRACE_COST = 1.0;

  /**
   * Default copper-to-board-edge clearance in micrometres (0.25 mm / 250 um, IPC-2221 precision /
   * CNC routing standard).
   */
  public static final double DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM = 250.0;

  /** Default drill-hole-to-copper clearance in micrometres. Zero preserves legacy DSN behaviour. */
  public static final double DEFAULT_HOLE_CLEARANCE_UM = 0.0;

  /** Current default router score formula. */
  public static final RouterScoringVersion DEFAULT_ROUTER_SCORING_VERSION =
      RouterScoringVersion.V2_CONTINUOUS;

  /** Current default optimizer score formula. */
  public static final OptimizerScoringVersion DEFAULT_OPTIMIZER_SCORING_VERSION =
      OptimizerScoringVersion.V2_LOWER_BOUND;

  public static final float DEFAULT_ROUTER_UNROUTED_CONNECTION_WEIGHT = 1000.0F;

  /** Split between first-half and second-half unrouted weights. Default 0.5. */
  public static final float DEFAULT_ROUTER_UNROUTED_FREE_FRACTION = 0.5F;

  /**
   * Penalty for having the first half of connections still open. Half of {@link
   * #DEFAULT_ROUTER_UNROUTED_SECOND_HALF_WEIGHT} so early progress still moves the score, but less
   * than finishing the remaining nets. Together with the second-half weight this sums to 1000.
   */
  public static final float DEFAULT_ROUTER_UNROUTED_FIRST_HALF_WEIGHT = 1000.0F / 3.0F;

  /**
   * Penalty for having the last half of connections still open. Twice the first-half weight so a
   * fully open board scores 0 and a half-done board scores about 333.
   */
  public static final float DEFAULT_ROUTER_UNROUTED_SECOND_HALF_WEIGHT = 2000.0F / 3.0F;

  public static final float DEFAULT_ROUTER_CLEARANCE_COUNT_WEIGHT = 25.0F;
  public static final float DEFAULT_ROUTER_CLEARANCE_DEPTH_WEIGHT = 300.0F;
  public static final float DEFAULT_ROUTER_CLEARANCE_DEPTH_SCALE_UM = 1000.0F;
  public static final float DEFAULT_OPTIMIZER_EXCESS_LENGTH_WEIGHT = 1000.0F;
  public static final float DEFAULT_OPTIMIZER_EXCESS_VIA_WEIGHT = 2000.0F;
  public static final float DEFAULT_OPTIMIZER_EXCESS_BEND_WEIGHT = 500.0F;
  public static final float DEFAULT_OPTIMIZER_LENGTH_FLOOR = 1.0F;
  public static final float DEFAULT_OPTIMIZER_DIFFICULTY_SCALE_FLOOR = 1.0F;

  /**
   * Relative optimizer-pass improvement percentage below which {@code BatchOptimizer} stops. The
   * comparison is {@code (scoreAfter - scoreBefore) / scoreBefore * 100}, expressed directly as an
   * actual percentage (e.g. 2.5 = 2.5%). Default is 2.5 (2.5%), providing optimal balance between
   * via elimination and runtime.
   */
  public static final float DEFAULT_OPTIMIZER_IMPROVEMENT_THRESHOLD = 2.5F;

  private static final int PRIORITY = 0;

  @Override
  public RouterSettings getSettings() {
    // Create a RouterSettings object with all default values.
    // Layer-count-dependent arrays (layers,
    // preferredDirectionTraceCost, undesiredDirectionTraceCost) are intentionally left null
    // here. Their actual sizes must come from the board file (via DsnFileSettings) and are
    // finalised by RouterSettings.applyBoardSpecificOptimizations() once the board is loaded.
    // Hardcoding a size-2 default causes incorrect behaviour for any board that is not a
    // 2-layer design.
    RouterSettings settings = new RouterSettings();

    settings.autorouter.enabled = true;
    settings.autorouter.algorithm = RouterSettings.ALGORITHM_CURRENT;
    settings.jobTimeoutString = "12:00:00";
    settings.autorouter.maxPasses = 0;
    settings.autorouter.maxItems = Integer.MAX_VALUE;
    settings.tracePullTightAccuracy = 500;
    settings.viasAllowed = true;
    settings.automaticNeckdown = true;
    settings.autorouter.saveIntermediateStages = false;
    settings.autorouter.ignoreNetClasses = new String[0];
    settings.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    settings.autorouter.maxThreads = settings.maxThreads;
    settings.copperToEdgeClearanceUm = DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM;
    settings.holeClearanceUm = DEFAULT_HOLE_CLEARANCE_UM;
    settings.neckWidthUm = 0.0;
    settings.strictDrc = false;

    // layers is left null intentionally –
    // they will be populated by DsnFileSettings (from the DSN layer count) and then
    // overwritten with board-geometry-aware values by applyBoardSpecificOptimizations().

    // Fanout pre-pass defaults
    settings.fanout.enabled = true;
    settings.fanout.maxPasses = 20;
    settings.fanout.maxMillisecondsPerPin = 10000L;
    settings.fanout.ripupAllowed = true;
    settings.fanout.minEscapeLengthMm = 2.5;
    settings.fanout.maxEscapeLengthMm = 4.5;
    settings.fanout.startViaDiameterMm = 0.250;
    settings.fanout.endViaDiameterMm = 0.250;
    settings.fanout.pinSortingOrder = "outer_first";
    settings.fanout.maxItems = Integer.MAX_VALUE;
    settings.fanout.fallbackToBoardVias = true;

    // Optimizer defaults
    settings.optimizer.enabled = true;
    settings.optimizer.algorithm = "freerouting-optimizer";
    settings.optimizer.maxPasses = 100;
    settings.optimizer.maxItems = Integer.MAX_VALUE;
    settings.optimizer.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    settings.optimizer.optimizationImprovementThreshold = DEFAULT_OPTIMIZER_IMPROVEMENT_THRESHOLD;
    settings.optimizer.boardUpdateStrategy = BoardUpdateStrategy.GLOBAL_OPTIMAL;
    settings.optimizer.itemSelectionStrategy = ItemSelectionStrategy.SEQUENTIAL;
    settings.optimizer.additionalRipupCostFactorAtStart = 10;
    settings.optimizer.traceRipupCostFactor = 0.6f;
    settings.optimizer.maxAutoroutePasses = 6;
    settings.optimizer.enablePreflightGuards = true;
    settings.optimizer.maxConsecutiveFailures = 50;
    settings.optimizer.maxConsecutiveFailuresPass1 = 12;

    // Scalar trace-cost defaults (layer-specific arrays are omitted for the same reason as
    // the layer arrays above – their sizes depend on the board).
    settings.scoring.defaultPreferredDirectionTraceCost = DEFAULT_PREFERRED_DIRECTION_TRACE_COST;
    settings.scoring.defaultUndesiredDirectionTraceCost = DEFAULT_UNDESIRED_DIRECTION_TRACE_COST;

    settings.scoring.viaCosts = DEFAULT_VIA_COSTS;
    settings.scoring.planeViaCosts = DEFAULT_PLANE_VIA_COSTS;
    settings.scoring.startRipupCosts = DEFAULT_START_RIPUP_COSTS;
    settings.scoring.unroutedNetPenalty = DEFAULT_UNROUTED_NET_PENALTY;
    settings.scoring.clearanceViolationPenalty = DEFAULT_CLEARANCE_VIOLATION_PENALTY;
    settings.scoring.bendPenalty = DEFAULT_BEND_PENALTY;
    settings.scoring.defaultBendCost = 0.0;

    settings.routerScoring.version = DEFAULT_ROUTER_SCORING_VERSION;
    settings.routerScoring.unroutedConnectionWeight = DEFAULT_ROUTER_UNROUTED_CONNECTION_WEIGHT;
    settings.routerScoring.unroutedFreeFraction = DEFAULT_ROUTER_UNROUTED_FREE_FRACTION;
    settings.routerScoring.unroutedFirstHalfWeight = DEFAULT_ROUTER_UNROUTED_FIRST_HALF_WEIGHT;
    settings.routerScoring.unroutedSecondHalfWeight = DEFAULT_ROUTER_UNROUTED_SECOND_HALF_WEIGHT;
    settings.routerScoring.clearanceViolationCountWeight = DEFAULT_ROUTER_CLEARANCE_COUNT_WEIGHT;
    settings.routerScoring.clearanceViolationDepthWeight = DEFAULT_ROUTER_CLEARANCE_DEPTH_WEIGHT;
    settings.routerScoring.clearanceViolationDepthScale = DEFAULT_ROUTER_CLEARANCE_DEPTH_SCALE_UM;

    settings.optimizerScoring.version = DEFAULT_OPTIMIZER_SCORING_VERSION;
    settings.optimizerScoring.excessWireLengthWeight = DEFAULT_OPTIMIZER_EXCESS_LENGTH_WEIGHT;
    settings.optimizerScoring.excessViaWeight = DEFAULT_OPTIMIZER_EXCESS_VIA_WEIGHT;
    settings.optimizerScoring.excessBendWeight = DEFAULT_OPTIMIZER_EXCESS_BEND_WEIGHT;
    settings.optimizerScoring.lengthFloor = DEFAULT_OPTIMIZER_LENGTH_FLOOR;
    settings.optimizerScoring.difficultyScaleFloor = DEFAULT_OPTIMIZER_DIFFICULTY_SCALE_FLOOR;

    return settings;
  }

  @Override
  public String getSourceName() {
    return "Default Settings";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
