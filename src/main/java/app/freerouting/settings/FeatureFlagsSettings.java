package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Feature toggles controlling optional Freerouting capabilities. */
public class FeatureFlagsSettings implements Serializable {

  /** Whether multi-threaded operations are enabled. Default is false. */
  @SerializedName("multi_threading")
  public boolean multiThreading;

  /** Whether inspection mode is enabled. */
  @SerializedName("inspection_mode")
  public boolean inspectionMode;

  /** Whether the additional menu is shown. */
  @SerializedName(
      value = "other_menu",
      alternate = {"otherMenu"})
  public boolean otherMenu;

  /** Whether completed jobs may be saved. */
  @SerializedName("save_jobs")
  public boolean saveJobs;
}
