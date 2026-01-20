package app.freerouting.geometry.planar;

/**
 * Implementation of an enum class Side with the three values ON_THE_LEFT, ON_THE_RIGHT, COLLINEAR.
 */
public class Side {
  public static final Side ON_THE_LEFT = new Side("on_the_left");
  public static final Side ON_THE_RIGHT = new Side("on_the_right");
  public static final Side COLLINEAR = new Side("collinear");
  private final String name;

  private Side(String p_name) {
    name = p_name;
  }

  /**
   * returns ON_THE_LEFT, if p_value < 0, ON_THE_RIGHT, if p_value > 0 and COLLINEAR, if p_value ==
   * 0
   */
  static Side of(double p_value) {
    Side result;
    if (p_value > 0) {
      result = Side.ON_THE_LEFT;
    } else if (p_value < 0) {
      result = Side.ON_THE_RIGHT;
    } else {
      result = Side.COLLINEAR;
    }
    return result;
  }

  /** returns the string of this instance */
  public String to_string() {
    return name;
  }

  /** returns the opposite side of this side */
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
