package app.freerouting.gui.controls;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/** Draws a crosshair cursor in board coordinates. */
public abstract class Cursor {

  private static final double MAX_COOR = 1000;
  private static final Line2D VERTICAL_LINE = new Line2D.Double(0, -MAX_COOR, 0, MAX_COOR);
  private static final Line2D HORIZONTAL_LINE = new Line2D.Double(-MAX_COOR, 0, MAX_COOR, 0);
  private static final Line2D RIGHT_DIAGONAL_LINE =
      new Line2D.Double(-MAX_COOR, -MAX_COOR, MAX_COOR, MAX_COOR);
  private static final Line2D LEFT_DIAGONAL_LINE =
      new Line2D.Double(-MAX_COOR, MAX_COOR, MAX_COOR, -MAX_COOR);
  double cursorX;
  double cursorY;
  boolean locationInitialized;

  /** Returns a cursor containing orthogonal and diagonal crosshair lines. */
  public static Cursor get45DegreeCrossHairCursor() {
    return new FortyfiveDegreeCrossHairCursor();
  }

  /**
   * Configures graphics state used to draw a cursor.
   *
   * @param graphics the graphics context to configure
   */
  protected static void initGraphics(Graphics2D graphics) {
    BasicStroke bs = new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    graphics.setStroke(bs);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setColor(Color.WHITE);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));
  }

  /**
   * Draws the cursor.
   *
   * @param graphics the graphics context to use
   */
  public abstract void draw(Graphics graphics);

  /**
   * Sets the board location at which the cursor is drawn.
   *
   * @param location the cursor location
   */
  public void setLocation(Point2D location) {
    this.cursorX = location.getX();
    this.cursorY = location.getY();
    locationInitialized = true;
  }

  private static class FortyfiveDegreeCrossHairCursor extends Cursor {

    @Override
    public void draw(Graphics graphics) {

      if (!locationInitialized) {
        return;
      }
      Graphics2D g2 = (Graphics2D) graphics;
      initGraphics(g2);
      GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
      drawPath.append(VERTICAL_LINE, false);
      drawPath.append(HORIZONTAL_LINE, false);
      drawPath.append(RIGHT_DIAGONAL_LINE, false);
      drawPath.append(LEFT_DIAGONAL_LINE, false);
      g2.translate(this.cursorX, this.cursorY);
      g2.draw(drawPath);
    }
  }
}
