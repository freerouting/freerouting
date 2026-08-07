package app.freerouting.geometry.planar;

import java.io.Serializable;

/**
 * Describes functionality of an ellipse in the plane. Does not implement the ConvexShape interface,
 * because coordinates are float.
 */
public class Ellipse implements Serializable {

  public final FloatPoint center;

  /** Rotation of the ellipse in radian normed to 0 {@literal <}= rotation {@literal <} pi */
  public final double rotation;

  public final double biggerRadius;
  public final double smallerRadius;

  /** Creates a new instance of Ellipse */
  public Ellipse(FloatPoint p_center, double p_rotation, double p_radius_1, double p_radius_2) {
    this.center = p_center;
    double currRotation;
    if (p_radius_1 >= p_radius_2) {
      this.biggerRadius = p_radius_1;
      this.smallerRadius = p_radius_2;
      currRotation = p_rotation;
    } else {
      this.biggerRadius = p_radius_2;
      this.smallerRadius = p_radius_1;
      currRotation = p_rotation + 0.5 * Math.PI;
    }
    while (currRotation >= Math.PI) {
      currRotation -= Math.PI;
    }
    while (currRotation < 0) {
      currRotation += Math.PI;
    }
    this.rotation = currRotation;
  }
}
