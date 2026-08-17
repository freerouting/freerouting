package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.util.TextManager;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shape class used for printing a geometry.planar.Shape after transforming it to user coordinates.
 */
public abstract class PrintableShape {

  protected final Locale locale;

  /** PrintableShape. */
  protected PrintableShape(Locale locale) {
    this.locale = locale;
  }

  /** Returns text information about the PrintableShape. */
  @Override
  public abstract String toString();

  static class Circle extends PrintableShape {

    public final FloatPoint center;
    public final double radius;

    /** Creates a Circle from the input coordinates. */
    public Circle(FloatPoint center, double radius, Locale locale) {
      super(locale);
      this.center = center;
      this.radius = radius;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      String result = tm.getText("circle") + ": ";
      if (center.x != 0 || center.y != 0) {
        String centerString = tm.getText("center") + " =" + center.toString(this.locale);
        result += centerString;
      }
      NumberFormat nf = NumberFormat.getInstance(this.locale);
      nf.setMaximumFractionDigits(4);
      String radiusString = tm.getText("radius") + " = " + nf.format((float) radius);
      result += radiusString;
      return result;
    }
  }

  /** Creates a Polygon from the input coordinates. */
  static class Rectangle extends PrintableShape {

    public final FloatPoint lowerLeft;
    public final FloatPoint upperRight;

    public Rectangle(FloatPoint lowerLeft, FloatPoint upperRight, Locale locale) {
      super(locale);
      this.lowerLeft = lowerLeft;
      this.upperRight = upperRight;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      return tm.getText("rectangle")
          + ": "
          + tm.getText("lowerLeft")
          + " = "
          + lowerLeft.toString(this.locale)
          + ", "
          + tm.getText("upperRight")
          + " = "
          + upperRight.toString(this.locale);
    }
  }

  static class Polygon extends PrintableShape {

    public final FloatPoint[] corners;

    public Polygon(FloatPoint[] corners, Locale locale) {
      super(locale);
      this.corners = corners;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      return tm.getText("polygon")
          + ": "
          + Arrays.stream(corners)
              .map(c -> c.toString(this.locale))
              .collect(Collectors.joining(", "));
    }
  }
}
