package app.freerouting.autoroute.maze;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.library.Padstack;
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

  public final int layerCount;

  /** The currently used trace half widths in the autoroute algorithm on each layer. */
  public final int[] traceHalfWidth;

  /**
   * The currently used compensated trace half widths in the autoroute algorithm on each layer.
   * Equal to traceHalfWidth if no clearance compensation is used.
   */
  public final int[] compensatedTraceHalfWidth;

  public final double[] viaRadii;

  /** The additional costs to min_normal via_cost for inserting a via between 2 layers. */
  public final ViaCost[] addViaCosts;

  /** The currently used clearance class for traces in the autoroute algorithm. */
  public int traceClearanceClassIndex;

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

  /** The possible (partial) vias, which can be used by the autorouter. */
  public ViaRule viaRule;

  /** The currently used net number in the autoroute algorithm. */
  public int netNumber;

  /** The currently used clearance class for vias in the autoroute algorithm. */
  public int viaClearanceClass;

  /** The array of possible via ranges used by the autorouter. */
  public ViaMask[] viaInfos;

  /** The lower bound for the first layer of vias. */
  public int viaLowerBound;

  /** The upper bound for the last layer of vias. */
  public int viaUpperBound;

  public double maxViaRadius;

  /** The width of the region around changed traces, where traces are pulled tight. */
  public int tidyRegionWidth;

  /** The pull tight accuracy of traces. */
  public int pullTightAccuracy;

  /** The maximum recursion depth for shoving traces. */
  public int maxShoveTraceRecursionDepth;

  /** The maximum recursion depth for shoving obstacles. */
  public int maxShoveViaRecursionDepth;

  /** The maximum recursion depth for traces springing over obstacles. */
  public int maxSpringOverRecursionDepth;

  /** The minimal cost value of all cheap vias. */
  public double minCheapViaCost;

  /** Creates a new instance of AutorouteControl for the input net. */
  public AutorouteControl(RoutingBoard board, int netNumber, RouterSettings settings) {
    this(board, settings, settings.getTraceCosts());
    initNet(netNumber, board, settings.getViaCosts());
  }

  /** Creates a new instance of AutorouteControl for the input net. */
  public AutorouteControl(
      RoutingBoard board,
      int netNumber,
      RouterSettings settings,
      int viaCosts,
      ExpansionCostFactor[] traceCosts) {
    this(board, settings, traceCosts);
    initNet(netNumber, board, viaCosts);
  }

  /** Creates a new instance of AutorouteControl. */
  private AutorouteControl(
      RoutingBoard board, RouterSettings settings, ExpansionCostFactor[] traceCosts) {
    this.settings = settings;
    layerCount = board.getLayerCount();
    traceHalfWidth = new int[layerCount];
    compensatedTraceHalfWidth = new int[layerCount];
    layerActive = new boolean[layerCount];
    viasAllowed = settings.getViasAllowed();
    viaRadii = new double[layerCount];
    addViaCosts = new ViaCost[layerCount];
    this.bendCosts = new double[layerCount];
    for (int i = 0; i < layerCount; i++) {
      this.bendCosts[i] = settings.getBendCost(i);
    }

    for (int i = 0; i < layerCount; i++) {
      addViaCosts[i] = new ViaCost(layerCount);
      boolean activeSetting = settings.getLayerActive(i);
      if (!board.layerStructure.layers[i].isSignal && activeSetting) {
        FRLogger.warn(
            "Layer '"
                + board.layerStructure.layers[i].name
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
    this.traceCosts = traceCosts;
    attachSmdAllowed = false;
    viaLowerBound = 0;
    viaUpperBound = layerCount;

    ripupAllowed = false;
    ripupCosts = 1000;
    ripupPassNo = 1;
  }

  private static boolean isPureSmdNet(RoutingBoard board, int netNumber) {
    Collection<Item> netItems = board.getConnectableItems(netNumber);
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

  private void initNet(int netNumber, RoutingBoard board, int viaCosts) {
    this.netNumber = netNumber;
    Net currentNet = board.rules.nets.get(netNumber);
    NetClass currentNetClass;
    if (currentNet != null) {
      currentNetClass = currentNet.getNetClass();
      traceClearanceClassIndex = currentNetClass.getTraceClearanceClass();
      viaRule = currentNetClass.getViaRule();
    } else {
      traceClearanceClassIndex = 1;
      viaRule = board.rules.viaRules.firstElement();
      currentNetClass = null;
    }
    for (int i = 0; i < layerCount; i++) {
      if (netNumber > 0) {
        traceHalfWidth[i] = board.rules.getTraceHalfWidth(netNumber, i);
      } else {
        traceHalfWidth[i] = board.rules.getTraceHalfWidth(1, i);
      }
      compensatedTraceHalfWidth[i] =
          traceHalfWidth[i]
              + board.rules.clearanceMatrix.clearanceCompensationValue(traceClearanceClassIndex, i);
      if (currentNetClass != null && !currentNetClass.isActiveRoutingLayer(i)) {
        layerActive[i] = false;
      }
    }
    rebuildViaInfo(board, viaCosts, netNumber);
  }

  /** Rebuilds via info masks and costs for the specified board, via costs, and net. */
  public void rebuildViaInfo(RoutingBoard board, int viaCosts, int netNumber) {
    if (viaRule.viaCount() > 0) {
      this.viaClearanceClass = viaRule.getVia(0).getClearanceClassIndex();
    } else {
      this.viaClearanceClass = 1;
    }
    this.viaInfos = new ViaMask[viaRule.viaCount()];
    this.attachSmdAllowed = false;
    for (int i = 0; i < viaRule.viaCount(); i++) {
      ViaInfo currentVia = viaRule.getVia(i);
      if (currentVia.attachSmdAllowed()) {
        this.attachSmdAllowed = true;
      }
      Padstack currentViaPadstack = currentVia.getPadstack();
      int fromLayer = currentViaPadstack.fromLayer();
      int toLayer = currentViaPadstack.toLayer();
      for (int j = fromLayer; j <= toLayer; j++) {
        ConvexShape currentShape = currentViaPadstack.getShape(j);
        double currentRadius;
        if (currentShape != null) {
          currentRadius = 0.5 * currentShape.maxWidth();
        } else {
          currentRadius = 0;
        }
        this.viaRadii[j] = Math.max(this.viaRadii[j], currentRadius);
      }
      viaInfos[i] = new ViaMask(fromLayer, toLayer, currentVia.attachSmdAllowed());
    }

    boolean pureSmdNet = isPureSmdNet(board, netNumber);
    if (!this.attachSmdAllowed && layerCount > 1 && pureSmdNet) {
      // Pure SMD nets must still be able to escape their component layer, even if the DSN marks
      // every padstack as attach-off. This only relaxes the routing gate for same-net fanout;
      // cross-net DRC remains governed by the padstack's attach flag.
      this.attachSmdAllowed = true;
    }

    for (int j = 0; j < this.layerCount; j++) {
      this.viaRadii[j] = Math.max(this.viaRadii[j], traceHalfWidth[j]);
      this.maxViaRadius = Math.max(this.maxViaRadius, this.viaRadii[j]);
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

  /** Horizontal and vertical costs for traces on a board layer. */
  public record ExpansionCostFactor(double horizontal, double vertical) {}

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
