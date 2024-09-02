package app.freerouting.settings;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class RouterSettings implements Serializable
{
  public transient boolean[] isLayerActive;
  public transient boolean[] isPreferredDirectionHorizontalOnLayer;
  public transient double[] preferredDirectionTraceCost;
  public transient double[] undesiredDirectionTraceCost;
  @SerializedName("default_preferred_direction_trace_cost")
  public double defaultPreferredDirectionTraceCost = 1;
  @SerializedName("default_undesired_direction_trace_cost")
  public double defaultUndesiredDirectionTraceCost = 1;
  @SerializedName("max_passes")
  public int maxPasses = 100;
  @SerializedName("fanout_max_passes")
  public int maxFanoutPasses = 20;
  @SerializedName("max_threads")
  public int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  @SerializedName("improvement_threshold")
  public float optimizationImprovementThreshold = 0.01f;
  public transient boolean save_intermediate_stages = false;
  public transient BoardUpdateStrategy boardUpdateStrategy = BoardUpdateStrategy.GREEDY;
  public transient String hybridRatio = "1:1";
  public transient ItemSelectionStrategy itemSelectionStrategy = ItemSelectionStrategy.PRIORITIZED;
  @SerializedName("ignore_net_classes")
  public transient String[] ignoreNetClasses = new String[0];
  /**
   * The accuracy of the pull tight algorithm.
   */
  @SerializedName("trace_pull_tight_accuracy")
  public int trace_pull_tight_accuracy = 500;
  @SerializedName("allowed_via_types")
  public boolean vias_allowed = true;
  @SerializedName("via_costs")
  public int via_costs = 50;
  @SerializedName("plane_via_costs")
  public int plane_via_costs = 5;
  @SerializedName("start_ripup_costs")
  public int start_ripup_costs = 100;
  /**
   * If true, the trace width at static pins smaller the trace width will be lowered
   * automatically to the pin with, if necessary.
   */
  @SerializedName("automatic_neckdown")
  public boolean automatic_neckdown = true;
  private transient boolean runFanout = false;
  private transient boolean runRouter = true;
  private transient boolean runOptimizer = false;
  private transient int start_pass_no = 1;
  private transient int stop_pass_no = 999;

  /**
   * We need a parameterless constructor for the serialization.
   */
  public RouterSettings()
  {
    this(0);
  }

  /**
   * Creates a new instance of AutorouteSettings with default values and @p_layer_count layers.
   */
  public RouterSettings(int p_layer_count)
  {
    isLayerActive = new boolean[p_layer_count];
    isPreferredDirectionHorizontalOnLayer = new boolean[p_layer_count];
    preferredDirectionTraceCost = new double[p_layer_count];
    undesiredDirectionTraceCost = new double[p_layer_count];
  }

  /**
   * Creates a new instance of AutorouteSettings
   */
  public RouterSettings(RoutingBoard p_board)
  {
    this(p_board.get_layer_count());

    double horizontal_width = p_board.bounding_box.width();
    double vertical_width = p_board.bounding_box.height();

    int layer_count = p_board.get_layer_count();

    // additional costs against  preferred direction with 1 digit behind the decimal point.
    double horizontal_add_costs_against_preferred_dir = 0.1 * Math.round(10 * horizontal_width / vertical_width);

    double vertical_add_costs_against_preferred_dir = 0.1 * Math.round(10 * vertical_width / horizontal_width);

    // make more horizontal preferred direction, if the board is horizontal.

    boolean curr_preferred_direction_is_horizontal = horizontal_width < vertical_width;
    for (int i = 0; i < layer_count; ++i)
    {
      isLayerActive[i] = p_board.layer_structure.arr[i].is_signal;
      if (p_board.layer_structure.arr[i].is_signal)
      {
        curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal;
      }
      isPreferredDirectionHorizontalOnLayer[i] = curr_preferred_direction_is_horizontal;
      preferredDirectionTraceCost[i] = defaultPreferredDirectionTraceCost;
      undesiredDirectionTraceCost[i] = defaultUndesiredDirectionTraceCost;
      if (curr_preferred_direction_is_horizontal)
      {
        undesiredDirectionTraceCost[i] += horizontal_add_costs_against_preferred_dir;
      }
      else
      {
        undesiredDirectionTraceCost[i] += vertical_add_costs_against_preferred_dir;
      }
    }
    int signal_layer_count = p_board.layer_structure.signal_layer_count();
    if (signal_layer_count > 2)
    {
      double outer_add_costs = 0.2 * signal_layer_count;
      // increase costs on the outer layers.
      preferredDirectionTraceCost[0] += outer_add_costs;
      preferredDirectionTraceCost[layer_count - 1] += outer_add_costs;
      undesiredDirectionTraceCost[0] += outer_add_costs;
      undesiredDirectionTraceCost[layer_count - 1] += outer_add_costs;
    }
  }

  /**
   * Copy constructor
   */
  public RouterSettings clone()
  {
    RouterSettings result = new RouterSettings(this.isLayerActive.length);
    result.isLayerActive = this.isLayerActive.clone();
    result.isPreferredDirectionHorizontalOnLayer = this.isPreferredDirectionHorizontalOnLayer.clone();
    result.preferredDirectionTraceCost = this.preferredDirectionTraceCost.clone();
    result.undesiredDirectionTraceCost = this.undesiredDirectionTraceCost.clone();
    result.defaultPreferredDirectionTraceCost = this.defaultPreferredDirectionTraceCost;
    result.defaultUndesiredDirectionTraceCost = this.defaultUndesiredDirectionTraceCost;
    result.maxPasses = this.maxPasses;
    result.maxThreads = this.maxThreads;
    result.optimizationImprovementThreshold = this.optimizationImprovementThreshold;
    result.boardUpdateStrategy = this.boardUpdateStrategy;
    result.hybridRatio = this.hybridRatio;
    result.itemSelectionStrategy = this.itemSelectionStrategy;
    result.ignoreNetClasses = this.ignoreNetClasses.clone();
    result.trace_pull_tight_accuracy = this.trace_pull_tight_accuracy;
    System.arraycopy(this.isLayerActive, 0, result.isLayerActive, 0, isLayerActive.length);
    System.arraycopy(this.isPreferredDirectionHorizontalOnLayer, 0, result.isPreferredDirectionHorizontalOnLayer, 0, isPreferredDirectionHorizontalOnLayer.length);
    System.arraycopy(this.preferredDirectionTraceCost, 0, result.preferredDirectionTraceCost, 0, preferredDirectionTraceCost.length);
    System.arraycopy(this.undesiredDirectionTraceCost, 0, result.undesiredDirectionTraceCost, 0, undesiredDirectionTraceCost.length);
    result.runFanout = this.runFanout;
    result.runRouter = this.runRouter;
    result.runOptimizer = this.runOptimizer;
    result.vias_allowed = this.vias_allowed;
    result.automatic_neckdown = this.automatic_neckdown;
    result.via_costs = this.via_costs;
    result.plane_via_costs = this.plane_via_costs;
    result.start_ripup_costs = this.start_ripup_costs;
    result.start_pass_no = this.start_pass_no;
    result.stop_pass_no = this.stop_pass_no;
    return result;
  }

  public int get_start_ripup_costs()
  {
    return start_ripup_costs;
  }

  public void set_start_ripup_costs(int p_value)
  {
    start_ripup_costs = Math.max(p_value, 1);
  }

  public int get_start_pass_no()
  {
    return start_pass_no;
  }

  public void set_start_pass_no(int p_value)
  {
    start_pass_no = Math.max(p_value, 1);
    start_pass_no = Math.min(start_pass_no, 99999);
  }

  public int get_stop_pass_no()
  {
    return stop_pass_no;
  }

  public void set_stop_pass_no(int p_value)
  {
    stop_pass_no = Math.max(p_value, start_pass_no);
    stop_pass_no = Math.min(stop_pass_no, 99999);
  }

  public void increment_pass_no()
  {
    ++start_pass_no;
  }

  public boolean getRunFanout()
  {
    return runFanout;
  }

  public void setRunFanout(boolean p_value)
  {
    runFanout = p_value;
  }

  public boolean getRunRouter()
  {
    return runRouter;
  }

  public void setRunRouter(boolean p_value)
  {
    runRouter = p_value;
  }

  public boolean getRunOptimizer()
  {
    return runOptimizer;
  }

  public void setRunOptimizer(boolean p_value)
  {
    runOptimizer = p_value;
  }

  public boolean get_vias_allowed()
  {
    return vias_allowed;
  }

  public void set_vias_allowed(boolean p_value)
  {
    vias_allowed = p_value;
  }

  public int get_via_costs()
  {
    return via_costs;
  }

  public void set_via_costs(int p_value)
  {
    via_costs = Math.max(p_value, 1);
  }

  public int get_plane_via_costs()
  {
    return plane_via_costs;
  }

  public void set_plane_via_costs(int p_value)
  {
    plane_via_costs = Math.max(p_value, 1);
  }

  public void set_layer_active(int p_layer, boolean p_value)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.set_layer_active: p_layer out of range");
      return;
    }
    isLayerActive[p_layer] = p_value;
  }

  public boolean get_layer_active(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_layer_active: p_layer out of range");
      return false;
    }
    return isLayerActive[p_layer];
  }

  public void set_preferred_direction_is_horizontal(int p_layer, boolean p_value)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_is_horizontal: p_layer out of range");
      return;
    }
    isPreferredDirectionHorizontalOnLayer[p_layer] = p_value;
  }

  public boolean get_preferred_direction_is_horizontal(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_is_horizontal: p_layer out of range");
      return false;
    }
    return isPreferredDirectionHorizontalOnLayer[p_layer];
  }

  public void set_preferred_direction_trace_costs(int p_layer, double p_value)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    preferredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
  }

  public double get_preferred_direction_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return preferredDirectionTraceCost[p_layer];
  }

  public double get_against_preferred_direction_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return undesiredDirectionTraceCost[p_layer];
  }

  public double get_horizontal_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (isPreferredDirectionHorizontalOnLayer[p_layer])
    {
      result = preferredDirectionTraceCost[p_layer];
    }
    else
    {
      result = undesiredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public void set_against_preferred_direction_trace_costs(int p_layer, double p_value)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.set_against_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    undesiredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
  }

  public double get_vertical_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (isPreferredDirectionHorizontalOnLayer[p_layer])
    {
      result = undesiredDirectionTraceCost[p_layer];
    }
    else
    {
      result = preferredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public AutorouteControl.ExpansionCostFactor[] get_trace_cost_arr()
  {
    AutorouteControl.ExpansionCostFactor[] result = new AutorouteControl.ExpansionCostFactor[preferredDirectionTraceCost.length];
    for (int i = 0; i < result.length; ++i)
    {
      result[i] = new AutorouteControl.ExpansionCostFactor(get_horizontal_trace_costs(i), get_vertical_trace_costs(i));
    }
    return result;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered
   * automatically to the pin with, if necessary.
   */
  public boolean get_automatic_neckdown()
  {
    return this.automatic_neckdown;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered
   * automatically to the pin with, if necessary.
   */
  public void set_automatic_neckdown(boolean p_value)
  {
    this.automatic_neckdown = p_value;
  }
}