package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.ProgressThrottler;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the fanout inside the batch autorouter. */
public final class BatchFanout {

  private final StoppableThread thread;
  private final RoutingBoard routingBoard;
  private final RouterSettings settings;
  private final SortedSet<Component> sortedComponents;
  private final int totalSmdPinCount;
  private final int alreadyConnectedPinCount;
  private final ProgressThrottler progressThrottler = new ProgressThrottler(1000);
  private int lastNotRoutedCount;
  private int extraViasTotal;
  public int totalItemsFanouted;
  private Long deadlineMs;
  private boolean isTimedOut;

  private BatchFanout(RoutingBoard p_board, RouterSettings p_settings, StoppableThread p_thread) {
    this.thread = p_thread;
    this.routingBoard = p_board;
    this.settings = p_settings;
    String sortingOrder =
        p_settings.fanout != null && p_settings.fanout.pinSortingOrder != null
            ? p_settings.fanout.pinSortingOrder
            : "outer_first";
    Collection<app.freerouting.board.Pin> boardSmdPinList = routingBoard.get_smd_pins();
    // Filter out SMD pins that belong to no net — they don't need fanout and would inflate
    // total pin counts and escape statistics.
    Collection<app.freerouting.board.Pin> boardSmdPinListWithNets = new LinkedList<>();
    for (app.freerouting.board.Pin pin : boardSmdPinList) {
      if (pin.net_count() > 0) {
        boardSmdPinListWithNets.add(pin);
      }
    }
    this.sortedComponents = new TreeSet<>();
    for (int i = 1; i <= routingBoard.components.count(); ++i) {
      app.freerouting.board.Component currBoardComponent = routingBoard.components.get(i);
      Component currComponent =
          new Component(currBoardComponent, boardSmdPinListWithNets, sortingOrder, routingBoard);
      if (currComponent.smdPinCount > 0) {
        sortedComponents.add(currComponent);
      }
    }
    int pinCount = 0;
    int alreadyConnected = 0;
    for (Component component : sortedComponents) {
      pinCount += component.smdPinCount;
      for (Component.Pin pin : component.smdPins) {
        // A pin is already connected if all items in its connected set are on the pin's layer
        // and its unconnected set is empty — same logic as RoutingBoard.fanout().
        app.freerouting.board.Pin boardPin = pin.boardPin;
        int netNo = boardPin.get_net_no(0);
        if (boardPin.get_unconnected_set(netNo).isEmpty()) {
          alreadyConnected++;
        }
      }
    }
    this.totalSmdPinCount = pinCount;
    this.alreadyConnectedPinCount = alreadyConnected;
  }

  public static FanoutRunSummary fanout_board(
      RoutingBoard p_board, RouterSettings p_settings, StoppableThread p_thread) {
    return fanout_board(p_board, p_settings, p_thread, null);
  }

