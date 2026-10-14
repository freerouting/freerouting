package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Same-layer pin connections before any escape via. Existing traces and vias stay fixed for the
 * pass, vias and rip-up are off, and a failed pin is left for the autorouter.
 *
 * <p>Ported from freerouting.net (Erik Gavriluk, Experimental Television Center). The pass walks
 * every eligible pin; elapsed time does not drop pins from the walk. The job's stop request still
 * ends it.
 */
final class DirectPinRouting {

  private DirectPinRouting() {}

  /** A null or non-positive {@code fanout.max_items} is unlimited, as in {@code BatchFanout}. */
  static int fanoutItemLimit(RouterSettings settings) {
    Integer configured = settings.fanout != null ? settings.fanout.maxItems : null;
    return configured != null && configured > 0 ? configured : Integer.MAX_VALUE;
  }

  static BatchFanout.FanoutRunSummary run(
      RoutingBoard board, RouterSettings settings, StoppableThread thread) {
    if (!settings.getRunRouter()) {
      throw new IllegalArgumentException("direct_first requires the autorouter stage");
    }
    long started = System.currentTimeMillis();
    List<Pin> pins = new ArrayList<>();
    for (Pin pin : board.getSmdPins()) {
      if (pin.netCount() == 1 && !pin.hasIgnoredNets()) {
        pins.add(pin);
      }
    }
    pins.sort(Comparator.comparingInt(Pin::getComponentId).thenComparingInt(pin -> pin.pinIndex));
    List<FixedCopper> fixed = new ArrayList<>();
    for (Item item : board.getItems()) {
      if (item instanceof Trace || item instanceof Via) {
        fixed.add(new FixedCopper(item.getId(), item.getFixedState()));
        item.setFixedState(FixedState.SYSTEM_FIXED);
      }
    }
    int attempts = 0;
    int routed = 0;
    int deferred = 0;
    int maxItems = DirectPinRouting.fanoutItemLimit(settings);
    boolean fanoutDisabled =
        settings.fanout != null
            && settings.fanout.maxPasses != null
            && settings.fanout.maxPasses <= 0;
    try {
      for (Pin pin : pins) {
        if (stopped(thread) || attempts >= maxItems || fanoutDisabled) {
          break;
        }
        int net = pin.getNetNumber(0);
        int layer = pin.firstLayer();
        Set<Item> source = pin.getConnectedSet(net);
        Set<Item> destinations = pin.getUnconnectedSet(net);
        if (destinations.isEmpty() || touchesPlane(source) || !onLayer(destinations, layer)) {
          continue;
        }
        attempts++;
        AutorouteAttemptResult result =
            routeOnLayer(board, settings, thread, pin, net, layer, false);
        if (result.state != AutorouteAttemptState.ROUTED
            && settings.getNeckWidthUm() > 0
            && !stopped(thread)) {
          result = routeOnLayer(board, settings, thread, pin, net, layer, true);
        }
        if (result.state == AutorouteAttemptState.ROUTED) {
          routed++;
        } else {
          deferred++;
        }
      }
    } finally {
      board.clearTransientAutorouteState();
      for (FixedCopper copper : fixed) {
        for (Item item : board.getItems()) {
          if (item.getId() == copper.id) {
            item.setFixedState(copper.state);
            break;
          }
        }
      }
    }
    BoardStatistics stats = new BoardStatistics(board);
    FRLogger.info(
        "Direct pin routing: "
            + routed
            + " complete surface connections, "
            + deferred
            + " deferred.");
    return new BatchFanout.FanoutRunSummary(
        1,
        System.currentTimeMillis() - started,
        BatchFanout.EscapeStatistics.fromBoardStatistics(stats),
        false);
  }

  private static AutorouteAttemptResult routeOnLayer(
      RoutingBoard board,
      RouterSettings settings,
      StoppableThread thread,
      Pin pin,
      int net,
      int layer,
      boolean neck) {
    AutorouteControl control = new AutorouteControl(board, net, settings);
    control.viasAllowed = false;
    control.ripupAllowed = false;
    control.removeUnconnectedVias = false;
    for (int i = 0; i < control.layerCount; i++) {
      control.layerActive[i] = control.layerActive[i] && i == layer;
    }
    if (!control.layerActive[layer]) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Pin layer is inactive.");
    }
    if (neck) {
      int neckHalfWidth = neckHalfWidth(board, settings);
      if (neckHalfWidth >= control.traceHalfWidth[layer]) {
        return new AutorouteAttemptResult(
            AutorouteAttemptState.FAILED, "Neck width is not narrower than the class width.");
      }
      int compensation = control.compensatedTraceHalfWidth[layer] - control.traceHalfWidth[layer];
      control.traceHalfWidth[layer] = neckHalfWidth;
      control.compensatedTraceHalfWidth[layer] = neckHalfWidth + compensation;
    }
    long perPinMs =
        settings.fanout != null && settings.fanout.maxMillisecondsPerPin != null
            ? settings.fanout.maxMillisecondsPerPin
            : 1000L;
    var engine =
        board.initAutoroute(
            net,
            control.traceClearanceClassIndex,
            thread,
            new TimeLimit((int) Math.min(perPinMs, Integer.MAX_VALUE)),
            false);
    return engine.autorouteConnection(
        pin.getConnectedSet(net), pin.getUnconnectedSet(net), control, new TreeSet<>(), null);
  }

  private static int neckHalfWidth(RoutingBoard board, RouterSettings settings) {
    int resolution = Math.max(1, board.communication.resolution);
    int neckWidth =
        (int)
            Math.round(
                Unit.scale(
                    settings.getNeckWidthUm() * resolution, Unit.UM, board.communication.unit));
    return Math.max(Math.max(1, neckWidth / 2), board.rules.getMinAllowedTraceHalfWidth());
  }

  private static boolean touchesPlane(Set<Item> source) {
    for (Item item : source) {
      if (item instanceof ConductionArea) {
        return true;
      }
    }
    return false;
  }

  private static boolean onLayer(Set<Item> destinations, int layer) {
    for (Item item : destinations) {
      if (item.firstLayer() <= layer && item.lastLayer() >= layer) {
        return true;
      }
    }
    return false;
  }

  private static boolean stopped(StoppableThread thread) {
    return thread != null && (thread.isStopRequested() || thread.isStopAutoRouterRequested());
  }

  private record FixedCopper(int id, FixedState state) {}
}
