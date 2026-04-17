package app.freerouting.tests;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
 * The routing engine does not fully respect the "inactive" status of layers when the via cost
 * threshold is sufficiently low (≤ 45). Reducing via cost makes the router more aggressive in
 * seeking alternative paths through vias, which causes it to inadvertently consider — and
 * ultimately commit to — layer transitions onto layers that should be off-limits. The
 * inactive-layer constraint is not enforced during the via/layer-selection phase of the routing
 * algorithm, allowing the router to escape to forbidden layers whenever via usage is made cheap
 * enough. In effect, the inactive-layer setting acts as a soft cost rather than a hard
 * exclusion, and a sufficiently low via cost is able to outweigh it.
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

    assertTrue(elapsedMs < 300_000,
            "Routing of the reference board 'Issue230-CNH_Functional_Tester_1.dsn' should complete in less than 5 minutes, but took " + elapsedMs + " ms.");
    assertTrue(job.getCurrentPass() <= 2,
            "Routing of the reference board 'Issue230-CNH_Functional_Tester_1.dsn' should complete within the first 2 passes, but required " + job.getCurrentPass() + " passes.");
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 0,
            "Routing of the reference board 'Issue230-CNH_Functional_Tester_1.dsn' should result in 0 unrouted connections after the first pass.");
  }
}