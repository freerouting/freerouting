package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class LoggingSettings implements Serializable {

    @SerializedName("enabled")
    public boolean enabled = true;

    @SerializedName("level")
    public String level = "INFO";

    @SerializedName("location")
    public String location = "";
}
