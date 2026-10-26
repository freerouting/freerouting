package app.freerouting.gui.windows.board;

import app.freerouting.logger.FRLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for loading and caching the curated list of Freerouting donors and sponsors.
 *
 * <p>Retrieves the remote single-source-of-truth JSON file asynchronously with graceful timeout and
 * offline fallback to bundled resources.
 */
public class DonorService {

  public static final String DEFAULT_REMOTE_URL =
      "https://raw.githubusercontent.com/freerouting/freerouting/master/website/donors.json";
  private static final String BUNDLED_DONORS_RESOURCE =
      "/app/freerouting/gui/windows/board/donors.json";

  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(Donor.class, new DonorDeserializer()).create();

  private static volatile DonorPayload cachedPayload;

  private final String remoteUrl;
  private final HttpClient httpClient;

  /** Constructs a DonorService with default remote endpoint and standard HttpClient. */
  public DonorService() {
    this(DEFAULT_REMOTE_URL, createDefaultHttpClient());
  }

  /**
   * Constructs a DonorService with explicit remote endpoint and HttpClient.
   *
   * @param remoteUrl the endpoint URL to fetch donors from
   * @param httpClient the HTTP client for executing requests
   */
  public DonorService(String remoteUrl, HttpClient httpClient) {
    this.remoteUrl = remoteUrl != null ? remoteUrl : DEFAULT_REMOTE_URL;
    this.httpClient = httpClient;
  }

  private static HttpClient createDefaultHttpClient() {
    try {
      return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    } catch (Throwable t) {
      FRLogger.warn("Could not initialize HttpClient for DonorService: " + t.getMessage());
      return null;
    }
  }

  /**
   * Asynchronously fetches the latest donors payload. Returns the cached or bundled payload if the
   * network is unavailable or times out.
   *
   * @return a CompletableFuture resolving to the DonorPayload
   */
  public CompletableFuture<DonorPayload> fetchDonorsAsync() {
    if (cachedPayload != null) {
      return CompletableFuture.completedFuture(cachedPayload);
    }

    if (httpClient == null) {
      DonorPayload fallback = getBundledFallback();
      cachedPayload = fallback;
      return CompletableFuture.completedFuture(fallback);
    }

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(remoteUrl))
              .header("User-Agent", "Freerouting-Client")
              .timeout(Duration.ofSeconds(3))
              .build();

      return httpClient
          .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
          .thenApply(HttpResponse::body)
          .thenApply(this::parseJson)
          .thenApply(
              payload -> {
                if (payload != null && payload.tiers() != null && !payload.tiers().isEmpty()) {
                  cachedPayload = payload;
                  return payload;
                }
                return getBundledFallback();
              })
          .exceptionally(
              ex -> {
                FRLogger.debug("Remote donors fetch fallback: " + ex.getMessage());
                return getBundledFallback();
              });
    } catch (Exception ex) {
      FRLogger.debug("Failed to initiate donors fetch: " + ex.getMessage());
      return CompletableFuture.completedFuture(getBundledFallback());
    }
  }

  /**
   * Parses a raw JSON string into a {@link DonorPayload}.
   *
   * @param jsonString raw JSON string
   * @return parsed payload, or bundled fallback if parsing fails
   */
  public DonorPayload parseJson(String jsonString) {
    if (jsonString == null || jsonString.isBlank()) {
      return getBundledFallback();
    }
    try {
      DonorPayload payload = GSON.fromJson(jsonString, DonorPayload.class);
      return payload != null ? payload : getBundledFallback();
    } catch (Exception ex) {
      FRLogger.warn("Failed to parse donors payload: " + ex.getMessage());
      return getBundledFallback();
    }
  }

  /**
   * Loads the bundled offline copy of {@code donors.json} included inside the application JAR.
   *
   * @return offline fallback DonorPayload, never null
   */
  public DonorPayload getBundledFallback() {
    try (InputStream is = getClass().getResourceAsStream(BUNDLED_DONORS_RESOURCE)) {
      if (is != null) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
          DonorPayload payload = GSON.fromJson(reader, DonorPayload.class);
          if (payload != null) {
            return payload;
          }
        }
      }
    } catch (Exception ex) {
      FRLogger.warn("Could not read bundled donors.json fallback: " + ex.getMessage());
    }
    return new DonorPayload("unknown", Collections.emptyList());
  }

  /** Represents a single donor or corporate sponsor. */
  public record Donor(String name, String url) {}

  /** Represents a category tier of donors. */
  public record DonorTier(String name, List<Donor> donors) {}

  /** Represents the complete donors document. */
  public record DonorPayload(String updated, List<DonorTier> tiers) {}

  /** Flexible JSON deserializer supporting both primitive strings and structured donor objects. */
  private static class DonorDeserializer implements JsonDeserializer<Donor> {
    @Override
    public Donor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      if (json == null || json.isJsonNull()) {
        return new Donor("", null);
      }
      if (json.isJsonPrimitive()) {
        return new Donor(json.getAsString().trim(), null);
      }
      if (json.isJsonObject()) {
        JsonObject obj = json.getAsJsonObject();
        String name =
            obj.has("name") && !obj.get("name").isJsonNull()
                ? obj.get("name").getAsString().trim()
                : "";
        String url =
            obj.has("url") && !obj.get("url").isJsonNull()
                ? obj.get("url").getAsString().trim()
                : null;
        return new Donor(name, url);
      }
      return new Donor("", null);
    }
  }
}
