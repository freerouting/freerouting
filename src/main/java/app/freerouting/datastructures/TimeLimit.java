package app.freerouting.datastructures;

/** Class used to cancel a performance critical algorithm after a time limit is exceeded. */
public class TimeLimit {

  private final long time_stamp;
  private int time_limit;

  /** Creates a new instance with a time limit of p_milli_seconds milli seconds */
  public TimeLimit(int p_milli_seconds) {
    this.time_limit = p_milli_seconds;
    this.time_stamp = (new java.util.Date()).getTime();
  }

  /** Returns true, if the time limit provided in the constructor of this class is exceeded. */
  public boolean limit_exceeded() {
    long curr_time = (new java.util.Date()).getTime();
    return (curr_time - this.time_stamp > this.time_limit);
  }

  /** Multiplies this TimeLimit by p_factor. */
  public void muultiply(double p_factor) {
    if (p_factor <= 0) {
      return;
    }
    double new_limit = (p_factor * this.time_limit);
    new_limit = Math.min(new_limit, Integer.MAX_VALUE);
    this.time_limit = (int) new_limit;
  }
}
