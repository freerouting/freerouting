package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/** Settings for debugging the routing engine. */
public class DebugSettings implements Serializable {

  @SerializedName("enable_detailed_logging")
  public boolean enableDetailedLogging;

  @SerializedName("single_step_execution")
  public boolean singleStepExecution;

  @SerializedName("trace_insertion_delay")
  public int traceInsertionDelay;

  @SerializedName("filter_by_net")
  public Set<String> filterByNet = new HashSet<>();

  @SerializedName("operation_filters")
  public String[] operationFilters =
      new String[] {
        "insert_trace_segment",
        "remove_trace_segment",
        "insert_trace_failure",
        "remove_tail",
        "insert_trace",
        "remove_trace",
        "insert_via",
        "remove_via"
      };

  /** Creates debugging settings with the default operation filters. */
  public DebugSettings() {}

  /**
   * Checks if the given net number or name is permitted by the filter. If the filter is empty, all
   * nets are permitted.
   */
  public boolean isNetPermitted(int netNumber, String netName) {
    if (filterByNet.isEmpty()) {
      return true;
    }
    String netNoStr = String.valueOf(netNumber);
    // Check "1", "Net #1", "Net#1"
    return filterByNet.contains(netNoStr)
        || filterByNet.contains("Net #" + netNumber)
        || filterByNet.contains("Net#" + netNumber)
        || (netName != null && filterByNet.contains(netName.toLowerCase()));
    // Note: external input should be lower-cased before adding to the set if we
    // want case insensitivity
  }
}
