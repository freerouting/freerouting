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
 * Computes and holds the incomplete connections (ratsnest) for a single net.
 * This class is responsible for calculating the minimum spanning tree (or
 * similar structure) to determine which items need to be connected
 * to satisfy the net's connectivity requirements.
 * It also tracks length violations if the net has length constraints.
 */
public class NetIncompletes {

    /**
     * Collection of elements of class AirLine representing the incomplete
     * connections.
     */
    final Collection<AirLine> incompletes;
    /**
     * The net for which the incompletes are calculated.
     */
    private final Net net;
    /**
     * The radius of the markers drawn at the ends of airlines or layer changes.
     */
    private final double draw_marker_radius;
    /**
     * The length of the violation of the length restriction of the net.
     * > 0: cumulative trace length is too big.
     * < 0: trace length is too small.
     * 0: trace length is ok or the net has no length restrictions.
     */
    private double length_violation = 0;

    /**
     * Number of connected groups in this net at calculation time.
     */
    private int connected_group_count = 0;

    /**
     * Creates a new instance of NetIncompletes.
     * Calculates the incomplete connections (ratsnest) for the given net items.
     *
     * @param p_net_no    The net number.
     * @param p_net_items The collection of items belonging to this net.
     * @param p_board     The board context.
     */
    public NetIncompletes(int p_net_no, Collection<Item> p_net_items, BasicBoard p_board) {
        this.draw_marker_radius = p_board.rules.get_min_trace_half_width() * 2;
        this.incompletes = new LinkedList<>();
        this.net = p_board.rules.nets.get(p_net_no);

        String netLabel = "Net #" + p_net_no + (net != null ? " (" + net.name + ")" : "");

        FRLogger.trace("NetIncompletes.<init>", "start_calculation",
            "Starting incomplete calculation: net=" + p_net_no
                + ", name=" + (net != null ? net.name : "null")
                + ", total_items_in_collection=" + p_net_items.size(),
            netLabel,
            new Point[0]);

        // Filter out dangling items (vias and tracks with is_tail() == true)
        // AND items with zero contacts (unconnected pins/pads)
        // These are DRC violations, not unrouted connections, and should not be counted
        // as incompletes
        Collection<Item> filtered_items = new LinkedList<>();
        int dangling_count = 0;
        int unconnected_count = 0;
        int conduction_area_count = 0;
        int conduction_area_filtered_count = 0;
        for (Item item : p_net_items) {
            // Track ConductionArea items
            if (item instanceof ConductionArea) {
                conduction_area_count++;
            }

            // Skip dangling vias and traces - they're violations, not incomplete
            // connections
            if (item.is_tail()) {
                dangling_count++;
                continue;
            }
            // Skip items with no contacts - they're isolated/unconnected, not incomplete
            // connections
            // EXCEPT for DrillItems (pins/vias) - unrouted pins legitimately have no
            // contacts
            // and SHOULD appear in the ratsnest
            // EXCEPT for ConductionArea which acts as a connection medium
            if (!(item instanceof ConductionArea) && !(item instanceof DrillItem)
                    && item.get_normal_contacts().isEmpty()) {
                unconnected_count++;
                continue;
            }

            // Track if ConductionArea made it through the filter
            if (item instanceof ConductionArea) {
                conduction_area_filtered_count++;
            }

            filtered_items.add(item);
        }

        FRLogger.trace("NetIncompletes.<init>", "filtering_complete",
            "Filtering complete: filtered_items=" + filtered_items.size()
                + ", dangling=" + dangling_count
                + ", unconnected=" + unconnected_count
                + ", conduction_areas_total=" + conduction_area_count
                + ", conduction_areas_kept=" + conduction_area_filtered_count,
            netLabel,
            new Point[0]);

        // Create an array of Item-connected_set pairs.
        NetItem[] net_items = calculate_net_items(filtered_items);

        Set<Collection<Item>> unique_connected_sets = new HashSet<>();
        for (NetItem net_item : net_items) {
          unique_connected_sets.add(net_item.connected_set);
        }
        this.connected_group_count = unique_connected_sets.size();

        FRLogger.trace("NetIncompletes.<init>", "connected_sets_calculated",
            "Connected sets calculated: net_items_count=" + net_items.length
                + ", unique_connected_sets=" + unique_connected_sets.size()
                + " (for N groups, expect N-1 airlines)",
            netLabel,
            new Point[0]);

        if (net_items.length <= 1) {
          this.connected_group_count = net_items.length;
          FRLogger.trace("NetIncompletes.<init>", "fully_connected",
              "Net is fully connected or has no routable items: net_items=" + net_items.length,
              netLabel,
              new Point[0]);
          return;
        }

        // create a Delaunay Triangulation for the net_items
        Collection<PlanarDelaunayTriangulation.Storable> triangulation_objects = new LinkedList<>(
                Arrays.asList(net_items));
        PlanarDelaunayTriangulation triangulation = new PlanarDelaunayTriangulation(triangulation_objects);

        // sort the result edges of the triangulation by length in ascending order.
        Collection<PlanarDelaunayTriangulation.ResultEdge> triangulation_lines = triangulation.get_edge_lines();
        SortedSet<Edge> sorted_edges = new TreeSet<>();

        for (PlanarDelaunayTriangulation.ResultEdge curr_line : triangulation_lines) {
            Edge new_edge = new Edge((NetItem) curr_line.start_object, curr_line.start_point.to_float(),
                    (NetItem) curr_line.end_object, curr_line.end_point.to_float());
            sorted_edges.add(new_edge);
        }

        // Create the Airlines. Skip edges, whose from_item and to_item are already in
        // the same
        // connected set
        // or whose connected sets have already an airline.
        Net curr_net = p_board.rules.nets.get(p_net_no);
        for (Edge curr_edge : sorted_edges) {
            if (curr_edge.from_item.connected_set == curr_edge.to_item.connected_set) {
                continue; // airline exists already
            }

            this.incompletes.add(new AirLine(curr_net, curr_edge.from_item.item, curr_edge.from_corner,
                    curr_edge.to_item.item, curr_edge.to_corner));
            join_connected_sets(net_items, curr_edge.from_item.connected_set, curr_edge.to_item.connected_set);
        }

        FRLogger.trace("NetIncompletes.<init>", "airlines_created",
            "Airlines created: incomplete_count=" + this.incompletes.size()
                + ", total_items=" + p_net_items.size()
                + ", filtered_items=" + filtered_items.size()
                + ", net_items=" + net_items.length
                + ", connected_groups=" + unique_connected_sets.size()
                + " => Formula: total_items - incomplete_count = " + (p_net_items.size() - this.incompletes.size()),
            netLabel,
            new Point[0]);

        calc_length_violation();
    }

