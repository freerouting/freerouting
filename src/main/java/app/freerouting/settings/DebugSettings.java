package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Settings for debugging the routing engine.
 */
public class DebugSettings implements Serializable {

    @SerializedName("enable_detailed_logging")
    public boolean enableDetailedLogging = false;

    @SerializedName("single_step_execution")
    public boolean singleStepExecution = false;

    @SerializedName("trace_insertion_delay")
    public int traceInsertionDelay = 0;

    @SerializedName("filter_by_net")
    public Set<String> filterByNet = new HashSet<>();

    @SerializedName("operation_filters")
    public String[] operationFilters = new String[] { "insert_trace_segment", "remove_trace_segment", "insert_trace_failure", "remove_tail" };

    public DebugSettings() {
    }

    /**
     * Checks if the given net number or name is permitted by the filter.
     * If the filter is empty, all nets are permitted.
     */
    public boolean isNetPermitted(int netNo, String netName) {
        if (filterByNet.isEmpty()) {
            return true;
        }
        String netNoStr = String.valueOf(netNo);
        // Check "1", "Net #1", "Net#1"
        return filterByNet.contains(netNoStr) ||
                filterByNet.contains("Net #" + netNo) ||
                filterByNet.contains("Net#" + netNo) ||
                (netName != null && filterByNet.contains(netName.toLowerCase()));
        // Note: external input should be lower-cased before adding to the set if we
        // want case insensitivity
    }
}