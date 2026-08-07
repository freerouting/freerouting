package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.RouterSettings;
import java.util.Collection;

/** Structure for controlling the autoroute algorithm. */
public class AutorouteControl {

  public final RouterSettings settings;

  /** The horizontal and vertical trace costs on each layer */
  public final ExpansionCostFactor[] traceCosts;

  public final double[] bendCosts;
  public final boolean withNeckdown;

  /** Defines for each layer, if it may be used for routing. */
  public final boolean[] layerActive;

  final int layerCount;

  /** The currently used trace half widths in the autoroute algorithm on each layer */
  final int[] traceHalfWidth;

  /**
   * The currently used compensated trace half widths in the autoroute algorithm on each layer.
   * Equal to traceHalfWidth if no clearance compensation is used.
   */
  final int[] compensatedTraceHalfWidth;

  final double[] viaRadiusArr;

  /** the additional costs to min_normal via_cost for inserting a via between 2 layers */
  final ViaCost[] addViaCosts;

  /** The currently used clearance class for traces in the autoroute algorithm */
  public int traceClearanceClassNo;

  /** True, if layer change by inserting of vias is allowed */
  public boolean viasAllowed;

  /** True, if vias may drill to the pad of SMD pins */
  public boolean attachSmdAllowed;

  /** The minimum cost value of all normal vias */
  public double minNormalViaCost;

  public boolean ripupAllowed;
  public int ripupCosts;
  public int ripupPassNo;

  /** If true, the autoroute algorithm completes after the first drill */
  public boolean isFanout;

  /** Source pin name for targeted fanout diagnostics. */
  public String fanoutStartPinName;

  /** Source pin center for targeted fanout diagnostics. */
  public Point fanoutStartPinCenter;

  /** Source pin layer for targeted fanout diagnostics and limits. */
  public int fanoutStartPinLayer = -1;

  /** Normally true, if the autorouter contains no fanout pass */
  public boolean removeUnconnectedVias;

  /** The currently used net number in the autoroute algorithm */
  int netNo;

  /** The currently used clearance class for vias in the autoroute algorithm */
  int viaClearanceClass;

  /** The possible (partial) vias, which can be used by the autorouter */
  public ViaRule viaRule;

  /** The array of possible via ranges used bei the autorouter */
  ViaMask[] viaInfoArr;

  /** The lower bound for the first layer of vias */
  int viaLowerBound;

  /** The upper bound for the last layer of vias */
  int viaUpperBound;

  double maxViaRadius;

  /** The width of the region around changed traces, where traces are pulled tight */
  int tidyRegionWidth;

  /** The pull tight accuracy of traces */
  int pullTightAccuracy;

  /** The maximum recursion depth for shoving traces */
  int maxShoveTraceRecursionDepth;

  /** The maximum recursion depth for shoving obstacles */
  int maxShoveViaRecursionDepth;

  /** The maximum recursion depth for traces springing over obstacles */
  int maxSpringOverRecursionDepth;

  /** The minimal cost value of all cheap vias */
  double minCheapViaCost;

  /** Creates a new instance of AutorouteControl for the input net */
  public AutorouteControl(RoutingBoard p_board, int p_net_no, RouterSettings p_settings) {
    this(p_board, p_settings, p_settings.get_trace_cost_arr());
    init_net(p_net_no, p_board, p_settings.get_via_costs());
  }

  /** Creates a new instance of AutorouteControl for the input net */
  public AutorouteControl(
      RoutingBoard p_board,
      int p_net_no,
      RouterSettings p_settings,
      int p_via_costs,
      ExpansionCostFactor[] p_trace_cost_arr) {
    this(p_board, p_settings, p_trace_cost_arr);
    init_net(p_net_no, p_board, p_via_costs);
  }

  /** Creates a new instance of AutorouteControl */
  private AutorouteControl(
      RoutingBoard p_board, RouterSettings p_settings, ExpansionCostFactor[] p_trace_costs_arr) {
    this.settings = p_settings;
    layerCount = p_board.get_layer_count();
    traceHalfWidth = new int[layerCount];
    compensatedTraceHalfWidth = new int[layerCount];
    layerActive = new boolean[layerCount];
    viasAllowed = p_settings.get_vias_allowed();
    viaRadiusArr = new double[layerCount];
    addViaCosts = new ViaCost[layerCount];
    this.bendCosts = new double[layerCount];
    for (int i = 0; i < layerCount; i++) {
      this.bendCosts[i] = p_settings.get_bend_cost(i);
    }

    for (int i = 0; i < layerCount; i++) {
      addViaCosts[i] = new ViaCost(layerCount);
      boolean activeSetting = p_settings.get_layer_active(i);
      if (!p_board.layerStructure.arr[i].isSignal && activeSetting) {
        FRLogger.warn(
            "Layer '"
                + p_board.layerStructure.arr[i].name
                + "' is a dedicated power plane and cannot be routed. Forcing active state to false.");
        layerActive[i] = false;
      } else {
        layerActive[i] = activeSetting;
      }
    }
    isFanout = false;
    fanoutStartPinName = null;
    fanoutStartPinCenter = null;
    fanoutStartPinLayer = -1;
    removeUnconnectedVias = true;
    withNeckdown = p_settings.get_automatic_neckdown();
    tidyRegionWidth = Integer.MAX_VALUE;
    pullTightAccuracy = 500;
    maxShoveTraceRecursionDepth = 20;
    maxShoveViaRecursionDepth = 5;
    maxSpringOverRecursionDepth = 5;
    for (int i = 0; i < layerCount; i++) {
      for (int j = 0; j < layerCount; j++) {
        addViaCosts[i].toLayer[j] = 0;
      }
    }
    traceCosts = p_trace_costs_arr;
    attachSmdAllowed = false;
    viaLowerBound = 0;
    viaUpperBound = layerCount;

    ripupAllowed = false;
    ripupCosts = 1000;
    ripupPassNo = 1;
  }

