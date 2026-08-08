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
  public Ellipse(FloatPoint pCenter, double pRotation, double pRadius1, double pRadius2) {
    this.center = pCenter;
    double currRotation;
    if (pRadius1 >= pRadius2) {
      this.biggerRadius = pRadius1;
      this.smallerRadius = pRadius2;
      currRotation = pRotation;
    } else {
      this.biggerRadius = pRadius2;
      this.smallerRadius = pRadius1;
      currRotation = pRotation + 0.5 * Math.PI;
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
