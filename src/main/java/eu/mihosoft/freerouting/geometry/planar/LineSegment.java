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

import datastructures.Signum;

/**
 * Implements functionality for line segments.
 * The difference between a LineSegment and a
 * Line is, that a Line is infinite and a
 * LineSegment has a start and an endpoint.
 *
 *
 * @author Alfons Wirtz
 */
public class LineSegment implements java.io.Serializable
{

    /**
     * Creates a line segment from the 3 input lines.
     * It starts at the intersection of p_start_line and p_middle_line
     * and ends at the intersection of p_middle_line and p_end_line.
     * p_start_line and p_end_line must not be parallel to p_middle_line.
     */
    public LineSegment(Line p_start_line, Line p_middle_line, Line p_end_line)
    {
        start = p_start_line;
        middle = p_middle_line;
        end = p_end_line;
    }

    /**
     * creates the p_no-th line segment of p_polyline
     * for p_no between 1 and p_polyline.line_count - 2.
     */
    public LineSegment(Polyline p_polyline, int p_no)
    {
        if (p_no <= 0 || p_no >= p_polyline.arr.length - 1)
        {
            System.out.println("LineSegment from Polyline: p_no out of range");
            start = null;
            middle = null;
            end = null;
            return;
        }
        start = p_polyline.arr[p_no - 1];
        middle = p_polyline.arr[p_no];
        end = p_polyline.arr[p_no + 1];
    }

    /**
     * Creates the p_no-th line segment of p_shape
     * for p_no between 0 and  p_shape.line_count - 1.
     */
    public LineSegment(PolylineShape p_shape, int p_no)
    {
        int line_count = p_shape.border_line_count();
        if (p_no < 0 || p_no >= line_count)
        {
            System.out.println("LineSegment from TileShape: p_no out of range");
            start = null;
            middle = null;
            end = null;
            return;
        }
        if (p_no == 0)
        {
            start = p_shape.border_line(line_count - 1);
        }
        else
        {
            start = p_shape.border_line(p_no - 1);
        }
        middle = p_shape.border_line(p_no);
        if (p_no == line_count - 1)
        {
            end = p_shape.border_line(0);
        }
        else
        {
            end = p_shape.border_line(p_no + 1);
        }
    }

    /**
     * Returns the intersection of the first 2 lines of this segment
     */
    public Point start_point()
    {
        if (precalculated_start_point == null)
        {
            precalculated_start_point = middle.intersection(start);
        }
        return precalculated_start_point;
    }

    /**
     * Returns the intersection of the last 2 lines of this segment
     */
    public Point end_point()
    {
        if (precalculated_end_point == null)
        {
            precalculated_end_point = middle.intersection(end);
        }
        return precalculated_end_point;
    }

    /**
     * Returns an approximation of the intersection of the first 2 lines of this segment
     */
    public FloatPoint start_point_approx()
    {
        FloatPoint result;
        if (precalculated_start_point != null)
        {
            result = precalculated_start_point.to_float();
        }
        else
        {
            result = this.start.intersection_approx(this.middle);
        }
        return result;
    }

    /**
     * Returns an approximation of the intersection of the last 2 lines of this segment
     */
    public FloatPoint end_point_approx()
    {
        FloatPoint result;
        if (precalculated_end_point != null)
        {
            result = precalculated_end_point.to_float();
        }
        else
        {
            result = this.end.intersection_approx(this.middle);
        }
        return result;
    }

    /**
     * Returns the (infinite) line of this segment.
     */
    public Line get_line()
    {
        return middle;
    }

    /**
     * Returns the start closing line of this segment.
     */
    public Line get_start_closing_line()
    {
        return start;
    }

    /**
     * Returns the end closing line of this segment.
     */
    public Line get_end_closing_line()
    {
        return end;
    }

    /**
     * Returns the line segment with tje opposite direction.
     */
    public LineSegment opposite()
    {
        return new LineSegment(end.opposite(), middle.opposite(), start.opposite());
    }

    /**
     * Transforms this LinsSegment into a polyline of lenght 3.
     */
    public Polyline to_polyline()
    {
        Line[] lines = new Line[3];
        lines[0] = start;
        lines[1] = middle;
        lines[2] = end;
        return new Polyline(lines);
    }

