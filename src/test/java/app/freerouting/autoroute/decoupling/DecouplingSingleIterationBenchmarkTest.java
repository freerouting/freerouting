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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class DecouplingSingleIterationBenchmarkTest {

  @Test
  void runSingleIterationBenchmarks() throws IOException {
    String[] boards = {
      "Issue508-DAC2020_bm01.dsn",
      "Issue508-DAC2020_bm04.dsn",
      "Issue978-esp32c3.dsn",
      "Issue145-smoothieboard.dsn"
    };

    System.out.println(
        "\n==========================================================================");
    System.out.println(
        "   DECOUPLING ROUTER: 1-ITERATION BENCHMARK EVALUATION (MUTUAL INDUCTANCE BIAS)");
    System.out.println(
        "==========================================================================");

    for (String boardName : boards) {
      // 1. Standard Strapping Mode
      RoutingBoard board = DsnTestFixtures.loadBoard(boardName);
      RouterSettings settings = new DefaultSettings().getSettings();
      settings.setLayerCount(board.getLayerCount());
      settings.applyBoardSpecificOptimizations(board);
      settings.decoupling = new DecouplingSettings();
      settings.decoupling.enabled = true;
      settings.decoupling.lockMode = DecouplingLockMode.HARD;
      settings.decoupling.mutualInductanceBias = true;
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
          "DRC violations must not regress on "
              + boardName
              + " in strap mode (baseline="
              + drcBaseline
              + ", post="
              + drcPost
              + ")");

      System.out.println("\n--- BOARD: " + boardName + " (Surface Straps) ---");
      System.out.printf(
          "   Duration=%d ms, Candidates=%d, Routed=%d, Connected=%d, ViasPlaced=%d, Failed=%d, Length=%.2f mm, DRC=%d->%d (delta=%d)%n",
          durationMs,
          summary.candidatesTotal(),
          summary.strapsRouted(),
          summary.alreadyConnected(),
          summary.viasPlaced(),
          summary.failed(),
          summary.totalStrapLengthMm(),
          drcBaseline,
          drcPost,
          (drcPost - drcBaseline));

      // 2. Supply Plane Via Drops Mode with Flank-Coordinated Mutual Inductance Bias
      RoutingBoard boardPlanes = DsnTestFixtures.loadBoard(boardName);
      RouterSettings planeSettings = new DefaultSettings().getSettings();
      planeSettings.setLayerCount(boardPlanes.getLayerCount());
      planeSettings.applyBoardSpecificOptimizations(boardPlanes);
      planeSettings.decoupling = new DecouplingSettings();
      planeSettings.decoupling.enabled = true;
      planeSettings.decoupling.lockMode = DecouplingLockMode.HARD;
      planeSettings.decoupling.mutualInductanceBias = true;
      planeSettings.decoupling.maxStrapLengthMm = 5.0;
      planeSettings.decoupling.maxOwnerRadiusMm = 8.0;

      for (int i = 1; i <= boardPlanes.rules.nets.maxNetNumber(); i++) {
        app.freerouting.rules.Net net = boardPlanes.rules.nets.get(i);
        if (net != null) {
          net.setContainsPlane(true);
        }
      }

      int drcBaselinePlane =
          new DesignRulesChecker(boardPlanes, null).getAllClearanceViolations().size();
      long startTimePlane = System.nanoTime();
      DecouplingRoutingSummary summaryPlane =
          BatchDecouplingRouter.run(boardPlanes, planeSettings, null);
      long durationMsPlane = (System.nanoTime() - startTimePlane) / 1_000_000L;

      assertNotNull(summaryPlane);
      int drcPostPlane =
          new DesignRulesChecker(boardPlanes, null).getAllClearanceViolations().size();
      assertTrue(
          drcPostPlane <= drcBaselinePlane,
          "DRC violations must not regress on "
              + boardName
              + " in plane via mode (baseline="
              + drcBaselinePlane
              + ", post="
              + drcPostPlane
              + ")");

      System.out.println(
          "--- BOARD: " + boardName + " (Plane Via Drops with Mutual Inductance Bias) ---");
      System.out.printf(
          "   Duration=%d ms, Candidates=%d, Routed=%d, Connected=%d, ViasPlaced=%d (Coupled=%d, Fallback=%d), Failed=%d, Length=%.2f mm, DRC=%d->%d (delta=%d)%n",
          durationMsPlane,
          summaryPlane.candidatesTotal(),
          summaryPlane.strapsRouted(),
          summaryPlane.alreadyConnected(),
          summaryPlane.viasPlaced(),
          summaryPlane.coupledViaPairs(),
          summaryPlane.fallbackViaPairs(),
          summaryPlane.failed(),
          summaryPlane.totalStrapLengthMm(),
          drcBaselinePlane,
          drcPostPlane,
          (drcPostPlane - drcBaselinePlane));
    }
  }
}
