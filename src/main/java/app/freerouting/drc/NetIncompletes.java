package app.freerouting.drc;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.datastructures.PlanarDelaunayTriangulation;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Computes and holds the incomplete connections (ratsnest) for a single net. This class is
 * responsible for calculating the minimum spanning tree (or similar structure) to determine which
 * items need to be connected to satisfy the net's connectivity requirements. It also tracks length
 * violations if the net has length constraints.
 */
public class NetIncompletes {

  /** Collection of elements of class AirLine representing the incomplete connections. */
  final Collection<AirLine> incompletes;

  /** The net for which the incompletes are calculated. */
  private final Net net;

  /** The radius of the markers drawn at the ends of airlines or layer changes. */
  private final double drawMarkerRadius;

  /**
   * The length of the violation of the length restriction of the net. > 0: cumulative trace length
   * is too big. < 0: trace length is too small. 0: trace length is ok or the net has no length
   * restrictions.
   */
  private double lengthViolation = 0;

  /** Number of connected groups in this net at calculation time. */
  private int connectedGroupCount;

  /**
   * Creates a new instance of NetIncompletes. Calculates the incomplete connections (ratsnest) for
   * the given net items.
   *
   * @param p_net_no The net number.
   * @param p_net_items The collection of items belonging to this net.
   * @param p_board The board context.
   */
  public NetIncompletes(int p_net_no, Collection<Item> p_net_items, BasicBoard p_board) {
    this.drawMarkerRadius = p_board.rules.get_min_trace_half_width() * 2;
    this.incompletes = new LinkedList<>();
    this.net = p_board.rules.nets.get(p_net_no);

    String netLabel = "Net #" + p_net_no + (net != null ? " (" + net.name + ")" : "");

    FRLogger.trace(
        "NetIncompletes.<init>",
        "start_calculation",
        "Starting incomplete calculation: net="
            + p_net_no
            + ", name="
            + (net != null ? net.name : "null")
            + ", total_items_in_collection="
            + p_net_items.size(),
        netLabel,
        new Point[0]);

    // Filter out dangling items (vias and tracks with is_tail() == true)
    // AND items with zero contacts (unconnected pins/pads)
    // These are DRC violations, not unrouted connections, and should not be counted
    // as incompletes
    Collection<Item> filteredItems = new LinkedList<>();
    int danglingCount = 0;
    int unconnectedCount = 0;
    int conductionAreaCount = 0;
    int conductionAreaFilteredCount = 0;
    for (Item item : p_net_items) {
      // Track ConductionArea items
      if (item instanceof ConductionArea) {
        conductionAreaCount++;
      }

      // Skip dangling vias and traces - they're violations, not incomplete
      // connections
      if (item.is_tail()) {
        danglingCount++;
        continue;
      }
      // Skip items with no contacts - they're isolated/unconnected, not incomplete
      // connections
      // EXCEPT for DrillItems (pins/vias) - unrouted pins legitimately have no
      // contacts
      // and SHOULD appear in the ratsnest
      // EXCEPT for ConductionArea which acts as a connection medium
      if (!(item instanceof ConductionArea)
          && !(item instanceof DrillItem)
          && item.get_normal_contacts().isEmpty()) {
        unconnectedCount++;
        continue;
      }

      // Track if ConductionArea made it through the filter
      if (item instanceof ConductionArea) {
        conductionAreaFilteredCount++;
      }

      filteredItems.add(item);
    }

    FRLogger.trace(
        "NetIncompletes.<init>",
        "filtering_complete",
        "Filtering complete: filteredItems="
            + filteredItems.size()
            + ", dangling="
            + danglingCount
            + ", unconnected="
            + unconnectedCount
            + ", conduction_areas_total="
            + conductionAreaCount
            + ", conduction_areas_kept="
            + conductionAreaFilteredCount,
        netLabel,
        new Point[0]);

    // Create an array of Item-connectedSet pairs.
    NetItem[] netItems = calculate_net_items(filteredItems);

    Set<Collection<Item>> uniqueConnectedSets = new HashSet<>();
    for (NetItem net_item : netItems) {
      uniqueConnectedSets.add(net_item.connectedSet);
    }
    this.connectedGroupCount = uniqueConnectedSets.size();

    FRLogger.trace(
        "NetIncompletes.<init>",
        "connected_sets_calculated",
        "Connected sets calculated: net_items_count="
            + netItems.length
            + ", uniqueConnectedSets="
            + uniqueConnectedSets.size()
            + " (for N groups, expect N-1 airlines)",
        netLabel,
        new Point[0]);

    if (netItems.length <= 1) {
      this.connectedGroupCount = netItems.length;
      FRLogger.trace(
          "NetIncompletes.<init>",
          "fully_connected",
          "Net is fully connected or has no routable items: netItems=" + netItems.length,
          netLabel,
          new Point[0]);
      return;
    }

    // create a Delaunay Triangulation for the netItems
    Collection<PlanarDelaunayTriangulation.Storable> triangulationObjects =
        new LinkedList<>(Arrays.asList(netItems));
    PlanarDelaunayTriangulation triangulation =
        new PlanarDelaunayTriangulation(triangulationObjects);

    // sort the result edges of the triangulation by length in ascending order.
    Collection<PlanarDelaunayTriangulation.ResultEdge> triangulationLines =
        triangulation.get_edge_lines();
    SortedSet<Edge> sortedEdges = new TreeSet<>();

    for (PlanarDelaunayTriangulation.ResultEdge currLine : triangulationLines) {
      Edge newEdge =
          new Edge(
              (NetItem) currLine.startObject,
              currLine.startPoint.to_float(),
              (NetItem) currLine.endObject,
              currLine.endPoint.to_float());
      sortedEdges.add(newEdge);
    }

    // Create the Airlines. Skip edges, whose fromItem and toItem are already in
    // the same
    // connected set
    // or whose connected sets have already an airline.
    Net currNet = p_board.rules.nets.get(p_net_no);
    for (Edge currEdge : sortedEdges) {
      if (currEdge.fromItem.connectedSet == currEdge.toItem.connectedSet) {
        continue; // airline exists already
      }

      this.incompletes.add(
          new AirLine(
              currNet,
              currEdge.fromItem.item,
              currEdge.fromCorner,
              currEdge.toItem.item,
              currEdge.toCorner));
      join_connected_sets(netItems, currEdge.fromItem.connectedSet, currEdge.toItem.connectedSet);
    }

    FRLogger.trace(
        "NetIncompletes.<init>",
        "airlines_created",
        "Airlines created: incompleteCount="
            + this.incompletes.size()
            + ", total_items="
            + p_net_items.size()
            + ", filteredItems="
            + filteredItems.size()
            + ", netItems="
            + netItems.length
            + ", connected_groups="
            + uniqueConnectedSets.size()
            + " => Formula: total_items - incompleteCount = "
            + (p_net_items.size() - this.incompletes.size()),
        netLabel,
        new Point[0]);

    calc_length_violation();
  }

