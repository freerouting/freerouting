package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Configuration for the decoupling capacitor pre-routing pass.
 *
 * <p>All fields follow the {@link RouterSettings} nullable-field contract: a {@code null} value
 * means "this source has no opinion" and is skipped by {@code SettingsMerger}. Hardcoded defaults
 * live exclusively in {@link app.freerouting.settings.sources.DefaultSettings}.
 */
public class DecouplingSettings implements Serializable, Cloneable {

  /**
   * Whether to run the decoupling capacitor pre-routing pass. Defaults to {@code false} in {@link
   * app.freerouting.settings.sources.DefaultSettings} to preserve non-regression invariants.
   */
  @SerializedName("enabled")
  public Boolean enabled;

  /** Locking mode applied to routed decoupling straps and vias. */
  @SerializedName("lock_mode")
  public DecouplingLockMode lockMode;

  /**
   * Maximum Euclidean search radius in millimetres between a decoupling capacitor and an IC pin.
   */
  @SerializedName("max_owner_radius_mm")
  public Double maxOwnerRadiusMm;

  /** Maximum allowed surface strap length in millimetres between capacitor pad and IC pin. */
  @SerializedName("max_strap_length_mm")
  public Double maxStrapLengthMm;

  /**
   * Multiplier applied to net-class trace width for low-inductance surface straps. Clamped to >=
   * max(netClassWidth, neckWidthUm).
   */
  @SerializedName("trace_width_scale")
  public Double traceWidthScale;

  /**
   * Multiplier applied to the ripup cost of decoupling traces and vias during main autoroute passes
   * when soft-locked.
   */
  @SerializedName("ripup_cost_multiplier")
  public Double ripupCostMultiplier;

  /** Maximum number of maze door expansions allowed per decoupling strap connection attempt. */
  @SerializedName("max_door_expansions")
  public Integer maxDoorExpansions;

  /** Whether to route review-grade candidates in addition to confident decoupling candidates. */
  @SerializedName("include_review_candidates")
  public Boolean includeReviewCandidates;

  /**
   * Whether to directionally bias decoupling via placement toward the coupled pin/via to maximize
   * mutual-inductance coupling and minimize loop area.
   */
  @SerializedName("mutual_inductance_bias")
  public Boolean mutualInductanceBias;

  /**
   * Dimensionless weight factor applied to the Euclidean distance from the flank target when
   * scoring via candidates in A* maze expansion.
   */
  @SerializedName("mutual_inductance_bias_weight")
  public Double mutualInductanceBiasWeight;

  /**
   * Whether to route opposite-side SMD decoupling capacitors where the capacitor sits on the
   * opposite layer from the owner IC pin (e.g. bottom-side capacitor under a top-side IC pad). When
   * enabled, allows at most 1 feed-through via transition or targets an existing fanout via.
   */
  @SerializedName("opposite_side_routing")
  public Boolean oppositeSideRouting;

  /** No-arg constructor required for deserialisation and {@link #clone()}. */
  public DecouplingSettings() {}

  /**
   * Creates a deep copy of this {@code DecouplingSettings} object. All fields are immutable
   * wrappers or enums, so a shallow clone is sufficient.
   */
  @Override
  public DecouplingSettings clone() {
    try {
      return (DecouplingSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
