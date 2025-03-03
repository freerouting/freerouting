package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class BoardFileStatisticsComponents implements Serializable
{
  @SerializedName("total_count")
  public Integer totalCount = null;
}