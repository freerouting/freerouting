package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ApiKeysLocationSettings implements Serializable {

  /*
   * The google_sheets field is used to specify the location of the Google Sheets
   * spreadsheet that contains the allowed API keys.
   *
   * The value of this field is set to the environment variable
   * FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_SHEETS.
   */
  @SerializedName("google_sheets")
  public String googleSheets = null;

  /*
   * The google_api_key field is used to authenticate requests to the Google
   * Sheets API.
   * This is required to read publicly accessible sheets.
   *
   * The value of this field is set to the environment variable
   * FREEROUTING__API_SERVER__KEYS_LOCATION__GOOGLE_API_KEY.
   *
   * To create a Google API key:
   * 1. Go to https://console.cloud.google.com/apis/credentials
   * 2. Create a new API key
   * 3. Restrict it to Google Sheets API only (recommended)
   */
  @SerializedName("google_api_key")
  public String googleApiKey = null;
}