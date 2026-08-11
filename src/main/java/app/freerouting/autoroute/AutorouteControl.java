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

  /** The horizontal and vertical trace costs on each layer. */
  public final ExpansionCostFactor[] traceCosts;

  public final double[] bendCosts;
  public final boolean withNeckdown;

  /** Defines for each layer, if it may be used for routing. */
  public final boolean[] layerActive;

  final int layerCount;

  /** The currently used trace half widths in the autoroute algorithm on each layer. */
  final int[] traceHalfWidth;

  /**
   * The currently used compensated trace half widths in the autoroute algorithm on each layer.
   * Equal to traceHalfWidth if no clearance compensation is used.
   */
  final int[] compensatedTraceHalfWidth;

  final double[] viaRadiusArr;

  /** The additional costs to min_normal via_cost for inserting a via between 2 layers. */
  final ViaCost[] addViaCosts;

  /** The currently used clearance class for traces in the autoroute algorithm. */
  public int traceClearanceClassNo;

  /** True, if layer change by inserting of vias is allowed. */
  public boolean viasAllowed;

  /** True, if vias may drill to the pad of SMD pins. */
  public boolean attachSmdAllowed;

  /** The minimum cost value of all normal vias. */
  public double minNormalViaCost;

  public boolean ripupAllowed;
  public int ripupCosts;
  public int ripupPassNo;

  /** If true, the autoroute algorithm completes after the first drill. */
  public boolean isFanout;

  /** Source pin name for targeted fanout diagnostics. */
  public String fanoutStartPinName;

  /** Source pin center for targeted fanout diagnostics. */
  public Point fanoutStartPinCenter;

  /** Source pin layer for targeted fanout diagnostics and limits. */
  public int fanoutStartPinLayer = -1;

  /** Normally true, if the autorouter contains no fanout pass. */
  public boolean removeUnconnectedVias;

  /** The currently used net number in the autoroute algorithm. */
  int netNo;

  /** The currently used clearance class for vias in the autoroute algorithm. */
  int viaClearanceClass;

  /** The possible (partial) vias, which can be used by the autorouter. */
  public ViaRule viaRule;

  /** The array of possible via ranges used by the autorouter. */
  ViaMask[] viaInfoArr;

  /** The lower bound for the first layer of vias. */
  int viaLowerBound;

  /** The upper bound for the last layer of vias. */
  int viaUpperBound;

  double maxViaRadius;

  /** The width of the region around changed traces, where traces are pulled tight. */
  int tidyRegionWidth;

  /** The pull tight accuracy of traces. */
  int pullTightAccuracy;

  /** The maximum recursion depth for shoving traces. */
  int maxShoveTraceRecursionDepth;

  /** The maximum recursion depth for shoving obstacles. */
  int maxShoveViaRecursionDepth;

  /** The maximum recursion depth for traces springing over obstacles. */
  int maxSpringOverRecursionDepth;

  /** The minimal cost value of all cheap vias. */
  double minCheapViaCost;

  /** Creates a new instance of AutorouteControl for the input net. */
  public AutorouteControl(RoutingBoard board, int netNo, RouterSettings settings) {
    this(board, settings, settings.getTraceCostArr());
    initNet(netNo, board, settings.getViaCosts());
  }

  /** Creates a new instance of AutorouteControl for the input net. */
  public AutorouteControl(
      RoutingBoard board,
      int netNo,
      RouterSettings settings,
      int viaCosts,
      ExpansionCostFactor[] traceCostArr) {
    this(board, settings, traceCostArr);
    initNet(netNo, board, viaCosts);
  }

  /** Creates a new instance of AutorouteControl. */
  private AutorouteControl(
      RoutingBoard board, RouterSettings settings, ExpansionCostFactor[] traceCostsArr) {
    this.settings = settings;
    layerCount = board.getLayerCount();
    traceHalfWidth = new int[layerCount];
    compensatedTraceHalfWidth = new int[layerCount];
    layerActive = new boolean[layerCount];
    viasAllowed = settings.getViasAllowed();
    viaRadiusArr = new double[layerCount];
    addViaCosts = new ViaCost[layerCount];
    this.bendCosts = new double[layerCount];
    for (int i = 0; i < layerCount; i++) {
      this.bendCosts[i] = settings.getBendCost(i);
    }

    for (int i = 0; i < layerCount; i++) {
      addViaCosts[i] = new ViaCost(layerCount);
      boolean activeSetting = settings.getLayerActive(i);
      if (!board.layerStructure.arr[i].isSignal && activeSetting) {
        FRLogger.warn(
            "Layer '"
                + board.layerStructure.arr[i].name
                + "' is a dedicated power plane and cannot be routed. "
                + "Forcing active state to false.");
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
    withNeckdown = settings.getAutomaticNeckdown();
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
    traceCosts = traceCostsArr;
    attachSmdAllowed = false;
    viaLowerBound = 0;
    viaUpperBound = layerCount;

    ripupAllowed = false;
    ripupCosts = 1000;
    ripupPassNo = 1;
  }

  private void initNet(int netNo, RoutingBoard board, int viaCosts) {
    this.netNo = netNo;
    Net currentNet = board.rules.nets.get(netNo);
    NetClass currNetClass;
    if (currentNet != null) {
      currNetClass = currentNet.getNetClass();
      traceClearanceClassNo = currNetClass.getTraceClearanceClass();
      viaRule = currNetClass.getViaRule();
    } else {
      traceClearanceClassNo = 1;
      viaRule = board.rules.viaRules.firstElement();
      currNetClass = null;
    }
    for (int i = 0; i < layerCount; i++) {
      if (netNo > 0) {
        traceHalfWidth[i] = board.rules.getTraceHalfWidth(netNo, i);
      } else {
        traceHalfWidth[i] = board.rules.getTraceHalfWidth(1, i);
      }
      compensatedTraceHalfWidth[i] =
          traceHalfWidth[i]
              + board.rules.clearanceMatrix.clearanceCompensationValue(traceClearanceClassNo, i);
      if (currNetClass != null && !currNetClass.isActiveRoutingLayer(i)) {
        layerActive[i] = false;
      }
    }
    rebuildViaInfo(board, viaCosts, netNo);
  }

  /** Rebuilds via info masks and costs for the specified board, via costs, and net. */
  public void rebuildViaInfo(RoutingBoard board, int viaCosts, int netNo) {
    if (viaRule.viaCount() > 0) {
      this.viaClearanceClass = viaRule.getVia(0).getClearanceClass();
    } else {
      this.viaClearanceClass = 1;
    }
    this.viaInfoArr = new ViaMask[viaRule.viaCount()];
    this.attachSmdAllowed = false;
    for (int i = 0; i < viaRule.viaCount(); i++) {
      ViaInfo currVia = viaRule.getVia(i);
      if (currVia.attachSmdAllowed()) {
        this.attachSmdAllowed = true;
      }
      Padstack currViaPadstack = currVia.getPadstack();
      int fromLayer = currViaPadstack.fromLayer();
      int toLayer = currViaPadstack.toLayer();
      for (int j = fromLayer; j <= toLayer; j++) {
        ConvexShape currShape = currViaPadstack.getShape(j);
        double currRadius;
        if (currShape != null) {
          currRadius = 0.5 * currShape.maxWidth();
        } else {
          currRadius = 0;
        }
        this.viaRadiusArr[j] = Math.max(this.viaRadiusArr[j], currRadius);
      }
      viaInfoArr[i] = new ViaMask(fromLayer, toLayer, currVia.attachSmdAllowed());
    }

    boolean pureSmdNet = isPureSmdNet(board, netNo);
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
    minNormalViaCost = viaCosts * viaCostFactor;
    minCheapViaCost = 0.8 * minNormalViaCost;
  }

  private static boolean isPureSmdNet(RoutingBoard board, int netNo) {
    Collection<Item> netItems = board.getConnectableItems(netNo);
    if (netItems.isEmpty()) {
      return false;
    }

    for (Item item : netItems) {
      if (!(item instanceof Pin pin) || pin.firstLayer() != pin.lastLayer()) {
        return false;
      }
    }

    return true;
  }

  /** Horizontal and vertical costs for traces on a board layer. */
  public static class ExpansionCostFactor {

    /** The horizontal expansion cost factor on a layer of the board. */
    public final double horizontal;

    /** The vertical expansion cost factor on a layer of the board. */
    public final double vertical;

    /** Constructs an ExpansionCostFactor with specified horizontal and vertical costs. */
    public ExpansionCostFactor(double horizontal, double vertical) {
      this.horizontal = horizontal;
      this.vertical = vertical;
    }
  }

  /** Array of via costs from one layer to the other layers. */
  static final class ViaCost {

    public int[] toLayer;

    private ViaCost(int layerCount) {
      toLayer = new int[layerCount];
    }
  }

  static class ViaMask {

    final int fromLayer;
    final int toLayer;
    final boolean attachSmdAllowed;

    ViaMask(int fromLayer, int toLayer, boolean attachSmdAllowed) {
      this.fromLayer = fromLayer;
      this.toLayer = toLayer;
      this.attachSmdAllowed = attachSmdAllowed;
    }
  }
}
