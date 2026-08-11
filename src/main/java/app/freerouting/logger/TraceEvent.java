package app.freerouting.logger;

import app.freerouting.geometry.planar.Point;
import java.time.Instant;

/** Payload describing an interesting trace event. */
public class TraceEvent {

  private final String method;
  private final String operation;
  private final String message;
  private final String impactedItems;
  private final Point[] impactedPoints;
  private final Instant timestamp;

  /**
   * Creates a trace event payload for debug visualization.
   *
   * @param method the originating method name
   * @param operation the operation type
   * @param message the trace message
   * @param impactedItems description of impacted board items
   * @param impactedPoints optional geometry points involved in the operation
   * @param timestamp when the event occurred
   */
  public TraceEvent(
      String method,
      String operation,
      String message,
      String impactedItems,
      Point[] impactedPoints,
      Instant timestamp) {
    this.method = method;
    this.operation = operation;
    this.message = message;
    this.impactedItems = impactedItems;
    this.impactedPoints = impactedPoints;
    this.timestamp = timestamp;
  }

  public String getMethod() {
    return method;
  }

  public String getOperation() {
    return operation;
  }

  public String getMessage() {
    return message;
  }

  public String getImpactedItems() {
    return impactedItems;
  }

  public Point[] getImpactedPoints() {
    return impactedPoints;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
