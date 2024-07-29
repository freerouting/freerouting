package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class FeatureFlagsSettings implements Serializable
{
  @SerializedName("logging")
  public boolean logging = true;
  @SerializedName("multi_threading")
  public boolean multiThreading = false;
  @SerializedName("select_mode")
  public boolean selectMode = false;
  @SerializedName("macros")
  public boolean macros = false;
  @SerializedName("other_menu")
  public boolean otherMenu = false;
  @SerializedName("snapshots")
  public boolean snapshots = false;
  @SerializedName("file_load_dialog_at_startup")
  public boolean fileLoadDialogAtStartup = false;
}