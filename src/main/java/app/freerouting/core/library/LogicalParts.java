package app.freerouting.core.library;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Vector;

/** The logical parts contain information for gate swap and pin swap. */
public class LogicalParts implements Serializable {

  /** The array of logical parts. */
  private final Vector<LogicalPart> partArr = new Vector<>();

  /** Adds a logical part to the database. */
  public LogicalPart add(String name, LogicalPart.PartPin[] partPinArr) {
    Arrays.sort(partPinArr);
    LogicalPart newPart = new LogicalPart(name, partArr.size() + 1, partPinArr);
    partArr.add(newPart);
    return newPart;
  }

  /** Returns the logical part with the input name or null, if no such package exists. */
  public LogicalPart get(String name) {
    for (LogicalPart currPart : this.partArr) {
      if (currPart != null && currPart.name.equalsIgnoreCase(name)) {
        return currPart;
      }
    }
    return null;
  }

  /** Returns the logical part with the specified index. Part numbers start at 1. */
  public LogicalPart get(int partNo) {
    LogicalPart result = partArr.elementAt(partNo - 1);
    if (result != null && result.no != partNo) {
      FRLogger.warn("LogicalParts.get: inconsistent part number");
    }
    return result;
  }

  /** Returns the count of logical parts. */
  public int count() {
    return partArr.size();
  }
}
