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
  private static final String BIGQUERY_ENCODED_SERVICE_ACCOUNT_KEY = "ewogICJ0eXBlIjogInNlcnZpY2VfYWNjb3VudCIsCiAgInByb2plY3RfaWQiOiAiZnJlZXJvdXRpbmctYW5hbHl0aWNzIiwKICAicHJpdmF0ZV9rZXlfaWQiOiAiMWMwY2EzYjRhOGMzMjQ4M2NlZmZjODIwOGZiMmY3NjA2YmIyNzRkYyIsCiAgInByaXZhdGVfa2V5IjogIi0tLS0tQkVHSU4gUFJJVkFURSBLRVktLS0tLVxuTUlJRXZRSUJBREFOQmdrcWhraUc5dzBCQVFFRkFBU0NCS2N3Z2dTakFnRUFBb0lCQVFDeVZpaWtNeUVNYjVQRlxuaEJSWklsMWNSODJMNHpldTVlQnNJWG81THMvVi9HN1NxRGQxU2VCWFlVOGgvdFo1VFBQMFhDeEx4YWhRV2pwa1xuM1ljbjIyR0QreGlzMWF3L1FZeEhhWXdTeVh3NzFsZVZTaWxwbVhJTGFzVGpxVlhGWlltM1Q3M1JWNElkVzlXWFxueVVpdTFKenZMRXNQcGp2YVhTUENpTmgzUno2ZVVIVGx1Tm1XUEZmVU54VW9hWVdwd1piV2tLRExvdlU4dlZWZVxud2ZGN0c1RkxGenJ1dEQ1SE1uZExFWXZRSjNEMDFzUUx6R1JMOXd4QnNuZk5pMktodVBtSGtmRm5WakNJN09aZFxuR3p1Z1N4SGFXZ0dKMEs1VUxDUmh6ZHJjaHpoMlcxMS8zTTRwUkZHYVBCRHFWbFl6ZTVoWVV0aWl4Y2pXNm9seVxucVpDT2srT1JBZ01CQUFFQ2dnRUFBNndyemRiYU5rRDVlbkt4L3hScXR3YnBCQm1YcU5mRno0QkFmYnM1cnpyUlxudHUrZEpjS1Ixc2JUbS92OUkzWWEyNFJHNnNBTksydHlRcWUvdStuRUNEVmtjSkc5YUhlVGVaaUNPSlJTNVVJUVxubjJ2TnJzV28wRFNnalo0ci9LTE95MW8rYXRoYUh2dm9TZ1lNZzhJdjRhcUlPTEFvRVNNRUpLSlFEK1BGRmJwQ1xuVGRRc0c5VGpra01ObXQ2YUlBR3Z0K3RtSURianIrV09LUTZHejdYb1NLRWJienFqaWRPSVgrNmRXeVRSaDdtelxubWZySEZKa2NDeENhclM0TjJyVDhnYVNRYkY1RGdRbXg1aEdQWjlFcXEzV2x3RlNJMDRNZUwwaXRsZ2xFeFlIWFxuQ2tjTk0vRzNpM3l5WTZkY2E0VDllRWZzb3pVUlUzQVMwbkN5bmtKbmdRS0JnUUQxSUlrMFNRaFNsc01YZGpLM1xuNmZhakF3QnMvSGNSRjl3SGQ4UG01dWJ2S2JnaVRzbm4vUnptWEVQemUzRmFtLzZuVjU3RkN1KzFWbUFSblRRM1xuK2hwamMvdTU0dCttcE4zUDBxUFU4NjlPMUpyT2FXbjBJY2FGOStnclFRQU43V3JCeUhoTmVPM0tzV3ovcktXL1xuVFBBeGY4ai9sVW8zVG9ldS9lRlpMaCs1d1FLQmdRQzZQelFwbXJCYUdDSVk0ZitiY0dZcUxJeXBNM3llcEhGaFxuZzErb2E3YmdBZHVFQkJQVHB2M0xSRTN1ZmhPTFFSRDBidDlMck5PNEdTeGVtRlptd2h4QithNkg5Si9wNGxCbFxuOGE5aXp4M3ZEVjVyOXVDMTk3TnNIbm9qRlZyMWo2MFltWkpFYXdZNDZVamQ2YThuSVJRZUJ3M3QvTkttYTZVR1xuNHZ2WW8zcDkwUUtCZ1FDbVNDanF2L1FXV2xFRFZGbjVhb3UxYnU3Vi83a2hia2NEQmRwdGd3cjdDQmp4cFBMUVxuSFdLQ1hlcDJlN0djWHAremt2dVAvT2c0NGR3UGRzMmFmMTF4UTVkcU5KMjBwTGdYSjNPZG5LUzZXL3lic3VSK1xuQ0g5c0Y0eDE4d1QvYmFOeXl2UFkwZ1MwOEFEWnU1dEFGd2dFL0FNMFpXaFA5a1NTajRSVGc3ZGZ3UUtCZ0F4alxuK0F4c2hoNzRUQXhydkoyU3RMbEpqWElVUXM0ckVuL3lSWUxtNTV1dmcvTWNjbFU2WHRnUEMwQTZrd0pJcWVBWlxuSURIZ3BaVXgxNG5UaUt2OWJmUFZzSTdLNzNpWDNkRnFhc2lnRHRYQWhlK1kxUXBHR0dHeEJWOGdKSlVCb2Zwb1xuL1JvZ0pLSFVvMHhnSjQ3cTNIUEM5R0pJMTVyS3ZvZmV3ZkovcmI2QkFvR0Fjb3BzNGw0b0NMRFQzUVNhZ2x2QVxuQXA3Y2dPdmthNklmOU5RVGF0eDN4aFVlamxTdjdSNFZob2E0M3IzUXdFUEJEMStCb0kyd3R4QngxRUhuTEtUZFxuVVI4ajRGdXFuczUvVk0xdkFtVkg3VTVpZm1DK3d0ZXhSbzdUZFhNRFl4YnZET3d1Tlhsb2xyS1JsbW5YaUg3TVxuSUF5MFNiY1FTVTVEMFJpOGhTb0pscVk9XG4tLS0tLUVORCBQUklWQVRFIEtFWS0tLS0tXG4iLAogICJjbGllbnRfZW1haWwiOiAiZnJlZXJvdXRpbmctYXBwbGljYXRpb25AZnJlZXJvdXRpbmctYW5hbHl0aWNzLmlhbS5nc2VydmljZWFjY291bnQuY29tIiwKICAiY2xpZW50X2lkIjogIjExNjg0OTUzOTQ3OTY2MTQ5MDYzNCIsCiAgImF1dGhfdXJpIjogImh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbS9vL29hdXRoMi9hdXRoIiwKICAidG9rZW5fdXJpIjogImh0dHBzOi8vb2F1dGgyLmdvb2dsZWFwaXMuY29tL3Rva2VuIiwKICAiYXV0aF9wcm92aWRlcl94NTA5X2NlcnRfdXJsIjogImh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL29hdXRoMi92MS9jZXJ0cyIsCiAgImNsaWVudF94NTA5X2NlcnRfdXJsIjogImh0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL3JvYm90L3YxL21ldGFkYXRhL3g1MDkvZnJlZXJvdXRpbmctYXBwbGljYXRpb24lNDBmcmVlcm91dGluZy1hbmFseXRpY3MuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLAogICJ1bml2ZXJzZV9kb21haW4iOiAiZ29vZ2xlYXBpcy5jb20iCn0K";
  private static final String BIGQUERY_PROJECT_ID = "freerouting-analytics";
  private static final String BIGQUERY_DATASET_ID = "freerouting_application";
  private static BigQuery bigQuery;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;

  public BigQueryClient(String libraryVersion)
  {
    LIBRARY_VERSION = libraryVersion;
    bigQuery = createBigQueryClient();
  }

  private BigQuery createBigQueryClient()
  {
    try
    {
      // decode the service account key
      byte[] decodedKey = Base64.getDecoder().decode(BIGQUERY_ENCODED_SERVICE_ACCOUNT_KEY);
      InputStream keyStream = new ByteArrayInputStream(decodedKey);
      GoogleCredentials credentials = ServiceAccountCredentials.fromStream(keyStream);
      // create the BigQuery client builder
      var bigQueryBuilder = BigQueryOptions.newBuilder();
      // set the credentials
      var bigQueryOptions = bigQueryBuilder.setCredentials(credentials).build();
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
        String tableName = payload.event.toLowerCase().replace(" ", "_");

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
          response.getInsertErrors().forEach((index, errors) ->
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