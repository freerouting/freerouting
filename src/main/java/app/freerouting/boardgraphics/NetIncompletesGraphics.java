package app.freerouting.boardgraphics;

import app.freerouting.board.Pin;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.geometry.planar.FloatPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;

/** Utility class for drawing contents of NetIncompletes. */
public class NetIncompletesGraphics {

  /**
   * Draws the incomplete connections and optional length violations.
   *
   * @param p_net_incompletes The net incompletes data object.
   * @param p_graphics The AWT graphics object.
   * @param p_graphics_context The board graphics context.
   * @param p_length_violations_only If true, only draws length violation markers, not airlines.
   */
  public static void draw(
      NetIncompletes p_net_incompletes,
      Graphics p_graphics,
      GraphicsContext p_graphics_context,
      boolean p_length_violations_only) {
    if (!p_length_violations_only) {
      Color drawColor = p_graphics_context.get_incomplete_color();
      double drawIntensity = p_graphics_context.get_incomplete_color_intensity();
      if (drawIntensity <= 0) {
        return;
      }
      FloatPoint[] drawPoints = new FloatPoint[2];
      int drawWidth = 1;
      for (AirLine curr_incomplete : p_net_incompletes.getIncompletes()) {
        drawPoints[0] = curr_incomplete.fromCorner;
        drawPoints[1] = curr_incomplete.toCorner;
        p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, drawIntensity);
        if (!curr_incomplete.fromItem.shares_layer(curr_incomplete.toItem)) {
          draw_layer_change_marker(
              curr_incomplete.fromCorner,
              p_net_incompletes.getMarkerRadius(),
              p_graphics,
              p_graphics_context);
          draw_layer_change_marker(
              curr_incomplete.toCorner,
              p_net_incompletes.getMarkerRadius(),
              p_graphics,
              p_graphics_context);
        }
      }
    }
    if (p_net_incompletes.get_length_violation() == 0) {
      return;
    }
    // draw the length violation around every Pin of the net.
    Collection<Pin> netPins = p_net_incompletes.getNet().get_pins();
    for (Pin currPin : netPins) {
      draw_length_violation_marker(
          currPin.get_center().to_float(),
          p_net_incompletes.get_length_violation(),
          p_graphics,
          p_graphics_context);
    }
  }

  /** Draws a marker indicating a layer change (via or trace segment end) in an airline. */
  public static void draw_layer_change_marker(
      FloatPoint p_location,
      double p_radius,
      Graphics p_graphics,
      GraphicsContext p_graphics_context) {
    final int drawWidth = 1;
    Color drawColor = p_graphics_context.get_incomplete_color();
    double drawIntensity = p_graphics_context.get_incomplete_color_intensity();
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(p_location.x - p_radius, p_location.y - p_radius);
    drawPoints[1] = new FloatPoint(p_location.x + p_radius, p_location.y + p_radius);
    p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, drawIntensity);
    drawPoints[0] = new FloatPoint(p_location.x + p_radius, p_location.y - p_radius);
    drawPoints[1] = new FloatPoint(p_location.x - p_radius, p_location.y + p_radius);
    p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, drawIntensity);
  }

  /** Draws a marker indicating a length violation on a pin. */
  static void draw_length_violation_marker(
      FloatPoint p_location,
      double p_diameter,
      Graphics p_graphics,
      GraphicsContext p_graphics_context) {
    final int drawWidth = 1;
    Color drawColor = p_graphics_context.get_incomplete_color();
    double drawIntensity = p_graphics_context.get_incomplete_color_intensity();
    double circleRadius = 0.5 * Math.abs(p_diameter);
    p_graphics_context.draw_circle(
        p_location, circleRadius, drawWidth, drawColor, p_graphics, drawIntensity);
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(p_location.x - circleRadius, p_location.y);
    drawPoints[1] = new FloatPoint(p_location.x + circleRadius, p_location.y);
    p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, drawIntensity);
    if (p_diameter > 0) {
      // draw also the vertical diameter to create a "+"
      drawPoints[0] = new FloatPoint(p_location.x, p_location.y - circleRadius);
      drawPoints[1] = new FloatPoint(p_location.x, p_location.y + circleRadius);
      p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, drawIntensity);
    }
  }
}
