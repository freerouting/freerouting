package app.freerouting;

import app.freerouting.logger.FRLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProtocolRegistrar {

  public static void registerIfNeeded() {
    String exePath = getExecutablePath();
    if (exePath == null) {
      return;
    }

    // Only register when running as a jpackage-produced application
    String exeName = Path.of(exePath).getFileName().toString().toLowerCase();
    if (exeName.startsWith("java")) {
      return;
    }

    String os = System.getProperty("os.name", "").toLowerCase();
    try {
      if (os.contains("win")) {
        registerWindows(exePath);
      } else if (os.contains("linux")) {
        registerLinux(exePath);
      }
      // macOS: URL scheme is registered via Info.plist at build time
    } catch (Exception e) {
      FRLogger.warn("Failed to register freerouting:// protocol handler: " + e.getMessage());
    }
  }

  private static String getExecutablePath() {
    try {
      return ProcessHandle.current().info().command().orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static void registerWindows(String exePath) throws IOException, InterruptedException {
    // Use a .reg file to avoid ProcessBuilder quoting issues with nested quotes
    String escapedPath = exePath.replace("\\", "\\\\");
    String regContent = "Windows Registry Editor Version 5.00\r\n\r\n"
        + "[HKEY_CURRENT_USER\\Software\\Classes\\freerouting]\r\n"
        + "@=\"URL:Freerouting Protocol\"\r\n"
        + "\"URL Protocol\"=\"\"\r\n\r\n"
        + "[HKEY_CURRENT_USER\\Software\\Classes\\freerouting\\shell\\open\\command]\r\n"
        + "@=\"\\\"" + escapedPath + "\\\" \\\"%1\\\"\"\r\n";

    Path regFile = Files.createTempFile("freerouting-protocol", ".reg");
    try {
      Files.writeString(regFile, regContent);
      exec("reg", "import", regFile.toString());
      FRLogger.info("Registered freerouting:// protocol handler in Windows registry");
    } finally {
      Files.deleteIfExists(regFile);
    }
  }

  private static void registerLinux(String exePath) throws IOException {
    Path applicationsDir = Path.of(System.getProperty("user.home"), ".local", "share", "applications");
    Files.createDirectories(applicationsDir);

    Path desktopFile = applicationsDir.resolve("freerouting-url-handler.desktop");
    String content = "[Desktop Entry]\n"
        + "Name=Freerouting\n"
        + "Exec=" + exePath + " %u\n"
        + "Type=Application\n"
        + "NoDisplay=true\n"
        + "MimeType=x-scheme-handler/freerouting;\n";

    Files.writeString(desktopFile, content);

    try {
      exec("xdg-mime", "default", "freerouting-url-handler.desktop", "x-scheme-handler/freerouting");
    } catch (Exception e) {
      FRLogger.warn("xdg-mime command failed, .desktop file was written but may not be active: " + e.getMessage());
    }

    FRLogger.info("Registered freerouting:// protocol handler for Linux");
  }

  private static void exec(String... command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      String output = new String(process.getInputStream().readAllBytes());
      throw new IOException("Command failed (exit " + exitCode + "): " + String.join(" ", command) + " — " + output);
    }
  }
}
