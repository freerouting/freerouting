package app.freerouting.settings.sources;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides hardcoded default values for all router settings.
 * This has the lowest priority and serves as the base for all other settings.
 *
 * <p>All {@code DEFAULT_*} constants are the single authoritative source of truth for every
 * scoring weight. Reference these constants instead of repeating magic numbers throughout
 * the codebase or in tests.
 */
public class DefaultSettings implements SettingsSource {

    private static final int PRIORITY = 0;

    // -----------------------------------------------------------------------
    // Scoring weight defaults
    // -----------------------------------------------------------------------

    /**
     * Penalty subtracted from the board score for each unrouted connection.
     * This is intentionally the largest penalty so that routing completion
     * always dominates trace-length and via-count considerations.
     */
    public static final float DEFAULT_UNROUTED_NET_PENALTY = 1000000.0f;

    /**
     * Penalty subtracted from the board score for each clearance (DRC) violation.
     * Should be large enough that the optimizer never accepts a routed board with
     * violations over an unrouted-but-clean board.
     */
    public static final float DEFAULT_CLEARANCE_VIOLATION_PENALTY = 1000.0f;

    /**
     * Penalty per bend (direction-change corner) in any trace.
     * Kept small so that bend reduction is a tie-breaker after completion and
     * clearance quality, not a primary objective.
     */
    public static final float DEFAULT_BEND_PENALTY = 10.0f;

    /**
     * Cost per via placed on a regular (non-plane) net.
     * Via costs drive the autorouter's layer-change decisions during maze search;
     * they also appear in the board score as an absolute cost term.
     */
    public static final int DEFAULT_VIA_COSTS = 50;

    /**
     * Reduced via cost for vias that connect to a copper pour / power plane.
     * Lower than {@link #DEFAULT_VIA_COSTS} to encourage short stubs into the
     * plane rather than long surface traces.
     */
    public static final int DEFAULT_PLANE_VIA_COSTS = 5;

    /**
     * Base ripup cost used at the start of each ripup-and-reroute pass.
     * This is multiplied by the pass number inside {@code BatchAutorouter}, so
     * later passes are progressively more willing to rip up existing traces.
     * Note: this is a routing-control parameter; it does not appear in the board
     * score formula.
     */
    public static final int DEFAULT_START_RIPUP_COSTS = 100;

    /**
     * Cost multiplier per millimetre of trace routed in the preferred direction
     * on a given layer.  A value of {@code 1.0} means "1 cost unit per mm".
     * Board-specific geometry adjustment is applied on top in
     * {@link app.freerouting.settings.RouterSettings#applyBoardSpecificOptimizations}.
     */
    public static final double DEFAULT_PREFERRED_DIRECTION_TRACE_COST = 1.0;

    /**
     * Cost multiplier per millimetre of trace routed against the preferred
     * direction.  Set to the same value as {@link #DEFAULT_PREFERRED_DIRECTION_TRACE_COST}
     * here; {@code applyBoardSpecificOptimizations} adds a board-aspect-ratio penalty on
     * top so cross-direction routing is naturally more expensive on rectangular boards.
     */
    public static final double DEFAULT_UNDESIRED_DIRECTION_TRACE_COST = 1.0;

    /** Default copper-to-board-edge clearance in micrometres (0.5 mm). */
    public static final double DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM = 500.0;

    @Override
    public RouterSettings getSettings() {
        // Create a RouterSettings object with all default values.
        // Layer-count-dependent arrays (isLayerActive, isPreferredDirectionHorizontalOnLayer,
        // preferredDirectionTraceCost, undesiredDirectionTraceCost) are intentionally left null
        // here. Their actual sizes must come from the board file (via DsnFileSettings) and are
        // finalised by RouterSettings.applyBoardSpecificOptimizations() once the board is loaded.
        // Hardcoding a size-2 default causes incorrect behaviour for any board that is not a
        // 2-layer design.
        RouterSettings settings = new RouterSettings();

        settings.enabled = true;
        settings.algorithm = RouterSettings.ALGORITHM_CURRENT;
        settings.jobTimeoutString = "12:00:00";
        settings.maxPasses = 9999;
        settings.maxItems = Integer.MAX_VALUE;
        settings.trace_pull_tight_accuracy = 500;
        settings.vias_allowed = true;
        settings.automatic_neckdown = true;
        settings.save_intermediate_stages = false;
        settings.ignoreNetClasses = new String[0];
        settings.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        settings.copperToEdgeClearanceUm = DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM;

        // isLayerActive and isPreferredDirectionHorizontalOnLayer are left null intentionally –
        // they will be populated by DsnFileSettings (from the DSN layer count) and then
        // overwritten with board-geometry-aware values by applyBoardSpecificOptimizations().

        // Fanout pre-pass defaults
        settings.fanout.enabled = true;
        settings.fanout.maxPasses = 20;
        settings.fanout.maxMillisecondsPerPin = 10000L;
        settings.fanout.ripupAllowed = true;

        // Optimizer defaults
        settings.optimizer.enabled = false;
        settings.optimizer.algorithm = "freerouting-optimizer";
        settings.optimizer.maxPasses = 100;
        settings.optimizer.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        settings.optimizer.optimizationImprovementThreshold = 0.01f;
        settings.optimizer.boardUpdateStrategy = BoardUpdateStrategy.GREEDY;
        settings.optimizer.hybridRatio = "1:1";
        settings.optimizer.itemSelectionStrategy = ItemSelectionStrategy.PRIORITIZED;

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