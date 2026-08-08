package app.freerouting.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public abstract class Cursor {

  private static final double MAX_COOR = 1000;
  private static final Line2D VERTICAL_LINE = new Line2D.Double(0, -MAX_COOR, 0, MAX_COOR);
  private static final Line2D HORIZONTAL_LINE = new Line2D.Double(-MAX_COOR, 0, MAX_COOR, 0);
  private static final Line2D RIGHT_DIAGONAL_LINE =
      new Line2D.Double(-MAX_COOR, -MAX_COOR, MAX_COOR, MAX_COOR);
  private static final Line2D LEFT_DIAGONAL_LINE =
      new Line2D.Double(-MAX_COOR, MAX_COOR, MAX_COOR, -MAX_COOR);
  double xCoor;
  double yCoor;
  boolean locationInitialized;

  public static Cursor get45DegreeCrossHairCursor() {
    return new FortyfiveDegreeCrossHairCursor();
  }

  protected static void initGraphics(Graphics2D pGraphics) {
    BasicStroke bs = new BasicStroke(0, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    pGraphics.setStroke(bs);
    pGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    pGraphics.setColor(Color.WHITE);
    pGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));
  }

  public abstract void draw(Graphics pGraphics);

  public void setLocation(Point2D pLocation) {
    this.xCoor = pLocation.getX();
    this.yCoor = pLocation.getY();
    locationInitialized = true;
  }

  private static class FortyfiveDegreeCrossHairCursor extends Cursor {

    @Override
    public void draw(Graphics pGraphics) {

      if (!locationInitialized) {
        return;
      }
      Graphics2D g2 = (Graphics2D) pGraphics;
      initGraphics(g2);
      GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
      drawPath.append(VERTICAL_LINE, false);
      drawPath.append(HORIZONTAL_LINE, false);
      drawPath.append(RIGHT_DIAGONAL_LINE, false);
      drawPath.append(LEFT_DIAGONAL_LINE, false);
      g2.translate(this.xCoor, this.yCoor);
      g2.draw(drawPath);
    }
  }
}
