package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Statistics of the clearance violations of a board.
 */
public class BoardFileStatisticsClearanceViolations implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
}