  private void init_net(int p_net_no, RoutingBoard p_board, int p_via_costs) {
    netNo = p_net_no;
    Net currNet = p_board.rules.nets.get(p_net_no);
    NetClass currNetClass;
    if (currNet != null) {
      currNetClass = currNet.get_class();
      traceClearanceClassNo = currNetClass.get_trace_clearance_class();
      viaRule = currNetClass.get_via_rule();
    } else {
      traceClearanceClassNo = 1;
      viaRule = p_board.rules.viaRules.firstElement();
      currNetClass = null;
    }
    for (int i = 0; i < layerCount; i++) {
      if (netNo > 0) {
        traceHalfWidth[i] = p_board.rules.get_trace_half_width(netNo, i);
      } else {
        traceHalfWidth[i] = p_board.rules.get_trace_half_width(1, i);
      }
      compensatedTraceHalfWidth[i] =
          traceHalfWidth[i]
              + p_board.rules.clearanceMatrix.clearance_compensation_value(
                  traceClearanceClassNo, i);
      if (currNetClass != null && !currNetClass.is_active_routing_layer(i)) {
        layerActive[i] = false;
      }
    }
    rebuild_via_info(p_board, p_via_costs, p_net_no);
  }

  public void rebuild_via_info(RoutingBoard p_board, int p_via_costs, int p_net_no) {
    if (viaRule.via_count() > 0) {
      this.viaClearanceClass = viaRule.get_via(0).get_clearance_class();
    } else {
      this.viaClearanceClass = 1;
    }
    this.viaInfoArr = new ViaMask[viaRule.via_count()];
    this.attachSmdAllowed = false;
    for (int i = 0; i < viaRule.via_count(); i++) {
      ViaInfo currVia = viaRule.get_via(i);
      if (currVia.attach_smd_allowed()) {
        this.attachSmdAllowed = true;
      }
      Padstack currViaPadstack = currVia.get_padstack();
      int fromLayer = currViaPadstack.from_layer();
      int toLayer = currViaPadstack.to_layer();
      for (int j = fromLayer; j <= toLayer; j++) {
        ConvexShape currShape = currViaPadstack.get_shape(j);
        double currRadius;
        if (currShape != null) {
          currRadius = 0.5 * currShape.max_width();
        } else {
          currRadius = 0;
        }
        this.viaRadiusArr[j] = Math.max(this.viaRadiusArr[j], currRadius);
      }
      viaInfoArr[i] = new ViaMask(fromLayer, toLayer, currVia.attach_smd_allowed());
    }

    boolean pureSmdNet = isPureSmdNet(p_board, p_net_no);
    if (!this.attachSmdAllowed && layerCount > 1 && pureSmdNet) {
      // Pure SMD nets must still be able to escape their component layer, even if the DSN marks
      // every padstack as attach-off. This only relaxes the routing gate for same-net fanout;
      // cross-net DRC remains governed by the padstack's attach flag.
      this.attachSmdAllowed = true;
    }

    for (int j = 0; j < this.layerCount; j++) {
      this.viaRadiusArr[j] = Math.max(this.viaRadiusArr[j], traceHalfWidth[j]);
      this.maxViaRadius = Math.max(this.maxViaRadius, this.viaRadiusArr[j]);
    }
    double viaCostFactor = this.maxViaRadius;
    viaCostFactor = Math.max(viaCostFactor, 1);
    if (pureSmdNet) {
      // Pure SMD boards need a much cheaper via escape to avoid exhausting the local pad channel
      // before the search commits to a layer change.
      viaCostFactor *= 0.1;
    }
    minNormalViaCost = p_via_costs * viaCostFactor;
    minCheapViaCost = 0.8 * minNormalViaCost;
  }

  private static boolean isPureSmdNet(RoutingBoard p_board, int p_net_no) {
    Collection<Item> netItems = p_board.get_connectable_items(p_net_no);
    if (netItems.isEmpty()) {
      return false;
    }

    for (Item item : netItems) {
      if (!(item instanceof Pin pin) || pin.first_layer() != pin.last_layer()) {
        return false;
      }
    }

    return true;
  }

  /** horizontal and vertical costs for traces on a board layer */
  public static class ExpansionCostFactor {

    /** The horizontal expansion cost factor on a layer of the board */
    public final double horizontal;

    /** The vertical expansion cost factor on a layer of the board */
    public final double vertical;

    public ExpansionCostFactor(double p_horizontal, double p_vertical) {
      horizontal = p_horizontal;
      vertical = p_vertical;
    }
  }

  /** Array of via costs from one layer to the other layers */
  static final class ViaCost {

    public int[] toLayer;

    private ViaCost(int p_layer_count) {
      toLayer = new int[p_layer_count];
    }
  }

  static class ViaMask {

    final int fromLayer;
    final int toLayer;
    final boolean attachSmdAllowed;

    ViaMask(int p_from_layer, int p_to_layer, boolean p_attach_smd_allowed) {
      fromLayer = p_from_layer;
      toLayer = p_to_layer;
      attachSmdAllowed = p_attach_smd_allowed;
    }
  }
}
