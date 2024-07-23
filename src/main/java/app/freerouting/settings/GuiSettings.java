package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

public class GuiSettings
{
  @SerializedName("enabled")
  public Boolean isEnabled = true;
  @SerializedName("input_directory")
  public String input_directory = "";
  @SerializedName("dialog_confirmation_timeout")
  public int dialog_confirmation_timeout = 5;
}