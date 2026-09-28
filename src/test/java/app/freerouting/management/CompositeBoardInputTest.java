package app.freerouting.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.settings.GlobalSettings;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompositeBoardInputTest extends RoutingFixtureTest {

  @BeforeEach
  protected void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  @Test
  void testAssembleBoardFromDsn() throws Exception {
    RoutingJob sampleJob = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    assertNotNull(sampleJob);
    byte[] dsnBytes = sampleJob.input.getData().readAllBytes();

    CompositeBoardInput input = new CompositeBoardInput();
    input.setDesign(dsnBytes, "test.dsn");

    RoutingJob job = new RoutingJob(UUID.randomUUID());
    input.assembleBoard(job);

    assertNotNull(job.board, "Routing board should be assembled");
    assertNotNull(job.routerSettings, "Router settings should be merged");
    assertNotNull(job.board.getStatistics());
  }

  @Test
  void testAssembleBoardFromDsnAndRules() throws Exception {
    RoutingJob sampleJob = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    assertNotNull(sampleJob);
    byte[] dsnBytes = sampleJob.input.getData().readAllBytes();

    String rulesContent = "(rules (autoroute (fanout on) (pass 5)))";
    CompositeBoardInput input = new CompositeBoardInput();
    input.setDesign(dsnBytes, "test.dsn");
    input.setRules(rulesContent.getBytes(StandardCharsets.UTF_8), "test.rules");

    RoutingJob job = new RoutingJob(UUID.randomUUID());
    input.assembleBoard(job);

    assertNotNull(job.board);
    assertNotNull(job.rules);
    assertEquals("test.rules", job.rules.getFilename());
  }
}
