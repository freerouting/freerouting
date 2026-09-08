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
}
