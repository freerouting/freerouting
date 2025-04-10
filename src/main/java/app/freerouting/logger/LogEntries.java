package app.freerouting.logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class LogEntries
{
  private final List<LogEntry> entries = new ArrayList<>();
  private final List<LogEntryAddedListener> listeners = new ArrayList<>();

  public int getWarningCount()
  {
    synchronized (entries)
    {
      return (int) entries
          .stream()
          .filter(e -> e.type == LogEntryType.Warning)
          .count();
    }
  }

  public int getErrorCount()
  {
    synchronized (entries)
    {
      return (int) entries
          .stream()
          .filter(e -> e.type == LogEntryType.Error)
          .count();
    }
  }

  public void clear()
  {
    synchronized (entries)
    {
      entries.clear();
    }
  }

  public String getAsString()
  {
    synchronized (entries)
    {
      return entries
          .stream()
          .map(LogEntry::toString)
          .collect(Collectors.joining("\n", "", "\n"));
    }
  }

  public String[] get()
  {
    synchronized (entries)
    {
      return entries
          .stream()
          .map(LogEntry::toString)
          .toArray(String[]::new);
    }
  }

  public LogEntry[] getEntries(Instant entriesSince, UUID topic)
  {
    synchronized (entries)
    {
      return entries
          .stream()
          .filter(e -> ((entriesSince == null) || e.timestamp.isAfter(entriesSince)) && (topic == null || ((e.topic != null) && e.topic.equals(topic))))
          .toArray(LogEntry[]::new);
    }
  }

  public LogEntry add(LogEntryType type, String message, UUID topic)
  {
    LogEntry logEntry = this.add(type, message, topic, null);

    return logEntry;
  }

  public LogEntry add(LogEntryType type, String message, UUID topic, Throwable exception)
  {
    LogEntry logEntry = new LogEntry(type, message, exception, topic);

    synchronized (entries)
    {
      entries.add(logEntry);
    }

    // Raise the event
    for (LogEntryAddedListener listener : listeners)
    {
      listener.logEntryAdded(logEntry);
    }

    return logEntry;
  }

  public void addLogEntryAddedListener(LogEntryAddedListener listener)
  {
    listeners.add(listener);
  }

  // Event to be raised when a log entry is added
  public interface LogEntryAddedListener
  {
    void logEntryAdded(LogEntry logEntry);
  }
}