package app.freerouting.logger;

import java.time.Instant;

/// <summary>
/// Represents a log entry.
/// </summary>
public class LogEntry {
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
  Throwable exception;

  /// <summary>
  /// Initializes a new instance of the <see cref="LogEntry"/> class.
  /// </summary>
  public LogEntry(LogEntryType type, String message, Throwable exception) {
    this.timestamp = Instant.now();
    this.type = type;
    this.message = message;
    this.exception = exception;
  }

  @Override
  public String toString() {
    return String.format("%-7s", this.type.toString().toUpperCase()) + " " + this.message;
  }
}