    /**
     * Creates a 1 dimensional simplex rom this line segment, which
     * has the same shape as the line sgment.
     */
    public Simplex to_simplex()
    {
        Line[] line_arr = new Line[4];
        if (this.end_point().side_of(this.start) == Side.ON_THE_RIGHT)
        {
            line_arr[0] = this.start.opposite();
        }
        else
        {
            line_arr[0] = this.start;
        }
        line_arr[1] = this.middle;
        line_arr[2] = this.middle.opposite();
        if (this.start_point().side_of(this.end) == Side.ON_THE_RIGHT)
        {
            line_arr[3] = this.end.opposite();
        }
        else
        {
            line_arr[3] = this.end;
        }
        Simplex result = Simplex.get_instance(line_arr);
        return result;
    }

    /**
     * Checks if p_point is contained in this line segment
     */
    public boolean contains(Point p_point)
    {
        if (!(p_point instanceof IntPoint))
        {
            System.out.println("LineSegments.contains currently only implementet for IntPoints");
            return false;
        }
        if (middle.side_of(p_point) != Side.COLLINEAR)
        {
            return false;
        }
        // create a perpendicular line at p_point and check, that the two
        // endpoints of this segment are on difcferent sides of that line.
        Direction perpendicular_direction = middle.direction().turn_45_degree(2);
        Line perpendicular_line = new Line(p_point, perpendicular_direction);
        Side start_point_side = perpendicular_line.side_of(this.start_point());
        Side end_point_side = perpendicular_line.side_of(this.end_point());
        if (start_point_side != Side.COLLINEAR && end_point_side != Side.COLLINEAR && start_point_side == end_point_side)
        {
            return false;
        }
        return true;
    }

    /**
     * calculates the smallest surrounding box of this line segmant
     */
    public IntBox bounding_box()
    {
        FloatPoint start_corner = middle.intersection_approx(start);
        FloatPoint end_corner = middle.intersection_approx(end);
        double llx = Math.min(start_corner.x, end_corner.x);
        double lly = Math.min(start_corner.y, end_corner.y);
        double urx = Math.max(start_corner.x, end_corner.x);
        double ury = Math.max(start_corner.y, end_corner.y);
        IntPoint lower_left = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
        IntPoint upper_right = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
        return new IntBox(lower_left, upper_right);
    }

    /**
     * calculates the smallest surrounding octagon of this line segmant
     */
    public IntOctagon bounding_octagon()
    {
        FloatPoint start_corner = middle.intersection_approx(start);
        FloatPoint end_corner = middle.intersection_approx(end);
        double lx = Math.floor(Math.min(start_corner.x, end_corner.x));
        double ly = Math.floor(Math.min(start_corner.y, end_corner.y));
        double rx = Math.ceil(Math.max(start_corner.x, end_corner.x));
        double uy = Math.ceil(Math.max(start_corner.y, end_corner.y));
        double start_x_minus_y = start_corner.x - start_corner.y;
        double end_x_minus_y = end_corner.x - end_corner.y;
        double ulx = Math.floor(Math.min(start_x_minus_y, end_x_minus_y));
        double lrx = Math.ceil(Math.max(start_x_minus_y, end_x_minus_y));
        double start_x_plus_y = start_corner.x + start_corner.y;
        double end_x_plus_y = end_corner.x + end_corner.y;
        double llx = Math.floor(Math.min(start_x_plus_y, end_x_plus_y));
        double urx = Math.ceil(Math.max(start_x_plus_y, end_x_plus_y));
        IntOctagon result =
                new IntOctagon((int) lx, (int) ly, (int) rx, (int) uy, (int) ulx, (int) lrx, (int) llx, (int) urx);
        return result.normalize();
    }

    /**
     * Creates a new line segment with the same start and middle line and an end line,
     * so that the length of the new line segment is about p_new_length.
     */
    public LineSegment change_length_approx(double p_new_length)
    {
        FloatPoint new_end_point =
                start_point_approx().change_length(end_point_approx(), p_new_length);
        Direction perpendicular_direction = this.middle.direction().turn_45_degree(2);
        Line new_end_line = new Line(new_end_point.round(), perpendicular_direction);
        LineSegment result = new LineSegment(this.start, this.middle, new_end_line);
        return result;
    }

