package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class GuiSettings implements Serializable
{
  @SerializedName("enabled")
  public Boolean isEnabled = true;
  @SerializedName("running")
  public transient Boolean isRunning = false;
  @SerializedName("input_directory")
  public String inputDirectory = "";
  @SerializedName("dialog_confirmation_timeout")
  public int dialogConfirmationTimeout = 5;
  public transient boolean exitWhenFinished;
}