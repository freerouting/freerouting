package app.freerouting.logger;

import java.time.Instant;
import java.util.UUID;

/// <summary>
/// Represents a log entry.
/// </summary>
public class LogEntry
{
  /// <summary>
  /// Timestamp of the log entry.
  /// </summary>
  Instant timestamp;
  /// <summary>
  /// Type of the log entry.
  /// </summary>
  LogEntryType type;
  /// <summary>
  /// Message of the log entry.
  /// </summary>
  String message;
  /// <summary>
  /// Exception of the log entry.
  /// </summary>
  transient Throwable exception;
  /// <summary>
  /// Topic of the log entry. It is usually the ID of the user, session or job.
  /// </summary>
  UUID topic;

  /// <summary>
  /// Initializes a new instance of the <see cref="LogEntry"/> class.
  /// </summary>
  public LogEntry(LogEntryType type, String message, Throwable exception, UUID topic)
  {
    this.timestamp = Instant.now();
    this.type = type;
    this.message = message;
    this.exception = exception;
    this.topic = topic;
  }

  public LogEntryType getType()
  {
    return this.type;
  }

  public String getMessage()
  {
    return this.message;
  }

  public UUID getTopic()
  {
    return this.topic;
  }

  @Override
  public String toString()
  {
    return "%-7s".formatted(this.type.toString().toUpperCase()) + " " + this.message;
  }
}