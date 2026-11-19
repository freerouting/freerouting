package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end regression and machine-readable manifest telemetry tests for the decoupling capacitor
 * engine.
 */
public class DecouplingManifestAndRegressionTest {

  @Test
  void testDecouplingManifestTelemetryRecorded(@TempDir Path tempDir) throws IOException {
    String fixture = "Issue508-DAC2020_bm01.dsn";
    RoutingBoard board = DsnTestFixtures.loadBoard(fixture);

    RoutingJob job = new RoutingJob();
    job.board = board;
    job.thread =
        new StoppableThread() {
          @Override
          protected void threadAction() {}
        };

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.autorouter.maxPasses = 1;
    settings.optimizer.maxPasses = 0;
    settings.decoupling.enabled = true;
    settings.decoupling.includeReviewCandidates = true;
    job.routerSettings = settings;

    BatchAutorouter router = new BatchAutorouter(job);
    router.runBatchLoop();

    // Verify manifest telemetry
    Path manifestPath = tempDir.resolve("routing_result.json");
    RoutingResultManifest manifest = RoutingResultManifest.fromJob(job, fixture, true, 0, null);
    assertNotNull(manifest.phases, "Phases must be present in manifest");
    assertNotNull(manifest.phases.decoupling, "Decoupling phase must be recorded in manifest");
    assertTrue(manifest.phases.decoupling.candidatesTotal > 0, "Candidates must be counted");
    assertTrue(manifest.phases.decoupling.strapsRouted > 0, "Straps routed must be counted");
    assertTrue(
        manifest.phases.decoupling.totalStrapLengthMm > 0.0, "Strap length must be positive");
    assertTrue(manifest.phases.decoupling.durationSeconds >= 0.0f, "Duration must be recorded");

    // Write manifest to JSON and verify round-trip deserialization
    RoutingResultManifest.write(manifestPath, manifest);
    assertTrue(Files.exists(manifestPath), "Manifest file must exist");
    String json = Files.readString(manifestPath);
    assertTrue(json.contains("\"decoupling\""), "JSON must contain decoupling phase section");
    assertTrue(json.contains("\"straps_routed\""), "JSON must contain straps_routed field");
    RoutingResultManifest roundTrip =
        app.freerouting.util.gson.GsonProvider.GSON.fromJson(json, RoutingResultManifest.class);
    assertNotNull(roundTrip.phases.decoupling, "Decoupling phase must round-trip through JSON");
    assertEquals(manifest.phases.decoupling.strapsRouted, roundTrip.phases.decoupling.strapsRouted);
  }

  @Test
  void testDecouplingDeterministicParityAcrossIterations() throws IOException {
    String fixture = "Issue508-DAC2020_bm01.dsn";

    // Run 1
    RoutingBoard board1 = DsnTestFixtures.loadBoard(fixture);
    RoutingJob job1 = createJob(board1);
    BatchAutorouter router1 = new BatchAutorouter(job1);
    router1.runBatchLoop();

    // Run 2
    RoutingBoard board2 = DsnTestFixtures.loadBoard(fixture);
    RoutingJob job2 = createJob(board2);
    BatchAutorouter router2 = new BatchAutorouter(job2);
    router2.runBatchLoop();

    long traces1 = board1.getItems().stream().filter(it -> it instanceof Trace).count();
    long traces2 = board2.getItems().stream().filter(it -> it instanceof Trace).count();
    assertEquals(traces1, traces2, "Trace count must be deterministic across runs");

    long vias1 = board1.getItems().stream().filter(it -> it instanceof Via).count();
    long vias2 = board2.getItems().stream().filter(it -> it instanceof Via).count();
    assertEquals(vias1, vias2, "Via count must be deterministic across runs");

    long decapProtected1 = board1.getItems().stream().filter(Item::isDecouplingProtected).count();
    long decapProtected2 = board2.getItems().stream().filter(Item::isDecouplingProtected).count();
    assertEquals(decapProtected1, decapProtected2, "Decoupling protected items must match exactly");
    assertEquals(
        board1.getHash(),
        board2.getHash(),
        "Full board snapshot hash must match deterministically");

    // Authoritative DRC violation check
    DesignRulesChecker drc1 = new DesignRulesChecker(board1, null);
    DesignRulesChecker drc2 = new DesignRulesChecker(board2, null);
    assertEquals(0, drc1.getAllClearanceViolations().size(), "Run 1 must have 0 DRC violations");
    assertEquals(0, drc2.getAllClearanceViolations().size(), "Run 2 must have 0 DRC violations");
  }

  private RoutingJob createJob(RoutingBoard board) {
    RoutingJob job = new RoutingJob();
    job.board = board;
    job.thread =
        new StoppableThread() {
          @Override
          protected void threadAction() {}
        };
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.autorouter.maxPasses = 1;
    settings.optimizer.maxPasses = 0;
    settings.decoupling.enabled = true;
    settings.decoupling.includeReviewCandidates = true;
    job.routerSettings = settings;
    return job;
  }
}
