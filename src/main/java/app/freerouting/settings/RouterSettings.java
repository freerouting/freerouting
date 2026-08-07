package app.freerouting.settings;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.ReflectionUtil;
import com.google.gson.annotations.SerializedName;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

public class RouterSettings implements Serializable, Cloneable {
  // Valid algorithm values
  public static final String ALGORITHM_CURRENT = "freerouting-router";
  public static final String ALGORITHM_V19 = "freerouting-router-v19";
  public static final double MIN_BEND_COST = 0.0;
  public static final double MAX_BEND_COST = 9.9;

  @SerializedName("enabled")
  public Boolean enabled;

  @SerializedName("algorithm")
  public String algorithm;

  /** Configuration for the SMD-pin fanout pre-pass. */
  @SerializedName("fanout")
  public FanoutSettings fanout;

  @SerializedName("copper_to_edge_clearance_um")
  public Double copperToEdgeClearanceUm;

  @SerializedName("hole_clearance_um")
  public Double holeClearanceUm;

  /**
   * Opt-in width necking: when a connection fails at its net-class trace width, retry it once with
   * all trace half-widths clamped to this width (in micrometers). Intended for fine-pitch regions
   * where the class width physically cannot exit the pads; supply a legal manufacturable width
   * (e.g. the project's densest net class). 0/absent = off.
   */
  @SerializedName("neck_width_um")
  public Double neckWidthUm;

  /**
   * When true, a routed connection whose newly inserted traces/vias carry clearance violations is
   * ripped up again and counted as not routed for that pass, instead of being kept with the
   * violations (v1 limitation: violations between two pre-existing foreign items pushed by a shove
   * are not attributed to the new connection).
   */
  @SerializedName("strict_drc")
  public Boolean strictDrc;

  @SerializedName("job_timeout")
  public String jobTimeoutString;

  @SerializedName("max_passes")
  public Integer maxPasses;

  @SerializedName("max_items")
  public transient Integer maxItems;

  @SerializedName("layers")
  public transient LayerSettings[] layers;

  public transient Boolean saveIntermediateStages = false;

  @SerializedName("ignore_net_classes")
  public transient String[] ignoreNetClasses;

  /** The accuracy of the pull tight algorithm. */
  @SerializedName("tracePullTightAccuracy")
  public Integer tracePullTightAccuracy;

  @SerializedName("allowed_via_types")
  public Boolean viasAllowed;

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered automatically
   * to the pin with, if necessary.
   */
  @SerializedName("automaticNeckdown")
  public Boolean automaticNeckdown;

  @SerializedName("optimizer")
  public OptimizerSettings optimizer;

  @SerializedName("scoring")
  public ScoringSettings scoring;

  @SerializedName("max_threads")
  public Integer maxThreads;

  /**
   * When {@code true}, per-layer trace costs were initialized from board geometry (or set
   * explicitly by the user) and must not be overwritten by subsequent calls to {@link
   * #applyBoardSpecificOptimizations}.
   */
  private transient Boolean boardSpecificTraceCostsApplied;

