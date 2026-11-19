package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
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

/**
 * Electromagnetic physics comparison between pre-commit (unbiased fanout) and post-commit
 * (flank-coordinated mutual-inductance bias) decoupling via placement.
 */
@Tag("slow")
class DecouplingPhysicsSimulationComparisonTest {

  /** Physical constant: Permeability of free space μ0 in nH/mm. */
  private static final double MU_0_NH_PER_MM = 1.256637;

  /** Standard multi-layer PCB reference board thickness to power/ground plane in mm. */
  private static final double DEFAULT_PLANE_DEPTH_H_MM = 0.8;

  /** Standard via drill radius in mm. */
  private static final double DEFAULT_VIA_RADIUS_MM = 0.15;

  /** Standard high-speed current transient di/dt: 1.0 A in 1.0 ns (1.0e9 A/s). */
  private static final double DI_DT_A_PER_NS = 1.0;

  record ViaPairPhysics(
      int componentId,
      String componentName,
      double viaSpacingMm,
      double padSpacingMm,
      double viaSelfInductanceTotalNh,
      double mutualInductanceNh,
      double totalLoopInductanceNh,
      double loopAreaMm2,
      double voltageDroopVolts) {}

  record BoardPhysicsReport(
      String boardName,
      int viasPlacedPre,
      int viasPlacedPost,
      int drcPre,
      int drcPost,
      double avgSpacingPreMm,
      double avgSpacingPostMm,
      double avgMutualInductancePreNh,
      double avgMutualInductancePostNh,
      double avgLoopInductancePreNh,
      double avgLoopInductancePostNh,
      double inductanceReductionPercent,
      double avgVoltageDroopPreV,
      double avgVoltageDroopPostV) {}

