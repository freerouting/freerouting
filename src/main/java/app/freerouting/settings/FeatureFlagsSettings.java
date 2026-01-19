package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class FeatureFlagsSettings implements Serializable {

  @SerializedName("logging")
  public boolean logging = true;
  @SerializedName("multi_threading")
  public boolean multiThreading = true;
  @SerializedName("select_mode")
  public boolean selectMode;

  @SerializedName("other_menu")
  public boolean otherMenu;

  @SerializedName("save_jobs")
  public boolean saveJobs;
}