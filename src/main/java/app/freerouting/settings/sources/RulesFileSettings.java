package app.freerouting.settings.sources;

import app.freerouting.io.specctra.RulesReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.SettingsSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Extracts router settings from RULES files. Only the settings present in the RULES file will be
 * non-null.
 */
public class RulesFileSettings implements SettingsSource {

  private static final int PRIORITY = 40;
  private final RouterSettings settings;
  private final String fileName;

  /**
   * Creates a RulesFileSettings source from an input stream.
   *
   * @param in Input stream of the RULES file
   * @param fileName Name of the RULES file (for logging)
   */
  public RulesFileSettings(InputStream in, String fileName) {
    this.fileName = fileName;
    this.settings = loadSettings(in);
  }

  /**
   * Creates a RulesFileSettings source from a File.
   *
   * @param file the RULES file
   */
  public RulesFileSettings(File file) {
    this.fileName = file != null ? file.getName() : "unknown.rules";
    this.settings = file != null && file.exists() ? loadFromFile(file) : new RouterSettings();
  }

  /**
   * Creates a RulesFileSettings source from a Path.
   *
   * @param path Path to the RULES file
   */
  public RulesFileSettings(Path path) {
    this(path != null ? path.toFile() : null);
  }

  /**
   * Creates a RulesFileSettings source from a RULES file path/name.
   *
   * @param fileName Name or path of the RULES file (for logging)
   */
  public RulesFileSettings(String fileName) {
    this.fileName = fileName;
    if (fileName != null) {
      File f = new File(fileName);
      if (f.exists()) {
        this.settings = loadFromFile(f);
      } else {
        this.settings = new RouterSettings();
      }
    } else {
      this.settings = new RouterSettings();
    }
  }

  private RouterSettings loadFromFile(File file) {
    try (InputStream in = new FileInputStream(file)) {
      return loadSettings(in);
    } catch (Exception e) {
      FRLogger.warn(
          "Failed to load settings from RULES file: " + file.getName() + ": " + e.getMessage());
      return new RouterSettings();
    }
  }

  private RouterSettings loadSettings(InputStream in) {
    try {
      RouterSettings extracted = RulesReader.readRouterSettings(in);
      if (extracted != null) {
        FRLogger.debug("Loaded router settings from RULES file: " + fileName);
        return extracted;
      }
      return new RouterSettings();
    } catch (Exception e) {
      FRLogger.warn("Failed to load settings from RULES file: " + fileName + ": " + e.getMessage());
      return new RouterSettings();
    }
  }

  @Override
  public RouterSettings getSettings() {
    return settings;
  }

  @Override
  public String getSourceName() {
    return "RULES file: " + fileName;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
