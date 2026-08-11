package app.freerouting.settings.sources;

import app.freerouting.settings.OptimizerSettings;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.ScoringSettings;
import app.freerouting.settings.SettingsSource;

/**
 * Settings source for unit tests. Allows injecting specific settings needed for testing with high
 * priority.
 */
public class TestingSettings implements SettingsSource {

  private final RouterSettings settings;

  /** Creates testing settings with null nested defaults for merge safety. */
  public TestingSettings() {
    this.settings = new RouterSettings();
    // Set complex objects to null to avoid overwriting them with defaults during
    // merge
    // (RouterSettings constructor initializes them)
    this.settings.optimizer = null;
    this.settings.scoring = null;
    // Keep legacy fixture expectations stable unless a test explicitly opts in.
    this.settings.copperToEdgeClearanceUm = 0.0;
  }

  /** Sets the bend cost for a layer, expanding the layer array if needed. */
  public void setBendCost(int layer, double bendCost) {
    if (this.settings.layers == null) {
      this.settings.setLayerCount(layer + 1);
    } else if (layer >= this.settings.layers.length) {
      this.settings.setLayerCount(layer + 1);
    }
    this.settings.setBendCost(layer, bendCost);
  }

  /** Sets the default bend cost in the scoring settings block. */
  public void setDefaultBendCost(double defaultBendCost) {
    if (this.settings.scoring == null) {
      this.settings.scoring = new ScoringSettings();
    }
    this.settings.scoring.defaultBendCost = defaultBendCost;
  }

  /** Sets the maximum number of routing items per pass. */
  public void setMaxItems(int maxItems) {
    this.settings.maxItems = maxItems;
  }

  /** Sets the maximum routing pass count when not already configured. */
  public void setMaxPasses(int maxPasses) {
    if (this.settings.maxPasses == null) {
      this.settings.maxPasses = maxPasses;
    }
  }

  /** Sets the job timeout string when not already configured. */
  public void setJobTimeoutString(String jobTimeoutString) {
    if (this.settings.jobTimeoutString == null) {
      this.settings.jobTimeoutString = jobTimeoutString;
    }
  }

  /** Enables or disables fanout routing in the testing settings. */
  public void setFanoutEnabled(boolean enabled) {
    if (this.settings.fanout == null) {
      this.settings.fanout = new app.freerouting.settings.FanoutSettings();
    }
    this.settings.fanout.enabled = enabled;
  }

  /** Enables or disables the autorouter. */
  public void setRouterEnabled(boolean enabled) {
    this.settings.enabled = enabled;
  }

  /** Sets the copper-to-edge clearance override used by tests. */
  public void setCopperToEdgeClearanceUm(double copperToEdgeClearanceUm) {
    this.settings.copperToEdgeClearanceUm = copperToEdgeClearanceUm;
  }

  /** Sets the hole clearance override used by tests. */
  public void setHoleClearanceUm(double holeClearanceUm) {
    this.settings.holeClearanceUm = holeClearanceUm;
  }

  /** Sets the neck width override used by tests. */
  public void setNeckWidthUm(double neckWidthUm) {
    this.settings.neckWidthUm = neckWidthUm;
  }

  /** Enables strict DRC behavior for the test run. */
  public void setStrictDrc(boolean strictDrc) {
    this.settings.strictDrc = strictDrc;
  }

  @Override
  public RouterSettings getSettings() {
    return settings;
  }

  @Override
  public String getSourceName() {
    return "Testing Settings";
  }

  @Override
  public int getPriority() {
    // High priority to override other sources (API is 70)
    return 80;
  }

  /** Sets the router enabled flag when not already configured. */
  public void setEnabled(boolean enabled) {
    if (this.settings.enabled == null) {
      this.settings.enabled = enabled;
    }
  }

  /** Enables or disables the optimizer phase. */
  public void setOptimizerEnabled(boolean enabled) {
    if (this.settings.optimizer == null) {
      this.settings.optimizer = new OptimizerSettings();
    }
    this.settings.optimizer.enabled = enabled;
  }

  /** Sets the fanout pass limit. */
  public void setFanoutMaxPasses(int maxPasses) {
    if (this.settings.fanout == null) {
      this.settings.fanout = new app.freerouting.settings.FanoutSettings();
    }
    this.settings.fanout.maxPasses = maxPasses;
  }

  /** Sets the fanout item limit. */
  public void setFanoutMaxItems(int maxItems) {
    if (this.settings.fanout == null) {
      this.settings.fanout = new app.freerouting.settings.FanoutSettings();
    }
    this.settings.fanout.maxItems = maxItems;
  }

  /** Sets the optimizer pass limit. */
  public void setOptimizerMaxPasses(int maxPasses) {
    if (this.settings.optimizer == null) {
      this.settings.optimizer = new OptimizerSettings();
    }
    this.settings.optimizer.maxPasses = maxPasses;
  }

  /** Sets the optimizer item limit. */
  public void setOptimizerMaxItems(int maxItems) {
    if (this.settings.optimizer == null) {
      this.settings.optimizer = new OptimizerSettings();
    }
    this.settings.optimizer.maxItems = maxItems;
  }
}
