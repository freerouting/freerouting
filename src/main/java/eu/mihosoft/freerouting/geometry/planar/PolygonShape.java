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
 * PolygonShape.java
 *
 * Created on 13. Juni 2003, 12:12
 */

package geometry.planar;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Shape described bei a closed polygon of corner points.
 * The corners are ordered in counterclock sense around the border of the shape.
 * The corners are normalysed, so that the corner with the lowest y-value comes first.
 * In case of equal y-value the corner with the lowest x-value comes first.
 *
 * @author  Alfons Wirtz
 */
public class PolygonShape extends PolylineShape
{
    
    /** Creates a new instance of PolygonShape */
    public PolygonShape(Polygon p_polygon)
    {
        Polygon curr_polygon = p_polygon;
        if (p_polygon.winding_number_after_closing() < 0)
        {
            // the the corners of the polygon are in clockwise sense
            curr_polygon = p_polygon.revert_corners();
        }
        Point [] curr_corners = curr_polygon.corner_array();
        int last_corner_no = curr_corners.length - 1;
        
        if (last_corner_no > 0)
        {
            if (curr_corners[0].equals(curr_corners[last_corner_no]))
            {
                // skip last point
                --last_corner_no;
            }
        }
        
        boolean last_point_collinear = false;
        
        if (last_corner_no >= 2)
        {
            last_point_collinear =
                    curr_corners[last_corner_no].side_of(curr_corners[last_corner_no - 1], curr_corners[0])
                    == Side.COLLINEAR;
        }
        if (last_point_collinear)
        {
            // skip last point
            --last_corner_no;
        }
        
        int first_corner_no = 0;
        boolean first_point_collinear = false;
        
        if (last_corner_no - first_corner_no >= 2)
        {
            first_point_collinear =
                    curr_corners[0].side_of(curr_corners[1], curr_corners[last_corner_no])
                    == Side.COLLINEAR;
        }
        
        if (first_point_collinear)
        {
            // skip first point
            ++first_corner_no;
        }
        // search the point with the lowest y and then with the lowest x
        int start_corner_no = first_corner_no;
        FloatPoint start_corner = curr_corners[start_corner_no].to_float();
        for (int i = start_corner_no + 1; i <= last_corner_no; ++i)
        {
            FloatPoint curr_corner = curr_corners[i].to_float();
            if (curr_corner.y < start_corner.y ||
                    curr_corner.y == start_corner.y && curr_corner.x < start_corner.x)
            {
                start_corner_no = i;
                start_corner = curr_corner;
            }
        }
        int new_corner_count =  last_corner_no - first_corner_no + 1;
        Point[] result = new Point[new_corner_count];
        int curr_corner_no = 0;
        for (int i = start_corner_no; i <= last_corner_no; ++i)
        {
            result[curr_corner_no] = curr_corners[i];
            ++curr_corner_no;
        }
        for (int i = first_corner_no; i < start_corner_no; ++i)
        {
            result[curr_corner_no] = curr_corners[i];
            ++curr_corner_no;
        }
        corners = result;
    }
    
    
    public PolygonShape(Point[] p_corner_arr)
    {
        this(new Polygon(p_corner_arr));
    }
    
    
    public Point corner(int p_no)
    {
        if (p_no < 0 || p_no >= corners.length)
        {
            System.out.println("PolygonShape.corner: p_no out of range");
            return null;
        }
        return corners[p_no];
    }
    
    public int border_line_count()
    {
        return corners.length;
    }
    
    public boolean corner_is_bounded(int p_no)
    {
        return true;
    }
    
    public boolean intersects(Shape p_shape)
    {
        return p_shape.intersects(this);
    }
    
