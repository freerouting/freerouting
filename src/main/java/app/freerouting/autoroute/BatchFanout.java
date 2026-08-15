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

  private BatchFanout(RoutingBoard board, RouterSettings settings, StoppableThread thread) {
    this.thread = thread;
    this.routingBoard = board;
    this.settings = settings;
    String sortingOrder =
        settings.fanout != null && settings.fanout.pinSortingOrder != null
            ? settings.fanout.pinSortingOrder
            : "outer_first";
    Collection<app.freerouting.board.Pin> boardSmdPinList = routingBoard.getSmdPins();
    // Filter out SMD pins that belong to no net — they don't need fanout and would inflate
    // total pin counts and escape statistics.
    Collection<app.freerouting.board.Pin> boardSmdPinListWithNets = new LinkedList<>();
    for (app.freerouting.board.Pin pin : boardSmdPinList) {
      if (pin.netCount() > 0) {
        boardSmdPinListWithNets.add(pin);
      }
    }
    this.sortedComponents = new TreeSet<>();
    for (int i = 1; i <= routingBoard.components.count(); ++i) {
      app.freerouting.board.Component currentBoardComponent = routingBoard.components.get(i);
      Component currentComponent =
          new Component(currentBoardComponent, boardSmdPinListWithNets, sortingOrder, routingBoard);
      if (currentComponent.smdPinCount > 0) {
        sortedComponents.add(currentComponent);
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
        int netNumber = boardPin.getNetNumber(0);
        if (boardPin.getUnconnectedSet(netNumber).isEmpty()) {
          alreadyConnected++;
        }
      }
    }
    this.totalSmdPinCount = pinCount;
    this.alreadyConnectedPinCount = alreadyConnected;
  }

  /** Performs fanout routing for SMD components on board. */
  public static FanoutRunSummary fanoutBoard(
      RoutingBoard board, RouterSettings settings, StoppableThread thread) {
    return fanoutBoard(board, settings, thread, null);
  }

  /** Performs fanout routing for SMD components on board with a progress listener. */
  public static FanoutRunSummary fanoutBoard(
      RoutingBoard board,
      RouterSettings settings,
      StoppableThread thread,
      FanoutProgressListener progressListener) {
    BatchFanout fanoutInstance = new BatchFanout(board, settings, thread);
    long fanoutStart = System.currentTimeMillis();
    if (settings.fanout != null && settings.fanout.timeoutString != null) {
      Long timeoutSeconds =
          app.freerouting.util.TextManager.parseTimespanString(settings.fanout.timeoutString);
      if (timeoutSeconds != null) {
        fanoutInstance.deadlineMs = fanoutStart + timeoutSeconds * 1000;
      }
    }
    int maxPasses =
        settings.fanout != null && settings.fanout.maxPasses != null
            ? settings.fanout.maxPasses
            : 20;
    final int stagnationPassLimit = 3;
    int completedPasses = 0;
    long previousBoardState = Long.MIN_VALUE;
    int identicalPasses = 0;
    String lastBoardHash = board.getHash();
    for (int i = 0; i < maxPasses; ++i) {
      if (fanoutInstance.deadlineMs != null
          && System.currentTimeMillis() >= fanoutInstance.deadlineMs) {
        fanoutInstance.isTimedOut = true;
        FRLogger.info("Fanout stage timed out before starting pass #" + (i + 1));
        break;
      }
      if (settings.fanout != null
          && settings.fanout.maxItems != null
          && settings.fanout.maxItems > 0
          && fanoutInstance.totalItemsFanouted >= settings.fanout.maxItems) {
        break;
      }
      int routedCount = fanoutInstance.fanoutPass(i, progressListener);
      completedPasses++;
      if (routedCount == 0) {
        break;
      }
      // Oscillation detector, complementing the single-pass board-hash check below: a
      // fanout cycle where pins keep ripping each other's escapes alternates between two
      // board states, so consecutive hashes always differ — but the per-pass outcome
      // (routed count + via count) repeats exactly while ripup costs escalate uselessly
      // (observed: 14 identical passes on a dense SMD carrier).
      long boardState = ((long) routedCount << 32) ^ board.getVias().size();
      if (boardState == previousBoardState) {
        identicalPasses++;
        if (identicalPasses >= stagnationPassLimit) {
          FRLogger.info(
              "Fanout stopped after "
                  + completedPasses
                  + " passes: no progress for "
                  + stagnationPassLimit
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
      String currentBoardHash = board.getHash();
      if (currentBoardHash.equals(lastBoardHash)) {
        break;
      }
      lastBoardHash = currentBoardHash;
    }
    BoardStatistics stats = new BoardStatistics(board, null, false);
    EscapeStatistics finalEscape = EscapeStatistics.fromBoardStatistics(stats);
    long totalDurationMillis = Math.max(0, System.currentTimeMillis() - fanoutStart);
    return new FanoutRunSummary(
        completedPasses, totalDurationMillis, finalEscape, fanoutInstance.isTimedOut);
  }

  /** Routes a fanout pass and returns the number of new fanouted SMD-pins in this pass. */
  private int fanoutPass(int passNo, FanoutProgressListener progressListener) {
    long passStart = System.currentTimeMillis();
    int pinsToGo = this.totalSmdPinCount;
    int routedCount = 0;
    int notRoutedCount = 0;
    int insertErrorCount = 0;
    final int viasBeforePass = this.routingBoard.getVias().size();
    int ripupCosts = this.settings.getStartRipupCosts() * (passNo + 1);

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
            + (passNo + 1)
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
        passNo,
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
    int alreadyConnectedCount = 0;
    for (Component currentComponent : this.sortedComponents) {
      for (Component.Pin currentPin : currentComponent.smdPins) {
        if (this.settings.fanout != null
            && this.settings.fanout.maxItems != null
            && this.settings.fanout.maxItems > 0
            && this.totalItemsFanouted >= this.settings.fanout.maxItems) {
          FRLogger.info(
              "Max items limit reached (" + this.settings.fanout.maxItems + "). Stopping fanout.");
          maxLimitReached = true;
          break;
        }
        double maxMilliseconds = baseMillisPerPin * (passNo + 1);
        final TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);
        String fullPinName =
            currentComponent.boardComponent.name + "-" + currentPin.boardPin.name();
        int netNumber = currentPin.boardPin.getNetNumber(0);
        int targetCount = currentPin.boardPin.getUnconnectedSet(netNumber).size();

        app.freerouting.rules.Net net = this.routingBoard.rules.nets.get(netNumber);
        if (net != null) {
          app.freerouting.rules.NetClass netClass = net.getNetClass();
          app.freerouting.rules.ViaRule viaRule = netClass != null ? netClass.getViaRule() : null;
          boolean hasBoardVias =
              !this.routingBoard.rules.viaRules.isEmpty()
                  && this.routingBoard.rules.viaRules.firstElement().viaCount() > 0;
          boolean fallbackAllowed =
              this.settings.fanout != null
                  && Boolean.TRUE.equals(this.settings.fanout.fallbackToBoardVias)
                  && hasBoardVias;
          boolean canUseVias = (viaRule != null && viaRule.viaCount() > 0) || fallbackAllowed;
          if (!canUseVias) {
            FRLogger.debug(
                "BatchFanout: skipping pin "
                    + fullPinName
                    + " because its net class has no vias defined and fallback is "
                    + "disabled/unavailable.");
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
                + netNumber
                + ", targetCount="
                + targetCount
                + ", center="
                + currentPin.boardPin.getCenter()
                + ", layer="
                + currentPin.boardPin.firstLayer()
                + ", pass="
                + (passNo + 1),
            fullPinName,
            new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});

        this.routingBoard.startMarkingChangedArea();
        long pinStartNanos = System.nanoTime();
        AutorouteAttemptResult currentResult =
            this.routingBoard.fanout(
                currentPin.boardPin, this.settings, effectiveRipupCosts, this.thread, timeLimit);
        long pinDurationMs = (System.nanoTime() - pinStartNanos) / 1_000_000L;

        switch (currentResult.state) {
          case ROUTED -> {
            ++routedCount;
            this.totalItemsFanouted++;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_routed",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNumber
                    + ", durationMs="
                    + pinDurationMs
                    + ", targetCount="
                    + targetCount,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
          }
          case ALREADY_CONNECTED -> {
            ++alreadyConnectedCount;
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_already_connected",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNumber
                    + ", targetCount="
                    + targetCount
                    + ", detail="
                    + currentResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
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
                    + netNumber
                    + ", targetCount="
                    + targetCount
                    + ", durationMs="
                    + pinDurationMs
                    + ", detail="
                    + (currentResult.details == null || currentResult.details.isEmpty()
                        ? "no detail"
                        : currentResult.details),
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
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
                    + netNumber
                    + ", detail="
                    + (currentResult.details == null || currentResult.details.isEmpty()
                        ? "no detail"
                        : currentResult.details),
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
          }
          case NO_UNCONNECTED_NETS -> {
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_no_unconnected_nets",
                "pin=" + fullPinName + ", net=" + netNumber + ", detail=" + currentResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
          }
          default -> {
            FRLogger.trace(
                "BatchFanout.fanout_pass",
                "pin_other_state",
                "pin="
                    + fullPinName
                    + ", net="
                    + netNumber
                    + ", state="
                    + currentResult.state
                    + ", detail="
                    + currentResult.details,
                fullPinName,
                new app.freerouting.geometry.planar.Point[] {currentPin.boardPin.getCenter()});
          }
        }
        --pinsToGo;
        int extraViasThisPass = Math.max(0, this.routingBoard.getVias().size() - viasBeforePass);
        maybePublishProgress(
            progressListener,
            passNo,
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
              passNo,
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
        if (this.thread != null && this.thread.isStopAutoRouterRequested()) {
          BoardStatistics passStats = new BoardStatistics(this.routingBoard, null, false);
          EscapeStatistics escapeStats = EscapeStatistics.fromBoardStatistics(passStats);
          publishProgress(
              progressListener,
              passNo,
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
    int extraViasThisPass = Math.max(0, this.routingBoard.getVias().size() - viasBeforePass);
    this.extraViasTotal += extraViasThisPass;
    BoardStatistics passStats = new BoardStatistics(this.routingBoard, null, false);
    EscapeStatistics escapeStats = EscapeStatistics.fromBoardStatistics(passStats);

    long passDurationMs = System.currentTimeMillis() - passStart;
    FRLogger.trace(
        "BatchFanout.fanout_pass",
        "pass_end",
        "pass="
            + (passNo + 1)
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
              + (passNo + 1)
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
        passNo,
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
        progressStats.vias.totalCount = this.routingBoard.getVias().size();
        progressStats.traces.totalCount = this.routingBoard.getTraces().size();
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

  /** Listener for fanout pass progress updates. */
  @FunctionalInterface
  public interface FanoutProgressListener {
    /** Reports fanout pass progress. */
    void onProgress(FanoutPassStatus status);
  }

  /**
   * Statistics about how many SMD pins were successfully escaped after a fanout pass. A pin is
   * considered escaped when it has at least one Trace (wire) or Via directly connected to it (with
   * no clearance violations on the trace/via), or a Via that itself has a Trace connected to it
   * (also without clearance violations).
   */
  public record EscapeStatistics(int totalSmdPins, int escapedCount, double escapedPercentage) {

    /** Creates escape statistics from board statistics. */
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

  /** Status snapshot for a single fanout pass. */
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

  /** Summary of a complete fanout run. */
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
        app.freerouting.board.Component boardComponent,
        Collection<app.freerouting.board.Pin> boardSmdPinList,
        String pinSortingOrder,
        RoutingBoard routingBoard) {
      this.boardComponent = boardComponent;
      this.pinSortingOrder = pinSortingOrder;

      // calculate the center of gravity of all SMD pins of this component.
      Collection<app.freerouting.board.Pin> currentPinList = new LinkedList<>();
      int cmpNo = boardComponent.no;
      for (app.freerouting.board.Pin currentBoardPin : boardSmdPinList) {
        if (currentBoardPin.getComponentNo() == cmpNo) {
          currentPinList.add(currentBoardPin);
        }
      }
      double x = 0;
      double y = 0;
      for (app.freerouting.board.Pin currentPin : currentPinList) {
        FloatPoint currentPoint = currentPin.getCenter().toFloat();
        x += currentPoint.x;
        y += currentPoint.y;
      }
      this.smdPinCount = currentPinList.size();
      if (this.smdPinCount > 0) {
        x /= this.smdPinCount;
        y /= this.smdPinCount;
      }
      this.gravityCenterOfSmdPins = new FloatPoint(x, y);

      // calculate the sorted SMD pins of this component
      this.smdPins = new TreeSet<>();

      for (app.freerouting.board.Pin currentBoardPin : currentPinList) {
        this.smdPins.add(new Pin(currentBoardPin, boardSmdPinList, routingBoard));
      }
    }

    /** Sort the components, so that components with more pins come first. */
    @Override
    public int compareTo(Component other) {
      int compareValue = this.smdPinCount - other.smdPinCount;
      int result;
      if (compareValue > 0) {
        result = -1;
      } else if (compareValue < 0) {
        result = 1;
      } else {
        result = this.boardComponent.no - other.boardComponent.no;
      }
      return result;
    }

    class Pin implements Comparable<Pin> {

      final app.freerouting.board.Pin boardPin;
      final double distanceToComponentCenter;
      final double distanceToClosestOnNet;
      final int surroundingsDensity;

      Pin(
          app.freerouting.board.Pin boardPin,
          Collection<app.freerouting.board.Pin> boardSmdPinList,
          RoutingBoard routingBoard) {
        this.boardPin = boardPin;
        FloatPoint pinLocation = boardPin.getCenter().toFloat();
        this.distanceToComponentCenter = pinLocation.distance(gravityCenterOfSmdPins);

        // distanceToClosestOnNet calculation
        double minDistance = Double.MAX_VALUE;
        int netNumber = boardPin.netCount() > 0 ? boardPin.getNetNumber(0) : 0;
        if (netNumber > 0) {
          for (app.freerouting.board.Pin otherPin : routingBoard.getPins()) {
            if (otherPin != boardPin && otherPin.containsNet(netNumber)) {
              double dist = pinLocation.distance(otherPin.getCenter().toFloat());
              if (dist < minDistance) {
                minDistance = dist;
              }
            }
          }
        }
        this.distanceToClosestOnNet = minDistance;

        // surroundingsDensity calculation
        double resolution = routingBoard.communication.getResolution(app.freerouting.board.Unit.UM);
        double maxDist = 20000.0 * resolution; // 20.0 mm in coordinate units
        int density = 0;
        for (app.freerouting.board.Pin otherPin : boardSmdPinList) {
          if (otherPin != boardPin) {
            double dist = pinLocation.distance(otherPin.getCenter().toFloat());
            if (dist <= maxDist) {
              density++;
            }
          }
        }
        this.surroundingsDensity = density;
      }

      @Override
      public int compareTo(Pin other) {
        int result = 0;
        if ("inner_first".equals(pinSortingOrder)) {
          double deltaDist = this.distanceToComponentCenter - other.distanceToComponentCenter;
          if (deltaDist > 0) {
            result = 1;
          } else if (deltaDist < 0) {
            result = -1;
          }
        } else if ("outer_first".equals(pinSortingOrder)) {
          double deltaDist = this.distanceToComponentCenter - other.distanceToComponentCenter;
          if (deltaDist > 0) {
            result = -1;
          } else if (deltaDist < 0) {
            result = 1;
          }
        } else if ("distanceToClosestOnNet".equals(pinSortingOrder)) {
          double delta = this.distanceToClosestOnNet - other.distanceToClosestOnNet;
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        } else if ("surroundingsDensity".equals(pinSortingOrder)) {
          int delta = other.surroundingsDensity - this.surroundingsDensity; // densest first
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        }
        if (result == 0) {
          result = this.boardPin.pinNo - other.boardPin.pinNo;
        }
        return result;
      }
    }
  }
}
