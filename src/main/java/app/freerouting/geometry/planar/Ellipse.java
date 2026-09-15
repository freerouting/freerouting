package app.freerouting.geometry.planar;

import java.io.Serializable;

/**
 * Describes functionality of an ellipse in the plane. Does not implement the ConvexShape interface,
 * because coordinates are float.
 */
public class Ellipse implements Serializable {

  public final FloatPoint center;

  /** Rotation of the ellipse in radian normed to 0 {@literal <}= rotation {@literal <} pi. */
  public final double rotation;

  public final double biggerRadius;
  public final double smallerRadius;

  /** Creates a new instance of Ellipse. */
  public Ellipse(FloatPoint center, double rotation, double radius1, double radius2) {
    this.center = center;
    double currentRotation;
    if (radius1 >= radius2) {
      this.biggerRadius = radius1;
      this.smallerRadius = radius2;
      currentRotation = rotation;
    } else {
      this.biggerRadius = radius2;
      this.smallerRadius = radius1;
      currentRotation = rotation + 0.5 * Math.PI;
    }
    while (currentRotation >= Math.PI) {
      currentRotation -= Math.PI;
    }
    while (currentRotation < 0) {
      currentRotation += Math.PI;
    }
    this.rotation = currentRotation;
  }
}
