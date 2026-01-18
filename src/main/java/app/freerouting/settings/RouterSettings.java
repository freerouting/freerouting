package app.freerouting.settings;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.ReflectionUtil;
import com.google.gson.annotations.SerializedName;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

public class RouterSettings implements Serializable, Cloneable {
  // PropertyChangeSupport for bidirectional binding with GUI
  private transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  @SerializedName("enabled")
  public Boolean enabled;
  // Valid algorithm values
  public static final String ALGORITHM_CURRENT = "freerouting-router";
  public static final String ALGORITHM_V19 = "freerouting-router-v19";

  @SerializedName("algorithm")
  public String algorithm;
  @SerializedName("job_timeout")
  public String jobTimeoutString;
  @SerializedName("max_passes")
  public Integer maxPasses;
  public transient boolean[] isLayerActive;
  public transient boolean[] isPreferredDirectionHorizontalOnLayer;
  public transient Boolean save_intermediate_stages;
  @SerializedName("ignore_net_classes")
  public transient String[] ignoreNetClasses;
  /**
   * The accuracy of the pull tight algorithm.
   */
  @SerializedName("trace_pull_tight_accuracy")
  public Integer trace_pull_tight_accuracy;
  @SerializedName("allowed_via_types")
  public Boolean vias_allowed;
  /**
   * If true, the trace width at static pins smaller the trace width will be
   * lowered automatically to the pin with, if necessary.
   */
  @SerializedName("automatic_neckdown")
  public Boolean automatic_neckdown;

  @SerializedName("optimizer")
  public RouterOptimizerSettings optimizer;
  @SerializedName("scoring")
  public RouterScoringSettings scoring;
  @SerializedName("max_threads")
  public Integer maxThreads;

  /**
   * We need a parameterless constructor for the serialization.
   * Initializes all fields with default values.
   */
  public RouterSettings() {
    this(0);
  }

  /**
   * Creates a new instance of AutorouteSettings with default values
   * and @p_layer_count layers.
   */
  public RouterSettings(int p_layer_count) {
    // Initialize with default values
    this.enabled = true;
    this.algorithm = ALGORITHM_CURRENT;
    this.jobTimeoutString = "12:00:00";
    this.maxPasses = 9999;
    this.trace_pull_tight_accuracy = 500;
    this.vias_allowed = true;
    this.automatic_neckdown = true;
    this.save_intermediate_stages = false;
    this.ignoreNetClasses = new String[0];
    this.maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.optimizer = new RouterOptimizerSettings();
    this.scoring = new RouterScoringSettings();

    setLayerCount(p_layer_count);
  }

