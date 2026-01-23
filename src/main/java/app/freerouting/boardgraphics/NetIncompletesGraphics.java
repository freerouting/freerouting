package app.freerouting.boardgraphics;

import app.freerouting.board.Pin;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;

/**
 * Utility class for drawing contents of NetIncompletes.
 */
public class NetIncompletesGraphics {

    /**
     * Draws the incomplete connections and optional length violations.
     *
     * @param p_net_incompletes        The net incompletes data object.
     * @param p_graphics               The AWT graphics object.
     * @param p_graphics_context       The board graphics context.
     * @param p_length_violations_only If true, only draws length violation markers,
     *                                 not airlines.
     */
    public static void draw(NetIncompletes p_net_incompletes, Graphics p_graphics, GraphicsContext p_graphics_context,
            boolean p_length_violations_only) {
        if (!p_length_violations_only) {
            Color draw_color = p_graphics_context.get_incomplete_color();
            double draw_intensity = p_graphics_context.get_incomplete_color_intensity();
            if (draw_intensity <= 0) {
                return;
            }
            FloatPoint[] draw_points = new FloatPoint[2];
            int draw_width = 1;
            for (AirLine curr_incomplete : p_net_incompletes.getIncompletes()) {
                draw_points[0] = curr_incomplete.from_corner;
                draw_points[1] = curr_incomplete.to_corner;
                p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
                if (!curr_incomplete.from_item.shares_layer(curr_incomplete.to_item)) {
                    draw_layer_change_marker(curr_incomplete.from_corner, p_net_incompletes.getMarkerRadius(),
                            p_graphics,
                            p_graphics_context);
                    draw_layer_change_marker(curr_incomplete.to_corner, p_net_incompletes.getMarkerRadius(), p_graphics,
                            p_graphics_context);
                }
            }
        }
        if (p_net_incompletes.get_length_violation() == 0) {
            return;
        }
        // draw the length violation around every Pin of the net.
        Collection<Pin> net_pins = p_net_incompletes.getNet().get_pins();
        for (Pin curr_pin : net_pins) {
            draw_length_violation_marker(curr_pin
                    .get_center()
                    .to_float(), p_net_incompletes.get_length_violation(), p_graphics, p_graphics_context);
        }
    }

    /**
     * Draws a marker indicating a layer change (via or trace segment end) in an
     * airline.
     */
    public static void draw_layer_change_marker(FloatPoint p_location, double p_radius, Graphics p_graphics,
            GraphicsContext p_graphics_context) {
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

    /**
     * Draws a marker indicating a length violation on a pin.
     */
    static void draw_length_violation_marker(FloatPoint p_location, double p_diameter, Graphics p_graphics,
            GraphicsContext p_graphics_context) {
        final int draw_width = 1;
        Color draw_color = p_graphics_context.get_incomplete_color();
        double draw_intensity = p_graphics_context.get_incomplete_color_intensity();
        double circle_radius = 0.5 * Math.abs(p_diameter);
        p_graphics_context.draw_circle(p_location, circle_radius, draw_width, draw_color, p_graphics, draw_intensity);
        FloatPoint[] draw_points = new FloatPoint[2];
        draw_points[0] = new FloatPoint(p_location.x - circle_radius, p_location.y);
        draw_points[1] = new FloatPoint(p_location.x + circle_radius, p_location.y);
        p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
        if (p_diameter > 0) {
            // draw also the vertical diameter to create a "+"
            draw_points[0] = new FloatPoint(p_location.x, p_location.y - circle_radius);
            draw_points[1] = new FloatPoint(p_location.x, p_location.y + circle_radius);
            p_graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, draw_intensity);
        }
    }
}
