package app.freerouting.logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** In-memory collection of log entries for UI display and export. */
public class LogEntries {

  private final List<LogEntry> entries = new ArrayList<>();
  private final List<LogEntryAddedListener> listeners = new ArrayList<>();

  /** Returns the number of warning entries currently stored. */
  public int getWarningCount() {
    synchronized (entries) {
      return (int) entries.stream().filter(e -> e.type == LogEntryType.Warning).count();
    }
  }

  /** Returns the number of error entries currently stored. */
  public int getErrorCount() {
    synchronized (entries) {
      return (int) entries.stream().filter(e -> e.type == LogEntryType.Error).count();
    }
  }

  /** Removes all stored log entries. */
  public void clear() {
    synchronized (entries) {
      entries.clear();
    }
  }

  /** Returns all entries as a single newline-delimited string. */
  public String getAsString() {
    synchronized (entries) {
      return entries.stream().map(LogEntry::toString).collect(Collectors.joining("\n", "", "\n"));
    }
  }

  /** Returns all entries as an array of formatted strings. */
  public String[] get() {
    synchronized (entries) {
      return entries.stream().map(LogEntry::toString).toArray(String[]::new);
    }
  }

  /** Returns entries filtered by timestamp and optional topic. */
  public LogEntry[] getEntries(Instant entriesSince, UUID topic) {
    synchronized (entries) {
      return entries.stream()
          .filter(
              e ->
                  ((entriesSince == null) || e.timestamp.isAfter(entriesSince))
                      && (topic == null || ((e.topic != null) && e.topic.equals(topic))))
          .toArray(LogEntry[]::new);
    }
  }

  /** Adds a log entry without an associated exception. */
  public LogEntry add(LogEntryType type, String message, UUID topic) {
    return this.add(type, message, topic, null);
  }

  /** Adds a log entry, optionally with an associated exception. */
  public LogEntry add(LogEntryType type, String message, UUID topic, Throwable exception) {
    LogEntry logEntry = new LogEntry(type, message, exception, topic);

    synchronized (entries) {
      entries.add(logEntry);
    }

    // Raise the event
    for (LogEntryAddedListener listener : listeners) {
      listener.logEntryAdded(logEntry);
    }

    return logEntry;
  }

  /** Registers a listener notified when a new entry is added. */
  public void addLogEntryAddedListener(LogEntryAddedListener listener) {
    listeners.add(listener);
  }

  /** Unregisters a previously added entry listener. */
  public void removeLogEntryAddedListener(LogEntryAddedListener listener) {
    listeners.remove(listener);
  }

  /** Listener notified when a new log entry is added. */
  public interface LogEntryAddedListener {

    /** Called when a new log entry is stored. */
    void logEntryAdded(LogEntry logEntry);
  }
}
