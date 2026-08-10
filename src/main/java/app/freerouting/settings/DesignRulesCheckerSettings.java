package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Configuration controlling design-rule-check reporting. */
public class DesignRulesCheckerSettings implements Serializable {

  /** Whether the checker is enabled. */
  @SerializedName("enabled")
  public transient boolean enabled;

  /** Whether warning-level violations are included in reports. */
  @SerializedName("include_warnings")
  public boolean includeWarnings = true;

  /** Whether error-level violations are included in reports. */
  @SerializedName("include_errors")
  public boolean includeErrors = true;

  /** Creates a copy of this design-rule-checker configuration. */
  @Override
  public DesignRulesCheckerSettings clone() {
    DesignRulesCheckerSettings clone = new DesignRulesCheckerSettings();
    clone.enabled = this.enabled;
    clone.includeWarnings = this.includeWarnings;
    clone.includeErrors = this.includeErrors;
    return clone;
  }
}
