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
 * FloatPoint.java
 *
 * Created on 2. Februar 2003, 09:14
 */

package geometry.planar;

/**
 *
 * Implements a point in the plane as a touple of double's.
 * Because arithmetic calculations with double's are in general not
 * exact, FloatPoint is not derived from the abstract class Point.
 *
 *
 * @author Alfons Wirtz
 */


public class FloatPoint implements java.io.Serializable
{
    
    public static final FloatPoint ZERO = new FloatPoint(0,0);
    
    /**
     * creates an instance of class FloatPoint from two double's,
     */
    public FloatPoint(double p_x, double p_y)
    {
        x = p_x;
        y = p_y;
    }
    
    public FloatPoint(IntPoint p_pt)
    {
        x = p_pt.x ;
        y = p_pt.y ;
    }
    
    /**
     * returns the square of the distance from this point to the zero point
     */
    public final double size_square()
    {
        return x * x + y * y;
    }
    
    /**
     * returns the distance from this point to the zero point
     */
    public final double size()
    {
        return Math.sqrt(size_square());
    }
    
    /**
     * returns the square of the distance from this Point to the Point p_other
     */
    public final double distance_square(FloatPoint p_other)
    {
        double dx = p_other.x - x;
        double dy = p_other.y - y;
        return dx * dx + dy * dy;
    }
    
    /**
     * returns the distance from this point to the point p_other
     */
    public final double distance(FloatPoint p_other)
    {
        return Math.sqrt(distance_square(p_other));
    }
    
    /**
     * Computes the weighted distance to p_other.
     */
    public double weighted_distance(FloatPoint p_other, double p_horizontal_weight, double p_vertical_weight)
    {
        double delta_x = this.x - p_other.x;
        double delta_y = this.y - p_other.y;
        delta_x *= p_horizontal_weight;
        delta_y *= p_vertical_weight;
        double result = Math.sqrt(delta_x * delta_x + delta_y * delta_y);
        return result;
    }
    
    /**
     * rounds the coordinates from an object of class Point_double to
     * an object of class IntPoint
     */
    public IntPoint round()
    {
        return new IntPoint((int)Math.round(x), (int)Math.round(y));
    }
    
    /**
     * Rounds this point, so that if this point is on the right side
     * of any directed line with direction p_dir, the result
     * point will also be on the right side.
     */
    public IntPoint round_to_the_right(Direction p_dir)
    {
        FloatPoint dir = p_dir.get_vector().to_float();
        int rounded_x;
        
        if (dir.y > 0)
        {
            rounded_x = (int) Math.ceil(x);
        }
        else if (dir.y < 0)
        {
            rounded_x = (int) Math.floor(x);
        }
        else
        {
            rounded_x = (int) Math.round(x);
        }
        
        int rounded_y;
        
        if (dir.x > 0)
        {
            rounded_y = (int) Math.floor(y);
        }
        else if (dir.x < 0)
        {
            rounded_y = (int) Math.ceil(y);
        }
        else
        {
            rounded_y = (int) Math.round(y);
        }
        return new IntPoint(rounded_x, rounded_y);
    }
    
    /**
     * Round this Point so the x coordinate of the result will be a multiple of p_horizontal_grid
     * and the y coordinate a multiple of p_vertical_grid.
     */
    public IntPoint round_to_grid(int p_horizontal_grid, int p_vertical_grid)
    {
        double rounded_x;
        if (p_horizontal_grid > 0)
        {
            rounded_x = Math.rint(this.x / p_horizontal_grid) *  p_horizontal_grid;
        }
        else
        {
            rounded_x = this.x;
        }
        double rounded_y;
        if (p_vertical_grid > 0)
        {
            rounded_y =  Math.rint(this.y / p_vertical_grid) *  p_vertical_grid;
        }
        else
        {
            rounded_y = this.y;
        }
        return new IntPoint((int) rounded_x, (int) rounded_y );
    }
    
    /**
     * Rounds this point, so that if this point is on the left side
     * of any directed line with direction p_dir, the result
     * point will also be on the left side.
     */
    public IntPoint round_to_the_left(Direction p_dir)
    {
        FloatPoint dir = p_dir.get_vector().to_float();
        int rounded_x;
        
        if (dir.y > 0)
        {
            rounded_x = (int) Math.floor(x);
        }
        else if (dir.y < 0)
        {
            rounded_x = (int) Math.ceil(x);
        }
        else
        {
            rounded_x = (int) Math.round(x);
        }
        
        int rounded_y;
        
        if (dir.x > 0)
        {
            rounded_y = (int) Math.ceil(y);
        }
        else if (dir.x < 0)
        {
            rounded_y = (int) Math.floor(y);
        }
        else
        {
            rounded_y = (int) Math.round(y);
        }
        return new IntPoint(rounded_x, rounded_y);
    }
    
