package app.freerouting.surveys;

import app.freerouting.Freerouting;
import app.freerouting.analytics.AnalyticsErrorAggregator;
import app.freerouting.analytics.NetworkProxyConfig;
import app.freerouting.util.gson.GsonProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Headless HTTP transport for the micro-survey feature.
 *
 * <p>Deliberately decoupled from any Swing UI: every method returns a {@link CompletableFuture} so
 * the client can be triggered from a toolbar, a menu item, or headless code. All failures are
 * silent — they are routed to {@link AnalyticsErrorAggregator} (first failure logged, then hourly
 * summaries) and never surfaced as dialogs. Callers that show UI must never block on the returned
 * futures; the optimistic-UI rule (close after 400 ms regardless of the network outcome) is the
 * responsibility of the UI layer.
 */
public class SurveyClient {

  /** Default REST API base URL; overridable for tests and self-hosted deployments. */
  public static final String DEFAULT_API_BASE_URL = "https://api.freerouting.app/v1/";

  /** Environment variable for setting or mocking an active survey locally. */
  public static final String ACTIVE_SURVEY_ENV = "FREEROUTING__SURVEYS__ACTIVE_SURVEY";

  /** System property for setting or mocking an active survey locally. */
  public static final String ACTIVE_SURVEY_PROP = "freerouting.surveys.active_survey";

  /** Environment variable for overriding the API base URL. */
  public static final String API_URL_ENV = "FREEROUTING__SURVEYS__API_URL";

  /** System property for overriding the API base URL. */
  public static final String API_URL_PROP = "freerouting.surveys.api_url";

  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 10000;

  private final String baseUrl;
  private final ExecutorService executor;

  /** Creates a client that talks to the public Freerouting API (or local override if set). */
  public SurveyClient() {
    this(resolveBaseUrl());
  }

