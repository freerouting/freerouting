package app.freerouting.core;

import app.freerouting.management.RoutingJobScheduler;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a user session that contains the jobs that will be processed by the router.
 */
public class Session implements Serializable {

  @SerializedName("id")
  public final UUID id = UUID.randomUUID();
  @SerializedName("user_id")
  public final UUID userId;
  @SerializedName("host")
  public final String host;
  public transient boolean isGuiSession;

  /**
   * Creates a new session.
   *
   * @param userId The user ID that the session belongs to.
   */
  public Session(UUID userId, String host) {
    this.userId = userId;
    this.host = host;

    // check if the host value is valid (it must contain the host name and version separated by "/")
    if (host.split("/").length != 2) {
      throw new IllegalArgumentException("Invalid host value: '" + host + "'. It must contain the host name and version separated by '/'.");
    }
  }

  /**
   * Adds a job to the session.
   *
   * @param routingJob The job to add.
   */
  public void addJob(RoutingJob routingJob) {
    RoutingJobScheduler.getInstance().enqueueJob(routingJob);
  }
}