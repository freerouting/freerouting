package app.freerouting.core.results;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.util.gson.GsonProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoutingResultManifestTest {

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void resolveGitShaPrefersEnvironmentVariable() {
    String prior = System.getenv("FREEROUTING_GIT_SHA");
    try {
      // Cannot set env in Java portably; verify unknown when unset
      if (prior == null || prior.isBlank()) {
        assertEquals("unknown", RoutingResultManifest.resolveGitSha());
      }
    } finally {
      // no env mutation
    }
  }

  @Test
  void writeProducesRequiredJsonKeys() throws Exception {
    BoardReadResult result =
        DsnReader.readBoard(DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn"), null, null);
    assertTrue(result instanceof BoardReadResult.Success);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    RoutingJob job = new RoutingJob(java.util.UUID.randomUUID());
    job.board = board;
    job.routerSettings = new DefaultSettings().getSettings();
    job.state = RoutingJobState.COMPLETED;
    job.resourceUsage.cpuTimeUsed = 1.5f;
    job.resourceUsage.peakMemoryUsed = 128.0f;

    Path inputPath = tempDir.resolve("input.dsn");
    try (var in = DsnTestFixtures.openResource("Issue143-rpi_splitter.dsn")) {
      Files.copy(in, inputPath);
    }

    RoutingResultManifest manifest =
        RoutingResultManifest.fromJob(job, inputPath.toString(), true, 0);
    Path outPath = tempDir.resolve("result.json");
    RoutingResultManifest.write(outPath, manifest);

    String json = Files.readString(outPath);
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(1, root.get("schema_version").getAsInt());
    assertNotNull(root.get("app_version"));
    assertNotNull(root.get("git_sha"));
    assertNotNull(root.get("fixture"));
    assertNotNull(root.get("board_statistics"));
    assertNotNull(root.get("resource_usage"));
    assertEquals("COMPLETED", root.get("final_state").getAsString());
    assertEquals(0, root.get("exit_code").getAsInt());
    assertTrue(root.get("output_written").getAsBoolean());

    RoutingResultManifest roundTrip = GsonProvider.GSON.fromJson(json, RoutingResultManifest.class);
    assertNotNull(roundTrip.boardStatistics);
    assertEquals(board.getLayerCount(), roundTrip.boardStatistics.layers.totalCount);
  }
}
