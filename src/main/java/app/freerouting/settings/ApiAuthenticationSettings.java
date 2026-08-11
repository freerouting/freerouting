package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Authentication configuration shared by the REST API and MCP servers. */
public class ApiAuthenticationSettings implements Serializable {

  /** Whether authentication is required for incoming requests. */
  @SerializedName("enabled")
  public Boolean isEnabled = true;

  /** Comma-separated authentication providers enabled for the server. */
  @SerializedName("providers")
  public String providers = "";

  /** Configuration for the Google Sheets authentication provider. */
  @SerializedName("google_sheets")
  public GoogleSheetsProviderSettings googleSheets = new GoogleSheetsProviderSettings();
}
