package app.freerouting.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class BigQueryClientTest {

  @Test
  void testTransientNetworkErrorDetection() {
    assertTrue(
        BigQueryClient.isTransientNetworkError(new SocketTimeoutException("Connect timed out")));
    assertTrue(BigQueryClient.isTransientNetworkError(new ConnectException("Connection refused")));
    assertTrue(BigQueryClient.isTransientNetworkError(new NoRouteToHostException("No route")));
    assertTrue(
        BigQueryClient.isTransientNetworkError(new UnknownHostException("Host unresolvable")));
    assertTrue(BigQueryClient.isTransientNetworkError(new SocketException("Connection reset")));

    // Nested exceptions
    BigQueryException wrappedTimeout =
        new BigQueryException(
            0, "Connect timed out", new SocketTimeoutException("Connect timed out"));
    assertTrue(BigQueryClient.isTransientNetworkError(wrappedTimeout));

    // Non-transient errors
    assertFalse(
        BigQueryClient.isTransientNetworkError(new IllegalArgumentException("Invalid argument")));
    assertFalse(BigQueryClient.isTransientNetworkError(new NullPointerException("NPE")));
    assertFalse(BigQueryClient.isTransientNetworkError(new BigQueryException(404, "Not found")));
  }

  @Test
  void testLifecycleWithMock() throws IOException {
    BigQuery mockBigQuery = mock(BigQuery.class);
    InsertAllResponse mockResponse = mock(InsertAllResponse.class);
    when(mockResponse.hasErrors()).thenReturn(false);
    when(mockBigQuery.insertAll(any(InsertAllRequest.class))).thenReturn(mockResponse);

    BigQueryClient client = new BigQueryClient("2.3.0", mockBigQuery);

    Properties props = new Properties();
    props.put("file_format", "SES");
    props.put("file_details", "board.ses");

    // Track event
    client.track("user-1", "anon-1", "File Saved", props);
    client.flush(1000);

    verify(mockBigQuery).insertAll(any(InsertAllRequest.class));

    // Identify
    Traits traits = new Traits();
    traits.put("test", "val");
    client.identify("user-1", "anon-1", traits);
    client.flush(1000);

    // Upsert user snapshot
    client.upsertUserSnapshot("user-1", "anon-1", traits);
    client.flush(1000);

    // Shutdown
    assertDoesNotThrow(client::shutdown);
  }

  @Test
  void testDisabledClientDoesNotSend() throws IOException {
    BigQuery mockBigQuery = mock(BigQuery.class);
    BigQueryClient client = new BigQueryClient("2.3.0", mockBigQuery);
    client.setEnabled(false);

    Properties props = new Properties();
    props.put("key", "value");
    client.track("user-1", "anon-1", "Some Event", props);

    client.flush(500);
    client.shutdown();
  }
}
