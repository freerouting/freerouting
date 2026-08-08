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
  public LogicalPart add(String pName, LogicalPart.PartPin[] pPartPinArr) {
    Arrays.sort(pPartPinArr);
    LogicalPart newPart = new LogicalPart(pName, partArr.size() + 1, pPartPinArr);
    partArr.add(newPart);
    return newPart;
  }

  /** Returns the logical part with the input name or null, if no such package exists. */
  public LogicalPart get(String pName) {
    for (LogicalPart currPart : this.partArr) {
      if (currPart != null && currPart.name.equalsIgnoreCase(pName)) {
        return currPart;
      }
    }
    return null;
  }

  /** Returns the logical part with index p_part_no. Part numbers are from 1 to part count. */
  public LogicalPart get(int pPartNo) {
    LogicalPart result = partArr.elementAt(pPartNo - 1);
    if (result != null && result.no != pPartNo) {
      FRLogger.warn("LogicalParts.get: inconsistent part number");
    }
    return result;
  }

  /** Returns the count of logical parts. */
  public int count() {
    return partArr.size();
  }
}
