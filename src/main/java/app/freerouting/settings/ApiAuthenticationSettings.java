package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ApiAuthenticationSettings implements Serializable {

    @SerializedName("enabled")
    public Boolean isEnabled = true;

    @SerializedName("providers")
    public String providers = "";

    @SerializedName("google_sheets")
    public GoogleSheetsProviderSettings googleSheets = new GoogleSheetsProviderSettings();
}
