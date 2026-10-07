package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * Execution knobs for the batch autorouter stage (fanout and optimizer stay in their own objects).
 * All fields are nullable so {@code SettingsMerger} can tell "this source sets the field" from
 * "this source has no opinion".
 */
@Schema(name = "AutorouterSettings", description = "Execution settings for the autorouter stage")
public class AutorouterSettings implements Serializable, Cloneable {

  /** Whether the autorouter stage runs after fanout. */
  @SerializedName("enabled")
  @Schema(description = "Whether the autorouter stage runs after fanout")
  public Boolean enabled;

  /** Algorithm identifier (for example {@link RouterSettings#ALGORITHM_CURRENT}). */
  @SerializedName("algorithm")
  @Schema(description = "Algorithm identifier (e.g. 'freerouting-router')")
  public String algorithm;

  /** Maximum autorouter passes. {@code 0} means no limit. */
  @SerializedName("max_passes")
  @Schema(description = "Maximum autorouter passes (0 means unlimited)")
  public Integer maxPasses;

  /** Maximum items attempted in the autorouter stage. */
  @SerializedName("max_items")
  @Schema(description = "Maximum items attempted in the autorouter stage")
  public Integer maxItems;

  /**
   * Kept for the canonical CLI {@code --router.autorouter.max_threads}. The autorouter pass does
   * not read it. The optimizer pool uses {@code router.optimizer.max_threads}.
   */
  @SerializedName("max_threads")
  @Schema(description = "Maximum worker threads for the autorouter stage")
  public Integer maxThreads;

  /** When true, intermediate board snapshots are saved between autorouter passes. */
  @SerializedName("save_intermediate_stages")
  @Schema(description = "When true, intermediate board snapshots are saved between passes")
  public Boolean saveIntermediateStages;

  /** Net class names the autorouter should skip. */
  @SerializedName("ignore_net_classes")
  @Schema(
      description = "Array of exact net class names to skip/ignore during autorouting",
      example = "[\"kicad_default\"]")
  public String[] ignoreNetClasses;

  /**
   * Comma-separated routing fallback strategies, tried in order when the pass loop gives up with
   * unrouted connections or new clearance violations; "none" disables the fallback.
   */
  @SerializedName("fallback_strategies")
  @Schema(
      description =
          "Comma-separated routing fallback strategies tried if pass loop gives up with unrouted"
              + " connections; 'none' disables fallback",
      example = "fanout-retry,short-escape,open-escape,no-fanout")
  public String fallbackStrategies;

  /** Wall-clock budget in seconds for all fallback attempts together. */
  @SerializedName("fallback_max_seconds")
  @Schema(description = "Wall-clock budget in seconds for all fallback attempts together")
  public Double fallbackMaxSeconds;

  /** Via cost factor on pure-SMD nets. */
  @SerializedName("smd_net_via_cost_factor")
  @Schema(description = "Via cost factor on pure-SMD nets")
  public Double smdNetViaCostFactor;

  /** When true, allows placing vias directly inside pads. */
  @SerializedName("via_in_pad")
  @Schema(description = "When true, allows placing vias directly inside pads")
  public Boolean viaInPad;

  /** When true, prefers placing vias at pad center of gravity. */
  @SerializedName("via_centre_of_gravity")
  @Schema(description = "When true, prefers placing vias at pad center of gravity")
  public Boolean viaCentreOfGravity;

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
