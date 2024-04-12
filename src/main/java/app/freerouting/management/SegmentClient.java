package app.freerouting.management;

import app.freerouting.management.segment.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A client for Segment's HTTP API.
 */
public class SegmentClient
{
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String SEGMENT_ENDPOINT = "https://api.segment.io/v1/";
  private final String WRITE_KEY;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;

  public SegmentClient(String libraryVersion, String writeKey)
  {
    LIBRARY_VERSION = libraryVersion;
    WRITE_KEY = writeKey;
  }

  private void sendPayloadAsync(String endpoint, Payload payload) throws IOException
  {
    if (!enabled)
    {
      return;
    }

    new Thread(() ->
    {
      try
      {
        // Serialize to JSON using GSON
        String jsonPayload = GSON.toJson(payload);

        // Create and configure HTTP connection
        URL url = new URI(endpoint).toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setRequestProperty("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((WRITE_KEY + ":").getBytes()));
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
        //FRLogger.error("Exception in SegmentClient.send_payload_async: " + e.getMessage(), e);
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

    sendPayloadAsync(SEGMENT_ENDPOINT + "identify", payload);
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

    sendPayloadAsync(SEGMENT_ENDPOINT + "track", payload);
  }

  public void setEnabled(boolean enabled)
  {
    this.enabled = enabled;
  }
}