    /**
     * Looks up the intersections of this line segment with p_other.
     * The result array may have length 0, 1 or 2.
     * If the segments do not intersect the result array will have length 0.
     * The result lines are so that the intersections of the result lines with
     * this line segment will deliver the intersection points.
     * If the segments overlap, the result array has length 2 and the intersection
     * points are the first and the last overlap point.
     * Otherwise the result array has length 1 and the intersection point is the
     * the unique intersection or touching point.
     * The result is not symmetric in this and p_other, because intersecting lines
     * and not the intersection points are returned.
     */
    public Line[] intersection(LineSegment p_other)
    {
        if (!this.bounding_box().intersects(p_other.bounding_box()))
        {
            return new Line[0];
        }
        Side start_point_side = start_point().side_of(p_other.middle);
        Side end_point_side = end_point().side_of(p_other.middle);
        if (start_point_side == Side.COLLINEAR && end_point_side == Side.COLLINEAR)
        {
            // there may be an overlap
            LineSegment this_sorted = this.sort_endpoints_in_x_y();
            LineSegment other_sorted = p_other.sort_endpoints_in_x_y();
            LineSegment left_line;
            LineSegment right_line;
            if (this_sorted.start_point().compare_x_y(other_sorted.start_point()) <= 0)
            {
                left_line = this_sorted;
                right_line = other_sorted;
            }
            else
            {
                left_line = other_sorted;
                right_line = this_sorted;
            }
            int cmp = left_line.end_point().compare_x_y(right_line.start_point());
            if (cmp < 0)
            {
                // end point of the left line is to the lsft of the start point of the right line
                return new Line[0];
            }
            if (cmp == 0)
            {
                // end point of the left line is equal to the start point of the right line
                Line[] result = new Line[1];
                result[0] = left_line.end;
                return result;
            }
            // now there is a real overlap
            Line[] result = new Line[2];
            result[0] = right_line.start;
            if (right_line.end_point().compare_x_y(left_line.end_point()) >= 0)
            {
                result[1] = left_line.end;
            }
            else
            {
                result[1] = right_line.end;
            }
            return result;
        }
        if (start_point_side == end_point_side ||
                p_other.start_point().side_of(this.middle) == p_other.end_point().side_of(this.middle))
        {
            return new Line[0]; // no intersection possible
        }
        // now both start points and both end points are on different sides of the middle
        // line of the other segment.
        Line[] result = new Line[1];
        result[0] = p_other.middle;
        return result;
    }

    /**
     * Checks if this LineSegment and p_other contain a commen point
     */
    public boolean intersects(LineSegment p_other)
    {
        Line[] intersections = this.intersection(p_other);
        return intersections.length > 0;
    }

    /**
     * Checks if this LineSegment and p_other contain a common LineSegment,
     * which is not reduced to a point.
     */
    public boolean overlaps(LineSegment p_other)
    {
        Line[] intersections = this.intersection(p_other);
        return intersections.length > 1;
    }

