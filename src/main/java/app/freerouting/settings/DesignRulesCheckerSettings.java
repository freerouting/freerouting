package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class DesignRulesCheckerSettings implements Serializable
{
  @SerializedName("enabled")
  public transient boolean enabled = false;
  @SerializedName("include_warnings")
  public boolean includeWarnings = true;
  @SerializedName("include_errors")
  public boolean includeErrors = true;

  /**
   * Copy constructor
   */
  @Override
  public DesignRulesCheckerSettings clone()
  {
    DesignRulesCheckerSettings clone = new DesignRulesCheckerSettings();
    clone.enabled = this.enabled;
    clone.includeWarnings = this.includeWarnings;
    clone.includeErrors = this.includeErrors;
    return clone;
  }
}