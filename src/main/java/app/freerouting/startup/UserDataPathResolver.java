package app.freerouting.startup;

import app.freerouting.settings.AppPaths;
import app.freerouting.settings.GlobalSettings;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Resolves the user-data path according to OS defaults, environment variables, and command-line
 * arguments, checks permissions, and locks the path in GlobalSettings.
 */
public final class UserDataPathResolver {

  /** Container for the resolved user data path and its source description. */
  public record ResolvedUserDataPath(Path path, String source) {}

  private UserDataPathResolver() {}

  /**
   * Resolves, verifies, and locks the user data path in {@link GlobalSettings}.
   *
   * @param args command line arguments
   * @return the resolved path and description
   */
  public static ResolvedUserDataPath resolveAndLock(String[] args) {
    Path userdataPath = AppPaths.getDefaultUserDataPath();
    String userdataPathSource = "default (OS standard user-data path)";

    if (System.getenv("FREEROUTING__USER_DATA_PATH") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__USER_DATA_PATH"));
      userdataPathSource = "environment variable FREEROUTING__USER_DATA_PATH";
    } else if (System.getenv("FREEROUTING__LOGGING__FILE__LOCATION") != null) {
      userdataPath = Path.of(System.getenv("FREEROUTING__LOGGING__FILE__LOCATION"));
      userdataPathSource =
          "environment variable FREEROUTING__LOGGING__FILE__LOCATION (deprecated fallback)";
    }

    if (args != null && args.length > 0) {
      var userDataPathArg =
          Arrays.stream(args).filter(s -> s.startsWith("--user_data_path=")).findFirst();
      if (userDataPathArg.isPresent()) {
        userdataPath = Path.of(userDataPathArg.get().substring("--user_data_path=".length()));
        userdataPathSource = "CLI argument --user_data_path";
      }
    }

    File dir = userdataPath.toFile();
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        System.err.println(
            "WARNING: Could not create user-data directory '"
                + userdataPath
                + "' (source: "
                + userdataPathSource
                + "). "
                + "Freerouting will attempt to create it when writing files. "
                + "If this persists, check permissions for the specified path.");
      }
    } else {
      if (!dir.canRead()) {
        System.err.println(
            "WARNING: User-data directory '"
                + userdataPath
                + "' (source: "
                + userdataPathSource
                + ") exists but is NOT READABLE. "
                + "freerouting.json cannot be loaded. "
                + "Check that the process has read permission on the directory. "
                + "In Docker deployments, verify the volume mount and file ownership.");
      }
      if (!dir.canWrite()) {
        System.err.println(
            "WARNING: User-data directory '"
                + userdataPath
                + "' (source: "
                + userdataPathSource
                + ") exists but is NOT WRITABLE. "
                + "freerouting.json cannot be saved and settings won't be persisted. "
                + "Check that the process has write permission on the directory. "
                + "In Docker deployments, verify the volume mount and file ownership.");
      }
    }

    GlobalSettings.setUserDataPath(userdataPath);
    GlobalSettings.lockUserDataPath();

    return new ResolvedUserDataPath(userdataPath, userdataPathSource);
  }
}
