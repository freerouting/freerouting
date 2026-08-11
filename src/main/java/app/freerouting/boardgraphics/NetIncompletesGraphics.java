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
   * @param netIncompletes The net incompletes data object.
   * @param graphics The AWT graphics object.
   * @param graphicsContext The board graphics context.
   * @param lengthViolationsOnly If true, only draws length violation markers, not airlines.
   */
  public static void draw(
      NetIncompletes netIncompletes,
      Graphics graphics,
      GraphicsContext graphicsContext,
      boolean lengthViolationsOnly) {
    if (!lengthViolationsOnly) {
      Color drawColor = graphicsContext.getIncompleteColor();
      double drawIntensity = graphicsContext.getIncompleteColorIntensity();
      if (drawIntensity <= 0) {
        return;
      }
      FloatPoint[] drawPoints = new FloatPoint[2];
      int drawWidth = 1;
      for (AirLine currIncomplete : netIncompletes.getIncompletes()) {
        drawPoints[0] = currIncomplete.fromCorner;
        drawPoints[1] = currIncomplete.toCorner;
        graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
        if (!currIncomplete.fromItem.sharesLayer(currIncomplete.toItem)) {
          drawLayerChangeMarker(
              currIncomplete.fromCorner,
              netIncompletes.getMarkerRadius(),
              graphics,
              graphicsContext);
          drawLayerChangeMarker(
              currIncomplete.toCorner, netIncompletes.getMarkerRadius(), graphics, graphicsContext);
        }
      }
    }
    if (netIncompletes.getLengthViolation() == 0) {
      return;
    }
    // draw the length violation around every Pin of the net.
    Collection<Pin> netPins = netIncompletes.getNet().getPins();
    for (Pin currPin : netPins) {
      drawLengthViolationMarker(
          currPin.getCenter().toFloat(),
          netIncompletes.getLengthViolation(),
          graphics,
          graphicsContext);
    }
  }

  /** Draws a marker indicating a layer change (via or trace segment end) in an airline. */
  public static void drawLayerChangeMarker(
      FloatPoint location, double radius, Graphics graphics, GraphicsContext graphicsContext) {
    final int drawWidth = 1;
    Color drawColor = graphicsContext.getIncompleteColor();
    double drawIntensity = graphicsContext.getIncompleteColorIntensity();
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(location.x - radius, location.y - radius);
    drawPoints[1] = new FloatPoint(location.x + radius, location.y + radius);
    graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
    drawPoints[0] = new FloatPoint(location.x + radius, location.y - radius);
    drawPoints[1] = new FloatPoint(location.x - radius, location.y + radius);
    graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
  }

  /** Draws a marker indicating a length violation on a pin. */
  static void drawLengthViolationMarker(
      FloatPoint location, double diameter, Graphics graphics, GraphicsContext graphicsContext) {
    final int drawWidth = 1;
    Color drawColor = graphicsContext.getIncompleteColor();
    double drawIntensity = graphicsContext.getIncompleteColorIntensity();
    double circleRadius = 0.5 * Math.abs(diameter);
    graphicsContext.drawCircle(
        location, circleRadius, drawWidth, drawColor, graphics, drawIntensity);
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(location.x - circleRadius, location.y);
    drawPoints[1] = new FloatPoint(location.x + circleRadius, location.y);
    graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
    if (diameter > 0) {
      // draw also the vertical diameter to create a "+"
      drawPoints[0] = new FloatPoint(location.x, location.y - circleRadius);
      drawPoints[1] = new FloatPoint(location.x, location.y + circleRadius);
      graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
    }
  }
}