  /** Creates a client with a custom API base URL (trailing slash normalized). */
  public SurveyClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.executor =
        Executors.newFixedThreadPool(
            1,
            r -> {
              Thread t = new Thread(r, "survey-client");
              t.setDaemon(true);
              return t;
            });
  }

  /** Resolves the base URL, checking system properties and environment variables first. */
  static String resolveBaseUrl() {
    String propUrl = System.getProperty(API_URL_PROP);
    if (propUrl != null && !propUrl.isBlank()) {
      return propUrl;
    }
    String envUrl = System.getenv(API_URL_ENV);
    if (envUrl != null && !envUrl.isBlank()) {
      return envUrl;
    }
    return DEFAULT_API_BASE_URL;
  }

  /** Resolves local active survey JSON from system property or environment variable. */
  static String resolveLocalActiveSurveyJson() {
    String prop = System.getProperty(ACTIVE_SURVEY_PROP);
    if (prop != null && !prop.isBlank()) {
      return prop;
    }
    String env = System.getenv(ACTIVE_SURVEY_ENV);
    if (env != null && !env.isBlank()) {
      return env;
    }
    return null;
  }

  /**
   * Fetches the currently active survey. Completes with {@code null} when the server has no active
   * survey (HTTP 204) or on any failure — offline is indistinguishable from "nothing to ask" by
   * design.
   */
  public CompletableFuture<SurveyDefinition> fetchActiveSurvey() {
    return CompletableFuture.supplyAsync(this::fetchActiveSurveyBlocking, executor);
  }

  /**
   * Blocking variant of {@link #fetchActiveSurvey()} for callers that already run on a background
   * thread. Never throws.
   */
  public SurveyDefinition fetchActiveSurveyBlocking() {
    String localJson = resolveLocalActiveSurveyJson();
    if (localJson != null && !localJson.isBlank()) {
      try {
        return GsonProvider.GSON.fromJson(localJson, SurveyDefinition.class);
      } catch (Exception e) {
        AnalyticsErrorAggregator.recordFailure(
            "local-survey-json",
            new IOException("Failed to parse " + ACTIVE_SURVEY_ENV + ": " + e.getMessage()));
        return null;
      }
    }

    String endpoint = baseUrl + "surveys/active";
    HttpURLConnection connection = null;
    try {
      connection = openConnection(endpoint);
      connection.setRequestMethod("GET");

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
        return null;
      }
      if (responseCode != HttpURLConnection.HTTP_OK) {
        AnalyticsErrorAggregator.recordFailure(
            endpoint,
            new IOException(
                "Server returned HTTP response code: " + responseCode + " for URL: " + endpoint),
            readErrorBody(connection));
        return null;
      }
      String body = readBody(connection);
      return GsonProvider.GSON.fromJson(body, SurveyDefinition.class);
    } catch (Exception e) {
      AnalyticsErrorAggregator.recordFailure(endpoint, e);
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Submits a survey response. The returned future completes with {@code true} on HTTP 2xx, {@code
   * false} on any network failure. HTTP 409 Conflict (server-side duplicate) also completes with
   * {@code true} — the answer is already recorded, so callers treat it as delivered.
   */
  public CompletableFuture<Boolean> submitResponse(SurveyResponsePayload payload) {
    return CompletableFuture.supplyAsync(() -> submitResponseBlocking(payload), executor);
  }

  /**
   * Blocking variant of {@link #submitResponse(SurveyResponsePayload)} for callers that already run
   * on a background thread. Never throws.
   */
  public boolean submitResponseBlocking(SurveyResponsePayload payload) {
    if (resolveLocalActiveSurveyJson() != null) {
      // In local override mode, simulate immediate successful recording without network call.
      return true;
    }

    String endpoint = baseUrl + "surveys/" + payload.surveyId + "/response";
    HttpURLConnection connection = null;
    try {
      connection = openConnection(endpoint);
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = GsonProvider.GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }
      int responseCode = connection.getResponseCode();
      if (responseCode >= 400) {
        AnalyticsErrorAggregator.recordFailure(
            endpoint,
            new IOException(
                "Server returned HTTP response code: " + responseCode + " for URL: " + endpoint),
            readErrorBody(connection));
      }
      // 409 (duplicate) counts as delivered — the answer is already recorded server-side.
      return responseCode == HttpURLConnection.HTTP_CONFLICT || responseCode < 400;
    } catch (Exception e) {
      AnalyticsErrorAggregator.recordFailure(endpoint, e);
      return false;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /** Closes the background executor. The client must not be used afterwards. */
  public void shutdown() {
    executor.shutdown();
  }

  private HttpURLConnection openConnection(String endpoint) throws IOException {
    try {
      URI uri = URI.create(endpoint);
      HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      if (connection instanceof HttpsURLConnection httpsConn) {
        SSLSocketFactory sslSocketFactory =
            NetworkProxyConfig.getCompositeSslSocketFactory(
                Freerouting.globalSettings != null
                    ? Freerouting.globalSettings.networkSettings
                    : null);
        if (sslSocketFactory != null) {
          httpsConn.setSSLSocketFactory(sslSocketFactory);
        }
      }
      connection.setRequestProperty("Content-Type", "application/json");
      return connection;
    } catch (Exception e) {
      throw new IOException("Invalid survey endpoint: " + endpoint, e);
    }
  }

  private static String readBody(HttpURLConnection connection) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line.trim());
      }
      return sb.toString();
    }
  }

  private static String readErrorBody(HttpURLConnection connection) {
    if (connection.getErrorStream() == null) {
      return "";
    }
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line.trim());
      }
      return sb.toString();
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * Registers {@code callback} on {@code future} without ever letting an exception escape into the
   * completing thread. Utility for UI glue that must stay silent on failures.
   */
  public static <T> void whenCompleteSilently(CompletableFuture<T> future, Consumer<T> callback) {
    future.whenComplete(
        (value, error) -> {
          if (error != null) {
            return;
          }
          try {
            callback.accept(value);
          } catch (Exception ignored) {
            // Never propagate into the completing thread — silent by design.
          }
        });
  }
}
