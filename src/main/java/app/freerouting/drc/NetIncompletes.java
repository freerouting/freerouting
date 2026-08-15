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
   * @param netNo The net number.
   * @param netItems The collection of items belonging to this net.
   * @param board The board context.
   */
  public NetIncompletes(int netNo, Collection<Item> netItems, BasicBoard board) {
    this.drawMarkerRadius = board.rules.getMinTraceHalfWidth() * 2;
    this.incompletes = new LinkedList<>();
    this.net = board.rules.nets.get(netNo);

    String netLabel = "Net #" + netNo + (net != null ? " (" + net.name + ")" : "");

    FRLogger.trace(
        "NetIncompletes.<init>",
        "start_calculation",
        "Starting incomplete calculation: net="
            + netNo
            + ", name="
            + (net != null ? net.name : "null")
            + ", total_items_in_collection="
            + netItems.size(),
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
    for (Item item : netItems) {
      // Track ConductionArea items
      if (item instanceof ConductionArea) {
        conductionAreaCount++;
      }

      // Skip dangling vias and traces - they're violations, not incomplete
      // connections
      if (item.isTail()) {
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
          && item.getNormalContacts().isEmpty()) {
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
    NetItem[] groupedNetItems = calculateNetItems(filteredItems);

    Set<Collection<Item>> uniqueConnectedSets = new HashSet<>();
    for (NetItem netItem : groupedNetItems) {
      uniqueConnectedSets.add(netItem.connectedSet);
    }
    this.connectedGroupCount = uniqueConnectedSets.size();

    FRLogger.trace(
        "NetIncompletes.<init>",
        "connected_sets_calculated",
        "Connected sets calculated: net_items_count="
            + groupedNetItems.length
            + ", uniqueConnectedSets="
            + uniqueConnectedSets.size()
            + " (for N groups, expect N-1 airlines)",
        netLabel,
        new Point[0]);

    if (groupedNetItems.length <= 1) {
      this.connectedGroupCount = groupedNetItems.length;
      FRLogger.trace(
          "NetIncompletes.<init>",
          "fully_connected",
          "Net is fully connected or has no routable items: netItems=" + groupedNetItems.length,
          netLabel,
          new Point[0]);
      return;
    }

    // create a Delaunay Triangulation for the netItems
    Collection<PlanarDelaunayTriangulation.Storable> triangulationObjects =
        new LinkedList<>(Arrays.asList(groupedNetItems));
    PlanarDelaunayTriangulation triangulation =
        new PlanarDelaunayTriangulation(triangulationObjects);

    // sort the result edges of the triangulation by length in ascending order.
    Collection<PlanarDelaunayTriangulation.ResultEdge> triangulationLines =
        triangulation.getEdgeLines();
    SortedSet<Edge> sortedEdges = new TreeSet<>();

    for (PlanarDelaunayTriangulation.ResultEdge currentLine : triangulationLines) {
      Edge newEdge =
          new Edge(
              (NetItem) currentLine.startObject,
              currentLine.startPoint.toFloat(),
              (NetItem) currentLine.endObject,
              currentLine.endPoint.toFloat());
      sortedEdges.add(newEdge);
    }

    // Create the Airlines. Skip edges, whose fromItem and toItem are already in
    // the same
    // connected set
    // or whose connected sets have already an airline.
    Net currentNet = board.rules.nets.get(netNo);
    for (Edge currentEdge : sortedEdges) {
      if (currentEdge.fromItem.connectedSet == currentEdge.toItem.connectedSet) {
        continue; // airline exists already
      }

      this.incompletes.add(
          new AirLine(
              currentNet,
              currentEdge.fromItem.item,
              currentEdge.fromCorner,
              currentEdge.toItem.item,
              currentEdge.toCorner));
      joinConnectedSets(
          groupedNetItems, currentEdge.fromItem.connectedSet, currentEdge.toItem.connectedSet);
    }

    FRLogger.trace(
        "NetIncompletes.<init>",
        "airlines_created",
        "Airlines created: incompleteCount="
            + this.incompletes.size()
            + ", total_items="
            + netItems.size()
            + ", filteredItems="
            + filteredItems.size()
            + ", netItems="
            + groupedNetItems.length
            + ", connected_groups="
            + uniqueConnectedSets.size()
            + " => Formula: total_items - incompleteCount = "
            + (netItems.size() - this.incompletes.size()),
        netLabel,
        new Point[0]);

    calcLengthViolation();
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
  public int getConnectedGroupCount() {
    return this.connectedGroupCount;
  }

  /** Recalculates the length violations. Return false, if the length violation has not changed. */
  boolean calcLengthViolation() {
    double maxLength = this.net.getNetClass().getMaximumTraceLength();
    double minLength = this.net.getNetClass().getMinimumTraceLength();
    if (maxLength <= 0 && minLength <= 0) {
      this.lengthViolation = 0;
      return false;
    }
    double newViolation = 0;
    double traceLength = this.net.getTraceLength();
    if (maxLength > 0 && traceLength > maxLength) {
      newViolation = traceLength - maxLength;
    }
    if (minLength > 0 && traceLength < minLength && this.incompletes.isEmpty()) {
      newViolation = traceLength - minLength;
    }
    double oldViolation = this.lengthViolation;
    this.lengthViolation = newViolation;
    return Math.abs(newViolation - oldViolation) > 0.1;
  }

  /**
   * Returns the length of the violation of the length restriction of the net.
   *
   * @return positive if too long, negative if too short, 0 if valid.
   */
  public double getLengthViolation() {
    return this.lengthViolation;
  }

  /**
   * Calculates an array of Item-connectedSet pairs for the items of this net. Groups items that are
   * physically connected into the same connected set.
   *
   * @param itemList The list of items to group.
   * @return An array of NetItem objects representing the grouped items.
   */
  private NetItem[] calculateNetItems(Collection<Item> itemList) {
    ArrayList<NetItem> result = new ArrayList<>();
    Set<Item> uniqueItems = new HashSet<>(itemList);
    int uniqueItemsCount = uniqueItems.size();

    while (!uniqueItems.isEmpty()) {
      Item startItem = uniqueItems.iterator().next();
      Collection<Item> currentConnectedSet = startItem.getConnectedSet(this.net.netNumber);

      // Prevent ConcurrentModificationException by creating a list of items to remove
      Collection<Item> itemsInComponent = new ArrayList<>();
      for (Item itemInSet : currentConnectedSet) {
        if (uniqueItems.contains(itemInSet)) {
          itemsInComponent.add(itemInSet);
        }
      }

      for (Item currentItem : itemsInComponent) {
        result.add(new NetItem(currentItem, currentConnectedSet));
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
   * Joins fromConnectedSet to toConnectedSet and updates the connected sets of the items in
   * netItems. Used during Kruskal's algorithm to merge sets.
   */
  private void joinConnectedSets(
      NetItem[] netItems, Collection<Item> fromConnectedSet, Collection<Item> toConnectedSet) {
    for (int i = 0; i < netItems.length; i++) {
      NetItem currentItem = netItems[i];
      if (currentItem.connectedSet == fromConnectedSet) {
        toConnectedSet.add(currentItem.item);
        currentItem.connectedSet = toConnectedSet;
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

    private Edge(NetItem fromItem, FloatPoint fromCorner, NetItem toItem, FloatPoint toCorner) {
      this.fromItem = fromItem;
      this.fromCorner = fromCorner;
      this.toItem = toItem;
      this.toCorner = toCorner;
      lengthSquare = toCorner.distanceSquare(fromCorner);
    }

    @Override
    public int compareTo(Edge other) {
      double result = this.lengthSquare - other.lengthSquare;
      if (result == 0) {
        // prevent result 0, so that edges with the same length as another edge are not
        // skipped in
        // the set
        result = this.fromCorner.x - other.fromCorner.x;
        if (result == 0) {
          result = this.fromCorner.y - other.fromCorner.y;
        }
        if (result == 0) {
          result = this.toCorner.x - other.toCorner.x;
        }
        if (result == 0) {
          result = this.toCorner.y - other.toCorner.y;
        }
      }
      return Signum.asInt(result);
    }
  }

  /** Wrapper for an Item used in the Delaunay triangulation, including its connected set. */
  private static class NetItem implements PlanarDelaunayTriangulation.Storable {

    final Item item;
    Collection<Item> connectedSet;

    NetItem(Item item, Collection<Item> connectedSet) {
      this.item = item;
      this.connectedSet = connectedSet;
    }

    @Override
    public Point[] getTriangulationCorners() {
      return this.item.getRatsnestCorners();
    }
  }
}
