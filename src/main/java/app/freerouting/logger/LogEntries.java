package app.freerouting.logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogEntries
{
  private final List<LogEntry> entries = new ArrayList<>();
  private final List<LogEntryAddedListener> listeners = new ArrayList<>();

  public int getWarningCount()
  {
    return (int) entries.stream().filter(e -> e.type == LogEntryType.Warning).count();
  }

  public int getErrorCount()
  {
    return (int) entries.stream().filter(e -> e.type == LogEntryType.Error).count();
  }

  public void clear()
  {
    entries.clear();
  }

  public String getAsString()
  {
    return entries.stream().map(LogEntry::toString).collect(Collectors.joining("\n", "", "\n"));
  }

  public String[] get()
  {
    return entries.stream().map(LogEntry::toString).toArray(String[]::new);
  }

  public LogEntry[] getEntries()
  {
    return entries.toArray(new LogEntry[0]);
  }

  public void add(LogEntryType type, String message)
  {
    this.add(type, message, null);

    // Raise the event
    for (LogEntryAddedListener listener : listeners)
    {
      listener.logEntryAdded(type, message);
    }
  }

  public void add(LogEntryType type, String message, Throwable exception)
  {
    entries.add(new LogEntry(type, message, exception));
  }

  public void addLogEntryAddedListener(LogEntryAddedListener listener)
  {
    listeners.add(listener);
  }

  // Event to be raised when a log entry is added
  public interface LogEntryAddedListener
  {
    void logEntryAdded(LogEntryType type, String message);
  }
}