    /**
     * Adds the coordinates of this FloatPoint and p_other.
     */
    public FloatPoint add(FloatPoint p_other)
    {
        return new FloatPoint(this.x + p_other.x, this.y + p_other.y);
    }
    
    /**
     * Substracts the coordinates of p_other from this FloatPoint.
     */
    public FloatPoint substract(FloatPoint p_other)
    {
        return new FloatPoint(this.x - p_other.x, this.y - p_other.y);
    }
    
    /**
     * Returns an approximation of the perpendicular projection
     * of this point onto p_line
     */
    public FloatPoint projection_approx(Line p_line)
    {
        FloatLine line = new FloatLine(p_line.a.to_float(), p_line.b.to_float());
        return line.perpendicular_projection(this);
    }
    
    /**
     * Calculates the scalar prodct of (p_1 - this). with (p_2 - this).
     */
    public double scalar_product(FloatPoint p_1, FloatPoint p_2)
    {
        if (p_1 == null || p_2 == null)
        {
            System.out.println("FloatPoint.scalar_product: parameter point is null");
            return 0;
        }
        double  dx_1 = p_1.x - this.x;
        double  dx_2 = p_2.x - this.x;
        double  dy_1 = p_1.y - this.y;
        double  dy_2 = p_2.y - this.y;
        return (dx_1 * dx_2 + dy_1 * dy_2);
    }
    
    /**
     * Approximates a FloatPoint on the line from zero to this point
     * with distance p_new_length from zero.
     */
    public FloatPoint change_size(double p_new_size)
    {
        if (x == 0 && y == 0)
        {
            // the size of the zero point cannot be changed
            return this;
        }
        double length = Math.sqrt(x * x + y * y);
        double new_x = (x * p_new_size) / length;
        double new_y = (y * p_new_size) / length;
        return new FloatPoint(new_x, new_y);
    }
    
    /**
     * Approximates a FloatPoint on the line from this point to p_to_point
     * with distance p_new_length from this point.
     */
    public FloatPoint change_length(FloatPoint p_to_point, double p_new_length)
    {
        double dx = p_to_point.x - this.x;
        double dy = p_to_point.y - this.y;
        if (dx == 0 && dy == 0)
        {
            System.out.println("IntPoint.change_length: Points are equal");
            return p_to_point;
        }
        double length = Math.sqrt(dx * dx + dy * dy);
        double new_x = this.x + (dx * p_new_length) / length;
        double new_y = this.y + (dy * p_new_length) / length;
        return new FloatPoint(new_x, new_y);
    }
    
    /**
     * Returns the middle point between this point and p_to_point.
     */
    public FloatPoint middle_point(FloatPoint p_to_point)
    {
        if (p_to_point == this)
        {
            return this;
        }
        double middle_x = 0.5 * (this.x + p_to_point.x);
        double middle_y = 0.5 * (this.y + p_to_point.y);
        return new FloatPoint(middle_x, middle_y);
    }
    
    /**
     * The function returns
     * Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to p_2;
     * and Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2.
     * Collinearity is not defined, becouse numerical calculations ar not exact for FloatPoints.
     */
    public Side side_of(FloatPoint p_1, FloatPoint p_2)
    {
        double d21_x = p_2.x - p_1.x;
        double d21_y = p_2.y - p_1.y;
        double d01_x = this.x - p_1.x;
        double d01_y = this.y - p_1.y;
        double determinant = d21_x * d01_y - d21_y * d01_x;
        return Side.of(determinant);
    }
    
    /**
     *  Rotates this FloatPoints by p_angle ( in radian ) around the p_pole.
     */
    public FloatPoint rotate(double p_angle, FloatPoint p_pole)
    {
        if (p_angle == 0)
        {
            return this;
        }
        double dx = x - p_pole.x;
        double dy = y - p_pole.y;
        double sin_angle = Math.sin(p_angle);
        double cos_angle = Math.cos(p_angle);
        double new_dx = dx * cos_angle - dy * sin_angle;
        double new_dy = dx * sin_angle + dy * cos_angle;
        return new FloatPoint(p_pole.x + new_dx, p_pole.y + new_dy);
    }
    
