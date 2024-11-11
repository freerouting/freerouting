package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.dto.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A client for Google BigQuery's API.
 * Please note, that identifies, tracks, users tables are NOT updated in BigQuery (unlike Segment).
 */
public class BigQueryClient implements AnalyticsClient
{
  private static final String BIGQUERY_ENCRYPTED_SERVICE_ACCOUNT_KEY = "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";
  private static final String BIGQUERY_PASSPHRASE = "ysddgyXy49JLkGKvEjrMviDaN8h78nHu";
  private static final String BIGQUERY_PROJECT_ID = "freerouting-analytics";
  private static final String BIGQUERY_DATASET_ID = "freerouting_application";
  private static BigQuery bigQuery;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;

  public BigQueryClient(String libraryVersion)
  {
    LIBRARY_VERSION = libraryVersion;
    // Enable TLS protocols
    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    bigQuery = createBigQueryClient();
  }

  private BigQuery createBigQueryClient()
  {
    try
    {
      // decode the service account key
      byte[] decodedKey = Base64
          .getDecoder()
          .decode(BIGQUERY_ENCRYPTED_SERVICE_ACCOUNT_KEY);

      // decrypt the service account key using aes-256-cbc
      byte[] serviceKey = TextManager.decryptAes256Cbc(decodedKey, BIGQUERY_PASSPHRASE);

      InputStream keyStream = new ByteArrayInputStream(serviceKey);
      GoogleCredentials credentials = ServiceAccountCredentials
          .fromStream(keyStream)
          .createScoped("https://www.googleapis.com/auth/bigquery");
      credentials.refreshIfExpired();
      // create the BigQuery client builder
      var bigQueryBuilder = BigQueryOptions.newBuilder();
      // set the credentials
      var bigQueryOptions = bigQueryBuilder
          .setCredentials(credentials)
          .build();
      // create the BigQuery client
      return bigQueryOptions.getService();
    } catch (IOException e)
    {
      throw new RuntimeException("Failed to create BigQuery client", e);
    }
  }

  private void sendPayloadAsync(Payload payload)
  {
    if (!enabled)
    {
      return;
    }

    // generate the fields Map from the payload class
    Map<String, String> fields = generateFieldsFromPayload(payload);

    new Thread(() ->
    {
      try
      {
        // table name is the event name with some formatting
        String tableName = payload.event
            .toLowerCase()
            .replace(" ", "_")
            .replace("-", "_");

        // apply a text transformation to the event and event_text fields
        fields.put("event_text", fields.get("event"));
        fields.remove("event");
        fields.put("event", tableName);


        TableId tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, tableName);
        InsertAllRequest.Builder builder = InsertAllRequest.newBuilder(tableId);

        InsertAllRequest.RowToInsert row = InsertAllRequest.RowToInsert.of(fields);
        builder.addRow(row);

        InsertAllResponse response = bigQuery.insertAll(builder.build());
        if (response.hasErrors())
        {
          // Handle errors
          response
              .getInsertErrors()
              .forEach((index, errors) ->
              {
                // Log or handle the errors
                FRLogger.error("Error in BigQueryClient.send_payload_async: (" + tableName + ")" + errors, null);
              });
        }
      } catch (Exception e)
      {
        FRLogger.error("Exception in BigQueryClient.send_payload_async: " + e.getMessage(), e);
      }
    }).start();
  }

  private Map<String, String> generateFieldsFromPayload(Payload payload)
  {
    // fields are the fields of the payload class, plus the traits and properties map combined formatted for BigQuery
    Map<String, String> fields = new HashMap<String, String>();

    // payload.traits needs to have a few more fields that are normally set in the SegmentClient
    fields.put("id", "frg-2o0" + TextManager.generateRandomAlphanumericString(25));
    var eventHappenedAt = Instant.now();
    fields.put("received_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("sent_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss") + " UTC");
    fields.put("original_timestamp", "<nil>");
    fields.put("timestamp", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    // add the basic fields to the map
    fields.put("user_id", payload.userId);
    fields.put("anonymous_id", payload.anonymousId);
    fields.put("event", payload.event);
    fields.put("context_library_name", payload.context.library.name);
    fields.put("context_library_version", payload.context.library.version);

    // payload.traits needs to have a few more fields that are normally set in the SegmentClient
    var payloadUploadedAt = Instant.now();
    fields.put("loaded_at", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("uuid_ts", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    if ((payload.traits != null) && (!payload.traits.isEmpty()))
    {
      fields.putAll(payload.traits);
    }

    if ((payload.properties != null) && (!payload.properties.isEmpty()))
    {
      fields.putAll(payload.properties);
    }

    return fields;
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

    // NOTE: we ignore the identify event in BigQuery (because we have the tracked "application started" event instead)
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

    sendPayloadAsync(payload);
  }

  public void setEnabled(boolean enabled)
  {
    this.enabled = enabled;
  }
}