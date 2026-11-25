package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.physics.BoardFieldContext;
import app.freerouting.board.physics.PhysicsEvaluator;
import app.freerouting.board.physics.PhysicsOptimizationPass;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Collection;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * End-to-end pipeline, feature-flag, determinism, CLI plumbing, and DRC safety tests for the
 * standalone physics subsystem.
 */
class PhysicsPipelineTest extends RoutingFixtureTest {

  private static TestingSettings createBoundedSettings(boolean physicsEnabled) {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(2);
    settings.setJobTimeoutString("00:01:00");
    if (settings.getSettings().optimizer == null) {
      settings.getSettings().optimizer = new app.freerouting.settings.OptimizerSettings();
    }
    settings.getSettings().optimizer.enabled = true;
    settings.getSettings().optimizer.physics = new app.freerouting.settings.PhysicsSettings();
    settings.getSettings().optimizer.physics.enabled = physicsEnabled;
    settings.getSettings().optimizer.physics.gridResolutionMm = 0.5;
    settings.getSettings().optimizer.physics.maxIterations = 4;
    settings.getSettings().optimizer.physics.identityWeight = 1.0;
    return settings;
  }

  @Test
  void disabledPhysicsMatchesEnabledIdentityOutputDeterministicallyAndCleanly() {
    FRLogger.granularTraceEnabled = false;

    // 1. Baseline run (physics disabled)
    RoutingJob baselineJob =
        getRoutingJob("Issue508-DAC2020_bm01.dsn", createBoundedSettings(false));
    long baselineStart = System.nanoTime();
    runRoutingJob(baselineJob);
    long baselineNanos = System.nanoTime() - baselineStart;
    Collection<ClearanceViolation> baselineViolations =
        new DesignRulesChecker(baselineJob.board, null).getAllClearanceViolations();
    assertTrue(baselineViolations.isEmpty(), "Baseline run must have 0 DRC violations");

    // 2. Physics enabled run #1 (identity rule)
    RoutingJob physicsJob1 =
        getRoutingJob("Issue508-DAC2020_bm01.dsn", createBoundedSettings(true));
    long physicsStart1 = System.nanoTime();
    runRoutingJob(physicsJob1);
    long physicsNanos1 = System.nanoTime() - physicsStart1;
    Collection<ClearanceViolation> physicsViolations1 =
        new DesignRulesChecker(physicsJob1.board, null).getAllClearanceViolations();
    assertTrue(physicsViolations1.isEmpty(), "Physics run #1 must have 0 DRC violations");

    // 3. Physics enabled run #2 (determinism check)
    RoutingJob physicsJob2 =
        getRoutingJob("Issue508-DAC2020_bm01.dsn", createBoundedSettings(true));
    runRoutingJob(physicsJob2);
    Collection<ClearanceViolation> physicsViolations2 =
        new DesignRulesChecker(physicsJob2.board, null).getAllClearanceViolations();
    assertTrue(physicsViolations2.isEmpty(), "Physics run #2 must have 0 DRC violations");

    // Assert identical board hash & incomplete count across baseline and repeated physics runs
    assertEquals(
        baselineJob.board.routingLedger().incompleteCount(),
        physicsJob1.board.routingLedger().incompleteCount(),
        "Identity physics pass must not alter incomplete connection count");
    assertEquals(
        baselineJob.board.getHash(),
        physicsJob1.board.getHash(),
        "Identity physics pass must preserve exact board hash");
    assertEquals(
        physicsJob1.board.getHash(),
        physicsJob2.board.getHash(),
        "Repeated physics runs must be 100% deterministic");

    // Benchmark metrics hook
    BoardFieldContext ctx = BoardFieldContext.fromBoard(physicsJob1.board, 0.5);
    double totalEnergy = PhysicsEvaluator.identity(1.0).evaluateBoard(physicsJob1.board, ctx);
    PhysicsOptimizationPass.Result directPass =
        PhysicsOptimizationPass.run(
            physicsJob1.board, physicsJob1.routerSettings.optimizer.physics, null);
    long peakHeapMb =
        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);

    FRLogger.info(
        String.format(
            Locale.US,
            "Physics benchmark [Issue508-DAC2020_bm01]: baseline=%.2f ms, physics=%.2f ms, "
                + "directPass=%.3f ms, energy=%.4f, drc=%d, heap=%d MB",
            baselineNanos / 1e6,
            physicsNanos1 / 1e6,
            directPass.elapsedNanos() / 1e6,
            totalEnergy,
            physicsViolations1.size(),
            peakHeapMb));
  }

  @Test
  void cliFlagsMapToPhysicsSettingsViaSnakeCase() {
    CliSettings cli =
        new CliSettings(
            new String[] {
              "--router.optimizer.physics.enabled=true",
              "--router.optimizer.physics.grid_resolution_mm=0.25",
              "--router.optimizer.physics.max_iterations=12",
              "--router.optimizer.physics.identity_weight=2.5"
            });

    var physics = cli.getSettings().optimizer.physics;
    assertNotNull(physics);
    assertTrue(Boolean.TRUE.equals(physics.enabled));
    assertEquals(0.25, physics.gridResolutionMm);
    assertEquals(12, physics.maxIterations);
    assertEquals(2.5, physics.identityWeight);
  }

  @Test
  void defaultSettingsDisablesPhysicsByDefault() {
    var defaults = new app.freerouting.settings.sources.DefaultSettings().getSettings();
    assertNotNull(defaults.optimizer.physics);
    assertFalse(Boolean.TRUE.equals(defaults.optimizer.physics.enabled));
    assertEquals(0.5, defaults.optimizer.physics.gridResolutionMm);
    assertEquals(8, defaults.optimizer.physics.maxIterations);
    assertEquals(1.0, defaults.optimizer.physics.identityWeight);
  }
}