    /**
     * Returns the collection of airlines (incomplete connections) for this net.
     */
    public Collection<AirLine> getIncompletes() {
        return this.incompletes;
    }

    /**
     * Returns the net associated with these incompletes.
     */
    public Net getNet() {
        return this.net;
    }

    /**
     * Returns the radius used for drawing markers (e.g., layer changes).
     * This is typically derived from the minimum trace width rules.
     */
    public double getMarkerRadius() {
        return this.draw_marker_radius;
    }

    /**
     * Returns the number of incompletes/airlines of this net.
     */
    public int count() {
        return incompletes.size();
    }

    /**
     * Returns the number of connected groups used to compute airlines.
     */
    public int get_connected_group_count() {
      return this.connected_group_count;
    }

    /**
     * Recalculates the length violations. Return false, if the length violation has
     * not changed.
     */
    boolean calc_length_violation() {
        double old_violation = this.length_violation;
        double max_length = this.net
                .get_class()
                .get_maximum_trace_length();
        double min_length = this.net
                .get_class()
                .get_minimum_trace_length();
        if (max_length <= 0 && min_length <= 0) {
            this.length_violation = 0;
            return false;
        }
        double new_violation = 0;
        double trace_length = this.net.get_trace_length();
        if (max_length > 0 && trace_length > max_length) {
            new_violation = trace_length - max_length;
        }
        if (min_length > 0 && trace_length < min_length && this.incompletes.isEmpty()) {
            new_violation = trace_length - min_length;
        }
        this.length_violation = new_violation;
        return Math.abs(new_violation - old_violation) > 0.1;
    }

