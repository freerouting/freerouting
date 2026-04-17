package app.freerouting.tests;

import app.freerouting.board.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to GitHub Issue #230: "Auto-router uses inactive layers if via cost set to 45 or lower"
 *
 * <p><b>Summary:</b><br>
 * When the via cost is set to 45 or lower in the freerouting settings, the auto-router places
 * traces on PCB layers that have been explicitly marked as inactive (i.e., excluded from routing).
 * In the reported case, the user's board had a 4-layer stackup in KiCad 7.0.7 where the two inner
 * copper layers ({@code In1.Cu} and {@code In2.Cu}) were designated as power planes and were
 * intentionally not listed/enabled in freerouting's layer configuration. Despite this, the router
 * produced traces on those inner power layers — visually confirmed as green routes appearing on the
 * power plane layers after importing the session back into KiCad.
 *
 * <p><b>Detailed Description:</b><br>
 * The reporter's board has the following 4-layer copper stackup as defined in KiCad 7.0.7:
 * <ol>
 *   <li>{@code F.Cu}   — Top signal layer (enabled for routing)</li>
 *   <li>{@code In1.Cu} — Inner layer 1, assigned as a power plane (NOT enabled for routing)</li>
 *   <li>{@code In2.Cu} — Inner layer 2, assigned as a power plane (NOT enabled for routing)</li>
 *   <li>{@code B.Cu}   — Bottom signal layer (enabled for routing)</li>
 * </ol>
 * When the DSN file is imported into freerouting, the inner power-plane layers ({@code In1.Cu}
 * and {@code In2.Cu}) do not appear in freerouting's layer list at all — they are effectively
 * invisible to the router, which should mean the router has no knowledge of and no permission to
 * use those layers.
 *
 * <p>The problem manifests when the user opens the freerouting "Interactive Router Settings"
 * dialog and lowers the <em>via cost</em> parameter to 45 or below. With the default via cost
 * (which is higher), the router correctly confines all traces to {@code F.Cu} and {@code B.Cu}.
 * But as soon as the via cost drops to ≤ 45, the router begins placing wire segments on
 * {@code In1.Cu} (and potentially {@code In2.Cu}), which are the layers that were never
 * exposed to freerouting's layer-selection UI. The illegal traces are written into the output
 * SES file and become visible as green routes on the power-plane layers once the session is
 * back-annotated into KiCad.
 *
 * <p>The reporter confirmed the bug is reproducible with both the KiCad freerouting plugin
 * (version 1.8.0) and the standalone freerouting application (version 1.8, dated 2023-05-22),
 * ruling out any plugin-specific wrapping as the cause. No special command-line arguments are
 * needed to trigger it; simply adjusting the via cost slider in the GUI is sufficient.
 *
 * <p><b>Root Cause / Observed Behavior:</b><br>
 * The exported DSN file actually contains all 4 copper layers; freerouting correctly omits the
 * power-plane layers from its UI (the "Parameter / Select" and "Parameter / Autoroute" windows),
 * but — critically — the omission is only cosmetic. The routing algorithm itself still has
 * access to those layers and, under the right cost conditions, will use them.
 *
 * <p>The specific trigger is a via cost of ≤ 45 in the
 * "Settings / Auto-router / Detailed Settings" dialog. Lowering the via cost to 45 (and pressing
 * Enter to apply) and then starting the auto-router is sufficient to reproduce the issue — no
 * Fanout or Post-route options need to be enabled. At via cost 46 the bug does not occur.
 * A notable side effect of this threshold is a dramatic increase in routing passes: in the
 * reporter's tests the pass count jumped from 5 (cost = 46) to 210 (cost = 45), suggesting
 * that the router is taking many additional, otherwise-forbidden layer transitions.
 *
 * <p>The confirmed root cause is the {@code LocateFoundConnectionAlgo} class, which is part of
 * the auto-routing routine. Its connection-location logic suggested wire modifications that
 * could result in traces on inactive layers in certain edge cases. When the via cost was high
 * enough these suggestions were implicitly rejected by the cost model; at ≤ 45 the cost model
 * no longer suppressed them, allowing illegal layer assignments to propagate into the routed
 * output. In effect, the inactive-layer setting acted as a soft cost rather than a hard
 * exclusion, and a sufficiently low via cost was able to outweigh it.
 *
 * <p><b>Affected Versions:</b><br>
 * Reported on freerouting plugin 1.8.0 and standalone freerouting 1.8 (2023-05-22).
 *
 * <p><b>Environment:</b><br>
 * <ul>
 *   <li>Platform: Windows 10 (64-bit)</li>
 *   <li>EDA: KiCad 7.0.7 with the freerouting plugin</li>
 *   <li>Board: 4-layer PCB with {@code In1.Cu} and {@code In2.Cu} configured as power planes
 *       and excluded from the freerouting layer list</li>
 *   <li>Reproducible with both the KiCad freerouting plugin and the standalone JAR</li>
 * </ul>
 *
 * <p><b>Steps to Reproduce:</b><br>
 * <ol>
 *   <li>Open a multi-layer PCB in KiCad that has inner layers assigned as power planes.</li>
 *   <li>Export the board to a DSN file and open it in freerouting (plugin or standalone).</li>
 *   <li>In the freerouting "Interactive Router Settings" dialog, set the via cost to 45 or lower.</li>
 *   <li>Start the auto-router.</li>
 *   <li>Import the resulting SES session file back into KiCad.</li>
 *   <li>Observe that traces have been placed on the inner power-plane layers ({@code In1.Cu},
 *       {@code In2.Cu}) that were not enabled for routing — visible as green routes on the
 *       power plane layers in the KiCad PCB editor.</li>
 * </ol>
 *
 * <p><b>Suggested Improvement (from maintainer):</b><br>
 * A cleaner long-term solution would be to display power-plane layers in both the
 * "Parameter / Select" and "Parameter / Autoroute" UI windows, but have auto-routing
 * disabled for them by default. This would make their existence explicit to the user and
 * allow intentional opt-in rather than relying on silent omission.
 *
 * <p><b>Fix:</b><br>
 * The routing pipeline must be patched so that inactive layers are treated as hard
 * constraints — absolute exclusions — in every decision path: initial net routing, via
 * placement, layer-transition logic, and post-route trace optimisation. The primary
 * candidate class is {@code LocateFoundConnectionAlgo}, whose wire-modification
 * suggestions must be validated against layer activity before being committed.
 * Additionally, the {@code MazeSearchAlgo} expansion logic should be reviewed to ensure
 * that expansion rooms on inactive layers are never offered as candidates, regardless of
 * the via-cost setting. Until these guards are in place the test below acts as the
 * regression sentinel: it asserts that zero traces land on {@code In1.Cu} or
 * {@code In2.Cu} after a full routing run with the default via cost.
 *
 * <p><b>Expected Behavior:</b><br>
 * The router must never place traces on layers that are marked as inactive / not enabled for
 * routing, regardless of the via cost setting. Inactive layers must be treated as hard
 * constraints — absolute exclusions — not soft preferences that can be overridden by a low
 * via cost. The layer-activity check must be applied unconditionally in every part of the
 * routing pipeline (initial routing, via placement, layer transition, and trace optimization).
 *
 * <p><b>Impact:</b><br>
 * Critical — placing copper traces on power plane layers causes short circuits between signal
 * nets and the power/ground planes, directly violates the designer's intent, and makes the
 * routed board electrically unusable without extensive manual correction. The reporter
 * confirmed this issue is blocking for their production workflow.
 *
 * @see <a href="https://github.com/freerouting/freerouting/issues/230">GitHub Issue #230</a>
 */
