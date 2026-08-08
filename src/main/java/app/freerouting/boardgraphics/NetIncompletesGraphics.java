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
   * @param pNetIncompletes The net incompletes data object.
   * @param pGraphics The AWT graphics object.
   * @param pGraphicsContext The board graphics context.
   * @param pLengthViolationsOnly If true, only draws length violation markers, not airlines.
   */
  public static void draw(
      NetIncompletes pNetIncompletes,
      Graphics pGraphics,
      GraphicsContext pGraphicsContext,
      boolean pLengthViolationsOnly) {
    if (!pLengthViolationsOnly) {
      Color drawColor = pGraphicsContext.getIncompleteColor();
      double drawIntensity = pGraphicsContext.getIncompleteColorIntensity();
      if (drawIntensity <= 0) {
        return;
      }
      FloatPoint[] drawPoints = new FloatPoint[2];
      int drawWidth = 1;
      for (AirLine currIncomplete : pNetIncompletes.getIncompletes()) {
        drawPoints[0] = currIncomplete.fromCorner;
        drawPoints[1] = currIncomplete.toCorner;
        pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, drawIntensity);
        if (!currIncomplete.fromItem.sharesLayer(currIncomplete.toItem)) {
          drawLayerChangeMarker(
              currIncomplete.fromCorner,
              pNetIncompletes.getMarkerRadius(),
              pGraphics,
              pGraphicsContext);
          drawLayerChangeMarker(
              currIncomplete.toCorner,
              pNetIncompletes.getMarkerRadius(),
              pGraphics,
              pGraphicsContext);
        }
      }
    }
    if (pNetIncompletes.getLengthViolation() == 0) {
      return;
    }
    // draw the length violation around every Pin of the net.
    Collection<Pin> netPins = pNetIncompletes.getNet().getPins();
    for (Pin currPin : netPins) {
      drawLengthViolationMarker(
          currPin.getCenter().toFloat(),
          pNetIncompletes.getLengthViolation(),
          pGraphics,
          pGraphicsContext);
    }
  }

  /** Draws a marker indicating a layer change (via or trace segment end) in an airline. */
  public static void drawLayerChangeMarker(
      FloatPoint pLocation, double pRadius, Graphics pGraphics, GraphicsContext pGraphicsContext) {
    final int drawWidth = 1;
    Color drawColor = pGraphicsContext.getIncompleteColor();
    double drawIntensity = pGraphicsContext.getIncompleteColorIntensity();
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(pLocation.x - pRadius, pLocation.y - pRadius);
    drawPoints[1] = new FloatPoint(pLocation.x + pRadius, pLocation.y + pRadius);
    pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, drawIntensity);
    drawPoints[0] = new FloatPoint(pLocation.x + pRadius, pLocation.y - pRadius);
    drawPoints[1] = new FloatPoint(pLocation.x - pRadius, pLocation.y + pRadius);
    pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, drawIntensity);
  }

  /** Draws a marker indicating a length violation on a pin. */
  static void drawLengthViolationMarker(
      FloatPoint pLocation,
      double pDiameter,
      Graphics pGraphics,
      GraphicsContext pGraphicsContext) {
    final int drawWidth = 1;
    Color drawColor = pGraphicsContext.getIncompleteColor();
    double drawIntensity = pGraphicsContext.getIncompleteColorIntensity();
    double circleRadius = 0.5 * Math.abs(pDiameter);
    pGraphicsContext.drawCircle(
        pLocation, circleRadius, drawWidth, drawColor, pGraphics, drawIntensity);
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = new FloatPoint(pLocation.x - circleRadius, pLocation.y);
    drawPoints[1] = new FloatPoint(pLocation.x + circleRadius, pLocation.y);
    pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, drawIntensity);
    if (pDiameter > 0) {
      // draw also the vertical diameter to create a "+"
      drawPoints[0] = new FloatPoint(pLocation.x, pLocation.y - circleRadius);
      drawPoints[1] = new FloatPoint(pLocation.x, pLocation.y + circleRadius);
      pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, drawIntensity);
    }
  }
}