    /**
     * Constructs an approximation of this line segment by orthogonal stairs with integer coordinates.
     *  The length of the stairs will be at most p_stair_width.
     *  If p_to_the_right, the stairs will be to the right of this line segment, else to the left.
     */
    public IntPoint[] stair_approximation(double p_width, boolean p_to_the_right)
    {
        IntPoint start_point = this.start_point().to_float().round();
        IntPoint end_point = this.end_point().to_float().round();
        if (start_point.equals(end_point))
        {
            return new IntPoint[0];

        }

        if (start_point.x == end_point.x || start_point.y == end_point.y)
        {
            IntPoint[] result = new IntPoint[2];
            result[0] = start_point;
            result[1] = end_point;
            return result;
        }

        int dx = end_point.x - start_point.x;
        int dy = end_point.y - start_point.y;
        int abs_dx = Math.abs(dx);
        int abs_dy = Math.abs(dy);
        boolean function_of_x = abs_dx >= abs_dy;
        // use otherwise function of y for better numerical  stability

        int stair_width;
        int stair_count;


        if (function_of_x)
        {
            stair_width = (int) Math.round(((p_width * (double) abs_dx) / (double) abs_dy));
            stair_count = (abs_dx - 1) / stair_width + 1;
            if (end_point.x < start_point.x)
            {
                stair_width = -stair_width;
            }
        }
        else
        {
            stair_width = (int) Math.round((p_width * (double) abs_dy) / (double) abs_dx);
            stair_count = (abs_dy - 1) / stair_width + 1;
            if (end_point.y < start_point.y)
            {
                stair_width = -stair_width;
            }
        }
        IntPoint[] result = new IntPoint[2 * stair_count + 1];

        result[0] = start_point;
        double det = (double) dx * (double) dy;
        boolean change_x_first = p_to_the_right && det > 0 || !p_to_the_right && det < 0;
        int curr_index = 0;

        int prev_line_point_x = start_point.x;
        int prev_line_point_y = start_point.y;
        for (int i = 1; i < stair_count; ++i)
        {
            int curr_line_point_x;
            int curr_line_point_y;
            if (function_of_x)
            {
                curr_line_point_x = start_point.x + i * stair_width;
                curr_line_point_y = (int) Math.round(this.get_line().function_value_approx(curr_line_point_x));
            }
            else
            {
                curr_line_point_y = start_point.y + i * stair_width;
                curr_line_point_x = (int) Math.round(this.get_line().function_in_y_value_approx(curr_line_point_y));
            }
            ++curr_index;
            if (change_x_first)
            {
                result[curr_index] = new IntPoint(curr_line_point_x, prev_line_point_y);
            }
            else
            {
                result[curr_index] = new IntPoint(prev_line_point_x, curr_line_point_y);
            }
            ++curr_index;
            result[curr_index] = new IntPoint(curr_line_point_x, curr_line_point_y);
            prev_line_point_x = curr_line_point_x;
            prev_line_point_y = curr_line_point_y;
        }
        ++curr_index;
        if (change_x_first)
        {
            result[curr_index] = new IntPoint(end_point.x, prev_line_point_y);
        }
        else
        {
            result[curr_index] = new IntPoint(prev_line_point_x, end_point.y);
        }
        ++curr_index;
        result[curr_index] = end_point;
        return result;
    }

    /**
     * Constructs an approximation of this line segment by 45 degree stairs with integer coordinates.
     *  The length of the stairs will be at most p_stair_width.
     *  If p_to_the_right, the stairs will be to the right of this line segment, else to the left.
     */
    public IntPoint[] stair_approximation_45(double p_width, boolean p_to_the_right)
    {
        IntPoint start_point = this.start_point().to_float().round();
        IntPoint end_point = this.end_point().to_float().round();
        if (start_point.equals(end_point))
        {
            return new IntPoint[0];

        }
        IntVector delta = end_point.difference_by(start_point);
        if (delta.is_multiple_of_45_degree())
        {
            IntPoint[] result = new IntPoint[2];
            result[0] = start_point;
            result[1] = end_point;
            return result;
        }
        IntVector abs_delta = new IntVector(Math.abs(delta.x), Math.abs(delta.y));
        boolean function_of_x = abs_delta.x >= abs_delta.y;
        // use otherwise function of y for better numerical  stability
        double det = (double) delta.x * (double) delta.y;
        int stair_width;
        int stair_count;
        if (function_of_x)
        {
            stair_width = (int) Math.round((p_width * (double) abs_delta.x) / (double) abs_delta.y);
            stair_count = (abs_delta.x - 1) / stair_width + 1;
            if (end_point.x < start_point.x)
            {
                stair_width = -stair_width;
            }
        }
        else
        {
            stair_width = (int) Math.round((p_width * (double) abs_delta.y) / (double) abs_delta.x);
            stair_count = (abs_delta.y - 1) / stair_width + 1;
            if (end_point.y < start_point.y)
            {
                stair_width = -stair_width;
            }

        }
        IntPoint[] result = new IntPoint[2 * stair_count + 1];
        result[0] = start_point;
        IntPoint prev_line_point = start_point;
        int curr_index = 0;
        for (int i = 1; i <= stair_count; ++i)
        {
            IntPoint curr_line_point;
            int curr_x;
            int curr_y;
            if (i == stair_count)
            {
                curr_line_point = end_point;
            }
            else
            {
                if (function_of_x)
                {
                    curr_x = start_point.x + i * stair_width;
                    curr_y = (int) Math.round(this.get_line().function_value_approx(curr_x));
                }
                else
                {
                    curr_y = start_point.y + i * stair_width;
                    curr_x = (int) Math.round(this.get_line().function_value_approx(curr_y));
                }
                curr_line_point = new IntPoint(curr_x, curr_y);
            }
            if (function_of_x)
            {
                boolean diagonal_first = p_to_the_right && det < 0 || !p_to_the_right && det > 0;

                if (diagonal_first)
                {
                    curr_x = prev_line_point.x + Signum.as_int(stair_width) * Math.abs(curr_line_point.y - prev_line_point.y);
                    curr_y = curr_line_point.y;
                }
                else
                // horizontal first
                {
                    curr_x = curr_line_point.x - Signum.as_int(stair_width) * Math.abs(curr_line_point.y - prev_line_point.y);
                    curr_y = prev_line_point.y;
                }
            }
            else
            // function of y
            {
                boolean  diagonal_first = p_to_the_right && det > 0 || !p_to_the_right && det < 0;

                if (diagonal_first)
                {
                    curr_x = curr_line_point.x;
                    curr_y = prev_line_point.y + Signum.as_int(stair_width) * Math.abs(curr_line_point.x - prev_line_point.x);
                }
                else
                {
                    curr_x = prev_line_point.x;
                    curr_y = curr_line_point.y - Signum.as_int(stair_width) * Math.abs(curr_line_point.x - prev_line_point.x);
                }

            }
            ++curr_index;
            result[curr_index] = new IntPoint (curr_x, curr_y);
            ++curr_index;
            result[curr_index] = curr_line_point;
            prev_line_point = curr_line_point;
        }
        return result;
    }

