package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
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

  private final StoppableThread thread;
  private final RoutingBoard routing_board;
  private final RouterSettings settings;
  private final SortedSet<Component> sorted_components;
  private final int totalSmdPinCount;
  private int lastNotRoutedCount;
  private int extraViasTotal;
  private long lastProgressUpdateTimestamp;

  private BatchFanout(RoutingBoard p_board, RouterSettings p_settings, StoppableThread p_thread) {
    this.thread = p_thread;
    this.routing_board = p_board;
    this.settings = p_settings;
    Collection<app.freerouting.board.Pin> board_smd_pin_list = routing_board.get_smd_pins();
    this.sorted_components = new TreeSet<>();
    for (int i = 1; i <= routing_board.components.count(); ++i) {
      app.freerouting.board.Component curr_board_component = routing_board.components.get(i);
      Component curr_component = new Component(curr_board_component, board_smd_pin_list);
      if (curr_component.smd_pin_count > 0) {
        sorted_components.add(curr_component);
      }
    }
    int pinCount = 0;
    for (Component component : sorted_components) {
      pinCount += component.smd_pin_count;
    }
    this.totalSmdPinCount = pinCount;
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
    for (int i = 0; i < maxPasses; ++i) {
      int routed_count = fanout_instance.fanout_pass(i, progressListener);
      completedPasses++;
      if (routed_count == 0 && fanout_instance.lastNotRoutedCount == 0) {
        break;
      }
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
    int viasBeforePass = this.routing_board.get_vias().size();
    int ripup_costs = this.settings.get_start_ripup_costs() * (p_pass_no + 1);

    long baseMillisPerPin = (this.settings.fanout != null && this.settings.fanout.maxMillisecondsPerPin != null)
        ? this.settings.fanout.maxMillisecondsPerPin : 10000L;
    boolean ripupAllowed = (this.settings.fanout == null || this.settings.fanout.ripupAllowed == null)
        || Boolean.TRUE.equals(this.settings.fanout.ripupAllowed);
    // Negative ripup costs signal "no ripup" to RoutingBoard.fanout()
    int effectiveRipupCosts = ripupAllowed ? ripup_costs : -1;

    for (Component curr_component : this.sorted_components) {
      for (Component.Pin curr_pin : curr_component.smd_pins) {
        double max_milliseconds = baseMillisPerPin * (p_pass_no + 1);
        TimeLimit time_limit = new TimeLimit((int) max_milliseconds);
        String fullPinName = curr_component.board_component.name + "-" + curr_pin.board_pin.name();
        this.routing_board.start_marking_changed_area();
        AutorouteAttemptResult curr_result =
            this.routing_board.fanout(
                curr_pin.board_pin,
                this.settings,
                effectiveRipupCosts,
                this.thread,
                time_limit);
        switch (curr_result.state) {
          case ROUTED       -> {
             ++routed_count;
             FRLogger.trace("BatchFanout.fanout_pass", "fanout_success", "Fanout successful", curr_pin.board_pin.name(), new app.freerouting.geometry.planar.Point[]{curr_pin.board_pin.get_center()});
          }
          case FAILED       -> {
            ++not_routed_count;
            if (fullPinName.startsWith("U27-")) {
              FRLogger.trace("FANOUT_DIAG event=fanout_failed, pin=" + fullPinName
                  + ", net=" + curr_pin.board_pin.get_net_no(0)
                  + ", reason=" + (curr_result.details == null || curr_result.details.isEmpty()
                  ? "Fanout attempt failed"
                  : curr_result.details));
            }
          }
          case INSERT_ERROR -> ++insert_error_count;
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
    long now = System.currentTimeMillis();
    if (passCompleted || now - lastProgressUpdateTimestamp >= 250) {
      lastProgressUpdateTimestamp = now;
      // Mid-pass interim updates use a lightweight empty escape statistics placeholder
      // to avoid the cost of a full escape scan on every 250 ms tick.
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

  private static class Component implements Comparable<Component> {

    final app.freerouting.board.Component board_component;
    final int smd_pin_count;
    final SortedSet<Pin> smd_pins;
    /** The center of gravity of all SMD pins of this component. */
    final FloatPoint gravity_center_of_smd_pins;

    Component(
        app.freerouting.board.Component p_board_component,
        Collection<app.freerouting.board.Pin> p_board_smd_pin_list) {
      this.board_component = p_board_component;

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
        this.smd_pins.add(new Pin(curr_board_pin));
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

      Pin(app.freerouting.board.Pin p_board_pin) {
        this.board_pin = p_board_pin;
        FloatPoint pin_location = p_board_pin.get_center().to_float();
        this.distance_to_component_center = pin_location.distance(gravity_center_of_smd_pins);
      }

      @Override
      public int compareTo(Pin p_other) {
        int result;
        double delta_dist =
            this.distance_to_component_center - p_other.distance_to_component_center;
        if (delta_dist > 0) {
          result = -1;
        } else if (delta_dist < 0) {
          result = 1;
        } else {
          result = this.board_pin.pin_no - p_other.board_pin.pin_no;
        }
        return result;
      }
    }
  }
}