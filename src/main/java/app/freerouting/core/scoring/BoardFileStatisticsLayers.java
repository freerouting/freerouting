package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class BoardFileStatisticsLayers implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
  @SerializedName("signal_count")
  public Integer signalCount = null;
}