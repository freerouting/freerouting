package app.freerouting.core;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Router-related counters that reflect the progress of the router.
 */
public class RouterCounters implements Serializable
{
  // The number of items on the board that are in the queue to be routed in the current pass
  @SerializedName("pass_count")
  public Integer passCount = null;
  // The number of items on the board that are in the queue to be routed in the current pass
  @SerializedName("queued_to_be_routed_count")
  public Integer queuedToBeRoutedCount = null;
  // The number of items on the board that got successfully routed in this pass
  @SerializedName("routed_count")
  public Integer routedCount = null;
  // The number of items on the board that were skipped in this pass
  @SerializedName("skipped_count")
  public Integer skippedCount = null;
  // The number of items on the board that were ripped in this pass
  @SerializedName("ripped_count")
  public Integer rippedCount = null;
  // The number of items on the board that were failed to be routed in this pass
  @SerializedName("failed_to_be_routed_count")
  public Integer failedToBeRoutedCount = null;
  // The number of items on the board that are still in the ratsnest (so they are not yet routed)
  @SerializedName("incomplete_count")
  public Integer incompleteCount = null;
}