  public static FanoutRunSummary fanout_board(
      RoutingBoard p_board,
      RouterSettings p_settings,
      StoppableThread p_thread,
      FanoutProgressListener progressListener) {
    BatchFanout fanoutInstance = new BatchFanout(p_board, p_settings, p_thread);
    long fanoutStart = System.currentTimeMillis();
    if (p_settings.fanout != null && p_settings.fanout.timeoutString != null) {
      Long timeoutSeconds =
          app.freerouting.util.TextManager.parseTimespanString(p_settings.fanout.timeoutString);
      if (timeoutSeconds != null) {
        fanoutInstance.deadlineMs = fanoutStart + timeoutSeconds * 1000;
      }
    }
    int maxPasses =
        p_settings.fanout != null && p_settings.fanout.maxPasses != null
            ? p_settings.fanout.maxPasses
            : 20;
    final int STAGNATION_PASS_LIMIT = 3;
    int completedPasses = 0;
    long previousBoardState = Long.MIN_VALUE;
    int identicalPasses = 0;
    String lastBoardHash = p_board.get_hash();
    for (int i = 0; i < maxPasses; ++i) {
      if (fanoutInstance.deadlineMs != null
          && System.currentTimeMillis() >= fanoutInstance.deadlineMs) {
        fanoutInstance.isTimedOut = true;
        FRLogger.info("Fanout stage timed out before starting pass #" + (i + 1));
        break;
      }
      if (p_settings.fanout != null
          && p_settings.fanout.maxItems != null
          && p_settings.fanout.maxItems > 0
          && fanoutInstance.totalItemsFanouted >= p_settings.fanout.maxItems) {
        break;
      }
      int routedCount = fanoutInstance.fanout_pass(i, progressListener);
      completedPasses++;
      if (routedCount == 0) {
        break;
      }
      // Oscillation detector, complementing the single-pass board-hash check below: a
      // fanout cycle where pins keep ripping each other's escapes alternates between two
      // board states, so consecutive hashes always differ — but the per-pass outcome
      // (routed count + via count) repeats exactly while ripup costs escalate uselessly
      // (observed: 14 identical passes on a dense SMD carrier).
      long boardState = ((long) routedCount << 32) ^ p_board.get_vias().size();
      if (boardState == previousBoardState) {
        identicalPasses++;
        if (identicalPasses >= STAGNATION_PASS_LIMIT) {
          FRLogger.info(
              "Fanout stopped after "
                  + completedPasses
                  + " passes: no progress for "
                  + STAGNATION_PASS_LIMIT
                  + " consecutive passes.");
          break;
        }
      } else {
        identicalPasses = 0;
        previousBoardState = boardState;
      }
      if (fanoutInstance.isTimedOut) {
        break;
      }
      String currentBoardHash = p_board.get_hash();
      if (currentBoardHash.equals(lastBoardHash)) {
        break;
      }
      lastBoardHash = currentBoardHash;
    }
    BoardStatistics stats = new BoardStatistics(p_board, null, false);
    EscapeStatistics finalEscape = EscapeStatistics.fromBoardStatistics(stats);
    long totalDurationMillis = Math.max(0, System.currentTimeMillis() - fanoutStart);
    return new FanoutRunSummary(
        completedPasses, totalDurationMillis, finalEscape, fanoutInstance.isTimedOut);
  }

