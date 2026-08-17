package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Settings controlling the graphical user interface session. */
public class GuiApplicationSettings implements Serializable {

  /** Whether the GUI should be enabled. */
  @SerializedName("enabled")
  public Boolean isEnabled = true;

  /** Whether the GUI is currently running. */
  @SerializedName("running")
  public transient Boolean isRunning = false;

  /** Directory used when selecting input design files. */
  @SerializedName("input_directory")
  public String inputDirectory = "";

  /** Timeout in seconds for dialog confirmations. */
  @SerializedName("dialog_confirmation_timeout")
  public int dialogConfirmationTimeout = 5;

  /** Whether the application exits after the current operation finishes. */
  public transient boolean exitWhenFinished;
}
