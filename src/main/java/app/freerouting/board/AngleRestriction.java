package app.freerouting.board;

/** Enum for angle restrictions none, fortyfive degree and ninety degree. */
public class AngleRestriction {
  public static final AngleRestriction NONE = new AngleRestriction("none", 0);
  public static final AngleRestriction FORTYFIVE_DEGREE = new AngleRestriction("45 degree", 1);
  public static final AngleRestriction NINETY_DEGREE = new AngleRestriction("90 degree", 2);

  public static final AngleRestriction[] arr = {NONE, FORTYFIVE_DEGREE, NINETY_DEGREE};
  private final String name;
  private final int no;
  /** Creates a new instance of SnapAngle */
  private AngleRestriction(String p_name, int p_no) {
    name = p_name;
    no = p_no;
  }

  /** Returns the string of this instance */
  public String to_string() {
    return name;
  }

  /** Returns the number of this instance */
  public int get_no() {
    return no;
  }
}
