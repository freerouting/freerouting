package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.FileFormat;
import app.freerouting.io.kicad.KiCadJsonReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.CompositeBoardInput;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.JsonFileSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks that effective settings, rather than a stale job snapshot, select the import policy. */
class Issue1016SeededImportPolicyTest extends RoutingFixtureTest {
  private static final String REVERSED = "Issue1016-placement-order-reversed.dsn";
  @TempDir Path directory;

  static Stream<Arguments> seedSources() {
    return Stream.of(
        Arguments.of(null, null, null),
        Arguments.of(42, null, 42),
        Arguments.of(null, 42, 42),
        Arguments.of(42, 0, 0),
        Arguments.of(42, -1, -1));
  }

  @ParameterizedTest
  @MethodSource("seedSources")
  void schedulerChoosesPolicyFromMergedSeed(Integer fileSeed, Integer apiSeed, Integer expected)
      throws Exception {
    configureFileSeed(fileSeed);
    RoutingJob job = getRoutingJob(REVERSED);
    job.routerSettings.autorouter.itemOrderSeed = apiSeed;
    job.routerSettings.optimizer.enabled = false;
    job.routerSettings.fanout.enabled = false;
    job.routerSettings.setAutomaticNeckdown(false);
    job.routerSettings.maxThreads = 1;
    runRoutingJob(job);
    assertPolicy(job, expected);
  }

  @ParameterizedTest
  @MethodSource("seedSources")
  void compositeChoosesPolicyFromMergedSeed(Integer fileSeed, Integer apiSeed, Integer expected)
      throws Exception {
    configureFileSeed(fileSeed);
    var job = new RoutingJob();
    job.routerSettings.autorouter.itemOrderSeed = apiSeed;
    try (var in = DsnTestFixtures.openResource(REVERSED)) {
      new CompositeBoardInput().setDesign(in.readAllBytes(), REVERSED).assembleBoard(job);
    }
    assertPolicy(job, expected);
  }

  @Test
  void seededCompositeJsonKeepsItsExistingAllocationPolicy() throws Exception {
    String name = "Issue649-kicad_ecc83-pp_input_board_v2.json";
    byte[] bytes;
    try (var in = DsnTestFixtures.openResource(name)) {
      bytes = in.readAllBytes();
    }
    BasicBoard direct;
    try (var reader =
        new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
      direct = ((BoardReadResult.Success) KiCadJsonReader.readBoard(reader, null, null)).board();
    }
    var job = new RoutingJob();
    job.routerSettings.autorouter.itemOrderSeed = 42;
    new CompositeBoardInput().setDesign(bytes, name).assembleBoard(job);
    assertEquals(componentIds(direct), componentIds(job.board));
    assertEquals(direct.getPins().size(), job.board.getPins().size());
    assertLegacyJsonSettings(legacyJsonJob(bytes), job);
  }

  @Test
  void seededSchedulerJsonKeepsItsExistingAllocationAndBoardSettings() throws Exception {
    String name = "Issue649-kicad_ecc83-pp_input_board_v2.json";
    byte[] bytes;
    try (var in = DsnTestFixtures.openResource(name)) {
      bytes = in.readAllBytes();
    }
    var legacyJob = legacyJsonJob(bytes);
    var job = getRoutingJob(name);
    job.output.format = FileFormat.KICAD_SESSION_JSON;
    job.routerSettings.autorouter.itemOrderSeed = 42;
    job.routerSettings.autorouter.enabled = false;
    job.routerSettings.optimizer.enabled = false;
    job.routerSettings.fanout.enabled = false;
    job.routerSettings.maxThreads = 1;
    runRoutingJob(job);
    assertEquals(componentIds(legacyJob.board), componentIds(job.board));
    assertEquals(legacyJob.board.getPins().size(), job.board.getPins().size());
    assertLegacyJsonSettings(legacyJob, job);
  }

  private static RoutingJob legacyJsonJob(byte[] bytes) {
    var legacy = new RoutingJob();
    var manager = new HeadlessBoardManager(legacy);
    manager.loadFromKiCadJson(new java.io.ByteArrayInputStream(bytes), null, null);
    legacy.board = manager.getRoutingBoard();
    return legacy;
  }

  private static void assertLegacyJsonSettings(RoutingJob legacy, RoutingJob actual) {
    assertEquals(legacy.board.getLayerCount(), actual.routerSettings.getLayerCount());
    for (int layer = 0; layer < legacy.board.getLayerCount(); layer++) {
      assertEquals(
          legacy.routerSettings.getPreferredDirectionIsHorizontal(layer),
          actual.routerSettings.getPreferredDirectionIsHorizontal(layer));
      assertEquals(
          legacy.routerSettings.getPreferredDirectionTraceCosts(layer),
          actual.routerSettings.getPreferredDirectionTraceCosts(layer));
      assertEquals(
          legacy.routerSettings.getAgainstPreferredDirectionTraceCosts(layer),
          actual.routerSettings.getAgainstPreferredDirectionTraceCosts(layer));
    }
  }

  private void configureFileSeed(Integer seed) throws Exception {
    var merger = new SettingsMerger(new DefaultSettings());
    if (seed != null) {
      Path file = directory.resolve("settings.json");
      Files.writeString(file, "{\"router\":{\"autorouter\":{\"item_order_seed\":" + seed + "}}}");
      merger.addOrReplaceSources(new JsonFileSettings(file));
    }
    Freerouting.globalSettings.settingsMergerProtype = merger;
  }

  private static void assertPolicy(RoutingJob job, Integer expected) {
    assertNotNull(job.board);
    assertEquals(expected, job.routerSettings.autorouter.itemOrderSeed);
    assertEquals("A1", job.board.components.get(1).name);
    assertEquals(5, job.board.components.count());
    assertEquals(5, job.board.getPins().size());
    // The fixture has a 20 x 12 mm outline and two signal layers. The reader's
    // 0.1 mm margin gives a 20.2 / 12.2 aspect ratio: rounded penalties are 1.7
    // horizontally and 0.6 vertically, added to the default unit trace cost.
    assertEquals(2, job.routerSettings.getLayerCount());
    assertEquals(true, job.routerSettings.getPreferredDirectionIsHorizontal(0));
    assertEquals(false, job.routerSettings.getPreferredDirectionIsHorizontal(1));
    assertEquals(1.0, job.routerSettings.getPreferredDirectionTraceCosts(0), 1e-12);
    assertEquals(1.0, job.routerSettings.getPreferredDirectionTraceCosts(1), 1e-12);
    assertEquals(2.7, job.routerSettings.getAgainstPreferredDirectionTraceCosts(0), 1e-12);
    assertEquals(1.6, job.routerSettings.getAgainstPreferredDirectionTraceCosts(1), 1e-12);
  }

  private static Map<String, Integer> componentIds(BasicBoard board) {
    Map<String, Integer> result = new TreeMap<>();
    for (int i = 1; i <= board.components.count(); i++) result.put(board.components.get(i).name, i);
    return result;
  }
}
