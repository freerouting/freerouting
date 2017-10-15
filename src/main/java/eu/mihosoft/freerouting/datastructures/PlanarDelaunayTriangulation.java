/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * PlanarDelaunayTriangulation.java
 *
 * Created on 8. Januar 2005, 10:12
 */

package datastructures;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import geometry.planar.Point;
import geometry.planar.IntPoint;
import geometry.planar.Side;
import geometry.planar.Limits;

/**
 * Creates a Delaunay triangulation in the plane for the input objects.
 * The objects in the input list must implement the interface PlanarDelaunayTriangulation.Storable,
 * which consists of the the method get_triangulation_corners().
 * The result can be read by the funktion get_edge_lines().
 * The algorithm is from Chapter 9.3. of the book Computational Geometry, Algorithms and Applications
 * from M. de Berg, M. van Kreveld, M Overmars  and O Schwarzkopf.
 *
 * @author Alfons Wirtz
 */
public class PlanarDelaunayTriangulation
{
    
    /** Creates a new instance of PlanarDelaunayTriangulation from p_object_list. */
    public PlanarDelaunayTriangulation(Collection<PlanarDelaunayTriangulation.Storable> p_object_list)
    {
        List<Corner> corner_list = new LinkedList<Corner>();
        for (PlanarDelaunayTriangulation.Storable curr_object : p_object_list)
        {
            Point[] curr_corners = curr_object.get_triangulation_corners();
            for (Point curr_corner : curr_corners)
            {
                corner_list.add(new Corner(curr_object, curr_corner));
            }
        }
        
        // create a random permutation of the corners.
        // use a fixed seed to get reproducable result
        random_generator.setSeed(seed);
        Collections.shuffle(corner_list, random_generator);
        
        // create a big triangle contaning all corners in the list to start with.
        
        int bounding_coor = Limits.CRIT_INT;
        Corner[] bounding_corners = new Corner[3];
        bounding_corners[0] = new Corner(null, new IntPoint(bounding_coor, 0));
        bounding_corners[1] =  new Corner(null, new IntPoint(0, bounding_coor));
        bounding_corners[2] = new Corner(null, new IntPoint(-bounding_coor, -bounding_coor));
        
        Edge [] edge_lines = new Edge[3];
        for (int i = 0; i < 2; ++i)
        {
            edge_lines[i] = new Edge(bounding_corners[i], bounding_corners[i+1]);
        }
        edge_lines[2] = new Edge(bounding_corners[2], bounding_corners[0]);
        
        Triangle start_triangle = new Triangle(edge_lines, null);
        
        // Set the left triangle of the edge lines to start_triangle.
        // The right triangles remains null.
        for (Edge curr_edge : edge_lines)
        {
            curr_edge.set_left_triangle(start_triangle);
        }
        
        // Initialize the search graph.
        
        this.search_graph = new TriangleGraph(start_triangle);
        this.degenerate_edges = new LinkedList<Edge>();
        
        // Insert the corners in the corner list into the search graph.
        
        for (Corner curr_corner : corner_list)
        {
            Triangle triangle_to_split = this.search_graph.position_locate(curr_corner);
            this.split(triangle_to_split, curr_corner);
        }
    }
    
    /**
     * Returns all edge lines of the result of the Delaunay Triangulation.
     */
    public Collection<ResultEdge> get_edge_lines()
    {
        Collection<ResultEdge> result = new LinkedList<ResultEdge>();
        for (Edge curr_edge : this.degenerate_edges)
        {
            result.add(new ResultEdge(curr_edge.start_corner.coor, curr_edge.start_corner.object,
                    curr_edge.end_corner.coor, curr_edge.end_corner.object));
        }
        if (this.search_graph.anchor != null)
        {
            Set<Edge> result_edges = new TreeSet<Edge>();
            this.search_graph.anchor.get_leaf_edges(result_edges);
            for (Edge curr_edge : result_edges)
            {
                result.add(new ResultEdge(curr_edge.start_corner.coor, curr_edge.start_corner.object,
                        curr_edge.end_corner.coor, curr_edge.end_corner.object));
            }
        }
        return result;
    }
    
    
    /**
     * Splits p_triangle into 3 new triangles at p_corner, if p_corner lies in the interiour.
     * If p_corner lies on the border, p_triangle and the corresponding neighbour
     * are split into 2 new triangles each at p_corner.
     * If p_corner lies outside this triangle or on a corner, nothing is split.
     * In this case the function returns false.
     */
    private boolean split(Triangle p_triangle, Corner p_corner)
    {
        
        // check, if p_corner is in the interiour of this triangle or
        // if p_corner is contained in an edge line.
        
        Edge containing_edge = null;
        for (int i = 0; i < 3; ++i)
        {
            Edge curr_edge = p_triangle.edge_lines[i];
            Side curr_side;
            if (curr_edge.left_triangle == p_triangle)
            {
                curr_side = p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner);
            }
            else
            {
                curr_side = p_corner.side_of(curr_edge.end_corner, curr_edge.start_corner);
            }
            if (curr_side == Side.ON_THE_RIGHT)
            {
                // p_corner is outside this triangle
                System.out.println("PlanarDelaunayTriangulation.split: p_corner is outside");
                return false;
            }
            else if (curr_side == Side.COLLINEAR)
            {
                if (containing_edge != null)
                {
                    // p_corner is equal to a corner of this triangle
                    
                    Corner common_corner = curr_edge.common_corner(containing_edge);
                    if (common_corner == null)
                    {
                        System.out.println("PlanarDelaunayTriangulation.split: common corner expected");
                        return false;
                    }
                    if (p_corner.object == common_corner.object)
                    {
                        return false;
                    }
                    this.degenerate_edges.add(new Edge(p_corner, common_corner));
                    return true;
                }
                containing_edge = curr_edge;
            }
        }
        
