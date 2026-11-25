package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Configuration for the standalone, engine-agnostic board physics subsystem.
 *
 * <p>All fields are nullable reference types with no inline defaults so {@link SettingsMerger} can
 * layer configuration sources without low-priority sources overriding higher-priority ones.
 */
public class PhysicsSettings implements Serializable, Cloneable {

  /** Master enable flag for running the post-route physics pass. */
  @SerializedName("enabled")
  public Boolean enabled;

  /** Target coarse 2D field grid cell size in millimeters. */
  @SerializedName("grid_resolution_mm")
  public Double gridResolutionMm;

  /** Maximum number of relaxation/evaluation iterations per optimization pass. */
  @SerializedName("max_iterations")
  public Integer maxIterations;

  /** Weight applied to the baseline identity/no-op physics rule. */
  @SerializedName("identity_weight")
  public Double identityWeight;

  /** Creates physics settings without source-specific values. */
  public PhysicsSettings() {}

  @Override
  public PhysicsSettings clone() {
    try {
      return (PhysicsSettings) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }
}
