package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Credentials and endpoint settings for Google Sheets authentication. */
public class GoogleSheetsProviderSettings implements Serializable {

  /** Google API key used by the provider. */
  @SerializedName("google_api_key")
  public String googleApiKey;

  /** URL of the Google Sheet used by the provider. */
  @SerializedName("sheet_url")
  public String sheetUrl;
}