  /** Routes a fanout pass and returns the number of new fanouted SMD-pins in this pass. */
  private int fanout_pass(int p_pass_no, FanoutProgressListener progressListener) {
    long passStart = System.currentTimeMillis();
    int pinsToGo = this.totalSmdPinCount;
    int routedCount = 0;
    int notRoutedCount = 0;
    int insertErrorCount = 0;
    int alreadyConnectedCount = 0;
    int viasBeforePass = this.routingBoard.get_vias().size();
    int ripupCosts = this.settings.get_start_ripup_costs() * (p_pass_no + 1);

    long baseMillisPerPin =
        this.settings.fanout != null && this.settings.fanout.maxMillisecondsPerPin != null
            ? this.settings.fanout.maxMillisecondsPerPin
            : 10000L;
    boolean ripupAllowed =
        (this.settings.fanout == null || this.settings.fanout.ripupAllowed == null)
            || Boolean.TRUE.equals(this.settings.fanout.ripupAllowed);
    // Negative ripup costs signal "no ripup" to RoutingBoard.fanout()
    int effectiveRipupCosts = ripupAllowed ? ripupCosts : -1;

    FRLogger.trace(
        "BatchFanout.fanout_pass",
        "pass_start",
        "pass="
            + (p_pass_no + 1)
            + ", totalPins="
            + this.totalSmdPinCount
            + ", alreadyConnected="
            + this.alreadyConnectedPinCount
            + ", pinsToFanout="
            + (this.totalSmdPinCount - this.alreadyConnectedPinCount)
            + ", ripupCosts="
            + effectiveRipupCosts
            + ", baseMillisPerPin="
            + baseMillisPerPin,
        "",
        new app.freerouting.geometry.planar.Point[0]);

    this.progressThrottler.reset();
    BoardStatistics progressStats = new BoardStatistics(this.routingBoard, null, false);
    publishProgress(
        progressListener,
        p_pass_no,
        ripupCosts,
        pinsToGo,
        routedCount,
        notRoutedCount,
        insertErrorCount,
        0,
        new EscapeStatistics(this.totalSmdPinCount, 0, 0.0),
        false,
        passStart,
        progressStats);
    boolean maxLimitReached = false;
    for (Component currComponent : this.sortedComponents) {
      for (Component.Pin currPin : currComponent.smdPins) {
        if (this.settings.fanout != null
            && this.settings.fanout.maxItems != null
            && this.settings.fanout.maxItems > 0
            && this.totalItemsFanouted >= this.settings.fanout.maxItems) {
          FRLogger.info(
              "Max items limit reached (" + this.settings.fanout.maxItems + "). Stopping fanout.");
          maxLimitReached = true;
          break;
        }
        double maxMilliseconds = baseMillisPerPin * (p_pass_no + 1);
        TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);
        String fullPinName = currComponent.boardComponent.name + "-" + currPin.boardPin.name();
        int netNo = currPin.boardPin.get_net_no(0);
        int targetCount = currPin.boardPin.get_unconnected_set(netNo).size();

        app.freerouting.rules.Net net = this.routingBoard.rules.nets.get(netNo);
        if (net != null) {
          app.freerouting.rules.NetClass netClass = net.get_class();
          app.freerouting.rules.ViaRule viaRule = netClass != null ? netClass.get_via_rule() : null;
          boolean hasBoardVias =
              !this.routingBoard.rules.viaRules.isEmpty()
                  && this.routingBoard.rules.viaRules.firstElement().via_count() > 0;
          boolean fallbackAllowed =
              this.settings.fanout != null
                  && Boolean.TRUE.equals(this.settings.fanout.fallbackToBoardVias)
                  && hasBoardVias;
          boolean canUseVias = (viaRule != null && viaRule.via_count() > 0) || fallbackAllowed;
          if (!canUseVias) {
            FRLogger.debug(
                "BatchFanout: skipping pin "
                    + fullPinName
                    + " because its net class has no vias defined and fallback is disabled/unavailable.");
            --pinsToGo;
            continue;
          }
        }

        FRLogger.trace(
            "BatchFanout.fanout_pass",
            "pin_start",
            "pin="
                + fullPinName
                + ", net="
                + netNo
                + ", targetCount="
                + targetCount
                + ", center="
                + currPin.boardPin.get_center()
                + ", layer="
                + currPin.boardPin.first_layer()
                + ", pass="
                + (p_pass_no + 1),
            fullPinName,
            new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});

        this.routingBoard.start_marking_changed_area();
        long pinStartNanos = System.nanoTime();
        AutorouteAttemptResult currResult =
            this.routingBoard.fanout(
                currPin.boardPin, this.settings, effectiveRipupCosts, this.thread, timeLimit);
        long pinDurationMs = (System.nanoTime() - pinStartNanos) / 1_000_000L;

        switch (currResult.state) {
          case ROUTED -> {
            ++routedCount;
            this.totalItemsFanouted++;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_routed",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNo
                    + ", durationMs="
                    + pinDurationMs
                    + ", targetCount="
                    + targetCount,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
          case ALREADY_CONNECTED -> {
            ++alreadyConnectedCount;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_already_connected",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNo
                    + ", targetCount="
                    + targetCount
                    + ", detail="
                    + currResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
          case FAILED -> {
            ++notRoutedCount;
            this.totalItemsFanouted++;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_failed",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNo
                    + ", targetCount="
                    + targetCount
                    + ", durationMs="
                    + pinDurationMs
                    + ", detail="
                    + (currResult.details == null || currResult.details.isEmpty()
                        ? "no detail"
                        : currResult.details),
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
          case INSERT_ERROR -> {
            ++insertErrorCount;
            this.totalItemsFanouted++;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_insert_error",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNo
                    + ", detail="
                    + (currResult.details == null || currResult.details.isEmpty()
                        ? "no detail"
                        : currResult.details),
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
          case NO_UNCONNECTED_NETS -> {
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_no_unconnected_nets",
                "pin=" + fullPinName + ", net=" + netNo + ", detail=" + currResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
          default -> {
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_other_state",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNo
                    + ", state="
                    + currResult.state
                    + ", detail="
                    + currResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currPin.boardPin.get_center()});
          }
        }
        --pinsToGo;
        int extraViasThisPass = Math.max(0, this.routingBoard.get_vias().size() - viasBeforePass);
        maybePublishProgress(
            progressListener,
            p_pass_no,
            ripupCosts,
            pinsToGo,
            routedCount,
            notRoutedCount,
            insertErrorCount,
            extraViasThisPass,
            false,
            passStart,
            progressStats);
        if (this.deadlineMs != null && System.currentTimeMillis() >= this.deadlineMs) {
          FRLogger.info("Fanout stage timed out.");
          this.isTimedOut = true;
          BoardStatistics passStats = new BoardStatistics(this.routingBoard, null, false);
          EscapeStatistics escapeStats = EscapeStatistics.fromBoardStatistics(passStats);
          publishProgress(
              progressListener,
              p_pass_no,
              ripupCosts,
              pinsToGo,
              routedCount,
              notRoutedCount,
              insertErrorCount,
              extraViasThisPass,
              escapeStats,
              true,
              passStart,
              passStats);
          return routedCount;
        }
        if (this.thread != null && this.thread.is_stop_auto_router_requested()) {
          BoardStatistics passStats = new BoardStatistics(this.routingBoard, null, false);
          EscapeStatistics escapeStats = EscapeStatistics.fromBoardStatistics(passStats);
          publishProgress(
              progressListener,
              p_pass_no,
              ripupCosts,
              pinsToGo,
              routedCount,
              notRoutedCount,
              insertErrorCount,
              extraViasThisPass,
              escapeStats,
              true,
              passStart,
              passStats);
          return routedCount;
        }
      }
      if (maxLimitReached) {
        break;
      }
    }
    int extraViasThisPass = Math.max(0, this.routingBoard.get_vias().size() - viasBeforePass);
    this.extraViasTotal += extraViasThisPass;
    BoardStatistics passStats = new BoardStatistics(this.routingBoard, null, false);
    EscapeStatistics escapeStats = EscapeStatistics.fromBoardStatistics(passStats);

    long passDurationMs = System.currentTimeMillis() - passStart;
    FRLogger.trace(
        "BatchFanout.fanout_pass",
        "pass_end",
        "pass="
            + (p_pass_no + 1)
            + ", durationMs="
            + passDurationMs
            + ", routed="
            + routedCount
            + ", notRouted="
            + notRoutedCount
            + ", insertErrors="
            + insertErrorCount
            + ", alreadyConnected="
            + alreadyConnectedCount
            + ", escaped="
            + escapeStats.escapedCount()
            + "/"
            + escapeStats.totalSmdPins()
            + " ("
            + String.format("%.1f", escapeStats.escapedPercentage())
            + "%)",
        "",
        new app.freerouting.geometry.planar.Point[0]);

    if (progressListener == null) {
      FRLogger.info(
          "fanout pass: "
              + (p_pass_no + 1)
              + ", routed: "
              + routedCount
              + ", not routed: "
              + notRoutedCount
              + ", errors: "
              + insertErrorCount
              + ", extra vias: +"
              + extraViasThisPass
              + ", escaped SMD pins: "
              + escapeStats.escapedCount()
              + "/"
              + escapeStats.totalSmdPins()
              + " ("
              + String.format("%.1f", escapeStats.escapedPercentage())
              + "%)");
    }
    this.lastNotRoutedCount = notRoutedCount;
    publishProgress(
        progressListener,
        p_pass_no,
        ripupCosts,
        pinsToGo,
        routedCount,
        notRoutedCount,
        insertErrorCount,
        extraViasThisPass,
        escapeStats,
        true,
        passStart,
        passStats);

    return routedCount;
  }

  private void maybePublishProgress(
      FanoutProgressListener progressListener,
      int passNo,
      int ripupCosts,
      int pinsToGo,
      int routedCount,
      int notRoutedCount,
      int insertErrorCount,
      int extraViasThisPass,
      boolean passCompleted,
      long passStart,
      BoardStatistics progressStats) {
    if (passCompleted || progressThrottler.shouldUpdate()) {
      // Mid-pass interim updates use a lightweight empty escape statistics placeholder
      // to avoid the cost of a full escape scan on every tick.
      EscapeStatistics interimEscape = new EscapeStatistics(this.totalSmdPinCount, 0, 0.0);
      if (progressStats != null) {
        progressStats.vias.totalCount = this.routingBoard.get_vias().size();
        progressStats.traces.totalCount = this.routingBoard.get_traces().size();
      }
      publishProgress(
          progressListener,
          passNo,
          ripupCosts,
          pinsToGo,
          routedCount,
          notRoutedCount,
          insertErrorCount,
          extraViasThisPass,
          interimEscape,
          passCompleted,
          passStart,
          progressStats);
    }
  }

  private void publishProgress(
      FanoutProgressListener progressListener,
      int passNo,
      int ripupCosts,
      int pinsToGo,
      int routedCount,
      int notRoutedCount,
      int insertErrorCount,
      int extraViasThisPass,
      EscapeStatistics escapeStatistics,
      boolean passCompleted,
      long passStart,
      BoardStatistics boardStatistics) {
    if (progressListener == null) {
      return;
    }
    long duration = Math.max(0, System.currentTimeMillis() - passStart);
    progressListener.onProgress(
        new FanoutPassStatus(
            passNo + 1,
            ripupCosts,
            this.totalSmdPinCount,
            pinsToGo,
            routedCount,
            notRoutedCount,
            insertErrorCount,
            extraViasThisPass,
            this.extraViasTotal + extraViasThisPass,
            duration,
            boardStatistics,
            passCompleted,
            escapeStatistics));
  }

  @FunctionalInterface
  public interface FanoutProgressListener {
    void onProgress(FanoutPassStatus status);
  }

  /**
   * Statistics about how many SMD pins were successfully escaped after a fanout pass. A pin is
   * considered escaped when it has at least one Trace (wire) or Via directly connected to it (with
   * no clearance violations on the trace/via), or a Via that itself has a Trace connected to it
   * (also without clearance violations).
   */
  public record EscapeStatistics(int totalSmdPins, int escapedCount, double escapedPercentage) {

    public static EscapeStatistics fromBoardStatistics(BoardStatistics stats) {
      double percentage =
          stats.fanout.totalSmdPins > 0
              ? (stats.fanout.escapedCount * 100.0) / stats.fanout.totalSmdPins
              : 0.0;
      return new EscapeStatistics(stats.fanout.totalSmdPins, stats.fanout.escapedCount, percentage);
    }

    @Override
    public String toString() {
      return String.format("%d/%d (%.1f%%)", escapedCount, totalSmdPins, escapedPercentage);
    }
  }

  public record FanoutPassStatus(
      int passNo,
      int ripupCosts,
      int totalPins,
      int pinsToGo,
      int routedCount,
      int notRoutedCount,
      int insertErrorCount,
      int extraViasThisPass,
      int extraViasTotal,
      long passDurationMillis,
      BoardStatistics boardStatistics,
      boolean passCompleted,
      EscapeStatistics escapeStatistics) {}

  public record FanoutRunSummary(
      int completedPassCount,
      long totalDurationMillis,
      EscapeStatistics escapeStatistics,
      boolean isTimedOut) {}

  private static class Component implements Comparable<Component> {

    final app.freerouting.board.Component boardComponent;
    final int smdPinCount;
    final SortedSet<Pin> smdPins;

    /** The center of gravity of all SMD pins of this component. */
    final FloatPoint gravityCenterOfSmdPins;

    final String pinSortingOrder;

    Component(
        app.freerouting.board.Component p_board_component,
        Collection<app.freerouting.board.Pin> p_board_smd_pin_list,
        String p_pin_sorting_order,
        RoutingBoard p_routing_board) {
      this.boardComponent = p_board_component;
      this.pinSortingOrder = p_pin_sorting_order;

      // calculate the center of gravity of all SMD pins of this component.
      Collection<app.freerouting.board.Pin> currPinList = new LinkedList<>();
      int cmpNo = p_board_component.no;
      for (app.freerouting.board.Pin curr_board_pin : p_board_smd_pin_list) {
        if (curr_board_pin.get_component_no() == cmpNo) {
          currPinList.add(curr_board_pin);
        }
      }
      double x = 0;
      double y = 0;
      for (app.freerouting.board.Pin currPin : currPinList) {
        FloatPoint currPoint = currPin.get_center().to_float();
        x += currPoint.x;
        y += currPoint.y;
      }
      this.smdPinCount = currPinList.size();
      if (this.smdPinCount > 0) {
        x /= this.smdPinCount;
        y /= this.smdPinCount;
      }
      this.gravityCenterOfSmdPins = new FloatPoint(x, y);

      // calculate the sorted SMD pins of this component
      this.smdPins = new TreeSet<>();

      for (app.freerouting.board.Pin curr_board_pin : currPinList) {
        this.smdPins.add(new Pin(curr_board_pin, p_board_smd_pin_list, p_routing_board));
      }
    }

    /** Sort the components, so that components with more pins come first */
    @Override
    public int compareTo(Component p_other) {
      int compareValue = this.smdPinCount - p_other.smdPinCount;
      int result;
      if (compareValue > 0) {
        result = -1;
      } else if (compareValue < 0) {
        result = 1;
      } else {
        result = this.boardComponent.no - p_other.boardComponent.no;
      }
      return result;
    }

    class Pin implements Comparable<Pin> {

      final app.freerouting.board.Pin boardPin;
      final double distanceToComponentCenter;
      final double distanceToClosestOnNet;
      final int surroundingsDensity;

      Pin(
          app.freerouting.board.Pin p_board_pin,
          Collection<app.freerouting.board.Pin> p_board_smd_pin_list,
          RoutingBoard p_routing_board) {
        this.boardPin = p_board_pin;
        FloatPoint pinLocation = p_board_pin.get_center().to_float();
        this.distanceToComponentCenter = pinLocation.distance(gravityCenterOfSmdPins);

        // distanceToClosestOnNet calculation
        double minDistance = Double.MAX_VALUE;
        int netNo = p_board_pin.net_count() > 0 ? p_board_pin.get_net_no(0) : 0;
        if (netNo > 0) {
          for (app.freerouting.board.Pin otherPin : p_routing_board.get_pins()) {
            if (otherPin != p_board_pin && otherPin.contains_net(netNo)) {
              double dist = pinLocation.distance(otherPin.get_center().to_float());
              if (dist < minDistance) {
                minDistance = dist;
              }
            }
          }
        }
        this.distanceToClosestOnNet = minDistance;

        // surroundingsDensity calculation
        double resolution =
            p_routing_board.communication.get_resolution(app.freerouting.board.Unit.UM);
        double maxDist = 20000.0 * resolution; // 20.0 mm in coordinate units
        int density = 0;
        for (app.freerouting.board.Pin otherPin : p_board_smd_pin_list) {
          if (otherPin != p_board_pin) {
            double dist = pinLocation.distance(otherPin.get_center().to_float());
            if (dist <= maxDist) {
              density++;
            }
          }
        }
        this.surroundingsDensity = density;
      }

      @Override
      public int compareTo(Pin p_other) {
        int result = 0;
        if ("inner_first".equals(pinSortingOrder)) {
          double deltaDist = this.distanceToComponentCenter - p_other.distanceToComponentCenter;
          if (deltaDist > 0) {
            result = 1;
          } else if (deltaDist < 0) {
            result = -1;
          }
        } else if ("outer_first".equals(pinSortingOrder)) {
          double deltaDist = this.distanceToComponentCenter - p_other.distanceToComponentCenter;
          if (deltaDist > 0) {
            result = -1;
          } else if (deltaDist < 0) {
            result = 1;
          }
        } else if ("distanceToClosestOnNet".equals(pinSortingOrder)) {
          double delta = this.distanceToClosestOnNet - p_other.distanceToClosestOnNet;
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        } else if ("surroundingsDensity".equals(pinSortingOrder)) {
          int delta = p_other.surroundingsDensity - this.surroundingsDensity; // densest first
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        }
        if (result == 0) {
          result = this.boardPin.pinNo - p_other.boardPin.pinNo;
        }
        return result;
      }
    }
  }
}
