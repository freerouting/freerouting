package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.io.FileFormat;
import app.freerouting.settings.OptimizerScoringVersion;
import app.freerouting.settings.RouterScoringVersion;
import app.freerouting.settings.RouterSettings;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BoardStatisticsTest {

  @Test
  void boardStatisticsWithValidJson() {
    String jsonContent =
        """
        {
          "designName": "test-board",
          "layers": [{"index": 0, "name": "F.Cu"}, {"index": 1, "name": "B.Cu"}],
          "components": [{"reference": "R1"}, {"reference": "R2"}],
          "netClasses": [{"name": "default"}],
          "nets": [{"id": 1, "name": "N1"}, {"id": 2, "name": "N2"}],
          "traces": [{"netName": "N1"}],
          "vias": [{"netName": "N1"}]
        }
        """;

    byte[] data = jsonContent.getBytes(StandardCharsets.UTF_8);
    BoardStatistics stats = new BoardStatistics(data, FileFormat.KICAD_DESIGN_JSON);

    assertNotNull(stats.layers);
    assertEquals(2, stats.layers.totalCount);
    assertEquals(2, stats.components.totalCount);
    assertEquals(1, stats.nets.classCount);
    assertEquals(2, stats.nets.totalCount);
    assertEquals(1, stats.traces.totalCount);
    assertEquals(1, stats.vias.totalCount);
    assertEquals("KiCad JSON,test-board", stats.host);
  }

  @Test
  void v2RouterScoreUsesConnectionFractionAndClearanceDepth() {
    BoardStatistics stats = new BoardStatistics();
    stats.connections.maximumCount = 10;
    stats.connections.incompleteCount = 2;
    stats.clearanceViolations.totalCount = 2;
    stats.clearanceViolations.totalViolationUm = 3000.0;
    stats.difficulty.difficultyD = 10.0f;

    RouterSettings settings = new RouterSettings();
    settings.routerScoring.version = RouterScoringVersion.V2_CONTINUOUS;
    settings.routerScoring.unroutedConnectionWeight = 1000.0f;
    settings.routerScoring.clearanceViolationCountWeight = 25.0f;
    settings.routerScoring.clearanceViolationDepthWeight = 1.0f;
    settings.routerScoring.clearanceViolationDepthScale = 1000.0f;

    assertEquals(794.7f, stats.getRouterScore(settings), 0.001f);
  }

  @Test
  void v2OptimizerScoreUsesLowerBoundsAndDifficulty() {
    BoardStatistics stats = new BoardStatistics();
    stats.bounds.minTraceLengthMm = 10.0f;
    stats.bounds.minViaCount = 1;
    stats.bounds.minBendCount = 1;
    stats.traces.totalLengthMm = 15.0f;
    stats.vias.totalCount = 2;
    stats.bends.totalCount = 3;
    stats.difficulty.difficultyD = 10.0f;

    RouterSettings settings = new RouterSettings();
    settings.optimizerScoring.version = OptimizerScoringVersion.V2_LOWER_BOUND;

    assertEquals(200.0f, stats.getOptimizerScore(settings), 0.001f);
  }

  @Test
  void v2RouterScoreIsDefinedForZeroConnectionBoards() {
    BoardStatistics stats = new BoardStatistics();
    stats.connections.maximumCount = 0;
    stats.connections.incompleteCount = 0;
    stats.clearanceViolations.totalCount = 0;
    stats.clearanceViolations.totalViolationUm = 0.0;
    stats.difficulty.difficultyD = 1.0f;

    RouterSettings settings = new RouterSettings();
    settings.routerScoring.version = RouterScoringVersion.V2_CONTINUOUS;
    settings.routerScoring.unroutedConnectionWeight = 1000.0f;
    settings.routerScoring.clearanceViolationCountWeight = 25.0f;
    settings.routerScoring.clearanceViolationDepthWeight = 1.0f;
    settings.routerScoring.clearanceViolationDepthScale = 1000.0f;

    assertEquals(1000.0f, stats.getRouterScore(settings), 0.001f);

    stats.clearanceViolations.totalCount = 2;
    assertEquals(950.0f, stats.getRouterScore(settings), 0.001f);
  }
}
