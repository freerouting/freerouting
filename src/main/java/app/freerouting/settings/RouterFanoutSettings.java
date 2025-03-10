package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class RouterFanoutSettings implements Serializable
{
  @SerializedName("enabled")
  public boolean enabled = false;
  @SerializedName("algorithm")
  public String algorithm = "freerouting-fanout";
  @SerializedName("max_passes")
  public int maxPasses = 100;
}