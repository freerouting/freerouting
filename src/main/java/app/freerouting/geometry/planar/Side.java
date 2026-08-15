package app.freerouting.geometry.planar;

/**
 * Implementation of an enum class Side with the three values ON_THE_LEFT, ON_THE_RIGHT, COLLINEAR.
 */
public final class Side {

  public static final Side ON_THE_LEFT = new Side("onTheLeft");
  public static final Side ON_THE_RIGHT = new Side("onTheRight");
  public static final Side COLLINEAR = new Side("collinear");
  private final String name;

  private Side(String name) {
    this.name = name;
  }

  /**
   * Returns ON_THE_LEFT if value &lt; 0, ON_THE_RIGHT if value &gt; 0, and COLLINEAR if value == 0.
   */
  static Side of(double value) {
    Side result;
    if (value > 0) {
      result = Side.ON_THE_LEFT;
    } else if (value < 0) {
      result = Side.ON_THE_RIGHT;
    } else {
      result = Side.COLLINEAR;
    }
    return result;
  }

  /** Returns the string of this instance. */
  @Override
  public String toString() {
    return name;
  }

  /** Returns the opposite side of this side. */
  public final Side negate() {
    Side result;
    if (this == ON_THE_LEFT) {
      result = ON_THE_RIGHT;
    } else if (this == ON_THE_RIGHT) {
      result = ON_THE_LEFT;
    } else {
      result = this;
    }
    return result;
  }
}