    /**
     * Returns an array with the borderline numbers of p_shape,
     * which are intersected by this line segment. Intersections at an endpoint
     * of this line segment are only counted, if the line segment intersects
     * with the interiour of p_shape.
     * The result array may have lenght 0, 1 or 2.
     * With 2 intersections the intersection which is nearest to the start
     * point of the line segment comes first.
     */
    public int[] border_intersections(TileShape p_shape)
    {
        int[] empty_result = new int[0];
        if (!this.bounding_box().intersects(p_shape.bounding_box()))
        {
            return empty_result;
        }

        int edge_count = p_shape.border_line_count();
        Line prev_line = p_shape.border_line(edge_count - 1);
        Line curr_line = p_shape.border_line(0);
        int[] result = new int[2];
        Point[] intersection = new Point[2];
        int intersection_count = 0;
        Point line_start = this.start_point();
        Point line_end = this.end_point();

        for (int edge_line_no = 0; edge_line_no <
                edge_count; ++edge_line_no)
        {
            Line next_line;
            if (edge_line_no == edge_count - 1)
            {
                next_line = p_shape.border_line(0);
            }
            else
            {
                next_line = p_shape.border_line(edge_line_no + 1);
            }

            Side start_point_side = curr_line.side_of(line_start);
            Side end_point_side = curr_line.side_of(line_end);
            if (start_point_side == Side.ON_THE_LEFT &&
                    end_point_side == Side.ON_THE_LEFT)
            {
                // both endpoints are outside the border_line,
                // no intersection possible
                return empty_result;
            }

            if (start_point_side == Side.COLLINEAR)
            {
                // the start is on curr_line, check that the end point is inside
                // the halfplane, because touches count only, if the interiour
                // is entered
                if (end_point_side != Side.ON_THE_RIGHT)
                {
                    return empty_result;
                }

            }

            if (end_point_side == Side.COLLINEAR)
            {
                // the end is on curr_line, check that the start point is inside
                // the halfplane, because touches count only, if the interiour
                // is entered
                if (start_point_side != Side.ON_THE_RIGHT)
                {
                    return empty_result;
                }

            }

            if (start_point_side != Side.ON_THE_RIGHT ||
                    end_point_side != Side.ON_THE_RIGHT)
            {
                // not both points are inside the halplane defined by curr_line
                Point is = this.middle.intersection(curr_line);
                Side prev_line_side_of_is = prev_line.side_of(is);
                Side next_line_side_of_is = next_line.side_of(is);
                if (prev_line_side_of_is != Side.ON_THE_LEFT &&
                        next_line_side_of_is != Side.ON_THE_LEFT)
                {
                    // this line segment intersects curr_line between the
                    // previous and the next corner of p_simplex

                    if (prev_line_side_of_is == Side.COLLINEAR)
                    {
                        // this line segment goes through the previous
                        // corner of p_simplex. Check, that the intersection
                        // isn't merely a touch.
                        Point prev_prev_corner;
                        if (edge_line_no == 0)
                        {
                            prev_prev_corner = p_shape.corner(edge_count - 1);
                        }
                        else
                        {
                            prev_prev_corner = p_shape.corner(edge_line_no - 1);
                        }

                        Point next_corner;
                        if (edge_line_no == edge_count - 1)
                        {
                            next_corner = p_shape.corner(0);
                        }
                        else
                        {
                            next_corner = p_shape.corner(edge_line_no + 1);
                        }
// check, that prev_prev_corner and next_corner
// are on different sides of this line segment.
                        Side prev_prev_corner_side =
                                this.middle.side_of(prev_prev_corner);
                        Side next_corner_side =
                                this.middle.side_of(next_corner);
                        if (prev_prev_corner_side == Side.COLLINEAR ||
                                next_corner_side == Side.COLLINEAR ||
                                prev_prev_corner_side == next_corner_side)
                        {
                            return empty_result;
                        }

                    }
                    if (next_line_side_of_is == Side.COLLINEAR)
                    {
                        // this line segment goes through the next
                        // corner of p_simplex. Check, that the intersection
                        // isn't merely a touch.
                        Point prev_corner = p_shape.corner(edge_line_no);
                        Point next_next_corner;

                        if (edge_line_no == edge_count - 2)
                        {
                            next_next_corner = p_shape.corner(0);
                        }
                        else if (edge_line_no == edge_count - 1)
                        {
                            next_next_corner = p_shape.corner(1);
                        }
                        else
                        {
                            next_next_corner = p_shape.corner(edge_line_no + 2);
                        }
// check, that prev_corner and next_next_corner
// are on different sides of this line segment.
                        Side prev_corner_side =
                                this.middle.side_of(prev_corner);
                        Side next_next_corner_side =
                                this.middle.side_of(next_next_corner);
                        if (prev_corner_side == Side.COLLINEAR ||
                                next_next_corner_side == Side.COLLINEAR ||
                                prev_corner_side == next_next_corner_side)
                        {
                            return empty_result;
                        }

                    }
                    boolean intersection_already_handeled = false;
                    for (int i = 0; i <
                            intersection_count; ++i)
                    {
                        if (is.equals(intersection[i]))
                        {
                            intersection_already_handeled = true;
                            break;

                        }




                    }
                    if (!intersection_already_handeled)
                    {
                        if (intersection_count < result.length)
                        {
                            // a new intersection is found
                            result[intersection_count] = edge_line_no;
                            intersection[intersection_count] = is;
                            ++intersection_count;
                        }
                        else
                        {
                            System.out.println("border_intersections: intersection_count to big!");
                        }

                    }
                }
            }

            prev_line = curr_line;
            curr_line =
                    next_line;
        }

        if (intersection_count == 0)
        {
            return empty_result;
        }

        if (intersection_count == 2)
        {
            // assure the correct order
            FloatPoint is0 = intersection[0].to_float();
            FloatPoint is1 = intersection[1].to_float();
            FloatPoint curr_start = line_start.to_float();
            if (curr_start.distance_square(is1) < curr_start.distance_square(is0))
            // swap the result points
            {
                int tmp = result[0];
                result[0] = result[1];
                result[1] = tmp;
            }

            return result;
        }

        if (intersection_count != 1)
        {
            System.out.println(
                    "LineSegment.border_intersections: intersection_count 1 expected");
        }

        int[] normalised_result = new int[1];
        normalised_result[0] = result[0];
        return normalised_result;
    }

    /**
     * Inverts the direction of this.middle, if start_point() has a bigger
     * x coordinate than end_point(), or an equal x coordinate and a bigger y coordinate.
     */
    public LineSegment sort_endpoints_in_x_y()
    {
        boolean swap_endlines = (start_point().compare_x_y(end_point()) > 0);
        LineSegment result;

        if (swap_endlines)
        {
            result = new LineSegment(this.end, this.middle, this.start);
            result.precalculated_start_point = this.precalculated_end_point;
            result.precalculated_end_point = this.precalculated_start_point;
        }
        else
        {
            result = this;
        }

        return result;
    }
    private final Line start;
    private final Line middle;
    private final Line end;
    transient private Point precalculated_start_point = null;
    transient private Point precalculated_end_point = null;
}