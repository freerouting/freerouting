package app.freerouting.interactive;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.PlanarDelaunayTriangulation;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;

import java.awt.*;
import java.util.*;

/**
 * Creates the Incompletes (Ratsnest) of one net to display them on the screen.
 */
public class NetIncompletes
{

  /**
   * Collection of elements of class AirLine.
   */
  final Collection<RatsNest.AirLine> incompletes;
  private final Net net;
  private final double draw_marker_radius;
  /**
   * The length of the violation of the length restriction of the net, > 0, if the cumulative trace
   * length is too big, < 0, if the trace length is too small, 0, if the trace length is ok or the
   * net has no length restrictions
   */
  private double length_violation = 0;

  /**
   * Creates a new instance of NetIncompletes
   */
  public NetIncompletes(int p_net_no, Collection<Item> p_net_items, BasicBoard p_board)
  {
    this.draw_marker_radius = p_board.rules.get_min_trace_half_width() * 2;
    this.incompletes = new LinkedList<>();
    this.net = p_board.rules.nets.get(p_net_no);

    // Create an array of Item-connected_set pairs.
    NetItem[] net_items = calculate_net_items(p_net_items);
    if (net_items.length <= 1)
    {
      return;
    }

    // create a Delaunay Triangulation for the net_items
    Collection<PlanarDelaunayTriangulation.Storable> triangulation_objects = new LinkedList<>(Arrays.asList(net_items));
    PlanarDelaunayTriangulation triangulation = new PlanarDelaunayTriangulation(triangulation_objects);

    // sort the result edges of the triangulation by length in ascending order.
    Collection<PlanarDelaunayTriangulation.ResultEdge> triangulation_lines = triangulation.get_edge_lines();
    SortedSet<Edge> sorted_edges = new TreeSet<>();

    for (PlanarDelaunayTriangulation.ResultEdge curr_line : triangulation_lines)
    {
      Edge new_edge = new Edge((NetItem) curr_line.start_object, curr_line.start_point.to_float(), (NetItem) curr_line.end_object, curr_line.end_point.to_float());
      sorted_edges.add(new_edge);
    }

    // Create the Airlines. Skip edges, whose from_item and to_item are already in the same
    // connected set
    // or whose connected sets have already an airline.
    Net curr_net = p_board.rules.nets.get(p_net_no);
    for (Edge curr_edge : sorted_edges)
    {
      if (curr_edge.from_item.connected_set == curr_edge.to_item.connected_set)
      {
        continue; // airline exists already
      }
      this.incompletes.add(new RatsNest.AirLine(curr_net, curr_edge.from_item.item, curr_edge.from_corner, curr_edge.to_item.item, curr_edge.to_corner));
      join_connected_sets(net_items, curr_edge.from_item.connected_set, curr_edge.to_item.connected_set);
    }
    calc_length_violation();
  }

  static void draw_layer_change_marker(FloatPoint p_location, double p_radius, Graphics p_graphics, GraphicsContext p_graphics_context)
  {
    final int draw_width = 1;
    Color draw_color = p_graphics_context.get_incomplete_color();
    double draw_intensity = p_graphics_context.get_incomplete_color_intensity();
    FloatPoint[] draw_points = new FloatPoint[2];
    draw_points[0] = new FloatPoint(p_location.x - p_radius, p_location.y - p_radius);
    draw_points[1] = new FloatPoint(p_location.x + p_radius, p_location.y + p_radius);
    p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
    draw_points[0] = new FloatPoint(p_location.x + p_radius, p_location.y - p_radius);
    draw_points[1] = new FloatPoint(p_location.x - p_radius, p_location.y + p_radius);
    p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
  }

  static void draw_length_violation_marker(FloatPoint p_location, double p_diameter, Graphics p_graphics, GraphicsContext p_graphics_context)
  {
    final int draw_width = 1;
    Color draw_color = p_graphics_context.get_incomplete_color();
    double draw_intensity = p_graphics_context.get_incomplete_color_intensity();
    double circle_radius = 0.5 * Math.abs(p_diameter);
    p_graphics_context.draw_circle(p_location, circle_radius, draw_width, draw_color, p_graphics, draw_intensity);
    FloatPoint[] draw_points = new FloatPoint[2];
    draw_points[0] = new FloatPoint(p_location.x - circle_radius, p_location.y);
    draw_points[1] = new FloatPoint(p_location.x + circle_radius, p_location.y);
    p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
    if (p_diameter > 0)
    {
      // draw also the vertical diameter to create a "+"
      draw_points[0] = new FloatPoint(p_location.x, p_location.y - circle_radius);
      draw_points[1] = new FloatPoint(p_location.x, p_location.y + circle_radius);
      p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
    }
  }

  /**
   * Returns the number of incompletes/airlines of this net.
   */
  public int count()
  {
    return incompletes.size();
  }

  /**
   * Recalculates the length violations. Return false, if the length violation has not changed.
   */
  boolean calc_length_violation()
  {
    double old_violation = this.length_violation;
    double max_length = this.net
        .get_class()
        .get_maximum_trace_length();
    double min_length = this.net
        .get_class()
        .get_minimum_trace_length();
    if (max_length <= 0 && min_length <= 0)
    {
      this.length_violation = 0;
      return false;
    }
    double new_violation = 0;
    double trace_length = this.net.get_trace_length();
    if (max_length > 0 && trace_length > max_length)
    {
      new_violation = trace_length - max_length;
    }
    if (min_length > 0 && trace_length < min_length && this.incompletes.isEmpty())
    {
      new_violation = trace_length - min_length;
    }
    this.length_violation = new_violation;
    return Math.abs(new_violation - old_violation) > 0.1;
  }

