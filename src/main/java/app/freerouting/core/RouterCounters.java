package app.freerouting.core;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Router-related counters that reflect the progress of the router.
 */
public class RouterCounters implements Serializable {

  // The number of items on the board that are in the queue to be routed in the current pass
  @SerializedName("pass_count")
  public Integer passCount;
  // The number of items on the board that are in the queue to be routed in the current pass
  @SerializedName("queued_to_be_routed_count")
  public Integer queuedToBeRoutedCount;
  // The number of items on the board that got successfully routed in this pass
  @SerializedName("routed_count")
  public Integer routedCount;
  // The number of items on the board that were skipped in this pass
  @SerializedName("skipped_count")
  public Integer skippedCount;
  // The number of items on the board that were ripped in this pass
  @SerializedName("ripped_count")
  public Integer rippedCount;
  // The number of items on the board that were failed to be routed in this pass
  @SerializedName("failed_to_be_routed_count")
  public Integer failedToBeRoutedCount;
  // The number of items on the board that are still in the ratsnest (so they are not yet routed)
  @SerializedName("incomplete_count")
  public Integer incompleteCount;
}