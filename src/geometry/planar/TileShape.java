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
 */
package geometry.planar;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 *
 * Abstract class defining functionality for convex shapes, whose
 * borders consists of straight lines.
 *
 * @author Alfons Wirtz
 */
public abstract class TileShape extends PolylineShape implements ConvexShape, java.io.Serializable
{

    /**
     * creates a Simplex as intersection of the halfplanes defined
     * by an array of directed lines
     */
    public static TileShape get_instance(Line[] p_line_arr)
    {
        Simplex result = Simplex.get_instance(p_line_arr);
        return result.simplify();
    }

    /**
     * Creates a TileShape from a Point array, who forms the corners of the shape
     * of a convex polygon. May work only for IntPoints.
     */
    public static TileShape get_instance(Point[] p_convex_polygon)
    {
        Line[] line_arr = new Line[p_convex_polygon.length];
        for (int j = 0; j < line_arr.length - 1; ++j)
        {
            line_arr[j] = new Line(p_convex_polygon[j], p_convex_polygon[j + 1]);
        }
        line_arr[line_arr.length - 1] =
                new Line(p_convex_polygon[line_arr.length - 1], p_convex_polygon[0]);
        return get_instance(line_arr);
    }

    /**
     * creates a half_plane from a directed line
     */
    public static TileShape get_instance(Line p_line)
    {
        Line[] lines = new Line[1];
        lines[0] = p_line;
        return Simplex.get_instance(lines);
    }

    /**
     * Creates a normalized  IntOctagon from the input values.
     * For the meaning of the parameter shortcuts see class IntOctagon.
     */
    public static IntOctagon get_instance(int p_lx, int p_ly, int p_rx,
                                          int p_uy, int p_ulx, int p_lrx,
                                          int p_llx, int p_urx)
    {
        IntOctagon oct = new IntOctagon(p_lx, p_ly, p_rx, p_uy, p_ulx,
                p_lrx, p_llx, p_urx);
        return oct.normalize();
    }

    /**
     * creates a boxlike convex shape
     */
    public static IntOctagon get_instance(int p_lower_left_x,
                                          int p_lower_left_y,
                                          int p_upper_right_x,
                                          int p_upper_right_y)
    {
        IntBox box = new IntBox(p_lower_left_x, p_lower_left_y,
                p_upper_right_x, p_upper_right_y);
        return box.to_IntOctagon();
    }

    /**
     * creates the smallest IntOctagon containing p_point
     */
    public static IntBox get_instance(Point p_point)
    {
        return p_point.surrounding_box();
    }

    /**
     * Tries to simplify the result shape to a simpler shape.
     * Simplifying always in the intersection function may cause performance problems.
     */
    public TileShape intersection_with_simplify(TileShape p_other)
    {
        TileShape result = this.intersection(p_other);
        return result.simplify();
    }

    /**
     * Converts the physical instance of this shape to a simpler physical instance, if possible.
     */
    public abstract TileShape simplify();

    /**
     * checks if this TileShape is an IntBox or can be converted into an IntBox
     */
    public abstract boolean is_IntBox();

    /**
     * checks if this TileShape is an IntOctagon or can be converted into an IntOctagon
     */
    public abstract boolean is_IntOctagon();

    /**
     * Returns the intersection of this shape with p_other
     */
    public abstract TileShape intersection(TileShape p_other);

    /**
     * Returns the p_no-th edge line of this shape
     * for p_no between 0 and edge_line_count() - 1.
     * The edge lines are sorted in counterclock sense around
     * the shape starting with the edge with the smallest direction.
     */
    public abstract Line border_line(int p_no);

    /**
     * if p_line is a borderline of this shape the number of that
     * edge is returned, otherwise -1
     */
    public abstract int border_line_index(Line p_line);

    /**
     * Converts the internal representation of this TieShape to a Simplex
     */
    public abstract Simplex to_Simplex();

    /**
     * Returns the content of the area of the shape.
     * If the shape is unbounded, Double.MAX_VALUE is returned.
     */
    public double area()
    {
        if (!is_bounded())
        {
            return Double.MAX_VALUE;
        }

        if (dimension() < 2)
        {
            return 0;
        }
        // calculate half of the absolute value of
        // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
        // where xi, yi are the coordinates of the i-th corner of this TileShape.

        double result = 0;
        int corner_count = border_line_count();
        FloatPoint prev_corner = corner_approx(corner_count - 2);
        FloatPoint curr_corner = corner_approx(corner_count - 1);
        for (int i = 0; i < corner_count; ++i)
        {
            FloatPoint next_corner = corner_approx(i);
            result += curr_corner.x * (next_corner.y - prev_corner.y);
            prev_corner = curr_corner;
            curr_corner = next_corner;
        }
        result = 0.5 * Math.abs(result);
        return result;
    }

