package app.freerouting.fixtures;

import app.freerouting.board.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests that validate correct multi-layer board handling.
 *
 * <p><b>Background:</b><br>
 * {@code DefaultSettings} previously hard-coded a {@code defaultLayerCount = 2} and initialized
 * the layer arrays ({@code isLayerActive}, {@code isPreferredDirectionHorizontalOnLayer}, and the
 * per-layer trace-cost arrays) with exactly 2 entries. Any board that has more than 2 signal
 * layers therefore received incorrectly-sized arrays from the settings merger before
 * {@code applyBoardSpecificOptimizations()} was called, leading to:
 * <ul>
 *   <li>Inner layers being invisible to (or silently ignored by) the router until the first call
 *       to {@code applyBoardSpecificOptimizations}.</li>
 *   <li>Settings sources at lower priority than {@code DsnFileSettings} (e.g. CLI or API) being
 *       able to overwrite the correctly-sized arrays with the stale size-2 defaults.</li>
 *   <li>Any code that reads layer settings between {@code merger.merge()} and
 *       {@code applyBoardSpecificOptimizations()} seeing wrong data.</li>
 * </ul>
 *
 * <p><b>Fix:</b><br>
 * {@code DefaultSettings} no longer initialises the layer arrays at all (they are left
 * {@code null}). {@code DsnFileSettings} always calls {@code RouterSettings.setLayerCount(n)}
 * using the layer count extracted from the DSN {@code (structure (layer …))} declarations, so
 * the merged settings have correctly-sized arrays as soon as the DSN file is parsed –
 * independently of whether an {@code (autoroute_settings …)} block is present in the file.
 * {@code applyBoardSpecificOptimizations()} then refines the per-layer cost values based on
 * the actual board geometry.
 *
 * <p><b>Covered boards:</b><br>
 * <ul>
 *   <li>{@code Issue066-Project_GP8B.dsn} — 4-layer board (F.Cu / In1.Cu / In2.Cu / B.Cu),
 *       266 connections, no {@code (autoroute_settings)} block (exercises the "DsnFileSettings
 *       must set layer count even without an autoroute block" path).</li>
 *   <li>{@code Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn} — 6-layer board
 *       (layers 1, 2, 21, 22, 23, 24), 142 connections, no {@code (autoroute_settings)} block.</li>
 * </ul>
 */
public class MultiLayerBoardRoutingTest extends RoutingFixtureTest {

    /**
     * Verifies that a 4-layer board is correctly handled by the settings merger.
     *
     * <p>Specifically:
     * <ol>
     *   <li>After {@code GetRoutingJob} (which runs {@code merger.merge()} but NOT yet
     *       {@code applyBoardSpecificOptimizations}), the layer count in
     *       {@code job.routerSettings} must already be 4 — not the former hard-coded 2.</li>
     *   <li>After routing, traces must be present on at least one inner layer
     *       (layer index 1 or 2), confirming that the router actually used the full
     *       4-layer stack rather than being constrained to the outer 2 layers.</li>
     * </ol>
     */
    @Test
    void test_4layer_board_issue066_layer_count_and_inner_layer_usage() {
        TestingSettings testingSettings = new TestingSettings();
        // Keep CI-friendly: this test validates DSN-derived layer handling, not fanout quality.
        // Disable the expensive SMD fanout pre-pass so the bounded autorouter slice can still
        // demonstrate inner-layer usage without timing out on 800+ SMD pins.
        testingSettings.setFanoutEnabled(false);
        testingSettings.setMaxPasses(2);
        testingSettings.setMaxItems(60);
        testingSettings.setJobTimeoutString("00:02:00");

        RoutingJob job = GetRoutingJob("Issue066-Project_GP8B.dsn", testingSettings);

        // --- Pre-routing: layer count must be read from the DSN, not defaulted to 2 ---
        // The merger has run but applyBoardSpecificOptimizations has NOT yet been called.
        // With the fix DsnFileSettings seeds the layer arrays from the DSN metadata (4 layers),
        // so getLayerCount() must already return 4 here.
        assertEquals(4, job.routerSettings.getLayerCount(),
            "Before routing, routerSettings.getLayerCount() should be 4 for"
                + " 'Issue066-Project_GP8B.dsn' (F.Cu / In1.Cu / In2.Cu / B.Cu)."
                + " If it returns 2 the DefaultSettings hard-coded layer count is still active.");

        RunRoutingJob(job);

        // --- Post-routing: layer count must still be 4 ---
        assertEquals(4, job.routerSettings.getLayerCount(),
            "After routing, routerSettings.getLayerCount() should be 4 for"
                + " 'Issue066-Project_GP8B.dsn'.");

        // --- Post-routing: at least one trace must exist on an inner layer ---
        // For a 4-layer board the inner signal layers are indices 1 (In1.Cu) and 2 (In2.Cu).
        // If the router was inadvertently restricted to size-2 layer arrays before the fix,
        // it would never place traces on these layers.
        Set<Integer> layersWithTraces = job.board.get_traces().stream()
            .map(Trace::get_layer)
            .collect(Collectors.toSet());

        assertTrue(layersWithTraces.contains(1) || layersWithTraces.contains(2),
            "After routing 'Issue066-Project_GP8B.dsn', at least one trace should exist"
                + " on an inner layer (In1.Cu=1 or In2.Cu=2). Layers with traces: "
                + layersWithTraces + ". If no inner-layer traces exist the router may"
                + " still be constrained to 2 layers.");
    }

    /**
     * Verifies that a 6-layer board is correctly handled by the settings merger.
     *
     * <p>Specifically:
     * <ol>
     *   <li>After {@code GetRoutingJob}, the layer count in {@code job.routerSettings} must
     *       be 6 — not the former hard-coded 2.</li>
     *   <li>After routing, at least one trace must exist on an inner layer
     *       (layer index ≥ 1 and ≤ 4), confirming the router uses the full 6-layer stack.</li>
     * </ol>
     */
    @Test
    void test_6layer_board_issue289_layer_count_and_inner_layer_usage() {
        TestingSettings testingSettings = new TestingSettings();
        testingSettings.setFanoutEnabled(false);
        testingSettings.setMaxPasses(2);
        testingSettings.setMaxItems(50);
        testingSettings.setJobTimeoutString("00:02:00");

        RoutingJob job = GetRoutingJob("Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn", testingSettings);

        // --- Pre-routing: layer count must be read from the DSN, not defaulted to 2 ---
        assertEquals(6, job.routerSettings.getLayerCount(),
            "Before routing, routerSettings.getLayerCount() should be 6 for"
                + " 'Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn' (layers 1/2/21/22/23/24)."
                + " If it returns 2 the DefaultSettings hard-coded layer count is still active.");

        RunRoutingJob(job);

        // --- Post-routing: layer count must still be 6 ---
        assertEquals(6, job.routerSettings.getLayerCount(),
            "After routing, routerSettings.getLayerCount() should be 6 for"
                + " 'Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn'.");

        // --- Post-routing: at least one trace must exist on an inner layer ---
        // For this 6-layer board the inner signal layers are indices 1 through 4.
        Set<Integer> layersWithTraces = job.board.get_traces().stream()
            .map(Trace::get_layer)
            .collect(Collectors.toSet());

        boolean hasInnerLayerTrace = layersWithTraces.stream().anyMatch(l -> l >= 1 && l <= 4);
        assertTrue(hasInnerLayerTrace,
            "After routing 'Issue289-Autorouter_PCB_FHT-8086_2024-03-08.dsn', at least one"
                + " trace should exist on an inner layer (index 1-4). Layers with traces: "
                + layersWithTraces + ". If no inner-layer traces exist the router may"
                + " still be constrained to 2 layers.");
    }
}