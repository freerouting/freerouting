package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;

import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Handles the sequencing of the fanout inside the batch autorouter.
 */
public class BatchFanout extends NamedAlgorithm
{
  private final SortedSet<Component> sorted_components;

  public BatchFanout(StoppableThread p_thread, RoutingBoard board, RouterSettings settings)
  {
    super(p_thread, board, settings);

    Collection<app.freerouting.board.Pin> board_smd_pin_list = board.get_smd_pins();
    this.sorted_components = new TreeSet<>();
    for (int i = 1; i <= board.components.count(); ++i)
    {
      app.freerouting.board.Component curr_board_component = board.components.get(i);
      Component curr_component = new Component(curr_board_component, board_smd_pin_list);
      if (curr_component.smd_pin_count > 0)
      {
        sorted_components.add(curr_component);
      }
    }
  }

  public void runBatchLoop()
  {
    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    int curr_pass_no;
    for (curr_pass_no = 0; curr_pass_no < this.settings.maxFanoutPasses; ++curr_pass_no)
    {
      String current_board_hash = this.board.get_hash();
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.RUNNING, curr_pass_no, current_board_hash));

      int routed_count = this.fanout_pass(curr_pass_no);
      if (routed_count == 0)
      {
        break;
      }
    }

    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED, curr_pass_no, this.board.get_hash()));
  }

  /**
   * Routes a fanout pass and returns the number of new fanouted SMD-pins in this pass.
   */
  private int fanout_pass(int p_pass_no)
  {
    int components_to_go = this.sorted_components.size();
    int routed_count = 0;
    int not_routed_count = 0;
    int insert_error_count = 0;
    int ripup_costs = settings.get_start_ripup_costs() * (p_pass_no + 1);
    for (Component curr_component : this.sorted_components)
    {
      for (Component.Pin curr_pin : curr_component.smd_pins)
      {
        double max_milliseconds = 10000 * (p_pass_no + 1);
        TimeLimit time_limit = new TimeLimit((int) max_milliseconds);
        this.board.start_marking_changed_area();
        AutorouteEngine.AutorouteResult curr_result = this.board.fanout(curr_pin.board_pin, settings, ripup_costs, this.thread, time_limit);
        switch (curr_result)
        {
          case ROUTED -> ++routed_count;
          case NOT_ROUTED -> ++not_routed_count;
          case INSERT_ERROR -> ++insert_error_count;
        }
        if (curr_result != AutorouteEngine.AutorouteResult.NOT_ROUTED)
        {
          fireBoardUpdatedEvent(this.board.get_statistics());
        }
        if (this.thread.isStopRequested())
        {
          return routed_count;
        }
      }
      --components_to_go;
    }
    FRLogger.debug("fanout pass: " + (p_pass_no + 1) + ", routed: " + routed_count + ", not routed: " + not_routed_count + ", errors: " + insert_error_count);
    return routed_count;
  }

  @Override
  protected String getId()
  {
    return "fanout-classic";
  }

  @Override
  protected String getName()
  {
    return "Freerouting Classic Fanout";
  }

  @Override
  protected String getVersion()
  {
    return "1.0";
  }

  @Override
  protected String getDescription()
  {
    return "Freerouting Classic Fanout algorithm v1.0";
  }

  @Override
  protected NamedAlgorithmType getType()
  {
    return NamedAlgorithmType.FANOUT;
  }

  private static class Component implements Comparable<Component>
  {

    final app.freerouting.board.Component board_component;
    final int smd_pin_count;
    final SortedSet<Pin> smd_pins;
    /**
     * The center of gravity of all SMD pins of this component.
     */
    final FloatPoint gravity_center_of_smd_pins;

    Component(app.freerouting.board.Component p_board_component, Collection<app.freerouting.board.Pin> p_board_smd_pin_list)
    {
      this.board_component = p_board_component;

      // calculate the center of gravity of all SMD pins of this component.
      Collection<app.freerouting.board.Pin> curr_pin_list = new LinkedList<>();
      int cmp_no = p_board_component.no;
      for (app.freerouting.board.Pin curr_board_pin : p_board_smd_pin_list)
      {
        if (curr_board_pin.get_component_no() == cmp_no)
        {
          curr_pin_list.add(curr_board_pin);
        }
      }
      double x = 0;
      double y = 0;
      for (app.freerouting.board.Pin curr_pin : curr_pin_list)
      {
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

      for (app.freerouting.board.Pin curr_board_pin : curr_pin_list)
      {
        this.smd_pins.add(new Pin(curr_board_pin));
      }
    }

    /**
     * Sort the components, so that components with maor pins come first
     */
    @Override
    public int compareTo(Component p_other)
    {
      int compare_value = this.smd_pin_count - p_other.smd_pin_count;
      int result;
      if (compare_value > 0)
      {
        result = -1;
      }
      else if (compare_value < 0)
      {
        result = 1;
      }
      else
      {
        result = this.board_component.no - p_other.board_component.no;
      }
      return result;
    }

    class Pin implements Comparable<Pin>
    {

      final app.freerouting.board.Pin board_pin;
      final double distance_to_component_center;

      Pin(app.freerouting.board.Pin p_board_pin)
      {
        this.board_pin = p_board_pin;
        FloatPoint pin_location = p_board_pin.get_center().to_float();
        this.distance_to_component_center = pin_location.distance(gravity_center_of_smd_pins);
      }

      @Override
      public int compareTo(Pin p_other)
      {
        int result;
        double delta_dist = this.distance_to_component_center - p_other.distance_to_component_center;
        if (delta_dist > 0)
        {
          result = 1;
        }
        else if (delta_dist < 0)
        {
          result = -1;
        }
        else
        {
          result = this.board_pin.pin_no - p_other.board_pin.pin_no;
        }
        return result;
      }
    }
  }
}