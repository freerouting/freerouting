package app.freerouting.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.settings.sources.CliSettings;
import app.freerouting.settings.sources.DefaultSettings;
import org.junit.jupiter.api.Test;

class ScoringVersionSettingsTest {

  @Test
  void defaultSettingsUseCurrentRouterAndOptimizerVersions() {
    RouterSettings settings = new DefaultSettings().getSettings();

    assertEquals(RouterScoringVersion.V2_CONTINUOUS, settings.routerScoring.version);
    assertEquals(OptimizerScoringVersion.V2_LOWER_BOUND, settings.optimizerScoring.version);
  }

  @Test
  void routerAndOptimizerVersionsCanBeSelectedIndependently() {
    RouterSettings settings =
        new CliSettings(
                new String[] {"--router-scoring-version=v2", "--optimizer-scoring-version=v1"})
            .getSettings();

    assertEquals(RouterScoringVersion.V2_CONTINUOUS, settings.routerScoring.version);
    assertEquals(OptimizerScoringVersion.V1_LEGACY, settings.optimizerScoring.version);
  }

  @Test
  void convenienceVersionSelectsBoth() {
    RouterSettings settings = new CliSettings(new String[] {"--scoring-version=v1"}).getSettings();

    assertEquals(RouterScoringVersion.V1_LEGACY, settings.routerScoring.version);
    assertEquals(OptimizerScoringVersion.V1_LEGACY, settings.optimizerScoring.version);
  }

  @Test
  void clonedSettingsRetainIndependentVersionSelections() {
    RouterSettings settings = new RouterSettings();
    settings.routerScoring.version = RouterScoringVersion.V2_CONTINUOUS;
    settings.optimizerScoring.version = OptimizerScoringVersion.V1_LEGACY;

    RouterSettings clone = settings.clone();

    assertEquals(RouterScoringVersion.V2_CONTINUOUS, clone.routerScoring.version);
    assertEquals(OptimizerScoringVersion.V1_LEGACY, clone.optimizerScoring.version);
  }

  @Test
  void scoringVersionCliOverridesDefaultV2AndKeepsV2Weights() {
    RouterSettings merged =
        new SettingsMerger(
                new DefaultSettings(), new CliSettings(new String[] {"--scoring-version=v1"}))
            .merge();

    assertEquals(RouterScoringVersion.V1_LEGACY, merged.routerScoring.version);
    assertEquals(OptimizerScoringVersion.V1_LEGACY, merged.optimizerScoring.version);
    assertEquals(
        DefaultSettings.DEFAULT_ROUTER_UNROUTED_FIRST_HALF_WEIGHT,
        merged.routerScoring.unroutedFirstHalfWeight,
        0.001f);
    assertEquals(
        DefaultSettings.DEFAULT_OPTIMIZER_EXCESS_LENGTH_WEIGHT,
        merged.optimizerScoring.excessWireLengthWeight,
        0.001f);
    assertEquals(DefaultSettings.DEFAULT_VIA_COSTS, merged.scoring.viaCosts);
  }

  @Test
  void defaultOptimizerImprovementThresholdIsDefaultPercentage() {
    RouterSettings settings = new DefaultSettings().getSettings();
    assertEquals(
        DefaultSettings.DEFAULT_OPTIMIZER_IMPROVEMENT_THRESHOLD,
        settings.optimizer.optimizationImprovementThreshold,
        0.0f);
  }
}
