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
  @SerializedName("macros")
  public boolean macros;
  @SerializedName("other_menu")
  public boolean otherMenu;
  @SerializedName("snapshots")
  public boolean snapshots;
  @SerializedName("file_load_dialog_at_startup")
  public boolean fileLoadDialogAtStartup;
  @SerializedName("save_jobs")
  public boolean saveJobs;
}