package app.freerouting.core.events;

/** Receives events when routing jobs change. */
public interface RoutingJobUpdatedEventListener {

  /** Handles a routing job update. */
  void onRoutingJobUpdated(RoutingJobUpdatedEvent event);
}
