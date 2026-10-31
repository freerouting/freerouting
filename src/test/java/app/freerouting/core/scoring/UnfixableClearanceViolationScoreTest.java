package app.freerouting.core.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A clearance violation between two items that the router can never move (here two pads of one
 * footprint) is the same on every routing result, so it must be reported but must not lower the
 * router score. Violations that routing can resolve must still lower it.
 */
class UnfixableClearanceViolationScoreTest {

  /**
   * Larger than the 160 um gap between the two pads of one footprint, smaller than the 230 um gap
   * between a wire ending at a pad centre and the neighbouring pad.
   */
  private static final int VIOLATED_CLEARANCE = 200;

  /** Smaller than every gap on the board. */
  private static final int MET_CLEARANCE = 100;

  private static final String NET_B_WIRE =
      "(wire (path top 100 5200 5000 6000 5000 6000 3000 16000 3000 16000 5000 15200 5000)"
          + " (net B))";

  /** Both nets leave their pads away from the neighbouring pad. */
  private static final String CLEAN_WIRING =
      "(wire (path top 100 4800 5000 4000 5000 4000 7000 14000 7000 14000 5000 14800 5000)"
          + " (net A))"
          + NET_B_WIRE;

  /** Like {@link #CLEAN_WIRING}, but net A passes right over a pad and the wire of net B. */
  private static final String WIRE_TOO_CLOSE =
      "(wire (path top 100 4800 5000 4800 5250 5600 5250 5600 7000 14000 7000 14000 5000"
          + " 14800 5000) (net A))"
          + NET_B_WIRE;

  private static final RouterSettings SETTINGS = new SettingsMerger(new DefaultSettings()).merge();

  private static BoardStatistics statistics(int clearance, String wiring) {
    String dsn =
        """
        (pcb unfixable_violation
          (resolution um 1)
          (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 20000 10000))
            (rule (width 100) (clearance %d)))
          (placement
            (component R
              (place R1 5000 5000 front 0)
              (place R2 15000 5000 front 0)))
          (library
            (image R (pin SMD 1 -200 0) (pin SMD 2 200 0))
            (padstack SMD (shape (rect top -120 -120 120 120)) (attach off)))
          (network
            (net A (pins R1-1 R2-1))
            (net B (pins R1-2 R2-2)))
          (wiring
            %s))
        """
            .formatted(clearance, wiring);
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    BasicBoard board = ((BoardReadResult.Success) result).board();
    return new BoardStatistics(board);
  }

  @Test
  void violationBetweenImmovableItemsIsReportedButNotScored() {
    BoardStatistics clean = statistics(MET_CLEARANCE, CLEAN_WIRING);
    BoardStatistics padsTooClose = statistics(VIOLATED_CLEARANCE, CLEAN_WIRING);

    assertEquals(0, clean.clearanceViolations.totalCount);
    assertEquals(0, clean.connections.incompleteCount);
    assertTrue(padsTooClose.clearanceViolations.totalCount > 0, "the violation is reported");
    assertEquals(
        padsTooClose.clearanceViolations.totalCount,
        padsTooClose.clearanceViolations.unfixableCount,
        "only the pads of one footprint violate the clearance");
    assertEquals(clean.getRouterScore(SETTINGS), padsTooClose.getRouterScore(SETTINGS));
    assertEquals(
        clean.getRouterScore(SETTINGS.scoring), padsTooClose.getRouterScore(SETTINGS.scoring));
    assertEquals(
        clean.getOptimizerScore(SETTINGS.scoring),
        padsTooClose.getOptimizerScore(SETTINGS.scoring));
  }

  @Test
  void violationOfMovableWireStillLowersScore() {
    BoardStatistics padsTooClose = statistics(VIOLATED_CLEARANCE, CLEAN_WIRING);
    BoardStatistics wireTooClose = statistics(VIOLATED_CLEARANCE, WIRE_TOO_CLOSE);

    assertTrue(
        wireTooClose.clearanceViolations.totalCount
            > wireTooClose.clearanceViolations.unfixableCount,
        "the wire violation can be fixed by routing");
    assertTrue(wireTooClose.getRouterScore(SETTINGS) < padsTooClose.getRouterScore(SETTINGS));
    assertTrue(
        wireTooClose.getRouterScore(SETTINGS.scoring)
            < padsTooClose.getRouterScore(SETTINGS.scoring));
  }
}
