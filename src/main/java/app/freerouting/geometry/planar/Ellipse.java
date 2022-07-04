package app.freerouting.geometry.planar;

/**
 * Describes functionality of an elllipse in the plane. Does not implement the ConvexShape
 * interface, because coordinates are float.
 */
public class Ellipse implements java.io.Serializable {

  public final FloatPoint center;
  /** Rotation of the ellipse in radian normed to 0 {@literal <}= rotation {@literal <} pi */
  public final double rotation;
  public final double bigger_radius;
  public final double smaller_radius;
  /** Creates a new instance of Ellipse */
  public Ellipse(FloatPoint p_center, double p_rotation, double p_radius_1, double p_radius_2) {
    this.center = p_center;
    double curr_rotation;
    if (p_radius_1 >= p_radius_2) {
      this.bigger_radius = p_radius_1;
      this.smaller_radius = p_radius_2;
      curr_rotation = p_rotation;
    } else {
      this.bigger_radius = p_radius_2;
      this.smaller_radius = p_radius_1;
      curr_rotation = p_rotation + 0.5 * Math.PI;
    }
    while (curr_rotation >= Math.PI) {
      curr_rotation -= Math.PI;
    }
    while (curr_rotation < 0) {
      curr_rotation += Math.PI;
    }
    this.rotation = curr_rotation;
  }
}
