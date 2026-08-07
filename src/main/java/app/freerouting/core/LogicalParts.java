package app.freerouting.core;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Vector;

/** The logical parts contain information for gate swap and pin swap. */
public class LogicalParts implements Serializable {

  /** The array of logical parts */
  private final Vector<LogicalPart> partArr = new Vector<>();

  /** Adds a logical part to the database. */
  public LogicalPart add(String p_name, LogicalPart.PartPin[] p_part_pin_arr) {
    Arrays.sort(p_part_pin_arr);
    LogicalPart newPart = new LogicalPart(p_name, partArr.size() + 1, p_part_pin_arr);
    partArr.add(newPart);
    return newPart;
  }

  /** Returns the logical part with the input name or null, if no such package exists. */
  public LogicalPart get(String p_name) {
    for (LogicalPart currPart : this.partArr) {
      if (currPart != null && currPart.name.equalsIgnoreCase(p_name)) {
        return currPart;
      }
    }
    return null;
  }

  /** Returns the logical part with index p_part_no. Part numbers are from 1 to part count. */
  public LogicalPart get(int p_part_no) {
    LogicalPart result = partArr.elementAt(p_part_no - 1);
    if (result != null && result.no != p_part_no) {
      FRLogger.warn("LogicalParts.get: inconsistent part number");
    }
    return result;
  }

  /** Returns the count of logical parts. */
  public int count() {
    return partArr.size();
  }
}
