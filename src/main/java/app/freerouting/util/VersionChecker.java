package app.freerouting.util;

import app.freerouting.logger.FRLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * VersionChecker retrieves the latest release information from GitHub in the background
 * and logs a message if a newer version of the application is available.
 */
public class VersionChecker implements Runnable {

  private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/freerouting/freerouting/releases/latest";
  
  private final String currentVersion;
  private final HttpClient httpClient;

  /**
   * Constructs a new VersionChecker with the specified current version and the default HttpClient.
   *
   * @param version the current version of the application (e.g. "1.2.3" or "v1.2.3")
   */
  public VersionChecker(String version) {
    this(version, HttpClient.newHttpClient());
  }

  /**
   * Package-private constructor for dependency injection during testing.
   *
   * @param version the current version of the application (e.g. "1.2.3" or "v1.2.3")
   * @param httpClient the HttpClient to use for making requests
   */
  VersionChecker(String version, HttpClient httpClient) {
    if (version == null || version.isBlank()) {
      this.currentVersion = "v0.0.0";
    } else if (version.startsWith("v") || version.startsWith("V")) {
      this.currentVersion = "v" + version.substring(1);
    } else {
      this.currentVersion = "v" + version;
    }
    this.httpClient = httpClient;
  }

  /**
   * Gets the normalized current version (always starts with 'v').
   *
   * @return the normalized current version string
   */
  public String getCurrentVersion() {
    return currentVersion;
  }

  @Override
  public void run() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(GITHUB_RELEASES_URL))
          .header("User-Agent", "Freerouting-Version-Checker")
          .build();

      httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(HttpResponse::body)
          .thenAccept(this::processResponse)
          .exceptionally(e -> {
            FRLogger.warn("Failed to check for new version: " + e.getMessage());
            return null;
          });
    } catch (Exception e) {
      FRLogger.warn("Failed to initiate version check: " + e.getMessage());
    }
  }

  /**
   * Processes the JSON response body from the GitHub releases API.
   *
   * @param responseBody the raw response body string
   */
  void processResponse(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      FRLogger.warn("Received empty response body during version check.");
      return;
    }
    try {
      JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
      if (json.has("tag_name")) {
        String latestVersion = json.get("tag_name").getAsString();
        
        String cleanCurrent = currentVersion.substring(1); // currentVersion always starts with 'v'
        String cleanLatest = latestVersion.startsWith("v") || latestVersion.startsWith("V")
            ? latestVersion.substring(1)
            : latestVersion;

        if (!cleanCurrent.equalsIgnoreCase(cleanLatest)) {
          FRLogger.info("New version available: " + latestVersion);
        } else {
          FRLogger.debug("No new version available. Current version is up to date: " + currentVersion);
        }
      } else {
        FRLogger.warn("GitHub release response does not contain 'tag_name': " + responseBody);
      }
    } catch (Exception e) {
      FRLogger.warn("Failed to parse version check response: " + e.getMessage());
    }
  }
}
