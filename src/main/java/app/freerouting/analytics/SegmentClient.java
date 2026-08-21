package app.freerouting.analytics;

import app.freerouting.analytics.dto.Context;
import app.freerouting.analytics.dto.Library;
import app.freerouting.analytics.dto.Payload;
import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import app.freerouting.util.gson.GsonProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Sends analytics payloads to Segment's HTTP API. */
public class SegmentClient implements AnalyticsClient {

  private static final String SEGMENT_ENDPOINT = "https://api.segment.io/v1/";
  private final String writeKey;
  private final String libraryName = "freerouting";
  private final String libraryVersion;
  private boolean enabled = true;

  /**
   * Creates a Segment analytics client.
   *
   * @param libraryVersion the Freerouting version included in each payload
   * @param writeKey the Segment write key
   */
  public SegmentClient(String libraryVersion, String writeKey) {
    this.libraryVersion = libraryVersion;
    this.writeKey = writeKey;
  }

  private void sendPayloadAsync(String endpoint, Payload payload) throws IOException {
    if (!enabled) {
      return;
    }

    Thread senderThread =
        new Thread(
            () -> {
              try {
                // Create and configure HTTP connection
                URL url = new URI(endpoint).toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; utf-8");
                connection.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString((writeKey + ":").getBytes()));
                connection.setDoOutput(true);

                // Write JSON payload to request
                String jsonPayload = GsonProvider.GSON.toJson(payload);
                try (OutputStream os = connection.getOutputStream()) {
                  byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                  os.write(input, 0, input.length);
                }

                // Read the response
                try (BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8))) {
                  StringBuilder response = new StringBuilder();
                  String responseLine;
                  while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                  }
                  // return response.toString();
                }
              } catch (Exception _) {
                // FRLogger.error("Exception in SegmentClient.send_payload_async: " +
                // e.getMessage(), e);
              }
            },
            "analytics-segment-sender");
    senderThread.setDaemon(true);
    senderThread.start();
  }

  @Override
  public void identify(String userId, String anonymousId, Traits traits) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = libraryName;
    payload.context.library.version = libraryVersion;
    payload.traits = traits;

    sendPayloadAsync(SEGMENT_ENDPOINT + "identify", payload);
  }

  @Override
  public void track(String userId, String anonymousId, String event, Properties properties)
      throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = libraryName;
    payload.context.library.version = libraryVersion;
    payload.event = event;
    payload.properties = properties;

    sendPayloadAsync(SEGMENT_ENDPOINT + "track", payload);
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
