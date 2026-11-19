package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.DecouplingLockMode;
import app.freerouting.settings.DecouplingSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class DecouplingTwoIterationBenchmarkTest {

  record IterationResult(
      int iteration,
      long durationMs,
      int candidates,
      int routed,
      int alreadyConnected,
      int viasPlaced,
      int failed,
      double totalStrapLengthMm,
      int drcBaseline,
      int drcPost) {}

  @Test
  void runTwoIterationBenchmarks() throws IOException {
    String[] boards = {
      "Issue508-DAC2020_bm01.dsn",
      "Issue508-DAC2020_bm04.dsn",
      "Issue978-esp32c3.dsn",
      "Issue145-smoothieboard.dsn"
    };

    System.out.println(
        "\n==========================================================================");
    System.out.println("   DECOUPLING ROUTER: 2-ITERATION BENCHMARK EVALUATION (SUPPLY PLANE VIA)");
    System.out.println(
        "==========================================================================");

    for (String boardName : boards) {
      List<IterationResult> results = new ArrayList<>();

      for (int iter = 1; iter <= 2; iter++) {
        RoutingBoard board = DsnTestFixtures.loadBoard(boardName);
        RouterSettings settings = new DefaultSettings().getSettings();
        settings.setLayerCount(board.getLayerCount());
        settings.applyBoardSpecificOptimizations(board);
        settings.decoupling = new DecouplingSettings();
        settings.decoupling.enabled = true;
        settings.decoupling.lockMode = DecouplingLockMode.HARD;
        settings.decoupling.maxStrapLengthMm = 5.0;
        settings.decoupling.maxOwnerRadiusMm = 8.0;

        int drcBaseline = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

        long startTime = System.nanoTime();
        DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000L;

        assertNotNull(summary);
        int drcPost = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
        assertTrue(
            drcPost <= drcBaseline,
            "DRC violations must not regress (baseline=" + drcBaseline + ", post=" + drcPost + ")");

        results.add(
            new IterationResult(
                iter,
                durationMs,
                summary.candidatesTotal(),
                summary.strapsRouted(),
                summary.alreadyConnected(),
                summary.viasPlaced(),
                summary.failed(),
                summary.totalStrapLengthMm(),
                drcBaseline,
                drcPost));
      }

      IterationResult r1 = results.get(0);
      IterationResult r2 = results.get(1);
      double avgDurationMs = (r1.durationMs() + r2.durationMs()) / 2.0;
      double avgLengthMm = (r1.totalStrapLengthMm() + r2.totalStrapLengthMm()) / 2.0;

      System.out.println("\n--- BOARD: " + boardName + " ---");
      System.out.printf(
          "   Iter 1: Duration=%d ms, Candidates=%d, Routed=%d, Connected=%d, ViasPlaced=%d, Failed=%d, Length=%.2f mm, DRC=%d->%d%n",
          r1.durationMs(),
          r1.candidates(),
          r1.routed(),
          r1.alreadyConnected(),
          r1.viasPlaced(),
          r1.failed(),
          r1.totalStrapLengthMm(),
          r1.drcBaseline(),
          r1.drcPost());
      System.out.printf(
          "   Iter 2: Duration=%d ms, Candidates=%d, Routed=%d, Connected=%d, ViasPlaced=%d, Failed=%d, Length=%.2f mm, DRC=%d->%d%n",
          r2.durationMs(),
          r2.candidates(),
          r2.routed(),
          r2.alreadyConnected(),
          r2.viasPlaced(),
          r2.failed(),
          r2.totalStrapLengthMm(),
          r2.drcBaseline(),
          r2.drcPost());
      System.out.printf(
          "   AVERAGE: Duration=%.1f ms, StrapsRouted=%d, ViasPlaced=%d, Length=%.2f mm, DRC Delta=%d%n",
          avgDurationMs,
          r1.routed(),
          r1.viasPlaced(),
          avgLengthMm,
          (r1.drcPost() - r1.drcBaseline()));
    }
  }
}