    /**
     * Turns this FloatPoint by p_factor times 90 degree around ZERO.
     */
    public FloatPoint turn_90_degree(int p_factor)
    {
        int n = p_factor;
        while (n < 0)
        {
            n += 4;
        }
        while (n >= 4)
        {
            n -= 4;
        }
        double new_x ;
        double new_y ;
        switch (n)
        {
            case 0: // 0 degree
                new_x = x;
                new_y = y;
                break;
            case 1: // 90 degree
                new_x = -y ;
                new_y = x ;
                break;
            case 2: // 180 degree
                new_x = -x ;
                new_y = -y ;
                break;
            case 3: // 270 degree
                new_x = y ;
                new_y = -x ;
                break;
            default:
                new_x = 0 ;
                new_y = 0 ;
        }
        return new FloatPoint(new_x, new_y);
    }
    
    /**
     * Turns this FloatPoint by p_factor times 90 degree around p_pole.
     */
    public FloatPoint turn_90_degree(int p_factor, FloatPoint p_pole)
    {
        FloatPoint v = this.substract(p_pole);
        v = v.turn_90_degree(p_factor);
        return p_pole.add(v);
    }
    /**
     * Checks, if this point is contained in the box spanned by p_1 and p_2 with the input tolerance.
     */
    public boolean is_contained_in_box(FloatPoint p_1, FloatPoint p_2, double p_tolerance)
    {
        double min_x;
        double max_x;
        if (p_1.x < p_2.x)
        {
            min_x = p_1.x;
            max_x = p_2.x;
        }
        else
        {
            min_x = p_2.x;
            max_x = p_1.x;
        }
        if (this.x < min_x - p_tolerance || this.x > max_x + p_tolerance)
        {
            return false;
        }
        double min_y;
        double max_y;
        if (p_1.y < p_2.y)
        {
            min_y = p_1.y;
            max_y = p_2.y;
        }
        else
        {
            min_y = p_2.y;
            max_y = p_1.y;
        }
        return (this.y >= min_y - p_tolerance && this.y <= max_y + p_tolerance);
    }
    
    /**
     * Creates the smallest IntBox containing this point.
     */
    public IntBox bounding_box()
    {
        IntPoint lower_left = new IntPoint((int)Math.floor(this.x),(int)Math.floor(this.y));
        IntPoint upper_right = new IntPoint((int)Math.ceil(this.x),(int)Math.ceil(this.y));
        return new IntBox(lower_left, upper_right);
    }
    
    /**
     * Calculates the touching points of the tangents from this point to a circle
     * around p_to_point with radius p_distance.
     * Solves the quadratic equation, which results by substituting x by the
     * term in y from the equation of the polar line of a circle with center
     * p_to_point and radius p_distance and putting it into the circle
     * equation. The polar line is the line through the 2 tangential points
     * of the circle looked at from from this point and
     * has the equation
     *  (this.x - p_to_point.x) * (x - p_to_point.x)
     *   + (this.y - p_to_point.y) * (y - p_to_point.y) = p_distance **2
     */
    public FloatPoint[] tangential_points(FloatPoint p_to_point, double p_distance)
    {
        // turn the situation 90 degree if the x difference is smaller
        // than the y difference for better numerical stability
        
        double dx = Math.abs(this.x - p_to_point.x);
        double dy = Math.abs(this.y - p_to_point.y);
        boolean situation_turned = (dy > dx);
        FloatPoint pole;
        FloatPoint circle_center;
        
        if (situation_turned)
        {
            // turn the situation by 90 degree
            pole = new FloatPoint(-this.y, this.x);
            circle_center = new FloatPoint(-p_to_point.y, p_to_point.x);
        }
        else
        {
            pole = this;
            circle_center = p_to_point;
        }
        
        dx = pole.x - circle_center.x;
        dy = pole.y - circle_center.y;
        double dx_square = dx * dx;
        double dy_square = dy * dy;
        double dist_square = dx_square + dy_square;
        double radius_square = p_distance * p_distance;
        double discriminant = radius_square * dy_square - (radius_square - dx_square) * dist_square;
        
        if (discriminant <= 0)
        {
            // pole is inside the circle.
            return new FloatPoint[0];
        }
        double square_root = Math.sqrt(discriminant);
        
        FloatPoint[] result = new FloatPoint[2];
        
        double     a1 = radius_square * dy;
        double     dy1 = (a1 + p_distance * square_root) / dist_square;
        double     dy2 = (a1 - p_distance * square_root) / dist_square;
        
        double first_point_y = dy1 + circle_center.y;
        double first_point_x = (radius_square - dy * dy1) / dx + circle_center.x;
        double second_point_y = dy2 + circle_center.y;
        double second_point_x = (radius_square - dy * dy2) / dx + circle_center.x;
        
        if (situation_turned)
        {
            // turn the result by 270 degree
            result[0] = new FloatPoint(first_point_y, -first_point_x);
            result[1] = new FloatPoint(second_point_y, -second_point_x);
        }
        else
        {
            result[0] = new FloatPoint(first_point_x, first_point_y);
            result[1] = new FloatPoint(second_point_x, second_point_y);
        }
        return result;
    }
    
