package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class GoogleSheetsProviderSettings implements Serializable {

    @SerializedName("google_api_key")
    public String googleApiKey = null;

    @SerializedName("sheet_url")
    public String sheetUrl = null;
}