public class Issue230Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  void test_Issue_230_Wires_on_inactive_layers() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:05:00");

    // Get a routing job
    job = GetRoutingJob("Issue230-CNH_Functional_Tester_1.dsn", testingSettings);

    // Run the job and measure elapsed time via the job's own timestamps
    RunRoutingJob(job);
    long elapsedMs = java.time.Duration.between(job.startedAt, job.finishedAt).toMillis();

    // --- Timing ---
    assertTrue(elapsedMs < 300_000,
        "Routing of 'Issue230-CNH_Functional_Tester_1.dsn' should complete in under 5 minutes, but took " + elapsedMs + " ms.");

    // --- Routing quality (based on observed realistic results for this board) ---
    assertTrue(job.getCurrentPass() <= 20,
        "Routing of 'Issue230-CNH_Functional_Tester_1.dsn' required too many passes: " + job.getCurrentPass() + " (expected <= 20).");
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 5,
        "Routing of 'Issue230-CNH_Functional_Tester_1.dsn' left too many unrouted connections: "
            + job.board.get_statistics().connections.incompleteCount + " (expected <= 5).");

    // --- Core bug check for Issue #230 ---
    // The board has 4 copper layers: F.Cu (0, signal/active), In1.Cu (1, power/inactive),
    // In2.Cu (2, power/inactive), B.Cu (3, signal/active).
    // The router MUST NOT place any traces on the inactive power-plane layers In1.Cu or In2.Cu,
    // regardless of the configured via cost. This was the confirmed root cause of Issue #230.
    long tracesOnPowerLayers = job.board.get_traces().stream()
        .filter(t -> t.get_layer() == 1 || t.get_layer() == 2)
        .count();
    assertEquals(0L, tracesOnPowerLayers,
        "The router placed " + tracesOnPowerLayers + " trace(s) on inactive power-plane layers (In1.Cu or In2.Cu). "
            + "Traces must never be placed on layers that are not marked as signal layers in the DSN file.");
  }
}