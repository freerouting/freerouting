package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class FeatureFlagsSettings implements Serializable {

  @SerializedName("multi_threading")
  public boolean multiThreading = true;
  @SerializedName("inspection_mode")
  public boolean inspectionMode;
  @SerializedName("other_menu")
  public boolean otherMenu;
  @SerializedName("save_jobs")
  public boolean saveJobs;
}