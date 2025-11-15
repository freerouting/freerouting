package app.freerouting.settings;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.ReflectionUtil;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Random;

public class RouterSettings implements Serializable
{
  @SerializedName("enabled")
  public transient boolean enabled = true;
  @SerializedName("algorithm")
  public String algorithm = "freerouting-router";
  @SerializedName("job_timeout")
  public String jobTimeoutString = "12:00:00";
  @SerializedName("max_passes")
  public int maxPasses = 9999;
  public transient boolean[] isLayerActive;
  public transient boolean[] isPreferredDirectionHorizontalOnLayer;
  public transient boolean save_intermediate_stages = false;
  @SerializedName("ignore_net_classes")
  public transient String[] ignoreNetClasses = new String[0];
  /**
   * The accuracy of the pull tight algorithm.
   */
  @SerializedName("trace_pull_tight_accuracy")
  public int trace_pull_tight_accuracy = 500;
  @SerializedName("allowed_via_types")
  public boolean vias_allowed = true;
  /**
   * If true, the trace width at static pins smaller the trace width will be lowered
   * automatically to the pin with, if necessary.
   */
  @SerializedName("automatic_neckdown")
  public boolean automatic_neckdown = true;
  @SerializedName("fanout")
  public RouterFanoutSettings fanout = new RouterFanoutSettings();
  @SerializedName("optimizer")
  public RouterOptimizerSettings optimizer = new RouterOptimizerSettings();
  @SerializedName("scoring")
  public RouterScoringSettings scoring = new RouterScoringSettings();
  @SerializedName("max_threads")
  public int maxThreads = Math.max(1, Runtime
      .getRuntime()
      .availableProcessors() - 1);
  @SerializedName("random_seed")
  public transient Long random_seed = new Random().nextLong();
  // The starting and current pass number.
  private transient int start_pass_no = 1;
  // The stopping pass number.
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
    setLayerCount(p_layer_count);
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
      scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
      scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
      if (curr_preferred_direction_is_horizontal)
      {
        scoring.undesiredDirectionTraceCost[i] += horizontal_add_costs_against_preferred_dir;
      }
      else
      {
        scoring.undesiredDirectionTraceCost[i] += vertical_add_costs_against_preferred_dir;
      }
    }
    int signal_layer_count = p_board.layer_structure.signal_layer_count();
    if (signal_layer_count > 2)
    {
      double outer_add_costs = 0.2 * signal_layer_count;
      // increase costs on the outer layers.
      scoring.preferredDirectionTraceCost[0] += outer_add_costs;
      scoring.preferredDirectionTraceCost[layer_count - 1] += outer_add_costs;
      scoring.undesiredDirectionTraceCost[0] += outer_add_costs;
      scoring.undesiredDirectionTraceCost[layer_count - 1] += outer_add_costs;
    }
  }

  /**
   * Set the layer count and initialize the layer specific settings.
   */
  public void setLayerCount(int layerCount)
  {
    isLayerActive = new boolean[layerCount];
    isPreferredDirectionHorizontalOnLayer = new boolean[layerCount];
    scoring.preferredDirectionTraceCost = new double[layerCount];
    scoring.undesiredDirectionTraceCost = new double[layerCount];

    for (int i = 0; i < layerCount; ++i)
    {
      isLayerActive[i] = true;
      isPreferredDirectionHorizontalOnLayer[i] = (i % 2 == 1);
      scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
      scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
    }
  }

  /**
   * Copy constructor
   */
  public RouterSettings clone()
  {
    RouterSettings result = new RouterSettings(this.isLayerActive.length);
    result.algorithm = this.algorithm;
    result.jobTimeoutString = this.jobTimeoutString;
    result.isLayerActive = this.isLayerActive.clone();
    result.isPreferredDirectionHorizontalOnLayer = this.isPreferredDirectionHorizontalOnLayer.clone();
    result.scoring.preferredDirectionTraceCost = this.scoring.preferredDirectionTraceCost.clone();
    result.scoring.undesiredDirectionTraceCost = this.scoring.undesiredDirectionTraceCost.clone();
    result.scoring.defaultPreferredDirectionTraceCost = this.scoring.defaultPreferredDirectionTraceCost;
    result.scoring.defaultUndesiredDirectionTraceCost = this.scoring.defaultUndesiredDirectionTraceCost;
    result.maxPasses = this.maxPasses;
    result.optimizer.maxThreads = this.optimizer.maxThreads;
    result.optimizer.optimizationImprovementThreshold = this.optimizer.optimizationImprovementThreshold;
    result.optimizer.boardUpdateStrategy = this.optimizer.boardUpdateStrategy;
    result.optimizer.hybridRatio = this.optimizer.hybridRatio;
    result.optimizer.itemSelectionStrategy = this.optimizer.itemSelectionStrategy;
    result.ignoreNetClasses = this.ignoreNetClasses.clone();
    result.trace_pull_tight_accuracy = this.trace_pull_tight_accuracy;
    System.arraycopy(this.isLayerActive, 0, result.isLayerActive, 0, isLayerActive.length);
    System.arraycopy(this.isPreferredDirectionHorizontalOnLayer, 0, result.isPreferredDirectionHorizontalOnLayer, 0, isPreferredDirectionHorizontalOnLayer.length);
    System.arraycopy(this.scoring.preferredDirectionTraceCost, 0, result.scoring.preferredDirectionTraceCost, 0, scoring.preferredDirectionTraceCost.length);
    System.arraycopy(this.scoring.undesiredDirectionTraceCost, 0, result.scoring.undesiredDirectionTraceCost, 0, scoring.undesiredDirectionTraceCost.length);
    result.fanout.enabled = this.fanout.enabled;
    result.enabled = this.enabled;
    result.optimizer.enabled = this.optimizer.enabled;
    result.vias_allowed = this.vias_allowed;
    result.automatic_neckdown = this.automatic_neckdown;
    result.scoring.via_costs = this.scoring.via_costs;
    result.scoring.plane_via_costs = this.scoring.plane_via_costs;
    result.scoring.start_ripup_costs = this.scoring.start_ripup_costs;
    result.random_seed = this.random_seed;
    result.start_pass_no = this.start_pass_no;
    result.stop_pass_no = this.stop_pass_no;
    return result;
  }

  public int get_start_ripup_costs()
  {
    return scoring.start_ripup_costs;
  }

  public void set_start_ripup_costs(int p_value)
  {
    scoring.start_ripup_costs = Math.max(p_value, 1);
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
    return fanout.enabled;
  }

  public void setRunFanout(boolean p_value)
  {
    fanout.enabled = p_value;
  }

  public boolean getRunRouter()
  {
    return enabled;
  }

  public void setRunRouter(boolean p_value)
  {
    enabled = p_value;
  }

  public boolean getRunOptimizer()
  {
    return optimizer.enabled;
  }

  public void setRunOptimizer(boolean p_value)
  {
    optimizer.enabled = p_value;
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
    return scoring.via_costs;
  }

  public void set_via_costs(int p_value)
  {
    scoring.via_costs = Math.max(p_value, 1);
  }

  public int get_plane_via_costs()
  {
    return scoring.plane_via_costs;
  }

  public void set_plane_via_costs(int p_value)
  {
    scoring.plane_via_costs = Math.max(p_value, 1);
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
    scoring.preferredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
  }

  public double get_preferred_direction_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return scoring.preferredDirectionTraceCost[p_layer];
  }

  public double get_against_preferred_direction_trace_costs(int p_layer)
  {
    if (p_layer < 0 || p_layer >= isLayerActive.length)
    {
      FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return scoring.undesiredDirectionTraceCost[p_layer];
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
      result = scoring.preferredDirectionTraceCost[p_layer];
    }
    else
    {
      result = scoring.undesiredDirectionTraceCost[p_layer];
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
    scoring.undesiredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
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
      result = scoring.undesiredDirectionTraceCost[p_layer];
    }
    else
    {
      result = scoring.preferredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public AutorouteControl.ExpansionCostFactor[] get_trace_cost_arr()
  {
    AutorouteControl.ExpansionCostFactor[] result = new AutorouteControl.ExpansionCostFactor[scoring.preferredDirectionTraceCost.length];
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

  /**
   * Apply the new values from the given settings to this settings object.
   *
   * @param settings The settings to copy the values from.
   * @return The number of fields that were changed.
   */
  public int applyNewValuesFrom(RouterSettings settings)
  {
    return ReflectionUtil.copyFields(settings, this);
  }
}