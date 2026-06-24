package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.board.TestLevel;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;

/** Handles the sequencing of the fanout inside the batch autorouter. */
public class BatchFanout {

  public static class PassStatus {
    public int routedCount = 0;
    public int notRoutedCount = 0;
    public int insertErrorCount = 0;
  }

  public static class EscapeStatistics {
    private final int total;
    private final int escaped;
    private final double pct;
    public EscapeStatistics(int total, int escaped, double pct) {
      this.total = total;
      this.escaped = escaped;
      this.pct = pct;
    }
    public int total() { return total; }
    public int escaped() { return escaped; }
    public double pct() { return pct; }
  }

  private final InteractiveActionThread thread;
  private final RoutingBoard routing_board;
  private final SortedSet<Component> sorted_components;

  private BatchFanout(InteractiveActionThread p_thread) {
    this.thread = p_thread;
    this.routing_board = p_thread.hdlg.get_routing_board();
    Collection<app.freerouting.board.Pin> board_smd_pin_list = routing_board.get_smd_pins();
    this.sorted_components = new TreeSet<>();
    for (int i = 1; i <= routing_board.components.count(); ++i) {
      app.freerouting.board.Component curr_board_component = routing_board.components.get(i);
      Component curr_component = new Component(curr_board_component, board_smd_pin_list);
      if (curr_component.smd_pin_count > 0) {
        sorted_components.add(curr_component);
      }
    }
  }

