package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.dto.Context;
import app.freerouting.management.analytics.dto.Library;
import app.freerouting.management.analytics.dto.Payload;
import app.freerouting.management.analytics.dto.Properties;
import app.freerouting.management.analytics.dto.Traits;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A client for Google BigQuery's API.
 *
 * <p>Please note that {@code identify}, {@code track}, and {@code users} tables are NOT updated in
 * BigQuery (unlike Segment).
 *
 * <h2>Singleton lifecycle</h2>
 * Creating a BigQuery service involves network I/O (credential refresh against Google's token
 * endpoint) and is expensive enough to avoid doing on every analytics call. Use
 * {@link #getInstance(String, String)} to obtain the shared instance. The singleton is recreated
 * transparently if the service-account key changes (e.g. key rotation), so callers do not need
 * to manage lifecycle themselves.
 */
public class BigQueryClient implements AnalyticsClient {

  private static final String BIGQUERY_PROJECT_ID = "freerouting-analytics";
  private static final String BIGQUERY_DATASET_ID = "freerouting_application";

  // -------------------------------------------------------------------------
  // Singleton state — guarded by the class monitor
  // -------------------------------------------------------------------------

  /** The single shared instance, replaced only when the service-account key changes. */
  private static volatile BigQueryClient singletonInstance;

  /**
   * The service-account key string that was used to build {@link #singletonInstance}. Compared
   * with the key passed to {@link #getInstance} to detect key rotation.
   */
  private static volatile String singletonKey;

  // -------------------------------------------------------------------------
  // Instance state
  // -------------------------------------------------------------------------

  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  /** The authenticated BigQuery service. Owned exclusively by this instance. */
  private final BigQuery bigQuery;
  private boolean enabled = true;

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  /**
   * Returns the shared {@link BigQueryClient} for the given service-account key, creating (or
   * recreating) it if necessary.
   *
   * <p>This method is thread-safe. The underlying GCP credential refresh and
   * {@link BigQuery} construction happen at most once per distinct key value, not on every
   * analytics event.
   *
   * @param libraryVersion   the Freerouting version string embedded in every event payload
   * @param serviceAccountKey the full JSON content of the GCP service-account key file
   * @return the shared instance, never {@code null}
   * @throws RuntimeException if the GCP client cannot be initialised (propagated from
   *                          {@link #createBigQueryService(byte[])})
   */
  public static BigQueryClient getInstance(String libraryVersion, String serviceAccountKey) {
    // Fast path — no synchronisation needed if the singleton is already warm and the key
    // hasn't changed.
    if (singletonInstance != null && serviceAccountKey.equals(singletonKey)) {
      return singletonInstance;
    }

    synchronized (BigQueryClient.class) {
      // Re-check inside the lock in case another thread just initialised the singleton.
      if (singletonInstance == null || !serviceAccountKey.equals(singletonKey)) {
        singletonInstance = new BigQueryClient(libraryVersion, serviceAccountKey);
        singletonKey = serviceAccountKey;
        FRLogger.debug("BigQueryClient: created new singleton instance (library version: " + libraryVersion + ")");
      }
    }

    return singletonInstance;
  }

  // -------------------------------------------------------------------------
  // Constructor (package-visible for tests; prefer getInstance() in production)
  // -------------------------------------------------------------------------

  public BigQueryClient(String libraryVersion, String serviceAccountKey) {
    LIBRARY_VERSION = libraryVersion;
    // Enable TLS protocols
    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    bigQuery = createBigQueryService(serviceAccountKey.getBytes());
  }

  // -------------------------------------------------------------------------
  // GCP helpers
  // -------------------------------------------------------------------------

  private static BigQuery createBigQueryService(byte[] serviceAccountKeyBytes) {
    try {
      InputStream keyStream = new ByteArrayInputStream(serviceAccountKeyBytes);
      GoogleCredentials credentials = ServiceAccountCredentials
          .fromStream(keyStream)
          .createScoped("https://www.googleapis.com/auth/bigquery");
      credentials.refreshIfExpired();
      return BigQueryOptions.newBuilder()
          .setCredentials(credentials)
          .build()
          .getService();
    } catch (IOException e) {
      throw new RuntimeException("Failed to create BigQuery client", e);
    }
  }

  // -------------------------------------------------------------------------
  // AnalyticsClient implementation
  // -------------------------------------------------------------------------

  @Override
  public void identify(String userId, String anonymousId, Traits traits) throws IOException {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.traits = traits;

    // NOTE: we ignore the identify event in BigQuery (because we have the tracked
    // "application started" event instead).
  }

  @Override
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

    sendPayloadAsync(payload);
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  // -------------------------------------------------------------------------
  // Async send
  // -------------------------------------------------------------------------

  private void sendPayloadAsync(Payload payload) {
    if (!enabled) {
      return;
    }

    // Snapshot the fields on the calling thread so the background thread doesn't race
    // on mutable payload state.
    Map<String, String> fields = generateFieldsFromPayload(payload);

    new Thread(() -> {
      try {
        // Table name is the event name with some formatting.
        String tableName = payload.event
            .toLowerCase()
            .replace(" ", "_")
            .replace("-", "_");

        // Apply a text transformation to the event and event_text fields.
        fields.put("event_text", fields.get("event"));
        fields.remove("event");
        fields.put("event", tableName);

        TableId tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, tableName);
        InsertAllRequest request = InsertAllRequest.newBuilder(tableId)
            .addRow(InsertAllRequest.RowToInsert.of(fields))
            .build();

        InsertAllResponse response = bigQuery.insertAll(request);
        if (response.hasErrors()) {
          response.getInsertErrors().forEach((_, errors) ->
              FRLogger.error("Error in BigQueryClient.sendPayloadAsync: (" + tableName + ") " + errors, null));
        }
      } catch (Exception e) {
        FRLogger.error("Exception in BigQueryClient.sendPayloadAsync: " + e.getMessage(), e);
      }
    }).start();
  }

  private Map<String, String> generateFieldsFromPayload(Payload payload) {
    Map<String, String> fields = new HashMap<>();

    fields.put("id", "frg-2o0" + TextManager.generateRandomAlphanumericString(25));
    var eventHappenedAt = Instant.now();
    fields.put("received_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("sent_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss") + " UTC");
    fields.put("original_timestamp", "<nil>");
    fields.put("timestamp", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    fields.put("user_id", payload.userId);
    fields.put("anonymous_id", payload.anonymousId);
    fields.put("event", payload.event);
    fields.put("context_library_name", payload.context.library.name);
    fields.put("context_library_version", payload.context.library.version);

    var payloadUploadedAt = Instant.now();
    fields.put("loaded_at", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("uuid_ts", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    if (payload.traits != null && !payload.traits.isEmpty()) {
      fields.putAll(payload.traits);
    }
    if (payload.properties != null && !payload.properties.isEmpty()) {
      fields.putAll(payload.properties);
    }

    return fields;
  }
}