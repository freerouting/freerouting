package app.freerouting.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FreeroutingAnalyticsClientTest {

  @Test
  void testFlushAndShutdownLifecycle() throws IOException {
    FreeroutingAnalyticsClient client = new FreeroutingAnalyticsClient("2.3.0", "dummy-key");
    client.setEnabled(false); // disable actual network calls

    Traits traits = new Traits();
    traits.put("test_key", "test_value");
    client.identify("user-1", "anon-1", traits);

    Properties props = new Properties();
    props.put("prop1", "val1");
    client.track("user-1", "anon-1", "Test Event", props);

    assertDoesNotThrow(() -> client.flush(500));
    assertDoesNotThrow(client::shutdown);
  }

  @Test
  void testFRAnalyticsFlush() {
    assertDoesNotThrow(() -> FRAnalytics.flush(100));
  }
}
