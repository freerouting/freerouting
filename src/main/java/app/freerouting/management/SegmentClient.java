package app.freerouting.management;

import app.freerouting.management.segment.*;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.Gson;

public class SegmentClient {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String SEGMENT_ENDPOINT = "https://api.segment.io/v1/";
  private final String WRITE_KEY;

  public SegmentClient(String writeKey)
  {
    WRITE_KEY = writeKey;
  }

  private String send_payload(String endpoint, Payload payload) throws IOException
  {
    // Serialize to JSON using GSON
    String jsonPayload = GSON.toJson(payload);

    // Create and configure HTTP connection
    URL url = new URL(endpoint);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json; utf-8");
    connection.setRequestProperty("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((WRITE_KEY + ":").getBytes()));
    connection.setDoOutput(true);

    // Write JSON payload to request
    try(OutputStream os = connection.getOutputStream()) {
      byte[] input = jsonPayload.getBytes("utf-8");
      os.write(input, 0, input.length);
    }

    // Read and print the response
    try(BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
      StringBuilder response = new StringBuilder();
      String responseLine;
      while ((responseLine = br.readLine()) != null) {
        response.append(responseLine.trim());
      }
      return response.toString();
    }
  }

  public void identify(String userId, String anonymousId, Traits traits) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.traits = traits;

    send_payload(SEGMENT_ENDPOINT + "identify", payload);
  }

  public void track(String userId, String anonymousId, String event, Properties properties) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.event = event;
    payload.properties = properties;

    send_payload(SEGMENT_ENDPOINT + "track", payload);
  }
}

