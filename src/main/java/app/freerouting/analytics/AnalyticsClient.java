package app.freerouting.analytics;

import app.freerouting.analytics.dto.Properties;
import app.freerouting.analytics.dto.Traits;
import java.io.IOException;

/** Sends analytics identity and event payloads to a telemetry backend. */
public interface AnalyticsClient {

  /**
   * Identify a user. This is only called once at the beginning of the session.
   *
   * @param userId The user's unique identifier.
   * @param anonymousId The anonymous identifier associated with the user.
   * @param traits The user's traits.
   * @throws IOException if the payload cannot be sent.
   */
  void identify(String userId, String anonymousId, Traits traits) throws IOException;

  /**
   * Track an event. The event can be anything that happens during the session.
   *
   * @param userId The user's unique identifier.
   * @param anonymousId The anonymous identifier associated with the event.
   * @param event The event name.
   * @param properties Additional properties to include with the event.
   * @throws IOException if the payload cannot be sent.
   */
  void track(String userId, String anonymousId, String event, Properties properties)
      throws IOException;

  /**
   * Enable or disable the client. When disabled, the client will not send any events.
   *
   * @param enabled Whether the client should be enabled.
   */
  void setEnabled(boolean enabled);
}
