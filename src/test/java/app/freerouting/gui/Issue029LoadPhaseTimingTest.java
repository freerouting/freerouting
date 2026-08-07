package app.freerouting.gui;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.interactive.RatsNest;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.SettingsMerger;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Issue029LoadPhaseTimingTest {

  @Test
  void profileAllPhases() throws Exception {
    try (InputStream stream = DsnTestFixtures.openResource("Issue029-hw48na.dsn")) {
      long t0 = System.nanoTime();
      BoardReadResult result = DsnReader.readBoard(stream, null, null, "Issue029-hw48na.dsn");
      long t1 = System.nanoTime();
      System.out.printf("1 parse: %.2f ms%n", ms(t0, t1));

      RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();
      RoutingJob job = new RoutingJob(UUID.randomUUID());
      job.setDummyInputFile("Issue029-hw48na.dsn");

      SettingsMerger merger = new SettingsMerger();
      merger.addOrReplaceSources(new DefaultSettings());

      long t2 = System.nanoTime();
      HeadlessBoardManager manager = new HeadlessBoardManager(job);
      manager.applyParsedBoardResult(result, "Issue029-hw48na.dsn", "DSN");
      long t3 = System.nanoTime();
      System.out.printf("2 applyParsedBoardResult: %.2f ms%n", ms(t2, t3));

      long t4 = System.nanoTime();
      merger.merge();
      long t5 = System.nanoTime();
      System.out.printf("3 settingsMerger.merge: %.2f ms%n", ms(t4, t5));

      long t6 = System.nanoTime();
      new RatsNest(board);
      long t7 = System.nanoTime();
      System.out.printf("4 RatsNest: %.2f ms%n", ms(t6, t7));

      long t8 = System.nanoTime();
      app.freerouting.geometry.planar.IntBox bbox = board.get_bounding_box();
      for (app.freerouting.board.Item currItem : board.get_items()) {
        app.freerouting.geometry.planar.IntBox currBoundingBox = currItem.bounding_box();
        if (currBoundingBox.ur.x < Integer.MAX_VALUE) {
          bbox = bbox.union(currBoundingBox);
        }
      }
      long t9 = System.nanoTime();
      System.out.printf("5 adjust_design_bounds loop: %.2f ms%n", ms(t8, t9));

      long t10 = System.nanoTime();
      job.routerSettings.applyBoardSpecificOptimizations(board);
      long t11 = System.nanoTime();
      System.out.printf("6 applyBoardSpecificOptimizations: %.2f ms%n", ms(t10, t11));

      long t12 = System.nanoTime();
      manager.calculateCrc32();
      long t13 = System.nanoTime();
      System.out.printf("7 calculateCrc32 (DsnWriter): %.2f ms%n", ms(t12, t13));

      long t14 = System.nanoTime();
      new BoardStatistics(board, null, true, true);
      long t15 = System.nanoTime();
      System.out.printf("8 BoardStatistics with DRC+connections: %.2f ms%n", ms(t14, t15));

      System.out.printf(
          "Board: components=%d traces=%d items=%d%n",
          board.components.count(), board.get_traces().size(), board.get_items().size());
    }
  }

  private static double ms(long start, long end) {
    return (end - start) / 1_000_000.0;
  }
}
