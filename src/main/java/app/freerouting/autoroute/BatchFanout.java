package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.ProgressThrottler;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the fanout inside the batch autorouter. */
public class BatchFanout {

  private final StoppableThread thread;
  private final RoutingBoard routing_board;
  private final RouterSettings settings;
  private final SortedSet<Component> sorted_components;
  private final int totalSmdPinCount;
  private final int alreadyConnectedPinCount;
  private final ProgressThrottler progressThrottler = new ProgressThrottler(1000);
  private int lastNotRoutedCount;
  private int extraViasTotal;
  private BatchFanout(RoutingBoard p_board, RouterSettings p_settings, StoppableThread p_thread) {
    this.thread = p_thread;
    this.routing_board = p_board;
    this.settings = p_settings;
    String sortingOrder = (p_settings.fanout != null && p_settings.fanout.pinSortingOrder != null)
        ? p_settings.fanout.pinSortingOrder : "outer_first";
    Collection<app.freerouting.board.Pin> board_smd_pin_list = routing_board.get_smd_pins();
    // Filter out SMD pins that belong to no net — they don't need fanout and would inflate
    // total pin counts and escape statistics.
    Collection<app.freerouting.board.Pin> board_smd_pin_list_with_nets = new LinkedList<>();
    for (app.freerouting.board.Pin pin : board_smd_pin_list) {
      if (pin.net_count() > 0) {
        board_smd_pin_list_with_nets.add(pin);
      }
    }
    this.sorted_components = new TreeSet<>();
    for (int i = 1; i <= routing_board.components.count(); ++i) {
      app.freerouting.board.Component curr_board_component = routing_board.components.get(i);
      Component curr_component = new Component(curr_board_component, board_smd_pin_list_with_nets, sortingOrder, routing_board);
      if (curr_component.smd_pin_count > 0) {
        sorted_components.add(curr_component);
      }
    }
    int pinCount = 0;
    int alreadyConnected = 0;
    for (Component component : sorted_components) {
      pinCount += component.smd_pin_count;
      for (Component.Pin pin : component.smd_pins) {
        // A pin is already connected if all items in its connected set are on the pin's layer
        // and its unconnected set is empty — same logic as RoutingBoard.fanout().
        app.freerouting.board.Pin boardPin = pin.board_pin;
        int netNo = boardPin.get_net_no(0);
        if (boardPin.get_unconnected_set(netNo).isEmpty()) {
          alreadyConnected++;
        }
      }
    }
    this.totalSmdPinCount = pinCount;
    this.alreadyConnectedPinCount = alreadyConnected;
  }

  public static FanoutRunSummary fanout_board(RoutingBoard p_board, RouterSettings p_settings,
      StoppableThread p_thread) {
    return fanout_board(p_board, p_settings, p_thread, null);
  }

  public static FanoutRunSummary fanout_board(RoutingBoard p_board, RouterSettings p_settings,
      StoppableThread p_thread,
      FanoutProgressListener progressListener) {
    BatchFanout fanout_instance = new BatchFanout(p_board, p_settings, p_thread);
    FanoutDiagnostics.resetCounters();
    long fanoutStart = System.currentTimeMillis();
    int maxPasses = (p_settings.fanout != null && p_settings.fanout.maxPasses != null)
        ? p_settings.fanout.maxPasses : 20;
    int completedPasses = 0;
    String lastBoardHash = p_board.get_hash();
    for (int i = 0; i < maxPasses; ++i) {
      int routed_count = fanout_instance.fanout_pass(i, progressListener);
      completedPasses++;
      if (routed_count == 0) {
        break;
      }
      String currentBoardHash = p_board.get_hash();
      if (currentBoardHash.equals(lastBoardHash)) {
        break;
      }
      lastBoardHash = currentBoardHash;
    }
    EscapeStatistics initialEscape = fanout_instance.computeEscapeStatistics();
    if (initialEscape.escapedCount() < initialEscape.totalSmdPins()) {
      fanout_instance.runEscapeViaPhase();
    }
    EscapeStatistics finalEscape = fanout_instance.computeEscapeStatistics();
    FanoutDiagnostics.logSessionSummary();
    long totalDurationMillis = Math.max(0, System.currentTimeMillis() - fanoutStart);
    return new FanoutRunSummary(completedPasses, totalDurationMillis, finalEscape);
  }

