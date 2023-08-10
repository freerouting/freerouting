package app.freerouting.logger;

import java.util.ArrayList;
import java.util.List;

public class LogEntries {
  private final List<LogEntry> entries = new ArrayList<LogEntry>();

  public int getWarningCount()
  {
    return (int)entries.stream().filter(e -> e.type == LogEntryType.Warning).count();
  }

  public int getErrorCount()
  {
    return (int)entries.stream().filter(e -> e.type == LogEntryType.Error).count();
  }

  public void clear()
  {
    entries.clear();
  }

  public String getAsString()
  {
    StringBuilder sb = new StringBuilder();
    for (LogEntry entry : entries)
    {
      sb.append(entry.toString());
      sb.append("\n");
    }
    return sb.toString();
  }

  public String[] get()
  {
    String[] result = new String[entries.size()];
    for (int i = 0; i < entries.size(); i++)
    {
      result[i] = entries.get(i).toString();
    }
    return result;
  }

  public LogEntry[] getEntries()
  {
    return entries.toArray(new LogEntry[0]);
  }

  public void add(LogEntryType type, String message)
  {
    this.add(type, message, null);
  }

  public void add(LogEntryType type, String message, Throwable exception)
  {
    entries.add(new LogEntry(type, message, exception));
  }
}
