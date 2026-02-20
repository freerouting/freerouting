package app.freerouting.management;
import app.freerouting.logger.FRLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;

public class VersionChecker implements Runnable {

  private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/freerouting/freerouting/releases/latest";
  private static String CURRENT_VERSION = "v1.0";  // replace with your current version

  public VersionChecker(String version) {
    CURRENT_VERSION = version;
  }

  @Override
  public void run() {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(GITHUB_RELEASES_URL))
        .build();

    client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body)
        .thenAccept(this::processResponse)
        .exceptionally(e -> {
          e.printStackTrace();
          return null;
        });
  }

  private void processResponse(String responseBody) {
    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
    String latestVersion = json.get("tag_name").getAsString();

    if (!CURRENT_VERSION.equals(latestVersion)) {
      FRLogger.info("New version available: " + latestVersion);
    } else {
      FRLogger.debug("No new version available.");
    }
  }
}