    /**
     * Returns the length of the violation of the length restriction of the net.
     *
     * @return > 0 if too long, < 0 if too short, 0 if valid.
     */
    public double get_length_violation() {
        return this.length_violation;
    }

    /**
     * Calculates an array of Item-connected_set pairs for the items of this net.
     * Groups items that are physically connected into the same connected set.
     *
     * @param p_item_list The list of items to group.
     * @return An array of NetItem objects representing the grouped items.
     */
    private NetItem[] calculate_net_items(Collection<Item> p_item_list) {
        ArrayList<NetItem> result = new ArrayList<>();
        Set<Item> unique_items = new HashSet<>(p_item_list);
        int unique_items_count = unique_items.size();

        while (!unique_items.isEmpty()) {
            Item start_item = unique_items.iterator().next();
            Collection<Item> curr_connected_set = start_item.get_connected_set(this.net.net_number);

            // Prevent ConcurrentModificationException by creating a list of items to remove
            Collection<Item> items_in_component = new ArrayList<>();
            for (Item item_in_set : curr_connected_set) {
                if (unique_items.contains(item_in_set)) {
                    items_in_component.add(item_in_set);
                }
            }

            for (Item curr_item : items_in_component) {
                result.add(new NetItem(curr_item, curr_connected_set));
            }
            unique_items.removeAll(items_in_component);
        }

        if (result.size() > unique_items_count) {
            FRLogger.warn("NetIncompletes.calculate_net_items: too many items");
        } else if (result.size() < unique_items_count) {
            FRLogger.warn("NetIncompletes.calculate_net_items: too few items");
        }
        return result.toArray(new NetItem[0]);
    }

    /**
     * Joins p_from_connected_set to p_to_connected_set and updates the connected
     * sets of the items in p_net_items. Used during Kruskal's algorithm to merge
     * sets.
     */
    private void join_connected_sets(NetItem[] p_net_items, Collection<Item> p_from_connected_set,
            Collection<Item> p_to_connected_set) {
        for (int i = 0; i < p_net_items.length; i++) {
            NetItem curr_item = p_net_items[i];
            if (curr_item.connected_set == p_from_connected_set) {
                p_to_connected_set.add(curr_item.item);
                curr_item.connected_set = p_to_connected_set;
            }
        }
    }

    /**
     * Represents a potential edge (connection) between two NetItems in the Delaunay
     * triangulation.
     * Sortable by length to facilitate finding the shortest connections (Minimum
     * Spanning Tree-like approach).
     */
    private static class Edge implements Comparable<Edge> {

        public final NetItem from_item;
        public final FloatPoint from_corner;
        public final NetItem to_item;
        public final FloatPoint to_corner;
        public final double length_square;

        private Edge(NetItem p_from_item, FloatPoint p_from_corner, NetItem p_to_item, FloatPoint p_to_corner) {
            from_item = p_from_item;
            from_corner = p_from_corner;
            to_item = p_to_item;
            to_corner = p_to_corner;
            length_square = p_to_corner.distance_square(p_from_corner);
        }

        @Override
        public int compareTo(Edge p_other) {
            double result = this.length_square - p_other.length_square;
            if (result == 0) {
                // prevent result 0, so that edges with the same length as another edge are not
                // skipped in
                // the set
                result = this.from_corner.x - p_other.from_corner.x;
                if (result == 0) {
                    result = this.from_corner.y - p_other.from_corner.y;
                }
                if (result == 0) {
                    result = this.to_corner.x - p_other.to_corner.x;
                }
                if (result == 0) {
                    result = this.to_corner.y - p_other.to_corner.y;
                }
            }
            return Signum.as_int(result);
        }
    }

    /**
     * Wrapper for an Item used in the Delaunay triangulation, including its
     * connected set.
     */
    private static class NetItem implements PlanarDelaunayTriangulation.Storable {

        final Item item;
        Collection<Item> connected_set;

        NetItem(Item p_item, Collection<Item> p_connected_set) {
            item = p_item;
            connected_set = p_connected_set;
        }

        @Override
        public Point[] get_triangulation_corners() {
            return this.item.get_ratsnest_corners();
        }
    }
}