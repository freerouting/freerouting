package app.freerouting.library;

import app.freerouting.logger.FRLogger;
import java.util.Vector;

/** The logical parts contain information for gate swap and pin swap. */
public class LogicalParts implements java.io.Serializable {
  /** The array of logical parts */
  private final Vector<LogicalPart> part_arr = new Vector<LogicalPart>();

  /** Adds a logical part to the database. */
  public LogicalPart add(String p_name, LogicalPart.PartPin[] p_part_pin_arr) {
    java.util.Arrays.sort(p_part_pin_arr);
    LogicalPart new_part = new LogicalPart(p_name, part_arr.size() + 1, p_part_pin_arr);
    part_arr.add(new_part);
    return new_part;
  }

  /** Returns the logical part with the input name or null, if no such package exists. */
  public LogicalPart get(String p_name) {
    for (LogicalPart curr_part : this.part_arr) {
      if (curr_part != null && curr_part.name.compareToIgnoreCase(p_name) == 0) {
        return curr_part;
      }
    }
    return null;
  }

  /** Returns the logical part with index p_part_no. Part numbers are from 1 to part count. */
  public LogicalPart get(int p_part_no) {
    LogicalPart result = part_arr.elementAt(p_part_no - 1);
    if (result != null && result.no != p_part_no) {
      FRLogger.warn("LogicalParts.get: inconsistent part number");
    }
    return result;
  }

  /** Returns the count of logical parts. */
  public int count() {
    return part_arr.size();
  }
}