    /**
     * Calculates the left tangential point of the line from this point
     * to a circle around p_to_point with radius p_distance.
     * Returns null, if this point is inside this circle.
     */
    public FloatPoint left_tangential_point(FloatPoint p_to_point, double p_distance)
    {
        if (p_to_point == null)
        {
            return null;
        }
        FloatPoint[] tangent_points = tangential_points(p_to_point, p_distance);
        if (tangent_points.length < 2)
        {
            return null;
        }
        FloatPoint result;
        if (p_to_point.side_of(this,  tangent_points[0]) == Side.ON_THE_RIGHT)
        {
            result = tangent_points[0];
        }
        else
        {
            result = tangent_points[1];
        }
        return result;
    }
    
    /**
     * Calculates the right tangential point of the line from this point
     * to a circle around p_to_point with radius p_distance.
     * Returns null, if this point is inside this circle.
     */
    public FloatPoint right_tangential_point(FloatPoint p_to_point, double p_distance)
    {
        if (p_to_point == null)
        {
            return null;
        }
        FloatPoint[] tangent_points = tangential_points(p_to_point, p_distance);
        if (tangent_points.length < 2)
        {
            return null;
        }
        FloatPoint result;
        if (p_to_point.side_of(this,  tangent_points[0]) == Side.ON_THE_LEFT)
        {
            result = tangent_points[0];
        }
        else
        {
            result = tangent_points[1];
        }
        return result;
    }
    
    /**
     * Calculates the center of the circle through this point, p_1 and p_2
     * by calculating the intersection of the two lines perpendicular to and passing through
     * the midpoints of the lines (this, p_1) and (p_1, p_2).
     */
    public FloatPoint circle_center(FloatPoint p_1, FloatPoint p_2)
    {
        double slope_1 = (p_1.y - this.y)/(p_1.x - this.x);
        double slope_2 = (p_2.y - p_1.y)/(p_2.x - p_1.x);
        double x_center =
                (slope_1 * slope_2 * (this.y -p_2.y) + slope_2 * (this.x + p_1.x) - slope_1 *(p_1.x + p_2.x))
                /(2 * (slope_2 - slope_1));
        double y_center = (0.5 * (this.x + p_1.x) - x_center)/slope_1 + 0.5 * (this.y + p_1.y);
        return new FloatPoint(x_center, y_center);
    }
    
    /**
     * Returns true, if this point is contained in the circle through p_1, p_2 and p_3.
     */
    public boolean inside_circle(FloatPoint p_1, FloatPoint p_2, FloatPoint p_3)
    {
        FloatPoint center =  p_1.circle_center(p_2, p_3);
        double radius_square = center.distance_square(p_1);
        return (this.distance_square(center) < radius_square - 1); // - 1 is a tolerance for numerical stability.
    }
    
    public String to_string(java.util.Locale p_locale)
    {
        java.text.NumberFormat nf =  java.text.NumberFormat.getInstance(p_locale);
        nf.setMaximumFractionDigits(4);
        return (" (" + nf.format(x) + " , " + nf.format(y) + ") ");
    }
    
    public String toString()
    {
        return to_string(java.util.Locale.ENGLISH);
    }
    
    /**
     * Calculates the smallest IntOctagon containing all the input points
     */
    public static IntOctagon bounding_octagon(FloatPoint [] p_point_arr)
    {
        double lx = Integer.MAX_VALUE;
        double ly = Integer.MAX_VALUE;
        double rx = Integer.MIN_VALUE;
        double uy = Integer.MIN_VALUE;
        double ulx = Integer.MAX_VALUE;
        double lrx = Integer.MIN_VALUE;
        double llx = Integer.MAX_VALUE;
        double urx = Integer.MIN_VALUE;
        for (int i = 0; i < p_point_arr.length; ++i)
        {
            FloatPoint curr = p_point_arr[i];
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
        IntOctagon surrounding_octagon = new
                IntOctagon((int)Math.floor(lx), (int)Math.floor(ly),
                (int)Math.ceil(rx), (int)Math.ceil(uy),
                (int)Math.floor(ulx), (int)Math.ceil(lrx),
                (int)Math.floor(llx), (int)Math.ceil(urx));
        return surrounding_octagon;
    }
    
    /**
     * the x coordinate of this point
     */
    public final double x;
    
    /**
     * the y coordinate of this point
     */
    public final double y;
}