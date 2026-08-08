package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.dto.Context;
import app.freerouting.management.analytics.dto.Library;
import app.freerouting.management.analytics.dto.Payload;
import app.freerouting.management.analytics.dto.Properties;
import app.freerouting.management.analytics.dto.Traits;
import app.freerouting.util.TextManager;
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
 * <p>Please note that {@code track} and {@code identify} events are written to their respective
 * BigQuery tables ({@code application_started}, etc. and {@code identify}).
 *
 * <h2>Singleton lifecycle</h2>
 *
 * Creating a BigQuery service involves network I/O (credential refresh against Google's token
 * endpoint) and is expensive enough to avoid doing on every analytics call. Use {@link
 * #getInstance(String, String)} to obtain the shared instance. The singleton is recreated
 * transparently if the service-account key changes (e.g. key rotation), so callers do not need to
 * manage lifecycle themselves.
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
   * The service-account key string that was used to build {@link #singletonInstance}. Compared with
   * the key passed to {@link #getInstance} to detect key rotation.
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
   * <p>This method is thread-safe. The underlying GCP credential refresh and {@link BigQuery}
   * construction happen at most once per distinct key value, not on every analytics event.
   *
   * @param libraryVersion the Freerouting version string embedded in every event payload
   * @param serviceAccountKey the full JSON content of the GCP service-account key file
   * @return the shared instance, never {@code null}
   * @throws RuntimeException if the GCP client cannot be initialised (propagated from {@link
   *     #createBigQueryService(byte[])})
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
        FRLogger.debug(
            "BigQueryClient: created new singleton instance (library version: "
                + libraryVersion
                + ")");
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
      GoogleCredentials credentials =
          ServiceAccountCredentials.fromStream(keyStream)
              .createScoped("https://www.googleapis.com/auth/bigquery");
      credentials.refreshIfExpired();
      return BigQueryOptions.newBuilder().setCredentials(credentials).build().getService();
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
    // Use "identifies" as the event name so sendPayloadAsync routes the row to the
    // BigQuery `identifies` table — this follows the Segment convention and matches
    // the existing table schema.
    payload.event = "identifies";
    payload.traits = traits;

    sendPayloadAsync(payload);
  }

  /**
   * Appends a snapshot row to the {@code user_snapshots} dimension table. Queries should take the
   * latest row per {@code anonymous_id} (or {@code user_id}) ordered by {@code received_at}.
   *
   * <p>Uses {@code user_snapshots} rather than {@code users} to avoid colliding with any legacy
   * {@code users} table that may exist in the dataset with an incompatible schema.
   */
  public void upsertUserSnapshot(String userId, String anonymousId, Traits traits)
      throws IOException {
    Traits snapshotTraits = new Traits();
    if (traits != null) {
      snapshotTraits.putAll(traits);
    }
    snapshotTraits.put("last_seen", Instant.now().toString());

    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.event = "user_snapshots";
    payload.traits = snapshotTraits;

    sendPayloadAsync(payload);
  }

  @Override
  public void track(String userId, String anonymousId, String event, Properties properties)
      throws IOException {
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

    new Thread(
            () -> {
              try {
                // Table name is the event name with some formatting.
                String tableName = payload.event.toLowerCase().replace(" ", "_").replace("-", "_");

                // Trait-only tables (identifies, user_snapshots) follow the Segment identifies
                // schema:
                // flattened traits + standard metadata, but no event / event_text columns.
                if (!isTraitOnlyTable(tableName)) {
                  fields.put("event_text", fields.get("event"));
                  fields.remove("event");
                  fields.put("event", tableName);
                } else {
                  fields.remove("event");
                }

                TableId tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, tableName);
                InsertAllRequest request =
                    InsertAllRequest.newBuilder(tableId)
                        .setIgnoreUnknownValues(true)
                        .addRow(InsertAllRequest.RowToInsert.of(fields))
                        .build();

                InsertAllResponse response = bigQuery.insertAll(request);
                if (response.hasErrors()) {
                  response
                      .getInsertErrors()
                      .forEach(
                          (_, errors) ->
                              FRLogger.error(
                                  "Error in BigQueryClient.sendPayloadAsync: ("
                                      + tableName
                                      + ") "
                                      + errors,
                                  null));
                }
              } catch (Exception e) {
                FRLogger.error(
                    "Exception in BigQueryClient.sendPayloadAsync: " + e.getMessage(), e);
              }
            })
        .start();
  }

  private Map<String, String> generateFieldsFromPayload(Payload payload) {
    Map<String, String> fields = new HashMap<>();

    fields.put("id", "frg-2o0" + TextManager.generateRandomAlphanumericString(25));
    var eventHappenedAt = Instant.now();
    fields.put(
        "received_at",
        TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put(
        "sent_at",
        TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss") + " UTC");
    fields.put("original_timestamp", "<nil>");
    fields.put(
        "timestamp",
        TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    fields.put("user_id", payload.userId);
    fields.put("anonymous_id", payload.anonymousId);
    fields.put("event", payload.event);
    fields.put("context_library_name", payload.context.library.name);
    fields.put("context_library_version", payload.context.library.version);

    var payloadUploadedAt = Instant.now();
    fields.put(
        "loaded_at",
        TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS")
            + " UTC");
    fields.put(
        "uuid_ts",
        TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS")
            + " UTC");

    if (payload.traits != null && !payload.traits.isEmpty()) {
      fields.putAll(payload.traits);
    }
    if (payload.properties != null && !payload.properties.isEmpty()) {
      fields.putAll(payload.properties);
    }

    return fields;
  }

  private static boolean isTraitOnlyTable(String tableName) {
    return "identifies".equals(tableName) || "user_snapshots".equals(tableName);
  }
}