  /** Routes a fanout pass and returns the number of new fanouted SMD-pins in this pass. */
  private int fanout_pass(int p_pass_no, FanoutProgressListener progressListener) {
    long passStart = System.currentTimeMillis();
    int pinsToGo = this.totalSmdPinCount;
    int routed_count = 0;
    int not_routed_count = 0;
    int insert_error_count = 0;
    int already_connected_count = 0;
    int viasBeforePass = this.routing_board.get_vias().size();
    int ripup_costs = this.settings.get_start_ripup_costs() * (p_pass_no + 1);

    long baseMillisPerPin = (this.settings.fanout != null && this.settings.fanout.maxMillisecondsPerPin != null)
        ? this.settings.fanout.maxMillisecondsPerPin : 10000L;
    boolean ripupAllowed = (this.settings.fanout == null || this.settings.fanout.ripupAllowed == null)
        || Boolean.TRUE.equals(this.settings.fanout.ripupAllowed);
    // Negative ripup costs signal "no ripup" to RoutingBoard.fanout()
    int effectiveRipupCosts = ripupAllowed ? ripup_costs : -1;

    FRLogger.trace("BatchFanout.fanout_pass", "pass_start",
        "pass=" + (p_pass_no + 1)
            + ", totalPins=" + this.totalSmdPinCount
            + ", alreadyConnected=" + this.alreadyConnectedPinCount
            + ", pinsToFanout=" + (this.totalSmdPinCount - this.alreadyConnectedPinCount)
            + ", ripupCosts=" + effectiveRipupCosts
            + ", baseMillisPerPin=" + baseMillisPerPin,
        "", new app.freerouting.geometry.planar.Point[0]);

    this.progressThrottler.reset();
    publishProgress(progressListener, p_pass_no, ripup_costs, pinsToGo, routed_count, not_routed_count,
        insert_error_count, 0, new EscapeStatistics(this.totalSmdPinCount, 0, 0.0), false, passStart);

    for (Component curr_component : this.sorted_components) {
      for (Component.Pin curr_pin : curr_component.smd_pins) {
        double max_milliseconds = baseMillisPerPin * (p_pass_no + 1);
        TimeLimit time_limit = new TimeLimit((int) max_milliseconds);
        String fullPinName = curr_component.board_component.name + "-" + curr_pin.board_pin.name();
        int netNo = curr_pin.board_pin.get_net_no(0);
        app.freerouting.geometry.planar.Point pinCenter = curr_pin.board_pin.get_center();

        if (isPinEscaped(curr_pin.board_pin)) {
          FRLogger.trace("BatchFanout.fanout_pass", "FANOUT_DIAG",
              "event=pin_already_escaped, pin=" + fullPinName
                  + ", net=" + netNo
                  + ", pass=" + (p_pass_no + 1)
                  + ", " + FanoutDiagnostics.describePinEscapeGeometry(curr_pin.board_pin, this.routing_board),
              fullPinName, new app.freerouting.geometry.planar.Point[]{pinCenter});
          --pinsToGo;
          continue;
        }

        int targetCount = curr_pin.board_pin.get_unconnected_set(netNo).size();

        FRLogger.trace("BatchFanout.fanout_pass", "pin_start",
            "pin=" + fullPinName
                + ", net=" + netNo
                + ", targetCount=" + targetCount
                + ", center=" + curr_pin.board_pin.get_center()
                + ", layer=" + curr_pin.board_pin.first_layer()
                + ", pass=" + (p_pass_no + 1),
            fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});

        this.routing_board.start_marking_changed_area();
        long pinStartNanos = System.nanoTime();
        AutorouteAttemptResult curr_result = null;
        boolean pinEscaped = false;
        boolean keepAttempt = false;

        this.routing_board.generate_snapshot();
        curr_result = this.routing_board.fanout(
            curr_pin.board_pin, this.settings, effectiveRipupCosts, this.thread, time_limit);
        pinEscaped = isPinEscaped(curr_pin.board_pin);
        keepAttempt = shouldKeepFanoutAttempt(curr_result, pinEscaped);

        if (!keepAttempt) {
          rollbackFanoutAttempt(fullPinName, netNo, curr_result.state.name());
          FanoutEscalationResult escalation = tryFanoutWithEscapeVia(
              curr_pin.board_pin, fullPinName, netNo, effectiveRipupCosts, time_limit, p_pass_no + 1, false, false);
          if (escalation != null) {
            curr_result = escalation.result();
            pinEscaped = isPinEscaped(curr_pin.board_pin);
            keepAttempt = escalation.keepAttempt();
            if (keepAttempt) {
              this.routing_board.pop_snapshot();
            }
          }
        } else {
          this.routing_board.pop_snapshot();
        }

        long pinDurationMs = (System.nanoTime() - pinStartNanos) / 1_000_000L;
        if (curr_result.state == AutorouteAttemptState.ROUTED && pinEscaped
            && !FanoutDiagnostics.meetsMinEscapeLength(curr_pin.board_pin, this.routing_board, this.settings)) {
          FanoutDiagnostics.incrementBelowMinEscapeLength();
          double measuredMm = FanoutDiagnostics.measurePinEscapeLengthMm(curr_pin.board_pin, this.routing_board);
          FanoutDiagnostics.trace("BatchFanout.fanout_pass", fullPinName, netNo, "below_min_escape_length",
              "pass=" + (p_pass_no + 1)
                  + ", measuredMm=" + FRLogger.defaultFloatFormat.format(measuredMm)
                  + ", minMm=" + FanoutDiagnostics.resolveMinLenMm(this.settings)
                  + ", kept=true"
                  + ", " + FanoutDiagnostics.describePinEscapeGeometry(curr_pin.board_pin, this.routing_board),
              pinCenter);
        }

        switch (curr_result.state) {
          case ROUTED       -> {
             if (keepAttempt) {
               ++routed_count;
             } else {
               ++not_routed_count;
             }
             FRLogger.trace("BatchFanout.fanout_pass", "pin_routed",
                 "pin=" + fullPinName
                     + ", net=" + netNo
                     + ", durationMs=" + pinDurationMs
                     + ", targetCount=" + targetCount
                     + ", isPinEscaped=" + pinEscaped
                     + ", kept=" + keepAttempt,
                 fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
             if (!pinEscaped) {
               FanoutDiagnostics.incrementRoutedButNotEscaped();
               FanoutDiagnostics.trace("BatchFanout.fanout_pass", fullPinName, netNo, "pass_routed_but_not_escaped",
                   "pass=" + (p_pass_no + 1)
                       + ", rolledBack=true"
                       + ", " + FanoutDiagnostics.describePinEscapeFailure(curr_pin.board_pin)
                       + ", geometry=" + FanoutDiagnostics.describePinEscapeGeometry(curr_pin.board_pin, this.routing_board),
                   pinCenter);
             }
          }
          case ALREADY_CONNECTED -> {
            ++already_connected_count;
            FRLogger.trace("BatchFanout.fanout_pass", "pin_already_connected",
                "pin=" + fullPinName
                    + ", net=" + netNo
                    + ", targetCount=" + targetCount
                    + ", detail=" + curr_result.details,
                fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
          case FAILED       -> {
            ++not_routed_count;
            FanoutDiagnostics.incrementFanoutPassFailed();
            FRLogger.trace("BatchFanout.fanout_pass", "pin_failed",
                "pin=" + fullPinName
                    + ", net=" + netNo
                    + ", targetCount=" + targetCount
                    + ", durationMs=" + pinDurationMs
                    + ", detail=" + (curr_result.details == null || curr_result.details.isEmpty()
                    ? "no detail"
                    : curr_result.details),
                fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
          case INSERT_ERROR -> {
            ++insert_error_count;
            FRLogger.trace("BatchFanout.fanout_pass", "pin_insert_error",
                "pin=" + fullPinName
                    + ", net=" + netNo
                    + ", detail=" + (curr_result.details == null || curr_result.details.isEmpty()
                    ? "no detail"
                    : curr_result.details),
                fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
          case NO_UNCONNECTED_NETS -> {
            FRLogger.trace("BatchFanout.fanout_pass", "pin_no_unconnected_nets",
                "pin=" + fullPinName
                    + ", net=" + netNo
                    + ", detail=" + curr_result.details,
                fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
          default -> {
            FRLogger.trace("BatchFanout.fanout_pass", "pin_other_state",
                "pin=" + fullPinName
                    + ", net=" + netNo
                    + ", state=" + curr_result.state
                    + ", detail=" + curr_result.details,
                fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
        }
        --pinsToGo;
        int extraViasThisPass = Math.max(0, this.routing_board.get_vias().size() - viasBeforePass);
        maybePublishProgress(progressListener, p_pass_no, ripup_costs, pinsToGo, routed_count, not_routed_count,
            insert_error_count, extraViasThisPass, false, passStart);
        if (this.thread.is_stop_auto_router_requested()) {
          EscapeStatistics escapeStats = computeEscapeStatistics();
          publishProgress(progressListener, p_pass_no, ripup_costs, pinsToGo, routed_count, not_routed_count,
              insert_error_count, extraViasThisPass, escapeStats, true, passStart);
          return routed_count;
        }
      }
    }
    int extraViasThisPass = Math.max(0, this.routing_board.get_vias().size() - viasBeforePass);
    this.extraViasTotal += extraViasThisPass;
    EscapeStatistics escapeStats = computeEscapeStatistics();

    long passDurationMs = System.currentTimeMillis() - passStart;
    FRLogger.trace("BatchFanout.fanout_pass", "pass_end",
        "pass=" + (p_pass_no + 1)
            + ", durationMs=" + passDurationMs
            + ", routed=" + routed_count
            + ", notRouted=" + not_routed_count
            + ", insertErrors=" + insert_error_count
            + ", alreadyConnected=" + already_connected_count
            + ", escaped=" + escapeStats.escapedCount()
            + "/" + escapeStats.totalSmdPins()
            + " (" + String.format("%.1f", escapeStats.escapedPercentage()) + "%)",
        "", new app.freerouting.geometry.planar.Point[0]);

    if (progressListener == null) {
      FRLogger.info(
          "fanout pass: "
              + (p_pass_no + 1)
              + ", routed: "
              + routed_count
              + ", not routed: "
              + not_routed_count
              + ", errors: "
              + insert_error_count
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
    this.lastNotRoutedCount = not_routed_count;
    publishProgress(progressListener, p_pass_no, ripup_costs, pinsToGo, routed_count, not_routed_count,
        insert_error_count, extraViasThisPass, escapeStats, true, passStart);

    return routed_count;
  }

  private void maybePublishProgress(FanoutProgressListener progressListener, int passNo, int ripupCosts, int pinsToGo,
      int routedCount, int notRoutedCount, int insertErrorCount, int extraViasThisPass, boolean passCompleted,
      long passStart) {
    if (passCompleted || progressThrottler.shouldUpdate()) {
      // Mid-pass interim updates use a lightweight empty escape statistics placeholder
      // to avoid the cost of a full escape scan on every tick.
      EscapeStatistics interimEscape = new EscapeStatistics(this.totalSmdPinCount, 0, 0.0);
      publishProgress(progressListener, passNo, ripupCosts, pinsToGo, routedCount, notRoutedCount, insertErrorCount,
          extraViasThisPass, interimEscape, passCompleted, passStart);
    }
  }

  private void publishProgress(FanoutProgressListener progressListener, int passNo, int ripupCosts, int pinsToGo,
      int routedCount, int notRoutedCount, int insertErrorCount, int extraViasThisPass,
      EscapeStatistics escapeStatistics, boolean passCompleted, long passStart) {
    if (progressListener == null) {
      return;
    }
    BoardStatistics boardStatistics = this.routing_board.get_statistics();
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

  private void runEscapeViaPhase() {
    int totalEscalated = 0;
    int successfullyEscalated = 0;

    for (Component component : this.sorted_components) {
      for (Component.Pin pin : component.smd_pins) {
        if (this.thread.is_stop_auto_router_requested()) {
          return;
        }
        if (isPinEscaped(pin.board_pin)) {
          continue;
        }

        app.freerouting.board.Pin boardPin = pin.board_pin;
        String pinName = component.board_component.name + "-" + boardPin.name();
        int netNo = boardPin.net_count() > 0 ? boardPin.get_net_no(0) : 0;
        if (netNo <= 0) {
          continue;
        }

        FRLogger.trace("BatchFanout.runEscapeViaPhase", "FANOUT_DIAG",
            "event=escalate_unescaped_pin, pin=" + pinName
                + ", net=" + netNo
                + ", startLayer=" + boardPin.first_layer()
                + ", hasEscapeVia=" + hasActiveEscapeVia(boardPin)
                + ", " + FanoutDiagnostics.describePinEscapeGeometry(boardPin, this.routing_board),
            pinName, new app.freerouting.geometry.planar.Point[]{boardPin.get_center()});

        boolean ripupAllowed = (this.settings.fanout == null || this.settings.fanout.ripupAllowed == null)
            || Boolean.TRUE.equals(this.settings.fanout.ripupAllowed);
        int ripupCosts = ripupAllowed ? this.settings.get_start_ripup_costs() : -1;
        long baseMillisPerPin = (this.settings.fanout != null && this.settings.fanout.maxMillisecondsPerPin != null)
            ? this.settings.fanout.maxMillisecondsPerPin : 10000L;
        TimeLimit timeLimit = new TimeLimit((int) baseMillisPerPin);

        FanoutEscalationResult escalation = tryFanoutWithEscapeVia(
            boardPin, pinName, netNo, ripupCosts, timeLimit, 0, true, true);
        totalEscalated++;
        if (escalation != null && escalation.keepAttempt()) {
          successfullyEscalated++;
        }
      }
    }

    if (totalEscalated > 0) {
      FRLogger.trace("BatchFanout.runEscapeViaPhase", "FANOUT_DIAG",
          "event=escape_via_phase_summary, totalEscalated=" + totalEscalated
              + ", successfullyEscalated=" + successfullyEscalated,
          "", new app.freerouting.geometry.planar.Point[0]);
    }
  }

  /**
   * Computes escape statistics for all SMD pins currently in the sorted_components list.
   * A pin is considered "escaped" when it has at least one Trace (wire) or Via directly
   * connected to it at the pin's center — with no clearance violations on that trace/via —
   * or when a Via connected to the pin itself has a Trace connected to it.
   */
  EscapeStatistics computeEscapeStatistics() {
    int total = 0;
    int escaped = 0;
    for (Component component : this.sorted_components) {
      for (Component.Pin pin : component.smd_pins) {
        total++;
        if (isPinEscaped(pin.board_pin)) {
          escaped++;
        } else {
          app.freerouting.board.Pin boardPin = pin.board_pin;
          String pinName = component.board_component.name + "-" + boardPin.name();
          int netNo = boardPin.net_count() > 0 ? boardPin.get_net_no(0) : 0;
          FRLogger.trace("BatchFanout.computeEscapeStatistics", "FANOUT_DIAG",
              "event=pin_not_escaped, pin=" + pinName
                  + ", net=" + netNo
                  + ", center=" + boardPin.get_center()
                  + ", layer=" + boardPin.first_layer()
                  + ", " + FanoutDiagnostics.describePinEscapeFailure(boardPin)
                  + ", geometry=" + FanoutDiagnostics.describePinEscapeGeometry(boardPin, this.routing_board),
              pinName, new app.freerouting.geometry.planar.Point[]{boardPin.get_center()});
        }
      }
    }
    double pct = total == 0 ? 0.0 : 100.0 * escaped / total;
    return new EscapeStatistics(total, escaped, pct);
  }

  /**
   * Returns {@code true} if the given SMD pin has a valid escape route:
   * <ul>
   *   <li>A {@link Trace} (wire) is directly connected to the pin center with no clearance
   *       violations on that trace, <em>or</em></li>
   *   <li>A {@link Via} is directly connected to the pin center with no clearance violations
   *       on the via, <em>and</em> that via has at least one {@link Trace} connected to it, <em>or</em></li>
   *   <li>The pin is directly connected to a {@link ConductionArea} (plane/pour).</li>
   * </ul>
   */
  private void rollbackFanoutAttempt(String pinName, int netNo, String reason) {
    Set<Integer> changedNets = new java.util.TreeSet<>();
    if (this.routing_board.undo(changedNets)) {
      FanoutDiagnostics.trace("BatchFanout", pinName, netNo, "fanout_attempt_rollback", "reason=" + reason);
    }
  }

  private record FanoutEscalationResult(AutorouteAttemptResult result, boolean keepAttempt) {
  }

  private static boolean shouldKeepFanoutAttempt(AutorouteAttemptResult result, boolean pinEscaped) {
    return switch (result.state) {
      case ROUTED -> pinEscaped;
      case ALREADY_CONNECTED, NO_UNCONNECTED_NETS -> true;
      default -> false;
    };
  }

  private boolean hasActiveEscapeVia(app.freerouting.board.Pin pin) {
    for (Item contact : pin.get_normal_contacts()) {
      if (contact instanceof Via via && via.isEscapeVia) {
        return true;
      }
    }
    return false;
  }

  private void removeEscapeViasOnPin(app.freerouting.board.Pin pin) {
    for (Item contact : pin.get_normal_contacts()) {
      if (contact instanceof Via via && via.isEscapeVia) {
        this.routing_board.remove_item(via);
      }
    }
  }

  /**
   * Inserts a persistent escape via (outside the undo snapshot) and retries fanout from inner layers.
   * Failed stub inserts are rolled back while the escape via remains for later passes.
   */
  private FanoutEscalationResult tryFanoutWithEscapeVia(app.freerouting.board.Pin boardPin, String pinName,
      int netNo, int ripupCosts, TimeLimit timeLimit, int passNo, boolean tryAllLayers, boolean allowForcedStub) {
    FRLogger.trace("BatchFanout.tryFanoutWithEscapeVia", "FANOUT_DIAG",
        "event=inline_escape_via_retry, pin=" + pinName
            + ", net=" + netNo
            + ", pass=" + passNo
            + ", hasEscapeVia=" + hasActiveEscapeVia(boardPin)
            + ", " + FanoutDiagnostics.describePinEscapeGeometry(boardPin, this.routing_board),
        pinName, new app.freerouting.geometry.planar.Point[]{boardPin.get_center()});

    FloatPoint componentGravity = getPinComponentGravity(boardPin);
    int startLayer = boardPin.first_layer();
    int boardLayers = this.routing_board.get_layer_count();
    int step = (startLayer == 0) ? 1 : -1;

    if (hasActiveEscapeVia(boardPin)) {
      FanoutEscalationResult retry = tryFanoutAndForcedStub(
          boardPin, pinName, netNo, ripupCosts, timeLimit, componentGravity, "existing_escape_via", allowForcedStub);
      if (retry != null) {
        return retry;
      }
      if (!tryAllLayers) {
        return null;
      }
    }

    int firstTarget = startLayer + step;
    int lastTarget = tryAllLayers ? (step > 0 ? boardLayers - 1 : 0) : firstTarget;
    if (firstTarget < 0 || firstTarget >= boardLayers) {
      return null;
    }
    for (int targetLayer = firstTarget; step > 0 ? targetLayer <= lastTarget : targetLayer >= lastTarget;
        targetLayer += step) {
      if (this.thread.is_stop_auto_router_requested()) {
        return null;
      }
      app.freerouting.rules.ViaInfo viaInfo =
          this.routing_board.get_via_info_for_layers(netNo, startLayer, targetLayer);
      if (viaInfo == null) {
        continue;
      }
      removeEscapeViasOnPin(boardPin);
      this.routing_board.start_marking_changed_area();
      Via insertedVia = this.routing_board.insertEscapeVia(boardPin, viaInfo, this.settings);
      if (insertedVia == null) {
        continue;
      }
      FRLogger.trace("BatchFanout.tryFanoutWithEscapeVia", "FANOUT_DIAG",
          "event=escape_via_inserted, pin=" + pinName
              + ", net=" + netNo
              + ", targetLayer=" + targetLayer
              + ", via=" + viaInfo.get_name(),
          pinName, new app.freerouting.geometry.planar.Point[]{boardPin.get_center()});

      FanoutEscalationResult retry = tryFanoutAndForcedStub(
          boardPin, pinName, netNo, ripupCosts, timeLimit, componentGravity, "targetLayer=" + targetLayer,
          allowForcedStub);
      if (retry != null) {
        return retry;
      }
    }
    return null;
  }

  private FanoutEscalationResult tryFanoutAndForcedStub(app.freerouting.board.Pin boardPin, String pinName,
      int netNo, int ripupCosts, TimeLimit timeLimit, FloatPoint componentGravity, String context,
      boolean allowForcedStub) {
    if (this.thread.is_stop_auto_router_requested()) {
      return null;
    }
    this.routing_board.start_marking_changed_area();
    this.routing_board.generate_snapshot();
    AutorouteAttemptResult result =
        this.routing_board.fanout(boardPin, this.settings, ripupCosts, this.thread, timeLimit);
    boolean pinEscaped = isPinEscaped(boardPin);
    if (shouldKeepFanoutAttempt(result, pinEscaped)) {
      return new FanoutEscalationResult(result, true);
    }
    rollbackFanoutAttempt(pinName, netNo, result.state.name());
    FanoutDiagnostics.incrementEscapeViaRollback();

    if (!allowForcedStub) {
      return null;
    }

    this.routing_board.start_marking_changed_area();
    this.routing_board.generate_snapshot();
    if (FanoutForcedStub.tryInsertDrillEndStub(boardPin, this.routing_board, this.settings, componentGravity)) {
      pinEscaped = isPinEscaped(boardPin);
      if (pinEscaped) {
        FRLogger.trace("BatchFanout.tryFanoutAndForcedStub", "FANOUT_DIAG",
            "event=forced_stub_success, pin=" + pinName + ", net=" + netNo + ", context=" + context,
            pinName, new app.freerouting.geometry.planar.Point[]{boardPin.get_center()});
        return new FanoutEscalationResult(new AutorouteAttemptResult(AutorouteAttemptState.ROUTED), true);
      }
      rollbackFanoutAttempt(pinName, netNo, "forced_stub_not_escaped");
    } else {
      this.routing_board.pop_snapshot();
    }
    return null;
  }

  private FloatPoint getPinComponentGravity(app.freerouting.board.Pin boardPin) {
    for (Component component : this.sorted_components) {
      for (Component.Pin pin : component.smd_pins) {
        if (pin.board_pin == boardPin) {
          return component.gravity_center_of_smd_pins;
        }
      }
    }
    return null;
  }

  private boolean isPinEscaped(app.freerouting.board.Pin pin) {
    Set<Item> contacts = pin.get_normal_contacts();
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        // Direct wire exit from the pin — check no clearance violations on the trace itself
        if (trace.clearance_violations().isEmpty()) {
          return true;
        }
      } else if (contact instanceof Via via) {
        // Via planted on the pin — check the via is clean and has at least one trace or conduction area attached
        if (via.clearance_violations().isEmpty()) {
          Set<Item> viaContacts = via.get_normal_contacts();
          for (Item viaContact : viaContacts) {
            if (viaContact instanceof Trace || viaContact instanceof app.freerouting.board.ConductionArea) {
              return true;
            }
          }
        }
      } else if (contact instanceof app.freerouting.board.ConductionArea) {
        // Directly connected to a conduction area (plane/pour)
        return true;
      }
    }
    return false;
  }

  @FunctionalInterface
  public interface FanoutProgressListener {
    void onProgress(FanoutPassStatus status);
  }

  /**
   * Statistics about how many SMD pins were successfully escaped after a fanout pass.
   * A pin is considered escaped when it has at least one Trace (wire) or Via directly connected
   * to it (with no clearance violations on the trace/via), or a Via that itself has a Trace
   * connected to it (also without clearance violations).
   */
  public record EscapeStatistics(
      int totalSmdPins,
      int escapedCount,
      double escapedPercentage) {

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
      EscapeStatistics escapeStatistics) {
  }

  public record FanoutRunSummary(
      int completedPassCount,
      long totalDurationMillis,
      EscapeStatistics escapeStatistics) {
  }

  private static class Component implements Comparable<Component> {

    final app.freerouting.board.Component board_component;
    final int smd_pin_count;
    final SortedSet<Pin> smd_pins;
    /** The center of gravity of all SMD pins of this component. */
    final FloatPoint gravity_center_of_smd_pins;
    final String pinSortingOrder;

    Component(
        app.freerouting.board.Component p_board_component,
        Collection<app.freerouting.board.Pin> p_board_smd_pin_list,
        String p_pin_sorting_order,
        RoutingBoard p_routing_board) {
      this.board_component = p_board_component;
      this.pinSortingOrder = p_pin_sorting_order;

      // calculate the center of gravity of all SMD pins of this component.
      Collection<app.freerouting.board.Pin> curr_pin_list =
          new LinkedList<>();
      int cmp_no = p_board_component.no;
      for (app.freerouting.board.Pin curr_board_pin : p_board_smd_pin_list) {
        if (curr_board_pin.get_component_no() == cmp_no) {
          curr_pin_list.add(curr_board_pin);
        }
      }
      double x = 0;
      double y = 0;
      for (app.freerouting.board.Pin curr_pin : curr_pin_list) {
        FloatPoint curr_point = curr_pin.get_center().to_float();
        x += curr_point.x;
        y += curr_point.y;
      }
      this.smd_pin_count = curr_pin_list.size();
      if (this.smd_pin_count > 0) {
        x /= this.smd_pin_count;
        y /= this.smd_pin_count;
      }
      this.gravity_center_of_smd_pins = new FloatPoint(x, y);

      // calculate the sorted SMD pins of this component
      this.smd_pins = new TreeSet<>();

      for (app.freerouting.board.Pin curr_board_pin : curr_pin_list) {
        this.smd_pins.add(new Pin(curr_board_pin, p_board_smd_pin_list, p_routing_board));
      }
    }

    /** Sort the components, so that components with more pins come first */
    @Override
    public int compareTo(Component p_other) {
      int compare_value = this.smd_pin_count - p_other.smd_pin_count;
      int result;
      if (compare_value > 0) {
        result = -1;
      } else if (compare_value < 0) {
        result = 1;
      } else {
        result = this.board_component.no - p_other.board_component.no;
      }
      return result;
    }

    class Pin implements Comparable<Pin> {

      final app.freerouting.board.Pin board_pin;
      final double distance_to_component_center;
      final double distance_to_closest_on_net;
      final int surroundings_density;

      Pin(app.freerouting.board.Pin p_board_pin, Collection<app.freerouting.board.Pin> p_board_smd_pin_list, RoutingBoard p_routing_board) {
        this.board_pin = p_board_pin;
        FloatPoint pin_location = p_board_pin.get_center().to_float();
        this.distance_to_component_center = pin_location.distance(gravity_center_of_smd_pins);

        // distance_to_closest_on_net calculation
        double minDistance = Double.MAX_VALUE;
        int netNo = p_board_pin.net_count() > 0 ? p_board_pin.get_net_no(0) : 0;
        if (netNo > 0) {
          for (app.freerouting.board.Pin otherPin : p_routing_board.get_pins()) {
            if (otherPin != p_board_pin && otherPin.contains_net(netNo)) {
              double dist = pin_location.distance(otherPin.get_center().to_float());
              if (dist < minDistance) {
                minDistance = dist;
              }
            }
          }
        }
        this.distance_to_closest_on_net = minDistance;

        // surroundings_density calculation
        double resolution = p_routing_board.communication.get_resolution(app.freerouting.board.Unit.UM);
        double maxDist = 20000.0 * resolution; // 20.0 mm in coordinate units
        int density = 0;
        for (app.freerouting.board.Pin otherPin : p_board_smd_pin_list) {
          if (otherPin != p_board_pin) {
            double dist = pin_location.distance(otherPin.get_center().to_float());
            if (dist <= maxDist) {
              density++;
            }
          }
        }
        this.surroundings_density = density;
      }

      @Override
      public int compareTo(Pin p_other) {
        int result = 0;
        if ("inner_first".equals(pinSortingOrder)) {
          double delta_dist =
              this.distance_to_component_center - p_other.distance_to_component_center;
          if (delta_dist > 0) {
            result = 1;
          } else if (delta_dist < 0) {
            result = -1;
          }
        } else if ("outer_first".equals(pinSortingOrder)) {
          double delta_dist =
              this.distance_to_component_center - p_other.distance_to_component_center;
          if (delta_dist > 0) {
            result = -1;
          } else if (delta_dist < 0) {
            result = 1;
          }
        } else if ("distance_to_closest_on_net".equals(pinSortingOrder)) {
          double delta = this.distance_to_closest_on_net - p_other.distance_to_closest_on_net;
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        } else if ("surroundings_density".equals(pinSortingOrder)) {
          int delta = p_other.surroundings_density - this.surroundings_density; // densest first
          if (delta > 0) {
            result = 1;
          } else if (delta < 0) {
            result = -1;
          }
        }
        if (result == 0) {
          result = this.board_pin.pin_no - p_other.board_pin.pin_no;
        }
        return result;
      }
    }
  }
}