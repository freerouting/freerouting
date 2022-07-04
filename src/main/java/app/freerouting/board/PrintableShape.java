package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Shape class used for printing a geometry.planar.Shape after transforming it to user coordinates.
 */
public abstract class PrintableShape {
  protected final java.util.Locale locale;

  protected PrintableShape(java.util.Locale p_locale) {
    this.locale = p_locale;
  }

  /** Returns text information about the PrintableShape. */
  public abstract String toString();

  static class Circle extends PrintableShape {
    public final FloatPoint center;
    public final double radius;

    /** Creates a Circle from the input coordinates. */
    public Circle(FloatPoint p_center, double p_radius, java.util.Locale p_locale) {
      super(p_locale);
      center = p_center;
      radius = p_radius;
    }

    public String toString() {
      java.util.ResourceBundle resources =
          java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", this.locale);
      String result = resources.getString("circle") + ": ";
      if (center.x != 0 || center.y != 0) {
        String center_string = resources.getString("center") + " =" + center.to_string(this.locale);
        result += center_string;
      }
      java.text.NumberFormat nf = java.text.NumberFormat.getInstance(this.locale);
      nf.setMaximumFractionDigits(4);
      String radius_string = resources.getString("radius") + " = " + nf.format((float) radius);
      result += radius_string;
      return result;
    }
  }

  /** Creates a Polygon from the input coordinates. */
  static class Rectangle extends PrintableShape {
    public final FloatPoint lower_left;
    public final FloatPoint upper_right;

    public Rectangle(FloatPoint p_lower_left, FloatPoint p_upper_right, java.util.Locale p_locale) {
      super(p_locale);
      lower_left = p_lower_left;
      upper_right = p_upper_right;
    }

    public String toString() {
      java.util.ResourceBundle resources =
          java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", this.locale);
      String result =
          resources.getString("rectangle")
              + ": "
              + resources.getString("lower_left")
              + " = "
              + lower_left.to_string(this.locale)
              + ", "
              + resources.getString("upper_right")
              + " = "
              + upper_right.to_string(this.locale);
      return result;
    }
  }

  static class Polygon extends PrintableShape {
    public final FloatPoint[] corner_arr;

    public Polygon(FloatPoint[] p_corners, java.util.Locale p_locale) {
      super(p_locale);
      corner_arr = p_corners;
    }

    public String toString() {
      java.util.ResourceBundle resources =
          java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", this.locale);
      String result = resources.getString("polygon") + ": ";
      for (int i = 0; i < corner_arr.length; ++i) {
        if (i > 0) {
          result += ", ";
        }
        result += corner_arr[i].to_string(this.locale);
      }
      return result;
    }
  }
}
