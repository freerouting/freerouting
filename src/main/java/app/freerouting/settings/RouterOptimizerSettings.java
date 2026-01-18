package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class RouterOptimizerSettings implements Serializable {

  @SerializedName("enabled")
  public Boolean enabled;
  @SerializedName("algorithm")
  public String algorithm;
  @SerializedName("max_passes")
  public Integer maxPasses;
  @SerializedName("max_threads")
  public Integer maxThreads;
  @SerializedName("improvement_threshold")
  public Float optimizationImprovementThreshold;
  public transient BoardUpdateStrategy boardUpdateStrategy;
  public transient String hybridRatio;
  public transient ItemSelectionStrategy itemSelectionStrategy;

  public RouterOptimizerSettings() {
    // Initialize with default values
    this.enabled = false;
    this.algorithm = "freerouting-optimizer";
    this.maxPasses = 100;
    this.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.optimizationImprovementThreshold = 0.01f;
    this.boardUpdateStrategy = BoardUpdateStrategy.GREEDY;
    this.hybridRatio = "1:1";
    this.itemSelectionStrategy = ItemSelectionStrategy.PRIORITIZED;
  }
}