package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.dto.*;
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
public class FreeroutingAnalyticsClient implements AnalyticsClient
{
  private static final String FREEROUTING_ANALYTICS_ENDPOINT = "https://api.freerouting.app/v1/";
  //private static final String FREEROUTING_ANALYTICS_ENDPOINT = "http://localhost:37864/v1/";
  private final String WRITE_KEY;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;

  public FreeroutingAnalyticsClient(String libraryVersion, String key)
  {
    LIBRARY_VERSION = libraryVersion;
    WRITE_KEY = key;
  }

  private void sendPayloadAsync(String endpoint, Payload payload) throws IOException
  {
    if (!enabled)
    {
      return;
    }

    new Thread(() ->
    {
      HttpURLConnection connection = null;

      try
      {
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
        try (OutputStream os = connection.getOutputStream())
        {
          byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
          os.write(input, 0, input.length);
        }

        // Read the response
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)))
        {
          StringBuilder response = new StringBuilder();
          String responseLine;
          while ((responseLine = br.readLine()) != null)
          {
            response.append(responseLine.trim());
          }
          // return response.toString();
        }
      } catch (Exception e)
      {
        FRLogger.debug("Exception in FreeroutingAnalyticsClient.sendPayloadAsync: " + connection.getRequestMethod() + " " + connection
            .getURL()
            .toString() + " - " + e.getMessage(), null);
      }
    }).start();
  }

  public void identify(String userId, String anonymousId, Traits traits) throws IOException
  {
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

  public void track(String userId, String anonymousId, String event, Properties properties) throws IOException
  {
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

  public void setEnabled(boolean enabled)
  {
    this.enabled = enabled;
  }
}
