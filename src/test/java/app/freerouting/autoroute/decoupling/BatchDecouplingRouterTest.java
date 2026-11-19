package app.freerouting.autoroute.decoupling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.Net;
import app.freerouting.settings.DecouplingLockMode;
import app.freerouting.settings.DecouplingSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BatchDecouplingRouterTest {

  @Test
  void testDisabledReturnsNull() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    assertFalse(settings.isDecouplingEnabled());

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNull(summary);
  }

  @Test
  void testGoldenFixtureDecouplingPreRouting() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.maxStrapLengthMm = 4.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);

    assertNotNull(summary);
    assertTrue(summary.candidatesTotal() > 0, "Expected decoupling candidates on DAC2020_bm01");
    assertNotNull(summary.metrics());
    assertEquals(summary.candidatesTotal(), summary.metrics().size());

    // Gate: No new clearance violations vs baseline
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(
        postDrc <= baselineDrc,
        "Decoupling routing must not introduce new clearance violations (baseline="
            + baselineDrc
            + ", post="
            + postDrc
            + ")");

    // Verify protection flags on items
    for (Item item : board.getItems()) {
      if ((item instanceof Trace || item instanceof Via) && item.isDecouplingProtected()) {
        if (!item.getFixedState().isPreservedRoute()) {
          assertEquals(FixedState.SYSTEM_FIXED, item.getFixedState());
        }
      }
    }

    // Verify per-cap metrics
    for (DecouplingMetrics m : summary.metrics()) {
      assertTrue(m.componentId() > 0);
      assertNotNull(m.componentName());
      assertNotNull(m.ownerName());
      assertTrue(m.strapLengthMm() >= 0.0);
      assertTrue(m.viaCount() >= 0);
      assertTrue(m.groundViaDistanceMm() >= 0.0);
    }
  }

  @Test
  void testAlreadyConnectedSkip() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;

    // Run first time
    DecouplingRoutingSummary firstRun = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(firstRun);

    // Run second time on same board: all previously routed or connected candidates should now be
    // alreadyConnected
    DecouplingRoutingSummary secondRun = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(secondRun);
    assertEquals(
        0, secondRun.strapsRouted(), "Second run should not re-route already connected straps");
    assertTrue(
        secondRun.alreadyConnected() >= firstRun.strapsRouted() + firstRun.alreadyConnected());
  }

  @Test
  void testFanoutSkipsDecouplingPins() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(summary.strapsRouted() > 0);

    java.util.Set<Item> protectedItems = new java.util.HashSet<>();
    for (Item it : board.getItems()) {
      if (it.isDecouplingProtected()) {
        protectedItems.add(it);
      }
    }
    assertFalse(protectedItems.isEmpty());

    app.freerouting.autoroute.pipeline.BatchFanout.fanoutBoard(board, settings, null);

    for (Item it : protectedItems) {
      if (it instanceof app.freerouting.board.model.items.Pin pin) {
        assertTrue(pin.isDecouplingProtected());
      }
    }
  }

  @Test
  void testInspectDecouplingTracesOnMultipleBoards() throws IOException {
    String[] testBoards = {
      "Issue508-DAC2020_bm01.dsn",
      "Issue508-DAC2020_bm02.dsn",
      "Issue508-DAC2020_bm04.dsn",
      "Issue145-smoothieboard.dsn",
      "Issue269-caniot-tiny-arm.dsn",
      "Issue367-Charger.dsn",
      "Issue957-smoke-ldo-led.dsn",
      "Issue978-esp32c3.dsn"
    };

    for (String boardName : testBoards) {
      RoutingBoard board = DsnTestFixtures.loadBoard(boardName);
      int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

      RouterSettings settings = new DefaultSettings().getSettings();
      settings.decoupling = new DecouplingSettings();
      settings.decoupling.enabled = true;
      settings.decoupling.lockMode = DecouplingLockMode.HARD;
      settings.decoupling.maxStrapLengthMm = 5.0;
      settings.decoupling.maxOwnerRadiusMm = 10.0;
      settings.decoupling.includeReviewCandidates = true;

      DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
      assertNotNull(summary, "Summary should not be null for " + boardName);

      int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
      System.out.printf(
          "[%s] candidates=%d, routed=%d, alreadyConnected=%d, vias=%d, failed=%d, len=%.2fmm,"
              + " drcBaseline=%d, drcPost=%d%n",
          boardName,
          summary.candidatesTotal(),
          summary.strapsRouted(),
          summary.alreadyConnected(),
          summary.viasPlaced(),
          summary.failed(),
          summary.totalStrapLengthMm(),
          baselineDrc,
          postDrc);

      java.util.Map<String, Long> statusCounts =
          summary.metrics().stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      DecouplingMetrics::status, java.util.stream.Collectors.counting()));
      System.out.printf("   [%s] Status Breakdown: %s%n", boardName, statusCounts);

      assertTrue(
          postDrc <= baselineDrc,
          "Board "
              + boardName
              + " introduced new DRC violations! (baseline="
              + baselineDrc
              + ", post="
              + postDrc
              + ")");

      // Inspect every protected trace
      int protectedTraceCount = 0;
      for (Item item : board.getItems()) {
        if (item.isDecouplingProtected()
            && item instanceof app.freerouting.board.trace.PolylineTrace trace) {
          protectedTraceCount++;
          // Verify geometric validity: trace must have non-zero length and valid corners
          assertTrue(trace.getLength() > 0, "Decoupling trace must have positive length: " + trace);
          app.freerouting.geometry.planar.Polyline poly = trace.polyline();
          assertNotNull(poly);
          assertTrue(poly.cornerCount() >= 2, "Trace must have at least 2 corners: " + trace);
          System.out.printf(
              "   [Trace #%d] net=%d, layer=%d, length=%.2fum, corners=%d%n",
              trace.getId(),
              trace.getNetNumber(0),
              trace.getLayer(),
              trace.getLength(),
              poly.cornerCount());
          for (int c = 0; c < poly.cornerCount(); c++) {
            app.freerouting.geometry.planar.Point pt = poly.corner(c);
            assertNotNull(pt);
            assertFalse(Double.isNaN(pt.toFloat().x));
            assertFalse(Double.isNaN(pt.toFloat().y));
          }
        }
      }
      System.out.printf("   [%s] total protected traces: %d%n", boardName, protectedTraceCount);
    }
  }

  @Test
  void testEndToEndAutoroutingWithDecoupling() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertEquals(0, baselineDrc);

    app.freerouting.core.RoutingJob job = new app.freerouting.core.RoutingJob();
    job.board = board;
    job.thread =
        new app.freerouting.core.StoppableThread() {
          @Override
          protected void threadAction() {}
        };

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.autorouter.maxPasses = 1;
    settings.autorouter.maxItems = 10;
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    job.routerSettings = settings;

    app.freerouting.autoroute.pipeline.BatchAutorouter router =
        new app.freerouting.autoroute.pipeline.BatchAutorouter(job);
    router.runBatchLoop();

    // Verify DRC remains completely clean after decoupling + fanout + autorouting pass
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertEquals(
        0, postDrc, "End-to-end autorouting with decoupling must introduce 0 clearance violations");

    // Verify protected items still exist and are protected
    long protectedCount = board.getItems().stream().filter(Item::isDecouplingProtected).count();
    assertTrue(protectedCount > 0, "Decoupling protected items must remain on board");
  }

  @Test
  void testComparativeEvaluationWithAndWithoutDecoupling() throws IOException {
    String[] testBoards = {
      "Issue508-DAC2020_bm01.dsn", "Issue508-DAC2020_bm04.dsn", "Issue978-esp32c3.dsn"
    };

    for (String boardName : testBoards) {
      System.out.println("======================================================================");
      System.out.println("BOARD: " + boardName);
      System.out.println("======================================================================");

      // 1. Diagnostic candidate breakdown
      RoutingBoard diagBoard = DsnTestFixtures.loadBoard(boardName);
      RouterSettings diagSettings = new DefaultSettings().getSettings();
      diagSettings.decoupling = new DecouplingSettings();
      diagSettings.decoupling.enabled = true;
      diagSettings.decoupling.lockMode = DecouplingLockMode.HARD;
      diagSettings.decoupling.maxStrapLengthMm = 5.0;
      diagSettings.decoupling.maxOwnerRadiusMm = 10.0;
      diagSettings.decoupling.includeReviewCandidates = true;

      DecouplingRoutingSummary diagSummary =
          BatchDecouplingRouter.run(diagBoard, diagSettings, null);
      assertNotNull(diagSummary);

      System.out.printf(
          "Decoupling Pre-Pass Diagnostic Summary: %d total candidates, %d routed, %d already connected, %d failed%n",
          diagSummary.candidatesTotal(),
          diagSummary.strapsRouted(),
          diagSummary.alreadyConnected(),
          diagSummary.failed());

      System.out.println("--- Per-Candidate Breakdown & Outcome Reasons ---");
      for (DecouplingMetrics m : diagSummary.metrics()) {
        System.out.printf(
            "   Cap %s -> Owner %s | Status: %s | Strap: %.2f mm | Vias: %d | GroundViaDist: %.2f mm%n",
            m.componentName(),
            m.ownerName(),
            m.status(),
            m.strapLengthMm(),
            m.viaCount(),
            m.groundViaDistanceMm());
      }

      // 2. RUN A: WITHOUT DECOUPLING ROUTER (Baseline)
      RoutingBoard boardWithout = DsnTestFixtures.loadBoard(boardName);
      app.freerouting.core.RoutingJob jobWithout = new app.freerouting.core.RoutingJob();
      jobWithout.board = boardWithout;
      jobWithout.thread =
          new app.freerouting.core.StoppableThread() {
            @Override
            protected void threadAction() {}
          };
      RouterSettings settingsWithout = new DefaultSettings().getSettings();
      settingsWithout.setLayerCount(boardWithout.getLayerCount());
      settingsWithout.applyBoardSpecificOptimizations(boardWithout);
      settingsWithout.autorouter.maxPasses = 1;
      settingsWithout.autorouter.maxItems = 15;
      settingsWithout.decoupling = new DecouplingSettings();
      settingsWithout.decoupling.enabled = false;
      jobWithout.routerSettings = settingsWithout;

      app.freerouting.autoroute.pipeline.BatchAutorouter routerWithout =
          new app.freerouting.autoroute.pipeline.BatchAutorouter(jobWithout);
      routerWithout.runBatchLoop();

      long viasWithout =
          boardWithout.getItems().stream()
              .filter(it -> it instanceof app.freerouting.board.model.items.Via)
              .count();
      double traceLenWithout =
          boardWithout.getItems().stream()
              .filter(it -> it instanceof app.freerouting.board.model.items.Trace)
              .mapToDouble(it -> ((app.freerouting.board.model.items.Trace) it).getLength())
              .sum();
      int drcWithout =
          new DesignRulesChecker(boardWithout, null).getAllClearanceViolations().size();

      // 3. RUN B: WITH DECOUPLING ROUTER
      RoutingBoard boardWith = DsnTestFixtures.loadBoard(boardName);
      app.freerouting.core.RoutingJob jobWith = new app.freerouting.core.RoutingJob();
      jobWith.board = boardWith;
      jobWith.thread =
          new app.freerouting.core.StoppableThread() {
            @Override
            protected void threadAction() {}
          };
      RouterSettings settingsWith = new DefaultSettings().getSettings();
      settingsWith.setLayerCount(boardWith.getLayerCount());
      settingsWith.applyBoardSpecificOptimizations(boardWith);
      settingsWith.autorouter.maxPasses = 1;
      settingsWith.autorouter.maxItems = 15;
      settingsWith.decoupling = new DecouplingSettings();
      settingsWith.decoupling.enabled = true;
      settingsWith.decoupling.lockMode = DecouplingLockMode.HARD;
      settingsWith.decoupling.maxStrapLengthMm = 5.0;
      settingsWith.decoupling.maxOwnerRadiusMm = 10.0;
      settingsWith.decoupling.includeReviewCandidates = true;
      jobWith.routerSettings = settingsWith;

      app.freerouting.autoroute.pipeline.BatchAutorouter routerWith =
          new app.freerouting.autoroute.pipeline.BatchAutorouter(jobWith);
      routerWith.runBatchLoop();

      long viasWith =
          boardWith.getItems().stream()
              .filter(it -> it instanceof app.freerouting.board.model.items.Via)
              .count();
      double traceLenWith =
          boardWith.getItems().stream()
              .filter(it -> it instanceof app.freerouting.board.model.items.Trace)
              .mapToDouble(it -> ((app.freerouting.board.model.items.Trace) it).getLength())
              .sum();
      int drcWith = new DesignRulesChecker(boardWith, null).getAllClearanceViolations().size();
      long decouplingProtectedItems =
          boardWith.getItems().stream().filter(Item::isDecouplingProtected).count();

      // 4. COMPARISON SUMMARY
      System.out.println("--- Comparative Analysis: WITHOUT Decoupling vs WITH Decoupling ---");
      System.out.printf(
          "   Without Decoupling: Vias = %d, Total Trace Length = %.2f mm, DRC Violations = %d%n",
          viasWithout, traceLenWithout / 10000.0, drcWithout);
      System.out.printf(
          "   With Decoupling:    Vias = %d, Total Trace Length = %.2f mm, DRC Violations = %d, Protected Items = %d%n",
          viasWith, traceLenWith / 10000.0, drcWith, decouplingProtectedItems);
      System.out.printf(
          "   Delta: Vias diff = %+d, Trace length diff = %+.2f mm, DRC diff = %+d%n",
          (viasWith - viasWithout),
          (traceLenWith - traceLenWithout) / 10000.0,
          (drcWith - drcWithout));

      assertTrue(
          drcWith <= drcWithout,
          "DRC with decoupling must be clean / no new violations vs without");
    }
  }

  @Test
  void testDecouplingEdgeCases() throws IOException {
    // 1. Thread cancellation edge case: when stop requested, pre-pass terminates early
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    app.freerouting.core.StoppableThread stoppedThread =
        new app.freerouting.core.StoppableThread() {
          @Override
          protected void threadAction() {}
        };
    stoppedThread.requestStop();

    DecouplingRoutingSummary stoppedSummary =
        BatchDecouplingRouter.run(board, settings, stoppedThread);
    assertNotNull(stoppedSummary);
    assertEquals(0, stoppedSummary.strapsRouted(), "Should route 0 straps when thread is stopped");

    // 2. Layer disabled edge case: when layer 0 is disabled, pre-pass skips with LAYER_DISABLED
    RoutingBoard boardLayerDisabled = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settingsLayerDisabled = new DefaultSettings().getSettings();
    settingsLayerDisabled.setLayerCount(boardLayerDisabled.getLayerCount());
    settingsLayerDisabled.applyBoardSpecificOptimizations(boardLayerDisabled);
    settingsLayerDisabled.setLayerActive(0, false);
    settingsLayerDisabled.decoupling = new DecouplingSettings();
    settingsLayerDisabled.decoupling.enabled = true;

    DecouplingRoutingSummary layerDisabledSummary =
        BatchDecouplingRouter.run(boardLayerDisabled, settingsLayerDisabled, null);
    assertNotNull(layerDisabledSummary);
    assertEquals(0, layerDisabledSummary.strapsRouted());
    assertTrue(
        layerDisabledSummary.metrics().stream().anyMatch(m -> "LAYER_DISABLED".equals(m.status())),
        "Expected LAYER_DISABLED status when surface layer is inactive");
  }

  @Test
  void testPlaneOnlyFallbackMetricsReporting() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    // Set tiny owner radius so all candidates fall back to plane-only routing
    settings.decoupling.maxOwnerRadiusMm = 0.0001;

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertFalse(summary.metrics().isEmpty());

    // In PR2, plane-only tasks must never be left as unattempted NO_OWNER_IN_RADIUS
    for (DecouplingMetrics m : summary.metrics()) {
      assertFalse(
          "NO_OWNER_IN_RADIUS".equals(m.status()),
          "Plane-only task should be evaluated for plane routing, not left as NO_OWNER_IN_RADIUS");
      assertTrue(
          "PLANE_ONLY_ROUTED".equals(m.status())
              || "PLANE_VIA_FAILED".equals(m.status())
              || "NO_GROUND_PLANE".equals(m.status()),
          "Expected plane-only status (PLANE_ONLY_ROUTED, PLANE_VIA_FAILED, or NO_GROUND_PLANE), got: "
              + m.status());
    }
  }

  @Test
  void testSupplyPlaneViaDropAndProtection() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.maxStrapLengthMm = 4.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;

    // Enable containsPlane on the VCC supply rail nets (e.g. Net #2 or all non-ground rail nets)
    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      app.freerouting.rules.Net net = board.rules.nets.get(i);
      if (net != null) {
        net.setContainsPlane(true);
      }
    }

    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(summary.candidatesTotal() > 0);

    // Gate: No DRC regressions introduced by supply plane vias
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(
        postDrc <= baselineDrc,
        "Supply plane via drop must not introduce clearance violations (baseline="
            + baselineDrc
            + ", post="
            + postDrc
            + ")");

    // Verify all placed decoupling vias are protected and SYSTEM_FIXED in HARD mode
    int protectedViaCount = 0;
    for (Item item : board.getItems()) {
      if (item instanceof Via via && via.isDecouplingProtected()) {
        protectedViaCount++;
        assertEquals(FixedState.SYSTEM_FIXED, via.getFixedState());
      }
    }

    assertTrue(summary.viasPlaced() > 0, "Expected at least 1 plane decoupling via to be placed");
    assertTrue(
        protectedViaCount >= summary.viasPlaced(),
        "Expected at least "
            + summary.viasPlaced()
            + " protected vias, found: "
            + protectedViaCount);
  }

  @Test
  void testMutualInductanceFlankCoordinatedViaCoupling() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.mutualInductanceBias = true;
    settings.decoupling.maxStrapLengthMm = 4.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;

    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      app.freerouting.rules.Net net = board.rules.nets.get(i);
      if (net != null) {
        net.setContainsPlane(true);
      }
    }

    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(summary.candidatesTotal() > 0);

    // Gate: No DRC regressions introduced by biased flank via placement
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(
        postDrc <= baselineDrc,
        "Flank-coordinated via placement must not introduce clearance violations (baseline="
            + baselineDrc
            + ", post="
            + postDrc
            + ")");

    // Verify all placed decoupling vias are protected and SYSTEM_FIXED in HARD mode
    int protectedViaCount = 0;
    for (Item item : board.getItems()) {
      if (item instanceof Via via && via.isDecouplingProtected()) {
        protectedViaCount++;
        assertEquals(FixedState.SYSTEM_FIXED, via.getFixedState());
      }
    }
    assertTrue(
        summary.viasPlaced() > 0,
        "Expected at least 1 decoupling via to be placed with mutual inductance bias");
    assertTrue(
        protectedViaCount >= summary.viasPlaced(),
        "Expected at least "
            + summary.viasPlaced()
            + " protected vias, found: "
            + protectedViaCount);
    assertTrue(
        (summary.coupledViaPairs() + summary.fallbackViaPairs()) > 0,
        "Expected coupled or fallback via pairs to be recorded in telemetry");
  }

  @Test
  void testHighBiasWeightExecution() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.mutualInductanceBias = true;
    settings.decoupling.mutualInductanceBiasWeight = 0.5;

    // Verifies that aggressive dimensionless bias weight (0.5) operates stably
    // without causing A* loop stalls or routing regressions
    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(summary.candidatesTotal() > 0);
  }

  @Test
  void testCustomBiasWeightAndDualFlankProbe() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.mutualInductanceBias = true;
    settings.decoupling.mutualInductanceBiasWeight = 0.35;
    settings.decoupling.maxStrapLengthMm = 4.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;

    for (int i = 1; i <= board.rules.nets.maxNetNumber(); i++) {
      app.freerouting.rules.Net net = board.rules.nets.get(i);
      if (net != null) {
        net.setContainsPlane(true);
      }
    }

    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(
        postDrc <= baselineDrc,
        "Custom bias weight and dual-flank probe must not introduce clearance violations");
    assertTrue(summary.viasPlaced() > 0);
  }

  @Test
  void testOppositeSideDisabledSetting() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue269-caniot-tiny-arm.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.oppositeSideRouting = false;
    settings.decoupling.includeReviewCandidates = true;
    settings.decoupling.maxStrapLengthMm = 10.0;
    settings.decoupling.maxOwnerRadiusMm = 10.0;

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(
        summary.metrics().stream().anyMatch(m -> "OPPOSITE_SIDE_DISABLED".equals(m.status())),
        "Expected opposite-side candidates to report OPPOSITE_SIDE_DISABLED");
    assertFalse(
        summary.metrics().stream().anyMatch(m -> m.status().startsWith("ROUTED_OPPOSITE")),
        "Feedthrough or opposite-side fanout routing must not run when oppositeSideRouting is false");
  }

  @Test
  void testLayerParityThroughHolePinHandling() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.oppositeSideRouting = true;

    Pin thtPin = null;
    Pin smdPin = null;
    int sharedNet = -1;
    for (Item item : board.getItems()) {
      if (item instanceof Pin pin && pin.firstLayer() != pin.lastLayer()) {
        for (int netNo : pin.netNumbers) {
          for (Item other : board.getItems()) {
            if (other instanceof Pin otherPin
                && otherPin.firstLayer() == otherPin.lastLayer()
                && otherPin.containsNet(netNo)
                && !otherPin.equals(pin)) {
              thtPin = pin;
              smdPin = otherPin;
              sharedNet = netNo;
              break;
            }
          }
          if (thtPin != null) {
            break;
          }
        }
      }
      if (thtPin != null) {
        break;
      }
    }

    assertNotNull(thtPin, "Expected a through-hole pin on the board");
    assertNotNull(smdPin, "Expected an SMD pin sharing a net with the through-hole pin");
    assertTrue(thtPin.isOnLayer(smdPin.firstLayer()));
    assertTrue(thtPin.isOnLayer(thtPin.firstLayer()));
    assertTrue(thtPin.isOnLayer(thtPin.lastLayer()));

    app.freerouting.autoroute.maze.AutorouteControl control =
        new app.freerouting.autoroute.maze.AutorouteControl(board, sharedNet, settings);
    control.viasAllowed = false;
    control.ripupAllowed = false;
    for (int i = 0; i < control.layerCount; i++) {
      control.layerActive[i] = (i == smdPin.firstLayer());
    }

    double mmResolution =
        board.communication.getResolution(app.freerouting.board.model.structure.Unit.MM);

    BatchDecouplingRouter.StrapAttemptOutcome outcome =
        BatchDecouplingRouter.tryRouteStrap(
            board,
            settings,
            null,
            control,
            smdPin,
            thtPin,
            smdPin.getConnectedSet(sharedNet),
            thtPin.getConnectedSet(sharedNet),
            smdPin.firstLayer(),
            smdPin.firstLayer(),
            sharedNet,
            0,
            thtPin.getComponentId(),
            smdPin.getComponentId(),
            1.0,
            100.0,
            mmResolution);

    assertNotNull(outcome);
    assertTrue(
        outcome.success(),
        "Route between SMD pin and THT pin on capLayer must succeed: " + outcome.status());
    for (Item it : outcome.newItems()) {
      assertFalse(it instanceof Via, "No via may be placed when connecting to THT on capLayer");
    }
  }

  @Test
  void testStrictOneViaInvariantInTryRouteStrap() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);

    app.freerouting.board.model.items.Pin pin1 = null;
    app.freerouting.board.model.items.Pin pin2 = null;
    for (Item item : board.getItems()) {
      if (item instanceof app.freerouting.board.model.items.Pin pin && pin.containsNet(1)) {
        if (pin1 == null) {
          pin1 = pin;
        } else if (pin2 == null && !pin.equals(pin1)) {
          pin2 = pin;
          break;
        }
      }
    }

    assertNotNull(pin1);
    assertNotNull(pin2);

    app.freerouting.autoroute.maze.AutorouteControl control =
        new app.freerouting.autoroute.maze.AutorouteControl(board, 1, settings);
    control.viasAllowed = false;
    control.ripupAllowed = false;

    double mmResolution =
        board.communication.getResolution(app.freerouting.board.model.structure.Unit.MM);

    BatchDecouplingRouter.StrapAttemptOutcome outcome =
        BatchDecouplingRouter.tryRouteStrap(
            board,
            settings,
            null,
            control,
            pin1,
            pin2,
            pin1.getConnectedSet(1),
            pin2.getConnectedSet(1),
            pin1.firstLayer(),
            pin2.firstLayer(),
            1,
            2,
            pin2.getComponentId(),
            pin1.getComponentId(),
            1.0,
            50.0,
            mmResolution);

    assertNotNull(outcome);
    assertTrue(
        outcome.success(), "Strap route between pin1 and pin2 must succeed: " + outcome.status());
    for (Item it : outcome.newItems()) {
      assertFalse(it instanceof Via, "No via may be placed when viasAllowed = false");
    }
  }

  @Test
  void testCrossLayerStrapPlacesAtMostOneVia() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue269-caniot-tiny-arm.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);

    Pin topPin = null;
    Pin botPin = null;
    int targetNet = -1;
    for (Item item : board.getItems()) {
      if (item instanceof Pin pin && pin.firstLayer() == 0 && pin.lastLayer() == 0) {
        for (int netNo : pin.netNumbers) {
          for (Item other : board.getItems()) {
            if (other instanceof Pin otherPin
                && otherPin.firstLayer() == 1
                && otherPin.lastLayer() == 1
                && otherPin.containsNet(netNo)
                && !otherPin.equals(pin)) {
              double dist = pin.getCenter().toFloat().distance(otherPin.getCenter().toFloat());
              if (dist < 50000) {
                topPin = pin;
                botPin = otherPin;
                targetNet = netNo;
                break;
              }
            }
          }
          if (topPin != null) {
            break;
          }
        }
      }
      if (topPin != null) {
        break;
      }
    }

    if (topPin != null && botPin != null) {
      app.freerouting.autoroute.maze.AutorouteControl control =
          new app.freerouting.autoroute.maze.AutorouteControl(board, targetNet, settings);
      control.viasAllowed = true;
      control.ripupAllowed = false;
      control.layerActive[0] = true;
      control.layerActive[1] = true;

      double mmResolution =
          board.communication.getResolution(app.freerouting.board.model.structure.Unit.MM);

      BatchDecouplingRouter.StrapAttemptOutcome outcome =
          BatchDecouplingRouter.tryRouteStrap(
              board,
              settings,
              null,
              control,
              topPin,
              botPin,
              topPin.getConnectedSet(targetNet),
              botPin.getConnectedSet(targetNet),
              0,
              1,
              targetNet,
              0,
              botPin.getComponentId(),
              topPin.getComponentId(),
              1.0,
              10.0,
              mmResolution);

      assertNotNull(outcome);
      if (outcome.success()) {
        int vias = 0;
        for (Item it : outcome.newItems()) {
          if (it instanceof Via) {
            vias++;
          }
        }
        assertEquals(1, vias, "Cross-layer feed-through strap must place exactly 1 via");
      }
    }
  }

  @Test
  void testOppositeSideFeedThroughViaRoutingOnMultiLayerBoard() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue269-caniot-tiny-arm.dsn");
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.oppositeSideRouting = true;
    settings.decoupling.includeReviewCandidates = true;
    settings.decoupling.maxStrapLengthMm = 10.0;
    settings.decoupling.maxOwnerRadiusMm = 10.0;

    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(
        postDrc <= baselineDrc,
        "Opposite side decoupling must not introduce new clearance violations (baseline="
            + baselineDrc
            + ", post="
            + postDrc
            + ")");

    assertTrue(
        summary.metrics().stream().anyMatch(m -> m.status().startsWith("ROUTED_OPPOSITE")),
        "Expected at least one candidate with ROUTED_OPPOSITE status");
    assertTrue(
        summary.viasPlaced() > 0, "Expected at least one via placed in opposite-side routing");

    boolean hasLayer0 = false;
    boolean hasLayer1 = false;
    for (Item it : board.getItems()) {
      if (it.isDecouplingProtected() && it instanceof Trace trace) {
        if (trace.getLayer() == 0) {
          hasLayer0 = true;
        } else if (trace.getLayer() == 1) {
          hasLayer1 = true;
        }
      }
    }

    assertTrue(hasLayer0 && hasLayer1, "Expected decoupling traces on both Layer 0 and Layer 1");
  }

  @Test
  void testIsPlaneDirectlyUnderneathDetection() throws IOException {
    RoutingBoard boardPlaneLess = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    // Invalid net safety
    assertFalse(BatchDecouplingRouter.isPlaneDirectlyUnderneath(boardPlaneLess, null, -1));
    assertFalse(BatchDecouplingRouter.isPlaneDirectlyUnderneath(boardPlaneLess, null, 0));

    // DAC2020_bm01 is a 2-layer board without planes; verify plane underneath is correctly reported
    // as false
    IdentifierConfig config = IdentifierConfig.defaultConfig();
    IdentificationResult idResult = DecouplingIdentifier.identify(boardPlaneLess, config);
    CapacitorCandidate capPlaneLess =
        idResult.candidates().stream()
            .filter(c -> c.verdict() == Verdict.DECOUPLING)
            .findFirst()
            .orElseThrow();

    int gndPinIdPlaneLess =
        (capPlaneLess.netNumberA() == capPlaneLess.groundNetNumber())
            ? capPlaneLess.pinIdA()
            : capPlaneLess.pinIdB();
    Pin gndPinPlaneLess = (Pin) boardPlaneLess.getItem(gndPinIdPlaneLess);
    assertNotNull(gndPinPlaneLess);
    assertFalse(
        BatchDecouplingRouter.isPlaneDirectlyUnderneath(
            boardPlaneLess, gndPinPlaneLess, capPlaneLess.groundNetNumber()),
        "2-layer board without planes must report isPlaneDirectlyUnderneath as false");

    // Issue015-StackOverflow.dsn has a bottom-layer B.Cu plane on GND; verify it detects true
    RoutingBoard boardWithPlane = DsnTestFixtures.loadBoard("Issue015-StackOverflow.dsn");
    Net gndNet = boardWithPlane.rules.nets.get("GND").iterator().next();
    assertNotNull(gndNet);
    boolean detectedOnPlaneBoard = false;
    for (Item it : boardWithPlane.getItems()) {
      if (it instanceof Pin p && p.containsNet(gndNet.netNumber) && p.firstLayer() == 0) {
        if (BatchDecouplingRouter.isPlaneDirectlyUnderneath(boardWithPlane, p, gndNet.netNumber)) {
          detectedOnPlaneBoard = true;
          break;
        }
      }
    }
    assertTrue(
        detectedOnPlaneBoard,
        "Board with bottom GND plane must detect isPlaneDirectlyUnderneath as true for top SMD pins");
  }

  @Test
  void testDecouplingPreRoutingWithGroundLoopProtection() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue508-DAC2020_bm01.dsn");
    int baselineDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.decoupling = new DecouplingSettings();
    settings.decoupling.enabled = true;
    settings.decoupling.lockMode = DecouplingLockMode.HARD;
    settings.decoupling.maxStrapLengthMm = 4.0;
    settings.decoupling.maxOwnerRadiusMm = 8.0;

    DecouplingRoutingSummary summary = BatchDecouplingRouter.run(board, settings, null);
    assertNotNull(summary);
    assertTrue(summary.candidatesTotal() > 0);
    assertTrue(summary.strapsRouted() > 0, "Expected surface straps routed on DAC2020_bm01");

    // Zero DRC regression
    int postDrc = new DesignRulesChecker(board, null).getAllClearanceViolations().size();
    assertTrue(postDrc <= baselineDrc);
  }
}
