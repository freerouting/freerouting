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

  protected PrintableShape(Locale p_locale) {
    this.locale = p_locale;
  }

  /** Returns text information about the PrintableShape. */
  @Override
  public abstract String toString();

  static class Circle extends PrintableShape {

    public final FloatPoint center;
    public final double radius;

    /** Creates a Circle from the input coordinates. */
    public Circle(FloatPoint p_center, double p_radius, Locale p_locale) {
      super(p_locale);
      center = p_center;
      radius = p_radius;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      String result = tm.getText("circle") + ": ";
      if (center.x != 0 || center.y != 0) {
        String centerString = tm.getText("center") + " =" + center.to_string(this.locale);
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

    public Rectangle(FloatPoint p_lower_left, FloatPoint p_upper_right, Locale p_locale) {
      super(p_locale);
      lowerLeft = p_lower_left;
      upperRight = p_upper_right;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      return tm.getText("rectangle")
          + ": "
          + tm.getText("lowerLeft")
          + " = "
          + lowerLeft.to_string(this.locale)
          + ", "
          + tm.getText("upperRight")
          + " = "
          + upperRight.to_string(this.locale);
    }
  }

  static class Polygon extends PrintableShape {

    public final FloatPoint[] cornerArr;

    public Polygon(FloatPoint[] p_corners, Locale p_locale) {
      super(p_locale);
      cornerArr = p_corners;
    }

    @Override
    public String toString() {
      TextManager tm = new TextManager(this.getClass(), this.locale);

      return tm.getText("polygon")
          + ": "
          + Arrays.stream(cornerArr)
              .map(c -> c.to_string(this.locale))
              .collect(Collectors.joining(", "));
    }
  }
}