        if (containing_edge == null)
        {
            // split p_triangle into 3 new triangles by adding edges from
            // the corners of  p_triangle to p_corner.
            
            Triangle[] new_triangles = p_triangle.split_at_inner_point(p_corner);
            
            if (new_triangles == null)
            {
                return false;
            }
            
            for (Triangle curr_triangle : new_triangles)
            {
                this.search_graph.insert(curr_triangle, p_triangle);
            }
            
            for (int i = 0; i < 3; ++i)
            {
                legalize_edge(p_corner, p_triangle.edge_lines[i]);
            }
            
        }
        else
        {
            // split this triangle and the neighbour triangle into 4 new triangles by adding edges from
            // the corners of the triangles to p_corner.
            
            Triangle neighbour_to_split = containing_edge.other_neighbour(p_triangle);
            
            Triangle[] new_triangles = p_triangle.split_at_border_point(p_corner, neighbour_to_split);
            if (new_triangles  == null)
            {
                return false;
            }
            
            // There are exact four new triangles with the first 2 dividing p_triangle and
            // the last 2 dividing neighbour_to_split.
            this.search_graph.insert(new_triangles[0], p_triangle);
            this.search_graph.insert(new_triangles[1], p_triangle);
            this.search_graph.insert(new_triangles[2], neighbour_to_split);
            this.search_graph.insert(new_triangles[3], neighbour_to_split);
            
            for (int i = 0; i < 3; ++i)
            {
                Edge curr_edge = p_triangle.edge_lines[i];
                if (curr_edge != containing_edge)
                {
                    legalize_edge(p_corner, curr_edge);
                }
            }
            for (int i = 0; i < 3; ++i)
            {
                Edge curr_edge = neighbour_to_split.edge_lines[i];
                if (curr_edge != containing_edge)
                {
                    legalize_edge(p_corner, curr_edge);
                }
            }
        }
        return true;
    }
    
    /**
     * Flips p_edge, if it is no legal edge of the Delaunay Triangulation.
     * p_corner is the last inserted corner of the triangulation
     * Return true, if the triangulation was changed.
     */
    private boolean legalize_edge(Corner p_corner, Edge p_edge)
    {
        if (p_edge.is_legal())
        {
            return false;
        }
        Triangle triangle_to_change;
        if (p_edge.left_triangle.opposite_corner(p_edge) == p_corner)
        {
            triangle_to_change = p_edge.right_triangle;
        }
        else if (p_edge.right_triangle.opposite_corner(p_edge) == p_corner)
        {
            triangle_to_change = p_edge.left_triangle;
        }
        else
        {
            System.out.println("PlanarDelaunayTriangulation.legalize_edge: edge lines inconsistant");
            return false;
        }
        Edge flipped_edge = p_edge.flip();
        
        // Update the search graph.
        
        this.search_graph.insert(flipped_edge.left_triangle, p_edge.left_triangle);
        this.search_graph.insert(flipped_edge.right_triangle, p_edge.left_triangle);
        this.search_graph.insert(flipped_edge.left_triangle, p_edge.right_triangle);
        this.search_graph.insert(flipped_edge.right_triangle, p_edge.right_triangle);
        
        // Call this function recursively for the other edge lines of triangle_to_change.
        for (int i = 0; i < 3; ++i)
        {
            Edge curr_edge = triangle_to_change.edge_lines[i];
            if (curr_edge != p_edge)
            {
                legalize_edge(p_corner, curr_edge);
            }
        }
        return true;
    }
    
    
    /**
     * Checks the consistancy of the triangles in this triagulation.
     * Used for debugging purposes.
     */
    public boolean validate()
    {
        boolean result = this.search_graph.anchor.validate();
        if (result == true)
        {
            System.out.println("Delauny triangulation check passed ok");
        }
        else
        {
            System.out.println("Delauny triangulation check has detected problems");
        }
        return result;
    }
    
    /**
     * Creates a new unique edge id number.
     */
    private int new_edge_id_no()
    {
        ++this.last_edge_id_no;
        return this.last_edge_id_no;
    }
    
    /**
     * The structure for seaching the triangle containing a given input corner.
     */
    private final TriangleGraph search_graph;
    
    /**
     * This list contain the edges of the trinangulation, where the start corner and end corner are equal.
     */
    private Collection<Edge> degenerate_edges;
    
    /**
     * id numbers are for implementing an ordering on the Edges so that they can be used in a set for example
     */
    private int last_edge_id_no = 0;
    
    /**
     * Randum generatur to shuffle the input corners.
     * A fixed seed is used to make the results reproduceble.
     */
    static private int seed = 99;
    static private java.util.Random random_generator = new java.util.Random(seed);
    
    /**
     * Interface with funktionality required for objects to be used
     * in a planar triangulation.
     */
    public interface Storable
    {
        /**
         * Returns an array of corners, which can be used in a planar triangulation.
         */
        geometry.planar.Point[] get_triangulation_corners();
    }
    
    /**
     * Describes a line segment in the result of the Delaunay Triangulation.
     */
    public static class ResultEdge
    {
        private ResultEdge(Point p_start_point, PlanarDelaunayTriangulation.Storable p_start_object,
                Point p_end_point, PlanarDelaunayTriangulation.Storable p_end_object)
        {
            start_point = p_start_point;
            start_object = p_start_object;
            end_point = p_end_point;
            end_object = p_end_object;
        }
        /** The start point of the line segment */
        public final Point start_point;
        /** The object at the start point of the line segment */
        public final PlanarDelaunayTriangulation.Storable start_object;
        /** The end point of the line segment */
        public final Point end_point;
        /** The object at the end point of the line segment */
        public final PlanarDelaunayTriangulation.Storable end_object;
    }
    
    /**
     * Contains a corner point together with the objects this corner belongs to.
     */
    private static class Corner
    {
        public Corner(PlanarDelaunayTriangulation.Storable p_object, Point p_coor)
        {
            object = p_object;
            coor = p_coor;
        }
        
        /**
         * The function returns
         *         Side.ON_THE_LEFT, if this corner is on the left of the line from p_1 to p_2;
         *         Side.ON_THE_RIGHT, if this corner is on the right of the line from p_1 to p_2;
         *     and Side.COLLINEAR, if this corner is collinear with p_1 and p_2.
         */
        public Side side_of(Corner p_1, Corner p_2)
        {
            return this.coor.side_of(p_1.coor, p_2.coor);
        }
        
        public final PlanarDelaunayTriangulation.Storable object;
        public final Point coor;
    }
    
    /**
     * Describes an edge between two triangles in the triangulation.
     * The unique id_nos are for making edges comparable.
     */
    private class Edge implements Comparable<Edge>
    {
        public Edge(Corner p_start_corner, Corner p_end_corner)
        {
            start_corner = p_start_corner;
            end_corner = p_end_corner;
            id_no = new_edge_id_no();
        }
        
        public int compareTo(Edge p_other)
        {
            return (this.id_no - p_other.id_no);
        }
        
        public void set_left_triangle(Triangle p_triangle)
        {
            left_triangle = p_triangle;
        }
        
        public Triangle get_left_triangle()
        {
            return left_triangle;
        }
        
        public void set_right_triangle(Triangle p_triangle)
        {
            right_triangle = p_triangle;
        }
        
        public Triangle get_right_triangle()
        {
            return right_triangle;
        }
        
        /**
         * Returns the common corner of this edge and p_other,
         * or null, if no commen corner exists.
         */
        public Corner common_corner(Edge p_other)
        {
            Corner result = null;
            if (p_other.start_corner.equals(this.start_corner) || p_other.end_corner.equals(this.start_corner))
            {
                result = this.start_corner;
            }
            else if (p_other.start_corner.equals(this.end_corner) || p_other.end_corner.equals(this.end_corner))
            {
                result = this.end_corner;
            }
            return result;
        }
        
        /**
         * Returns the neighbour triangle of this edge, which is different from p_triangle.
         * If p_triangle is not a neighbour of this edge, null is returned.
         */
        public Triangle other_neighbour( Triangle p_triangle)
        {
            Triangle result;
            if (p_triangle == this.left_triangle)
            {
                result = this.right_triangle;
            }
            else if (p_triangle == this.right_triangle)
            {
                result = this.left_triangle;
            }
            else
            {
                System.out.println("Edge.other_neighbour: inconsistant neigbour triangle");
                result = null;
            }
            return result;
        }
        
        /**
         * Returns true, if this is a legal edge of the Delaunay Triangulation.
         */
        public boolean is_legal()
        {
            if (this.left_triangle == null || this.right_triangle == null)
            {
                return true;
            }
            Corner left_opposite_corner = this.left_triangle.opposite_corner(this);
            Corner right_opposite_corner = this.right_triangle.opposite_corner(this);
            
            boolean inside_circle  = right_opposite_corner.coor.to_float().inside_circle(
                    this.start_corner.coor.to_float(),left_opposite_corner.coor.to_float(),
                    this.end_corner.coor.to_float());
            return !inside_circle;
        }
        
        /**
         * Flips this edge line to the edge line between the opposite corners
         * of the adjacent triangles.
         * Returns the new constructed Edge.
         */
        public Edge flip()
        {
            // Create the flipped edge, so that the start corner of this edge is on the left
            // and the end corner of this edge on the right.
            Edge flipped_edge =
                    new Edge(this.right_triangle.opposite_corner(this), this.left_triangle.opposite_corner(this));
            
            Triangle first_parent = this.left_triangle;
            
            // Calculate the index of this edge line in the left and right adjacent triangles.
            
            int left_index = -1;
            int right_index = -1;
            for (int i = 0; i < 3; ++i)
            {
                if (this.left_triangle.edge_lines[i] == this)
                {
                    left_index = i;
                }
                if (this.right_triangle.edge_lines[i] == this)
                {
                    right_index = i;
                }
            }
            if (left_index < 0 || right_index < 0)
            {
                System.out.println("Edge.flip: edge line inconsistant");
                return null;
            }
            Edge left_prev_edge = this.left_triangle.edge_lines[(left_index + 2) % 3];
            Edge left_next_edge = this.left_triangle.edge_lines[(left_index + 1) % 3];
            Edge right_prev_edge = this.right_triangle.edge_lines[(right_index + 2) % 3];
            Edge right_next_edge = this.right_triangle.edge_lines[(right_index + 1) % 3];
            
            // Create the left triangle of the flipped edge.
            
            Edge [] curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = flipped_edge;
            curr_edge_lines[1] = left_prev_edge;
            curr_edge_lines[2] = right_next_edge;
            Triangle new_left_triangle = new Triangle(curr_edge_lines, first_parent);
            flipped_edge.left_triangle = new_left_triangle;
            if (left_prev_edge.left_triangle == this.left_triangle)
            {
                left_prev_edge.left_triangle = new_left_triangle;
            }
            else
            {
                left_prev_edge.right_triangle = new_left_triangle;
            }
            if (right_next_edge.left_triangle == this.right_triangle)
            {
                right_next_edge.left_triangle = new_left_triangle;
            }
            else
            {
                right_next_edge.right_triangle = new_left_triangle;
            }
            
            // Create the right triangle of the flipped edge.
            
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = flipped_edge;
            curr_edge_lines[1] = right_prev_edge;
            curr_edge_lines[2] = left_next_edge;
            Triangle new_right_triangle = new Triangle(curr_edge_lines, first_parent);
            flipped_edge.right_triangle = new_right_triangle;
            if (right_prev_edge.left_triangle == this.right_triangle)
            {
                right_prev_edge.left_triangle = new_right_triangle;
            }
            else
            {
                right_prev_edge.right_triangle = new_right_triangle;
            }
            if (left_next_edge.left_triangle == this.left_triangle)
            {
                left_next_edge.left_triangle = new_right_triangle;
            }
            else
            {
                left_next_edge.right_triangle = new_right_triangle;
            }
            
            return flipped_edge;
        }
        
        /**
         * Checks the consistancy of this edge in its database.
         * Used for debugging purposes.
         */
        public boolean validate()
        {
            boolean result = true;
            if (this.left_triangle == null)
            {
                if (this.start_corner.object != null || this.end_corner.object != null)
                {
                    System.out.println("Edge.validate: left triangle may be null only for bounding edges");
                    result = false;
                }
            }
            else
            {
                // check if the left triangle contains this edge
                boolean found = false;
                for (int i = 0; i < 3; ++i)
                {
                    if (left_triangle.edge_lines[i] == this)
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    System.out.println("Edge.validate: left triangle does not contain this edge");
                    result = false;
                }
            }
            if (this.right_triangle == null)
            {
                if (this.start_corner.object != null || this.end_corner.object != null)
                {
                    System.out.println("Edge.validate: right triangle may be null only for bounding edges");
                    result = false;
                }
            }
            else
            {
                // check if the left triangle contains this edge
                boolean found = false;
                for (int i = 0; i < 3; ++i)
                {
                    if (right_triangle.edge_lines[i] == this)
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    System.out.println("Edge.validate: right triangle does not contain this edge");
                    result = false;
                }
            }
            
            return result;
        }
        
        
        public final Corner start_corner;
        public final Corner end_corner;
        
        
        /** The triangle on the left side of this edge. */
        private Triangle left_triangle = null;
        /** The triangle on the right side of this edge. */
        private Triangle right_triangle = null;
        /** The unique id number of this triangle. */
        private final int id_no;
    }
    
    
    /**
     * Describes a triangle in the triagulation. edge_lines ia an array of dimension 3.
     * The edge lines arec sorted in counter clock sense around the border of this triangle.
     * The list children points to the children of this triangle, when used as a node in the
     * search graph.
     */
    private class Triangle
    {
        public Triangle(Edge[] p_edge_lines, Triangle p_first_parent)
        {
            this.edge_lines = p_edge_lines;
            // create an empty list for the children.
            this.children = new LinkedList<Triangle>();
            this.first_parent = p_first_parent;
        }
        
        /**
         * Returns true, if this triangle node is a leaf,
         * and false, if it is an inner node.
         */
        public boolean is_leaf()
        {
            return this.children.isEmpty();
        }
        
        /**
         * Gets the corner with index p_no.
         */
        public Corner get_corner(int p_no)
        {
            if (p_no < 0 || p_no >= 3)
            {
                System.out.println("Triangle.get_corner: p_no out of range");
                return null;
            }
            Edge curr_edge = edge_lines[p_no];
            Corner result;
            if (curr_edge.left_triangle == this)
            {
                result = curr_edge.start_corner;
            }
            else if (curr_edge.right_triangle == this)
            {
                result = curr_edge.end_corner;
            }
            else
            {
                System.out.println("Triangle.get_corner: inconsistant edge lines");
                result = null;
            }
            return result;
        }
        
        /**
         * Calculates the opposite corner of this triangle to p_edge_line.
         * Returns null, if p_edge_line is nor an edge line of this triangle.
         */
        public Corner opposite_corner(Edge p_edge_line)
        {
            int edge_line_no = -1;
            for (int i = 0; i < 3; ++i)
            {
                if (this.edge_lines[i] == p_edge_line)
                {
                    edge_line_no = i;
                    break;
                }
            }
            if (edge_line_no < 0)
            {
                System.out.println("Triangle.opposite_corner: p_edge_line not found");
                return null;
            }
            Edge next_edge = this.edge_lines[(edge_line_no + 1)% 3];
            Corner result;
            if (next_edge.left_triangle == this)
            {
                result = next_edge.end_corner;
            }
            else
            {
                result = next_edge.start_corner;
            }
            return result;
        }
        
        /**
         * Checks if p_point is inside or on the border of this triangle.
         */
        public boolean contains(Corner p_corner)
        {
            if (this.is_on_the_left_of_edge_line == null)
            {
                System.out.println("Triangle.contains: array is_on_the_left_of_edge_line not initialized");
                return false;
            }
            for (int i = 0; i < 3; ++i)
            {
                Edge curr_edge = this.edge_lines[i];
                Side curr_side = p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner);
                if (this.is_on_the_left_of_edge_line[i])
                    // checking curr_edge.left_triangle == this instead will not work, if this triangle is an inner node.
                {
                    if (curr_side == Side.ON_THE_RIGHT)
                    {
                        return false;
                    }
                }
                else
                {
                    if (curr_side == Side.ON_THE_LEFT)
                    {
                        return false;
                    }
                }
            }
            return true;
        }
        
        /**
         * Puts the edges of all leafs below this node into the list p_result_edges
         */
        public void get_leaf_edges(Set<Edge> p_result_edges)
        {
            if (this.is_leaf())
            {
                for (int i = 0; i < 3; ++i)
                {
                    Edge curr_edge = this.edge_lines[i];
                    if (curr_edge.start_corner.object != null && curr_edge.end_corner.object != null)
                    {
                        // Skip edges containing a bounding corner.
                        p_result_edges.add(curr_edge);
                    }
                }
                
            }
            else
            {
                for (Triangle curr_child : this.children)
                {
                    if (curr_child.first_parent == this) // to prevent traversing nodes more than once
                    {
                        curr_child.get_leaf_edges(p_result_edges);
                    }
                }
            }
        }
        
        /** Split this triangle into 3 new triangles by adding edges from
         * the corners of this triangle to p_corner,
         * p_corner has to be located in the interiour of this triangle.
         */
        
        public Triangle[] split_at_inner_point(Corner p_corner)
        {
            Triangle [] new_triangles = new Triangle[3];
            
            Edge[] new_edges = new Edge[3];
            for (int i = 0; i < 3; ++i)
            {
                new_edges[i] = new Edge(this.get_corner(i), p_corner);
            }
            
            // construct the 3 new triangles.
            Edge [] curr_edge_lines = new Edge[3];
            
            curr_edge_lines[0] = this.edge_lines[0];
            curr_edge_lines[1] = new Edge(this.get_corner(1), p_corner);
            curr_edge_lines[2] = new Edge(p_corner, this.get_corner(0));
            new_triangles[0] = new Triangle(curr_edge_lines, this);
            
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = this.edge_lines[1];
            curr_edge_lines[1] = new Edge(this.get_corner(2), p_corner);
            curr_edge_lines[2] = new_triangles[0].edge_lines[1];
            new_triangles[1] = new Triangle(curr_edge_lines, this);
            
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = this.edge_lines[2];
            curr_edge_lines[1] = new_triangles[0].edge_lines[2];
            curr_edge_lines[2] = new_triangles[1].edge_lines[1];
            new_triangles[2] = new Triangle(curr_edge_lines, this);
            
            // Set the new neigbour triangles of the edge lines.
            
            for(int i = 0; i < 3; ++i)
            {
                Edge curr_edge = new_triangles[i].edge_lines[0];
                if (curr_edge.get_left_triangle() == this)
                {
                    curr_edge.set_left_triangle(new_triangles[i]);
                }
                else
                {
                    curr_edge.set_right_triangle(new_triangles[i]);
                }
                // The other neighbour triangle remains valid.
            }
            
            Edge curr_edge = new_triangles[0].edge_lines[1];
            curr_edge.set_left_triangle(new_triangles[0]);
            curr_edge.set_right_triangle(new_triangles[1]);
            
            curr_edge = new_triangles[1].edge_lines[1];
            curr_edge.set_left_triangle(new_triangles[1]);
            curr_edge.set_right_triangle(new_triangles[2]);
            
            curr_edge = new_triangles[2].edge_lines[1];
            curr_edge.set_left_triangle(new_triangles[0]);
            curr_edge.set_right_triangle(new_triangles[2]);
            return new_triangles;
        }
        
        /**
         * Split this triangle and p_neighbour_to_split into 4 new triangles by adding edges from
         * the corners of the triangles to p_corner.
         * p_corner is assumed to be loacated on the common edge line of this triangle and p_neigbour_to_split.
         * If that is not true, the function returns null.
         * The first 2 result triangles are from splitting this triangle,
         * and the last 2 result triangles are from splitting p_neighbour_to_split.
         */
        public Triangle[] split_at_border_point(Corner p_corner, Triangle p_neighbour_to_split)
        {
            Triangle[] new_triangles = new Triangle[4];
            // look for the triangle edge of this and the neighbour triangle containing p_point;
            int this_touching_edge_no = -1;
            int neigbbour_touching_edge_no = -1;
            Edge touching_edge = null;
            Edge other_touching_edge = null;
            for (int i = 0; i < 3; ++i)
            {
                Edge curr_edge = this.edge_lines[i];
                if (p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner) == Side.COLLINEAR)
                {
                    this_touching_edge_no = i;
                    touching_edge = curr_edge;
                }
                curr_edge = p_neighbour_to_split.edge_lines[i];
                if (p_corner.side_of(curr_edge.start_corner, curr_edge.end_corner) == Side.COLLINEAR)
                {
                    neigbbour_touching_edge_no = i;
                    other_touching_edge = curr_edge;
                }
            }
            if (this_touching_edge_no < 0 || neigbbour_touching_edge_no < 0)
            {
                System.out.println("Triangle.split_at_border_point: touching edge not found");
                return null;
            }
            if (touching_edge != other_touching_edge)
            {
                System.out.println("Triangle.split_at_border_point: edges inconsistent");
                return null;
            }
            
            Edge first_common_new_edge;
            Edge second_common_new_edge;
            // Construct the new edge lines that 2 split triangles of this triangle
            // will be on the left side of the new common touching edges.
            if (this == touching_edge.left_triangle)
            {
                first_common_new_edge = new Edge(touching_edge.start_corner, p_corner);
                second_common_new_edge = new Edge(p_corner, touching_edge.end_corner);
            }
            else
            {
                first_common_new_edge = new Edge(touching_edge.end_corner, p_corner);
                second_common_new_edge = new Edge( p_corner, touching_edge.start_corner);
            }
            
            // Construct the first split triangle of this triangle.
            
            Edge prev_edge = this.edge_lines[(this_touching_edge_no + 2) % 3];
            Edge this_splitting_edge;
            // construct the splitting edge line of this triangle, so that the first split
            // triangle lies on the left side, and the second split triangle on the right side.
            if (this == prev_edge.left_triangle)
            {
                this_splitting_edge = new Edge(p_corner, prev_edge.start_corner);
            }
            else
            {
                this_splitting_edge = new Edge(p_corner, prev_edge.end_corner);
            }
            Edge[] curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = prev_edge;
            curr_edge_lines[1] = first_common_new_edge;
            curr_edge_lines[2] = this_splitting_edge;
            new_triangles[0] = new Triangle(curr_edge_lines, this);
            if (this == prev_edge.left_triangle)
            {
                prev_edge.set_left_triangle(new_triangles[0]);
            }
            else
            {
                prev_edge.set_right_triangle(new_triangles[0]);
            }
            first_common_new_edge.set_left_triangle(new_triangles[0]);
            this_splitting_edge.set_left_triangle(new_triangles[0]);
            
            // Construct the second split triangle of this triangle.
            
            Edge next_edge = this.edge_lines[(this_touching_edge_no + 1) % 3];
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = this_splitting_edge;
            curr_edge_lines[1] = second_common_new_edge;
            curr_edge_lines[2] = next_edge;
            new_triangles[1] = new Triangle(curr_edge_lines, this);
            this_splitting_edge.set_right_triangle(new_triangles[1]);
            second_common_new_edge.set_left_triangle(new_triangles[1]);
            if (this == next_edge.left_triangle)
            {
                next_edge.set_left_triangle(new_triangles[1]);
            }
            else
            {
                next_edge.set_right_triangle(new_triangles[1]);
            }
            
            // construct the first split triangle of p_neighbour_to_split
            next_edge = p_neighbour_to_split.edge_lines[(neigbbour_touching_edge_no + 1) % 3];
            Edge neighbour_splitting_edge;
            // construct the splitting edge line of p_neighbour_to_split, so that the first split
            // triangle lies on the left side, and the second split triangle on the right side.
            if (p_neighbour_to_split == next_edge.left_triangle)
            {
                neighbour_splitting_edge = new Edge(next_edge.end_corner, p_corner);
            }
            else
            {
                neighbour_splitting_edge = new Edge(next_edge.start_corner, p_corner);
            }
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = neighbour_splitting_edge;
            curr_edge_lines[1] = first_common_new_edge;
            curr_edge_lines[2] = next_edge;
            new_triangles[2] = new Triangle(curr_edge_lines, p_neighbour_to_split);
            neighbour_splitting_edge.set_left_triangle(new_triangles[2]);
            first_common_new_edge.set_right_triangle(new_triangles[2]);
            if (p_neighbour_to_split == next_edge.left_triangle)
            {
                next_edge.set_left_triangle(new_triangles[2]);
            }
            else
            {
                next_edge.set_right_triangle(new_triangles[2]);
                
            }
            
            // construct the second split triangle of p_neighbour_to_split
            prev_edge = p_neighbour_to_split.edge_lines[(neigbbour_touching_edge_no + 2) % 3];
            curr_edge_lines = new Edge[3];
            curr_edge_lines[0] = prev_edge;
            curr_edge_lines[1] = second_common_new_edge;
            curr_edge_lines[2] = neighbour_splitting_edge;
            new_triangles[3] = new Triangle(curr_edge_lines, p_neighbour_to_split);
            if (p_neighbour_to_split == prev_edge.left_triangle)
            {
                prev_edge.set_left_triangle(new_triangles[3]);
            }
            else
            {
                prev_edge.set_right_triangle(new_triangles[3]);
                
            }
            second_common_new_edge.set_right_triangle(new_triangles[3]);
            neighbour_splitting_edge.set_right_triangle(new_triangles[3]);
            
            return new_triangles;
        }
        
        /**
         * Checks the consistancy of this triangle and its children.
         * Used for debugging purposes.
         */
        public boolean validate()
        {
            boolean result = true;
            if (this.is_leaf())
            {
                Edge prev_edge = this.edge_lines[2];
                for (int i = 0; i < 3; ++i)
                {
                    Edge curr_edge = this.edge_lines[i];
                    if (!curr_edge.validate())
                    {
                        result = false;
                    }
                    // Check, if the ens corner of the previous line equals to the start corner of this line.
                    Corner prev_end_corner;
                    if (prev_edge.left_triangle == this)
                    {
                        prev_end_corner = prev_edge.end_corner;
                    }
                    else
                    {
                        prev_end_corner = prev_edge.start_corner;
                    }
                    Corner curr_start_corner;
                    if (curr_edge.left_triangle == this)
                    {
                        curr_start_corner = curr_edge.start_corner;
                    }
                    else if (curr_edge.right_triangle == this)
                    {
                        curr_start_corner = curr_edge.end_corner;
                    }
                    else
                    {
                        System.out.println("Triangle.validate: edge inconsistent");
                        return false;
                    }
                    if (curr_start_corner != prev_end_corner)
                    {
                        System.out.println("Triangle.validate: corner inconsistent");
                        result = false;
                    }
                    prev_edge = curr_edge;
                }
            }
            else
            {
                for (Triangle curr_child: this.children)
                {
                    if (curr_child.first_parent == this) // to avoid traversing nodes more than once.
                    {
                        curr_child.validate();
                    }
                }
            }
            return result;
        }
        
        /**
         * Must be done as long as this triangle node is a leaf and after for all its edge lines
         * the left_triangle or the right_triangle reference is set to this triangle.
         */
        private void initialize_is_on_the_left_of_edge_line_array()
        {
            if (this.is_on_the_left_of_edge_line != null)
            {
                return; // already initialized
            }
            this.is_on_the_left_of_edge_line = new boolean[3];
            for (int i = 0; i < 3; ++i)
            {
                this.is_on_the_left_of_edge_line[i] = (this.edge_lines[i].left_triangle == this);
            }
        }
        
        /** The 3 edge lines of this triangle sorted in counter clock sense around the border. */
        private final Edge[] edge_lines;
        
        /**
         * Indicates, if this triangle is on the left of the i-th edge line for i = 0 to 2.
         * Must be set, if this triagngle is an inner node
         * because left_triangle and right_triangle of edge lines point only to leaf nodes.
         */
        private boolean[]  is_on_the_left_of_edge_line = null;
        
        /** The children of this triangle when used as a node in the triangle search graph. */
        private Collection<Triangle>  children;
        
        /**
         * Triangles resulting from an edge flip have 2 parents, all other triangles have 1 parent.
         * first parent is used when traversing the graph sequentially to avoid
         * visiting children nodes more than once.
         */
        private final Triangle first_parent;
    }
    
    /**
     * Directed acyclic graph for finding the triangle containing a search point p.
     * The leaves contain the trianngles of the current triangulation.
     * The internal nodes are triangles, that were part of the triangulationn at some earlier stage,
     * but have been replaced their children.
     */
    
    private static class TriangleGraph
    {
        public TriangleGraph(Triangle p_triangle)
        {
            if (p_triangle != null)
            {
                insert(p_triangle, null);
            }
            else
            {
                this.anchor = null;
            }
        }
        
        public void insert(Triangle p_triangle, Triangle p_parent)
        {
            p_triangle.initialize_is_on_the_left_of_edge_line_array();
            if (p_parent == null)
            {
                anchor = p_triangle;
            }
            else
            {
                p_parent.children.add(p_triangle);
            }
        }
        
        /**
         * Search for the leaf triangle containing p_corner.
         * It will not be unique, if p_corner lies on a triangle edge.
         */
        public Triangle position_locate(Corner p_corner)
        {
            if (this.anchor == null)
            {
                return null;
            }
            if (this.anchor.children.isEmpty())
            {
                return this.anchor;
            }
            for (Triangle curr_child : this.anchor.children)
            {
                Triangle result = position_locate_reku(p_corner, curr_child);
                if (result != null)
                {
                    return result;
                }
            }
            System.out.println("TriangleGraph.position_locate: containing triangle not found");
            return null;
        }
        
        /**
         * Recursive part of position_locate.
         */
        private Triangle position_locate_reku(Corner p_corner, Triangle p_triangle)
        {
            if (!p_triangle.contains(p_corner))
            {
                return null;
            }
            
            if (p_triangle.is_leaf())
            {
                return p_triangle;
            }
            for (Triangle curr_child : p_triangle.children)
            {
                Triangle result = position_locate_reku(p_corner, curr_child);
                if (result != null)
                {
                    return result;
                }
            }
            System.out.println("TriangleGraph.position_locate_reku: containing triangle not found");
            return null;
        }
        
        private Triangle anchor = null;
    }
}
