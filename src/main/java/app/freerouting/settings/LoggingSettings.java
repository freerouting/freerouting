package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class LoggingSettings implements Serializable {

    @SerializedName("console")
    public final ConsoleLoggingSettings console = new ConsoleLoggingSettings();

    @SerializedName("file")
    public final FileLoggingSettings file = new FileLoggingSettings();

    public static class ConsoleLoggingSettings implements Serializable {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("level")
        public String level = "INFO";
    }

    public static class FileLoggingSettings implements Serializable {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("level")
        public String level = "INFO";

        @SerializedName("location")
        public String location = "";
    }
}