  // PropertyChangeListener support for bidirectional binding
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (pcs == null) {
      pcs = new PropertyChangeSupport(this);
    }
    pcs.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (pcs != null) {
      pcs.removePropertyChangeListener(listener);
    }
  }

  // Setter methods that fire property change events for bidirectional binding
  public void setMaxPasses(Integer value) {
    Integer oldValue = this.maxPasses;
    this.maxPasses = value;
    if (pcs != null) {
      pcs.firePropertyChange("maxPasses", oldValue, value);
    }
  }

  public void setMaxThreads(Integer value) {
    Integer oldValue = this.maxThreads;
    this.maxThreads = value;
    if (pcs != null) {
      pcs.firePropertyChange("maxThreads", oldValue, value);
    }
    // Also update optimizer's maxThreads to keep them in sync
    if (this.optimizer != null) {
      this.optimizer.maxThreads = value;
    }
  }

  public void setJobTimeoutString(String value) {
    String oldValue = this.jobTimeoutString;
    this.jobTimeoutString = value;
    if (pcs != null) {
      pcs.firePropertyChange("jobTimeoutString", oldValue, value);
    }
  }

  public void setEnabled(Boolean value) {
    Boolean oldValue = this.enabled;
    this.enabled = value;
    if (pcs != null) {
      pcs.firePropertyChange("enabled", oldValue, value);
    }
  }

  public void setViasAllowed(Boolean value) {
    Boolean oldValue = this.vias_allowed;
    this.vias_allowed = value;
    if (pcs != null) {
      pcs.firePropertyChange("vias_allowed", oldValue, value);
    }
  }

  public void setAlgorithm(String value) {
    String oldValue = this.algorithm;
    this.algorithm = value;
    if (pcs != null) {
      pcs.firePropertyChange("algorithm", oldValue, value);
    }
  }

  public void setOptimizerEnabled(Boolean value) {
    Boolean oldValue = this.optimizer != null ? this.optimizer.enabled : null;
    if (this.optimizer != null) {
      this.optimizer.enabled = value;
    }
    if (pcs != null) {
      pcs.firePropertyChange("optimizer.enabled", oldValue, value);
    }
  }

  /**
   * Creates a new instance of AutorouteSettings
   */
  public RouterSettings(RoutingBoard p_board) {
    this(p_board.get_layer_count());
    applyBoardSpecificOptimizations(p_board);
  }

  /**
   * Apply board-specific optimizations to RouterSettings based on board geometry
   * and layer structure.
   * This calculates layer costs based on board aspect ratio and adds penalties
   * for outer layers.
   * Should be called after loading a board to optimize routing performance.
   * 
   * @param p_board The routing board to optimize settings for
   */
  public void applyBoardSpecificOptimizations(RoutingBoard p_board) {
    double horizontal_width = p_board.bounding_box.width();
    double vertical_width = p_board.bounding_box.height();

    int layer_count = p_board.get_layer_count();

    // additional costs against preferred direction with 1 digit behind the decimal
    // point.
    double horizontal_add_costs_against_preferred_dir = 0.1 * Math.round(10 * horizontal_width / vertical_width);

    double vertical_add_costs_against_preferred_dir = 0.1 * Math.round(10 * vertical_width / horizontal_width);

    // make more horizontal preferred direction, if the board is horizontal.

    boolean curr_preferred_direction_is_horizontal = horizontal_width < vertical_width;
    for (int i = 0; i < layer_count; i++) {
      isLayerActive[i] = p_board.layer_structure.arr[i].is_signal;
      if (p_board.layer_structure.arr[i].is_signal) {
        curr_preferred_direction_is_horizontal = !curr_preferred_direction_is_horizontal;
      }
      isPreferredDirectionHorizontalOnLayer[i] = curr_preferred_direction_is_horizontal;
      scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
      scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
      if (curr_preferred_direction_is_horizontal) {
        scoring.undesiredDirectionTraceCost[i] += horizontal_add_costs_against_preferred_dir;
      } else {
        scoring.undesiredDirectionTraceCost[i] += vertical_add_costs_against_preferred_dir;
      }
    }
    int signal_layer_count = p_board.layer_structure.signal_layer_count();
    if (signal_layer_count > 2) {
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
  public void setLayerCount(int layerCount) {
    isLayerActive = new boolean[layerCount];
    isPreferredDirectionHorizontalOnLayer = new boolean[layerCount];
    scoring.preferredDirectionTraceCost = new double[layerCount];
    scoring.undesiredDirectionTraceCost = new double[layerCount];

    for (int i = 0; i < layerCount; i++) {
      isLayerActive[i] = true;
      isPreferredDirectionHorizontalOnLayer[i] = i % 2 == 1;
      scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
      scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
    }
  }

  /**
   * Copy constructor
   */
  public RouterSettings clone() {
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
    System.arraycopy(this.isPreferredDirectionHorizontalOnLayer, 0, result.isPreferredDirectionHorizontalOnLayer, 0,
        isPreferredDirectionHorizontalOnLayer.length);
    System.arraycopy(this.scoring.preferredDirectionTraceCost, 0, result.scoring.preferredDirectionTraceCost, 0,
        scoring.preferredDirectionTraceCost.length);
    System.arraycopy(this.scoring.undesiredDirectionTraceCost, 0, result.scoring.undesiredDirectionTraceCost, 0,
        scoring.undesiredDirectionTraceCost.length);

    result.enabled = this.enabled;
    result.optimizer.enabled = this.optimizer.enabled;
    result.vias_allowed = this.vias_allowed;
    result.automatic_neckdown = this.automatic_neckdown;
    result.scoring.via_costs = this.scoring.via_costs;
    result.scoring.plane_via_costs = this.scoring.plane_via_costs;
    result.scoring.start_ripup_costs = this.scoring.start_ripup_costs;
    return result;
  }

  public int get_start_ripup_costs() {
    return scoring.start_ripup_costs;
  }

  public void set_start_ripup_costs(int p_value) {
    scoring.start_ripup_costs = Math.max(p_value, 1);
  }

  public boolean getRunRouter() {
    return enabled != null ? enabled : true;
  }

  public void setRunRouter(boolean p_value) {
    enabled = p_value;
  }

  public boolean getRunOptimizer() {
    return optimizer != null && optimizer.enabled != null ? optimizer.enabled : false;
  }

  public void setRunOptimizer(boolean p_value) {
    if (optimizer == null) {
      optimizer = new RouterOptimizerSettings();
    }
    optimizer.enabled = p_value;
  }

  public boolean get_vias_allowed() {
    return vias_allowed != null ? vias_allowed : true;
  }

  public void set_vias_allowed(boolean p_value) {
    vias_allowed = p_value;
  }

  public int get_via_costs() {
    return scoring.via_costs;
  }

  public void set_via_costs(int p_value) {
    scoring.via_costs = Math.max(p_value, 1);
  }

  public int get_plane_via_costs() {
    return scoring.plane_via_costs;
  }

  public void set_plane_via_costs(int p_value) {
    scoring.plane_via_costs = Math.max(p_value, 1);
  }

  public void set_layer_active(int p_layer, boolean p_value) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.set_layer_active: p_layer=" + p_layer + " out of range [0.."
          + (isLayerActive.length - 1) + "]");
      return;
    }
    isLayerActive[p_layer] = p_value;
  }

  public boolean get_layer_active(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_layer_active: p_layer=" + p_layer + " out of range [0.."
          + (isLayerActive.length - 1) + "]");
      return false;
    }
    return isLayerActive[p_layer];
  }

  public void set_preferred_direction_is_horizontal(int p_layer, boolean p_value) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_is_horizontal: p_layer=" + p_layer + " out of range [0.."
          + (isLayerActive.length - 1) + "]");
      return;
    }
    isPreferredDirectionHorizontalOnLayer[p_layer] = p_value;
  }

  public boolean get_preferred_direction_is_horizontal(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_is_horizontal: p_layer=" + p_layer + " out of range [0.."
          + (isLayerActive.length - 1) + "]");
      return false;
    }
    return isPreferredDirectionHorizontalOnLayer[p_layer];
  }

  public void set_preferred_direction_trace_costs(int p_layer, double p_value) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    scoring.preferredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
  }

  public double get_preferred_direction_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return scoring.preferredDirectionTraceCost[p_layer];
  }

  public double get_against_preferred_direction_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    return scoring.undesiredDirectionTraceCost[p_layer];
  }

  public double get_horizontal_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (isPreferredDirectionHorizontalOnLayer[p_layer]) {
      result = scoring.preferredDirectionTraceCost[p_layer];
    } else {
      result = scoring.undesiredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public void set_against_preferred_direction_trace_costs(int p_layer, double p_value) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.set_against_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    scoring.undesiredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
  }

  public double get_vertical_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= isLayerActive.length) {
      FRLogger.warn("AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (isPreferredDirectionHorizontalOnLayer[p_layer]) {
      result = scoring.undesiredDirectionTraceCost[p_layer];
    } else {
      result = scoring.preferredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public AutorouteControl.ExpansionCostFactor[] get_trace_cost_arr() {
    AutorouteControl.ExpansionCostFactor[] result = new AutorouteControl.ExpansionCostFactor[scoring.preferredDirectionTraceCost.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new AutorouteControl.ExpansionCostFactor(get_horizontal_trace_costs(i), get_vertical_trace_costs(i));
    }
    return result;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be
   * lowered automatically to the pin with, if necessary.
   */
  public boolean get_automatic_neckdown() {
    return this.automatic_neckdown;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be
   * lowered automatically to the pin with, if necessary.
   */
  public void set_automatic_neckdown(boolean p_value) {
    this.automatic_neckdown = p_value;
  }

  /**
   * Apply the new values from the given settings to this settings object.
   *
   * @param settings The settings to copy the values from.
   * @return The number of fields that were changed.
   */
  public int applyNewValuesFrom(RouterSettings settings) {
    int changedCount = ReflectionUtil.copyFields(settings, this);

    // Fire property change events for key properties to update GUI
    // Note: We fire events even if values didn't change to ensure GUI is in sync
    if (pcs != null) {
      pcs.firePropertyChange("maxPasses", null, this.maxPasses);
      pcs.firePropertyChange("maxThreads", null, this.maxThreads);
      pcs.firePropertyChange("jobTimeoutString", null, this.jobTimeoutString);
      pcs.firePropertyChange("enabled", null, this.enabled);
    }

    return changedCount;
  }
}