  // PropertyChangeSupport for bidirectional binding with GUI
  private transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);

  /**
   * We need a parameterless constructor for the serialization. Initializes all fields with default
   * values.
   */
  public RouterSettings() {
    this.optimizer = new OptimizerSettings();
    this.scoring = new ScoringSettings();
    this.fanout = new FanoutSettings();
  }

  /** Creates a new instance of AutorouteSettings */
  public RouterSettings(RoutingBoard p_board) {
    this();
    setLayerCount(p_board.get_layer_count());
    applyBoardSpecificOptimizations(p_board);
  }

  private static int defaultMaxThreads() {
    return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  }

  private static int normalizeMaxThreads(Integer value) {
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    if (value == null) {
      return defaultMaxThreads();
    }
    if (value < 0) {
      return defaultMaxThreads();
    }
    if (value == 0) {
      return availableProcessors;
    }
    return Math.min(value, availableProcessors);
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
    this.maxThreads = normalizeMaxThreads(value);
    if (pcs != null) {
      pcs.firePropertyChange("maxThreads", oldValue, this.maxThreads);
    }
    // Also update optimizer's maxThreads to keep them in sync
    if (this.optimizer != null) {
      this.optimizer.maxThreads = this.maxThreads;
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
    Boolean oldValue = this.viasAllowed;
    this.viasAllowed = value;
    if (pcs != null) {
      pcs.firePropertyChange("viasAllowed", oldValue, value);
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
   * Apply board-specific optimizations to RouterSettings based on board geometry and layer
   * structure. This calculates layer costs based on board aspect ratio and adds penalties for outer
   * layers. Should be called after loading a board to optimize routing performance.
   *
   * @param p_board The routing board to optimize settings for
   */
  public void applyBoardSpecificOptimizationsIfNeeded(RoutingBoard p_board) {
    if (p_board == null) {
      return;
    }
    int boardLayerCount = p_board.get_layer_count();
    if (getLayerCount() != boardLayerCount
        || !Boolean.TRUE.equals(boardSpecificTraceCostsApplied)) {
      applyBoardSpecificOptimizations(p_board);
    }
  }

  /** Returns whether board-specific trace costs have already been computed or explicitly set. */
  public boolean areBoardSpecificTraceCostsApplied() {
    return Boolean.TRUE.equals(boardSpecificTraceCostsApplied);
  }

  public void applyBoardSpecificOptimizations(RoutingBoard p_board) {
    double horizontalWidth = p_board.boundingBox.width();
    double verticalWidth = p_board.boundingBox.height();

    int layerCount = p_board.get_layer_count();

    // Track original values to log changes later
    Boolean[] originalRoutable = new Boolean[layerCount];
    Double[] originalBendCost = new Double[layerCount];
    Boolean[] originalPrefHoriz = new Boolean[layerCount];
    if (layers != null) {
      for (int i = 0; i < Math.min(layers.length, layerCount); i++) {
        if (layers[i] != null) {
          originalRoutable[i] = layers[i].routable;
          originalBendCost[i] = layers[i].bendCost;
          originalPrefHoriz[i] = layers[i].preferredDirectionHorizontal;
        }
      }
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    double[] originalPrefCost =
        scoring.preferredDirectionTraceCost != null
            ? scoring.preferredDirectionTraceCost.clone()
            : null;
    double[] originalUndesiredCost =
        scoring.undesiredDirectionTraceCost != null
            ? scoring.undesiredDirectionTraceCost.clone()
            : null;

    // additional costs against preferred direction with 1 digit behind the decimal
    // point.
    double horizontalAddCostsAgainstPreferredDir =
        0.1 * Math.round(10 * horizontalWidth / verticalWidth);

    double verticalAddCostsAgainstPreferredDir =
        0.1 * Math.round(10 * verticalWidth / horizontalWidth);

    // make more horizontal preferred direction, if the board is horizontal.

    boolean currPreferredDirectionIsHorizontal = horizontalWidth < verticalWidth;

    // initialize the layer specific settings.
    if (layers == null || layers.length != layerCount) {
      boardSpecificTraceCostsApplied = false;
      LayerSettings[] oldLayers = layers;
      layers = new LayerSettings[layerCount];
      for (int i = 0; i < layerCount; i++) {
        if (oldLayers != null && i < oldLayers.length && oldLayers[i] != null) {
          layers[i] = oldLayers[i];
        } else {
          layers[i] = new LayerSettings();
        }
      }
    } else {
      // Ensure no elements inside the layers array are null
      for (int i = 0; i < layerCount; i++) {
        if (layers[i] == null) {
          layers[i] = new LayerSettings();
        }
      }
    }
    if (scoring.preferredDirectionTraceCost == null
        || scoring.preferredDirectionTraceCost.length != layerCount) {
      scoring.preferredDirectionTraceCost = new double[layerCount];
      boardSpecificTraceCostsApplied = false;
    }
    if (scoring.undesiredDirectionTraceCost == null
        || scoring.undesiredDirectionTraceCost.length != layerCount) {
      scoring.undesiredDirectionTraceCost = new double[layerCount];
      boardSpecificTraceCostsApplied = false;
    }

    // Guard against null defaults that can occur when RouterSettings is
    // deserialized without going through DefaultSettings (e.g. from board history).
    if (scoring.defaultPreferredDirectionTraceCost == null) {
      scoring.defaultPreferredDirectionTraceCost = 1.0;
    }
    if (scoring.defaultUndesiredDirectionTraceCost == null) {
      scoring.defaultUndesiredDirectionTraceCost = 1.0;
    }

    boolean initializeTraceCosts = !Boolean.TRUE.equals(boardSpecificTraceCostsApplied);

    for (int i = 0; i < layerCount; i++) {
      if (!p_board.layerStructure.arr[i].isSignal) {
        layers[i].routable = false;
      } else if (layers[i].routable == null) {
        layers[i].routable = true;
      }
      if (layers[i].bendCost == null) {
        layers[i].bendCost =
            scoring != null && scoring.defaultBendCost != null ? scoring.defaultBendCost : 0.0;
      }
      if (p_board.layerStructure.arr[i].isSignal) {
        currPreferredDirectionIsHorizontal = !currPreferredDirectionIsHorizontal;
      }
      if (layers[i].preferredDirectionHorizontal == null) {
        layers[i].preferredDirectionHorizontal = currPreferredDirectionIsHorizontal;
      }

      if (initializeTraceCosts) {
        scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
        scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
        if (currPreferredDirectionIsHorizontal) {
          scoring.undesiredDirectionTraceCost[i] += horizontalAddCostsAgainstPreferredDir;
        } else {
          scoring.undesiredDirectionTraceCost[i] += verticalAddCostsAgainstPreferredDir;
        }
      }
    }
    if (initializeTraceCosts) {
      int signalLayerCount = p_board.layerStructure.signal_layer_count();
      if (signalLayerCount > 2) {
        double outerAddCosts = 0.2 * signalLayerCount;
        // increase costs on the outer layers.
        scoring.preferredDirectionTraceCost[0] += outerAddCosts;
        scoring.preferredDirectionTraceCost[layerCount - 1] += outerAddCosts;
        scoring.undesiredDirectionTraceCost[0] += outerAddCosts;
        scoring.undesiredDirectionTraceCost[layerCount - 1] += outerAddCosts;
      }
      boardSpecificTraceCostsApplied = true;
    }

    // Log the changed parameters
    StringBuilder summary =
        new StringBuilder("applyBoardSpecificOptimizations changed parameters:");
    boolean changed = false;
    for (int i = 0; i < layerCount; i++) {
      StringBuilder layerChanges = new StringBuilder();

      if (!java.util.Objects.equals(originalRoutable[i], layers[i].routable)) {
        layerChanges.append(
            String.format(" routable: %s -> %s;", originalRoutable[i], layers[i].routable));
      }
      if (!java.util.Objects.equals(originalBendCost[i], layers[i].bendCost)) {
        layerChanges.append(
            String.format(" bendCost: %s -> %s;", originalBendCost[i], layers[i].bendCost));
      }
      if (!java.util.Objects.equals(originalPrefHoriz[i], layers[i].preferredDirectionHorizontal)) {
        layerChanges.append(
            String.format(
                " preferredDirectionHorizontal: %s -> %s;",
                originalPrefHoriz[i], layers[i].preferredDirectionHorizontal));
      }

      double oldPrefCost =
          originalPrefCost != null && i < originalPrefCost.length ? originalPrefCost[i] : 0.0;
      if (oldPrefCost != scoring.preferredDirectionTraceCost[i]) {
        layerChanges.append(
            String.format(
                " preferredDirectionTraceCost: %s -> %s;",
                oldPrefCost, scoring.preferredDirectionTraceCost[i]));
      }

      double oldUndesiredCost =
          originalUndesiredCost != null && i < originalUndesiredCost.length
              ? originalUndesiredCost[i]
              : 0.0;
      if (oldUndesiredCost != scoring.undesiredDirectionTraceCost[i]) {
        layerChanges.append(
            String.format(
                " undesiredDirectionTraceCost: %s -> %s;",
                oldUndesiredCost, scoring.undesiredDirectionTraceCost[i]));
      }

      if (layerChanges.length() > 0) {
        summary.append("\n  Layer ").append(i).append(":").append(layerChanges);
        changed = true;
      }
    }
    if (changed) {
      FRLogger.debug(summary.toString());
    }
  }

  /**
   * Get the number of layers configured in the router settings.
   *
   * @return The layer count
   */
  public int getLayerCount() {
    if (layers == null) {
      return 0;
    }

    return layers.length;
  }

  /**
   * Set the layer count and initialize the layer specific settings. Also initialises the per-layer
   * trace-cost arrays so that {@link #set_preferred_direction_trace_costs} and {@link
   * #set_against_preferred_direction_trace_costs} can be called immediately without a prior call to
   * {@link #applyBoardSpecificOptimizations}.
   */
  public void setLayerCount(int layerCount) {
    if (layers == null || layers.length != layerCount) {
      boardSpecificTraceCostsApplied = false;
      layers = new LayerSettings[layerCount];
      for (int i = 0; i < layerCount; i++) {
        layers[i] = new LayerSettings();
      }
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    // Initialize per-layer cost arrays with a neutral default so callers can
    // write individual entries without waiting for applyBoardSpecificOptimizations.
    scoring.preferredDirectionTraceCost = new double[layerCount];
    scoring.undesiredDirectionTraceCost = new double[layerCount];

    for (int i = 0; i < layerCount; i++) {
      layers[i].routable = true;
      layers[i].preferredDirectionHorizontal = null;
      layers[i].bendCost = null;
      scoring.preferredDirectionTraceCost[i] = 1.0;
      scoring.undesiredDirectionTraceCost[i] = 1.0;
    }
  }

  /**
   * Creates a deep copy of this RouterSettings object. All fields including nested objects and
   * arrays are cloned.
   *
   * @return A new RouterSettings instance with the same values
   */
  @Override
  public RouterSettings clone() {
    RouterSettings result = new RouterSettings();
    int layerCount = this.getLayerCount();
    if (layerCount > 0) {
      result.setLayerCount(layerCount);
    }
    result.algorithm = this.algorithm;
    result.jobTimeoutString = this.jobTimeoutString;
    if (this.layers != null) {
      result.layers = new LayerSettings[this.layers.length];
      for (int i = 0; i < this.layers.length; i++) {
        if (this.layers[i] != null) {
          result.layers[i] = this.layers[i].clone();
        }
      }
    }
    result.maxPasses = this.maxPasses;
    result.maxItems = this.maxItems;
    result.copperToEdgeClearanceUm = this.copperToEdgeClearanceUm;
    result.holeClearanceUm = this.holeClearanceUm;
    result.neckWidthUm = this.neckWidthUm;
    result.strictDrc = this.strictDrc;
    result.ignoreNetClasses = this.ignoreNetClasses != null ? this.ignoreNetClasses.clone() : null;
    result.tracePullTightAccuracy = this.tracePullTightAccuracy;
    result.enabled = this.enabled;
    result.viasAllowed = this.viasAllowed;
    result.automaticNeckdown = this.automaticNeckdown;
    result.maxThreads = this.maxThreads;

    // Use proper clone() methods for nested objects
    result.optimizer = this.optimizer != null ? this.optimizer.clone() : new OptimizerSettings();
    result.scoring = this.scoring != null ? this.scoring.clone() : new ScoringSettings();
    result.fanout = this.fanout != null ? this.fanout.clone() : new FanoutSettings();
    result.boardSpecificTraceCostsApplied = this.boardSpecificTraceCostsApplied;

    return result;
  }

  /** Neck width in micrometers, or 0 when width necking is disabled. */
  public double getNeckWidthUm() {
    return neckWidthUm != null && neckWidthUm > 0 ? neckWidthUm : 0;
  }

  public boolean isStrictDrc() {
    return Boolean.TRUE.equals(strictDrc);
  }

  public int get_start_ripup_costs() {
    return scoring != null && scoring.startRipupCosts != null ? scoring.startRipupCosts : 1;
  }

  public void set_start_ripup_costs(int p_value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.startRipupCosts = Math.max(p_value, 1);
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
      optimizer = new OptimizerSettings();
    }
    optimizer.enabled = p_value;
  }

  public boolean getRunFanout() {
    return fanout != null && fanout.enabled != null ? fanout.enabled : true;
  }

  /** Returns whether the fanout pre-pass should run. */
  public boolean isFanoutEnabled() {
    return fanout != null && Boolean.TRUE.equals(fanout.enabled);
  }

  public void setFanoutEnabled(Boolean value) {
    if (this.fanout == null) {
      this.fanout = new FanoutSettings();
    }
    Boolean oldValue = this.fanout.enabled;
    this.fanout.enabled = value;
    if (pcs != null) {
      pcs.firePropertyChange("fanout.enabled", oldValue, value);
    }
  }

  public boolean get_vias_allowed() {
    return viasAllowed != null ? viasAllowed : true;
  }

  public void set_vias_allowed(boolean p_value) {
    viasAllowed = p_value;
  }

  public int get_via_costs() {
    return scoring != null && scoring.viaCosts != null ? scoring.viaCosts : 1;
  }

  public void set_via_costs(int p_value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.viaCosts = Math.max(p_value, 1);
  }

  public int get_plane_via_costs() {
    return scoring != null && scoring.planeViaCosts != null ? scoring.planeViaCosts : 1;
  }

  public void set_plane_via_costs(int p_value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.planeViaCosts = Math.max(p_value, 1);
  }

  public void set_layer_active(int p_layer, boolean p_value) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_layer_active: p_layer="
              + p_layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return;
    }
    if (layers[p_layer] == null) {
      layers[p_layer] = new LayerSettings();
    }
    layers[p_layer].routable = p_value;
  }

  public boolean get_layer_active(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_layer_active: p_layer="
              + p_layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return false;
    }
    if (layers[p_layer] == null) {
      return true;
    }
    return layers[p_layer].routable != null ? layers[p_layer].routable : true;
  }

  public void set_bend_cost(int p_layer, double p_value) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn("RouterSettings.set_bend_cost: p_layer out of range");
      return;
    }
    if (layers[p_layer] == null) {
      layers[p_layer] = new LayerSettings();
    }
    layers[p_layer].bendCost = Math.max(MIN_BEND_COST, Math.min(MAX_BEND_COST, p_value));
  }

  public double get_bend_cost(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn("RouterSettings.get_bend_cost: p_layer out of range");
      return 0.0;
    }
    if (layers[p_layer] == null || layers[p_layer].bendCost == null) {
      return scoring != null && scoring.defaultBendCost != null
          ? Math.max(MIN_BEND_COST, Math.min(MAX_BEND_COST, scoring.defaultBendCost))
          : 0.0;
    }
    return layers[p_layer].bendCost;
  }

  public void set_preferred_direction_is_horizontal(int p_layer, boolean p_value) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_preferred_direction_is_horizontal: p_layer="
              + p_layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return;
    }
    if (layers[p_layer] == null) {
      layers[p_layer] = new LayerSettings();
    }
    layers[p_layer].preferredDirectionHorizontal = p_value;
  }

  public boolean get_preferred_direction_is_horizontal(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_preferred_direction_is_horizontal: p_layer="
              + p_layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return false;
    }
    if (layers[p_layer] == null) {
      return p_layer % 2 == 1;
    }
    return layers[p_layer].preferredDirectionHorizontal != null
        ? layers[p_layer].preferredDirectionHorizontal
        : (p_layer % 2 == 1);
  }

  public void set_preferred_direction_trace_costs(int p_layer, double p_value) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    if (scoring.preferredDirectionTraceCost == null
        || scoring.preferredDirectionTraceCost.length != this.getLayerCount()) {
      scoring.preferredDirectionTraceCost = new double[this.getLayerCount()];
    }
    scoring.preferredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
    boardSpecificTraceCostsApplied = true;
  }

  public double get_preferred_direction_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    if (scoring == null
        || scoring.preferredDirectionTraceCost == null
        || p_layer >= scoring.preferredDirectionTraceCost.length) {
      return 1.0;
    }
    return scoring.preferredDirectionTraceCost[p_layer];
  }

  public double get_against_preferred_direction_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    if (scoring == null
        || scoring.undesiredDirectionTraceCost == null
        || p_layer >= scoring.undesiredDirectionTraceCost.length) {
      return 1.0;
    }
    return scoring.undesiredDirectionTraceCost[p_layer];
  }

  public double get_horizontal_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (get_preferred_direction_is_horizontal(p_layer)) {
      result = scoring.preferredDirectionTraceCost[p_layer];
    } else {
      result = scoring.undesiredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public void set_against_preferred_direction_trace_costs(int p_layer, double p_value) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_against_preferred_direction_trace_costs: p_layer out of range");
      return;
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    if (scoring.undesiredDirectionTraceCost == null
        || scoring.undesiredDirectionTraceCost.length != this.getLayerCount()) {
      scoring.undesiredDirectionTraceCost = new double[this.getLayerCount()];
    }
    scoring.undesiredDirectionTraceCost[p_layer] = Math.max(p_value, 0.1);
    boardSpecificTraceCostsApplied = true;
  }

  public double get_vertical_trace_costs(int p_layer) {
    if (p_layer < 0 || p_layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_against_preferred_direction_trace_costs: p_layer out of range");
      return 0;
    }
    double result;
    if (get_preferred_direction_is_horizontal(p_layer)) {
      result = scoring.undesiredDirectionTraceCost[p_layer];
    } else {
      result = scoring.preferredDirectionTraceCost[p_layer];
    }
    return result;
  }

  public AutorouteControl.ExpansionCostFactor[] get_trace_cost_arr() {
    if (scoring == null || scoring.preferredDirectionTraceCost == null) {
      return new AutorouteControl.ExpansionCostFactor[0];
    }
    AutorouteControl.ExpansionCostFactor[] result =
        new AutorouteControl.ExpansionCostFactor[scoring.preferredDirectionTraceCost.length];
    for (int i = 0; i < result.length; i++) {
      result[i] =
          new AutorouteControl.ExpansionCostFactor(
              get_horizontal_trace_costs(i), get_vertical_trace_costs(i));
    }
    return result;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered automatically
   * to the pin with, if necessary.
   */
  public boolean get_automatic_neckdown() {
    return this.automaticNeckdown != null && this.automaticNeckdown;
  }

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered automatically
   * to the pin with, if necessary.
   */
  public void set_automatic_neckdown(boolean p_value) {
    this.automaticNeckdown = p_value;
  }

  /**
   * Applies new values from the given settings to this settings object. Uses reflection to copy
   * only non-null fields from the source settings. This is the core mechanism used by
   * SettingsMerger to merge settings from multiple sources.
   *
   * @param settings The settings to copy the values from (null values are skipped)
   * @return The number of fields that were changed
   */
  public int applyNewValuesFrom(RouterSettings settings) {
    if (settings == null) {
      FRLogger.warn("Attempted to apply null settings, skipping");
      return 0;
    }

    int changedCount = ReflectionUtil.copyFields(settings, this);

    // Fire property change events for key properties to update GUI
    // Note: We fire events even if values didn't change to ensure GUI is in sync
    if (pcs != null) {
      pcs.firePropertyChange("maxPasses", null, this.maxPasses);
      pcs.firePropertyChange("maxThreads", null, this.maxThreads);
      pcs.firePropertyChange("jobTimeoutString", null, this.jobTimeoutString);
      pcs.firePropertyChange("enabled", null, this.enabled);
      pcs.firePropertyChange(
          "optimizer.enabled", null, this.optimizer != null ? this.optimizer.enabled : null);
      pcs.firePropertyChange(
          "fanout.enabled", null, this.fanout != null ? this.fanout.enabled : null);
    }

    return changedCount;
  }

  public void validate() {
    // Validate maxPasses (0 means no limit)
    if (this.maxPasses < 0 || this.maxPasses > 9999) {
      FRLogger.warn("Invalid maxPasses value: " + this.maxPasses + ", using default 9999");
      this.maxPasses = 9999;
    } else if (this.maxPasses == 0) {
      // 0 means no limit, set to maximum
      this.maxPasses = Integer.MAX_VALUE;
      FRLogger.debug("maxPasses set to 0 (no limit), using Integer.MAX_VALUE");
    }

    // Validate maxThreads (0 means no limit - handled as max available during normalization)
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    if (this.maxThreads == null) {
      this.maxThreads = defaultMaxThreads();
    } else if (this.maxThreads < 0) {
      FRLogger.warn(
          "Invalid maxThreads value: " + this.maxThreads + ", using " + defaultMaxThreads());
      this.maxThreads = defaultMaxThreads();
    } else if (this.maxThreads > availableProcessors) {
      FRLogger.warn(
          "Invalid maxThreads value: " + this.maxThreads + ", capping at " + availableProcessors);
      this.maxThreads = availableProcessors;
    }

    // Validate tracePullTightAccuracy
    if (this.tracePullTightAccuracy < 1) {
      FRLogger.warn(
          "Invalid tracePullTightAccuracy value: "
              + this.tracePullTightAccuracy
              + ", using default 500");
      this.tracePullTightAccuracy = 500;
    }
  }
}
