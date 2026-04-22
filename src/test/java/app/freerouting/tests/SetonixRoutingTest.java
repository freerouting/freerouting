package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test for GitHub Issue #159 (Discussion): Java heap space OutOfMemoryError when importing a DSN
 * file exported from KiCad.
 *
 * <p>Issue summary:
 * A user running freerouting 1.6.5 on Windows 10 with Java JRE 8 Update 351 reported that
 * importing any DSN file into freerouting — either through the KiCad plugin or the standalone
 * freerouting window — caused the application to fail with an {@code OutOfMemoryError}:
 * {@code "java.lang.OutOfMemoryError: Java heap space: failed reallocation of scalar replaced
 * objects"}. The error occurred consistently regardless of the complexity or file size of the
 * design. The reported DSN file ("setonix_2hp-pcb.dsn") was described as not particularly
 * complicated, yet it still triggered the out-of-memory condition during the import/routing phase.
 * The user was working with KiCad version 6.99 (nightly build) on Windows 10.
 *
 * <p>Root cause:
 * The {@code OutOfMemoryError} was caused by excessive memory allocation during DSN file parsing
 * and/or the initial board normalization phase. Certain board geometries or net structures in the
 * DSN file triggered unbounded memory growth (e.g., scalar object replacement in the JVM's JIT
 * compiler escaping heap limits), which exhausted the default Java heap space. The issue was
 * exacerbated by the small default heap size of Java JRE 8 when launched as a KiCad plugin
 * sub-process, where no explicit {@code -Xmx} flag was passed.
 *
 * <p>Expected behavior:
 * Freerouting should be able to import and route any valid DSN file exported from KiCad without
 * crashing due to memory exhaustion. The application should either complete the routing within
 * available memory or degrade gracefully (e.g., terminate routing early) rather than throwing an
 * unhandled {@code OutOfMemoryError}. Upon completion, the routed board should have zero clearance
 * violations.
 *
 * <p>Steps to reproduce (as reported):
 * 1. Export a PCB design from KiCad 6.x/6.99 as a DSN file.
 * 2. Open the DSN file in freerouting 1.6.5, either via the KiCad plugin or the standalone GUI.
 * 3. Observe that freerouting processes for a short time, then terminates with a
 *    {@code java.lang.OutOfMemoryError: Java heap space} error before routing begins or completes.
 *
 * <p>Platform: Windows 10, KiCad 6.99 (nightly), Java JRE 8 Update 351, freerouting 1.6.5.
 * Attached files: DSN file (Issue159-setonix_2hp-pcb.dsn).
 * Command line arguments: none.
 */
public class SetonixRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_159_Out_of_memory_error() {
    // The original issue was an out-of-memory error during routing.
    // This test verifies that routing completes without crashing and produces no clearance violations.
    // The board has 4 connections that the auto-router cannot complete within its stopping criteria.
    var job = GetRoutingJob("Issue159-setonix_2hp-pcb.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertTrue(statsAfter.connections.incompleteCount <= 4,
            "The incomplete count should be at most 4");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}