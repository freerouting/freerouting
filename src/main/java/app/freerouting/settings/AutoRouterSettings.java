package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;

public class AutoRouterSettings
{
  public int max_passes = 100;
  public int num_threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  public BoardUpdateStrategy board_update_strategy = BoardUpdateStrategy.GREEDY;
  public String hybrid_ratio = "1:1";
  public ItemSelectionStrategy item_selection_strategy = ItemSelectionStrategy.PRIORITIZED;
  public float optimization_improvement_threshold = 0.01f;
  public transient String[] ignore_net_classes_by_autorouter = new String[0];

}
