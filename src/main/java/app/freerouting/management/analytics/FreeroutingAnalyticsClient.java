package app.freerouting.management.analytics;

import app.freerouting.management.analytics.dto.Context;
import app.freerouting.management.analytics.dto.Library;
import app.freerouting.management.analytics.dto.Payload;
import app.freerouting.management.analytics.dto.Properties;
import app.freerouting.management.analytics.dto.Traits;
import app.freerouting.management.gson.GsonProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A client for Segment's HTTP API.
 */
public class FreeroutingAnalyticsClient implements AnalyticsClient {

  private static final String FREEROUTING_ANALYTICS_ENDPOINT = "https://api.freerouting.app/v1/";
  //private static final String FREEROUTING_ANALYTICS_ENDPOINT = "http://localhost:37864/v1/";
  private final String WRITE_KEY;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;

  public FreeroutingAnalyticsClient(String libraryVersion, String key) {
    LIBRARY_VERSION = libraryVersion;
    WRITE_KEY = key;
  }

  private void sendPayloadAsync(String endpoint, Payload payload) throws IOException {
    if (!enabled) {
      return;
    }

    new Thread(() ->
    {
      HttpURLConnection connection = null;

      try {
        // Serialize to JSON using GSON
        String jsonPayload = GsonProvider.GSON.toJson(payload);
        var uri = new URI(endpoint);

        // Create and configure HTTP connection
        connection = (HttpURLConnection) uri
            .toURL()
            .openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Host", uri.getHost());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Basic " + Base64
            .getEncoder()
            .encodeToString((WRITE_KEY + ":").getBytes()));
        connection.setDoOutput(true);

        // Write JSON payload to request
        try (OutputStream os = connection.getOutputStream()) {
          byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
          os.write(input, 0, input.length);
        }

        // Check the HTTP response code *before* touching getInputStream() so that we can
        // read the error-stream body on HTTP 4xx/5xx — getInputStream() would throw and
        // swallow that body.
        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
          // Read the server's error body for diagnostic context and forward it to the
          // aggregator so it can surface it in the hourly summary log.
          String errorBody = readErrorBody(connection);
          AnalyticsErrorAggregator.recordFailure(
              endpoint,
              new IOException("Server returned HTTP response code: " + responseCode + " for URL: " + endpoint),
              errorBody);
        } else {
          // Consume the success body (currently unused but keeps the connection clean).
          try (BufferedReader br = new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            while (br.readLine() != null) { /* discard */ }
          }
        }
      } catch (Exception e) {
        // Do not log here directly — connection may be null if the exception was thrown
        // before openConnection() succeeded, and a per-failure log line would flood the
        // output when the analytics endpoint is down.  Delegate to the aggregator, which
        // logs the first failure immediately and then emits a single hourly summary.
        AnalyticsErrorAggregator.recordFailure(endpoint, e);
      }
    }).start();
  }

  /**
   * Safely reads the body from {@link HttpURLConnection#getErrorStream()}.
   * Returns an empty string if the error stream is null or unreadable.
   */
  private static String readErrorBody(HttpURLConnection connection) {
    if (connection.getErrorStream() == null) {
      return "";
    }
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line.trim());
      }
      return sb.toString();
    } catch (Exception ignored) {
      return "";
    }
  }

  public void identify(String userId, String anonymousId, Traits traits) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.traits = traits;

    sendPayloadAsync(FREEROUTING_ANALYTICS_ENDPOINT + "analytics/identify", payload);
  }

  public void track(String userId, String anonymousId, String event, Properties properties) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.event = event;
    payload.properties = properties;

    sendPayloadAsync(FREEROUTING_ANALYTICS_ENDPOINT + "analytics/track", payload);
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}