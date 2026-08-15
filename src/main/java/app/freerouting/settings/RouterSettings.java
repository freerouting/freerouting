package app.freerouting.settings;

import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.board.RoutingBoard;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.ReflectionUtil;
import com.google.gson.annotations.SerializedName;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

/** Mutable router configuration assembled from the configured settings sources. */
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

  public transient Boolean saveIntermediateStages;

  @SerializedName("ignore_net_classes")
  public transient String[] ignoreNetClasses;

  /** The accuracy of the pull tight algorithm. */
  @SerializedName(
      value = "trace_pull_tight_accuracy",
      alternate = {"tracePullTightAccuracy"})
  public Integer tracePullTightAccuracy;

  @SerializedName("allowed_via_types")
  public Boolean viasAllowed;

  /**
   * If true, the trace width at static pins smaller the trace width will be lowered automatically
   * to the pin with, if necessary.
   */
  @SerializedName(
      value = "automatic_neckdown",
      alternate = {"automaticNeckdown"})
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

  /** Creates router settings sized and tuned for the supplied board. */
  public RouterSettings(RoutingBoard board) {
    this();
    setLayerCount(board.getLayerCount());
    applyBoardSpecificOptimizations(board);
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

  /** Registers a listener for changes made through the settings API. */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (pcs == null) {
      pcs = new PropertyChangeSupport(this);
    }
    pcs.addPropertyChangeListener(listener);
  }

  /** Removes a previously registered property-change listener. */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (pcs != null) {
      pcs.removePropertyChangeListener(listener);
    }
  }

  /** Sets the maximum number of routing passes and notifies listeners. */
  public void setMaxPasses(Integer value) {
    Integer oldValue = this.maxPasses;
    this.maxPasses = value;
    if (pcs != null) {
      pcs.firePropertyChange("maxPasses", oldValue, value);
    }
  }

  /** Sets and normalizes the maximum number of worker threads. */
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

  /** Sets the maximum duration allowed for a routing job. */
  public void setJobTimeoutString(String value) {
    String oldValue = this.jobTimeoutString;
    this.jobTimeoutString = value;
    if (pcs != null) {
      pcs.firePropertyChange("jobTimeoutString", oldValue, value);
    }
  }

  /** Enables or disables the autorouter. */
  public void setEnabled(Boolean value) {
    Boolean oldValue = this.enabled;
    this.enabled = value;
    if (pcs != null) {
      pcs.firePropertyChange("enabled", oldValue, value);
    }
  }

  /** Sets whether the autorouter may place vias. */
  public void setViasAllowed(Boolean value) {
    Boolean oldValue = this.viasAllowed;
    this.viasAllowed = value;
    if (pcs != null) {
      pcs.firePropertyChange("viasAllowed", oldValue, value);
    }
  }

  /** Sets whether the autorouter may place vias using a primitive boolean. */
  public void setViasAllowed(boolean value) {
    viasAllowed = value;
  }

  /** Selects the routing algorithm implementation. */
  public void setAlgorithm(String value) {
    String oldValue = this.algorithm;
    this.algorithm = value;
    if (pcs != null) {
      pcs.firePropertyChange("algorithm", oldValue, value);
    }
  }

  /** Enables or disables the post-routing optimizer. */
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
   * Applies board-specific optimizations only when the board dimensions or layer count changed.
   *
   * @param board board whose geometry determines routing costs
   */
  public void applyBoardSpecificOptimizationsIfNeeded(RoutingBoard board) {
    if (board == null) {
      return;
    }
    int boardLayerCount = board.getLayerCount();
    if (getLayerCount() != boardLayerCount
        || !Boolean.TRUE.equals(boardSpecificTraceCostsApplied)) {
      applyBoardSpecificOptimizations(board);
    }
  }

  /** Returns whether board-specific trace costs have been initialized. */
  public boolean areBoardSpecificTraceCostsApplied() {
    return Boolean.TRUE.equals(boardSpecificTraceCostsApplied);
  }

  /**
   * Calculates layer routing costs from board geometry and signal-layer structure.
   *
   * @param board board whose geometry determines routing costs
   */
  public void applyBoardSpecificOptimizations(RoutingBoard board) {
    double horizontalWidth = board.boundingBox.width();
    double verticalWidth = board.boundingBox.height();

    int layerCount = board.getLayerCount();

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
    final double[] originalPrefCost =
        scoring.preferredDirectionTraceCost != null
            ? scoring.preferredDirectionTraceCost.clone()
            : null;
    final double[] originalUndesiredCost =
        scoring.undesiredDirectionTraceCost != null
            ? scoring.undesiredDirectionTraceCost.clone()
            : null;

    // additional costs against preferred direction with 1 digit behind the decimal
    // point.
    final double horizontalAddCostsAgainstPreferredDir =
        0.1 * Math.round(10 * horizontalWidth / verticalWidth);

    final double verticalAddCostsAgainstPreferredDir =
        0.1 * Math.round(10 * verticalWidth / horizontalWidth);

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

    boolean currentPreferredDirectionIsHorizontal = horizontalWidth < verticalWidth;
    boolean initializeTraceCosts = !Boolean.TRUE.equals(boardSpecificTraceCostsApplied);

    for (int i = 0; i < layerCount; i++) {
      if (board.layerStructure.arr[i].isSignal) {
        currentPreferredDirectionIsHorizontal = !currentPreferredDirectionIsHorizontal;
      }
      if (!board.layerStructure.arr[i].isSignal) {
        layers[i].routable = false;
      } else if (layers[i].routable == null) {
        layers[i].routable = true;
      }
      if (layers[i].bendCost == null) {
        layers[i].bendCost =
            scoring != null && scoring.defaultBendCost != null ? scoring.defaultBendCost : 0.0;
      }
      if (layers[i].preferredDirectionHorizontal == null) {
        layers[i].preferredDirectionHorizontal = currentPreferredDirectionIsHorizontal;
      }

      if (initializeTraceCosts) {
        scoring.preferredDirectionTraceCost[i] = scoring.defaultPreferredDirectionTraceCost;
        scoring.undesiredDirectionTraceCost[i] = scoring.defaultUndesiredDirectionTraceCost;
        if (currentPreferredDirectionIsHorizontal) {
          scoring.undesiredDirectionTraceCost[i] += horizontalAddCostsAgainstPreferredDir;
        } else {
          scoring.undesiredDirectionTraceCost[i] += verticalAddCostsAgainstPreferredDir;
        }
      }
    }
    if (initializeTraceCosts) {
      int signalLayerCount = board.layerStructure.signalLayerCount();
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
            " routable: %s -> %s;".formatted(originalRoutable[i], layers[i].routable));
      }
      if (!java.util.Objects.equals(originalBendCost[i], layers[i].bendCost)) {
        layerChanges.append(
            " bendCost: %s -> %s;".formatted(originalBendCost[i], layers[i].bendCost));
      }
      if (!java.util.Objects.equals(originalPrefHoriz[i], layers[i].preferredDirectionHorizontal)) {
        layerChanges.append(
            " preferredDirectionHorizontal: %s -> %s;"
                .formatted(originalPrefHoriz[i], layers[i].preferredDirectionHorizontal));
      }

      double oldPrefCost =
          originalPrefCost != null && i < originalPrefCost.length ? originalPrefCost[i] : 0.0;
      if (oldPrefCost != scoring.preferredDirectionTraceCost[i]) {
        layerChanges.append(
            " preferredDirectionTraceCost: %s -> %s;"
                .formatted(oldPrefCost, scoring.preferredDirectionTraceCost[i]));
      }

      double oldUndesiredCost =
          originalUndesiredCost != null && i < originalUndesiredCost.length
              ? originalUndesiredCost[i]
              : 0.0;
      if (oldUndesiredCost != scoring.undesiredDirectionTraceCost[i]) {
        layerChanges.append(
            " undesiredDirectionTraceCost: %s -> %s;"
                .formatted(oldUndesiredCost, scoring.undesiredDirectionTraceCost[i]));
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
   * Sets the number of board layers and initializes layer-specific cost arrays.
   *
   * @param layerCount number of layers in the board
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
    result.saveIntermediateStages = this.saveIntermediateStages;
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

  /** Returns the configured necking width in micrometres, or zero when disabled. */
  public double getNeckWidthUm() {
    return neckWidthUm != null && neckWidthUm > 0 ? neckWidthUm : 0;
  }

  /** Returns whether connections with newly introduced clearance violations are rejected. */
  public boolean isStrictDrc() {
    return Boolean.TRUE.equals(strictDrc);
  }

  /** Returns the minimum ripup cost used by the router. */
  public int getStartRipupCosts() {
    return scoring != null && scoring.startRipupCosts != null ? scoring.startRipupCosts : 1;
  }

  /** Sets the minimum ripup cost used by the router. */
  public void setStartRipupCosts(int value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.startRipupCosts = Math.max(value, 1);
  }

  /** Returns whether the autorouter should run. */
  public boolean getRunRouter() {
    return enabled != null ? enabled : true;
  }

  /** Sets whether the autorouter should run. */
  public void setRunRouter(boolean value) {
    enabled = value;
  }

  /** Returns whether the post-routing optimizer should run. */
  public boolean getRunOptimizer() {
    return optimizer != null && optimizer.enabled != null ? optimizer.enabled : false;
  }

  /** Sets whether the post-routing optimizer should run. */
  public void setRunOptimizer(boolean value) {
    if (optimizer == null) {
      optimizer = new OptimizerSettings();
    }
    optimizer.enabled = value;
  }

  /** Returns whether the fanout pre-pass should run. */
  public boolean getRunFanout() {
    return fanout != null && fanout.enabled != null ? fanout.enabled : true;
  }

  /** Returns whether the fanout pre-pass should run. */
  public boolean isFanoutEnabled() {
    return fanout != null && Boolean.TRUE.equals(fanout.enabled);
  }

  /** Enables or disables the fanout pre-pass. */
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

  /** Returns whether the autorouter may place vias. */
  public boolean getViasAllowed() {
    return viasAllowed != null ? viasAllowed : true;
  }

  /** Returns the cost assigned to regular vias. */
  public int getViaCosts() {
    return scoring != null && scoring.viaCosts != null ? scoring.viaCosts : 1;
  }

  /** Sets the cost assigned to regular vias. */
  public void setViaCosts(int value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.viaCosts = Math.max(value, 1);
  }

  /** Returns the cost assigned to vias connecting to a plane. */
  public int getPlaneViaCosts() {
    return scoring != null && scoring.planeViaCosts != null ? scoring.planeViaCosts : 1;
  }

  /** Sets the cost assigned to vias connecting to a plane. */
  public void setPlaneViaCosts(int value) {
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    scoring.planeViaCosts = Math.max(value, 1);
  }

  /**
   * Enables or disables routing on one layer.
   *
   * @param layer layer index to update
   * @param value whether routing is enabled on the layer
   */
  public void setLayerActive(int layer, boolean value) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_layer_active: layer="
              + layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return;
    }
    if (layers[layer] == null) {
      layers[layer] = new LayerSettings();
    }
    layers[layer].routable = value;
  }

  /**
   * Returns whether routing is enabled on one layer.
   *
   * @param layer layer index to query
   * @return whether routing is enabled on the layer
   */
  public boolean getLayerActive(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_layer_active: layer="
              + layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return false;
    }
    if (layers[layer] == null) {
      return true;
    }
    return layers[layer].routable != null ? layers[layer].routable : true;
  }

  /**
   * Sets the bend cost for one layer.
   *
   * @param layer layer index to update
   * @param value bend cost to store
   */
  public void setBendCost(int layer, double value) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn("RouterSettings.set_bend_cost: layer out of range");
      return;
    }
    if (layers[layer] == null) {
      layers[layer] = new LayerSettings();
    }
    layers[layer].bendCost = Math.max(MIN_BEND_COST, Math.min(MAX_BEND_COST, value));
  }

  /**
   * Returns the effective bend cost for one layer.
   *
   * @param layer layer index to query
   * @return effective bend cost
   */
  public double getBendCost(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn("RouterSettings.get_bend_cost: layer out of range");
      return 0.0;
    }
    if (layers[layer] == null || layers[layer].bendCost == null) {
      return scoring != null && scoring.defaultBendCost != null
          ? Math.max(MIN_BEND_COST, Math.min(MAX_BEND_COST, scoring.defaultBendCost))
          : 0.0;
    }
    return layers[layer].bendCost;
  }

  /**
   * Sets the preferred routing direction for one layer.
   *
   * @param layer layer index to update
   * @param value whether the preferred direction is horizontal
   */
  public void setPreferredDirectionIsHorizontal(int layer, boolean value) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_preferred_direction_is_horizontal: layer="
              + layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return;
    }
    if (layers[layer] == null) {
      layers[layer] = new LayerSettings();
    }
    layers[layer].preferredDirectionHorizontal = value;
  }

  /**
   * Returns the preferred routing direction for one layer.
   *
   * @param layer layer index to query
   * @return whether the preferred direction is horizontal
   */
  public boolean getPreferredDirectionIsHorizontal(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_preferred_direction_is_horizontal: layer="
              + layer
              + " out of range [0.."
              + (this.getLayerCount() - 1)
              + "]");
      return false;
    }
    if (layers[layer] == null) {
      return layer % 2 == 1;
    }
    return layers[layer].preferredDirectionHorizontal != null
        ? layers[layer].preferredDirectionHorizontal
        : (layer % 2 == 1);
  }

  /**
   * Sets the preferred-direction trace cost for one layer.
   *
   * @param layer layer index to update
   * @param value trace cost to store
   */
  public void setPreferredDirectionTraceCosts(int layer, double value) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.set_preferred_direction_trace_costs: layer out of range");
      return;
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    if (scoring.preferredDirectionTraceCost == null
        || scoring.preferredDirectionTraceCost.length != this.getLayerCount()) {
      scoring.preferredDirectionTraceCost = new double[this.getLayerCount()];
    }
    scoring.preferredDirectionTraceCost[layer] = Math.max(value, 0.1);
    boardSpecificTraceCostsApplied = true;
  }

  /**
   * Returns the preferred-direction trace cost for one layer.
   *
   * @param layer layer index to query
   * @return preferred-direction trace cost
   */
  public double getPreferredDirectionTraceCosts(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: layer out of range");
      return 0;
    }
    if (scoring == null
        || scoring.preferredDirectionTraceCost == null
        || layer >= scoring.preferredDirectionTraceCost.length) {
      return 1.0;
    }
    return scoring.preferredDirectionTraceCost[layer];
  }

  /**
   * Returns the against-preferred-direction trace cost for one layer.
   *
   * @param layer layer index to query
   * @return against-preferred-direction trace cost
   */
  public double getAgainstPreferredDirectionTraceCosts(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_against_preferred_direction_trace_costs: layer out of range");
      return 0;
    }
    if (scoring == null
        || scoring.undesiredDirectionTraceCost == null
        || layer >= scoring.undesiredDirectionTraceCost.length) {
      return 1.0;
    }
    return scoring.undesiredDirectionTraceCost[layer];
  }

  /**
   * Returns the trace cost for horizontal movement on one layer.
   *
   * @param layer layer index to query
   * @return horizontal trace cost
   */
  public double getHorizontalTraceCosts(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn("AutorouteSettings.get_preferred_direction_trace_costs: layer out of range");
      return 0;
    }
    double result;
    if (getPreferredDirectionIsHorizontal(layer)) {
      result = scoring.preferredDirectionTraceCost[layer];
    } else {
      result = scoring.undesiredDirectionTraceCost[layer];
    }
    return result;
  }

  /**
   * Sets the against-preferred-direction trace cost for one layer.
   *
   * @param layer layer index to update
   * @param value trace cost to store
   */
  public void setAgainstPreferredDirectionTraceCosts(int layer, double value) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.set_against_preferred_direction_trace_costs: layer out of range");
      return;
    }
    if (scoring == null) {
      scoring = new ScoringSettings();
    }
    if (scoring.undesiredDirectionTraceCost == null
        || scoring.undesiredDirectionTraceCost.length != this.getLayerCount()) {
      scoring.undesiredDirectionTraceCost = new double[this.getLayerCount()];
    }
    scoring.undesiredDirectionTraceCost[layer] = Math.max(value, 0.1);
    boardSpecificTraceCostsApplied = true;
  }

  /**
   * Returns the trace cost for vertical movement on one layer.
   *
   * @param layer layer index to query
   * @return vertical trace cost
   */
  public double getVerticalTraceCosts(int layer) {
    if (layer < 0 || layer >= this.getLayerCount()) {
      FRLogger.warn(
          "AutorouteSettings.get_against_preferred_direction_trace_costs: layer out of range");
      return 0;
    }
    double result;
    if (getPreferredDirectionIsHorizontal(layer)) {
      result = scoring.undesiredDirectionTraceCost[layer];
    } else {
      result = scoring.preferredDirectionTraceCost[layer];
    }
    return result;
  }

  /** Returns per-layer horizontal and vertical trace-cost factors. */
  public AutorouteControl.ExpansionCostFactor[] getTraceCostArr() {
    if (scoring == null || scoring.preferredDirectionTraceCost == null) {
      return new AutorouteControl.ExpansionCostFactor[0];
    }
    AutorouteControl.ExpansionCostFactor[] result =
        new AutorouteControl.ExpansionCostFactor[scoring.preferredDirectionTraceCost.length];
    for (int i = 0; i < result.length; i++) {
      result[i] =
          new AutorouteControl.ExpansionCostFactor(
              getHorizontalTraceCosts(i), getVerticalTraceCosts(i));
    }
    return result;
  }

  /** Returns whether automatic trace neckdown is enabled. */
  public boolean getAutomaticNeckdown() {
    return this.automaticNeckdown != null && this.automaticNeckdown;
  }

  /** Sets whether automatic trace neckdown is enabled. */
  public void setAutomaticNeckdown(boolean value) {
    this.automaticNeckdown = value;
  }

  /**
   * Applies explicitly populated values from another settings object.
   *
   * @param settings source settings whose non-null values should be copied
   * @return number of fields changed
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

  /** Validates and normalizes values that affect routing execution. */
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