    public boolean intersects(Circle p_circle)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (convex_pieces[i].intersects(p_circle))
                return true;
        }
        return false;
    }
    
    public boolean intersects(Simplex p_simplex)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (convex_pieces[i].intersects(p_simplex))
                return true;
        }
        return false;
    }
    
    public boolean intersects(IntOctagon p_oct)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (convex_pieces[i].intersects(p_oct))
                return true;
        }
        return false;
    }
    
    public boolean intersects(IntBox p_box)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (convex_pieces[i].intersects(p_box))
                return true;
        }
        return false;
    }
    
    public Polyline[] cutout(Polyline p_polyline)
    {
        System.out.println("PolygonShape.cutout not yet implemented");
        return null;
    }
    
    public PolygonShape enlarge(double p_offset)
    {
        if (p_offset == 0)
        {
            return this;
        }
        System.out.println("PolygonShape.enlarge not yet implemented");
        return null;
    }
    
    public double border_distance(FloatPoint p_point)
    {
        System.out.println("PolygonShape.border_distance not yet implemented");
        return 0;
    }
    
    public double smallest_radius()
    {
        return border_distance(centre_of_gravity());
    }
    
    public boolean contains(FloatPoint p_point)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (convex_pieces[i].contains(p_point))
                return true;
        }
        return false;
    }
    
    public boolean contains_inside(Point p_point)
    {
        if (contains_on_border(p_point))
        {
            return false;
        }
        return !is_outside(p_point);
    }
    
    public boolean is_outside(Point p_point)
    {
        TileShape[] convex_pieces = split_to_convex();
        for (int i = 0; i < convex_pieces.length; ++i)
        {
            if (!convex_pieces[i].is_outside(p_point))
                return false;
        }
        return true;
    }
    
    public boolean contains(Point p_point)
    {
        return !is_outside(p_point);
    }
    
    public boolean contains_on_border(Point p_point)
    {
        //System.out.println("PolygonShape.contains_on_edge not yet implemented");
        return false;
    }
    
    public double distance(FloatPoint p_point)
    {
        System.out.println("PolygonShape.distance not yet implemented");
        return 0;
    }
    public PolygonShape translate_by(Vector p_vector)
    {
        if (p_vector.equals(Vector.ZERO))
        {
            return this;
        }
        Point[] new_corners = new Point[corners.length];
        for (int i = 0; i < corners.length; ++i)
        {
            new_corners[i] = corners[i].translate_by(p_vector);
        }
        return new PolygonShape(new_corners);
    }
    
    public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs)
    {
        return p_dirs.bounds(this);
    }
    
    public IntBox bounding_box()
    {
        if (precalculated_bounding_box == null)
        {
            double llx = Integer.MAX_VALUE;
            double lly = Integer.MAX_VALUE;
            double urx = Integer.MIN_VALUE;
            double ury = Integer.MIN_VALUE;
            for (int i = 0; i < corners.length; ++i)
            {
                FloatPoint curr = corners[i].to_float();
                llx = Math.min(llx, curr.x);
                lly = Math.min(lly, curr.y);
                urx = Math.max(urx, curr.x);
                ury = Math.max(ury, curr.y);
            }
            IntPoint lower_left = new IntPoint((int)Math.floor(llx),(int)Math.floor(lly));
            IntPoint upper_right = new IntPoint((int)Math.ceil(urx),(int)Math.ceil(ury));
            precalculated_bounding_box = new IntBox(lower_left, upper_right);
        }
        return precalculated_bounding_box;
    }
    public IntOctagon bounding_octagon()
    {
        if (precalculated_bounding_octagon == null)
        {
            double lx = Integer.MAX_VALUE;
            double ly = Integer.MAX_VALUE;
            double rx = Integer.MIN_VALUE;
            double uy = Integer.MIN_VALUE;
            double ulx = Integer.MAX_VALUE;
            double lrx = Integer.MIN_VALUE;
            double llx = Integer.MAX_VALUE;
            double urx = Integer.MIN_VALUE;
            for (int i = 0; i < corners.length; ++i)
            {
                FloatPoint curr = corners[i].to_float();
                lx = Math.min(lx, curr.x);
                ly = Math.min(ly, curr.y);
                rx = Math.max(rx, curr.x);
                uy = Math.max(uy, curr.y);
                
                double tmp = curr.x - curr.y;
                ulx = Math.min(ulx, tmp);
                lrx = Math.max(lrx, tmp);
                
                tmp = curr.x + curr.y;
                llx = Math.min(llx, tmp);
                urx = Math.max(urx, tmp);
            }
            precalculated_bounding_octagon = new
                    IntOctagon((int)Math.floor(lx), (int)Math.floor(ly),
                    (int)Math.ceil(rx), (int)Math.ceil(uy),
                    (int)Math.floor(ulx), (int)Math.ceil(lrx),
                    (int)Math.floor(llx), (int)Math.ceil(urx));
        }
        return precalculated_bounding_octagon;
    }
    
    /**
     * Checks, if every line segment between 2 points of the shape is contained
     * completely in the shape.
     */
    public boolean is_comvex()
    {
        if (corners.length <= 2)
            return true;
        Point prev_point = corners[corners.length - 1];
        Point curr_point = corners[0];
        Point next_point = corners[1];
        
        for (int ind = 0; ind < corners.length; ++ind)
        {
            if (next_point.side_of(prev_point, curr_point) == Side.ON_THE_RIGHT)
                return false;
            prev_point = curr_point;
            curr_point = next_point;
            if (ind == corners.length - 2)
                next_point = corners[0];
            else
                next_point = corners[ind + 2];
        }
        // check, if the sum of the interior angles is at most 2 * pi
        
        Line first_line = new Line(corners[corners.length - 1], corners[0]);
        Line curr_line = new Line(corners[0], corners[1]);
        IntDirection first_direction = (IntDirection)first_line.direction();
        IntDirection curr_direction = (IntDirection)curr_line.direction();
        double last_det = first_direction.determinant(curr_direction);
        
        for (int ind2 = 2; ind2 < corners.length; ++ind2)
        {
            curr_line = new Line(curr_line.b, corners[ind2]);
            curr_direction = (IntDirection)curr_line.direction();
            double curr_det = first_direction.determinant(curr_direction);
            if (last_det <= 0 && curr_det > 0)
                return false;
            last_det = curr_det;
        }
        
        return true;
    }
    
    public PolygonShape convex_hull()
    {
        if (corners.length <= 2)
            return this;
        Point prev_point = corners[corners.length - 1];
        Point curr_point = corners[0];
        Point next_point;
        for (int ind = 0; ind < corners.length; ++ind)
        {
            if (ind == corners.length - 1)
            {
                next_point = corners[0];
            }
            else
            {
                next_point = corners[ind + 1];
            }
            if (next_point.side_of(prev_point, curr_point) != Side.ON_THE_LEFT)
            {
                //skip curr_point;
                Point[] new_corners = new Point[corners.length - 1];
                for (int i = 0; i < ind; ++i)
                {
                    new_corners[i] = corners[i];
                }
                for (int i = ind; i < new_corners.length; ++i)
                {
                    new_corners[i] = corners[i + 1];
                }
                PolygonShape result = new PolygonShape(new_corners);
                return result.convex_hull();
            }
            prev_point = curr_point;
            curr_point = next_point;
        }
        return this;
    }
    
    public TileShape bounding_tile()
    {
        PolygonShape hull = convex_hull();
        Line[] bounding_lines = new Line[hull.corners.length];
        for (int i = 0; i < bounding_lines.length - 1; ++i)
        {
            bounding_lines[i] = new Line(hull.corners[i], hull.corners[i + 1]);
        }
        bounding_lines[bounding_lines.length - 1] =
                new Line(hull.corners[hull.corners.length - 1], hull.corners[0]);
        return TileShape.get_instance(bounding_lines);
    }
    
    public double area()
    {
        
        if (dimension() <= 2)
        {
            return 0;
        }
        // calculate half of the absolute value of
        // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
        // where xi, yi are the coordinates of the i-th corner of this polygon.
        
        double result = 0;
        FloatPoint prev_corner = corners[corners.length - 2].to_float();
        FloatPoint curr_corner = corners[corners.length - 1].to_float();
        for (int i = 0; i < corners.length; ++i)
        {
            FloatPoint next_corner = corners[i].to_float();
            result += curr_corner.x * (next_corner.y - prev_corner.y);
            prev_corner = curr_corner;
            curr_corner = next_corner;
        }
        result = 0.5 * Math.abs(result);
        return result;
    }
    
    public int dimension()
    {
        if (corners.length == 0)
            return -1;
        if (corners.length == 1)
            return 0;
        if (corners.length == 2)
            return 1;
        return 2;
    }
    
    public boolean is_bounded()
    {
        return true;
    }
    
    public boolean is_empty()
    {
        return corners.length == 0;
    }
    
    public Line border_line(int p_no)
    {
        if (p_no < 0 || p_no >= corners.length)
        {
            System.out.println("PolygonShape.edge_line: p_no out of range");
            return null;
        }
        Point next_corner;
        if (p_no == corners.length - 1)
        {
            next_corner = corners[0];
        }
        else
        {
            next_corner = corners[p_no + 1];
        }
        return new Line(corners[p_no], next_corner);
    }
    
    public FloatPoint nearest_point_approx(FloatPoint p_from_point)
    {
        double min_dist = Double.MAX_VALUE;
        FloatPoint result = null;
        TileShape[] convex_shapes = split_to_convex();
        for (int i = 0; i < convex_shapes.length; ++i)
        {
            FloatPoint curr_nearest_point = convex_shapes[i].nearest_point_approx(p_from_point);
            double curr_dist = curr_nearest_point.distance_square(p_from_point);
            if (curr_dist < min_dist)
            {
                min_dist = curr_dist;
                result = curr_nearest_point;
            }
        }
        return result;
    }
    
    public PolygonShape turn_90_degree(int p_factor, IntPoint p_pole)
    {
        Point[] new_corners = new Point[corners.length];
        for (int i = 0; i < corners.length; ++i)
        {
            new_corners[i] = corners[i].turn_90_degree(p_factor, p_pole);
        }
        return new PolygonShape(new_corners);
    }
    
    public PolygonShape rotate_approx(double p_angle, FloatPoint p_pole)
    {
        if (p_angle == 0)
        {
            return this;
        }
        Point[] new_corners = new Point[corners.length];
        for (int i = 0; i < corners.length; ++i)
        {
            new_corners[i] = corners[i].to_float().rotate(p_angle, p_pole).round();
        }
        return new PolygonShape(new_corners);
    }
    
    public PolygonShape mirror_vertical(IntPoint p_pole)
    {
        Point[] new_corners = new Point[corners.length];
        for (int i = 0; i < corners.length; ++i)
        {
            new_corners[i] = corners[i].mirror_vertical(p_pole);
        }
        return new PolygonShape(new_corners);
    }
    
    public PolygonShape mirror_horizontal(IntPoint p_pole)
    {
        Point[] new_corners = new Point[corners.length];
        for (int i = 0; i < corners.length; ++i)
        {
            new_corners[i] = corners[i].mirror_horizontal(p_pole);
        }
        return new PolygonShape(new_corners);
    }
    
    /**
     * Splits this polygon shape into convex pieces.
     * The result is not exact, because rounded intersections of lines are
     * used in the result pieces. It can be made exact, if Polylines are returned
     * instead of Polygons, so that no intersection points are needed in the result.
     */
    public TileShape[]  split_to_convex()
    {
        if (this.precalculated_convex_pieces == null)
            // not yet precalculated
        {
            // use a fixed seed to get reproducable result
            random_generator.setSeed(seed);
            Collection<PolygonShape> convex_pieces = split_to_convex_recu();
            if(convex_pieces == null)
            {
                // split failed, maybe the polygon has selfontersections
                return null;
            }
            precalculated_convex_pieces = new TileShape[convex_pieces.size()];
            Iterator<PolygonShape> it = convex_pieces.iterator();
            for (int i = 0; i < precalculated_convex_pieces.length; ++i)
            {
                PolygonShape curr_piece = it.next();
                precalculated_convex_pieces[i] = TileShape.get_instance(curr_piece.corners);
            }
        }
        return this.precalculated_convex_pieces;
    }
    
    /**
     * Crivate recursive part of split_to_convex.
     * Returns a collection of polygon shape pieces.
     */
    private Collection<PolygonShape> split_to_convex_recu()
    {
        // start with a hashed corner and search the first concave corner
        int start_corner_no =  random_generator.nextInt(corners.length);
        Point curr_corner = corners[start_corner_no];
        Point prev_corner;
        if (start_corner_no != 0)
            prev_corner = corners[start_corner_no - 1];
        else
            prev_corner = corners[corners.length - 1];
        
        Point next_corner = null;
        
        // search for the next concave corner from here
        int concave_corner_no = -1;
        for (int i = 0; i < corners.length; ++i)
        {
            if (start_corner_no < corners.length - 1)
                next_corner = corners[start_corner_no + 1];
            else
                next_corner = corners[0];
            if (next_corner.side_of(prev_corner, curr_corner) == Side.ON_THE_RIGHT)
            {
                // concave corner found
                concave_corner_no = start_corner_no;
                break;
            }
            prev_corner = curr_corner;
            curr_corner = next_corner;
            start_corner_no = (start_corner_no + 1) % corners.length;
        }
        Collection<PolygonShape> result = new LinkedList<PolygonShape>();
        if (concave_corner_no < 0)
        {
            // no concave corner found, this shape is already convex
            result.add(this);
            return result;
        }
        DivisionPoint d = new DivisionPoint(concave_corner_no);
        if (d.projection == null)
        {
            // projection not found, maybe polygon has selfintersections
            return null;
        }
        
        // construct the result pieces from p_polygon and the division point
        int corner_count = d.corner_no_after_projection - concave_corner_no;
        
        if (corner_count < 0)
            corner_count += corners.length;
        ++corner_count;
        Point[] first_arr = new Point[corner_count];
        int corner_ind = concave_corner_no;
        
        for (int i = 0; i < corner_count - 1; ++i)
        {
            first_arr[i] = corners[corner_ind];
            corner_ind = (corner_ind + 1) % corners.length;
        }
        first_arr[corner_count - 1] = d.projection.round();
        PolygonShape first_piece = new PolygonShape(first_arr);
        
        corner_count = concave_corner_no - d.corner_no_after_projection;
        if (corner_count < 0)
            corner_count += corners.length;
        corner_count += 2;
        Point[] last_arr = new Point[corner_count];
        last_arr[0] = d.projection.round();
        corner_ind = d.corner_no_after_projection;
        for (int i = 1; i < corner_count; ++i)
        {
            last_arr[i] = corners[corner_ind];
            corner_ind = (corner_ind + 1) % corners.length;
        }
        PolygonShape last_piece = new PolygonShape(last_arr);
        Collection<PolygonShape> c1 = first_piece.split_to_convex_recu();
        if (c1 == null)
            return null;
        Collection<PolygonShape> c2 = last_piece.split_to_convex_recu();
        if (c2 == null)
            return null;
        result.addAll(c1);
        result.addAll(c2);
        return result;
    }
    
    
    public final Point[] corners;
    
    /**
     * the following fields are for storing precalculated data
     */
    transient private IntBox precalculated_bounding_box = null;
    transient private IntOctagon precalculated_bounding_octagon = null;
    transient private TileShape[] precalculated_convex_pieces = null;
    static private int seed = 99;
    static private java.util.Random random_generator = new java.util.Random(seed);
    
    private class DivisionPoint
    {
        /** At a concave corner  of the closed polygon, a minimal axis parallel
         * division line is constructed, to divide the closed polygon into two.
         */
        DivisionPoint(int p_concave_corner_no)
        {
            FloatPoint concave_corner = corners[p_concave_corner_no].to_float();
            FloatPoint before_concave_corner;
            
            if (p_concave_corner_no != 0)
                before_concave_corner =  corners[p_concave_corner_no - 1].to_float();
            else
                before_concave_corner = corners[corners.length - 1].to_float();
            
            FloatPoint after_concave_corner;
            
            if (p_concave_corner_no == corners.length - 1)
                after_concave_corner = corners[0].to_float();
            else
                after_concave_corner = corners [p_concave_corner_no + 1].to_float();
            
            boolean search_right = before_concave_corner.y > concave_corner.y ||
                    concave_corner.y > after_concave_corner.y;
            
            boolean search_left = before_concave_corner.y < concave_corner.y ||
                    concave_corner.y < after_concave_corner.y;
            
            boolean search_up = before_concave_corner.x < concave_corner.x ||
                    concave_corner.x < after_concave_corner.x;
            
            boolean search_down = before_concave_corner.x > concave_corner.x ||
                    concave_corner.x > after_concave_corner.x;
            
            double min_projection_dist = Integer.MAX_VALUE;
            FloatPoint min_projection = null;
            int corner_no_after_min_projection = 0;
            
            int corner_no_after_curr_projection = (p_concave_corner_no + 2) % corners.length;
            
            Point corner_before_curr_projection;
            if (corner_no_after_curr_projection != 0)
                corner_before_curr_projection = corners[corner_no_after_curr_projection - 1];
            else
                corner_before_curr_projection = corners[corners.length - 1];
            FloatPoint corner_before_projection_approx = corner_before_curr_projection.to_float();
            
            double curr_dist;
            int loop_end = corners.length - 2;
            
            for (int i = 0; i < loop_end; ++i)
            {
                Point corner_after_curr_projection = corners[corner_no_after_curr_projection];
                FloatPoint corner_after_projection_approx = corner_after_curr_projection.to_float();
                if (corner_before_projection_approx.y != corner_after_projection_approx.y)
                    // try a horizontal division
                {
                    double min_y;
                    double max_y;
                    
                    if (corner_after_projection_approx.y > corner_before_projection_approx.y)
                    {
                        min_y = corner_before_projection_approx.y;
                        max_y = corner_after_projection_approx.y;
                    }
                    else
                    {
                        min_y = corner_after_projection_approx.y;
                        max_y = corner_before_projection_approx.y;
                    }
                    
                    if (concave_corner.y >= min_y && concave_corner.y <= max_y)
                    {
                        Line curr_line =
                                new Line(corner_before_curr_projection, corner_after_curr_projection);
                        double x_intersect = curr_line.function_in_y_value_approx(concave_corner.y);
                        curr_dist = Math.abs(x_intersect - concave_corner.x);
                        // Make shure, that the new shape will not be concave at the projection point.
                        // That might happen, if the boundary curve runs back in itself.
                        boolean projection_ok =  curr_dist < min_projection_dist &&
                                (search_right && x_intersect > concave_corner.x &&
                                concave_corner.y <= corner_after_projection_approx.y
                                || search_left && x_intersect < concave_corner.x &&
                                concave_corner.y >= corner_after_projection_approx.y);
                        if (projection_ok)
                        {
                            min_projection_dist = curr_dist;
                            corner_no_after_min_projection = corner_no_after_curr_projection;
                            min_projection = new FloatPoint(x_intersect, concave_corner.y);
                        }
                    }
                }
                
                if (corner_before_projection_approx.x != corner_after_projection_approx.x)
                    // try a vertical division
                {
                    double min_x;
                    double max_x;
                    if (corner_after_projection_approx.x > corner_before_projection_approx.x)
                    {
                        min_x = corner_before_projection_approx.x;
                        max_x = corner_after_projection_approx.x;
                    }
                    else
                    {
                        min_x = corner_after_projection_approx.x;
                        max_x = corner_before_projection_approx.x;
                    }
                    if (concave_corner.x >= min_x && concave_corner.x <= max_x)
                    {
                        Line curr_line =
                                new Line(corner_before_curr_projection, corner_after_curr_projection);
                        double y_intersect = curr_line.function_value_approx(concave_corner.x);
                        curr_dist = Math.abs(y_intersect - concave_corner.y);
                        // make shure, that the new shape will be convex at the projection point
                        boolean projection_ok =  curr_dist < min_projection_dist &&
                                (search_up && y_intersect > concave_corner.y &&
                                concave_corner.x >= corner_after_projection_approx.x
                                || search_down && y_intersect < concave_corner.y
                                && concave_corner.x <= corner_after_projection_approx.x);
                        
                        if (projection_ok)
                        {
                            min_projection_dist = curr_dist;
                            corner_no_after_min_projection = corner_no_after_curr_projection;
                            min_projection = new FloatPoint(concave_corner.x, y_intersect);
                        }
                    }
                }
                corner_before_curr_projection = corner_after_curr_projection;
                corner_before_projection_approx = corner_after_projection_approx;
                if (corner_no_after_curr_projection == corners.length - 1)
                {
                    corner_no_after_curr_projection = 0;
                }
                else
                {
                    ++corner_no_after_curr_projection;
                }
            }
            if (min_projection_dist == Integer.MAX_VALUE )
            {
                System.out.println("PolygonShape.DivisionPoint: projection not found");
            }
            
            projection = min_projection;
            corner_no_after_projection = corner_no_after_min_projection;
        }
        
        final int corner_no_after_projection;
        final FloatPoint projection;
    }
}