    /**
     * Returns true, if p_point is not contained in the inside or the
     * edge of the shape
     */
    public boolean is_outside(Point p_point)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return true;
        }
        for (int i = 0; i < line_count; ++i)
        {
            if (border_line(i).side_of(p_point) == Side.ON_THE_LEFT)
            {
                return true;
            }
        }
        return false;
    }

    public boolean contains(Point p_point)
    {
        return !is_outside(p_point);
    }

    /**
     * Returns true, if p_point is contained in this shape,
     * but not on an edge line
     */
    public boolean contains_inside(Point p_point)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return false;
        }
        for (int i = 0; i < line_count; ++i)
        {
            if (border_line(i).side_of(p_point) != Side.ON_THE_RIGHT)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true, if p_point is contained in this shape.
     */
    public boolean contains(FloatPoint p_point)
    {
        return contains(p_point, 0);
    }

    /**
     * Returns true, if p_point is contained in this shape with tolerance p_tolerance.
     * p_tolerance is used when determing, if a point is on the left side of a border line.
     * It is used there in calculating a determinant and is not the distance of p_point to the border.
     */
    public boolean contains(FloatPoint p_point, double p_tolerance)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return false;
        }
        for (int i = 0; i < line_count; ++i)
        {
            if (border_line(i).side_of(p_point, p_tolerance) != Side.ON_THE_RIGHT)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns Side.COLLINEAR if p_point is on the border of this shape with tolerance p_tolerence.
     * p_tolerance is used when determing, if a point is on the right side of a border line.
     * It is used there in calculating a determinant and is not the distance of p_point to the border.
     * Otherwise the function returns Side.ON_THE_LEFT if p_point is outside of this shape,
     * and Side.ON_THE_RIGTH if p_point is inside this shape.
     */
    public Side side_of_border(FloatPoint p_point, double p_tolerance)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return Side.COLLINEAR;
        }
        Side result = Side.ON_THE_RIGHT; // point is inside
        for (int i = 0; i < line_count; ++i)
        {
            Side curr_side = border_line(i).side_of(p_point, p_tolerance);
            if (curr_side == Side.ON_THE_LEFT)
            {
                return Side.ON_THE_LEFT; // point is outside
            }
            else if (curr_side == Side.COLLINEAR)
            {
                result = curr_side;
            }

        }
        return result;
    }

    /**
     * If p_point lies on the border of this shape, the number of the
     * edge line segment containing p_point is returned,
     * otherwise -1 is returned.
     */
    public int contains_on_border_line_no(Point p_point)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return -1;
        }
        int containing_line_no = -1;
        for (int i = 0; i < line_count; ++i)
        {
            Side side_of = border_line(i).side_of(p_point);
            if (side_of == Side.ON_THE_LEFT)
            {
                // p_point outside the convex shape
                return -1;
            }
            if (side_of == Side.COLLINEAR)
            {
                containing_line_no = i;
            }
        }
        return containing_line_no;
    }

    /**
     * Returns true, if p_point lies exact on the boundary of the shape
     */
    public boolean contains_on_border(Point p_point)
    {
        return (contains_on_border_line_no(p_point) >= 0);
    }

    /**
     * Returns true, if this shape contains p_other completely.
     * THere may be some numerical inaccurracy.
     */
    public boolean contains_approx(TileShape p_other)
    {
        FloatPoint[] corners = p_other.corner_approx_arr();
        for (FloatPoint curr_corner : corners)
        {
            if (!this.contains(curr_corner))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true, if this shape contains p_other completely.
     */
    public boolean contains(TileShape p_other)
    {
        for (int i = 0; i < p_other.border_line_count(); ++i)
        {
            if (!this.contains(p_other.corner(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the distance between p_point and its nearest point
     * on the shape. 0, if p_point is contained in this shape
     */
    public double distance(FloatPoint p_point)
    {
        FloatPoint nearest_point = nearest_point_approx(p_point);
        return nearest_point.distance(p_point);
    }

    /**
     * Returns the distance between p_point and its nearest point
     * on the edge of the shape.
     */
    public double border_distance(FloatPoint p_point)
    {
        FloatPoint nearest_point = nearest_border_point_approx(p_point);
        return nearest_point.distance(p_point);
    }

    public double smallest_radius()
    {
        return border_distance(centre_of_gravity());
    }

    /**
     * Returns the point in this shape, which has the smallest
     * distance to p_from_point. p_from_point, if that point
     * is contained in this shape
     */
    public Point nearest_point(Point p_from_point)
    {
        if (!is_outside(p_from_point))
        {
            return p_from_point;
        }
        return nearest_border_point(p_from_point);
    }

    public FloatPoint nearest_point_approx(FloatPoint p_from_point)
    {
        if (this.contains(p_from_point))
        {
            return p_from_point;
        }
        return nearest_border_point_approx(p_from_point);
    }

    /**
     * Returns a nearest point to p_from_point on the edge of the shape
     */
    public Point nearest_border_point(Point p_from_point)
    {
        int line_count = border_line_count();
        if (line_count == 0)
        {
            return null;
        }
        FloatPoint from_point_f = p_from_point.to_float();
        if (line_count == 1)
        {
            return border_line(0).perpendicular_projection(p_from_point);
        }
        Point nearest_point = null;
        double min_dist = Double.MAX_VALUE;
        int min_dist_ind = 0;

        // calculate the distance to the nearest corner first
        for (int i = 0; i < line_count; ++i)
        {
            FloatPoint curr_corner_f = corner_approx(i);
            double curr_dist = curr_corner_f.distance_square(from_point_f);
            if (curr_dist < min_dist)
            {
                min_dist = curr_dist;
                min_dist_ind = i;
            }
        }

        nearest_point = corner(min_dist_ind);

        int prev_ind = line_count - 2;
        int curr_ind = line_count - 1;

        for (int next_ind = 0; next_ind < line_count; ++next_ind)
        {
            Point projection =
                    border_line(curr_ind).perpendicular_projection(p_from_point);
            if ((!corner_is_bounded(curr_ind) || border_line(prev_ind).side_of(projection) == Side.ON_THE_RIGHT) && (!corner_is_bounded(next_ind) || border_line(next_ind).side_of(projection) == Side.ON_THE_RIGHT))
            {
                FloatPoint projection_f = projection.to_float();
                double curr_dist = projection_f.distance_square(from_point_f);
                if (curr_dist < min_dist)
                {
                    min_dist = curr_dist;
                    nearest_point = projection;
                }
            }
            prev_ind = curr_ind;
            curr_ind = next_ind;
        }
        return nearest_point;
    }

    /**
     * Returns an approximation of the nearest point
     * to p_from_point on the border of the this shape
     */
    public FloatPoint nearest_border_point_approx(FloatPoint p_from_point)
    {
        FloatPoint[] nearest_points = nearest_border_points_approx(p_from_point, 1);
        if (nearest_points.length <= 0)
        {
            return null;
        }
        return nearest_points[0];
    }

    /**
     * Returns an approximation of the p_count nearest points
     * to p_from_point on the border of the this shape.
     * The result points must be located on different border lines and are
     * sorted in ascending order (the nearest point comes first).
     */
    public FloatPoint[] nearest_border_points_approx(FloatPoint p_from_point, int p_count)
    {
        if (p_count <= 0)
        {
            return new FloatPoint[0];
        }
        int line_count = border_line_count();
        int result_count = Math.min(p_count, line_count);
        if (line_count == 0)
        {
            return new FloatPoint[0];
        }
        if (line_count == 1)
        {
            FloatPoint[] result = new FloatPoint[1];
            result[0] = p_from_point.projection_approx(border_line(0));
            return result;
        }
        if (this.dimension() == 0)
        {
            FloatPoint[] result = new FloatPoint[1];
            result[0] = corner_approx(0);
            return result;
        }
        FloatPoint[] nearest_points = new FloatPoint[result_count];
        double[] min_dists = new double[result_count];
        for (int i = 0; i < result_count; ++i)
        {
            min_dists[i] = Double.MAX_VALUE;
        }

        // calculate the distances to the nearest corners first
        for (int i = 0; i < line_count; ++i)
        {
            if (corner_is_bounded(i))
            {
                FloatPoint curr_corner = corner_approx(i);
                double curr_dist = curr_corner.distance_square(p_from_point);
                for (int j = 0; j < result_count; ++j)
                {
                    if (curr_dist < min_dists[j])
                    {
                        for (int k = j + 1; k < result_count; ++k)
                        {
                            min_dists[k] = min_dists[k - 1];
                            nearest_points[k] = nearest_points[k - 1];
                        }
                        min_dists[j] = curr_dist;
                        nearest_points[j] = curr_corner;
                        break;
                    }
                }
            }
        }

        int prev_ind = line_count - 2;
        int curr_ind = line_count - 1;

        for (int next_ind = 0; next_ind < line_count; ++next_ind)
        {
            FloatPoint projection = p_from_point.projection_approx(border_line(curr_ind));
            if ((!corner_is_bounded(curr_ind) || border_line(prev_ind).side_of(projection) == Side.ON_THE_RIGHT) && (!corner_is_bounded(next_ind) || border_line(next_ind).side_of(projection) == Side.ON_THE_RIGHT))
            {
                double curr_dist = projection.distance_square(p_from_point);
                for (int j = 0; j < result_count; ++j)
                {
                    if (curr_dist < min_dists[j])
                    {
                        for (int k = j + 1; k < result_count; ++k)
                        {
                            min_dists[k] = min_dists[k - 1];
                            nearest_points[k] = nearest_points[k - 1];
                        }
                        min_dists[j] = curr_dist;
                        nearest_points[j] = projection;
                        break;
                    }
                }
            }
            prev_ind = curr_ind;
            curr_ind = next_ind;
        }
        return nearest_points;
    }

    /**
     * Returns the number of a nearest corner of the shape
     * to p_from_point
     */
    public int index_of_nearest_corner(Point p_from_point)
    {
        FloatPoint from_point_f = p_from_point.to_float();
        int result = 0;
        int corner_count = border_line_count();
        double min_dist = Double.MIN_VALUE;
        for (int i = 0; i < corner_count; ++i)
        {
            double curr_dist = corner_approx(i).distance(from_point_f);
            if (curr_dist < min_dist)
            {
                min_dist = curr_dist;
                result = i;
            }
        }
        return result;
    }

    /**
     * Returns a line segment consisting of an approximations of the corners with
     * index 0 and corner_count / 2.
     */
    public FloatLine diagonal_corner_segment()
    {
        if (this.is_empty())
        {
            return null;
        }
        FloatPoint first_corner = this.corner_approx(0);
        FloatPoint last_corner = this.corner_approx(this.border_line_count() / 2);
        return new FloatLine(first_corner, last_corner);
    }

    /**
     * Returns an approximation of the p_count nearest relative outside locations
     * of p_shape in the direction of different border lines of this shape.
     * These relative locations are sorted in ascending order (the shortest comes first).
     */
    public FloatPoint[] nearest_relative_outside_locations(TileShape p_shape, int p_count)
    {
        int line_count = border_line_count();
        if (p_count <= 0 || line_count < 3 || !this.intersects(p_shape))
        {
            return new FloatPoint[0];
        }

        int result_count = Math.min(p_count, line_count);

        FloatPoint[] translate_coors = new FloatPoint[result_count];
        double[] min_dists = new double[result_count];
        for (int i = 0; i < result_count; ++i)
        {
            min_dists[i] = Double.MAX_VALUE;
        }

        int curr_ind = line_count - 1;

        int other_line_count = p_shape.border_line_count();

        for (int next_ind = 0; next_ind < line_count; ++next_ind)
        {
            double curr_max_dist = 0;
            FloatPoint curr_translate_coor = FloatPoint.ZERO;
            for (int corner_no = 0; corner_no < other_line_count; ++corner_no)
            {
                FloatPoint curr_corner = p_shape.corner_approx(corner_no);
                if (border_line(curr_ind).side_of(curr_corner) == Side.ON_THE_RIGHT)
                {
                    FloatPoint projection = curr_corner.projection_approx(border_line(curr_ind));
                    double curr_dist = projection.distance_square(curr_corner);
                    if (curr_dist > curr_max_dist)
                    {
                        curr_max_dist = curr_dist;
                        curr_translate_coor = projection.substract(curr_corner);
                    }
                }
            }

            for (int j = 0; j < result_count; ++j)
            {
                if (curr_max_dist < min_dists[j])
                {
                    for (int k = j + 1; k < result_count; ++k)
                    {
                        min_dists[k] = min_dists[k - 1];
                        translate_coors[k] = translate_coors[k - 1];
                    }
                    min_dists[j] = curr_max_dist;
                    translate_coors[j] = curr_translate_coor;
                    break;
                }
            }
            curr_ind = next_ind;
        }
        return translate_coors;
    }

    public ConvexShape shrink(double p_offset)
    {
        ConvexShape result = this.offset(-p_offset);
        if (result.is_empty())
        {
            IntBox centre_box = this.centre_of_gravity().bounding_box();
            result = this.intersection(centre_box);
        }
        return result;
    }

    /**
     * Returns the maximum of the edge widths of the shape.
     * Only defined when the  shape is bounded.
     */
    public double length()
    {
        if (!this.is_bounded())
        {
            return Integer.MAX_VALUE;
        }
        int dimension = this.dimension();
        if (dimension <= 0)
        {
            return 0;
        }
        if (dimension == 1)
        {
            return this.circumference() / 2;
        }
        // now the shape is 2-dimensional
        double max_distance = -1;
        double max_distance_2 = -1;
        FloatPoint gravity_point = this.centre_of_gravity();
        for (int i = 0; i < border_line_count(); ++i)
        {
            double curr_distance = Math.abs(border_line(i).signed_distance(gravity_point));
            if (curr_distance > max_distance)
            {
                max_distance_2 = max_distance;
                max_distance = curr_distance;
            }
            else if (curr_distance > max_distance_2)
            {
                max_distance_2 = curr_distance;
            }
        }
        return max_distance + max_distance_2;
    }

    /**
     * Calculates, if this Shape and p_other habe a common border piece and returns
     * an 2 dimensional array with the indices in this shape and p_other of the
     * touching edge lines in this case.
     * Otherwise an array of dimension 0 is returned.
     * Used if the intersection shape is 1-dimensional.
     */
    public int[] touching_sides(TileShape p_other)
    {
        // search the first edge line of p_other with reverse direction >= right

        int side_no_2 = -1;
        Direction dir2 = null;
        for (int i = 0; i < p_other.border_line_count(); ++i)
        {
            Direction curr_dir = p_other.border_line(i).direction();
            if (curr_dir.compareTo(Direction.LEFT) >= 0)
            {
                side_no_2 = i;
                dir2 = curr_dir.opposite();
                break;
            }
        }
        if (dir2 == null)
        {
            System.out.println("touching_side : dir2 not found");
            return new int[0];
        }
        int side_no_1 = 0;
        Direction dir1 = this.border_line(0).direction();
        final int max_ind = this.border_line_count() + p_other.border_line_count();

        for (int i = 0; i < max_ind; ++i)
        {
            int compare = dir2.compareTo(dir1);
            if (compare == 0)
            {
                if (this.border_line(side_no_1).is_equal_or_opposite(p_other.border_line(side_no_2)))
                {
                    int[] result = new int[2];
                    result[0] = side_no_1;
                    result[1] = side_no_2;
                    return result;
                }
            }
            if (compare >= 0) // dir2 is bigger than dir1
            {
                side_no_1 = (side_no_1 + 1) % this.border_line_count();
                dir1 = this.border_line(side_no_1).direction();
            }
            else //dir1 is bigger than dir2
            {
                side_no_2 = (side_no_2 + 1) % p_other.border_line_count();
                dir2 = p_other.border_line(side_no_2).direction().opposite();
            }
        }
        return new int[0];
    }

    /**
     * Calculates the minimal distance of p_line to this shape,
     * assuming, that p_line is on the left of this shape.
     * Returns -1, if p_line is on the right of this shape or intersects
     * with the interiour of this shape.
     */
    public double distance_to_the_left(Line p_line)
    {
        double result = Integer.MAX_VALUE;
        for (int i = 0; i < this.border_line_count(); ++i)
        {
            FloatPoint curr_corner = this.corner_approx(i);
            Side line_side = p_line.side_of(curr_corner, 1);
            if (line_side == Side.COLLINEAR)
            {
                line_side = p_line.side_of(this.corner(i));
            }
            if (line_side == Side.ON_THE_RIGHT)
            {
                // curr_point would be outside the result shape
                result = -1;
                break;
            }
            result = Math.min(result, p_line.signed_distance(curr_corner));
        }
        return result;
    }

    /**
     * Returns Side.COLLINEAR, if p_line intersects with the interiour of this shape,
     * Side.ON_THE_LEFT, if this shape is completely on the left of p_line
     * or Side.ON_THE_RIGHT, if this shape is completely on the right of p_line.
     */
    public Side side_of(Line p_line)
    {
        boolean on_the_left = false;
        boolean on_the_right = false;
        for (int i = 0; i < this.border_line_count(); ++i)
        {
            Side curr_side = p_line.side_of(this.corner(i));
            if (curr_side == Side.ON_THE_LEFT)
            {
                on_the_right = true;
            }
            else if (curr_side == Side.ON_THE_RIGHT)
            {
                on_the_left = true;
            }
            if (on_the_left && on_the_right)
            {
                return Side.COLLINEAR;
            }
        }
        Side result;
        if (on_the_left)
        {
            result = Side.ON_THE_LEFT;
        }
        else
        {
            result = Side.ON_THE_RIGHT;
        }
        return result;
    }

    public TileShape turn_90_degree(int p_factor, IntPoint p_pole)
    {
        Line[] new_lines = new Line[border_line_count()];
        for (int i = 0; i < new_lines.length; ++i)
        {
            new_lines[i] = this.border_line(i).turn_90_degree(p_factor, p_pole);
        }
        return get_instance(new_lines);
    }

    public TileShape rotate_approx(double p_angle, FloatPoint p_pole)
    {
        if (p_angle == 0)
        {
            return this;
        }
        IntPoint[] new_corners = new IntPoint[border_line_count()];
        for (int i = 0; i < new_corners.length; ++i)
        {

            new_corners[i] = this.corner_approx(i).rotate(p_angle, p_pole).round();
        }
        Polygon corner_polygon = new Polygon(new_corners);
        Point[] polygon_corners = corner_polygon.corner_array();
        TileShape result;
        if (polygon_corners.length >= 3)
        {
            result = get_instance(polygon_corners);
        }
        else if (polygon_corners.length == 2)
        {
            Polyline curr_polyline = new Polyline(polygon_corners);
            LineSegment curr_segment = new LineSegment(curr_polyline, 0);
            result = curr_segment.to_simplex();
        }
        else if (polygon_corners.length == 1)
        {
            result = get_instance(polygon_corners[0]);
        }
        else
        {
            result = Simplex.EMPTY;
        }
        return result;
    }

    public TileShape mirror_vertical(IntPoint p_pole)
    {
        Line[] new_lines = new Line[border_line_count()];
        for (int i = 0; i < new_lines.length; ++i)
        {
            new_lines[i] = this.border_line(i).mirror_vertical(p_pole);
        }
        return get_instance(new_lines);
    }

    public TileShape mirror_horizontal(IntPoint p_pole)
    {
        Line[] new_lines = new Line[border_line_count()];
        for (int i = 0; i < new_lines.length; ++i)
        {
            new_lines[i] = this.border_line(i).mirror_horizontal(p_pole);
        }
        return get_instance(new_lines);
    }

    /**
     * Calculates the border line of this shape intersecting the ray from p_from_point into the direction p_direction.
     * p_from_point is assumed to be inside this shape, otherwise -1 is returned.
     */
    public int intersecting_border_line_no(Point p_from_point, Direction p_direction)
    {
        if (!this.contains(p_from_point))
        {
            return -1;
        }
        FloatPoint from_point = p_from_point.to_float();
        Line intersection_line = new Line(p_from_point, p_direction);
        FloatPoint second_line_point = intersection_line.b.to_float();
        int result = -1;
        double min_distance = Float.MAX_VALUE;
        for (int i = 0; i < this.border_line_count(); ++i)
        {
            Line curr_border_line = this.border_line(i);
            FloatPoint curr_intersection = curr_border_line.intersection_approx(intersection_line);
            if (curr_intersection.x >= Integer.MAX_VALUE)
            {
                continue; // lines are parallel
            }
            double curr_distence = curr_intersection.distance_square(from_point);
            if (curr_distence < min_distance)
            {
                boolean direction_ok = curr_border_line.side_of(second_line_point) == Side.ON_THE_LEFT || second_line_point.distance_square(curr_intersection) < curr_distence;
                if (direction_ok)
                {
                    result = i;
                    min_distance = curr_distence;
                }
            }
        }
        return result;
    }

    /**
     * Cuts p_shape out of this shape and divides the result into convex pieces
     */
    public abstract TileShape[] cutout(TileShape p_shape);

    /**
     * Returns an arry of tuples of integers. The length of the array is
     * the number of points, where p_polyline enters or leaves the interiour
     * of this shape. The first coordinate of the tuple is the number of
     * the line segment of p_polyline, which enters the simplex and the
     * second coordinate of the tuple is the number of the edge_line of the
     * simplex, which is crossed there.
     * That means that the entrance point is the intersection of this 2 lines.
     */
    public int[][] entrance_points(Polyline p_polyline)
    {
        int[][] result = new int[2 * p_polyline.arr.length][2];
        int intersection_count = 0;
        int prev_intersection_line_no = -1;
        int prev_intersection_edge_no = -1;
        for (int line_no = 1; line_no < p_polyline.arr.length - 1; ++line_no)
        {
            LineSegment curr_line_seg = new LineSegment(p_polyline, line_no);
            int[] curr_intersections = curr_line_seg.border_intersections(this);
            for (int i = 0; i < curr_intersections.length; ++i)
            {
                int edge_no = curr_intersections[i];
                if (line_no != prev_intersection_line_no ||
                        edge_no != prev_intersection_edge_no)
                {
                    result[intersection_count][0] = line_no;
                    result[intersection_count][1] = edge_no;
                    ++intersection_count;
                    prev_intersection_line_no = line_no;
                    prev_intersection_edge_no = edge_no;
                }
            }
        }
        int[][] normalized_result = new int[intersection_count][2];
        for (int j = 0; j < intersection_count; ++j)
        {
            for (int i = 0; i < 2; ++i)
            {
                normalized_result[j][i] = result[j][i];
            }
        }
        return normalized_result;
    }

    /**
     * Cuts out the parts of p_polyline in the interiour of this shape
     * and returns a list of the remaining pieces of p_polyline.
     * Pieces completely contained in the border of this shape
     * are not returned.
     */
    public Polyline[] cutout(Polyline p_polyline)
    {
        int[][] intersection_no = this.entrance_points(p_polyline);
        Point first_corner = p_polyline.first_corner();
        boolean first_corner_is_inside = this.contains_inside(first_corner);
        if (intersection_no.length == 0)
        // no intersections
        {
            if (first_corner_is_inside)
            // p_polyline is contained completely in this shape
            {
                return new Polyline[0];
            }
            // p_polyline is completely outside
            Polyline[] result = new Polyline[1];
            result[0] = p_polyline;
            return result;
        }
        Collection<Polyline> pieces = new LinkedList<Polyline>();
        int curr_intersection_no = 0;
        int[] curr_intersection_tuple = intersection_no[curr_intersection_no];
        Point first_intersection =
                p_polyline.arr[curr_intersection_tuple[0]].intersection(this.border_line(curr_intersection_tuple[1]));
        if (!first_corner_is_inside)
        // calculate outside piece at start
        {
            if (!first_corner.equals(first_intersection))
            // otherwise skip 1 point outside polyline at the start
            {
                int curr_polyline_intersection_no = curr_intersection_tuple[0];
                Line[] curr_lines = new Line[curr_polyline_intersection_no + 2];
                System.arraycopy(p_polyline.arr, 0, curr_lines, 0, curr_polyline_intersection_no + 1);
                // close the polyline piece with the intersected edge line.
                curr_lines[curr_polyline_intersection_no + 1] = this.border_line(curr_intersection_tuple[1]);
                Polyline curr_piece = new Polyline(curr_lines);
                if (!curr_piece.is_empty())
                {
                    pieces.add(curr_piece);
                }
            }
            ++curr_intersection_no;
        }
        while (curr_intersection_no < intersection_no.length - 1)
        // calculate the next outside polyline piece
        {
            curr_intersection_tuple = intersection_no[curr_intersection_no];
            int[] next_intersection_tuple = intersection_no[curr_intersection_no + 1];
            int curr_intersection_no_of_polyline = curr_intersection_tuple[0];
            int next_intersection_no_of_polyline = next_intersection_tuple[0];
            // check that at least 1 corner of p_polyline with number between
            // between curr_intersection_no_of_polyline and
            // next_intersection_no_of_polyline
            // is not contained in this shape. Otherwise the part of p_polyline
            // between this intersections is completely contained in the border
            // and can be ignored
            boolean insert_piece = false;
            for (int i = curr_intersection_no_of_polyline + 1;
                    i < next_intersection_no_of_polyline; ++i)
            {
                if (this.is_outside(p_polyline.corner(i)))
                {
                    insert_piece = true;
                    break;
                }
            }

            if (insert_piece)
            {
                Line[] curr_lines = new Line[next_intersection_no_of_polyline - curr_intersection_no_of_polyline + 3];
                curr_lines[0] = this.border_line(curr_intersection_tuple[1]);
                System.arraycopy(p_polyline.arr, curr_intersection_no_of_polyline, curr_lines,
                        1, curr_lines.length - 2);
                curr_lines[curr_lines.length - 1] = this.border_line(next_intersection_tuple[1]);
                Polyline curr_piece = new Polyline(curr_lines);
                if (!curr_piece.is_empty())
                {
                    pieces.add(curr_piece);
                }
            }
            curr_intersection_no += 2;
        }
        if (curr_intersection_no <= intersection_no.length - 1)
        // calculate outside piece at end
        {
            curr_intersection_tuple = intersection_no[curr_intersection_no];
            int curr_polyline_intersection_no = curr_intersection_tuple[0];
            Line[] curr_lines = new Line[p_polyline.arr.length - curr_polyline_intersection_no + 1];
            curr_lines[0] = this.border_line(curr_intersection_tuple[1]);
            System.arraycopy(p_polyline.arr, curr_polyline_intersection_no, curr_lines,
                    1, curr_lines.length - 1);
            Polyline curr_piece = new Polyline(curr_lines);
            if (!curr_piece.is_empty())
            {
                pieces.add(curr_piece);
            }
        }
        Polyline[] result = new Polyline[pieces.size()];
        Iterator<Polyline> it = pieces.iterator();
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = it.next();
        }
        return result;
    }

    public TileShape[] split_to_convex()
    {
        TileShape[] result = new TileShape[1];
        result[0] = this;
        return result;
    }

    /**
     * Divides this shape into sections with width and height at most p_max_section_width
     * of about equal size.
     */
    public TileShape[] divide_into_sections(double p_max_section_width)
    {
        if (this.is_empty())
        {
            TileShape[] result = new TileShape[1];
            result[0] = this;
            return result;
        }
        TileShape[] section_boxes = this.bounding_box().divide_into_sections(p_max_section_width);
        Collection<TileShape> section_list = new LinkedList<TileShape>();
        for (int i = 0; i < section_boxes.length; ++i)
        {
            TileShape curr_section = this.intersection_with_simplify(section_boxes[i]);
            if (curr_section.dimension() == 2)
            {
                section_list.add(curr_section);
            }
        }
        TileShape[] result = new TileShape[section_list.size()];
        Iterator<TileShape> it = section_list.iterator();
        for (int i = 0; i < result.length; ++i)
        {
            result[i] = it.next();
        }
        return result;
    }

    /**
     * Checks, if p_line_segment has a common point with the interiour of this shape.
     */
    public boolean is_intersected_interiour_by(LineSegment p_line_segment)
    {
        FloatPoint float_start_point = p_line_segment.start_point_approx();
        FloatPoint float_end_point = p_line_segment.end_point_approx();

        Side[] border_line_side_of_start_point_arr = new Side[this.border_line_count()];
        Side[] border_line_side_of_end_point_arr = new Side[border_line_side_of_start_point_arr.length];
        for (int i = 0; i < border_line_side_of_start_point_arr.length; ++i)
        {
            Line curr_border_line = this.border_line(i);
            Side border_line_side_of_start_point = curr_border_line.side_of(float_start_point, 1);
            if (border_line_side_of_start_point == Side.COLLINEAR)
            {
                border_line_side_of_start_point = curr_border_line.side_of(p_line_segment.start_point());
            }
            Side border_line_side_of_end_point = curr_border_line.side_of(float_end_point, 1);
            if (border_line_side_of_end_point == Side.COLLINEAR)
            {
                border_line_side_of_end_point = curr_border_line.side_of(p_line_segment.end_point());
            }
            if (border_line_side_of_start_point != Side.ON_THE_RIGHT && border_line_side_of_end_point != Side.ON_THE_RIGHT)
            {
                // both endpoints are outside the border_line,
                // no intersection possible
                return false;
            }
            border_line_side_of_start_point_arr[i] = border_line_side_of_start_point;
            border_line_side_of_end_point_arr[i] = border_line_side_of_end_point;
        }
        boolean start_point_is_inside = true;
        for (int i = 0; i < border_line_side_of_start_point_arr.length; ++i)
        {
            if (border_line_side_of_start_point_arr[i] != Side.ON_THE_RIGHT)
            {
                start_point_is_inside = false;
                break;
            }
        }
        if (start_point_is_inside)
        {
            return true;
        }
        boolean end_point_is_inside = true;
        for (int i = 0; i < border_line_side_of_end_point_arr.length; ++i)
        {
            if (border_line_side_of_end_point_arr[i] != Side.ON_THE_RIGHT)
            {
                end_point_is_inside = false;
                break;
            }
        }
        if (end_point_is_inside)
        {
            return true;
        }
        Line segment_line = p_line_segment.get_line();
        // Check, if this line segments intersect a border line of p_shape.
        for (int i = 0; i < border_line_side_of_start_point_arr.length; ++i)
        {
            Side border_line_side_of_start_point = border_line_side_of_start_point_arr[i];
            Side border_line_side_of_end_point = border_line_side_of_end_point_arr[i];
            if (border_line_side_of_start_point != border_line_side_of_end_point)
            {
                if (border_line_side_of_start_point == Side.COLLINEAR && border_line_side_of_end_point == Side.ON_THE_LEFT || border_line_side_of_end_point == Side.COLLINEAR && border_line_side_of_start_point == Side.ON_THE_LEFT)
                {
                    // the interiour of p_shape is not intersected.
                    continue;
                }
                Side prev_corner_side = segment_line.side_of(this.corner_approx(i), 1);
                if (prev_corner_side == Side.COLLINEAR)
                {
                    prev_corner_side = segment_line.side_of(this.corner(i));
                }
                int next_corner_index;
                if (i == border_line_side_of_start_point_arr.length - 1)
                {
                    next_corner_index = 0;
                }
                else
                {
                    next_corner_index = i + 1;
                }
                Side next_corner_side = segment_line.side_of(this.corner_approx(next_corner_index), 1);
                if (next_corner_side == Side.COLLINEAR)
                {
                    next_corner_side = segment_line.side_of(this.corner(next_corner_index));
                }
                if (prev_corner_side == Side.ON_THE_LEFT && next_corner_side == Side.ON_THE_RIGHT || prev_corner_side == Side.ON_THE_RIGHT && next_corner_side == Side.ON_THE_LEFT)
                {
                    // this line segment crosses a border line of  p_shape
                    return true;
                }
            }
        }
        return false;
    }

    // auxiliary functions needed because the virtual function mechanism does
    // not work in parameter position
    abstract TileShape intersection(Simplex p_other);

    abstract TileShape intersection(IntOctagon p_other);

    abstract TileShape intersection(IntBox p_other);

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    abstract TileShape[] cutout_from(IntBox p_shape);

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    abstract TileShape[] cutout_from(IntOctagon p_shape);

    /**
     * Auxiliary function to implement the public function cutout(TileShape p_shape)
     */
    abstract TileShape[] cutout_from(Simplex p_shape);
}
