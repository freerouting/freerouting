package app.freerouting.autoroute.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.autoroute.accelerator.AcceleratorRegistry;
import app.freerouting.autoroute.accelerator.ComputeBackend;
import app.freerouting.autoroute.accelerator.opencl.PanamaOpenClBackend;
import app.freerouting.autoroute.fanout.BgaBufferMarshaller.MarshaledBgaData;
import app.freerouting.autoroute.fanout.GpuBgaEscapeSolver.PinEscapeProposal;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** End-to-end parity and DRC verification test for GPU BGA escape routing. */
class GpuBgaEscapeParityTest extends RoutingFixtureTest {

  @Test
  void testGpuVersusCpuWavefrontParityOnHardBgaFixture() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard("BGA_Escape_Test_HARD_v2.dsn");
    RouterSettings settings = new RouterSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.fanout.gpuAccelerationEnabled = true;
    settings.fanout.gpuMinBgaPins = 64;

    List<BgaEscapeDescriptor> bgas = BgaFootprintDetector.detect(board, settings, null);
    assertEquals(1, bgas.size(), "Must detect 1 BGA (Lattice caBGA-381)");
    BgaEscapeDescriptor bga = bgas.get(0);

    AcceleratorRegistry.reset();
    ComputeBackend backend = AcceleratorRegistry.discover(settings.fanout);
    assumeTrue(
        backend.isAvailable() && backend instanceof PanamaOpenClBackend,
        "Skipping GPU vs CPU parity comparison: No active OpenCL hardware backend ("
            + backend.fallbackReason()
            + ")");

    List<PinEscapeProposal> cpuProposals;
    try (MarshaledBgaData data = BgaBufferMarshaller.marshal(board, bga, settings)) {
      cpuProposals = GpuBgaEscapeSolver.executeDeterministicWavefront(board, bga, data);
    }

    List<PinEscapeProposal> gpuProposals = GpuBgaEscapeSolver.solve(board, bga, settings, null);

    assertNotNull(cpuProposals);
    assertNotNull(gpuProposals);
    assertEquals(bga.pins().size(), cpuProposals.size());
    assertEquals(bga.pins().size(), gpuProposals.size());

    long cpuEscaped = cpuProposals.stream().filter(PinEscapeProposal::escaped).count();
    long gpuEscaped = gpuProposals.stream().filter(PinEscapeProposal::escaped).count();

    System.out.println(
        "Parity Comparison on caBGA-381: CPU Wavefront="
            + cpuEscaped
            + " escaped, GPU OpenCL="
            + gpuEscaped
            + " escaped (delta="
            + Math.abs(gpuEscaped - cpuEscaped)
            + ")");

    // Assert that GPU achieves at least 95% of CPU escape count, and both escape the vast majority
    // of pins
    assertTrue(
        cpuEscaped >= 360,
        "CPU deterministic wavefront should escape at least 360 pins, got " + cpuEscaped);
    assertTrue(
        gpuEscaped >= 360, "GPU OpenCL kernels should escape at least 360 pins, got " + gpuEscaped);
    assertTrue(
        Math.abs(gpuEscaped - cpuEscaped) <= 15,
        "Escape count divergence between GPU ("
            + gpuEscaped
            + ") and CPU ("
            + cpuEscaped
            + ") must be within tolerance <= 15");
  }

  @Test
  void testSmokeBgaFixtureZeroDrcClearanceViolations() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(50);

    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", settings);
    assertNotNull(job);
    job = runRoutingJob(job);
    assertNotNull(job.board);

    // Verify full board DRC produces 0 clearance violations
    List<?> violations =
        new java.util.ArrayList<>(
            new DesignRulesChecker(job.board, null).getAllClearanceViolations());
    assertEquals(0, violations.size(), "Board must have 0 DRC clearance violations");
  }

  @Test
  @Tag("slow")
  void testExtendedBgaParityDeterminism() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(50);

    RoutingJob job1 = runRoutingJob(getRoutingJob("Issue508-DAC2020_bm01.dsn", settings));
    String hash1 = job1.board.getHash();

    RoutingJob job2 = runRoutingJob(getRoutingJob("Issue508-DAC2020_bm01.dsn", settings));
    String hash2 = job2.board.getHash();

    assertEquals(hash1, hash2, "Repeated BGA fanout runs must produce identical board state");
  }
}
