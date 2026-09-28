package app.freerouting.core.scoring;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/** Board-only complexity and difficulty values used by V2 scoring. */
public class BoardStatisticsDifficulty implements Serializable {

  @SerializedName("pin_count")
  public Integer pinCount;

  @SerializedName("signal_layer_count")
  public Integer signalLayerCount;

  @SerializedName("complexity_c")
  public Integer complexityC;

  @SerializedName("difficulty_d")
  public Float difficultyD;

  @SerializedName("board_area_cm2")
  public Float boardAreaCm2;
}
