package app.freerouting.logger;

import java.time.Instant;
import java.util.UUID;

/** Represents a single in-memory log entry. */
public class LogEntry {

  /** Timestamp of the log entry. */
  Instant timestamp;

  /** Type of the log entry. */
  LogEntryType type;

  /** Message of the log entry. */
  String message;

  /** Exception of the log entry. */
  transient Throwable exception;

  /** Topic of the log entry, usually the ID of the user, session, or job. */
  UUID topic;

  /**
   * Creates a log entry with the given type, message, optional exception, and topic.
   *
   * @param type the entry severity/type
   * @param message the log message text
   * @param exception optional associated exception
   * @param topic optional topic identifier
   */
  public LogEntry(LogEntryType type, String message, Throwable exception, UUID topic) {
    this.timestamp = Instant.now();
    this.type = type;
    this.message = message;
    this.exception = exception;
    this.topic = topic;
  }

  public LogEntryType getType() {
    return this.type;
  }

  public String getMessage() {
    return this.message;
  }

  public UUID getTopic() {
    return this.topic;
  }

  public Throwable getException() {
    return this.exception;
  }

  @Override
  public String toString() {
    return "%-7s".formatted(this.type.toString().toUpperCase()) + " " + this.message;
  }
}
