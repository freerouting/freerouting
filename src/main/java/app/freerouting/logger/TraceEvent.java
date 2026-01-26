package app.freerouting.logger;

import java.time.Instant;

/** Payload describing an interesting trace event. */
public class TraceEvent {

  private final String method;
  private final String operation;
  private final String message;
  private final String impactedItems;
  private final Instant timestamp;

  public TraceEvent(String method, String operation, String message, String impactedItems, Instant timestamp) {
    this.method = method;
    this.operation = operation;
    this.message = message;
    this.impactedItems = impactedItems;
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

  public Instant getTimestamp() {
    return timestamp;
  }
}