  /** Returns the collection of airlines (incomplete connections) for this net. */
  public Collection<AirLine> getIncompletes() {
    return this.incompletes;
  }

  /** Returns the net associated with these incompletes. */
  public Net getNet() {
    return this.net;
  }

  /**
   * Returns the radius used for drawing markers (e.g., layer changes). This is typically derived
   * from the minimum trace width rules.
   */
  public double getMarkerRadius() {
    return this.drawMarkerRadius;
  }

  /** Returns the number of incompletes/airlines of this net. */
  public int count() {
    return incompletes.size();
  }

  /** Returns the number of connected groups used to compute airlines. */
  public int get_connected_group_count() {
    return this.connectedGroupCount;
  }

  /** Recalculates the length violations. Return false, if the length violation has not changed. */
  boolean calc_length_violation() {
    double oldViolation = this.lengthViolation;
    double maxLength = this.net.get_class().get_maximum_trace_length();
    double minLength = this.net.get_class().get_minimum_trace_length();
    if (maxLength <= 0 && minLength <= 0) {
      this.lengthViolation = 0;
      return false;
    }
    double newViolation = 0;
    double traceLength = this.net.get_trace_length();
    if (maxLength > 0 && traceLength > maxLength) {
      newViolation = traceLength - maxLength;
    }
    if (minLength > 0 && traceLength < minLength && this.incompletes.isEmpty()) {
      newViolation = traceLength - minLength;
    }
    this.lengthViolation = newViolation;
    return Math.abs(newViolation - oldViolation) > 0.1;
  }

