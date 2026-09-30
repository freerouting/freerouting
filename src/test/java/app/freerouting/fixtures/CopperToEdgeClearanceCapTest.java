package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.DefaultSettings;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * The default copper-to-edge clearance is only a guess. It must never be larger than the gap that
 * the input design already has between its pins and the board outline.
 */
class CopperToEdgeClearanceCapTest extends RoutingFixtureTest {

  private static final String BOARD = "PCBench-retroreflectors-CONGFLOCK.dsn";

  private static int toBoardUnits(RoutingJob job, double um) {
    return (int)
        Math.round(
            Unit.scale(
                um * Math.max(1, job.board.communication.resolution),
                Unit.UM,
                job.board.communication.unit));
  }

  private RoutingJob loadWith(double copperToEdgeUm) {
    var testingSettings = new TestingSettings();
    testingSettings.setCopperToEdgeClearanceUm(copperToEdgeUm);
    testingSettings.setMaxPasses(1);
    testingSettings.setJobTimeoutString("00:01:00");
    return runRoutingJob(getRoutingJob(BOARD, testingSettings));
  }

  @Test
  void defaultEdgeClearanceIsCappedByPinToOutlineGap() {
    RoutingJob job = loadWith(DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM);

    int boardEdgeClassNo = job.board.rules.clearanceMatrix.getNo("board_edge");
    assertTrue(boardEdgeClassNo >= 0, "board_edge class must exist");
    int defaultBoardUnits = toBoardUnits(job, DefaultSettings.DEFAULT_COPPER_TO_EDGE_CLEARANCE_UM);
    double pinGap = job.board.getOutline().minimumPinGap();

    assertTrue(
        pinGap > 0 && pinGap < defaultBoardUnits,
        "fixture must have a pin closer to the edge than 250 um, gap=" + pinGap);
    for (int layer = 0; layer < job.board.rules.clearanceMatrix.getLayerCount(); layer++) {
      int applied =
          job.board.rules.clearanceMatrix.getValue(
              boardEdgeClassNo, boardEdgeClassNo, layer, false);
      assertEquals(
          pinGap,
          applied,
          1.0,
          "edge clearance must equal the smallest pin-to-outline gap (" + pinGap + ")");
    }
  }

  @Test
  void explicitEdgeClearanceIsNotCapped() {
    final double explicitUm = 400.0;
    RoutingJob job = loadWith(explicitUm);

    int boardEdgeClassNo = job.board.rules.clearanceMatrix.getNo("board_edge");
    assertTrue(boardEdgeClassNo >= 0, "board_edge class must exist");
    int expected = toBoardUnits(job, explicitUm);
    for (int layer = 0; layer < job.board.rules.clearanceMatrix.getLayerCount(); layer++) {
      assertEquals(
          expected,
          job.board.rules.clearanceMatrix.getValue(
              boardEdgeClassNo, boardEdgeClassNo, layer, false),
          "an explicit router.copper_to_edge_clearance_um value must be applied unchanged");
    }
  }
}