  /**
   * Returns the length of the violation of the length restriction of the net, > 0, if the
   * cumulative trace length is too big, < 0, if the trace length is too small, 0, if the trace
   * length is ok or the net has no length restrictions
   */
  double get_length_violation()
  {
    return this.length_violation;
  }

  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, boolean p_length_violations_only)
  {
    if (!p_length_violations_only)
    {
      Color draw_color = p_graphics_context.get_incomplete_color();
      double draw_intensity = p_graphics_context.get_incomplete_color_intensity();
      if (draw_intensity <= 0)
      {
        return;
      }
      FloatPoint[] draw_points = new FloatPoint[2];
      int draw_width = 1;
      for (RatsNest.AirLine curr_incomplete : incompletes)
      {
        draw_points[0] = curr_incomplete.from_corner;
        draw_points[1] = curr_incomplete.to_corner;
        p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
        if (!curr_incomplete.from_item.shares_layer(curr_incomplete.to_item))
        {
          draw_layer_change_marker(curr_incomplete.from_corner, this.draw_marker_radius, p_graphics, p_graphics_context);
          draw_layer_change_marker(curr_incomplete.to_corner, this.draw_marker_radius, p_graphics, p_graphics_context);
        }
      }
    }
    if (this.length_violation == 0)
    {
      return;
    }
    // draw the length violation around every Pin of the net.
    Collection<Pin> net_pins = this.net.get_pins();
    for (Pin curr_pin : net_pins)
    {
      draw_length_violation_marker(curr_pin
          .get_center()
          .to_float(), this.length_violation, p_graphics, p_graphics_context);
    }
  }

  /**
   * Calculates an array of Item-connected_set pairs for the items of this net. Pairs belonging to
   * the same connected set are located next to each other.
   */
  private NetItem[] calculate_net_items(Collection<Item> p_item_list)
  {
    ArrayList<NetItem> result = new ArrayList<>();
    Set<Item> unique_items = new HashSet<>(p_item_list);
    int unique_items_count = unique_items.size();

    while (!unique_items.isEmpty())
    {
      Item start_item = unique_items.iterator().next();
      Collection<Item> curr_connected_set = start_item.get_connected_set(this.net.net_number);

      // Prevent ConcurrentModificationException by creating a list of items to remove
      Collection<Item> items_in_component = new ArrayList<>();
      for (Item item_in_set : curr_connected_set) {
        if (unique_items.contains(item_in_set)) {
          items_in_component.add(item_in_set);
        }
      }

      for (Item curr_item : items_in_component)
      {
        result.add(new NetItem(curr_item, curr_connected_set));
      }
      unique_items.removeAll(items_in_component);
    }

    if (result.size() > unique_items_count)
    {
      FRLogger.warn("NetIncompletes.calculate_net_items: too many items");
    }
    else if (result.size() < unique_items_count)
    {
      FRLogger.warn("NetIncompletes.calculate_net_items: too few items");
    }
    return result.toArray(new NetItem[0]);
  }

  /**
   * Joins p_from_connected_set to p_to_connected_set and updates the connected sets of the items in
   * p_net_items.
   */
  private void join_connected_sets(NetItem[] p_net_items, Collection<Item> p_from_connected_set, Collection<Item> p_to_connected_set)
  {
    for (int i = 0; i < p_net_items.length; ++i)
    {
      NetItem curr_item = p_net_items[i];
      if (curr_item.connected_set == p_from_connected_set)
      {
        p_to_connected_set.add(curr_item.item);
        curr_item.connected_set = p_to_connected_set;
      }
    }
  }

  private static class Edge implements Comparable<Edge>
  {
    public final NetItem from_item;
    public final FloatPoint from_corner;
    public final NetItem to_item;
    public final FloatPoint to_corner;
    public final double length_square;

    private Edge(NetItem p_from_item, FloatPoint p_from_corner, NetItem p_to_item, FloatPoint p_to_corner)
    {
      from_item = p_from_item;
      from_corner = p_from_corner;
      to_item = p_to_item;
      to_corner = p_to_corner;
      length_square = p_to_corner.distance_square(p_from_corner);
    }

    @Override
    public int compareTo(Edge p_other)
    {
      double result = this.length_square - p_other.length_square;
      if (result == 0)
      {
        // prevent result 0, so that edges with the same length as another edge are not skipped in
        // the set
        result = this.from_corner.x - p_other.from_corner.x;
        if (result == 0)
        {
          result = this.from_corner.y - p_other.from_corner.y;
        }
        if (result == 0)
        {
          result = this.to_corner.x - p_other.to_corner.x;
        }
        if (result == 0)
        {
          result = this.to_corner.y - p_other.to_corner.y;
        }
      }
      return Signum.as_int(result);
    }
  }

  private static class NetItem implements PlanarDelaunayTriangulation.Storable
  {
    final Item item;
    Collection<Item> connected_set;

    NetItem(Item p_item, Collection<Item> p_connected_set)
    {
      item = p_item;
      connected_set = p_connected_set;
    }

    @Override
    public Point[] get_triangulation_corners()
    {
      return this.item.get_ratsnest_corners();
    }
  }
}