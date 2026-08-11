package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Configuration for console and file logging. */
public class LoggingSettings implements Serializable {

  /** Console logging configuration. */
  @SerializedName("console")
  public final ConsoleLoggingSettings console = new ConsoleLoggingSettings();

  /** File logging configuration. */
  @SerializedName("file")
  public final FileLoggingSettings file = new FileLoggingSettings();

  /** Settings controlling console log output. */
  public static class ConsoleLoggingSettings implements Serializable {
    /** Whether console logging is enabled. */
    @SerializedName("enabled")
    public boolean enabled = true;

    /** Minimum console log level. */
    @SerializedName("level")
    public String level = "INFO";
  }

  /** Settings controlling file log output. */
  public static class FileLoggingSettings implements Serializable {
    /** Whether file logging is enabled. */
    @SerializedName("enabled")
    public boolean enabled = true;

    /** Minimum file log level. */
    @SerializedName("level")
    public String level = "INFO";

    /** Path where log files are written. */
    @SerializedName("location")
    public String location;
  }
}
