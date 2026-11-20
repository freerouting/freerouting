package app.freerouting.analytics;

import app.freerouting.analytics.dto.Context;
import app.freerouting.analytics.dto.Library;
import app.freerouting.analytics.dto.Payload;
import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.http.HttpTransportOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides analytics delivery through Google BigQuery's API.
 *
 * <p>Please note that {@code track} and {@code identify} events are written to their respective
 * BigQuery tables ({@code application_started}, etc. and {@code identify}).
 *
 * <h2>Singleton lifecycle</h2>
 *
 * <p>Creating a BigQuery service involves network I/O (credential refresh against Google's token
 * endpoint) and is expensive enough to avoid doing on every analytics call. Use {@link
 * #getInstance(String, String)} to obtain the shared instance. The singleton is recreated
 * transparently if the service-account key changes (e.g. key rotation), so callers do not need to
 * manage lifecycle themselves.
 */
public class BigQueryClient implements AnalyticsClient {

  private static final String BIGQUERY_PROJECT_ID = "freerouting-analytics";
  private static final String BIGQUERY_DATASET_ID = "freerouting_application";

  /** Maximum number of unwritten rows buffered across all tables before dropping. */
  static final int MAX_PENDING_ROWS = 5_000;

  /** Maximum number of distinct table queues allowed in memory. */
  static final int MAX_DISTINCT_TABLES = 100;

  /** Maximum number of rows sent in a single insertAll HTTP request. */
  static final int MAX_BATCH_SIZE = 100;

  /** Interval in milliseconds between scheduled batch flushes. */
  static final long FLUSH_INTERVAL_MS = 1_000L;

  /** Duration in milliseconds that the circuit breaker stays open after a 429 quota error. */
  static final long CIRCUIT_BREAKER_DURATION_MS = Duration.ofMinutes(15).toMillis();

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

  private final String libraryName = "freerouting";
  private final String libraryVersion;

  /** The authenticated BigQuery service. Owned exclusively by this instance. */
  private final BigQuery bigQuery;

  private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Map<String, String>>>
      pendingByTable = new ConcurrentHashMap<>();
  private final AtomicInteger totalPendingCount = new AtomicInteger(0);
  private final AtomicInteger inFlightBatches = new AtomicInteger(0);
  private final Object flushLock = new Object();

  private volatile long circuitBreakerExpiryEpochMs = 0L;
  private final AtomicBoolean circuitBreakerLogged = new AtomicBoolean(false);

  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          2,
          2,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(1_000),
          r -> {
            Thread t = new Thread(r, "analytics-bigquery-sender");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.DiscardPolicy());

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "analytics-bigquery-scheduler");
            t.setDaemon(true);
            return t;
          });

  private boolean enabled = true;

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  /**
   * Creates a BigQuery analytics client.
   *
   * @param libraryVersion the Freerouting version included in each payload
   * @param serviceAccountKey the full JSON content of the service-account key
   */
  public BigQueryClient(String libraryVersion, String serviceAccountKey) {
    this.libraryVersion = libraryVersion;
    // Enable TLS protocols
    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    bigQuery = createBigQueryService(serviceAccountKey.getBytes());
    startScheduler();
  }

  /** Package-private constructor for unit tests with a custom BigQuery instance. */
  BigQueryClient(String libraryVersion, BigQuery bigQuery) {
    this.libraryVersion = libraryVersion;
    this.bigQuery = bigQuery;
    startScheduler();
  }

  private void startScheduler() {
    scheduler.scheduleWithFixedDelay(
        this::flushAllPendingAsync, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  // -------------------------------------------------------------------------
  // Constructor (package-visible for tests; prefer getInstance() in production)
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
        if (singletonInstance != null) {
          singletonInstance.shutdown();
        }
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
  // GCP helpers
  // -------------------------------------------------------------------------

  private static BigQuery createBigQueryService(byte[] serviceAccountKeyBytes) {
    try {
      InputStream keyStream = new ByteArrayInputStream(serviceAccountKeyBytes);
      GoogleCredentials credentials =
          ServiceAccountCredentials.fromStream(keyStream)
              .createScoped("https://www.googleapis.com/auth/bigquery");
      credentials.refreshIfExpired();

      HttpTransportOptions transportOptions =
          HttpTransportOptions.newBuilder()
              .setConnectTimeout(20_000)
              .setReadTimeout(30_000)
              .build();

      RetrySettings retrySettings =
          RetrySettings.newBuilder()
              .setMaxAttempts(3)
              .setInitialRetryDelayDuration(Duration.ofMillis(1_000))
              .setRetryDelayMultiplier(2.0)
              .setMaxRetryDelayDuration(Duration.ofMillis(5_000))
              .setInitialRpcTimeoutDuration(Duration.ofMillis(20_000))
              .setRpcTimeoutMultiplier(1.5)
              .setMaxRpcTimeoutDuration(Duration.ofMillis(30_000))
              .setTotalTimeoutDuration(Duration.ofMillis(60_000))
              .build();

      return BigQueryOptions.newBuilder()
          .setCredentials(credentials)
          .setTransportOptions(transportOptions)
          .setRetrySettings(retrySettings)
          .build()
          .getService();
    } catch (IOException e) {
      throw new RuntimeException("Failed to create BigQuery client", e);
    }
  }

  // -------------------------------------------------------------------------
  // AnalyticsClient implementation
  // -------------------------------------------------------------------------

  private static boolean isTraitOnlyTable(String tableName) {
    return "identifies".equals(tableName) || "user_snapshots".equals(tableName);
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
    payload.context.library.name = libraryName;
    payload.context.library.version = libraryVersion;
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
    payload.context.library.name = libraryName;
    payload.context.library.version = libraryVersion;
    payload.event = event;
    payload.properties = properties;

    sendPayloadAsync(payload);
  }

  // -------------------------------------------------------------------------
  // Async send & micro-batching
  // -------------------------------------------------------------------------

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    if (!enabled) {
      clearPendingBuffers();
    }
  }

  boolean isCircuitBreakerOpen() {
    long expiry = circuitBreakerExpiryEpochMs;
    if (expiry == 0L) {
      return false;
    }
    if (System.currentTimeMillis() < expiry) {
      return true;
    }
    circuitBreakerExpiryEpochMs = 0L;
    circuitBreakerLogged.set(false);
    return false;
  }

  void tripCircuitBreaker() {
    circuitBreakerExpiryEpochMs = System.currentTimeMillis() + CIRCUIT_BREAKER_DURATION_MS;
    clearPendingBuffers();
    if (circuitBreakerLogged.compareAndSet(false, true)) {
      FRLogger.warn(
          "BigQuery rate limit / quota exceeded (429 RESOURCE_EXHAUSTED). Circuit breaker tripped; "
              + "pausing BigQuery streaming for "
              + Duration.ofMillis(CIRCUIT_BREAKER_DURATION_MS).toMinutes()
              + " minutes.");
    }
  }

  void resetCircuitBreaker() {
    circuitBreakerExpiryEpochMs = 0L;
    circuitBreakerLogged.set(false);
  }

  private void clearPendingBuffers() {
    for (var entry : pendingByTable.entrySet()) {
      ConcurrentLinkedQueue<Map<String, String>> queue = entry.getValue();
      int drained = 0;
      while (queue.poll() != null) {
        drained++;
      }
      if (drained > 0) {
        totalPendingCount.addAndGet(-drained);
      }
      pendingByTable.remove(entry.getKey(), queue);
    }
    synchronized (flushLock) {
      flushLock.notifyAll();
    }
  }

  static boolean isQuotaExceededError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof BigQueryException bqe) {
        if (bqe.getCode() == 429
            || "rateLimitExceeded".equalsIgnoreCase(bqe.getReason())
            || "RESOURCE_EXHAUSTED".equalsIgnoreCase(bqe.getReason())) {
          return true;
        }
      }
      String className = current.getClass().getName();
      if ("com.google.api.client.googleapis.json.GoogleJsonResponseException".equals(className)) {
        if (current.getMessage() != null && current.getMessage().contains("429")) {
          return true;
        }
      }
      String msg = current.getMessage();
      if (msg != null
          && (msg.contains("rateLimitExceeded")
              || msg.contains("RESOURCE_EXHAUSTED")
              || msg.contains("RATE_LIMIT_EXCEEDED")
              || msg.contains("Quota exceeded"))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private void sendPayloadAsync(Payload payload) {
    if (!enabled || executor.isShutdown() || isCircuitBreakerOpen()) {
      return;
    }

    if (totalPendingCount.get() >= MAX_PENDING_ROWS) {
      AnalyticsErrorAggregator.recordFailure(
          "BigQuery/buffer_overflow",
          new RuntimeException(
              "BigQuery buffer full (" + MAX_PENDING_ROWS + " rows). Dropping telemetry event."));
      return;
    }

    String tableName = payload.event.toLowerCase().replace(" ", "_").replace("-", "_");
    if (!pendingByTable.containsKey(tableName) && pendingByTable.size() >= MAX_DISTINCT_TABLES) {
      AnalyticsErrorAggregator.recordFailure(
          "BigQuery/table_overflow",
          new RuntimeException(
              "BigQuery distinct table limit reached ("
                  + MAX_DISTINCT_TABLES
                  + "). Dropping telemetry event."));
      return;
    }

    // Snapshot the fields on the calling thread so background threads don't race
    // on mutable payload state.
    Map<String, String> fields = generateFieldsFromPayload(payload);

    // Trait-only tables (identifies, user_snapshots) follow the Segment identifies schema:
    // flattened traits + standard metadata, but no event / event_text columns.
    if (!isTraitOnlyTable(tableName)) {
      fields.put("event_text", fields.get("event"));
      fields.remove("event");
      fields.put("event", tableName);
    } else {
      fields.remove("event");
    }

    ConcurrentLinkedQueue<Map<String, String>> queue =
        pendingByTable.computeIfAbsent(tableName, _ -> new ConcurrentLinkedQueue<>());
    queue.add(fields);
    totalPendingCount.incrementAndGet();

    if (queue.size() >= MAX_BATCH_SIZE) {
      triggerBatchSend(tableName);
    }
  }

  void flushAllPendingAsync() {
    if (!enabled || isCircuitBreakerOpen() || executor.isShutdown()) {
      return;
    }
    for (String tableName : pendingByTable.keySet()) {
      triggerBatchSend(tableName);
    }
  }

  private void triggerBatchSend(String tableName) {
    if (!enabled || executor.isShutdown() || isCircuitBreakerOpen()) {
      return;
    }
    try {
      executor.submit(() -> drainAndSend(tableName));
    } catch (Exception ignored) {
      // Handled by ThreadPoolExecutor discard policy or shutdown
    }
  }

  private void drainAndSend(String tableName) {
    if (!enabled || isCircuitBreakerOpen() || executor.isShutdown()) {
      return;
    }

    ConcurrentLinkedQueue<Map<String, String>> queue = pendingByTable.get(tableName);
    if (queue == null || queue.isEmpty()) {
      return;
    }

    List<Map<String, String>> batch = new ArrayList<>(MAX_BATCH_SIZE);
    Map<String, String> row;
    int drained = 0;
    while (batch.size() < MAX_BATCH_SIZE && (row = queue.poll()) != null) {
      batch.add(row);
      drained++;
    }

    if (drained > 0) {
      totalPendingCount.addAndGet(-drained);
    }

    if (queue.isEmpty()) {
      pendingByTable.remove(tableName, queue);
    }

    if (batch.isEmpty()) {
      return;
    }

    inFlightBatches.incrementAndGet();
    try {
      sendBatch(tableName, batch);
    } finally {
      inFlightBatches.decrementAndGet();
      synchronized (flushLock) {
        flushLock.notifyAll();
      }
    }

    // If more items remain in this table's queue, trigger another batch drain
    if (!queue.isEmpty() && enabled && !isCircuitBreakerOpen() && !executor.isShutdown()) {
      triggerBatchSend(tableName);
    }
  }

  private void sendBatch(String tableName, List<Map<String, String>> batch) {
    try {
      TableId tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, tableName);
      InsertAllRequest.Builder builder =
          InsertAllRequest.newBuilder(tableId).setIgnoreUnknownValues(true);
      for (Map<String, String> rowFields : batch) {
        builder.addRow(InsertAllRequest.RowToInsert.of(rowFields));
      }

      InsertAllResponse response = bigQuery.insertAll(builder.build());
      if (response.hasErrors()) {
        response
            .getInsertErrors()
            .forEach(
                (_, errors) ->
                    AnalyticsErrorAggregator.recordFailure(
                        "BigQuery/" + tableName,
                        new RuntimeException("Insert errors for " + tableName + ": " + errors),
                        errors.toString()));
      }
    } catch (Exception e) {
      if (isQuotaExceededError(e)) {
        tripCircuitBreaker();
      }
      AnalyticsErrorAggregator.recordFailure("BigQuery/" + tableName, e, e.getMessage());
    }
  }

  static boolean isTransientNetworkError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SocketTimeoutException
          || current instanceof ConnectException
          || current instanceof NoRouteToHostException
          || current instanceof UnknownHostException
          || current instanceof SocketException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  @Override
  public void flush(long timeoutMs) {
    if (!enabled || isCircuitBreakerOpen()) {
      return;
    }
    flushAllPendingAsync();
    long deadline = System.currentTimeMillis() + timeoutMs;
    synchronized (flushLock) {
      while (enabled
          && (totalPendingCount.get() > 0 || inFlightBatches.get() > 0)
          && !isCircuitBreakerOpen()) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          break;
        }
        try {
          flushLock.wait(remaining);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  @Override
  public void shutdown() {
    scheduler.shutdown();
    try {
      flush(2_000);
    } catch (Exception ignored) {
      // Best-effort flush before shutdown; ignore any errors.
    }
    executor.shutdown();
  }

  int getTotalPendingCount() {
    return totalPendingCount.get();
  }

  void tripCircuitBreakerForTesting() {
    tripCircuitBreaker();
  }

  void resetCircuitBreakerForTesting() {
    resetCircuitBreaker();
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

  /**
   * BigQuery table that stores micro-survey responses. Rows carry the standard analytics metadata
   * columns ({@code id}, {@code received_at}, {@code user_id}, {@code event}, ...) plus the
   * flattened payload columns {@code survey_id}, {@code option} and {@code client_version}.
   */
  static final String SURVEY_RESPONSE_TABLE = "survey_response";

  /**
   * Asynchronously inserts a survey response into the {@code survey_response} BigQuery table. The
   * caller (SurveyControllerV1) is responsible for the server-side dedup check; this method only
   * records the answer.
   *
   * @param surveyId the survey that was answered
   * @param userId the anonymous profile UUID
   * @param option the option the user clicked
   * @param clientVersion the desktop client version
   */
  public void recordSurveyResponse(
      String surveyId, String userId, String option, String clientVersion) {
    if (surveyId == null || userId == null || option == null) {
      return;
    }
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = userId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = libraryName;
    payload.context.library.version = libraryVersion;
    payload.event = SURVEY_RESPONSE_TABLE;

    Properties properties = new Properties();
    properties.put("survey_id", surveyId);
    properties.put("option", option);
    if (clientVersion != null) {
      properties.put("client_version", clientVersion);
    }
    payload.properties = properties;

    sendPayloadAsync(payload);
  }

  /**
   * Checks whether a response for the given {@code (surveyId, userId)} has already been recorded.
   * Enforces server-side deduplication in case the local cache is cleared.
   *
   * @return {@code true} if a row already exists; {@code false} if none exists or the lookup could
   *     not be performed. A transient lookup failure fails open (returns {@code false}) so that a
   *     real answer is never silently dropped on the server's side; the controller still writes the
   *     row, and BigQuery's row insertion is idempotent via the row {@code id}.
   */
  public boolean hasSurveyResponse(String surveyId, String userId) {
    if (surveyId == null || userId == null) {
      return false;
    }
    try {
      String query =
          "SELECT 1 FROM `"
              + BIGQUERY_PROJECT_ID
              + "."
              + BIGQUERY_DATASET_ID
              + "."
              + SURVEY_RESPONSE_TABLE
              + "` WHERE survey_id = @survey_id AND user_id = @user_id LIMIT 1";
      QueryJobConfiguration config =
          QueryJobConfiguration.newBuilder(query)
              .addNamedParameter("survey_id", QueryParameterValue.string(surveyId))
              .addNamedParameter("user_id", QueryParameterValue.string(userId))
              .build();
      TableResult result = bigQuery.query(config);
      return result != null && result.getTotalRows() > 0;
    } catch (Exception e) {
      FRLogger.warn("Could not check survey_response dedup for survey '" + surveyId + "': " + e);
      return false;
    }
  }
}
