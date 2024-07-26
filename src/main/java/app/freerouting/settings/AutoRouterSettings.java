package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import com.google.gson.annotations.SerializedName;

public class AutoRouterSettings
{
  @SerializedName("max_passes")
  public int max_passes = 100;
  @SerializedName("max_threads")
  public int num_threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  public transient BoardUpdateStrategy board_update_strategy = BoardUpdateStrategy.GREEDY;
  public transient String hybrid_ratio = "1:1";
  public ItemSelectionStrategy item_selection_strategy = ItemSelectionStrategy.PRIORITIZED;
  @SerializedName("improvement_threshold")
  public float optimization_improvement_threshold = 0.01f;
  @SerializedName("ignore_net_classes")
  public transient String[] ignore_net_classes_by_autorouter = new String[0];

}