  private EscapeStatistics computeEscapeStatistics() {
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

  private boolean isPinEscaped(app.freerouting.board.Pin pin) {
    java.util.Collection<Item> contacts = pin.get_normal_contacts();
    for (Item contact : contacts) {
      if (contact instanceof Trace) {
        Trace trace = (Trace) contact;
        if (trace.clearance_violations().isEmpty()) {
          return true;
        }
      } else if (contact instanceof Via) {
        Via via = (Via) contact;
        if (via.clearance_violations().isEmpty()) {
          // Check if there's at least one trace connected to the via
          java.util.Collection<Item> viaContacts = via.get_normal_contacts();
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

  public static void fanout_board(InteractiveActionThread p_thread) {
    long phaseStartTime = System.currentTimeMillis();
    com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    long startCpuTime = threadMXBean.getThreadCpuTime(Thread.currentThread().threadId());
    long startAllocatedBytes = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().threadId());

    BatchFanout fanout_instance = new BatchFanout(p_thread);
    int totalSmdPins = 0;
    for (Component c : fanout_instance.sorted_components) {
      totalSmdPins += c.smd_pin_count;
    }

    final int MAX_PASS_COUNT = 20;
    for (int i = 0; i < MAX_PASS_COUNT; ++i) {
      long passStartTime = System.currentTimeMillis();
      long passStartCpu = threadMXBean.getThreadCpuTime(Thread.currentThread().threadId());
      long passStartAlloc = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      int viasBefore = fanout_instance.routing_board.get_vias().size();

      PassStatus status = fanout_instance.fanout_pass(i);
      
      long passDuration = System.currentTimeMillis() - passStartTime;
      long passEndCpu = threadMXBean.getThreadCpuTime(Thread.currentThread().threadId());
      long passEndAlloc = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      double passCpuSec = (passEndCpu - passStartCpu) / 1_000_000_000.0;
      long passAllocBytes = passEndAlloc - passStartAlloc;

      int viasAfter = fanout_instance.routing_board.get_vias().size();
      int extraVias = viasAfter - viasBefore;

      if (status.routedCount == 0 && status.insertErrorCount == 0) {
        break;
      }

      String boardHash = fanout_instance.routing_board.get_hash();

      FRLogger.info(String.format(java.util.Locale.US,
          "Fanout pass #%d on board '%s' completed in %.2f seconds with %d SMD pin%s fanouted, %d not routed, %d insert error%s, +%d extra via%s (%d SMD pin%s still to check in pass, ripup costs=%d).",
          i + 1, boardHash,
          passDuration / 1000.0,
          status.routedCount, status.routedCount == 1 ? "" : "s",
          status.notRoutedCount,
          status.insertErrorCount, status.insertErrorCount == 1 ? "" : "s",
          extraVias, extraVias == 1 ? "" : "s",
          totalSmdPins - status.routedCount, (totalSmdPins - status.routedCount) == 1 ? "" : "s",
          (p_thread.hdlg.get_settings().autoroute_settings.get_start_ripup_costs() * (i + 1))
      ));
    }

    long phaseEndTime = System.currentTimeMillis();
    long endCpuTime = threadMXBean.getThreadCpuTime(Thread.currentThread().threadId());
    long endAllocatedBytes = threadMXBean.getThreadAllocatedBytes(Thread.currentThread().threadId());

    double totalCpuTime = (endCpuTime - startCpuTime) / 1_000_000_000.0;
    double totalAllocatedGb = (endAllocatedBytes - startAllocatedBytes) / 1024.0 / 1024.0 / 1024.0;
    long peakHeap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024 / 1024;

    EscapeStatistics finalEscape = fanout_instance.computeEscapeStatistics();
    String fanoutCompletionStatus = p_thread.is_stop_auto_router_requested() ? "interrupted:" : "completed:";
    FRLogger.info(String.format(java.util.Locale.US,
        "Fanout phase %s started with %d total SMD pins, completed in %.2f seconds, escaped pins: %d/%d (%.1f%%), using %.2f total CPU seconds, %.2f GB total allocated, and %.1f MB peak heap usage.",
        fanoutCompletionStatus,
        finalEscape.total(),
        (phaseEndTime - phaseStartTime) / 1000.0,
        finalEscape.escaped(),
        finalEscape.total(),
        finalEscape.pct(),
        totalCpuTime,
        totalAllocatedGb,
        (double) peakHeap
    ));
  }

  /** Routes a fanout pass and returns the status of new fanouted SMD-pins in this pass. */
  private PassStatus fanout_pass(int p_pass_no) {
    PassStatus status = new PassStatus();
    int components_to_go = this.sorted_components.size();
    int ripup_costs =
        this.thread.hdlg.get_settings().autoroute_settings.get_start_ripup_costs()
            * (p_pass_no + 1);
    for (Component curr_component : this.sorted_components) {
      this.thread.hdlg.screen_messages.set_batch_fanout_info(p_pass_no + 1, components_to_go);
      for (Component.Pin curr_pin : curr_component.smd_pins) {
        double max_milliseconds = 10000 * (p_pass_no + 1);
        TimeLimit time_limit = new TimeLimit((int) max_milliseconds);
        this.routing_board.start_marking_changed_area();
        AutorouteEngine.AutorouteResult curr_result =
            this.routing_board.fanout(
                curr_pin.board_pin,
                this.thread.hdlg.get_settings(),
                ripup_costs,
                this.thread,
                time_limit);
        switch (curr_result) {
          case ROUTED       -> ++status.routedCount;
          case NOT_ROUTED   -> {
            ++status.notRoutedCount;
            // FANOUT_DIAG parity: log fanout failures for U27 pins matching current branch format
            app.freerouting.board.Component cmp = this.routing_board.components.get(curr_pin.board_pin.get_component_no());
            String pinLabel = (cmp != null && curr_pin.board_pin.name() != null)
                ? cmp.name + "-" + curr_pin.board_pin.name() : curr_pin.board_pin.toString();
            if (pinLabel.startsWith("U27-")) {
              int net_no = curr_pin.board_pin.net_count() > 0 ? curr_pin.board_pin.get_net_no(0) : -1;
              FRLogger.trace("FANOUT_DIAG event=fanout_failed, pin=" + pinLabel + ", net=" + net_no
                  + ", reason=Failed to route (v1.9)");
            }
          }
          case INSERT_ERROR -> ++status.insertErrorCount;
        }
        if (curr_result != AutorouteEngine.AutorouteResult.NOT_ROUTED) {
          this.thread.hdlg.repaint();
        }
        if (this.thread.is_stop_requested()) {
          return status;
        }
      }
      --components_to_go;
    }
    if (this.routing_board.get_test_level() != TestLevel.RELEASE_VERSION) {
      FRLogger.warn(
          "fanout pass: "
              + (p_pass_no + 1)
              + ", routed: "
              + status.routedCount
              + ", not routed: "
              + status.notRoutedCount
              + ", errors: "
              + status.insertErrorCount);
    }
    return status;
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
      x /= this.smd_pin_count;
      y /= this.smd_pin_count;
      this.gravity_center_of_smd_pins = new FloatPoint(x, y);

      // calculate the sorted SMD pins of this component
      this.smd_pins = new TreeSet<>();

      for (app.freerouting.board.Pin curr_board_pin : curr_pin_list) {
        this.smd_pins.add(new Pin(curr_board_pin));
      }
    }

    /** Sort the components, so that components with maor pins come first */
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
          result = 1;
        } else if (delta_dist < 0) {
          result = -1;
        } else {
          result = this.board_pin.pin_no - p_other.board_pin.pin_no;
        }
        return result;
      }
    }
  }
}