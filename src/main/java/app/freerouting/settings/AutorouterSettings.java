package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Execution knobs for the batch autorouter stage (fanout and optimizer stay in their own objects).
 * All fields are nullable so {@code SettingsMerger} can tell "this source sets the field" from
 * "this source has no opinion".
 */
public class AutorouterSettings implements Serializable, Cloneable {

  /** Whether the autorouter stage runs after fanout. */
  @SerializedName("enabled")
  public Boolean enabled;

  /** Algorithm identifier (for example {@link RouterSettings#ALGORITHM_CURRENT}). */
  @SerializedName("algorithm")
  public String algorithm;

  /** Maximum autorouter passes. {@code 0} means no limit. */
  @SerializedName("max_passes")
  public Integer maxPasses;

  /** Maximum items attempted in the autorouter stage. */
  @SerializedName("max_items")
  public Integer maxItems;

  /**
   * Maximum worker threads for a multi-thread autorouter pass. The production batch loop currently
   * runs a single-thread pass; this limit applies to {@code AutoroutePassRunner.runMultiThread}.
   * Canonical CLI: {@code --router.autorouter.max_threads}. Independent of {@code
   * router.optimizer.max_threads}.
   */
  @SerializedName("max_threads")
  public Integer maxThreads;

  /** When true, intermediate board snapshots are saved between autorouter passes. */
  @SerializedName("save_intermediate_stages")
  public Boolean saveIntermediateStages;

  /** Net class names the autorouter should skip. */
  @SerializedName("ignore_net_classes")
  public String[] ignoreNetClasses;

  @Override
  public AutorouterSettings clone() {
    try {
      AutorouterSettings copy = (AutorouterSettings) super.clone();
      if (this.ignoreNetClasses != null) {
        copy.ignoreNetClasses = this.ignoreNetClasses.clone();
      }
      return copy;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