  /**
   * Returns the length of the violation of the length restriction of the net.
   *
   * @return positive if too long, negative if too short, 0 if valid.
   */
  public double get_length_violation() {
    return this.lengthViolation;
  }

  /**
   * Calculates an array of Item-connectedSet pairs for the items of this net. Groups items that are
   * physically connected into the same connected set.
   *
   * @param p_item_list The list of items to group.
   * @return An array of NetItem objects representing the grouped items.
   */
  private NetItem[] calculate_net_items(Collection<Item> p_item_list) {
    ArrayList<NetItem> result = new ArrayList<>();
    Set<Item> uniqueItems = new HashSet<>(p_item_list);
    int uniqueItemsCount = uniqueItems.size();

    while (!uniqueItems.isEmpty()) {
      Item startItem = uniqueItems.iterator().next();
      Collection<Item> currConnectedSet = startItem.get_connected_set(this.net.netNumber);

      // Prevent ConcurrentModificationException by creating a list of items to remove
      Collection<Item> itemsInComponent = new ArrayList<>();
      for (Item item_in_set : currConnectedSet) {
        if (uniqueItems.contains(item_in_set)) {
          itemsInComponent.add(item_in_set);
        }
      }

      for (Item currItem : itemsInComponent) {
        result.add(new NetItem(currItem, currConnectedSet));
      }
      uniqueItems.removeAll(itemsInComponent);
    }

    if (result.size() > uniqueItemsCount) {
      FRLogger.warn("NetIncompletes.calculate_net_items: too many items");
    } else if (result.size() < uniqueItemsCount) {
      FRLogger.warn("NetIncompletes.calculate_net_items: too few items");
    }
    return result.toArray(new NetItem[0]);
  }

  /**
   * Joins p_from_connected_set to p_to_connected_set and updates the connected sets of the items in
   * p_net_items. Used during Kruskal's algorithm to merge sets.
   */
  private void join_connected_sets(
      NetItem[] p_net_items,
      Collection<Item> p_from_connected_set,
      Collection<Item> p_to_connected_set) {
    for (int i = 0; i < p_net_items.length; i++) {
      NetItem currItem = p_net_items[i];
      if (currItem.connectedSet == p_from_connected_set) {
        p_to_connected_set.add(currItem.item);
        currItem.connectedSet = p_to_connected_set;
      }
    }
  }

  /**
   * Represents a potential edge (connection) between two NetItems in the Delaunay triangulation.
   * Sortable by length to facilitate finding the shortest connections (Minimum Spanning Tree-like
   * approach).
   */
  private static final class Edge implements Comparable<Edge> {

    public final NetItem fromItem;
    public final FloatPoint fromCorner;
    public final NetItem toItem;
    public final FloatPoint toCorner;
    public final double lengthSquare;

    private Edge(
        NetItem p_from_item, FloatPoint p_from_corner, NetItem p_to_item, FloatPoint p_to_corner) {
      fromItem = p_from_item;
      fromCorner = p_from_corner;
      toItem = p_to_item;
      toCorner = p_to_corner;
      lengthSquare = p_to_corner.distance_square(p_from_corner);
    }

    @Override
    public int compareTo(Edge p_other) {
      double result = this.lengthSquare - p_other.lengthSquare;
      if (result == 0) {
        // prevent result 0, so that edges with the same length as another edge are not
        // skipped in
        // the set
        result = this.fromCorner.x - p_other.fromCorner.x;
        if (result == 0) {
          result = this.fromCorner.y - p_other.fromCorner.y;
        }
        if (result == 0) {
          result = this.toCorner.x - p_other.toCorner.x;
        }
        if (result == 0) {
          result = this.toCorner.y - p_other.toCorner.y;
        }
      }
      return Signum.as_int(result);
    }
  }

  /** Wrapper for an Item used in the Delaunay triangulation, including its connected set. */
  private static class NetItem implements PlanarDelaunayTriangulation.Storable {

    final Item item;
    Collection<Item> connectedSet;

    NetItem(Item p_item, Collection<Item> p_connected_set) {
      item = p_item;
      connectedSet = p_connected_set;
    }

    @Override
    public Point[] get_triangulation_corners() {
      return this.item.get_ratsnest_corners();
    }
  }
}