  @Test
  void testPhysicsSimulationComparison() throws IOException {
    String[] boards = {
      "Issue508-DAC2020_bm01.dsn",
      "Issue508-DAC2020_bm04.dsn",
      "Issue978-esp32c3.dsn",
      "Issue145-smoothieboard.dsn"
    };

    System.out.println(
        "\n=========================================================================================");
    System.out.println(
        "   ELECTROMAGNETIC PDN PHYSICS SIMULATION: PRE-COMMIT VS POST-COMMIT VIA BIAS");
    System.out.println(
        "=========================================================================================");

    List<BoardPhysicsReport> reports = new ArrayList<>();

    for (String boardName : boards) {
      // 1. Pre-Commit Simulation: mutualInductanceBias = false (unconstrained fanout)
      RoutingBoard boardPre = DsnTestFixtures.loadBoard(boardName);
      enablePlanes(boardPre);
      RouterSettings settingsPre = createSettings(boardPre, false);
      int drcBaselinePre =
          new DesignRulesChecker(boardPre, null).getAllClearanceViolations().size();
      DecouplingRoutingSummary summaryPre = BatchDecouplingRouter.run(boardPre, settingsPre, null);
      assertNotNull(summaryPre);
      int drcPostPre = new DesignRulesChecker(boardPre, null).getAllClearanceViolations().size();
      assertTrue(drcPostPre <= drcBaselinePre, "DRC must not regress in pre-commit run");
      List<ViaPairPhysics> physicsPre = extractPhysics(boardPre, summaryPre);

      // 2. Post-Commit Simulation: mutualInductanceBias = true (flank normal projection)
      RoutingBoard boardPost = DsnTestFixtures.loadBoard(boardName);
      enablePlanes(boardPost);
      RouterSettings settingsPost = createSettings(boardPost, true);
      int drcBaselinePost =
          new DesignRulesChecker(boardPost, null).getAllClearanceViolations().size();
      DecouplingRoutingSummary summaryPost =
          BatchDecouplingRouter.run(boardPost, settingsPost, null);
      assertNotNull(summaryPost);
      int drcPostPost = new DesignRulesChecker(boardPost, null).getAllClearanceViolations().size();
      assertTrue(drcPostPost <= drcBaselinePost, "DRC must not regress in post-commit run");
      List<ViaPairPhysics> physicsPost = extractPhysics(boardPost, summaryPost);

      // Compute aggregates
      double avgSpacingPre = average(physicsPre, ViaPairPhysics::viaSpacingMm);
      double avgSpacingPost = average(physicsPost, ViaPairPhysics::viaSpacingMm);

      double avgMutualPre = average(physicsPre, ViaPairPhysics::mutualInductanceNh);
      double avgMutualPost = average(physicsPost, ViaPairPhysics::mutualInductanceNh);

      double avgLoopPre = average(physicsPre, ViaPairPhysics::totalLoopInductanceNh);
      double avgLoopPost = average(physicsPost, ViaPairPhysics::totalLoopInductanceNh);

      double avgDroopPre = average(physicsPre, ViaPairPhysics::voltageDroopVolts);
      double avgDroopPost = average(physicsPost, ViaPairPhysics::voltageDroopVolts);

      double inductanceReduction =
          avgLoopPre > 0.0 ? ((avgLoopPre - avgLoopPost) / avgLoopPre) * 100.0 : 0.0;

      BoardPhysicsReport report =
          new BoardPhysicsReport(
              boardName,
              summaryPre.viasPlaced(),
              summaryPost.viasPlaced(),
              drcPostPre,
              drcPostPost,
              avgSpacingPre,
              avgSpacingPost,
              avgMutualPre,
              avgMutualPost,
              avgLoopPre,
              avgLoopPost,
              inductanceReduction,
              avgDroopPre,
              avgDroopPost);
      reports.add(report);

      System.out.printf("\n--- BOARD: %s ---%n", boardName);
      System.out.printf(
          "   Vias Placed: Pre=%d, Post=%d | DRC: Pre=%d, Post=%d%n",
          report.viasPlacedPre(), report.viasPlacedPost(), report.drcPre(), report.drcPost());
      System.out.printf(
          "   Inter-Via Spacing (s): Pre=%.2f mm -> Post=%.2f mm (delta=%.2f mm)%n",
          avgSpacingPre, avgSpacingPost, (avgSpacingPost - avgSpacingPre));
      System.out.printf(
          "   Mutual Inductance (M): Pre=%.3f nH -> Post=%.3f nH (+%.1f%%)%n",
          avgMutualPre,
          avgMutualPost,
          avgMutualPre > 0 ? ((avgMutualPost - avgMutualPre) / avgMutualPre) * 100.0 : 0.0);
      System.out.printf(
          "   Total Loop Inductance: Pre=%.3f nH -> Post=%.3f nH (REDUCTION: -%.1f%%)%n",
          avgLoopPre, avgLoopPost, inductanceReduction);
      System.out.printf(
          "   Peak Voltage Droop (1A/1ns): Pre=%.3f V -> Post=%.3f V (delta=-%.3f V)%n",
          avgDroopPre, avgDroopPost, (avgDroopPre - avgDroopPost));
    }

    System.out.println(
        "\n=========================================================================================");
    System.out.println("   ELECTROMAGNETIC PDN SUMMARY TABLE");
    System.out.println(
        "=========================================================================================");
    System.out.printf(
        "%-28s | %-12s | %-12s | %-12s | %-12s%n",
        "Board Fixture", "Avg Spacing", "Mutual Ind (M)", "Loop Inductance", "L Reduction");
    System.out.println(
        "-----------------------------------------------------------------------------------------");
    for (BoardPhysicsReport r : reports) {
      System.out.printf(
          "%-28s | %5.2f->%5.2f mm | %5.3f->%5.3f nH | %5.3f->%5.3f nH | %5.1f%%%n",
          r.boardName(),
          r.avgSpacingPreMm(),
          r.avgSpacingPostMm(),
          r.avgMutualInductancePreNh(),
          r.avgMutualInductancePostNh(),
          r.avgLoopInductancePreNh(),
          r.avgLoopInductancePostNh(),
          r.inductanceReductionPercent());
    }
  }

  private static RouterSettings createSettings(RoutingBoard board, boolean biasEnabled) {
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.mutualInductanceBias = biasEnabled;
    settings.decoupling.mutualInductanceBiasWeight = 0.2;
    settings.decoupling.maxStrapLengthMm = 5.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;
    return settings;
  }

