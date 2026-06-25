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
    EscapeStatistics finalEscape = fanout_instance.computeEscapeStatistics();
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
        AutorouteAttemptResult curr_result =
            this.routing_board.fanout(
                curr_pin.board_pin,
                this.settings,
                effectiveRipupCosts,
                this.thread,
                time_limit);
        long pinDurationMs = (System.nanoTime() - pinStartNanos) / 1_000_000L;

        switch (curr_result.state) {
          case ROUTED       -> {
             ++routed_count;
             FRLogger.trace("BatchFanout.fanout_pass", "pin_routed",
                 "pin=" + fullPinName
                     + ", net=" + netNo
                     + ", durationMs=" + pinDurationMs
                     + ", targetCount=" + targetCount,
                 fullPinName, new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
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
          // Log details about non-escaped pins to aid investigation of incomplete fanout
          app.freerouting.board.Pin boardPin = pin.board_pin;
          String pinName = component.board_component.name + "-" + boardPin.name();
          int netNo = boardPin.net_count() > 0 ? boardPin.get_net_no(0) : 0;
          Set<Item> contacts = boardPin.get_normal_contacts();
          int contactCount = contacts.size();
          int traceContacts = 0;
          int viaContacts = 0;
          int tracesWithViolations = 0;
          int viasWithViolations = 0;
          int viasWithoutTrace = 0;
          for (Item contact : contacts) {
            if (contact instanceof Trace) {
              traceContacts++;
              if (!contact.clearance_violations().isEmpty()) tracesWithViolations++;
            } else if (contact instanceof Via) {
              viaContacts++;
              if (!contact.clearance_violations().isEmpty()) {
                viasWithViolations++;
              } else {
                boolean hasTraceContact = false;
                for (Item viaContact : contact.get_normal_contacts()) {
                  if (viaContact instanceof Trace) { hasTraceContact = true; break; }
                }
                if (!hasTraceContact) viasWithoutTrace++;
              }
            }
          }
          FRLogger.trace("BatchFanout.computeEscape", "pin_not_escaped",
              "pin=" + pinName
                  + ", net=" + netNo
                  + ", center=" + boardPin.get_center()
                  + ", layer=" + boardPin.first_layer()
                  + ", contacts=" + contactCount
                  + ", traces=" + traceContacts
                  + " (withViolations=" + tracesWithViolations + ")"
                  + ", vias=" + viaContacts
                  + " (withViolations=" + viasWithViolations
                  + ", noTraceAttached=" + viasWithoutTrace + ")",
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
   *       on the via, <em>and</em> that via has at least one {@link Trace} connected to it.</li>
   * </ul>
   */
  private boolean isPinEscaped(app.freerouting.board.Pin pin) {
    Set<Item> contacts = pin.get_normal_contacts();
    for (Item contact : contacts) {
      if (contact instanceof Trace trace) {
        // Direct wire exit from the pin — check no clearance violations on the trace itself
        if (trace.clearance_violations().isEmpty()) {
          return true;
        }
      } else if (contact instanceof Via via) {
        // Via planted on the pin — check the via is clean and has at least one trace attached
        if (via.clearance_violations().isEmpty()) {
          Set<Item> viaContacts = via.get_normal_contacts();
          for (Item viaContact : viaContacts) {
            if (viaContact instanceof Trace) {
              return true;
            }
          }
        }
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