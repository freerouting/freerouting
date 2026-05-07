package app.freerouting.settings.sources;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Provides hardcoded default values for all router settings.
 * This has the lowest priority and serves as the base for all other settings.
 */
public class DefaultSettings implements SettingsSource {

    private static final int PRIORITY = 0;

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

        // isLayerActive and isPreferredDirectionHorizontalOnLayer are left null intentionally –
        // they will be populated by DsnFileSettings (from the DSN layer count) and then
        // overwritten with board-geometry-aware values by applyBoardSpecificOptimizations().

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
        settings.scoring.defaultPreferredDirectionTraceCost = 1.0;
        settings.scoring.defaultUndesiredDirectionTraceCost = 1.0;

        settings.scoring.via_costs = 50;
        settings.scoring.plane_via_costs = 5;
        settings.scoring.start_ripup_costs = 100;
        settings.scoring.unroutedNetPenalty = 4000.0f;
        settings.scoring.clearanceViolationPenalty = 1000.0f;
        settings.scoring.bendPenalty = 10.0f;

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