package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the router-related counters of a board.
 */
public class BoardFileStatisticsRouterCounters implements Serializable
{
  // The number of items in the board that are not routed
  @SerializedName("total_count")
  public Integer unrouted_item_count = null;
  // TODO: is this the same as the unrouted item count?
  @SerializedName("incomplete_item_count")
  public Integer incompleteItemCount;
  // The number of items in the board that are routed
  @SerializedName("routed_item_count")
  public Integer routed_item_count;
  // The number of items in the board that were ripped
  @SerializedName("ripped_item_count")
  public Integer ripped_item_count;
  // The number of items in the board that were not found
  @SerializedName("not_found_item_count")
  public Integer not_found_item_count;
}