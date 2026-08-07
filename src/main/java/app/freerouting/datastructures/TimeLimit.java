package app.freerouting.datastructures;

import java.util.Date;

/** Class used to cancel a performance critical algorithm after a time limit is exceeded. */
public class TimeLimit {

  private final long timeStamp;
  private int timeLimit;

  /** Creates a new instance with a time limit of p_milli_seconds milliseconds */
  public TimeLimit(int p_milli_seconds) {
    this.timeLimit = p_milli_seconds;
    this.timeStamp = new Date().getTime();
  }

  /** Returns true, if the time limit provided in the constructor of this class is exceeded. */
  public boolean limit_exceeded() {
    long currTime = new Date().getTime();
    return currTime - this.timeStamp > this.timeLimit;
  }

  /** Multiplies this TimeLimit by p_factor. */
  public void multiply(double p_factor) {
    if (p_factor <= 0) {
      return;
    }
    double newLimit = p_factor * this.timeLimit;
    newLimit = Math.min(newLimit, Integer.MAX_VALUE);
    this.timeLimit = (int) newLimit;
  }
}