  private static void enablePlanes(RoutingBoard board) {
    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      app.freerouting.rules.Net net = board.rules.nets.get(i);
      if (net != null) {
        net.setContainsPlane(true);
      }
    }
  }

  private static List<ViaPairPhysics> extractPhysics(
      RoutingBoard board, DecouplingRoutingSummary summary) {
    double mmResolution = board.communication.getResolution(Unit.MM);
    List<ViaPairPhysics> result = new ArrayList<>();

    for (DecouplingMetrics m : summary.metrics()) {
      if (m.viaCount() == 0) {
        continue;
      }
      Pin pin1 = board.getPin(m.componentId(), 0);
      Pin pin2 = board.getPin(m.componentId(), 1);
      if (pin1 == null || pin2 == null) {
        continue;
      }

      FloatPoint p1 = pin1.getCenter().toFloat();
      FloatPoint p2 = pin2.getCenter().toFloat();
      double padDistMm = p1.distance(p2) / mmResolution;

      Via v1 = findDecouplingVia(pin1, 2.5, mmResolution);
      Via v2 = findDecouplingVia(pin2, 2.5, mmResolution);

      double spacingMm;
      if (v1 != null && v2 != null) {
        spacingMm = v1.getCenter().toFloat().distance(v2.getCenter().toFloat()) / mmResolution;
      } else if (v1 != null) {
        spacingMm = v1.getCenter().toFloat().distance(p2) / mmResolution;
      } else if (v2 != null) {
        spacingMm = v2.getCenter().toFloat().distance(p1) / mmResolution;
      } else {
        spacingMm = padDistMm;
      }

      spacingMm = Math.max(spacingMm, 0.2); // Physical lower bound (clearance web)

      // Grover / Paul Partial Self-Inductance of Via Barrels
      double h = DEFAULT_PLANE_DEPTH_H_MM;
      double rv = DEFAULT_VIA_RADIUS_MM;
      double lViaSingleNh =
          (MU_0_NH_PER_MM * h / (2.0 * Math.PI)) * (Math.log(2.0 * h / rv) - 0.75);
      double lSelfTotalNh = 2.0 * lViaSingleNh;

      // Exact Grover Partial Mutual Inductance for Parallel Cylinders of Length h Separated by
      // Pitch s
      double ratio = h / spacingMm;
      double mNh =
          (MU_0_NH_PER_MM * h / (2.0 * Math.PI))
              * (Math.log(ratio + Math.sqrt(1.0 + ratio * ratio))
                  - Math.sqrt(1.0 + (1.0 / (ratio * ratio)))
                  + (1.0 / ratio));

      mNh = Math.max(mNh, 0.0);

      // Loop Inductance: L_loop = L_self - 2M + L_surface
      double lSurfaceNh = 0.6 * spacingMm; // Surface trace loop contribution ~0.6 nH/mm
      double totalLoopNh = Math.max(lSelfTotalNh - 2.0 * mNh + lSurfaceNh, 0.1);
      double loopAreaMm2 = spacingMm * h;
      double droopVolts = totalLoopNh * DI_DT_A_PER_NS; // V = L * di/dt

      result.add(
          new ViaPairPhysics(
              m.componentId(),
              m.componentName(),
              spacingMm,
              padDistMm,
              lSelfTotalNh,
              mNh,
              totalLoopNh,
              loopAreaMm2,
              droopVolts));
    }
    return result;
  }

  private static Via findDecouplingVia(Pin pin, double maxRadiusMm, double mmResolution) {
    if (pin.netCount() == 0) {
      return null;
    }
    FloatPoint pinCenter = pin.getCenter().toFloat();
    Via closestVia = null;
    double closestDist = Double.MAX_VALUE;
    for (Item item : pin.getConnectedSet(pin.getNetNumber(0))) {
      if (item instanceof Via via && via.isDecouplingProtected()) {
        double d = via.getCenter().toFloat().distance(pinCenter) / mmResolution;
        if (d <= maxRadiusMm && d < closestDist) {
          closestDist = d;
          closestVia = via;
        }
      }
    }
    return closestVia;
  }

  @FunctionalInterface
  interface MetricExtractor {
    double extract(ViaPairPhysics p);
  }

  private static double average(List<ViaPairPhysics> list, MetricExtractor extractor) {
    if (list.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (ViaPairPhysics p : list) {
      sum += extractor.extract(p);
    }
    return sum / list.size();
  }
}
