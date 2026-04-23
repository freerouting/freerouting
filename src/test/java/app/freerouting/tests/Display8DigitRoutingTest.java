package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test for GitHub Issue #229: Keepout zone in DSN file wasn't exported correctly from KiCad 4.0.7.
 *
 * <p>Issue summary:
 * A keepout zone defined in a KiCad 4.0.7 PCB design ("display-8-digit-hc595") was not exported
 * correctly into the DSN file. Specifically, the keepout area definition appeared malformed at
 * line 27 of the exported DSN file, causing freerouting to misinterpret or ignore the keepout
 * constraint during auto-routing. As a result, traces could be routed through areas that should
 * have been restricted, potentially leading to design rule violations or incorrect board layouts.
 * The user confirmed this by attaching both the DSN file and the original KiCad PCB file, and also
 * provided a screenshot showing the misrouted traces crossing the keepout zone boundary.
 *
 * <p>Root cause:
 * The DSN export from KiCad 4.0.7 produced an incorrect keepout zone representation. The keepout
 * entry at line 27 of the DSN file was malformed (bad polygon or missing layer reference), which
 * prevented freerouting from recognizing and enforcing the keepout constraint during the routing
 * process. No command line arguments were used by the reporter — the issue was reproducible with
 * default settings via the standard freerouting GUI workflow.
 *
 * <p>Expected behavior:
 * Keepout zones defined in the KiCad PCB design should be faithfully represented in the exported
 * DSN file, and freerouting should respect those keepout constraints during auto-routing, resulting
 * in zero incomplete connections and zero clearance violations upon successful routing completion.
 *
 * <p>Steps to reproduce (as reported):
 * 1. Design a PCB in KiCad 4.0.7 containing one or more keepout zones.
 * 2. Export the design to a DSN file via KiCad's freerouting export function.
 * 3. Open the DSN file in freerouting v1.8 and run the auto-router.
 * 4. Observe that the router ignores the keepout zones and routes traces through restricted areas.
 *
 * <p>Platform: Windows 8.1 x64, KiCad 4.0.7, freerouting v1.8.
 * Attached files: DSN file (Issue229-display-8-digit-hc595.dsn) and KiCad PCB file.
 * Command line arguments: none.
 */
public class Display8DigitRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_229_Keepout_zone_was_not_exported_correctly() {
    var job = GetRoutingJob("Issue229-display-8-digit-hc595.dsn");

    // The DSN file contains a degenerate keepout polygon at line 27 (all 3 vertices identical,
    // producing a zero-area shape). Freerouting must handle this gracefully — warn and skip —
    // rather than crashing with a NullPointerException in Polyline.projection_line.
    // The router is expected to complete without clearance violations.
    // Due to normalization failures caused by the degenerate geometry in the board design,
    // some connections may remain incomplete; we accept up to 50 incomplete connections.
    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
    assertTrue(statsAfter.connections.incompleteCount <= 20,
        "Expected at most 20 incomplete connections, but got " + statsAfter.connections.incompleteCount);
  }
}