package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

public class DisabledFeaturesSettings
{
  @SerializedName("logging")
  public boolean logging = false;
  @SerializedName("multi_threading")
  public boolean multiThreading = true;
  @SerializedName("select_mode")
  public boolean selectMode = true;
  @SerializedName("macros")
  public boolean macros = true;
  @SerializedName("other_menu")
  public boolean otherMenu = true;
  @SerializedName("snapshots")
  public boolean snapshots = true;
  @SerializedName("file_load_dialog_at_startup")
  public boolean fileLoadDialogAtStartup = true;
}